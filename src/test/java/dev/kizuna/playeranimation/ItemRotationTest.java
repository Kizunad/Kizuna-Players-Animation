package dev.kizuna.playeranimation;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.kizuna.playeranimation.animation.AnimationClip;
import dev.kizuna.playeranimation.animation.AnimationEntry;
import dev.kizuna.playeranimation.animation.AnimationPlayer;
import dev.kizuna.playeranimation.animation.ItemRotation;
import dev.kizuna.playeranimation.model.BoneId;
import dev.kizuna.playeranimation.model.Pose;
import dev.kizuna.playeranimation.model.Rig;
import dev.kizuna.playeranimation.model.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** 索引字段 item_rotation：按剪辑时间转持物手挂点（docs/asset-contract.md「索引条目」）。 */
class ItemRotationTest {
    private static final String KEY = "animation.kizuna_players_animation.player.test";
    private static final float TOLERANCE = 0.0001f;
    private static final String KNIFE_FLAT = ",\"item_rotation\":[[0,[0,0,0]],[0.2,[40,10,-20]],[0.55,[40,10,-20]],"
            + "[0.75,[0,0,0]]]";

    private static AnimationClip clip(String bone) {
        JsonObject document = JsonParser.parseString("{\"format_version\":\"1.8.0\",\"animations\":{\"" + KEY
                + "\":{\"animation_length\":1,\"bones\":{\"" + bone + "\":{\"rotation\":[-10,0,0]}}}}}")
                .getAsJsonObject();
        return AnimationClip.parse(document, KEY, error -> { throw new AssertionError(error); });
    }

    private static AnimationEntry entry(String id, String hand, int priority, String extra, AnimationClip clip) {
        JsonObject raw = JsonParser.parseString("{\"id\":\"" + id + "\",\"file\":\"animations/" + id
                + ".animation.json\",\"mask\":\"full\",\"priority\":" + priority + ",\"blend_in\":0,"
                + "\"blend_out\":0.2,\"hand\":\"" + hand + "\",\"fpv\":false" + extra + "}").getAsJsonObject();
        return AnimationEntry.parse(raw, clip);
    }

    private static void assertRotation(Vec3 expected, Pose actual) {
        assertEquals(expected.x(), actual.rotation().x(), TOLERANCE);
        assertEquals(expected.y(), actual.rotation().y(), TOLERANCE);
        assertEquals(expected.z(), actual.rotation().z(), TOLERANCE);
    }

    @Test
    void keysEaseWithSmoothstepAndHoldBothEnds() {
        ItemRotation rotation = entry("herb_knife_cut", "main", 24, KNIFE_FLAT, clip("r_forearm")).itemRotation();
        assertEquals(new Vec3(0, 0, 0), rotation.sample(0));
        // 0.05 s 是第一段的四分之一：smoothstep(0.25) = 0.15625。
        assertEquals(40 * 0.15625f, rotation.sample(0.05f).x(), TOLERANCE);
        assertEquals(20, rotation.sample(0.1f).x(), TOLERANCE);
        assertEquals(new Vec3(40, 10, -20), rotation.sample(0.4f));
        assertEquals(new Vec3(0, 0, 0), rotation.sample(1));
        assertNull(entry("plain", "main", 24, "", clip("r_forearm")).itemRotation());
    }

    @Test
    void malformedTracksAndUnboundHandsAreRejected() {
        for (String bad : new String[]{
                ",\"item_rotation\":[]",
                ",\"item_rotation\":{}",
                ",\"item_rotation\":[[0,40]]",
                ",\"item_rotation\":[[0,[40,0]]]",
                ",\"item_rotation\":[[0,[40,0,0],1]]",
                ",\"item_rotation\":[[0.5,[0,0,0]],[0.5,[40,0,0]]]",
                ",\"item_rotation\":[[1.5,[0,0,0]]]",
                ",\"item_rotation\":[[0,[\"40\",0,0]]]"}) {
            assertThrows(IllegalArgumentException.class,
                    () -> entry("herb_knife_cut", "main", 24, bad, clip("r_forearm")), bad);
        }
        for (String hand : new String[]{"both", "none"}) {
            assertThrows(IllegalArgumentException.class,
                    () -> entry("herb_knife_cut", hand, 24, KNIFE_FLAT, clip("r_forearm")), hand);
        }
    }

    @Test
    void mainHandRotatesTheRightSocketAndMirrorsForLeftHanders() {
        // 挂点和骨骼关键帧同一约定：Blockbench (x, y, z) 右手按 (−x, −y, z)，
        // 左撇子镜像到左手按 (−x, y, −z)。
        AnimationEntry cut = entry("herb_knife_cut", "main", 24, KNIFE_FLAT, clip("r_forearm"));
        AnimationPlayer right = new AnimationPlayer();
        right.play(cut, 0, false /* leftHanded */);
        Pose[] rightPose = right.sample(Rig.zeroPose(), 0.4, false /* firstPerson */);
        assertRotation(new Vec3(-40, -10, -20), rightPose[BoneId.R_ITEM.ordinal()]);
        assertRotation(Vec3.ZERO, rightPose[BoneId.L_ITEM.ordinal()]);
        AnimationPlayer left = new AnimationPlayer();
        left.play(cut, 0, true /* leftHanded */);
        Pose[] leftPose = left.sample(Rig.zeroPose(), 0.4, false /* firstPerson */);
        assertRotation(new Vec3(-40, 10, 20), leftPose[BoneId.L_ITEM.ordinal()]);
        assertRotation(Vec3.ZERO, leftPose[BoneId.R_ITEM.ordinal()]);
        AnimationEntry offHand = entry("herb_knife_cut_off", "off", 24, KNIFE_FLAT, clip("l_forearm"));
        AnimationPlayer off = new AnimationPlayer();
        off.play(offHand, 0, false /* leftHanded */);
        Pose[] offPose = off.sample(Rig.zeroPose(), 0.4, false /* firstPerson */);
        assertRotation(new Vec3(-40, -10, -20), offPose[BoneId.L_ITEM.ordinal()]);
    }

    @Test
    void socketFollowsTheLayerThatOwnsTheArmAndFadesWithIt() {
        AnimationEntry cut = entry("herb_knife_cut", "main", 24, KNIFE_FLAT, clip("r_forearm"));
        AnimationPlayer player = new AnimationPlayer();
        player.play(cut, 0, false /* leftHanded */);
        // 停止后按 0.2 s 淡出：0.1 s 处权重一半，挂点转回一半。
        player.stop(cut.id(), 0.4);
        Pose fading = player.sample(Rig.zeroPose(), 0.5, false /* firstPerson */)[BoneId.R_ITEM.ordinal()];
        assertRotation(new Vec3(-20, -5, -10), fading);
        // 上层接管右臂又没写 item_rotation：刀在手里转回原版握法；
        // 只动左臂的上层不碰右手挂点。
        AnimationPlayer layered = new AnimationPlayer();
        layered.play(cut, 0, false /* leftHanded */);
        layered.play(entry("attack_main", "main", 30, "", clip("r_forearm")), 0, false /* leftHanded */);
        Pose[] layeredPose = layered.sample(Rig.zeroPose(), 0.4, false /* firstPerson */);
        assertRotation(Vec3.ZERO, layeredPose[BoneId.R_ITEM.ordinal()]);
        AnimationPlayer leftArm = new AnimationPlayer();
        leftArm.play(cut, 0, false /* leftHanded */);
        leftArm.play(entry("wave_off", "off", 30, "", clip("l_forearm")), 0, false /* leftHanded */);
        Pose[] leftArmPose = leftArm.sample(Rig.zeroPose(), 0.4, false /* firstPerson */);
        assertRotation(new Vec3(-40, -10, -20), leftArmPose[BoneId.R_ITEM.ordinal()]);
    }
}
