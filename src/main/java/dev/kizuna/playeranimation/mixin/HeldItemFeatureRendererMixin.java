package dev.kizuna.playeranimation.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.kizuna.playeranimation.client.EntityFrame;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.PlayerItemInHandLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerItemInHandLayer.class)
public abstract class HeldItemFeatureRendererMixin {
    @Inject(method = "submitArmWithItem(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;"
            + "Lnet/minecraft/client/renderer/item/ItemStackRenderState;Lnet/minecraft/world/item/ItemStack;"
            + "Lnet/minecraft/world/entity/HumanoidArm;Lcom/mojang/blaze3d/vertex/PoseStack;"
            + "Lnet/minecraft/client/renderer/SubmitNodeCollector;I)V", at = @At("HEAD"), cancellable = true)
    private void kpa$item(AvatarRenderState state, ItemStackRenderState item, ItemStack stack, HumanoidArm arm,
                          PoseStack matrices, SubmitNodeCollector collector, int light, CallbackInfo callback) {
        // 玩家子类的望远镜分支绕过父类；在分支前统一处理显隐与同帧手掌挂点。
        EntityFrame frame = EntityFrame.of(state);
        if (frame == null || frame.player() == null) {
            return;
        }
        callback.cancel();
        EntityFrame.PlayerParts parts = frame.player();
        // 第一人称保留原版手时，身体这一趟不画手持物；双手都空时原版不进持物层。
        if (parts.firstPersonPass() && parts.vanillaHands() || parts.emptyHands()) {
            return;
        }
        EntityFrame.HeldItem held = parts.held(arm);
        if (held != null) {
            held.submit(matrices, collector, light, OverlayTexture.NO_OVERLAY, state.outlineColor);
        }
    }
}
