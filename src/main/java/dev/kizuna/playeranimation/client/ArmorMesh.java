package dev.kizuna.playeranimation.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.kizuna.playeranimation.model.BoneId;
import dev.kizuna.playeranimation.model.Rig;
import dev.kizuna.playeranimation.model.Skeleton;
import dev.kizuna.playeranimation.model.SkinnedMesh;
import dev.kizuna.playeranimation.model.Vec3;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/** 从原版盔甲绑定几何烘焙网格，复用玩家权重与骨骼，不复制材质或动画。 */
public final class ArmorMesh {
    private static final Map<HumanoidModel<?>, Cached> CACHE = new IdentityHashMap<>();
    private record Cached(Skeleton skeleton, ArmorMesh mesh) { }
    private record Binding(BoneId bone, Skeleton.Cube cube) { }
    record Corner(Vec3 position, Vec3 normal, float u, float v) { }
    private static final String HAT_PATH = "/hat";
    private static final float CUT_KEY_SCALE = 1024;
    private static final int AXES = 3;
    /** 一个原版面按像素格点烘焙好的顶点，[列][行]；columnAt、rowAt 是各条格线在绑定空间的「轴 + 坐标」键。 */
    private record FaceGrid(SkinnedMesh.Vertex[][] vertices, long[] columnAt, long[] rowAt) { }
    private final List<SkinnedMesh.Surface> surfaces;

    public ArmorMesh(HumanoidModel<?> armor, PlayerAssets.Model player) {
        List<SkinnedMesh.Surface> result = new ArrayList<>();
        // 原版盔甲每槽一套六部件几何；hat 挂在 head 下，与 head 保留各自 UV 和显隐。
        for (BoneId region : new BoneId[]{BoneId.HEAD, BoneId.PELVIS, BoneId.R_UPPER_ARM,
                BoneId.L_UPPER_ARM, BoneId.R_THIGH, BoneId.L_THIGH}) {
            bakePart(part(armor, region, false), region, false, player, result);
        }
        bakePart(armor.head, BoneId.HEAD, true, player, result);
        surfaces = List.copyOf(result);
    }

    public static void clearCache() { CACHE.clear(); }

    public static ArmorMesh cached(HumanoidModel<?> armor, PlayerAssets.Model player) {
        // 两种体型各有实际渲染模型实例；资源重载后不能继续使用旧骨架权重。
        Cached cached = CACHE.get(armor);
        if (cached == null || cached.skeleton() != player.skeleton()) {
            cached = new Cached(player.skeleton(), new ArmorMesh(armor, player));
            CACHE.put(armor, cached);
        }
        return cached.mesh();
    }

    public List<SkinnedMesh.Surface> surfaces() { return surfaces; }

    public static boolean visible(HumanoidModel<?> armor, SkinnedMesh.Surface surface) {
        ModelPart part = part(armor, surface.bone(), false);
        if (!part.visible || part.skipDraw) {
            return false;
        }
        // 帽层是 head 的子节点，父节点可见时再看自己的开关。
        if (surface.outer()) {
            return armor.hat.visible && !armor.hat.skipDraw;
        }
        return true;
    }

    private static ModelPart part(HumanoidModel<?> armor, BoneId region, boolean hat) {
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
        part.visit(new PoseStack(), (ignored, path, index, cuboid) -> {
            // head 的自身方块与 hat 子节点分开烘焙；其余部件只有自身方块（皮肤外层子节点在盔甲里为空）。
            if (hat != HAT_PATH.equals(path)) {
                return;
            }
            // visit 的矩阵带当前动画；这里只读方块，绑定矩阵另取默认姿态。
            Matrix4f bind = bindTransform(part, path);
            if (region.arm()) {
                // 盔甲手臂对齐 PA 骨架的肩部枢轴（细臂肩部低半像素），盔甲本身仍保持四像素宽。
                Vec3 shoulder = player.skeleton().bone(region).pivot();
                PartPose original = part.getInitialPose();
                bind.m30(bind.m30() + shoulder.x() - original.x());
                bind.m31(bind.m31() + Rig.MODEL_ORIGIN_HEIGHT - shoulder.y() - original.y());
                bind.m32(bind.m32() + shoulder.z() - original.z());
            }
            QuadCollector collector = new QuadCollector();
            cuboid.compile(new PoseStack().last(), collector, 0, 0, -1);
            bakeCuboid(cuboid, collector.corners, bind, bindings, player.mesh(), vertices);
        });
        result.add(new SkinnedMesh.Surface(region, hat, List.copyOf(vertices)));
    }

    private static Matrix4f bindTransform(ModelPart root, String path) {
        Matrix4f result = new Matrix4f();
        appendBind(result, root.getInitialPose());
        // 原版部件没有子节点；仍按模型提供的默认层级读取，避免混入当前姿态。
        ModelPart current = root;
        if (!path.isEmpty()) {
            for (String name : path.substring(1).split("/")) {
                current = current.getChild(name);
                appendBind(result, current.getInitialPose());
            }
        }
        return result;
    }

    private static void appendBind(Matrix4f matrix, PartPose bind) {
        // 此处使用原版模型像素坐标（Y 向下），提交蒙皮前统一转换到骨架空间。
        matrix.translate(bind.x(), bind.y(), bind.z()).rotateZYX(bind.zRot(), bind.yRot(), bind.xRot());
    }

    private static void bakeCuboid(ModelPart.Cube cuboid, List<Corner> corners, Matrix4f bind,
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
        Placement placement = new Placement(bind, uninflate, bindings, skin);
        List<FaceGrid> grids = new ArrayList<>();
        for (int face = 0; face < corners.size(); face += 4) {
            grids.add(bakeGrid(corners.subList(face, face + 4), placement));
        }
        // 权重不变的一片像素合成一个四边形，和逐像素细分是同一张曲面；关节带里的格子保持一像素一格。
        // 要留的格线按「轴 + 坐标」记，同一方块的各个面共用，面与面的公共边上不出现丁字接头。
        Set<Long> cuts = new HashSet<>();
        for (FaceGrid grid : grids) {
            collectCuts(grid, cuts);
        }
        for (FaceGrid grid : grids) {
            emitFace(grid, cuts, result);
        }
    }

    private record Placement(Matrix4f bind, Matrix4f uninflate, List<Binding> bindings, SkinnedMesh skin) { }

    private static FaceGrid bakeGrid(List<Corner> quad, Placement placement) {
        Vector3f qa = transform(placement.uninflate(), quad.get(0).position());
        // 格点每原始像素一个，膝肘、腰胸、踝与脚尖切面都来自现有分段几何。
        int columns = Math.max(1, Math.round(qa.distance(transform(placement.uninflate(), quad.get(1).position()))));
        int rows = Math.max(1, Math.round(qa.distance(transform(placement.uninflate(), quad.get(3).position()))));
        SkinnedMesh.Vertex[][] vertices = new SkinnedMesh.Vertex[columns + 1][rows + 1];
        Vec3[][] references = new Vec3[columns + 1][rows + 1];
        for (int x = 0; x <= columns; x++) {
            for (int y = 0; y <= rows; y++) {
                float u = (float) x / columns;
                float v = (float) y / rows;
                references[x][y] = reference(quad, u, v, placement);
                vertices[x][y] = vertex(quad, u, v, references[x][y], placement);
            }
        }
        // 列方向沿哪根轴走，那根轴上的坐标就是各列格线的位置；行同理。
        int columnAxis = dominantAxis(references[0][0], references[columns][0]);
        int rowAxis = dominantAxis(references[0][0], references[0][rows]);
        long[] columnAt = new long[columns + 1];
        long[] rowAt = new long[rows + 1];
        for (int x = 0; x <= columns; x++) {
            columnAt[x] = cutKey(columnAxis, references[x][0].axis(columnAxis));
        }
        for (int y = 0; y <= rows; y++) {
            rowAt[y] = cutKey(rowAxis, references[0][y].axis(rowAxis));
        }
        return new FaceGrid(vertices, columnAt, rowAt);
    }

    private static int dominantAxis(Vec3 from, Vec3 to) {
        int dominant = 0;
        for (int axis = 1; axis < AXES; axis++) {
            if (Math.abs(to.axis(axis) - from.axis(axis)) > Math.abs(to.axis(dominant) - from.axis(dominant))) {
                dominant = axis;
            }
        }
        return dominant;
    }

    private static long cutKey(int axis, float coordinate) {
        return ((long) axis << Integer.SIZE) | (Math.round(coordinate * CUT_KEY_SCALE) & 0xFFFFFFFFL);
    }

    private static void collectCuts(FaceGrid grid, Set<Long> cuts) {
        SkinnedMesh.Vertex[][] vertices = grid.vertices();
        int columns = vertices.length - 1;
        int rows = vertices[0].length - 1;
        boolean bendsAcrossColumns = false;
        boolean bendsAcrossRows = false;
        for (int x = 0; x < columns; x++) {
            for (int y = 0; y <= rows; y++) {
                if (!Arrays.equals(vertices[x][y].weights(), vertices[x + 1][y].weights())) {
                    // 权重沿这一列变化：这一格是关节带，两侧格线都留。
                    cuts.add(grid.columnAt()[x]);
                    cuts.add(grid.columnAt()[x + 1]);
                    bendsAcrossColumns = true;
                    break;
                }
            }
        }
        for (int y = 0; y < rows; y++) {
            for (int x = 0; x <= columns; x++) {
                if (!Arrays.equals(vertices[x][y].weights(), vertices[x][y + 1].weights())) {
                    cuts.add(grid.rowAt()[y]);
                    cuts.add(grid.rowAt()[y + 1]);
                    bendsAcrossRows = true;
                    break;
                }
            }
        }
        // 关节带里的格子弯折后不是平面，另一个方向必须保持逐像素，才和原来的三角面一致。
        if (bendsAcrossRows) {
            for (long column : grid.columnAt()) {
                cuts.add(column);
            }
        }
        if (bendsAcrossColumns) {
            for (long row : grid.rowAt()) {
                cuts.add(row);
            }
        }
    }

    private static void emitFace(FaceGrid grid, Set<Long> cuts, List<SkinnedMesh.Vertex> output) {
        SkinnedMesh.Vertex[][] vertices = grid.vertices();
        int[] columns = keptLines(grid.columnAt(), cuts);
        int[] rows = keptLines(grid.rowAt(), cuts);
        for (int y = 0; y + 1 < rows.length; y++) {
            for (int x = 0; x + 1 < columns.length; x++) {
                output.add(vertices[columns[x]][rows[y]]);
                output.add(vertices[columns[x + 1]][rows[y]]);
                output.add(vertices[columns[x + 1]][rows[y + 1]]);
                output.add(vertices[columns[x]][rows[y + 1]]);
            }
        }
    }

    private static int[] keptLines(long[] lines, Set<Long> cuts) {
        // 首尾两条是面的边界，一定留；中间的只留登记过的。
        int last = lines.length - 1;
        int[] kept = new int[lines.length];
        int count = 0;
        for (int line = 0; line <= last; line++) {
            if (line == 0 || line == last || cuts.contains(lines[line])) {
                kept[count++] = line;
            }
        }
        return Arrays.copyOf(kept, count);
    }

    private static Vec3 reference(List<Corner> quad, float u, float v, Placement placement) {
        // 从膨胀后的实际顶点还原到未膨胀的绑定位置，权重和格线都按这个位置算。
        Vec3 point = point(quad, u, v);
        Vector3f reference = transform(placement.uninflate(), point);
        placement.bind().transformPosition(reference);
        return modelSpace(reference);
    }

    private static Vec3 point(List<Corner> quad, float u, float v) {
        return quad.get(0).position().lerp(quad.get(1).position(), u)
                .lerp(quad.get(3).position().lerp(quad.get(2).position(), u), v);
    }

    private static SkinnedMesh.Vertex vertex(List<Corner> quad, float u, float v, Vec3 q, Placement placement) {
        // 在原版四边形上插值位置与 UV，保留镜像、面绕序和纹理图集坐标。
        Corner a = quad.get(0);
        Corner b = quad.get(1);
        Corner c = quad.get(2);
        Corner d = quad.get(3);
        Matrix4f bind = placement.bind();
        List<Binding> bindings = placement.bindings();
        SkinnedMesh skin = placement.skin();
        Vec3 point = point(quad, u, v);
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
        return new SkinnedMesh.Vertex(modelSpace(transform(bind, point)),
                new Vec3(normal.x, -normal.y, normal.z), texU, texV, skin.weights(selected.bone(), q));
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

        @Override
        public void addVertex(float x, float y, float z, int color, float u, float v, int overlay, int light,
                              float normalX, float normalY, float normalZ) {
            // 原版提交方块单位，烘焙与玩家骨架统一使用像素单位；材质和光照由每次原版绘制传入。
            corners.add(new Corner(new Vec3(x * Rig.PIXELS_PER_BLOCK, y * Rig.PIXELS_PER_BLOCK,
                    z * Rig.PIXELS_PER_BLOCK), new Vec3(normalX, normalY, normalZ), u, v));
        }

        // 方块只走完整顶点入口；单属性入口不会被调用。
        @Override public VertexConsumer addVertex(float x, float y, float z) { throw unsupported(); }
        @Override public VertexConsumer setColor(int r, int g, int b, int a) { throw unsupported(); }
        @Override public VertexConsumer setColor(int color) { throw unsupported(); }
        @Override public VertexConsumer setUv(float u, float v) { throw unsupported(); }
        @Override public VertexConsumer setUv1(int u, int v) { throw unsupported(); }
        @Override public VertexConsumer setUv2(int u, int v) { throw unsupported(); }
        @Override public VertexConsumer setNormal(float x, float y, float z) { throw unsupported(); }
        @Override public VertexConsumer setLineWidth(float width) { throw unsupported(); }

        private static UnsupportedOperationException unsupported() {
            return new UnsupportedOperationException("盔甲烘焙只接受完整顶点");
        }
    }
}
