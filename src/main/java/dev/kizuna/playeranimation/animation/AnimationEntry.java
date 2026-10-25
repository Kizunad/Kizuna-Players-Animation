package dev.kizuna.playeranimation.animation;

import com.google.gson.JsonObject;
import dev.kizuna.playeranimation.model.BoneId;
import java.util.Set;

public record AnimationEntry(String id, String file, Mask mask, int priority, float blendIn,
                             float blendOut, Hand hand, boolean firstPerson, AnimationClip clip,
                             Float impactWindowStart, Float impactWindowEnd,
                             Float loopRegionStart, Float loopRegionEnd, boolean lookLayer) {
    public AnimationEntry(String id, String file, Mask mask, int priority, float blendIn,
                          float blendOut, Hand hand, boolean firstPerson, AnimationClip clip) {
        this(id, file, mask, priority, blendIn, blendOut, hand, firstPerson, clip,
                null, null, null, null, false);
    }

    public AnimationEntry(String id, String file, Mask mask, int priority, float blendIn,
                          float blendOut, Hand hand, boolean firstPerson, AnimationClip clip,
                          Float impactWindowStart, Float impactWindowEnd) {
        this(id, file, mask, priority, blendIn, blendOut, hand, firstPerson, clip,
                impactWindowStart, impactWindowEnd, null, null, false);
    }

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
        Float impactStart = null;
        Float impactEnd = null;
        if (raw.has("impact_window")) {
            if (!raw.get("impact_window").isJsonArray() || raw.getAsJsonArray("impact_window").size() != 2) {
                throw new IllegalArgumentException("impact_window 必须为 [start,end]");
            }
            impactStart = raw.getAsJsonArray("impact_window").get(0).getAsFloat();
            impactEnd = raw.getAsJsonArray("impact_window").get(1).getAsFloat();
            if (!Float.isFinite(impactStart) || !Float.isFinite(impactEnd)
                    || impactStart < 0 || impactStart >= impactEnd || impactEnd > clip.duration()) {
                throw new IllegalArgumentException("impact_window 超出动画时长");
            }
            if (!Set.of("attack_main", "attack_off", "mine_main", "mine_off").contains(id)) {
                throw new IllegalArgumentException("impact_window 只能用于攻击或挖掘动作");
            }
        }
        Float loopStart = null;
        Float loopEnd = null;
        if (raw.has("loop_region")) {
            if (!raw.get("loop_region").isJsonArray() || raw.getAsJsonArray("loop_region").size() != 2) {
                throw new IllegalArgumentException("loop_region 必须为 [start,end]");
            }
            loopStart = raw.getAsJsonArray("loop_region").get(0).getAsFloat();
            loopEnd = raw.getAsJsonArray("loop_region").get(1).getAsFloat();
            if (!Float.isFinite(loopStart) || !Float.isFinite(loopEnd)
                    || loopStart < 0 || loopStart >= loopEnd || loopEnd > clip.duration()) {
                throw new IllegalArgumentException("loop_region 超出动画时长");
            }
        }
        // layer 只接受 look：视线叠加层由渲染器按视线角度取样并加到姿态上，不经过播放与同步。
        boolean lookLayer = false;
        if (raw.has("layer")) {
            String layer = JsonInput.string(raw, "layer");
            if (!layer.equals("look")) {
                throw new IllegalArgumentException("未知 layer: " + layer);
            }
            lookLayer = true;
        }
        return new AnimationEntry(id, file, mask, priority,
                JsonInput.nonnegative(raw, "blend_in"), JsonInput.nonnegative(raw, "blend_out"),
                hand, JsonInput.bool(raw, "fpv"), clip, impactStart, impactEnd, loopStart, loopEnd, lookLayer);
    }
}
