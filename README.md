# Guardian Shield 🛡️

**Package:** `com.guardian.shield` · **Min SDK:** 26 (Android 8.0) · **Target SDK:** 35 · **Version:** 2.2.0

An Android parental/self-control app using `AccessibilityService` to block harmful content on-device.

## Features
- 🔍 Real-time content filtering via `AccessibilityService`
- 🧠 On-device AI detection (TFLite + GPU delegate)
- 🚻 Opposite-gender NSFW filtering
- 🔤 Keyword + regex matching
- 📱 Per-app block/whitelist
- ⏰ Time-based schedule blocking (with overnight wrap)
- 🔐 PIN-protected settings (SHA-256, EncryptedSharedPreferences)
- 📊 Block event logs + CSV export
- ☪️ **NEW v2.2.0** — Reel/Short addiction Islamic reminder overlay
- 🚫 **NEW v2.2.0** — One-way block rule (blocklist → allowlist forbidden)
- ⏳ **NEW v2.2.0** — 3-strike AI rule → 24h hard lock
- 💬 **NEW v2.2.0** — Extended default allowlist (IMO, Signal, Messenger, popular keyboards)

## v2.2.0 Changes
| # | Task | Files Changed |
|---|------|---------------|
| 1 | One-way block rule | `AppListAdapter.kt`, `AppListViewModel.kt`, `item_app_rule.xml` |
| 2 | Reel/Short Islamic reminder | `GuardianAccessibilityService.kt`, `AndroidManifest.xml`, **new:** `ReelScrollDetector.kt`, `ReelReminderActivity.kt`, `activity_reel_reminder.xml` |
| 3 | 3-strike → 24h AI block | `Constants.kt`, `TempBlockManager.kt`, `BlockOverlayActivity.kt` |
| 4 | Extended allowlist | `AppClassifier.kt` |
| 5 | UI polish | `item_app_rule.xml`, `colors.xml`, `themes.xml`, `bg_badge.xml` |

## Architecture
- **MVVM + Clean Architecture**
- **Hilt** dependency injection
- **Room v2** with manual migration (`MIGRATION_1_2`)
- **DataStore Preferences**
- **Kotlin Coroutines + Flow**
- **Material Design 3** (dark theme)

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

Models are imported to `filesDir` and validated by TFL3 header.

## License
Personal use. Use responsibly.
