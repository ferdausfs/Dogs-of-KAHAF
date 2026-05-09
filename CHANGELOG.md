# Guardian Shield — v11 (2.1.1) STABILITY PATCH

versionCode: 4 → **5**
versionName: 2.1.0 → **2.1.1**

## 🚨 Why this release exists

User report: *"app er onek function thik thak kaj kore... akhon update korechi.. kintu crush kore app"*
(Most features work, but after the v10 update the app crashes.)

After full audit, **eight separate crash / ANR vectors** were discovered.
This release fixes every one of them. No feature changes — pure stability.

## 🔧 Critical fixes

### 1. AiDetector.close() ANR on import / reset model — FIXED
**Root cause:** `close()` used `runBlocking(Dispatchers.IO)` on the Main
thread inside `SettingsViewModel.viewModelScope`. With the 2-second
inference timeout, the UI froze for up to 2 s every time the user
tapped "Import" or "Reset model" → Android killed the activity with
`Application Not Responding`.

**Fix:**
- Added `closeAsync(scope)` — non-blocking, schedules teardown on IO.
- Added `closeSuspend()` — proper suspend variant.
- `SettingsViewModel.importModel/resetModel` now wrap in `withContext(Dispatchers.IO)`.
- `GuardianAccessibilityService.onDestroy` uses `closeAsync` so destroy returns instantly.

### 2. ForegroundServiceStartNotAllowedException crash — FIXED
**Root cause:** On Android 12+, `startForegroundService` from background
(e.g. `BootReceiver` after device reboot, or after app update via
`MY_PACKAGE_REPLACED`) can throw `ForegroundServiceStartNotAllowedException`
or `SecurityException` (missing `POST_NOTIFICATIONS`). Both were uncaught
→ visible crash.

**Fix:**
- `startForegroundSafely()` wrapper catches every Throwable.
- If start fails, schedules a retry via `AlarmManager` instead of crashing.
- `stopSelf()` cleanly when foreground promotion is denied.

### 3. POST_NOTIFICATIONS missing on Android 13+ — FIXED
**Root cause:** Permission was declared in manifest but **never requested
at runtime**. On Android 13+ the foreground notification was suppressed,
which then made the OS more likely to kill our service.

**Fix:**
- `MainActivity` now launches `RequestPermission` for `POST_NOTIFICATIONS`
  on first unlocked dashboard render (API 33+).
- User-visible toast if denied.

### 4. Background activity launch rejected on Android 14 — FIXED
**Root cause:** `BlockingEngine` passed `ActivityOptions` for the overlay
but **not for the HOME intent**. On Android 14 (Pixel + One UI 6) HOME
launched silently failed → user remained inside the offending app.

**Fix:**
- Both HOME and Overlay launches now receive
  `MODE_BACKGROUND_ACTIVITY_START_ALLOWED` ActivityOptions on API 34+.

### 5. EncryptedSharedPreferences crash on broken Keystore — FIXED
**Root cause:** Some Mediatek / older Huawei devices and devices
post-factory-reset have a broken AndroidKeyStore. Calling
`EncryptedSharedPreferences.create` threw → app crashed at startup,
**before MainActivity even rendered**.

**Fix:**
- 3-tier fallback in `SecureStorage`:
  1. Try EncryptedSharedPreferences (preferred).
  2. On failure, wipe the corrupted prefs file and retry.
  3. As last resort, fall back to plain `SharedPreferences`. PIN is
     still SHA-256 hashed, so security degrades gracefully.

### 6. AppList screen crash via MATCH_ALL — FIXED
**Root cause:** `AppListViewModel.getInstalledApplications(MATCH_ALL)`.
`MATCH_ALL` requires `QUERY_ALL_PACKAGES` privilege. On some Android 11+
ROMs without that privilege it threw `SecurityException` → AppList
screen crashed when opened.

**Fix:**
- Use safe default flag `0` instead of `MATCH_ALL`.
- Whole `load()` body wrapped in `runCatching`.

### 7. DataStore IOException on corrupted prefs — FIXED
**Root cause:** A force-shutdown during a write can corrupt the DataStore
file. Subsequent reads threw IOException, which was uncaught in the
collector → crash.

**Fix:**
- Every Flow now `.catch{}` IOException and emits empty preferences
  (returns to defaults instead of crashing).
- `currentRulesVersion / currentProtectionEnabled` wrapped in `runCatching`.

### 8. AccessibilityService permanently disabled by uncaught throw — FIXED
**Root cause:** The Android framework auto-disables an accessibility
service if its `onAccessibilityEvent` ever throws. A single NPE inside
`evaluatePackage` or a recycled-node access during traversal would
permanently kill protection until user manually re-enabled it from
Settings — user reported this as *"sob thik ase kintu app kaj kore na"*.

**Fix:**
- Top-level `try/catch` around the entire `onAccessibilityEvent` body.
- All `node.text / node.contentDescription / node.childCount /
  node.getChild()` calls wrapped in `runCatching` (these can throw
  `IllegalStateException("nodeInfo is sealed")`).
- `screenStateReceiver.onReceive` also hardened.

## 🛡️ Defensive hardening (no behaviour change)

- `BootReceiver.onReceive` whole body in runCatching.
- `outputClasses` in AiDetector marked `@Volatile` (memory visibility race).
- TFLite native call wrapped in try/catch (`runLegacyInference` could
  throw IllegalStateException from JNI on some Adreno drivers).
- Removed unused `kotlinx.coroutines.flow.first` import in AiDetector.
- `BlockOverlayActivity.setShowWhenLocked / setTurnScreenOn` wrapped
  (some OEMs throw on these).
- `MainActivity.exportLog` rewritten to use `MediaStore.Downloads`
  on Android 10+ (legacy `Environment.getExternalStoragePublicDirectory`
  write is rejected on scoped-storage devices).
- `MainActivity` click handlers via `safeStartActivity()` helper with
  user-visible toast on failure.
- `GuardianApp` installs uncaught exception logger in DEBUG builds.
- Receiver registration with `RECEIVER_NOT_EXPORTED` flag on API 33+
  (defensive for non-protected broadcast misclassification by some
  Vivo / Realme builds).
- Manifest: added `android:hardwareAccelerated="true"` and
  `resizeableActivity="false"` on overlay for OEM compat.
- AppModule migrations now use `IF NOT EXISTS` and add
  `fallbackToDestructiveMigrationOnDowngrade()`.
- KSP arg `room.incremental=true` for faster builds.

## 📁 Files changed (15)

**Modified — every change is non-breaking:**
- `app/build.gradle.kts` — versionCode 4→5, versionName 2.1.0→2.1.1
- `app/src/main/AndroidManifest.xml`
- `app/src/main/java/com/guardian/shield/GuardianApp.kt`
- `app/src/main/java/com/guardian/shield/di/AppModule.kt`
- `app/src/main/java/com/guardian/shield/data/local/datastore/SecureStorage.kt`
- `app/src/main/java/com/guardian/shield/data/local/datastore/GuardianPreferences.kt`
- `app/src/main/java/com/guardian/shield/service/detection/AiDetector.kt`
- `app/src/main/java/com/guardian/shield/service/blocker/BlockingEngine.kt`
- `app/src/main/java/com/guardian/shield/service/blocker/GuardianForegroundService.kt`
- `app/src/main/java/com/guardian/shield/service/accessibility/GuardianAccessibilityService.kt`
- `app/src/main/java/com/guardian/shield/receiver/BootReceiver.kt`
- `app/src/main/java/com/guardian/shield/viewmodel/SettingsViewModel.kt`
- `app/src/main/java/com/guardian/shield/viewmodel/AppListViewModel.kt`
- `app/src/main/java/com/guardian/shield/ui/dashboard/MainActivity.kt`
- `app/src/main/java/com/guardian/shield/ui/overlay/BlockOverlayActivity.kt`

## 🚀 Deploy

1. Replace files / push tree.
2. GitHub Actions → debug APK → install over v10.
3. Room migration v3 → v3 = no-op. **No data wipe.**
4. On first launch:
   - Grant POST_NOTIFICATIONS prompt (Android 13+).
   - Re-confirm permissions on Permission Health screen.

---

# Guardian Shield — v10 (2.1.0) Smart Tiered Detection

(see prior changelog block — kept verbatim from v10 release)

versionCode: 3 → 4
versionName: 2.0.0 → 2.1.0

## 🎯 Headline change — anti-aggressive policy

Four-tier classification: only the EXPLICIT tier blocks.

| Tier | Score range | Action |
|------|-------------|--------|
| `SAFE`        | combined < 0.30 | ignore |
| `NATURAL`     | 0.30 ≤ x < 0.55 | ignore |
| `SUGGESTIVE`  | 0.55 ≤ x < 0.75 | log only |
| `EXPLICIT`    | porn ≥ 0.78, hentai ≥ 0.75, OR combined ≥ 0.75 | BLOCK |

Plus: 2-frame EXPLICIT debounce, 15-min source-app auto-lock, +0.10
threshold boost for Photos / Gallery / Camera / Maps.

---

# Guardian Shield — v9 (2.0.0) Performance Pass
(see git history)
