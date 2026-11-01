package dev.kizuna.playeranimation.animation;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.kizuna.playeranimation.model.BoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public record AnimationEntry(String id, String file, Mask mask, int priority, float blendIn,
                             float blendOut, Hand hand, boolean firstPerson, AnimationClip clip,
                             List<Window> impactWindows,
                             Float loopRegionStart, Float loopRegionEnd, boolean lookLayer) {
    public AnimationEntry(String id, String file, Mask mask, int priority, float blendIn,
                          float blendOut, Hand hand, boolean firstPerson, AnimationClip clip) {
        this(id, file, mask, priority, blendIn, blendOut, hand, firstPerson, clip,
                List.of(), null, null, false);
    }

    /** 剪辑时间里的一段 [start, end]（秒）。 */
    public record Window(float start, float end) { }

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
        List<Window> impact = readImpactWindows(raw, clip.duration());
        validateImpactAction(id, impact);
        Window loop = raw.has("loop_region") ? readWindow(raw.get("loop_region"), "loop_region", clip.duration()) : null;
        return new AnimationEntry(id, file, mask, priority,
                JsonInput.nonnegative(raw, "blend_in"), JsonInput.nonnegative(raw, "blend_out"),
                hand, JsonInput.bool(raw, "fpv"), clip, impact,
                loop == null ? null : loop.start(), loop == null ? null : loop.end(), readLookLayer(raw));
    }

    private static List<Window> readImpactWindows(JsonObject raw, float duration) {
        // 单段写 [start,end]，多击的式写 [[start,end], ...]；多段按时间先后、互不重叠。
        if (!raw.has("impact_window")) {
            return List.of();
        }
        var value = raw.get("impact_window");
        if (!value.isJsonArray() || value.getAsJsonArray().isEmpty()) {
            throw new IllegalArgumentException("impact_window 必须为 [start,end] 或 [[start,end], ...]");
        }
        var array = value.getAsJsonArray();
        if (!array.get(0).isJsonArray()) {
            return List.of(readWindow(value, "impact_window", duration));
        }
        List<Window> windows = new ArrayList<>();
        for (var item : array) {
            Window window = readWindow(item, "impact_window", duration);
            if (!windows.isEmpty() && windows.get(windows.size() - 1).end() > window.start()) {
                throw new IllegalArgumentException("impact_window 各段必须按时间先后且互不重叠");
            }
            windows.add(window);
        }
        return List.copyOf(windows);
    }

    private static Window readWindow(JsonElement value, String key, float duration) {
        // 打击段与循环区共用有限数与时长校验。
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

    private static void validateImpactAction(String id, List<Window> impact) {
        // 只有已有接触语义的动作允许声明打击窗口，避免其他动画绕过混合规则。
        if (!impact.isEmpty() && !Set.of("attack_main", "attack_off", "mine_main", "mine_off",
                "door_breaker_knock", "door_breaker_knock_off",
                "door_breaker_cleave", "door_breaker_cleave_off",
                "door_breaker_ram", "door_breaker_ram_off",
                "door_breaker_hook", "door_breaker_hook_off",
                "door_breaker_close", "door_breaker_close_off",
                "door_breaker_chain", "door_breaker_chain_off",
                "door_breaker_finale", "door_breaker_finale_off",
                "peddler_snap_kick", "peddler_push_kick", "peddler_roundhouse", "peddler_triple_kick",
                "peddler_mule_kick", "peddler_flying_kick", "peddler_axe_kick", "peddler_whirlwind").contains(id)) {
            throw new IllegalArgumentException("impact_window 只能用于攻击、挖掘、破门斩或千里腿动作");
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
