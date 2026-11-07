package dev.kizuna.playeranimation.mixin;

import dev.kizuna.playeranimation.animation.ItemStates;
import dev.kizuna.playeranimation.client.AnimatedItemModels;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.item.properties.select.Charge;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Charge.class)
public abstract class AnimatedCrossbowChargeMixin {
    @Inject(method = "get(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/client/multiplayer/ClientLevel;"
            + "Lnet/minecraft/world/entity/LivingEntity;ILnet/minecraft/world/item/ItemDisplayContext;)"
            + "Lnet/minecraft/world/item/CrossbowItem$ChargeType;", at = @At("HEAD"), cancellable = true)
    private void kpa$charged(ItemStack stack, ClientLevel level, LivingEntity owner, int seed,
                             ItemDisplayContext context, CallbackInfoReturnable<CrossbowItem.ChargeType> callback) {
        // 显示装填状态时读取另一手弹药；真实弩的上弦组件保持服务端同步值。
        Float value = AnimatedItemModels.value(stack, owner, ItemStates.Channel.CHARGED);
        if (value == null) {
            return;
        }
        if (value == 0) {
            callback.setReturnValue(CrossbowItem.ChargeType.NONE);
            return;
        }
        ItemStack ammunition;
        if (stack == owner.getOffhandItem()) {
            ammunition = owner.getMainHandItem();
        } else {
            ammunition = owner.getOffhandItem();
        }
        if (ammunition.is(Items.FIREWORK_ROCKET)) {
            callback.setReturnValue(CrossbowItem.ChargeType.ROCKET);
        } else {
            callback.setReturnValue(CrossbowItem.ChargeType.ARROW);
        }
    }
}
