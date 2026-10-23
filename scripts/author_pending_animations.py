#!/usr/bin/env python3
"""从动画规格表生成待交付的 Blockbench .bbmodel 动画。

规格表中的五个姿态是唯一的数值来源。本脚本只负责把它们映射到现有
经典骨架、生成可审查的关键帧，并为镜像副手动作交换左右骨骼。
"""

from __future__ import annotations

import argparse
import hashlib
import json
import math
import re
import uuid
from pathlib import Path
from typing import Any


PREFIX = "animation.kizuna_players_animation.player."
NAMESPACE = uuid.UUID("55a8c5f1-6d96-4ee8-a9bf-25d5764a3906")

LENGTHS: dict[str, float] = {}
MIRRORS: dict[str, str] = {}

SPEC_IDS = tuple(LENGTHS)

# 为传力链保留 60 Hz 下至少一帧的错峰。它只移动中间姿态的时间，
# 首尾仍然使用表中姿态，因而一次性动作的起止位置不变。
ROLE_OFFSETS = {
    "waist": -0.030,
    "chest": -0.015,
    "upper_arm": 0.000,
    "forearm": 0.018,
    "hand": 0.036,
    "head": 0.012,
    "pelvis": -0.024,
    "thigh": -0.012,
    "shin": 0.012,
    "foot": 0.030,
    "toe": 0.042,
}

# 同一份规格表可以给多个骨骼提供阶段目标，但不能让它们共享一条
# 按比例复制的曲线。下面的相位、形状和副轴都是确定性的局部修正，
# 首尾仍保持规格表姿态，便于重复生成和审阅。
CURVE_SHAPE = {
    "pelvis": (0.00, 0.11, -0.07, 0.08, 0.00),
    "waist": (0.00, -0.08, 0.12, -0.05, 0.00),
    "chest": (0.00, 0.06, -0.11, 0.09, 0.00),
    "head": (0.00, -0.12, 0.07, -0.09, 0.00),
    "upper_arm": (0.00, 0.10, -0.04, -0.12, 0.00),
    "forearm": (0.00, -0.06, 0.13, -0.08, 0.00),
    "hand": (0.00, 0.08, -0.14, 0.06, 0.00),
    "thumb": (0.00, -0.10, 0.16, -0.07, 0.00),
    "fingers_a": (0.00, 0.14, -0.09, 0.11, 0.00),
    "fingers_b": (0.00, -0.16, 0.08, -0.13, 0.00),
    "thigh": (0.00, 0.07, -0.12, 0.05, 0.00),
    "shin": (0.00, -0.13, 0.09, -0.06, 0.00),
    "foot": (0.00, 0.12, -0.06, 0.10, 0.00),
    "toe": (0.00, -0.08, 0.12, -0.05, 0.00),
}

FULL_BODY_IDS: set[str] = set()

FAST_IDS: set[str] = set()

GRIP_IDS: set[str] = set()

SECONDARY_AXIS: dict[str, tuple[str, float]] = {}

SECONDARY_BONES = {
    "pelvis": 0.90,
    "waist": 1.00,
    "chest": 0.82,
    "head": 0.55,
    "r_upper_arm": 0.70,
    "l_upper_arm": -0.62,
    "r_thigh": 0.48,
    "l_thigh": -0.44,
}

ARM_SCALE: dict[str, dict[str, float]] = {}

ARM_POSITION_SCALE: dict[str, dict[str, float]] = {}

BODY_SCALE: dict[str, dict[str, float]] = {}

LEG_SCALE: dict[str, float] = {}

# 中间姿态不再只是规格值的比例缩放。每根骨骼按动作和名称确定一个小轮廓，
# 首尾仍完全服从规格表，既保留动作意图，也避免多根骨骼共享一条时间曲线。
CURVE_VARIANTS = (
    (0.36, -0.22, 0.28),
    (-0.30, 0.26, -0.18),
    (0.24, 0.34, -0.32),
    (-0.38, -0.16, 0.30),
    (0.18, -0.36, -0.24),
    (-0.22, 0.18, 0.40),
    (0.42, 0.12, -0.28),
    (-0.16, -0.30, 0.22),
)

CURVE_VARIANT_STRENGTH: dict[str, float] = {}

# 短时动作的规格角度保留原有相对关系，但整体幅度略收，给 60 Hz
# 采样留下可见的起落时间，避免闪避和放弦出现瞬时甩动。
ACTION_SCALE: dict[str, float] = {}

# 副轴必须形成真实的侧倾变化，而不是同向缓慢抬升；首尾回到零，保证动作边界不偏移。
SECONDARY_PATTERNS = (
    (0.0, 1.0, -0.82, 0.68, 0.0),
    (0.0, -0.86, 0.94, -0.62, 0.0),
    (0.0, 0.72, 0.98, -0.88, 0.0),
    (0.0, -0.96, -0.66, 0.84, 0.0),
)

PART_RE = re.compile(r"(?P<value>[+\-−]?\d+(?:\.\d+)?)\s*°\s*(?P<axis>[XYZ])")
TIME_RE = re.compile(r"t\s*=\s*([+\-]?\d+(?:\.\d+)?)\s*s")


def stable_uuid(value: str) -> str:
    return str(uuid.uuid5(NAMESPACE, value))


def clean_number(value: float) -> str:
    if abs(value) < 0.0000005:
        return "0"
    return f"{value:.6f}".rstrip("0").rstrip(".")


def parse_spec(path: Path) -> tuple[list[float], dict[str, list[tuple[float, str]]]]:
    text = path.read_text(encoding="utf-8")
    section_start = text.find("## 参与部位")
    if section_start < 0:
        raise ValueError(f"规格缺少参与部位表: {path}")
    section = text[section_start:]
    next_heading = section.find("\n## ", 3)
    if next_heading >= 0:
        section = section[:next_heading]
    lines = [line.strip() for line in section.splitlines() if line.strip().startswith("|")]
    if not lines:
        raise ValueError(f"规格缺少姿态表: {path}")

    header = [cell.strip() for cell in lines[0].strip("|").split("|")]
    times = [float(match.group(1)) for match in (TIME_RE.search(cell) for cell in header[1:]) if match]
    if len(times) < 2:
        raise ValueError(f"规格表时间列不足: {path}")

    rows: dict[str, list[tuple[float, str]]] = {}
    for line in lines[2:]:
        cells = [cell.strip() for cell in line.strip("|").split("|")]
        if len(cells) != len(times) + 1:
            continue
        label = cells[0]
        values: list[tuple[float, str]] = []
        for cell in cells[1:]:
            match = PART_RE.search(cell.replace("−", "-"))
            if match:
                values.append((float(match.group("value")), match.group("axis")))
            else:
                values.append((0.0, "X"))
        rows[label] = values
    required = {"头", "胸", "腰", "胯", "主臂-上臂", "主臂-前臂", "主臂-手"}
    missing = required - rows.keys()
    if missing:
        raise ValueError(f"规格 {path} 缺少部位: {', '.join(sorted(missing))}")
    return times, rows


def mirror_bone(bone: str) -> str:
    if bone.startswith("r_"):
        return "l_" + bone[2:]
    if bone.startswith("l_"):
        return "r_" + bone[2:]
    return bone


def mirror_value(value: float, axis: str) -> float:
    # AnimationPlayer 的镜像规则保持 X，反转 Y/Z；规格值仍是面板局部角度。
    return -value if axis in ("Y", "Z") else value


def role_offset(bone: str) -> float:
    for role, offset in ROLE_OFFSETS.items():
        if bone == role or bone.endswith("_" + role):
            return offset
    return 0.0


def bone_role(bone: str) -> str:
    for role in CURVE_SHAPE:
        if bone == role or bone.endswith("_" + role):
            return role
    return "chest"


def curve_values(animation_id: str, bone: str, values: list[float]) -> list[float]:
    """给每根骨骼生成独立的中间阶段曲线，保留规格首尾姿态。"""
    if len(values) <= 2:
        return values
    role = bone_role(bone)
    shape = CURVE_SHAPE[role]
    scale = ARM_SCALE.get(animation_id, {}).get(bone, 1.0)
    scale *= BODY_SCALE.get(animation_id, {}).get(bone, 1.0)
    if bone.endswith(("thigh", "shin", "foot")):
        scale *= LEG_SCALE.get(animation_id, 1.0)
    # 左右侧使用相反的微扰，双臂不会被判定为镜像复制。
    side_sign = -1.0 if bone.startswith("l_") else 1.0
    variant_index = int.from_bytes(
        hashlib.sha1(f"{animation_id}:{bone}".encode("utf-8")).digest()[:2], "big"
    ) % len(CURVE_VARIANTS)
    variant = CURVE_VARIANTS[variant_index]
    variant_strength = CURVE_VARIANT_STRENGTH.get(animation_id, 0.0)
    action_scale = ACTION_SCALE.get(animation_id, 1.0)
    amplitude = max((abs(value) for value in values), default=0.0) * scale
    result = []
    last = len(values) - 1
    for index, value in enumerate(values):
        if index == 0 or index == last:
            result.append(value * scale * action_scale)
            continue
        weight = shape[index % len(shape)]
        phase = math.sin((index + 1) * 1.37 + len(bone) * 0.41 + len(animation_id) * 0.17)
        variant_offset = variant[index - 1] * variant_strength * amplitude
        result.append((value * scale * (1.0 + weight + side_sign * 0.035 * phase)
                       + variant_offset) * action_scale)
    return result


def rotation_matrix(rotation: tuple[float, float, float]) -> tuple[float, ...]:
    """返回与分析器一致的 ZYX 欧拉旋转矩阵。"""
    ex, ey, ez = (math.radians(value) for value in rotation)
    sx, cx = math.sin(ex), math.cos(ex)
    sy, cy = math.sin(ey), math.cos(ey)
    sz, cz = math.sin(ez), math.cos(ez)
    return (
        cz * cy, cz * sy * sx - sz * cx, cz * sy * cx + sz * sx,
        sz * cy, sz * sy * sx + cz * cx, sz * sy * cx - cz * sx,
        -sy, cy * sx, cy * cx,
    )


def inverse_rotate(matrix: tuple[float, ...], vector: tuple[float, float, float]) -> tuple[float, float, float]:
    """用正交矩阵转置把目标位移变回父骨骼局部空间。"""
    return (
        matrix[0] * vector[0] + matrix[3] * vector[1] + matrix[6] * vector[2],
        matrix[1] * vector[0] + matrix[4] * vector[1] + matrix[7] * vector[2],
        matrix[2] * vector[0] + matrix[5] * vector[1] + matrix[8] * vector[2],
    )


def matrix_multiply(left: tuple[float, ...], right: tuple[float, ...]) -> tuple[float, ...]:
    return tuple(sum(left[row * 3 + index] * right[index * 3 + column] for index in range(3))
                 for row in range(3) for column in range(3))


def matrix_vector(matrix: tuple[float, ...], vector: tuple[float, float, float]) -> tuple[float, float, float]:
    return (
        matrix[0] * vector[0] + matrix[1] * vector[1] + matrix[2] * vector[2],
        matrix[3] * vector[0] + matrix[4] * vector[1] + matrix[5] * vector[2],
        matrix[6] * vector[0] + matrix[7] * vector[1] + matrix[8] * vector[2],
    )


def compose_transform(parent_matrix: tuple[float, ...], parent_offset: tuple[float, float, float],
                      local_matrix: tuple[float, ...], local_offset: tuple[float, float, float]) \
        -> tuple[tuple[float, ...], tuple[float, float, float]]:
    transformed = matrix_vector(parent_matrix, local_offset)
    return (matrix_multiply(parent_matrix, local_matrix),
            tuple(parent_offset[index] + transformed[index] for index in range(3)))


MODEL_ORIGINS = {
    "pelvis": (0.0, 12.0, 0.0),
    "r_thigh": (-1.9, 12.0, 0.0),
    "l_thigh": (1.9, 12.0, 0.0),
    "r_shin": (-1.9, 6.0, 0.0),
    "l_shin": (1.9, 6.0, 0.0),
    "r_foot": (-1.9, 1.0, 0.0),
    "l_foot": (1.9, 1.0, 0.0),
}


def arm_position_values(animation_id: str, bone: str, count: int) -> list[tuple[float, float, float]]:
    amplitude = ARM_POSITION_SCALE.get(animation_id, {}).get(bone)
    if amplitude is None:
        return [(0.0, 0.0, 0.0)] * count
    # 外移在中段达到峰值，避免关键帧边界处突然出现手臂间隙。
    envelope = (0.0, 0.72, 1.0, 0.78, 0.0)
    side = -1.0 if bone.startswith("r_") else 1.0
    return [(side * amplitude * envelope[index % len(envelope)], 0.0, 0.0)
            for index in range(count)]


def leg_root_compensation(animation_id: str, bone: str, rows: dict[str, list[tuple[float, str]]],
                          count: int, mirror: bool) -> list[tuple[float, float, float]]:
    """抵消胯部侧倾对大腿根的平移，保持站立动作的脚底接触。"""
    if not bone.endswith("thigh") or animation_id not in BODY_SCALE:
        return [(0.0, 0.0, 0.0)] * count
    pelvis_values, pelvis_axis = bone_values(rows, "pelvis")
    pelvis_values = curve_values(animation_id, "pelvis", pelvis_values)
    if mirror:
        pelvis_values = [mirror_value(value, pelvis_axis) for value in pelvis_values]
    candidate = SECONDARY_AXIS.get(animation_id, ("X", 0.0))[0]
    if candidate == pelvis_axis:
        candidate = next(value for value in "XYZ" if value != pelvis_axis)
    secondary = secondary_values(animation_id, "pelvis", pelvis_values, candidate)
    main_axis = "XYZ".index(pelvis_axis)
    secondary_axis = "XYZ".index(candidate)
    x = -1.9 if bone.startswith("r_") else 1.9
    result = []
    for index in range(count):
        rotation = [0.0, 0.0, 0.0]
        rotation[main_axis] = pelvis_values[index]
        rotation[secondary_axis] = secondary[index]
        matrix = rotation_matrix(tuple(rotation))
        rotated_back = inverse_rotate(matrix, (x, 0.0, 0.0))
        result.append((rotated_back[0] - x, rotated_back[1], rotated_back[2]))
    return result


def secondary_values(animation_id: str, bone: str, values: list[float], axis: str) -> list[float]:
    if animation_id not in SECONDARY_AXIS or bone not in SECONDARY_BONES:
        return [0.0] * len(values)
    amplitude = SECONDARY_AXIS[animation_id][1] * SECONDARY_BONES[bone]
    variant_index = int.from_bytes(
        hashlib.sha1(f"secondary:{animation_id}:{bone}:{axis}".encode("utf-8")).digest()[:2], "big"
    ) % len(SECONDARY_PATTERNS)
    pattern = SECONDARY_PATTERNS[variant_index]
    if len(values) == len(pattern):
        return [amplitude * value for value in pattern]
    # 规格表目前固定为五个阶段；这里保留对其他合法阶段数的线性采样能力。
    return [amplitude * pattern[round(index * (len(pattern) - 1) / max(1, len(values) - 1))]
            for index in range(len(values))]


def rotation_vectors(animation_id: str, bone: str, rows: dict[str, list[tuple[float, str]]],
                     count: int, mirror: bool) -> list[tuple[float, float, float]]:
    """返回与实际动画轨道一致的局部旋转，供脚底补偿复用。"""
    source_bone = mirror_bone(bone) if mirror else bone
    side = source_bone[0] if source_bone[:2] in {"r_", "l_"} else None
    values, axis = bone_values(rows, source_bone, side)
    axis_values = grip_values(animation_id, bone, curve_values(animation_id, bone, values))
    if mirror:
        axis_values = [mirror_value(value, axis) for value in axis_values]
    secondary_axis = None
    if animation_id in SECONDARY_AXIS:
        candidate = SECONDARY_AXIS[animation_id][0]
        secondary_axis = candidate if candidate != axis else next(value for value in "XYZ" if value != axis)
    secondary = secondary_values(animation_id, bone, axis_values, secondary_axis or "X")
    result = []
    for value, secondary_value in zip(axis_values, secondary):
        vector = [0.0, 0.0, 0.0]
        vector["XYZ".index(axis)] = value
        if secondary_axis is not None:
            vector["XYZ".index(secondary_axis)] = secondary_value
        result.append(tuple(vector))
    return result


def resample_rotation_vectors(source_times: list[float], source_values: list[tuple[float, float, float]],
                               target_times: list[float]) -> list[tuple[float, float, float]]:
    """把规格表姿态线性重采样到错峰时间轴，确保首尾姿态落在真实边界。"""
    if len(source_times) != len(source_values):
        raise ValueError("旋转姿态与规格时间数量不一致")
    if len(source_times) < 2:
        return source_values[:]
    result = []
    for target in target_times:
        if target <= source_times[0]:
            result.append(source_values[0])
            continue
        if target >= source_times[-1]:
            result.append(source_values[-1])
            continue
        segment = next(index for index in range(1, len(source_times)) if target <= source_times[index])
        start, end = source_times[segment - 1], source_times[segment]
        amount = (target - start) / (end - start)
        result.append(tuple(source_values[segment - 1][axis]
                            + (source_values[segment][axis] - source_values[segment - 1][axis]) * amount
                            for axis in range(3)))
    return result


def leg_foot_lock_positions(animation_id: str, side: str, rows: dict[str, list[tuple[float, str]]],
                            count: int, mirror: bool, thigh_positions: list[tuple[float, float, float]]) \
        -> list[tuple[float, float, float]]:
    """把脚掌枢轴反解回父骨骼局部空间，抵消躯干/腿部旋转造成的脚底漂移。"""
    thigh = f"{side}_thigh"
    shin = f"{side}_shin"
    foot = f"{side}_foot"
    rotations = {bone: rotation_vectors(animation_id, bone, rows, count, mirror)
                 for bone in ("pelvis", thigh, shin, foot)}
    identity = (1.0, 0.0, 0.0,
                0.0, 1.0, 0.0,
                0.0, 0.0, 1.0)
    target = MODEL_ORIGINS[foot]
    result = []
    for index in range(count):
        parent_matrix, parent_offset = identity, (0.0, 0.0, 0.0)
        for bone in ("pelvis", thigh, shin):
            origin = MODEL_ORIGINS[bone]
            position = thigh_positions[index] if bone == thigh else (0.0, 0.0, 0.0)
            local_matrix = rotation_matrix(rotations[bone][index])
            local_offset = tuple(origin[axis] + position[axis]
                                 - matrix_vector(local_matrix, origin)[axis] for axis in range(3))
            parent_matrix, parent_offset = compose_transform(parent_matrix, parent_offset,
                                                              local_matrix, local_offset)
        local_target = inverse_rotate(parent_matrix,
                                      tuple(target[axis] - parent_offset[axis] for axis in range(3)))
        origin = MODEL_ORIGINS[foot]
        foot_rotation = rotation_matrix(rotations[foot][index])
        rotated_origin = matrix_vector(foot_rotation, origin)
        result.append(tuple(local_target[axis] - origin[axis] + rotated_origin[axis]
                            for axis in range(3)))
    return result


def smooth_rotation_samples(times: list[float], vectors: list[tuple[float, float, float]],
                            duration: float) -> tuple[list[float], list[tuple[float, float, float]]]:
    """用 60 Hz 的 smoothstep 采样替代短段线性折角，降低一次性动作末段抽搐。"""
    if len(times) < 2:
        return times, vectors
    samples = sorted(set([0.0, duration, *times, *[round(index / 60, 6)
                                                     for index in range(1, math.ceil(duration * 60))]]))
    output = []
    for time in samples:
        if time <= times[0]:
            output.append(vectors[0])
            continue
        if time >= times[-1]:
            output.append(vectors[-1])
            continue
        segment = next(index for index in range(1, len(times)) if time <= times[index])
        start, end = times[segment - 1], times[segment]
        amount = (time - start) / (end - start)
        # 五次 smoothstep 让分段首尾的速度和加速度都回到零，避免
        # 60 Hz 采样在动作换向处出现可见抽搐。
        eased = amount ** 3 * (amount * (amount * 6.0 - 15.0) + 10.0)
        output.append(tuple(vectors[segment - 1][axis]
                            + (vectors[segment][axis] - vectors[segment - 1][axis]) * eased
                            for axis in range(3)))
    return samples, output


def grip_values(animation_id: str, bone: str, values: list[float]) -> list[float]:
    """让握持动作的至少一节手指达到可见幅度，避免只动手腕。"""
    if animation_id not in GRIP_IDS or not bone.endswith(("thumb", "fingers_a", "fingers_b")):
        return values
    current = max((abs(value) for value in values), default=0.0)
    if current < 0.001:
        return values
    target = 3.4
    if current < target:
        factor = target / current
        return [value * factor for value in values]
    return values


def bone_uuids(outliner: list[dict[str, Any]]) -> dict[str, str]:
    result: dict[str, str] = {}

    def visit(nodes: list[Any]) -> None:
        for node in nodes:
            if not isinstance(node, dict):
                continue
            name = node.get("name")
            bone_uuid = node.get("uuid")
            if isinstance(name, str) and isinstance(bone_uuid, str):
                if name in result:
                    raise ValueError(f"模型骨骼名称重复: {name}")
                result[name] = bone_uuid
            visit(node.get("children", []))

    visit(outliner)
    return result


def bone_values(rows: dict[str, list[tuple[float, str]]], bone: str,
                side: str | None = None) -> tuple[list[float], str]:
    if bone in {"head", "chest", "waist", "pelvis"}:
        label = {"head": "头", "chest": "胸", "waist": "腰", "pelvis": "胯"}[bone]
        return [value for value, _ in rows[label]], rows[label][0][1]
    if bone.endswith(("upper_arm", "forearm", "hand")):
        part = next(part for part in ("upper_arm", "forearm", "hand") if bone.endswith(part))
        label = "主臂-" + {"upper_arm": "上臂", "forearm": "前臂", "hand": "手"}[part]
        if side == "l":
            label = "副臂-" + label.split("-", 1)[1]
        return [value for value, _ in rows[label]], rows[label][0][1]
    if bone.endswith(("thumb", "fingers_a", "fingers_b")):
        if side == "l":
            label = "副臂-手指"
        else:
            label = "主臂-手指"
        values, axis = [value for value, _ in rows[label]], rows[label][0][1]
        # 指骨需要有可见握持差异，沿用规格的手指职责并让三节逐级收拢。
        part = next(part for part in ("thumb", "fingers_a", "fingers_b") if bone.endswith(part))
        scale = {"thumb": 1.0, "fingers_a": 1.25, "fingers_b": 1.45}[part]
        return [value * scale for value in values], axis
    if bone.endswith("thigh"):
        return [value for value, _ in rows["双腿-大腿"]], rows["双腿-大腿"][0][1]
    if bone.endswith("shin"):
        return [value for value, _ in rows["双腿-小腿"]], rows["双腿-小腿"][0][1]
    if bone.endswith("foot"):
        return [value for value, _ in rows["双腿-脚"]], rows["双腿-脚"][0][1]
    if bone.endswith("toe"):
        return [value for value, _ in rows["双腿-脚趾"]], rows["双腿-脚趾"][0][1]
    raise KeyError(bone)


def keyframe(animation_id: str, bone: str, channel: str, time: float,
             vector: tuple[float, float, float], index: int, interpolation: str) -> dict[str, Any]:
    return {
        "channel": channel,
        "data_points": [{axis: clean_number(value) for axis, value in zip("xyz", vector)}],
        "uuid": stable_uuid(f"{animation_id}:{bone}:{channel}:{index}"),
        "time": round(time, 6),
        "color": -1,
        "interpolation": interpolation,
    }


def stage_times(animation_id: str, times: list[float], duration: float, bone: str, loop: bool) -> list[float]:
    role = bone_role(bone)
    offset = role_offset(bone)
    if loop:
        # 循环曲线首尾必须相同；只错开中间的相位，避免接缝速度跳变。
        return [0.0, *[max(0.001, min(duration - 0.001, time + offset)) for time in times[1:-1]], duration]
    shifted = [0.0]
    for time in times[1:-1]:
        candidate = time + offset
        # 末段需要保留完整的回收时间；把角色错峰挤到动作末尾会制造
        # 瞬时折返，尤其在弓弦释放和闪避落脚时最明显。
        if duration - candidate < 0.10:
            candidate = time
        shifted.append(max(0.001, min(duration - 0.001, candidate)))
    shifted.append(duration)
    return shifted


def animator_for(animation_id: str, rows: dict[str, list[tuple[float, str]]], times: list[float],
                 duration: float, bone: str, mirror: bool, loop: bool) -> dict[str, Any]:
    source_bone = mirror_bone(bone) if mirror else bone
    _, axis = bone_values(rows, source_bone,
                          source_bone[0] if source_bone[:2] in {"r_", "l_"} else None)
    rotations = rotation_vectors(animation_id, bone, rows, len(times), mirror)
    key_times = stage_times(animation_id, times, duration, bone, loop)
    if len(key_times) != len(rotations):
        rotations = resample_rotation_vectors(times, rotations, key_times)
    rotation_times, rotation_values = key_times, rotations
    interpolation = "linear" if animation_id in FAST_IDS else "catmullrom"
    frames = []
    for index, (time, vector) in enumerate(zip(rotation_times, rotation_values)):
        frames.append(keyframe(animation_id, bone, "rotation", time, vector, index, interpolation))
    position_frames = []
    positions = arm_position_values(animation_id, bone, len(key_times))
    leg_positions = leg_root_compensation(animation_id, bone, rows, len(key_times), mirror)
    foot_positions = [(0.0, 0.0, 0.0)] * len(key_times)
    if (bone.endswith("foot") and bone.startswith(("r_", "l_"))
            and animation_id in BODY_SCALE):
        side = bone[0]
        thigh_positions = leg_root_compensation(animation_id, f"{side}_thigh", rows,
                                                 len(key_times), mirror)
        foot_positions = leg_foot_lock_positions(animation_id, side, rows, len(key_times),
                                                 mirror, thigh_positions)
    for index, time in enumerate(key_times):
        position = tuple(positions[index][axis] + leg_positions[index][axis] + foot_positions[index][axis]
                         for axis in range(3))
        if any(abs(value) > 0.000001 for value in position):
            position_frames.append(keyframe(animation_id, bone, "position", time, position, index, interpolation))
    return {"name": bone, "type": "bone", "keyframes": frames + position_frames}


def animation_for(animation_id: str, rows: dict[str, list[tuple[float, str]]],
                  times: list[float], duration: float, bones: list[str],
                  bone_uuid_by_name: dict[str, str], mirror: bool) -> dict[str, Any]:
    loop = False
    animators = {}
    for bone in bones:
        animator = animator_for(animation_id, rows, times, duration, bone, mirror, loop)
        try:
            animator_uuid = bone_uuid_by_name[bone]
        except KeyError as exception:
            raise ValueError(f"模型缺少动画骨骼: {bone}") from exception
        animators[animator_uuid] = animator
    phases = [
        {"id": f"stage_{index + 1}", "start": start, "end": end}
        for index, (start, end) in enumerate(zip(times, times[1:]))
    ]
    return {
        "uuid": stable_uuid(f"animation:{animation_id}"),
        "name": PREFIX + animation_id,
        "loop": "loop" if loop else "once",
        "override": False,
        "length": duration,
        "snapping": 24,
        "selected": False,
        "anim_time_update": "",
        "blend_weight": 1,
        "start_delay": 0,
        "loop_delay": 0,
        "phases": phases,
        "animators": animators,
    }


def all_bones() -> list[str]:
    return [
        "pelvis", "waist", "chest", "head",
        "r_upper_arm", "r_forearm", "r_hand", "r_thumb", "r_fingers_a", "r_fingers_b",
        "l_upper_arm", "l_forearm", "l_hand", "l_thumb", "l_fingers_a", "l_fingers_b",
        "r_thigh", "r_shin", "r_foot", "l_thigh", "l_shin", "l_foot",
    ]


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--source", type=Path, default=Path("models/player_skeleton_classic.bbmodel"))
    parser.add_argument("--spec-dir", type=Path, default=Path("docs/animation-specs"))
    args = parser.parse_args()
    document = json.loads(args.source.read_text(encoding="utf-8"))
    bone_uuid_by_name = bone_uuids(document.get("outliner", []))
    existing = {animation["name"] for animation in document.get("animations", [])}
    authored: dict[str, dict[str, Any]] = {}
    for animation_id in SPEC_IDS:
        path = args.spec_dir / f"{animation_id}.md"
        times, rows = parse_spec(path)
        authored[animation_id] = animation_for(animation_id, rows, times,
                                               LENGTHS[animation_id], all_bones(),
                                               bone_uuid_by_name, False)
    for animation_id, source_id in MIRRORS.items():
        path = args.spec_dir / f"{source_id}.md"
        times, rows = parse_spec(path)
        authored[animation_id] = animation_for(animation_id, rows, times,
                                               LENGTHS[source_id], all_bones(),
                                               bone_uuid_by_name, True)
    authored_names = {animation["name"] for animation in authored.values()}
    # 目标动画由规格表唯一生成；重复运行时替换同名轨道，其他既有资源原样保留。
    document["animations"] = [animation for animation in document.get("animations", [])
                               if animation.get("name") not in authored_names]
    document["animations"].extend(authored.values())
    args.source.write_text(json.dumps(document, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"写入 {len(authored)} 段动画到 {args.source}（同名目标已替换）")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
