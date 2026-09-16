@echo off
call "%~dp0packaging\windows-launch.bat" package
exit /b %errorlevel%
