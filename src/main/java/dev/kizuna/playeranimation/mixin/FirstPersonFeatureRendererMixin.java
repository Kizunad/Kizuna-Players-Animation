package dev.kizuna.playeranimation.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.kizuna.playeranimation.client.FirstPersonBody;
import dev.kizuna.playeranimation.client.MobEquipmentRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.layers.CustomHeadLayer;
import net.minecraft.client.renderer.entity.layers.Deadmau5EarsLayer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.layers.StuckInBodyLayer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(LivingEntityRenderer.class)
public abstract class FirstPersonFeatureRendererMixin<T extends Entity> {
    @Redirect(method = "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;"
            + "Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/entity/layers/RenderLayer;"
                    + "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;"
                    + "ILnet/minecraft/world/entity/Entity;FFFFFF)V"))
    private void kpa$hideNearCamera(RenderLayer<T, ?> feature, PoseStack matrices,
                                    MultiBufferSource buffers, int light, T entity,
                                    float limbAngle, float limbDistance, float delta,
                                    float age, float headYaw, float headPitch) {
        // 头部物品与插入身体的箭/蜂刺可能穿过近裁面，只隐藏本地全身第一人称这一趟。
        if (MobEquipmentRenderer.replaces(feature, entity)) {
            return;
        }
        if (FirstPersonBody.rendering(entity) && (feature instanceof CustomHeadLayer
                || feature instanceof StuckInBodyLayer || feature instanceof Deadmau5EarsLayer)) {
            return;
        }
        feature.render(matrices, buffers, light, entity, limbAngle, limbDistance, delta, age, headYaw, headPitch);
    }
}
