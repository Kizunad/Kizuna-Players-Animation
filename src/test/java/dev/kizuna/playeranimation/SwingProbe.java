package dev.kizuna.playeranimation;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.kizuna.playeranimation.animation.AnimationClip;
import dev.kizuna.playeranimation.animation.AnimationEntry;
import dev.kizuna.playeranimation.animation.AnimationPlayer;
import dev.kizuna.playeranimation.model.BoneId;
import dev.kizuna.playeranimation.model.Rig;
import dev.kizuna.playeranimation.model.Skeleton;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * 量一段剪辑里某个部位（武器尖、手、脚）每一刻走得多快，从速度曲线里找出挥击段。
 * 索引里的 swings 是照这里量出来的结果写的，{@link SwingWindowsContractTest} 再拿它核对，不靠人眼猜。
 */
final class SwingProbe {
    static final Path INDEX = Path.of("src/main/resources/assets/kizuna_players_animation/player_anim_index.json");
    static final String KEY_PREFIX = "animation.kizuna_players_animation.player.";
    /** 取样步长：60 分之一秒，比游戏的 20 tick 细，挥得再快也不会漏。 */
    static final double STEP = 1.0 / 60;
    /** 武器在挂点坐标里的尖端：原版手持物的显示变换算出来的，见 SwingTrails 里同一个数。 */
    static final Vector3f WEAPON_TIP = new Vector3f(0, 0.84f, -0.07f);
    /** 相邻两段挥击之间停顿不到这么久的，算同一段。 */
    static final double MERGE_GAP = 0.08;

    record Window(double start, double end, double peak) { }

    private SwingProbe() {
        // 只有静态的量法。
    }

    static AnimationEntry entry(String id) throws Exception {
        JsonObject index = JsonParser.parseString(Files.readString(INDEX)).getAsJsonObject();
        for (var element : index.getAsJsonArray("animations")) {
            JsonObject raw = element.getAsJsonObject();
            if (id.equals(raw.get("id").getAsString())) {
                JsonObject document = JsonParser.parseString(Files.readString(
                        INDEX.getParent().resolve(raw.get("file").getAsString()))).getAsJsonObject();
                AnimationClip clip = AnimationClip.parse(document, KEY_PREFIX + id,
                        error -> { throw new AssertionError(error); });
                return AnimationEntry.parse(raw, clip);
            }
        }
        throw new AssertionError("缺少动作: " + id);
    }

    /** 这个部位在剪辑第 time 秒的位置（实体自己的坐标，单位格）。part 是 main、off、r_hand、l_hand、r_foot、l_foot。 */
    static Vector3f point(AnimationEntry entry, Skeleton skeleton, String part, double time) {
        AnimationPlayer player = new AnimationPlayer();
        player.play(entry, 0, new AnimationPlayer.PlaybackRequest(0, AnimationPlayer.Handedness.RIGHT, null, null));
        Matrix4f[] matrices = Rig.matrices(skeleton, player.sample(Rig.zeroPose(), time, false /* firstPerson */));
        Vector3f tip = new Vector3f(WEAPON_TIP);
        return switch (part) {
            case "main" -> Rig.itemTransform(skeleton, matrices, BoneId.R_ITEM).transformPosition(tip);
            case "off" -> Rig.itemTransform(skeleton, matrices, BoneId.L_ITEM).transformPosition(tip);
            default -> Rig.itemTransform(skeleton, matrices, bone(part)).transformPosition(new Vector3f());
        };
    }

    static BoneId bone(String part) {
        return switch (part) {
            case "r_hand" -> BoneId.R_HAND;
            case "l_hand" -> BoneId.L_HAND;
            case "r_foot" -> BoneId.R_FOOT;
            case "l_foot" -> BoneId.L_FOOT;
            default -> throw new IllegalArgumentException("不认识的部位: " + part);
        };
    }

    /** 每个取样步的速度（格每秒）。 */
    static double[] speeds(AnimationEntry entry, Skeleton skeleton, String part) {
        int steps = (int) Math.floor(entry.clip().duration() / STEP);
        double[] speeds = new double[steps];
        Vector3f previous = point(entry, skeleton, part, 0);
        for (int step = 0; step < steps; step++) {
            Vector3f next = point(entry, skeleton, part, (step + 1) * STEP);
            speeds[step] = next.distance(previous) / STEP;
            previous = next;
        }
        return speeds;
    }

    /** 速度不低于 threshold 的各段；隔得很近的并成一段。 */
    static List<Window> windows(double[] speeds, double threshold) {
        List<Window> found = new ArrayList<>();
        int start = -1;
        double peak = 0;
        for (int step = 0; step <= speeds.length; step++) {
            boolean fast = step < speeds.length && speeds[step] >= threshold;
            if (fast) {
                start = start < 0 ? step : start;
                peak = Math.max(peak, speeds[step]);
                continue;
            }
            if (start >= 0) {
                Window window = new Window(start * STEP, step * STEP, peak);
                if (!found.isEmpty() && window.start() - found.getLast().end() < MERGE_GAP) {
                    Window earlier = found.removeLast();
                    window = new Window(earlier.start(), window.end(), Math.max(earlier.peak(), peak));
                }
                found.add(window);
                start = -1;
                peak = 0;
            }
        }
        return found;
    }
}
