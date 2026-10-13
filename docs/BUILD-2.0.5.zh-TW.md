# Hermes Desktop 2.0.5 構建說明

應用使用 JDK 21、Gradle 8.13、Kotlin 2.3.20 與 Compose Desktop 1.10.1，從本版原始碼編譯。

使用 `-PheadlessTests=true` 執行檔案路徑、檔案發現、索引持久化、工作空間恢復、伺服器路徑、Markdown 附件與首頁讀取相關回歸；`renderArtifactLookup205` 使用同一 Compose 介面渲染候選路徑彈窗，檢查普通視窗及小視窗的底部按鈕可見性。

平臺依賴宣告未改變。校驗 2.0.4 Mac / Windows 包與依賴清單後複用原生執行時及依賴，替換新編譯的應用 JAR；重新構建 Windows 啟動器、安裝器和 Mac ad-hoc 簽名，並驗證歸檔與資源完整性。

Mac 使用單個 Hermes.app 根目錄的 ZIP。未提供未經實際製作的 DMG，未做 Apple Developer ID 公證，也未在 macOS / Windows 真機執行。
