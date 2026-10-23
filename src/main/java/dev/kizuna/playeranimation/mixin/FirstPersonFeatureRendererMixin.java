package dev.kizuna.playeranimation.mixin;

import dev.kizuna.playeranimation.client.FirstPersonBody;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.feature.Deadmau5FeatureRenderer;
import net.minecraft.client.render.entity.feature.HeadFeatureRenderer;
import net.minecraft.client.render.entity.feature.StuckObjectsFeatureRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(LivingEntityRenderer.class)
public abstract class FirstPersonFeatureRendererMixin<T extends Entity> {
    @Redirect(method = "render(Lnet/minecraft/entity/LivingEntity;FFLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/entity/feature/FeatureRenderer;render(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;ILnet/minecraft/entity/Entity;FFFFFF)V"))
    private void kpa$hideNearCamera(FeatureRenderer<T, ?> feature, MatrixStack matrices,
                                    VertexConsumerProvider buffers, int light, T entity,
                                    float limbAngle, float limbDistance, float delta,
                                    float age, float headYaw, float headPitch) {
        // 头部物品与插入身体的箭/蜂刺可能穿过近裁面，只隐藏本地全身第一人称这一趟。
        if (FirstPersonBody.rendering(entity) && (feature instanceof HeadFeatureRenderer
                || feature instanceof StuckObjectsFeatureRenderer || feature instanceof Deadmau5FeatureRenderer)) {
            return;
        }
        feature.render(matrices, buffers, light, entity, limbAngle, limbDistance, delta, age, headYaw, headPitch);
    }
}
