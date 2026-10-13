# Hermes Windows 1.7.0 · 配置、閱讀與附件

## 模型供應商

設定新增獨立的“模型供應商”頁面。可配置常用供應商 API Key，新增、編輯自定義 API 位址和模型，測試連線、讀取模型，以及設為新對話的預設供應商。模型目錄載入失敗時，仍可進入供應商設定修正配置。

使用當前工作空間的閘道配置介面；新閘道使用自定義端點介面，舊閘道僅在介面確實未提供時回退到配置合併。更新自定義端點時，API Key 留空會保留已有金鑰，其他供應商及既有高階引數也會保留。金鑰不會回顯。測試已有端點需要臨時輸入金鑰，直接儲存已有配置無需重填。

## 聊天頭像與檔案

- 使用者訊息旁顯示設定中的“我的頭像”；未設定時顯示姓名縮寫。與 F1 應用圖示、Hermes 助理頭像分別管理。
- 檔案選擇器回到桌面事件佇列後開啟，顯示正在讀取和失敗反饋。讀取附件期間切換對話，檔案仍歸屬於原來的草稿。
- 可將本機檔案拖到聊天或工作台輸入框，也可在資源管理器複製檔案後按 Ctrl+V / Shift+Insert 貼上。普通文字貼上維持原有行為。
- 附件先顯示為可移除條目，傳送時才上傳。較小文本作為文本內容，圖片沿用圖片附件通道，PDF、Office 等二進位制原檔案寫入當前工作空間。
- 同一檔案重試使用穩定路徑，並核對伺服器上已有檔案的原始位元組；不覆蓋不同內容。失敗會保留或恢復文字與附件，便於重試。
- 每次最多 10 個檔案；圖片最多 8 MB，其他檔案最多 12 MB。不直接上傳目錄。

## HTML 預覽

內嵌網頁改為非同步初始化，避免在 Compose/AWT 視窗建立階段等待 JavaFX。載入中保留介面響應，加入超時、失敗重試及“檢視原始碼”入口。

HTML、DOM 數量和相對資源讀取設有限額；每個資源請求有超時，圖片和 CSS 讀取也受總量約束。預覽停用網頁尾本、事件處理器和動畫，外部連結由系統瀏覽器開啟。這是靜態網頁閱讀，不執行網頁應用；結構過大時會提示並允許檢視原始碼。

## Markdown 閱讀與雙欄

- 參考 Typora 主題的文件閱讀方式，調整正文寬度、16 sp 字級、1.85 倍行高和段落留白。
- 重新梳理標題層級、引用、列表、程式碼片段、表格和分隔線；中文軟換行不額外插入空格，英文軟換行自然銜接。
- 雙欄預設同步滾動，按 Markdown 內容塊與實際原始碼換行位置對映。可以從任一側滾動，也可隨時切換“獨立滾動”。
- 保留原有目錄、查詢替換、撤銷重做、圖片、列表續寫、草稿、修改對比和儲存衝突檢查。

## 安裝與驗證

推薦退出舊版後執行 `Hermes-Windows-1.7.0-Setup.exe` 升級。便攜更新包需整體替換 `app` 目錄和 `Hermes.exe`，詳見包內更新說明。F1 圖示、當前主題和原有使用者資料目錄保持相容。

自動化覆蓋協議、配置保留、附件原始位元組與失敗恢復、頭像元件、雙向滾動和既有功能；另提供真實 Compose 元件渲染截圖。當前驗證環境為 Linux，Windows 原生選擇器、檔案拖放/剪貼簿、JavaFX 內嵌視窗和安裝執行還需 Windows 真機確認。未連線或更改你的真實閘道。詳細證據見 `WINDOWS-VALIDATION.md`。

實現參考：[Hermes 供應商配置](https://github.com/NousResearch/hermes-agent/blob/main/website/docs/integrations/providers.md)、[Hermes 配置與端點介面](https://github.com/NousResearch/hermes-agent/blob/main/hermes_cli/web_routers/config_env.py)、[Hermes 檔案介面](https://github.com/NousResearch/hermes-agent/blob/main/hermes_cli/web_routers/files.py)、[JavaFX Swing 嵌入](https://openjfx.io/javadoc/11/javafx.swing/javafx/embed/swing/JFXPanel.html)、[Typora 預設主題](https://github.com/typora/typora-default-themes)、[Folio 主題](https://theme.typora.io/theme/Folio/)。Markdown 使用自身 Compose 排版實現，未複製這些主題的素材。
