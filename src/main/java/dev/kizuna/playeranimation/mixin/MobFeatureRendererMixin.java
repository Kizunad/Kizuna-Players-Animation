package dev.kizuna.playeranimation.mixin;

import dev.kizuna.playeranimation.client.MobAnimationRenderer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.model.EntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** 保留原版职业、群系、等级和覆盖层的纹理选择，只替换其几何。 */
@Mixin(FeatureRenderer.class)
public abstract class MobFeatureRendererMixin {
    @Redirect(method = "renderModel", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/render/entity/model/EntityModel;render("
                    + "Lnet/minecraft/client/util/math/MatrixStack;"
                    + "Lnet/minecraft/client/render/VertexConsumer;IIFFFF)V"))
    private static void kpa$overlay(EntityModel<?> model, MatrixStack matrices, VertexConsumer vertices,
                                    int light, int overlay, float red, float green, float blue, float alpha,
                                    EntityModel<?> originalModel, Identifier texture, MatrixStack originalMatrices,
                                    VertexConsumerProvider buffers, int originalLight, LivingEntity entity,
                                    float originalRed, float originalGreen, float originalBlue) {
        // 原版 renderModel 已选好缓冲与显隐，覆盖层和本体共享同一次采样矩阵。
        MobAnimationRenderer.Frame frame = MobAnimationRenderer.frame(entity.getId());
        if (frame != null) {
            MobAnimationRenderer.Draw draw = new MobAnimationRenderer.Draw(matrices, vertices, light, overlay,
                    red, green, blue, alpha);
            MobAnimationRenderer.overlay(frame, draw, model);
            return;
        }
        model.render(matrices, vertices, light, overlay, red, green, blue, alpha);
    }
}
