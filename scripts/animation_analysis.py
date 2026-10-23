#!/usr/bin/env python3
"""玩家骨架动画的采样、正向运动学和门禁基础库。

坐标采用 .bbmodel 的像素空间：Y 向上、前方 -Z。JSON 采样时恢复
Blockbench 导出器的 X/Y 反号，避免分析工具把 Bedrock 数值当成面板数值。
"""

from __future__ import annotations

import base64
import json
import math
from dataclasses import dataclass
from pathlib import Path
from typing import Any


Vec = tuple[float, float, float]


def add(a: Vec, b: Vec) -> Vec:
    return (a[0] + b[0], a[1] + b[1], a[2] + b[2])


def sub(a: Vec, b: Vec) -> Vec:
    return (a[0] - b[0], a[1] - b[1], a[2] - b[2])


def scale(a: Vec, n: float) -> Vec:
    return (a[0] * n, a[1] * n, a[2] * n)


def lerp(a: Vec, b: Vec, amount: float) -> Vec:
    return add(a, scale(sub(b, a), amount))


def catmull(p0: Vec, p1: Vec, p2: Vec, p3: Vec, amount: float) -> Vec:
    t2 = amount * amount
    t3 = t2 * amount
    return tuple(0.5 * ((2 * p1[i]) + (-p0[i] + p2[i]) * amount
                        + (2 * p0[i] - 5 * p1[i] + 4 * p2[i] - p3[i]) * t2
                        + (-p0[i] + 3 * p1[i] - 3 * p2[i] + p3[i]) * t3)
                 for i in range(3))


def rotate_zyx(point: Vec, rotation: Vec) -> Vec:
    """Blockbench 的 ZYX 欧拉顺序：先 X，再 Y，最后 Z。"""
    x, y, z = (math.radians(value) for value in rotation)
    sx, cx = math.sin(x), math.cos(x)
    sy, cy = math.sin(y), math.cos(y)
    sz, cz = math.sin(z), math.cos(z)
    # Rz * Ry * Rx
    return (
        (cz * cy) * point[0] + (cz * sy * sx - sz * cx) * point[1] + (cz * sy * cx + sz * sx) * point[2],
        (sz * cy) * point[0] + (sz * sy * sx + cz * cx) * point[1] + (sz * sy * cx - cz * sx) * point[2],
        (-sy) * point[0] + (cy * sx) * point[1] + (cy * cx) * point[2],
    )


@dataclass(frozen=True)
class Transform:
    # 3x3 行主序旋转矩阵与平移。
    matrix: tuple[float, ...] = (1, 0, 0, 0, 1, 0, 0, 0, 1)
    offset: Vec = (0, 0, 0)

    def point(self, value: Vec) -> Vec:
        m = self.matrix
        return (
            m[0] * value[0] + m[1] * value[1] + m[2] * value[2] + self.offset[0],
            m[3] * value[0] + m[4] * value[1] + m[5] * value[2] + self.offset[1],
            m[6] * value[0] + m[7] * value[1] + m[8] * value[2] + self.offset[2],
        )

    def compose(self, local_matrix: tuple[float, ...], local_offset: Vec) -> "Transform":
        a, b = self.matrix, local_matrix
        matrix = tuple(sum(a[row * 3 + k] * b[k * 3 + col] for k in range(3))
                       for row in range(3) for col in range(3))
        return Transform(matrix, add(self.point(local_offset), (0, 0, 0)))


def rotation_matrix(rotation: Vec) -> tuple[float, ...]:
    ex, ey, ez = (math.radians(value) for value in rotation)
    sx, cx = math.sin(ex), math.cos(ex)
    sy, cy = math.sin(ey), math.cos(ey)
    sz, cz = math.sin(ez), math.cos(ez)
    return (
        cz * cy, cz * sy * sx - sz * cx, cz * sy * cx + sz * sx,
        sz * cy, sz * sy * sx + cz * cx, sz * sy * cx - cz * sx,
        -sy, cy * sx, cy * cx,
    )


def transform_about(transform: Transform, pivot: Vec, position: Vec, rotation: Vec) -> Transform:
    # p' = parent(pivot + position + R(p-pivot))，其中 pivot 为模型空间骨骼枢轴。
    r = rotation_matrix(rotation)
    local_offset = sub(add(pivot, position), mat_point(r, pivot))
    return compose(transform, r, local_offset)


def mat_point(matrix: tuple[float, ...], value: Vec) -> Vec:
    return (
        matrix[0] * value[0] + matrix[1] * value[1] + matrix[2] * value[2],
        matrix[3] * value[0] + matrix[4] * value[1] + matrix[5] * value[2],
        matrix[6] * value[0] + matrix[7] * value[1] + matrix[8] * value[2],
    )


def compose(parent: Transform, local_matrix: tuple[float, ...], local_offset: Vec) -> Transform:
    a, b = parent.matrix, local_matrix
    matrix = tuple(sum(a[row * 3 + k] * b[k * 3 + col] for k in range(3))
                   for row in range(3) for col in range(3))
    return Transform(matrix, parent.point(local_offset))


def vec_from_json(value: Any, channel: str) -> Vec:
    if isinstance(value, dict):
        value = value.get("post", value.get("pre"))
    if not isinstance(value, list) or len(value) != 3:
        raise ValueError(f"JSON {channel} 关键帧不是三元向量: {value!r}")
    values = tuple(float(number) for number in value)
    if channel == "rotation":
        return (-values[0], -values[1], values[2])
    return (-values[0], values[1], values[2])


def raw_frame_value(value: Any, channel: str) -> Vec:
    return vec_from_json(value, channel)


class AnimationSampler:
    def __init__(self, model: "SkeletonModel", document: dict[str, Any], animation_key: str):
        self.model = model
        self.animation = document["animations"][animation_key]
        self.key = animation_key
        self.duration = float(self.animation["animation_length"])
        self.loop = self.animation.get("loop", False) is True
        self.hold = self.animation.get("loop") == "hold_on_last_frame"
        self.tracks = self.animation.get("bones", {})

    def _frames(self, bone: str, channel: str) -> list[tuple[float, Vec, str]]:
        raw = self.tracks.get(bone, {}).get(channel)
        if raw is None:
            return [(0.0, (0.0, 0.0, 0.0), "linear")]
        if isinstance(raw, list):
            return [(0.0, raw_frame_value(raw, channel), "linear")]
        frames = []
        for time, value in raw.items():
            mode = value.get("lerp_mode", "linear") if isinstance(value, dict) else "linear"
            frames.append((float(time), raw_frame_value(value, channel), mode))
        return sorted(frames)

    def value(self, bone: str, channel: str, time: float, wrap: bool = True) -> Vec:
        frames = self._frames(bone, channel)
        if self.duration <= 0 or len(frames) == 1:
            return frames[0][1]
        if wrap and self.loop:
            time = time % self.duration
        if time <= frames[0][0]:
            return frames[0][1]
        if time >= frames[-1][0]:
            return frames[-1][1]
        for index in range(1, len(frames)):
            right = frames[index]
            if time <= right[0]:
                left = frames[index - 1]
                amount = (time - left[0]) / (right[0] - left[0])
                if right[2] == "catmullrom":
                    p0 = frames[index - 2][1] if index >= 2 else (frames[-1][1] if self.loop else left[1])
                    p3 = frames[index + 1][1] if index + 1 < len(frames) else (frames[0][1] if self.loop else right[1])
                    return catmull(p0, left[1], right[1], p3, amount)
                if right[2] == "step":
                    return left[1]
                return lerp(left[1], right[1], amount)
        return frames[-1][1]

    def local_pose(self, time: float, wrap: bool = True) -> dict[str, dict[str, Vec]]:
        return {bone: {channel: self.value(bone, channel, time, wrap)
                       for channel in ("position", "rotation")}
                for bone in self.model.bones}

    def forward_kinematics(self, time: float, wrap: bool = True) -> tuple[dict[str, Transform], dict[str, dict[str, Vec]]]:
        local = self.local_pose(time, wrap)
        world: dict[str, Transform] = {}

        def visit(bone: str, parent: Transform) -> None:
            values = local[bone]
            world[bone] = transform_about(parent, self.model.origins[bone], values["position"], values["rotation"])
            for child in self.model.children.get(bone, ()):
                visit(child, world[bone])

        identity = Transform()
        for root in self.model.roots:
            visit(root, identity)
        return world, local


class SkeletonModel:
    def __init__(self, source: dict[str, Any]):
        self.source = source
        self.bones: list[str] = []
        self.parent: dict[str, str | None] = {}
        self.children: dict[str, list[str]] = {}
        self.origins: dict[str, Vec] = {}
        self.elements: list[dict[str, Any]] = []
        self.element_bones: dict[str, str] = {}
        self._read_outliner(source.get("outliner", []), None)
        self._read_elements(source.get("elements", []))
        self.roots = [bone for bone in self.bones if self.parent[bone] is None]

    def _read_outliner(self, items: list[Any], parent: str | None) -> None:
        for item in items:
            if isinstance(item, str):
                continue
            bone = item["name"]
            self.bones.append(bone)
            self.parent[bone] = parent
            self.children.setdefault(bone, [])
            self.origins[bone] = tuple(float(v) for v in item.get("origin", (0, 0, 0)))
            if parent is not None:
                self.children.setdefault(parent, []).append(bone)
            self._read_outliner(item.get("children", []), bone)

    def _read_elements(self, elements: list[dict[str, Any]]) -> None:
        by_uuid = {element.get("uuid"): element for element in elements}
        def visit(items: list[Any], bone: str) -> None:
            for item in items:
                if isinstance(item, str):
                    if item in by_uuid:
                        self.element_bones[item] = bone
                else:
                    visit(item.get("children", []), item["name"])
        for root in self.source.get("outliner", []):
            if isinstance(root, dict):
                visit(root.get("children", []), root["name"])
        self.elements = elements

    def texture(self) -> Any:
        from PIL import Image
        for texture in self.source.get("textures", []):
            data = texture.get("source", "")
            if data.startswith("data:image/"):
                return Image.open(__import__("io").BytesIO(base64.b64decode(data.split(",", 1)[1]))).convert("RGBA")
        return None


def load_json(path: Path) -> dict[str, Any]:
    return json.loads(path.read_text(encoding="utf-8"))


def find_animation(document: dict[str, Any], animation_id: str) -> str:
    prefix = "animation.kizuna_players_animation.player."
    direct = prefix + animation_id
    if direct in document.get("animations", {}):
        return direct
    matches = [name for name in document.get("animations", {}) if name.rsplit(".", 1)[-1] == animation_id]
    if len(matches) != 1:
        raise ValueError(f"找不到唯一动画 {animation_id}: {matches}")
    return matches[0]


def source_animation_document(source: dict[str, Any], animation_id: str) -> tuple[dict[str, Any], str]:
    prefix = "animation.kizuna_players_animation.player."
    animations = {animation["name"]: animation for animation in source.get("animations", [])}
    key = prefix + animation_id
    if key not in animations:
        raise ValueError(f".bbmodel 没有动画 {key}")
    # 复用导出器，保证分析/渲染看到的正是打包 Bedrock 数据。
    import importlib.util
    module_path = Path(__file__).with_name("export_bbmodel_animation.py")
    spec = importlib.util.spec_from_file_location("kpa_export", module_path)
    module = importlib.util.module_from_spec(spec)
    assert spec and spec.loader
    spec.loader.exec_module(module)
    return module.export_animation(source, animations[key]), key


def sample_times(sampler: AnimationSampler, hz: int = 60, include_end: bool = True) -> list[float]:
    count = max(1, math.ceil(sampler.duration * hz))
    times = [i / hz for i in range(count)]
    if include_end:
        times.append(sampler.duration)
    return times


def animation_key_from_file(path: Path, animation_id: str) -> str:
    return find_animation(load_json(path), animation_id)

