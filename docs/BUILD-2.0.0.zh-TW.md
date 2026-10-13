# 構建 Hermes Desktop 2.0.0

需要 JDK 21。執行 `./gradlew test -PheadlessTests=true`，以及 `python3 -m unittest discover -s tests`。

原始碼包保留 `gradlew`、macOS `.command` 入口和原生準備指令碼的執行許可權。如果解壓工具移除了許可權，可先執行 `chmod +x gradlew *.command packaging/prepare-native.sh packaging/macos/Build-Mac-App.command`。僅執行 Gradle 時也可以使用 `bash gradlew`。

Windows 原生環境執行 `gradlew.bat packageExe packageMsi packageWindowsPortable`。
macOS Apple Silicon 環境執行 `./gradlew packageDmg`；系統語音 helper 使用倉庫的 `packaging/NativeSpeech.swift` 構建。

跨平臺暫存可使用 `stageWindowsApplication -PdesktopTarget=windows-x64` 或 `stageMacApplication -PdesktopTarget=macos-arm64`。構建選擇對應 sherpa-onnx 原生庫，不能將 Linux 庫放入 Windows/macOS 包。

Windows 驗收包使用 `build-windows-runtime.py` 從 SHA-256 已校驗的 Windows Temurin JDK 連結執行時；使用 `assemble-windows-portable.py` 組合 Windows 暫存應用與執行時，再由 `build-windows-installer.py` 從同一便攜包生成安裝器。禁止把 Linux 原生庫重新命名後裝入目標包。

Mac 驗收包使用 `assemble-macos-from-release.py` 從 SHA-256 已校驗的 1.9.0 DMG 中提取啟動器、執行時和系統語音 helper，替換全部應用 JAR、更新版本與配置、恢復可執行許可權、重新 ad-hoc 簽名並核對資源封印，再輸出 .app.zip。原生 `packageDmg` 仍保留。

2026-10-08 打包修訂 r2：`.app.zip` 頂層必須只有 `Hermes.app/`，禁止另放說明檔案。否則 Archive Utility 可能建立同名 `.app` 外層目錄，Finder 會把沒有 `Contents/Info.plist` 的外層當作損壞的應用。說明已在簽名前放進 `Contents/Resources/Release/`。統一用 `macos_archive.write_app_archive` 輸出並校驗歸檔；打包迴歸執行 `python3 -m unittest discover -s tests -p test_macos_archive.py -v`。完整記錄見 `MACOS-PACKAGING-REPAIR-2.0.0.md`。

可執行 `render200Previews` 生成九個離屏場景。`localVoiceSmoke --args=/absolute/model-cache` 使用真實模型執行 TTS → WAV → STT；該目錄需有 `sense-voice-int8.tar.bz2` 和 `kokoro-int8.tar.bz2`，摘要必須與 `LocalVoiceModels.kt` 一致。此任務不訪問麥克風或 Hermes 伺服器。

打包來源與摘要見驗收記錄和各包 manifest。
