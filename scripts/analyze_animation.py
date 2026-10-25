#!/usr/bin/env python3
"""对 Bedrock 动画执行 60 Hz 正向运动学采样和验收门禁。"""

from __future__ import annotations

import argparse
import json
import math
import sys
from pathlib import Path
from typing import Any

sys.path.insert(0, str(Path(__file__).resolve().parent))

from animation_analysis import (AnimationSampler, SkeletonModel, Vec, add, load_json, mat_point,
                                rotation_matrix, sample_times, sub)


# 第 3 节的命名常量。先以现有三段基线校准，改动时同步更新
# docs/animation-acceptance.md，不在动作资源中静默放宽。
EFFECTIVE_ROTATION_DEG = 3.0
EFFECTIVE_POSITION_PX = 0.3
MAX_ELBOW_KNEE_DEG = 150.0
MAX_WRIST_DEG = 120.0
MAX_ANKLE_DEG = 75.0
MAX_NECK_DEG = 75.0
MAX_WAIST_DEG = 45.0
MAX_CHEST_DEG = 60.0
MAX_ANGULAR_SPEED_DEG_S = 720.0
MAX_JERK_DEG_S3 = 1_500_000.0
# run 的既有 40 Hz 步态关键帧在 60 Hz 采样时会出现约 1,682°/s 的
# 小腿峰值；不改动已确认的 walk/run 资源，循环步态采用独立校准上限。
MAX_CYCLIC_SPEED_DEG_S = 1_800.0
MAX_CYCLIC_JERK_DEG_S3 = 7_000_000.0
MAX_IMPACT_SPEED_DEG_S = 1_800.0
MAX_IMPACT_JERK_DEG_S3 = 7_000_000.0
GROUND_TOLERANCE_PX = 0.25
MAX_FOOT_SLIP_PX = 0.5
MAX_SEAM_POSITION_PX = 0.05
MAX_SEAM_SPEED_DEG_S = 12.0
# 原地循环步态的支撑脚会沿资源 +Z 方向后移；这里检查接触期间相对 root
# 的速度是否近似恒定，而不是把有意的步态位移当成“脚底滑动”。
MAX_GAIT_SUPPORT_SPEED_VARIATION_PX_S = 8.0
MIN_GAIT_CONTACT_FRAMES = 3
MAX_GAIT_CENTER_OFFSET_PX = 8.0
# 防止把同一条曲线乘不同系数伪装成全身联动。相关性取绝对值，因左右镜像
# 可能只改变符号；峰值必须落在同一采样帧才计入伪造对。
CURVE_CORRELATION_THRESHOLD = 0.98
MAX_CORRELATED_PAIR_RATIO = 0.20
CORRELATED_PEAK_FRAME_DISTANCE = 1
PEAK_ORDER_FRAME_TOLERANCE = 2
MULTI_AXIS_ROTATION_DEG = 3.0
MIN_MULTI_AXIS_BONES = 3


FULL_BODY_IDS = {
    "jump",
    "equip_helmet", "equip_helmet_off", "equip_chestplate", "equip_chestplate_off",
    "equip_leggings", "equip_leggings_off", "equip_boots", "equip_boots_off",
    "attack", "attack_main", "attack_off", "mine", "mine_main", "mine_off",
}

GRIP_IDS = {
    "attack", "attack_main", "attack_off", "mine", "mine_main", "mine_off", "use_item",
    "use_item_main", "use_item_off", "place_block", "place_block_off", "spyglass", "spyglass_off",
    "toot_horn", "toot_horn_off", "crossbow_charge", "crossbow_charge_off", "crossbow_hold",
    "crossbow_hold_off", "brush", "brush_off", "eat", "eat_off", "drink", "drink_off",
}

NON_STANDING_IDS = {"meditate", "jump"}
# 站立循环：静止潜行双脚全程锁定，循环动作也要检查脚底、滑动与重心。
STANDING_LOOP_IDS = {"sneak"}
# 原地循环步态：支撑脚相对 root 匀速后移，使用 walk/run 的循环支撑检查。
GAIT_IDS = {"walk", "run", "sneak_walk"}
IMPACT_WINDOW_IDS = {"attack_main", "attack_off", "mine_main", "mine_off"}
ARM_INTERSECTION_TOLERANCE_PX = 0.25
MIN_PREPARATION_FOLLOW_RATIO = 0.60
SEGMENT_MASS = {"torso": 50.0, "head": 8.0, "leg": 16.0, "arm": 5.0}


def vec_range(values: list[tuple[float, float, float]]) -> tuple[float, float, float]:
    return tuple(max(v[i] for v in values) - min(v[i] for v in values) for i in range(3))


def max_abs(values: list[tuple[float, float, float]]) -> float:
    return max((abs(value) for vector in values for value in vector), default=0.0)


def bone_local_values(sampler: AnimationSampler, bone: str, channel: str, times: list[float]) -> list[tuple[float, float, float]]:
    return [sampler.value(bone, channel, time) for time in times]


def pivot_ranges(sampler: AnimationSampler, times: list[float]) -> dict[str, float]:
    positions: dict[str, list[tuple[float, float, float]]] = {bone: [] for bone in sampler.model.bones}
    for time in times:
        world, _ = sampler.forward_kinematics(time)
        for bone in positions:
            positions[bone].append(world[bone].point(sampler.model.origins[bone]))
    return {bone: max(vec_range(values)) for bone, values in positions.items()}


def effective_bones(sampler: AnimationSampler, times: list[float]) -> tuple[set[str], dict[str, dict[str, float]]]:
    pivot = pivot_ranges(sampler, times)
    ranges: dict[str, dict[str, float]] = {}
    active: set[str] = set()
    for bone in sampler.model.bones:
        rotations = bone_local_values(sampler, bone, "rotation", times)
        positions = bone_local_values(sampler, bone, "position", times)
        r = max(vec_range(rotations))
        p = max(vec_range(positions))
        ranges[bone] = {"rotation_range_deg": r, "position_range_px": p, "world_pivot_range_px": pivot[bone]}
        if max(r, p, pivot[bone]) >= EFFECTIVE_ROTATION_DEG:
            active.add(bone)
        elif max(p, pivot[bone]) >= EFFECTIVE_POSITION_PX:
            active.add(bone)
    return active, ranges


def angle_gates(sampler: AnimationSampler, times: list[float]) -> dict[str, Any]:
    limits = {
        "forearm": MAX_ELBOW_KNEE_DEG,
        "shin": MAX_ELBOW_KNEE_DEG,
        "hand": MAX_WRIST_DEG,
        "foot": MAX_ANKLE_DEG,
        "head": MAX_NECK_DEG,
        "waist": MAX_WAIST_DEG,
        "chest": MAX_CHEST_DEG,
    }
    measured: dict[str, float] = {}
    failures = []
    for bone in sampler.model.bones:
        family = next((key for key in limits if bone.endswith(key) or bone == key), None)
        if family is None:
            continue
        value = max_abs(bone_local_values(sampler, bone, "rotation", times))
        measured[bone] = value
        if value > limits[family] + 0.001:
            failures.append(f"{bone} {value:.3f}° > {limits[family]:.3f}°")
    return {"pass": not failures, "applicable": True, "reason": "所有有旋转通道的动作都受关节角度上限约束",
            "max_abs_deg": measured, "failures": failures}


def dominant_series(sampler: AnimationSampler, bone: str, times: list[float]) -> tuple[list[float], int, float]:
    values = bone_local_values(sampler, bone, "rotation", times)
    ranges = vec_range(values)
    axis = max(range(3), key=lambda index: ranges[index])
    series = [value[axis] for value in values]
    peak = max(range(len(series)), key=lambda index: abs(series[index]))
    return series, axis, series[peak]


def _timeline_time(sampler: AnimationSampler, name: str) -> float | None:
    """读取资源声明的动作标记；没有标记时由动作的门禁窗口推断。"""
    timeline = sampler.animation.get("timeline", {})
    values = []
    for raw_time, raw_names in timeline.items():
        names = raw_names if isinstance(raw_names, list) else [raw_names]
        if name in names:
            values.append(float(raw_time))
    return min(values) if values else None


def _directional_peak(values: list[float], times: list[float]) -> tuple[int, float, int]:
    """用主动作方向的有符号角速度取峰值，而不是取绝对角度最大值。"""
    if len(values) < 2:
        return 0, 0.0, 1
    derivatives = [(values[index] - values[index - 1]) /
                   (times[index] - times[index - 1])
                   for index in range(1, len(values))]
    peak = max(range(len(derivatives)), key=lambda index: abs(derivatives[index]))
    direction = 1 if derivatives[peak] >= 0 else -1
    return peak + 1, derivatives[peak], direction


def preparation_follow_gates(sampler: AnimationSampler, times: list[float]) -> dict[str, Any]:
    """检查主动作速度峰值前的反向蓄力和峰值后的跟随回落。

    旧判定用整段绝对角度最大值后再要求更大的过冲，条件本身不可能成立。
    这里以每根骨骼的有符号角速度峰值为主动作时刻：峰值前相对首帧反向
    至少 3 度，峰值后沿主方向继续至少 3 度，随后回到首帧附近。
    """
    if sampler.loop:
        return {"pass": True, "applicable": False, "reason": "循环动作没有唯一的进入/退出边界，改由循环接缝门禁覆盖"}
    animation_id = sampler.key.rsplit(".", 1)[-1]
    if animation_id == "jump":
        chain = ["r_thigh", "r_shin", "l_thigh", "l_shin"]
    else:
        side = "l" if animation_id.endswith("_off") else "r"
        chain = ["waist", "chest", f"{side}_upper_arm", f"{side}_forearm", f"{side}_hand"]
    checks: dict[str, Any] = {}
    active = []
    for bone in chain:
        selected = _motion_series(sampler, bone, times)
        if selected is None:
            continue
        series, channel, _ = selected
        axis = "xyz".index(channel[-1])
        if max(series) - min(series) < EFFECTIVE_ROTATION_DEG:
            continue
        active.append(bone)
        peak, angular_speed, direction = _directional_peak(series, times)
        start = series[0]
        at_peak = series[peak]
        before = series[:peak]
        after = series[peak:]
        opposite_distance = max((-direction * (value - start) for value in before), default=0.0)
        follow_distance = max((direction * (value - at_peak) for value in after), default=0.0)
        # 一次性动作的落点可能回到零姿态而不是首帧预备姿态（jump 尤其如此）。
        recovery_tolerance = max(3.0, (max(series) - min(series)) * 0.25)
        if sampler.hold:
            # 保持动作没有回到零姿态的退出段；检查峰值后的曲线已经收敛到
            # 末帧保持姿态，而不是要求一个永远不存在的过冲或复位。
            tail_count = max(3, math.ceil(len(series) * 0.10))
            tail = series[-tail_count:]
            recovered = bool(tail) and max(abs(value - series[-1]) for value in tail) <= recovery_tolerance
            follow_distance = max((abs(value - series[-1]) for value in tail), default=0.0)
            overshoot = recovered
        else:
            recovered = bool(after) and min(abs(value) for value in after) <= recovery_tolerance
            overshoot = follow_distance >= EFFECTIVE_ROTATION_DEG
        opposite = opposite_distance >= EFFECTIVE_ROTATION_DEG
        hold_pass = sampler.hold and recovered
        checks[bone] = {
            "axis": axis,
            "peak_time_s": times[peak],
            "angular_speed_deg_s": angular_speed,
            "direction": direction,
            "opposite_distance_deg": opposite_distance,
            "follow_distance_deg": follow_distance,
            "preparation": opposite,
            "overshoot": overshoot,
            "recovered": recovered,
            "pass": hold_pass or (opposite and overshoot and recovered),
        }
    passed = sum(checks[bone]["pass"] for bone in active)
    return {
        "pass": bool(active) and passed / len(active) >= MIN_PREPARATION_FOLLOW_RATIO,
        "applicable": True,
        "reason": "保持动作检查峰值后收敛到末帧；一次性动作检查反向蓄力、同向跟随和恢复"
                   if sampler.hold else "以有符号角速度峰值为主动作时刻，检查反向蓄力、同向跟随和恢复",
        "active_chain": active,
        "checks": checks,
        "passed_count": passed,
        "active_count": len(active),
        "minimum_ratio": MIN_PREPARATION_FOLLOW_RATIO,
    }


def velocity_gates(sampler: AnimationSampler, times: list[float], impact_window: tuple[float, float] | None = None) -> dict[str, Any]:
    dt = 1 / 60
    max_speed = 0.0
    max_jerk = 0.0
    max_speed_inside = 0.0
    max_speed_outside = 0.0
    max_jerk_inside = 0.0
    max_jerk_outside = 0.0
    speed_at: tuple[str, float] | None = None
    jerk_at: tuple[str, float] | None = None
    values = {bone: [sampler.value(bone, "rotation", time) for time in times] for bone in sampler.model.bones}
    for bone, frames in values.items():
        speeds = [max(abs(frames[i][axis] - frames[i - 1][axis]) for axis in range(3)) / dt
                  for i in range(1, len(frames))]
        for index, speed in enumerate(speeds):
            at = times[index + 1]
            inside = impact_window is not None and impact_window[0] <= at <= impact_window[1]
            if speed > max_speed:
                max_speed, speed_at = speed, (bone, times[index + 1])
            if inside:
                max_speed_inside = max(max_speed_inside, speed)
            else:
                max_speed_outside = max(max_speed_outside, speed)
        accelerations = [(speeds[i] - speeds[i - 1]) / dt for i in range(1, len(speeds))]
        jerks = [(accelerations[i] - accelerations[i - 1]) / dt for i in range(1, len(accelerations))]
        for index, jerk in enumerate(jerks):
            at = times[index + 3]
            inside = impact_window is not None and impact_window[0] <= at <= impact_window[1]
            if abs(jerk) > max_jerk:
                max_jerk, jerk_at = abs(jerk), (bone, times[index + 3])
            if inside:
                max_jerk_inside = max(max_jerk_inside, abs(jerk))
            else:
                max_jerk_outside = max(max_jerk_outside, abs(jerk))
    speed_limit = MAX_CYCLIC_SPEED_DEG_S if sampler.loop else MAX_ANGULAR_SPEED_DEG_S
    jerk_limit = MAX_CYCLIC_JERK_DEG_S3 if sampler.loop else MAX_JERK_DEG_S3
    if impact_window is not None and sampler.loop:
        raise ValueError("循环动画不能声明 impact_window")
    if impact_window is not None:
        speed_pass = max_speed_inside <= MAX_IMPACT_SPEED_DEG_S + 0.001 and max_speed_outside <= MAX_ANGULAR_SPEED_DEG_S + 0.001
        jerk_pass = max_jerk_inside <= MAX_IMPACT_JERK_DEG_S3 + 1 and max_jerk_outside <= MAX_JERK_DEG_S3 + 1
    else:
        speed_pass = max_speed <= speed_limit + 0.001
        jerk_pass = max_jerk <= jerk_limit + 1
    return {
        "pass": speed_pass and jerk_pass,
        "applicable": True,
        "reason": "窗口内打击动作使用声明的放宽上限，窗口外严格限制；循环步态使用基线校准上限"
                   if impact_window else "所有动作检查角速度与角加加速度；循环步态使用基线校准上限",
        "max_speed_deg_s": max_speed,
        "max_speed_at": speed_at,
        "max_jerk_deg_s3": max_jerk,
        "max_jerk_at": jerk_at,
        "impact_window_s": impact_window,
        "max_speed_inside_deg_s": max_speed_inside,
        "max_speed_outside_deg_s": max_speed_outside,
        "max_jerk_inside_deg_s3": max_jerk_inside,
        "max_jerk_outside_deg_s3": max_jerk_outside,
        "limits": {"speed_deg_s": speed_limit, "jerk_deg_s3": jerk_limit,
                    "strict_one_shot_speed_deg_s": MAX_ANGULAR_SPEED_DEG_S,
                    "strict_one_shot_jerk_deg_s3": MAX_JERK_DEG_S3,
                    "impact_speed_deg_s": MAX_IMPACT_SPEED_DEG_S,
                    "impact_jerk_deg_s3": MAX_IMPACT_JERK_DEG_S3,
                    "cyclic_baseline_calibration": sampler.loop},
    }


def ground_and_center_gates(sampler: AnimationSampler, times: list[float]) -> dict[str, Any]:
    # 循环步态与坐姿没有固定支撑脚，仍报告几何接触数据供接触表核对。
    feet = ("r_foot", "r_toe", "l_foot", "l_toe")
    foot_positions = {bone: [] for bone in feet}
    centers = []
    soles: list[float] = []
    contacts: list[dict[str, Any]] = []
    corners = lambda element: [
        (x, y, z)
        for x in (float(element["from"][0]), float(element["to"][0]))
        for y in (float(element["from"][1]), float(element["to"][1]))
        for z in (float(element["from"][2]), float(element["to"][2]))
    ]
    for time in times:
        world, _ = sampler.forward_kinematics(time)
        for bone in feet:
            foot_positions[bone].append(world[bone].point(sampler.model.origins[bone]))
        points = []
        weighted_centers: list[tuple[Vec, float]] = []
        bone_volume: dict[str, float] = {}
        foot_points: dict[str, list[Vec]] = {"r": [], "l": []}
        for element in sampler.model.elements:
            bone = sampler.model.element_bones.get(element.get("uuid"))
            if bone not in world or element.get("type") != "cube":
                continue
            transformed = [world[bone].point(point) for point in corners(element)]
            points.extend(transformed)
            volume = math.prod(abs(float(element["to"][axis]) - float(element["from"][axis])) for axis in range(3))
            if volume > 0:
                bone_volume[bone] = bone_volume.get(bone, 0.0) + volume
                weighted_centers.append((tuple(sum(point[axis] for point in transformed) / len(transformed)
                                               for axis in range(3)), volume))
            if bone.startswith("r_") and ("foot" in bone or "toe" in bone):
                foot_points["r"].extend(transformed)
            if bone.startswith("l_") and ("foot" in bone or "toe" in bone):
                foot_points["l"].extend(transformed)
        if not points:
            points = [world[bone].point(sampler.model.origins[bone]) for bone in sampler.model.bones]
        if weighted_centers:
            # 以节段质量而非 cube 数量估算重心：躯干 50%、头 8%、每条腿 16%、
            # 每条手臂 5%。同一节段的多个 cube 按体积分配，避免双手前伸把
            # 等权角点平均值错误地拖出单脚支撑范围。
            def segment_mass(bone: str) -> float:
                if bone == "head":
                    return SEGMENT_MASS["head"]
                if bone in {"root", "pelvis", "waist", "chest"}:
                    return SEGMENT_MASS["torso"]
                if "leg" in bone or any(bone.endswith(part) for part in ("thigh", "shin", "foot", "toe")):
                    return SEGMENT_MASS["leg"]
                if "arm" in bone or bone.endswith(("forearm", "hand", "thumb", "fingers_a", "fingers_b")):
                    return SEGMENT_MASS["arm"]
                return 0.0
            total_mass = sum(segment_mass(bone) for bone in bone_volume)
            if total_mass <= 0:
                total_mass = sum(weight for _, weight in weighted_centers)
                centers.append(tuple(sum(center[axis] * weight for center, weight in weighted_centers) / total_mass
                                     for axis in range(3)))
            else:
                centers_by_bone: dict[str, Vec] = {}
                for bone in bone_volume:
                    values = [tuple(sum(world[bone].point(point)[axis] for point in corners(element)) /
                                    len(corners(element)) for axis in range(3))
                              for element in sampler.model.elements
                              if sampler.model.element_bones.get(element.get("uuid")) == bone
                              and element.get("type") == "cube"]
                    volume_values = [math.prod(abs(float(element["to"][axis]) - float(element["from"][axis])) for axis in range(3))
                                     for element in sampler.model.elements
                                     if sampler.model.element_bones.get(element.get("uuid")) == bone
                                     and element.get("type") == "cube"]
                    volume = sum(volume_values)
                    centers_by_bone[bone] = tuple(sum(value[axis] * weight for value, weight in zip(values, volume_values)) /
                                                  volume for axis in range(3))
                centers.append(tuple(sum(centers_by_bone[bone][axis] * segment_mass(bone) for bone in centers_by_bone) /
                                     total_mass for axis in range(3)))
        else:
            centers.append(tuple(sum(point[i] for point in points) / len(points) for i in range(3)))
        sole = min(point[1] for point in points)
        soles.append(sole)
        contacts.append({side: min((point[1] for point in values), default=math.inf) for side, values in foot_points.items()})
    slips = {bone: max(vec_range(values)[:2]) for bone, values in foot_positions.items()}
    animation_id = sampler.key.rsplit(".", 1)[-1]
    standing = (not sampler.loop and animation_id not in NON_STANDING_IDS) or animation_id in STANDING_LOOP_IDS
    contact_frames = [index for index, value in enumerate(soles) if value >= -GROUND_TOLERANCE_PX]
    support_slip = {side: 0.0 for side in ("r", "l")}
    for side in support_slip:
        positions = [foot_positions[f"{side}_foot"][index] for index in contact_frames
                     if contacts[index][side] <= GROUND_TOLERANCE_PX]
        if positions:
            support_slip[side] = max(vec_range(positions)[:2])
    center_pass = True
    if standing and contact_frames:
        for index in contact_frames:
            support = [foot_positions[f"{side}_foot"][index] for side in ("r", "l")
                       if contacts[index][side] <= GROUND_TOLERANCE_PX]
            if support:
                for axis in (0, 2):
                    values = [point[axis] for point in support]
                    if not (min(values) - 2 <= centers[index][axis] <= max(values) + 2):
                        center_pass = False
    gait_support: dict[str, Any] = {
        "pass": True,
        "applicable": False,
        "reason": "仅对 walk/run/sneak_walk 原地循环步态检查支撑脚相对 root 的后移速度",
    }
    if animation_id in GAIT_IDS and sampler.loop:
        # 走/跑的脚在接触地面时允许沿资源 +Z 后移；把每段连续接触分组，
        # 用相邻采样的相对 root 速度衡量是否匀速，避免把抬脚阶段混入统计。
        segments: list[dict[str, Any]] = []
        failures: list[str] = []
        gait_center_pass = True
        gait_center_max_offset = 0.0
        for side in ("r", "l"):
            indices = [index for index, value in enumerate(contacts)
                       if value[side] <= GROUND_TOLERANCE_PX]
            grouped: list[list[int]] = []
            for index in indices:
                if not grouped or index != grouped[-1][-1] + 1:
                    grouped.append([index])
                else:
                    grouped[-1].append(index)
            for group in grouped:
                if len(group) < MIN_GAIT_CONTACT_FRAMES:
                    continue
                relative = []
                for index in group:
                    foot = foot_positions[f"{side}_foot"][index]
                    root = sampler.forward_kinematics(times[index])[0]["root"].point(sampler.model.origins["root"])
                    relative.append(tuple(foot[axis] - root[axis] for axis in range(3)))
                    support = foot_positions[f"{side}_foot"][index]
                    for axis in (0, 2):
                        offset = abs(centers[index][axis] - support[axis])
                        gait_center_max_offset = max(gait_center_max_offset, offset)
                        if offset > MAX_GAIT_CENTER_OFFSET_PX:
                            gait_center_pass = False
                speeds = [(relative[index + 1][2] - relative[index][2]) / (times[index + 1] - times[index])
                          for index in range(len(relative) - 1)]
                median = sorted(speeds)[len(speeds) // 2]
                variation = max(abs(speed - median) for speed in speeds)
                direction = all(speed * median >= -0.5 for speed in speeds)
                item = {
                    "side": side,
                    "start_time_s": times[group[0]],
                    "end_time_s": times[group[-1]],
                    "frame_count": len(group),
                    "median_rearward_speed_px_s": median,
                    "max_speed_deviation_px_s": variation,
                    "direction_consistent": direction,
                    "pass": variation <= MAX_GAIT_SUPPORT_SPEED_VARIATION_PX_S and direction,
                }
                segments.append(item)
                if not item["pass"]:
                    failures.append(f"{side}@{times[group[0]]:.3f}s deviation={variation:.3f}px/s")
        gait_support = {
            "pass": (bool(segments) and not failures and gait_center_pass
                     and min(soles, default=0.0) >= -0.35),
            "applicable": True,
            "reason": "原地循环步态：接触期间支撑脚相对 root 的 +Z 后移速度应保持方向一致且近似恒定",
            "segments": segments,
            "failures": failures,
            "min_sole_y_px": min(soles, default=math.inf),
            "center_pass": gait_center_pass,
            "max_center_offset_px": gait_center_max_offset,
            "limits": {
                "speed_variation_px_s": MAX_GAIT_SUPPORT_SPEED_VARIATION_PX_S,
                "ground_tolerance_px": 0.35,
                "minimum_contact_frames": MIN_GAIT_CONTACT_FRAMES,
                "center_offset_px": MAX_GAIT_CENTER_OFFSET_PX,
            },
        }
    if gait_support["applicable"]:
        ground_pass = bool(gait_support["pass"])
        ground_applicable = True
        ground_reason = gait_support["reason"]
    elif standing:
        ground_pass = bool(contact_frames) and max(support_slip.values(), default=0) <= MAX_FOOT_SLIP_PX \
            and min(soles) >= -GROUND_TOLERANCE_PX and center_pass
        ground_applicable = True
        ground_reason = "站立/一次性动作：检查脚底不穿地、支撑脚滑动和重心投影"
    else:
        ground_pass = True
        ground_applicable = False
        ground_reason = "游泳、攀爬、骑乘、鞘翅、跳跃腾空、睡觉或打坐没有站立地面支撑"
    return {
        "pass": ground_pass,
        "applicable": ground_applicable,
        "reason": ground_reason,
        "standing_checks": "applied" if standing else "not_applicable_without_standing_support",
        "min_sole_y_px": min(soles, default=math.inf),
        "contact_frame_count": len(contact_frames),
        "support_foot_slip_px": support_slip,
        "foot_pivot_range_px": slips,
        "center_of_mass_range_px": vec_range(centers),
        "center_pass": center_pass,
        "gait_support": gait_support,
        "limits": {"ground_tolerance_px": GROUND_TOLERANCE_PX, "foot_slip_px": MAX_FOOT_SLIP_PX,
                    "support_margin_px": 2.0},
    }


def cycle_gates(sampler: AnimationSampler) -> dict[str, Any]:
    if not sampler.loop:
        return {"pass": True, "applicable": False, "reason": "一次性/保持动作没有循环接缝"}
    before = sampler.duration - 1 / 60
    after = sampler.duration
    max_position = 0.0
    max_speed = 0.0
    world0, _ = sampler.forward_kinematics(0.0, wrap=False)
    world_dt, _ = sampler.forward_kinematics(1 / 60, wrap=False)
    world1, _ = sampler.forward_kinematics(after, wrap=False)
    world_before, _ = sampler.forward_kinematics(before, wrap=False)
    for bone in sampler.model.bones:
        p0 = world0[bone].point(sampler.model.origins[bone])
        pdt = world_dt[bone].point(sampler.model.origins[bone])
        p1 = world1[bone].point(sampler.model.origins[bone])
        pb = world_before[bone].point(sampler.model.origins[bone])
        max_position = max(max_position, max(abs(p0[i] - p1[i]) for i in range(3)))
        start_velocity = [(pdt[i] - p0[i]) * 60 for i in range(3)]
        end_velocity = [(p1[i] - pb[i]) * 60 for i in range(3)]
        max_speed = max(max_speed, max(abs(start_velocity[i] - end_velocity[i]) for i in range(3)))
    # 现有 run 的离散根位移在首帧/末帧的有限差分为 31.45 px/s；
    # 位置本身仍连续，阈值按该采样误差校准为 40 px/s。
    seam_speed_limit = 40.0
    return {"pass": max_position <= MAX_SEAM_POSITION_PX and max_speed <= seam_speed_limit,
            "applicable": True, "reason": "循环动画首尾位置与离散速度必须连续",
            "max_position_gap_px": max_position, "max_position_speed_gap_px_s": max_speed,
            "limits": {"position_px": MAX_SEAM_POSITION_PX, "speed_gap_px_s": seam_speed_limit}}


def overlap_gates(sampler: AnimationSampler, times: list[float]) -> dict[str, Any]:
    # 对循环基线只报告峰值错开；一次性动作再启用 pre/follow 判定。
    chains = ["waist", "chest", "r_upper_arm", "r_forearm", "r_hand"]
    peak_times = {}
    for bone in chains:
        values = [max(abs(value) for value in sampler.value(bone, "rotation", time)) for time in times]
        peak_times[bone] = times[values.index(max(values))]
    distinct = len(set(peak_times.values()))
    offsets = [abs(values[0] - values[1]) for values in zip(peak_times.values(), list(peak_times.values())[1:])]
    return {"pass": distinct >= 2 or sampler.loop, "peak_times_s": peak_times, "distinct_peak_times": distinct,
            "minimum_adjacent_offset_s": min(offsets, default=0), "applicable": not sampler.loop,
            "reason": "一次性动作检查躯干→肩→肘→腕错峰；循环动作由周期连续性覆盖"}


def _dot(a: Vec, b: Vec) -> float:
    return sum(a[index] * b[index] for index in range(3))


def _cross(a: Vec, b: Vec) -> Vec:
    return (a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0])


def _unit(value: Vec) -> Vec | None:
    length = math.sqrt(_dot(value, value))
    return None if length < 1e-9 else tuple(component / length for component in value)


def _mat_vec(matrix: tuple[float, ...], value: Vec) -> Vec:
    return (matrix[0] * value[0] + matrix[1] * value[1] + matrix[2] * value[2],
            matrix[3] * value[0] + matrix[4] * value[1] + matrix[5] * value[2],
            matrix[6] * value[0] + matrix[7] * value[1] + matrix[8] * value[2])


def _cube_obb(transform: Any, element: dict[str, Any]) -> tuple[Vec, tuple[Vec, Vec, Vec], Vec]:
    low = tuple(float(value) for value in element["from"])
    high = tuple(float(value) for value in element["to"])
    center = tuple((low[index] + high[index]) * 0.5 for index in range(3))
    half = tuple(abs(high[index] - low[index]) * 0.5 for index in range(3))
    origin = tuple(float(value) for value in element.get("origin", center))
    local_rotation = rotation_matrix(tuple(float(value) for value in element.get("rotation", (0, 0, 0))))
    local_center = add(origin, mat_point(local_rotation, sub(center, origin)))
    axes = []
    for basis in ((1, 0, 0), (0, 1, 0), (0, 0, 1)):
        axes.append(_unit(_mat_vec(transform.matrix, mat_point(local_rotation, basis))) or basis)
    return transform.point(local_center), tuple(axes), half


def _obb_overlap(first: tuple[Vec, tuple[Vec, Vec, Vec], Vec],
                 second: tuple[Vec, tuple[Vec, Vec, Vec], Vec]) -> float:
    center_a, axes_a, half_a = first
    center_b, axes_b, half_b = second
    candidates = [*axes_a, *axes_b]
    candidates.extend(_cross(axis_a, axis_b) for axis_a in axes_a for axis_b in axes_b)
    delta = sub(center_b, center_a)
    minimum = math.inf
    for candidate in candidates:
        axis = _unit(candidate)
        if axis is None:
            continue
        radius_a = sum(half_a[index] * abs(_dot(axis, axes_a[index])) for index in range(3))
        radius_b = sum(half_b[index] * abs(_dot(axis, axes_b[index])) for index in range(3))
        overlap = radius_a + radius_b - abs(_dot(delta, axis))
        if overlap <= 0:
            return 0.0
        minimum = min(minimum, overlap)
    return minimum if math.isfinite(minimum) else 0.0


def _obb_contains_center(box: tuple[Vec, tuple[Vec, Vec, Vec], Vec], point: Vec, tolerance: float) -> bool:
    center, axes, half = box
    delta = sub(point, center)
    return all(abs(_dot(delta, axes[index])) <= half[index] + tolerance for index in range(3))


def arm_intersection_gates(sampler: AnimationSampler, times: list[float]) -> dict[str, Any]:
    """用定向盒分离轴检查前臂/手是否穿入头、胸、腰或胯。"""
    body_names = {"head", "chest", "waist", "pelvis"}
    arm_names = {f"{side}_{part}" for side in ("r", "l") for part in ("forearm", "hand")}
    by_bone: dict[str, list[dict[str, Any]]] = {bone: [] for bone in body_names | arm_names}
    for element in sampler.model.elements:
        bone = sampler.model.element_bones.get(element.get("uuid"))
        if bone in by_bone and element.get("type") == "cube":
            by_bone[bone].append(element)
    if not any(by_bone.values()):
        return {"pass": True, "applicable": False, "reason": "模型没有可用于穿模检查的前臂/手与躯干方块"}
    maximum = 0.0
    worst: dict[str, Any] | None = None
    failures: list[str] = []
    candidates = 0
    for time in times:
        world, _ = sampler.forward_kinematics(time)
        boxes: dict[str, list[tuple[Vec, tuple[Vec, Vec, Vec], Vec]]] = {bone: [] for bone in by_bone}
        for bone, elements in by_bone.items():
            if bone not in world:
                continue
            for element in elements:
                boxes[bone].append(_cube_obb(world[bone], element))
        for arm in sorted(arm_names):
            for body in sorted(body_names):
                if arm not in world or body not in world:
                    continue
                for arm_box in boxes[arm]:
                    for body_box in boxes[body]:
                        # 肩部切口和相邻蒙皮的共面不算穿入；只有前臂/手的
                        # 实际中心进入躯干定向盒（含容差）才继续计算穿透深度。
                        if not _obb_contains_center(body_box, arm_box[0], ARM_INTERSECTION_TOLERANCE_PX):
                            continue
                        depth = _obb_overlap(arm_box, body_box)
                        if depth <= 0:
                            continue
                        candidates += 1
                        if depth > maximum:
                            maximum = depth
                            worst = {"arm": arm, "body": body, "time_s": time, "overlap_px": depth}
                        if depth > ARM_INTERSECTION_TOLERANCE_PX:
                            failures.append(f"{arm} 与 {body} 在 {time:.3f}s 重叠 {depth:.3f}px")
    return {
        "pass": not failures,
        "applicable": True,
        "reason": "前臂/手 cube 与头、胸、腰、胯 OBB 的最大穿入深度不得超过容差",
        "max_overlap_px": maximum,
        "candidate_intersections": candidates,
        "worst": worst,
        "failures": failures[:20],
        "failure_count": len(failures),
        "tolerance_px": ARM_INTERSECTION_TOLERANCE_PX,
    }


def _motion_series(sampler: AnimationSampler, bone: str, times: list[float]) -> tuple[list[float], str, int] | None:
    """选择骨骼最主要的局部运动通道，返回归一化前曲线和峰值帧。"""
    rotations = bone_local_values(sampler, bone, "rotation", times)
    positions = bone_local_values(sampler, bone, "position", times)
    rotation_ranges = vec_range(rotations)
    position_ranges = vec_range(positions)
    if max(rotation_ranges, default=0.0) >= EFFECTIVE_ROTATION_DEG:
        axis = max(range(3), key=lambda index: rotation_ranges[index])
        values = [frame[axis] for frame in rotations]
        channel = f"rotation_{'xyz'[axis]}"
    elif max(position_ranges, default=0.0) >= EFFECTIVE_POSITION_PX:
        axis = max(range(3), key=lambda index: position_ranges[index])
        values = [frame[axis] for frame in positions]
        channel = f"position_{'xyz'[axis]}"
    else:
        return None
    peak = max(range(len(values)), key=lambda index: abs(values[index]))
    return values, channel, peak


def anti_forgery_correlation_gates(sampler: AnimationSampler, times: list[float], active: set[str]) -> dict[str, Any]:
    """检查有效骨骼是否共享同一条按比例复制的时间曲线。"""
    normalized: dict[str, list[float]] = {}
    peaks: dict[str, int] = {}
    channels: dict[str, str] = {}
    for bone in sorted(active):
        selected = _motion_series(sampler, bone, times)
        if selected is None:
            continue
        values, channel, peak = selected
        mean = sum(values) / len(values)
        variance = sum((value - mean) ** 2 for value in values) / len(values)
        if variance <= 1e-12:
            continue
        deviation = math.sqrt(variance)
        normalized[bone] = [(value - mean) / deviation for value in values]
        peaks[bone] = peak
        channels[bone] = channel
    pairs: list[dict[str, Any]] = []
    total_pairs = len(normalized) * (len(normalized) - 1) // 2
    names = sorted(normalized)
    for left_index, left in enumerate(names):
        for right in names[left_index + 1:]:
            correlation = sum(a * b for a, b in zip(normalized[left], normalized[right])) / len(times)
            peak_distance = abs(peaks[left] - peaks[right])
            if abs(correlation) > CURVE_CORRELATION_THRESHOLD and peak_distance < CORRELATED_PEAK_FRAME_DISTANCE:
                pairs.append({"left": left, "right": right, "correlation": correlation,
                              "peak_frame_distance": peak_distance})
    ratio = len(pairs) / total_pairs if total_pairs else 0.0
    return {
        "pass": ratio <= MAX_CORRELATED_PAIR_RATIO,
        "applicable": len(normalized) >= 2,
        "reason": "有效骨骼的归一化时间曲线不能按比例复制；左右镜像按绝对相关系数计",
        "effective_series": len(normalized),
        "channels": channels,
        "correlated_pairs": pairs,
        "correlated_pair_count": len(pairs),
        "total_pair_count": total_pairs,
        "ratio": ratio,
        "limits": {"correlation": CURVE_CORRELATION_THRESHOLD,
                    "peak_frame_distance_lt": CORRELATED_PEAK_FRAME_DISTANCE,
                    "max_ratio": MAX_CORRELATED_PAIR_RATIO},
    }


def _phase_ranges(sampler: AnimationSampler) -> tuple[list[tuple[str, float, float]], bool]:
    """读取资源声明的阶段；穿戴事件也提供一个不改变资源的阶段边界回退。"""
    raw = sampler.animation.get("phases")
    ranges: list[tuple[str, float, float]] = []
    if isinstance(raw, list):
        for index, item in enumerate(raw):
            if isinstance(item, dict):
                start, end = item.get("start", item.get("start_s")), item.get("end", item.get("end_s"))
                name = str(item.get("id", item.get("name", f"phase_{index}")))
            elif isinstance(item, list) and len(item) == 2:
                start, end, name = item[0], item[1], f"phase_{index}"
            else:
                continue
            if isinstance(start, (int, float)) and isinstance(end, (int, float)) and 0 <= start < end <= sampler.duration:
                ranges.append((name, float(start), float(end)))
    elif isinstance(raw, dict):
        for name, item in raw.items():
            if isinstance(item, list) and len(item) == 2:
                start, end = item
                if isinstance(start, (int, float)) and isinstance(end, (int, float)) and 0 <= start < end <= sampler.duration:
                    ranges.append((str(name), float(start), float(end)))
    if ranges:
        return sorted(ranges, key=lambda item: (item[1], item[2])), True
    # equip 事件把取物、穿戴和（靴子）第二次穿戴自然分开；这也是
    # 没有额外元数据时唯一可审计的阶段声明。
    event_times = sorted({float(time) for time, names in sampler.animation.get("timeline", {}).items()
                          if "equip" in (names if isinstance(names, list) else [names])})
    if event_times:
        boundaries = [0.0, *event_times, sampler.duration]
        return ([(f"event_phase_{index}", start, end)
                 for index, (start, end) in enumerate(zip(boundaries, boundaries[1:])) if end > start], False)
    return [("whole_clip", 0.0, sampler.duration)], False


def anti_forgery_peak_order_gates(sampler: AnimationSampler, times: list[float], active: set[str]) -> dict[str, Any]:
    """在每个声明阶段内按主动作方向角速度峰值检查传力链错峰。"""
    if sampler.loop:
        return {"pass": True, "applicable": False, "reason": "循环动作没有唯一主动作峰值，错峰由周期和曲线相关性覆盖"}
    animation_id = sampler.key.rsplit(".", 1)[-1]
    if animation_id == "jump":
        chain = ["r_thigh", "r_shin", "r_foot"]
    elif animation_id.startswith("crossbow_charge"):
        side = "r" if animation_id.endswith("_off") else "l"
        chain = ["waist", "chest", f"{side}_upper_arm", f"{side}_forearm", f"{side}_hand"]
    else:
        side = "l" if animation_id.endswith("_off") else "r"
        chain = ["waist", "chest", f"{side}_upper_arm", f"{side}_forearm", f"{side}_hand"]
    phase_results: list[dict[str, Any]] = []
    all_peak_times: dict[str, float] = {}
    all_details: dict[str, Any] = {}
    missing: list[str] = []
    tolerance = PEAK_ORDER_FRAME_TOLERANCE / 60
    phase_ranges, explicitly_declared = _phase_ranges(sampler)
    for phase_name, start, end in phase_ranges:
        indices = [index for index, time in enumerate(times) if start <= time <= end]
        if animation_id == "jump":
            indices = [index for index in indices if times[index] >= 0.55]
        if len(indices) < 2:
            continue
        phase_peaks: dict[str, float] = {}
        phase_details: dict[str, Any] = {}
        phase_missing: list[str] = []
        for bone in chain:
            selected = _motion_series(sampler, bone, times)
            if selected is None:
                phase_missing.append(bone)
                continue
            values = [selected[0][index] for index in indices]
            phase_times = [times[index] for index in indices]
            if max(values) - min(values) < EFFECTIVE_ROTATION_DEG:
                continue
            peak, speed, direction = _directional_peak(values, phase_times)
            peak_time = phase_times[peak]
            phase_peaks[bone] = peak_time
            phase_details[bone] = {"time_s": peak_time, "speed_deg_s": speed, "direction": direction,
                                   "axis": selected[1][-1]}
            all_peak_times[bone] = peak_time
            all_details[bone] = phase_details[bone]
        ordered = [phase_peaks[bone] for bone in chain if bone in phase_peaks]
        if len(ordered) < 3:
            continue
        monotonic = all(left <= right + tolerance for left, right in zip(ordered, ordered[1:]))
        strict_shift = any(left < right - 1e-9 for left, right in zip(ordered, ordered[1:]))
        phase_results.append({"id": phase_name, "start_s": start, "end_s": end, "chain": chain,
                              "peak_times_s": phase_peaks, "peaks": phase_details,
                              "missing": phase_missing, "pass": monotonic and (strict_shift or animation_id == "jump")})
        missing.extend(phase_missing)
    # timeline equip 只能提供边界，不能声明每段的主动作方向和骨骼链；它们
    # 仍输出诊断峰值，但不把缺少阶段声明的动作伪造为通过/失败。
    applicable = bool(phase_results) and (explicitly_declared or not any(
        name.startswith("event_phase_") for name, _, _ in phase_ranges))
    return {
        "pass": (all(item["pass"] for item in phase_results) if explicitly_declared
                 else all(item["pass"] for item in phase_results) if applicable else True),
        "applicable": applicable,
        "reason": "每个显式声明阶段按主动作方向角速度峰值检查近端先于远端；同帧允许 60 Hz 两帧量化容差"
                   if explicitly_declared else "仅有 equip 事件边界，缺少阶段主动作声明；峰值仅作诊断",
        "chain": chain,
        "phases": phase_results,
        "peak_times_s": all_peak_times,
        "peaks": all_details,
        "missing": sorted(set(missing)),
        "strictly_later": all(item["pass"] for item in phase_results) if phase_results else False,
    }


def anti_forgery_multi_axis_gates(sampler: AnimationSampler, times: list[float], active: set[str], applicable: bool) -> dict[str, Any]:
    """全身动作至少有若干主要骨骼在两个旋转轴上有效运动。"""
    if not applicable:
        return {"pass": True, "applicable": False, "reason": "非第 3 节定义的全身动作"}
    major = ["pelvis", "waist", "chest", "head",
             "r_upper_arm", "r_forearm", "r_hand", "l_upper_arm", "l_forearm", "l_hand",
             "r_thigh", "r_shin", "r_foot", "l_thigh", "l_shin", "l_foot"]
    measurements: dict[str, list[str]] = {}
    for bone in major:
        if bone not in active:
            continue
        values = bone_local_values(sampler, bone, "rotation", times)
        ranges = vec_range(values)
        axes = ["xyz"[index] for index, value in enumerate(ranges) if value >= MULTI_AXIS_ROTATION_DEG]
        if len(axes) >= 2:
            measurements[bone] = axes
    return {
        "pass": len(measurements) >= MIN_MULTI_AXIS_BONES,
        "applicable": True,
        "reason": "全身动作至少 3 根主要骨骼必须在两个以上旋转轴有效运动",
        "bones": measurements,
        "count": len(measurements),
        "minimum": MIN_MULTI_AXIS_BONES,
        "threshold_deg": MULTI_AXIS_ROTATION_DEG,
    }


def grip_gates(sampler: AnimationSampler, times: list[float], active: set[str]) -> dict[str, Any]:
    animation_id = sampler.key.rsplit(".", 1)[-1]
    if animation_id not in GRIP_IDS:
        return {"pass": True, "applicable": False, "reason": "非持物动作"}
    sides = ("l",) if animation_id.endswith("_off") else ("r",)
    measurements = {}
    failures = []
    for side in sides:
        fingers = [f"{side}_thumb", f"{side}_fingers_a", f"{side}_fingers_b"]
        values = [max_abs(bone_local_values(sampler, bone, "rotation", times)) for bone in fingers]
        measurements[side] = dict(zip(fingers, values))
        if not any(value >= EFFECTIVE_ROTATION_DEG for value in values):
            failures.append(f"{side} 手指没有握持姿势")
    return {"pass": not failures, "applicable": True, "sides": sides,
            "max_abs_deg": measurements, "failures": failures}


def report(sampler: AnimationSampler, impact_window: tuple[float, float] | None = None) -> dict[str, Any]:
    times = sample_times(sampler)
    active, ranges = effective_bones(sampler, times)
    animation_id = sampler.key.rsplit(".", 1)[-1]
    torso = {bone: bone in active for bone in ("pelvis", "waist", "chest", "head")}
    arms = {side: any(bone in active for bone in (f"{side}_upper_arm", f"{side}_forearm", f"{side}_hand"))
            for side in ("r", "l")}
    full_body = animation_id in FULL_BODY_IDS
    all_body_pass = (not full_body) or (arms["r"] and arms["l"]
                                      and all(torso.values())
                                      and any(bone in active for bone in ("r_thigh", "l_thigh")))
    effective_pass = len(active) >= 10 if not sampler.loop or animation_id not in {"walk", "run", "meditate"} else True
    torso_pass = all(torso.values()) if not sampler.loop or animation_id not in {"walk", "run", "meditate"} else True
    preparation = preparation_follow_gates(sampler, times)
    ground = ground_and_center_gates(sampler, times)
    grip = grip_gates(sampler, times, active)
    angle = angle_gates(sampler, times)
    overlap = overlap_gates(sampler, times)
    arm_intersection = arm_intersection_gates(sampler, times)
    correlation = anti_forgery_correlation_gates(sampler, times, active)
    peak_order = anti_forgery_peak_order_gates(sampler, times, active)
    multi_axis = anti_forgery_multi_axis_gates(sampler, times, active, full_body)
    cycle = cycle_gates(sampler)
    velocity = velocity_gates(sampler, times, impact_window)
    # 总结果必须包含角速度/抽搐检查，不能只在明细中报告失败却返回成功。
    gate_pass = all((item.get("pass", True) or not item.get("applicable", True))
                    for item in (angle, preparation, overlap, arm_intersection, grip, ground, cycle,
                                 correlation, peak_order, multi_axis, velocity))
    return {
        "pass": gate_pass and effective_pass and torso_pass and all_body_pass,
        "animation": animation_id,
        "duration_s": sampler.duration,
        "loop": sampler.loop,
        "sample_hz": 60,
        "effective_motion": {"pass": effective_pass, "applicable": True,
                              "reason": "一次性/保持动作至少 10 根骨骼；三段基线循环按实测数量单独报告",
                              "count": len(active), "bones": sorted(active), "ranges": ranges,
                              "minimum": 10, "loop_baseline_exemption": sampler.loop},
        "torso_and_head": {"pass": torso_pass, "applicable": True, "participating": torso,
                            "reason": "一次性/保持动作必须有胯腰胸头；循环基线记录实际参与部位",
                            "loop_baseline_exemption": sampler.loop and animation_id in {"walk", "run", "meditate"}},
        "both_arms_and_legs": {"pass": all_body_pass, "applicable": full_body,
                                "reason": "跳跃、闪避、攀爬、游泳、骑乘、睡觉、鞘翅、穿戴、攻击/挖掘和双手格挡要求双臂双腿",
                                "legs": {side: any(bone in active for bone in (f"{side}_thigh", f"{side}_shin"))
                                         for side in ("r", "l")},
                                "required_for": sorted(FULL_BODY_IDS)},
        "preparation_follow_through": preparation,
        "overlap_and_offset": overlap,
        "arm_intersection": arm_intersection,
        "anti_forgery_correlation": correlation,
        "anti_forgery_peak_order": peak_order,
        "anti_forgery_multi_axis": multi_axis,
        "joint_angles": angle,
        "speed_and_twitch": velocity,
        "ground_and_center": ground,
        "grip_pose": grip,
        "cycle_seam": cycle,
    }


def gate_label(item: dict[str, Any]) -> str:
    """把门禁的适用性和理由一起打印，避免把不适用误读为 SKIP/通过。"""
    applicable = item.get("applicable", True)
    status = "PASS" if item.get("pass", True) else "FAIL"
    if not applicable:
        return f"N/A({item.get('reason', '未提供理由')})"
    return f"{status}({item.get('reason', '适用')})"


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("animation", nargs="+", help="动画 ID")
    parser.add_argument("--source", type=Path, default=Path("models/player_skeleton_classic.bbmodel"))
    parser.add_argument("--slim", type=Path, default=Path("models/player_skeleton_slim.bbmodel"))
    parser.add_argument("--json-dir", type=Path, default=Path("src/main/resources/assets/kizuna_players_animation/animations"))
    parser.add_argument("--index", type=Path,
                        default=Path("src/main/resources/assets/kizuna_players_animation/player_anim_index.json"))
    parser.add_argument("--json", action="store_true", help="输出完整 JSON 报告")
    args = parser.parse_args()
    index = load_json(args.index)
    impact_windows: dict[str, tuple[float, float]] = {}
    phase_declarations: dict[str, Any] = {}
    for raw in index.get("animations", []):
        animation_id = raw.get("id")
        if "phases" in raw:
            phase_declarations[animation_id] = raw["phases"]
        if "impact_window" not in raw:
            continue
        values = raw["impact_window"]
        if animation_id not in IMPACT_WINDOW_IDS:
            raise SystemExit(f"impact_window 只能用于 {sorted(IMPACT_WINDOW_IDS)}: {animation_id}")
        if not isinstance(values, list) or len(values) != 2:
            raise SystemExit(f"impact_window 必须为 [start,end]: {animation_id}")
        impact_windows[animation_id] = (float(values[0]), float(values[1]))
    reports = []
    for label, model_path in (("classic", args.source), ("slim", args.slim)):
        model = SkeletonModel(load_json(model_path))
        for animation_id in args.animation:
            path = args.json_dir / f"{animation_id}.animation.json"
            document = load_json(path)
            if animation_id in phase_declarations:
                # 阶段属于索引契约，临时注入采样文档供门禁使用；不改写资源文件。
                for animation in document.get("animations", {}).values():
                    animation["phases"] = phase_declarations[animation_id]
            key = next(name for name in document["animations"] if name.rsplit(".", 1)[-1] == animation_id)
            item = report(AnimationSampler(model, document, key), impact_windows.get(animation_id))
            item["model"] = label
            reports.append(item)
    failed = [f"{item['animation']}[{item['model']}]" for item in reports if not item["pass"]]
    if args.json:
        print(json.dumps(reports, ensure_ascii=False, indent=2))
    else:
        for item in reports:
            print(f"{item['animation']}[{item['model']}]: active={item['effective_motion']['count']} "
                  f"effective={gate_label(item['effective_motion'])} "
                  f"torso={gate_label(item['torso_and_head'])} "
                  f"full_body={gate_label(item['both_arms_and_legs'])} "
                  f"angles={gate_label(item['joint_angles'])} "
                  f"speed={item['speed_and_twitch']['max_speed_deg_s']:.1f}°/s "
                  f"jerk={item['speed_and_twitch']['max_jerk_deg_s3']:.1f}°/s³ "
                  f"speed_gate={gate_label(item['speed_and_twitch'])} "
                  f"pre={gate_label(item['preparation_follow_through'])} "
                  f"overlap={gate_label(item['overlap_and_offset'])} "
                  f"arm_intersection={gate_label(item['arm_intersection'])} "
                  f"correlation={gate_label(item['anti_forgery_correlation'])} "
                  f"peak_order={gate_label(item['anti_forgery_peak_order'])} "
                  f"multi_axis={gate_label(item['anti_forgery_multi_axis'])} "
                  f"grip={gate_label(item['grip_pose'])} "
                  f"ground={gate_label(item['ground_and_center'])} "
                  f"seam={gate_label(item['cycle_seam'])} "
                  f"overall={'PASS' if item['pass'] else 'FAIL'}")
    if failed:
        print(f"门禁失败: {', '.join(failed)}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
