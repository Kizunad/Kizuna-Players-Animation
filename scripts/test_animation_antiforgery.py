#!/usr/bin/env python3
"""回归：上一轮按同一条曲线缩放的动作设计必须被防伪造门禁拒绝；爆发窗口只放宽窗口内的角速度上限；
相触窗口拒绝分开与穿入；手指覆盖层拒绝张开的手指；索引 item_rotation 转动挂点并进角速度门禁；
坐地「触地即支撑」拒绝沉地和臀滑，日常站姿循环按站立口径查滑脚，村民几何量得出玩家版抱臂穿进袍子。"""

from __future__ import annotations

import json
import math
import re
import sys
from pathlib import Path
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parent))

import bbmodel_store
from animation_analysis import AnimationSampler, SkeletonModel, attach_item_rotation, load_json, sample_times
from analyze_animation import hand_contact_gates, report, velocity_gates


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


def burst_window_passes(model: SkeletonModel, windows: list[list[float]] | None,
                        impact_window: tuple[float, float] | None = None, peak_deg_s: float = 1200.0) -> bool:
    """小腿单轴蹬伸：60 Hz 逐帧角速度 0 → 峰值 → 0（缺省 1,200°/s：超过 720°/s 的严格上限、低于 1,500°/s 的爆发上限）。"""
    shape = [150.0, 500.0, 950.0, 1200.0, 950.0, 500.0, 150.0]
    speeds_deg_s = [0.0] * 7 + [value * peak_deg_s / 1200.0 for value in shape] + [0.0] * 10
    angle = 0.0
    keys = {}
    for index, speed in enumerate(speeds_deg_s):
        angle += speed / 60
        keys[f"{index / 60:.4f}"] = [-angle, 0.0, 0.0]
    key = "animation.kizuna_players_animation.player.dodge_forward"
    animation = {"animation_length": (len(speeds_deg_s) - 1) / 60, "bones": {"r_shin": {"rotation": keys}}}
    if windows is not None:
        animation["burst_windows"] = windows
    sampler = AnimationSampler(model, {"format_version": "1.8.0", "animations": {key: animation}}, key)
    return velocity_gates(sampler, sample_times(sampler), impact_window)["pass"]


def hand_contact_passes(model: SkeletonModel, gap_px: float) -> bool:
    """两臂前平举、平移上臂让两掌心平行相对，间隙为 gap_px（负值为穿入）；整段声明相触窗口。"""
    shift = 4 - gap_px / 2
    bones = {"r_upper_arm": {"rotation": {"0": [-90, 0, 0]}, "position": {"0": [-shift, 0, 0]}},
             "l_upper_arm": {"rotation": {"0": [-90, 0, 0]}, "position": {"0": [shift, 0, 0]}}}
    key = "animation.kizuna_players_animation.player.evoker_wood_thunder"
    animation = {"animation_length": 0.1, "bones": bones, "hand_contact_windows": [[0.0, 0.1]]}
    sampler = AnimationSampler(model, {"format_version": "1.8.0", "animations": {key: animation}}, key)
    return hand_contact_gates(sampler, sample_times(sampler))["pass"]


def finger_overlay_passes(model: SkeletonModel, thumb: list[float], fingers_a: float, fingers_b: float) -> bool:
    """主手手指覆盖层：0.20 s 两帧同值、保持末帧，只含右手三片手指。"""
    pose = {"r_thumb": thumb, "r_fingers_a": [0.0, 0.0, fingers_a], "r_fingers_b": [0.0, 0.0, fingers_b]}
    bones = {bone: {"rotation": {"0.0": value, "0.2": value}} for bone, value in pose.items()}
    key = "animation.kizuna_players_animation.player.evoker_grip"
    animation = {"animation_length": 0.2, "loop": "hold_on_last_frame", "bones": bones}
    return report(AnimationSampler(model, {"format_version": "1.8.0", "animations": {key: animation}}, key))["pass"]


def item_rotation_sampler(model: SkeletonModel, degrees: float, seconds: float) -> AnimationSampler:
    """右前臂不动，索引 item_rotation 在 seconds 内把右手挂点绕 X 转 degrees（smoothstep，峰值角速度 1.5 × 平均）。"""
    key = "animation.kizuna_players_animation.player.herb_knife_cut"
    animation = {"animation_length": 0.4, "bones": {"r_forearm": {"rotation": {"0": [-10, 0, 0], "0.4": [-10, 0, 0]}}}}
    attach_item_rotation(animation, {"hand": "main", "item_rotation": [[0, [0, 0, 0]], [seconds, [degrees, 0, 0]]]})
    return AnimationSampler(model, {"format_version": "1.8.0", "animations": {key: animation}}, key)


def shifted_pelvis(document: dict, offset) -> dict:
    """导出文档的副本：胯的位置关键帧逐个加 offset(时刻) → (dx, dy, dz)。"""
    copy = json.loads(json.dumps(document))
    channel = next(iter(copy["animations"].values()))["bones"]["pelvis"]["position"]
    for time, value in channel.items():
        channel[time] = [a + b for a, b in zip(value, offset(float(time)))]
    return copy


def gates_of(model: SkeletonModel, document: dict) -> dict:
    return report(AnimationSampler(model, document, next(iter(document["animations"]))))


def main() -> int:
    root = Path(__file__).resolve().parents[1]
    document, key = fake_from_spec(root / "docs/animation-specs/attack_main.md")
    model = SkeletonModel(load_json(root / bbmodel_store.CLASSIC))
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
    # 爆发窗口：覆盖蹬伸峰值时放行，不声明或窗口错位时仍按 720°/s 拒绝。
    assert not burst_window_passes(model, None), "未声明爆发窗口的蹬伸峰值应被拒绝"
    assert burst_window_passes(model, [[0.12, 0.20]]), "爆发窗口内的蹬伸峰值应放行"
    assert not burst_window_passes(model, [[0.30, 0.38]]), "窗口外的蹬伸峰值不得放行"
    # 同时声明打击时段时（弓梢），爆发窗口里的帧仍按爆发上限查，不能两边都不查。
    assert burst_window_passes(model, [[0.12, 0.20]], [(0.30, 0.36)]), "另有打击时段时爆发窗口内的蹬伸仍应放行"
    assert not burst_window_passes(model, [[0.12, 0.20]], [(0.30, 0.36)], 1650.0), "另有打击时段时爆发窗口不得免查"

    # 相触窗口：两掌贴合放行，分开 1 px 或穿入 0.5 px 都拒绝。
    assert hand_contact_passes(model, 0.0), "贴合的两掌应放行"
    assert not hand_contact_passes(model, 1.0), "相触窗口里分开 1 px 应被拒绝"
    assert not hand_contact_passes(model, -0.5), "相触窗口里穿入 0.5 px 应被拒绝"
    # 手指覆盖层：握拳放行，三片手指都张开（0°）拒绝。
    assert finger_overlay_passes(model, [-10.0, 0.0, 50.0], 80.0, 86.0), "握拳的手指覆盖层应放行"
    assert not finger_overlay_passes(model, [0.0, 0.0, 0.0], 0.0, 0.0), "张开手指的覆盖层应被拒绝"
    # 挂点旋转：40° 用 0.2 s（峰值 300°/s）放行，用 0.05 s（峰值 1,200°/s）按 720°/s 拒绝；挂在挂点前方的点跟着转。
    slow = item_rotation_sampler(model, 40.0, 0.2)
    assert velocity_gates(slow, sample_times(slow))["pass"], "0.2 s 转 40° 的挂点应放行"
    fast = item_rotation_sampler(model, 40.0, 0.05)
    assert not velocity_gates(fast, sample_times(fast))["pass"], "0.05 s 转 40° 的挂点应被角速度门禁拒绝"
    world, _ = slow.forward_kinematics(0.3)
    origin = model.origins["r_item"]
    ahead = (origin[0], origin[1], origin[2] - 4)
    moved = [a - b for a, b in zip(world["r_item"].point(ahead), world["r_hand"].point(ahead))]
    pivot_drift = max(abs(a - b) for a, b in zip(world["r_item"].point(origin), world["r_hand"].point(origin)))
    assert abs(moved[1]) > 2 and pivot_drift < 1e-9, (moved, pivot_drift)
    # 坐地「触地即支撑」（campfire_*、idle_squat*）：原样的烤火循环放行；整个人往地下沉 1 px、臀在地上前后滑 1.5 px 拒绝。
    animations = root / "src/main/resources/assets/kizuna_players_animation/animations"
    warm = load_json(animations / "campfire_warm_loop.animation.json")
    seated = gates_of(model, warm)["ground_and_center"]
    assert seated["pass"] and seated["seated_frame_count"] > 0, seated
    sunk = gates_of(model, shifted_pelvis(warm, lambda time: (0.0, -1.0, 0.0)))["ground_and_center"]
    assert not sunk["pass"] and sunk["min_sole_y_px"] < -0.25, sunk
    slid = gates_of(model, shifted_pelvis(warm, lambda time: (0.0, 0.0, 1.5 * math.sin(math.pi * time / 12) ** 2)))
    slide = slid["ground_and_center"]
    assert not slide["pass"] and slide["seat_slip_px"] > 0.5, slide
    # 日常站姿循环进了 STANDING_LOOP_IDS：原样的叉腰循环放行，两脚跟着胯左右滑 1.2 px 拒绝。
    akimbo = load_json(animations / "idle_akimbo.animation.json")
    assert gates_of(model, akimbo)["ground_and_center"]["pass"], "原样的叉腰循环应放行"
    swayed = gates_of(model, shifted_pelvis(akimbo, lambda time: (1.2 * math.sin(math.pi * time / 12) ** 2, 0.0, 0.0)))
    sway = swayed["ground_and_center"]
    assert sway["standing_checks"] == "applied" and not sway["pass"], sway
    assert max(sway["support_foot_slip_px"].values()) > 0.5, sway
    # 村民几何：玩家版抱臂放到村民骨架上前臂穿进躯干（拒绝），村民版放行。
    robed = SkeletonModel(load_json(root / "models/robed_skeleton.bbmodel"))
    crossed = load_json(animations / "idle_arms_crossed.animation.json")
    crossed_robed = load_json(animations / "idle_arms_crossed_robed.animation.json")
    assert not gates_of(robed, crossed)["arm_intersection"]["pass"], "玩家版抱臂在村民几何上应穿进躯干"
    assert gates_of(robed, crossed_robed)["arm_intersection"]["pass"], "村民版抱臂在村民几何上应放行"
    print("旧伪造 attack_main 夹具被相关性、主链错峰、多轴门禁全部拒绝")
    print(f"correlation ratio={correlation['ratio']:.3f}, pairs={correlation['correlated_pair_count']}")
    print(f"peak order={peak_order['peak_times_s']}")
    print(f"multi-axis bones={multi_axis['count']}")
    print("爆发窗口只放宽窗口内的蹬伸峰值")
    print("相触窗口拒绝分开与穿入；手指覆盖层拒绝张开的手指")
    print("item_rotation 绕挂点转手持物，转得过快被角速度门禁拒绝")
    print(f"触地即支撑：沉地 1 px 拒绝（最低点 {sunk['min_sole_y_px']:.2f} px），"
          f"臀滑 1.5 px 拒绝（{slide['seat_slip_px']:.2f} px）")
    print(f"日常站姿循环按站立口径查：两脚跟着滑 1.2 px 拒绝（{max(sway['support_foot_slip_px'].values()):.2f} px）")
    print("村民几何：玩家版抱臂穿进袍子被拒绝，村民版放行")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
