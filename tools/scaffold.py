#!/usr/bin/env python3
# Writes all non-Java project files for BoozeCraft (gradle, mods.toml, CI, build scripts, README).
import os

ROOT = "/data/boozecraft"

FILES = {}

FILES["settings.gradle"] = r"""pluginManagement {
    repositories {
        mavenLocal()
        gradlePluginPortal()
        maven {
            name = 'NeoForged'
            url = 'https://maven.neoforged.net/releases'
        }
    }
}

plugins {
    id 'org.gradle.toolchains.foojay-resolver-convention' version '0.9.0'
}

rootProject.name = 'boozecraft'
"""

FILES["gradle.properties"] = r"""# Gradle
org.gradle.jvmargs=-Xmx3G
org.gradle.daemon=false
org.gradle.parallel=true
org.gradle.caching=true
org.gradle.configuration-cache=false

# Minecraft / NeoForge
minecraft_version=1.21.1
minecraft_version_range=[1.21.1]
neo_version=21.1.248
neo_version_range=[21.1.0,)
loader_version_range=[4,)

# Mod
mod_id=boozecraft
mod_name=BoozeCraft
mod_license=MIT
mod_version=1.0.0
mod_group_id=com.s4fmer.boozecraft
mod_authors=s4fmer
mod_description=Alcohol, soft drinks, energy drinks, glassware, a bar counter and a full drunkenness system.
"""

FILES["build.gradle"] = r"""plugins {
    id 'java-library'
    id 'net.neoforged.moddev' version '2.0.144'
}

version = mod_version
group = mod_group_id

base {
    archivesName = mod_id
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
}

neoForge {
    version = project.neo_version

    runs {
        client {
            client()
        }
        server {
            server()
            programArgument '--nogui'
        }
    }

    mods {
        "${mod_id}" {
            sourceSet sourceSets.main
        }
    }
}

tasks.withType(JavaCompile).configureEach {
    options.encoding = 'UTF-8'
    options.release = 21
}

tasks.named('jar', Jar).configure {
    manifest {
        attributes([
                'Specification-Title'     : mod_id,
                'Specification-Vendor'    : mod_authors,
                'Specification-Version'   : '1',
                'Implementation-Title'    : mod_name,
                'Implementation-Version'  : mod_version,
                'Implementation-Vendor'   : mod_authors
        ])
    }
}
"""

FILES["src/main/resources/META-INF/neoforge.mods.toml"] = r"""modLoader = "javafml"
loaderVersion = "[4,)"
license = "MIT"

[[mods]]
modId = "boozecraft"
version = "1.0.0"
displayName = "BoozeCraft"
authors = "s4fmer"
description = '''
Alcohol, soft drinks, energy drinks, coffee, glassware, three machines and a bar counter.
Drinking gives short starter effects, then tipsy / drunk / heavy drunk stages, blackouts,
hangovers and addiction. Server side logic only - works in multiplayer and on hybrid servers
such as Youer, Mohist and Arclight.
'''

[[dependencies.boozecraft]]
modId = "neoforge"
type = "required"
versionRange = "[21.1.0,)"
ordering = "NONE"
side = "BOTH"

[[dependencies.boozecraft]]
modId = "minecraft"
type = "required"
versionRange = "[1.21.1]"
ordering = "NONE"
side = "BOTH"
"""

FILES["src/main/resources/pack.mcmeta"] = r"""{
  "pack": {
    "description": "BoozeCraft resources",
    "pack_format": 34
  }
}
"""

FILES[".github/workflows/build.yml"] = r"""name: build

on:
  push:
    branches: [ "**" ]
  workflow_dispatch:

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - name: Checkout
        uses: actions/checkout@v4

      - name: Set up JDK 21
        uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: '21'

      - name: Set up Gradle
        uses: gradle/actions/setup-gradle@v4
        with:
          gradle-version: '8.12'

      - name: Build
        run: gradle build --no-daemon --stacktrace

      - name: Upload jar
        uses: actions/upload-artifact@v4
        with:
          name: boozecraft-jar
          path: build/libs/*.jar
          if-no-files-found: error
"""

FILES["build.sh"] = r"""#!/usr/bin/env bash
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
"""

FILES["build.bat"] = r"""@echo off
rem One-click build on Windows. Requires JDK 21 and internet access.
setlocal
set GRADLE_VERSION=8.12
cd /d "%~dp0"

where javac >nul 2>nul
if errorlevel 1 (
  echo [!] JDK 21 not found. Install Temurin/Corretto 21 and run again.
  exit /b 1
)

where gradle >nul 2>nul
if not errorlevel 1 (
  echo [*] Using system Gradle
  gradle build --no-daemon
  goto done
)

if not exist ".gradle-dist\gradle-%GRADLE_VERSION%\bin\gradle.bat" (
  echo [*] Downloading Gradle %GRADLE_VERSION% ...
  if not exist ".gradle-dist" mkdir ".gradle-dist"
  powershell -NoProfile -Command "Invoke-WebRequest -Uri 'https://services.gradle.org/distributions/gradle-%GRADLE_VERSION%-bin.zip' -OutFile '.gradle-dist\gradle.zip'"
  powershell -NoProfile -Command "Expand-Archive -Force '.gradle-dist\gradle.zip' '.gradle-dist'"
  del ".gradle-dist\gradle.zip"
)

call ".gradle-dist\gradle-%GRADLE_VERSION%\bin\gradle.bat" build --no-daemon

:done
echo.
echo [+] Done. The jar is in build\libs\
dir /b build\libs\*.jar
endlocal
"""

FILES[".gitignore"] = r"""build/
.gradle/
.gradle-dist/
run/
runs/
*.iml
.idea/
.vscode/
.DS_Store
"""

FILES["LICENSE"] = r"""MIT License

Copyright (c) 2026 s4fmer

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
"""

def write(path, text):
    full = os.path.join(ROOT, path)
    os.makedirs(os.path.dirname(full), exist_ok=True)
    with open(full, "w", encoding="utf-8") as handle:
        handle.write(text)

for path, text in FILES.items():
    write(path, text)

os.chmod(os.path.join(ROOT, "build.sh"), 0o755)
print("scaffold written: %d files" % len(FILES))
