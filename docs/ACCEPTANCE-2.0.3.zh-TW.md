# Hermes Desktop 2.0.3 驗收記錄

- 66 項 JVM / 真實 HTTP / 桌面狀態迴歸測試通過，0 失敗；另有 17 項 Python 測試通過。
- 已復現並修正相對路徑截斷；驗證中文目錄、空格、括號、Windows 磁碟機代號及 UNC、連結編碼保留。
- 驗證舊卡片從原訊息恢復路徑、歷史索引替換、跨工作空間隔離、同名歧義不猜測、403 不嘗試其他路徑。
- 測試 XML 見 validation/desktop-2.0.3-junit，彙總見 validation/desktop-2.0.3-validation.json。
- 安裝包組裝指令碼核驗依賴與應用 JAR 雜湊，Mac 重新 ad-hoc 簽名並檢查資源封裝與歸檔許可權，Windows 驗證執行時清單與 NSIS 安裝包載荷。

未在 Windows / macOS 真機執行安裝啟動；真實使用者伺服器檔案是否存在，仍由伺服器實際狀態決定。
