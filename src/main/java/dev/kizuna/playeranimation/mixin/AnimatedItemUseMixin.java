package dev.kizuna.playeranimation.mixin;

import dev.kizuna.playeranimation.animation.ItemStates;
import dev.kizuna.playeranimation.client.AnimatedItemModels;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.item.properties.conditional.IsUsingItem;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(IsUsingItem.class)
public abstract class AnimatedItemUseMixin {
    @Inject(method = "get", at = @At("HEAD"), cancellable = true)
    private void kpa$pulling(ItemStack stack, ClientLevel level, LivingEntity owner, int seed,
                             ItemDisplayContext context, CallbackInfoReturnable<Boolean> callback) {
        // 仅覆盖模型条件，功法不需要伪造原版正在使用物品的实体标记。
        Float value = AnimatedItemModels.value(stack, owner, ItemStates.Channel.PULLING);
        if (value != null) {
            callback.setReturnValue(value == 1);
        }
    }
}
