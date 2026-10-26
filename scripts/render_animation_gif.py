#!/usr/bin/env python3
"""直接从 .bbmodel 动画源渲染三视角 GIF 与接触表。

渲染器只用于离线审图，不替代 Minecraft 的蒙皮渲染。它使用 .bbmodel
内嵌的 Steve 贴图、同一套骨架正向运动学，并在每帧标出地面线和重心。
离线图按刚性 cube 绘制；父子分段切口按游戏内 `SkinnedMesh` 的规则补 1 像素过渡带
（切口顶点随父骨、向子段内部 1 像素过渡到子骨），屈膝、屈肘时凸侧不再露出楔形缝。
过渡带只补凸侧缺口，不模拟整条关节带的蒙皮形变。

Blockbench 空间到游戏是 Y 反射（`Rig.toVanilla`），游戏里看到的是 Blockbench
视口的镜像；投影时把 X 取反，GIF 的左右与游戏一致（`r_*` 在正视图位于观察者左侧）。
手持道具、穿戴盔甲和跳跃轨迹只是预览叠加，定义在本文件的预览表里，不进入动画数据。
"""

from __future__ import annotations

import argparse
import math
import sys
from pathlib import Path
from typing import Any

from PIL import Image, ImageDraw

sys.path.insert(0, str(Path(__file__).resolve().parent))

from animation_analysis import AnimationSampler, SkeletonModel, load_json, source_animation_document


PANEL_WIDTH = 320
MIN_PANEL_HEIGHT = 360
GROUND_MARGIN = 40
SCALE = 9.0
# 模型的正面朝 -Z；yaw=180 才会把 Steve 的脸朝向观察者。35° 是从正面
# 向侧面偏移的角度，因此四分之三视角使用 145°，避免把后脑勺误标成正面。
VIEWS = (("front", 180.0), ("side", 90.0), ("three_quarter", 145.0))
BACKGROUND = (242, 244, 247, 255)
GROUND = (125, 132, 142, 255)
CENTER = (193, 55, 67, 255)
OUTLINE = (35, 38, 45, 255)
# 一次性/保持动作的 GIF 首尾各停留这么久，便于看清起止姿态。
HOLD_SECONDS = 0.5
# 剪辑不长于该时长时额外输出慢放 GIF。
SLOW_MAX_DURATION = 1.6
SLOW_FACTOR = 0.25

# ---- 预览表：只服务审图，数值来源写在对应设计稿里 ----

# 接触表的 10 个关键时刻（含首尾），取自设计稿的阶段边界；未列出的动作均匀取样。
KEY_TIMES = {
    "attack_main": (0.0, 0.06, 0.10, 0.14, 0.175, 0.21, 0.25, 0.30, 0.40, 0.50),
    "jump": (0.0, 0.06, 0.12, 0.20, 0.30, 0.45, 0.56, 0.60, 0.72, 0.90),
    "equip_chestplate": (0.0, 0.14, 0.32, 0.46, 0.60, 0.76, 0.92, 1.08, 1.25, 1.50),
    "equip_helmet": (0.0, 0.12, 0.28, 0.39, 0.50, 0.67, 0.82, 0.94, 1.05, 1.28),
    "equip_leggings": (0.0, 0.14, 0.34, 0.58, 0.74, 0.92, 1.34, 1.88, 2.02, 2.18),
    "equip_boots": (0.0, 0.30, 0.58, 0.74, 0.88, 1.06, 1.46, 1.62, 2.10, 2.24),
    "mine_main": (0.0, 0.03, 0.06, 0.11, 0.145, 0.20, 0.25, 0.30, 0.40, 0.50),
    "place_block": (0.0, 0.04, 0.07, 0.10, 0.13, 0.16, 0.20, 0.30, 0.40, 0.50),
    "spyglass": (0.0, 0.06, 0.12, 0.20, 0.26, 0.30, 0.36, 0.85, 1.35, 2.35),
    "crossbow_hold": (0.0, 0.05, 0.10, 0.15, 0.20, 0.24, 0.28, 0.32, 0.36, 0.40),
    "crossbow_charge": (0.0, 0.10, 0.22, 0.28, 0.45, 0.66, 0.88, 1.08, 1.12, 1.25),
    "toot_horn": (0.0, 0.06, 0.12, 0.18, 0.24, 0.33, 0.60, 1.20, 1.80, 2.60),
    "brush": (0.0, 0.06, 0.12, 0.18, 0.24, 0.30, 0.425, 0.55, 0.675, 0.80),
    "use_item": (0.0, 0.05, 0.10, 0.15, 0.20, 0.24, 0.28, 0.32, 0.36, 0.40),
}
# 闪避 / 轻步：下蹲、蹬伸离地、收腿、迎地、先后触地、缓冲、回架势各取一帧，接触表能看清腿部动作链。
for _direction in ("forward", "back", "left", "right"):
    KEY_TIMES[f"dodge_{_direction}"] = (0.0, 0.15, 0.22, 0.27, 0.32, 0.38, 0.42, 0.46, 0.55, 0.70)
    KEY_TIMES[f"technique_dash_{_direction}"] = (0.0, 0.06, 0.11, 0.14, 0.25, 0.35, 0.40, 0.45, 0.55, 0.78)

# 手持与穿戴道具。剑刃方向由原版 handheld 第三人称 display 推算（见 conventions.md），
# 胸甲在手中显示到“胸甲上身”时刻，之后改画穿在身上的盔甲层。
PROPS = {
    "attack_main": ({"kind": "sword", "bone": "r_item"},),
    "mine_main": ({"kind": "pickaxe", "bone": "r_item"},),
    "place_block": ({"kind": "block", "bone": "r_item"},),
    # 下列道具的端点由原版物品模型的 thirdperson_righthand display 经游戏内挂点变换
    # （Rig.itemTransform：rotX(−90°)·rotY(180°)，再做 toVanilla 的 Y 反射）换算到 r_item 局部像素。
    "spyglass": ({"kind": "segments", "bone": "r_item", "parts": (
        ((0.0, 0.0, -3.5), (0.0, 0.0, 2.0), 0.9, 0.9, "copper"),
        ((0.0, 0.0, 2.0), (0.0, 0.0, 7.6), 1.1, 1.1, "copper"))},),
    "toot_horn": ({"kind": "segments", "bone": "r_item", "normal": (0.0, 0.0, 1.0), "parts": (
        ((2.72, 4.46, -1.75), (1.3, 2.4, -2.4), 0.5, 0.5, "horn"),
        ((1.3, 2.4, -2.4), (-0.15, 0.36, -3.0), 1.2, 1.0, "horn"))},),
    "crossbow_charge": ({"kind": "segments", "bone": "r_item", "normal": (0.0, 0.0, 1.0), "parts": (
        ((0.14, 4.99, -0.1), (-2.99, -6.69, -0.1), 0.8, 0.5, "handle"),
        ((-9.87, -2.87, -0.1), (4.04, -6.27, -0.1), 0.6, 0.5, "iron_dark"))},),
    "brush": ({"kind": "segments", "bone": "r_item", "normal": (1.0, 0.0, 0.0), "parts": (
        ((0.32, 0.0, 3.32), (0.1, 0.0, -4.5), 0.5, 0.4, "handle"),
        ((0.1, 0.0, -4.5), (0.0, 0.0, -10.36), 1.6, 0.5, "copper"))},),
    "use_item": ({"kind": "segments", "bone": "r_item", "normal": (0.0, 1.0, 0.0), "parts": (
        ((0.0, 1.0, 1.4), (0.0, 1.0, -7.4), 4.4, 0.3, "dirt"),)},),
    "equip_chestplate": ({"kind": "plate_item", "bone": "r_item", "until": 0.30},
                         {"kind": "plate_two_hand", "from": 0.30, "until": 0.80, "size": (4.5, 0.6)},
                         {"kind": "worn", "from": 0.80, "bones": ("pelvis", "waist", "chest", "r_upper_arm", "l_upper_arm")}),
    "equip_helmet": ({"kind": "plate_item", "bone": "r_item", "until": 0.28},
                     {"kind": "plate_two_hand", "from": 0.28, "until": 0.68, "size": (3.5, 3.5)},
                     {"kind": "worn", "from": 0.68, "bones": ("head",), "open_front": True}),
    "equip_leggings": ({"kind": "plate_item", "bone": "r_item", "until": 0.34},
                       {"kind": "plate_two_hand", "from": 0.34, "until": 2.02, "size": (1.5, 1.0)},
                       {"kind": "worn", "from": 2.02, "bones": ("pelvis", "r_thigh", "l_thigh", "r_shin", "l_shin")}),
    "equip_boots": ({"kind": "plate_item", "bone": "r_item", "until": 0.30},
                    {"kind": "plate_two_hand", "from": 0.30, "until": 1.62, "size": (2.0, 2.0)},
                    {"kind": "worn", "from": 0.74, "bones": ("r_shin", "r_foot", "r_toe")},
                    {"kind": "worn", "from": 1.62, "bones": ("l_shin", "l_foot", "l_toe")}),
}

PROPS["crossbow_hold"] = PROPS["crossbow_charge"]

# 原版平地起跳的实体高度（格，每 tick），只用于让 GIF 看得出离地。
VANILLA_JUMP_Y = (0, 0.42, 0.7532, 1.0013, 1.166, 1.249, 1.252, 1.1766, 1.024, 0.796, 0.4943, 0.12, 0.0)


def jump_lift_px(time: float) -> float:
    if time >= 0.60:
        return 0.0
    ticks = time * 20
    index = int(ticks)
    amount = ticks - index
    low = VANILLA_JUMP_Y[index]
    high = VANILLA_JUMP_Y[min(index + 1, len(VANILLA_JUMP_Y) - 1)]
    return (low + (high - low) * amount) * 16


ROOT_PREVIEW = {"jump": jump_lift_px}

# 原版驱动预览：按调度规则把剪辑时间拼成一段（起点, 终点, 重复次数），以慢放输出。
# mine：连续挖掘时原版每 4 tick 重开挥动，剪辑在 0–0.20 s 回绕；停手后再播一次完整挥动与收势。
DRIVE_PREVIEW = {
    "mine_main": ((0.0, 0.20, 3), (0.0, 0.50, 1)),
    "place_block": ((0.0, 0.20, 3), (0.0, 0.50, 1)),
    # brush：按已使用时长播放，0.30 s 后在循环段 [0.30, 0.80] 内回绕（一个往返 = 原版 10 tick 刷动周期）。
    "brush": ((0.0, 0.30, 1), (0.30, 0.80, 3)),
}

SWORD_POMMEL = (0.0, 2.2, 5.5)
SWORD_TIP = (0.0, -1.2, -13.5)
IRON = (206, 208, 212, 255)
IRON_DARK = (150, 153, 158, 255)
HANDLE = (110, 74, 42, 255)
GUARD = (80, 82, 88, 255)
DIRT = (134, 96, 67, 255)
COPPER = (196, 120, 70, 255)
PROP_COLORS = {"copper": COPPER, "handle": HANDLE, "iron_dark": IRON_DARK, "dirt": DIRT, "horn": (214, 200, 170, 255)}


def projection(point: tuple[float, float, float], yaw: float, center: tuple[float, float], scale: float) -> tuple[float, float, float]:
    # X 取反：把 Blockbench 空间镜像成游戏里看到的左右。
    x, y, z = -point[0], point[1], point[2]
    radians = math.radians(yaw)
    cy, sy = math.cos(radians), math.sin(radians)
    vx = x * cy - z * sy
    depth = x * sy + z * cy
    return center[0] + vx * scale, center[1] - y * scale, depth


def face_vertices(bounds: tuple[float, float, float, float, float, float], face: str) -> list[tuple[float, float, float]]:
    x0, y0, z0, x1, y1, z1 = bounds
    corners = {
        "000": (x0, y0, z0), "001": (x0, y0, z1), "010": (x0, y1, z0), "011": (x0, y1, z1),
        "100": (x1, y0, z0), "101": (x1, y0, z1), "110": (x1, y1, z0), "111": (x1, y1, z1),
    }
    names = {
        "north": ("010", "110", "100", "000"),
        "south": ("011", "001", "101", "111"),
        "east": ("110", "111", "101", "100"),
        "west": ("011", "010", "000", "001"),
        "up": ("010", "011", "111", "110"),
        "down": ("000", "100", "101", "001"),
    }
    return [corners[name] for name in names[face]]


def textured_polygon(image: Image.Image, texture: Image.Image | None, polygon: list[tuple[float, float]], uv: list[float] | None) -> None:
    if texture is None or uv is None or len(uv) != 4:
        return
    left, top, right, bottom = (int(round(value)) for value in uv)
    left, right = sorted((max(0, left), min(texture.width, right)))
    top, bottom = sorted((max(0, top), min(texture.height, bottom)))
    if right <= left or bottom <= top:
        return
    patch = texture.crop((left, top, right, bottom))
    min_x = max(0, int(math.floor(min(point[0] for point in polygon))))
    min_y = max(0, int(math.floor(min(point[1] for point in polygon))))
    max_x = min(image.width, int(math.ceil(max(point[0] for point in polygon))) + 1)
    max_y = min(image.height, int(math.ceil(max(point[1] for point in polygon))) + 1)
    if max_x <= min_x or max_y <= min_y:
        return
    patch = patch.resize((max_x - min_x, max_y - min_y), Image.Resampling.NEAREST)
    mask = Image.new("L", patch.size, 0)
    ImageDraw.Draw(mask).polygon([(x - min_x, y - min_y) for x, y in polygon], fill=255)
    # 贴图的透明像素不能把背景涂黑；同时保留多边形裁剪。
    from PIL import ImageChops
    mask = ImageChops.multiply(mask, patch.getchannel("A"))
    image.paste(patch, (min_x, min_y), mask)


def box_corners(center: tuple[float, float, float], axes: tuple[tuple[float, float, float], ...],
                half: tuple[float, float, float]) -> dict[str, tuple[float, float, float]]:
    """道具盒的 8 个角点（模型零姿态坐标），键与 face_vertices 的角点命名一致。"""
    corners = {}
    for sx in (0, 1):
        for sy in (0, 1):
            for sz in (0, 1):
                signs = (2 * sx - 1, 2 * sy - 1, 2 * sz - 1)
                corners[f"{sx}{sy}{sz}"] = tuple(
                    center[i] + sum(signs[k] * half[k] * axes[k][i] for k in range(3)) for i in range(3))
    return corners


BOX_FACES = {
    "north": ("010", "110", "100", "000"), "south": ("011", "001", "101", "111"),
    "east": ("110", "111", "101", "100"), "west": ("011", "010", "000", "001"),
    "up": ("010", "011", "111", "110"), "down": ("000", "100", "101", "001"),
}


def unit(vector: tuple[float, float, float]) -> tuple[float, float, float]:
    length = math.sqrt(sum(value * value for value in vector))
    return tuple(value / length for value in vector)


def prop_boxes(model: SkeletonModel, prop: dict[str, Any]) -> list[tuple[str, dict[str, tuple[float, float, float]], tuple[int, ...]]]:
    """把道具拆成挂在骨骼上的若干盒子：(骨名, 零姿态角点, 颜色)。"""
    kind = prop["kind"]
    if kind == "sword":
        pivot = model.origins[prop["bone"]]
        pommel = tuple(pivot[i] + SWORD_POMMEL[i] for i in range(3))
        tip = tuple(pivot[i] + SWORD_TIP[i] for i in range(3))
        along = unit(tuple(tip[i] - pommel[i] for i in range(3)))
        # 剑身是 handheld 贴图平面，位于手的 YZ 平面内；护手沿平面内垂直剑身的方向。
        across = unit((0.0, -along[2], along[1]))
        side = (1.0, 0.0, 0.0)
        length = math.dist(pommel, tip)
        point = lambda s: tuple(pommel[i] + along[i] * length * s for i in range(3))
        axes = (along, across, side)
        return [
            (prop["bone"], box_corners(point(0.14), axes, (length * 0.14, 0.55, 0.45)), HANDLE),
            (prop["bone"], box_corners(point(0.30), axes, (0.5, 2.2, 0.6)), GUARD),
            (prop["bone"], box_corners(point(0.65), axes, (length * 0.35, 0.8, 0.25)), IRON),
        ]
    if kind == "pickaxe":
        # 镐与剑同为 handheld 贴图：柄沿贴图对角线，镐头在柄顶端横跨对角线。
        pivot = model.origins[prop["bone"]]
        pommel = tuple(pivot[i] + SWORD_POMMEL[i] for i in range(3))
        tip = tuple(pivot[i] + SWORD_TIP[i] for i in range(3))
        along = unit(tuple(tip[i] - pommel[i] for i in range(3)))
        across = unit((0.0, -along[2], along[1]))
        side = (1.0, 0.0, 0.0)
        length = math.dist(pommel, tip)
        point = lambda s: tuple(pommel[i] + along[i] * length * s for i in range(3))
        axes = (along, across, side)
        return [
            (prop["bone"], box_corners(point(0.45), axes, (length * 0.45, 0.55, 0.45)), HANDLE),
            (prop["bone"], box_corners(point(0.86), axes, (0.9, length * 0.36, 0.5)), IRON),
            (prop["bone"], box_corners(point(0.86), axes, (1.0, 1.1, 0.55)), IRON_DARK),
        ]
    if kind == "block":
        # 手持方块按原版第三人称 0.375 倍缩放，预览画成约 5 px 立方体，中心在掌前。
        pivot = model.origins[prop["bone"]]
        center = (pivot[0], pivot[1] + 0.5, pivot[2] - 2.5)
        axes = ((1.0, 0.0, 0.0), (0.0, 1.0, 0.0), (0.0, 0.0, 1.0))
        return [(prop["bone"], box_corners(center, axes, (2.4, 2.4, 2.4)), DIRT)]
    if kind == "segments":
        # 物品按若干段长方体近似：每段从 a 到 b（挂点局部像素），宽度方向与 normal 和段方向都垂直。
        pivot = model.origins[prop["bone"]]
        normal = prop.get("normal", (1.0, 0.0, 0.0))
        boxes = []
        for a, b, width, thickness, color in prop["parts"]:
            along = unit(tuple(b[i] - a[i] for i in range(3)))
            across = (along[1] * normal[2] - along[2] * normal[1], along[2] * normal[0] - along[0] * normal[2],
                      along[0] * normal[1] - along[1] * normal[0])
            if math.sqrt(sum(c * c for c in across)) < 1e-6:
                across = (0.0, 1.0, 0.0) if abs(along[1]) < 0.9 else (1.0, 0.0, 0.0)
            across = unit(across)
            side = unit((along[1] * across[2] - along[2] * across[1], along[2] * across[0] - along[0] * across[2],
                         along[0] * across[1] - along[1] * across[0]))
            center = tuple(pivot[i] + (a[i] + b[i]) / 2 for i in range(3))
            boxes.append((prop["bone"], box_corners(center, (along, across, side), (math.dist(a, b) / 2, width, thickness)),
                          PROP_COLORS[color]))
        return boxes
    if kind == "plate_item":
        pivot = model.origins[prop["bone"]]
        center = (pivot[0], pivot[1] + 1.0, pivot[2] - 3.5)
        axes = ((1.0, 0.0, 0.0), (0.0, 1.0, 0.0), (0.0, 0.0, 1.0))
        return [(prop["bone"], box_corners(center, axes, (0.5, 4.0, 4.0)), IRON)]
    if kind == "worn":
        boxes = []
        axes = ((1.0, 0.0, 0.0), (0.0, 1.0, 0.0), (0.0, 0.0, 1.0))
        for element in model.elements:
            bone = model.element_bones.get(element.get("uuid"))
            name = element.get("name", "")
            if not name.endswith("_inner") or bone not in prop["bones"]:
                continue
            low = [float(value) for value in element["from"]]
            high = [float(value) for value in element["to"]]
            center = tuple((low[i] + high[i]) / 2 for i in range(3))
            half = tuple((high[i] - low[i]) / 2 + 1.0 for i in range(3))
            boxes.append((bone, box_corners(center, axes, half), IRON))
        return boxes
    raise ValueError(f"未知预览道具: {kind}")


def two_hand_plate(model: SkeletonModel, world: dict[str, Any], size: tuple[float, float]) -> list[tuple[list, tuple[int, ...]]]:
    """双手捧着的胸甲：以两个挂点的中点为中心，宽度跨过两手，竖直方向取胸骨的上方。"""
    right = world["r_item"].point(model.origins["r_item"])
    left = world["l_item"].point(model.origins["l_item"])
    width_axis = unit(tuple(right[i] - left[i] for i in range(3)))
    chest_up = world["chest"].matrix[1], world["chest"].matrix[4], world["chest"].matrix[7]
    dot = sum(chest_up[i] * width_axis[i] for i in range(3))
    up_axis = unit(tuple(chest_up[i] - dot * width_axis[i] for i in range(3)))
    normal = (width_axis[1] * up_axis[2] - width_axis[2] * up_axis[1],
              width_axis[2] * up_axis[0] - width_axis[0] * up_axis[2],
              width_axis[0] * up_axis[1] - width_axis[1] * up_axis[0])
    center = tuple((right[i] + left[i]) / 2 + up_axis[i] * 1.5 for i in range(3))
    half_width = math.dist(right, left) / 2 + 1.0
    return [(box_corners(center, (width_axis, up_axis, normal), (half_width, size[0], size[1])), IRON)]


def mirrored_side(name: str) -> str:
    if name.startswith("r_"):
        return "l_" + name[2:]
    if name.startswith("l_"):
        return "r_" + name[2:]
    return name


def main_hand_id(animation_id: str) -> str | None:
    """副手动作 ID 对应的主手动作：`attack_off` → `attack_main`，`equip_helmet_off` → `equip_helmet`。"""
    if not animation_id.endswith("_off"):
        return None
    base = animation_id.removesuffix("_off")
    return base + "_main" if base + "_main" in PROPS or base + "_main" in KEY_TIMES else base


def preview_props(animation_id: str) -> tuple[dict[str, Any], ...]:
    """副手动作（*_off）由主手镜像而来，预览道具也把挂点与左右骨互换。"""
    main_id = main_hand_id(animation_id)
    if animation_id in PROPS or main_id is None:
        return PROPS.get(animation_id, ())
    props = []
    for prop in PROPS.get(main_id, ()):
        swapped = dict(prop)
        if "bone" in swapped:
            swapped["bone"] = mirrored_side(swapped["bone"])
        if "parts" in swapped:
            # 左手物品是右手 display 的镜像：挂点局部 X 取反。
            flip = lambda v: (-v[0], v[1], v[2])
            swapped["parts"] = tuple((flip(a), flip(b), w, t, c) for a, b, w, t, c in swapped["parts"])
        if "bones" in swapped:
            swapped["bones"] = tuple(mirrored_side(bone) for bone in swapped["bones"])
        props.append(swapped)
    return tuple(props)


def active_props(model: SkeletonModel, animation_id: str, time: float, world: dict[str, Any]) -> list[tuple[dict, tuple[int, ...]]]:
    """返回当前时刻所有道具盒的世界坐标角点；open_front 的盔甲（头盔）不画正面，露出脸。"""
    boxes = []
    for prop in preview_props(animation_id):
        if time < prop.get("from", -math.inf) or time >= prop.get("until", math.inf):
            continue
        if prop["kind"] == "plate_two_hand":
            boxes.extend(two_hand_plate(model, world, prop["size"]))
            continue
        for bone, corners, color in prop_boxes(model, prop):
            world_corners = {name: world[bone].point(point) for name, point in corners.items()}
            if prop.get("open_front"):
                world_corners["skip"] = ("north",)
            boxes.append((world_corners, color))
    return boxes


_SEAM_CACHE: dict[int, list[tuple[dict[str, Any], str, int, float]]] = {}


def skin_part(bone: str) -> str:
    """与 `BoneId.skinPart` 一致：胯腰胸是身体，头单独，每条手臂、每条腿各一个原版部件。"""
    if bone in ("pelvis", "waist", "chest"):
        return "body"
    if bone == "head":
        return "head"
    side = bone[:2]
    leg = any(bone.endswith(part) for part in ("thigh", "shin", "foot", "toe"))
    return side + ("leg" if leg else "arm")


def seam_cuts(model: SkeletonModel) -> list[tuple[dict[str, Any], str, int, float]]:
    """父子分段的切口：子段 cube 的某个面与父骨 cube 的反向面共面且有正面积交叠。

    返回 (子 cube, 父骨, 切口轴, 切口坐标)。游戏内 `SkinnedMesh` 让切口顶点继承父段权重，
    向子段内部 1 像素过渡到子骨；离线刚性 cube 在屈膝、屈肘时会在凸侧露出楔形缝，
    这里用同样的规则补一条 1 像素的过渡带。"""
    key = id(model)
    if key in _SEAM_CACHE:
        return _SEAM_CACHE[key]
    cuts = []
    for element in model.elements:
        bone = model.element_bones.get(element.get("uuid"))
        parent = model.parent.get(bone) if bone else None
        # 只连接同一原版皮肤部件的分段（与 SkinnedMesh 相同），头、肩、胯保留原版部件边界。
        if parent is None or element.get("type") != "cube" or skin_part(parent) != skin_part(bone):
            continue
        lo, hi = element["from"], element["to"]
        for other in model.elements:
            if model.element_bones.get(other.get("uuid")) != parent or other.get("type") != "cube" \
                    or float(other.get("inflate", 0)) != float(element.get("inflate", 0)):
                continue
            plo, phi = other["from"], other["to"]
            for axis in range(3):
                for value, parent_value in ((lo[axis], phi[axis]), (hi[axis], plo[axis])):
                    if abs(float(value) - float(parent_value)) > 1e-6:
                        continue
                    rest = [a for a in range(3) if a != axis]
                    if all(min(hi[a], phi[a]) - max(lo[a], plo[a]) > 1e-6 for a in rest):
                        cuts.append((element, parent, axis, float(value)))
    _SEAM_CACHE[key] = cuts
    return cuts


def seam_band_faces(model: SkeletonModel, world: dict[str, Any], lifted, yaw: float, center: tuple[float, float],
                    scale: float) -> list[tuple[float, list[tuple[float, float]], list[float] | None, tuple[int, ...] | None]]:
    bands = []
    for element, parent, axis, value in seam_cuts(model):
        bone = model.element_bones[element["uuid"]]
        x0, y0, z0 = (float(v) for v in element["from"])
        x1, y1, z1 = (float(v) for v in element["to"])
        bounds = (x0, y0, z0, x1, y1, z1)
        for name, face in element.get("faces", {}).items():
            if face.get("texture") is None or name not in BOX_FACES:
                continue
            vertices = face_vertices(bounds, name)
            on_cut = [v for v in vertices if abs(v[axis] - value) < 1e-6]
            if len(on_cut) != 2:
                continue  # 切口面本身或与切口平行的面
            inward = 1.0 if value == bounds[axis] else -1.0
            inner = [tuple(v[i] + (inward if i == axis else 0.0) for i in range(3)) for v in on_cut]
            points = [lifted(world[parent].point(on_cut[0])), lifted(world[parent].point(on_cut[1])),
                      lifted(world[bone].point(inner[1])), lifted(world[bone].point(inner[0]))]
            projected = [projection(point, yaw, center, scale) for point in points]
            depth = sum(point[2] for point in projected) / len(projected)
            uv = face.get("uv")
            if uv and len(uv) == 4 and axis == 1:
                # 竖直侧面的贴图上沿对应高 y：取紧贴切口的一行像素。
                top, bottom = sorted((uv[1], uv[3]))
                row = (top, top + 1) if value == bounds[4] else (bottom - 1, bottom)
                uv = [uv[0], row[0], uv[2], row[1]]
            bands.append((depth, [(p[0], p[1]) for p in projected], uv, "band"))
    return bands


def element_faces(model: SkeletonModel, world: dict[str, Any], yaw: float, center: tuple[float, float], scale: float,
                  image: Image.Image, texture: Image.Image | None, props: list, lift: float) -> None:
    faces: list[tuple[float, list[tuple[float, float]], list[float] | None, tuple[int, ...] | None]] = []
    lifted = lambda point: (point[0], point[1] + lift, point[2])
    faces.extend(seam_band_faces(model, world, lifted, yaw, center, scale))
    for element in model.elements:
        bone = model.element_bones.get(element.get("uuid"))
        if bone not in world or element.get("type") != "cube":
            continue
        x0, y0, z0 = (float(value) for value in element["from"])
        x1, y1, z1 = (float(value) for value in element["to"])
        bounds = (x0, y0, z0, x1, y1, z1)
        transform = world[bone]
        for name, face in element.get("faces", {}).items():
            if face.get("texture") is None or name not in BOX_FACES:
                continue
            points = [lifted(transform.point(point)) for point in face_vertices(bounds, name)]
            projected = [projection(point, yaw, center, scale) for point in points]
            # 深度小的面先画，保证近处的面覆盖远处的面。
            depth = sum(point[2] for point in projected) / len(projected)
            faces.append((depth, [(point[0], point[1]) for point in projected], face.get("uv"), None))
    for corners, color in props:
        for face_name, names in BOX_FACES.items():
            if face_name in corners.get("skip", ()):
                continue
            points = [lifted(corners[name]) for name in names]
            projected = [projection(point, yaw, center, scale) for point in points]
            # 道具略微前移半像素深度，贴合皮肤时不被同深度的皮肤面盖住。
            depth = sum(point[2] for point in projected) / len(projected) + 0.05
            faces.append((depth, [(point[0], point[1]) for point in projected], None, color))
    draw = ImageDraw.Draw(image)
    for _, polygon, uv, color in sorted(faces, key=lambda item: item[0]):
        if color == "band":
            textured_polygon(image, texture, polygon, uv)  # 过渡带不描边，否则会画出一条假接缝
        elif color is None:
            textured_polygon(image, texture, polygon, uv)
            draw.line(polygon + [polygon[0]], fill=OUTLINE, width=1, joint="curve")
        else:
            draw.polygon(polygon, fill=color, outline=IRON_DARK if color == IRON else OUTLINE)


def panel_height(model: SkeletonModel, sampler: AnimationSampler, animation_id: str, times: list[float]) -> int:
    """按全部取样帧的最高点决定画布高度，同一段动画的帧尺寸一致、缩放不变。"""
    lift = ROOT_PREVIEW.get(animation_id, lambda time: 0.0)
    top = 32.0
    for time in times:
        world, _ = sampler.forward_kinematics(time, wrap=False)
        for element in model.elements:
            bone = model.element_bones.get(element.get("uuid"))
            if bone not in world:
                continue
            for y in (float(element["from"][1]), float(element["to"][1])):
                for x in (float(element["from"][0]), float(element["to"][0])):
                    for z in (float(element["from"][2]), float(element["to"][2])):
                        top = max(top, world[bone].point((x, y, z))[1] + lift(time))
    return max(MIN_PANEL_HEIGHT, int(math.ceil(GROUND_MARGIN + (top + 4) * SCALE)))


def render_frame(model: SkeletonModel, sampler: AnimationSampler, texture: Image.Image | None, animation_id: str,
                 time: float, height: int, label: str) -> Image.Image:
    frame = Image.new("RGBA", (PANEL_WIDTH * len(VIEWS), height), BACKGROUND)
    world, _ = sampler.forward_kinematics(time, wrap=False)
    lift = ROOT_PREVIEW.get(animation_id, lambda value: 0.0)(time)
    props = active_props(model, animation_id, time, world)
    pivots = [world[bone].point(model.origins[bone]) for bone in model.bones if bone not in ("r_item", "l_item")]
    center_of_mass = tuple(sum(point[i] for point in pivots) / len(pivots) for i in range(3))
    center_of_mass = (center_of_mass[0], center_of_mass[1] + lift, center_of_mass[2])
    for index, (name, yaw) in enumerate(VIEWS):
        panel = frame.crop((index * PANEL_WIDTH, 0, (index + 1) * PANEL_WIDTH, height))
        # 地面固定在画布底部上方 GROUND_MARGIN；跳跃预览只抬人物、不抬地面，
        # 抬脚、穿地和滑步都按真实高度显示，不逐帧自动平移。
        panel_center = (PANEL_WIDTH / 2, height - GROUND_MARGIN)
        draw = ImageDraw.Draw(panel)
        ground_y = panel_center[1]
        draw.line((20, ground_y, PANEL_WIDTH - 20, ground_y), fill=GROUND, width=2)
        element_faces(model, world, yaw, panel_center, SCALE, panel, texture, props, lift)
        marker = projection(center_of_mass, yaw, panel_center, SCALE)
        draw.ellipse((marker[0] - 3, marker[1] - 3, marker[0] + 3, marker[1] + 3), fill=CENTER)
        draw.text((10, 12), f"{name}  {label}", fill=OUTLINE)
        frame.paste(panel, (index * PANEL_WIDTH, 0))
    return frame.convert("P", palette=Image.Palette.ADAPTIVE, colors=256)


def save_gif(frames: list[Image.Image], path: Path, frame_ms: int) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    frames[0].save(path, save_all=True, append_images=frames[1:], duration=frame_ms, loop=0, optimize=False)


def verify_front_orientation(model: SkeletonModel) -> None:
    """用内嵌 Steve 贴图的头部 north(-Z) 面确认 front 真的看到脸。"""
    name, yaw = VIEWS[0]
    if name != "front" or abs(yaw - 180.0) > 0.001:
        raise ValueError("front 视角必须使用 yaw=180° 才能观察模型 -Z 正面")
    head_elements = [element for element in model.elements
                     if model.element_bones.get(element.get("uuid")) == "head"
                     and element.get("name") == "head_inner"]
    if not head_elements:
        raise ValueError("找不到 head_inner，无法执行 Steve 脸部方向自检")
    north = head_elements[0].get("faces", {}).get("north", {})
    uv = north.get("uv", [])
    if len(uv) != 4 or tuple(sorted((uv[0], uv[2]))) != (8.0, 16.0) \
            or tuple(sorted((uv[1], uv[3]))) != (8.0, 16.0):
        raise ValueError(f"head_inner north 面不是 Steve 脸部 UV 8..16: {uv}")
    # 镜像自检：右上臂枢轴（Blockbench -X）在正视图必须落在观察者左侧。
    pivot = model.origins["r_upper_arm"]
    if projection(pivot, yaw, (0.0, 0.0), 1.0)[0] >= 0:
        raise ValueError("正视图中 r_upper_arm 没有位于观察者左侧，左右镜像错误")


def contact_sheet(frames: list[Image.Image], path: Path) -> None:
    # 每个关键时刻占一行，三视角在同一行并排；这样审阅者不需要在数百帧
    # 中寻找姿势。GIF 仍由调用方使用完整采样序列生成。
    width, height = frames[0].size
    sheet = Image.new("RGB", (width, height * len(frames)), (255, 255, 255))
    for index, frame in enumerate(frames):
        sheet.paste(frame.convert("RGB"), (0, index * height))
    sheet.save(path)


def gif_times(duration: float, sample_fps: float) -> list[float]:
    count = max(1, math.ceil(duration * sample_fps - 1e-9))
    times = [index / sample_fps for index in range(count)]
    return times


def render_model(source: dict[str, Any], model_path: Path, document: dict[str, Any], key: str,
                 animation_id: str, output: Path, label: str, fps: int) -> None:
    model = SkeletonModel(load_json(model_path))
    sampler = AnimationSampler(model, document, key)
    texture = SkeletonModel(source).texture()  # 两套体型共用内嵌 Steve 贴图。
    contact_times = list(KEY_TIMES.get(animation_id, KEY_TIMES.get(main_hand_id(animation_id) or "", ())))
    if not contact_times:
        # 接触表固定保留 10 个时刻（包括 0 和 duration 的阶段边界）。
        contact_times = [sampler.duration * index / 9 for index in range(10)]
    slow = not sampler.loop and sampler.duration <= SLOW_MAX_DURATION
    realtime = gif_times(sampler.duration, fps)
    slow_times = gif_times(sampler.duration, fps / SLOW_FACTOR) if slow else []
    height = panel_height(model, sampler, animation_id, realtime + slow_times + contact_times + [sampler.duration])
    frame_ms = round(1000 / fps)

    def sequence(times: list[float], tag: str) -> list[Image.Image]:
        frames = [render_frame(model, sampler, texture, animation_id, time, height, f"t={time:.3f}s{tag}") for time in times]
        if not sampler.loop:
            # 一次性动作补上末帧并在首尾停留，避免 GIF 循环时起止姿态一闪而过。
            last = render_frame(model, sampler, texture, animation_id, sampler.duration, height,
                                f"t={sampler.duration:.3f}s{tag}")
            hold = max(1, round(HOLD_SECONDS * fps))
            frames = [frames[0]] * hold + frames + [last] * hold
        return frames

    save_gif(sequence(realtime, ""), output / f"{animation_id}-{label}.gif", frame_ms)
    if slow:
        save_gif(sequence(slow_times, f" slow x{SLOW_FACTOR:g}"), output / f"{animation_id}-{label}-slow.gif", frame_ms)
    drive = DRIVE_PREVIEW.get(animation_id, DRIVE_PREVIEW.get(main_hand_id(animation_id) or "", ()))
    if drive:
        step = SLOW_FACTOR / fps
        frames = []
        for start, end, repeat in drive:
            count = max(1, round((end - start) / step))
            for _ in range(repeat):
                frames += [render_frame(model, sampler, texture, animation_id, start + index * step, height,
                                        f"t={start + index * step:.3f}s drive x{SLOW_FACTOR:g}") for index in range(count)]
        save_gif(frames, output / f"{animation_id}-{label}-drive-slow.gif", frame_ms)
    contact_frames = [render_frame(model, sampler, texture, animation_id, time, height, f"t={time:.3f}s")
                      for time in contact_times]
    contact_sheet(contact_frames, output / f"{animation_id}-{label}-contact.png")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("animation", nargs="+", help="动画 ID")
    parser.add_argument("--source", type=Path, default=Path("models/player_skeleton_classic.bbmodel"))
    parser.add_argument("--slim", type=Path, default=Path("models/player_skeleton_slim.bbmodel"))
    parser.add_argument("--output", type=Path, default=Path("build/anim-review"))
    parser.add_argument("--fps", type=int, default=12)
    args = parser.parse_args()
    source = load_json(args.source)
    verify_front_orientation(SkeletonModel(source))
    print("front 自检通过：yaw=180°，观察 head_inner north(-Z) Steve 脸部 UV 8..16；r_upper_arm 位于观察者左侧")
    for animation_id in args.animation:
        document, key = source_animation_document(source, animation_id)
        output = args.output / animation_id
        render_model(source, args.source, document, key, animation_id, output, "classic", args.fps)
        render_model(source, args.slim, document, key, animation_id, output, "slim", args.fps)
        print(f"渲染 {animation_id}: {output}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
