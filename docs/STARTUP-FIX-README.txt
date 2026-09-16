Hermes Windows 1.8.0 启动修复包

推荐直接运行同版本 Startup-Repair.exe 自动修复。
如果使用这个 ZIP 手动修复：

1. 如果 Hermes 仍在运行，请在右下角托盘右键选择“退出 Hermes”。
2. 找到原 Hermes.exe 所在目录。安装版通常在 %LOCALAPPDATA%\Programs\Hermes。
3. 完整解压本 ZIP，将 runtime 文件夹和所有启动文件复制到该目录，确认替换同名文件。
4. 保留原 app 文件夹，不需要修改它。
5. 双击 Hermes.exe 启动。

修复不修改 %LOCALAPPDATA%\HermesDesktop 内的网关、头像、会话或草稿。
本包仅修复已有 Hermes 1.8.0，不能单独运行。

如果仍然打不开，运行 Diagnose-Hermes.bat，将生成的完整 startup-*.log 发给开发者。
日志路径：%LOCALAPPDATA%\HermesDesktop\logs。
