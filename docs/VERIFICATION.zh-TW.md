# 驗證記錄

構建環境：Linux、JDK 21。2026-09-10。

主程式編譯和 `test` 成功；39 項測試通過，0 失敗、0 錯誤、0 跳過。

| 測試套件 | 數量 |
| --- | --- |
| AudioFileRequestBodyTest | 3 |
| ChatInsightsTest | 1 |
| ConcurrentGatewayTest | 11 |
| HermesApiClientTest | 16 |
| RemotePaths361Test | 4 |
| DecisionCodecTest | 1 |
| DesktopStoreTest | 3 |

測試範圍：複用的 HTTP / WebSocket 協議、併發對話路由、音訊請求、產物解析、路徑相容；新增加密儲存、Cookie 隔離和待處理決策編解碼。測試伺服器均為本機模擬。

四個頁面使用 `renderPreviews` 從實際 Compose 程式碼渲染；渲染指令碼給非同步字型載入和 UI 回撥預留穩定時間。系統標題欄、JavaFX 富文本編輯檢視與麥克風不在無介面渲染範圍內。

構建存在 Compose 舊資源載入介面和 ClickableText 的棄用提示，未阻止編譯。Mac 指令碼通過 shell 語法檢查，DMG 沒有在本環境生成。

詳見 MAC-ACCEPTANCE.md 的真機驗收範圍。
