## 2026-09-15 · UI 修订 3

本轮检查记录见 [更新说明](RELEASE-1.8.3-UI3.md)。以下历史检查保留用于追溯，不表示本轮重新执行了所有原生检查。

# Windows 1.8.3 验证范围

## 本次回归

- 237 项自动化测试：235 项通过、0 项失败、2 项跳过。跳过项为 Windows DPAPI 原生加密检查；本机为 Linux。逐套件结果和跳过项见 `validation/windows-1.8.3-tests.json`。
- 新增回归覆盖英文流式空格与换行、新版服务端 JSON-RPC clarify / approval、旧版结构化问题事件、启动消息前的请求、多问题恢复与锁定答案、原始请求 id 回答、按会话隔离取消、待确认事项进入任务中心、附件原始字节读取及本地状态恢复。
- Linux Xvfb 的实际 AWT/Compose 窗口中，Robot 鼠标点击确认浮窗和主输入框的附件名打开预览；Esc 关闭预览，附件和草稿保留；只有独立 × 按钮删除附件。
- 所有 Hermes 主窗口隐藏后，实际键盘 Esc 取消截图，回调仅执行一次；右键取消及拖动完成选区均通过。执行记录：`validation/windows-1.8.3-native-check.txt`。
- 实际渲染截图位于 `previews/windows-1.8.3/`：浮窗、图片预览、多问题选择面板和悬浮菜单。检查图片完整显示、缩放按钮、独立附件删除按钮、多选/单选及填写区域。阴影使用显式模糊参数与 24 dp 透明边距。

## 交付包

- 从校验 SHA-256 的官方 Windows x64 JDK 21 构建完整运行环境，保留 `jdk.unsupported.desktop`，无需另装 Java。
- 应用、启动器与安装器版本均为 1.8.3；Windows JavaFX / Skiko 原生依赖单独组装。
- `packaging/verify-release.py` 解开最终 NSIS 安装器，并逐文件比较其真实载荷与便携包；检查实际运行环境模块、PE 版本、新预览与确认协议类及内置字体。最终报告见源码包的 `validation/windows-1.8.3-package.json`。
- 源码包附 `SOURCE-SHA256.txt`，打包时逐文件重新读取 ZIP 校验。

## 实机限制

本轮没有 Windows 真机，未执行 Windows 安装升级、系统托盘外壳、多显示器混合 DPI 和 DPAPI 原生测试。上述实窗检查在 Linux Xvfb 完成，不等同于 Windows 系统桌面验收。

网关测试使用本地 WebSocket 协议夹具，未连接用户实际服务。恢复选项依赖服务器发出请求或提供 `open_requests` / 旧版 pending 字段。升级前请在托盘中退出旧版 Hermes；账号配置和草稿沿用原用户目录。旧版本已保存为粘连状态的思考文本无法可靠恢复空格。

## 历史验证（不计入 1.8.3 本次结果）

# Windows 1.8.2 验证范围

## 本次验证

- 23 项定向回归全部通过，0 项失败、0 项跳过。包含菜单定位、关闭信号去重、关闭后取消回调、重新打开隔离、悬浮球和按住/松开语音路径。结果：`validation/windows-1.8.2-tests.json`。
- 使用实际 Compose 导航组件在 100% / 200% 缩放下检查工作空间图标、最近会话和展开按钮的中心坐标，均位于 64 dp 导航栏的中轴线上。640 dp 高度下底部按钮可见；展开模式的四条会话由受约束的滚动区域承载。
- Linux Xvfb 实窗在 100% / 200% 下验证共享菜单：鼠标点击、状态更新、键盘方向键/Enter/Esc、同一 JVM 外部窗口点击、重复打开、主题/大字号、实际悬浮球右键、提问与隐藏悬浮球。通过，记录：`validation/windows-1.8.2-native-check.txt`。
- 当前界面截图：`previews/windows-1.8.2/`，含完整聊天布局、展开/收窄导航与两种缩放下的桌面菜单。
- 交付包校验检查 1.8.2 应用与启动器版本、Windows 原生依赖、实际运行环境模块、新全局鼠标监听类、最近会话组件、中文字体和全部安装载荷。最终报告：源码的 `validation/windows-1.8.2-package.json`。

## Windows 原生分支与限制

本轮构建和已执行的实窗检查运行于 Linux。新增的 `WH_MOUSE_LL` 全局鼠标分支未在 Windows 真机执行；Linux 窗口点击通过不等于跨程序点击已在 Windows 验证。安装升级、系统托盘壳、多显示器混合 DPI 和 Windows 鼠标钩子的实际效果仍未作实机验收。

源码附 `WindowsGlobalMenuCheck`：在 Windows 上启动独立 JVM 点击目标，关闭菜单窗口的焦点能力，验证跨程序点击仍能关闭菜单、原点击传给目标、重复开关后监听线程退出。该检查在 Linux 明确返回 SKIP；未混入上述 23 项通过数。

实现依据：微软 [LowLevelMouseProc](https://learn.microsoft.com/en-us/windows/win32/winmsg/lowlevelmouseproc) 和 [WindowFromPhysicalPoint](https://learn.microsoft.com/en-us/windows/win32/api/winuser/nf-winuser-windowfromphysicalpoint)。全局监听只在桌面菜单打开期间存在，在专用消息线程处理，点击通过钩子链继续传递；原生命中测试使用物理坐标，避免与 AWT 逻辑坐标混用。

## 历史验证（不计入 1.8.2 本次结果）

# Windows 1.8.1 验证范围

## 界面修正版（本轮）

- 菜单定位、悬浮球手势和按住说话相关的 19 项定向回归全部通过，0 项跳过。结果见 `validation/windows-1.8.1-ui-tests.json`。
- 在 Linux Xvfb 的真实 AWT/Compose 窗口中，以 100% 和 200% 显示缩放验证托盘菜单组件及实际悬浮球右键入口：点击、开关状态、键盘选择、Enter、Esc、点击外部关闭、单一活动菜单、提问与关闭悬浮球均通过。菜单不依赖主窗口可见。
- 检查深色主题与 130% 文字大小，中文使用应用内置字体；重新打开菜单复用同一窗口，跨显示器时按该显示器配置重建。
- 左上角布局及桌面菜单的实际截图保存在 `previews/windows-1.8.1-ui/`。原生窗口检查结果见 `validation/windows-1.8.1-ui-native-check.txt`。
- Windows 安装程序与最终便携载荷逐文件校验；同时检查新菜单类、左上角布局及内置字体确实存在于交付 JAR 中。打包结果见源码中的 `validation/windows-1.8.1-ui-package.json`。

本轮构建与上述实窗验证运行于 Linux。没有执行 Windows 真机安装；Windows 系统托盘本身、多显示器混合 DPI 及实际任务栏行为仍需在 Windows 上验收。原生检查使用与托盘相同的菜单窗口、真实悬浮球入口和本地演示控制器，没有连接用户网关。

## 1.8.1 首发：语音与手势

本轮覆盖完整的按住/松开语音路径：真实 DesktopController、加密语音记录、HTTP 转写与 WebSocket 消息提交。物理麦克风由确定的 WAV 数据源替代；没有使用或修改用户的真实网关。

- 按住时停顿不会提前结束；松开立即停止录音，并且只提交一次。
- 转写完成前不会发送；录音上限先结束时，也等待鼠标松开再发送。
- 主窗口停留在其他页面和会话时，仍发送到悬浮球选中的会话。
- 会话准备期间松开不会延迟开启麦克风；识别期间重复按住不重复录音或提交。
- 取消、切换会话后保留原会话的可用录音，不自动发给新会话。
- 空识别不发送；失败录音可重新识别到草稿；模型切换期间保留识别文字。
- 忙碌会话接收下一条语音队列；旧任务回调不会覆盖新录音状态。
- 无论是否开启自动朗读，按住模式都不会自动启动下一轮麦克风。

首发版本完整回归共 221 项：217 项通过，0 项失败，4 项因平台条件跳过。结果见 `validation/windows-1.8.1-tests.json`。

使用 Linux Xvfb、真实 AWT/Compose 窗口和 Robot 鼠标事件，验证长按后打开小窗、移出悬浮球后松开、拖动和单击。弹出小窗在按住期间不抢焦点。结果见 `validation/windows-1.8.1-native-hold-check.txt`。

## 启动与打包

1.8.1 使用与已修复的 1.8.0 相同的 Windows x64 运行环境，包括实际镜像中的 `jdk.unsupported.desktop`，没有复用原有不完整 JRE。打包程序检查实际模块镜像、Windows PE、JavaFX 平台、主程序版本、每个依赖和运行环境文件的 SHA-256。

NSIS 启动器与安装器的文件版本为 1.8.1.0，应用显示 1.8.1。启动日志在 JVM 运行前写入标题，合并 stdout 与 stderr。`packaging/verify-release.py` 对安装 EXE 可解压出的真实载荷逐项比对便携包，并验证主程序版本及启动文件。

使用首发版本的最终应用 JAR，在含中文、空格和 & 的目录下，经 Java 参数文件启动并确认主窗口显示（1440 × 900），退出码为 0。该检查使用 Linux 对应运行环境，记录见 `validation/windows-1.8.1-startup.json`。

## 环境限制

自动化环境是 Linux + JDK 21。Windows 系统麦克风硬件、DPAPI、任务栏、跨显示器实际鼠标捕获和最终安装升级未在本轮 Windows 真机运行。麦克风识别质量还取决于本机设备及所选识别服务。跳过项在回归结果中明确记录。

文档预览、图标、窗口布局与主题保持既有实现。1.8.0 的真实文档窗口验证与截图保留在 `validation/windows-1.8.0-native-doc-check.txt`、`previews/windows-1.8.0/`；它们是历史验证证据。本轮未将这些历史截图当作新版本实机截图。
