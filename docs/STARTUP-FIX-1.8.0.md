# Hermes Windows 1.8.0 启动修复版

构建标识：`1.8.0+startup.1`，应用功能版本仍为 1.8.0。

## 原因

1.8.0 使用 JavaFX 命名模块启动。此前捆绑的 Temurin JRE 的实际模块镜像不包含 `jdk.unsupported.desktop`，而 `javafx.swing` 必须依赖它。JVM 在执行应用代码前就会退出。

旧运行环境的 `release` 文件虽然列出了这个模块，实际 `runtime/lib/modules` 中却没有。现已改为检查实际镜像，不能只信元数据。

使用与原 Windows 运行环境相同的模块集合以及发行包内 Windows JavaFX JAR，已复现：

```
Error occurred during initialization of boot layer
java.lang.module.FindException: Module jdk.unsupported.desktop not found, required by javafx.swing
```

退出码为 1，错误写在标准输出（stdout），标准错误（stderr）为空。旧启动器只打开 stderr 文件，因此用户看到空白日志。

## 修复

- 从校验 SHA-256 的官方 Windows x64 JDK 21.0.12.1 重新链接运行环境，保留旧模块集合并补齐桌面互操作模块。
- 启动前验证实际桌面模块和 JavaFX 模块依赖。
- 使用一个共同的启动脚本和参数文件。常规启动、诊断启动保持一致。
- 在 JVM 启动前写入时间、应用位置和版本，持续将 stdout 与 stderr 合并到一个日志中，保留退出码。
- 原生启动器监督整个进程，不再仅观察启动后的 1.5 秒。退出到托盘会正常保持运行。
- 不依赖 PowerShell 启动，不更改 Windows 脚本执行策略。
- 打包工具拒绝缺少必需模块的运行环境，并验证安装包的完整运行环境文件清单。

## 安装

推荐已有 1.8.0 的用户运行 `Hermes-Windows-1.8.0-Startup-Repair.exe`，自动定位安装目录；便携版可选择 Hermes.exe 所在文件夹。

修复包只更新运行环境与启动文件。网关、会话、草稿、头像、偏好设置和应用 JAR 保持原样，无需卸载或另装 Java。

也提供修复覆盖 ZIP，以及已包含修复的完整安装包和便携包。旧的未修复 1.8.0 安装包不应继续使用。

## 日志

完整日志在 `%LOCALAPPDATA%\HermesDesktop\logs\startup-*.log`。启动异常会自动打开该日志。

`Diagnose-Hermes.bat` 会走相同启动链路，在控制台显示日志及文件位置。不要再只收集旧版 `*-error.log`。

## 运行环境来源

[官方 Eclipse Temurin Windows JDK 21.0.12.1](https://github.com/adoptium/temurin21-binaries/releases/tag/jdk-21.0.12.1%2B1)

归档：`OpenJDK21U-jdk_x64_windows_hotspot_21.0.12.1_1.zip`

SHA-256：`f9d6e191ab098c0d416e7d588a24420a8621cd2f4720dab2459b8b7b2d2d8b4e`

模块依赖问题也有[运行环境维护方的问题记录](https://github.com/adoptium/adoptium-support/issues/12)。本次根因以发行包的实际模块检查和本地复现为依据。

## 验证范围

本次针对启动链路、模块依赖、网页组件初始化、分发文件一致性做回归。应用 JAR 与已交付 1.8.0 相同。

验证结果与限制见 `validation/windows-1.8.0-startup-fix.json`。当前构建环境为 Linux；未在真实 Windows 桌面执行安装向导、系统托盘或音频设备测试。
