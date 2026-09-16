#!/usr/bin/env python3
"""Import the reviewed Fluent System Icons 1.1.341 subset, retaining upstream license.

Usage: python packaging/import-fluent-icons.py /path/to/extracted/npm/package
Uses upstream 20/24 px optical sizes and Regular/Filled pairs, without redrawing paths.
"""
from pathlib import Path
import hashlib, json, sys

ROOT = Path(__file__).resolve().parent.parent
PACKAGE = Path(sys.argv[1])
MAPPING = {
    'expand':'open', 'capture':'screenshot', 'move':'arrow_move', 'add':'add', 'alert':'warning', 'arrow-down':'arrow_down', 'arrow-up':'arrow_up',
    'attachment':'attach', 'audio':'speaker_2', 'back':'arrow_left', 'bookmark':'bookmark',
    'calendar':'calendar_ltr', 'check':'checkmark', 'chevron-down':'chevron_down',
    'chevron-right':'chevron_right', 'clock':'clock', 'close':'dismiss', 'code':'code',
    'command':'slash_forward', 'connect':'plug_connected', 'copy':'copy',
    'dashboard':'grid', 'document':'document_text', 'files':'document_multiple',
    'focus':'full_screen_maximize', 'folder':'folder', 'help':'question_circle',
    'history':'chat', 'home':'home', 'image':'image', 'inbox':'tray_item_add',
    'memory':'brain', 'mic':'mic', 'minus':'subtract', 'model':'sparkle', 'more':'more_horizontal',
    'notification':'alert', 'outline':'text_bullet_list_ltr', 'palette':'color',
    'panel':'panel_right', 'pause':'pause', 'pin':'pin', 'play':'play', 'profile':'person',
    'queue':'text_bullet_list_add', 'refresh':'arrow_sync', 'search':'search',
    'send':'send', 'settings':'settings', 'sidebar':'panel_left', 'stop':'stop',
    'table':'table', 'tasks':'task_list_ltr', 'tool':'wrench', 'workspace':'briefcase',
    'window-minimize':'subtract', 'window-maximize':'square', 'window-restore':'square_multiple',
    'edit':'edit', 'delete':'delete', 'download':'arrow_download', 'save':'save',
    'cut':'cut','paste':'clipboard_paste','sort':'arrow_sort_down_lines',
    'archive':'archive', 'bold':'text_bold', 'italic':'text_italic', 'link':'link',
    'quote':'text_quote', 'list-bullet':'text_bullet_list_ltr', 'list-number':'text_number_list_ltr',
    'heading':'text_header_1', 'undo':'arrow_undo', 'redo':'arrow_redo',
    'split':'panel_left_expand', 'eye':'eye', 'eye-off':'eye_off', 'keyboard':'keyboard',
    'check-square':'checkbox_checked', 'checkbox':'checkbox_unchecked',
    'strike':'text_strikethrough', 'chevron-left':'chevron_left',
}
output = ROOT / 'src/main/resources/icons'
rows=[]
for name, upstream in MAPPING.items():
    sizes=[]
    for size in (20,24):
        src=PACKAGE / f'icons/{upstream}_{size}_regular.svg'
        if not src.exists(): continue
        target=output / (f'{name}.svg' if size==20 else f'{name}-24.svg')
        target.write_bytes(src.read_bytes())
        rows.append({'name':target.name,'source':src.name,'sha256':hashlib.sha256(src.read_bytes()).hexdigest()})
        sizes.append(size)
    assert 20 in sizes, f'Missing 20px asset: {name} / {upstream}'
for name in ('history','dashboard','tasks','settings'):
    src=PACKAGE / f'icons/{MAPPING[name]}_24_filled.svg'
    target=output / f'nav-{name}.svg';target.write_bytes(src.read_bytes())
    rows.append({'name':target.name,'source':src.name,'sha256':hashlib.sha256(src.read_bytes()).hexdigest()})
(ROOT/'docs/fluent-icons.json').write_text(json.dumps({'package':'@fluentui/svg-icons','version':'1.1.341',
    'source':'https://github.com/microsoft/fluentui-system-icons','icons':rows},ensure_ascii=False,indent=2)+'\n')
print(f'Imported {len(rows)} optical-size assets')
