# Guardian Shield — Screen & Data-Binding Audit (design-language overhaul baseline)

**Branch:** `arena/01a00f6c-dogs-of-kahaf` · **Base:** `main` @ `57a71eb` (v2.5.4 / versionCode 18)
**Date:** 2026-08-17

## 0. What already landed on main (verified, not assumed)

- **v2.5.0 "Premium Dark" redesign** — merged. `ProtectionFragment`, `BlockedDetailActivity`,
  `fragment_protection.xml`, `activity_blocked_detail.xml`, 4-tab bottom nav all present.
- **Strike-1/2 warning card** — merged (v2.5.2), **including the "Not sensitive" report button**
  (v2.5.4). Confirmed in `view_strike_warning.xml` (`btnNotSensitive`) + `GuardianAccessibilityService.reportNotSensitive()`.
  Auto-dismiss = `STRIKE_WARNING_AUTO_DISMISS_MS = 3_500L` (3.5 s) — must be preserved exactly.
- No `AGENT_LOG.md` exists in the repo (only `COMPILE_REVIEW_REPORT.md`). Session history read from there.

## 1. Layout technology (checked, not assumed)

**XML Views + ViewBinding + Material3. No Jetpack Compose.** Every screen is an `AppCompatActivity`
(or `Fragment`) inflating an XML layout via generated `*Binding`. RecyclerViews use `ListAdapter` +
DiffUtil. Navigation is activity-based (no NavComponent). Dark theme only (`Theme.Material3.Dark`).

## 2. Complete screen inventory (every Activity / Fragment / overlay)

| # | Screen | Class | Layout | States today |
|---|--------|-------|--------|--------------|
| 1 | Home shell | `MainActivity` | `activity_main.xml` | toolbar + fragment host + bottom nav (Home/Activity/Protection/Settings) |
| 2 | Dashboard | `DashboardFragment` | `fragment_dashboard.xml` | active / paused / service-off (3-way render); empty list handled by adapter |
| 3 | Protection hub | `ProtectionFragment` | `fragment_protection.xml` | active / paused / off |
| 4 | Activity log | `ActivityLogActivity` | `activity_log.xml` | filters All/AI/Keyword/App/Schedule; empty; count |
| 5 | Settings | `SettingsActivity` | `activity_settings.xml` | PIN-gated; lock-banner; controls enabled/disabled |
| 6 | App blocking | `AppListActivity` | `activity_app_list.xml` + `item_app_rule.xml` | search; 3 tabs; lock banner; per-row switches |
| 7 | Keywords | `KeywordActivity` | `activity_keyword.xml` + `item_keyword.xml` + `dialog_add_keyword.xml` | empty; swipe-to-delete; FAB |
| 8 | Schedule | `ScheduleActivity` | `activity_schedule.xml` + `item_schedule_rule.xml` + `dialog_schedule_editor.xml` | empty; FAB; edit/delete |
| 9 | Commitment Lock | `TimeLockActivity` | `activity_time_lock.xml` | locked / unlocked states + cooldown note |
| 10 | Permission health | `PermissionsActivity` | `activity_permissions.xml` (+ unused `item_permission_row.xml`) | per-row granted/FIX |
| 11 | PIN setup | `PinSetupActivity` | `activity_pin_setup.xml` | enter/confirm; dots; keypad |
| 12 | PIN verify | `PinVerifyActivity` | `activity_pin_verify.xml` | dots; wrong-attempts; lockout snackbar |
| 13 | Block overlay (strike 3 / full block) | `BlockOverlayActivity` | `activity_block_overlay.xml` | AI temp-block vs non-AI; false-positive "Mark false" (AI only); no unlock on temp block |
| 14 | Blocked content details | `BlockedDetailActivity` | `activity_blocked_detail.xml` | info rows + actions |
| 15 | Accessibility prompt | `AccessibilityPromptActivity` | `activity_accessibility_prompt.xml` | single CTA |
| 16 | Delay unlock | `DelayUnlockActivity` | `activity_delay_unlock.xml` | countdown |
| 17 | Onboarding (first run) | `OnboardingActivity` + `OnboardingPageFragment` | `activity_onboarding.xml` + `fragment_onboarding_page.xml` | 4 pages (Welcome/Features/Permissions/PIN) |
| 18 | Reel reminder (Islamic) | `ReelReminderActivity` | `activity_reel_reminder.xml` | hadith + CTA |
| 19 | **Strike-1/2 warning card** | — (WindowManager overlay, not an Activity) | `view_strike_warning.xml` | strike 1 & 2 only; "Not sensitive" audit button; 3.5 s auto-dismiss |
| 20 | Event list row | `BlockEventAdapter` | `item_block_event.xml` | used by Dashboard + Activity log; tap → details; long-press → delete |

**Do not invent screens.** No DNS/VPN/browser modules. No standalone whitelist activity (whitelist is
a filter tab inside App List). No side drawer (bottom nav only). No standalone "Safe Search" screen
(keyword filter). Protected-time figure is derived (`hours = totalBlocks/5`), not real uptime — keep
mapped to real `totalBlocks` in any redesign.

## 3. Data bindings per screen (must be preserved 1:1)

### Dashboard (`DashboardFragment`)
- `uiState` → `render(state)`: `txtStatusTitle`, `txtStatusSubtitle`, `imgShield` (on/off), `btnToggle`
  text (Enable/Resume/Pause), `txtProtectionBadge` (+ color), `txtStatTotal`, `txtStatAi`, `txtStatTime`,
  `adapter.submitList(state.recent)`.
- Clicks: `btnToggle`→`handleToggle()` (TimeLock guard → AccessibilityPrompt → `toggleProtection()`);
  `cardAppBlocking`→AppList; `cardKeywords`→Keyword; `cardWhitelist`→AppList; `txtSeeAll`→ActivityLog.
- `shieldGlow`/`imgShield` pulse animators (`startShieldPulse`/`stopShieldPulse`).
- Adapter: `txtPackage`, `txtReason` (+emoji), `txtTime`, `txtTimeInline`, `txtBadgeBlocked`, `badge`
  tint, `imgAppIcon`; root click→BlockedDetail (extras PACKAGE/CATEGORY/SOURCE/TIME); long-press→delete.

### Protection (`ProtectionFragment`)
- `uiState` → `txtProtectionTitle`, `txtProtectionSubtitle`, `imgShield`, `txtBadgeActive` (+color).
- Clicks: `cardAppBlocking`, `cardKeyword`, `cardSchedule`, `cardAi`, `cardAccessibility` → activities.

### Activity log (`ActivityLogActivity`)
- `uiState` → `adapter.submit(events)`, `txtEmpty` visibility, `txtCount`, `updateChipSelection(filter)`.
- Chips `chipAll/Ai/Keyword/App/Schedule` → `setFilter`. (Period Day/Week/Month buttons are decorative.)

### App list (`AppListActivity`)
- `lockBanner` + `txtLockRemaining` (TimeLock); `switchHero` (always-on guard + snackbar);
  `chipAll/Blocked/Whitelisted` → `setFilter`; `editSearch` TextWatcher → `setQuery`;
  `recycler` ← `state.apps`.
- Adapter per row: `txtAppName`, `txtPackage`, `imgIcon`, `switchBlock`, `switchWhitelist` (hidden when
  blocked — one-way rule), `txtStatusBadge` (BLOCKED/ALLOWED), `imgLockIcon`, `txtCategory` (heuristic),
  `viewLeftIndicator` color.

### Settings (`SettingsActivity`)
- PIN gate → `PinVerifyActivity` (launcher), else `initUI()` once.
- `lockBanner` + `txtLockRemaining`; `editEnabled` disables the whole control set.
- Bindings: `switchKeyword`, `switchAi`, `sliderDelay`+`txtDelayValue`, `sliderGuardianThreshold`+
  `txtGuardianThresholdValue`, `chipVote1-4`, `chip15/30/60min`, `btnImportLegacy`/`btnRemoveLegacy`,
  `txtLegacyStatus` (`ModelSlotUi`), `btnChangePin`; nav `btnApps/Keywords/Schedule/Permissions/CommitmentLock`.

### Keywords / Schedule
- `lockBanner` + `txtLockRemaining`; FAB hidden when locked; `txtEmpty` visibility; RecyclerView.
- KeywordAdapter: `txtKeyword`, `badge` (REGEX/PLAIN), `btnDelete`. Swipe-to-delete.
- ScheduleAdapter: `txtPackage`, `txtSchedule`, `btnEdit`; long-press delete.

### PIN setup / verify
- Keypad `btn0-9`, `btnDel`, `btnOk`; dots `dot1-6` (`dot_filled`/`dot_empty`); `txtPrompt` (setup);
  Snackbars for min-length / mismatch / saved / wrong-attempts / lockout / not-set.

### Block overlay (`BlockOverlayActivity`)
- Extras PACKAGE/REASON/DETAIL. `txtPackage`, `txtReason` (+color), `txtCategory`, `cardTempBlock`+
  `txtTempBanner` (temp-block only, unlock hidden), `btnHome`→`goHome()`, `btnUnlock`→DelayUnlock
  (non-temp only), `btnMarkFalse` (AI blocks only) → `takePendingCandidate()`/`addSignature()`.
- Preserve `formatDuration` Bengali rendering + vibration + back-press→goHome.

### Accessibility prompt / Delay unlock / Onboarding / Reel reminder
- Prompt: `btnEnable` → accessibility settings. Delay: `txtPackage`, `txtCountdown`, `btnCancel`.
  Onboarding: `viewPager`, `btnSkip/Back/Next`, `indicatorContainer`; pages bind `txtIcon/txtHighlight/
  txtTitle/txtBody`. Reel: `imgIslamic`, `txtTitle`, `txtHadith`, `txtSubtitle`, `btnOpenQuran`, `btnContinue`.

### Strike-1/2 warning card (`view_strike_warning.xml` — WindowManager overlay)
- Width = screen − 40 dp; `Gravity.TOP|CENTER_HORIZONTAL`, `y = 18%` screen height.
- `txtStrikeKicker`, `txtStrikeTitle` (`সতর্কতা %d/%d`), `txtStrikeBody`, `btnNotSensitive`
  (audit-only → `reportNotSensitive` writes a `NOT_SENSITIVE` event; does NOT touch
  `FalsePositiveMemory`/`AiDetector`), card tap→dismiss, auto-dismiss 3.5 s.
- **Re-skin only. Do not alter timing (3.5 s), position, or the audit-report behavior.**

## 4. Hard-constraint boundaries (do not touch — cosmetic only)

- `service/detection/**` (`AiDetector`, `FalsePositiveMemory`, `ReelScrollDetector`, `ModelImportManager`, `RulesEngine`)
- `service/blocker/TempBlockManager.kt`, `BlockingEngine.kt`
- strike counting/threshold in `GuardianAccessibilityService.kt`
- Room DAO/entity query logic (`data/local/db/**`)
- All thresholds/timings in `util/Constants.kt`

Layout/theme/view-model *binding* changes are allowed where a visual layout requires them; business
rules, thresholds, and detection behavior must not change even incidentally.

## 5. Current design-system snapshot (to be replaced)

- Dark blue "Premium Dark": `bg_main #0E1116`, primary `#D0E4FF`, primary_container `#1A344F`,
  surfaces `#161C26→#2A3647`, success `#6EE7B7`, warning `#FBBF24`, error `#FFB4AB`, ai `#A78BFA`.
- Cards: 0dp elevation + 1dp 8%-white border, radius 12/20/28. Icons: hand-rolled vectors +
  heavy **emoji-as-icon** usage (🔤 📱 ⏰ 🔐 🔧 🚫 ⚠️ ✅ ☪️ 🖐 💪 🔑 🤖 🕐 ✋ 📝) — the single
  biggest "amateur" signal to fix in this overhaul.
- App icon: adaptive icon with `bg_main` background + **teal `#00E5CC`** shield foreground — a
  color that clashes with everything else in the app (real inconsistency to fix; the vector
  foreground + adaptive XML are fully editable in-repo, minSdk 26 ⇒ `mipmap-anydpi-v26` covers all devices).
- Splash: no splash activity; system default = `windowBackground` (`bg_main`) + Android 12 auto-splash
  from the launcher icon. (Flag: theming the window background color is in scope; a dedicated
  SplashScreen API screen is optional asset work — not silently skipped, see report.)
