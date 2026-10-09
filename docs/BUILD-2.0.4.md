# Hermes Desktop 2.0.4 构建说明

应用使用 JDK 21、Gradle 8.13、Kotlin 2.3.20 与 Compose Desktop 1.10.1，从本版源码编译。

测试命令使用 `-PheadlessTests=true`，执行连接恢复、真实 HTTP / WebSocket 跨端同步、消息显示、未读、任务和工作空间恢复回归。

依赖声明未改变。以 SHA-256 验证 2.0.3 平台包及清单后复用平台依赖与原生运行时，替换新编译的应用 JAR。Windows 原生启动器和 NSIS 安装器重新构建；Mac 重新进行 ad-hoc 签名并验证代码页、资源和归档权限。

Mac 提供只有一个 Hermes.app 根目录的 ZIP；未用 Linux 生成伪装的 DMG。Mac 包没有 Apple Developer ID 公证。Windows / macOS 真机安装启动未执行。
