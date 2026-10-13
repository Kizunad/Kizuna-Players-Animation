package dev.kizuna.playeranimation.mixin;

import dev.kizuna.playeranimation.client.PlayerAnimationClient;
import dev.kizuna.playeranimation.client.PlayerMeshRenderer;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.feature.HeldItemFeatureRenderer;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Arm;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(HeldItemFeatureRenderer.class)
public abstract class HeldItemFeatureRendererMixin {
    @Shadow @Final private HeldItemRenderer heldItemRenderer;

    @Inject(method = "renderItem", at = @At("HEAD"), cancellable = true)
    private void kpa$item(LivingEntity entity, ItemStack item, ModelTransformationMode mode, Arm arm,
                          MatrixStack matrices, VertexConsumerProvider buffers, int light, CallbackInfo callback) {
        // 使用本次玩家渲染的同一帧，物品和手掌不会出现一个 tick 的错位。
        if (entity instanceof AbstractClientPlayerEntity player) {
            PlayerMeshRenderer.Frame frame = PlayerAnimationClient.PLAYBACK.frame(player.getUuid());
            if (frame != null) {
                PlayerMeshRenderer.item(frame, player, arm, item, matrices, buffers, light, heldItemRenderer);
                callback.cancel();
            }
        }
    }
}
