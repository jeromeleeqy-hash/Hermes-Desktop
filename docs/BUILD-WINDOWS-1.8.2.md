# 从源码构建 Hermes Windows 1.8.2

## 环境

- Windows x64 JDK 21，`JAVA_HOME` 指向该 JDK；Gradle Wrapper 首次运行需要下载依赖。
- Python 3.10 或以上、NSIS 3.x。
- 官方 Windows x64 Temurin JDK ZIP（运行程序不需要用户另装 Java，此 ZIP 只用于打包）：`OpenJDK21U-jdk_x64_windows_hotspot_21.0.12.1_1.zip`。
- 本次构建使用该 ZIP 的 SHA-256：`f9d6e191ab098c0d416e7d588a24420a8621cd2f4720dab2459b8b7b2d2d8b4e`。
- 下载来源与许可证记录于 `packaging/build-windows-runtime.py`、运行环境清单及 `THIRD_PARTY_NOTICES.md`。

以下命令在源码根目录的命令提示符中执行。调整 NSIS 与 JDK ZIP 的实际路径。运行环境输出目录须为空或尚不存在。

## 测试、应用与启动器

```bat
gradlew.bat test stageWindowsApplication -PdesktopTarget=windows-x64 --console=plain
py packaging\build-windows-installer.py --launcher-only --makensis "C:\Program Files (x86)\NSIS\makensis.exe" --output build\native-launcher\Hermes.exe
```

## 构建完整运行环境

```bat
py packaging\build-windows-runtime.py --jdk "C:\Downloads\OpenJDK21U-jdk_x64_windows_hotspot_21.0.12.1_1.zip" --sha256 f9d6e191ab098c0d416e7d588a24420a8621cd2f4720dab2459b8b7b2d2d8b4e --host-jdk "%JAVA_HOME%" --output build\windows-runtime
```

脚本先核对官方 JDK 字节，再用 jlink 构建 Windows runtime，并通过 jimage 验证实际模块。不要改用缺少 `jdk.unsupported.desktop` 的旧 JRE ZIP，也不要只检查 `release` 文本中的模块名称。

## 完整便携包、安装包和源码

```bat
py packaging\assemble-windows-portable.py --app build\windows-staging\app --runtime build\windows-runtime --output build\release\Hermes-Windows-1.8.2-portable.zip
```

该命令输出 ZIP 的 `sha256`。将它填入下一条命令的 `SHA256_FROM_PREVIOUS_COMMAND` 位置：

```bat
py packaging\build-windows-installer.py --makensis "C:\Program Files (x86)\NSIS\makensis.exe" --portable build\release\Hermes-Windows-1.8.2-portable.zip --portable-sha256 SHA256_FROM_PREVIOUS_COMMAND --output build\release\Hermes-Windows-1.8.2-Setup.exe
py packaging\assemble-source.py --output build\release\Hermes-Windows-1.8.2-source.zip
```

安装包从完整便携包生成，并逐项校验应用依赖与运行环境清单。源码包包含文件校验表，不包含本机配置、密钥、缓存及构建输出。

安装 7-Zip 后可运行以下验证，比较安装程序中真实可解压的载荷与便携包：

```bat
py packaging\verify-release.py --portable build\release\Hermes-Windows-1.8.2-portable.zip --installer build\release\Hermes-Windows-1.8.2-Setup.exe --sevenzip "C:\Program Files\7-Zip\7z.exe" --report build\release\verification.json
```

本轮发布采用上面的 NSIS 流程。旧的 Compose `Package-Windows.bat` / MSI 脚本保留用于开发，不是本轮 Setup 的打包入口。历史 `build-startup-repair.py` 仅针对 1.8.0，不能替代 1.8.2 完整安装包。

## Linux 构建主机

同样使用 JDK 21、Python 和 NSIS。先用宿主依赖编译及测试，再执行 `stageWindowsApplication -PdesktopTarget=windows-x64 -x compileKotlin` 替换为 Windows 原生依赖；这一步必须在最新应用 JAR 编译完成后运行。`build-windows-runtime.py` 可用 Linux JDK 的 jlink 处理指定的 Windows JDK jmods。设置宿主 `JAVA_HOME`，以便打包器使用 jimage 验证实际镜像。

Linux 自动化与载荷校验不能替代 Windows 麦克风硬件、系统托盘及安装升级的实机验证。

## Windows 跨程序菜单检查

在已解锁的 Windows 测试桌面，执行 `gradlew.bat writeVerificationClasspath` 后，在 PowerShell 中运行：

```powershell
$menuCheckClasspath = Get-Content -Raw build/verification180/classpath.txt
& "$env:JAVA_HOME/bin/java.exe" -cp $menuCheckClasspath com.qingyu.hermescompanion.desktop.WindowsGlobalMenuCheck
```

这会打开独立进程的测试目标并模拟三次鼠标点击。菜单被设为不获取焦点，用于确认关闭来自全局鼠标监听，而非焦点丢失。请在空闲测试桌面运行；Linux 会明确跳过。
