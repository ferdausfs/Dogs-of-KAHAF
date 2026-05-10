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
| versionCode           | 16                   |
| versionName           | 3.1.3                |
| Min SDK / Target SDK  | 26 / 35              |
| JVM target            | 17                   |
| Language              | 100% Kotlin          |
| UI                    | Jetpack Compose + Material 3 |
| Architecture          | Clean Architecture + MVVM   |
| DI                    | Hilt                 |
| DB                    | Room (schema v4)     |
| Async                 | Coroutines + Flow    |

## ⚠ v3.1.3 — CRITICAL "AI doesn't detect NSFW" fix

v3.1.3 fixes a chain of three bugs that, together, caused user reports of
*"the app does NSFW detection but never actually blocks anything"*:

1. **`SettingsDataStore.modelInputNormalized` default flipped `false → true`.**
   v3.1.2 had already flipped the `AiSettings` data-class default, but the
   DataStore default WINS at decode time, so the pre-processor was still
   sending raw `[0,255]` pixels into MobileNetV2-class models that expect
   `[0,1]`. Result: every frame scored near-zero → SAFE → nothing blocked.
2. **`AnalyzeFrameUseCase` sensitivity slider was a no-op above ~0.20.**
   The block decision required BOTH `label == EXPLICIT` (which for 2-class
   models hard-cuts at `nsfw ≥ 0.80`) AND a threshold check. The label gate
   always fired first, so the slider didn't matter. Now: EITHER the label
   says EXPLICIT, OR the score crosses the user's effective threshold.
3. **AI scan was skipped on most apps by default.** The default content
   source list was 7 social apps, so browsers / gallery / messengers / file
   managers were never scanned. The list now defaults to ~45 packages and
   `runAiScanFor` auto-allows any `AppClassifier.isContentSourceApp()` pkg
   even when the user list is empty.

No schema changes. No new permissions. See [CHANGELOG.md](CHANGELOG.md) for
the full bug-fix list.

## v3.1.2 — Full code-review / build-green release

v3.1.2 is a full code-review pass on v3.1.1:

- **CI is green again**: the `SchedulesViewModelTest > upsert delegates…`
  failure that broke `./gradlew testDebugUnitTest` for the whole project
  is fixed. (Root cause: `TimedBlockManager.recompute()` has a default-arg
  `Long`, so MockK's no-arg `verify` couldn't match the synthetic-bridge
  call into `recompute(Long)`. Verification now uses `verify { timed.recompute(any()) }`.)
- **Every Kotlin compiler warning surfaced by CI is now gone** — see
  [CHANGELOG.md](CHANGELOG.md) for the per-file list.
- **Dashboard counters no longer thrash the DB once a minute** —
  `distinctUntilChanged` on the today-rollover ticker means the DAO Flow
  is only re-subscribed when the day actually rolls over.

No behaviour changes. No schema changes. No new permissions.

## v3.1.1 — Critical NSFW-detection fix

If you imported a custom `.tflite` model in v3.1.0 and noticed the AI
overlay never fires, **that wasn't your model — it was a load-path bug
in the classifier**. v3.0.0/v3.1.0's `TfLiteNsfwClassifier` only ever
read `assets/nsfw_v1.tflite`, so user-imported models (saved to
`filesDir/nsfw_model.tflite` by `ModelImportManager`) were silently
ignored, and the SAFE deterministic fallback was used instead.

v3.1.1 fixes this end-to-end:
1. The classifier now resolves models in priority order — custom →
   bundled → SAFE.
2. The Detection Settings screen surfaces which model is live ("Active:
   custom imported model" / "Active: bundled model" / "Model not found —
   safe fallback active").
3. Importing or deleting a custom model triggers a hot-reload of the
   live `Interpreter`, so changes take effect immediately — no need to
   force-stop the app.

See [CHANGELOG.md](CHANGELOG.md) for the full bug-fix list.

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
(`TfLiteNsfwClassifier`) is the **default** binding in `RepositoryModule`.

The classifier resolves a model in this order (first-found wins):

1. **`filesDir/nsfw_model.tflite`** — user-imported via the
   *Detection Settings → Import custom NSFW model* button (Storage
   Access Framework).
2. **`assets/nsfw_v1.tflite`** — fetched and bundled at build time by CI
   (see below).
3. **SAFE deterministic fallback** — if neither file exists, the app
   still installs and runs, but no AI block fires until a model is
   provided.

### Option A — bundle the model at build time (recommended for CI)

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

### Option B — let the user import their own model at runtime

In the app: **Detection Settings → Import custom NSFW model (.tflite)**.
The picker accepts `*/*` because Android's SAF doesn't always expose a
`.tflite` MIME type. The file is:

1. Atomically copied to `filesDir/nsfw_model.tflite.tmp`.
2. Validated against the TFLite `"TFL3"` magic header.
3. Opened in a throwaway `Interpreter` to confirm it's not corrupt.
4. Renamed to `nsfw_model.tflite` only on success.
5. The classifier is hot-reloaded — the new model is live immediately.

### Input normalization

Some public NSFW models expect inputs in `[0,1]` (after dividing by 255),
others expect raw `[0,255]`. The default is raw. Toggle
**Detection Settings → Input normalization** in the app to switch — no
recompile required (the toggle now also re-loads the interpreter so any
delegate state gets fresh kernels).

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
| `BIND_DEVICE_ADMIN` (v3.1.0+)           | Optional uninstall protection via Device Admin. |
| `REQUEST_DISABLE_APP_HIBERNATION` (v3.1.0+) | Optional auto-revoke opt-out. |

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
- The TFLite NSFW model (`assets/nsfw_v1.tflite` or `filesDir/nsfw_model.tflite`)
  is loaded from local storage only and inference runs through GPU → NNAPI →
  CPU delegate fallback chain.
- The Settings PIN is stored as SHA-256 of the 4-digit PIN inside the local
  DataStore — never exported by the **Export configuration** feature.

---

## Feature map → source

| Spec | Implementation |
|------|----------------|
| F1 — App-level rules     | `AppRuleRepository*`, `AppListScreen`, `AppRuleEntity` |
| F2 — Keyword filter      | `KeywordRepository*`, `ScanTextForKeywordsUseCase`, `KeywordsScreen` |
| F3 — Time schedules      | `ScheduleRepository*`, `TimedBlockManager`, `MinuteTickReceiver`, `ScheduleRecomputeWorker` |
| F4 — AI NSFW (TFLite)    | `NsfwClassifier`, `TfLiteNsfwClassifier`, `AnalyzeFrameUseCase`, `ModelImportManager` |
| F5 — 15-minute auto-lock | `AppLockEntity`, `AppLockRepository*`, `AutoLockSourceAppUseCase`, `EvaluateForegroundAppUseCase` |
| F6 — Browser domain block (v3.0.0) | `DomainRule`, `DomainRepository*`, `ScanUrlForDomainUseCase`, `DomainsScreen` |
| F7 — Settings PIN (v3.0.0)         | `PinManager`, `PinEntryDialog`, `PinSetupDialog` |
| F8 — Uninstall protection (v3.1.0) | `GuardianDeviceAdminReceiver`, `PermissionManager.requestDeviceAdmin` |
| F9 — Reflection delay (v3.1.0)     | `DelayUnlockScreen` |

---

## Architecture

```
presentation/  Compose screens + Hilt-injected ViewModels
domain/        UseCases, pure-Kotlin models, repository interfaces
data/          Room DAOs, DataStore, repository impls, TFLite wrapper, PinManager
service/       AccessibilityService, ForegroundService, BlockOverlayActivity,
               BootReceiver, MinuteTickReceiver, WorkManager workers,
               TimedBlockManager
admin/         GuardianDeviceAdminReceiver (v3.1.0)
di/            Hilt modules (DatabaseModule, RepositoryModule, ServiceModule)
```

Single-Activity (`MainActivity`) + Compose Navigation. No Fragments anywhere.

---

## Testing

```bash
./gradlew testDebugUnitTest
```

Unit tests cover every ViewModel using **MockK** + **Turbine** +
`kotlinx-coroutines-test`. See `app/src/test/`. v3.1.1 fixes the
`OnboardingViewModelTest` compile error introduced by the
`PermissionManager.Snapshot` change in v3.1.0.

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
- All system-Settings deep-link launches are wrapped in `runCatching` (v3.1.1)
  so onboarding can't crash on stripped OEM ROMs.

---

## CI

GitHub Actions workflow at `.github/workflows/build-debug.yml`:
- triggers on push to `main` / `master` / `dev` and `workflow_dispatch`
- regenerates `gradle-wrapper.jar` (which is `.gitignore`d), accepts SDK
  licenses, downloads the NSFW model from `NSFW_MODEL_URL` (if set), runs
  `./gradlew testDebugUnitTest` then `./gradlew assembleDebug`
- uploads the debug APK as `Dogs-of-KAHAF-v3.1.2-${{ github.run_number }}`

---

## License

This repository is private and intended for internal distribution only.
