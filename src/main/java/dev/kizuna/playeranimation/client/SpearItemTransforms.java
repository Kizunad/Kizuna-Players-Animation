package dev.kizuna.playeranimation.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.kizuna.playeranimation.animation.AnimationEntry;
import dev.kizuna.playeranimation.animation.AnimationPlayer;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.effects.SpearAnimations;
import net.minecraft.client.renderer.entity.state.ArmedEntityRenderState;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwingAnimationType;
import org.joml.Matrix4f;

/** 手掌挂点之后的原版矛变换；专属剪辑接管持物手时不重复叠加。 */
final class SpearItemTransforms {
    enum PoseSource { VANILLA, CLIP }

    private SpearItemTransforms() { }

    static PoseSource source(AnimationPlayer animation, HumanoidArm arm, HumanoidArm mainArm, double now) {
        // 动作的主副手按实体惯用手解释，左撇子不改变索引的 hand 语义。
        if (animation == null) {
            return PoseSource.VANILLA;
        }
        AnimationEntry.Hand hand = AnimationEntry.Hand.MAIN;
        if (arm != mainArm) {
            hand = AnimationEntry.Hand.OFF;
        }
        for (var active : animation.active(now)) {
            AnimationEntry entry = active.entry();
            if (entry.id().startsWith("spear_") && entry.hand() == hand) {
                return PoseSource.CLIP;
            }
        }
        return PoseSource.VANILLA;
    }

    static Matrix4f apply(Matrix4f socket, ArmedEntityRenderState state, HumanoidArm arm, PoseSource source) {
        // 与原版 ItemInHandLayer 相同：手骨、坐标方向和握位之后，先戳刺再使用变换。
        if (source == PoseSource.CLIP) {
            return socket;
        }
        boolean stabbing = state.attackTime > 0 && state.attackArm == arm
                && state.swingAnimationType == SwingAnimationType.STAB;
        float usedTicks = state.ticksUsingItem(arm);
        if (!stabbing && usedTicks <= 0) {
            return socket;
        }
        PoseStack matrices = new PoseStack();
        matrices.last().pose().set(socket);
        if (stabbing) {
            SpearAnimations.thirdPersonAttackItem(state, matrices);
        }
        if (usedTicks > 0) {
            HumanoidModel.ArmPose pose = state.rightArmPose;
            if (arm == HumanoidArm.LEFT) {
                pose = state.leftArmPose;
            }
            ItemStack stack = state.getUseItemStackForArm(arm);
            pose.animateUseItem(state, matrices, usedTicks, arm, stack);
        }
        return new Matrix4f(matrices.last().pose());
    }
}
