# 1.8.4 構建與驗收

本交付為完整開發原始碼，尚未完成全量編譯驗證。需要對應作業系統、JDK 21 和可訪問 Gradle/Maven 的網路。

## Windows x64

在專案根目錄開啟終端：

```bat
gradlew.bat test --console=plain
gradlew.bat packageWindowsPortable --console=plain
```

便攜執行包位於 `build/windows/Hermes-Windows-x64.zip`，內含 Java 執行時。EXE/MSI 安裝程式可在具備 Compose 打包依賴的 Windows 上執行 `gradlew.bat packageExe` 或 `packageMsi`。本次沒有生成上述二進位制檔案。

## macOS

使用與晶片匹配的 JDK 21。專案目錄執行：

```bash
chmod +x gradlew
bash Build-macOS.command
```

此指令碼編譯原生元件，執行測試並構建 DMG。檢視 `build/compose/binaries/main/dmg`。也可使用原有 Package-macOS.command。

## GitHub Actions

新增 `.github/workflows/build-desktop.yml`：手動觸發，對所選分支當前原始碼執行 Windows/macOS 測試與打包，產物包括 Windows 便攜 ZIP、EXE/MSI 安裝程式和 macOS DMG。只上傳構建產物，不修改 main、不釋出 Release。替換舊的從 1.8.3 Release 下載舊原始碼並覆蓋主分支的構建流程。

該流程尚未在本次任務中觸發或驗證。當前未連線並寫入你的 GitHub 倉庫。

## 獨立圖片檢查

```bash
javac -encoding UTF-8 -d build/image-check src/main/java/com/qingyu/hermescompanion/platform/ModernImageSupport.java src/test/java/com/qingyu/hermescompanion/platform/ModernImageSupportCheck.java
java -Djava.awt.headless=true -cp build/image-check com.qingyu.hermescompanion.platform.ModernImageSupportCheck
```

更多功能邊界和實機驗收專案見 RELEASE-1.8.4.md。

## 雲端測試修訂

首次原生編譯已通過。自動測試發現模型切換模擬服務未返回實際模型，以及 macOS 測試快捷鍵誤用 Ctrl，已修正測試夾具並新增伺服器保留原模型的迴歸檢查。
雲端執行 `test -PheadlessTests=true`，使用離屏 Compose 渲染。需要真實互動桌面的 AWT Robot/系統選擇器測試遵循原有 headless 跳過條件；本機執行普通 `test` 可執行這些檢查。不能將雲端通過替代實機驗收。
