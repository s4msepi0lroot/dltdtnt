#!/usr/bin/env bash
# One-click build without a Gradle wrapper. Requires JDK 21 and internet access.
set -e
GRADLE_VERSION=8.12
cd "$(cd "$(dirname "$0")" && pwd)"

if ! command -v javac >/dev/null 2>&1; then
  echo "[!] JDK 21 not found (no javac). Install Temurin/Corretto 21 and run again."
  exit 1
 fi

if command -v gradle >/dev/null 2>&1; then
  echo "[*] Using system Gradle"
  gradle build --no-daemon
else
  DIST=".gradle-dist/gradle-${GRADLE_VERSION}/bin/gradle"
  if [ ! -x "$DIST" ]; then
    echo "[*] Downloading Gradle ${GRADLE_VERSION}..."
    mkdir -p .gradle-dist
    curl -L -o .gradle-dist/gradle.zip "https://services.gradle.org/distributions/gradle-${GRADLE_VERSION}-bin.zip"
    (cd .gradle-dist && unzip -q gradle.zip && rm -f gradle.zip)
  fi
  "$DIST" build --no-daemon
fi

echo
echo "[+] Done. The jar is in build/libs/"
ls -1 build/libs/*.jar || true
