package dev.kizuna.playeranimation.model;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.kizuna.playeranimation.animation.JsonInput;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public record Skeleton(List<Bone> bones) {
    public record Bone(BoneId id, Vec3 pivot, List<Cube> cubes) { }
    public record Cube(Vec3 min, Vec3 size, float inflate, Map<Face, Uv> faces) {
        public float max(int axis) { return min.axis(axis) + size.axis(axis); }
    }
    public record Uv(float u, float v, float width, float height, int rotation) { }
    public enum Face { NORTH, SOUTH, EAST, WEST, UP, DOWN }

    public Bone bone(BoneId id) {
        return bones.get(id.ordinal());
    }

    public static Skeleton parse(JsonObject document, String identifier) {
        // 两个体型分别读取完整几何，保持皮肤像素宽度和各自枢轴不变。
        if (!"1.12.0".equals(JsonInput.string(document, "format_version"))) {
            throw new IllegalArgumentException("几何 format_version 必须为 1.12.0");
        }
        JsonObject selected = null;
        for (var element : JsonInput.array(document, "minecraft:geometry")) {
            JsonObject candidate = element.getAsJsonObject();
            if (identifier.equals(JsonInput.string(JsonInput.object(candidate, "description"), "identifier"))) {
                if (selected != null) {
                    throw new IllegalArgumentException("几何 identifier 重复: " + identifier);
                }
                selected = candidate;
            }
        }
        if (selected == null) {
            throw new IllegalArgumentException("缺少几何: " + identifier);
        }
        JsonObject description = JsonInput.object(selected, "description");
        if (JsonInput.number(description.get("texture_width")) != 64
                || JsonInput.number(description.get("texture_height")) != 64) {
            throw new IllegalArgumentException("玩家皮肤纹理必须为 64×64");
        }
        Map<BoneId, Bone> bones = new EnumMap<>(BoneId.class);
        for (var element : JsonInput.array(selected, "bones")) {
            JsonObject raw = element.getAsJsonObject();
            BoneId id = BoneId.named(JsonInput.string(raw, "name"));
            BoneId parent = raw.has("parent") ? BoneId.named(JsonInput.string(raw, "parent")) : null;
            if (parent != id.parent) {
                throw new IllegalArgumentException("父骨不符合接口: " + id.key);
            }
            requireZeroRotation(raw);
            if (raw.has("mirror") && JsonInput.bool(raw, "mirror")
                    || raw.has("inflate") && JsonInput.number(raw.get("inflate")) != 0) {
                throw new IllegalArgumentException("骨级 mirror/inflate 必须烘焙到逐面 UV 与方块: " + id.key);
            }
            Vec3 pivot = JsonInput.vector(raw.get("pivot"));
            List<Cube> cubes = new ArrayList<>();
            if (raw.has("cubes")) {
                for (var cube : JsonInput.array(raw, "cubes")) {
                    cubes.add(parseCube(cube.getAsJsonObject()));
                }
            }
            if ((id.socket() || id == BoneId.ROOT) && !cubes.isEmpty()) {
                throw new IllegalArgumentException("root 与挂点必须为空骨: " + id.key);
            }
            if (!id.socket() && id != BoneId.ROOT) {
                float outerInflate = id == BoneId.HEAD ? 0.5f : 0.25f;
                if (cubes.stream().noneMatch(c -> c.inflate() == 0)
                        || cubes.stream().noneMatch(c -> c.inflate() == outerInflate)) {
                    throw new IllegalArgumentException("显示骨缺少内层或指定厚度的外层: " + id.key);
                }
            }
            Bone bone = new Bone(id, new Vec3(-pivot.x(), pivot.y(), pivot.z()), List.copyOf(cubes));
            if (bones.put(id, bone) != null) {
                throw new IllegalArgumentException("骨名重复: " + id.key);
            }
        }
        List<Bone> ordered = new ArrayList<>();
        for (BoneId id : BoneId.ALL) {
            if (!bones.containsKey(id)) {
                throw new IllegalArgumentException("缺少骨骼: " + id.key);
            }
            ordered.add(bones.get(id));
        }
        return new Skeleton(List.copyOf(ordered));
    }

    private static Cube parseCube(JsonObject raw) {
        requireZeroRotation(raw);
        Vec3 origin = JsonInput.vector(raw.get("origin"));
        Vec3 size = JsonInput.vector(raw.get("size"));
        // 固定骨架由有体积的像素方块组成，面 UV 已经包含皮肤方向。
        if (size.x() <= 0 || size.y() <= 0 || size.z() <= 0) {
            throw new IllegalArgumentException("方块尺寸必须为正数");
        }
        if (raw.has("mirror") && JsonInput.bool(raw, "mirror")) {
            throw new IllegalArgumentException("逐面 UV 不支持额外 mirror，请在导出时烘焙");
        }
        float inflate = raw.has("inflate") ? JsonInput.nonnegative(raw, "inflate") : 0;
        if (inflate != 0 && inflate != 0.25f && inflate != 0.5f) {
            throw new IllegalArgumentException("外层 inflate 必须为 0.25 或 0.5 像素");
        }
        JsonObject uv = JsonInput.object(raw, "uv");
        Map<Face, Uv> faces = new EnumMap<>(Face.class);
        for (var entry : uv.entrySet()) {
            Face face = switch (entry.getKey()) {
                case "north" -> Face.NORTH; case "south" -> Face.SOUTH;
                case "east" -> Face.EAST; case "west" -> Face.WEST;
                case "up" -> Face.UP; case "down" -> Face.DOWN;
                default -> throw new IllegalArgumentException("未知方块面: " + entry.getKey());
            };
            JsonObject faceData = entry.getValue().getAsJsonObject();
            JsonArray start = JsonInput.array(faceData, "uv");
            JsonArray span = JsonInput.array(faceData, "uv_size");
            if (start.size() != 2 || span.size() != 2) {
                throw new IllegalArgumentException("UV 必须为二维数组");
            }
            float rotation = faceData.has("uv_rotation") ? JsonInput.number(faceData.get("uv_rotation")) : 0;
            if (rotation != 0 && rotation != 90 && rotation != 180 && rotation != 270) {
                throw new IllegalArgumentException("UV 旋转必须是 0/90/180/270");
            }
            float u = JsonInput.number(start.get(0));
            float v = JsonInput.number(start.get(1));
            float width = JsonInput.number(span.get(0));
            float height = JsonInput.number(span.get(1));
            if (Math.min(u, u + width) < 0 || Math.max(u, u + width) > 64
                    || Math.min(v, v + height) < 0 || Math.max(v, v + height) > 64) {
                throw new IllegalArgumentException("UV 超出 64×64 皮肤范围");
            }
            // Bedrock 导出器会反转顶底面的 UV 两端，这里恢复编辑器逐面坐标。
            if (face == Face.UP || face == Face.DOWN) {
                u += width;
                v += height;
                width = -width;
                height = -height;
            }
            faces.put(face, new Uv(u, v, width, height, (int) rotation));
        }
        return new Cube(new Vec3(-origin.x() - size.x(), origin.y(), origin.z()), size,
                inflate, Map.copyOf(faces));
    }

    private static void requireZeroRotation(JsonObject object) {
        // 自动蒙皮以轴对齐切口为依据，未烘焙的静态旋转不能默默改变接缝判定。
        if (object.has("rotation")) {
            Vec3 rotation = JsonInput.vector(object.get("rotation"));
            if (rotation.x() != 0 || rotation.y() != 0 || rotation.z() != 0) {
                throw new IllegalArgumentException("模板零姿态的骨骼与方块旋转必须为零");
            }
        }
    }
}
