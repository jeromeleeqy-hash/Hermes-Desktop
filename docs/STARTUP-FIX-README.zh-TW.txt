Hermes Windows 1.8.0 啟動修復包

推薦直接運行同版本 Startup-Repair.exe 自動修復。
如果使用這個 ZIP 手動修復：

1. 如果 Hermes 仍在執行，請在右下角托盤右鍵選擇“退出 Hermes”。
2. 找到原 Hermes.exe 所在目錄。安裝版通常在 %LOCALAPPDATA%\Programs\Hermes。
3. 完整解壓本 ZIP，將 runtime 資料夾和所有啟動檔案複製到該目錄，確認替換同名檔案。
4. 保留原 app 資料夾，不需要修改它。
5. 雙擊 Hermes.exe 啟動。

修復不修改 %LOCALAPPDATA%\HermesDesktop 內的閘道、頭像、對話或草稿。
本包僅修復已有 Hermes 1.8.0，不能單獨執行。

如果仍然打不開，執行 Diagnose-Hermes.bat，將生成的完整 startup-*.log 發給開發者。
日誌路徑：%LOCALAPPDATA%\HermesDesktop\logs。
