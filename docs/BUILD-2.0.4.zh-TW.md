# Hermes Desktop 2.0.4 構建說明

應用使用 JDK 21、Gradle 8.13、Kotlin 2.3.20 與 Compose Desktop 1.10.1，從本版原始碼編譯。

測試命令使用 `-PheadlessTests=true`，執行連線恢復、真實 HTTP / WebSocket 跨端同步、訊息顯示、未讀、任務和工作空間恢復迴歸。

依賴宣告未改變。以 SHA-256 驗證 2.0.3 平臺包及清單後複用平臺依賴與原生執行時，替換新編譯的應用 JAR。Windows 原生啟動器和 NSIS 安裝器重新構建；Mac 重新進行 ad-hoc 簽名並驗證內碼表、資源和歸檔許可權。

Mac 提供只有一個 Hermes.app 根目錄的 ZIP；未用 Linux 生成偽裝的 DMG。Mac 包沒有 Apple Developer ID 公證。Windows / macOS 真機安裝啟動未執行。
