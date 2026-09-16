@echo off
setlocal EnableExtensions DisableDelayedExpansion
chcp 65001 >nul
if not exist "%~dp0Hermes.exe" goto incomplete
start "" "%~dp0Hermes.exe"
exit /b 0
:incomplete
echo 启动文件不完整，请安装 Hermes 启动修复版。
pause
exit /b 1
