# 構建 Hermes Desktop 2.0.1

使用 JDK 21、Gradle 8.13、Kotlin 2.3.20、Compose Desktop 1.10.1。原始碼包含 Gradle wrapper。

```sh
bash gradlew test -PheadlessTests=true
bash gradlew render201Previews
python3 -m unittest discover -s tests
```

`render201Previews` 使用實際應用元件生成簡潔首頁、深入首頁、長命令、小視窗 130% 字級、卡片詳情和語音設定圖。`DecisionLayout201Test` 檢查 120 行命令下按鈕的實際座標與可點選性。

```sh
bash gradlew localVoiceSmoke --args=/absolute/model-cache
```

模型目錄需包含 `sense-voice-int8.tar.bz2` 和 `kokoro-int8.tar.bz2`。任務使用與應用一致的匯入、大小與摘要校驗，再實際執行 Kokoro → WAV → SenseVoice；不訪問麥克風或 Hermes 伺服器。

隨後可執行 `bash gradlew offlineVoiceCheck --args=/absolute/model-cache` 驗證沒有閘道配置的真實應用呼叫鏈；只有麥克風和揚聲器由固定 WAV 與記憶體輸出替代，識別／合成仍呼叫真實模型。

原生 Windows 使用 `gradlew.bat packageExe packageMsi packageWindowsPortable`。原生 Apple Silicon 使用 `bash packaging/prepare-native.sh` 後執行 `bash gradlew packageDmg`。雲端跨平臺暫存分別使用 `stageWindowsApplication -PdesktopTarget=windows-x64` 和 `stageMacApplication -PdesktopTarget=macos-arm64`；不得混入其他平臺原生庫。

此次 Mac 包複用使用者已確認可開啟的 2.0.0 `.app.zip` 中的 ARM64 啟動器、JDK 執行時和系統語音 helper，替換全部應用 JAR、更新版本配置，重新進行 ad-hoc 簽名。`assemble-macos-from-release.py` 核對基包 SHA-256、每個 Mach-O 的內碼表、應用資源封印、JAR 清單、Unix 許可權與單層 `.app` 歸檔。

Windows 複用已驗證完整的 Windows Temurin 21 執行時，重新編譯 NSIS 啟動器；便攜包組合新 Windows 暫存依賴，安裝器由同一便攜包生成。構建指令碼支援自定義 NSIS 路徑，無需系統安裝。

構建輸出可放在臨時磁碟，原始碼目錄的 `build` 指向該輸出後執行打包指令碼；原始碼歸檔排除符號連結、構建產物和快取。執行時不攜帶構建環境代理配置。

版本更新指令碼、原始檔、預覽與記錄均隨原始碼提供。原生麥克風、系統許可權、實際揚聲器和雙擊安裝必須在對應系統驗收。
