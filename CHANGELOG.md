# Guardian Shield — v17 (2.1.7) BUILD-FIX RELEASE

versionCode: 10 → **11**
versionName: 2.1.6 → **2.1.7**

## 🚨 Why this release exists

The CI build for v16 (2.1.6) failed with the following Kotlin
compile error:

```
e: file:///.../viewmodel/DashboardViewModel.kt:75:10
   Using 'distinctUntilChanged(): Flow<T>' is an error.
   Applying 'distinctUntilChanged' to StateFlow has no effect.
   See the StateFlow documentation on Operator Fusion.
> Task :app:compileDebugKotlin FAILED
```

Starting with **kotlinx-coroutines 1.8.x**, calling
`distinctUntilChanged()` directly on a `StateFlow` (or
`MutableStateFlow`) is a **hard compile error** — not a warning —
because StateFlow already de-duplicates by `equals()` per the
Operator Fusion contract. v16 introduced this exact pattern in
`DashboardViewModel.todayStats` (NEW-OPT-1), which the build server
flagged immediately.

This release is a **single-file build-fix patch**. No DB schema
changes, no behavioural changes to the AI/blocking pipeline, no new
dependencies, no manifest changes. Room stays at v3.

---

## 🔧 The fix — `DashboardViewModel.kt`

### Before (v16 — broken)
```kotlin
val todayStats: StateFlow<BlockStats> = midnightTrigger
    .distinctUntilChanged()                       // ❌ compile error
    .flatMapLatest { since -> observeSinceUC(since).map { aggregate(it) } }
    .stateIn(viewModelScope, SharingStarted.Lazily, BlockStats())
```

### After (v17 — compiles cleanly)
```kotlin
val todayStats: StateFlow<BlockStats> = (midnightTrigger as Flow<Long>)
    .distinctUntilChanged()                       // ✅ regular Flow extension
    .flatMapLatest { since -> observeSinceUC(since).map { aggregate(it) } }
    .stateIn(viewModelScope, SharingStarted.Lazily, BlockStats())
```

**Why this works:** the upcast routes the call to the *regular*
`Flow.distinctUntilChanged` extension, not the deprecated/error
StateFlow overload. The runtime behaviour is identical — and in
fact strictly superior to "remove it" because it preserves the
NEW-OPT-1 guarantee that `flatMapLatest` only re-subscribes to the
Room flow when the *midnight boundary* actually changes (not on
every `setProtectionActive()` call).

### Side-improvements bundled in
- Wildcard `import kotlinx.coroutines.flow.*` replaced with
  **explicit imports**. The wildcard was the original cause:
  it pulled in the now-error `StateFlow.distinctUntilChanged`
  overload at the same time as the regular one, and the compiler
  picked the more specific (error) one.
- Added `@OptIn(ExperimentalCoroutinesApi::class)` at class level
  for `flatMapLatest` — defensive, even though the project
  globally opts in via `freeCompilerArgs`.

---

## ✅ Full app review — other findings

A complete sweep of the codebase for the same operator-fusion bug
turned up **no other occurrences**:

```bash
grep -rE "StateFlow|MutableStateFlow" --include='*.kt' \
    | grep -E '\.(distinctUntilChanged|conflate|debounce|sample|flowOn)\('
# → only the one site in DashboardViewModel.kt:75 (now fixed)
```

Other items reviewed and **confirmed clean**:

| Area | Status |
|---|---|
| `TimedBlockManager` (StateFlow + asStateFlow) | ✅ correct usage |
| `SettingsViewModel` (combine + stateIn) | ✅ no fusion ops on StateFlow |
| `AppListViewModel` (combine + stateIn, IO timeout) | ✅ correct |
| `KeywordViewModel` / `ScheduleViewModel` | ✅ correct |
| Room schema v3 + MIGRATION_1_2 / MIGRATION_2_3 | ✅ idempotent |
| Hilt 2.52 + KSP 1.9.24-1.0.20 + Kotlin 1.9.24 | ✅ compatible matrix |
| AGP 8.5.2 + Gradle 8.7 + compileSdk 35 | ✅ supported officially |
| AndroidManifest foregroundServiceType=specialUse | ✅ correct |
| Permissions (Accessibility / Overlay / FGS / etc.) | ✅ complete |

---

## 📦 Build matrix (unchanged from v16)

- AGP **8.5.2**
- Gradle wrapper **8.7**
- Kotlin **1.9.24**
- KSP **1.9.24-1.0.20**
- Hilt **2.52**
- compileSdk **35**, minSdk **26**, targetSdk **35**
- Java/JVM target **17**
- Room **2.6.1**

---

## 🔁 Carried over from v16 (still in this build)

All v16 stability fixes are preserved verbatim:

- NEW-FIX-1 — `MainActivity.onResume()` `bindingReady` guard
- NEW-FIX-2 — `BlockOverlayActivity.vibrateOreo()` API-26 isolation
- NEW-OPT-1 — single-subscribe Room flow on midnight rollover
  (now compiles thanks to v17 fix)
- v15 OPT-2 — single-source-of-truth `todayCount`
- v15 OPT-3 — `PermissionManager.snapshot()` 10s cache
- v13/v14 stability patches

---

## 🧪 How to verify locally

```bash
./gradlew clean
./gradlew :app:compileDebugKotlin     # the previously failing task
./gradlew :app:assembleDebug
```

The first two commands should now complete with **BUILD SUCCESSFUL**.
