package dev.kizuna.playeranimation.model;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.kizuna.playeranimation.animation.JsonInput;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.joml.Matrix4f;

public record Skeleton(List<Bone> bones, int textureWidth, int textureHeight) {
    private static final int PLAYER_TEXTURE_SIZE = 64;
    private static final float HEAD_OUTER_INFLATE = 0.5f;
    private static final float BODY_OUTER_INFLATE = 0.25f;
    private static final int QUARTER_TURN_DEGREES = 90;
    private static final int HALF_TURN_DEGREES = 180;
    private static final int THREE_QUARTER_TURN_DEGREES = 270;
    private static final float RADIANS_PER_DEGREE = (float) (Math.PI / HALF_TURN_DEGREES);

    public Skeleton(List<Bone> bones) {
        this(bones, PLAYER_TEXTURE_SIZE, PLAYER_TEXTURE_SIZE);
    }

    public record Bone(BoneId id, Vec3 pivot, List<Cube> cubes) { }
    public record Cube(Vec3 min, Vec3 size, float inflate, Map<Face, Uv> faces, Placement placement) {
        public Cube(Vec3 min, Vec3 size, float inflate, Map<Face, Uv> faces) {
            this(min, size, inflate, faces, null);
        }
        public float max(int axis) { return min.axis(axis) + size.axis(axis); }
    }
    public record Placement(Vec3 min, Vec3 size, Matrix4f transform, String part) { }
    private enum GeometryKind { PLAYER, ENTITY }
    public record Uv(float u, float v, float width, float height, int rotation) { }
    public enum Face { NORTH, SOUTH, EAST, WEST, UP, DOWN }

    public Bone bone(BoneId id) {
        return bones.get(id.ordinal());
    }

    public static Skeleton parse(JsonObject document, String identifier) {
        return parse(document, identifier, GeometryKind.PLAYER);
    }

    public static Skeleton parseEntity(JsonObject document, String identifier) {
        return parse(document, identifier, GeometryKind.ENTITY);
    }

    private static Skeleton parse(JsonObject document, String identifier, GeometryKind kind) {
        // 玩家和实体共用固定骨层级，实体纹理尺寸与膨胀厚度由原版模型决定。
        if (!"1.12.0".equals(JsonInput.string(document, "format_version"))) {
            throw new IllegalArgumentException("几何 format_version 必须为 1.12.0");
        }
        JsonObject selected = selectGeometry(document, identifier);
        JsonObject description = JsonInput.object(selected, "description");
        int textureWidth = JsonInput.integer(description.get("texture_width"));
        int textureHeight = JsonInput.integer(description.get("texture_height"));
        if (textureWidth <= 0 || textureHeight <= 0) {
            throw new IllegalArgumentException("纹理尺寸必须为正数");
        }
        if (kind == GeometryKind.PLAYER
                && (textureWidth != PLAYER_TEXTURE_SIZE || textureHeight != PLAYER_TEXTURE_SIZE)) {
            throw new IllegalArgumentException("玩家皮肤纹理必须为 64×64");
        }
        List<Bone> ordered = parseBones(selected, description, kind);
        return new Skeleton(ordered, textureWidth, textureHeight);
    }

    private static JsonObject selectGeometry(JsonObject document, String identifier) {
        // 一个标识符只能指向一份几何，防止资源内容依赖遍历顺序。
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
        return selected;
    }

    private static List<Bone> parseBones(JsonObject selected, JsonObject description, GeometryKind kind) {
        // 按公开骨序排列，矩阵与顶点权重都使用相同序号。
        Map<BoneId, Bone> bones = new EnumMap<>(BoneId.class);
        for (var element : JsonInput.array(selected, "bones")) {
            Bone bone = parseBone(element.getAsJsonObject(), description, kind);
            if (bones.put(bone.id(), bone) != null) {
                throw new IllegalArgumentException("骨名重复: " + bone.id().key);
            }
        }
        List<Bone> ordered = new ArrayList<>();
        for (BoneId id : BoneId.ALL) {
            if (!bones.containsKey(id)) {
                throw new IllegalArgumentException("缺少骨骼: " + id.key);
            }
            ordered.add(bones.get(id));
        }
        return List.copyOf(ordered);
    }

    private static Bone parseBone(JsonObject raw, JsonObject description, GeometryKind kind) {
        // 空挂点不带几何，骨级变换必须预先烘焙。
        BoneId id = BoneId.named(JsonInput.string(raw, "name"));
        BoneId parent = null;
        if (raw.has("parent")) {
            parent = BoneId.named(JsonInput.string(raw, "parent"));
        }
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
                cubes.add(parseCube(cube.getAsJsonObject(), description, kind));
            }
        }
        if ((id.socket() || id == BoneId.ROOT) && !cubes.isEmpty()) {
            throw new IllegalArgumentException("root 与挂点必须为空骨: " + id.key);
        }
        if (kind == GeometryKind.PLAYER && !id.socket() && id != BoneId.ROOT) {
            float outerInflate = id == BoneId.HEAD ? HEAD_OUTER_INFLATE : BODY_OUTER_INFLATE;
            if (cubes.stream().noneMatch(c -> c.inflate() == 0)
                    || cubes.stream().noneMatch(c -> c.inflate() == outerInflate)) {
                throw new IllegalArgumentException("显示骨缺少内层或指定厚度的外层: " + id.key);
            }
        }
        return new Bone(id, new Vec3(-pivot.x(), pivot.y(), pivot.z()), List.copyOf(cubes));
    }

    private static Cube parseCube(JsonObject raw, JsonObject description, GeometryKind kind) {
        if (kind == GeometryKind.PLAYER) {
            requireZeroRotation(raw);
        }
        Vec3 origin = JsonInput.vector(raw.get("origin"));
        Vec3 size = JsonInput.vector(raw.get("size"));
        if (raw.has("mirror") && JsonInput.bool(raw, "mirror")) {
            throw new IllegalArgumentException("逐面 UV 不支持额外 mirror，请在导出时烘焙");
        }
        float inflate = 0;
        if (raw.has("inflate")) {
            inflate = JsonInput.nonnegative(raw, "inflate");
        }
        validateCubeSize(size, inflate, kind);
        if (kind == GeometryKind.PLAYER && inflate != 0
                && inflate != BODY_OUTER_INFLATE && inflate != HEAD_OUTER_INFLATE) {
            throw new IllegalArgumentException("外层 inflate 必须为 0.25 或 0.5 像素");
        }
        Map<Face, Uv> faces = parseFaces(raw, description);
        Vec3 min = reflectedMin(origin, size);
        Placement placement = null;
        if (kind == GeometryKind.ENTITY) {
            placement = placement(raw, min, size);
        }
        return new Cube(min, size, inflate, faces, placement);
    }

    private static void validateCubeSize(Vec3 size, float inflate, GeometryKind kind) {
        // 生物可包含原版双面平面（如沼骸蘑菇）；玩家体积约束不变，线和点不作为可见几何。
        int flatAxes = 0;
        for (int axis = 0; axis < 3; axis++) {
            float length = size.axis(axis);
            if (length < 0) {
                throw new IllegalArgumentException("方块尺寸不得为负数");
            }
            if (length == 0) {
                flatAxes++;
            }
        }
        if (flatAxes > 1 || (flatAxes == 1 && (kind == GeometryKind.PLAYER || inflate != 0))) {
            throw new IllegalArgumentException("仅实体允许单轴零厚度平面，且平面不得膨胀");
        }
    }

    private static Map<Face, Uv> parseFaces(JsonObject raw, JsonObject description) {
        // 逐面 UV 按各自纹理尺寸校验，保留镜像方向。
        JsonObject uv = JsonInput.object(raw, "uv");
        Map<Face, Uv> faces = new EnumMap<>(Face.class);
        int textureWidth = JsonInput.integer(description.get("texture_width"));
        int textureHeight = JsonInput.integer(description.get("texture_height"));
        for (var entry : uv.entrySet()) {
            Face face = switch (entry.getKey()) {
                case "north" -> Face.NORTH; case "south" -> Face.SOUTH;
                case "east" -> Face.EAST; case "west" -> Face.WEST;
                case "up" -> Face.UP; case "down" -> Face.DOWN;
                default -> throw new IllegalArgumentException("未知方块面: " + entry.getKey());
            };
            faces.put(face, parseFace(entry.getValue().getAsJsonObject(), face, textureWidth, textureHeight));
        }
        return Map.copyOf(faces);
    }

    private static Uv parseFace(JsonObject faceData, Face face, int textureWidth, int textureHeight) {
        // Bedrock 导出器会反转顶底面的 UV 两端，这里恢复编辑器逐面坐标。
        JsonArray start = JsonInput.array(faceData, "uv");
        JsonArray span = JsonInput.array(faceData, "uv_size");
        if (start.size() != 2 || span.size() != 2) {
            throw new IllegalArgumentException("UV 必须为二维数组");
        }
        float rotation = 0;
        if (faceData.has("uv_rotation")) {
            rotation = JsonInput.number(faceData.get("uv_rotation"));
        }
        if (rotation != 0 && rotation != QUARTER_TURN_DEGREES
                && rotation != HALF_TURN_DEGREES && rotation != THREE_QUARTER_TURN_DEGREES) {
            throw new IllegalArgumentException("UV 旋转必须是 0/90/180/270");
        }
        float u = JsonInput.number(start.get(0));
        float v = JsonInput.number(start.get(1));
        float width = JsonInput.number(span.get(0));
        float height = JsonInput.number(span.get(1));
        if (Math.min(u, u + width) < 0 || Math.max(u, u + width) > textureWidth
                || Math.min(v, v + height) < 0 || Math.max(v, v + height) > textureHeight) {
            throw new IllegalArgumentException("UV 超出纹理范围");
        }
        if (face == Face.UP || face == Face.DOWN) {
            return new Uv(u + width, v + height, -width, -height, (int) rotation);
        }
        return new Uv(u, v, width, height, (int) rotation);
    }

    private static Placement placement(JsonObject raw, Vec3 min, Vec3 size) {
        // 原版子部件的静态旋转和整块膨胀范围先烘焙，再套用同一套动画蒙皮。
        Vec3 boundsMin = min;
        Vec3 boundsSize = size;
        if (raw.has("inflate_origin")) {
            Vec3 boundsOrigin = JsonInput.vector(raw.get("inflate_origin"));
            boundsSize = JsonInput.vector(raw.get("inflate_size"));
            boundsMin = reflectedMin(boundsOrigin, boundsSize);
        }
        Matrix4f transform = new Matrix4f();
        if (raw.has("rotation")) {
            Vec3 pivot = JsonInput.vector(raw.get("pivot"));
            Vec3 rotation = JsonInput.vector(raw.get("rotation"));
            transform.translate(-pivot.x(), pivot.y(), pivot.z())
                    .rotateZYX(-rotation.z() * RADIANS_PER_DEGREE,
                            rotation.y() * RADIANS_PER_DEGREE, -rotation.x() * RADIANS_PER_DEGREE)
                    .translate(pivot.x(), -pivot.y(), -pivot.z());
        }
        String part = "";
        if (raw.has("part")) {
            part = JsonInput.string(raw, "part");
        }
        return new Placement(boundsMin, boundsSize, transform, part);
    }

    private static Vec3 reflectedMin(Vec3 origin, Vec3 size) {
        return new Vec3(-origin.x() - size.x(), origin.y(), origin.z());
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
