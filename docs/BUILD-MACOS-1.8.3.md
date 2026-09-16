# Hermes macOS ARM64 1.8.3 构建

## 在 M 系列 Mac 从源码构建

需要 Apple Silicon JDK 21。完整解压后运行 `Package-macOS.command`，先测试再打包则运行 `Build-macOS.command`。Swift 系统识别辅助程序是可选项，缺少 Apple Command Line Tools 时仍可使用服务器识别。

```bash
bash Package-macOS.command
```

输出：`build/compose/binaries/main/dmg/`。正式公开发行时需要单独配置 Developer ID 签名与公证。

## Linux 上准备离线打包文件

JVM 代码先用本机依赖编译与测试，再组装真实的 Mac ARM64 原生依赖，不把 Linux 动态库混入目标包：

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

需要在最新代码编译成功后执行 staging，不能仅依赖旧 JAR。目标包检查应用 JAR 一致性、JavaFX/Skiko 动态库的 Mach-O ARM64 架构，以及官方 JDK 的 SHA-256 与架构。

固定 JDK：Temurin 21.0.12.1+1，`OpenJDK21U-jdk_aarch64_mac_hotspot_21.0.12.1_1.tar.gz`。

SHA-256：`3623232f33a9c3baadf304480b2535f9a3cba8a58d42ecbb438ba267315d9998`。

来源：[Adoptium 官方发布](https://github.com/adoptium/temurin21-binaries/releases/tag/jdk-21.0.12.1%2B1)。校验值取自同一官方发布的 `.sha256.txt`。

origin JSON 包含 `version`、`name`、`source`、`sha256`、`bytes`，本次完整值见 `validation/macos-1.8.3-package.json`。

## Mac 上的最终生成流程

离线包的入口来自 `packaging/macos/Build-Mac-App.command`：核对所有文件 → 解压校验过的 JDK → jlink 生成 runtime → jpackage 生成 app → 设置用途说明与最低系统版本 → 可选 Swift 识别组件 → 本机 ad-hoc 签名与校验 → hdiutil 生成和校验 DMG。

每次构建使用独立临时目录，结果使用带时间的目录。失败不覆盖已安装的应用，错误码不被日志管道吞掉；临时构建目录会被清理，日志保留。

脚本需要 macOS 的系统工具，不在 Linux 模拟生成 DMG。没有执行本机脚本前，`app_and_dmg_generated` 为 false。

## 验证依据

- [Compose native distributions](https://kotlinlang.org/docs/multiplatform/compose-native-distribution.html)：jpackage / DMG 平台限制。
- [Java 21 Desktop API](https://docs.oracle.com/en/java/javase/21/docs/api/java.desktop/java/awt/Desktop.html)：Dock reopen、应用退出、设置与关于菜单。
- [CGPreflightScreenCaptureAccess](https://developer.apple.com/documentation/coregraphics/cgpreflightscreencaptureaccess()) 和 [CGRequestScreenCaptureAccess](https://developer.apple.com/documentation/coregraphics/cgrequestscreencaptureaccess())：截图前授权检查。

离线包只含应用所需程序与工具链，不包含用户网关地址、Cookie、密码、钥匙串数据或开发机缓存。
