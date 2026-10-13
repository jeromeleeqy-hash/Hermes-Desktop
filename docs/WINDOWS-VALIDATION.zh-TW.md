## 2026-09-15 · UI 修訂 3

本輪檢查記錄見 [更新說明](RELEASE-1.8.3-UI3.zh-TW.md)。以下歷史檢查保留用於追溯，不表示本輪重新執行了所有原生檢查。

# Windows 1.8.3 驗證範圍

## 本次迴歸

- 237 項自動化測試：235 項通過、0 項失敗、2 項跳過。跳過項為 Windows DPAPI 原生加密檢查；本機為 Linux。逐套件結果和跳過項見 `validation/windows-1.8.3-tests.json`。
- 新增迴歸覆蓋英文流式空格與換行、新版伺服器端 JSON-RPC clarify / approval、舊版結構化問題事件、啟動訊息前的請求、多問題恢復與鎖定答案、原始請求 id 回答、按對話隔離取消、待確認事項進入任務中心、附件原始位元組讀取及本機狀態恢復。
- Linux Xvfb 的實際 AWT/Compose 視窗中，Robot 滑鼠點選確認浮窗和主輸入框的附件名開啟預覽；Esc 關閉預覽，附件和草稿保留；只有獨立 × 按鈕刪除附件。
- 所有 Hermes 主視窗隱藏後，實際鍵盤 Esc 取消截圖，回撥僅執行一次；右鍵取消及拖動完成選區均通過。執行記錄：`validation/windows-1.8.3-native-check.txt`。
- 實際渲染截圖位於 `previews/windows-1.8.3/`：浮窗、圖片預覽、多問題選擇面板和懸浮選單。檢查圖片完整顯示、縮放按鈕、獨立附件刪除按鈕、多選/單選及填寫區域。陰影使用顯式模糊引數與 24 dp 透明邊距。

## 交付包

- 從校驗 SHA-256 的官方 Windows x64 JDK 21 構建完整執行環境，保留 `jdk.unsupported.desktop`，無需另裝 Java。
- 應用、啟動器與安裝器版本均為 1.8.3；Windows JavaFX / Skiko 原生依賴單獨組裝。
- `packaging/verify-release.py` 解開最終 NSIS 安裝器，並逐檔案比較其真實載荷與便攜包；檢查實際執行環境模組、PE 版本、新預覽與確認協議類及內建字型。最終報告見原始碼包的 `validation/windows-1.8.3-package.json`。
- 原始碼包附 `SOURCE-SHA256.txt`，打包時逐檔案重新讀取 ZIP 校驗。

## 實機限制

本輪沒有 Windows 真機，未執行 Windows 安裝升級、系統托盤外殼、多顯示器混合 DPI 和 DPAPI 原生測試。上述實窗檢查在 Linux Xvfb 完成，不等同於 Windows 系統桌面驗收。

閘道測試使用本機 WebSocket 協議夾具，未連線使用者實際服務。恢復選項依賴伺服器發出請求或提供 `open_requests` / 舊版 pending 欄位。升級前請在托盤中退出舊版 Hermes；賬號配置和草稿沿用原使用者目錄。舊版本已儲存為粘連狀態的思考文本無法可靠恢復空格。

## 歷史驗證（不計入 1.8.3 本次結果）

# Windows 1.8.2 驗證範圍

## 本次驗證

- 23 項定向迴歸全部通過，0 項失敗、0 項跳過。包含選單定位、關閉訊號去重、關閉後取消回撥、重新開啟隔離、懸浮球和按住/鬆開語音路徑。結果：`validation/windows-1.8.2-tests.json`。
- 使用實際 Compose 導航元件在 100% / 200% 縮放下檢查工作空間圖示、最近對話和展開按鈕的中心座標，均位於 64 dp 導航欄的中軸線上。640 dp 高度下底部按鈕可見；展開模式的四條對話由受約束的滾動區域承載。
- Linux Xvfb 實窗在 100% / 200% 下驗證共享選單：滑鼠點選、狀態更新、鍵盤方向鍵/Enter/Esc、同一 JVM 外部視窗點選、重複開啟、主題/大字級、實際懸浮球右鍵、提問與隱藏懸浮球。通過，記錄：`validation/windows-1.8.2-native-check.txt`。
- 當前介面截圖：`previews/windows-1.8.2/`，含完整聊天佈局、展開/收窄導航與兩種縮放下的桌面選單。
- 交付包校驗檢查 1.8.2 應用與啟動器版本、Windows 原生依賴、實際執行環境模組、新全域性滑鼠監聽類、最近對話元件、中文字型和全部安裝載荷。最終報告：原始碼的 `validation/windows-1.8.2-package.json`。

## Windows 原生分支與限制

本輪構建和已執行的實窗檢查運行於 Linux。新增的 `WH_MOUSE_LL` 全域性滑鼠分支未在 Windows 真機執行；Linux 視窗點選通過不等於跨程式點選已在 Windows 驗證。安裝升級、系統托盤殼、多顯示器混合 DPI 和 Windows 滑鼠鉤子的實際效果仍未作實機驗收。

原始碼附 `WindowsGlobalMenuCheck`：在 Windows 上啟動獨立 JVM 點選目標，關閉選單視窗的焦點能力，驗證跨程式點選仍能關閉選單、原點選傳給目標、重複開關後監聽執行緒退出。該檢查在 Linux 明確返回 SKIP；未混入上述 23 項通過數。

實現依據：微軟 [LowLevelMouseProc](https://learn.microsoft.com/en-us/windows/win32/winmsg/lowlevelmouseproc) 和 [WindowFromPhysicalPoint](https://learn.microsoft.com/en-us/windows/win32/api/winuser/nf-winuser-windowfromphysicalpoint)。全域性監聽只在桌面選單開啟期間存在，在專用訊息執行緒處理，點選通過鉤子鏈繼續傳遞；原生命中測試使用物理座標，避免與 AWT 邏輯座標混用。

## 歷史驗證（不計入 1.8.2 本次結果）

# Windows 1.8.1 驗證範圍

## 介面修正版（本輪）

- 選單定位、懸浮球手勢和按住說話相關的 19 項定向迴歸全部通過，0 項跳過。結果見 `validation/windows-1.8.1-ui-tests.json`。
- 在 Linux Xvfb 的真實 AWT/Compose 視窗中，以 100% 和 200% 顯示縮放驗證托盤選單元件及實際懸浮球右鍵入口：點選、開關狀態、鍵盤選擇、Enter、Esc、點選外部關閉、單一活動選單、提問與關閉懸浮球均通過。選單不依賴主視窗可見。
- 檢查深色主題與 130% 文字大小，中文使用應用內建字型；重新開啟選單複用同一視窗，跨顯示器時按該顯示器配置重建。
- 左上角佈局及桌面選單的實際截圖儲存在 `previews/windows-1.8.1-ui/`。原生視窗檢查結果見 `validation/windows-1.8.1-ui-native-check.txt`。
- Windows 安裝程式與最終便攜載荷逐檔案校驗；同時檢查新選單類、左上角佈局及內建字型確實存在於交付 JAR 中。打包結果見原始碼中的 `validation/windows-1.8.1-ui-package.json`。

本輪構建與上述實窗驗證運行於 Linux。沒有執行 Windows 真機安裝；Windows 系統托盤本身、多顯示器混合 DPI 及實際工作列行為仍需在 Windows 上驗收。原生檢查使用與托盤相同的選單視窗、真實懸浮球入口和本機演示控制器，沒有連線使用者閘道。

## 1.8.1 首發：語音與手勢

本輪覆蓋完整的按住/鬆開語音路徑：真實 DesktopController、加密語音記錄、HTTP 轉寫與 WebSocket 訊息提交。物理麥克風由確定的 WAV 資料來源替代；沒有使用或修改使用者的真實閘道。

- 按住時停頓不會提前結束；鬆開立即停止錄音，並且只提交一次。
- 轉寫完成前不會傳送；錄音上限先結束時，也等待滑鼠鬆開再傳送。
- 主視窗停留在其他頁面和對話時，仍傳送到懸浮球選中的對話。
- 對話準備期間鬆開不會延遲開啟麥克風；識別期間重複按住不重複錄音或提交。
- 取消、切換對話後保留原對話的可用錄音，不自動發給新對話。
- 空識別不傳送；失敗錄音可重新識別到草稿；模型切換期間保留識別文字。
- 忙碌對話接收下一條語音佇列；舊任務回撥不會覆蓋新錄音狀態。
- 無論是否開啟自動朗讀，按住模式都不會自動啟動下一輪麥克風。

首發版本完整迴歸共 221 項：217 項通過，0 項失敗，4 項因平臺條件跳過。結果見 `validation/windows-1.8.1-tests.json`。

使用 Linux Xvfb、真實 AWT/Compose 視窗和 Robot 滑鼠事件，驗證長按後開啟小窗、移出懸浮球后鬆開、拖動和單擊。彈出小窗在按住期間不搶焦點。結果見 `validation/windows-1.8.1-native-hold-check.txt`。

## 啟動與打包

1.8.1 使用與已修復的 1.8.0 相同的 Windows x64 執行環境，包括實際映象中的 `jdk.unsupported.desktop`，沒有複用原有不完整 JRE。打包程式檢查實際模組映象、Windows PE、JavaFX 平臺、主程式版本、每個依賴和執行環境檔案的 SHA-256。

NSIS 啟動器與安裝器的檔案版本為 1.8.1.0，應用顯示 1.8.1。啟動日誌在 JVM 執行前寫入標題，合併 stdout 與 stderr。`packaging/verify-release.py` 對安裝 EXE 可解壓出的真實載荷逐項比對便攜包，並驗證主程式版本及啟動檔案。

使用首發版本的最終應用 JAR，在含中文、空格和 & 的目錄下，經 Java 引數檔案啟動並確認主視窗顯示（1440 × 900），退出碼為 0。該檢查使用 Linux 對應執行環境，記錄見 `validation/windows-1.8.1-startup.json`。

## 環境限制

自動化環境是 Linux + JDK 21。Windows 系統麥克風硬體、DPAPI、工作列、跨顯示器實際滑鼠捕獲和最終安裝升級未在本輪 Windows 真機執行。麥克風識別質量還取決於本機裝置及所選識別服務。跳過項在迴歸結果中明確記錄。

文件預覽、圖示、窗口布局與主題保持既有實現。1.8.0 的真實文件視窗驗證與截圖保留在 `validation/windows-1.8.0-native-doc-check.txt`、`previews/windows-1.8.0/`；它們是歷史驗證證據。本輪未將這些歷史截圖當作新版本實機截圖。
