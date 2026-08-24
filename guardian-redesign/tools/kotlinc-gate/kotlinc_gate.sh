#!/usr/bin/env bash
# kotlinc_gate.sh — full Kotlin type-check of the app without Gradle/SDK.
#
#   stubs-src/*.kt            -> minimal API surface of every external lib
#                                (androidx, material, dagger, room, work,
#                                 datastore, security, timber, tflite, kotlinx)
#                                so the same sources compile in CI too.
#   build/generated/*.kt      -> R.kt / BuildConfig.kt / *Binding.kt,
#                                GENERATED from res/ by gen_gate_sources.py.
#   app/src/main/java/**.kt   -> the REAL app sources (the thing we gate).
#
# Requirements: a JDK, a kotlin-compiler classpath and android.jar.
# All three live under GATE_HOME (default /home/user/gate); layout:
#   $GATE_HOME/unpacked/jdk4py/java-runtime/bin/java
#   $GATE_HOME/unpacked/run_kotlin_kernel/jars/kotlin-jupyter-kernel-*-all.jar
#   $GATE_HOME/unpacked/run_kotlin_kernel/jars/kotlin-reflect-*.jar
#   $GATE_HOME/unpacked/run_kotlin_kernel/jars/kotlin-stdlib-*.jar
#   $GATE_HOME/android-35.jar
#
# Usage:  guardian-redesign/tools/kotlinc-gate/kotlinc_gate.sh [repo_root]
set -euo pipefail

REPO_ROOT="${1:-$(cd "$(dirname "$0")/../.." && pwd)}"
TOOLS="$REPO_ROOT/guardian-redesign/tools/kotlinc-gate"
STUBS_SRC="$TOOLS/stubs-src"
GEN="$TOOLS/build/generated"
STUBS_OUT="$TOOLS/build/stubs-out"
APP_OUT="$TOOLS/build/app-out"
APP_SRC="$REPO_ROOT/app/src/main/java"
RES="$REPO_ROOT/app/src/main/res"

GATE_HOME="${GATE_HOME:-/home/user/gate}"
JAVA="$GATE_HOME/unpacked/jdk4py/java-runtime/bin/java"
JARS="$GATE_HOME/unpacked/run_kotlin_kernel/jars"
ANDROID_JAR="$GATE_HOME/android-35.jar"

KOTLIN_CP="$(ls "$JARS"/kotlin-jupyter-kernel-*-all.jar):$(ls "$JARS"/kotlin-reflect-*.jar)"
STDLIB="$(ls "$JARS"/kotlin-stdlib-*.jar)"

echo "== gen gate sources =="
python3 "$TOOLS/gen_gate_sources.py" "$RES" "$GEN"

kotlinc() {
    "$JAVA" -Xmx3g -classpath "$KOTLIN_CP" \
        org.jetbrains.kotlin.cli.jvm.K2JVMCompiler -nowarn "$@"
}

echo "== compile stubs ($(ls "$STUBS_SRC"/*.kt | wc -l) files + R/BuildConfig) =="
mkdir -p "$STUBS_OUT" "$APP_OUT"
kotlinc -jvm-target 17 \
    -classpath "$STDLIB:$ANDROID_JAR" \
    -d "$STUBS_OUT" \
    "$STUBS_SRC"/*.kt "$GEN/R.kt" "$GEN/BuildConfig.kt"

echo "== compile app ($(find "$APP_SRC" -name '*.kt' | wc -l) sources + Bindings) =="
# shellcheck disable=SC2046
kotlinc -jvm-target 17 \
    -classpath "$STDLIB:$ANDROID_JAR:$STUBS_OUT" \
    -d "$APP_OUT" \
    $(find "$APP_SRC" -name '*.kt' | sort) "$GEN/Bindings.kt"

APP_CLASSES=$(find "$APP_OUT" -name '*.class' | wc -l)
STUB_CLASSES=$(find "$STUBS_OUT" -name '*.class' | wc -l)
echo "GATE PASS — app classes: $APP_CLASSES, stub classes: $STUB_CLASSES"
