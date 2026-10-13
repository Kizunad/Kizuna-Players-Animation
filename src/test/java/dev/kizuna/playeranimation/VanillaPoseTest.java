package dev.kizuna.playeranimation;

import dev.kizuna.playeranimation.client.PlayerMeshRenderer;
import dev.kizuna.playeranimation.model.BoneId;
import dev.kizuna.playeranimation.model.Pose;
import dev.kizuna.playeranimation.model.Rig;
import dev.kizuna.playeranimation.model.Skeleton;
import dev.kizuna.playeranimation.model.Vec3;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.model.Dilation;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Matrix4f;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VanillaPoseTest {
    @Test
    void splitBonesReproduceActualVanillaPartTransformsForBothSkinTypes() {
        for (boolean slim : new boolean[]{false, true}) {
            PlayerEntityModel<?> model = new PlayerEntityModel<>(
                    PlayerEntityModel.getTexturedModelData(Dilation.NONE, slim).getRoot().createPart(64, 64), slim);
            // 混合旋转和枢轴位移模拟走路、抬手与蹲伏，不只检查静止的单位矩阵。
            model.body.pitch = 0.4f;
            model.body.yaw = 0.2f;
            model.body.pivotY += 2;
            model.rightArm.pitch = -1.2f;
            model.rightArm.yaw = 0.3f;
            model.rightArm.roll = -0.2f;
            model.rightArm.pivotZ += 1.4f;
            model.leftArm.pitch = 0.5f;
            model.rightLeg.pitch = 0.7f;
            model.leftLeg.pitch = -0.7f;
            model.head.yaw = 0.4f;
            List<Skeleton.Bone> bones = new ArrayList<>();
            for (BoneId bone : BoneId.ALL) {
                bones.add(new Skeleton.Bone(bone, new Vec3(-2, 12, 1), List.of()));
            }
            Skeleton skeleton = new Skeleton(bones);
            Pose[] pose = PlayerMeshRenderer.basePose(skeleton, model, false);
            Matrix4f[] actual = Rig.matrices(skeleton, pose);
            for (BoneId bone : BoneId.ALL) {
                if (bone == BoneId.ROOT) {
                    continue;
                }
                ModelPart part = PlayerMeshRenderer.part(model, bone, false);
                var bind = part.getDefaultTransform();
                MatrixStack vanilla = new MatrixStack();
                part.rotate(vanilla);
                Matrix4f expected = new Matrix4f(vanilla.peek().getPositionMatrix())
                        .translate(-bind.pivotX / 16, -bind.pivotY / 16, -bind.pivotZ / 16);
                assertTrue(expected.equals(Rig.toVanilla(actual[bone.ordinal()]), 0.00001f), bone + " slim=" + slim);
            }
        }
    }
}
