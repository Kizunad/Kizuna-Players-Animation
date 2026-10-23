package dev.kizuna.playeranimation;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.kizuna.playeranimation.animation.AnimationClip;
import dev.kizuna.playeranimation.animation.AnimationEntry;
import dev.kizuna.playeranimation.animation.AnimationPlayer;
import dev.kizuna.playeranimation.animation.Channel;
import dev.kizuna.playeranimation.model.BoneId;
import dev.kizuna.playeranimation.model.Pose;
import dev.kizuna.playeranimation.model.Rig;
import dev.kizuna.playeranimation.model.Vec3;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AnimationTest {
    private static final String KEY = "animation.kizuna_players_animation.player.test";

    private static AnimationClip clip(String body, List<String> errors) {
        // 测试直接使用 Bedrock 载荷，验证导出契约而不是内部构造器的自洽性。
        JsonObject document = JsonParser.parseString("{\"format_version\":\"1.8.0\",\"animations\":{\""
                + KEY + "\":{" + body + "}}}").getAsJsonObject();
        return AnimationClip.parse(document, KEY, errors::add);
    }

    private static AnimationEntry entry(String id, AnimationClip clip, AnimationEntry.Mask mask,
                                         int priority, float fadeIn, float fadeOut, AnimationEntry.Hand hand, boolean fpv) {
        return new AnimationEntry(id, "animations/" + id + ".animation.json", mask, priority, fadeIn, fadeOut, hand, fpv, clip);
    }

    private static AnimationClip rotationClip(String rotation, String extra) {
        return clip("\"animation_length\":1,\"bones\":{\"r_forearm\":{\"rotation\":" + rotation + "}}" + extra,
                new ArrayList<>());
    }

    @Test
    void timeKeysSortNumericallyAndConstantChannelsRemainConstant() {
        Channel channel = Channel.parse(JsonParser.parseString("{\"10\":[100,0,0],\"2\":[20,0,0],\"0\":[0,0,0]}"), 10);
        assertEquals(60, channel.sample(6).x(), 0.001);
        assertEquals(0, channel.sample(-1).x());
        assertEquals(100, channel.sample(20).x());
        Channel constant = Channel.parse(JsonParser.parseString("[1,2,3]"), 1);
        assertEquals(new Vec3(1, 2, 3), constant.sample(0.6f));
    }

    @Test
    void blockbenchStepExportUsesPreAndPostAtTheBoundary() {
        Channel channel = Channel.parse(JsonParser.parseString("""
                {"0":[0,0,0],"0.5":{"pre":[0,0,0],"post":[90,0,0]},"1":[90,0,0]}
                """), 1);
        assertEquals(0, channel.sample(0.499f).x());
        assertEquals(90, channel.sample(0.5f).x());
        assertEquals(90, channel.sample(0.75f).x());
    }

    @Test
    void explicitStepAndCatmullRomAreSupported() {
        Channel step = Channel.parse(JsonParser.parseString("""
                {"0":{"post":[10,0,0],"lerp_mode":"step"},"1":[100,0,0]}
                """), 1);
        assertEquals(10, step.sample(0.9f).x());
        Channel smooth = Channel.parse(JsonParser.parseString("""
                {"0":{"post":[0,0,0],"lerp_mode":"catmullrom"},
                 "1":{"post":[10,0,0],"lerp_mode":"catmullrom"},
                 "2":{"post":[0,0,0],"lerp_mode":"catmullrom"}}
                """), 2);
        assertEquals(5.625, smooth.sample(0.5f).x(), 0.0001);
        assertEquals(10, smooth.sample(1).x());
        assertEquals(5, smooth.sample(0.5f, true).x(), 0.0001);
        assertEquals(5, smooth.sample(1.5f, true).x(), 0.0001);
    }

    @Test
    void invalidBoundaryValuesAreRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> Channel.parse(JsonParser.parseString("{\"0\":[\"query.life_time\",0,0]}"), 1));
        assertThrows(IllegalArgumentException.class,
                () -> Channel.parse(JsonParser.parseString("{\"NaN\":[0,0,0]}"), 1));
        assertThrows(IllegalArgumentException.class,
                () -> Channel.parse(JsonParser.parseString("{\"2\":[0,0,0]}"), 1));
        assertThrows(IllegalArgumentException.class,
                () -> Channel.parse(JsonParser.parseString("{\"0\":[0,0,0],\"0.0\":[1,0,0]}"), 1));
        assertThrows(IllegalArgumentException.class,
                () -> Channel.parse(JsonParser.parseString("{\"0\":{\"post\":[0,0,0],\"lerp_mode\":\"ease\"}}"), 1));
        assertThrows(IllegalArgumentException.class,
                () -> rotationClip("[0,0,0]", ",\"loop\":\"yes\""));
        assertThrows(IllegalArgumentException.class,
                () -> clip("\"animation_length\":1,\"bones\":{\"head\":{\"scale\":[1,2,1]}}", new ArrayList<>()));
    }

    @Test
    void unknownBonesSocketsAndEventsAreReportedWithoutLosingValidTracks() {
        List<String> errors = new ArrayList<>();
        AnimationClip clip = clip("""
                "animation_length":1,
                "bones":{"r_forearm":{"rotation":[-90,0,0]},"rightArm":{"rotation":[0,0,0]},"r_item":{"rotation":[1,0,0]}},
                "timeline":{"0.5":["hit","equip","typo"]}
                """, errors);
        assertEquals(3, errors.size());
        assertEquals(1, clip.tracks().size());
        assertEquals(List.of("hit", "equip"), clip.events().stream().map(AnimationClip.TimelineEvent::name).toList());
    }

    @Test
    void missingBonesAndChannelsInheritVanillaAndLowerLayers() {
        AnimationPlayer player = new AnimationPlayer();
        AnimationClip clip = rotationClip("[-90,0,0]", "");
        player.play(entry("wave", clip, AnimationEntry.Mask.UPPER, 1, 0, 0, AnimationEntry.Hand.NONE, false), 0, false);
        Pose[] base = Rig.zeroPose();
        base[BoneId.R_SHIN.ordinal()] = new Pose(Vec3.ZERO, new Vec3(20, 0, 0));
        base[BoneId.R_FOREARM.ordinal()] = new Pose(new Vec3(3, 2, 1), Vec3.ZERO);
        Pose[] sampled = player.sample(base, 0.5, false);
        assertEquals(base[BoneId.R_SHIN.ordinal()], sampled[BoneId.R_SHIN.ordinal()]);
        assertEquals(new Vec3(3, 2, 1), sampled[BoneId.R_FOREARM.ordinal()].position());
        assertEquals(90, sampled[BoneId.R_FOREARM.ordinal()].rotation().x());
    }

    @Test
    void actionLayerOverridesLoopThenBlendsBackAfterEnd() {
        AnimationPlayer player = new AnimationPlayer();
        player.play(entry("idle", rotationClip("[-20,0,0]", ",\"loop\":true"), AnimationEntry.Mask.UPPER,
                100, 0, 0, AnimationEntry.Hand.NONE, false), 0, false);
        player.play(entry("hit", rotationClip("[-100,0,0]", ""), AnimationEntry.Mask.UPPER,
                0, 0.5f, 0.5f, AnimationEntry.Hand.NONE, false), 0, false);
        assertEquals(60, player.sample(Rig.zeroPose(), 0.25, false)[BoneId.R_FOREARM.ordinal()].rotation().x(), 0.001);
        assertEquals(100, player.sample(Rig.zeroPose(), 0.75, false)[BoneId.R_FOREARM.ordinal()].rotation().x(), 0.001);
        assertEquals(60, player.sample(Rig.zeroPose(), 1.25, false)[BoneId.R_FOREARM.ordinal()].rotation().x(), 0.001);
        assertEquals(20, player.sample(Rig.zeroPose(), 1.5, false)[BoneId.R_FOREARM.ordinal()].rotation().x(), 0.001);
    }

    @Test
    void sameLayerPriorityAndNewestTieWin() {
        AnimationPlayer player = new AnimationPlayer();
        player.play(entry("high", rotationClip("[-70,0,0]", ""), AnimationEntry.Mask.R_ARM, 5, 0, 0, AnimationEntry.Hand.NONE, false), 0, false);
        player.play(entry("low", rotationClip("[-10,0,0]", ""), AnimationEntry.Mask.R_ARM, 1, 0, 0, AnimationEntry.Hand.NONE, false), 0, false);
        assertEquals(70, player.sample(Rig.zeroPose(), 0.5, false)[BoneId.R_FOREARM.ordinal()].rotation().x());
        player.play(entry("newHigh", rotationClip("[-80,0,0]", ""), AnimationEntry.Mask.R_ARM, 5, 0, 0, AnimationEntry.Hand.NONE, false), 0, false);
        assertEquals(80, player.sample(Rig.zeroPose(), 0.5, false)[BoneId.R_FOREARM.ordinal()].rotation().x());
    }

    @Test
    void leftMainHandMirrorsRotationPositionMaskAndFootEvents() {
        AnimationClip clip = clip("""
                "animation_length":1,"bones":{"r_forearm":{"rotation":[10,20,30],"position":[1,2,3]}},
                "timeline":{"0.2":"step_r"}
                """, new ArrayList<>());
        AnimationPlayer player = new AnimationPlayer();
        player.play(entry("mirror", clip, AnimationEntry.Mask.R_ARM, 0, 0, 0, AnimationEntry.Hand.MAIN, true), 0, true);
        Pose[] pose = player.sample(Rig.zeroPose(), 0.5, false);
        assertEquals(Pose.ZERO, pose[BoneId.R_FOREARM.ordinal()]);
        assertEquals(new Vec3(-10, 20, -30), pose[BoneId.L_FOREARM.ordinal()].rotation());
        assertEquals(new Vec3(1, 2, 3), pose[BoneId.L_FOREARM.ordinal()].position());
        List<String> events = new ArrayList<>();
        player.advance(0.5, (id, event) -> events.add(event));
        assertEquals(List.of("step_l"), events);
    }

    @Test
    void timelineDoesNotRepeatOnRenderAndDoesNotDropSkippedCycles() {
        AnimationClip clip = rotationClip("[0,0,0]", ",\"loop\":true,\"timeline\":{\"0\":\"step_l\",\"0.5\":\"step_r\"}");
        AnimationPlayer player = new AnimationPlayer();
        player.play(entry("walk", clip, AnimationEntry.Mask.FULL, 0, 0, 0, AnimationEntry.Hand.NONE, false), 0, false);
        List<String> events = new ArrayList<>();
        player.advance(2.6, (id, event) -> events.add(event));
        player.sample(Rig.zeroPose(), 2.7, false);
        player.sample(Rig.zeroPose(), 2.8, false);
        player.advance(2.6, (id, event) -> events.add(event));
        assertEquals(List.of("step_l", "step_r", "step_l", "step_r", "step_l", "step_r"), events);
    }

    @Test
    void holdStopsWithFadeAndOneShotEmitsEndEventBeforeRemoval() {
        AnimationPlayer player = new AnimationPlayer();
        player.play(entry("hold", rotationClip("[-90,0,0]", ",\"loop\":\"hold_on_last_frame\""),
                AnimationEntry.Mask.R_ARM, 0, 0, 1, AnimationEntry.Hand.NONE, false), 0, false);
        assertEquals(90, player.sample(Rig.zeroPose(), 20, false)[BoneId.R_FOREARM.ordinal()].rotation().x());
        player.stop("hold", 20);
        assertEquals(45, player.sample(Rig.zeroPose(), 20.5, false)[BoneId.R_FOREARM.ordinal()].rotation().x());
        player.advance(21, (id, event) -> fail());
        assertTrue(player.empty());
        player.play(entry("once", rotationClip("[0,0,0]", ",\"timeline\":{\"1\":\"release\"}"),
                AnimationEntry.Mask.FULL, 0, 0, 0, AnimationEntry.Hand.NONE, false), 0, false);
        List<String> events = new ArrayList<>();
        player.advance(2, (id, event) -> events.add(event));
        assertEquals(List.of("release"), events);
        assertTrue(player.empty());
    }

    @Test
    void firstPersonFiltersAnimationsAndBodyTracks() {
        AnimationClip clip = clip("""
                "animation_length":1,"bones":{"r_forearm":{"rotation":[-90,0,0]},"waist":{"rotation":[-30,0,0]}}
                """, new ArrayList<>());
        AnimationPlayer player = new AnimationPlayer();
        player.play(entry("fpv", clip, AnimationEntry.Mask.FULL, 0, 0, 0, AnimationEntry.Hand.NONE, true), 0, false);
        player.play(entry("third", rotationClip("[-20,0,0]", ""), AnimationEntry.Mask.R_ARM,
                10, 0, 0, AnimationEntry.Hand.NONE, false), 0, false);
        Pose[] pose = player.sample(Rig.zeroPose(), 0.5, true);
        assertEquals(90, pose[BoneId.R_FOREARM.ordinal()].rotation().x());
        assertEquals(Pose.ZERO, pose[BoneId.WAIST.ordinal()]);
    }

    @Test
    void synchronizedPlaybackResumesPhaseWithoutHistoricalEvents() {
        // 晚入范围 100 个循环后，姿态接续原时钟，但不补发过去的脚步声。
        AnimationClip clip = rotationClip("{\"0\":[0,0,0],\"1\":[-100,0,0]}",
                ",\"loop\":true,\"timeline\":{\"0\":\"step_l\",\"0.5\":\"step_r\"}");
        AnimationEntry entry = entry("walk", clip, AnimationEntry.Mask.R_ARM, 0, 0, 0.5f, AnimationEntry.Hand.NONE, false);
        AnimationPlayer player = new AnimationPlayer();
        player.synchronize(entry, 110.25, 10, false, "p1");
        assertEquals(25, player.sample(Rig.zeroPose(), 110.25, false)[BoneId.R_FOREARM.ordinal()].rotation().x(), 0.001);
        List<String> events = new ArrayList<>();
        player.advance(110.25, (id, event) -> events.add(event));
        assertTrue(events.isEmpty());
        player.advance(110.6, (id, event) -> events.add(event));
        assertEquals(List.of("step_r"), events);
        player.synchronize(entry, 110.7, 10, false, "p1");
        player.advance(111.1, (id, event) -> events.add(event));
        assertEquals(List.of("step_r", "step_l"), events);
        player.synchronize(entry, 111.1, 10, false, "p2");
        player.advance(111.6, (id, event) -> events.add(event));
        assertEquals(List.of("step_r", "step_l", "step_r"), events);
        player.stop("walk", 111.6);
        player.advance(112.1, (id, event) -> fail("停止后不能继续发事件"));
        assertTrue(player.empty());
    }

    @Test
    void duplicateSyncKeepsPriorityButNewRequestInSameTickCanReplay() {
        // 追踪补发不重新抢占同优先级骨骼；服务端明确重播则应成为最新动作。
        var first = entry("first", rotationClip("[-30,0,0]", ",\"loop\":true"),
                AnimationEntry.Mask.R_ARM, 0, 0, 0, AnimationEntry.Hand.NONE, false);
        var second = entry("second", rotationClip("[-60,0,0]", ",\"loop\":true"),
                AnimationEntry.Mask.R_ARM, 0, 0, 0, AnimationEntry.Hand.NONE, false);
        AnimationPlayer player = new AnimationPlayer();
        player.synchronize(first, 10, 10, false, "first-request");
        player.synchronize(second, 10, 10, false, "second-request");
        player.synchronize(first, 10, 10, false, "first-request");
        assertEquals(60, player.sample(Rig.zeroPose(), 10, false)[BoneId.R_FOREARM.ordinal()].rotation().x());
        player.synchronize(first, 10, 10, false, "new-request");
        assertEquals(30, player.sample(Rig.zeroPose(), 10, false)[BoneId.R_FOREARM.ordinal()].rotation().x());
    }

    @Test
    void futureServerStartWaitsBeforeFiringZeroTimeEvent() {
        var clip = rotationClip("[-90,0,0]", ",\"timeline\":{\"0\":\"release\"}");
        var entry = entry("future", clip, AnimationEntry.Mask.R_ARM, 0, 0, 0, AnimationEntry.Hand.NONE, false);
        AnimationPlayer player = new AnimationPlayer();
        player.synchronize(entry, 9.9, 10, false, "future-request");
        List<String> events = new ArrayList<>();
        player.advance(9.9, (id, event) -> events.add(event));
        assertTrue(events.isEmpty());
        player.advance(10, (id, event) -> events.add(event));
        assertEquals(List.of("release"), events);
    }

    @Test
    void lateOneShotIsSkippedWhileHeldPoseRemainsVisible() {
        // 已结束的一次性动作不重播；保持末帧的动作仍应展示给新观察者。
        AnimationPlayer player = new AnimationPlayer();
        var once = entry("once", rotationClip("[-90,0,0]", ",\"timeline\":{\"1\":\"hit\"}"),
                AnimationEntry.Mask.R_ARM, 0, 0, 0.2f, AnimationEntry.Hand.NONE, false);
        player.synchronize(once, 100, 0, false, "once");
        assertTrue(player.empty());
        player.advance(100, (id, event) -> fail());
        var hold = entry("hold", rotationClip("[-90,0,0]", ",\"loop\":\"hold_on_last_frame\""),
                AnimationEntry.Mask.R_ARM, 0, 0.2f, 0.2f, AnimationEntry.Hand.NONE, false);
        player.synchronize(hold, 100, 0, false, "hold");
        assertEquals(90, player.sample(Rig.zeroPose(), 100, false)[BoneId.R_FOREARM.ordinal()].rotation().x());
    }

    @Test
    void scrubMapsNormalizedProgressWithoutRestartingBlendAndFreezesOnStop() {
        AnimationPlayer player = new AnimationPlayer();
        AnimationEntry entry = entry("scrub", clip("""
                "animation_length":1,"bones":{"r_forearm":{"rotation":{"0":[0,0,0],"1":[-100,0,0]}}}
                """,
                new ArrayList<>()), AnimationEntry.Mask.R_ARM, 5, 0.4f, 0.4f,
                AnimationEntry.Hand.NONE, false);
        player.scrub(entry, 0, 0, false);
        player.scrub(entry, 0.2, 0.5f, false);
        assertEquals(25, player.sample(Rig.zeroPose(), 0.2, false)[BoneId.R_FOREARM.ordinal()].rotation().x(), 0.001,
                "连续 scrub 必须沿用原淡入进度");
        player.scrub(entry, 0.21, 0.75f, false);
        assertEquals(39.375, player.sample(Rig.zeroPose(), 0.21, false)[BoneId.R_FOREARM.ordinal()].rotation().x(), 0.01);
        player.stop("scrub", 0.4);
        assertEquals(37.5, player.sample(Rig.zeroPose(), 0.6, false)[BoneId.R_FOREARM.ordinal()].rotation().x(), 0.01,
                "停止后必须保留 scrub 末姿态并淡出");
    }

    @Test
    void scrubInterpolatesBetweenServerTicks() {
        AnimationPlayer player = new AnimationPlayer();
        AnimationEntry entry = entry("interpolated", clip("""
                "animation_length":1,"bones":{"r_forearm":{"rotation":{"0":[0,0,0],"1":[-100,0,0]}}}
                """, new ArrayList<>()), AnimationEntry.Mask.R_ARM, 1, 0, 0,
                AnimationEntry.Hand.NONE, false);
        player.scrub(entry, 0, 0, false);
        player.scrub(entry, 0.05, 1, false);
        assertEquals(50, player.sample(Rig.zeroPose(), 0.075, false)[BoneId.R_FOREARM.ordinal()].rotation().x(), 0.001);
    }

    @Test
    void scrubUpperOverlayLeavesWalkLowerBodyUntouched() {
        AnimationPlayer player = new AnimationPlayer();
        AnimationEntry walk = entry("walk", rotationClip("[-20,0,0]", ",\"loop\":true"),
                AnimationEntry.Mask.FULL, 0, 0, 0, AnimationEntry.Hand.NONE, false);
        AnimationEntry attack = entry("attack", rotationClip("[-80,0,0]", ""),
                AnimationEntry.Mask.UPPER, 10, 0, 0, AnimationEntry.Hand.NONE, false);
        player.play(walk, 0, false);
        player.scrub(attack, 0, 0.5f, false);
        Pose[] pose = player.sample(Rig.zeroPose(), 0, false);
        assertEquals(0, pose[BoneId.R_SHIN.ordinal()].rotation().x(), 0.001,
                "scrub 上层不能接管走路的下半身");
        assertEquals(80, pose[BoneId.R_FOREARM.ordinal()].rotation().x(), 0.001);
    }

    @Test
    void indexRejectsUnknownEnumsAndPreservesFullIntegerPriority() {
        JsonObject raw = JsonParser.parseString("""
                {"id":"test","file":"animations/test.animation.json","mask":"upper","priority":2147483647,
                 "blend_in":0.1,"blend_out":0.2,"hand":"main","fpv":true}
                """).getAsJsonObject();
        assertEquals(Integer.MAX_VALUE, AnimationEntry.parse(raw, rotationClip("[0,0,0]", "")).priority());
        raw.addProperty("priority", 1.5);
        assertThrows(IllegalArgumentException.class, () -> AnimationEntry.parse(raw, rotationClip("[0,0,0]", "")));
        raw.addProperty("priority", 0);
        raw.addProperty("mask", "typo");
        assertThrows(IllegalArgumentException.class, () -> AnimationEntry.parse(raw, rotationClip("[0,0,0]", "")));
    }

    @Test
    void slowedMiningWrapInterpolatesForwardThroughTheLoopEnd() {
        AnimationClip clip = clip("""
                "animation_length":0.5,"bones":{"r_forearm":{"rotation":{
                "0":[0,0,0],"0.1":[-100,0,0],"0.2":[0,0,0],"0.5":[0,0,0]}}}
                """, new ArrayList<>());
        AnimationEntry mine = entry("mine_main", clip, AnimationEntry.Mask.UPPER,
                20, 0, 0, AnimationEntry.Hand.MAIN, false);
        AnimationPlayer player = new AnimationPlayer();
        player.scrub(mine, 0, 0.32f, false);
        player.scrub(mine, 0.05, 0, false);
        assertEquals(20, player.sample(Rig.zeroPose(), 0.075, false)
                [BoneId.R_FOREARM.ordinal()].rotation().x(), 0.001,
                "回绕中点必须取 0.18 秒，不能倒放经过 0.08 秒的砸击姿势");
        // 急迫下回绕差值可能小于旧的 0.25 阈值，也必须正向衔接。
        player.scrub(mine, 0.1, 0.24f, false);
        player.scrub(mine, 0.15, 0.08f, false);
        assertEquals(20, player.sample(Rig.zeroPose(), 0.175, false)
                [BoneId.R_FOREARM.ordinal()].rotation().x(), 0.001);
    }

    @Test
    void indexAcceptsLoopRegionAndMiningImpactWindow() {
        JsonObject raw = JsonParser.parseString("""
                {"id":"mine_main","file":"animations/mine_main.animation.json","mask":"full",
                 "priority":20,"blend_in":0.04,"blend_out":0.14,"hand":"main","fpv":false,
                 "impact_window":[0.0,0.2],"loop_region":[0.1,0.8]}
                """).getAsJsonObject();
        AnimationEntry entry = AnimationEntry.parse(raw, rotationClip("[0,0,0]", ""));
        assertEquals(0.1f, entry.loopRegionStart(), 0.0001f);
        assertEquals(0.8f, entry.loopRegionEnd(), 0.0001f);
        assertEquals(0.2f, entry.impactWindowEnd(), 0.0001f);
    }
}
