# Hermes Windows 1.2.0 验证记录

构建环境：Linux x64，Temurin JDK 21.0.8+9，Gradle 8.13。应用版本 1.2.0。

## 自动化回归

`gradle test --offline`：129 项，127 项通过，0 项失败，2 项按平台条件跳过。

逐套件记录见 `validation/windows-1.2.0-tests.json`。新增覆盖会话上限、无总数分页、短页、重叠页、重复页、归档分页、错误响应、首页草稿与附件转移、模板保留原文及快速查找上下文。

## 实际组件渲染与交互

`gradle renderPreviews` 使用真实 Compose 组件、演示会话和文件生成预览。覆盖工作台三套主题、窄窗口、150% / 200% DPI、错误与加载状态、聊天与文档并排、专注模式、快速查找、设置、技能、语音、消息、任务、表格与文档差异。

实际操作检查包括：工作台输入→追加模板→开始任务；文件栏收放与拖动保留会话/文档；长消息跳到底部；设置未保存输入在导航后保留；高 DPI 下主题选择和滚动到保存按钮；会话时间筛选的鼠标状态。快速查找还验证了输入筛选、方向键、Enter、Esc 和原会话保留。

截图位于 `previews/windows-1.2.0/`。它们是组件渲染，不是 Windows 真机截图。

## Windows 包

Windows x64 依赖通过 `stageWindowsApplication -PdesktopTarget=windows-x64` 准备。组装脚本检查 JavaFX Windows 模块、Skiko DLL 和运行环境的 x64 PE 标识，拒绝其他平台及测试依赖，并检查归档 CRC。

官方运行环境：Eclipse Temurin JRE 21.0.12.1+1 Windows x64。发行商 ZIP SHA-256：

`d35f31e712f0fcf6ac5a093edc90204fbff22f720ba3950bd09d331d5e621636`

更新包只替换 1.1.0 的 `app/hermes-desktop.jar`；运行依赖版本保持一致。完整包内 `manifest.json` 记录每个 JAR 的 SHA-256。

## 平台边界

没有在 Windows 真机运行本包；未测试真实网关账号、麦克风、Windows 系统语音、系统通知、DPAPI 或 JavaFX 原生窗口。也没有新做 macOS 真机验收。Windows 启动失败时请运行 `Diagnose-Hermes.bat` 并查看日志。
