package dev.kizuna.playeranimation.mixin;

import dev.kizuna.playeranimation.client.PlayerAnimationClient;
import dev.kizuna.playeranimation.client.PlayerMeshRenderer;
import dev.kizuna.playeranimation.client.FirstPersonBody;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.model.EntityModel;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererMixin {
    @Shadow public abstract EntityModel<?> getModel();

    @Inject(method = "render(Lnet/minecraft/entity/LivingEntity;FFLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V", at = @At("HEAD"))
    private void kpa$begin(LivingEntity entity, float yaw, float delta, MatrixStack matrices,
                            VertexConsumerProvider buffers, int light, CallbackInfo callback) {
        // 不可见实体可能跳过模型绘制，清掉上一帧以防物品沿用旧姿态。
        if (entity instanceof AbstractClientPlayerEntity) {
            PlayerAnimationClient.PLAYBACK.beginRender(entity.getUuid());
        }
    }

    @Inject(method = "render(Lnet/minecraft/entity/LivingEntity;FFLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/entity/model/EntityModel;setAngles(Lnet/minecraft/entity/Entity;FFFFF)V", shift = At.Shift.AFTER))
    private void kpa$sampleFrame(LivingEntity entity, float yaw, float delta, MatrixStack matrices,
                                 VertexConsumerProvider buffers, int light, CallbackInfo callback) {
        // 先完成原版姿态再取样，隐身玩家即使跳过皮肤绘制，盔甲和持物也使用当前帧。
        if (entity instanceof AbstractClientPlayerEntity player && getModel() instanceof PlayerEntityModel<?> vanilla) {
            PlayerAnimationClient.PLAYBACK.frame(player.getUuid(), PlayerMeshRenderer.frame(player, vanilla, delta, false));
        }
    }

    @Redirect(method = "render(Lnet/minecraft/entity/LivingEntity;FFLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/entity/model/EntityModel;render(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumer;IIFFFF)V"))
    private void kpa$render(EntityModel<?> model, MatrixStack matrices, VertexConsumer vertices, int light, int overlay,
                            float red, float green, float blue, float alpha, LivingEntity entity, float yaw, float delta,
                            MatrixStack originalMatrices, VertexConsumerProvider buffers, int originalLight) {
        // 只替换玩家本体顶点，沿用原版朝向、光照、透明度和玩家贴图绑定。
        if (entity instanceof AbstractClientPlayerEntity player && model instanceof PlayerEntityModel<?> vanilla) {
            PlayerMeshRenderer.Frame frame = PlayerAnimationClient.PLAYBACK.frame(player.getUuid());
            if (frame != null) {
                PlayerMeshRenderer.draw(frame, matrices, vertices, light, overlay, red, green, blue, alpha,
                        surface -> FirstPersonBody.visible(player, surface.bone())
                                && PlayerMeshRenderer.part(vanilla, surface.bone(), surface.outer()).visible);
                return;
            }
        }
        model.render(matrices, vertices, light, overlay, red, green, blue, alpha);
    }
}
