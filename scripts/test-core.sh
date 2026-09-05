#!/usr/bin/env sh
set -eu
cd "$(dirname "$0")/.."
mkdir -p build/core-tests
# Можно запускать напрямую без Gradle/Minecraft. Нужен JDK 21+.
java -m jdk.compiler/com.sun.tools.javac.Main --release 21 -d build/core-tests   src/main/java/dev/vitalstages/health/Physiology.java   src/test/java/dev/vitalstages/health/PhysiologyTest.java
java -cp build/core-tests dev.vitalstages.health.PhysiologyTest
