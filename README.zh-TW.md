[繁體中文語系與驗證](docs/TRADITIONAL-CHINESE.md)

# Hermes Desktop

Hermes Agent 桌面用戶端，支援 Windows x64 與 macOS Apple Silicon（M 系列）。

當前開發版本：**2.0.5（檔案定位修復版）**。已釋出版本見 [Releases](https://github.com/jeromeleeqy-hash/Hermes-Desktop/releases)。

## 核心功能

- 對話管理：工作空間與專案、多對話、搜尋置頂、對話大綱、引用與執行中追加要求。
- 工作台：快速交辦任務、繼續最近對話、檢視近期成果。
- 任務中心：審批與澄清、待處理請求、執行進度、完成記錄、定時任務。
- 檔案工作區：附件預覽、圖片與常見文件預覽、Markdown 編輯與本機草稿。
- 桌面助手：懸浮球、小窗、截圖、語音輸入、托盤與 Mac 選單欄入口。
- AI 配置：模型供應商、預設與備用模型、思考強度、專家會審、Skills、工具集及 MCP。
- 個性化：主題、字級、頭像、回覆風格、長期記憶和助理設定。

對話和工具需要連線已部署的 Hermes Agent；本機語音模型安裝後可離線識別和朗讀。

## 2.0.5 更新

補齊僅含檔名的對話檔案連結：在來源工作目錄及子目錄中查詢，確認唯一後開啟；同名或查詢未完成時顯示候選位置，可貼上完整路徑並複製排查資訊。驗證過的路徑會在重新整理和重啟後保留。詳見 [更新說明](docs/RELEASE-2.0.5.zh-TW.md)、[構建說明](docs/BUILD-2.0.5.zh-TW.md)、[驗收記錄](docs/ACCEPTANCE-2.0.5.zh-TW.md)。

## 2.0.4 更新

區分普通介面與即時通道故障，即時通道重連期間繼續同步訊息；統一隱藏移動端內部回覆偏好；補上手機任務狀態、事件觸發重新整理和當前對話未讀處理。詳見 [更新說明](docs/RELEASE-2.0.4.zh-TW.md)、[構建說明](docs/BUILD-2.0.4.zh-TW.md)、[驗收記錄](docs/ACCEPTANCE-2.0.4.zh-TW.md)。

## 2.0.3 更新

修復對話檔案卡片截斷相對路徑的問題，保留完整中文目錄、空格和括號；重新整理歷史檔案索引，並按原訊息與工作空間恢復舊卡片。詳見 [更新說明](docs/RELEASE-2.0.3.zh-TW.md)、[構建說明](docs/BUILD-2.0.3.zh-TW.md)、[驗收記錄](docs/ACCEPTANCE-2.0.3.zh-TW.md)。

## 2.0.2 更新

修復跨裝置訊息延遲、未讀計數範圍和 macOS 通知跳轉。預設進入簡潔工作台，“值得留意的事”改為可進入和返回的模組；側欄最近對話增加到 8 個。詳見 [更新說明](docs/RELEASE-2.0.2.zh-TW.md)、[構建說明](docs/BUILD-2.0.2.zh-TW.md)、[驗收記錄](docs/ACCEPTANCE-2.0.2.zh-TW.md)。

## 2.0.1 更新

重排待確認操作、固定授權按鈕、取消每條訊息的“已傳送”彈窗；將首頁設定與工具移到事項區域，完善卡片詳情排版。補齊本機 STT／TTS 下載並啟用、模型包匯入和離線試錄／試聽。詳見 [更新說明](docs/RELEASE-2.0.1.zh-TW.md)、[構建說明](docs/BUILD-2.0.1.zh-TW.md)、[驗收記錄](docs/ACCEPTANCE-2.0.1.zh-TW.md)。

## 2.0.0 更新

對齊 Android 4.0.0 的共享首頁協議：簡潔／深入首頁、6 種卡片佈局、20 類互動、事項聊天與增量同步、操作回執和顯式恢復、早晚整理與 Cron 核對。

修復閘道就緒競態、空對話首條訊息、舊執行 ID、跨端授權過期、模型 lazy 回讀和響應斷流；保留草稿及傳送確認，避免不確定寫請求被自動重發。

語音識別／朗讀獨立設定，可下載 SenseVoice／Kokoro 在本機執行；支援完整句子提前朗讀、預取及打斷。新增離線連線指南。OSS 按計劃後續接入。

詳見 [更新說明](docs/RELEASE-2.0.0.zh-TW.md)、[構建說明](docs/BUILD-2.0.0.zh-TW.md) 和 [驗收清單](docs/ACCEPTANCE-2.0.0.zh-TW.md)。

## 下載與構建

本次 Windows 驗收包提供 Setup.exe 和便攜 ZIP；Mac M 系列提供可解壓安裝的 .app.zip。原生構建仍可生成 MSI／DMG。安裝包內建 Java，無需另裝 JDK。

本倉庫已同步安裝包使用的完整原始碼。開發構建需要 JDK 21：

```sh
# Windows
gradlew.bat test -PheadlessTests=true
gradlew.bat packageExe packageMsi packageWindowsPortable
# macOS
bash packaging/prepare-native.sh
bash gradlew test packageDmg -PheadlessTests=true
```

`-PheadlessTests=true` 用於雲端離屏測試；省略該引數可在真實互動桌面執行原生視窗檢查。

## 驗證

本次自動化結果與平臺邊界見 [驗收記錄](docs/ACCEPTANCE-2.0.5.zh-TW.md)。歷史版本的 CI 結果不作為本次的驗證結果。

Windows/macOS 真機安裝、系統麥克風／音色、休眠喚醒及真實閘道聯調需要實機驗收。驗收包無 Windows 商業程式碼簽名或 Apple Developer ID 公證；Mac 應用使用 ad-hoc 簽名。
