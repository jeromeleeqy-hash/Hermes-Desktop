@echo off
setlocal EnableExtensions DisableDelayedExpansion
chcp 65001 >nul
if not exist "%~dp0Run-Hermes.bat" goto incomplete
call "%~dp0Run-Hermes.bat" --diagnose
exit /b %errorlevel%
:incomplete
echo 启动文件不完整，请安装 Hermes 启动修复版。
pause
exit /b 1
