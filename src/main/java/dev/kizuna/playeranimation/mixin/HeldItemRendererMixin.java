package dev.kizuna.playeranimation.mixin;

import dev.kizuna.playeranimation.client.PlayerMeshRenderer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.kizuna.playeranimation.client.FirstPersonBody;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemInHandRenderer.class)
public abstract class HeldItemRendererMixin {
    @Inject(method = "renderHandsWithItems(FLcom/mojang/blaze3d/vertex/PoseStack;"
            + "Lnet/minecraft/client/renderer/MultiBufferSource$BufferSource;"
            + "Lnet/minecraft/client/player/LocalPlayer;I)V",
            at = @At("HEAD"), cancellable = true)
    private void kpa$firstPerson(float delta, PoseStack matrices, MultiBufferSource.BufferSource buffers,
                                 LocalPlayer player, int light, CallbackInfo callback) {
        // 全身通道已经绘制了手和物品；地图等专用显示保留原版入口。
        if (FirstPersonBody.active(player)) {
            if (!FirstPersonBody.vanillaHands(player)) {
                callback.cancel();
            }
            return;
        }
        // 关闭全身通道时仍保留原有 fpv 手臂动画契约。
        if (PlayerMeshRenderer.firstPerson(player, delta, matrices, buffers, light, (ItemInHandRenderer) (Object) this)) {
            buffers.endBatch();
            callback.cancel();
        }
    }
}
