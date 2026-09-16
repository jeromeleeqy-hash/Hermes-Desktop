# 1.8.4 构建与验收

本交付为完整开发源码，尚未完成全量编译验证。需要对应操作系统、JDK 21 和可访问 Gradle/Maven 的网络。

## Windows x64

在项目根目录打开终端：

```bat
gradlew.bat test --console=plain
gradlew.bat packageWindowsPortable --console=plain
```

便携运行包位于 `build/windows/Hermes-Windows-x64.zip`，内含 Java 运行时。EXE/MSI 安装程序可在具备 Compose 打包依赖的 Windows 上执行 `gradlew.bat packageExe` 或 `packageMsi`。本次没有生成上述二进制文件。

## macOS

使用与芯片匹配的 JDK 21。项目目录执行：

```bash
chmod +x gradlew
bash Build-macOS.command
```

此脚本编译原生组件，运行测试并构建 DMG。查看 `build/compose/binaries/main/dmg`。也可使用原有 Package-macOS.command。

## GitHub Actions

新增 `.github/workflows/build-desktop.yml`：手动触发，对所选分支当前源码运行 Windows/macOS 测试与打包，产物包括 Windows 便携 ZIP、EXE/MSI 安装程序和 macOS DMG。只上传构建产物，不修改 main、不发布 Release。替换旧的从 1.8.3 Release 下载旧源码并覆盖主分支的构建流程。

该流程尚未在本次任务中触发或验证。当前未连接并写入你的 GitHub 仓库。

## 独立图片检查

```bash
javac -encoding UTF-8 -d build/image-check src/main/java/com/qingyu/hermescompanion/platform/ModernImageSupport.java src/test/java/com/qingyu/hermescompanion/platform/ModernImageSupportCheck.java
java -Djava.awt.headless=true -cp build/image-check com.qingyu.hermescompanion.platform.ModernImageSupportCheck
```

更多功能边界和实机验收项目见 RELEASE-1.8.4.md。

## 云端测试修订

首次原生编译已通过。自动测试发现模型切换模拟服务未返回实际模型，以及 macOS 测试快捷键误用 Ctrl，已修正测试夹具并新增服务器保留原模型的回归检查。
云端运行 `test -PheadlessTests=true`，使用离屏 Compose 渲染。需要真实交互桌面的 AWT Robot/系统选择器测试遵循原有 headless 跳过条件；本机执行普通 `test` 可运行这些检查。不能将云端通过替代实机验收。
