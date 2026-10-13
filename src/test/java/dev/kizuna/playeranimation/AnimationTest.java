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
                "timeline":{"0.5":["hit","typo"]}
                """, errors);
        assertEquals(3, errors.size());
        assertEquals(1, clip.tracks().size());
        assertEquals("hit", clip.events().get(0).name());
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
}
