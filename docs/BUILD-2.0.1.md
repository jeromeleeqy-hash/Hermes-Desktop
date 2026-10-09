# 构建 Hermes Desktop 2.0.1

使用 JDK 21、Gradle 8.13、Kotlin 2.3.20、Compose Desktop 1.10.1。源代码包含 Gradle wrapper。

```sh
bash gradlew test -PheadlessTests=true
bash gradlew render201Previews
python3 -m unittest discover -s tests
```

`render201Previews` 使用实际应用组件生成简洁首页、深入首页、长命令、小窗口 130% 字号、卡片详情和语音设置图。`DecisionLayout201Test` 检查 120 行命令下按钮的实际坐标与可点击性。

```sh
bash gradlew localVoiceSmoke --args=/absolute/model-cache
```

模型目录需包含 `sense-voice-int8.tar.bz2` 和 `kokoro-int8.tar.bz2`。任务使用与应用一致的导入、大小与摘要校验，再实际执行 Kokoro → WAV → SenseVoice；不访问麦克风或 Hermes 服务器。

随后可执行 `bash gradlew offlineVoiceCheck --args=/absolute/model-cache` 验证没有网关配置的真实应用调用链；只有麦克风和扬声器由固定 WAV 与内存输出替代，识别／合成仍调用真实模型。

原生 Windows 使用 `gradlew.bat packageExe packageMsi packageWindowsPortable`。原生 Apple Silicon 使用 `bash packaging/prepare-native.sh` 后执行 `bash gradlew packageDmg`。云端跨平台暂存分别使用 `stageWindowsApplication -PdesktopTarget=windows-x64` 和 `stageMacApplication -PdesktopTarget=macos-arm64`；不得混入其他平台原生库。

此次 Mac 包复用用户已确认可打开的 2.0.0 `.app.zip` 中的 ARM64 启动器、JDK 运行时和系统语音 helper，替换全部应用 JAR、更新版本配置，重新进行 ad-hoc 签名。`assemble-macos-from-release.py` 核对基包 SHA-256、每个 Mach-O 的代码页、应用资源封印、JAR 清单、Unix 权限与单层 `.app` 归档。

Windows 复用已验证完整的 Windows Temurin 21 运行时，重新编译 NSIS 启动器；便携包组合新 Windows 暂存依赖，安装器由同一便携包生成。构建脚本支持自定义 NSIS 路径，无需系统安装。

构建输出可放在临时磁盘，源码目录的 `build` 指向该输出后运行打包脚本；源码归档排除符号链接、构建产物和缓存。运行时不携带构建环境代理配置。

版本更新脚本、源文件、预览与记录均随源码提供。原生麦克风、系统权限、实际扬声器和双击安装必须在对应系统验收。
