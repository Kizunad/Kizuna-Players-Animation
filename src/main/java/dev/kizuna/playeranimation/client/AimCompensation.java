package dev.kizuna.playeranimation.client;

import dev.kizuna.playeranimation.animation.AimWindow;
import dev.kizuna.playeranimation.animation.AnimationPlayer;
import dev.kizuna.playeranimation.model.BoneId;
import dev.kizuna.playeranimation.model.Pose;
import dev.kizuna.playeranimation.model.Rig;
import dev.kizuna.playeranimation.model.Skeleton;
import dev.kizuna.playeranimation.model.Vec3;
import org.joml.Matrix4f;

/**
 * 索引 aim_windows 的瞄准补偿（行矢各式，docs/asset-contract.md）。资源按平视制作：
 * PITCH 时段把视线俯仰按权重加到头部，双臂绕胸坐标系两肩枢轴连线刚性转同一角度（同 bow_draw）；
 * PITCH、YAW 两种时段都按权重压掉视线层俯仰，偏航照常由视线层给。纯姿态数学，不读 Minecraft 状态。
 */
final class AimCompensation {
    /** pitch：俯仰补偿权重；lookPitchScale：视线层俯仰权重要乘的系数。 */
    record Weights(float pitch, float lookPitchScale) {
        static final Weights NONE = new Weights(0, 1);
    }

    private AimCompensation() { }

    static Weights weights(AnimationPlayer animation, double now) {
        // 时段权重乘播放实例的淡入淡出权重；多段同时生效时各取最大，俯仰补偿只看 PITCH 段。
        float pitch = 0;
        float suppressed = 0;
        for (AnimationPlayer.ActivePlayback active : animation.active(now)) {
            for (AimWindow window : active.entry().aimWindows()) {
                float weight = active.weight() * window.weight(active.time());
                suppressed = Math.max(suppressed, weight);
                if (window.mode() == AimWindow.Mode.PITCH) {
                    pitch = Math.max(pitch, weight);
                }
            }
        }
        return new Weights(pitch, 1 - suppressed);
    }

    /**
     * @param weight 俯仰补偿权重（{@link Weights#pitch()}）
     * @param pitch  原版视线俯仰（度，正值低头）
     */
    static void apply(Pose[] pose, Skeleton skeleton, float weight, float pitch) {
        if (weight <= 0) {
            return;
        }
        // Minecraft 正俯仰表示低头；模型空间 Y 向上，X 旋转取反。
        float modelPitch = -pitch * weight;
        pose[BoneId.HEAD.ordinal()] = PlayerMeshRenderer.withPitch(pose[BoneId.HEAD.ordinal()], modelPitch);
        turnArms(pose, skeleton, modelPitch);
    }

    /**
     * 双臂绕两肩枢轴中点、在胸坐标系里绕 X 轴刚性转 modelPitch 度（度，模型空间正值抬头），
     * 两手相对位置不变：望远镜托掌、拉弓弦手都靠它保持接触；分别给两上臂加 X 欧拉角会因 Y/Z 不同而拉开。
     */
    static void turnArms(Pose[] pose, Skeleton skeleton, float modelPitch) {
        Vec3 right = skeleton.bone(BoneId.R_UPPER_ARM).pivot();
        Vec3 left = skeleton.bone(BoneId.L_UPPER_ARM).pivot();
        Vec3 center = right.lerp(left, 0.5f);
        float radians = (float) (Math.PI / 180);
        Matrix4f turn = new Matrix4f().translate(center.x(), center.y(), center.z())
                .rotateX(modelPitch * radians).translate(-center.x(), -center.y(), -center.z());
        for (BoneId upper : new BoneId[]{BoneId.R_UPPER_ARM, BoneId.L_UPPER_ARM}) {
            Vec3 pivot = skeleton.bone(upper).pivot();
            Pose arm = pose[upper.ordinal()];
            Matrix4f local = new Matrix4f(turn)
                    .translate(arm.position().x(), arm.position().y(), arm.position().z())
                    .translate(pivot.x(), pivot.y(), pivot.z())
                    .rotateZYX(arm.rotation().z() * radians, arm.rotation().y() * radians,
                            arm.rotation().x() * radians)
                    .translate(-pivot.x(), -pivot.y(), -pivot.z());
            pose[upper.ordinal()] = Rig.localPose(local, pivot);
        }
    }
}
