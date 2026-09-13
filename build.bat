@echo off
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
