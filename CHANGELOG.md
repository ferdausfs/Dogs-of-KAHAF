# Guardian Shield — v8 (1.1.0) Stability Pass

versionCode: 1 → **2**
versionName: 1.0.0 → **1.1.0**

## Bugs fixed (priority order)

### 🔴 P0 — Detection silently stops
- **BUG-01** `GuardianAccessibilityService.triggerAiCheck()`
  Wrapped the entire `withContext(Dispatchers.Main)` screenshot block in a
  guarded `try/finally` with an `AtomicBoolean callbackTookOver` sentinel.
  `aiInFlight` is now reset whenever no callback claims it (sync throws,
  cancellation, detached service).
- **BUG-02** `startPeriodicAiScanner()`
  Each tick wrapped in `try/catch`; a single failed tick can no longer kill
  the periodic loop.
- **BUG-03** `lastAiScanByPkg` is bounded at 50 entries; oldest entry is
  evicted before insert (`recordScanTime` / `lastScanTimeFor`, synchronized).
- **BUG-04** `AiDetector.close()` now acquires `inferenceLock` via
  `runBlocking { inferenceLock.withLock { … } }` before nulling native
  Interpreter handles. Eliminates the JNI native crash race with active
  `Interpreter.run()`.

### 🟠 P1 — Service killed, not restarted
- **BUG-05** `GuardianAccessibilityService.isRunning` companion flag, set in
  `onServiceConnected` / cleared in `onDestroy`. The watchdog in
  `GuardianForegroundService` now reports a degraded state when settings
  claim accessibility is on but the bound service is dead.
- **BUG-06** `onTaskRemoved` no longer calls `startForegroundService`
  directly. It schedules an `AlarmManager` exact alarm (3 s) targeting
  `BootReceiver` with `ACTION_RESTART_SERVICE`. Avoids
  `ForegroundServiceDidNotStartInTimeException` on Android 12+.
- **BUG-07** `BootReceiver` uses exact match
  `intent.data?.schemeSpecificPart == context.packageName` instead of
  `String.contains`.

### 🟡 P2 — Correctness gaps
- **BUG-08** `RulesEngine` uses a single immutable `RulesSnapshot` data
  class swapped atomically (one volatile reference write). Evaluators
  always see a coherent view.
- **BUG-09** API < 30 fallback: `scheduleLegacyFollowUpChain` runs three
  text-based rescans at 500/1500/3000 ms after window changes. Best-effort
  for scroll-heavy apps without MediaProjection.
- **BUG-10** `BlockingEngine` per-package throttle map (capped at 50,
  oldest-out eviction). Rapid alternation between two blocked packages no
  longer bypasses the 800 ms throttle.
- **BUG-11** `AppListViewModel.toggleBlock/toggleWhitelist` mutate just
  the changed entry in-memory (`patchInMemory`). Full `load()` only on
  init() and explicit refresh().
- **BUG-12** New DataStore key `KEY_RULES_VERSION` + `bumpRulesVersion()`.
  `MainActivity.onResume` only triggers `RulesEngine.reload()` when the
  on-disk version moved past `cachedRulesVersion`.

### 🟢 P3 — UX gaps
- **BUG-13** `SettingsActivity.copyLegacyModel` runs on `Dispatchers.IO`
  with on-screen `Importing…` status, atomic `.tmp` → rename, size
  guards, and TFLite "TFL3" header validation. No more 150 MB ANR.
- **BUG-14** Watchdog now posts a separate **high-importance** alert
  notification (ID `NOTIFICATION_ID + 1`) on `CHANNEL_ID_ALERT` when
  protection is degraded. The persistent foreground notification stays
  on the low-importance channel.

## Files changed (13)
- `app/build.gradle.kts`
- `app/src/main/res/values/strings.xml`
- `app/src/main/java/com/guardian/shield/service/accessibility/GuardianAccessibilityService.kt`
- `app/src/main/java/com/guardian/shield/service/detection/AiDetector.kt`
- `app/src/main/java/com/guardian/shield/service/detection/RulesEngine.kt`
- `app/src/main/java/com/guardian/shield/service/blocker/GuardianForegroundService.kt`
- `app/src/main/java/com/guardian/shield/service/blocker/BlockingEngine.kt`
- `app/src/main/java/com/guardian/shield/receiver/BootReceiver.kt`
- `app/src/main/java/com/guardian/shield/data/local/datastore/GuardianPreferences.kt`
- `app/src/main/java/com/guardian/shield/ui/dashboard/MainActivity.kt`
- `app/src/main/java/com/guardian/shield/ui/settings/SettingsActivity.kt`
- `app/src/main/java/com/guardian/shield/viewmodel/AppListViewModel.kt`
- `app/src/main/java/com/guardian/shield/viewmodel/KeywordViewModel.kt`

## Schema
- **Room DB:** unchanged (still `version=1`, no migration required).
- **DataStore:** one new key (`rules_version`, default `0`) — no migration
  required, missing keys default to 0.

## Conflicts
None — all fixes coexist cleanly.

## How to deploy
1. Replace the 13 files above (or push the entire `v8` tree to Git).
2. Run the GitHub Actions debug workflow → `./gradlew assembleDebug`.
3. Install over the existing v7 build (`adb install -r app-debug.apk` or
   tap the APK on device). **No data wipe needed** — the install is fully
   compatible (same `applicationId`, no DB schema change, no breaking
   DataStore key rename).
4. **First launch after update:**
   - Open the app once. The PIN prompt unlocks the dashboard as before.
   - On the **Permission Health** screen, re-confirm:
     * Accessibility (after every Android update some OEMs re-disable it),
     * Battery → Unrestricted,
     * Notifications,
     * Disable permission auto-revoke.
   - The watchdog will pop a **high-importance** alert if anything is off
     (this is the new BUG-14 channel) — tap the alert to fix.
5. Optional: re-import any TFLite models. Existing files in `filesDir/`
   from v7 are reused as-is — no re-import required.
