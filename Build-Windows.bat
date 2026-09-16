@echo off
call "%~dp0packaging\windows-launch.bat" build
exit /b %errorlevel%
