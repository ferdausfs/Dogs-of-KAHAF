# Guardian Shield 🛡️

**Package:** `com.guardian.shield` · **Min SDK:** 26 (Android 8.0) · **Target SDK:** 35 · **Version:** 2.3.0-phase4

An Android parental/self-control app using `AccessibilityService` to block harmful content on-device.

## v2.3.0-phase4 — Phase 4: Onboarding Flow

**New in this build:**
- 🎉 4-screen welcome onboarding (Welcome → Features → Permissions intro → PIN setup intro)
- 📱 ViewPager2-based swipeable pager with dot indicators
- 🔁 Auto-shown only on first launch (driven by `GuardianPreferences.firstRun` flag)
- ⏭️ Skip and Back buttons for flexibility
- 🚏 After completion, smart routing: jumps to Permissions screen if accessibility is off, otherwise directly to Dashboard
- 🎨 Themed to match Guardian Shield's dark Material 3 palette

**Files added/changed in this phase:**
| Type | File |
|------|------|
| New | `OnboardingActivity.kt` |
| New | `OnboardingPagerAdapter.kt` |
| New | `OnboardingPageFragment.kt` |
| New | `activity_onboarding.xml` |
| New | `fragment_onboarding_page.xml` |
| New | `indicator_dot_filled.xml`, `indicator_dot_empty.xml` |
| New | `bg_indicator_filled.xml`, `bg_indicator_empty.xml` |
| Modified | `MainActivity.kt` (first-run check + redirect) |
| Modified | `AndroidManifest.xml` (register OnboardingActivity) |
| Modified | `app/build.gradle.kts` (add `androidx.viewpager2:1.1.0`) |

### Previous v2.2.0 features (preserved)
- One-Way Block Rule (blocklist → allowlist forbidden)
- Reel/Short addiction Islamic reminder
- 3-strike AI → 24h hard lock
- Extended default allowlist (IMO, Signal, Messenger, popular keyboards)
- UI polish (status badges, left indicators, refined item rows)

## Core Features
- 🔍 Real-time content filtering via `AccessibilityService`
- 🧠 On-device AI detection (TFLite + GPU delegate)
- 🚻 Opposite-gender NSFW filtering
- 🔤 Keyword + regex matching
- 📱 Per-app block/whitelist
- ⏰ Time-based schedule blocking (with overnight wrap)
- 🔐 PIN-protected settings (SHA-256, EncryptedSharedPreferences)
- 📊 Block event logs + CSV export
- ☪️ Reel/Short addiction Islamic reminder overlay

## Architecture
- **MVVM + Clean Architecture**
- **Hilt** dependency injection
- **Room v2** with manual migration (`MIGRATION_1_2`)
- **DataStore Preferences**
- **Kotlin Coroutines + Flow**
- **Material Design 3** (dark theme)
- **ViewPager2** for onboarding

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
