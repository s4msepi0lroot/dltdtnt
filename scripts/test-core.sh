#!/usr/bin/env sh
set -eu
cd "$(dirname "$0")/.."
mkdir -p build/core-tests
P=src/main/java/dev/vitalstages/health
T=src/test/java/dev/vitalstages/health
java -m jdk.compiler/com.sun.tools.javac.Main --release 21 -d build/core-tests   "$P/Physiology.java" "$P/BodyPart.java" "$P/FractureProfile.java" "$P/RecoveryClock.java" "$P/Wound.java"   "$T/PhysiologyTest.java" "$T/Mvp2CoreTest.java"
java -cp build/core-tests dev.vitalstages.health.PhysiologyTest
java -cp build/core-tests dev.vitalstages.health.Mvp2CoreTest
# Mvp2NbtTest требует реальный Minecraft classpath: gradle mvp2NbtTest.
