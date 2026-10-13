# 從原始碼構建 Hermes Windows 1.8.2

## 環境

- Windows x64 JDK 21，`JAVA_HOME` 指向該 JDK；Gradle Wrapper 首次執行需要下載依賴。
- Python 3.10 或以上、NSIS 3.x。
- 官方 Windows x64 Temurin JDK ZIP（執行程式不需要使用者另裝 Java，此 ZIP 只用於打包）：`OpenJDK21U-jdk_x64_windows_hotspot_21.0.12.1_1.zip`。
- 本次構建使用該 ZIP 的 SHA-256：`f9d6e191ab098c0d416e7d588a24420a8621cd2f4720dab2459b8b7b2d2d8b4e`。
- 下載來源與許可證記錄於 `packaging/build-windows-runtime.py`、執行環境清單及 `THIRD_PARTY_NOTICES.md`。

以下命令在原始碼根目錄的命令提示符中執行。調整 NSIS 與 JDK ZIP 的實際路徑。執行環境輸出目錄須為空或尚不存在。

## 測試、應用與啟動器

```bat
gradlew.bat test stageWindowsApplication -PdesktopTarget=windows-x64 --console=plain
py packaging\build-windows-installer.py --launcher-only --makensis "C:\Program Files (x86)\NSIS\makensis.exe" --output build\native-launcher\Hermes.exe
```

## 構建完整執行環境

```bat
py packaging\build-windows-runtime.py --jdk "C:\Downloads\OpenJDK21U-jdk_x64_windows_hotspot_21.0.12.1_1.zip" --sha256 f9d6e191ab098c0d416e7d588a24420a8621cd2f4720dab2459b8b7b2d2d8b4e --host-jdk "%JAVA_HOME%" --output build\windows-runtime
```

指令碼先核對官方 JDK 位元組，再用 jlink 構建 Windows runtime，並通過 jimage 驗證實際模組。不要改用缺少 `jdk.unsupported.desktop` 的舊 JRE ZIP，也不要只檢查 `release` 文本中的模組名稱。

## 完整便攜包、安裝包和原始碼

```bat
py packaging\assemble-windows-portable.py --app build\windows-staging\app --runtime build\windows-runtime --output build\release\Hermes-Windows-1.8.2-portable.zip
```

該命令輸出 ZIP 的 `sha256`。將它填入下一條命令的 `SHA256_FROM_PREVIOUS_COMMAND` 位置：

```bat
py packaging\build-windows-installer.py --makensis "C:\Program Files (x86)\NSIS\makensis.exe" --portable build\release\Hermes-Windows-1.8.2-portable.zip --portable-sha256 SHA256_FROM_PREVIOUS_COMMAND --output build\release\Hermes-Windows-1.8.2-Setup.exe
py packaging\assemble-source.py --output build\release\Hermes-Windows-1.8.2-source.zip
```

安裝包從完整便攜包生成，並逐項校驗應用依賴與執行環境清單。原始碼包包含檔案校驗表，不包含本機配置、金鑰、快取及構建輸出。

安裝 7-Zip 後可執行以下驗證，比較安裝程式中真實可解壓的載荷與便攜包：

```bat
py packaging\verify-release.py --portable build\release\Hermes-Windows-1.8.2-portable.zip --installer build\release\Hermes-Windows-1.8.2-Setup.exe --sevenzip "C:\Program Files\7-Zip\7z.exe" --report build\release\verification.json
```

本輪發布採用上面的 NSIS 流程。舊的 Compose `Package-Windows.bat` / MSI 指令碼保留用於開發，不是本輪 Setup 的打包入口。歷史 `build-startup-repair.py` 僅針對 1.8.0，不能替代 1.8.2 完整安裝包。

## Linux 構建主機

同樣使用 JDK 21、Python 和 NSIS。先用宿主依賴編譯及測試，再執行 `stageWindowsApplication -PdesktopTarget=windows-x64 -x compileKotlin` 替換為 Windows 原生依賴；這一步必須在最新應用 JAR 編譯完成後執行。`build-windows-runtime.py` 可用 Linux JDK 的 jlink 處理指定的 Windows JDK jmods。設定宿主 `JAVA_HOME`，以便打包器使用 jimage 驗證實際映象。

Linux 自動化與載荷校驗不能替代 Windows 麥克風硬體、系統托盤及安裝升級的實機驗證。

## Windows 跨程式選單檢查

在已解鎖的 Windows 測試桌面，執行 `gradlew.bat writeVerificationClasspath` 後，在 PowerShell 中執行：

```powershell
$menuCheckClasspath = Get-Content -Raw build/verification180/classpath.txt
& "$env:JAVA_HOME/bin/java.exe" -cp $menuCheckClasspath com.qingyu.hermescompanion.desktop.WindowsGlobalMenuCheck
```

這會開啟獨立程序的測試目標並模擬三次滑鼠點選。選單被設為不獲取焦點，用於確認關閉來自全域性滑鼠監聽，而非焦點丟失。請在空閒測試桌面執行；Linux 會明確跳過。
