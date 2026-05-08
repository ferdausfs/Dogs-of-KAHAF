# Guardian Shield — Bug Review & Fix Update

This update addresses the user-reported issue **"AI detects adult images/content but does not block them"** plus a full pass of bugs found across the codebase.

---

## 🔴 P0 — Why AI was detecting but not blocking

### 1. `AiDetector.kt` — pixel values were NEVER normalized
The original code fed raw `0..255` floats into TFLite. Every common NSFW model (NSFWJS, GantMan, OpenNSFW, MobileNet-NSFW) expects `0..1`. So even with a model uploaded, predictions were random noise → never crossed the threshold → no block.

**Fix:** Added `NormalizeOp(0f, 255f)` in the image-processor pipeline. Pixel values now arrive as `0..1` floats — the format every public NSFW TFLite model uses.

### 2. `GuardianAccessibilityService.kt` — AI ran only on text events
AI screenshot scan was triggered exclusively from `TYPE_VIEW_TEXT_CHANGED` / `TYPE_WINDOW_CONTENT_CHANGED`. **Image-only screens (Reels, Gallery, Image search, Video player) emit very few text events** — so AI almost never fired in exactly the apps that needed it most.

**Fix:** Added a periodic AI scanner coroutine that ticks every 1.5 s on the active foreground package whenever AI detection is enabled and a model is loaded — independent of accessibility events.

### 3. `AiDetector.kt` — output shape was guessed, not detected
2-class vs 5-class was decided by `try/catch` over `interpreter.run()`. Shape mismatches don't always throw, so a 5-class model could be read as 2-class and produce garbage indices.

**Fix:** Output shape is read once from `interpreter.getOutputTensor(0).shape()` at load time and dispatched correctly. Also added a generic fallback for arbitrary class counts.

### 4. No model bundled + no UX warning
The APK ships without a `.tflite` model. If the user enables AI without uploading one, nothing happens silently.

**Fix:** Settings screen now displays:
> ⚠️ AI is ON but no model uploaded — detection will NOT work

so the user immediately sees what's wrong.

---

## 🟠 P1 — Security holes

### 5. PIN gate did not actually gate anything
`MainActivity` rendered first, *then* launched `PinVerifyActivity` on top. Press HOME, come back → main UI was visible without verification.

**Fix:** `MainActivity` now keeps its root view `INVISIBLE` and launches `PinVerifyActivity` via `ActivityResultLauncher`. The dashboard only becomes visible on `RESULT_OK`. On cancel → `finishAffinity()`.

### 6. Settings, AppList, Keyword screens had ZERO PIN check
Anyone could open Settings and unblock apps, disable AI, or wipe keywords.

**Fix:** All three screens now require PIN verify before showing UI.

### 7. Block overlay was bypass-able on OEMs
Background-activity-launch restrictions on Android 10+ (and aggressive killers on MIUI / ColorOS / FunTouchOS) often dropped the overlay.

**Fix:** `BlockingEngine` now (a) always sends `Intent.CATEGORY_HOME` first to evict the offending app, then (b) launches the overlay with `FLAG_ACTIVITY_NO_HISTORY` and `ActivityOptions` allowing background starts on API 34+. Each `startActivity` is wrapped in `runCatching` so a single OEM failure doesn't break the whole block path.

---

## 🟡 P2 — Stability / logic / API correctness

### 8. `pkg.startsWith("com.android.systemui")` over-matched
Matched fictitious `com.android.systemuixyz`. Replaced with an exact-match `Set` for system packages plus a documented prefix list for legitimately-varying launcher packages (MIUI / Samsung / OPPO / Vivo / Realme / Huawei).

### 9. Deprecated `onBackPressed()` overrides
`@Suppress("MissingSuperCall")` doesn't make predictive-back work. Replaced in `PinVerifyActivity`, `PinSetupActivity`, and `BlockOverlayActivity` with `OnBackPressedDispatcher` callbacks. Manifest also gets `android:enableOnBackInvokedCallback="true"`.

### 10. `lastAiScanMs` was global
After switching apps the first 3 s was a blind spot. Replaced with a per-package `HashMap`.

### 11. `toggleBlock` deleted rows that still had whitelist info
`toggleBlock` called `delete()`, wiping a row that might have had `isWhitelisted=true`. Block / whitelist flags are now independent; rows are deleted only when both flags become false.

### 12. `notificationTimeout=100` dropped fast events
Bumped to `200`. Combined with the new periodic scanner this is comfortably reliable without spamming the OS.

### 13. `BootReceiver` could throw `BackgroundServiceStartNotAllowedException`
Wrapped in `runCatching`, also accepts `LOCKED_BOOT_COMPLETED` for Direct Boot devices, and verifies the action before starting.

### 14. `Interpreter.run()` was called from multiple coroutines
Native crash risk under load. Inference is now serialized through a `Mutex` and screenshot-then-inference is guarded by an `AtomicBoolean` so only one is in flight at a time.

### 15. `KeywordActivity` accepted invalid regex
Invalid patterns were silently swallowed in `RulesEngine.evaluateText`. Now validated at insert time with an inline error.

### 16. `AppListViewModel` listed our own package
Hidden — you can't whitelist Guardian Shield from itself anyway.

### 17. Foreground-service type not declared at start on API 34
Calling `startForeground(id, notif)` without the type parameter on API 34+ violates `FOREGROUND_SERVICE_SPECIAL_USE`. Fixed by passing `FOREGROUND_SERVICE_TYPE_SPECIAL_USE` on `UPSIDE_DOWN_CAKE+`.

### 18. `Settings` "Model loaded" indicator was stuck on stale state
Added `refresh()` on `SettingsViewModel` and called after upload — UI now updates immediately.

### 19. `<queries>` element missing
Play Store rejects `QUERY_ALL_PACKAGES` for most apps. Added a `<queries>` element with the LAUNCHER intent so the app list works without the wide permission. (Wide permission kept for sideload convenience.)

---

## 🟢 v2 — Persistence, Permission Health, Admin-level survival

User report (verbatim):
> *"app ta update korte hobe... khubi sabdane kono kiso nosto na kore... akhon app abloi kaj kore.. kintu maje maje permission auto remove hoy na not working ba emono hoy sob thik ase kintu app kaj kore na... app take admin level e permission deb jeno jokhon jekhene dorkaj kaj kore"*

Three independent root causes were identified:

| # | Why it fails | Where it gets fixed |
|---|---|---|
| A | Android 11+ silently auto-revokes runtime permissions for apps the user hasn't opened in ~3 months | `REQUEST_DISABLE_APP_HIBERNATION` permission + Permission Health screen |
| B | OEM Battery Savers (MIUI / ColorOS / FunTouch / Realme / OneUI) kill the foreground service in the background → "sob thik ase kintu kaj kore na" | `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` + Watchdog + onTaskRemoved self-restart |
| C | Casual uninstall — user disables the app in 3 taps | New `GuardianDeviceAdminReceiver` ("admin-level permission") |

### 20. NEW — `PermissionManager.kt` (`util/`)
Single source of truth for the live status of every permission Guardian needs. Returns a `snapshot()` list of seven keys: Accessibility, Overlay, Usage Stats, Battery Unrestricted, Notifications, Auto-Revoke Disabled, Device Admin. For every key it knows the canonical settings intent so we can deep-link the user to the exact OS page.

### 21. NEW — `PermissionsActivity` ("Permission Health")
Brand-new optional screen that lists every permission with live GRANTED / MISSING badges and a one-tap **Grant** button per row. Re-renders on `onResume()` so coming back from system settings updates the state instantly. Linked from both:
- a new entry-point button on the dashboard, and
- a new "Persistence" card in Settings.

A live red banner on the dashboard now warns the user the moment any critical permission is missing — directly addresses *"permission auto remove hoy"*.

### 22. NEW — `GuardianDeviceAdminReceiver` + `xml/device_admin.xml`
Optional Device Admin component. Once activated:
- The app cannot be uninstalled normally (the OS forces the user to disable admin first).
- Many OEMs (MIUI / ColorOS / FunTouch / Realme UI) treat device-admin apps as "protected" and stop killing them in the background, which directly fixes *"sob thik ase kintu app kaj kore na"*.

We deliberately ask only for the bare-minimum `force-lock` policy — no password / camera / wipe policies — to keep this Play-policy-friendly and non-intrusive. Activation is **completely optional** — every other feature works without it.

### 23. `GuardianForegroundService.kt` — Watchdog + self-restart
- **Watchdog**: every 30 s the service re-checks all critical permissions. If anything is missing, the persistent notification flips to high-priority **"⚠ N permission(s) missing — tap to fix"** that opens `PermissionsActivity` directly.
- **`onTaskRemoved`**: when the user (or an OEM cleaner) swipes the app away, the service immediately re-launches itself so protection never silently dies.
- A second, high-importance notification channel (`guardian_alerts`) is registered for future degraded-state alerts.

### 24. `BootReceiver.kt` — survive app updates
Now also fires on `MY_PACKAGE_REPLACED` and `PACKAGE_REPLACED`, so the protection service is restarted automatically right after the user installs an update — no need to reopen the app.

### 25. `MainActivity.kt` — live permission banner
On every `onResume()` the dashboard re-checks `PermissionManager.missingCritical(this)`. If anything has been silently revoked (auto-revoke / battery saver / OEM kill), a red banner appears at the top of the dashboard with one-tap navigation to Permission Health.

### 26. `AndroidManifest.xml` — three new permissions + admin receiver
- `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` — to ask for unrestricted battery
- `REQUEST_DISABLE_APP_HIBERNATION` — to disable Android 12+ auto-revoke
- `BIND_DEVICE_ADMIN` — required by the new admin receiver
- New activity, receiver, and `MY_PACKAGE_REPLACED` filter registered

### 27. Existing flows untouched
The whole v2 update is purely **additive**. No existing class signature changed, no DI binding moved, no DB schema touched, no existing string renamed. If the v2 features are never enabled, the app behaves exactly like v1.

---

## How to deploy

1. Replace your repo content with the files in this archive.
2. Push to GitHub — the existing `Build Debug APK` workflow will produce a new APK.
3. Install over the previous version (no data migration needed — DB schema unchanged).

### After install — recommended one-time setup

Open Guardian Shield → tap the new **Permission Health** button → grant in this order:

1. **Accessibility Service** (already had this — but re-check, OEMs sometimes drop it)
2. **Display over other apps** (overlay)
3. **Unrestricted battery** ⟵ this single toggle fixes most "sob thik ase kintu kaj kore na" cases
4. **Notifications** (Android 13+)
5. **Disable permission auto-reset** ⟵ stops "permission auto remove hoy" on Android 11+
6. **Device admin** ⟵ uninstall protection + better OEM survival

After all six are GRANTED, the red banner on the dashboard disappears and the persistent notification stays at "Protection Active" — that's how you know everything is wired up correctly.

### AI detection (unchanged from v1)

- In-app: Settings → AI Screen Detection → Upload Model
- Recommended models: NSFWJS converted to TFLite (224×224, output `[1,5]`),
  or any GantMan-style 2-class classifier (output `[1,2]`).
- Threshold 0.6–0.75 is a good starting point.

If after this update AI still doesn't catch anything, the root cause is almost certainly the model file itself (wrong input shape / quantization). Send `adb logcat | grep TFLite` and we can debug from the inference logs.
