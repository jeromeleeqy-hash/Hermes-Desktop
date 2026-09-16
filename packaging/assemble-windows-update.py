#!/usr/bin/env python3
"""Build a complete application-directory update, including the verified Windows Java runtime."""
import argparse, hashlib, json, tempfile, zipfile
from windows_runtime import validate_runtime_image
from pathlib import Path
VERSION='1.8.4'
def sha(data):return hashlib.sha256(data).hexdigest()
p=argparse.ArgumentParser(description=__doc__)
p.add_argument('--app',required=True,type=Path);p.add_argument('--output',required=True,type=Path)
p.add_argument('--runtime',required=True,type=Path,help='Linked Windows runtime; old JREs lack the Swing interop module')
a=p.parse_args();root=Path(__file__).resolve().parent.parent
runtime_modules=validate_runtime_image(a.runtime/'lib/modules')
jars=sorted(a.app.glob('*.jar'));launcher=root/'build/native-launcher/Hermes.exe'
assert jars and launcher.read_bytes()[:2]==b'MZ'
assert not any('-linux' in x.name or '-mac' in x.name for x in jars)
with zipfile.ZipFile(a.app/'hermes-desktop.jar') as z:assert VERSION.encode() in z.read('com/qingyu/hermescompanion/BuildConfig.class')
manifest={'application':'Hermes','version':VERSION,'platform':'windows-x64','from_versions':['1.2.0','1.2.1','1.3.0','1.4.0','1.5.0','1.6.0','1.7.0'],'requires_existing_portable_installation':True,'replace':'entire app directory and Hermes.exe','runtime_dependencies':'Replace ALL app JARs and the included verified Java 21 Windows runtime','jars':[{'name':x.name,'bytes':x.stat().st_size,'sha256':sha(x.read_bytes())} for x in jars],'launcher_sha256':sha(launcher.read_bytes())}
instructions=r'''Hermes Windows 1.8.3 更新包（已有完整便携版时使用）

推荐安装版用户直接运行 1.8.3 Setup.exe 升级。
本包包含已补齐桌面模块的 Java 运行环境。

1. 在系统托盘右键选择“退出 Hermes”，确保程序已完全退出。
2. 找到旧版 Hermes.exe 和 Start-Hermes.bat 所在目录。
3. 把旧 app 文件夹重命名为 app-backup-旧版本；旧 Hermes.exe 也备份到另一个目录。
4. 将本包中的整个 app 文件夹复制到原目录，再复制新的 Hermes.exe 和 licenses。
   必须是新的完整 app，不能与旧 app 合并，也不能只覆盖 hermes-desktop.jar。
5. 用本包 runtime 覆盖原运行环境，同时覆盖全部启动文件（3 个 bat、hermes.vmoptions 和 Hermes.exe）。
   不要保留旧版缺少桌面模块的运行环境。
6. 启动 Hermes.exe，设置中确认版本为 1.8.3。

用户数据仍在 %LOCALAPPDATA%\HermesDesktop，包含网关、偏好、头像与草稿。
需要回退时退出 Hermes，移走新的 app，恢复旧 app 文件夹和旧 Hermes.exe。

本包包含完整应用依赖，请整体替换 app 目录。详细变更见 RELEASE-1.8.3.md。
'''
a.output.parent.mkdir(parents=True,exist_ok=True)
with tempfile.TemporaryDirectory(prefix='hermes-update-',dir=a.output.parent) as temp:
 staged=Path(temp)/'update.zip'
 with zipfile.ZipFile(staged,'w',zipfile.ZIP_DEFLATED,compresslevel=6) as z:
  for jar in jars:z.write(jar,'app/'+jar.name)
  z.write(launcher,'Hermes.exe')
  for script in ('Start-Hermes.bat','Diagnose-Hermes.bat','Run-Hermes.bat','hermes.vmoptions'):z.write(root/'packaging'/script,script)
  for file in sorted(a.runtime.rglob('*')):
   if file.is_file():z.write(file,'runtime/'+file.relative_to(a.runtime).as_posix())
  for file in sorted((root/'licenses').rglob('*')):
   if file.is_file():z.write(file,file.relative_to(root).as_posix())
  z.writestr('更新说明.txt',instructions.encode('utf-8-sig'))
  z.writestr('update-manifest.json',json.dumps(manifest,ensure_ascii=False,indent=2)+'\n')
  z.write(root/'docs/RELEASE-1.8.3.md','RELEASE-1.8.3.md')
 with zipfile.ZipFile(staged) as z:
  assert z.testzip() is None
  for row in manifest['jars']:assert sha(z.read('app/'+row['name']))==row['sha256']
 staged.replace(a.output)
print(json.dumps({'file':str(a.output.resolve()),'bytes':a.output.stat().st_size,'sha256':sha(a.output.read_bytes())},indent=2))
