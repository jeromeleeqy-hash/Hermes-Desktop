; Per-user installation. User data in LOCALAPPDATA\HermesDesktop is never deleted.
Unicode true
!include "MUI2.nsh"
!include "LogicLib.nsh"
!include "x64.nsh"
!include "FileFunc.nsh"
Name "Hermes"
OutFile "${OUTPUT}"
InstallDir "$LOCALAPPDATA\Programs\Hermes"
InstallDirRegKey HKCU "Software\HermesDesktopInstall" "InstallLocation"
RequestExecutionLevel user
SetCompressor /SOLID lzma
SetCompressorDictSize 32
CRCCheck force
AllowSkipFiles off
ShowInstDetails show
ShowUninstDetails show
VIProductVersion "1.8.4.0"
VIAddVersionKey /LANG=1033 "ProductName" "Hermes"
VIAddVersionKey /LANG=1033 "FileDescription" "Hermes Desktop Setup"
VIAddVersionKey /LANG=1033 "FileVersion" "1.8.4.0"
VIAddVersionKey /LANG=1033 "ProductVersion" "1.8.4"
VIAddVersionKey /LANG=1033 "LegalCopyright" "Hermes Desktop contributors"
!define MUI_ICON "${ICON}"
!define MUI_UNICON "${ICON}"
!define MUI_ABORTWARNING
!define MUI_WELCOMEPAGE_TITLE "欢迎安装 Hermes"
!define MUI_WELCOMEPAGE_TEXT "将对话、任务和文档放在一个工作空间。$\r$\n$\r$\n安装程序已包含运行环境，无需另装 Java。$\r$\n已有网关、偏好和草稿会继续保留。"
!define MUI_FINISHPAGE_TITLE "Hermes 已准备好"
!define MUI_FINISHPAGE_RUN "$INSTDIR\Hermes.exe"
!define MUI_FINISHPAGE_RUN_TEXT "打开 Hermes"
!define MUI_FINISHPAGE_NOREBOOTSUPPORT
!insertmacro MUI_PAGE_WELCOME
!define MUI_DIRECTORYPAGE_TEXT_TOP "选择空文件夹或已有 Hermes 安装目录。配置与草稿保存在独立的用户数据目录中。"
!insertmacro MUI_PAGE_DIRECTORY
!insertmacro MUI_PAGE_COMPONENTS
!insertmacro MUI_PAGE_INSTFILES
!insertmacro MUI_PAGE_FINISH
!insertmacro MUI_UNPAGE_CONFIRM
!insertmacro MUI_UNPAGE_INSTFILES
!insertmacro MUI_UNPAGE_FINISH
!insertmacro MUI_LANGUAGE "SimpChinese"
!insertmacro MUI_LANGUAGE "English"

Function .onInit
  ${IfNot} ${RunningX64}
    MessageBox MB_OK|MB_ICONSTOP "Hermes 需要 64 位 Windows。"
    SetErrorLevel 2
    Abort
  ${EndIf}
  Call CheckClosed
FunctionEnd

Function CheckClosed
  retry:
  FindWindow $0 "SunAwtFrame" "Hermes"
  ${If} $0 != 0
    IfSilent blocked
    MessageBox MB_RETRYCANCEL|MB_ICONINFORMATION "请在右下角系统托盘中右键 Hermes，选择“退出 Hermes”，再继续安装。草稿会保留。" IDRETRY retry
    blocked:
    SetErrorLevel 2
    Abort
  ${EndIf}
FunctionEnd

; Reject an unrelated nonempty folder. An installer may replace only its own managed tree.
Function .onVerifyInstDir
  StrLen $0 $INSTDIR
  ${If} $0 < 8
    SetErrorLevel 2
    Abort
  ${EndIf}
  IfFileExists "$INSTDIR\*.*" 0 verified
  FindFirst $1 $2 "$INSTDIR\*"
  scan:
    ${If} $2 == ""
      FindClose $1
      Goto verified
    ${EndIf}
    ${If} $2 != "."
    ${AndIf} $2 != ".."
      FindClose $1
      ReadINIStr $0 "$INSTDIR\hermes-install.ini" "Hermes" "Product"
      ${If} $0 != "HermesDesktop"
        SetErrorLevel 2
        Abort
      ${EndIf}
      Goto verified
    ${EndIf}
    FindNext $1 $2
    Goto scan
  verified:
    SetErrorLevel 0
FunctionEnd

Section "Hermes 应用与运行环境（必需）" ApplicationSection
  SectionIn RO
  SetShellVarContext current
  Call CheckClosed
  ; The same validation also applies to silent installs, which skip the directory page.
  Call .onVerifyInstDir
  SetOutPath "$INSTDIR"
  File "${PAYLOAD}/Hermes.exe"
  File "${PAYLOAD}/Start-Hermes.bat"
  File "${PAYLOAD}/Diagnose-Hermes.bat"
  File "${PAYLOAD}/Run-Hermes.bat"
  File "${PAYLOAD}/hermes.vmoptions"
  File "${PAYLOAD}/manifest.json"
  File "${PAYLOAD}/开始使用.txt"
  File "${PAYLOAD}/THIRD_PARTY_NOTICES.md"
  ; app is an installer-managed classpath. Remove superseded dependency versions.
  Delete "$INSTDIR\app\*.jar"
  SetOutPath "$INSTDIR\app"
  File /r "${PAYLOAD}/app/*"
  SetOutPath "$INSTDIR\runtime"
  File /r "${PAYLOAD}/runtime/*"
  SetOutPath "$INSTDIR\licenses"
  File /r "${PAYLOAD}/licenses/*"
  SetOutPath "$INSTDIR\docs"
  File /r "${PAYLOAD}/docs/*"
  SetOutPath "$INSTDIR"
  WriteINIStr "$INSTDIR\hermes-install.ini" "Hermes" "Product" "HermesDesktop"
  WriteINIStr "$INSTDIR\hermes-install.ini" "Hermes" "Version" "1.8.4"
  WriteUninstaller "$INSTDIR\Uninstall.exe"
  CreateDirectory "$SMPROGRAMS\Hermes"
  CreateShortcut "$SMPROGRAMS\Hermes\Hermes.lnk" "$INSTDIR\Hermes.exe" "" "$INSTDIR\Hermes.exe"
  WriteRegStr HKCU "Software\HermesDesktopInstall" "InstallLocation" "$INSTDIR"
  WriteRegStr HKCU "Software\Microsoft\Windows\CurrentVersion\Uninstall\HermesDesktop" "DisplayName" "Hermes"
  WriteRegStr HKCU "Software\Microsoft\Windows\CurrentVersion\Uninstall\HermesDesktop" "DisplayVersion" "1.8.4"
  WriteRegStr HKCU "Software\Microsoft\Windows\CurrentVersion\Uninstall\HermesDesktop" "Publisher" "Jerome"
  WriteRegStr HKCU "Software\Microsoft\Windows\CurrentVersion\Uninstall\HermesDesktop" "InstallLocation" "$INSTDIR"
  WriteRegStr HKCU "Software\Microsoft\Windows\CurrentVersion\Uninstall\HermesDesktop" "DisplayIcon" "$INSTDIR\Hermes.exe"
  WriteRegStr HKCU "Software\Microsoft\Windows\CurrentVersion\Uninstall\HermesDesktop" "UninstallString" '$\"$INSTDIR\Uninstall.exe$\"'
  WriteRegStr HKCU "Software\Microsoft\Windows\CurrentVersion\Uninstall\HermesDesktop" "QuietUninstallString" '$\"$INSTDIR\Uninstall.exe$\" /S'
  WriteRegDWORD HKCU "Software\Microsoft\Windows\CurrentVersion\Uninstall\HermesDesktop" "NoModify" 1
  WriteRegDWORD HKCU "Software\Microsoft\Windows\CurrentVersion\Uninstall\HermesDesktop" "NoRepair" 1
  ${GetSize} "$INSTDIR" "/S=0K" $0 $1 $2
  WriteRegDWORD HKCU "Software\Microsoft\Windows\CurrentVersion\Uninstall\HermesDesktop" "EstimatedSize" $0
SectionEnd

Section /o "创建桌面快捷方式" DesktopSection
  SetShellVarContext current
  CreateShortcut "$DESKTOP\Hermes.lnk" "$INSTDIR\Hermes.exe" "" "$INSTDIR\Hermes.exe"
SectionEnd

Function un.onInit
  ReadINIStr $0 "$INSTDIR\hermes-install.ini" "Hermes" "Product"
  ${If} $0 != "HermesDesktop"
    MessageBox MB_OK|MB_ICONSTOP "找不到此目录的 Hermes 安装记录，已停止卸载。"
    SetErrorLevel 2
    Abort
  ${EndIf}
  retry:
  FindWindow $0 "SunAwtFrame" "Hermes"
  ${If} $0 != 0
    IfSilent blocked
    MessageBox MB_RETRYCANCEL|MB_ICONINFORMATION "请退出 Hermes 后再卸载。你的网关配置和草稿将保留。" IDRETRY retry
    blocked:
    SetErrorLevel 2
    Abort
  ${EndIf}
FunctionEnd

Section "Uninstall"
  SetShellVarContext current
  Delete "$DESKTOP\Hermes.lnk"
  Delete "$SMPROGRAMS\Hermes\Hermes.lnk"
  RMDir "$SMPROGRAMS\Hermes"
  RMDir /r "$INSTDIR\app"
  RMDir /r "$INSTDIR\runtime"
  RMDir /r "$INSTDIR\licenses"
  RMDir /r "$INSTDIR\docs"
  Delete "$INSTDIR\Hermes.exe"
  Delete "$INSTDIR\Start-Hermes.bat"
  Delete "$INSTDIR\Diagnose-Hermes.bat"
  Delete "$INSTDIR\Run-Hermes.bat"
  Delete "$INSTDIR\hermes.vmoptions"
  Delete "$INSTDIR\startup-fix.ini"
  Delete "$INSTDIR\manifest.json"
  Delete "$INSTDIR\开始使用.txt"
  Delete "$INSTDIR\THIRD_PARTY_NOTICES.md"
  Delete "$INSTDIR\hermes-install.ini"
  Delete "$INSTDIR\Uninstall.exe"
  RMDir "$INSTDIR"
  DeleteRegKey HKCU "Software\Microsoft\Windows\CurrentVersion\Uninstall\HermesDesktop"
  DeleteRegKey HKCU "Software\HermesDesktopInstall"
SectionEnd
