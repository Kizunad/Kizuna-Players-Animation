package dev.kizuna.playeranimation.client;

import dev.kizuna.playeranimation.model.BoneId;
import dev.kizuna.playeranimation.model.Rig;
import dev.kizuna.playeranimation.model.Skeleton;
import dev.kizuna.playeranimation.model.SkinnedMesh;
import dev.kizuna.playeranimation.model.Vec3;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.model.ModelTransform;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/** 从原版盔甲绑定几何烘焙网格，复用玩家权重与骨骼，不复制材质或动画。 */
public final class ArmorMesh {
    private static final Map<BipedEntityModel<?>, Cached> CACHE = new IdentityHashMap<>();
    private record Cached(Skeleton skeleton, ArmorMesh mesh) { }
    private record Binding(BoneId bone, Skeleton.Cube cube) { }
    record Corner(Vec3 position, Vec3 normal, float u, float v) { }
    private final List<SkinnedMesh.Surface> surfaces;

    public ArmorMesh(BipedEntityModel<?> armor, PlayerAssets.Model player) {
        List<SkinnedMesh.Surface> result = new ArrayList<>();
        // 原版盔甲内外层都是六部件几何；hat 与 head 保留各自 UV 和显隐。
        for (BoneId region : new BoneId[]{BoneId.HEAD, BoneId.PELVIS, BoneId.R_UPPER_ARM,
                BoneId.L_UPPER_ARM, BoneId.R_THIGH, BoneId.L_THIGH}) {
            bakePart(part(armor, region, false), region, false, player, result);
        }
        bakePart(armor.hat, BoneId.HEAD, true, player, result);
        surfaces = List.copyOf(result);
    }

    public static void clearCache() { CACHE.clear(); }

    public static ArmorMesh cached(BipedEntityModel<?> armor, PlayerAssets.Model player) {
        // 两种体型各有实际渲染模型实例；资源重载后不能继续使用旧骨架权重。
        Cached cached = CACHE.get(armor);
        if (cached == null || cached.skeleton() != player.skeleton()) {
            cached = new Cached(player.skeleton(), new ArmorMesh(armor, player));
            CACHE.put(armor, cached);
        }
        return cached.mesh();
    }

    public List<SkinnedMesh.Surface> surfaces() { return surfaces; }

    public static boolean visible(BipedEntityModel<?> armor, SkinnedMesh.Surface surface) {
        ModelPart part = part(armor, surface.bone(), surface.outer());
        return part.visible && !part.hidden;
    }

    private static ModelPart part(BipedEntityModel<?> armor, BoneId region, boolean hat) {
        // 标签仅选择原版部件，实际每个顶点可受多个细分关节共同影响。
        return switch (region) {
            case HEAD -> hat ? armor.hat : armor.head;
            case PELVIS -> armor.body;
            case R_UPPER_ARM -> armor.rightArm;
            case L_UPPER_ARM -> armor.leftArm;
            case R_THIGH -> armor.rightLeg;
            case L_THIGH -> armor.leftLeg;
            default -> throw new IllegalArgumentException("不是盔甲部件: " + region);
        };
    }

    private static void bakePart(ModelPart part, BoneId region, boolean hat, PlayerAssets.Model player,
                                 List<SkinnedMesh.Surface> result) {
        List<Binding> bindings = new ArrayList<>();
        for (Skeleton.Bone bone : player.skeleton().bones()) {
            if (bone.id().skinPart().equals(region.skinPart())) {
                for (Skeleton.Cube cube : bone.cubes()) {
                    if (cube.inflate() == 0) {
                        bindings.add(new Binding(bone.id(), cube));
                    }
                }
            }
        }
        List<SkinnedMesh.Vertex> vertices = new ArrayList<>();
        part.forEachCuboid(new MatrixStack(), (ignored, path, index, cuboid) -> {
            // forEachCuboid 的矩阵带当前动画；这里只读方块，绑定矩阵另取默认姿态。
            Matrix4f bind = bindTransform(part, path);
            if (region.arm()) {
                // 细臂原版复制姿态后肩部降低半像素，盔甲本身仍保持四像素宽。
                Vec3 shoulder = player.skeleton().bone(region).pivot();
                ModelTransform original = part.getDefaultTransform();
                bind.m30(bind.m30() + shoulder.x() - original.pivotX);
                bind.m31(bind.m31() + Rig.MODEL_ORIGIN_HEIGHT - shoulder.y() - original.pivotY);
                bind.m32(bind.m32() + shoulder.z() - original.pivotZ);
            }
            QuadCollector collector = new QuadCollector();
            cuboid.renderCuboid(new MatrixStack().peek(), collector, 0, 0, 1, 1, 1, 1);
            bakeCuboid(cuboid, collector.corners, bind, bindings, player.mesh(), vertices);
        });
        result.add(new SkinnedMesh.Surface(region, hat, List.copyOf(vertices)));
    }

    private static Matrix4f bindTransform(ModelPart root, String path) {
        Matrix4f result = new Matrix4f();
        appendBind(result, root.getDefaultTransform());
        // 原版部件没有子节点；仍按模型提供的默认层级读取，避免混入当前姿态。
        ModelPart current = root;
        if (!path.isEmpty()) {
            for (String name : path.substring(1).split("/")) {
                current = current.getChild(name);
                appendBind(result, current.getDefaultTransform());
            }
        }
        return result;
    }

    private static void appendBind(Matrix4f matrix, ModelTransform bind) {
        // 此处使用原版模型像素坐标（Y 向下），提交蒙皮前统一转换到骨架空间。
        matrix.translate(bind.pivotX, bind.pivotY, bind.pivotZ).rotateZYX(bind.roll, bind.yaw, bind.pitch);
    }

    private static void bakeCuboid(ModelPart.Cuboid cuboid, List<Corner> corners, Matrix4f bind,
                                    List<Binding> bindings, SkinnedMesh skin, List<SkinnedMesh.Vertex> result) {
        Vector3f min = new Vector3f(Float.POSITIVE_INFINITY);
        Vector3f max = new Vector3f(Float.NEGATIVE_INFINITY);
        for (Corner corner : corners) {
            Vec3 p = corner.position();
            min.min(new Vector3f(p.x(), p.y(), p.z()));
            max.max(new Vector3f(p.x(), p.y(), p.z()));
        }
        // 从膨胀后的实际顶点还原绑定位置，权重不受盔甲厚度影响。
        Matrix4f uninflate = new Matrix4f().translate(cuboid.minX, cuboid.minY, cuboid.minZ)
                .scale((cuboid.maxX - cuboid.minX) / (max.x - min.x),
                        (cuboid.maxY - cuboid.minY) / (max.y - min.y),
                        (cuboid.maxZ - cuboid.minZ) / (max.z - min.z)).translate(-min.x, -min.y, -min.z);
        for (int face = 0; face < corners.size(); face += 4) {
            Corner a = corners.get(face);
            Corner b = corners.get(face + 1);
            Corner c = corners.get(face + 2);
            Corner d = corners.get(face + 3);
            Vector3f qa = transform(uninflate, a.position());
            // 每原始像素一格，膝肘、腰胸、踝与脚尖切面都来自现有分段几何。
            int columns = Math.max(1, Math.round(qa.distance(transform(uninflate, b.position()))));
            int rows = Math.max(1, Math.round(qa.distance(transform(uninflate, d.position()))));
            for (int y = 0; y < rows; y++) {
                for (int x = 0; x < columns; x++) {
                    addVertex(a, b, c, d, (float) x / columns, (float) y / rows, bind, uninflate, bindings, skin, result);
                    addVertex(a, b, c, d, (float) (x + 1) / columns, (float) y / rows, bind, uninflate, bindings, skin, result);
                    addVertex(a, b, c, d, (float) (x + 1) / columns, (float) (y + 1) / rows, bind, uninflate, bindings, skin, result);
                    addVertex(a, b, c, d, (float) x / columns, (float) (y + 1) / rows, bind, uninflate, bindings, skin, result);
                }
            }
        }
    }

    private static void addVertex(Corner a, Corner b, Corner c, Corner d, float u, float v, Matrix4f bind,
                                   Matrix4f uninflate, List<Binding> bindings, SkinnedMesh skin,
                                   List<SkinnedMesh.Vertex> vertices) {
        // 在原版四边形上插值位置与 UV，保留镜像、面绕序和纹理图集坐标。
        Vec3 point = a.position().lerp(b.position(), u).lerp(d.position().lerp(c.position(), u), v);
        Vector3f reference = transform(uninflate, point);
        bind.transformPosition(reference);
        Vec3 q = modelSpace(reference);
        Binding selected = null;
        float nearest = Float.POSITIVE_INFINITY;
        for (Binding binding : bindings) {
            float distance = 0;
            for (int axis = 0; axis < 3; axis++) {
                float delta = Math.max(binding.cube().min().axis(axis) - q.axis(axis),
                        Math.max(0, q.axis(axis) - binding.cube().max(axis)));
                distance += delta * delta;
            }
            if (distance < nearest) {
                selected = binding;
                nearest = distance;
            }
        }
        Vector3f normal = bind.transformDirection(a.normal().x(), a.normal().y(), a.normal().z(), new Vector3f());
        float texU = lerp(lerp(a.u(), b.u(), u), lerp(d.u(), c.u(), u), v);
        float texV = lerp(lerp(a.v(), b.v(), u), lerp(d.v(), c.v(), u), v);
        vertices.add(new SkinnedMesh.Vertex(modelSpace(transform(bind, point)),
                new Vec3(normal.x, -normal.y, normal.z), texU, texV, skin.weights(selected.bone(), q)));
    }

    private static Vector3f transform(Matrix4f matrix, Vec3 point) {
        return matrix.transformPosition(point.x(), point.y(), point.z(), new Vector3f());
    }

    private static Vec3 modelSpace(Vector3f point) {
        return new Vec3(point.x, Rig.MODEL_ORIGIN_HEIGHT - point.y, point.z);
    }

    private static float lerp(float a, float b, float amount) { return a + (b - a) * amount; }

    static final class QuadCollector implements VertexConsumer {
        final List<Corner> corners = new ArrayList<>();
        private Vec3 position;
        private Vec3 normal;
        private float u;
        private float v;

        @Override
        public VertexConsumer vertex(double x, double y, double z) {
            // 原版提交方块单位，烘焙与玩家骨架统一使用像素单位。
            position = new Vec3((float) x * Rig.PIXELS_PER_BLOCK, (float) y * Rig.PIXELS_PER_BLOCK,
                    (float) z * Rig.PIXELS_PER_BLOCK);
            return this;
        }

        @Override
        public VertexConsumer texture(float u, float v) {
            this.u = u;
            this.v = v;
            return this;
        }

        @Override
        public VertexConsumer normal(float x, float y, float z) {
            normal = new Vec3(x, y, z);
            return this;
        }

        @Override public void next() { corners.add(new Corner(position, normal, u, v)); }
        // 材质和光照由每次原版绘制传入，缓存只记录几何与 UV。
        @Override public VertexConsumer color(int r, int g, int b, int a) { return this; }
        @Override public VertexConsumer overlay(int u, int v) { return this; }
        @Override public VertexConsumer light(int u, int v) { return this; }
        @Override public void fixedColor(int r, int g, int b, int a) { }
        @Override public void unfixColor() { }
    }
}
