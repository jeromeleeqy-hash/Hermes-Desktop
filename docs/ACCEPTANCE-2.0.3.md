# Hermes Desktop 2.0.3 验收记录

- 66 项 JVM / 真实 HTTP / 桌面状态回归测试通过，0 失败；另有 17 项 Python 测试通过。
- 已复现并修正相对路径截断；验证中文目录、空格、括号、Windows 盘符及 UNC、链接编码保留。
- 验证旧卡片从原消息恢复路径、历史索引替换、跨工作空间隔离、同名歧义不猜测、403 不尝试其他路径。
- 测试 XML 见 validation/desktop-2.0.3-junit，汇总见 validation/desktop-2.0.3-validation.json。
- 安装包组装脚本核验依赖与应用 JAR 哈希，Mac 重新 ad-hoc 签名并检查资源封装与归档权限，Windows 验证运行时清单与 NSIS 安装包载荷。

未在 Windows / macOS 真机执行安装启动；真实用户服务器文件是否存在，仍由服务器实际状态决定。
