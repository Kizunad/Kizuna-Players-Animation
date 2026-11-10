package dev.kizuna.playeranimation.client;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.kizuna.playeranimation.animation.ItemStates;
import dev.kizuna.playeranimation.mixin.RangeSelectItemPropertiesAccess;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.item.properties.numeric.RangeSelectItemModelProperty;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

/**
 * 数值属性 kizuna_players_animation:item_state：物品在实体主手或副手里时读该手剪辑的持物轨道，
 * 剪辑没给值、不在手里或没有持有者时返回 idle（docs/asset-contract.md）。不判断物品种类。
 */
public record AnimationItemStateProperty(ItemStates.Channel channel, float idle)
        implements RangeSelectItemModelProperty {
    private static final Codec<ItemStates.Channel> CHANNEL = Codec.STRING.comapFlatMap(
            AnimationItemStateProperty::channel, channel -> channel.key);
    static final MapCodec<AnimationItemStateProperty> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            CHANNEL.fieldOf("channel").forGetter(AnimationItemStateProperty::channel),
            Codec.FLOAT.optionalFieldOf("idle", 0f).forGetter(AnimationItemStateProperty::idle)
    ).apply(instance, AnimationItemStateProperty::new));

    static void register() {
        RangeSelectItemPropertiesAccess.kpa$mapper().put(PlayerAssets.id("item_state"), CODEC);
    }

    @Override
    public float get(ItemStack stack, ClientLevel level, ItemOwner owner, int seed) {
        // 展示框、掉落物等持有者不是生物，物品栏没有持有者，都取 idle。
        LivingEntity living = null;
        if (owner != null) {
            living = owner.asLivingEntity();
        }
        Float value = AnimatedItemModels.clipValue(stack, living, channel);
        if (value == null) {
            return idle;
        }
        return value;
    }

    @Override
    public MapCodec<AnimationItemStateProperty> type() {
        return CODEC;
    }

    private static DataResult<ItemStates.Channel> channel(String key) {
        try {
            return DataResult.success(ItemStates.Channel.find(key));
        } catch (IllegalArgumentException error) {
            return DataResult.error(error::getMessage);
        }
    }
}
