# 4.0.0 雙版本構建與釋出

| 專案 | GitHub 版（公開原始碼預設） | OSS 加速版（單獨交付） |
| --- | --- | --- |
| STT/TTS 包 | k2-fsa/sherpa-onnx 官方 Releases | 維護者配置的 OSS 服務 |
| App 更新 | 本倉庫 Releases 的 android-latest.json | OSS releases/android-latest.json |
| 功能與模型校驗 | 相同 | 相同 |
| 渠道欄位 | github | oss |

預設讀取根目錄 `distribution.properties`。OSS 原始碼包在同一路徑放置 OSS 配置，開箱構建即對應渠道；不需要修改 Kotlin 檔案。也可用 `-PhermesDistributionFile=/absolute/path/distribution.properties` 指定私有配置。請勿把個人下載服務位址、金鑰或簽名檔案提交到公開倉庫。

配置包含 `channel`、`sttUrl`、`ttsUrl`、`updateFeedUrl`、`updateApkBaseUrl` 五項。模型位址使用同一份官方原包，大小及 SHA-256 固定於程式碼。更新包必須屬於當前渠道、同一包名且保持相同簽名。4.0.0 及以後的 JSON 必須包含 `distribution`，App 不自動跨渠道更新。

```bash
./gradlew testDebugUnitTest assembleRelease -PhermesPreview=true -PhermesSigningFile=/path/to/original.keystore
# 指定另一套配置
./gradlew testDebugUnitTest assembleRelease -PhermesPreview=true -PhermesSigningFile=/path/to/original.keystore -PhermesDistributionFile=/private/distribution.properties
python3 tools/verify_tts_jni_callback.py app/build/outputs/apk/release/app-release.apk
```

釋出 APK 沿用 `com.qingyu.hermescompanion.preview` 與原簽名；私鑰不在任一原始碼包中。無釋出金鑰時，Android Studio 的 Debug 構建可自行簽名，但不能覆蓋不同簽名的已安裝版本。GitHub 和 OSS 版使用相同包名，同一手機只能安裝一個；同簽名覆蓋切換保留模型和偏好，清除舊渠道未完成的軟體更新。

GitHub Release 上傳 `Hermes-Android-4.0.0-github.apk`、該版 `android-latest.json` 和原始碼包。OSS 則先上傳 `Hermes-Android-4.0.0-oss.apk`，確認可下載後再上傳對應 JSON。不得交換兩版 JSON，雜湊與長度均來自各自已簽名 APK。

OSS 加速渠道只改變下載位置，不包含收費、賬號校驗或下載鑑權。公開 URL 本身不能限制付費訪問；若需要收費下載控制，應另行設計伺服器端鑑權。第三方模型及執行庫許可證見 `docs/third-party-voice/` 與 App 內許可說明。
