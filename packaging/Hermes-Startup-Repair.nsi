; Repairs only the shipped 1.8.0 runtime and launcher. Application JARs and user data are untouched.
Unicode true
!include "MUI2.nsh"
!include "LogicLib.nsh"
!include "StrFunc.nsh"
${StrStr}
Name "Hermes 1.8.0 启动修复"
OutFile "${OUTPUT}"
InstallDir "$LOCALAPPDATA\Programs\Hermes"
InstallDirRegKey HKCU "Software\HermesDesktopInstall" "InstallLocation"
RequestExecutionLevel user
SetCompressor /SOLID lzma
SetCompressorDictSize 32
CRCCheck force
AllowSkipFiles off
ShowInstDetails show
VIProductVersion "1.8.0.1"
VIAddVersionKey /LANG=1033 "ProductName" "Hermes Startup Repair"
VIAddVersionKey /LANG=1033 "FileDescription" "Hermes 1.8.0 Startup Repair"
VIAddVersionKey /LANG=1033 "FileVersion" "1.8.0.1"
VIAddVersionKey /LANG=1033 "LegalCopyright" "Hermes Desktop contributors"
!define MUI_ICON "${ICON}"
!define MUI_WELCOMEPAGE_TITLE "修复 Hermes 无法启动"
!define MUI_WELCOMEPAGE_TEXT "此修复包为 Hermes 1.8.0 补齐桌面运行模块，并更新启动日志。$\r$\n$\r$\n网关、会话、草稿、头像与偏好设置会保留。$\r$\n无需卸载，也无需另装 Java。"
!define MUI_DIRECTORYPAGE_TEXT_TOP "选择现有 Hermes 1.8.0 所在的文件夹（里面应有 Hermes.exe、app 和 runtime）。安装版会自动填写路径。"
!define MUI_FINISHPAGE_TITLE "Hermes 启动修复已完成"
!define MUI_FINISHPAGE_RUN "$INSTDIR\Hermes.exe"
!define MUI_FINISHPAGE_RUN_TEXT "打开 Hermes"
!insertmacro MUI_PAGE_WELCOME
!insertmacro MUI_PAGE_DIRECTORY
!insertmacro MUI_PAGE_INSTFILES
!insertmacro MUI_PAGE_FINISH
!insertmacro MUI_LANGUAGE "SimpChinese"
!insertmacro MUI_LANGUAGE "English"

Function .onVerifyInstDir
  IfFileExists "$INSTDIR\Hermes.exe" 0 invalid
  IfFileExists "$INSTDIR\app\hermes-desktop.jar" 0 invalid
  IfFileExists "$INSTDIR\app\javafx-swing-21.0.8-win.jar" 0 invalid
  FileOpen $0 "$INSTDIR\manifest.json" r
  IfErrors invalid
  scan:
    ClearErrors
    FileRead $0 $1
    IfErrors end_scan
    ${StrStr} $2 $1 '"version": "1.8.0"'
    StrCmp $2 "" scan
    FileClose $0
    Return
  end_scan:
    FileClose $0
  invalid:
    Abort
FunctionEnd

Section
  Call .onVerifyInstDir
  retry:
    FindWindow $0 "SunAwtFrame" "Hermes"
    ${If} $0 != 0
      IfSilent blocked
      MessageBox MB_RETRYCANCEL|MB_ICONINFORMATION "请在右下角托盘右键 Hermes，选择“退出 Hermes”，再继续修复。" IDRETRY retry
      blocked:
      SetErrorLevel 2
      Abort
    ${EndIf}
  ; Every new runtime file comes from the same verified image used by the full installer.
  SetOutPath "$INSTDIR\runtime"
  File /r "${PAYLOAD}/runtime/*"
  SetOutPath "$INSTDIR"
  File "${PAYLOAD}/Hermes.exe"
  File "${PAYLOAD}/Start-Hermes.bat"
  File "${PAYLOAD}/Diagnose-Hermes.bat"
  File "${PAYLOAD}/Run-Hermes.bat"
  File "${PAYLOAD}/hermes.vmoptions"
  WriteINIStr "$INSTDIR\startup-fix.ini" "Hermes" "Build" "1.8.0+startup.1"
SectionEnd
