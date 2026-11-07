package dev.kizuna.playeranimation.animation;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 索引 aim_windows 的一段瞄准时段（剪辑秒，docs/asset-contract.md「索引条目」）。
 * 权重在段内由起点后 {@link #FADE_SECONDS} 升到 1、终点前同样时长降到 0，渐入渐出都不越出时段。
 */
public record AimWindow(float start, float end, Mode mode) {
    /** 段内渐入、渐出各自的时长（秒）。 */
    public static final float FADE_SECONDS = 0.15f;

    /** PITCH：头与双臂补视线俯仰；YAW：不补俯仰。两种都压掉视线层俯仰，偏航照常走视线层。 */
    public enum Mode { PITCH, YAW }

    public float weight(float time) {
        // 离较近一端的距离决定权重，时段外为 0。
        float edge = Math.min(time - start, end - time);
        return Math.max(0, Math.min(1, edge / FADE_SECONDS));
    }

    public static List<AimWindow> parse(JsonElement raw, float duration) {
        // 非空数组；每段只有 start、end、mode，在剪辑内、按时间排列且不重叠。
        if (raw == null || !raw.isJsonArray() || raw.getAsJsonArray().isEmpty()) {
            throw new IllegalArgumentException("aim_windows 必须是非空数组");
        }
        List<AimWindow> windows = new ArrayList<>();
        float previousEnd = 0;
        for (JsonElement element : raw.getAsJsonArray()) {
            if (!element.isJsonObject() || !element.getAsJsonObject().keySet().equals(Set.of("start", "end", "mode"))) {
                throw new IllegalArgumentException("aim_windows 每段必须是 {start, end, mode}");
            }
            JsonObject window = element.getAsJsonObject();
            float start = JsonInput.number(window.get("start"));
            float end = JsonInput.number(window.get("end"));
            if (start < previousEnd || start >= end || end > duration) {
                throw new IllegalArgumentException("aim_windows 时段越界、倒序或重叠: [" + start + ", " + end + "]");
            }
            Mode mode = switch (JsonInput.string(window, "mode")) {
                case "pitch" -> Mode.PITCH;
                case "yaw" -> Mode.YAW;
                default -> throw new IllegalArgumentException("aim_windows 未知模式: " + window.get("mode"));
            };
            windows.add(new AimWindow(start, end, mode));
            previousEnd = end;
        }
        return List.copyOf(windows);
    }
}
