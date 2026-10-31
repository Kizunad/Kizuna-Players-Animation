package dev.kizuna.playeranimation.mixin;

import dev.kizuna.playeranimation.client.PlayerAnimationClient;
import dev.kizuna.playeranimation.client.PlayerMeshRenderer;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.LivingEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.kizuna.playeranimation.client.FirstPersonBody;
import dev.kizuna.playeranimation.client.MobAnimationRenderer;
import dev.kizuna.playeranimation.client.MobEquipmentRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererMixin {
    @Shadow public abstract EntityModel<?> getModel();
    @Unique private MobEquipmentRenderer kpa$equipment;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void kpa$equipmentContext(EntityRendererProvider.Context context, EntityModel<?> model,
                                     float shadowRadius, CallbackInfo callback) {
        // 装备上下文与 renderer 一起重建，资源重载不会保留旧的烘焙模型。
        kpa$equipment = new MobEquipmentRenderer(context);
    }

    @Inject(method = "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;"
            + "Lnet/minecraft/client/renderer/MultiBufferSource;I)V", at = @At("HEAD"))
    private void kpa$begin(LivingEntity entity, float yaw, float delta, PoseStack matrices,
                            MultiBufferSource buffers, int light, CallbackInfo callback) {
        // 不可见实体可能跳过模型绘制，清掉上一帧以防物品沿用旧姿态。
        if (entity instanceof AbstractClientPlayer) {
            PlayerAnimationClient.PLAYBACK.beginRender(entity.getUUID());
        } else {
            MobAnimationRenderer.begin(entity.getId());
        }
    }

    @Inject(method = "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;"
            + "Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/model/EntityModel;"
                    + "setupAnim(Lnet/minecraft/world/entity/Entity;FFFFF)V", shift = At.Shift.AFTER))
    private void kpa$sampleFrame(LivingEntity entity, float yaw, float delta, PoseStack matrices,
                                 MultiBufferSource buffers, int light, CallbackInfo callback) {
        // 先完成原版姿态再取样，隐身玩家即使跳过皮肤绘制，盔甲和持物也使用当前帧。
        if (entity instanceof AbstractClientPlayer player && getModel() instanceof PlayerModel<?> vanilla) {
            PlayerMeshRenderer.Frame frame = PlayerMeshRenderer.frame(player, vanilla, delta,
                    false /* firstPerson */);
            PlayerAnimationClient.PLAYBACK.frame(player.getUUID(), frame);
        } else {
            MobAnimationRenderer.frame(entity, getModel(), delta);
            var draw = new MobEquipmentRenderer.Draw(entity, matrices, buffers, light, delta);
            kpa$equipment.render(draw, getModel());
        }
    }

    @Inject(method = "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;"
            + "Lnet/minecraft/client/renderer/MultiBufferSource;I)V", at = @At("TAIL"))
    private void kpa$restoreMob(LivingEntity entity, float yaw, float delta, PoseStack matrices,
                                MultiBufferSource buffers, int light, CallbackInfo callback) {
        if (!(entity instanceof AbstractClientPlayer)) {
            MobAnimationRenderer.restore(entity.getId());
        }
    }

    @Redirect(method = "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;"
            + "Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/model/EntityModel;"
                    + "renderToBuffer(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;"
                    + "IIFFFF)V"))
    private void kpa$render(EntityModel<?> model, PoseStack matrices, VertexConsumer vertices, int light, int overlay,
                            float red, float green, float blue, float alpha,
                            LivingEntity entity, float yaw, float delta,
                            PoseStack originalMatrices, MultiBufferSource buffers, int originalLight) {
        // 替换玩家与已注册生物的本体顶点，沿用原版朝向、光照、透明度和贴图绑定。
        if (entity instanceof AbstractClientPlayer player && model instanceof PlayerModel<?> vanilla) {
            PlayerMeshRenderer.Frame frame = PlayerAnimationClient.PLAYBACK.frame(player.getUUID());
            if (frame != null) {
                PlayerMeshRenderer.draw(frame, matrices, vertices, light, overlay, red, green, blue, alpha,
                        surface -> FirstPersonBody.visible(player, surface.bone())
                                && PlayerMeshRenderer.part(vanilla, surface.bone(), surface.outer()).visible);
                return;
            }
        }
        if (!(entity instanceof AbstractClientPlayer)) {
            MobAnimationRenderer.Frame frame = MobAnimationRenderer.frame(entity.getId());
            if (frame != null) {
                MobAnimationRenderer.Draw draw = new MobAnimationRenderer.Draw(matrices, vertices, light, overlay,
                        red, green, blue, alpha);
                MobAnimationRenderer.draw(frame, draw, model);
                return;
            }
        }
        model.renderToBuffer(matrices, vertices, light, overlay, red, green, blue, alpha);
    }
}
