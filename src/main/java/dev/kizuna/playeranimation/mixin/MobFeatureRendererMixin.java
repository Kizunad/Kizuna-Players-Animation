package dev.kizuna.playeranimation.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.kizuna.playeranimation.client.EntityFrame;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.OrderedSubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** 保留原版职业、群系、等级和覆盖层的纹理选择，只替换其几何。 */
@Mixin(RenderLayer.class)
public abstract class MobFeatureRendererMixin {
    @WrapOperation(method = "renderColoredCutoutModel", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/OrderedSubmitNodeCollector;submitModel("
                    + "Lnet/minecraft/client/model/Model;Ljava/lang/Object;Lcom/mojang/blaze3d/vertex/PoseStack;"
                    + "Lnet/minecraft/client/renderer/rendertype/RenderType;IIILnet/minecraft/client/renderer/texture/"
                    + "TextureAtlasSprite;I"
                    + "Lnet/minecraft/client/renderer/feature/ModelFeatureRenderer$CrumblingOverlay;)V"))
    private static void kpa$overlay(OrderedSubmitNodeCollector collector, Model<?> model, Object state,
                                    PoseStack matrices, RenderType type, int light, int overlay, int tint,
                                    TextureAtlasSprite sprite, int outlineColor,
                                    ModelFeatureRenderer.CrumblingOverlay crumbling, Operation<Void> original) {
        // 覆盖层与本体共享同一次采样矩阵；职业层可能复用本体模型实例，登记时带上渲染类型区分。
        EntityFrame frame = EntityFrame.of(state);
        if (frame != null) {
            frame.registerOverlay(model, type);
        }
        original.call(collector, model, state, matrices, type, light, overlay, tint, sprite, outlineColor, crumbling);
    }
}
