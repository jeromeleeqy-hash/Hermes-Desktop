# Hermes Desktop 1.8.9

修复聊天输入区的思考深度菜单误报“服务器尚未提供该模型的档位”。

Hermes Agent Dashboard 使用 `capabilities.reasoning` 与 `can_disable_reasoning` 描述模型能力，而桌面端此前只识别 `reasoning_efforts` 数组，导致同一模型在 Dashboard 能看到思考档位、聊天输入区却显示空菜单。1.8.9 同时兼容两种协议：服务器明确支持思考时显示标准档位；明确不支持或强制思考时，分别隐藏全部档位或隐藏“关闭”。

本版本保留 1.8.8 的输入法原子更新修复、分隔线修复和悬浮球修复。
