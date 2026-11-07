package dev.kizuna.playeranimation;

import com.google.gson.JsonParser;
import dev.kizuna.playeranimation.animation.AnimationClip;
import dev.kizuna.playeranimation.animation.AnimationEntry;
import dev.kizuna.playeranimation.animation.AnimationPlayer;
import dev.kizuna.playeranimation.model.BoneId;
import dev.kizuna.playeranimation.model.Rig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class AnimationContinuityTest {
    private static final double TICK_SECONDS = 0.05;
    private static final double HALF_TICK = TICK_SECONDS / 2;
    private static final double EPSILON = 0.00001;

    @Test
    void updateBoundaryMustStartAtThePreviouslyRenderedPose() {
        // 同一次更新前后必须同姿态，随后逐帧向新目标前进，不能先跳目标再倒退。
        AnimationEntry entry = entry();
        AnimationPlayer player = new AnimationPlayer();
        player.scrub(entry, 0, 0, false /* leftHanded */);
        double before = angle(player, TICK_SECONDS);
        player.scrub(entry, TICK_SECONDS, 1, false /* leftHanded */);
        assertEquals(before, angle(player, TICK_SECONDS), EPSILON);
        assertEquals(50, angle(player, TICK_SECONDS + HALF_TICK), EPSILON);
        assertEquals(100, angle(player, TICK_SECONDS * 2), EPSILON);
    }

    @Test
    void earlyUpdateMustContinueFromVisiblePose() {
        // 两个进度包间隔不足一 tick 时，不能把尚未显示到的上一目标当作插值起点。
        AnimationEntry entry = entry();
        AnimationPlayer player = new AnimationPlayer();
        player.scrub(entry, 0, 0, false /* leftHanded */);
        player.scrub(entry, TICK_SECONDS, 0.5f, false /* leftHanded */);
        double update = TICK_SECONDS + HALF_TICK;
        double before = angle(player, update);
        player.scrub(entry, update, 1, false /* leftHanded */);
        assertEquals(before, angle(player, update), EPSILON);
        assertEquals((before + 100) / 2, angle(player, update + HALF_TICK), EPSILON);
    }

    @Test
    void stopDuringInterpolationMustFreezeTheVisiblePose() {
        // 停止发生在插值中间时，只淡出当前姿态，不能在淡出期间继续冲到目标。
        AnimationEntry entry = entry();
        AnimationPlayer player = new AnimationPlayer();
        player.scrub(entry, 0, 0, false /* leftHanded */);
        player.scrub(entry, TICK_SECONDS, 1, false /* leftHanded */);
        double stop = TICK_SECONDS + HALF_TICK;
        double before = angle(player, stop);
        player.stop(entry.id(), stop);
        assertEquals(before / 2, angle(player, stop + TICK_SECONDS), EPSILON);
    }

    private static double angle(AnimationPlayer player, double time) {
        return player.sample(Rig.zeroPose(), time, false /* firstPerson */)
                [BoneId.R_FOREARM.ordinal()].rotation().x();
    }

    private static AnimationEntry entry() {
        // 线性角度把输出姿态直接映射成可测进度，排除美术关键帧的速度变化。
        var document = JsonParser.parseString("""
                {"format_version":"1.8.0","animations":{"test":{"animation_length":1,"bones":{"r_forearm":{
                  "rotation":{"0":[0,0,0],"1":[-100,0,0]}}}}}}
                """).getAsJsonObject();
        AnimationClip clip = AnimationClip.parse(document, "test", message -> { throw new AssertionError(message); });
        var metadata = JsonParser.parseString("""
                {"id":"continuity","file":"animations/test.animation.json","mask":"r_arm",
                 "priority":0,"blend_in":0,"blend_out":0.1,"hand":"none","fpv":false}
                """).getAsJsonObject();
        return AnimationEntry.parse(metadata, clip);
    }
}
