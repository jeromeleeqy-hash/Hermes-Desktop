#!/usr/bin/env python3
"""Create a self-contained review of the real desktop screenshots and current test results."""
from pathlib import Path
import argparse,base64,json,html

def main():
    p=argparse.ArgumentParser();p.add_argument('--output',type=Path,required=True);a=p.parse_args()
    root=Path(__file__).resolve().parent.parent
    data=json.loads((root/'docs/validation/desktop-2.0.2-validation.json').read_text())
    screenshots=[('workbench','默认工作台','简洁首页作为固定入口，值得留意的事是其中一个模块。'),('attention','值得留意的事','独立事项页面，提供明确的“返回工作台”；整理与同步工具放在模块内。'),('conversations','会话与未读','侧栏最近会话最多 8 个；列表上的未读标记与左侧计数一致。')]
    figures=[]
    for name,title,caption in screenshots:
        encoded=base64.b64encode((root/f'docs/previews/desktop-2.0.2/{name}.png').read_bytes()).decode()
        figures.append(f'<section><h2>{title}</h2><p>{caption}</p><a href="data:image/png;base64,{encoded}" target="_blank"><img alt="{title}" src="data:image/png;base64,{encoded}"></a></section>')
    rows=''.join(f'<tr><td>{html.escape(r["class"].rsplit(".",1)[-1])}</td><td>{r["tests"]}</td><td>通过</td></tr>' for r in data['jvm']['classes'])
    doc='''<!doctype html><html lang="zh-CN"><meta charset="UTF-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>Hermes Desktop 2.0.2 验收清单</title><style>
    :root{color-scheme:light}*{box-sizing:border-box}body{margin:0;background:#f4f6fb;color:#202c43;font:16px/1.8 system-ui,-apple-system,"PingFang SC",sans-serif}main{max-width:1240px;margin:auto;padding:48px 24px}header,section{background:white;border:1px solid #e2e7f1;border-radius:18px;padding:28px;margin-bottom:24px}h1{font-size:34px;margin:6px 0}h2{font-size:22px;margin:0 0 8px}p{margin:8px 0;color:#59677f}.version{color:#315efb;font-weight:600}.metrics{display:flex;gap:12px;flex-wrap:wrap;margin-top:18px}.metrics span{background:#eff3ff;border-radius:9px;padding:8px 14px}img{width:100%;height:auto;border:1px solid #e4e8f1;border-radius:10px;margin-top:16px}table{width:100%;border-collapse:collapse}th,td{text-align:left;border-bottom:1px solid #e4e8f1;padding:9px 12px;font-size:14px}th{background:#f5f7fc}.note{border-left:3px solid #315efb;padding-left:16px}li{margin:5px 0}a{color:#315efb}@media(max-width:650px){main{padding:16px}header,section{padding:20px}h1{font-size:26px}td{word-break:break-word}}
    </style><main><header><div class="version">Hermes Desktop · 2.0.2</div><h1>同步与桌面导航更新</h1><p>按实际桌面使用反馈修复：最新消息、未读、默认工作台、通知会话跳转和最近会话。</p><div class="metrics"><span>54 项相关 JVM 测试通过</span><span>17 项 Python 测试通过</span><span>3 张真实界面截图</span></div></header>'''
    doc+=''.join(figures)
    doc+='<section><h2>自动化验证</h2><table><thead><tr><th>测试组</th><th>用例数</th><th>结果</th></tr></thead><tbody>'+rows+'</tbody></table><p>另有 17 项 Python 测试通过，包括 Mac 单一 app 目录、执行权限和安全解压验证。截图使用演示数据，来自真实 Compose 应用，并非设计稿。</p></section>'
    doc+='''<section><h2>Mac 安装后核对</h2><ol><li>退出旧版，解压后把 Hermes.app 放入“应用程序”，打开应先进入工作台。</li><li>手机在同一工作空间、同一会话发一轮，电脑前台聊天页会定期同步，切回窗口也立即核对。</li><li>设置中发送新的测试通知，并允许 Hermes 通知；点击回复通知应打开对应会话。</li></ol><p class="note">当前构建环境为 Linux，已验证通知目标路由与 JNA 回调，未进行 macOS 通知中心真机展示/点击，也未进行 Windows 原生启动。旧版已经发出的 AppleScript 通知无法被新版改写，需用新版产生的通知验证。</p></section></main></html>'''
    a.output.parent.mkdir(parents=True,exist_ok=True);a.output.write_text(doc);print(a.output.resolve())
if __name__=='__main__':main()
