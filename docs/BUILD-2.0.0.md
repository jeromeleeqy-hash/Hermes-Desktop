# 构建 Hermes Desktop 2.0.0

需要 JDK 21。执行 `./gradlew test -PheadlessTests=true`，以及 `python3 -m unittest discover -s tests`。

源码包保留 `gradlew`、macOS `.command` 入口和原生准备脚本的执行权限。如果解压工具移除了权限，可先执行 `chmod +x gradlew *.command packaging/prepare-native.sh packaging/macos/Build-Mac-App.command`。仅运行 Gradle 时也可以使用 `bash gradlew`。

Windows 原生环境执行 `gradlew.bat packageExe packageMsi packageWindowsPortable`。
macOS Apple Silicon 环境执行 `./gradlew packageDmg`；系统语音 helper 使用仓库的 `packaging/NativeSpeech.swift` 构建。

跨平台暂存可使用 `stageWindowsApplication -PdesktopTarget=windows-x64` 或 `stageMacApplication -PdesktopTarget=macos-arm64`。构建选择对应 sherpa-onnx 原生库，不能将 Linux 库放入 Windows/macOS 包。

Windows 验收包使用 `build-windows-runtime.py` 从 SHA-256 已校验的 Windows Temurin JDK 链接运行时；使用 `assemble-windows-portable.py` 组合 Windows 暂存应用与运行时，再由 `build-windows-installer.py` 从同一便携包生成安装器。禁止把 Linux 原生库重命名后装入目标包。

Mac 验收包使用 `assemble-macos-from-release.py` 从 SHA-256 已校验的 1.9.0 DMG 中提取启动器、运行时和系统语音 helper，替换全部应用 JAR、更新版本与配置、恢复可执行权限、重新 ad-hoc 签名并核对资源封印，再输出 .app.zip。原生 `packageDmg` 仍保留。

2026-10-08 打包修订 r2：`.app.zip` 顶层必须只有 `Hermes.app/`，禁止另放说明文件。否则 Archive Utility 可能创建同名 `.app` 外层目录，Finder 会把没有 `Contents/Info.plist` 的外层当作损坏的应用。说明已在签名前放进 `Contents/Resources/Release/`。统一用 `macos_archive.write_app_archive` 输出并校验归档；打包回归执行 `python3 -m unittest discover -s tests -p test_macos_archive.py -v`。完整记录见 `MACOS-PACKAGING-REPAIR-2.0.0.md`。

可运行 `render200Previews` 生成九个离屏场景。`localVoiceSmoke --args=/absolute/model-cache` 使用真实模型执行 TTS → WAV → STT；该目录需有 `sense-voice-int8.tar.bz2` 和 `kokoro-int8.tar.bz2`，摘要必须与 `LocalVoiceModels.kt` 一致。此任务不访问麦克风或 Hermes 服务器。

打包来源与摘要见验收记录和各包 manifest。
