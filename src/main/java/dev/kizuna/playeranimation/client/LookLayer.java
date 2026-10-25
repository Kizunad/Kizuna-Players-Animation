package dev.kizuna.playeranimation.client;

import dev.kizuna.playeranimation.animation.AnimationClip;
import dev.kizuna.playeranimation.animation.AnimationEntry;
import dev.kizuna.playeranimation.model.BoneId;
import dev.kizuna.playeranimation.model.Pose;
import dev.kizuna.playeranimation.model.Vec3;
import java.util.Map;

/**
 * 加性视线层：把原版头部相对身体的偏航（±50°）与俯仰（±90°）按 head_follow / head_follow_pitch
 * 两段姿势空间剪辑分配到头、胸、腰和双臂。剪辑时间表示视线角度，取样结果直接加到已混合姿态上。
 */
public final class LookLayer {
    public static final String YAW_ID = "head_follow";
    public static final String PITCH_ID = "head_follow_pitch";
    /** 原版 LivingEntity.turnHead 保证头相对身体最多偏 50°。 */
    static final float MAX_RELATIVE_YAW_DEGREES = 50;
    /** Minecraft 视线俯仰范围，正值低头。 */
    static final float MAX_PITCH_DEGREES = 90;

    private LookLayer() { }

    public record Clips(AnimationEntry yaw, AnimationEntry pitch) { }

    public static Clips clips(Map<String, AnimationEntry> animations) {
        // 两段都存在且声明为 layer: look 才启用；资源包移除时保留原版视线，不混用半套。
        AnimationEntry yaw = animations.get(YAW_ID);
        AnimationEntry pitch = animations.get(PITCH_ID);
        if (yaw == null || pitch == null || !yaw.lookLayer() || !pitch.lookLayer()) {
            return null;
        }
        return new Clips(yaw, pitch);
    }

    /**
     * @param yawDegrees   原版头相对身体的偏航（度，等于模型空间头部 Y 旋转）
     * @param pitchDegrees 原版视线俯仰（度，正值低头）
     * @param pitchWeight  俯仰层权重；已有俯仰补偿的动作传 0，饮用末段传 0.5
     */
    public static void apply(Pose[] pose, Clips clips, float yawDegrees, float pitchDegrees, float pitchWeight) {
        float yaw = clamp(yawDegrees, MAX_RELATIVE_YAW_DEGREES);
        float pitch = clamp(pitchDegrees, MAX_PITCH_DEGREES);
        // 角度线性映射到 [0,1] 剪辑时间，零视线在剪辑中点。
        add(pose, clips.yaw(), (yaw + MAX_RELATIVE_YAW_DEGREES) / (2 * MAX_RELATIVE_YAW_DEGREES), 1);
        if (pitchWeight > 0) {
            add(pose, clips.pitch(), (pitch + MAX_PITCH_DEGREES) / (2 * MAX_PITCH_DEGREES), pitchWeight);
        }
    }

    private static void add(Pose[] pose, AnimationEntry entry, float progress, float weight) {
        AnimationClip clip = entry.clip();
        float time = progress * clip.duration();
        for (var track : clip.tracks().entrySet()) {
            if (track.getValue().rotation() == null) {
                continue;
            }
            // Bedrock 导出值的 X/Y 反号，恢复为 Blockbench 面板值后再相加。
            Vec3 raw = track.getValue().rotation().sample(time);
            BoneId bone = track.getKey();
            Pose lower = pose[bone.ordinal()];
            Vec3 rotation = lower.rotation();
            pose[bone.ordinal()] = new Pose(lower.position(), new Vec3(rotation.x() - raw.x() * weight,
                    rotation.y() - raw.y() * weight, rotation.z() + raw.z() * weight));
        }
    }

    private static float clamp(float value, float limit) {
        return Math.max(-limit, Math.min(limit, value));
    }
}
