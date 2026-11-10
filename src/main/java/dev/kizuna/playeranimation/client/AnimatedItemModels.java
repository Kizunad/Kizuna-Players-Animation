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

    /** 弓弩谓词的混入用：只接管弓和弩，其他物品交回原版。 */
    public static Float value(ItemStack stack, LivingEntity owner, ItemStates.Channel channel) {
        if (!(stack.getItem() instanceof BowItem || stack.getItem() instanceof CrossbowItem)) {
            return null;
        }
        return clipValue(stack, owner, channel);
    }

    /** 不限物品种类：stack 是 owner 实际主手或副手里的那一份时，返回该手剪辑此刻的持物值，否则 null。 */
    public static Float clipValue(ItemStack stack, LivingEntity owner, ItemStates.Channel channel) {
        // 身份比较排除背包副本、掉落物和展示框；主副手不依赖物理左右手。
        if (owner == null) {
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
