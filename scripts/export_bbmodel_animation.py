#!/usr/bin/env python3
"""按 Blockbench Bedrock 规则导出并校验 .bbmodel 动画。

该脚本只负责资源转换，不生成关键帧。关键帧的唯一来源是 .bbmodel 的
animations 数组；导出的 JSON 只是运行时打包格式。
"""

from __future__ import annotations

import argparse
import json
import math
from pathlib import Path
from typing import Any


ROTATION_SIGN = (-1.0, -1.0, 1.0)
POSITION_SIGN = (-1.0, 1.0, 1.0)


def number(value: Any) -> float:
    value = float(value)
    if not math.isfinite(value):
        raise ValueError(f"关键帧包含非有限数值: {value!r}")
    return value


def vector(data_point: dict[str, Any], signs: tuple[float, float, float]) -> list[float]:
    values = [number(data_point.get(axis, 0)) for axis in ("x", "y", "z")]
    return [clean(values[i] * signs[i]) for i in range(3)]


def clean(value: float) -> int | float:
    if abs(value) < 0.0000005:
        return 0
    rounded = round(value, 6)
    return int(rounded) if rounded.is_integer() else rounded


def time_key(value: float) -> str:
    value = number(value)
    if value.is_integer():
        return str(int(value))
    return f"{value:.6f}".rstrip("0").rstrip(".")


def loop_value(value: str) -> bool | str:
    if value == "loop":
        return True
    if value in ("hold", "hold_on_last_frame"):
        return "hold_on_last_frame"
    return False


def channel_value(keyframe: dict[str, Any], channel: str) -> list[int | float]:
    points = keyframe.get("data_points")
    if not isinstance(points, list) or len(points) != 1 or not isinstance(points[0], dict):
        raise ValueError(f"{channel} 关键帧必须只有一个 data_point: {keyframe}")
    signs = ROTATION_SIGN if channel == "rotation" else POSITION_SIGN
    return vector(points[0], signs)


def export_channel(keyframes: list[dict[str, Any]], channel: str) -> list[int | float] | dict[str, Any]:
    if not keyframes:
        raise ValueError(f"空动画通道: {channel}")
    frames = sorted(keyframes, key=lambda frame: number(frame["time"]))
    if len(frames) == 1 and number(frames[0]["time"]) == 0:
        return channel_value(frames[0], channel)

    result: dict[str, Any] = {}
    for frame in frames:
        mode = frame.get("interpolation", "linear")
        value = channel_value(frame, channel)
        # Blockbench 的 Bedrock 导出器对 linear 关键帧直接写向量；只有
        # catmullrom/step 等需要插值语义的帧才写 pre/post 对象。
        if mode == "linear" and "pre" not in frame and "post" not in frame:
            result[time_key(number(frame["time"]))] = value
            continue
        entry: dict[str, Any] = {"post": value, "lerp_mode": mode}
        # Blockbench 的 step 导出可能携带显式 pre/post；保留它们而不猜测。
        if "pre" in frame:
            entry["pre"] = frame["pre"]
        if "post" in frame:
            entry["post"] = frame["post"]
        result[time_key(number(frame["time"]))] = entry
    return result


def export_animation(source: dict[str, Any], animation: dict[str, Any]) -> dict[str, Any]:
    bones: dict[str, dict[str, Any]] = {}
    for animator in animation.get("animators", {}).values():
        bone_name = animator.get("name")
        if not isinstance(bone_name, str) or not bone_name:
            raise ValueError(f"动画骨骼缺少名称: {animator!r}")
        by_channel: dict[str, list[dict[str, Any]]] = {}
        for keyframe in animator.get("keyframes", []):
            channel = keyframe.get("channel")
            if channel not in ("rotation", "position"):
                raise ValueError(f"{animation.get('name')}:{bone_name} 使用不支持的通道 {channel!r}")
            by_channel.setdefault(channel, []).append(keyframe)
        if by_channel:
            bones[bone_name] = {
                channel: export_channel(frames, channel)
                for channel, frames in by_channel.items()
            }

    name = animation["name"]
    exported = {
        "format_version": "1.8.0",
        "animations": {
            name: {
                "loop": loop_value(animation.get("loop", "once")),
                "animation_length": clean(number(animation["length"])),
                "bones": bones,
            }
        },
    }
    # 时间线标记由 .bbmodel 动画对象的 markers 字段维护。Blockbench 会保留
    # 未知字段，导出时原样写入运行时白名单事件。
    if animation.get("markers"):
        exported["animations"][name]["timeline"] = animation["markers"]
    return exported


def load_animations(path: Path) -> dict[str, dict[str, Any]]:
    document = json.loads(path.read_text(encoding="utf-8"))
    animations = document.get("animations")
    if not isinstance(animations, list):
        raise ValueError(f"{path} 缺少 Blockbench animations 数组")
    result = {}
    for animation in animations:
        name = animation.get("name")
        if not isinstance(name, str):
            raise ValueError(f"动画缺少 name: {animation!r}")
        if name in result:
            raise ValueError(f"重复动画名: {name}")
        result[name] = animation
    return result


def load_packaged_animations(directory: Path) -> dict[str, dict[str, Any]]:
    result: dict[str, dict[str, Any]] = {}
    for path in sorted(directory.glob("*.animation.json")):
        document = json.loads(path.read_text(encoding="utf-8"))
        for name, animation in document.get("animations", {}).items():
            if name in result:
                raise ValueError(f"打包资源重复动画键 {name}: {path}")
            result[name] = {"path": path, "document": {"format_version": document.get("format_version"), "animations": {name: animation}}}
    return result


def compare(expected: Any, actual: Any, path: str = "", tolerance: float = 0.001) -> list[str]:
    if isinstance(expected, (int, float)) and isinstance(actual, (int, float)):
        if abs(float(expected) - float(actual)) > tolerance:
            return [f"{path}: {expected!r} != {actual!r}"]
        return []
    if type(expected) is not type(actual):
        # JSON 数字的 int/float 类型差异已经在上面处理。
        return [f"{path}: 类型 {type(expected).__name__} != {type(actual).__name__}"]
    if isinstance(expected, dict):
        errors = []
        for key in sorted(set(expected) | set(actual)):
            if key not in expected:
                errors.append(f"{path}/{key}: 打包资源多出字段")
            elif key not in actual:
                errors.append(f"{path}/{key}: 打包资源缺少字段")
            else:
                errors.extend(compare(expected[key], actual[key], f"{path}/{key}", tolerance))
        return errors
    if isinstance(expected, list):
        if len(expected) != len(actual):
            return [f"{path}: 长度 {len(expected)} != {len(actual)}"]
        errors = []
        for i, (left, right) in enumerate(zip(expected, actual)):
            errors.extend(compare(left, right, f"{path}[{i}]", tolerance))
        return errors
    if expected != actual:
        return [f"{path}: {expected!r} != {actual!r}"]
    return []


def check(source_path: Path, packaged_directory: Path) -> int:
    source = load_animations(source_path)
    packaged = load_packaged_animations(packaged_directory)
    errors: list[str] = []
    for name, animation in source.items():
        if name not in packaged:
            errors.append(f".bbmodel 动画没有打包产物: {name}")
            continue
        expected = export_animation(source, animation)
        actual = packaged[name]["document"]
        errors.extend(compare(expected, actual, name))
    for name, info in packaged.items():
        if name not in source:
            errors.append(f"打包 JSON 存在而 .bbmodel 没有动画: {name} ({info['path']})")
    if errors:
        print("导出一致性失败:")
        print("\n".join(f"- {error}" for error in errors[:80]))
        if len(errors) > 80:
            print(f"- 其余 {len(errors) - 80} 项错误省略")
        return 1
    print(f"导出一致性通过: {len(source)} 段动画，浮点容差 <= 0.001")
    return 0


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--source", type=Path, default=Path("models/player_skeleton_classic.bbmodel"))
    parser.add_argument("--output", type=Path, default=Path("src/main/resources/assets/kizuna_players_animation/animations"))
    parser.add_argument("--animation", action="append", help="只导出指定的完整动画键，可重复")
    parser.add_argument("--check", action="store_true", help="检查所有打包 JSON 与 .bbmodel 一致")
    args = parser.parse_args()

    source = load_animations(args.source)
    if args.check:
        return check(args.source, args.output)

    selected = set(args.animation) if args.animation else set(source)
    unknown = selected - set(source)
    if unknown:
        raise SystemExit(f".bbmodel 没有动画: {', '.join(sorted(unknown))}")
    args.output.mkdir(parents=True, exist_ok=True)
    for name in sorted(selected):
        animation_id = name.rsplit(".", 1)[-1]
        output_path = args.output / f"{animation_id}.animation.json"
        output_path.write_text(json.dumps(export_animation(source, source[name]), ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
        print(f"导出 {name} -> {output_path}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
