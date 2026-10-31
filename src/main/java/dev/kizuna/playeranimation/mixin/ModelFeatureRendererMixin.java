package dev.kizuna.playeranimation.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.kizuna.playeranimation.client.EntityFrame;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** 原版照常提交模型（排序、描边、半透明不变），绘制已登记的模型时换成同帧蒙皮顶点。 */
@Mixin(ModelFeatureRenderer.class)
public abstract class ModelFeatureRendererMixin {
    @WrapOperation(method = "prepareModel", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/model/Model;"
            + "renderToBuffer(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;III)V"))
    private void kpa$draw(Model<?> model, PoseStack matrices, VertexConsumer vertices, int light, int overlay,
                          int color, Operation<Void> original,
                          @Local(argsOnly = true) ModelFeatureRenderer.Submit<?> submit) {
        // 原版刚对这次提交的状态执行过 setupAnim，部件显隐在这里读取才与原版一致。
        EntityFrame frame = EntityFrame.of(submit.state());
        if (frame != null && frame.draw(model, submit.renderType(), matrices, vertices, light, overlay, color)) {
            return;
        }
        original.call(model, matrices, vertices, light, overlay, color);
    }
}
