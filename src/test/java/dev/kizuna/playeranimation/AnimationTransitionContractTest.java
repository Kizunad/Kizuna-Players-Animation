package dev.kizuna.playeranimation;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.kizuna.playeranimation.animation.AnimationClip;
import dev.kizuna.playeranimation.animation.AnimationEntry;
import dev.kizuna.playeranimation.animation.AnimationPlayer;
import dev.kizuna.playeranimation.model.BoneId;
import dev.kizuna.playeranimation.model.Pose;
import dev.kizuna.playeranimation.model.Rig;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 第 4 节状态切换表的可执行回归。动作资源重做前使用完整骨架合成夹具。 */
class AnimationTransitionContractTest {
    private static final Path TABLE = Path.of("docs/animation-specs/transitions.md");
    private static final Pattern NUMBER = Pattern.compile("\\d+(?:\\.\\d+)?");
    private static final double FRAME_LIMIT_DEGREES = 12.0;

    private record Edge(String source, String target, float blendIn, float blendOut) { }

    private static List<String> states(String cell) {
        return List.of(cell.replace("`", "").split("\\s*/\\s*")).stream()
                .map(String::trim).filter(value -> !value.isEmpty()).toList();
    }

    private static float blend(String cell, boolean first) {
        Matcher matcher = NUMBER.matcher(cell);
        List<Float> values = new ArrayList<>();
        while (matcher.find()) {
            values.add(Float.parseFloat(matcher.group()));
        }
        if (values.isEmpty()) {
            return first ? 0.08f : 0.15f;
        }
        return first ? values.get(0) : values.get(values.size() - 1);
    }

    private static List<Edge> readEdges() throws IOException {
        List<Edge> edges = new ArrayList<>();
        boolean armPoseSection = false;
        for (String line : Files.readAllLines(TABLE)) {
            if (line.startsWith("## 原版 ArmPose 使用动作")) {
                armPoseSection = true;
                continue;
            }
            if (!line.startsWith("|") || line.contains("---")) {
                continue;
            }
            String[] columns = line.substring(1, line.length() - 1).split("\\|", -1);
            if (columns.length == 5) {
                if (columns[0].trim().equals("来源")) {
                    continue;
                }
                for (String source : states(columns[0])) {
                    for (String target : states(columns[1])) {
                        edges.add(new Edge(source, target, blend(columns[2], true), blend(columns[2], false)));
                    }
                }
            } else if (armPoseSection && columns.length == 4 && columns[0].contains("→")
                    && !columns[0].trim().startsWith("来源")) {
                // ArmPose 小节使用四列，把同一行的“来源 → 目标”先拆开再展开斜杠集合。
                String[] transition = columns[0].split("→", 2);
                for (String source : states(transition[0])) {
                    for (String target : states(transition[1])) {
                        edges.add(new Edge(source, target, blend(columns[1], true), blend(columns[1], false)));
                    }
                }
            }
        }
        return edges;
    }

    private static float stateAngle(String state) {
        return Math.floorMod(state.hashCode(), 17) - 8;
    }

    private static AnimationClip syntheticClip(String state, boolean repeat) {
        return syntheticClip(state, repeat ? "repeat" : "once");
    }

    private static AnimationClip syntheticHoldClip(String state) {
        return syntheticClip(state, "hold");
    }

    private static AnimationClip syntheticClip(String state, String mode) {
        JsonObject document = new JsonObject();
        document.addProperty("format_version", "1.8.0");
        JsonObject animations = new JsonObject();
        JsonObject animation = new JsonObject();
        animation.addProperty("animation_length", 2.0);
        if (mode.equals("repeat")) {
            animation.addProperty("loop", true);
        } else if (mode.equals("hold")) {
            animation.addProperty("loop", "hold_on_last_frame");
        }
        JsonObject bones = new JsonObject();
        float angle = stateAngle(state);
        for (BoneId bone : BoneId.ALL) {
            if (bone.socket()) {
                continue;
            }
            JsonObject track = new JsonObject();
            JsonArray rotation = new JsonArray();
            rotation.add(angle);
            rotation.add(angle / 2);
            rotation.add(-angle / 3);
            track.add("rotation", rotation);
            bones.add(bone.key, track);
        }
        animation.add("bones", bones);
        String key = "animation.kizuna_players_animation.player.synthetic";
        animations.add(key, animation);
        document.add("animations", animations);
        List<String> errors = new ArrayList<>();
        AnimationClip clip = AnimationClip.parse(document, key, errors::add);
        assertTrue(errors.isEmpty(), () -> "合成切换夹具解析失败: " + errors);
        return clip;
    }

    private static AnimationEntry entry(String state, AnimationClip clip, AnimationEntry.Mask mask,
                                        int priority, float blendIn, float blendOut) {
        return new AnimationEntry(state, "animations/synthetic.animation.json", mask, priority,
                blendIn, blendOut, AnimationEntry.Hand.NONE, false, clip);
    }

    private static void assertSmooth(Pose[] before, Pose[] after, String context) {
        for (BoneId bone : BoneId.ALL) {
            for (int axis = 0; axis < 3; axis++) {
                double difference = Math.abs(after[bone.ordinal()].rotation().axis(axis)
                        - before[bone.ordinal()].rotation().axis(axis));
                assertTrue(difference <= FRAME_LIMIT_DEGREES + 0.001,
                        () -> context + " " + bone.key + " 每帧角度跳变 " + difference + "°");
            }
        }
    }

    @Test
    void everyExpandedTransitionUsesAnimationPlayerWithoutFrameJump() throws IOException {
        List<Edge> edges = readEdges();
        assertEquals(141, edges.size(), "切换表展开后的有向边数量发生变化");
        Pose[] base = Rig.zeroPose();
        for (Edge edge : edges) {
            AnimationPlayer player = new AnimationPlayer();
            player.play(entry(edge.source(), syntheticClip(edge.source(), true), AnimationEntry.Mask.FULL,
                    0, 0, 0), 0, false);
            player.play(entry(edge.target(), syntheticClip(edge.target(), true), AnimationEntry.Mask.FULL,
                    10, edge.blendIn(), edge.blendOut()), 0, false);
            Pose[] previous = player.sample(base, 0, false);
            for (int frame = 1; frame <= 72; frame++) {
                double time = frame / 60.0;
                Pose[] current = player.sample(base, time, false);
                assertSmooth(previous, current, edge.source() + " -> " + edge.target());
                previous = current;
            }
            Pose[] settled = player.sample(base, 1.2, false);
            float targetAngle = -stateAngle(edge.target());
            assertEquals(targetAngle, settled[BoneId.CHEST.ordinal()].rotation().x(), 0.01,
                    edge.source() + " -> " + edge.target() + " 未稳定到目标姿态");
        }
    }

    @Test
    void oneShotAndHoldExitFadeBackToTheCurrentBaseState() {
        Pose[] base = Rig.zeroPose();
        AnimationPlayer oneShot = new AnimationPlayer();
        oneShot.play(entry("stand", syntheticClip("stand", true), AnimationEntry.Mask.FULL, 0, 0, 0), 0, false);
        AnimationClip onceClip = syntheticClip("attack", false);
        oneShot.play(entry("attack", onceClip, AnimationEntry.Mask.FULL, 10, 0.08f, 0.16f), 0, false);
        Pose[] previous = oneShot.sample(base, 0, false);
        for (int frame = 1; frame <= 180; frame++) {
            Pose[] current = oneShot.sample(base, frame / 60.0, false);
            assertSmooth(previous, current, "一次性动作退出");
            previous = current;
        }
        assertEquals(-stateAngle("stand"), oneShot.sample(base, 2.4, false)[BoneId.CHEST.ordinal()].rotation().x(), 0.01);

        AnimationPlayer hold = new AnimationPlayer();
        hold.play(entry("stand", syntheticClip("stand", true), AnimationEntry.Mask.FULL, 0, 0, 0), 0, false);
        hold.play(entry("use_item", syntheticHoldClip("use_item"), AnimationEntry.Mask.FULL,
                10, 0.08f, 0.16f), 0, false);
        hold.stop("use_item", 0.5);
        previous = hold.sample(base, 0.5, false);
        for (int frame = 31; frame <= 60; frame++) {
            Pose[] current = hold.sample(base, frame / 60.0, false);
            assertSmooth(previous, current, "保持动作退出");
            previous = current;
        }
        assertEquals(-stateAngle("stand"), hold.sample(base, 1.2, false)[BoneId.CHEST.ordinal()].rotation().x(), 0.01);
    }

    @Test
    void requiredOverlayCombinationsKeepLowerBodyOnTheBaseTrack() {
        List<String> combinations = List.of("walk+attack", "run+mine", "walk+use_item", "run+crossbow_hold",
                "jump_air+attack", "crouch_work+use_item", "meditate+spyglass");
        Pose[] base = Rig.zeroPose();
        for (String combination : combinations) {
            String[] names = combination.split("\\+");
            AnimationPlayer player = new AnimationPlayer();
            player.play(entry(names[0], syntheticClip(names[0], true), AnimationEntry.Mask.FULL, 0, 0, 0), 0, false);
            player.play(entry(names[1], syntheticClip(names[1], true), AnimationEntry.Mask.UPPER, 10, 0.1f, 0.12f),
                    0, false);
            for (int frame = 0; frame <= 60; frame++) {
                Pose[] pose = player.sample(base, frame / 60.0, false);
                for (BoneId bone : BoneId.ALL) {
                    assertTrue(Float.isFinite(pose[bone.ordinal()].rotation().x()), combination);
                    if (bone == BoneId.PELVIS || bone.skinPart().endsWith("leg")) {
                        assertEquals(-stateAngle(names[0]), pose[bone.ordinal()].rotation().x(), 0.01,
                                combination + " 下半身被上层动作断开");
                    }
                }
            }
        }
    }
}
