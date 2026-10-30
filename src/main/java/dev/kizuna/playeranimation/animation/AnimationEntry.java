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
            case "full" -> Mask.FULL;
            case "upper" -> Mask.UPPER;
            case "lower" -> Mask.LOWER;
            case "r_arm" -> Mask.R_ARM;
            case "l_arm" -> Mask.L_ARM;
            default -> throw new IllegalArgumentException("未知 mask: " + raw.get("mask"));
        };
        Hand hand = switch (JsonInput.string(raw, "hand")) {
            case "main" -> Hand.MAIN;
            case "off" -> Hand.OFF;
            case "both" -> Hand.BOTH;
            case "none" -> Hand.NONE;
            default -> throw new IllegalArgumentException("未知 hand: " + raw.get("hand"));
        };
        int priority = JsonInput.integer(raw.get("priority"));
        Window impact = readWindow(raw, "impact_window", clip.duration());
        validateImpactAction(id, impact);
        Window loop = readWindow(raw, "loop_region", clip.duration());
        return new AnimationEntry(id, file, mask, priority,
                JsonInput.nonnegative(raw, "blend_in"), JsonInput.nonnegative(raw, "blend_out"),
                hand, JsonInput.bool(raw, "fpv"), clip, impact.start(), impact.end(), loop.start(), loop.end(),
                readLookLayer(raw));
    }

    private record Window(Float start, Float end) { }

    private static Window readWindow(JsonObject raw, String key, float duration) {
        // 两种窗口共用有限数与时长校验，缺省窗口仍保留空端点语义。
        if (!raw.has(key)) {
            return new Window(null, null);
        }
        var value = raw.get(key);
        if (!value.isJsonArray() || value.getAsJsonArray().size() != 2) {
            throw new IllegalArgumentException(key + " 必须为 [start,end]");
        }
        var endpoints = value.getAsJsonArray();
        float start = endpoints.get(0).getAsFloat();
        float end = endpoints.get(1).getAsFloat();
        if (!Float.isFinite(start) || !Float.isFinite(end) || start < 0 || start >= end || end > duration) {
            throw new IllegalArgumentException(key + " 超出动画时长");
        }
        return new Window(start, end);
    }

    private static void validateImpactAction(String id, Window impact) {
        // 只有已有接触语义的动作允许声明打击窗口，避免其他动画绕过混合规则。
        if (impact.start() != null && !Set.of("attack_main", "attack_off", "mine_main", "mine_off",
                "door_breaker_knock", "door_breaker_knock_off",
                "door_breaker_cleave", "door_breaker_cleave_off",
                "door_breaker_ram", "door_breaker_ram_off",
                "door_breaker_hook", "door_breaker_hook_off",
                "door_breaker_close", "door_breaker_close_off",
                "door_breaker_chain", "door_breaker_chain_off",
                "door_breaker_finale", "door_breaker_finale_off").contains(id)) {
            throw new IllegalArgumentException("impact_window 只能用于攻击、挖掘或破门斩动作");
        }
    }

    private static boolean readLookLayer(JsonObject raw) {
        // layer 只接受 look：视线叠加层由渲染器按视线角度取样并加到姿态上，不经过播放与同步。
        boolean lookLayer = false;
        if (raw.has("layer")) {
            String layer = JsonInput.string(raw, "layer");
            if (!layer.equals("look")) {
                throw new IllegalArgumentException("未知 layer: " + layer);
            }
            lookLayer = true;
        }
        return lookLayer;
    }
}
