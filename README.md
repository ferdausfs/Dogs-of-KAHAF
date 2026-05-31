# Guardian Shield 🛡️

**Package:** `com.guardian.shield` · **Min SDK:** 26 (Android 8.0) · **Target SDK:** 35 · **Version:** 2.3.0

An Android parental/self-control app using `AccessibilityService` to block harmful content on-device.

---

## v2.3.0 — Major Stability & Modernization Update

### 🚨 Critical AI Detection Stability Fixes (priority)

The v2.2.0 build had a regression where AI detection became silent / inconsistent. Root cause was traced to **multiple interacting bugs**, all fixed in this build:

| # | Bug | Fix |
|---|-----|-----|
| 1 | `isBlockingInProgress` flag could get stuck `true` if `blockingEngine.block()` failed silently → all further AI scans skipped | Watchdog coroutine auto-clears the flag if held > 10 s; centralized `setBlockingFlag()` / `clearBlockingFlag()` helpers with timestamps |
| 2 | `aiScanMap` throttle entries were left in place after screenshot failure → next 3 s of scans suppressed for that package | `clearAiThrottleEntry()` called on `onFailure` and on exceptions |
| 3 | Repeated screenshot failures could spiral; no back-off | `screenshotFailStreak` counter + brief back-off after 5 consecutive failures, with periodic decay |
| 4 | When an IME (keyboard) popped on top of a target app, `handleWindowChange` was clearing `currentPackage`, so the periodic scanner stopped | Safe packages now only fully reset state when reaching the actual launcher; IMEs / system dialogs are transient and leave `currentPackage` intact |
| 5 | GPU delegate hardware failures could silently make every inference return junk on some devices | `runInferenceSafe()` catches all exceptions; after 3 consecutive failures, `rebuildAllOnCpu()` forces CPU-only fallback |
| 6 | `handleContentChange` didn't refresh `currentPackage`, so if an app emitted only `CONTENT_CHANGED` events the scanner had no target | `handleContentChange` now keeps `currentPackage` in sync |

After this build the detection should run reliably across screen rotations, app switches, keyboard popups, and on devices with broken Vulkan / GPU drivers.

### 📱 Phase 4 — Onboarding Flow (added)
- 4-screen welcome (Welcome → Features → Permissions → PIN intro)
- ViewPager2 with dot indicators, Skip / Back / Next
- Runs only on first launch (`firstRun` flag in DataStore)
- After completion, smart-routes to Permissions or Dashboard

### 🔒 Phase 5 — Anti-Uninstall (added)
- `UninstallProtection.kt`: AccessibilityService watches `com.android.settings`, MIUI Security, Samsung Smart Manager, etc. When the Guardian Shield app-info page is visible and an Uninstall / Force-stop / Disable / Clear-data button is present, the user is bounced to Home.
- `TamperLogger.kt`: high-priority notification fires for every detected attempt.
- Multi-language strings ("Uninstall", "আনইনস্টল", "卸载", "停用").
- Existing Device Admin receiver remains the primary defense; this layer adds a soft barrier even before Device Admin engages.

### 📊 Phase 1 — Activity Log Screen (added)
- New `ActivityLogActivity` accessible from the Dashboard overflow menu
- Filter chips: All / AI / Keyword / App / Schedule
- Shows up to 500 most recent events with delete-row gesture
- Uses existing `BlockEventAdapter` — no schema change

### 🧹 Phase 2 / 6 — Component & Stability tidying
- Centralized blocking-flag management → no more deadlocks
- Defensive screenshot handling with stream-based fail counters
- Inference-failure auto-recovery

---

## v2.3.0 — Previous v2.2.0 features (preserved)
- ☪️ Reel/Short addiction Islamic reminder overlay
- 🚫 One-way block rule (blocklist → allowlist forbidden)
- ⏳ 3-strike AI rule → 24h hard lock
- 💬 Extended default allowlist (IMO, Signal, Messenger, popular keyboards)
- 🎨 UI polish (status badges, left indicators)

## Core Features
- 🔍 Real-time content filtering via `AccessibilityService`
- 🧠 On-device AI detection (TFLite + GPU delegate with CPU fallback)
- 🚻 Opposite-gender NSFW filtering
- 🔤 Keyword + regex matching
- 📱 Per-app block/whitelist
- ⏰ Time-based schedule blocking (with overnight wrap)
- 🔐 PIN-protected settings (SHA-256, EncryptedSharedPreferences)
- 📊 Block event logs + CSV export
- 🛡️ Anti-uninstall protection (Device Admin + Accessibility-level)

## Architecture
- **MVVM + Clean Architecture**
- **Hilt** dependency injection
- **Room v2** with manual migration (`MIGRATION_1_2`)
- **DataStore Preferences**
- **Kotlin Coroutines + Flow**
- **Material Design 3** (dark theme)
- **ViewPager2** for onboarding

## Files Added / Changed in v2.3.0

### New files
- `ui/onboarding/OnboardingActivity.kt`
- `ui/onboarding/OnboardingPagerAdapter.kt`
- `ui/onboarding/OnboardingPageFragment.kt`
- `ui/activitylog/ActivityLogActivity.kt`
- `ui/activitylog/ActivityLogViewModel.kt`
- `admin/UninstallProtection.kt`
- `admin/TamperLogger.kt`
- Layouts: `activity_onboarding.xml`, `fragment_onboarding_page.xml`, `activity_log.xml`
- Drawables / indicators: `indicator_dot_filled.xml`, `indicator_dot_empty.xml`, `bg_indicator_filled.xml`, `bg_indicator_empty.xml`

### Modified files
- `service/accessibility/GuardianAccessibilityService.kt` (stability + uninstall protection wiring)
- `service/detection/AiDetector.kt` (`runInferenceSafe`, CPU fallback)
- `ui/dashboard/MainActivity.kt` (first-run check + Activity Log menu)
- `res/menu/menu_dashboard.xml` (new "Activity Log" entry)
- `res/values/strings.xml` (Phase 1 strings)
- `AndroidManifest.xml` (OnboardingActivity + ActivityLogActivity)
- `app/build.gradle.kts` (`androidx.viewpager2:1.1.0` + version bump)

## Build
```bash
./gradlew assembleDebug
```

CI builds automatically via GitHub Actions (`.github/workflows/build.yml`).

## TFLite Models
Place these `.tflite` files via the app's **Settings → AI Models → Import** UI:
| File | Purpose |
|---|---|
| `guardian_model.tflite` | Legacy combined NSFW classifier |
| `nsfw_model.tflite` | Dedicated NSFW gate |
| `gender_model.tflite` | Male/female classification |

## License
Personal use. Use responsibly.
