#!/usr/bin/env bash
# Rebuilds ALL ephemeral local gate binaries after a sandbox wipe:
#   /home/user/gate/bin/aapt2                    (pypi wheel 'aapt2' 0.2.1)
#   /home/user/gate/android-30.jar               (Sable/android-platforms, aapt2 link target)
#   /home/user/gate/android-35.jar               (Sable/android-platforms, kotlinc classpath)
#   /home/user/gate/unpacked/jdk4py/java-runtime (pypi wheel 'jdk4py' 25.0.2.1 — JDK for kotlinc)
#   /home/user/gate/unpacked/run_kotlin_kernel   (pypi wheel 'kotlin-jupyter-kernel' 0.19.0.944
#                                                 — ships the K2 compiler jars the gate runs)
# Then runs ALL THREE gates: id-contract, aapt2 res, kotlinc typecheck.
# The gate SCRIPTS + stubs live in this repo (guardian-redesign/tools/), so only
# these binaries ever need re-downloading. Requires: pip, unzip, gh (authenticated).
set -e
REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
GATE_HOME="${GATE_HOME:-/home/user/gate}"
mkdir -p "$GATE_HOME/wheels" "$GATE_HOME/bin" "$GATE_HOME/unpacked"
cd "$GATE_HOME/wheels"

# --- aapt2 -----------------------------------------------------------------
if [ ! -x "$GATE_HOME/bin/aapt2" ]; then
  [ -f aapt2-0.2.1-py3-none-any.whl ] || pip download aapt2==0.2.1 --no-deps -d . -q
  rm -rf aapt2x && mkdir -p aapt2x && (cd aapt2x && unzip -qo ../aapt2-*-py3-none-any.whl)
  cp aapt2x/aapt2/bin/Linux/aapt2 "$GATE_HOME/bin/aapt2"
  chmod +x "$GATE_HOME/bin/aapt2"
fi

# --- JDK 25 (for running the Kotlin compiler) ------------------------------
if [ ! -x "$GATE_HOME/unpacked/jdk4py/java-runtime/bin/java" ]; then
  ls jdk4py-25.0.2.1-*.whl >/dev/null 2>&1 || pip download jdk4py==25.0.2.1 --no-deps -d . -q
  rm -rf jdkx && mkdir -p jdkx && (cd jdkx && unzip -qo ../jdk4py-25.0.2.1-*.whl)
  mkdir -p "$GATE_HOME/unpacked"
  cp -r jdkx/jdk4py "$GATE_HOME/unpacked/jdk4py"
  chmod +x "$GATE_HOME/unpacked/jdk4py/java-runtime/bin/java"
fi

# --- Kotlin compiler (kotlin-jupyter-kernel all-in-one jar + reflect/std) ---
if ! ls "$GATE_HOME/unpacked/run_kotlin_kernel/jars/kotlin-jupyter-kernel-"*-all.jar >/dev/null 2>&1; then
  ls kotlin_jupyter_kernel-0.19.0.944-*.whl >/dev/null 2>&1 || \
    pip download kotlin-jupyter-kernel==0.19.0.944 --no-deps -d . -q
  rm -rf kjkx && mkdir -p kjkx && (cd kjkx && unzip -qo ../kotlin_jupyter_kernel-0.19.0.944-*.whl)
  cp -r kjkx/run_kotlin_kernel "$GATE_HOME/unpacked/run_kotlin_kernel"
fi

# --- android.jar (both API levels) ------------------------------------------
[ -f "$GATE_HOME/android-30.jar" ] || \
  gh api repos/Sable/android-platforms/contents/android-30/android.jar \
    -H "Accept: application/vnd.github.raw" > "$GATE_HOME/android-30.jar"
[ -f "$GATE_HOME/android-35.jar" ] || \
  gh api repos/Sable/android-platforms/contents/android-35/android.jar \
    -H "Accept: application/vnd.github.raw" > "$GATE_HOME/android-35.jar"

# keep kotlinc_gate.sh's expected layout happy — aapt2 unpacked path
mkdir -p "$GATE_HOME/unpacked/aapt2/bin/Linux"
[ -f "$GATE_HOME/unpacked/aapt2/bin/Linux/aapt2" ] || \
  cp "$GATE_HOME/bin/aapt2" "$GATE_HOME/unpacked/aapt2/bin/Linux/aapt2"

echo "== tool versions =="
"$GATE_HOME/bin/aapt2" version
"$GATE_HOME/unpacked/jdk4py/java-runtime/bin/java" -version 2>&1 | head -1

python3 "$REPO_ROOT/guardian-redesign/tools/id_contract_gate.py"
python3 "$REPO_ROOT/guardian-redesign/tools/aapt2_res_gate.py"
"$REPO_ROOT/guardian-redesign/tools/kotlinc-gate/kotlinc_gate.sh" "$REPO_ROOT"
