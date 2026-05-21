# 🛡️ Guardian Shield v2.0 — Modernized

একটি শিশু-নিরাপদ, ইসলামিক মূল্যবোধ সম্পন্ন Android parental-control app।
Material 3 UI, Kotlin, Room, AccessibilityService — সব v1 features + v2 এর 4টি নতুন feature।

---

## ✅ v2.0 New Features

### 1. One-Way Allowlist → Blocklist Rule
- Whitelisted app এর Block switch grayed-out (alpha=0.38, disabled)
- User must first turn OFF whitelist, then can turn ON block
- ViewModel + Repository defensive layer: `setBlocked(pkg, blocked=true)` returns `false` if app is whitelisted
- Snackbar message: "First remove this app from Allowlist…"

📂 Files: `AppListAdapter.kt`, `AppListViewModel.kt`, `GuardianRepository.kt`

### 2. Scroll Addiction Detection (Reel/Shorts Binge)
- Tracks `TYPE_VIEW_SCROLLED` events on Instagram Reels, YouTube Shorts, TikTok, Facebook Reels, Snapchat, Reddit, Twitter
- Sliding 1-minute window — 15 upward swipes → Quran/Hadith overlay
- 6 random Quran verses & hadith (Bengali + Arabic)
- 30-second skip countdown
- 5-minute cooldown after each suggestion
- "Quran App খুলুন" — auto-detects installed Quran apps (4 known packages), falls back to Play Store
- Memory-safe: tracks at most 10 packages (FIFO eviction)

📂 Files: `ScrollAddictionDetector.kt`, `ScrollSuggestionActivity.kt`, `QuranReminders.kt`

### 3. AI Detection — 3 Strikes → 24 Hour Block
- 3 strikes = automatic 24h fixed block (not configurable)
- Strikes do NOT reset on a daily timer — only when block expires
- During active block, new AI detections are ignored
- Strike 1/2 → Warning overlay ("⚠️ AI সতর্কতা: 1/3 — 2 আর")
- Strike 3 → Full block overlay with live countdown
- Thread-safe `@Synchronized` methods

📂 Files: `TempBlockManager.kt`, `AiContentDetector.kt`, `BlockOverlayActivity.kt`

### 4. Default Allowlist (Communication + Keyboard Apps)
- First open of App List screen → automatically whitelists 25+ packages
- IMO, Signal, Messenger, Viber, LINE, Telegram, WhatsApp
- Keyboards: Gboard, SwiftKey, Samsung, Grammarly, Ridmik, Mayabi, Avro, Bijoy
- Dialers (Google, AOSP, Samsung)
- **Idempotent** — does not override manual rules

📂 Files: `AppListViewModel.initDefaultAllowlist()`, `Constants.DEFAULT_ALLOWLIST_PACKAGES`

---

## 📱 Modern Material 3 UI

- **Dynamic Color (Material You)** — wallpaper-driven palette
- **Bottom Navigation**: Dashboard / Activity / Apps / Settings / Profile
- **Hero status card** on Dashboard with live AI status
- **3 quick-stats cards** (Apps blocked / Reels reminders / AI blocks)
- **"Today's Ayah" card** — stable per-day Quran reminder
- **Activity Log filter chips**: All / Blocked / AI / Scroll / Keywords / Schedule
- **Light + Dark mode** auto support
- **RTL-friendly** Arabic text with `textDirection="rtl"`
- **Dedicated overlay theme** — fullscreen islamic dark navy `#1A1A2E` + gold `#C4A55A` accent

---

## 🔒 Anti-Uninstall Protection (7 Layers)

1. **Device Admin** — `onDisableRequested` triggers PIN gate
2. **AccessibilityService** — intercepts "Uninstall" button on Play Store / Settings, redirects to home + tamper alert
3. **PackageMonitorReceiver** — if package gets removed, last-ditch tamper notification
4. **Stealth Mode** option — hide launcher icon (Settings → Stealth)
5. **PIN Protection** — `EncryptedSharedPreferences` + salted SHA-256, blocks all admin screens
6. **TamperAlertActivity** — full-screen alarm + vibration + repository log entry
7. **Auto-backup rules** — `guardian.db` survives factory reset via Cloud Backup

📂 Files: `GuardianDeviceAdminReceiver.kt`, `PackageMonitorReceiver.kt`, `TamperAlertActivity.kt`, `PinManager.kt`, `xml/backup_rules.xml`

---

## ⚡ Stability & Performance Fixes

- **Thread-safe** strike manager (`@Synchronized`)
- **Memory-bounded** scroll counter (≤10 packages)
- **Adapter listener detachment** before `setChecked` to avoid recursive toggles
- **`runCatching {}`** around all `PackageManager` calls (handle uninstalled apps gracefully)
- **`fallbackToDestructiveMigration()`** on Room DB to prevent crash on schema changes
- **Foreground service with `specialUse` type** (Android 14+ compliant)
- **`Boot + LOCKED_BOOT + MY_PACKAGE_REPLACED`** receivers — survives reboot & app update
- **Coroutines + Flow** throughout → no `Thread.sleep`, no leaks
- **Coroutine `SupervisorJob`** in AccessibilityService — one crash doesn't kill the rest

---

## 🛠️ Build & Run

```bash
# 1. Open in Android Studio Hedgehog+ (or later)
# 2. Let it sync Gradle (uses 8.4 + AGP 8.2.2 + Kotlin 1.9.22)
# 3. Build → Make Project
# 4. Run on device (min SDK 24, target SDK 34)
```

### First-time setup on device
1. App launches → asks to set 4–8 digit Parent PIN
2. Open Settings → Accessibility → enable "Guardian Shield Protection"
3. Open Settings → Security → Device Admin apps → enable "Guardian Shield Admin"
4. Grant Notification + Display-over-other-apps permissions when prompted
5. Open the App List tab — default whitelist is auto-applied

---

## 📂 Project Structure

```
app/src/main/
├── AndroidManifest.xml
├── java/com/guardianshield/app/
│   ├── GuardianApp.kt
│   ├── ui/
│   │   ├── MainActivity.kt
│   │   ├── dashboard/    (Dashboard + ViewModel)
│   │   ├── activity/     (Activity Log + filter chips)
│   │   ├── applist/      (One-way rule UI)
│   │   ├── scroll/       (Quran suggestion screen)
│   │   ├── overlay/      (24h Block overlay)
│   │   ├── admin/        (PIN + Tamper)
│   │   ├── settings/     (PreferenceFragment)
│   │   └── profile/
│   ├── service/
│   │   ├── GuardianAccessibilityService.kt   ← core engine
│   │   ├── ProtectionForegroundService.kt
│   │   └── ScheduleEvaluator.kt
│   ├── detector/
│   │   └── ScrollAddictionDetector.kt        ← v2 feature 2
│   ├── manager/
│   │   ├── TempBlockManager.kt               ← v2 feature 3
│   │   ├── AiContentDetector.kt
│   │   ├── PinManager.kt
│   │   └── QuranReminders.kt
│   ├── receiver/
│   │   ├── GuardianDeviceAdminReceiver.kt
│   │   ├── BootReceiver.kt
│   │   └── PackageMonitorReceiver.kt
│   ├── data/
│   │   ├── db/  (Room DB + DAOs)
│   │   ├── model/  (AppRule, ActivityLog, KeywordFilter, Schedule)
│   │   └── repo/   (GuardianRepository — one-way rule enforcement)
│   └── util/
│       └── Constants.kt
└── res/
    ├── layout/   (12 layouts)
    ├── values/   (Material 3 theme + Islamic palette)
    ├── values-night/   (Dark mode)
    ├── drawable/ (vector icons)
    ├── menu/     (bottom nav)
    ├── navigation/  (nav graph)
    └── xml/      (a11y / device-admin / backup / preferences)
```

---

## ✅ v2.0 Success Criteria — All Met

- ✅ Whitelisted app → block switch grayed, cannot be directly blocked
- ✅ 15+ swipes on Instagram Reels in 60s → Quran suggestion appears
- ✅ Suggestion has 30s countdown, "Open Quran" button works (4 fallback apps)
- ✅ AI detection 3rd strike → 24h block (fixed, not configurable)
- ✅ Block expires → strike count auto-resets
- ✅ First open of App List → IMO, Signal, Messenger, keyboards whitelisted
- ✅ Existing users: default allowlist does NOT override manual rules
- ✅ User cannot accidentally block a communication app
- ✅ Child cannot bypass Quran suggestion in <30 seconds

---

## 📦 Version

- **App version**: 2.0.0 (versionCode 20)
- **Min SDK**: 24 (Android 7.0)
- **Target SDK**: 34 (Android 14)
- **Compile SDK**: 34
- **Language**: Kotlin 1.9.22
- **Build**: AGP 8.2.2, Gradle 8.4

—
Built with ❤️ for children's safety and Islamic values.
