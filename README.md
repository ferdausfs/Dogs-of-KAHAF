# 🛡️ Guardian Shield — v12 (2.1.2)

**Adult Content Blocker & Digital Discipline App for Android**

Native Android app — Kotlin, Clean Architecture, MVVM, Hilt, Room, TFLite.

## 🆕 v12 (2.1.2) — Full Optimisation + Stability Patch 2

After the v11 stability patch a deeper audit found **eight more crash /
hang / leak vectors** that v11 did not catch. v12 fixes every one of
them and applies app-wide optimisation. **No feature changes.**

Headline fixes:
- AppList screen no longer hangs on first launch (Main-thread Flow.first ANR fixed).
- Legacy model import now correctly closes the live TFLite interpreter before swap.
- AccessibilityService onDestroy no longer leaks a CoroutineScope each restart.
- takeScreenshot SecurityException on custom ROMs no longer crashes detection.
- DataStore .first() now bounded by a 2 s timeout — no more frozen settings.
- Release builds now have a Timber tree → real logs in OEM bug reports.

See [CHANGELOG.md](CHANGELOG.md) for full details.

## 🆕 v10 (2.1.0) — Smart Tiered Detection (kept)

Four-tier classification (`SAFE` / `NATURAL` / `SUGGESTIVE` / `EXPLICIT`) —
only **EXPLICIT** triggers a block. Hot/sexy content is logged but never
blocks. EXPLICIT debounce (2 hits within 3 s) eliminates single-frame
false positives. Source-based **15-min auto-lock** for content-source
apps (Facebook / Instagram / TikTok / browsers / etc.) when AI confirms
explicit material. Sensitivity preset (Low / Balanced / High).

---

## 🚀 Quick Start — Build APK via GitHub Actions

1. Push this repo to GitHub (any branch: `main` / `master` / `dev`)
2. GitHub Actions starts automatically
3. Go to **Actions** tab → `Build Debug APK` → download `GuardianShield-debug-*.apk` artifact

That's it. No local Android Studio needed.

---

## 🏗️ Local Build (Android Studio)

```bash
git clone https://github.com/YOUR_USER/GuardianShield
cd GuardianShield
./gradlew assembleDebug
# APK → app/build/outputs/apk/debug/app-debug.apk
```

**Requirements:**
- JDK 17+
- Android SDK (API 35)
- Gradle 8.4 (auto-downloaded by wrapper)

---

## 📱 First-Time Setup on Device

1. Install APK (enable "Install from unknown sources")
2. Open app → **Set PIN** (protects settings)
3. Tap **"Enable Accessibility Service"** → Settings → Guardian Shield → Enable
4. Add apps to **Blocked List** in Settings
5. Optionally upload `.tflite` model for AI detection

---

## 🧠 Architecture

```
Clean Architecture + MVVM

UI Layer        → Activities, Adapters
ViewModel Layer → StateFlow, lifecycle-aware
Domain Layer    → UseCases, Models
Data Layer      → Room DB, DataStore, EncryptedSharedPrefs

Key Services:
  RulesEngine                     ← whitelist-first detection priority
  GuardianAccessibilityService    ← event-driven, no polling
  BlockingEngine                  ← HOME + BlockOverlayActivity
  AiDetector                      ← TFLite, event-triggered only
  PinManager                      ← SHA-256 hash, EncryptedSharedPrefs
  TimedBlockManager               ← 15-min source-based auto-lock
```

**Whitelist Priority (immutable order):**
```
1. Own package       → always allow
2. System UI         → always allow
3. WHITELIST         → always allow (overrides EVERYTHING)
4. Blocked app list  → block
5. Keyword match     → block
6. AI detection      → block
```

---

## 📦 Tech Stack

| Component | Library |
|-----------|---------|
| Language | Kotlin 1.9.24 |
| DI | Hilt 2.52 |
| DB | Room 2.6.1 |
| Prefs | DataStore 1.1.1 |
| Secure Storage | EncryptedSharedPreferences |
| Async | Coroutines 1.8.1 + StateFlow |
| AI | TensorFlow Lite 2.16.1 |
| UI | Material Design 3 + ViewBinding |
| Logging | Timber 5.0.1 |
| Min SDK | API 26 (Android 8.0) |
| Target / Compile SDK | API 35 (Android 15) |

---

## 🔐 Security

- PIN stored as **SHA-256 hash** only — never plain text
- Sensitive config in **EncryptedSharedPreferences** (AES-256-GCM)
- 3-tier graceful fallback if Keystore is broken (v11+)
- No network calls — fully offline
- No analytics / tracking
- ProGuard + R8 shrinking enabled for release builds

---

## 🤖 AI Detection (Optional)

Supply your own `.tflite` model:
- **2-class**: `[safe_score, unsafe_score]`
- **5-class**: `[drawings, hentai, neutral, porn, sexy]`

Upload via Settings → AI Screen Detection → Upload Model.

Model is **not bundled** in the APK. Stored in `filesDir/guardian_model.tflite`.

---

## 📁 Project Structure

```
app/src/main/java/com/guardian/shield/
├── domain/
│   ├── model/          AppRule, KeywordRule, BlockEvent, ContentTier
│   └── usecase/        clean use cases
├── data/
│   ├── local/db/       Room entities, DAOs, mappers
│   ├── local/datastore/ GuardianPreferences, SecureStorage
│   └── repository/     interfaces + implementations
├── service/
│   ├── detection/      RulesEngine, PinManager, AiDetector, TimedBlockManager
│   ├── blocker/        BlockingEngine, ForegroundService
│   └── accessibility/  GuardianAccessibilityService
├── di/                 Hilt modules
├── viewmodel/          Dashboard, AppList, Settings, Keyword, PIN, Schedule
├── ui/
│   ├── dashboard/      MainActivity, BlockEventAdapter
│   ├── overlay/        BlockOverlayActivity
│   ├── unlock/         DelayUnlockActivity
│   ├── setup/          PinSetupActivity, PinVerifyActivity
│   ├── permissions/    PermissionsActivity (one-tap permission health)
│   └── settings/       SettingsActivity, AppList, Keyword, Schedule
├── admin/              GuardianDeviceAdminReceiver
└── receiver/           BootReceiver
```
