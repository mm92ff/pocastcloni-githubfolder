param(
    [Parameter(Mandatory = $true)]
    [string]$Serial
)

$ErrorActionPreference = "Stop"

$repositoryRoot = Split-Path -Parent $PSScriptRoot
$adb = Join-Path $env:LOCALAPPDATA "Android\Sdk\platform-tools\adb.exe"
$gradle = Join-Path $repositoryRoot "gradlew.bat"
$applicationApk = Join-Path $repositoryRoot "app\build\outputs\apk\debug\app-debug.apk"
$testApk = Join-Path $repositoryRoot "app\build\outputs\apk\androidTest\debug\app-debug-androidTest.apk"
$testPackage = "com.example.pocastcloni.test"
$runner = "androidx.test.runner.AndroidJUnitRunner"
$testClass = "com.example.pocastcloni.ui.locale.LocaleFirstLaunchAfterMigrationAndroidTest"

function Invoke-Adb {
    param([string[]]$AdbArguments)

    $output = & $adb -s $Serial @AdbArguments 2>&1
    if ($LASTEXITCODE -ne 0) {
        throw "adb failed: $($output -join [Environment]::NewLine)"
    }
    return $output
}

function Invoke-InstrumentationTest {
    param([string]$Method)

    $output = Invoke-Adb -AdbArguments @(
        "shell", "am", "instrument", "-w", "-r", "-e", "class",
        "$testClass#$Method", "$testPackage/$runner"
    )
    $text = $output -join [Environment]::NewLine
    if ($text -notmatch "OK \(1 test\)") {
        throw "Instrumentation test $Method failed: $text"
    }
}

if (-not (Test-Path $adb)) {
    throw "Android Debug Bridge was not found at $adb"
}

$apiLevel = [int]((Invoke-Adb -AdbArguments @("shell", "getprop", "ro.build.version.sdk")) -join "").Trim()
if ($apiLevel -ne 35) {
    throw "This validation requires API 35; $Serial reports API $apiLevel"
}

& $gradle :app:assembleDebug :app:assembleDebugAndroidTest
if ($LASTEXITCODE -ne 0) {
    throw "Gradle failed to assemble the validation APKs"
}

Invoke-Adb -AdbArguments @("install", "-r", $applicationApk) | Out-Null
Invoke-Adb -AdbArguments @("install", "-r", $testApk) | Out-Null
Invoke-InstrumentationTest "prepareLegacyGermanLocaleForReboot"

Invoke-Adb -AdbArguments @("reboot") | Out-Null
$deadline = [DateTime]::UtcNow.AddMinutes(2)
do {
    Start-Sleep -Seconds 2
    $bootCompleted = (& $adb -s $Serial shell getprop sys.boot_completed 2>$null) -join ""
} while ($bootCompleted.Trim() -ne "1" -and [DateTime]::UtcNow -lt $deadline)

if ($bootCompleted.Trim() -ne "1") {
    throw "$Serial did not finish booting within two minutes"
}

Start-Sleep -Seconds 20
Invoke-Adb -AdbArguments @("shell", "input", "keyevent", "82") | Out-Null
Invoke-InstrumentationTest "firstLaunchAfterRebootUsesMigratedGermanLocale"
Write-Output "API 35 first-launch locale migration passed on $Serial"
