# Hermes macOS ARM64 1.8.3 構建

## 在 M 系列 Mac 從原始碼構建

需要 Apple Silicon JDK 21。完整解壓後執行 `Package-macOS.command`，先測試再打包則執行 `Build-macOS.command`。Swift 系統識別輔助程式是可選項，缺少 Apple Command Line Tools 時仍可使用伺服器識別。

```bash
bash Package-macOS.command
```

輸出：`build/compose/binaries/main/dmg/`。正式公開發行時需要單獨配置 Developer ID 簽名與公證。

## Linux 上準備離線打包檔案

JVM 程式碼先用本機依賴編譯與測試，再組裝真實的 Mac ARM64 原生依賴，不把 Linux 動態庫混入目標包：

```bash
./gradlew test writeVerificationClasspath jar
./gradlew stageMacApplication -PdesktopTarget=macos-arm64 -x compileKotlin
python3 packaging/assemble-macos-kit.py \
  --app build/macos-staging/app \
  --jdk /path/to/jdk-macos-arm64.tar.gz \
  --origin /path/to/macos-jdk-origin.json \
  --output build/release/Hermes-macOS-arm64-1.8.3-BuildKit.zip
python3 packaging/assemble-source.py --platform macOS-arm64 \
  --output build/release/Hermes-macOS-arm64-1.8.3-source.zip
```

需要在最新程式碼編譯成功後執行 staging，不能僅依賴舊 JAR。目標包檢查應用 JAR 一致性、JavaFX/Skiko 動態庫的 Mach-O ARM64 架構，以及官方 JDK 的 SHA-256 與架構。

固定 JDK：Temurin 21.0.12.1+1，`OpenJDK21U-jdk_aarch64_mac_hotspot_21.0.12.1_1.tar.gz`。

SHA-256：`3623232f33a9c3baadf304480b2535f9a3cba8a58d42ecbb438ba267315d9998`。

來源：[Adoptium 官方釋出](https://github.com/adoptium/temurin21-binaries/releases/tag/jdk-21.0.12.1%2B1)。校驗值取自同一官方釋出的 `.sha256.txt`。

origin JSON 包含 `version`、`name`、`source`、`sha256`、`bytes`，本次完整值見 `validation/macos-1.8.3-package.json`。

## Mac 上的最終生成流程

離線包的入口來自 `packaging/macos/Build-Mac-App.command`：核對所有檔案 → 解壓校驗過的 JDK → jlink 生成 runtime → jpackage 生成 app → 設定用途說明與最低系統版本 → 可選 Swift 識別元件 → 本機 ad-hoc 簽名與校驗 → hdiutil 生成和校驗 DMG。

每次構建使用獨立臨時目錄，結果使用帶時間的目錄。失敗不覆蓋已安裝的應用，錯誤碼不被日誌管道吞掉；臨時構建目錄會被清理，日誌保留。

指令碼需要 macOS 的系統工具，不在 Linux 模擬生成 DMG。沒有執行本機指令碼前，`app_and_dmg_generated` 為 false。

## 驗證依據

- [Compose native distributions](https://kotlinlang.org/docs/multiplatform/compose-native-distribution.html)：jpackage / DMG 平臺限制。
- [Java 21 Desktop API](https://docs.oracle.com/en/java/javase/21/docs/api/java.desktop/java/awt/Desktop.html)：Dock reopen、應用退出、設定與關於選單。
- [CGPreflightScreenCaptureAccess](https://developer.apple.com/documentation/coregraphics/cgpreflightscreencaptureaccess()) 和 [CGRequestScreenCaptureAccess](https://developer.apple.com/documentation/coregraphics/cgrequestscreencaptureaccess())：截圖前授權檢查。

離線包只含應用所需程式與工具鏈，不包含使用者閘道位址、Cookie、密碼、鑰匙串資料或開發機快取。
