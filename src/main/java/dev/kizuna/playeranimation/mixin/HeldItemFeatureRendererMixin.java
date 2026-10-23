package dev.kizuna.playeranimation.mixin;

import dev.kizuna.playeranimation.client.PlayerAnimationClient;
import dev.kizuna.playeranimation.client.PlayerMeshRenderer;
import dev.kizuna.playeranimation.client.FirstPersonBody;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.feature.PlayerHeldItemFeatureRenderer;
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

@Mixin(PlayerHeldItemFeatureRenderer.class)
public abstract class HeldItemFeatureRendererMixin {
    @Shadow @Final private HeldItemRenderer playerHeldItemRenderer;

    @Inject(method = "renderItem", at = @At("HEAD"), cancellable = true)
    private void kpa$item(LivingEntity entity, ItemStack item, ModelTransformationMode mode, Arm arm,
                          MatrixStack matrices, VertexConsumerProvider buffers, int light, CallbackInfo callback) {
        // 玩家子类的望远镜分支绕过父类；在分支前统一处理显隐与同帧手掌挂点。
        if (entity instanceof AbstractClientPlayerEntity player) {
            if (FirstPersonBody.rendering(player) && FirstPersonBody.vanillaHands(player)) {
                callback.cancel();
                return;
            }
            PlayerMeshRenderer.Frame frame = PlayerAnimationClient.PLAYBACK.frame(player.getUuid());
            if (frame != null) {
                PlayerMeshRenderer.item(frame, player, arm, item, matrices, buffers, light, playerHeldItemRenderer);
                callback.cancel();
            }
        }
    }
}
