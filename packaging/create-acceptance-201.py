#!/usr/bin/env python3
"""Self-contained visual review of the real Compose renderings and recorded checks."""
import argparse
import base64
import html
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    report = json.loads((ROOT / 'docs/validation/desktop-2.0.1-validation.json').read_text())
    figures = [
        ('approval-small', '01 · 待确认操作', '小窗口与 130% 字号：命令独立滚动，允许、拒绝与稍后处理始终可见。'),
        ('detail', '02 · 事项详情', '完整正文按标题、段落和列表排版，兼容旧卡片的字面换行。'),
        ('simple', '03 · 简洁首页', '事项工具位于输入框下方，模式切换收进首页设置。'),
        ('deep', '04 · 深入首页', '工具与事项筛选放在一起，减少页顶的独立操作区。'),
        ('voice', '05 · 本地语音', 'STT 与 TTS 分别下载并启用，也可以导入已下载的模型包。'),
    ]
    content = []
    for name, title, description in figures:
        data = base64.b64encode((ROOT / 'docs/previews/desktop-2.0.1' / (name + '.png')).read_bytes()).decode()
        content.append('<section><h2>' + html.escape(title) + '</h2><p>' + html.escape(description) + '</p><img alt="' + html.escape(title) + '" src="data:image/png;base64,' + data + '"></section>')
    counts = report['junit_latest_by_suite']
    passed = counts['tests'] - counts['skipped'] - counts['failures'] - counts['errors']
    page = '''<!doctype html><html lang="zh-CN"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>Hermes 2.0.1 · 修复与验收</title>
<style>body{margin:0;background:#f3f5fa;color:#202938;font:16px/1.75 system-ui,"Microsoft YaHei",sans-serif}main{max-width:1100px;margin:auto;padding:48px 24px}header{margin-bottom:32px}small{letter-spacing:.12em;color:#4165cc}h1{font-size:38px;line-height:1.25;margin:12px 0}h2{font-size:22px;margin:0}p{color:#536177}.checks{display:flex;gap:14px;flex-wrap:wrap}.checks span{background:#e7edff;border-radius:10px;padding:10px 16px;color:#244db5}section{background:white;border:1px solid #e2e7ef;border-radius:16px;margin:24px 0;padding:24px}img{display:block;width:100%;height:auto;border:1px solid #e2e7ef;border-radius:10px}footer{font-size:14px;color:#617087}li{margin:8px 0}@media(max-width:600px){main{padding:24px 12px}section{padding:16px}h1{font-size:29px}}</style><main>
<header><small>HERMES DESKTOP · 2.0.1</small><h1>这次修复，逐项可看</h1><p>2026-10-08 · 页面图片由真实应用组件离屏渲染，内容为虚构演示数据。</p><div class="checks"><span>JVM ''' + str(passed) + ''' 项通过</span><span>Python 17 项通过</span><span>真实本地 STT / TTS 通过</span></div></header>
<section><h2>发送消息更安静</h2><p>普通消息收到服务器确认后，只更新对话内状态，不再弹出“已发送”。失败与待处理事项仍会给出提示。</p></section>
''' + ''.join(content) + '''<section><h2>本地语音怎么用</h2><ol><li>打开“设置 → 语音”。</li><li>分别点击 SenseVoice 和 Kokoro 的“下载并启用”；也可手动下载后导入模型包。</li><li>安装后使用“试录一句”和“试听声音”。本地测试不需要连接网关；语音聊天的文字回复仍需要网关。</li></ol><p>真实模型已跑通中文朗读→WAV→识别；无网关配置的应用调用链也已通过，测试中仅替代了物理麦克风与扬声器。</p></section>
<footer>测试边界：Linux 构建与离屏检查；7 项原生桌面测试跳过。首轮发现的 2 处缺失图标引用已修正，相关 19 项复测通过。新版本的 Mac / Windows 真机启动、权限和真实音频设备仍需在你的电脑确认。Mac 包保留单层 Hermes.app 结构，重新核对代码签名、资源封印、权限与 ZIP 完整性。</footer></main></html>'''
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(page, encoding='utf-8')
    print(args.output)

if __name__ == '__main__':
    main()
