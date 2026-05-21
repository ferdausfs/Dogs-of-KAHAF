# Changelog

## v2.0.0 — Modernization Release

### 🆕 New Features
- **One-Way Allowlist → Blocklist rule** (`AppListAdapter`, `GuardianRepository.setBlocked`)
- **Scroll Addiction Detection** with Quran/Hadith suggestions (`ScrollAddictionDetector`, `ScrollSuggestionActivity`)
- **AI Detection: 3 strikes → 24-hour block** (`TempBlockManager`)
- **Default Allowlist** for communication & keyboard apps (`AppListViewModel.initDefaultAllowlist`)

### 🎨 UI/UX
- Migrated to Material Design 3 (Material You) with dynamic colors
- Bottom navigation with 5 tabs
- Dashboard hero card + 3 quick-stats + Today's Ayah
- Activity Log filter chips (All/Blocked/AI/Scroll/Keywords/Schedule)
- New islamic dark theme for overlays (#1A1A2E + #C4A55A)
- Full Light + Dark mode + RTL support

### 🔒 Security
- 7-layer anti-uninstall protection
- EncryptedSharedPreferences for PIN (salted SHA-256)
- Tamper alert with alarm + vibration
- Cloud backup for survival across factory reset

### ⚡ Stability
- @Synchronized methods on TempBlockManager
- Memory-bounded scroll counter
- runCatching{} around PackageManager calls
- SupervisorJob in AccessibilityService
- Foreground service specialUse compliance (Android 14)
- Boot + locked-boot + package-replaced auto-restart

### 🐛 Bug Fixes
- Adapter listener detachment prevents recursive toggles
- Room fallbackToDestructiveMigration prevents crash on schema bumps
- Schedule evaluator handles overnight windows correctly
- Whitelist auto-disables block flag (data integrity)
