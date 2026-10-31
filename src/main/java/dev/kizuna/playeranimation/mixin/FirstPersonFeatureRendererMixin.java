package dev.kizuna.playeranimation.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.kizuna.playeranimation.client.EntityFrame;
import dev.kizuna.playeranimation.client.MobEquipmentRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.layers.CustomHeadLayer;
import net.minecraft.client.renderer.entity.layers.Deadmau5EarsLayer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.layers.StuckInBodyLayer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(LivingEntityRenderer.class)
public abstract class FirstPersonFeatureRendererMixin {
    @WrapOperation(method = "submit(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;"
            + "Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;"
            + "Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/entity/layers/RenderLayer;submit("
                    + "Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;"
                    + "ILnet/minecraft/client/renderer/entity/state/EntityRenderState;FF)V"))
    private void kpa$hideNearCamera(RenderLayer<?, ?> feature, PoseStack matrices, SubmitNodeCollector collector,
                                    int light, EntityRenderState state, float yRot, float xRot,
                                    Operation<Void> original) {
        // 已由 PA 统一挂点的生物装备层不再走原版入口。
        EntityFrame frame = EntityFrame.of(state);
        if (MobEquipmentRenderer.replaces(feature, frame)) {
            return;
        }
        if (kpa$nearCamera(feature, frame)) {
            return;
        }
        original.call(feature, matrices, collector, light, state, yRot, xRot);
    }

    @Unique
    private static boolean kpa$nearCamera(RenderLayer<?, ?> feature, EntityFrame frame) {
        // 头部物品与插入身体的箭/蜂刺可能穿过近裁面，只隐藏本地全身第一人称这一趟。
        if (frame == null || frame.player() == null || !frame.player().firstPersonPass()) {
            return false;
        }
        return feature instanceof CustomHeadLayer<?, ?> || feature instanceof StuckInBodyLayer<?, ?>
                || feature instanceof Deadmau5EarsLayer;
    }
}
