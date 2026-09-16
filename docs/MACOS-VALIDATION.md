## 2026-09-15 · UI 修订 3

本轮检查记录见 [更新说明](RELEASE-1.8.3-UI3.md)。以下历史检查保留用于追溯，不表示本轮重新执行了所有原生检查。

# macOS ARM64 1.8.3 · 验证范围

本轮基于已交付的 Mac 1.8.3 源码完成界面修订 2。版本号仍为 1.8.3，新的源码包与 BuildKit 包含本轮全部修改。

## 已执行

- Linux JDK 21 编译通过。
- 本轮 244 项 JUnit 回归：240 项通过、0 项失败/错误、4 项跳过（2 项 Windows DPAPI、2 项需要可见图形环境的 AWT 交互测试）。结果见 `validation/macos-1.8.3-tests.json`。
- Mac 新增测试通过：Dock 重开回调、原生退出先取消系统默认退出再执行应用保存流程、释放监听后不执行残留事件、无重开支持时的降级、屏幕权限已允许/申请成功/拒绝三个分支，以及 Mac Control＋点击不会进入普通左键语音手势。
- 原生 Desktop 事件使用 Mockito，屏幕权限分支使用可注入回调；这些测试验证程序处理逻辑，不代表已经执行 Apple 系统 API。
- 上一轮已完成的 AWT/Compose 窗口共享交互检查在 Linux Xvfb 中运行，日志 `validation/macos-1.8.3-native-check.txt`。截图位于 `previews/macos-1.8.3-linux-review/`，不标作 Mac 系统截图。
- 打包脚本通过 Bash 语法检查，非 macOS 上会拒绝继续，避免产生伪装成 Mac 应用的文件。
- 官方 Mac ARM64 JDK SHA-256 校验、Java/jlink/jpackage Mach-O ARM64 检查、JavaFX/Skiko 动态库架构检查、应用 JAR 与 staging 一致性和 ZIP 逐文件校验。结果见 `validation/macos-1.8.3-package.json`。

## 本轮新增验证与原生接口依据

- 本轮测试总数、通过数见 `validation/macos-1.8.3-tests.json`。新增检查覆盖原生几何结构 ABI、不同窗口高度/按钮尺寸下的侧栏避让、单色模板图标的透明边距及 Retina 分辨率。
- 本轮 Compose 布局实渲染日志见 `validation/macos-1.8.3-appearance.txt`；展开/收起侧栏与图标样张在 `previews/macos-1.8.3-appearance/`。这些是 Linux 运行的共享 Compose 内容，不绘制假的 macOS 红绿灯，也不是 macOS 状态栏截图。
- 标题栏使用 JDK 21 `apple.awt.fullWindowContent`、`apple.awt.transparentTitleBar`、`apple.awt.windowTitleVisible` 根面板属性。位置调整在 AppKit 主线程执行，通过 NSApplication 的窗口列表识别唯一的主窗口并设置标识，避免跨线程保存 JAWT 裸视图指针。关闭后忽略排队回调；全屏时交由系统控制原生按钮位置。
- 菜单使用 AWT PopupMenu；其 macOS peer 直接调用 NSMenu `popUpMenuPositioningItem:atLocation:inView:`，无需辅助功能或全局鼠标监控权限。
- 图标使用 `apple.awt.enableTemplateImages=true` 和多分辨率透明 alpha 模板，保留 Dock/悬浮球的彩色头像。
- 原生窗口、结构返回 ABI、菜单跟踪和 AppKit 明暗着色仍需用户的 macOS 27 真机验收；本轮 Linux 测试不代替这些检查。

接口源码：
- [OpenJDK CPlatformWindow](https://github.com/openjdk/jdk21u/blob/master/src/java.desktop/macosx/classes/sun/lwawt/macosx/CPlatformWindow.java)
- [OpenJDK CTrayIcon](https://github.com/openjdk/jdk21u/blob/master/src/java.desktop/macosx/classes/sun/lwawt/macosx/CTrayIcon.java)
- [OpenJDK CPopupMenu](https://github.com/openjdk/jdk21u/blob/master/src/java.desktop/macosx/native/libawt_lwawt/awt/CPopupMenu.m)
- [Electron 原生标题栏容器的布局参考](https://github.com/electron/electron/blob/main/shell/browser/ui/cocoa/window_buttons_proxy.mm)

## 适配内容

- Mac 正常窗口下启用桌面悬浮球，不依赖 Windows 无边框窗口开关。
- 关闭主窗口后继续驻留；菜单栏和 Dock 可恢复。无可用恢复入口时降级为最小化。
- Mac 应用菜单中的退出、设置和关于接入已有控制器，退出保留保存检查。
- Mac 菜单栏和悬浮球均使用原生菜单，悬浮球菜单的外部点击与 Escape 取消由 NSMenu 处理。
- 支持 Control＋点击呼出悬浮菜单，Command＋滚轮缩放图片。
- 截图先检查系统屏幕录制授权；被拒绝时停止截图并给出指引。
- 新增 Apple Silicon 依赖 staging 和离线 Mac 打包文件，统一版本 1.8.3。

## 上游依赖说明

官方 `skiko-awt-runtime-macos-arm64-0.9.37.4.jar` 同时带有 ARM64 和 x64 两个资源。检查实际 `Library.findAndLoad()` 字节码后，确认它按 `hostId` 选择对应库；保留原始 JAR，不重打包修改依赖。校验器强制检查 ARM64 资源存在且架构正确，并将额外未使用的 x64 资源单独记录。JavaFX 原生文件仍逐个要求包含 ARM64 架构。

## 尚未执行

当前执行主机是 Linux，没有 macOS 27 真机。没有运行最终 Mac jlink / jpackage / codesign / hdiutil 流程，尚未生成实际 `.app` 或 `.dmg`。交付 BuildKit 会在用户 Mac 上执行这些步骤并校验结果。

Mac 系统菜单栏与 Dock、系统截图及麦克风权限、钥匙串、Swift 系统识别、Retina/多屏和实际网关连接仍需在 Mac 验收。没有 Apple Developer ID 签名和公证；本机打包默认使用 ad-hoc 签名。

本轮没有上传源码到 GitHub，也没有修改用户远端网关、旧安装或个人数据。
