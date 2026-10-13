# Hermes Windows 1.8.0 啟動修復版

構建標識：`1.8.0+startup.1`，應用功能版本仍為 1.8.0。

## 原因

1.8.0 使用 JavaFX 命名模組啟動。此前捆綁的 Temurin JRE 的實際模組映象不包含 `jdk.unsupported.desktop`，而 `javafx.swing` 必須依賴它。JVM 在執行應用程式碼前就會退出。

舊執行環境的 `release` 檔案雖然列出了這個模組，實際 `runtime/lib/modules` 中卻沒有。現已改為檢查實際映象，不能只信後設資料。

使用與原 Windows 執行環境相同的模組集合以及發行包內 Windows JavaFX JAR，已復現：

```
Error occurred during initialization of boot layer
java.lang.module.FindException: Module jdk.unsupported.desktop not found, required by javafx.swing
```

退出碼為 1，錯誤寫在標準輸出（stdout），標準錯誤（stderr）為空。舊啟動器只打開 stderr 檔案，因此使用者看到空白日誌。

## 修復

- 從校驗 SHA-256 的官方 Windows x64 JDK 21.0.12.1 重新連結執行環境，保留舊模組集合並補齊桌面互操作模組。
- 啟動前驗證實際桌面模組和 JavaFX 模組依賴。
- 使用一個共同的啟動指令碼和引數檔案。常規啟動、診斷啟動保持一致。
- 在 JVM 啟動前寫入時間、應用位置和版本，持續將 stdout 與 stderr 合併到一個日誌中，保留退出碼。
- 原生啟動器監督整個程序，不再僅觀察啟動後的 1.5 秒。退出到托盤會正常保持執行。
- 不依賴 PowerShell 啟動，不更改 Windows 指令碼執行策略。
- 打包工具拒絕缺少必需模組的執行環境，並驗證安裝包的完整執行環境檔案清單。

## 安裝

推薦已有 1.8.0 的使用者執行 `Hermes-Windows-1.8.0-Startup-Repair.exe`，自動定位安裝目錄；便攜版可選擇 Hermes.exe 所在資料夾。

修復包只更新執行環境與啟動檔案。閘道、對話、草稿、頭像、偏好設定和應用 JAR 保持原樣，無需解除安裝或另裝 Java。

也提供修復覆蓋 ZIP，以及已包含修復的完整安裝包和便攜包。舊的未修復 1.8.0 安裝包不應繼續使用。

## 日誌

完整日誌在 `%LOCALAPPDATA%\HermesDesktop\logs\startup-*.log`。啟動異常會自動開啟該日誌。

`Diagnose-Hermes.bat` 會走相同啟動鏈路，在控制台顯示日誌及檔案位置。不要再只收集舊版 `*-error.log`。

## 執行環境來源

[官方 Eclipse Temurin Windows JDK 21.0.12.1](https://github.com/adoptium/temurin21-binaries/releases/tag/jdk-21.0.12.1%2B1)

歸檔：`OpenJDK21U-jdk_x64_windows_hotspot_21.0.12.1_1.zip`

SHA-256：`f9d6e191ab098c0d416e7d588a24420a8621cd2f4720dab2459b8b7b2d2d8b4e`

模組依賴問題也有[執行環境維護方的問題記錄](https://github.com/adoptium/adoptium-support/issues/12)。本次根因以發行包的實際模組檢查和本機復現為依據。

## 驗證範圍

本次針對啟動鏈路、模組依賴、網頁元件初始化、分發檔案一致性做迴歸。應用 JAR 與已交付 1.8.0 相同。

驗證結果與限制見 `validation/windows-1.8.0-startup-fix.json`。當前構建環境為 Linux；未在真實 Windows 桌面執行安裝嚮導、系統托盤或音訊裝置測試。
