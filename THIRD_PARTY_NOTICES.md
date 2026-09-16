# 来源与第三方资源

- Hermes 的协议、数据模型、部分解析逻辑、品牌图标、头像与人物素材来自用户提供的 `hermes-assistant-2.zip`，其 README 标记 Android 版本为 3.6.5。此适配不为原项目或品牌资产重新指定许可，也不包含原 Android 签名私钥。
- 导出的原项目图标包含 Phosphor Icons 资源，保留原项目中的 MIT 许可全文：`licenses/Phosphor-MIT.txt`。
- 捆绑字体 `NotoSansCJKsc-Regular.otf` 来自 [notofonts/noto-cjk](https://github.com/notofonts/noto-cjk/tree/main/Sans)，字体许可全文：`licenses/NotoSansCJK-OFL.txt`。未修改字体。
- 应用依赖由 Gradle 下载，版本固定于 `build.gradle.kts`：Kotlin、Compose Multiplatform / Material 3、kotlinx.coroutines、OkHttp、org.json、JNA / JNA Platform、CommonMark Java、PDFBox、ICU4J 和 OpenJFX。测试依赖还包含 JUnit、MockWebServer 和 Mockito。各组件保留自己的上游许可与分发声明。
- `gradlew`、`gradlew.bat` 和 Gradle Wrapper 来自原工程，使用 Gradle 8.13。

构建正式二进制发行版时应随发行包保留依赖中附带的许可和声明；源码包不包含依赖缓存。配套便携包包含运行所需的依赖 JAR，保留其内嵌声明，并在 licenses/dependencies 下另存可识别的许可／声明文本。

- Windows 便携包包含未修改的 Eclipse Temurin JRE 21.0.12.1+1 x64：[官方发布](https://github.com/adoptium/temurin21-binaries/releases/tag/jdk-21.0.12.1%2B1)。运行环境的完整 `legal` 目录与 `NOTICE` 一并保留。OpenJDK 源码和构建来源由 `runtime/release` 中的上游链接记录；许可证为各模块附带的 GPLv2 with Classpath Exception 等声明。
- OpenJFX 使用官方 21.0.8 Windows JAR：[OpenJFX 源码与许可](https://github.com/openjdk/jfx)。Skiko 使用 `skiko-awt-runtime-windows-x64` 0.9.37.4。未把 Linux 原生库作为 Windows 原生库重新命名。
- 打包文件的版本、大小与校验摘要记录在便携包的 `manifest.json`。


## Fluent System Icons

UI icons are unmodified optical-size SVG assets from Microsoft Fluent System Icons, npm package `@fluentui/svg-icons` 1.1.341. MIT license: `licenses/Fluent-System-Icons-LICENSE.txt`. The source mapping and per-asset SHA-256 are in `docs/fluent-icons.json`.

Source: https://github.com/microsoft/fluentui-system-icons

## NSIS installer

The Windows setup and small launcher are compiled with NSIS 3.09. NSIS uses the zlib/libpng license for the core; see https://nsis.sourceforge.io/License. No NSIS build tools are bundled in the application.


## Document previews in 1.6.0

Apache POI 5.5.1 (poi-ooxml and poi-scratchpad), Apache PDFBox 3.0.8, jsoup 1.23.2, CommonMark Java 0.24.0 GFM strikethrough. Upstream LICENSE/NOTICE files for staged JAR dependencies are retained under licenses/dependencies; their versions and hashes are recorded in the portable manifest.
