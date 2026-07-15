@echo off
setlocal EnableExtensions DisableDelayedExpansion

set "SCRIPT_DIR=%~dp0"
cd /d "%SCRIPT_DIR%" || goto :project_root_error

for %%I in ("%SCRIPT_DIR:~0,-1%") do set "PROJECT_NAME=%%~nxI"
set "SELF_TEST=0"
if /i "%~1"=="--self-test" set "SELF_TEST=1"

call :find_7zip
if errorlevel 1 goto :missing_7zip

for /f "usebackq delims=" %%I in (`powershell.exe -NoProfile -Command "Get-Date -Format 'yyyyMMdd-HHmmss'"`) do set "TIMESTAMP=%%I"
if not defined TIMESTAMP goto :timestamp_error

set "GIT_SHA=nogit"
set "GIT_STATE=no-git"
set "DIRTY_SUFFIX="
if exist ".git\" call :read_git_state

if "%SELF_TEST%"=="1" (
    set "ARCHIVE_NAME=%PROJECT_NAME%-backup-self-test.7z"
    set "PASSWORD_SWITCH=-pcodex-backup-self-test-only"
) else (
    set "ARCHIVE_NAME=%PROJECT_NAME%-backup-%TIMESTAMP%-%GIT_SHA%%DIRTY_SUFFIX%.7z"
    set "PASSWORD_SWITCH=-p"
)
set "ARCHIVE_PATH=%SCRIPT_DIR%%ARCHIVE_NAME%"

if exist "%ARCHIVE_PATH%" del /q "%ARCHIVE_PATH%" >nul 2>&1

if "%SELF_TEST%"=="0" (
    echo.
    echo Encrypted project backup
    echo ========================
    echo Project:   %PROJECT_NAME%
    echo Git:       %GIT_SHA% ^(%GIT_STATE%^)
    echo Output:    %ARCHIVE_PATH%
    echo.
    echo This archive contains local signing files and may contain keystores.
    echo 7-Zip will ask for a password now and again for the integrity test.
    echo Use the same strong password both times. The password is not stored.
    echo.
) else (
    echo Running encrypted project backup self-test...
)

"%SEVEN_ZIP%" a -t7z "%ARCHIVE_PATH%" "." ^
    -mx=7 -m0=lzma2 -mhe=on %PASSWORD_SWITCH% ^
    -xr!build -xr!.gradle -xr!.idea ^
    -xr!benchmark-reports -xr!artifacts -xr!logs ^
    -xr!*.log -xr!*.perfetto-trace -xr!*benchmarkData.json ^
    -xr!emulator-screen.png -xr!ui*.xml ^
    -xr!%PROJECT_NAME%-backup-*.7z
if errorlevel 1 goto :archive_failed

echo.
echo Verifying archive integrity...
"%SEVEN_ZIP%" t "%ARCHIVE_PATH%" %PASSWORD_SWITCH% -bsp0
if errorlevel 1 goto :verification_failed

if "%SELF_TEST%"=="1" goto :validate_self_test

for %%I in ("%ARCHIVE_PATH%") do set "ARCHIVE_SIZE=%%~zI"
echo.
echo Backup created and verified successfully.
echo File:  %ARCHIVE_PATH%
echo Bytes: %ARCHIVE_SIZE%
echo Git:   %GIT_SHA% ^(%GIT_STATE%^)
echo.
pause
exit /b 0

:validate_self_test
set "LIST_FILE=%TEMP%\%PROJECT_NAME%-backup-list-%RANDOM%%RANDOM%.txt"
"%SEVEN_ZIP%" l "%ARCHIVE_PATH%" -pdefinitely-wrong-self-test-password -bso0 -bse0 >nul 2>&1
if not errorlevel 1 (
    echo [ERROR] Archive headers could be listed with an incorrect password.
    goto :self_test_failed
)
"%SEVEN_ZIP%" l -slt "%ARCHIVE_PATH%" %PASSWORD_SWITCH% >"%LIST_FILE%"
if errorlevel 1 goto :self_test_failed

findstr.exe /i /l /c:"Path = .git\HEAD" "%LIST_FILE%" >nul
if errorlevel 1 goto :missing_required_entry
findstr.exe /i /l /c:"Path = app\src\main" "%LIST_FILE%" >nul
if errorlevel 1 goto :missing_required_entry
findstr.exe /i /l /c:"Path = settings.gradle.kts" "%LIST_FILE%" >nul
if errorlevel 1 goto :missing_required_entry
findstr.exe /i /l /c:"Path = gradlew.bat" "%LIST_FILE%" >nul
if errorlevel 1 goto :missing_required_entry

if exist "apks\" (
    findstr.exe /i /l /c:"Path = apks" "%LIST_FILE%" >nul
    if errorlevel 1 goto :missing_required_entry
)
if exist "signing.local.properties" (
    findstr.exe /i /l /c:"Path = signing.local.properties" "%LIST_FILE%" >nul
    if errorlevel 1 goto :missing_required_entry
)
if exist "app\pocastcloni-release.jks" (
    findstr.exe /i /l /c:"Path = app\pocastcloni-release.jks" "%LIST_FILE%" >nul
    if errorlevel 1 goto :missing_required_entry
)

powershell.exe -NoProfile -Command "$bad = Get-Content -LiteralPath $env:LIST_FILE | Where-Object { $_ -match '^Path = (?:(?:.*\\)?build(?:\\|$)|\.gradle(?:\\|$)|\.idea(?:\\|$)|benchmark-reports(?:\\|$)|artifacts(?:\\|$)|logs(?:\\|$))' }; if ($bad) { $bad | Select-Object -First 20; exit 1 }"
if errorlevel 1 goto :found_excluded_entry

del /q "%LIST_FILE%" >nul 2>&1
del /q "%ARCHIVE_PATH%" >nul 2>&1
echo.
echo Self-test passed: integrity, required content, and exclusions are correct.
exit /b 0

:find_7zip
set "SEVEN_ZIP="
for %%E in (7z.exe 7zz.exe) do (
    if not defined SEVEN_ZIP for /f "delims=" %%P in ('where.exe %%E 2^>nul') do set "SEVEN_ZIP=%%P"
)
if defined SEVEN_ZIP exit /b 0
if exist "%ProgramFiles%\7-Zip\7z.exe" set "SEVEN_ZIP=%ProgramFiles%\7-Zip\7z.exe"
if defined SEVEN_ZIP exit /b 0
if exist "%ProgramFiles(x86)%\7-Zip\7z.exe" set "SEVEN_ZIP=%ProgramFiles(x86)%\7-Zip\7z.exe"
if defined SEVEN_ZIP exit /b 0
exit /b 1

:read_git_state
for /f "delims=" %%I in ('git rev-parse --short HEAD 2^>nul') do set "GIT_SHA=%%I"
set "GIT_STATE=clean"
for /f "delims=" %%I in ('git status --porcelain --untracked-files^=normal 2^>nul') do set "GIT_STATE=dirty"
if "%GIT_STATE%"=="dirty" set "DIRTY_SUFFIX=-dirty"
exit /b 0

:archive_failed
echo.
echo [ERROR] 7-Zip could not create the archive. Partial output is removed.
if exist "%ARCHIVE_PATH%" del /q "%ARCHIVE_PATH%" >nul 2>&1
goto :failed

:verification_failed
echo.
echo [ERROR] Archive verification failed. Unverified output is removed.
if exist "%ARCHIVE_PATH%" del /q "%ARCHIVE_PATH%" >nul 2>&1
goto :failed

:self_test_failed
echo.
echo [ERROR] Project backup self-test failed.
if defined LIST_FILE if exist "%LIST_FILE%" del /q "%LIST_FILE%" >nul 2>&1
if exist "%ARCHIVE_PATH%" del /q "%ARCHIVE_PATH%" >nul 2>&1
goto :failed

:missing_required_entry
echo [ERROR] A required project entry is missing from the backup.
goto :self_test_failed

:found_excluded_entry
echo [ERROR] An excluded generated entry is present in the backup.
goto :self_test_failed

:missing_7zip
echo [ERROR] 7-Zip was not found in PATH or a standard installation folder.
echo Install 7-Zip and run this script again.
goto :failed

:timestamp_error
echo [ERROR] Could not create a timestamp with PowerShell.
goto :failed

:project_root_error
echo [ERROR] Could not open the project root: %SCRIPT_DIR%
goto :failed

:failed
if "%SELF_TEST%"=="0" pause
exit /b 1
