# Hermes Desktop 2.0.2 构建说明

- JDK 21.0.8+9、Gradle 8.13、Kotlin 2.3.20、Compose Desktop 1.10.1，沿用 2.0.1 依赖版本。
- 运行相关 JVM/真实 HTTP/Compose 场景测试，报告见 docs/validation/desktop-2.0.2-junit。
- 应用 JAR 重新编译。确认依赖声明未变后，使用 stage-unchanged-dependencies.py 按 2.0.1 发布清单逐个校验并复用平台依赖；原生启动器与 Java 运行时来自校验过的 2.0.1 发布包。若依赖发生变化，应运行 stageMacApplication / stageWindowsApplication 重新组装。
- Mac 重新进行 ad-hoc 签名并校验代码页、资源封装、ZIP CRC、单一 app 根目录和执行权限。
- Windows 安装器由校验后的便携包制作，复核应用与运行时清单。
- 使用 UserNotifications 的 Objective-C 公共接口，JNA 回调和 Apple Blocks 64 位 ABI。未使用 AppleScript 通知或替换系统进程身份。

接口参考：

- https://developer.apple.com/documentation/usernotifications/unusernotificationcenter
- https://developer.apple.com/documentation/usernotifications/unusernotificationcenterdelegate
- https://clang.llvm.org/docs/Block-ABI-Apple.html

跨平台构建不代表已在 macOS 或 Windows 真机启动。Mac 原生通知交互需实际系统验证。

本次结果：54 项相关 JVM/HTTP/Compose/回调测试通过，17 项 Python 测试通过；3 张真实 Compose 界面截图已核对。
