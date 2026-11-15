#!/usr/bin/env python3
"""把量出来的挥击段写进索引的 swings。

先跑一遍测试 SwingWindowsContractTest（它每次都把量出来的结果写到 build/swing-windows.json，索引对不上时测试会失败），
再跑本脚本，然后重跑测试确认一致。改了带挥击的剪辑以后要走一遍。
"""
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
INDEX = ROOT / 'src/main/resources/assets/kizuna_players_animation/player_anim_index.json'
MEASURED = ROOT / 'build/swing-windows.json'


def main():
    index = json.loads(INDEX.read_text(encoding='utf-8'))
    measured = json.loads(MEASURED.read_text(encoding='utf-8'))
    entries = {entry['id']: entry for entry in index['animations']}
    for clip, swings in measured.items():
        entry = entries[clip]
        entry.pop('swings', None)
        if swings:
            entry['swings'] = swings
    INDEX.write_text(json.dumps(index, indent=2, ensure_ascii=False) + '\n', encoding='utf-8')
    print(f'写了 {sum(1 for swings in measured.values() if swings)} 个剪辑的 swings')


if __name__ == '__main__':
    main()
