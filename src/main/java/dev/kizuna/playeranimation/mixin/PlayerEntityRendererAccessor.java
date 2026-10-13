package dev.kizuna.playeranimation.mixin;

import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(PlayerEntityRenderer.class)
public interface PlayerEntityRendererAccessor {
    @Invoker("setModelPose")
    void kpa$setModelPose(AbstractClientPlayerEntity player);
}
