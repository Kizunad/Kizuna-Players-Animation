package dev.kizuna.playeranimation.server;

import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.Hand;
import net.minecraft.world.GameMode;

public final class HandSwingGameTest implements FabricGameTest {
    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
    public void offHandBlockSwingMustSelectOffHandPlacement(TestContext context) throws ReflectiveOperationException {
        // 主手剑不能把副手方块的挥动误判成攻击。
        assertScheduledSwing(context, Hand.OFF_HAND, Items.DIAMOND_SWORD, Items.STONE, "place_block_off");
    }

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
    public void mainHandBlockSwingMustSelectMainHandPlacement(TestContext context) throws ReflectiveOperationException {
        // 上次使用过副手物品，不应影响随后主手方块的挥动。
        assertScheduledSwing(context, Hand.MAIN_HAND, Items.STONE, Items.DIAMOND_SWORD, "place_block");
    }

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
    public void mainHandAttackMustSelectMainHandAttack(TestContext context) throws ReflectiveOperationException {
        // 副手方块不能改变主手剑的动作分类。
        assertScheduledSwing(context, Hand.MAIN_HAND, Items.DIAMOND_SWORD, Items.STONE, "attack_main");
    }

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
    public void offHandAttackMustSelectOffHandAttack(TestContext context) throws ReflectiveOperationException {
        // 主手方块不能改变副手剑的动作分类。
        assertScheduledSwing(context, Hand.OFF_HAND, Items.STONE, Items.DIAMOND_SWORD, "attack_off");
    }

    private static void assertScheduledSwing(TestContext context, Hand hand, Item main, Item off, String expected)
            throws ReflectiveOperationException {
        // 使用真实实体与原版物品使用、挥手入口，断言完整调度入口登记的动作。
        var server = context.getWorld().getServer();
        var player = context.createMockCreativeServerPlayerInWorld();
        try {
            swingWithDifferentUseHand(player, hand, main, off);
            context.assertFalse(player.isUsingItem(), "挥手场景不得仍在持续使用物品");
            context.assertTrue(player.handSwinging && player.preferredHand == hand, "原版必须使用指定的手挥动");
            context.assertTrue(player.getActiveHand() != hand, "物品使用手与挥动手必须不同");

            new VanillaAnimationScheduler().tick(server);
            var field = ServerPlayerAnimations.class.getDeclaredField("STATE");
            field.setAccessible(true /* flag */);
            var state = (AnimationSyncState) field.get(null);
            List<String> actions = state.snapshot(player.getUuid()).stream()
                    .map(request -> request.getAnimationId())
                    .filter(action -> !action.equals("jump"))
                    .toList();
            context.assertTrue(actions.equals(List.of(expected)), "预期 " + expected + "，实际 " + actions);
        } finally {
            // 假连接不能留到后续人体同步 tick。
            ServerPlayerAnimations.stopAll(player);
            server.getPlayerManager().remove(player);
        }
        context.complete();
    }

    private static void swingWithDifferentUseHand(ServerPlayerEntity player, Hand hand, Item main, Item off) {
        // 保留另一只手的物品使用标志，再通过原版入口开始挥手。
        player.changeGameMode(GameMode.SURVIVAL);
        player.setStackInHand(Hand.MAIN_HAND, new ItemStack(main));
        player.setStackInHand(Hand.OFF_HAND, new ItemStack(off));
        Hand previousUseHand;
        if (hand == Hand.MAIN_HAND) {
            previousUseHand = Hand.OFF_HAND;
        } else {
            previousUseHand = Hand.MAIN_HAND;
        }
        player.setCurrentHand(previousUseHand);
        player.stopUsingItem();
        player.swingHand(hand);
    }
}
