package dev.kizuna.playeranimation.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.kizuna.playeranimation.client.EntityFrame;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.EquipmentLayerRenderer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.resources.model.EquipmentClientInfo;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.equipment.EquipmentAsset;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** 保留原版盔甲材质及显隐，将盔甲顶点绑定到身体的同一帧骨骼。 */
@Mixin(HumanoidArmorLayer.class)
public abstract class ArmorFeatureRendererMixin {
    @WrapOperation(method = "renderArmorPiece", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/entity/layers/EquipmentLayerRenderer;renderLayers("
                    + "Lnet/minecraft/client/resources/model/EquipmentClientInfo$LayerType;"
                    + "Lnet/minecraft/resources/ResourceKey;Lnet/minecraft/client/model/Model;Ljava/lang/Object;"
                    + "Lnet/minecraft/world/item/ItemStack;Lcom/mojang/blaze3d/vertex/PoseStack;"
                    + "Lnet/minecraft/client/renderer/SubmitNodeCollector;II)V"))
    private void kpa$armor(EquipmentLayerRenderer renderer, EquipmentClientInfo.LayerType layerType,
                           ResourceKey<EquipmentAsset> asset, Model<?> model, Object state, ItemStack stack,
                           PoseStack matrices, SubmitNodeCollector collector, int light, int outlineColor,
                           Operation<Void> original, @Local(argsOnly = true) EquipmentSlot slot) {
        // 材质、染色、纹饰和附魔仍由原版逐层提交，同一盔甲模型的各层都画同一个变形网格。
        EntityFrame frame = EntityFrame.of(state);
        if (frame != null) {
            // 穿戴动作未到位、或第一人称身体这一趟的头盔，整件不提交。
            if (frame.hidesArmor(slot)) {
                return;
            }
            frame.registerArmor(model, slot);
        }
        original.call(renderer, layerType, asset, model, state, stack, matrices, collector, light, outlineColor);
    }
}
