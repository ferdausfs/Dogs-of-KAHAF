# Guardian Shield — "Sentinel" Design System (spec)

Companion written spec to `mocks/design-tokens.html` (authoritative, with computed contrast ratios).
Light-on-dark, from scratch. App name **Guardian Shield** unchanged; shield mark refined, not replaced.

## 1. Rationale (why dark + emerald)

A content-blocker is an always-on, always-watching product. Dark:
- lowers OLED drain and distraction for a service that runs 24/7,
- reads as "vigilant / serious" (the security-product genre convention),
- blends with system scrims for the overlay UI (`BlockOverlayActivity`, strike card),
- continuity: the app is dark-only today (no light theme exists — introducing one would be scope creep).

A **single phosphor-emerald accent** is the product's one semantic truth — **green = "protected / all-clear"**.
Every green element means the same thing; amber = warning, red = blocked/error, violet = AI, blue = info.
This is deliberately *not* the previous blue-on-navy palette, and it avoids the "default Material You"
look by (a) neutral green-graphite surfaces instead of blue-tinted navy, (b) one strong accent used with
restraint, and (c) a single stroke-based icon family replacing the emoji-as-icon usage.

## 2. Color tokens

| Token | Hex | Role |
|---|---|---|
| `bg` | `#0B0F0D` | canvas / window background / overlay scrim |
| `bg_elevated` | `#101612` | app bar / bottom nav |
| `surface1` | `#121915` | tab wells, nested regions |
| `surface2` | `#161F1A` | standard cards |
| `surface3` | `#1C2721` | elevated cards / inputs |
| `surface4` | `#232F29` | highest / hover / pressed |
| `on_surface` | `#E9F1EC` | primary text |
| `on_variant` | `#A7B9AE` | secondary text |
| `on_dim` | `#80918A` | tertiary / labels |
| `primary` | `#3BE39A` | brand accent |
| `on_primary` | `#04231A` | on accent |
| `primary_container` | `#123B2C` (gradient → `#0E2A1F`) | hero / status surfaces |
| `on_primary_container` | `#B9F7DA` | on container |
| `success` | `#5CF0AC` / container `#0E2B1E` | protected / granted |
| `info` | `#7CC4FF` / container `#12283A` | informational |
| `ai` | `#B79BFF` / container `#241B3D` | AI detection |
| `warning` | `#FFC94D` / container `#2A2210` | warning / lock banner |
| `error` | `#FF8F86` / container `#3A1E1C` | blocked / denied |
| `border_subtle` | `rgba(233,241,236,.07)` | 1dp card border |
| `border_strong` | `rgba(233,241,236,.15)` | hero border |

### Contrast (WCAG 2.1, computed — full table in `design-tokens.html`)

| Pair | Ratio | Grade |
|---|---|---|
| on_surface on bg | 16.78 : 1 | AAA |
| on_variant on bg | 9.37 : 1 | AAA |
| on_dim on surface2 | 4.75 : 1 | AA |
| on_dim on surface3 | 4.65 : 1 | AA |
| primary on bg | 11.61 : 1 | AAA |
| on_primary on primary | 10.03 : 1 | AAA |
| on_primary_container on container | 10.30 : 1 | AAA |
| error on bg | 8.75 : 1 | AAA |

Every text/background pair meets AA (≥ 4.5:1); primary text pairs exceed 10:1. Badge/overline sizes
reuse the same colors and stay AA.

## 3. Typography

System stack: `Inter → -apple-system → Segoe UI → Roboto → Noto Sans Bengali`; `ui-monospace` for
package names, counts, scores, PIN. Tabular figures for numerics.

| Role | Size/line | Weight | Tracking | Use |
|---|---|---|---|---|
| Display | 30 / 38 | 800 | −0.02 | hero numerals, big stats |
| Headline | 22 / 28 | 700 | −0.01 | screen titles |
| Title | 17 / 24 | 700 | 0 | card titles |
| Body | 14 / 20 | 400 | 0 | default text |
| Label | 12 / 16 | 600 | 0 | secondary, captions |
| Overline | 11 / 16 | 800 | +0.13 caps | section headers, kickers |
| Badge | 10 / 14 | 800 | +0.05 caps | pills, status |

## 4. Shape, elevation, spacing, motion

- **Radius:** 10 (icons/inputs) · 14 (search, lock banner) · 20 (cards) · 28 (hero, buttons, stats) · pill (chips/nav).
- **Elevation:** expressed as **1px inner top highlight + soft 24–60px shadow + 1px border** (not flat borders).
  Hero/overlay add a radial glow. Cards sit at 0dp; only hero/overlay "float".
- **Spacing (4pt):** 4 / 8 / 12 / 16 / 20 / 24 / 32. Screen padding 16, card padding 16 (hero 20–24),
  section gap 24, card gap 12, chip gap 8.
- **Motion:** fade-through screen transitions (200ms); container-transform for activity-row → details;
  card press 0.985 scale + ripple (150ms); switch glide 180ms; staggered list entrance 40ms/row (cap 200ms);
  shimmer skeleton 1.4s; overlay scrim fade + card rise 240ms; strike card pulse ×2. No custom/gimmicky
  animation — all Material-motion patterns. Pull-to-refresh not implemented today (out of scope).

## 5. Iconography

One family: **24dp grid, 1.8 stroke, rounded caps, consistent optical weight** (see `design-tokens.html`
grid). Set: shield, shield_x, lock, lock_open, check, check_circle, warning, info, history, tune, apps,
search, add, delete, edit, back, chevron, backspace, fingerprint, timer, accessibility, battery, bell,
layers, usage, key, book, flag, block, home, chip(AI), eye, moon, upload, download, people, male, female,
scale, refresh. Replaces **all** emoji-as-icon usage (🔤 📱 ⏰ 🔐 🔧 🚫 ⚠️ ✅ …). Shield mark refined, not replaced.

## 6. Component library (states defined per component — see `mocks/components.html`)

Buttons (primary / tonal / outline / danger / ghost / text; default · pressed · disabled) · switches
(on / off / disabled) · chips (filter / choice, on / off) · badges (success / error / ai / info / warn / neutral)
· stat tiles · list rows (app row, event row, setting row, permission row) · segmented control · slider ·
search field · keypad + PIN dots · empty states · loading skeleton · lock banner · snackbar/toast · dialog
(AlertDialog — no bottom sheets exist today) · extended FAB.
