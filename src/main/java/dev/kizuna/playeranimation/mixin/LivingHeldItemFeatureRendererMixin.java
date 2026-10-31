package dev.kizuna.playeranimation.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.kizuna.playeranimation.client.MobAnimationRenderer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 生物手持物复用 PA 的手骨挂点；没有实体动画时保留原版路径。 */
@Mixin(ItemInHandLayer.class)
public abstract class LivingHeldItemFeatureRendererMixin {
    @Shadow @Final private ItemInHandRenderer itemInHandRenderer;

    @Inject(method = "renderArmWithItem", at = @At("HEAD"), cancellable = true)
    private void kpa$item(LivingEntity entity, ItemStack item, ItemDisplayContext mode, HumanoidArm arm,
                          PoseStack matrices, MultiBufferSource buffers, int light, CallbackInfo callback) {
        MobAnimationRenderer.ItemDraw draw = new MobAnimationRenderer.ItemDraw(item, mode, arm,
                matrices, buffers, light);
        if (MobAnimationRenderer.item(entity, draw, itemInHandRenderer)) {
            callback.cancel();
        }
    }
}
