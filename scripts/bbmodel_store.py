#!/usr/bin/env python3
"""classic 模型的动画按组分在几个 .bbmodel 里，这里负责读回成一整份、按名字写回各组，并检查它们。

为什么分：一个文件装全部动画会超过 GitHub 单文件 100 MB 的上限。每个分组文件都带完整的骨架、几何和贴图，
单独用 Blockbench 打开就能编辑那一组动画。动画进哪个文件只看名字，见 GROUPS。

文件写法（dumps）：两格缩进的 JSON，只有关键帧一帧写成一行。一帧一行比全部展开小一半多，按行对比仍然能看出改了哪一帧。
Blockbench 存出来的写法不一样，存完跑一次 `bbmodel_store.py normalize` 改回本仓写法。
"""

from __future__ import annotations

import argparse
import json
from pathlib import Path
import sys
from typing import Any, Iterator


CLASSIC = Path("models/player_skeleton_classic")
PREFIX = "animation.kizuna_players_animation.player."
# 从上往下第一个对上前缀的算；都对不上的进 BASE。加新模组的动画就在这里加一行。
GROUPS: tuple[tuple[str, tuple[str, ...]], ...] = (
    ("villager", ("villager_",)),
    ("witch", ("witch_",)),
    ("herb_knife", ("herb_knife_",)),
    ("evoker", ("evoker_",)),
    ("wayfarer", ("wayfarer_",)),
    ("door_breaker", ("door_breaker_",)),
    ("peddler", ("peddler_",)),
    ("combat", ("stance_", "dodge_", "block_one_hand", "block_two_hand", "guard_arms", "technique_", "status_")),
)
BASE = "base"
ORDER = (BASE,) + tuple(name for name, _ in GROUPS)
# GitHub 拒收超过 100 MB 的文件；到 90 MB 就报，留出加动画的余地。
MAX_BYTES = 90_000_000


def group_of(animation_name: str) -> str:
    """动画（完整键）归哪一组。"""
    if not animation_name.startswith(PREFIX):
        raise ValueError(f"动画名不是 {PREFIX}<id>：{animation_name}")
    identifier = animation_name[len(PREFIX):]
    for group, prefixes in GROUPS:
        if identifier.startswith(prefixes):
            return group
    return BASE


def group_path(directory: Path, group: str) -> Path:
    return directory / f"{group}.bbmodel"


def dumps(document: Any) -> str:
    """本仓的 .bbmodel 写法。"""
    return "".join(_write(document, 0, None)) + "\n"


def _write(value: Any, depth: int, key: str | None) -> Iterator[str]:
    # 和 json.dumps(indent=2, ensure_ascii=False) 一样展开，只有 keyframes 数组里的每一帧压成一行。
    inner = "  " * (depth + 1)
    outer = "  " * depth
    if isinstance(value, dict) and value:
        yield "{\n"
        last = len(value) - 1
        for position, (name, item) in enumerate(value.items()):
            yield f"{inner}{json.dumps(name, ensure_ascii=False)}: "
            yield from _write(item, depth + 1, name)
            yield "\n" if position == last else ",\n"
        yield outer + "}"
    elif isinstance(value, list) and value:
        yield "[\n"
        last = len(value) - 1
        for position, item in enumerate(value):
            yield inner
            if key == "keyframes":
                yield json.dumps(item, ensure_ascii=False, separators=(",", ":"))
            else:
                yield from _write(item, depth + 1, None)
            yield "\n" if position == last else ",\n"
        yield outer + "]"
    else:
        yield json.dumps(value, ensure_ascii=False)


def static_of(document: dict[str, Any]) -> dict[str, Any]:
    """动画以外的部分：骨架、几何、贴图。各组必须一样。"""
    return {key: value for key, value in document.items() if key != "animations"}


def load_model(source: Path) -> dict[str, Any]:
    """读成一整份模型文档。source 是分组目录时把各组的动画接起来；是单个 .bbmodel 时原样读。"""
    if not source.is_dir():
        return json.loads(source.read_text(encoding="utf-8"))
    files = _group_files(source)
    static: dict[str, Any] | None = None
    animations: list[dict[str, Any]] = []
    seen: dict[str, str] = {}
    for group, path in files:
        document = json.loads(path.read_text(encoding="utf-8"))
        if static is None:
            static = static_of(document)
        elif static_of(document) != static:
            raise ValueError(f"{path} 的骨架、几何或贴图和 {files[0][1]} 不一样")
        for animation in document.get("animations", []):
            name = animation.get("name")
            if not isinstance(name, str):
                raise ValueError(f"{path} 里有动画缺少 name")
            if name in seen:
                raise ValueError(f"动画 {name} 同时在 {seen[name]} 和 {group} 两组里")
            if group_of(name) != group:
                raise ValueError(f"动画 {name} 按名字该在 {group_of(name)} 组，现在在 {path}")
            seen[name] = group
            animations.append(animation)
    return {**static, "animations": animations}


def _group_files(directory: Path) -> list[tuple[str, Path]]:
    # 按 ORDER 的次序列出存在的分组文件；目录里不认识的 .bbmodel 报错，免得动画放错地方没人读。
    known = [(group, group_path(directory, group)) for group in ORDER]
    unknown = sorted(set(directory.glob("*.bbmodel")) - {path for _, path in known})
    if unknown:
        raise ValueError(f"{directory} 里有不认识的分组文件：{', '.join(path.name for path in unknown)}")
    files = [(group, path) for group, path in known if path.exists()]
    if not files:
        raise ValueError(f"{directory} 里没有分组文件")
    return files


def save_model(directory: Path, document: dict[str, Any]) -> list[Path]:
    """把一整份模型文档按名字分回各组，只重写内容变了的文件。返回重写了的文件。"""
    static = static_of(document)
    grouped: dict[str, list[dict[str, Any]]] = {group: [] for group in ORDER}
    names: set[str] = set()
    for animation in document.get("animations", []):
        name = animation["name"]
        if name in names:
            raise ValueError(f"重复动画名: {name}")
        names.add(name)
        grouped[group_of(name)].append(animation)
    directory.mkdir(parents=True, exist_ok=True)
    written = []
    for group in ORDER:
        path = group_path(directory, group)
        if not grouped[group]:
            if path.exists():
                raise ValueError(f"{group} 组的动画全没了；要删这个分组请手动删 {path}")
            continue
        text = dumps({**static, "animations": grouped[group]})
        if not path.exists() or path.read_text(encoding="utf-8") != text:
            path.write_text(text, encoding="utf-8")
            written.append(path)
    return written


def upsert(directory: Path, animations: list[dict[str, Any]]) -> list[Path]:
    """同名的替换、没有的追加到它那一组的末尾。"""
    document = load_model(directory)
    positions = {animation["name"]: index for index, animation in enumerate(document["animations"])}
    for animation in animations:
        name = animation["name"]
        if name in positions:
            document["animations"][positions[name]] = animation
        else:
            positions[name] = len(document["animations"])
            document["animations"].append(animation)
    return save_model(directory, document)


def check(directory: Path) -> list[str]:
    """分组文件的毛病：写法不是本仓写法、太大。骨架不一致、放错组、重名由 load_model 直接报错。"""
    load_model(directory)
    errors = []
    for _, path in _group_files(directory):
        text = path.read_text(encoding="utf-8")
        if dumps(json.loads(text)) != text:
            errors.append(f"{path} 不是本仓写法，跑 scripts/bbmodel_store.py normalize")
        size = path.stat().st_size
        if size > MAX_BYTES:
            errors.append(f"{path} 有 {size / 1e6:.1f} MB，超过 {MAX_BYTES / 1e6:.0f} MB；把里面的动画再分一组（GROUPS）")
    return errors


def adopt(directory: Path, source: Path) -> list[Path]:
    """把一个单独的 .bbmodel 里的动画收进分组：骨架、几何必须和分组里的一样；分组目录还空着时以它为准。"""
    document = json.loads(source.read_text(encoding="utf-8"))
    if not directory.is_dir() or not any(directory.glob("*.bbmodel")):
        return save_model(directory, document)
    if static_of(document) != static_of(load_model(directory)):
        raise ValueError(f"{source} 的骨架、几何或贴图和分组里的不一样")
    return upsert(directory, document.get("animations", []))


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--directory", type=Path, default=CLASSIC)
    commands = parser.add_subparsers(dest="command", required=True)
    commands.add_parser("list", help="列出各组的文件大小和动画数")
    commands.add_parser("check", help="检查写法和大小")
    commands.add_parser("normalize", help="把各组改回本仓写法（Blockbench 存过以后用）")
    adopting = commands.add_parser("adopt", help="把一个单独的 .bbmodel 里的动画按名字收进各组")
    adopting.add_argument("source", type=Path)
    args = parser.parse_args()
    if args.command == "list":
        for group, path in _group_files(args.directory):
            count = len(json.loads(path.read_text(encoding="utf-8")).get("animations", []))
            print(f"{group:14s} {path.stat().st_size / 1e6:7.2f} MB  {count:3d} 段  {path}")
        return 0
    if args.command == "check":
        errors = check(args.directory)
        for error in errors:
            print(f"- {error}")
        if not errors:
            print(f"分组文件检查通过: {len(_group_files(args.directory))} 个")
        return 1 if errors else 0
    if args.command == "normalize":
        written = save_model(args.directory, load_model(args.directory))
    else:
        written = adopt(args.directory, args.source)
    for path in written:
        print(f"写入 {path}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
