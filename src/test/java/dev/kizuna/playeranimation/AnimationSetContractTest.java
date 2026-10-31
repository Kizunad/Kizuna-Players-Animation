package dev.kizuna.playeranimation;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.kizuna.playeranimation.animation.AnimationClip;
import dev.kizuna.playeranimation.animation.AnimationEntry;
import dev.kizuna.playeranimation.animation.AnimationPlayer;
import dev.kizuna.playeranimation.animation.PlayerAnimationAction;
import dev.kizuna.playeranimation.model.BoneId;
import dev.kizuna.playeranimation.model.Pose;
import dev.kizuna.playeranimation.model.Rig;
import dev.kizuna.playeranimation.model.Vec3;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnimationSetContractTest {
    private static final Path INDEX = Path.of(
            "src/main/resources/assets/kizuna_players_animation/player_anim_index.json");
    private static final String KEY_PREFIX = "animation.kizuna_players_animation.player.";
    private static final float POSE_EPSILON = 0.0001f;
    private static final double SWING_LEAD_SECONDS = 0.1;
    private static final float SWING_PROGRESS = 0.3f;
    private static final Map<String, Double> DOOR_HITS = Map.of(
            "door_breaker_knock", 1.7, "door_breaker_cleave", 3.2, "door_breaker_ram", 2.4,
            "door_breaker_hook", 2.05, "door_breaker_sweep", 3.7, "door_breaker_close", 2.6,
            "door_breaker_chain", 5.0, "door_breaker_finale", 4.85);

    @Test
    void everyRequestedActionIsIndexedAndParsesFromThePackagedResource() throws Exception {
        JsonObject index = JsonParser.parseString(Files.readString(INDEX)).getAsJsonObject();
        Set<String> ids = new HashSet<>();
        Map<String, AnimationClip> clips = new HashMap<>();
        for (var element : index.getAsJsonArray("animations")) {
            JsonObject entry = element.getAsJsonObject();
            String id = entry.get("id").getAsString();
            assertTrue(ids.add(id), "重复动作: " + id);
            Path file = INDEX.getParent().resolve(entry.get("file").getAsString());
            JsonObject document = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
            ArrayList<String> errors = new ArrayList<>();
            AnimationClip clip = AnimationClip.parse(document, KEY_PREFIX + id, errors::add);
            assertTrue(clip.duration() > 0, id);
            assertTrue(errors.isEmpty(), id + ": " + errors);
            clip.tracks().keySet().forEach(bone -> assertFalse(bone.socket(), id + " 写入挂点"));
            clips.put(id, clip);
        }
        // 索引只收实际打包片段；导出检查另行核对 .bbmodel 与 JSON 的全部轨道。
        assertEquals(Set.of("walk", "run", "meditate", "crouch_work", "calib_zero", "calib_axes",
                "calib_item", "calib_mirror", "calib_layer", "attack_main", "attack_off", "jump",
                "equip_chestplate", "equip_chestplate_off", "equip_helmet", "equip_helmet_off",
                "equip_leggings", "equip_leggings_off", "equip_boots", "equip_boots_off",
                "mine_main", "mine_off", "place_block", "place_block_off", "spyglass", "spyglass_off",
                "toot_horn", "toot_horn_off", "crossbow_charge", "crossbow_charge_off",
                "crossbow_hold", "crossbow_hold_off", "brush", "brush_off", "use_item", "use_item_off",
                "sneak", "sneak_walk", "eat", "eat_off", "drink", "drink_off",
                "head_follow", "head_follow_pitch", "block_shield", "block_shield_off",
                "bow_draw", "bow_draw_off", "trident_charge", "trident_charge_off",
                "ride", "sleep", "elytra", "climb", "swim",
                "stance_idle", "stance_enter", "stance_exit", "stance_walk_forward", "stance_walk_back",
                "stance_walk_left", "stance_walk_right", "stance_run", "dodge_forward", "dodge_back",
                "dodge_left", "dodge_right", "technique_dash_forward", "technique_dash_back",
                "technique_dash_left", "technique_dash_right", "block_one_hand", "block_one_hand_hit",
                "block_two_hand", "block_two_hand_hit", "technique_breath", "technique_charge",
                "door_breaker_knock", "door_breaker_knock_off", "door_breaker_cleave", "door_breaker_cleave_off",
                "door_breaker_ram", "door_breaker_ram_off", "door_breaker_hook", "door_breaker_hook_off",
                "door_breaker_sweep", "door_breaker_sweep_off", "door_breaker_close", "door_breaker_close_off",
                "door_breaker_chain", "door_breaker_chain_off", "door_breaker_finale", "door_breaker_finale_off",
                "door_breaker_guard", "door_breaker_guard_off", "technique_leap",
                "peddler_snap_kick", "peddler_push_kick", "peddler_roundhouse", "peddler_ground_sweep",
                "peddler_triple_kick"), ids);
        // 已交付标记必须与打包资源一致：调度只请求已交付动作，未打包的动作不得标为已交付。
        for (PlayerAnimationAction action : PlayerAnimationAction.values()) {
            assertEquals(ids.contains(action.id()), action.delivered(), action.id());
        }
    }

    @Test
    void miningPreservesVanillaCrouchWithoutASeparateSneakResource() throws Exception {
        Pose[] base = Rig.zeroPose();
        base[BoneId.PELVIS.ordinal()] = new Pose(new Vec3(0, -2, 1), new Vec3(-8, 0, 0));
        base[BoneId.R_SHIN.ordinal()] = new Pose(Vec3.ZERO, new Vec3(-65, 0, 0));
        base[BoneId.L_SHIN.ordinal()] = new Pose(Vec3.ZERO, new Vec3(-45, 0, 0));
        AnimationPlayer player = new AnimationPlayer();
        player.scrub(resource("mine_main"), 0, 0.22f, false);
        Pose[] crouched = player.sample(base, 1, false, true);
        for (BoneId bone : BoneId.ALL) {
            if (AnimationEntry.Mask.LOWER.includes(bone)) {
                assertEquals(base[bone.ordinal()], crouched[bone.ordinal()], bone.key);
            }
        }
        assertNotEquals(base[BoneId.R_FOREARM.ordinal()], crouched[BoneId.R_FOREARM.ordinal()]);
        assertNotEquals(base[BoneId.R_SHIN.ordinal()], player.sample(base, 1, false)[BoneId.R_SHIN.ordinal()]);
    }

    @Test
    void supportStartedAfterToolStillOwnsTheLegsAndSurvivesToolExit() throws Exception {
        AnimationPlayer combined = new AnimationPlayer();
        combined.scrub(resource("mine_main"), 0, 0.22f, false);
        combined.play(resource("crouch_work"), 0.1, false);
        AnimationPlayer support = new AnimationPlayer();
        support.play(resource("crouch_work"), 0.1, false);
        for (double time : new double[]{0.4, 0.8, 1.5}) {
            Pose[] expected = support.sample(Rig.zeroPose(), time, false);
            Pose[] actual = combined.sample(Rig.zeroPose(), time, false);
            for (BoneId bone : BoneId.ALL) {
                if (AnimationEntry.Mask.LOWER.includes(bone)) {
                    assertEquals(expected[bone.ordinal()], actual[bone.ordinal()], bone.key);
                }
            }
        }
        combined.stop("mine_main", 1.5);
        assertEquals(support.sample(Rig.zeroPose(), 2, false)[BoneId.R_SHIN.ordinal()],
                combined.sample(Rig.zeroPose(), 2, false)[BoneId.R_SHIN.ordinal()]);
    }

    @Test
    void stanceBasesKeepTheirLegsUnderVanillaOverlays() throws Exception {
        // 架势底层（含静止的 stance_idle）上，原版攻击、挖掘只取上身，错步的腿不被拖回并步。
        for (String base : new String[]{"stance_idle", "stance_walk_left", "stance_run"}) {
            AnimationPlayer combined = new AnimationPlayer();
            combined.play(resource(base), 0, false);
            combined.scrub(resource("attack_main"), 0, 0.12f, false);
            AnimationPlayer alone = new AnimationPlayer();
            alone.play(resource(base), 0, false);
            Pose[] expected = alone.sample(Rig.zeroPose(), 0.5, false);
            Pose[] actual = combined.sample(Rig.zeroPose(), 0.5, false);
            for (BoneId bone : BoneId.ALL) {
                if (AnimationEntry.Mask.LOWER.includes(bone)) {
                    assertEquals(expected[bone.ordinal()], actual[bone.ordinal()], base + ":" + bone.key);
                }
            }
            assertNotEquals(expected[BoneId.R_FOREARM.ordinal()], actual[BoneId.R_FOREARM.ordinal()], base);
        }
    }

    @Test
    void stanceOverlaysOwnTheLegsOnlyOnTheStationaryStance() throws Exception {
        // 格挡与功法的腿锁在架势踝位：静止架势上整段生效；移动架势底层上只取上身。
        AnimationPlayer still = new AnimationPlayer();
        still.play(resource("stance_idle"), 0, false);
        still.play(resource("block_one_hand"), 0, false);
        AnimationPlayer blockAlone = new AnimationPlayer();
        blockAlone.play(resource("block_one_hand"), 0, false);
        assertEquals(blockAlone.sample(Rig.zeroPose(), 1.0, false)[BoneId.R_SHIN.ordinal()],
                still.sample(Rig.zeroPose(), 1.0, false)[BoneId.R_SHIN.ordinal()]);
        AnimationPlayer walking = new AnimationPlayer();
        walking.play(resource("stance_walk_forward"), 0, false);
        walking.play(resource("technique_charge"), 0, false);
        AnimationPlayer walkAlone = new AnimationPlayer();
        walkAlone.play(resource("stance_walk_forward"), 0, false);
        Pose[] expected = walkAlone.sample(Rig.zeroPose(), 0.3, false);
        Pose[] actual = walking.sample(Rig.zeroPose(), 0.3, false);
        for (BoneId bone : BoneId.ALL) {
            if (AnimationEntry.Mask.LOWER.includes(bone)) {
                assertEquals(expected[bone.ordinal()], actual[bone.ordinal()], bone.key);
            }
        }
        assertNotEquals(expected[BoneId.R_FOREARM.ordinal()], actual[BoneId.R_FOREARM.ordinal()]);
    }

    @Test
    void leapKeepsItsLegsUnderUpperBodyActionsWithoutJumpBelow() throws Exception {
        // 怪物跃步下层没有 jump：攻击（20）与蓄力（17，后起）叠上来只取上身，腿仍是跃步。
        for (String upper : new String[]{"attack_main", "technique_charge"}) {
            AnimationPlayer combined = new AnimationPlayer();
            combined.play(resource("technique_leap"), 0, false);
            if (upper.equals("attack_main")) {
                combined.scrub(resource(upper), 0.1, 0.12f, false);
            } else {
                combined.play(resource(upper), 0.1, false);
            }
            AnimationPlayer alone = new AnimationPlayer();
            alone.play(resource("technique_leap"), 0, false);
            Pose[] expected = alone.sample(Rig.zeroPose(), 0.3, false);
            Pose[] actual = combined.sample(Rig.zeroPose(), 0.3, false);
            for (BoneId bone : BoneId.ALL) {
                if (AnimationEntry.Mask.LOWER.includes(bone)) {
                    assertEquals(expected[bone.ordinal()], actual[bone.ordinal()], upper + ":" + bone.key);
                }
            }
            assertNotEquals(expected[BoneId.R_FOREARM.ordinal()], actual[BoneId.R_FOREARM.ordinal()], upper);
        }
    }


    @Test
    void doorBreakerOwnsTheWholeBodyOverStanceAndVanillaSwing() throws Exception {
        // 原版挥手与专属动画在实际命中帧重叠时，不得接管专属动画的任何骨。
        for (var entry : DOOR_HITS.entrySet()) {
            for (String suffix : new String[]{"", "_off"}) {
                assertDoorPriority(entry.getKey() + suffix, entry.getValue());
            }
        }
    }

    private static void assertDoorPriority(String id, double hit) throws Exception {
        // 同时覆盖架势腿部、动作起手、命中以及后续收势，不只检查某一段上身。
        AnimationPlayer combined = new AnimationPlayer();
        combined.play(resource("stance_idle"), 0, false /* leftHanded */);
        combined.play(resource(id), 0, false /* leftHanded */);
        combined.scrub(resource("attack_main"), hit - SWING_LEAD_SECONDS,
                SWING_PROGRESS, false /* leftHanded */);
        AnimationPlayer alone = new AnimationPlayer();
        alone.play(resource("stance_idle"), 0, false /* leftHanded */);
        alone.play(resource(id), 0, false /* leftHanded */);
        for (double time : new double[]{SWING_LEAD_SECONDS, hit, hit + SWING_LEAD_SECONDS}) {
            Pose[] expected = alone.sample(Rig.zeroPose(), time, false /* riding */);
            Pose[] actual = combined.sample(Rig.zeroPose(), time, false /* riding */);
            for (BoneId bone : BoneId.ALL) {
                assertPose(expected[bone.ordinal()], actual[bone.ordinal()], id + "@" + time + ":" + bone.key);
            }
        }
        AnimationPlayer stance = new AnimationPlayer();
        stance.play(resource("stance_idle"), 0, false /* leftHanded */);
        assertNotEquals(stance.sample(Rig.zeroPose(), hit, false /* riding */)[BoneId.R_SHIN.ordinal()],
                alone.sample(Rig.zeroPose(), hit, false /* riding */)[BoneId.R_SHIN.ordinal()], id);
    }

    @Test
    void leftHandedPlayersMirrorBothDoorVariantsAtImpact() throws Exception {
        // 主副手各自使用原资源，再由运行时交换骨与旋转方向；禁止用副手资源冒充镜像。
        for (var entry : DOOR_HITS.entrySet()) {
            for (String suffix : new String[]{"", "_off"}) {
                AnimationEntry clip = resource(entry.getKey() + suffix);
                AnimationPlayer right = new AnimationPlayer();
                AnimationPlayer left = new AnimationPlayer();
                right.play(clip, 0, false /* leftHanded */);
                left.play(clip, 0, true /* leftHanded */);
                Pose[] original = right.sample(Rig.zeroPose(), entry.getValue(), false /* riding */);
                Pose[] mirrored = left.sample(Rig.zeroPose(), entry.getValue(), false /* riding */);
                for (BoneId bone : BoneId.ALL) {
                    Pose pose = original[bone.ordinal()];
                    Vec3 position = pose.position();
                    Vec3 rotation = pose.rotation();
                    Pose expected = new Pose(new Vec3(-position.x(), position.y(), position.z()),
                            new Vec3(rotation.x(), -rotation.y(), -rotation.z()));
                    assertPose(expected, mirrored[bone.mirrored().ordinal()], clip.id() + ":" + bone.key);
                }
            }
        }
    }

    private static void assertPose(Pose expected, Pose actual, String label) {
        // 混合与镜像允许浮点舍入误差，不能放过某个旋转或位移分量。
        assertEquals(expected.rotation().x(), actual.rotation().x(), POSE_EPSILON, label);
        assertEquals(expected.rotation().y(), actual.rotation().y(), POSE_EPSILON, label);
        assertEquals(expected.rotation().z(), actual.rotation().z(), POSE_EPSILON, label);
        assertEquals(expected.position().x(), actual.position().x(), POSE_EPSILON, label);
        assertEquals(expected.position().y(), actual.position().y(), POSE_EPSILON, label);
        assertEquals(expected.position().z(), actual.position().z(), POSE_EPSILON, label);
    }

    @Test
    void doorBreakerGuardOwnsOnlyTheHoldingArm() throws Exception {
        // 持物手覆盖层（优先级 25）叠在双手式上：收臂到位后持物臂 6 根骨等于覆盖层末帧，其余骨照该式单独播放；
        // 左主手玩家两层一起镜像，覆盖臂落到另一侧。
        String[][] layers = {{"door_breaker_cleave", "door_breaker_guard"}, {"door_breaker_cleave_off", "door_breaker_guard_off"}};
        for (String[] layer : layers) {
            AnimationEntry.Mask guardMask = resource(layer[1]).mask();
            for (boolean leftHanded : new boolean[]{false, true}) {
                AnimationPlayer combined = new AnimationPlayer();
                combined.play(resource("stance_idle"), 0, leftHanded);
                combined.play(resource(layer[0]), 0, leftHanded);
                combined.play(resource(layer[1]), 0, leftHanded);
                AnimationPlayer alone = new AnimationPlayer();
                alone.play(resource("stance_idle"), 0, leftHanded);
                alone.play(resource(layer[0]), 0, leftHanded);
                AnimationPlayer guard = new AnimationPlayer();
                guard.play(resource(layer[1]), 0, leftHanded);
                for (double time : new double[]{0.5, 1.7, 3.0}) {
                    Pose[] technique = alone.sample(Rig.zeroPose(), time, false);
                    Pose[] held = guard.sample(Rig.zeroPose(), time, false);
                    Pose[] actual = combined.sample(Rig.zeroPose(), time, false);
                    for (BoneId bone : BoneId.ALL) {
                        BoneId source = leftHanded ? bone.mirrored() : bone;
                        Pose want = guardMask.includes(source) ? held[bone.ordinal()] : technique[bone.ordinal()];
                        assertPose(want, actual[bone.ordinal()], layer[1] + (leftHanded ? "[左主手]@" : "@") + time + ":" + bone.key);
                    }
                }
                BoneId forearm = BoneId.named((guardMask == AnimationEntry.Mask.L_ARM) != leftHanded ? "l_forearm" : "r_forearm");
                assertNotEquals(alone.sample(Rig.zeroPose(), 1.7, false)[forearm.ordinal()],
                        combined.sample(Rig.zeroPose(), 1.7, false)[forearm.ordinal()], layer[1]);
            }
        }
    }


    @Test
    void sneakBasesKeepTheirLegsUnderToolOverlays() throws Exception {
        // 潜行与骑乘是移动/支撑底层：叠加挖掘时腿与胯仍取底层资源，工具只接管上身。
        for (String base : new String[]{"sneak", "sneak_walk", "ride"}) {
            AnimationPlayer combined = new AnimationPlayer();
            combined.play(resource(base), 0, false);
            combined.scrub(resource("mine_main"), 0, 0.22f, false);
            AnimationPlayer alone = new AnimationPlayer();
            alone.play(resource(base), 0, false);
            for (double time : new double[]{0.3, 0.9, 1.4}) {
                Pose[] expected = alone.sample(Rig.zeroPose(), time, false);
                Pose[] actual = combined.sample(Rig.zeroPose(), time, false);
                for (BoneId bone : BoneId.ALL) {
                    if (AnimationEntry.Mask.LOWER.includes(bone)) {
                        assertEquals(expected[bone.ordinal()], actual[bone.ordinal()], base + ":" + bone.key);
                    }
                }
                assertNotEquals(expected[BoneId.R_FOREARM.ordinal()], actual[BoneId.R_FOREARM.ordinal()], base);
            }
        }
    }

    @Test
    void eatingLoopCrossesItsSeamForwardInsteadOfReplayingTheChew() throws Exception {
        // 已使用 15 → 16 tick 时进度从 0.75 秒回绕到 0.40 秒；插值中点必须落在 0.775 秒（接缝前），
        // 而不是倒放经过 0.60 秒的咬下姿势。
        AnimationPlayer wrapped = new AnimationPlayer();
        // 先等淡入完成，只比较回绕插值本身。
        wrapped.scrub(resource("eat"), 0, 0.75f / 0.80f, false);
        wrapped.scrub(resource("eat"), 0.95, 0.75f / 0.80f, false);
        wrapped.scrub(resource("eat"), 1.0, 0.40f / 0.80f, false);
        AnimationPlayer expected = new AnimationPlayer();
        expected.scrub(resource("eat"), 0, 0.775f / 0.80f, false);
        Pose actualHead = wrapped.sample(Rig.zeroPose(), 1.025, false)[BoneId.HEAD.ordinal()];
        Pose expectedHead = expected.sample(Rig.zeroPose(), 1, false)[BoneId.HEAD.ordinal()];
        assertEquals(expectedHead.rotation().x(), actualHead.rotation().x(), 0.05f);
        assertEquals(0.5f, wrapped.normalizedProgress("eat", 1.025), 0.0001f);
    }

    @Test
    void loopingScrubTakesTheShortestWayAcrossTheSeam() throws Exception {
        // 攀爬相位 0.95 → 0.05（爬过一格接缝）沿正向插值到 1.0，而不是倒放整个循环；反向下滑同理。
        AnimationPlayer forward = new AnimationPlayer();
        forward.scrub(resource("climb"), 0, 0.95f, false);
        forward.scrub(resource("climb"), 0.5, 0.95f, false);
        forward.scrub(resource("climb"), 1.0, 0.05f, false);
        AnimationPlayer expected = new AnimationPlayer();
        expected.scrub(resource("climb"), 0, 0.0f, false);
        assertEquals(expected.sample(Rig.zeroPose(), 1, false)[BoneId.R_FOREARM.ordinal()].rotation().x(),
                forward.sample(Rig.zeroPose(), 1.025, false)[BoneId.R_FOREARM.ordinal()].rotation().x(), 0.05f);
        assertEquals(0.05f, forward.normalizedProgress("climb", 1.025), 0.0001f);
        AnimationPlayer backward = new AnimationPlayer();
        backward.scrub(resource("climb"), 0, 0.05f, false);
        backward.scrub(resource("climb"), 0.5, 0.05f, false);
        backward.scrub(resource("climb"), 1.0, 0.95f, false);
        assertEquals(expected.sample(Rig.zeroPose(), 1, false)[BoneId.R_FOREARM.ordinal()].rotation().x(),
                backward.sample(Rig.zeroPose(), 1.025, false)[BoneId.R_FOREARM.ordinal()].rotation().x(), 0.05f);
    }

    @Test
    void climbingKeepsTheOtherHandOnTheLadderDuringAnAttack() throws Exception {
        // 攀爬中攻击只接管右臂：左臂、躯干和双腿仍取攀爬资源。
        AnimationPlayer combined = new AnimationPlayer();
        combined.scrub(resource("climb"), 0, 0.3f, false);
        combined.scrub(resource("attack_main"), 0, 0.3f, false);
        AnimationPlayer alone = new AnimationPlayer();
        alone.scrub(resource("climb"), 0, 0.3f, false);
        Pose[] expected = alone.sample(Rig.zeroPose(), 1, false);
        Pose[] actual = combined.sample(Rig.zeroPose(), 1, false);
        for (BoneId bone : BoneId.ALL) {
            if (!AnimationEntry.Mask.R_ARM.includes(bone)) {
                assertEquals(expected[bone.ordinal()], actual[bone.ordinal()], bone.key);
            }
        }
        assertNotEquals(expected[BoneId.R_FOREARM.ordinal()], actual[BoneId.R_FOREARM.ordinal()]);
    }

    private static AnimationEntry resource(String id) throws Exception {
        // 直接读取打包资源，避免合成夹具掩盖实际 mask、循环和优先级问题。
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
}
