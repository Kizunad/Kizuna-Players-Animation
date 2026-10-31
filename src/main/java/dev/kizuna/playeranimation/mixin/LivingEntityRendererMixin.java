package dev.kizuna.playeranimation.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.kizuna.playeranimation.client.EntityFrame;
import dev.kizuna.playeranimation.client.MobEquipmentRenderer;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererMixin {
    @Unique private MobEquipmentRenderer kpa$equipment;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void kpa$equipmentContext(EntityRendererProvider.Context context, EntityModel<?> model,
                                      float shadowRadius, CallbackInfo callback) {
        // 装备上下文与 renderer 一起重建，资源重载不会保留旧的烘焙模型。
        kpa$equipment = new MobEquipmentRenderer(context);
    }

    @Inject(method = "submit(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;"
            + "Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;"
            + "Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/entity/LivingEntityRenderer;"
                    + "isBodyVisible(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;)Z"))
    private void kpa$submitEquipment(LivingEntityRenderState state, PoseStack matrices, SubmitNodeCollector collector,
                                     CameraRenderState camera, CallbackInfo callback) {
        // 已处于原版实体变换内；隐身生物跳过本体时，物品、盔甲和头饰仍按当前帧提交。
        EntityFrame frame = EntityFrame.of(state);
        if (frame != null && frame.mob() != null) {
            kpa$equipment.submit(frame, state, matrices, collector);
        }
    }

    @WrapOperation(method = "submit(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;"
            + "Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;"
            + "Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/SubmitNodeCollector;submitModel("
                    + "Lnet/minecraft/client/model/Model;Ljava/lang/Object;Lcom/mojang/blaze3d/vertex/PoseStack;"
                    + "Lnet/minecraft/client/renderer/rendertype/RenderType;IIILnet/minecraft/client/renderer/texture/"
                    + "TextureAtlasSprite;I"
                    + "Lnet/minecraft/client/renderer/feature/ModelFeatureRenderer$CrumblingOverlay;)V"))
    private void kpa$registerBody(SubmitNodeCollector collector, Model<?> model, Object state, PoseStack matrices,
                                  RenderType type, int light, int overlay, int tint, TextureAtlasSprite sprite,
                                  int outlineColor, ModelFeatureRenderer.CrumblingOverlay crumbling,
                                  Operation<Void> original) {
        // 本体照常提交，沿用原版朝向、光照、透明度和贴图；带帧时登记，绘制钩子换成蒙皮顶点。
        EntityFrame frame = EntityFrame.of(state);
        if (frame != null) {
            frame.registerBody(model, type);
        }
        original.call(collector, model, state, matrices, type, light, overlay, tint, sprite, outlineColor, crumbling);
    }
}
