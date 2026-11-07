package dev.kizuna.playeranimation.client;

import com.mojang.serialization.MapCodec;
import dev.kizuna.playeranimation.animation.ItemStates;
import dev.kizuna.playeranimation.mixin.ConditionalItemPropertiesAccess;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.item.properties.conditional.ConditionalItemModelProperty;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/** 弓模型的可见箭条件；未被动画接管时保持原版带箭模型。 */
public record AnimationArrowProperty() implements ConditionalItemModelProperty {
    private static final MapCodec<AnimationArrowProperty> CODEC = MapCodec.unit(new AnimationArrowProperty());

    static void register() {
        ConditionalItemPropertiesAccess.kpa$mapper().put(PlayerAssets.id("arrow"), CODEC);
    }

    @Override
    public boolean get(ItemStack stack, ClientLevel level, LivingEntity owner, int seed, ItemDisplayContext context) {
        // 拉空弓只改变贴图分支，不创建、隐藏或消耗服务端箭物品。
        Float value = AnimatedItemModels.value(stack, owner, ItemStates.Channel.ARROW);
        return value == null || value == 1;
    }

    @Override
    public MapCodec<AnimationArrowProperty> type() {
        return CODEC;
    }
}
