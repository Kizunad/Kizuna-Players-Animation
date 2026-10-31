package dev.kizuna.playeranimation.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.kizuna.playeranimation.client.MobAnimationRenderer;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** 保留原版职业、群系、等级和覆盖层的纹理选择，只替换其几何。 */
@Mixin(RenderLayer.class)
public abstract class MobFeatureRendererMixin {
    @Redirect(method = "renderColoredCutoutModel", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/model/EntityModel;renderToBuffer(Lcom/mojang/blaze3d/vertex/PoseStack;"
                    + "Lcom/mojang/blaze3d/vertex/VertexConsumer;IIFFFF)V"))
    private static void kpa$overlay(EntityModel<?> model, PoseStack matrices, VertexConsumer vertices,
                                    int light, int overlay, float red, float green, float blue, float alpha,
                                    EntityModel<?> originalModel, ResourceLocation texture, PoseStack originalMatrices,
                                    MultiBufferSource buffers, int originalLight, LivingEntity entity,
                                    float originalRed, float originalGreen, float originalBlue) {
        // 原版 renderModel 已选好缓冲与显隐，覆盖层和本体共享同一次采样矩阵。
        MobAnimationRenderer.Frame frame = MobAnimationRenderer.frame(entity.getId());
        if (frame != null) {
            MobAnimationRenderer.Draw draw = new MobAnimationRenderer.Draw(matrices, vertices, light, overlay,
                    red, green, blue, alpha);
            MobAnimationRenderer.overlay(frame, draw, model);
            return;
        }
        model.renderToBuffer(matrices, vertices, light, overlay, red, green, blue, alpha);
    }
}
