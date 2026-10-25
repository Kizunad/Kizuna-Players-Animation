package dev.kizuna.playeranimation;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.kizuna.playeranimation.animation.AnimationClip;
import dev.kizuna.playeranimation.animation.AnimationEntry;
import dev.kizuna.playeranimation.animation.AnimationPlayer;
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
    private static final Path INDEX = Path.of("src/main/resources/assets/kizuna_players_animation/player_anim_index.json");
    private static final String KEY_PREFIX = "animation.kizuna_players_animation.player.";

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
        // 动作枚举保留运行时 API 和调度接线，资源重做期间不把未打包动作
        // 当成占位资源；导出一致性由 scripts/export_bbmodel_animation.py
        // 在 Gradle check 阶段逐个核对 .bbmodel 与 JSON。
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
                "ride", "sleep", "elytra", "climb"), ids);
        // 已交付标记必须与打包资源一致：调度只请求已交付动作，未打包的动作不得标为已交付。
        for (dev.kizuna.playeranimation.animation.PlayerAnimationAction action
                : dev.kizuna.playeranimation.animation.PlayerAnimationAction.values()) {
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
