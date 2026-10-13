# Hermes Desktop 1.8.9

修復聊天輸入區的思考深度選單誤報“伺服器尚未提供該模型的檔位”。

Hermes Agent Dashboard 使用 `capabilities.reasoning` 與 `can_disable_reasoning` 描述模型能力，而桌面端此前只識別 `reasoning_efforts` 陣列，導致同一模型在 Dashboard 能看到思考檔位、聊天輸入區卻顯示空選單。1.8.9 同時相容兩種協議：伺服器明確支援思考時顯示標準檔位；明確不支援或強制思考時，分別隱藏全部檔位或隱藏“關閉”。

本版本保留 1.8.8 的輸入法原子更新修復、分隔線修復和懸浮球修復。
