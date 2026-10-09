# Hermes Desktop

Hermes Agent 桌面客户端，支持 Windows x64 与 macOS Apple Silicon（M 系列）。

当前开发版本：**2.0.2（同步与桌面导航修复版）**。已发布版本见 [Releases](https://github.com/jeromeleeqy-hash/Hermes-Desktop/releases)。

## 核心功能

- 会话管理：工作空间与项目、多会话、搜索置顶、会话大纲、引用与运行中追加要求。
- 工作台：快速交办任务、继续最近会话、查看近期成果。
- 任务中心：审批与澄清、待处理请求、执行进度、完成记录、定时任务。
- 文件工作区：附件预览、图片与常见文档预览、Markdown 编辑与本机草稿。
- 桌面助手：悬浮球、小窗、截图、语音输入、托盘与 Mac 菜单栏入口。
- AI 配置：模型供应商、默认与备用模型、思考强度、专家会审、Skills、工具集及 MCP。
- 个性化：主题、字号、头像、回复风格、长期记忆和助理设定。

对话和工具需要连接已部署的 Hermes Agent；本地语音模型安装后可离线识别和朗读。

## 2.0.2 更新

修复跨设备消息延迟、未读计数范围和 macOS 通知跳转。默认进入简洁工作台，“值得留意的事”改为可进入和返回的模块；侧栏最近会话增加到 8 个。详见 [更新说明](docs/RELEASE-2.0.2.md)、[构建说明](docs/BUILD-2.0.2.md)、[验收记录](docs/ACCEPTANCE-2.0.2.md)。

## 2.0.1 更新

重排待确认操作、固定授权按钮、取消每条消息的“已发送”弹窗；将首页设置与工具移到事项区域，完善卡片详情排版。补齐本地 STT／TTS 下载并启用、模型包导入和离线试录／试听。详见 [更新说明](docs/RELEASE-2.0.1.md)、[构建说明](docs/BUILD-2.0.1.md)、[验收记录](docs/ACCEPTANCE-2.0.1.md)。

## 2.0.0 更新

对齐 Android 4.0.0 的共享首页协议：简洁／深入首页、6 种卡片布局、20 类交互、事项聊天与增量同步、操作回执和显式恢复、早晚整理与 Cron 核对。

修复网关就绪竞态、空会话首条消息、旧运行 ID、跨端授权过期、模型 lazy 回读和响应断流；保留草稿及发送确认，避免不确定写请求被自动重发。

语音识别／朗读独立设置，可下载 SenseVoice／Kokoro 在本机运行；支持完整句子提前朗读、预取及打断。新增离线连接指南。OSS 按计划后续接入。

详见 [更新说明](docs/RELEASE-2.0.0.md)、[构建说明](docs/BUILD-2.0.0.md) 和 [验收清单](docs/ACCEPTANCE-2.0.0.md)。

## 下载与构建

本次 Windows 验收包提供 Setup.exe 和便携 ZIP；Mac M 系列提供可解压安装的 .app.zip。原生构建仍可生成 MSI／DMG。安装包内置 Java，无需另装 JDK。

本仓库已同步安装包使用的完整源码。开发构建需要 JDK 21：

```sh
# Windows
gradlew.bat test -PheadlessTests=true
gradlew.bat packageExe packageMsi packageWindowsPortable
# macOS
bash packaging/prepare-native.sh
bash gradlew test packageDmg -PheadlessTests=true
```

`-PheadlessTests=true` 用于云端离屏测试；省略该参数可在真实交互桌面运行原生窗口检查。

## 验证

本次自动化结果与平台边界见 [验收记录](docs/ACCEPTANCE-2.0.2.md)。历史版本的 CI 结果不作为本次的验证结果。

Windows/macOS 真机安装、系统麦克风／音色、休眠唤醒及真实网关联调需要实机验收。验收包无 Windows 商业代码签名或 Apple Developer ID 公证；Mac 应用使用 ad-hoc 签名。
