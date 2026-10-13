# Hermes Desktop 2.0.2 構建說明

- JDK 21.0.8+9、Gradle 8.13、Kotlin 2.3.20、Compose Desktop 1.10.1，沿用 2.0.1 依賴版本。
- 執行相關 JVM/真實 HTTP/Compose 場景測試，報告見 docs/validation/desktop-2.0.2-junit。
- 應用 JAR 重新編譯。確認依賴宣告未變後，使用 stage-unchanged-dependencies.py 按 2.0.1 釋出清單逐個校驗並複用平臺依賴；原生啟動器與 Java 執行時來自校驗過的 2.0.1 釋出包。若依賴發生變化，應執行 stageMacApplication / stageWindowsApplication 重新組裝。
- Mac 重新進行 ad-hoc 簽名並校驗內碼表、資源封裝、ZIP CRC、單一 app 根目錄和執行許可權。
- Windows 安裝器由校驗後的便攜包製作，複核應用與執行時清單。
- 使用 UserNotifications 的 Objective-C 公共介面，JNA 回撥和 Apple Blocks 64 位 ABI。未使用 AppleScript 通知或替換系統程序身份。

介面參考：

- https://developer.apple.com/documentation/usernotifications/unusernotificationcenter
- https://developer.apple.com/documentation/usernotifications/unusernotificationcenterdelegate
- https://clang.llvm.org/docs/Block-ABI-Apple.html

跨平臺構建不代表已在 macOS 或 Windows 真機啟動。Mac 原生通知互動需實際系統驗證。

本次結果：54 項相關 JVM/HTTP/Compose/回撥測試通過，17 項 Python 測試通過；3 張真實 Compose 介面截圖已核對。
