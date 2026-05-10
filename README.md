# Guardian Shield

> Internal codename: **Dogs of KAHAF**
> Privacy-first, 100% on-device content protection for Android.

Guardian Shield is a native Android application written entirely in Kotlin that
enforces app-level rules, keyword filters, time-based schedules and an
on-device NSFW classifier — all without sending a single byte over the
network. The application has **no `INTERNET` permission** declared in its
manifest. By design.

| Property              | Value                |
|-----------------------|----------------------|
| Package               | `com.kahaf.guardianshield` |
| versionCode           | 11                   |
| versionName           | 2.1.8                |
| Min SDK / Target SDK  | 26 / 35              |
| JVM target            | 17                   |
| Language              | 100% Kotlin          |
| UI                    | Jetpack Compose + Material 3 |
| Architecture          | Clean Architecture + MVVM   |
| DI                    | Hilt                 |
| DB                    | Room                 |
| Async                 | Coroutines + Flow    |

---

## Build

Prerequisites:
- JDK 17 (Temurin recommended)
- Android SDK with platform 35 + build-tools 35.0.0

Side-loaded debug APK:

```bash
git clone <your-fork>
cd GuardianShield
cp local.properties.template local.properties
# edit local.properties to set sdk.dir
./gradlew clean assembleDebug
```

The output APK lives at `app/build/outputs/apk/debug/app-debug.apk`.

---

## Permissions rationale

| Permission                              | Why we need it |
|-----------------------------------------|----------------|
| `BIND_ACCESSIBILITY_SERVICE`            | Detect when a blocked package is brought to the foreground; scan visible text for keyword rules. |
| `SYSTEM_ALERT_WINDOW`                   | Launch the full-screen `BlockOverlayActivity` over offending apps. |
| `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_SPECIAL_USE` | Keep the protection pipeline alive under Doze / OEM killers. The `<property>` tag in the manifest documents the special-use justification. |
| `RECEIVE_BOOT_COMPLETED`                | Resume protection after reboot via `BootReceiver`. |
| `POST_NOTIFICATIONS`                    | Show the persistent foreground-service notification on Android 13+. |
| `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`  | Optional — recommended so the foreground service is not killed in Doze. |
| `QUERY_ALL_PACKAGES`                    | Enumerate installed user apps for the App List screen. Justified in the Play listing as a parental/content-protection tool. |
| `PACKAGE_USAGE_STATS` (optional)        | Helpful as a fallback foreground detector on certain OEMs. |
| `VIBRATE`                               | Short haptic when the block overlay appears. |

**No `INTERNET` permission**. Verify with:

```bash
grep -R "android.permission.INTERNET" app/src/main/AndroidManifest.xml
# (empty)
```

---

## Privacy

- 100% on-device processing. No telemetry, no analytics SDK, no crash reporter
  that uploads.
- `data_extraction_rules.xml` and `backup_rules.xml` exclude all app data from
  cloud backup and device-transfer.
- The TFLite NSFW model (`assets/nsfw_v1.tflite`) is loaded from local assets;
  inference runs through NNAPI/CPU. The default binding is the
  `StubNsfwClassifier` (deterministic `SAFE`) so the build is green even
  without a real model. Switch to `TfLiteNsfwClassifier` in
  `di/RepositoryModule.kt` to use a real model.

---

## Feature map → source

| Spec | Implementation |
|------|----------------|
| F1 — App-level rules     | `AppRuleRepository*`, `AppListScreen`, `AppRuleEntity` |
| F2 — Keyword filter      | `KeywordRepository*`, `ScanTextForKeywordsUseCase`, `KeywordsScreen` |
| F3 — Time schedules      | `ScheduleRepository*`, `TimedBlockManager`, `MinuteTickReceiver`, `ScheduleRecomputeWorker` |
| F4 — AI NSFW (TFLite)    | `NsfwClassifier`, `TfLiteNsfwClassifier`, `StubNsfwClassifier`, `AnalyzeFrameUseCase` |
| F5 — 15-minute auto-lock | `AppLockEntity`, `AppLockRepository*`, `AutoLockSourceAppUseCase`, `EvaluateForegroundAppUseCase` |

---

## Architecture

```
presentation/  Compose screens + Hilt-injected ViewModels
domain/        UseCases, pure-Kotlin models, repository interfaces
data/          Room DAOs, DataStore, repository impls, TFLite wrapper
service/       AccessibilityService, ForegroundService, BlockOverlayActivity,
               BootReceiver, MinuteTickReceiver, WorkManager workers,
               TimedBlockManager
di/            Hilt modules (DatabaseModule, RepositoryModule, ServiceModule)
```

Single-Activity (`MainActivity`) + Compose Navigation. No Fragments anywhere.

---

## Testing

```bash
./gradlew testDebugUnitTest
```

Unit tests cover every ViewModel using **MockK** + **Turbine** +
`kotlinx-coroutines-test`. See `app/src/test/`.

---

## Code-quality conventions enforced in this codebase

These are the rules that bit us during earlier iterations and now must remain
true forever:

- **Never** call `.distinctUntilChanged()`, `.conflate()`, `.debounce()`,
  `.sample()` or `.flowOn()` directly on a `StateFlow` / `MutableStateFlow`.
  Always upcast to `Flow<T>` first. Coroutines 1.8+ makes the direct call a
  hard compile error. See `TimedBlockManager` for the canonical pattern.
- **No wildcard imports** from `kotlinx.coroutines.flow.*`. They pull in the
  deprecated overloads. Every flow operator is imported explicitly.
- Any class using `flatMapLatest` / `mapLatest` / `transformLatest` is annotated
  with `@OptIn(ExperimentalCoroutinesApi::class)`.
- The `AccessibilityService` filters event types tightly, debounces text scans
  by 250ms, and wraps every callback in `try/catch + Log.e`.
- Foreground service uses `foregroundServiceType="specialUse"` with the
  `<property>` tag in the manifest.
- API-26+-only Vibrator calls are isolated in dedicated `vibrateOreo()` helpers
  to avoid `NewApi` lint failures.
- Activities that touch Compose / view state in `onResume` guard with a
  `bindingReady` flag.

---

## CI

GitHub Actions workflow at `.github/workflows/build-debug.yml`:
- triggers on push to `main` / `master` / `dev` and `workflow_dispatch`
- regenerates `gradle-wrapper.jar` (which is `.gitignore`d), accepts SDK
  licenses, runs `./gradlew assembleDebug` and `testDebugUnitTest`
- uploads the debug APK as `GuardianShield-debug-${{ github.run_number }}`

---

## License

This repository is private and intended for internal distribution only.
