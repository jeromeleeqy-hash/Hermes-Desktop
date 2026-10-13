# 構建 Hermes 1.5.0

應用使用 JDK 21、Gradle 8.13。先完整解壓原始碼，執行 `gradlew test`；Windows 上也可雙擊 `Build-Windows.bat` 或 `Package-Windows.bat` 生成 jpackage 便攜應用。原有 `Installer-Windows.bat` 生成 MSI，需要 WiX 3.x。

本次交付的 Setup.exe 採用另一條可復現的 NSIS 3.09 流程，Linux 和 Windows 均可使用：

1. `gradlew stageWindowsApplication -PdesktopTarget=windows-x64` 輸出 `build/windows-staging/app`。
2. 使用 `packaging/build-windows-installer.py --launcher-only --output build/native-launcher/Hermes.exe` 編譯啟動器。可用 `--makensis` 指定 NSIS 編譯器，Linux 解壓版另傳 `--nsis-dir`。
3. 用 `packaging/assemble-windows-from-portable.py --app build/windows-staging/app --base <已验证的旧便携包> --base-sha256 <SHA256> --output <新便携包>`。僅當全部第三方依賴與基包逐位元組相同時複用其執行環境。或用 `assemble-windows-portable.py` 從獨立 Windows JRE 壓縮包構建。
4. 用 `build-windows-installer.py --portable <新便携包> --portable-sha256 <SHA256> --output Hermes-Windows-1.5.0-Setup.exe`。安裝包複用經過 CRC 和 JAR SHA 校驗的同一份便攜包內容。
5. `assemble-windows-update.py` 生成小更新；`assemble-source.py` 生成含逐檔案 SHA256 清單的原始碼包。

UI 截圖用 `gradlew renderPreviews --args="build/previews chat-office chat-paper chat-glass files-split-wide chat-workspace-menu settings"` 生成。它使用真實 Compose 元件和演示資料，不需要訪問個人閘道。

Fluent 圖示的上游包名、版本、檔案對應和 SHA256 在 `docs/fluent-icons.json`。需要更新資源時，解壓已校驗的 `@fluentui/svg-icons` npm 包後執行 `python packaging/import-fluent-icons.py <package目录>`；不改寫上游圖形路徑。
