package dev.kizuna.playeranimation.mixin;

import dev.kizuna.playeranimation.client.PlayerMeshRenderer;
import dev.kizuna.playeranimation.client.FirstPersonBody;
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
        // 全身通道已经绘制了手和物品；地图等专用显示保留原版入口。
        if (FirstPersonBody.active(player)) {
            if (!FirstPersonBody.vanillaHands(player)) {
                callback.cancel();
            }
            return;
        }
        // 关闭全身通道时仍保留原有 fpv 手臂动画契约。
        if (PlayerMeshRenderer.firstPerson(player, delta, matrices, buffers, light, (HeldItemRenderer) (Object) this)) {
            buffers.draw();
            callback.cancel();
        }
    }
}
