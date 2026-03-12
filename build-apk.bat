@echo off
setlocal EnableExtensions EnableDelayedExpansion

cd /d "%~dp0"

set "JAVA_HOME=C:\Program Files\Android\Android Studio\jbr"
set "ANDROID_SDK_ROOT=C:\Users\jemi\AppData\Local\Android\Sdk"
set "ANDROID_HOME=%ANDROID_SDK_ROOT%"
set "BUILD_TYPE=%~1"

if "%BUILD_TYPE%"=="" set "BUILD_TYPE=release"

if /I not "%BUILD_TYPE%"=="debug" if /I not "%BUILD_TYPE%"=="release" (
    echo Usage: %~nx0 [debug^|release]
    pause
    exit /b 1
)

if not exist "%JAVA_HOME%\bin\java.exe" (
    echo Android Studio JDK not found:
    echo   %JAVA_HOME%
    pause
    exit /b 1
)

if not exist "%ANDROID_SDK_ROOT%\platform-tools\adb.exe" (
    echo Android SDK not found:
    echo   %ANDROID_SDK_ROOT%
    pause
    exit /b 1
)

set "PATH=%JAVA_HOME%\bin;%ANDROID_SDK_ROOT%\platform-tools;%ANDROID_SDK_ROOT%\emulator;%PATH%"

if /I "%BUILD_TYPE%"=="release" (
    set "GRADLE_TASK=assembleRelease"
    set "APK_TARGET=%~dp0app-release.apk"
) else (
    set "GRADLE_TASK=assembleDebug"
    set "APK_SOURCE=%CD%\app\build\outputs\apk\debug\app-debug.apk"
    set "APK_TARGET=%~dp0app-debug.apk"
)

echo Project: %CD%
echo JAVA_HOME: %JAVA_HOME%
echo ANDROID_SDK_ROOT: %ANDROID_SDK_ROOT%
echo Running: .\gradlew.bat %GRADLE_TASK%
echo.

call .\gradlew.bat %GRADLE_TASK%
if errorlevel 1 (
    echo.
    echo Build failed.
    pause
    exit /b 1
)

if /I "%BUILD_TYPE%"=="release" (
    set "APK_SOURCE=%CD%\app\build\outputs\apk\release\app-release.apk"
    if not exist "!APK_SOURCE!" (
        set "APK_SOURCE=%CD%\app\build\outputs\apk\release\app-release-unsigned.apk"
    )
)

if not exist "%APK_SOURCE%" (
    echo.
    echo APK not found:
    echo   %APK_SOURCE%
    pause
    exit /b 1
)

copy /Y "%APK_SOURCE%" "%APK_TARGET%" >nul
if errorlevel 1 (
    echo.
    echo Failed to copy APK to:
    echo   %APK_TARGET%
    pause
    exit /b 1
)

echo.
echo APK created:
echo   %APK_TARGET%

pause
exit /b 0
