package dev.kizuna.playeranimation.mixin;

import dev.kizuna.playeranimation.animation.ItemStates;
import dev.kizuna.playeranimation.client.AnimatedItemModels;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.item.properties.numeric.CrossbowPull;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(CrossbowPull.class)
public abstract class AnimatedCrossbowPullMixin {
    @Inject(method = "get", at = @At("HEAD"), cancellable = true)
    private void kpa$pull(ItemStack stack, ClientLevel level, ItemOwner owner, int seed,
                          CallbackInfoReturnable<Float> callback) {
        // 弩属性本身就是归一化拉弦量，复用玩家和生物的同一轨道。
        if (owner == null) {
            return;
        }
        Float value = AnimatedItemModels.value(stack, owner.asLivingEntity(), ItemStates.Channel.PULL);
        if (value != null) {
            callback.setReturnValue(value);
        }
    }
}
