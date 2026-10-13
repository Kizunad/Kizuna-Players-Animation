package dev.kizuna.playeranimation.model;

import java.util.Arrays;
import org.joml.Matrix4f;
import org.joml.Vector3f;

public final class Rig {
    public static final float PIXELS_PER_BLOCK = 16;
    public static final float MODEL_ORIGIN_HEIGHT = 24;
    private static final float RADIANS_PER_DEGREE = (float) (Math.PI / 180);

    private Rig() { }

    public static Pose[] zeroPose() {
        Pose[] pose = new Pose[BoneId.ALL.length];
        Arrays.fill(pose, Pose.ZERO);
        return pose;
    }

    public static Matrix4f[] matrices(Skeleton skeleton, Pose[] pose) {
        Matrix4f[] matrices = new Matrix4f[BoneId.ALL.length];
        // 模型顶点位于全局绑定坐标，因此每节是父变换乘绕自身绝对枢轴的增量。
        for (BoneId bone : BoneId.ALL) {
            Vec3 pivot = skeleton.bone(bone).pivot();
            Pose value = pose[bone.ordinal()];
            Matrix4f matrix = bone.parent == null ? new Matrix4f() : new Matrix4f(matrices[bone.parent.ordinal()]);
            matrix.translate(value.position().x(), value.position().y(), value.position().z());
            matrix.translate(pivot.x(), pivot.y(), pivot.z());
            matrix.rotateZYX(value.rotation().z() * RADIANS_PER_DEGREE,
                    value.rotation().y() * RADIANS_PER_DEGREE, value.rotation().x() * RADIANS_PER_DEGREE);
            matrix.translate(-pivot.x(), -pivot.y(), -pivot.z());
            matrices[bone.ordinal()] = matrix;
        }
        return matrices;
    }

    public static Pose localPose(Matrix4f local, Vec3 pivot) {
        // 从原版部件的绝对变换恢复本骨局部偏移，使原版姿态作为分层的真实底层。
        Vector3f moved = local.transformPosition(pivot.x(), pivot.y(), pivot.z(), new Vector3f());
        // 游戏内 JOML 1.10.5 的四元数 ZYX 提取存在 X 分量错误，直接从旋转矩阵求解。
        float cosineY = (float) Math.hypot(local.m00(), local.m01());
        float rotationY = (float) Math.atan2(-local.m02(), cosineY);
        float rotationX;
        float rotationZ;
        if (cosineY > 0.000001f) {
            rotationX = (float) Math.atan2(local.m12(), local.m22());
            rotationZ = (float) Math.atan2(local.m01(), local.m00());
        } else {
            // 万向锁处 X/Z 不唯一，固定 Z 为零仍可还原同一空间变换。
            rotationX = (float) Math.atan2(-local.m21(), local.m11());
            rotationZ = 0;
        }
        return new Pose(new Vec3(moved.x - pivot.x(), moved.y - pivot.y(), moved.z - pivot.z()),
                new Vec3(rotationX / RADIANS_PER_DEGREE, rotationY / RADIANS_PER_DEGREE, rotationZ / RADIANS_PER_DEGREE));
    }

    public static Matrix4f toVanilla(Matrix4f matrix) {
        // C * M * C^-1：同时转换像素单位、脚底原点和 Java 模型向下的 Y 轴。
        Matrix4f conversion = new Matrix4f().translate(0, MODEL_ORIGIN_HEIGHT / PIXELS_PER_BLOCK, 0)
                .scale(1 / PIXELS_PER_BLOCK, -1 / PIXELS_PER_BLOCK, 1 / PIXELS_PER_BLOCK);
        return new Matrix4f(conversion).mul(matrix).mul(new Matrix4f(conversion).invert());
    }

    public static Matrix4f itemTransform(Skeleton skeleton, Matrix4f[] matrices, BoneId socket) {
        Vec3 pivot = skeleton.bone(socket).pivot();
        // 挂点继承完整动画层级，只在这里叠加原版第三人称物品的坐标方向。
        return toVanilla(matrices[socket.ordinal()])
                .translate(pivot.x() / PIXELS_PER_BLOCK, (MODEL_ORIGIN_HEIGHT - pivot.y()) / PIXELS_PER_BLOCK,
                        pivot.z() / PIXELS_PER_BLOCK)
                .rotateX(-(float) Math.PI / 2).rotateY((float) Math.PI);
    }
}
