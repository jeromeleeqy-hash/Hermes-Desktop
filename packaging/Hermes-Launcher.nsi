; Keep a small native supervisor alive so failures after the first second are also reported.
; Run-Hermes.bat merges BOTH JVM streams into one log before any Java code is loaded.
Unicode true
!include "LogicLib.nsh"
Name "Hermes"
OutFile "${OUTPUT}"
Icon "${ICON}"
RequestExecutionLevel user
SilentInstall silent
AutoCloseWindow true
ShowInstDetails nevershow
VIProductVersion "1.8.4.0"
VIAddVersionKey /LANG=1033 "ProductName" "Hermes"
VIAddVersionKey /LANG=1033 "FileDescription" "Hermes Desktop"
VIAddVersionKey /LANG=1033 "FileVersion" "1.8.4.0"
VIAddVersionKey /LANG=1033 "ProductVersion" "1.8.4"
VIAddVersionKey /LANG=1033 "LegalCopyright" "Hermes Desktop contributors"
Section
  IfFileExists "$EXEDIR\app\hermes-desktop.jar" +3
    MessageBox MB_OK|MB_ICONSTOP "请完整解压 Hermes，保留 app 和 runtime 文件夹。"
    Quit
  IfFileExists "$EXEDIR\runtime\bin\java.exe" +3
    MessageBox MB_OK|MB_ICONSTOP "运行环境不完整，请重新安装 Hermes。"
    Quit
  IfFileExists "$EXEDIR\Run-Hermes.bat" +3
    MessageBox MB_OK|MB_ICONSTOP "启动文件不完整，请重新安装 Hermes 完整版本。"
    Quit
  SetOutPath "$EXEDIR"
  ; No PowerShell dependency or execution-policy changes. cmd /D ignores AutoRun hooks.
  ; Do not set a timeout: closing to the tray intentionally keeps the application alive.
  nsExec::Exec '"$SYSDIR\cmd.exe" /D /S /C ""$EXEDIR\Run-Hermes.bat""'
  Pop $0
  ${If} $0 == "error"
    MessageBox MB_OK|MB_ICONSTOP "Hermes 启动器无法运行，请打开 Diagnose-Hermes.bat 查看详情。"
  ${ElseIf} $0 != 0
    MessageBox MB_OK|MB_ICONSTOP "Hermes 未能正常运行。已尝试打开完整启动日志，请将日志内容发给开发者。也可以运行 Diagnose-Hermes.bat。"
  ${EndIf}
SectionEnd
