# Guardian Shield — One UI 8 Overhaul Audit

**Date:** 2026-08-18  
**Branch:** `arena/01a012b4-dogs-of-kahaf`  
**Base:** `main` @ `6d08327` (Merge PR #45, Watchtower V3) · **versionName 3.0.1 / versionCode 20**  
**Prior audit:** `guardian-redesign/AUDIT-V3.md` (2026-08-17) — re-verified against current `main`. Still accurate on screen inventory; this file supersedes it with live binding IDs, unbound chrome, and One UI 8 scope notes.

`guardian-redesign/AUDIT.md` (v2.5) is **gone**. Session history lives in `COMPILE_REVIEW_REPORT.md`.

---

## 1) Layout technology (confirmed)

**XML Views + ViewBinding + Material 3** (`Theme.Material3.Dark.NoActionBar`).

- Zero Compose (`androidx.compose` / `setContent {` not present).
- All production UI is Constraint/Linear/Scroll + `MaterialCardView` + Recycler + Material switches/sliders/chips.
- Redesign work is XML + cosmetic Kotlin (color setters, visibility). ViewModels, click destinations, and detection logic stay.

---

## 2) Complete screen inventory (from AndroidManifest + layouts + Kotlin)

| # | Screen | Layout / code | Tech | Key bindings (must preserve) | Real data / states | Notes |
|---|--------|---------------|------|------------------------------|--------------------|-------|
| 1 | **Main shell** | `activity_main.xml` + `bottom_nav_menu.xml` · `MainActivity.kt` | XML | `appBarLayout`, `toolbar`, `fragment_container`, `bottom_navigation` | 4 tabs: Home / Activity / Protection / Settings | **Home + Protection = fragments.** Activity + Settings **launch Activities** and return `false` (tab does not stay selected). Do not change this nav contract. |
| 2 | **Home / Dashboard** | `fragment_dashboard.xml` · `DashboardFragment` + `DashboardViewModel` | XML | `statusCard`, `shieldGlow`, `imgShield`, `txtStatusTitle`, `txtStatusSubtitle`, `btnToggle`, `txtProtectionBadge`, `txtStatTotal`, `txtStatAi`, `txtStatTime`, `txtStatKeyword`, `cardAppBlocking`, `cardKeywords`, `cardWhitelist`, `txtSeeAll`, `recyclerRecent` | States: **ON** / **Paused** / **Service off**. Stats: `todayCount`, `totalBlocks`, `aiBlocks`, `keywordBlocks`. Recent = `observeEvents(20)`. Toggle respects TimeLock + Accessibility → `AccessibilityPromptActivity`. Cards → AppList / Keyword / AppList (whitelist is a filter, not a screen) / ActivityLog. | `txtStatKeyword` exists in XML but is **not written** by the fragment. `txtStatTime` is a **derived placeholder** (`totalBlocks/5` hours) — protected uptime is not tracked. Do not invent a time-tracker. Empty recent uses `empty_no_blocks`. No loading/error state in VM. |
| 3 | **Activity Log** | `activity_log.xml` · `ActivityLogActivity` + `ActivityLogViewModel` | XML | Wired: `toolbar`, `chipAll/Ai/Keyword/App/Schedule`, `txtCount`, `txtEmpty`, `recyclerEvents`. **Present in XML, unbound in Kotlin:** `btnDay`, `btnWeek`, `btnMonth`, `txtBlockedAttempts` | Filter = reason only (`LogFilter`). Events = `observeEvents(500)`. Empty via `txtEmpty`. Delete on row. Toolbar menu Export/Clear exists as strings; confirm in `menu_dashboard` / activity options. | **Do not invent Day/Week/Month period filtering** — those buttons are leftover V3 chrome. Reskin or keep IDs; do not add VM period logic. No loading/error in VM. |
| 4 | **Protection Hub** | `fragment_protection.xml` · `ProtectionFragment` | XML | `imgShield`, `txtProtectionTitle`, `txtProtectionSubtitle`, `txtBadgeActive`, `cardAi`, `cardAppBlocking`, `cardKeyword`, `cardSchedule`, `cardAccessibility` | ON / Off / Paused from same dashboard VM. Clicks: AppList, Keyword, Schedule, Settings (AI), Settings (Accessibility). | **5 real modules only.** No DNS / VPN / browser / Strict Mode feature. Comment mentions gender filter “via Settings” — **no gender UI exists in Settings**. |
| 5 | **Settings** | `activity_settings.xml` · `SettingsActivity` + `SettingsViewModel` | XML | `toolbar`, `lockBanner`, `txtLockRemaining`, `switchKeyword`, `switchAi`, `txtDelayValue`, `sliderDelay`, `chipGroupBlockDuration`, `chip15min/30min/60min`, `btnApps`, `btnKeywords`, `btnSchedule`, `btnPermissions`, `btnCommitmentLock`, `txtGuardianThresholdValue`, `sliderGuardianThreshold`, `chipGroupGridVote`, `chipVote1–4`, `txtLegacyStatus`, `btnImportLegacy`, `btnRemoveLegacy`, `btnChangePin` | Prefs: keywordFilter, aiDetection, delaySeconds (5–120), tempBlockDurationMins (15/30/60), aiThreshold (0.30–0.95), gridVoteCount (1–4), legacy model slot. PIN gate: if PIN set → `PinVerifyActivity` before `initUI()`. Commitment Lock disables the listed controls; nav buttons stay enabled. | Gender-filter **strings exist**, **no chips/UI**. Do not invent. |
| 6 | **App Blocking** (whitelist = filter tab) | `activity_app_list.xml` + `item_app_rule.xml` · `AppListActivity` + `AppListViewModel` + `AppListAdapter` | XML | Screen: `toolbar`, `lockBanner`, `txtLockRemaining`, `switchHero`, `filterGroup`, `chipBlocked`, `chipAll`, `chipWhitelisted`, `editSearch`, `recycler`. Row: `viewLeftIndicator`, `imgIcon`, `txtAppName`, `imgLockIcon`, `txtPackage`, `txtCategory`, `txtStatusBadge`, `switchBlock`, `switchWhitelist` | `AppListState`: apps, filter (`ALL/BLOCKED/WHITELISTED`), query, **`loading`**. Locked banner when TimeLock active. | **No standalone Whitelist activity.** Loading flag exists — mock a skeleton; no spinner. |
| 7 | **Keywords** | `activity_keyword.xml` + `item_keyword.xml` + `dialog_add_keyword.xml` · `KeywordActivity` + `KeywordViewModel` + `KeywordAdapter` | XML | `toolbar`, `lockBanner`, `txtLockRemaining`, `txtEmpty`, `recycler`, `fabAdd`. Row: `txtKeyword`, `badge`, `btnDelete`. Dialog: `editKeyword`, `checkRegex` | loaded / empty (`txtEmpty`). Lock banner. | |
| 8 | **Schedule** | `activity_schedule.xml` + `item_schedule_rule.xml` + `dialog_schedule_editor.xml` · `ScheduleActivity` + `ScheduleViewModel` | XML | `toolbar`, `lockBanner`, `txtLockRemaining`, `txtEmpty`, `recycler`, `fabAdd`. Row: `txtPackage`, `txtSchedule`, `btnEdit`. Dialog: `editPackage`, `txtStart`, `txtEnd`, `chipSun–Sat` | loaded / empty. `txtStart`/`txtEnd` are **TextViews** (time-picker hosts) — keep as TextView. | |
| 9 | **Commitment Lock** | `activity_time_lock.xml` · `TimeLockActivity` | XML | `toolbar`, `groupLocked`: `txtLockStatus`, `txtLockLabel`, `txtRemaining`, `txtLockEnd`, `txtCooldownNote`, `btnRequestUnlock`. `groupUnlocked`: `chip1day/3day/7day/15day/30day` | locked / cooldown / unlocked. Chip tap → confirm dialog (hardcoded Bengali duration labels in Kotlin). | Do not change lock durations or confirm flow. |
| 10 | **Permission Health** | `activity_permissions.xml` · `PermissionsActivity` | XML | `toolbar`, `rowAccessibility` + `iconAccessibility` + `btnAccessibility`, same for UsageStats / Overlay / Notification / Battery, `btnFixAll` | granted / missing per row (icon + button swap). | |
| 11 | **PIN Setup** | `activity_pin_setup.xml` · `PinSetupActivity` + `PinViewModel` | XML | `txtPrompt`, `dot1–6`, `btn0–9`, `btnDel`, `btnOk` | enter → confirm → error. 4–6 digits. | |
| 12 | **PIN Verify** | `activity_pin_verify.xml` · `PinVerifyActivity` | XML | `dot1–6`, `btn0–9`, `btnDel`, `btnOk` | idle / wrong (shake) / attempts left / lockout 30s after 5 fails. | **No `txtPrompt` id. No biometric. No Forgot-PIN control.** Do not invent either. |
| 13 | **Delay Unlock** | `activity_delay_unlock.xml` · `DelayUnlockActivity` | XML | `txtPackage`, `txtCountdown`, `btnCancel` | counting down `delaySeconds`. Cancel → home. | Timing from prefs — do not change. |
| 14 | **Strike-3 full block overlay** | `activity_block_overlay.xml` · `BlockOverlayActivity` | XML | `txtPackage`, `cardTempBlock`, `txtTempBanner`, `txtCategory`, `txtReason`, `btnHome` (goHome), `btnUnlock` (DelayUnlock), `btnMarkFalse` (FalsePositiveMemory) | Reasons: AI / keyword / app / schedule / tamper / manual. `btnMarkFalse` **visible only for AI**. Temp-block banner when `detail` starts with `temp_block:`. Unlock hidden on some temp-block paths. | **Reskin only.** False-positive takePendingCandidate + addSignature, goHome, DelayUnlock destinations stay. |
| 15 | **Blocked Content Detail** | `activity_blocked_detail.xml` · `BlockedDetailActivity` | XML | `toolbar`, `txtApp`, `txtCategory`, `txtSource`, `txtTime`, `rowStayFocused`, `rowViewActivity`, `rowWhitelist`, `btnBackToApp`, `btnReport` | Extras from `BlockEventAdapter` tap. | Optional screen; already shipped. Source URL is extra/placeholder — not a real URL blocklist. |
| 16 | **Accessibility Prompt** | `activity_accessibility_prompt.xml` · `AccessibilityPromptActivity` | XML | `btnEnable` | Static title/body/note strings (Bengali). | Copy verbatim. |
| 17 | **Reel Reminder** | `activity_reel_reminder.xml` · `ReelReminderActivity` | XML | `imgIslamic`, `txtTitle`, `txtHadith`, `txtSubtitle`, `btnOpenQuran`, `btnContinue` | Continue finishes. Open Quran tries known packages then Play search. | Copy is Bengali (`একটু থামো, ভাই!`). Exists. Mock it. |
| 18 | **Onboarding** | `activity_onboarding.xml` + `fragment_onboarding_page.xml` · 3 Kotlin files | XML | Shell: `btnSkip`, `viewPager`, `indicatorContainer`, `btnBack`, `btnNext`. Page: `iconFrame`, `txtIcon`, `txtHighlight`, `txtTitle`, `txtBody` | 4 fixed pages. Last page: Skip hidden, Next = hardcoded `"Get Started"`. Page copy is **hardcoded Bengali in `OnboardingPagerAdapter`** (not strings.xml). | Icons are emoji today (`🛡️✨🔐🔒`). Reskin to outlined icons; do not change page count or destinations. |
| 19 | **Strike 1/2 warning card** | `view_strike_warning.xml` (WindowManager overlay) · `GuardianAccessibilityService.showAiStrikeWarning` | XML | `cardStrikeWarning`, `txtStrikeKicker`, `txtStrikeTitle`, `txtStrikeBody`, `btnNotSensitive` | Strike 1 and 2 only. Tap card dismisses. `btnNotSensitive` = audit-only DB write. **Auto-dismiss `STRIKE_WARNING_AUTO_DISMISS_MS = 3_500L`** (private const in the service). | **Reskin only.** Do not touch the 3.5s timer, tap-dismiss, or `reportNotSensitive`. |
| 20 | **Dialogs** | `dialog_add_keyword.xml`, `dialog_schedule_editor.xml` + MaterialAlertDialogs (clear logs, battery, device admin, lock confirm) | XML | as above | — | Restyle chrome only. |
| 21 | **App icon** | `mipmap-anydpi-v26/ic_launcher.xml` + `ic_launcher_foreground/bg` | Vector | adaptive bg + fg + monochrome | Manifest `android:icon` currently `@drawable/ic_shield_on` (not the mipmap). | In scope. **No splash Activity** — launcher is `MainActivity`. Do not invent a splash screen. |

---

## 3) Real modules (Protection / Settings map only to these)

| Module | Where it lives | Do not invent |
|--------|----------------|---------------|
| AI Content Detection | Settings switches + threshold + vote chips + legacy model import | DNS / VPN / Safe Search / browser extension |
| App Blocking + whitelist filter | `AppListActivity` | Standalone whitelist screen, per-URL website blocker |
| Keyword filter | `KeywordActivity` | — |
| Schedule rules | `ScheduleActivity` | — |
| Accessibility / overlay / usage / notif / battery | `PermissionsActivity` + prompt | — |
| Commitment Lock | `TimeLockActivity` | — |
| PIN | Setup + Verify | Biometric unlock, Forgot-PIN reset |
| Reel reminder | `ReelReminderActivity` | Extra Islamic screens |
| 3-strike AI | Service + overlay + warning card | Changing thresholds / timing |

**Gender filter:** strings + detector constants exist; **no Settings UI**. Out of mock scope (do not invent a control).

---

## 4) Locked logic — do not touch

- `TempBlockManager.kt`
- `BlockingEngine.kt`
- `service/detection/**` (`AiDetector`, `FalsePositiveMemory`, `ReelScrollDetector`, `RulesEngine`, model import internals)
- Strike counting / `STRIKE_THRESHOLD` / `STRIKE_RESET_MS` / `showAiStrikeWarning` timing / `reportNotSensitive` in `GuardianAccessibilityService.kt`
- Block-overlay false-positive / unblock / relaunch behavior
- Room DAO / entity query logic

Cosmetic overlay/warning XML + text-color setters are allowed. ViewModel/layout binding changes for a new layout are allowed if business rules stay identical.

---

## 5) Copy / locale

- Default `values/strings.xml`: English with mixed Bengali (house tone).
- `values-bn/strings.xml`: full Bengali. `app_name` there is `গার্ডিয়ান শিল্ড` (locale translation — **do not rebrand**; EN name stays **Guardian Shield**).
- Onboarding page bodies are hardcoded Bengali in Kotlin.
- Time-lock chip confirm labels are hardcoded Bengali in `TimeLockActivity`.
- Overlay false-positive strings are Bengali in both locales.
- Reel reminder is Bengali.

Mocks use **real app copy**, Bengali where the live UI shows Bengali.

---

## 6) Unbound / leftover chrome (call out, don’t invent behavior)

| ID | Where | Reality |
|----|-------|---------|
| `btnDay`, `btnWeek`, `btnMonth` | Activity Log layout | Not referenced in `ActivityLogActivity`. No period in VM. |
| `txtBlockedAttempts` | Activity Log layout | Not bound. `+24% vs yesterday` string exists as static V3 copy — **not real analytics**. |
| `txtStatKeyword` | Dashboard layout | Never assigned. VM has `keywordBlocks`. |
| `txtStatTime` | Dashboard | Placeholder formula, not uptime. |
| Gender chips | strings only | No layout. |
| Biometric / Forgot PIN | V2.5 mock leftover | Not in PIN layouts. |
| Day/Week chart bars | Activity Log V3 layout | Visual only unless already static XML; VM has no series. |

---

## 7) Navigation map (preserve destinations)

```
MainActivity
 ├─ nav_dashboard  → DashboardFragment
 │    ├─ btnToggle           → toggleProtection / AccessibilityPrompt / TimeLock snackbar
 │    ├─ cardAppBlocking     → AppListActivity
 │    ├─ cardKeywords        → KeywordActivity
 │    ├─ cardWhitelist       → AppListActivity (user picks Whitelisted chip)
 │    └─ txtSeeAll           → ActivityLogActivity
 ├─ nav_logs        → start ActivityLogActivity (does not select tab)
 ├─ nav_protection  → ProtectionFragment
 │    ├─ cardAppBlocking     → AppListActivity
 │    ├─ cardKeyword         → KeywordActivity
 │    ├─ cardSchedule        → ScheduleActivity
 │    ├─ cardAi              → SettingsActivity
 │    └─ cardAccessibility   → SettingsActivity
 └─ nav_settings    → start SettingsActivity (PIN gate) (does not select tab)

SettingsActivity
 ├─ btnApps / Keywords / Schedule / Permissions / CommitmentLock → those activities
 ├─ btnChangePin → PinSetupActivity
 └─ model import / remove → existing pickers

BlockOverlayActivity
 ├─ btnHome      → HOME intent
 ├─ btnUnlock    → DelayUnlockActivity
 └─ btnMarkFalse → FalsePositiveMemory (AI only)

BlockedDetailActivity
 ├─ rowStayFocused / btnBackToApp → HOME
 ├─ rowViewActivity               → ActivityLogActivity
 ├─ rowWhitelist                  → AppListActivity
 └─ btnReport                     → finish()

Onboarding (firstRun) → then MainActivity / PIN setup path unchanged.
```

---

## 8) One UI 8 mock scope (this session)

Full self-contained HTML mockups (inline CSS, no external stylesheets) for every row in §2, including default / empty / loading-skeleton / error where the screen can have those states.

**Not mocked as new products:** DNS, VPN, Safe Search, biometric, Forgot PIN, standalone whitelist, splash Activity, gender-filter panel, Day/Week analytics.

**App name** remains **Guardian Shield**. Shield mark is refined, not replaced. No mascot.
