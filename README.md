# 🛡️ Guardian Shield

An offline Android content-blocking app using Accessibility Service, keyword filtering, and optional TFLite AI detection.

## Features
- 🔒 App-level blocking via AccessibilityService
- 🔤 Keyword / regex text scanning
- 🤖 Optional TFLite AI image detection
- 🔐 PIN-protected settings (AES-256-GCM)
- ⏱️ Timed unlock delay (reflection timer)
- 📊 Block event log with today's count
- 🚀 Boot-persistent foreground service
- ✅ Whitelist override (always-allow apps)

## Setup
1. Clone → open in Android Studio
2. Build → install APK
3. Set PIN on first launch
4. Enable Accessibility Service when prompted
5. Go to Settings → Blocked Apps → toggle apps to block

## Optional: AI Detection
Upload a `.tflite` binary classifier (5-class NSFW model) via Settings.
The app expects output shape `[1, 5]` for: `[drawings, hentai, neutral, porn, sexy]`.

## Build
```bash
./gradlew assembleDebug
```
GitHub Actions CI builds APK automatically on every push.

## Architecture
- **Clean Architecture** — Domain / Data / Service / UI layers
- **MVVM + StateFlow** — Lifecycle-aware ViewModels
- **Hilt DI** — Singleton-scoped engines
- **Room** — 3 entities (AppRule, KeywordRule, BlockEvent)
- **DataStore** — Settings persistence
- **EncryptedSharedPrefs** — AES-256-GCM PIN storage

## Min SDK: 26 (Android 8.0+)
