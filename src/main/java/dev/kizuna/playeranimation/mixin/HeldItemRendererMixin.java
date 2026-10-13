package dev.kizuna.playeranimation.mixin;

import dev.kizuna.playeranimation.client.PlayerMeshRenderer;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(HeldItemRenderer.class)
public abstract class HeldItemRendererMixin {
    @Inject(method = "renderItem(FLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider$Immediate;Lnet/minecraft/client/network/ClientPlayerEntity;I)V",
            at = @At("HEAD"), cancellable = true)
    private void kpa$firstPerson(float delta, MatrixStack matrices, VertexConsumerProvider.Immediate buffers,
                                 ClientPlayerEntity player, int light, CallbackInfo callback) {
        // 只有明确标记 fpv 的活动动画才接管第一人称，结束后自动回到原版。
        if (PlayerMeshRenderer.firstPerson(player, delta, matrices, buffers, light, (HeldItemRenderer) (Object) this)) {
            buffers.draw();
            callback.cancel();
        }
    }
}
