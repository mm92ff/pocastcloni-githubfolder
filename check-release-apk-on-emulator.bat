@echo off
setlocal EnableExtensions EnableDelayedExpansion

cd /d "%~dp0"

set "APP_ID=com.example.pocastcloni"
set "MAIN_ACTIVITY=%APP_ID%/.ui.main.MainActivity"
set "JAVA_HOME=C:\Program Files\Android\Android Studio\jbr"
set "ANDROID_SDK_ROOT=C:\Users\jemi\AppData\Local\Android\Sdk"
set "ANDROID_HOME=%ANDROID_SDK_ROOT%"
set "PATH=%JAVA_HOME%\bin;%ANDROID_SDK_ROOT%\platform-tools;%ANDROID_SDK_ROOT%\emulator;%PATH%"

set "ADB=%ANDROID_SDK_ROOT%\platform-tools\adb.exe"
set "EMULATOR=%ANDROID_SDK_ROOT%\emulator\emulator.exe"
set "APK_PATH=%~dp0app-release-signed.apk"
set "ACTIVITY_DUMP_FILE=%TEMP%\pocastcloni-top-activity.txt"
set "SLEEP_CMD=powershell -NoProfile -ExecutionPolicy Bypass -Command Start-Sleep -Seconds"
set "STOP_EMULATORS_CMD=Get-Process | Where-Object { $_.ProcessName -like 'emulator' -or $_.ProcessName -like 'qemu-system-*' } | Stop-Process -Force"

if /I "%~1"=="help" goto :help
if /I "%~1"=="--help" goto :help
if /I "%~1"=="-h" goto :help
if /I "%~1"=="list" goto :list_avds

if not exist "%ADB%" (
    echo FEHLER: adb nicht gefunden
    pause
    exit /b 1
)

if not exist "%EMULATOR%" (
    echo FEHLER: emulator.exe nicht gefunden
    pause
    exit /b 1
)

if not exist "%APK_PATH%" (
    echo FEHLER: APK nicht gefunden
    echo   %APK_PATH%
    pause
    exit /b 1
)

set "TARGET_SERIAL="
set "TARGET_AVD=%~1"
set "STALE_EMULATOR_FOUND=0"

if not "%TARGET_AVD%"=="" goto :ensure_device

for /f "skip=1 tokens=1,2" %%D in ('"%ADB%" devices') do (
    if not "%%D"=="" (
        echo %%D | findstr /R "^emulator-" >nul
        if not errorlevel 1 (
            if /I "%%E"=="device" (
                set "TARGET_SERIAL=%%D"
                goto :device_ready
            )
            if /I "%%E"=="offline" (
                set "STALE_EMULATOR_FOUND=1"
            )
        )
    )
)

for /f "delims=" %%A in ('"%EMULATOR%" -list-avds') do (
    if not "%%A"=="" (
        set "TARGET_AVD=%%A"
        goto :ensure_device
    )
)

echo FEHLER: Kein Android-Emulator verfuegbar
pause
exit /b 1

:ensure_device
if not "%TARGET_SERIAL%"=="" goto :device_ready

if "%STALE_EMULATOR_FOUND%"=="1" (
    powershell -NoProfile -ExecutionPolicy Bypass -Command "%STOP_EMULATORS_CMD%"
    %SLEEP_CMD% 3
)

start "Android Emulator - %TARGET_AVD%" "%EMULATOR%" -avd "%TARGET_AVD%" -no-snapshot

set /a WAIT_SECONDS=0
:wait_for_serial
set "TARGET_SERIAL="
for /f "skip=1 tokens=1,2" %%D in ('"%ADB%" devices') do (
    if not "%%D"=="" (
        echo %%D | findstr /R "^emulator-" >nul
        if not errorlevel 1 (
            set "TARGET_SERIAL=%%D"
            goto :wait_for_boot
        )
    )
)

set /a WAIT_SECONDS+=2
if %WAIT_SECONDS% GEQ 180 (
    echo FEHLER: Emulator taucht nicht in adb auf
    pause
    exit /b 1
)
%SLEEP_CMD% 2
goto :wait_for_serial

:wait_for_boot
set /a BOOT_WAIT=0
:boot_loop
set "DEVICE_STATE="
for /f "usebackq delims=" %%S in (`"%ADB%" -s %TARGET_SERIAL% get-state 2^>nul`) do (
    set "DEVICE_STATE=%%S"
)

if /I not "!DEVICE_STATE!"=="device" goto :boot_wait

set "BOOT_STATE="
for /f "usebackq delims=" %%B in (`"%ADB%" -s %TARGET_SERIAL% shell getprop sys.boot_completed 2^>nul`) do (
    set "BOOT_STATE=%%B"
)

if "!BOOT_STATE!"=="1" goto :device_ready

:boot_wait
set /a BOOT_WAIT+=3
if !BOOT_WAIT! GEQ 240 (
    echo FEHLER: Android bootet nicht fertig
    pause
    exit /b 1
)
%SLEEP_CMD% 3
goto :boot_loop

:device_ready
"%ADB%" -s %TARGET_SERIAL% install -r -d "%APK_PATH%" >nul
if errorlevel 1 (
    echo FEHLER: APK-Installation fehlgeschlagen
    pause
    exit /b 1
)

"%ADB%" -s %TARGET_SERIAL% shell am start -W -n %MAIN_ACTIVITY% >nul
if errorlevel 1 (
    echo FEHLER: App-Start fehlgeschlagen
    pause
    exit /b 1
)

set "TOP_ACTIVITY="
if exist "%ACTIVITY_DUMP_FILE%" del /f /q "%ACTIVITY_DUMP_FILE%" >nul 2>nul
"%ADB%" -s %TARGET_SERIAL% shell dumpsys activity activities > "%ACTIVITY_DUMP_FILE%"
for /f "usebackq delims=" %%L in (`findstr "topResumedActivity" "%ACTIVITY_DUMP_FILE%"`) do (
    set "TOP_ACTIVITY=%%L"
)

if "!TOP_ACTIVITY!"=="" (
    echo FEHLER: Konnte Vordergrund-Activity nicht lesen
    pause
    exit /b 1
)

set "TOP_MATCH=!TOP_ACTIVITY:%APP_ID%=!"
if "!TOP_MATCH!"=="!TOP_ACTIVITY!" (
    echo FEHLER: App ist nicht im Vordergrund
    echo !TOP_ACTIVITY!
    pause
    exit /b 1
)

echo OK: Release-APK ist installierbar und gestartet
echo   %APK_PATH%
pause
exit /b 0

:list_avds
echo Available AVDs:
"%EMULATOR%" -list-avds
pause
exit /b 0

:help
echo Usage:
echo   %~nx0
echo   %~nx0 list
echo   %~nx0 AVD_NAME
echo.
echo Prueft eine vorhandene app-release-signed.apk:
echo   1. Emulator bereitstellen
echo   2. APK installieren
echo   3. App starten
echo   4. Vordergrund pruefen
pause
exit /b 0
