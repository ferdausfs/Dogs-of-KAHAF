# Changelog

All notable changes to Guardian Shield are documented here.
The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/)
and the project adheres to [Semantic Versioning](https://semver.org/).

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
