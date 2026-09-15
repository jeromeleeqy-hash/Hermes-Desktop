# Hermes Desktop

Hermes Agent 的桌面客户端，支持 Windows x64 与 macOS Apple Silicon（M 系列）。

当前准备发布：**1.8.3 · UI 修订 3**。安装文件已构建并校验；Release 附件上传尚未完成。

## 核心功能

- 会话管理：工作空间与项目、多会话、搜索置顶、会话大纲、引用与运行中追加要求。
- 工作台：快速交办任务、继续最近会话、查看近期成果。
- 任务中心：审批与澄清选项、执行进度、完成记录、定时任务。
- 文件工作区：附件预览、图片与常见文档预览、Markdown 编辑与本机草稿。
- 桌面助手：悬浮球、小窗、截图、语音输入、托盘与 Mac 菜单栏入口。
- AI 配置：模型供应商、默认与备用模型、辅助模型、专家会审、Skills、工具集及 MCP。
- 个性化：主题、字号、头像、回复风格、长期记忆和助理设定。

使用需要连接已部署的 Hermes Agent；模型、工具和语音能力取决于服务器配置。

## UI 修订 3

1. 任务中心说明文字完整显示，适配放大字号和较矮窗口。
2. 顶部、右侧、底部留白一致。
3. 回复风格改为下拉选框，兼容服务器自定义风格，并修正保存字段。

243 项测试通过，4 项本机平台检查跳过；100% / 130% 字号与三种外观布局检查通过。

## 发布文件

准备上传到 [Releases](https://github.com/jeromeleeqy-hash/Hermes-Desktop/releases)：Windows 安装包、便携包、两端完整源码包、Mac ARM64 BuildKit 和 SHA-256 校验文件。

BuildKit 在 M 系列 Mac 上运行 `Build-Mac-App.command` 可离线生成 app 和 DMG。本仓库的 **Publish desktop source and build macOS ARM64 DMG** 工作流可在附件上传后校验并同步完整源码，再构建原生 DMG。完整源码尚未同步到本仓库目录。

尚未完成 Windows 与 macOS 27 真机验收。Mac 构建使用本地签名，不含 Apple Developer ID 公证。

Android 版：[Hermes-Android](https://github.com/jeromeleeqy-hash/Hermes-Android)
