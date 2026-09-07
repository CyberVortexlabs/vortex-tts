@echo off
setlocal
set "APP_DIR=%~dp0"
set "GRADLE_VERSION=8.7"
set "DIST_DIR=%APP_DIR%.gradle-local"
set "DIST_ZIP=%DIST_DIR%\gradle-%GRADLE_VERSION%-bin.zip"
set "DIST_HOME=%DIST_DIR%\gradle-%GRADLE_VERSION%"
set "GRADLE_BIN=%DIST_HOME%\bin\gradle.bat"

if not exist "%GRADLE_BIN%" (
  if not exist "%DIST_DIR%" mkdir "%DIST_DIR%"
  if not exist "%DIST_ZIP%" (
    powershell -NoProfile -ExecutionPolicy Bypass -Command "Invoke-WebRequest -UseBasicParsing -Uri 'https://services.gradle.org/distributions/gradle-%GRADLE_VERSION%-bin.zip' -OutFile '%DIST_ZIP%'"
    if errorlevel 1 exit /b 1
  )
  powershell -NoProfile -ExecutionPolicy Bypass -Command "Expand-Archive -Force '%DIST_ZIP%' '%DIST_DIR%'"
  if errorlevel 1 exit /b 1
)

call "%GRADLE_BIN%" %*
endlocal
