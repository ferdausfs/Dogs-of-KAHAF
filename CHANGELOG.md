# Changelog

All notable changes to Guardian Shield are documented here.
The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/)
and the project adheres to [Semantic Versioning](https://semver.org/).

## [3.1.3] — 2026-05-10  —  CRITICAL: "AI doesn't detect NSFW" fix

This release fixes a chain of bugs that, in combination, caused user reports
of "the app does not detect NSFW content even with a valid model loaded".
The pipeline was technically running — the model loaded, screenshots were
taken, inference completed — but every frame came back SAFE for one of
three reasons listed below.

### Fixed (CRITICAL — the actual root causes of "NSFW detection doesn't work")

- **`SettingsDataStore.decodeAi` defaulted `modelInputNormalized` to `false`**
  even though `AiSettings` data-class default was already flipped to `true`
  in v3.1.2. The DataStore default WINS over the data-class default at
  decode time, so on every fresh install the pre-processor sent raw
  `[0,255]` pixels into MobileNetV2-class models that expect `[0,1]`,
  producing near-zero `nsfw` scores for every frame. Default flipped to
  `true` to match the data class. Existing users with the old `false`
  preference can flip the toggle in Detection Settings.

- **`AnalyzeFrameUseCase` required BOTH `label == EXPLICIT` AND
  `score >= threshold`**, but for 2-class models the label hard-cuts at
  `nsfw ≥ 0.80`. With sensitivity `0.55` the threshold worked out to
  `0.45`, so any value of the slider above ~`0.20` was a no-op — only the
  `≥0.80` label gate ever fired. The slider is now actually wired:
  EITHER the model already says EXPLICIT, OR the score crosses the user's
  effective threshold. Stricter sensitivity now catches mid-tier frames.

- **AI scan was skipped on most apps by default.** The default content
  source list contained only 7 social apps. Anyone opening explicit
  imagery in their browser, gallery, file manager, messenger, video app,
  etc. saw NO AI scan at all. Two-pronged fix:
  1. Default `DEFAULT_CONTENT_SOURCES` expanded to ~45 packages covering
     social, messaging, video, browsers, and gallery surfaces.
  2. `GuardianAccessibilityService.runAiScanFor` also auto-allows any
     package that `AppClassifier.isContentSourceApp` recognises, so even
     fresh users with empty source lists get protection on browsers /
     messengers / galleries out of the box.

### Fixed (related)

- **`SettingsRepositoryImpl.ConfigSnapshot`** — `aiInputNormalized` default
  flipped `false` → `true`, matching the data-class default. Imports of
  v1 / v2 snapshots that omit the field no longer silently break
  detection.
- **`TfLiteNsfwClassifier.map2ClassOutput`** — `SUGGESTIVE` and
  `NATURAL` buckets now carry the real raw `nsfw` probability instead of
  `nsfw * 0.3f`. Previously the deflated SUGGESTIVE score made it
  impossible for the user's threshold to ever fire on the SUGGESTIVE
  path — sensitivity "HIGH" did nothing extra. The bucket layering is
  now: only the buckets the score actually falls into receive a non-zero
  value, mirroring the way 4-class softmax outputs work.
- **Auto-lock entry condition** in `GuardianAccessibilityService` no
  longer requires the package to ALSO appear in
  `ai.contentSourcePackages` — we already gate scan entry on either the
  user list OR the AppClassifier known-source list, so requiring it
  twice cancelled the v3.1.3 scan-coverage fix above.

### versionCode / versionName
- versionCode 16, versionName 3.1.3.

## [3.1.2] — 2026-05-10  —  Full code-review / build-green release

This release is the result of a complete code-review pass on v3.1.1. The
goal: make CI green, eliminate every Kotlin compiler warning, and apply
low-risk performance optimisations — without changing any user-visible
behaviour.

### Fixed (CRITICAL — unblocks CI)
- **`SchedulesViewModelTest › upsert delegates…` no longer fails.**
  `TimedBlockManager.recompute(nowMs: Long = System.currentTimeMillis())` has
  a default parameter, so Kotlin compiles `timedBlockManager.recompute()`
  into a call through the synthetic `recompute$default(…)` bridge into
  `recompute(Long)`. MockK's `verify { timed.recompute() }` could not match
  the actual `recompute(Long)` invocation, so the assertion always failed.
  The verification now uses `verify { timed.recompute(any()) }` to match the
  real signature. CI's `./gradlew testDebugUnitTest` is green again.

### Fixed (compiler warnings — every warning from the v3.1.1 build is now gone)
- **`SettingsRepositoryImpl.importJson`** now uses the parameter name `json`
  (matching the `SettingsRepository` supertype). The internal `Json { … }`
  codec was renamed to `jsonCodec` to avoid the shadowing collision. Resolves
  the `parameter name differs from supertype` warning.
- **`DashboardScreen`**: removed the unused `val scope = rememberCoroutineScope()`
  (and its `kotlinx.coroutines.launch` / `rememberCoroutineScope` imports) —
  nothing in the composable was launching a coroutine.
- **`SettingsScreen`**: `onRequestReflectionDelay` is preserved on the public
  signature (the `DELAY_UNLOCK` route is still wired in `GuardianNavHost`)
  but is intentionally suppressed with `@Suppress("UNUSED_PARAMETER")` until
  the reflection-delay gating is wired into the "disable protection" /
  "remove PIN" flows.
- **`GuardianAccessibilityService.runAiScanFor`**: capture the screenshot
  into a final local `val captured: Bitmap` before passing it into the
  suspending `withContext { … }` block. Eliminates the `bitmap!!` non-null
  assertion warning (Kotlin can't smart-cast a `var` across a lambda boundary).

### Performance
- **`BlockEventRepositoryImpl.observeBlocksTodayCount()`** now
  `distinctUntilChanged()`s the minute-tick source, so the DAO Flow is no
  longer torn down and re-subscribed every 60 seconds when the day hasn't
  rolled over. Previously the dashboard counter triggered a fresh DB query
  every single minute even when nothing had changed.
- **`DashboardViewModel.todayTicker`** likewise gets `distinctUntilChanged()`
  so `blocksByReasonToday` and `topBlockedAppsToday` (both `flatMapLatest`-ed
  on the same ticker) stop tearing down and re-subscribing once a minute.
  Net effect: the Dashboard idles at one DAO subscription per metric for the
  whole day, swapping only when the day actually rolls over.

### Changed
- `versionCode` 14 → 15, `versionName` 3.1.1 → 3.1.2.
- CI artifact name bumped to `Dogs-of-KAHAF-v3.1.2-${run_number}` to match.

### Not changed (intentionally)
- DB schema is still v4. No new entities, no new migrations.
- Hilt graph wiring, Compose UI, DataStore keys, WorkManager, accessibility
  service event filter, foreground service type — all unchanged.
- The custom-model load-path priority chain shipped in v3.1.1
  (filesDir/nsfw_model.tflite → assets/nsfw_v1.tflite → SAFE fallback)
  is unchanged.
- Still **no `INTERNET` permission**. App is still 100% on-device.

## [3.1.1] — 2026-05-10  —  Critical bug-fix release

### Fixed (CRITICAL — root cause of "AI doesn't detect / block NSFW")
- **`TfLiteNsfwClassifier` now actually reads the user-imported model.**
  The previous code only ever loaded `assets/nsfw_v1.tflite`, but
  `ModelImportManager` saves user-picked models to
  `filesDir/nsfw_model.tflite`. Result: every user who imported their own
  model got the SAFE deterministic fallback forever — no detection, no
  blocking. The classifier now resolves models in this priority order:
    1. `filesDir/nsfw_model.tflite`  (user-imported via SAF)
    2. `assets/nsfw_v1.tflite`       (CI-bundled at build time)
    3. SAFE deterministic fallback   (keeps the build green)
- **Classifier hot-reload after import / delete.** `AiSettingsViewModel`
  now calls `classifier.reload()` after a successful import or delete, so
  the new model takes effect immediately instead of after the next
  process restart.
- **AI Settings screen now shows model provenance** — distinct messages
  for "Active: custom imported model", "Active: bundled model", and
  "Model not found — safe fallback active".

### Fixed
- **Unit tests no longer compile-fail.** `OnboardingViewModelTest` was
  still constructing the v3.0.0 `PermissionManager.Snapshot` (without the
  new `deviceAdmin` and `autoRevokeDisabled` fields added in v3.1.0). All
  Snapshot call-sites in tests updated.
- **Mipmap launcher icons regenerated at proper density sizes.**
  Previous zip shipped the same 48×48 PNG in *every* density bucket
  (mdpi/hdpi/xhdpi/xxhdpi/xxxhdpi), so the launcher icon was blurry on
  high-DPI devices. Each bucket now ships its correct size:
  48 / 72 / 96 / 144 / 192.
- **`PinEntryDialog`** had a leftover `Text(stringResource(R.string.set_theme_system).let { "Cancel" })`
  that ignored the resource entirely and hard-coded "Cancel". Replaced
  with a localised `R.string.common_cancel`. Same fix applied to other
  dialogs that hard-coded "Cancel" / "Close".
- **`PermissionManager` deep-links** are all now wrapped in `runCatching`
  so onboarding can't crash on stripped-down OEM ROMs (MIUI / older EMUI)
  that don't ship the `Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`
  activity. Fallback to the generic battery-optimisation screen included.
- **Battery drain on no-model builds.** `GuardianAccessibilityService`
  now skips the screenshot + decode pipeline entirely when
  `classifier.isModelLoaded == false`. Previously it took a screenshot
  every 850ms, decoded ARGB_8888, then returned SAFE — pure waste.
- **`GuardianAccessibilityService` per-package throttle** now tracks the
  periodic-loop's timestamps too, so a content-change event landing
  inside the periodic window can't fire a back-to-back second screenshot.

### Added
- `R.string.common_cancel` / `R.string.common_close` shared strings.
- `ai_model_status_custom`, `ai_model_status_bundled`, `ai_import_help`
  user-facing strings.
- `TfLiteNsfwClassifier.modelSource: StateFlow<ModelSource>` so the UI
  can show which model is live.
- `TfLiteNsfwClassifier.reload()` for in-place re-load after a custom
  import / delete.

### Changed
- `versionCode` 13 → 14, `versionName` 3.1.0 → 3.1.1.
- ProGuard / R8 rules tightened: keep all `kotlinx.serialization`
  generated `$$serializer` classes + `Companion` members, otherwise
  release builds silently drop the Export/Import configuration codec.
- Also injected `TfLiteNsfwClassifier` into `GuardianAccessibilityService`
  so it can short-circuit the AI loop on no-model builds.

### CI
- The artifact name in `build-debug.yml` was still
  `Dogs-of-KAHAF-v3.0.0-…`; bumped to `Dogs-of-KAHAF-v3.1.1-…` so the
  filename matches `versionName`.
- Re-enabled `./gradlew testDebugUnitTest` in the CI step (was broken by
  the `OnboardingViewModelTest` compile error).

### Not changed (intentionally)
- Schema is still v4. No new entities, no new migrations.
- All Hilt graph wiring, Compose UI, DataStore keys, WorkManager
  scheduling, AccessibilityService event filter set, foreground service
  type — unchanged.
- Still **no `INTERNET` permission**. App is still 100% on-device.

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
