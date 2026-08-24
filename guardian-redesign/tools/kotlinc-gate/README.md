# kotlinc-gate

Sandbox type-check gate for the whole app: compiles every app
`app/src/main/java/**.kt` source with plain `kotlinc` — no Gradle, no AGP, no
Android SDK — against:

1. **stubs-src/** — hand-kept minimal API stubs for every external library the
   app imports (androidx, Material, Dagger/Hilt, Room, WorkManager, DataStore,
   Security-crypto, Timber, TensorFlow Lite, kotlinx.coroutines). Keep these
   IN GIT: the sandbox `/home/user/gate` directory has been wiped multiple
   times mid-session and rebuilding 60+ stub files from scratch costs turns.
2. **build/generated/** — `R.kt`, `BuildConfig.kt` and `*Binding.kt` (ViewBinding
   shims), regenerated from `app/src/main/res` on every run by
   `gen_gate_sources.py`, so resource edits can never drift out of sync.

Run (from repo root):

```bash
guardian-redesign/tools/kotlinc-gate/kotlinc_gate.sh
```

Toolchain (not in git) is fetched once by `bootstrap_gate.sh`-style steps:

| piece            | default location                                        |
|------------------|----------------------------------------------------------|
| JDK 25           | `$GATE_HOME/unpacked/jdk4py/java-runtime/bin/java`       |
| kotlin-compiler  | `$GATE_HOME/unpacked/run_kotlin_kernel/jars/*-all.jar` + `kotlin-reflect` + `kotlin-stdlib` |
| android.jar      | `$GATE_HOME/android-35.jar`                              |

`GATE_HOME` env var overrides the default `/home/user/gate`. Exit code is the
compiler's: 0 = the FULL app type-checks against the stub world. Pair with:

- `../id_contract_gate.py` — every `android:id` in layouts matches the
  Kotlin-side binding usage contract.
- `../aapt2_res_gate.py` — real `aapt2 compile` + `link` of all resources.
