package dev.kizuna.playeranimation.mixin;

import dev.kizuna.playeranimation.client.PlayerAnimationClient;
import dev.kizuna.playeranimation.client.FirstPersonBody;
import dev.kizuna.playeranimation.client.ArmorMesh;
import dev.kizuna.playeranimation.client.PlayerMeshRenderer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.feature.ArmorFeatureRenderer;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import java.util.UUID;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 保留原版盔甲材质及显隐，只将玩家盔甲顶点绑定到身体的同一帧骨骼。 */
@Mixin(ArmorFeatureRenderer.class)
public abstract class ArmorFeatureRendererMixin {
    @Unique
    private UUID kpa$currentPlayer;
    @Unique
    private boolean kpa$vanillaHands;

    @Redirect(method = {"renderArmorParts", "renderTrim", "renderGlint"},
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/entity/model/BipedEntityModel;render(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumer;IIFFFF)V"))
    private void kpa$drawArmor(BipedEntityModel<?> model, MatrixStack matrices, VertexConsumer vertices,
                                int light, int overlay, float red, float green, float blue, float alpha) {
        // 材质、染色、图集 UV 包装和附魔缓冲仍由原版选择，三条通道画同一个变形网格。
        PlayerMeshRenderer.Frame frame = kpa$currentPlayer == null ? null
                : PlayerAnimationClient.PLAYBACK.frame(kpa$currentPlayer);
        if (frame != null) {
            ArmorMesh mesh = ArmorMesh.cached(model, frame.model());
            PlayerMeshRenderer.draw(frame, mesh.surfaces(), matrices, vertices, light, overlay,
                    red, green, blue, alpha, surface -> ArmorMesh.visible(model, surface));
            return;
        }
        model.render(matrices, vertices, light, overlay, red, green, blue, alpha);
    }

    @Inject(method = "renderArmor", at = @At("HEAD"), cancellable = true)
    private void kpa$hideBeforeEquip(MatrixStack matrices, VertexConsumerProvider buffers, LivingEntity entity,
                                      EquipmentSlot slot, int light, BipedEntityModel<?> model, CallbackInfo callback) {
        kpa$currentPlayer = null;
        kpa$vanillaHands = false;
        if (!(entity instanceof AbstractClientPlayerEntity player)) {
            return;
        }
        kpa$currentPlayer = player.getUuid();
        kpa$vanillaHands = FirstPersonBody.rendering(player) && FirstPersonBody.vanillaHands(player);
        if (FirstPersonBody.rendering(player) && slot == EquipmentSlot.HEAD) {
            callback.cancel();
            return;
        }
        // 非靴子盔甲整层隐藏；靴子允许右、左腿在不同 equip 阶段恢复。
        if (PlayerAnimationClient.PLAYBACK.shouldHideArmor(player.getUuid(), slot)) {
            callback.cancel();
        }
    }

    @Inject(method = "setVisible", at = @At("TAIL"))
    private void kpa$hideBootLeg(BipedEntityModel<?> model, EquipmentSlot slot, CallbackInfo callback) {
        if (kpa$vanillaHands) {
            model.leftArm.visible = false;
            model.rightArm.visible = false;
        }
        if (slot != EquipmentSlot.FEET || kpa$currentPlayer == null) {
            return;
        }
        model.leftLeg.visible = !PlayerAnimationClient.PLAYBACK.shouldHideBoot(kpa$currentPlayer, true);
        model.rightLeg.visible = !PlayerAnimationClient.PLAYBACK.shouldHideBoot(kpa$currentPlayer, false);
    }
}
