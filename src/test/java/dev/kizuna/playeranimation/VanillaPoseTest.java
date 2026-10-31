package dev.kizuna.playeranimation;

import dev.kizuna.playeranimation.client.PlayerMeshRenderer;
import dev.kizuna.playeranimation.model.BoneId;
import dev.kizuna.playeranimation.model.Pose;
import dev.kizuna.playeranimation.model.Rig;
import dev.kizuna.playeranimation.model.Skeleton;
import dev.kizuna.playeranimation.model.Vec3;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import org.joml.Matrix4f;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mojang.blaze3d.vertex.PoseStack;

class VanillaPoseTest {
    @Test
    void splitBonesReproduceActualVanillaPartTransformsForBothSkinTypes() {
        for (boolean slim : new boolean[]{false, true}) {
            PlayerModel<?> model = new PlayerModel<>(
                    PlayerModel.createMesh(CubeDeformation.NONE, slim).getRoot().bake(64, 64), slim);
            // 混合旋转和枢轴位移模拟走路、抬手与蹲伏，不只检查静止的单位矩阵。
            model.body.xRot = 0.4f;
            model.body.yRot = 0.2f;
            model.body.y += 2;
            model.rightArm.xRot = -1.2f;
            model.rightArm.yRot = 0.3f;
            model.rightArm.zRot = -0.2f;
            model.rightArm.z += 1.4f;
            model.leftArm.xRot = 0.5f;
            model.rightLeg.xRot = 0.7f;
            model.leftLeg.xRot = -0.7f;
            model.head.yRot = 0.4f;
            List<Skeleton.Bone> bones = new ArrayList<>();
            for (BoneId bone : BoneId.ALL) {
                bones.add(new Skeleton.Bone(bone, new Vec3(-2, 12, 1), List.of()));
            }
            Skeleton skeleton = new Skeleton(bones);
            Pose[] pose = PlayerMeshRenderer.basePose(skeleton, model, false, slim);
            Matrix4f[] actual = Rig.matrices(skeleton, pose);
            for (BoneId bone : BoneId.ALL) {
                // 持物挂点有独立的细臂修正，由实际资源的持物矩阵回归覆盖。
                if (bone == BoneId.ROOT || bone.socket()) {
                    continue;
                }
                ModelPart part = PlayerMeshRenderer.part(model, bone, false);
                var bind = part.getInitialPose();
                PoseStack vanilla = new PoseStack();
                part.translateAndRotate(vanilla);
                Matrix4f expected = new Matrix4f(vanilla.last().pose())
                        .translate(-bind.x / 16, -bind.y / 16, -bind.z / 16);
                assertTrue(expected.equals(Rig.toVanilla(actual[bone.ordinal()]), 0.00001f), bone + " slim=" + slim);
            }
        }
    }
}
