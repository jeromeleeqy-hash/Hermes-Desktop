@echo off
setlocal EnableExtensions DisableDelayedExpansion
chcp 65001 >nul
pushd "%~dp0.."
if errorlevel 1 goto location_error
echo Hermes Desktop 1.8.3 - Windows x64
echo Project: "%CD%"
echo.
if not exist "src\main\kotlin\com\qingyu\hermescompanion\desktop\Main.kt" goto source_error
if not exist "gradle\wrapper\gradle-wrapper.jar" goto source_error
if not exist "packaging\Hermes.ico" goto source_error
call "packaging\windows-java.bat"
if errorlevel 1 goto failed
set "HERMES_GRADLE_TASKS="
set "HERMES_GRADLE_DEMO="
if /i "%~1"=="run" set "HERMES_GRADLE_TASKS=run"
if /i "%~1"=="preview" set "HERMES_GRADLE_TASKS=run"
if /i "%~1"=="preview" set "HERMES_GRADLE_DEMO=1"
if /i "%~1"=="package" set "HERMES_GRADLE_TASKS=packageWindowsPortable"
if /i "%~1"=="build" set "HERMES_GRADLE_TASKS=test packageWindowsPortable"
if /i "%~1"=="installer" goto installer
goto execute
:installer
if defined WIX set "PATH=%WIX%\bin;%PATH%"
where candle.exe >nul 2>&1
if errorlevel 1 goto wix_missing
where light.exe >nul 2>&1
if errorlevel 1 goto wix_missing
set "HERMES_GRADLE_TASKS=packageMsi"
:execute
if not defined HERMES_GRADLE_TASKS goto source_error
if not exist "build\logs" mkdir "build\logs"
echo The first build downloads dependencies. Progress appears below.
echo Log: "%CD%\build\logs\windows-build.log"
echo.
rem No execution-policy changes; use the built-in shell to mirror the live Gradle log.
"%SystemRoot%\System32\WindowsPowerShell\v1.0\powershell.exe" -NoLogo -NoProfile -NonInteractive -Command "$tasks = $env:HERMES_GRADLE_TASKS.Split(' '); if ($env:HERMES_GRADLE_DEMO -eq '1') { $tasks += '--args=--demo' }; & .\gradlew.bat @tasks '--console=plain' 2>&1 | Tee-Object -FilePath 'build\logs\windows-build.log'; $code = $LASTEXITCODE; if ($null -eq $code) { exit 1 }; exit $code"
if errorlevel 1 goto failed
if /i "%~1"=="package" goto portable_ready
if /i "%~1"=="build" goto portable_ready
if /i "%~1"=="installer" goto installer_ready
goto success
:portable_ready
if not exist "build\compose\binaries\main\app\Hermes\Hermes.exe" goto failed
if not exist "build\windows\Hermes-Windows-x64.zip" goto failed
echo.
echo Ready: build\windows\Hermes-Windows-x64.zip
echo Extract the WHOLE ZIP and open Hermes\Hermes.exe. No Java install is needed to run it.
if not defined CI start "" "%SystemRoot%\explorer.exe" "%CD%\build\windows"
goto success
:installer_ready
echo.
echo Installer: build\compose\binaries\main\msi
if not defined CI start "" "%SystemRoot%\explorer.exe" "%CD%\build\compose\binaries\main\msi"
:success
popd
if not defined CI pause
exit /b 0
:wix_missing
echo.
echo MSI packaging requires WiX 3.x with candle.exe and light.exe on PATH.
echo You can use Package-Windows.bat now to make a portable EXE without WiX.
goto failed
:source_error
echo Required project files are missing. Extract the ENTIRE source ZIP to a new folder.
echo Do not copy these scripts into an older Hermes project.
:failed
echo.
echo Hermes did not complete this operation. The error is shown above.
echo If a build started, share build\logs\windows-build.log for diagnosis.
popd
if not defined CI pause
exit /b 1
:location_error
echo Cannot open the project folder. Extract the ZIP before running this script.
if not defined CI pause
exit /b 1
