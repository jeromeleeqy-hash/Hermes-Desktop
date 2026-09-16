# Hermes Windows 1.7.0 · 配置、阅读与附件

## 模型供应商

设置新增独立的“模型供应商”页面。可配置常用供应商 API Key，添加、编辑自定义 API 地址和模型，测试连接、读取模型，以及设为新对话的默认供应商。模型目录加载失败时，仍可进入供应商设置修正配置。

使用当前工作空间的网关配置接口；新网关使用自定义端点接口，旧网关仅在接口确实未提供时回退到配置合并。更新自定义端点时，API Key 留空会保留已有密钥，其他供应商及既有高级参数也会保留。密钥不会回显。测试已有端点需要临时输入密钥，直接保存已有配置无需重填。

## 聊天头像与文件

- 用户消息旁显示设置中的“我的头像”；未设置时显示姓名缩写。与 F1 应用图标、Hermes 助理头像分别管理。
- 文件选择器回到桌面事件队列后打开，显示正在读取和失败反馈。读取附件期间切换会话，文件仍归属于原来的草稿。
- 可将本地文件拖到聊天或工作台输入框，也可在资源管理器复制文件后按 Ctrl+V / Shift+Insert 粘贴。普通文字粘贴维持原有行为。
- 附件先显示为可移除条目，发送时才上传。较小文本作为文本内容，图片沿用图片附件通道，PDF、Office 等二进制原文件写入当前工作空间。
- 同一文件重试使用稳定路径，并核对服务器上已有文件的原始字节；不覆盖不同内容。失败会保留或恢复文字与附件，便于重试。
- 每次最多 10 个文件；图片最多 8 MB，其他文件最多 12 MB。不直接上传目录。

## HTML 预览

内嵌网页改为异步初始化，避免在 Compose/AWT 窗口创建阶段等待 JavaFX。加载中保留界面响应，加入超时、失败重试及“查看源码”入口。

HTML、DOM 数量和相对资源读取设有限额；每个资源请求有超时，图片和 CSS 读取也受总量约束。预览停用网页脚本、事件处理器和动画，外部链接由系统浏览器打开。这是静态网页阅读，不执行网页应用；结构过大时会提示并允许查看源码。

## Markdown 阅读与双栏

- 参考 Typora 主题的文档阅读方式，调整正文宽度、16 sp 字号、1.85 倍行高和段落留白。
- 重新梳理标题层级、引用、列表、代码片段、表格和分隔线；中文软换行不额外插入空格，英文软换行自然衔接。
- 双栏默认同步滚动，按 Markdown 内容块与实际源码换行位置映射。可以从任一侧滚动，也可随时切换“独立滚动”。
- 保留原有目录、查找替换、撤销重做、图片、列表续写、草稿、修改对比和保存冲突检查。

## 安装与验证

推荐退出旧版后运行 `Hermes-Windows-1.7.0-Setup.exe` 升级。便携更新包需整体替换 `app` 目录和 `Hermes.exe`，详见包内更新说明。F1 图标、当前主题和原有用户数据目录保持兼容。

自动化覆盖协议、配置保留、附件原始字节与失败恢复、头像组件、双向滚动和既有功能；另提供真实 Compose 组件渲染截图。当前验证环境为 Linux，Windows 原生选择器、文件拖放/剪贴板、JavaFX 内嵌窗口和安装运行还需 Windows 真机确认。未连接或更改你的真实网关。详细证据见 `WINDOWS-VALIDATION.md`。

实现参考：[Hermes 供应商配置](https://github.com/NousResearch/hermes-agent/blob/main/website/docs/integrations/providers.md)、[Hermes 配置与端点接口](https://github.com/NousResearch/hermes-agent/blob/main/hermes_cli/web_routers/config_env.py)、[Hermes 文件接口](https://github.com/NousResearch/hermes-agent/blob/main/hermes_cli/web_routers/files.py)、[JavaFX Swing 嵌入](https://openjfx.io/javadoc/11/javafx.swing/javafx/embed/swing/JFXPanel.html)、[Typora 默认主题](https://github.com/typora/typora-default-themes)、[Folio 主题](https://theme.typora.io/theme/Folio/)。Markdown 使用自身 Compose 排版实现，未复制这些主题的素材。
