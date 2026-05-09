# Guardian Shield — v10 (2.1.0) Smart Tiered Detection

versionCode: 3 → **4**
versionName: 2.0.0 → **2.1.0**

## 🎯 Headline change — anti-aggressive policy

The user reported "App ta aggressive lagbe — safe content o block korce".
Root cause: v9 used a single boolean `isUnsafe()` and a low default threshold
(0.7) plus a `sexyThreshold = threshold * 0.55` rule, which meant a sexy
score as low as **0.385** would block. That fires constantly on portraits,
landscapes, and casual social-media frames.

v10 fixes this with **four-tier classification** — only the top tier blocks.

| Tier | Score range | Action |
|------|-------------|--------|
| `SAFE`        | combined < 0.30 | ignore |
| `NATURAL`     | 0.30 ≤ x < 0.55 | ignore |
| `SUGGESTIVE`  | 0.55 ≤ x < 0.75 (or sexy ≥ 0.60 alone) | **log only** — never blocks |
| `EXPLICIT`    | porn ≥ 0.78, hentai ≥ 0.75, OR combined ≥ 0.75 | **BLOCK** |

Per-class hard cutoffs: `PORN_BLOCK_THRESHOLD = 0.78`,
`HENTAI_BLOCK_THRESHOLD = 0.75`, `SEXY_LOG_THRESHOLD = 0.60` (sexy alone
**never** blocks).

## ✨ New features

### 1. Three-tier classification (`ContentTier`)
- `AiDetector.classify(bitmap, packageName)` returns
  `ClassificationResult(tier, pornScore, hentaiScore, sexyScore, combinedScore)`.
- Legacy `isUnsafe(bitmap)` kept as a thin wrapper — back-compat preserved.

### 2. Sensitivity preset (LOW / BALANCED / HIGH)
- New ChipGroup in Settings → Detection.
  - **Low** → threshold 0.85 — only obvious explicit content.
  - **Balanced** (default) → threshold 0.78 — blocks NSFW, ignores hot photos.
  - **High** → threshold 0.65 — catches more, may have false positives.
- The advanced manual slider (`AI Threshold`) still works and
  overrides the preset when moved away from its default.

### 3. EXPLICIT debounce — no more single-frame false positives
- `EXPLICIT_DEBOUNCE_MS = 3000` ms.
- `EXPLICIT_CONFIRM_COUNT = 2`.
- Block fires only when 2 consecutive EXPLICIT classifications occur within
  3 seconds. A single bad frame never blocks.
- Per-package `explicitHits` deque, bounded at `MAX_AI_SCAN_MAP = 50`.

### 4. Source-based 15-min auto-lock 🔒
- When EXPLICIT is **confirmed** (debounce cleared) on a known
  *content-source app*, that app is auto-locked for **15 minutes**
  (`AI_SOURCE_BLOCK_MS = 15 * 60 * 1000L`).
- No overlay arguments. No second chances. Re-opening the app inside the
  window goes straight to HOME + block overlay.
- New BlockReason: `AI_SOURCE_TIMED_BLOCK`.
- New persistent storage: Room table `timed_blocks` (Schema v2 → v3).
- New singleton: `TimedBlockManager` (hot StateFlow cache + Room persistence
  + auto-prune of expired entries).
- Recognised content-source apps include:
  - Social: Facebook, Messenger, Instagram, Twitter/X, TikTok, Snapchat,
    YouTube, Reddit, Pinterest, Tumblr, LinkedIn.
  - Messaging: Telegram, Telegram X, WhatsApp, WhatsApp Business, Discord,
    Viber, Skype.
  - Browsers: Chrome, Brave, Firefox, Edge, Opera, Samsung Internet,
    DuckDuckGo, UC Mobile, Kiwi, Vivaldi.
- System apps, launcher, IME, and whitelisted apps are NEVER timed-blocked.

### 5. Heavy-image-app threshold boost (+0.10)
- `KNOWN_SAFE_HEAVY_IMAGE_APPS` — Photos, Gallery, Camera, Maps, Earth,
  Contacts, Docs, Adobe Reader.
- Effective threshold for these apps is boosted by
  `HEAVY_IMAGE_APP_THRESHOLD_BOOST = 0.10`. Casual portrait / family
  photo / street-view skin tones stop spuriously triggering.

## 🔧 Updated defaults

| Constant | v9 | v10 |
|----------|-----|-----|
| `aiThreshold` (default slider value) | 0.7 | **0.78** |
| `NSFW_GATE_THRESHOLD` | 0.6 | **0.62** |
| Sexy block threshold | `0.55 × threshold` (≈ 0.385) | **never blocks** |

## 🗃 Schema changes (Room v2 → v3)

New table:
```sql
CREATE TABLE IF NOT EXISTS `timed_blocks` (
    `packageName` TEXT NOT NULL,
    `expiresAt`   INTEGER NOT NULL,
    `reason`      TEXT NOT NULL,
    `createdAt`   INTEGER NOT NULL,
    PRIMARY KEY(`packageName`)
)
```
Manual `MIGRATION_2_3` defined in `AppModule.kt`. No data wipe required.

## 📁 Files changed (16) / added (3)

**Added:**
- `app/src/main/java/com/guardian/shield/domain/model/ContentTier.kt`
- `app/src/main/java/com/guardian/shield/service/detection/TimedBlockManager.kt`
- (changelog block — this file)

**Modified:**
- `app/build.gradle.kts` — versionCode 3 → 4, versionName 2.0.0 → 2.1.0
- `app/src/main/java/com/guardian/shield/util/Constants.kt`
- `app/src/main/java/com/guardian/shield/util/AppClassifier.kt`
- `app/src/main/java/com/guardian/shield/service/detection/AiDetector.kt`
- `app/src/main/java/com/guardian/shield/service/detection/RulesEngine.kt`
- `app/src/main/java/com/guardian/shield/service/accessibility/GuardianAccessibilityService.kt`
- `app/src/main/java/com/guardian/shield/data/local/datastore/GuardianPreferences.kt`
- `app/src/main/java/com/guardian/shield/data/local/db/Entities.kt`
- `app/src/main/java/com/guardian/shield/data/local/db/Daos.kt`
- `app/src/main/java/com/guardian/shield/data/local/db/GuardianDatabase.kt`
- `app/src/main/java/com/guardian/shield/di/AppModule.kt`
- `app/src/main/java/com/guardian/shield/domain/model/BlockEvent.kt`
- `app/src/main/java/com/guardian/shield/viewmodel/SettingsViewModel.kt`
- `app/src/main/java/com/guardian/shield/ui/settings/SettingsActivity.kt`
- `app/src/main/res/layout/activity_settings.xml`
- `app/src/main/res/values/strings.xml`

## ⚙️ How it behaves now (decision flow)

```
onAccessibilityEvent (window change)
  └── RulesEngine.evaluatePackage(pkg)
        ├── always-allowed (system / launcher / IME / own pkg) → ALLOW
        ├── whitelisted                                         → ALLOW
        ├── manually blocked                                    → BLOCK [APP_BLOCKED]
        ├── ★ timed-block active                                → BLOCK [AI_SOURCE_TIMED_BLOCK]
        ├── schedule rule active                                → BLOCK [SCHEDULE_BLOCKED]
        └── otherwise                                           → ALLOW + AI scan

triggerAiCheck(pkg)
  └── takeScreenshot
        └── AiDetector.classify(bitmap, pkg)
              ├── SAFE / NATURAL  → no action
              ├── SUGGESTIVE      → log only (no block)
              └── EXPLICIT
                    ├── 1st hit  → record + wait
                    └── 2nd hit within 3 s
                          ├── add 15-min timed-block (if source-app)
                          └── immediate BLOCK [AI_DETECTION]
```

## 🚀 How to deploy

1. Replace the 16 modified + 3 new files (or push the entire v10 tree).
2. Run the GitHub Actions debug workflow → `./gradlew assembleDebug`.
3. Install over the existing v9 build (`adb install -r app-debug.apk`).
   Room migration v2 → v3 runs automatically — **no data wipe needed**.
4. **First launch after update:**
   - Open Settings → "Detection Sensitivity" → choose your preset.
     Default (Balanced) is the recommended anti-aggressive setting.
   - Optional: re-confirm permissions on the Permission Health screen.

---

# Guardian Shield — v9 (2.0.0) Performance Pass

versionCode: 2 → 3, versionName: 1.1.0 → 2.0.0

(see git history for the full v8/v9 fix log)
