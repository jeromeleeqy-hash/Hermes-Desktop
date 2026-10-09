# 4.0.0 双版本构建与发布

| 项目 | GitHub 版（公开源码默认） | OSS 加速版（单独交付） |
| --- | --- | --- |
| STT/TTS 包 | k2-fsa/sherpa-onnx 官方 Releases | 维护者配置的 OSS 服务 |
| App 更新 | 本仓库 Releases 的 android-latest.json | OSS releases/android-latest.json |
| 功能与模型校验 | 相同 | 相同 |
| 渠道字段 | github | oss |

默认读取根目录 `distribution.properties`。OSS 源码包在同一路径放置 OSS 配置，开箱构建即对应渠道；不需要修改 Kotlin 文件。也可用 `-PhermesDistributionFile=/absolute/path/distribution.properties` 指定私有配置。请勿把个人下载服务地址、密钥或签名文件提交到公开仓库。

配置包含 `channel`、`sttUrl`、`ttsUrl`、`updateFeedUrl`、`updateApkBaseUrl` 五项。模型地址使用同一份官方原包，大小及 SHA-256 固定于代码。更新包必须属于当前渠道、同一包名且保持相同签名。4.0.0 及以后的 JSON 必须包含 `distribution`，App 不自动跨渠道更新。

```bash
./gradlew testDebugUnitTest assembleRelease -PhermesPreview=true -PhermesSigningFile=/path/to/original.keystore
# 指定另一套配置
./gradlew testDebugUnitTest assembleRelease -PhermesPreview=true -PhermesSigningFile=/path/to/original.keystore -PhermesDistributionFile=/private/distribution.properties
python3 tools/verify_tts_jni_callback.py app/build/outputs/apk/release/app-release.apk
```

发布 APK 沿用 `com.qingyu.hermescompanion.preview` 与原签名；私钥不在任一源码包中。无发布密钥时，Android Studio 的 Debug 构建可自行签名，但不能覆盖不同签名的已安装版本。GitHub 和 OSS 版使用相同包名，同一手机只能安装一个；同签名覆盖切换保留模型和偏好，清除旧渠道未完成的软件更新。

GitHub Release 上传 `Hermes-Android-4.0.0-github.apk`、该版 `android-latest.json` 和源码包。OSS 则先上传 `Hermes-Android-4.0.0-oss.apk`，确认可下载后再上传对应 JSON。不得交换两版 JSON，哈希与长度均来自各自已签名 APK。

OSS 加速渠道只改变下载位置，不包含收费、账号校验或下载鉴权。公开 URL 本身不能限制付费访问；若需要收费下载控制，应另行设计服务端鉴权。第三方模型及运行库许可证见 `docs/third-party-voice/` 与 App 内许可说明。
