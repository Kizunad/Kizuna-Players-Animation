package dev.kizuna.playeranimation.mixin;

import dev.kizuna.playeranimation.client.MobAnimationRenderer;
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

/** 生物手持物复用 PA 的手骨挂点；没有实体动画时保留原版路径。 */
@Mixin(HeldItemFeatureRenderer.class)
public abstract class LivingHeldItemFeatureRendererMixin {
    @Shadow @Final private HeldItemRenderer heldItemRenderer;

    @Inject(method = "renderItem", at = @At("HEAD"), cancellable = true)
    private void kpa$item(LivingEntity entity, ItemStack item, ModelTransformationMode mode, Arm arm,
                          MatrixStack matrices, VertexConsumerProvider buffers, int light, CallbackInfo callback) {
        MobAnimationRenderer.ItemDraw draw = new MobAnimationRenderer.ItemDraw(item, mode, arm,
                matrices, buffers, light);
        if (MobAnimationRenderer.item(entity, draw, heldItemRenderer)) {
            callback.cancel();
        }
    }
}
