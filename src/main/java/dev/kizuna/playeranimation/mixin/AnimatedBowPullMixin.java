package dev.kizuna.playeranimation.mixin;

import dev.kizuna.playeranimation.animation.ItemStates;
import dev.kizuna.playeranimation.client.AnimatedItemModels;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.item.properties.numeric.UseDuration;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(UseDuration.class)
public abstract class AnimatedBowPullMixin {
    @Shadow @Final private boolean remaining;

    @Inject(method = "get", at = @At("HEAD"), cancellable = true)
    private void kpa$pull(ItemStack stack, ClientLevel level, ItemOwner owner, int seed,
                          CallbackInfoReturnable<Float> callback) {
        // 原版弓模型把使用 tick 乘 0.05；轨道的归一化拉弦量先还原为同一单位。
        if (remaining || owner == null || !(stack.getItem() instanceof BowItem)) {
            return;
        }
        Float value = AnimatedItemModels.value(stack, owner.asLivingEntity(), ItemStates.Channel.PULL);
        if (value != null) {
            callback.setReturnValue(value * AnimatedItemModels.BOW_DRAW_TICKS);
        }
    }
}
