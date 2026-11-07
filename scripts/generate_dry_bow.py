"""生成原创无箭拉弓贴图及对应模型，不复制 Minecraft 贴图。"""

import argparse
import io
import json
from pathlib import Path
from PIL import Image, ImageDraw


ROOT = Path(__file__).resolve().parents[1] / "src/main/resources/assets"
NAMESPACE = "kizuna_players_animation"
SIZE = 32
WOOD = (139, 91, 49, 255)
EDGE = (67, 44, 28, 255)
LIGHT = (205, 164, 101, 255)
STRING = (218, 215, 185, 255)
BOW = [(6, 26), (13, 26), (20, 23), (24, 18), (26, 11), (26, 6)]


def texture(nock):
    image = Image.new("RGBA", (SIZE, SIZE))
    draw = ImageDraw.Draw(image)
    draw.line([BOW[0], nock, BOW[-1]], fill=STRING, width=1)
    draw.line(BOW, fill=EDGE, width=4)
    draw.line(BOW, fill=WOOD, width=2)
    draw.line([(14, 25), (19, 22), (23, 17)], fill=LIGHT, width=1)
    draw.line([(19, 23), (23, 19)], fill=EDGE, width=3)
    stream = io.BytesIO()
    image.save(stream, format="PNG")
    return stream.getvalue()


def encoded(value):
    return (json.dumps(value, indent=2) + "\n").encode()


def model(path):
    return {"type": "minecraft:model", "model": path}


def pulling(namespace, prefix):
    return {"type": "minecraft:range_dispatch", "property": "minecraft:use_duration", "scale": 0.05,
            "fallback": model(f"{namespace}:item/{prefix}_0"),
            "entries": [{"threshold": 0.65, "model": model(f"{namespace}:item/{prefix}_1")},
                        {"threshold": 0.9, "model": model(f"{namespace}:item/{prefix}_2")}]}


def outputs():
    result = {}
    for stage, nock in enumerate([(15, 15), (11, 11), (7, 7)]):
        name = f"bow_dry_{stage}"
        result[ROOT / NAMESPACE / f"textures/item/{name}.png"] = texture(nock)
        result[ROOT / NAMESPACE / f"models/item/{name}.json"] = encoded({
            "parent": "minecraft:item/bow",
            "textures": {"layer0": f"{NAMESPACE}:item/{name}"}})
    condition = {"type": "minecraft:condition", "property": f"{NAMESPACE}:arrow",
                 "on_true": pulling("minecraft", "bow_pulling"),
                 "on_false": pulling(NAMESPACE, "bow_dry")}
    result[ROOT / "minecraft/items/bow.json"] = encoded({"model": {
        "type": "minecraft:condition", "property": "minecraft:using_item",
        "on_true": condition, "on_false": model("minecraft:item/bow")}})
    return result


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--check", action="store_true")
    args = parser.parse_args()
    for path, content in outputs().items():
        if args.check:
            if not path.exists() or path.read_bytes() != content:
                raise SystemExit(f"无箭弓资源与生成源不一致：{path}")
        else:
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_bytes(content)
    print("无箭弓资源：7 个输出一致")
