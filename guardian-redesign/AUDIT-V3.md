# Guardian Shield — V3 Design-Language Overhaul Audit ("Watchtower")

**Date:** 2026-08-17 · **Branch:** `arena/01a00f5b-dogs-of-kahaf` (base `main` @ `57a71eb`, v2.5.4 / versionCode 18)
**Note:** `AGENT_LOG.md` referenced by the task brief does **not exist** in this repo; `COMPILE_REVIEW_REPORT.md` is the real session history and was read in full (latest landed session = strike-warning card + "Not sensitive" report button, v2.5.4, confirmed present in `view_strike_warning.xml` and `GuardianAccessibilityService.kt`).

---

## 1) Layout technology

**XML Views + ViewBinding + Material 3** (`Theme.Material3.Dark.NoActionBar`). No Compose anywhere. All redesign work is XML/Kotlin-cosmetic; ViewModels and bindings are preserved.

## 2) Complete screen inventory (real, from AndroidManifest + layouts)

| # | Screen | Layout | Key bindings (must preserve) | States that exist / convention |
|---|--------|--------|------------------------------|-------------------------------|
| 1 | MainActivity shell (bottom nav) | `activity_main.xml` + `bottom_nav_menu.xml` | `toolbar`, `fragment_container`, `bottom_navigation` (Home/Activity/Protection/Settings) | — |
| 2 | Home / Dashboard | `fragment_dashboard.xml` | `statusCard`, `shieldGlow`, `imgShield`, `txtStatusTitle/Subtitle`, `btnToggle`, `txtProtectionBadge`, `txtStatTotal/Ai/Time/Keyword`, `cardAppBlocking/Keywords/Whitelist`, `txtSeeAll`, `recyclerRecent` | On / Off / Paused (DashboardFragment sets all three); loading & empty = UI-states convention |
| 3 | Activity Log | `activity_log.xml` | `btnDay/Week/Month`, `txtBlockedAttempts`, `chipAll/Ai/Keyword/App/Schedule`, `txtCount`, `txtEmpty`, `recyclerEvents`; toolbar menu Export/Clear | loaded / empty (`txtEmpty`) / loading skeleton / error |
| 4 | Protection Hub | `fragment_protection.xml` | `imgShield`, `txtProtectionTitle/Subtitle`, `txtBadgeActive`, `cardAppBlocking`, `cardKeyword`, `cardSchedule`, `cardAi`, `cardAccessibility` | active / off |
| 5 | Settings | `activity_settings.xml` | `lockBanner`, `txtLockRemaining`, `switchKeyword`, `switchAi`, `sliderDelay`, `txtDelayValue`, `chip15/30/60min`, `btnApps/Keywords/Schedule/Permissions/CommitmentLock`, `sliderGuardianThreshold`, `txtGuardianThresholdValue`, `chipVote1–4`, `btnImportLegacy/RemoveLegacy`, `txtLegacyStatus`, `btnChangePin` | locked (Commitment Lock banner) / normal |
| 6 | App Blocking (incl. whitelist tab) | `activity_app_list.xml`, `item_app_rule.xml` | `switchHero`, `filterGroup` (`chipBlocked/All/Whitelisted`), `editSearch`, `recycler`; row: `viewLeftIndicator`, `imgIcon`, `txtAppName`, `txtStatusBadge`, `txtPackage`, `txtCategory`, `switchBlock`, `imgLockIcon`, `switchWhitelist` | loaded / empty / **loading** (`AppListState.loading` exists) / locked banner |
| 7 | Keywords | `activity_keyword.xml`, `item_keyword.xml`, `dialog_add_keyword.xml` | `txtEmpty`, `recycler`, `fabAdd`; row: `txtKeyword`, regex `badge`, `btnDelete`; dialog: `editKeyword`, `checkRegex` | loaded / empty / locked |
| 8 | Schedule | `activity_schedule.xml`, `item_schedule_rule.xml`, `dialog_schedule_editor.xml` | `txtEmpty`, `recycler`, `fabAdd`; row: `txtPackage`, `txtSchedule`, `btnEdit`; dialog: `editPackage`, `txtStart/End`, `chipSun–Sat` | loaded / empty / locked |
| 9 | Commitment Lock (Time Lock) | `activity_time_lock.xml` | `groupLocked`: `txtLockStatus`, `txtLockLabel`, `txtRemaining`, `txtLockEnd`, `txtCooldownNote`, `btnRequestUnlock`; `groupUnlocked`: `chip1/3/7/15/30day` | locked / unlocked |
| 10 | Permission Health | `activity_permissions.xml` | rows: accessibility, usage stats, overlay, notification, battery + `btnFixAll`; each row icon + `btn*` fix button | granted / missing |
| 11 | PIN Setup | `activity_pin_setup.xml` | `txtPrompt`, `dot1–6`, `btn0–9`, `btnDel`, `btnOk` | enter / confirm / error |
| 12 | PIN Verify (lock) | `activity_pin_verify.xml` | `dot1–6`, `btn0–9`, `btnDel`, `btnOk` | idle / error-shake / attempts-left |
| 13 | Delay Unlock | `activity_delay_unlock.xml` | `txtPackage`, `txtCountdown`, `btnCancel` | counting |
| 14 | Strike-3 Full Block overlay | `activity_block_overlay.xml` | `txtPackage`, `cardTempBlock/txtTempBanner`, `txtReason`, `txtCategory`, `btnHome` (goHome), `btnUnlock` (DelayUnlock), `btnMarkFalse` (false-positive memory flow) | AI / keyword / app / schedule / tamper reasons |
| 15 | Blocked Content Detail | `activity_blocked_detail.xml` | `txtApp/Category/Source/Time`, `rowStayFocused/ViewActivity/Whitelist`, `btnBackToApp`, `btnReport` | loaded |
| 16 | Accessibility Prompt (urgent) | `activity_accessibility_prompt.xml` | `btnEnable` (static title/body copy) | — |
| 17 | Reel Reminder (Islamic interstitial) | `activity_reel_reminder.xml` | `imgIslamic`, `txtTitle`, `txtHadith`, `txtSubtitle`, `btnOpenQuran`, `btnContinue` | — |
| 18 | Onboarding (first run) | `activity_onboarding.xml`, `fragment_onboarding_page.xml` | `btnSkip`, viewPager, dots, `btnBack`, `btnNext`; 4 pages (icon/title/body/highlight) | 4 fixed pages |
| 19 | Strike 1/2 warning card | `view_strike_warning.xml` (WindowManager overlay) | `txtStrikeKicker/Title/Body`, `btnNotSensitive`; tap-to-dismiss; **3.5s auto-dismiss (`STRIKE_WARNING_AUTO_DISMISS_MS = 3_500L`)** | strike 1 / 2 |
| 20 | Dialogs | `dialog_add_keyword`, `dialog_schedule_editor` + MaterialAlertDialogs (clear logs, battery, device admin) | as above | — |
| 21 | App icon / splash | `mipmap-anydpi-v26/ic_launcher.xml` (adaptive: `bg_main` bg + `ic_launcher_foreground` vector + monochrome) | — | editable in-repo (vector) — in scope |

## 3) Modules that are REAL (Protection screen maps to these only)

AI Content Detection (`switchAi`, sensitivity slider + vote chips) · App Blocking (AppList + whitelist filter) · Schedule rules · Keyword filter · Accessibility/overlay permissions. **No DNS/VPN/browser-screen modules exist — none will be invented.** No biometric API is wired in the PIN screens — **no biometric affordance will be invented** (prior v2.5 mock included one; that was a gap, flagged).

## 4) Locked logic (hard constraint — NOT touched)

`TempBlockManager.kt`, `BlockingEngine.kt`, `service/detection/**`, strike counting in `GuardianAccessibilityService.kt` (incl. `STRIKE_THRESHOLD`, `STRIKE_WARNING_AUTO_DISMISS_MS = 3_500L`, `reportNotSensitive` audit-only write), Room DAO/entities.

## 5) Copy / locale reality

Default locale strings are English with Bengali mixed in the live UI; `values-bn/` is full Bengali. Mocks use the app's real copy verbatim (Bengali where the default UI shows Bengali). Tone: warm, direct, protective — e.g. "সুরক্ষা সক্রিয়", "ভুল ব্লক হয়েছে?", "একটু থামো, ভাই!".
