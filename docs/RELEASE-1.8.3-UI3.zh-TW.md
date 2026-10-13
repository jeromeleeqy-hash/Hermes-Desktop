# Hermes 1.8.3 · UI 修訂 3

本次 Windows 與 macOS 使用同一份修復原始碼，版本號保持 1.8.3。

## 本次修改

- 任務中心：修復「待你確認」「正在執行」卡片底部說明文字只顯示半行。空狀態在卡片內居中，卡片隨字級增加高度，視窗較矮時支援滾動。
- 主視窗：頂部、右側、底部使用同一主題邊距。輕盈辦公和流光玻璃為 8 dp，紙間留白為 12 dp。Mac 紅綠燈預留區域仍保持到頂部 46 dp。
- 回覆風格：從多行輸入框改為下拉選框。內建風格顯示中文名稱；讀取伺服器自定義風格，並保留原有未知選項。
- 風格儲存：按新版 Hermes 的 `display.personality` 儲存名稱；讀取時優先尊重新欄位，相容已有舊欄位。不改動 `agent.system_prompt`、自定義風格定義或 SOUL.md。

風格與儲存欄位對照：[Hermes Agent 官方說明](https://hermes-agent.nousresearch.com/docs/user-guide/features/personality/)（2026-09-15 檢查）。

## 更新方式

- Windows x64：退出 Hermes 後執行 Setup.exe，沿用原安裝位置更新。
- macOS M 系列：解壓 BuildKit.zip，執行 Build-Mac-App.command，生成 Hermes.app 和 DMG 後替換舊應用。
- 本次不會清理使用者設定、對話草稿或工作檔案。

## 檢查範圍

編譯、自動化與 Compose 佈局檢查在 Linux 構建環境執行。Windows 安裝器校驗其內容與便攜包一致；Mac BuildKit 校驗 Apple Silicon 依賴和 JDK，實際 app/DMG 由 Mac 執行生成。這些檢查不能代替 Windows/macOS 真機驗收。

當前檢查記錄：`validation/desktop-1.8.3-ui3-tests.json`、`validation/desktop-1.8.3-ui3-layout.txt`。
