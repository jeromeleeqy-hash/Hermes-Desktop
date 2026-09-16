# 构建 Hermes 1.5.0

应用使用 JDK 21、Gradle 8.13。先完整解压源码，运行 `gradlew test`；Windows 上也可双击 `Build-Windows.bat` 或 `Package-Windows.bat` 生成 jpackage 便携应用。原有 `Installer-Windows.bat` 生成 MSI，需要 WiX 3.x。

本次交付的 Setup.exe 采用另一条可复现的 NSIS 3.09 流程，Linux 和 Windows 均可使用：

1. `gradlew stageWindowsApplication -PdesktopTarget=windows-x64` 输出 `build/windows-staging/app`。
2. 使用 `packaging/build-windows-installer.py --launcher-only --output build/native-launcher/Hermes.exe` 编译启动器。可用 `--makensis` 指定 NSIS 编译器，Linux 解压版另传 `--nsis-dir`。
3. 用 `packaging/assemble-windows-from-portable.py --app build/windows-staging/app --base <已验证的旧便携包> --base-sha256 <SHA256> --output <新便携包>`。仅当全部第三方依赖与基包逐字节相同时复用其运行环境。或用 `assemble-windows-portable.py` 从独立 Windows JRE 压缩包构建。
4. 用 `build-windows-installer.py --portable <新便携包> --portable-sha256 <SHA256> --output Hermes-Windows-1.5.0-Setup.exe`。安装包复用经过 CRC 和 JAR SHA 校验的同一份便携包内容。
5. `assemble-windows-update.py` 生成小更新；`assemble-source.py` 生成含逐文件 SHA256 清单的源码包。

UI 截图用 `gradlew renderPreviews --args="build/previews chat-office chat-paper chat-glass files-split-wide chat-workspace-menu settings"` 生成。它使用真实 Compose 组件和演示数据，不需要访问个人网关。

Fluent 图标的上游包名、版本、文件对应和 SHA256 在 `docs/fluent-icons.json`。需要更新资源时，解压已校验的 `@fluentui/svg-icons` npm 包后运行 `python packaging/import-fluent-icons.py <package目录>`；不改写上游图形路径。
