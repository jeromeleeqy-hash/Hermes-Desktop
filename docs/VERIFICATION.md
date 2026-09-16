# 验证记录

构建环境：Linux、JDK 21。2026-09-10。

主程序编译和 `test` 成功；39 项测试通过，0 失败、0 错误、0 跳过。

| 测试套件 | 数量 |
| --- | --- |
| AudioFileRequestBodyTest | 3 |
| ChatInsightsTest | 1 |
| ConcurrentGatewayTest | 11 |
| HermesApiClientTest | 16 |
| RemotePaths361Test | 4 |
| DecisionCodecTest | 1 |
| DesktopStoreTest | 3 |

测试范围：复用的 HTTP / WebSocket 协议、并发会话路由、音频请求、产物解析、路径兼容；新增加密存储、Cookie 隔离和待处理决策编解码。测试服务器均为本机模拟。

四个页面使用 `renderPreviews` 从实际 Compose 代码渲染；渲染脚本给异步字体加载和 UI 回调预留稳定时间。系统标题栏、JavaFX 富文本编辑视图与麦克风不在无界面渲染范围内。

构建存在 Compose 旧资源加载接口和 ClickableText 的弃用提示，未阻止编译。Mac 脚本通过 shell 语法检查，DMG 没有在本环境生成。

详见 MAC-ACCEPTANCE.md 的真机验收范围。
