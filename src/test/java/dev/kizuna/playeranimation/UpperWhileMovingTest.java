package dev.kizuna.playeranimation;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.kizuna.playeranimation.animation.AnimationClip;
import dev.kizuna.playeranimation.animation.AnimationEntry;
import dev.kizuna.playeranimation.animation.AnimationPlayer;
import dev.kizuna.playeranimation.model.BoneId;
import dev.kizuna.playeranimation.model.Pose;
import dev.kizuna.playeranimation.model.Rig;
import java.nio.file.Files;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 索引字段 upper_while_moving（docs/asset-contract.md「索引条目」）：缺省关；打开后静止架势上整段生效，移动底层上只取上身。 */
class UpperWhileMovingTest {
    private static final String KEY = "animation.kizuna_players_animation.player.test";
    private static final String FLAG_ON = ",\"upper_while_moving\":true";
    private static final double SAMPLE_SECONDS = 0.5;
    private static final int BASE_PRIORITY = 1;
    private static final int TECHNIQUE_PRIORITY = 24;
    /** 底层和式各自的右大腿、右前臂角度（度），取不同值才分得出哪一层接管了这根骨。 */
    private static final int BASE_THIGH = -20;
    private static final int BASE_FOREARM = -10;
    private static final int TECHNIQUE_THIGH = -40;
    private static final int TECHNIQUE_FOREARM = -60;
    private static final Set<String> SWORD_IDS = Set.of(
            "piglin_sword_wind_around", "piglin_sword_wind_around_off",
            "piglin_sword_quiver", "piglin_sword_quiver_off",
            "piglin_sword_spring_back", "piglin_sword_spring_back_off");

    private static AnimationClip clip(int thigh, int forearm) {
        JsonObject document = JsonParser.parseString("{\"format_version\":\"1.8.0\",\"animations\":{\"" + KEY
                + "\":{\"animation_length\":1,\"bones\":{\"r_thigh\":{\"rotation\":[" + thigh + ",0,0]},"
                + "\"r_forearm\":{\"rotation\":[" + forearm + ",0,0]}}}}}").getAsJsonObject();
        return AnimationClip.parse(document, KEY, error -> { throw new AssertionError(error); });
    }

    private static JsonObject raw(String id, String mask, int priority, String extra) {
        return JsonParser.parseString("{\"id\":\"" + id + "\",\"file\":\"animations/" + id + ".animation.json\","
                + "\"mask\":\"" + mask + "\",\"priority\":" + priority + ",\"blend_in\":0,\"blend_out\":0,"
                + "\"hand\":\"none\",\"fpv\":false" + extra + "}").getAsJsonObject();
    }

    private static AnimationEntry base(String id) {
        return AnimationEntry.parse(raw(id, "full", BASE_PRIORITY, ""), clip(BASE_THIGH, BASE_FOREARM));
    }

    private static AnimationEntry technique(String extra) {
        return AnimationEntry.parse(raw("piglin_sword_wind_around", "full", TECHNIQUE_PRIORITY, extra),
                clip(TECHNIQUE_THIGH, TECHNIQUE_FOREARM));
    }

    /** 先播底层（可为空）再播式，取同一时刻的姿态。 */
    private static Pose[] sample(AnimationEntry under, AnimationEntry over, boolean crouching) {
        AnimationPlayer player = new AnimationPlayer();
        if (under != null) {
            player.play(under, 0, false /* leftHanded */);
        }
        player.play(over, 0, false /* leftHanded */);
        return player.sample(Rig.zeroPose(), SAMPLE_SECONDS, false /* firstPerson */, crouching);
    }

    private static Pose bone(Pose[] pose, BoneId bone) {
        return pose[bone.ordinal()];
    }

    @Test
    void absentFieldStaysOffAndOnlyABooleanOnAFullMaskIsAccepted() {
        AnimationClip overlay = clip(TECHNIQUE_THIGH, TECHNIQUE_FOREARM);
        assertFalse(AnimationEntry.parse(raw("plain", "full", TECHNIQUE_PRIORITY, ""), overlay).upperWhileMoving());
        assertFalse(AnimationEntry.parse(raw("plain", "full", TECHNIQUE_PRIORITY, ",\"upper_while_moving\":false"),
                overlay).upperWhileMoving());
        assertTrue(AnimationEntry.parse(raw("plain", "full", TECHNIQUE_PRIORITY, FLAG_ON), overlay).upperWhileMoving());
        assertThrows(IllegalArgumentException.class, () -> AnimationEntry.parse(
                raw("plain", "full", TECHNIQUE_PRIORITY, ",\"upper_while_moving\":\"true\""), overlay));
        assertThrows(IllegalArgumentException.class, () -> AnimationEntry.parse(
                raw("plain", "upper", TECHNIQUE_PRIORITY, FLAG_ON), overlay));
    }

    @Test
    void flaggedClipYieldsLegsToMovingBasesAndOwnsThemOnTheStillStance() {
        AnimationEntry flagged = technique(FLAG_ON);
        Pose[] alone = sample(null, flagged, false /* crouching */);
        AnimationEntry walk = base("stance_walk_forward");
        Pose[] walking = sample(null, walk, false /* crouching */);
        // 走路底层上：腿听底层，手臂照式。
        Pose[] onWalk = sample(walk, flagged, false /* crouching */);
        assertEquals(bone(walking, BoneId.R_THIGH), bone(onWalk, BoneId.R_THIGH));
        assertEquals(bone(alone, BoneId.R_FOREARM), bone(onWalk, BoneId.R_FOREARM));
        // 原版移动底层同样只取上身。
        Pose[] onVanillaWalk = sample(base("walk"), flagged, false /* crouching */);
        assertEquals(bone(walking, BoneId.R_THIGH), bone(onVanillaWalk, BoneId.R_THIGH));
        // 潜行时腿留给原版姿态。
        Pose[] crouched = sample(null, flagged, true /* crouching */);
        assertEquals(bone(Rig.zeroPose(), BoneId.R_THIGH), bone(crouched, BoneId.R_THIGH));
        assertEquals(bone(alone, BoneId.R_FOREARM), bone(crouched, BoneId.R_FOREARM));
        // 静止架势上整段生效。
        Pose[] onStance = sample(base("stance_idle"), flagged, false /* crouching */);
        assertEquals(bone(alone, BoneId.R_THIGH), bone(onStance, BoneId.R_THIGH));
        assertEquals(bone(alone, BoneId.R_FOREARM), bone(onStance, BoneId.R_FOREARM));
    }

    @Test
    void withoutTheFieldAFullClipStillOwnsTheLegsOnAMovingBase() {
        AnimationEntry plain = technique("");
        Pose[] alone = sample(null, plain, false /* crouching */);
        Pose[] onWalk = sample(base("stance_walk_forward"), plain, false /* crouching */);
        assertEquals(bone(alone, BoneId.R_THIGH), bone(onWalk, BoneId.R_THIGH));
        assertEquals(bone(alone, BoneId.R_FOREARM), bone(onWalk, BoneId.R_FOREARM));
    }

    @Test
    void shippedIndexTurnsTheFieldOnOnlyForTheSwordTechniques() throws Exception {
        JsonObject index = JsonParser.parseString(Files.readString(SwingProbe.INDEX)).getAsJsonObject();
        Set<String> flagged = new TreeSet<>();
        for (var element : index.getAsJsonArray("animations")) {
            JsonObject entry = element.getAsJsonObject();
            if (entry.has("upper_while_moving")) {
                flagged.add(entry.get("id").getAsString());
            }
        }
        assertEquals(new TreeSet<>(SWORD_IDS), flagged);
        for (String id : SWORD_IDS) {
            assertTrue(SwingProbe.entry(id).upperWhileMoving(), id);
        }
    }
}
