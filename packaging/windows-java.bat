@echo off
rem Called by windows-launch.bat with delayed expansion disabled.
set "HERMES_SELECTED_JAVA="
set "HERMES_JAVA_PROBE=%TEMP%\hermes-jdk-%RANDOM%-%RANDOM%.txt"
if defined HERMES_JAVA_HOME goto explicit_java
if defined JAVA_HOME call :candidate "%JAVA_HOME%"
if defined HERMES_SELECTED_JAVA goto ready
for /f "delims=" %%J in ('where javac.exe 2^>nul') do call :candidate "%%~dpJ.."
if defined HERMES_SELECTED_JAVA goto ready
for /d %%J in ("%ProgramFiles%\Eclipse Adoptium\*" "%ProgramFiles%\Microsoft\jdk*" "%ProgramFiles%\Java\jdk*" "%ProgramFiles%\Amazon Corretto\*" "%ProgramFiles%\BellSoft\*" "%ProgramFiles%\Zulu\*" "%USERPROFILE%\.jdks\*" "%LOCALAPPDATA%\Programs\Eclipse Adoptium\*" "%LOCALAPPDATA%\Programs\Microsoft\jdk*" "%~dp0..\jdk*") do call :candidate "%%~fJ"
if defined HERMES_SELECTED_JAVA goto ready
goto missing
:explicit_java
call :candidate "%HERMES_JAVA_HOME%"
if defined HERMES_SELECTED_JAVA goto ready
echo HERMES_JAVA_HOME does not point to a complete Windows x64 JDK 21.
goto missing
:candidate
if defined HERMES_SELECTED_JAVA exit /b 0
if not exist "%~f1\bin\javac.exe" exit /b 0
if not exist "%~f1\bin\java.exe" exit /b 0
if not exist "%~f1\bin\jpackage.exe" exit /b 0
"%~f1\bin\java.exe" -XshowSettings:properties -version >"%HERMES_JAVA_PROBE%" 2>&1
if errorlevel 1 exit /b 0
findstr /r /c:"java.specification.version = 21$" "%HERMES_JAVA_PROBE%" >nul
if errorlevel 1 exit /b 0
findstr /c:"os.arch = amd64" /c:"os.arch = x86_64" "%HERMES_JAVA_PROBE%" >nul
if errorlevel 1 exit /b 0
set "HERMES_SELECTED_JAVA=%~f1"
exit /b 0
:ready
if exist "%HERMES_JAVA_PROBE%" del /q "%HERMES_JAVA_PROBE%"
set "JAVA_HOME=%HERMES_SELECTED_JAVA%"
set "PATH=%JAVA_HOME%\bin;%PATH%"
echo JDK 21 x64: "%JAVA_HOME%"
exit /b 0
:missing
if exist "%HERMES_JAVA_PROBE%" del /q "%HERMES_JAVA_PROBE%"
echo.
echo Install Windows x64 JDK 21, then run this script again.
echo Download: https://adoptium.net/temurin/releases/?os=windows^&arch=x64^&version=21
echo Choose JDK 21, Windows, x64, MSI. Homebrew is not used on Windows.
echo For a ZIP installation, set HERMES_JAVA_HOME to the folder containing bin\javac.exe.
exit /b 1
