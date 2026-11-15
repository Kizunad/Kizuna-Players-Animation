package dev.kizuna.playeranimation.animation;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 索引 swings 的一段挥击（剪辑秒，docs/asset-contract.md「索引条目」）：哪个部位在这段时间里挥过去、挥得轻还是重。
 * 客户端在这段时间里给这个部位画风痕、起挥时放破风声。时段是从剪辑里量出来的（测试 SwingWindowsContractTest 核对）。
 */
public record SwingWindow(float start, float end, Part part, Weight weight) {
    /** MAIN、OFF 是主手、副手里的兵器（手里有东西才算）；其余是空着的手和脚。左右按右撇子写，镜像播放时对调。 */
    public enum Part { MAIN, OFF, R_HAND, L_HAND, R_FOOT, L_FOOT }

    public enum Weight { LIGHT, HEAVY }

    public boolean contains(float time) {
        return time >= start && time <= end;
    }

    public static List<SwingWindow> parse(JsonElement raw, float duration) {
        // 非空数组；每段只有 start、end、part、weight，在剪辑内、按起点先后排列。不同部位的时段可以重叠。
        if (raw == null || !raw.isJsonArray() || raw.getAsJsonArray().isEmpty()) {
            throw new IllegalArgumentException("swings 必须是非空数组");
        }
        List<SwingWindow> windows = new ArrayList<>();
        float previousStart = 0;
        for (JsonElement element : raw.getAsJsonArray()) {
            if (!element.isJsonObject()
                    || !element.getAsJsonObject().keySet().equals(Set.of("start", "end", "part", "weight"))) {
                throw new IllegalArgumentException("swings 每段必须是 {start, end, part, weight}");
            }
            JsonObject window = element.getAsJsonObject();
            float start = JsonInput.number(window.get("start"));
            float end = JsonInput.number(window.get("end"));
            if (start < previousStart || start >= end || end > duration) {
                throw new IllegalArgumentException("swings 时段越界或倒序: [" + start + ", " + end + "]");
            }
            windows.add(new SwingWindow(start, end, part(JsonInput.string(window, "part")),
                    weight(JsonInput.string(window, "weight"))));
            previousStart = start;
        }
        return List.copyOf(windows);
    }

    private static Part part(String name) {
        return switch (name) {
            case "main" -> Part.MAIN;
            case "off" -> Part.OFF;
            case "r_hand" -> Part.R_HAND;
            case "l_hand" -> Part.L_HAND;
            case "r_foot" -> Part.R_FOOT;
            case "l_foot" -> Part.L_FOOT;
            default -> throw new IllegalArgumentException("swings 未知部位: " + name);
        };
    }

    private static Weight weight(String name) {
        return switch (name) {
            case "light" -> Weight.LIGHT;
            case "heavy" -> Weight.HEAVY;
            default -> throw new IllegalArgumentException("swings 未知轻重: " + name);
        };
    }
}
