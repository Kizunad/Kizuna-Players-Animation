package dev.kizuna.playeranimation.mixin;

import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(PlayerRenderer.class)
public interface PlayerEntityRendererAccessor {
    @Invoker("setModelProperties")
    void kpa$setModelPose(AbstractClientPlayer player);
}
