package dev.kizuna.playeranimation.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.kizuna.playeranimation.animation.AnimationClip;
import dev.kizuna.playeranimation.animation.AnimationEntry;
import dev.kizuna.playeranimation.model.BoneId;
import dev.kizuna.playeranimation.model.Pose;
import dev.kizuna.playeranimation.model.Rig;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LookLayerTest {
    private static final Path INDEX = Path.of("src/main/resources/assets/kizuna_players_animation/player_anim_index.json");
    private static final String KEY_PREFIX = "animation.kizuna_players_animation.player.";

    @Test
    void yawIsSharedByHeadChestAndWaistAndSumsToTheVanillaAngle() throws Exception {
        LookLayer.Clips clips = clips();
        for (float yaw : new float[]{-50, -25, 0, 17, 50}) {
            Pose[] pose = Rig.zeroPose();
            LookLayer.apply(pose, clips, yaw, 0, 1);
            float sum = spine(pose, 1);
            assertEquals(yaw, sum, 0.01f, "yaw=" + yaw);
            if (Math.abs(yaw) == 50) {
                // 转到极限时胸腰确实参与，而不是只扭脖子。
                assertTrue(Math.abs(pose[BoneId.CHEST.ordinal()].rotation().y()) > 10, "胸未参与");
                assertTrue(Math.abs(pose[BoneId.WAIST.ordinal()].rotation().y()) > 5, "腰未参与");
            }
        }
    }

    @Test
    void pitchFollowsVanillaSignAndCanBeSuppressed() throws Exception {
        LookLayer.Clips clips = clips();
        Pose[] down = Rig.zeroPose();
        LookLayer.apply(down, clips, 0, 90, 1);
        // Minecraft 正俯仰为低头，模型空间 X 旋转取反。
        assertEquals(-90, spine(down, 0), 0.01f);
        Pose[] half = Rig.zeroPose();
        LookLayer.apply(half, clips, 0, -90, 0.5f);
        assertEquals(45, spine(half, 0), 0.01f);
        Pose[] suppressed = Rig.zeroPose();
        LookLayer.apply(suppressed, clips, 0, 60, 0);
        assertEquals(0, spine(suppressed, 0), 0.0001f);
    }

    private static float spine(Pose[] pose, int axis) {
        return pose[BoneId.HEAD.ordinal()].rotation().axis(axis) + pose[BoneId.CHEST.ordinal()].rotation().axis(axis)
                + pose[BoneId.WAIST.ordinal()].rotation().axis(axis);
    }

    private static LookLayer.Clips clips() throws Exception {
        // 读取打包资源，确保索引的 layer: look 与导出轨道一起生效。
        JsonObject index = JsonParser.parseString(Files.readString(INDEX)).getAsJsonObject();
        Map<String, AnimationEntry> entries = new HashMap<>();
        for (var element : index.getAsJsonArray("animations")) {
            JsonObject raw = element.getAsJsonObject();
            String id = raw.get("id").getAsString();
            if (!id.startsWith("head_follow")) {
                continue;
            }
            JsonObject document = JsonParser.parseString(Files.readString(
                    INDEX.getParent().resolve(raw.get("file").getAsString()))).getAsJsonObject();
            AnimationClip clip = AnimationClip.parse(document, KEY_PREFIX + id, error -> { throw new AssertionError(error); });
            entries.put(id, AnimationEntry.parse(raw, clip));
        }
        LookLayer.Clips clips = LookLayer.clips(entries);
        assertNotNull(clips, "视线层资源缺失或未声明 layer: look");
        return clips;
    }
}
