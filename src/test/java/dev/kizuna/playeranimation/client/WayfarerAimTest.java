package dev.kizuna.playeranimation.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.kizuna.playeranimation.animation.AnimationClip;
import dev.kizuna.playeranimation.animation.AnimationEntry;
import dev.kizuna.playeranimation.animation.AnimationPlayer;
import dev.kizuna.playeranimation.model.BoneId;
import dev.kizuna.playeranimation.model.Pose;
import dev.kizuna.playeranimation.model.Rig;
import dev.kizuna.playeranimation.model.Skeleton;
import dev.kizuna.playeranimation.model.Vec3;
import java.nio.file.Files;
import java.nio.file.Path;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 索引 aim_windows 的瞄准补偿：用已交付的满月资源核对时段权重与俯仰下弦手、弓把的相对位置（docs/asset-contract.md）。 */
class WayfarerAimTest {
    private static final float EPSILON = 0.0001f;
    private static final double BEFORE_AIM_SECONDS = 1;
    private static final double HALF_BLEND_SECONDS = 2.775;
    private static final double FULL_AIM_SECONDS = 3.3;
    private static final float HALF_WEIGHT = 0.5f;
    private static final float WEIGHT_EPSILON = 0.001f;
    private static final float[] AIM_PITCHES = {-60, -30, 30, 60};

    private record ContactBones(BoneId bowForearm, BoneId stringHand, BoneId socket) { }

    @Test
    void fullDrawAimWindowKeepsTheStringHandOnTheBowWhileLookingUpAndDown() throws Exception {
        Path assets = Path.of("src/main/resources/assets/kizuna_players_animation");
        JsonObject index = JsonParser.parseString(Files.readString(assets.resolve("player_anim_index.json")))
                .getAsJsonObject();
        JsonObject geometry = JsonParser.parseString(Files.readString(assets.resolve("geo/player_skeleton.geo.json")))
                .getAsJsonObject();
        int checked = 0;
        for (var element : index.getAsJsonArray("animations")) {
            JsonObject raw = element.getAsJsonObject();
            String id = raw.get("id").getAsString();
            if (!id.equals("wayfarer_full_draw") && !id.equals("wayfarer_full_draw_off")) {
                continue;
            }
            Path resource = assets.resolve(raw.get("file").getAsString());
            JsonObject document = JsonParser.parseString(Files.readString(resource))
                    .getAsJsonObject();
            AnimationClip clip = AnimationClip.parse(document, "animation.kizuna_players_animation.player." + id,
                    error -> { throw new AssertionError(error); });
            AnimationEntry entry = AnimationEntry.parse(raw, clip);
            AnimationPlayer animation = new AnimationPlayer();
            var request = new AnimationPlayer.PlaybackRequest(0, AnimationPlayer.Handedness.RIGHT, null, null);
            animation.play(entry, 0, request);
            assertWindowWeights(animation, id);
            assertModelContacts(animation, geometry, id);
            checked++;
        }
        assertEquals(2, checked);
    }

    private static void assertWindowWeights(AnimationPlayer animation, String id) {
        // 瞄准窗外不补，渐入半程权重减半，完整瞄准时关闭普通视线俯仰。
        AimCompensation.Weights before = AimCompensation.weights(animation, BEFORE_AIM_SECONDS);
        assertEquals(0, before.pitch(), EPSILON, id);
        assertEquals(1, before.lookPitchScale(), EPSILON, id);
        assertEquals(HALF_WEIGHT, AimCompensation.weights(animation, HALF_BLEND_SECONDS).pitch(), WEIGHT_EPSILON, id);
        AimCompensation.Weights full = AimCompensation.weights(animation, FULL_AIM_SECONDS);
        assertEquals(1, full.pitch(), EPSILON, id);
        assertEquals(0, full.lookPitchScale(), EPSILON, id);
    }

    private static void assertModelContacts(AnimationPlayer animation, JsonObject geometry, String id) {
        // 主手版右手握弓、左手勾弦；副手版对调，两种体型都保持同一个接触关系。
        ContactBones bones = new ContactBones(BoneId.R_FOREARM, BoneId.L_HAND, BoneId.R_ITEM);
        if (id.endsWith("_off")) {
            bones = new ContactBones(BoneId.L_FOREARM, BoneId.R_HAND, BoneId.L_ITEM);
        }
        Pose[] base = animation.sample(Rig.zeroPose(), FULL_AIM_SECONDS, false /* firstPerson */);
        for (String model : new String[]{"classic", "slim"}) {
            Skeleton skeleton = Skeleton.parse(geometry, "geometry.player." + model);
            assertPitchContacts(skeleton, base, bones, id + " " + model);
        }
    }

    private static void assertPitchContacts(Skeleton skeleton, Pose[] base, ContactBones bones, String context) {
        Matrix4f[] before = Rig.matrices(skeleton, base);
        Matrix4f contact = new Matrix4f(before[bones.bowForearm().ordinal()])
                .invert()
                .mul(before[bones.stringHand().ordinal()]);
        for (float pitch : AIM_PITCHES) {
            Pose[] pose = base.clone();
            AimCompensation.apply(pose, skeleton, 1, pitch);
            Matrix4f[] after = Rig.matrices(skeleton, pose);
            String assertion = context + " " + pitch;
            Matrix4f moved = new Matrix4f(after[bones.bowForearm().ordinal()])
                    .invert()
                    .mul(after[bones.stringHand().ordinal()]);
            assertTrue(contact.equals(moved, EPSILON), assertion);
            BoneId hand = bones.stringHand();
            assertFalse(before[hand.ordinal()].equals(after[hand.ordinal()], EPSILON), assertion);
            // 低头时弓把下沉、抬头时上抬，不能只保持接触却朝反方向瞄。
            BoneId socket = bones.socket();
            Vec3 pivot = skeleton.bone(socket).pivot();
            float oldHeight = before[socket.ordinal()]
                    .transformPosition(pivot.x(), pivot.y(), pivot.z(), new Vector3f()).y;
            float newHeight = after[socket.ordinal()]
                    .transformPosition(pivot.x(), pivot.y(), pivot.z(), new Vector3f()).y;
            assertTrue((newHeight - oldHeight) * pitch < 0, assertion);
        }
    }
}
