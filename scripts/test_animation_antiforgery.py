#!/usr/bin/env python3
"""回归：上一轮按同一条曲线缩放的动作设计必须被防伪造门禁拒绝。"""

from __future__ import annotations

import re
import sys
from pathlib import Path
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parent))

from animation_analysis import AnimationSampler, SkeletonModel, load_json
from analyze_animation import report


def fake_from_spec(path: Path) -> tuple[dict, str]:
    """把旧 attack_main 表的数值投影到单轴同形曲线，作为回归夹具。

    设计稿格式由动作侧维护；当前格式是“骨/通道/关键帧”表，旧格式仍保留
    “部位/时刻”兼容解析。每根骨骼从表中取幅度作为系数，但共享同一条形状，
    正好复现上一轮批量脚本的伪造特征。
    """
    text = path.read_text(encoding="utf-8")
    scales: dict[str, float] = {}
    for line in text.splitlines():
        if not line.startswith("|") or "---" in line:
            continue
        cells = [cell.strip() for cell in line.strip().strip("|").split("|")]
        if len(cells) >= 3 and cells[0] in {"骨", "bone"}:
            continue
        if len(cells) == 3 and cells[1] in {"rotation", "position"}:
            numbers = [float(value.replace("−", "-"))
                       for value in re.findall(r"[-−+]?\d+(?:\.\d+)?", cells[2])]
            if numbers:
                scales[cells[0]] = max(scales.get(cells[0], 0.0), max(abs(value) for value in numbers))
            continue
        # 兼容上一轮“部位”表：把主链/双腿行映射为骨名。
        old_mapping = {
            "头": ["head"], "胸": ["chest"], "腰": ["waist"], "胯": ["pelvis"],
            "主臂-上臂": ["r_upper_arm"], "主臂-前臂": ["r_forearm"], "主臂-手": ["r_hand"],
            "副臂-上臂": ["l_upper_arm"], "副臂-前臂": ["l_forearm"], "副臂-手": ["l_hand"],
            "双腿-大腿": ["r_thigh", "l_thigh"], "双腿-小腿": ["r_shin", "l_shin"],
            "双腿-脚": ["r_foot", "l_foot"],
        }
        if len(cells) > 2 and cells[0] in old_mapping:
            values = [float(value.replace("−", "-"))
                      for value in re.findall(r"[-−+]?\d+(?:\.\d+)?", " ".join(cells[1:]))]
            if values:
                for target in old_mapping[cells[0]]:
                    scales[target] = max(scales.get(target, 0.0), max(abs(value) for value in values))
    # 用表中幅度做不同系数，曲线形状仍完全相同且只有 Y 轴。
    bones: dict[str, dict] = {}
    times = ["0", "0.125", "0.25", "0.375", "0.5"]
    shape = [0.0, 1.0, 2.0, 1.0, 0.0]
    for bone, scale in scales.items():
        if bone in {"root", "r_item", "l_item"}:
            continue
        coefficient = max(3.5, scale)
        bones[bone] = {"rotation": {time: [0.0, coefficient * value, 0.0]
                                     for time, value in zip(times, shape)}}
    if len(bones) < 10:
        raise AssertionError(f"旧设计表解析出的骨骼过少: {len(bones)}")
    key = "animation.kizuna_players_animation.player.attack_main"
    return {"format_version": "1.8.0", "animations": {key: {
        "animation_length": float(times[-1]), "bones": bones,
    }}}, key


def main() -> int:
    root = Path(__file__).resolve().parents[1]
    document, key = fake_from_spec(root / "docs/animation-specs/attack_main.md")
    model = SkeletonModel(load_json(root / "models/player_skeleton_classic.bbmodel"))
    result = report(AnimationSampler(model, document, key))
    correlation = result["anti_forgery_correlation"]
    peak_order = result["anti_forgery_peak_order"]
    multi_axis = result["anti_forgery_multi_axis"]
    assert not correlation["pass"], correlation
    assert not peak_order["pass"], peak_order
    assert not multi_axis["pass"], multi_axis
    assert not result["pass"], result
    # 回归已发现的误报：速度门禁失败时，总结果也必须失败。
    baseline = load_json(root / "src/main/resources/assets/kizuna_players_animation/animations/walk.animation.json")
    baseline_key = next(iter(baseline["animations"]))
    with patch("analyze_animation.velocity_gates", return_value={"pass": False, "applicable": True}):
        rejected = report(AnimationSampler(model, baseline, baseline_key))
    assert not rejected["pass"], "总结果遗漏了速度门禁"
    print("旧伪造 attack_main 夹具被相关性、主链错峰、多轴门禁全部拒绝")
    print(f"correlation ratio={correlation['ratio']:.3f}, pairs={correlation['correlated_pair_count']}")
    print(f"peak order={peak_order['peak_times_s']}")
    print(f"multi-axis bones={multi_axis['count']}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
