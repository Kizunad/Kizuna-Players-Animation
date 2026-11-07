package dev.kizuna.playeranimation.client;

import dev.kizuna.playeranimation.animation.AnimationEntry;
import dev.kizuna.playeranimation.animation.AnimationPlayer;
import dev.kizuna.playeranimation.animation.ItemStates;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;

/** 玩家和生物的物品模型共用同一播放时钟；只读取持物轨道，不改变实体使用状态。 */
public final class AnimatedItemModels {
    public static final float BOW_DRAW_TICKS = 20;

    private AnimatedItemModels() { }

    public static Float value(ItemStack stack, LivingEntity owner, ItemStates.Channel channel) {
        // 身份比较排除背包副本、掉落物和展示框；主副手不依赖物理左右手。
        if (owner == null || !(stack.getItem() instanceof BowItem || stack.getItem() instanceof CrossbowItem)) {
            return null;
        }
        AnimationEntry.Hand hand;
        if (stack == owner.getMainHandItem()) {
            hand = AnimationEntry.Hand.MAIN;
        } else if (stack == owner.getOffhandItem()) {
            hand = AnimationEntry.Hand.OFF;
        } else {
            return null;
        }
        var playback = PlayerAnimationClient.PLAYBACK;
        AnimationPlayer animation;
        if (owner instanceof Player) {
            animation = playback.player(owner.getUUID());
        } else {
            animation = playback.entity(owner.getId());
        }
        if (animation == null) {
            return null;
        }
        float partialTick = Minecraft.getInstance().getDeltaTracker()
                .getGameTimeDeltaPartialTick(false /* ignoreFreeze */);
        return animation.itemValue(hand, channel, PlayerPlayback.now(partialTick));
    }
}
