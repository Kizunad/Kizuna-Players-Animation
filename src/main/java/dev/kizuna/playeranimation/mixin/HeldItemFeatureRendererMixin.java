package dev.kizuna.playeranimation.mixin;

import dev.kizuna.playeranimation.client.PlayerAnimationClient;
import dev.kizuna.playeranimation.client.PlayerMeshRenderer;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.PlayerItemInHandLayer;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.kizuna.playeranimation.client.FirstPersonBody;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerItemInHandLayer.class)
public abstract class HeldItemFeatureRendererMixin {
    @Shadow @Final private ItemInHandRenderer itemInHandRenderer;

    @Inject(method = "renderArmWithItem", at = @At("HEAD"), cancellable = true)
    private void kpa$item(LivingEntity entity, ItemStack item, ItemDisplayContext mode, HumanoidArm arm,
                          PoseStack matrices, MultiBufferSource buffers, int light, CallbackInfo callback) {
        // 玩家子类的望远镜分支绕过父类；在分支前统一处理显隐与同帧手掌挂点。
        if (entity instanceof AbstractClientPlayer player) {
            if (FirstPersonBody.rendering(player) && FirstPersonBody.vanillaHands(player)) {
                callback.cancel();
                return;
            }
            PlayerMeshRenderer.Frame frame = PlayerAnimationClient.PLAYBACK.frame(player.getUUID());
            if (frame != null) {
                PlayerMeshRenderer.item(frame, player, arm, item, matrices, buffers, light, itemInHandRenderer);
                callback.cancel();
            }
        }
    }
}
