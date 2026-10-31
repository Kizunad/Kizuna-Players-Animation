package dev.kizuna.playeranimation.mixin;

import dev.kizuna.playeranimation.client.EntityFrame;
import dev.kizuna.playeranimation.client.MobAnimationRenderer;
import dev.kizuna.playeranimation.client.PlayerMeshRenderer;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** extract 阶段：原版与各子类填完渲染状态后，取样本帧骨骼与装备并挂到状态上。 */
@Mixin(EntityRenderer.class)
public abstract class EntityRendererMixin {
    @Inject(method = "createRenderState(Lnet/minecraft/world/entity/Entity;F)"
            + "Lnet/minecraft/client/renderer/entity/state/EntityRenderState;", at = @At("RETURN"))
    private void kpa$extract(Entity entity, float delta, CallbackInfoReturnable<EntityRenderState> callback) {
        // 在 RETURN 取样，子类 extract（人形持物、蹲伏等）都已写入状态。
        EntityRenderState state = callback.getReturnValue();
        EntityFrame frame = kpa$frame(entity, state, delta);
        if (frame != null) {
            state.setData(EntityFrame.KEY, frame);
        }
    }

    @Unique
    private EntityFrame kpa$frame(Entity entity, EntityRenderState state, float delta) {
        Object renderer = this;
        // 玩家使用渲染器自己的身体模型（宽臂、细臂各一个实例）。
        if (entity instanceof AbstractClientPlayer player) {
            if (renderer instanceof AvatarRenderer<?> avatar && state instanceof AvatarRenderState avatarState
                    && avatar.getModel() instanceof PlayerModel vanilla) {
                return PlayerMeshRenderer.extract(player, avatarState, vanilla, delta);
            }
            return null;
        }
        // 其余生物只在登记了几何或正在播放动画时产帧。
        if (renderer instanceof LivingEntityRenderer<?, ?, ?> living && entity instanceof LivingEntity mob
                && state instanceof LivingEntityRenderState livingState) {
            return MobAnimationRenderer.extract(mob, livingState, living.getModel(), delta);
        }
        return null;
    }
}
