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
import net.minecraft.util.Arm;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpyglassPoseTest {
    @Test
    void lookingUpAndDownPreservesSupportHandContactForBothArmsAndModels() throws Exception {
        // 使用实际资源和渲染器入口，检查俯仰前后托掌相对持镜前臂的完整变换。
        Path assets = Path.of("src/main/resources/assets/kizuna_players_animation");
        JsonObject index = JsonParser.parseString(Files.readString(assets.resolve("player_anim_index.json")))
                .getAsJsonObject();
        JsonObject geometry = JsonParser.parseString(Files.readString(assets.resolve("geo/player_skeleton.geo.json")))
                .getAsJsonObject();
        for (var element : index.getAsJsonArray("animations")) {
            JsonObject raw = element.getAsJsonObject();
            String id = raw.get("id").getAsString();
            if (!id.equals("spyglass") && !id.equals("spyglass_off")) {
                continue;
            }
            JsonObject document = JsonParser.parseString(Files.readString(assets.resolve(raw.get("file").getAsString())))
                    .getAsJsonObject();
            AnimationClip clip = AnimationClip.parse(document, "animation.kizuna_players_animation.player." + id,
                    error -> { throw new AssertionError(error); });
            AnimationEntry entry = AnimationEntry.parse(raw, clip);
            for (String model : new String[]{"classic", "slim"}) {
                Skeleton skeleton = Skeleton.parse(geometry, "geometry.player." + model);
                for (Arm mainArm : Arm.values()) {
                    boolean rightHolds = id.equals("spyglass") == (mainArm == Arm.RIGHT);
                    BoneId forearm = rightHolds ? BoneId.R_FOREARM : BoneId.L_FOREARM;
                    BoneId support = rightHolds ? BoneId.L_HAND : BoneId.R_HAND;
                    for (float time : new float[]{0.5f, 1.35f, 2.3f}) {
                        AnimationPlayer animation = new AnimationPlayer();
                        animation.scrub(entry, 0, time / clip.duration(), mainArm == Arm.LEFT);
                        Pose[] base = animation.sample(Rig.zeroPose(), 1, false);
                        Matrix4f[] before = Rig.matrices(skeleton, base);
                        Matrix4f contact = new Matrix4f(before[forearm.ordinal()]).invert().mul(before[support.ordinal()]);
                        for (float pitch : new float[]{-89, -45, -20, 0, 20, 45, 89}) {
                            Pose[] pose = base.clone();
                            PlayerMeshRenderer.applyArmPosePitch(pose, skeleton, animation, 1, pitch, mainArm);
                            Matrix4f[] after = Rig.matrices(skeleton, pose);
                            Matrix4f movedContact = new Matrix4f(after[forearm.ordinal()]).invert().mul(after[support.ordinal()]);
                            String context = id + " " + model + " " + mainArm + " " + time + " " + pitch;
                            assertTrue(contact.equals(movedContact, 0.0001f), context);
                            if (pitch != 0) {
                                assertFalse(before[support.ordinal()].equals(after[support.ordinal()], 0.0001f), context);
                            }
                            if (Math.abs(pitch) == 20) {
                                // 低头必须让镜头和托掌降低，不能只保持两臂相对位置却向反方向瞄准。
                                BoneId socket = rightHolds ? BoneId.R_ITEM : BoneId.L_ITEM;
                                Vec3 pivot = skeleton.bone(socket).pivot();
                                float oldHeight = before[socket.ordinal()]
                                        .transformPosition(pivot.x(), pivot.y(), pivot.z(), new Vector3f()).y;
                                float newHeight = after[socket.ordinal()]
                                        .transformPosition(pivot.x(), pivot.y(), pivot.z(), new Vector3f()).y;
                                assertTrue((newHeight - oldHeight) * pitch < 0, context);
                            }
                        }
                    }
                }
            }
        }
    }
}
