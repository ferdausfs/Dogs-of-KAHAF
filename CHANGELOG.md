# Changelog

All notable changes to Guardian Shield are documented here.
The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/)
and the project adheres to [Semantic Versioning](https://semver.org/).

## [3.1.0] — 2026-05-10  —  Legacy-merge release

This release re-introduces every "power-user" feature that lived in the
old v2.x (`com.guardian.shield`) codebase but was missing in the v3.0.0
rewrite, while keeping the new v3.0.0 Compose / Hilt / Room / Clean
Architecture stack untouched. Nothing from the legacy build's known
bugs (Keystore-startup ANR, regex-backtracking, scope-leak, GPU-on-Adreno
crash, etc.) was carried over — only the working features were ported.

### Added (legacy merge)
- **Device Admin uninstall protection.** New
  `com.kahaf.guardianshield.admin.GuardianDeviceAdminReceiver` +
  `res/xml/device_admin.xml`. The `Settings → Uninstall protection`
  toggle now actually launches the system "Activate Device Admin?"
  prompt and reflects live OS state — stops casual uninstall and helps
  the FG service survive aggressive OEM killers (MIUI / ColorOS /
  FunTouch / Realme).
- **Custom NSFW model import** (`ModelImportManager`). User can pick a
  `.tflite` from Storage Access Framework; the file is atomically
  copied into `filesDir`, validated against the TFLite `"TFL3"` magic
  header, then opened in a throwaway `Interpreter` to confirm it loads
  before being committed.
- **Reflection / cool-down screen** (`DelayUnlockScreen` route) —
  self-imposed friction layer: wait N seconds before confirming a
  sensitive change (disable protection, remove PIN, mass-delete rules).
- **`AppClassifier` utility** with comprehensive content-source app
  list (Facebook / Instagram / X / TikTok / Snapchat / Reddit /
  Telegram / WhatsApp / 14+ browsers) and "safe heavy-image apps"
  list (Photos / Gallery / Camera / Maps) for less-aggressive judgment.
- **`GuardianConstants`** — single source of truth for tiered
  thresholds (NATURAL / SUGGESTIVE / EXPLICIT), per-class cut-offs
  (porn / hentai / sexy), debounce window, source-block duration,
  heavy-image boost.
- **`SecureStorage`** (encrypted prefs via `EncryptedSharedPreferences`
  with 3-tier fallback: encrypted → corrupted-prefs recovery → plain).
- **LOW / BALANCED / HIGH sensitivity preset chips** in the
  `AiSettings` screen.
- **"Disable permission auto-reset" (Android 11+)** action in Settings
  — stops the OS from silently revoking Accessibility / Overlay if
  the user doesn't open the app for a few months.
- **PNG launcher icons** for every density (mdpi / hdpi / xhdpi /
  xxhdpi / xxxhdpi) plus the legacy `ic_shield_on` / `ic_shield_off`
  / `search_view_bg` drawables — nicer visual on devices that don't
  render the adaptive-icon vectors.
- **Manifest hardening:** `hardwareAccelerated="true"`,
  `BlockOverlayActivity android:resizeableActivity="false"`,
  `enableOnBackInvokedCallback="true"`, `BIND_DEVICE_ADMIN`,
  `REQUEST_DISABLE_APP_HIBERNATION`.
- New strings: `device_admin_description`, sensitivity-preset labels,
  custom-model-import labels, auto-revoke labels.

### Changed
- `PermissionManager.Snapshot` extended with `deviceAdmin` and
  `autoRevokeDisabled` fields (kept `allCriticalGranted` semantics
  unchanged so existing callers still work).
- `SettingsViewModel` now exposes `deviceAdminActive` /
  `autoRevokeDisabled` `StateFlow`s and a working
  `setUninstallProtection` that triggers the OS prompt.
- `versionCode` 12 → 13, `versionName` 3.0.0 → 3.1.0.
- `:app/build.gradle` adds `androidx.security:security-crypto:1.1.0-alpha06`.

### Not changed (intentionally)
- The v3.0.0 Compose UI, Hilt graph, Room schema (v4), DataStore keys,
  WorkManager scheduler, foreground service, accessibility service,
  `TfLiteNsfwClassifier`, all use-cases, all repositories.
- No new bugs from the v2.x codebase were ported — every legacy file
  was rewritten against the new architecture rather than copied.

## [3.0.0] — 2026-05-10

### Added
- **Real on-device TFLite NSFW classifier is now the default.** The Hilt
  binding in `RepositoryModule` switched from `StubNsfwClassifier` to
  `TfLiteNsfwClassifier`. The stub remains in source as a test double but
  is no longer wired into the production graph. The real classifier still
  handles a missing model file gracefully — when `assets/nsfw_v1.tflite`
  is absent it returns `SAFE` deterministically.
- **CI bundles the model at build time.** New `Download NSFW model` step in
  `.github/workflows/build-debug.yml` reads `NSFW_MODEL_URL` from repo
  secrets and `curl`s the file into `app/src/main/assets/nsfw_v1.tflite`
  before Gradle runs. The artifact is renamed to
  `Dogs-of-KAHAF-v3.0.0-${run_number}` and the build summary now reports
  APK size + whether the model was bundled.
- **GPU delegate** (`org.tensorflow:tensorflow-lite-gpu:2.14.0`) with
  graceful fallback chain GPU → NNAPI → CPU.
- **Model warm-up** — one dummy inference after load to pre-JIT kernels.
- **2-class model auto-detection.** The classifier now reads the output
  tensor shape and supports both `[1,4]` (SAFE/NATURAL/SUGGESTIVE/EXPLICIT)
  and `[1,2]` (SFW/NSFW) softmax layouts; the latter is mapped to the
  4-tier severity scale via documented thresholds.
- **Configurable input normalization.** `AiSettings.modelInputNormalized`
  toggles a `NormalizeOp(0f, 255f)` in the image pre-processor for models
  trained on `[0,1]` floats. Exposed in the Detection Settings screen.
- **Min image size threshold** — frames smaller than
  `AiSettings.minImageSize` (default 120 px, slider 50–500) skip
  inference and return SAFE.
- **Settings PIN lock.** New `PinManager` (SHA-256), `PinEntryDialog` (with
  shake animation, 3-attempt lockout + 30 s cooldown), `PinSetupDialog`
  (two-step entry with mismatch detection). Settings navigation from the
  dashboard is now PIN-gated when enabled. PIN hash is **not** included in
  configuration export.
- **Browser domain blocking.** New `DomainRule` model, `domain_rules` Room
  table (schema bumped to v4 with `MIGRATION_3_4`), `DomainRepository*`,
  `ScanUrlForDomainUseCase`, `DomainsScreen` + `DomainsViewModel`. Wired
  into `GuardianAccessibilityService`: when the foreground app is a known
  major browser, collected text is matched against the user's domain list
  in addition to the keyword scan.
- **Today's Activity card** on the Dashboard — segmented bar (drawn with
  `Canvas`, no chart library) showing block counts per `BlockReason`,
  plus a top-3 list of most-blocked apps.
- **Block overlay improvements** — pulsing shield icon
  (`Animatable` 1f→1.1f infinite reverse), blocked-app launcher icon
  resolved via `PackageManager`, live `Locked for X min Y sec` countdown
  for `AUTO_LOCK`, `Blocked until HH:MM` for `SCHEDULE`, and a hardened
  back-gesture override via `OnBackPressedDispatcher`.

### Changed
- `AiSettingsScreen` no longer shows the engine toggle (stub/real). The
  card is replaced by a **Detection Engine** card showing model status
  (Loaded / Not found — safe fallback), the min-image-size slider, the
  input-normalization toggle, and a heuristic toggle.
- `AppSettings` gained `settingsPinHash` and `settingsPinEnabled`.
- `AiSettings` removed `engine` (no longer user-selectable) and gained
  `heuristicEnabled`, `minImageSize`, `modelInputNormalized`.
- `BlockEventRepository` gained `getBlocksByReason(sinceMs)` and
  `getTopBlockedApps(sinceMs, limit)` for dashboard statistics.
- `DashboardViewModel` exposes `appSettings`, `verifyPin(pin)`,
  `blocksByReasonToday`, and `topBlockedAppsToday`.
- Settings export schema bumped to `version = 2`. v1 snapshots still
  import correctly thanks to `ignoreUnknownKeys = true`.

### Database
- Schema **v3 → v4**: added `domain_rules` table.
  Migration is idempotent (`CREATE TABLE IF NOT EXISTS`).

### Build
- `versionCode 11 → 12`, `versionName 2.1.8 → 3.0.0`.
- New dependency: `org.tensorflow:tensorflow-lite-gpu:2.14.0`.
- No new runtime permissions added — still **no `INTERNET`**.

## [2.1.8] — 2026-05-10

### Fixed (build-breakers)
- **Re-added the missing `app/build.gradle`.** Earlier zips shipped only
  `app/proguard-rules.pro` and the `src/` tree under `app/`, with no module
  build script. Gradle therefore did not register `:app` as an Android
  application module and CI failed with:

  > Task 'assembleDebug' not found in root project 'GuardianShield' and its
  > subprojects.

  The new module script declares the AGP 8.5.2 / Kotlin 1.9.24 / Hilt 2.52
  / KSP / Compose BOM 2024.06.00 / Room 2.6.1 / DataStore / WorkManager /
  TFLite stack, plus all unit-test deps (`mockk`, `turbine`,
  `kotlinx-coroutines-test`).

### Fixed (latent runtime bugs found during the audit)
- `SettingsDataStore.readAppOnce()` / `readAiOnce()` were calling
  `dataStore.edit { … }` purely to read — that triggered a needless write to
  Preferences on every read. Replaced with a single `decodeApp(p)` /
  `decodeAi(p)` call inside the same `edit{}` block used by `updateApp` /
  `updateAi`, eliminating the spurious-write bug *and* the read-then-write
  TOCTOU race.
- `BlockEventRepositoryImpl.observeBlocksTodayCount()` captured
  `startOfTodayMs()` once at flow construction, so the dashboard counter
  went stale after midnight. Re-implemented with a 1-minute ticker so the
  "today" boundary always advances correctly.
- `ServiceModule.provideSettingsDataStore` collided with
  `SettingsDataStore`'s own `@Inject` constructor (Hilt would have failed
  with a duplicate-binding error). Removed the `@Provides` and added
  `@ApplicationContext` to the constructor parameter.
- `AndroidManifest.xml` referenced `@mipmap/ic_launcher` for `roundIcon`
  while no round drawable existed; added a dedicated
  `mipmap-anydpi-v26/ic_launcher_round.xml`.
- The single adaptive icon was placed in `mipmap-hdpi/`. Moved it to
  `mipmap-anydpi-v26/` (the correct location for adaptive icons; min SDK is
  26 so no PNG fallback is required).
- `values/themes.xml` redeclared `xmlns:tools` on a child `<item>`. Hoisted
  the namespace to the root `<resources>` element to avoid lint noise.

### Notes
- The `INTERNET` permission is still absent from the manifest — verified.
- `data_extraction_rules.xml` and `backup_rules.xml` continue to exclude all
  app data from cloud backup and device transfer.

---

## [2.1.7] — 2026-05-09

### Added
- Source-based 15-minute auto-lock: when the AI classifier confirms `EXPLICIT`
  content inside a configured "content-source" app (Facebook, Instagram, X,
  TikTok, Reddit, Pinterest), that package is locked for 15 minutes. The
  lock-until timestamp is persisted in Room (`AppLockEntity`) so it survives
  process death.
- Tiered NSFW classifier output (`SAFE` / `NATURAL` / `SUGGESTIVE` /
  `EXPLICIT`) with anti-false-positive debouncing: requires N consecutive
  `EXPLICIT` frames inside a configurable window before action.
- Per-app threshold boost map for "heavy image" surfaces.
- `StubNsfwClassifier` as the default binding so the build is green even
  without a real `nsfw_v1.tflite` model bundled.
- `TimedBlockManager` exposing `StateFlow<Set<String>>` of currently-blocked
  packages; recomputed on every Schedule change, every WorkManager tick and
  every `MinuteTickReceiver` AlarmManager wake-up.
- Export / import configuration as JSON from the Settings screen.
- Compose Material 3 theme with dynamic-color support on Android 12+.
- Unit tests for every ViewModel (MockK + Turbine).
- GitHub Actions workflow building debug APKs and running unit tests.

### Changed
- Migrated entire UI to Jetpack Compose (single-Activity + Compose Navigation,
  no Fragments).
- Foreground service now uses `foregroundServiceType="specialUse"` with a
  `<property>` tag justifying the use-case for Android 14+.
- `AccessibilityService` event filter narrowed to
  `TYPE_WINDOW_STATE_CHANGED | TYPE_VIEW_TEXT_CHANGED |
  TYPE_WINDOW_CONTENT_CHANGED`. Text scans are coalesced with a 250 ms
  debounce.
- Database upgraded to schema v3 with idempotent `MIGRATION_1_2` and
  `MIGRATION_2_3`. Schema is exported to `app/schemas/`.

### Fixed
- Eliminated all `.distinctUntilChanged` / `.conflate` / `.debounce` /
  `.sample` / `.flowOn` invocations on `StateFlow` / `MutableStateFlow` —
  these are hard compile errors in coroutines 1.8+.
- Removed every wildcard import from `kotlinx.coroutines.flow.*` so the
  deprecated overloads are no longer pulled in.
- API-26+ Vibrator calls isolated into dedicated helpers to avoid `NewApi`
  lint failures.
- Activities with Compose state guard `onResume` with a `bindingReady` flag.

### Security / Privacy
- The manifest no longer declares the `INTERNET` permission. Verified with
  `grep`. The application is now provably 100% on-device.
- `data_extraction_rules.xml` and `backup_rules.xml` exclude all app data
  from cloud backup and device transfer.
