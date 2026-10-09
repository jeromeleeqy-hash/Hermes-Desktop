#!/usr/bin/env python3
"""Create an offline acceptance checklist with actual application screenshots."""
import argparse
import base64
import html
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent


def inline(text):
    return re.sub(r"`([^`]+)`", r"<code>\1</code>", html.escape(text))


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument("--output", type=Path, required=True)
    a = p.parse_args()
    pieces = []
    table = False
    index = 0
    for line in (ROOT / "docs/ACCEPTANCE-2.0.0.md").read_text().splitlines():
        if line.startswith("|-"):
            continue
        if line.startswith("|"):
            values = [inline(x.strip()) for x in line.strip("|").split("|")]
            if not table:
                pieces.append('<div class="table"><table><thead><tr>' + ''.join('<th>' + x + '</th>' for x in values) + '<th>实机结果</th></tr></thead><tbody>')
                table = True
            else:
                index += 1
                pieces.append('<tr>' + ''.join('<td>' + x + '</td>' for x in values) + '<td><select aria-label="' + values[0] + '验收结果" data-result="' + str(index) + '"><option value="pending">待验收</option><option value="passed">通过</option><option value="issue">发现问题</option><option value="deferred">后置</option></select></td></tr>')
            continue
        if table:
            pieces.append('</tbody></table></div>')
            table = False
        if line.startswith("# "):
            pieces.append('<div class="eyebrow">DESKTOP / ACCEPTANCE</div><h1>' + inline(line[2:]) + '</h1>')
            pieces.append('<div class="stats"><div><strong>339</strong><span>JVM 回归通过 · 7 项跳过</span></div><div><strong>10</strong><span>首页 writer 测试通过</span></div><div><strong>9</strong><span>离屏界面场景</span></div></div>')
        elif line.startswith("## "):
            pieces.append('<h2>' + inline(line[3:]) + '</h2>')
        elif line.startswith("- "):
            pieces.append('<p class="bullet">' + inline(line[2:]) + '</p>')
        elif line:
            pieces.append('<p>' + inline(line) + '</p>')
    if table:
        pieces.append('</tbody></table></div>')
    pieces.append('<h2>界面预览</h2><p>使用虚构示例数据生成，展示实际 Compose 桌面界面。</p>')
    for name, title in (("simple", "简洁首页"), ("deep", "深入首页"), ("interaction", "卡片交互"), ("voice", "独立语音设置")):
        payload = base64.b64encode((ROOT / "docs/previews/desktop-2.0.0" / (name + ".png")).read_bytes()).decode()
        pieces.append('<details><summary>' + title + '</summary><img alt="' + title + '" src="data:image/png;base64,' + payload + '"></details>')
    pieces.append('<h2>你的验收记录</h2><textarea id="notes" rows="5" placeholder="电脑系统、测试的 Profile、复现步骤、实际现象……"></textarea><p><button onclick="downloadResults()">导出验收结果</button> <span id="saved">本页仅在当前浏览器保存，无网络请求。</span></p>')
    css = """body{margin:0;background:#f4f6fa;color:#202838;font:15px/1.8 system-ui,-apple-system,'Segoe UI',sans-serif}main{max-width:1180px;margin:40px auto;padding:40px;background:white;border-radius:18px;box-shadow:0 8px 36px #2438600a}.eyebrow{font-size:12px;letter-spacing:3px;color:#566cc9}h1{font-size:34px;line-height:1.35;margin:12px 0 28px}h2{font-size:23px;border-top:1px solid #e8edf5;padding-top:30px;margin:36px 0 14px}.stats{display:flex;gap:18px;margin:24px 0}.stats div{flex:1;background:#f2f5ff;border-radius:12px;padding:18px 22px}.stats strong{display:block;font-size:32px;color:#315cd2}.stats span{color:#647087;font-size:13px}.bullet{padding-left:16px;border-left:3px solid #dce6ff}.table{overflow:auto}table{border-collapse:collapse;width:100%;font-size:14px}td,th{border-bottom:1px solid #e8edf4;padding:14px 12px;text-align:left;vertical-align:top}th{background:#f3f6fc}td:first-child{font-weight:600;min-width:90px}select{border:1px solid #d5ddeb;border-radius:7px;padding:7px;background:white;color:#374865;min-width:92px}code{font:13px ui-monospace,monospace;background:#f2f4f8;padding:2px 5px;border-radius:4px;overflow-wrap:anywhere}details{border:1px solid #dde5f0;padding:14px 18px;border-radius:12px;margin:14px 0}summary{cursor:pointer;font-weight:600}img{width:100%;margin-top:14px}textarea{box-sizing:border-box;width:100%;font:inherit;border:1px solid #d4dceb;border-radius:10px;padding:14px}button{border:0;border-radius:8px;background:#315ff0;color:white;padding:12px 20px;font:inherit;cursor:pointer}#saved{color:#77839a;font-size:12px}@media(max-width:700px){main{padding:20px;margin:12px}h1{font-size:27px}.stats{gap:8px}.stats div{padding:12px}.stats strong{font-size:26px}.stats span{font-size:11px}}@media print{main{margin:0;box-shadow:none}.table{overflow:visible}button{display:none}details img{max-height:650px;object-fit:contain}}"""
    script = """const key='hermes-desktop-200-acceptance';let previous={};try{previous=JSON.parse(localStorage.getItem(key)||'{}')}catch{};const fields=[...document.querySelectorAll('[data-result]')];fields.forEach(x=>{x.value=previous[x.dataset.result]||'pending';x.addEventListener('change',save)});const notes=document.querySelector('#notes');notes.value=previous.notes||'';notes.addEventListener('input',save);function results(){return{version:'2.0.0',...Object.fromEntries(fields.map(x=>[x.dataset.result,x.value])),notes:notes.value,recorded_at:new Date().toISOString()}}function save(){try{localStorage.setItem(key,JSON.stringify(results()))}catch{document.querySelector('#saved').textContent='浏览器未允许保存，请导出验收结果。'}}function downloadResults(){const value={...results(),items:fields.map(x=>({name:x.closest('tr').querySelector('td').textContent,status:x.value}))};const url=URL.createObjectURL(new Blob([JSON.stringify(value,null,2)],{type:'application/json'}));const a=document.createElement('a');a.href=url;a.download='Hermes-2.0.0-验收结果.json';a.click();setTimeout(()=>URL.revokeObjectURL(url),1000)}"""
    document = '<!doctype html><html lang="zh-CN"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>Hermes Desktop 2.0.0 验收</title><style>' + css + '</style></head><body><main>' + ''.join(pieces) + '</main><script>' + script + '</script></body></html>'
    a.output.parent.mkdir(parents=True, exist_ok=True)
    a.output.write_text(document)
    print(a.output.resolve())


if __name__ == "__main__":
    main()
