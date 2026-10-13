## 2026-09-15 · UI 修訂 3

本輪檢查記錄見 [更新說明](RELEASE-1.8.3-UI3.zh-TW.md)。以下歷史檢查保留用於追溯，不表示本輪重新執行了所有原生檢查。

# macOS ARM64 1.8.3 · 驗證範圍

本輪基於已交付的 Mac 1.8.3 原始碼完成介面修訂 2。版本號仍為 1.8.3，新的原始碼包與 BuildKit 包含本輪全部修改。

## 已執行

- Linux JDK 21 編譯通過。
- 本輪 244 項 JUnit 迴歸：240 項通過、0 項失敗/錯誤、4 項跳過（2 項 Windows DPAPI、2 項需要可見圖形環境的 AWT 互動測試）。結果見 `validation/macos-1.8.3-tests.json`。
- Mac 新增測試通過：Dock 重開回調、原生退出先取消系統預設退出再執行應用儲存流程、釋放監聽後不執行殘留事件、無重開支援時的降級、螢幕許可權已允許/申請成功/拒絕三個分支，以及 Mac Control＋點選不會進入普通左鍵語音手勢。
- 原生 Desktop 事件使用 Mockito，螢幕許可權分支使用可注入回撥；這些測試驗證程式處理邏輯，不代表已經執行 Apple 系統 API。
- 上一輪已完成的 AWT/Compose 視窗共享互動檢查在 Linux Xvfb 中執行，日誌 `validation/macos-1.8.3-native-check.txt`。截圖位於 `previews/macos-1.8.3-linux-review/`，不標作 Mac 系統截圖。
- 打包指令碼通過 Bash 語法檢查，非 macOS 上會拒絕繼續，避免產生偽裝成 Mac 應用的檔案。
- 官方 Mac ARM64 JDK SHA-256 校驗、Java/jlink/jpackage Mach-O ARM64 檢查、JavaFX/Skiko 動態庫架構檢查、應用 JAR 與 staging 一致性和 ZIP 逐檔案校驗。結果見 `validation/macos-1.8.3-package.json`。

## 本輪新增驗證與原生介面依據

- 本輪測試總數、通過數見 `validation/macos-1.8.3-tests.json`。新增檢查覆蓋原生幾何結構 ABI、不同視窗高度/按鈕尺寸下的側欄避讓、單色模板圖示的透明邊距及 Retina 解析度。
- 本輪 Compose 佈局實渲染日誌見 `validation/macos-1.8.3-appearance.txt`；展開/收起側欄與圖示樣張在 `previews/macos-1.8.3-appearance/`。這些是 Linux 執行的共享 Compose 內容，不繪製假的 macOS 紅綠燈，也不是 macOS 狀態列截圖。
- 標題欄使用 JDK 21 `apple.awt.fullWindowContent`、`apple.awt.transparentTitleBar`、`apple.awt.windowTitleVisible` 根面板屬性。位置調整在 AppKit 主執行緒執行，通過 NSApplication 的視窗列表識別唯一的主視窗並設定標識，避免跨執行緒儲存 JAWT 裸檢視指標。關閉後忽略排隊回撥；全屏時交由系統控制原生按鈕位置。
- 選單使用 AWT PopupMenu；其 macOS peer 直接呼叫 NSMenu `popUpMenuPositioningItem:atLocation:inView:`，無需輔助功能或全域性滑鼠監控許可權。
- 圖示使用 `apple.awt.enableTemplateImages=true` 和多解析度透明 alpha 模板，保留 Dock/懸浮球的彩色頭像。
- 原生視窗、結構返回 ABI、選單跟蹤和 AppKit 明暗著色仍需使用者的 macOS 27 真機驗收；本輪 Linux 測試不代替這些檢查。

介面原始碼：
- [OpenJDK CPlatformWindow](https://github.com/openjdk/jdk21u/blob/master/src/java.desktop/macosx/classes/sun/lwawt/macosx/CPlatformWindow.java)
- [OpenJDK CTrayIcon](https://github.com/openjdk/jdk21u/blob/master/src/java.desktop/macosx/classes/sun/lwawt/macosx/CTrayIcon.java)
- [OpenJDK CPopupMenu](https://github.com/openjdk/jdk21u/blob/master/src/java.desktop/macosx/native/libawt_lwawt/awt/CPopupMenu.m)
- [Electron 原生標題欄容器的佈局參考](https://github.com/electron/electron/blob/main/shell/browser/ui/cocoa/window_buttons_proxy.mm)

## 適配內容

- Mac 正常視窗下啟用桌面懸浮球，不依賴 Windows 無邊框視窗開關。
- 關閉主視窗後繼續駐留；選單欄和 Dock 可恢復。無可用恢復入口時降級為最小化。
- Mac 應用選單中的退出、設定和關於接入已有控制器，退出保留儲存檢查。
- Mac 選單欄和懸浮球均使用原生選單，懸浮球選單的外部點選與 Escape 取消由 NSMenu 處理。
- 支援 Control＋點選撥出懸浮選單，Command＋滾輪縮放圖片。
- 截圖先檢查系統螢幕錄製授權；被拒絕時停止截圖並給出指引。
- 新增 Apple Silicon 依賴 staging 和離線 Mac 打包檔案，統一版本 1.8.3。

## 上游依賴說明

官方 `skiko-awt-runtime-macos-arm64-0.9.37.4.jar` 同時帶有 ARM64 和 x64 兩個資源。檢查實際 `Library.findAndLoad()` 位元組碼後，確認它按 `hostId` 選擇對應庫；保留原始 JAR，不重打包修改依賴。校驗器強制檢查 ARM64 資源存在且架構正確，並將額外未使用的 x64 資源單獨記錄。JavaFX 原生檔案仍逐個要求包含 ARM64 架構。

## 尚未執行

當前執行主機是 Linux，沒有 macOS 27 真機。沒有執行最終 Mac jlink / jpackage / codesign / hdiutil 流程，尚未生成實際 `.app` 或 `.dmg`。交付 BuildKit 會在使用者 Mac 上執行這些步驟並校驗結果。

Mac 系統選單欄與 Dock、系統截圖及麥克風許可權、鑰匙串、Swift 系統識別、Retina/多屏和實際閘道連線仍需在 Mac 驗收。沒有 Apple Developer ID 簽名和公證；本機打包預設使用 ad-hoc 簽名。

本輪沒有上傳原始碼到 GitHub，也沒有修改使用者遠端閘道、舊安裝或個人資料。
