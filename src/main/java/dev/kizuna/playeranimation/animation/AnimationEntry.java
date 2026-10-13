package dev.kizuna.playeranimation.animation;

import com.google.gson.JsonObject;
import dev.kizuna.playeranimation.model.BoneId;

public record AnimationEntry(String id, String file, Mask mask, int priority, float blendIn,
                             float blendOut, Hand hand, boolean firstPerson, AnimationClip clip) {
    public enum Hand { MAIN, OFF, BOTH, NONE }
    public enum Mask {
        FULL, UPPER, LOWER, R_ARM, L_ARM;

        public boolean includes(BoneId bone) {
            // 掩码按语义列举，避免 pelvis 的子树把上半身一起划入 lower。
            if (bone.socket()) {
                return false;
            }
            return switch (this) {
                case FULL -> true;
                case UPPER -> bone == BoneId.WAIST || bone == BoneId.CHEST || bone == BoneId.HEAD || bone.arm();
                case LOWER -> bone == BoneId.PELVIS || bone.skinPart().endsWith("leg");
                case R_ARM -> bone.skinPart().equals("r_arm");
                case L_ARM -> bone.skinPart().equals("l_arm");
            };
        }
    }

    public static AnimationEntry parse(JsonObject raw, AnimationClip clip) {
        // 枚举值严格按契约拼写，错误交给加载器带文件路径记录。
        String id = JsonInput.string(raw, "id");
        if (!id.matches("[a-z0-9_./-]+")) {
            throw new IllegalArgumentException("无效动画 id: " + id);
        }
        String file = JsonInput.string(raw, "file");
        if (!file.startsWith("animations/") || !file.endsWith(".animation.json") || file.contains("..")) {
            throw new IllegalArgumentException("动画文件必须位于 animations/ 内: " + file);
        }
        Mask mask = switch (JsonInput.string(raw, "mask")) {
            case "full" -> Mask.FULL; case "upper" -> Mask.UPPER; case "lower" -> Mask.LOWER;
            case "r_arm" -> Mask.R_ARM; case "l_arm" -> Mask.L_ARM;
            default -> throw new IllegalArgumentException("未知 mask: " + raw.get("mask"));
        };
        Hand hand = switch (JsonInput.string(raw, "hand")) {
            case "main" -> Hand.MAIN; case "off" -> Hand.OFF; case "both" -> Hand.BOTH; case "none" -> Hand.NONE;
            default -> throw new IllegalArgumentException("未知 hand: " + raw.get("hand"));
        };
        int priority = JsonInput.integer(raw.get("priority"));
        return new AnimationEntry(id, file, mask, priority,
                JsonInput.nonnegative(raw, "blend_in"), JsonInput.nonnegative(raw, "blend_out"),
                hand, JsonInput.bool(raw, "fpv"), clip);
    }
}
