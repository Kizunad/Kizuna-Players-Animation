package dev.kizuna.playeranimation;

import com.google.gson.JsonParser;
import dev.kizuna.playeranimation.animation.AnimationClip;
import dev.kizuna.playeranimation.animation.AnimationEntry;
import dev.kizuna.playeranimation.animation.AnimationPlayer;
import dev.kizuna.playeranimation.animation.AutomaticAnimationPlayer;
import dev.kizuna.playeranimation.model.Pose;
import dev.kizuna.playeranimation.model.Rig;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class AutomaticAnimationTest {
    private static final Path ASSETS = Path.of("src/main/resources/assets/kizuna_players_animation");
    private static final double SAMPLE_TIME = 0.3;
    private static final double TICK_SECONDS = 0.05;
    private static final double ENDED_TIME = 2;

    @Test
    void repeatedObservationKeepsClockAndCrossfadesMovement() throws Exception {
        // 实际资源的重复状态不能重启时钟，换步态的同一时刻也不能跳姿态。
        var walk = entry("walk");
        var run = entry("run");
        var automatic = new AutomaticAnimationPlayer();
        var reference = new AutomaticAnimationPlayer();
        automatic.update(walk, null, 0, AnimationPlayer.Handedness.RIGHT, 0);
        reference.update(walk, null, 0, AnimationPlayer.Handedness.RIGHT, 0);
        for (double time = TICK_SECONDS; time <= SAMPLE_TIME; time += TICK_SECONDS) {
            automatic.update(walk, null, 0, AnimationPlayer.Handedness.RIGHT, time);
        }
        Pose[] walking = sample(reference.playback(), SAMPLE_TIME);
        assertArrayEquals(walking, sample(automatic.playback(), SAMPLE_TIME));
        automatic.update(run, null, 0, AnimationPlayer.Handedness.RIGHT, SAMPLE_TIME);
        assertArrayEquals(walking, sample(automatic.playback(), SAMPLE_TIME));
        automatic.playback().advance(ENDED_TIME, (animation, event) -> { });
        assertTrue(automatic.playback().hasActive("run", ENDED_TIME));
    }

    @Test
    void explicitActionOverridesAutomaticAndReturnsAfterNaturalEnd() throws Exception {
        // 显式动作最后采样，完成与停止后都露出仍在推进的自动层。
        var automatic = new AutomaticAnimationPlayer();
        automatic.update(entry("walk"), entry("crossbow_hold"), 1, AnimationPlayer.Handedness.RIGHT, 0);
        var explicit = new AnimationPlayer();
        var request = new AnimationPlayer.PlaybackRequest(0, AnimationPlayer.Handedness.RIGHT, "cast", null);
        explicit.play(entry("attack_main"), 0, request);
        Pose[] lower = sample(automatic.playback(), SAMPLE_TIME);
        Pose[] composed = explicit.sample(lower, SAMPLE_TIME, false /* firstPerson */);
        assertNotEquals(Arrays.asList(lower), Arrays.asList(composed));
        explicit.advance(ENDED_TIME, (animation, event) -> { });
        Pose[] after = sample(automatic.playback(), ENDED_TIME);
        assertArrayEquals(after, explicit.sample(after, ENDED_TIME, false /* firstPerson */));
        assertTrue(automatic.playback().hasActive("walk", ENDED_TIME));
    }

    private static Pose[] sample(AnimationPlayer playback, double time) {
        // 从中立骨姿态取样，不允许原版举斧或交叉臂混入自动层。
        return playback.sample(Rig.zeroPose(), time, false /* firstPerson */);
    }

    private static AnimationEntry entry(String id) throws Exception {
        // 读取随模组交付的索引与片段，覆盖真实淡入淡出和掩码配置。
        var index = JsonParser.parseString(Files.readString(ASSETS.resolve("player_anim_index.json")))
                .getAsJsonObject().getAsJsonArray("animations");
        for (var element : index) {
            var raw = element.getAsJsonObject();
            if (raw.get("id").getAsString().equals(id)) {
                var json = JsonParser.parseString(Files.readString(ASSETS.resolve(raw.get("file").getAsString())))
                        .getAsJsonObject();
                String key = json.getAsJsonObject("animations").keySet().iterator().next();
                var clip = AnimationClip.parse(json, key, message -> { throw new AssertionError(message); });
                return AnimationEntry.parse(raw, clip);
            }
        }
        throw new AssertionError("缺少片段 " + id);
    }
}
