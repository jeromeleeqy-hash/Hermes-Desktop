# Hermes Desktop 2.0.5 构建说明

应用使用 JDK 21、Gradle 8.13、Kotlin 2.3.20 与 Compose Desktop 1.10.1，从本版源码编译。

使用 `-PheadlessTests=true` 执行文件路径、文件发现、索引持久化、工作空间恢复、服务器路径、Markdown 附件与首页读取相关回归；`renderArtifactLookup205` 使用同一 Compose 界面渲染候选路径弹窗，检查普通窗口及小窗口的底部按钮可见性。

平台依赖声明未改变。校验 2.0.4 Mac / Windows 包与依赖清单后复用原生运行时及依赖，替换新编译的应用 JAR；重新构建 Windows 启动器、安装器和 Mac ad-hoc 签名，并验证归档与资源完整性。

Mac 使用单个 Hermes.app 根目录的 ZIP。未提供未经实际制作的 DMG，未做 Apple Developer ID 公证，也未在 macOS / Windows 真机执行。
