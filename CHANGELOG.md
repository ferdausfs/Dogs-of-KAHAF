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

## How to deploy

1. Replace your repo content with the files in this archive.
2. Push to GitHub — the existing `Build Debug APK` workflow will produce a new APK.
3. Install over the previous version (no data migration needed — DB schema unchanged).
4. **Important for AI to actually work:**
   - In-app: Settings → AI Screen Detection → Upload Model
   - Recommended models: NSFWJS converted to TFLite (224×224, output `[1,5]`),
     or any GantMan-style 2-class classifier (output `[1,2]`).
   - Threshold 0.6–0.75 is a good starting point.

If after this update AI still doesn't catch anything, the root cause is almost certainly the model file itself (wrong input shape / quantization). Send `adb logcat | grep TFLite` and we can debug from the inference logs.
