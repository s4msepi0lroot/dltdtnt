#!/usr/bin/env sh
set -eu
cd "$(dirname "$0")/.."
mkdir -p build/core-tests
P=src/main/java/dev/vitalstages/health
C=src/main/java/dev/vitalstages/chat
T=src/test/java/dev/vitalstages/health
java -m jdk.compiler/com.sun.tools.javac.Main --release 21 -d build/core-tests \
  "$P/Physiology.java" "$P/BodyPart.java" "$P/FractureProfile.java" "$P/RecoveryClock.java" "$P/Wound.java" \
  "$P/TreatmentState.java" "$P/MedicalRules.java" "$C/PhraseBook.java" \
  "$T/PhysiologyTest.java" "$T/Mvp2CoreTest.java" "$T/Mvp3CoreTest.java"
java -cp build/core-tests dev.vitalstages.health.PhysiologyTest
java -cp build/core-tests dev.vitalstages.health.Mvp2CoreTest
java -cp build/core-tests dev.vitalstages.health.Mvp3CoreTest
# Настоящие NBT/Gson: gradle mvp2NbtTest mvp3IntegrationTest (Minecraft classpath).
