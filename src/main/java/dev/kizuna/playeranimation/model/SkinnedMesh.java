package dev.kizuna.playeranimation.model;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.joml.Matrix4f;
import org.joml.Vector3f;

public final class SkinnedMesh {
    private static final float JOIN_EPSILON = 0.0001f;
    private static final float BEND_BAND_PIXELS = 1;
    public record Vertex(Vec3 position, Vec3 normal, float u, float v, float[] weights) { }
    public record Surface(BoneId bone, boolean outer, List<Vertex> vertices) { }
    private record Seam(int axis, float plane, int direction) { }
    private record Bounds(Vec3 min, Vec3 max) { }
    private final Map<BoneId, Seam> seams = new EnumMap<>(BoneId.class);
    private final Map<String, Bounds> skinBounds = new HashMap<>();
    private final List<Surface> surfaces;

    public SkinnedMesh(Skeleton skeleton) {
        // 膨胀以完整原版部件为基准，使分段后的 UV 仍对应原版外层的同一像素位置。
        for (Skeleton.Bone bone : skeleton.bones()) {
            for (Skeleton.Cube cube : bone.cubes()) {
                if (cube.inflate() != 0) {
                    continue;
                }
                String part = bone.id().skinPart();
                Bounds previous = skinBounds.get(part);
                Vec3 min = cube.min();
                Vec3 max = new Vec3(cube.max(0), cube.max(1), cube.max(2));
                if (previous != null) {
                    min = new Vec3(Math.min(min.x(), previous.min.x()), Math.min(min.y(), previous.min.y()),
                            Math.min(min.z(), previous.min.z()));
                    max = new Vec3(Math.max(max.x(), previous.max.x()), Math.max(max.y(), previous.max.y()),
                            Math.max(max.z(), previous.max.z()));
                }
                skinBounds.put(part, new Bounds(min, max));
            }
        }
        // 只连接同一原版皮肤部件的分段，头、肩、胯仍保留原版部件边界。
        for (Skeleton.Bone bone : skeleton.bones()) {
            BoneId parent = bone.id().parent;
            if (parent == null || !parent.skinPart().equals(bone.id().skinPart())) {
                continue;
            }
            for (Skeleton.Cube cube : bone.cubes()) {
                if (cube.inflate() != 0) {
                    continue;
                }
                for (Skeleton.Cube parentCube : skeleton.bone(parent).cubes()) {
                    if (parentCube.inflate() == 0) {
                        Seam seam = touching(cube, parentCube);
                        if (seam != null) {
                            seams.put(bone.id(), seam);
                        }
                    }
                }
            }
        }
        List<Surface> baked = new ArrayList<>();
        for (Skeleton.Bone bone : skeleton.bones()) {
            for (Skeleton.Cube cube : bone.cubes()) {
                List<Vertex> vertices = new ArrayList<>();
                for (var face : cube.faces().entrySet()) {
                    bakeFace(bone.id(), cube, face.getKey(), face.getValue(), vertices);
                }
                baked.add(new Surface(bone.id(), cube.inflate() > 0, List.copyOf(vertices)));
            }
        }
        surfaces = List.copyOf(baked);
    }

    public List<Surface> surfaces() { return surfaces; }

    private static Seam touching(Skeleton.Cube child, Skeleton.Cube parent) {
        // 切口必须是共面且在另外两轴有正面积交叠，单个角点接触不算连接。
        for (int axis = 0; axis < 3; axis++) {
            int next = (axis + 1) % 3;
            int last = (axis + 2) % 3;
            if (Math.min(child.max(next), parent.max(next)) <= Math.max(child.min().axis(next), parent.min().axis(next))
                    || Math.min(child.max(last), parent.max(last)) <= Math.max(child.min().axis(last), parent.min().axis(last))) {
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

    private void bakeFace(BoneId bone, Skeleton.Cube cube, Skeleton.Face face, Skeleton.Uv uv, List<Vertex> vertices) {
        // 每像素细分一格，关节带内有足够顶点变形；UV 仍线性采样原有矩形。
        int horizontal = switch (face) { case EAST, WEST -> 2; default -> 0; };
        int vertical = switch (face) { case UP, DOWN -> 2; default -> 1; };
        int columns = (int) Math.ceil(cube.size().axis(horizontal));
        int rows = (int) Math.ceil(cube.size().axis(vertical));
        for (int y = 0; y < rows; y++) {
            for (int x = 0; x < columns; x++) {
                // 顺序在 Java 模型的 Y 反射后保持外表面朝向一致。
                vertex(bone, cube, face, uv, (float) x / columns, (float) y / rows, vertices);
                vertex(bone, cube, face, uv, (float) (x + 1) / columns, (float) y / rows, vertices);
                vertex(bone, cube, face, uv, (float) (x + 1) / columns, (float) (y + 1) / rows, vertices);
                vertex(bone, cube, face, uv, (float) x / columns, (float) (y + 1) / rows, vertices);
            }
        }
    }

    private void vertex(BoneId bone, Skeleton.Cube cube, Skeleton.Face face, Skeleton.Uv uv,
                        float u, float v, List<Vertex> vertices) {
        // 各面坐标遵循 Blockbench 逐面 UV 的观看方向，前后左右分别裁切原始皮肤。
        Vec3 unit = switch (face) {
            case NORTH -> new Vec3(1 - u, 1 - v, 0);
            case SOUTH -> new Vec3(u, 1 - v, 1);
            case WEST -> new Vec3(0, 1 - v, u);
            case EAST -> new Vec3(1, 1 - v, 1 - u);
            case UP -> new Vec3(u, 1, v);
            case DOWN -> new Vec3(u, 0, 1 - v);
        };
        Vec3 normal = switch (face) {
            case NORTH -> new Vec3(0, 0, -1); case SOUTH -> new Vec3(0, 0, 1);
            case WEST -> new Vec3(-1, 0, 0); case EAST -> new Vec3(1, 0, 0);
            case UP -> new Vec3(0, 1, 0); case DOWN -> new Vec3(0, -1, 0);
        };
        float[] point = new float[3];
        float[] inflated = new float[3];
        Bounds bounds = skinBounds.get(bone.skinPart());
        for (int axis = 0; axis < 3; axis++) {
            float min = cube.min().axis(axis);
            point[axis] = min + cube.size().axis(axis) * unit.axis(axis);
            float partMin = bounds.min.axis(axis);
            float partSize = bounds.max.axis(axis) - partMin;
            float fraction = (point[axis] - partMin) / partSize;
            // 完整部件两端各外扩指定像素；内部切口按同一比例移动，既不重复也不漏缝。
            inflated[axis] = point[axis] + cube.inflate() * (2 * fraction - 1);
        }
        float textureU = u;
        float textureV = v;
        for (int turn = 0; turn < uv.rotation() / 90; turn++) {
            float previousU = textureU;
            textureU = textureV;
            textureV = 1 - previousU;
        }
        vertices.add(new Vertex(new Vec3(inflated[0], inflated[1], inflated[2]), normal,
                (uv.u() + uv.width() * textureU) / 64, (uv.v() + uv.height() * textureV) / 64,
                weights(bone, new Vec3(point[0], point[1], point[2]))));
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
}
