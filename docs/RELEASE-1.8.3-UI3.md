# Hermes 1.8.3 · UI 修订 3

本次 Windows 与 macOS 使用同一份修复源码，版本号保持 1.8.3。

## 本次修改

- 任务中心：修复「待你确认」「正在执行」卡片底部说明文字只显示半行。空状态在卡片内居中，卡片随字号增加高度，窗口较矮时支持滚动。
- 主窗口：顶部、右侧、底部使用同一主题边距。轻盈办公和流光玻璃为 8 dp，纸间留白为 12 dp。Mac 红绿灯预留区域仍保持到顶部 46 dp。
- 回复风格：从多行输入框改为下拉选框。内置风格显示中文名称；读取服务器自定义风格，并保留原有未知选项。
- 风格保存：按新版 Hermes 的 `display.personality` 保存名称；读取时优先尊重新字段，兼容已有旧字段。不改动 `agent.system_prompt`、自定义风格定义或 SOUL.md。

风格与保存字段对照：[Hermes Agent 官方说明](https://hermes-agent.nousresearch.com/docs/user-guide/features/personality/)（2026-09-15 检查）。

## 更新方式

- Windows x64：退出 Hermes 后运行 Setup.exe，沿用原安装位置更新。
- macOS M 系列：解压 BuildKit.zip，运行 Build-Mac-App.command，生成 Hermes.app 和 DMG 后替换旧应用。
- 本次不会清理用户设置、会话草稿或工作文件。

## 检查范围

编译、自动化与 Compose 布局检查在 Linux 构建环境执行。Windows 安装器校验其内容与便携包一致；Mac BuildKit 校验 Apple Silicon 依赖和 JDK，实际 app/DMG 由 Mac 执行生成。这些检查不能代替 Windows/macOS 真机验收。

当前检查记录：`validation/desktop-1.8.3-ui3-tests.json`、`validation/desktop-1.8.3-ui3-layout.txt`。
