# Guardian Shield

> Internal codename: **Dogs of KAHAF**
> Privacy-first, 100% on-device content protection for Android.

Guardian Shield is a native Android application written entirely in Kotlin that
enforces app-level rules, keyword filters, time-based schedules, browser
domain blocking, and an on-device NSFW classifier — all without sending a
single byte over the network. The application has **no `INTERNET` permission**
declared in its manifest. By design.

| Property              | Value                |
|-----------------------|----------------------|
| Package               | `com.kahaf.guardianshield` |
| versionCode           | 12                   |
| versionName           | 3.0.0                |
| Min SDK / Target SDK  | 26 / 35              |
| JVM target            | 17                   |
| Language              | 100% Kotlin          |
| UI                    | Jetpack Compose + Material 3 |
| Architecture          | Clean Architecture + MVVM   |
| DI                    | Hilt                 |
| DB                    | Room (schema v4)     |
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

## Bundling the NSFW model

Starting with v3.0.0 the real on-device TFLite classifier
(`TfLiteNsfwClassifier`) is the **default** binding in `RepositoryModule`. The
classifier loads `app/src/main/assets/nsfw_v1.tflite` lazily; if the asset is
missing it returns `SAFE` deterministically and the build still works.

Because the model is binary (and may be license-restricted) the repo does
**not** commit it. Instead, CI fetches it at build time:

1. Obtain a compatible TFLite model:
   - **4-class** (preferred): `float32 [1,224,224,3] → [1,4]`
     softmax = `[SAFE, NATURAL, SUGGESTIVE, EXPLICIT]`, **or**
   - **2-class**: `float32 [1,224,224,3] → [1,2]` softmax = `[SFW, NSFW]`.
     The classifier auto-detects 2-output models and maps the NSFW score to
     the four severity tiers (see `TfLiteNsfwClassifier.kt`).
2. Upload the file as an asset to a GitHub Release in this repo
   (e.g. tag `model-v1`).
3. Add a repo secret in **Settings → Secrets and variables → Actions**:

   ```
   Name:  NSFW_MODEL_URL
   Value: https://github.com/<owner>/<repo>/releases/download/model-v1/nsfw_v1.tflite
   ```
4. Push to `main`/`master`/`dev`. The CI workflow downloads the model into
   `app/src/main/assets/` before invoking Gradle, so the bundled APK ships
   with the model embedded.

If the secret is unset the build still passes — the classifier falls back to
SAFE.

### Input normalization

Some public NSFW models expect inputs in `[0,1]` (after dividing by 255),
others expect raw `[0,255]`. The default is raw. Toggle
**Detection Settings → Input normalization** in the app to switch — no
recompile required.

### Recommended public models

- 2-class quantized MobileNetV2:
  `https://huggingface.co/s0md3v/ufal-nsfw-classifier/resolve/main/model.tflite`
- Or any custom 4-class model that matches the spec above.

---

## Permissions rationale

| Permission                              | Why we need it |
|-----------------------------------------|----------------|
| `BIND_ACCESSIBILITY_SERVICE`            | Detect when a blocked package is brought to the foreground; scan visible text for keyword/domain rules. |
| `SYSTEM_ALERT_WINDOW`                   | Launch the full-screen `BlockOverlayActivity` over offending apps. |
| `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_SPECIAL_USE` | Keep the protection pipeline alive under Doze / OEM killers. |
| `RECEIVE_BOOT_COMPLETED`                | Resume protection after reboot via `BootReceiver`. |
| `POST_NOTIFICATIONS`                    | Show the persistent foreground-service notification on Android 13+. |
| `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`  | Optional — recommended so the foreground service is not killed in Doze. |
| `QUERY_ALL_PACKAGES`                    | Enumerate installed user apps for the App List screen. |
| `PACKAGE_USAGE_STATS` (optional)        | Helpful as a fallback foreground detector on certain OEMs. |
| `VIBRATE`                               | Short haptic when the block overlay appears. |

**No `INTERNET` permission**. Verify with:

```bash
grep -R "android.permission.INTERNET" app/src/main/AndroidManifest.xml
# (empty)
```

---

## Privacy

- 100% on-device processing. No telemetry, no analytics SDK, no crash
  reporter that uploads.
- `data_extraction_rules.xml` and `backup_rules.xml` exclude all app data from
  cloud backup and device-transfer.
- The TFLite NSFW model (`assets/nsfw_v1.tflite`) is loaded from local assets
  and inference runs through GPU → NNAPI → CPU delegate fallback chain.
- The Settings PIN is stored as SHA-256 of the 4-digit PIN inside the local
  DataStore — never exported by the **Export configuration** feature.

---

## Feature map → source

| Spec | Implementation |
|------|----------------|
| F1 — App-level rules     | `AppRuleRepository*`, `AppListScreen`, `AppRuleEntity` |
| F2 — Keyword filter      | `KeywordRepository*`, `ScanTextForKeywordsUseCase`, `KeywordsScreen` |
| F3 — Time schedules      | `ScheduleRepository*`, `TimedBlockManager`, `MinuteTickReceiver`, `ScheduleRecomputeWorker` |
| F4 — AI NSFW (TFLite)    | `NsfwClassifier`, `TfLiteNsfwClassifier`, `AnalyzeFrameUseCase` |
| F5 — 15-minute auto-lock | `AppLockEntity`, `AppLockRepository*`, `AutoLockSourceAppUseCase`, `EvaluateForegroundAppUseCase` |
| F6 — Browser domain block (v3.0.0) | `DomainRule`, `DomainRepository*`, `ScanUrlForDomainUseCase`, `DomainsScreen` |
| F7 — Settings PIN (v3.0.0)         | `PinManager`, `PinEntryDialog`, `PinSetupDialog` |

---

## Architecture

```
presentation/  Compose screens + Hilt-injected ViewModels
domain/        UseCases, pure-Kotlin models, repository interfaces
data/          Room DAOs, DataStore, repository impls, TFLite wrapper, PinManager
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

- **Never** call `.distinctUntilChanged()`, `.conflate()`, `.debounce()`,
  `.sample()` or `.flowOn()` directly on a `StateFlow` / `MutableStateFlow`.
  Always upcast to `Flow<T>` first.
- **No wildcard imports** from `kotlinx.coroutines.flow.*`.
- Any class using `flatMapLatest` / `mapLatest` / `transformLatest` is
  annotated with `@OptIn(ExperimentalCoroutinesApi::class)`.
- The `AccessibilityService` filters event types tightly, debounces text
  scans by 250ms, and wraps every callback in `try/catch + Log.e`.
- Foreground service uses `foregroundServiceType="specialUse"` with the
  `<property>` tag in the manifest.
- API-26+-only Vibrator calls are isolated in dedicated `vibrateOreo()`
  helpers to avoid `NewApi` lint failures.
- Activities that touch Compose / view state in `onResume` guard with a
  `bindingReady` flag.
- Room migrations are idempotent (`CREATE TABLE IF NOT EXISTS`); we never
  use `fallbackToDestructiveMigration()`.

---

## CI

GitHub Actions workflow at `.github/workflows/build-debug.yml`:
- triggers on push to `main` / `master` / `dev` and `workflow_dispatch`
- regenerates `gradle-wrapper.jar` (which is `.gitignore`d), accepts SDK
  licenses, downloads the NSFW model from `NSFW_MODEL_URL` (if set), runs
  `./gradlew assembleDebug`
- uploads the debug APK as `Dogs-of-KAHAF-v3.0.0-${{ github.run_number }}`

---

## License

This repository is private and intended for internal distribution only.
