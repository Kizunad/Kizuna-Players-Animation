package dev.kizuna.playeranimation.model;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.joml.Matrix4f;
import org.joml.Vector3f;

public final class SkinnedMesh {
    private static final float JOIN_EPSILON = 0.0001f;
    private static final float BEND_BAND_PIXELS = 1;
    private static final int QUARTER_TURN_DEGREES = 90;
    private static final float CUT_KEY_SCALE = 1024;
    private static final int AXES = 3;
    /** bones 是 weights 里非零项的骨序号，逐帧变形只走这几根骨。 */
    public record Vertex(Vec3 position, Vec3 normal, float u, float v, float[] weights, int[] bones) {
        public Vertex(Vec3 position, Vec3 normal, float u, float v, float[] weights) {
            this(position, normal, u, v, weights, influences(weights));
        }
    }
    public record Surface(BoneId bone, boolean outer, List<Vertex> vertices, String part) {
        public Surface(BoneId bone, boolean outer, List<Vertex> vertices) {
            this(bone, outer, vertices, "");
        }
    }
    private record Seam(int axis, float plane, int direction) { }
    private record Bounds(Vec3 min, Vec3 max) { }
    private record FaceBake(BoneId bone, Skeleton.Cube cube, Skeleton.Face face, Skeleton.Uv uv) { }
    /** 一个面按像素格点烘焙好的全部顶点，[列][行]；horizontal、vertical 是这两个方向对应的方块轴。 */
    private record FaceGrid(Skeleton.Face face, int horizontal, int vertical, Vertex[][] vertices, float[] columnAt,
                            float[] rowAt) { }
    private final Map<BoneId, Seam> seams = new EnumMap<>(BoneId.class);
    private final Map<String, Bounds> skinBounds = new HashMap<>();
    private final List<Surface> surfaces;
    private final int textureWidth;
    private final int textureHeight;

    public SkinnedMesh(Skeleton skeleton) {
        // 膨胀以完整原版部件为基准，使分段后的 UV 仍对应原版外层的同一像素位置。
        textureWidth = skeleton.textureWidth();
        textureHeight = skeleton.textureHeight();
        collectSkinBounds(skeleton);
        collectSeams(skeleton);
        Map<Skeleton.Cube, List<FaceGrid>> grids = bakeGrids(skeleton);
        surfaces = bakeSurfaces(skeleton, grids, collectCuts(skeleton, grids));
    }

    private void collectSkinBounds(Skeleton skeleton) {
        // 原版内层共享完整部件边界，实体几何也允许独立声明膨胀边界。
        for (Skeleton.Bone bone : skeleton.bones()) {
            for (Skeleton.Cube cube : bone.cubes()) {
                if (cube.inflate() != 0 && cube.placement() == null) {
                    continue;
                }
                String part = bone.id().skinPart();
                Bounds previous = skinBounds.get(part);
                Vec3 cubeMin = cube.min();
                Vec3 cubeMax = new Vec3(cube.max(0), cube.max(1), cube.max(2));
                if (previous != null) {
                    Vec3 mergedMin = new Vec3(Math.min(cubeMin.x(), previous.min.x()),
                            Math.min(cubeMin.y(), previous.min.y()), Math.min(cubeMin.z(), previous.min.z()));
                    Vec3 mergedMax = new Vec3(Math.max(cubeMax.x(), previous.max.x()),
                            Math.max(cubeMax.y(), previous.max.y()), Math.max(cubeMax.z(), previous.max.z()));
                    skinBounds.put(part, new Bounds(mergedMin, mergedMax));
                } else {
                    skinBounds.put(part, new Bounds(cubeMin, cubeMax));
                }
            }
        }
    }

    private void collectSeams(Skeleton skeleton) {
        // 只连接同一原版皮肤部件的分段，头、肩、胯仍保留原版部件边界。
        for (Skeleton.Bone bone : skeleton.bones()) {
            BoneId parent = bone.id().parent;
            if (parent == null || !parent.skinPart().equals(bone.id().skinPart())) {
                continue;
            }
            for (Skeleton.Cube cube : bone.cubes()) {
                if (cube.inflate() != 0 && cube.placement() == null) {
                    continue;
                }
                for (Skeleton.Cube parentCube : skeleton.bone(parent).cubes()) {
                    if (parentCube.inflate() == 0 || parentCube.placement() != null) {
                        Seam seam = touching(cube, parentCube);
                        if (seam != null) {
                            seams.put(bone.id(), seam);
                        }
                    }
                }
            }
        }
    }

    private Map<Skeleton.Cube, List<FaceGrid>> bakeGrids(Skeleton skeleton) {
        // 先按像素格点把每个面的顶点全算出来，后面只决定留哪些格线。
        Map<Skeleton.Cube, List<FaceGrid>> grids = new IdentityHashMap<>();
        for (Skeleton.Bone bone : skeleton.bones()) {
            for (Skeleton.Cube cube : bone.cubes()) {
                List<FaceGrid> faces = new ArrayList<>();
                for (var face : cube.faces().entrySet()) {
                    faces.add(bakeGrid(bone.id(), cube, face.getKey(), face.getValue()));
                }
                grids.put(cube, faces);
            }
        }
        return grids;
    }

    private static Map<String, List<Set<Integer>>> collectCuts(Skeleton skeleton,
                                                               Map<Skeleton.Cube, List<FaceGrid>> grids) {
        // 权重不变的一片像素合成一个四边形，和逐像素细分是同一张曲面；关节带里的格子保持一像素一格。
        // 要留的格线按「皮肤部件 + 轴 + 坐标」记，同一部件里相邻的面和方块用同一组格线，边上不出现丁字接头。
        Map<String, List<Set<Integer>>> cuts = new HashMap<>();
        for (Skeleton.Bone bone : skeleton.bones()) {
            List<Set<Integer>> part = cuts.computeIfAbsent(bone.id().skinPart(), ignored -> {
                List<Set<Integer>> axes = new ArrayList<>();
                for (int axis = 0; axis < AXES; axis++) {
                    axes.add(new HashSet<>());
                }
                return axes;
            });
            for (Skeleton.Cube cube : bone.cubes()) {
                for (FaceGrid grid : grids.get(cube)) {
                    collectFaceCuts(grid, part.get(grid.horizontal()), part.get(grid.vertical()));
                }
            }
        }
        return cuts;
    }

    private static void collectFaceCuts(FaceGrid grid, Set<Integer> columnCuts, Set<Integer> rowCuts) {
        Vertex[][] vertices = grid.vertices();
        int columns = vertices.length - 1;
        if (columns < 1) {
            return;
        }
        int rows = vertices[0].length - 1;
        if (rows < 1) {
            return;
        }
        // 方块自己的边界也记下来，别的方块在这个位置有格线时保留。
        columnCuts.add(cutKey(grid.columnAt()[0]));
        columnCuts.add(cutKey(grid.columnAt()[columns]));
        rowCuts.add(cutKey(grid.rowAt()[0]));
        rowCuts.add(cutKey(grid.rowAt()[rows]));
        boolean bendsAcrossColumns = false;
        boolean bendsAcrossRows = false;
        for (int x = 0; x < columns; x++) {
            for (int y = 0; y <= rows; y++) {
                if (!Arrays.equals(vertices[x][y].weights(), vertices[x + 1][y].weights())) {
                    // 权重沿这一列变化：这一格是关节带，两侧格线都留。
                    columnCuts.add(cutKey(grid.columnAt()[x]));
                    columnCuts.add(cutKey(grid.columnAt()[x + 1]));
                    bendsAcrossColumns = true;
                    break;
                }
            }
        }
        for (int y = 0; y < rows; y++) {
            for (int x = 0; x <= columns; x++) {
                if (!Arrays.equals(vertices[x][y].weights(), vertices[x][y + 1].weights())) {
                    rowCuts.add(cutKey(grid.rowAt()[y]));
                    rowCuts.add(cutKey(grid.rowAt()[y + 1]));
                    bendsAcrossRows = true;
                    break;
                }
            }
        }
        // 关节带里的格子弯折后不是平面，另一个方向必须保持逐像素，才和原来的三角面一致。
        if (bendsAcrossRows) {
            for (float column : grid.columnAt()) {
                columnCuts.add(cutKey(column));
            }
        }
        if (bendsAcrossColumns) {
            for (float row : grid.rowAt()) {
                rowCuts.add(cutKey(row));
            }
        }
    }

    private static int cutKey(float coordinate) {
        return Math.round(coordinate * CUT_KEY_SCALE);
    }

    private static List<Surface> bakeSurfaces(Skeleton skeleton, Map<Skeleton.Cube, List<FaceGrid>> grids,
                                              Map<String, List<Set<Integer>>> cuts) {
        // 保留原版子部件路径，供服饰层按原版模型控制显隐。
        List<Surface> baked = new ArrayList<>();
        for (Skeleton.Bone bone : skeleton.bones()) {
            List<Set<Integer>> kept = cuts.get(bone.id().skinPart());
            for (Skeleton.Cube cube : bone.cubes()) {
                List<Vertex> vertices = new ArrayList<>();
                for (FaceGrid grid : grids.get(cube)) {
                    emitFace(grid, kept.get(grid.horizontal()), kept.get(grid.vertical()), vertices);
                }
                String part = "";
                if (cube.placement() != null) {
                    part = cube.placement().part();
                }
                baked.add(new Surface(bone.id(), cube.inflate() > 0, List.copyOf(vertices), part));
            }
        }
        return List.copyOf(baked);
    }

    public List<Surface> surfaces() { return surfaces; }

    private static Seam touching(Skeleton.Cube child, Skeleton.Cube parent) {
        // 切口必须是共面且在另外两轴有正面积交叠，单个角点接触不算连接。
        for (int axis = 0; axis < 3; axis++) {
            int next = (axis + 1) % 3;
            int last = (axis + 2) % 3;
            if (Math.min(child.max(next), parent.max(next)) <= Math.max(child.min().axis(next), parent.min().axis(next))
                    || Math.min(child.max(last), parent.max(last))
                    <= Math.max(child.min().axis(last), parent.min().axis(last))) {
                continue;
            }
            if (Math.abs(child.min().axis(axis) - parent.max(axis)) < JOIN_EPSILON) {
                return new Seam(axis, parent.max(axis), 1);
            }
            if (Math.abs(child.max(axis) - parent.min().axis(axis)) < JOIN_EPSILON) {
                return new Seam(axis, parent.min().axis(axis), -1);
            }
        }
        return null;
    }

    public float[] weights(BoneId bone, Vec3 point) {
        float[] weights = new float[BoneId.ALL.length];
        Seam seam = seams.get(bone);
        // 切口顶点继承父骨在同一位置的权重；递归保留脚踝和脚尖相接处的连续性。
        if (seam == null) {
            weights[bone.ordinal()] = 1;
            return weights;
        }
        float amount = Math.max(0, Math.min(1, (point.axis(seam.axis) - seam.plane)
                * seam.direction / BEND_BAND_PIXELS));
        if (amount < 1) {
            weights = weights(bone.parent, point);
            for (int i = 0; i < weights.length; i++) {
                weights[i] *= 1 - amount;
            }
        }
        weights[bone.ordinal()] += amount;
        return weights;
    }

    private FaceGrid bakeGrid(BoneId bone, Skeleton.Cube cube, Skeleton.Face face, Skeleton.Uv uv) {
        // 格点一像素一个，关节带内有足够顶点变形；UV 仍线性采样原有矩形。
        int horizontal = switch (face) { case EAST, WEST -> 2; default -> 0; };
        int vertical = switch (face) { case UP, DOWN -> 2; default -> 1; };
        int columns = (int) Math.ceil(cube.size().axis(horizontal));
        int rows = (int) Math.ceil(cube.size().axis(vertical));
        FaceBake faceBake = new FaceBake(bone, cube, face, uv);
        Vertex[][] vertices = new Vertex[columns + 1][rows + 1];
        float[] columnAt = new float[columns + 1];
        float[] rowAt = new float[rows + 1];
        if (columns < 1 || rows < 1) {
            // 零厚度平面的侧面没有面积，不出顶点。
            return new FaceGrid(face, horizontal, vertical, new Vertex[0][0], columnAt, rowAt);
        }
        for (int x = 0; x <= columns; x++) {
            columnAt[x] = bindPoint(cube, facePoint(face, (float) x / columns, 0)).axis(horizontal);
            for (int y = 0; y <= rows; y++) {
                vertices[x][y] = vertex(faceBake, (float) x / columns, (float) y / rows);
            }
        }
        for (int y = 0; y <= rows; y++) {
            rowAt[y] = bindPoint(cube, facePoint(face, 0, (float) y / rows)).axis(vertical);
        }
        return new FaceGrid(face, horizontal, vertical, vertices, columnAt, rowAt);
    }

    private static void emitFace(FaceGrid grid, Set<Integer> columnCuts, Set<Integer> rowCuts, List<Vertex> output) {
        Vertex[][] vertices = grid.vertices();
        if (vertices.length == 0) {
            return;
        }
        int[] columns = keptLines(grid.columnAt(), columnCuts);
        int[] rows = keptLines(grid.rowAt(), rowCuts);
        for (int y = 0; y + 1 < rows.length; y++) {
            for (int x = 0; x + 1 < columns.length; x++) {
                // 顺序在 Java 模型的 Y 反射后保持外表面朝向一致。
                output.add(vertices[columns[x]][rows[y]]);
                output.add(vertices[columns[x + 1]][rows[y]]);
                output.add(vertices[columns[x + 1]][rows[y + 1]]);
                output.add(vertices[columns[x]][rows[y + 1]]);
            }
        }
    }

    private static int[] keptLines(float[] coordinates, Set<Integer> cuts) {
        // 首尾两条是面的边界，一定留；中间的只留登记过的。
        int last = coordinates.length - 1;
        int[] kept = new int[coordinates.length];
        int count = 0;
        for (int line = 0; line <= last; line++) {
            if (line == 0 || line == last || cuts.contains(cutKey(coordinates[line]))) {
                kept[count++] = line;
            }
        }
        return Arrays.copyOf(kept, count);
    }

    private static Vec3 bindPoint(Skeleton.Cube cube, Vec3 unit) {
        return new Vec3(cube.min().x() + cube.size().x() * unit.x(),
                cube.min().y() + cube.size().y() * unit.y(), cube.min().z() + cube.size().z() * unit.z());
    }

    private static int[] influences(float[] weights) {
        int count = 0;
        for (float weight : weights) {
            if (weight != 0) {
                count++;
            }
        }
        int[] bones = new int[count];
        int next = 0;
        for (int bone = 0; bone < weights.length; bone++) {
            if (weights[bone] != 0) {
                bones[next++] = bone;
            }
        }
        return bones;
    }

    private static Vec3 facePoint(Skeleton.Face face, float u, float v) {
        // 各面坐标遵循 Blockbench 逐面 UV 的观看方向，前后左右分别裁切原始皮肤。
        return switch (face) {
            case NORTH -> new Vec3(1 - u, 1 - v, 0);
            case SOUTH -> new Vec3(u, 1 - v, 1);
            case WEST -> new Vec3(0, 1 - v, u);
            case EAST -> new Vec3(1, 1 - v, 1 - u);
            case UP -> new Vec3(u, 1, v);
            case DOWN -> new Vec3(u, 0, 1 - v);
        };
    }

    private Vertex vertex(FaceBake faceBake, float u, float v) {
        // 权重取未膨胀坐标；静态变换仅作用于烘焙后的位置与法线。
        BoneId bone = faceBake.bone();
        Skeleton.Cube cube = faceBake.cube();
        Skeleton.Uv uv = faceBake.uv();
        Vec3 unit = facePoint(faceBake.face(), u, v);
        Vec3 normal = switch (faceBake.face()) {
            case NORTH -> new Vec3(0, 0, -1); case SOUTH -> new Vec3(0, 0, 1);
            case WEST -> new Vec3(-1, 0, 0); case EAST -> new Vec3(1, 0, 0);
            case UP -> new Vec3(0, 1, 0); case DOWN -> new Vec3(0, -1, 0);
        };
        Vec3 point = bindPoint(cube, unit);
        Vector3f bakedPosition = inflated(bone, cube, point);
        float textureU = u;
        float textureV = v;
        for (int turn = 0; turn < uv.rotation() / QUARTER_TURN_DEGREES; turn++) {
            float previousU = textureU;
            textureU = textureV;
            textureV = 1 - previousU;
        }
        Vector3f bakedNormal = new Vector3f(normal.x(), normal.y(), normal.z());
        Skeleton.Placement placement = cube.placement();
        if (placement != null) {
            Matrix4f transform = placement.transform();
            transform.transformPosition(bakedPosition);
            transform.transformDirection(bakedNormal);
        }
        return new Vertex(new Vec3(bakedPosition.x, bakedPosition.y, bakedPosition.z),
                new Vec3(bakedNormal.x, bakedNormal.y, bakedNormal.z),
                (uv.u() + uv.width() * textureU) / textureWidth,
                (uv.v() + uv.height() * textureV) / textureHeight,
                weights(bone, point));
    }

    private Vector3f inflated(BoneId bone, Skeleton.Cube cube, Vec3 point) {
        // 完整部件两端各外扩指定像素；内部切口按同一比例移动，既不重复也不漏缝。
        if (cube.inflate() == 0) {
            return new Vector3f(point.x(), point.y(), point.z());
        }
        Bounds bounds = skinBounds.get(bone.skinPart());
        Skeleton.Placement placement = cube.placement();
        if (placement != null) {
            Vec3 min = placement.min();
            Vec3 size = placement.size();
            bounds = new Bounds(min, new Vec3(min.x() + size.x(), min.y() + size.y(), min.z() + size.z()));
        }
        float[] inflated = new float[3];
        for (int axis = 0; axis < 3; axis++) {
            float partMin = bounds.min.axis(axis);
            float partSize = bounds.max.axis(axis) - partMin;
            float coordinate = point.axis(axis);
            float fraction = (coordinate - partMin) / partSize;
            inflated[axis] = coordinate + cube.inflate() * (2 * fraction - 1);
        }
        return new Vector3f(inflated[0], inflated[1], inflated[2]);
    }

    public static Vector3f deform(Vec3 point, float[] weights, Matrix4f[] matrices, boolean normal, Vector3f result) {
        result.zero();
        Vector3f transformed = new Vector3f();
        // 蒙皮矩阵只包含刚体变换，法线只取旋转部分，禁止混入位移。
        for (int i = 0; i < weights.length; i++) {
            if (weights[i] == 0) {
                continue;
            }
            transformed.set(point.x(), point.y(), point.z());
            if (normal) {
                matrices[i].transformDirection(transformed);
            } else {
                matrices[i].transformPosition(transformed);
            }
            result.fma(weights[i], transformed);
        }
        if (normal) {
            result.normalize();
        }
        return result;
    }

    /** 同上，但只走顶点记下的非零权重骨；运算顺序和稠密版一致，结果逐位相同。 */
    public static Vector3f deform(Vertex vertex, Matrix4f[] matrices, boolean normal, Vector3f result) {
        Vec3 point = normal ? vertex.normal() : vertex.position();
        float[] weights = vertex.weights();
        result.zero();
        Vector3f transformed = new Vector3f();
        for (int bone : vertex.bones()) {
            transformed.set(point.x(), point.y(), point.z());
            if (normal) {
                matrices[bone].transformDirection(transformed);
            } else {
                matrices[bone].transformPosition(transformed);
            }
            result.fma(weights[bone], transformed);
        }
        if (normal) {
            result.normalize();
        }
        return result;
    }
}
