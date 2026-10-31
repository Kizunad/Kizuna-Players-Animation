package dev.kizuna.playeranimation;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.player.PlayerModel;

/** 按迁移前的原版细臂肩高构造玩家模型，供比较绝对位置的回归测试使用。 */
public final class VanillaPlayerFixture {
    private VanillaPlayerFixture() { }

    public static PlayerModel legacyPivotPlayer(boolean slim) {
        PlayerModel model = new PlayerModel(PlayerModel.createMesh(CubeDeformation.NONE, slim)
                .getRoot().bake(64, 64), slim);
        // 26.2 原版细臂枢轴从 y=2.5 上移到 2.0；PA 交付骨架仍是 1.20.1 的细臂肩高（21.5），
        // 夹具还原旧枢轴，回归迁移前后 PA 的手臂、盔甲与持物位置不变。
        if (slim) {
            for (ModelPart arm : new ModelPart[]{model.rightArm, model.leftArm}) {
                arm.setInitialPose(arm.getInitialPose().translated(0, 0.5f, 0));
                arm.resetPose();
            }
        }
        return model;
    }
}
