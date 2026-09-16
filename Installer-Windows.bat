@echo off
call "%~dp0packaging\windows-launch.bat" installer
exit /b %errorlevel%
