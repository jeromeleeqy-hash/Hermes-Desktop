@echo off
setlocal EnableExtensions DisableDelayedExpansion
chcp 65001 >nul
pushd "%~dp0"
if errorlevel 1 exit /b 2
set "HERMES_LOG_DIR=%LOCALAPPDATA%\HermesDesktop\logs"
if not defined LOCALAPPDATA set "HERMES_LOG_DIR=%TEMP%\HermesDesktop-logs"
if not exist "%HERMES_LOG_DIR%" mkdir "%HERMES_LOG_DIR%" 2>nul
if not exist "%HERMES_LOG_DIR%" goto log_fallback
goto log_ready
:log_fallback
set "HERMES_LOG_DIR=%TEMP%\HermesDesktop-logs"
if not exist "%HERMES_LOG_DIR%" mkdir "%HERMES_LOG_DIR%" 2>nul
:log_ready
if not exist "%HERMES_LOG_DIR%" goto log_failed
:new_log
set "HERMES_LOG=%HERMES_LOG_DIR%\startup-%RANDOM%-%RANDOM%.log"
if exist "%HERMES_LOG%" goto new_log
rem Test write access before launching Java. A JVM boot error can be on stdout, not stderr.
>"%HERMES_LOG%" echo Hermes Windows 1.8.4
if errorlevel 1 goto log_failed
call :run >>"%HERMES_LOG%" 2>&1
set "HERMES_EXIT_CODE=%errorlevel%"
if /I "%~1"=="--diagnose" goto show_diagnostic
if not "%HERMES_EXIT_CODE%"=="0" start "" "%SystemRoot%\System32\notepad.exe" "%HERMES_LOG%"
goto finish
:show_diagnostic
type "%HERMES_LOG%"
echo.
echo 完整日志："%HERMES_LOG%"
pause
:finish
popd
exit /b %HERMES_EXIT_CODE%
:log_failed
echo 无法写入启动日志。请检查本机临时目录是否可写。
popd
exit /b 3

:run
echo Started: %DATE% %TIME%
echo Application: "%~dp0"
echo System: %OS% / %PROCESSOR_ARCHITECTURE%
echo.
if not exist "runtime\bin\java.exe" goto missing_runtime
if not exist "app\hermes-desktop.jar" goto missing_app
if not exist "hermes.vmoptions" goto missing_options
"runtime\bin\java.exe" -version
if errorlevel 1 exit /b 10
echo.
echo Checking required desktop runtime module...
"runtime\bin\java.exe" --describe-module jdk.unsupported.desktop
if errorlevel 1 goto missing_module
echo.
echo Validating application modules...
"runtime\bin\java.exe" @hermes.vmoptions --validate-modules
if errorlevel 1 exit /b 12
echo.
echo Starting Hermes...
"runtime\bin\java.exe" @hermes.vmoptions com.qingyu.hermescompanion.desktop.MainKt
set "HERMES_JVM_EXIT=%errorlevel%"
echo.
echo Finished: %DATE% %TIME%
echo JVM exit code: %HERMES_JVM_EXIT%
exit /b %HERMES_JVM_EXIT%
:missing_runtime
echo ERROR: 缺少 runtime\bin\java.exe，请完整解压或重新安装。
exit /b 10
:missing_app
echo ERROR: 缺少 app\hermes-desktop.jar，请完整解压或重新安装。
exit /b 11
:missing_options
echo ERROR: 缺少 hermes.vmoptions，请重新安装完整版本。
exit /b 12
:missing_module
echo ERROR: 运行环境缺少 jdk.unsupported.desktop，无法加载网页编辑组件。
echo 请重新安装 Hermes 1.8.4 完整安装包；无需删除设置，也无需自行安装 Java。
exit /b 13
