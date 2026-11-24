package dev.kizuna.playeranimation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.kizuna.playeranimation.animation.AnimationEntry;
import dev.kizuna.playeranimation.animation.SwingWindow;
import dev.kizuna.playeranimation.model.Skeleton;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * 索引里的 swings 必须和从剪辑里量出来的一致：哪个部位、从几秒到几秒、轻还是重。
 * 量法：部位速度不低于 4 格每秒的连续一段算一次挥动（隔不到 0.08 秒的并成一段），最快处到 6 格每秒、
 * 持续 0.05 秒以上才记；最快处到 12 格每秒算重。
 * 流金剑三式只在索引 phases 声明的发力段里量，淡入期间也不量（docs/asset-contract.md「索引条目」swings）。
 * 每次运行都把量出来的结果写到 build/swing-windows.json，改了动作以后照它更新索引。
 */
class SwingWindowsContractTest {
    private static final double EXTENT_SPEED = 4;
    private static final double QUALIFY_SPEED = 6;
    private static final double HEAVY_SPEED = 12;
    /** 剪辑开头从站姿摆进架势的那一下也很快，但不是挥击；一秒以上的剪辑不记从头开始的那一段。 */
    private static final double ENTRY_SECONDS = 0.02;
    private static final double LONG_CLIP_SECONDS = 1;
    /** 一晃而过、不到三个取样步的不记：画不出风痕，也不该有破风声。 */
    private static final double MIN_SECONDS = 0.05;
    /** 索引里的秒数写到百分位，和量出来的差一个取样步以内都算一致。 */
    private static final double TOLERANCE = 0.02;
    private static final List<String> DOOR_MOVES =
            List.of("knock", "cleave", "ram", "hook", "sweep", "close", "chain", "finale");
    private static final List<String> KICKS = List.of("snap_kick", "push_kick", "roundhouse", "triple_kick",
            "mule_kick", "flying_kick", "axe_kick", "whirlwind");

    private static final List<String> FIST_MOVES = List.of("crushing_fist", "golem_lift", "shoulder_throw");
    private static final List<String> SWORD_MOVES = List.of("wind_around", "quiver", "spring_back");
    private static final String SWORD_PREFIX = "piglin_sword_";

    private record Measured(double start, double end, String part, String weight) { }

    @Test
    void indexSwingsMatchTheMeasuredMotion() throws Exception {
        Skeleton skeleton = DeliveredRigTest.skeleton(false /* slim */);
        Map<String, List<Measured>> measured = new LinkedHashMap<>();
        for (var clip : clips().entrySet()) {
            measured.put(clip.getKey(), measure(clip.getKey(), skeleton, clip.getValue()));
        }
        Files.createDirectories(Path.of("build"));
        Files.writeString(Path.of("build/swing-windows.json"), json(measured));
        for (var clip : measured.entrySet()) {
            List<SwingWindow> declared = SwingProbe.entry(clip.getKey()).swings();
            List<Measured> expected = clip.getValue();
            assertEquals(expected.size(), declared.size(), clip.getKey() + " 的 swings 段数和量出来的不一样：" + expected);
            for (int index = 0; index < expected.size(); index++) {
                Measured want = expected.get(index);
                SwingWindow got = declared.get(index);
                String where = clip.getKey() + " 第 " + (index + 1) + " 段，量出来是 " + want;
                assertEquals(want.part(), got.part().name().toLowerCase(Locale.ROOT), where);
                assertEquals(want.weight(), got.weight().name().toLowerCase(Locale.ROOT), where);
                assertTrue(Math.abs(want.start() - got.start()) <= TOLERANCE
                        && Math.abs(want.end() - got.end()) <= TOLERANCE, where);
            }
        }
    }

    private static Map<String, List<String>> clips() {
        // 要标挥击段的剪辑和各自要量的部位：兵器的式量兵器尖，腿法量两只脚，原版挥手兵器和空手各量一遍。
        Map<String, List<String>> clips = new LinkedHashMap<>();
        clips.put("attack_main", List.of("main", "r_hand"));
        clips.put("attack_off", List.of("off", "l_hand"));
        for (String move : DOOR_MOVES) {
            clips.put("door_breaker_" + move, List.of("main"));
            clips.put("door_breaker_" + move + "_off", List.of("off"));
        }
        clips.put("wayfarer_bow_strike", List.of("main"));
        clips.put("wayfarer_bow_strike_off", List.of("off"));
        for (String kick : KICKS) {
            clips.put("peddler_" + kick, List.of("r_foot", "l_foot"));
        }
        // 袖手拳是空手的：量两只手。
        for (String move : FIST_MOVES) {
            clips.put("villager_" + move, List.of("r_hand", "l_hand"));
        }
        for (String move : SWORD_MOVES) {
            clips.put("piglin_sword_" + move, List.of("main"));
            clips.put("piglin_sword_" + move + "_off", List.of("off"));
        }
        return clips;
    }

    private static List<Measured> measure(String id, Skeleton skeleton, List<String> parts) throws Exception {
        AnimationEntry entry = SwingProbe.entry(id);
        List<Measured> found = new ArrayList<>();
        for (String part : parts) {
            var speeds = SwingProbe.speeds(entry, skeleton, part);
            if (id.startsWith(SWORD_PREFIX)) {
                keepStrikePhases(speeds, strikePhases(id), entry.blendIn());
            }
            for (SwingProbe.Window window : SwingProbe.windows(speeds, EXTENT_SPEED)) {
                if (!recorded(entry, window)) {
                    continue;
                }
                // 原版挥手很短但极快，按速度会全判成重；它只是普通一击，一律算轻。
                boolean heavy = window.peak() >= HEAVY_SPEED && !entry.id().startsWith("attack_");
                found.add(new Measured(round(window.start()), round(Math.min(window.end(), entry.clip().duration())),
                        part, heavy ? "heavy" : "light"));
            }
        }
        found.sort(Comparator.comparingDouble(Measured::start));
        return found;
    }

    private static boolean recorded(AnimationEntry entry, SwingProbe.Window window) {
        // 太慢或一晃而过的不记；一秒以上的剪辑不记从头开始的那一段（从站姿摆进架势）。
        if (window.peak() < QUALIFY_SPEED || window.end() - window.start() < MIN_SECONDS) {
            return false;
        }
        boolean longClip = entry.clip().duration() > LONG_CLIP_SECONDS;
        return !longClip || window.start() >= ENTRY_SECONDS;
    }

    /**
     * 流金剑三式的端剑、弹回和收势（含 item_rotation 转回原版握法）剑尖也快，但破风声要落在刺、点、斩上：
     * 发力段之外的速度记为零。量的时候从零姿势淡入，游戏里从架势起手没有那一下，淡入期间也记为零。
     */
    private static void keepStrikePhases(double[] speeds, List<double[]> phases, float blendIn) {
        for (int step = 0; step < speeds.length; step++) {
            double time = step * SwingProbe.STEP;
            boolean striking = false;
            for (double[] phase : phases) {
                striking |= phase[0] <= time && time < phase[1];
            }
            if (!striking || time < blendIn) {
                speeds[step] = 0;
            }
        }
    }

    /** 索引 phases 的各段 [开始秒, 结束秒]：数组元素写成 [开始, 结束] 或 {"start", "end", ...}。 */
    private static List<double[]> strikePhases(String id) throws Exception {
        List<double[]> phases = new ArrayList<>();
        for (JsonElement element : SwingProbe.raw(id).getAsJsonArray("phases")) {
            if (element.isJsonArray()) {
                JsonArray pair = element.getAsJsonArray();
                phases.add(new double[] {pair.get(0).getAsDouble(), pair.get(1).getAsDouble()});
            } else {
                JsonObject phase = element.getAsJsonObject();
                phases.add(new double[] {phase.get("start").getAsDouble(), phase.get("end").getAsDouble()});
            }
        }
        return phases;
    }

    private static double round(double seconds) {
        return Math.round(seconds * 100) / 100.0;
    }

    private static String json(Map<String, List<Measured>> measured) {
        StringBuilder out = new StringBuilder("{\n");
        var clips = new ArrayList<>(measured.entrySet());
        for (int index = 0; index < clips.size(); index++) {
            out.append("  \"").append(clips.get(index).getKey()).append("\": [");
            List<Measured> windows = clips.get(index).getValue();
            for (int window = 0; window < windows.size(); window++) {
                Measured one = windows.get(window);
                out.append(String.format(Locale.ROOT,
                        "%s{\"start\": %s, \"end\": %s, \"part\": \"%s\", \"weight\": \"%s\"}",
                        window == 0 ? "" : ", ", one.start(), one.end(), one.part(), one.weight()));
            }
            out.append(index + 1 < clips.size() ? "],\n" : "]\n");
        }
        return out.append("}\n").toString();
    }
}
