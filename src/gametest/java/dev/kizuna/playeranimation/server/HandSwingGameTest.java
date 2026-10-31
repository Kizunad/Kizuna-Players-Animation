package dev.kizuna.playeranimation.server;

import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;

public final class HandSwingGameTest implements FabricGameTest {
    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void offHandBlockSwingMustSelectOffHandPlacement(GameTestHelper context) throws ReflectiveOperationException {
        // 主手剑不能把副手方块的挥动误判成攻击。
        assertScheduledSwing(context, InteractionHand.OFF_HAND, Items.DIAMOND_SWORD, Items.STONE, "place_block_off");
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void mainHandBlockSwingMustSelectMainHandPlacement(GameTestHelper context) throws ReflectiveOperationException {
        // 上次使用过副手物品，不应影响随后主手方块的挥动。
        assertScheduledSwing(context, InteractionHand.MAIN_HAND, Items.STONE, Items.DIAMOND_SWORD, "place_block");
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void mainHandAttackMustSelectMainHandAttack(GameTestHelper context) throws ReflectiveOperationException {
        // 副手方块不能改变主手剑的动作分类。
        assertScheduledSwing(context, InteractionHand.MAIN_HAND, Items.DIAMOND_SWORD, Items.STONE, "attack_main");
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void offHandAttackMustSelectOffHandAttack(GameTestHelper context) throws ReflectiveOperationException {
        // 主手方块不能改变副手剑的动作分类。
        assertScheduledSwing(context, InteractionHand.OFF_HAND, Items.STONE, Items.DIAMOND_SWORD, "attack_off");
    }

    private static void assertScheduledSwing(GameTestHelper context, InteractionHand hand, Item main, Item off, String expected)
            throws ReflectiveOperationException {
        // 使用真实实体与原版物品使用、挥手入口，断言完整调度入口登记的动作。
        var server = context.getLevel().getServer();
        var player = context.makeMockServerPlayerInLevel();
        try {
            swingWithDifferentUseHand(player, hand, main, off);
            context.assertFalse(player.isUsingItem(), "挥手场景不得仍在持续使用物品");
            context.assertTrue(player.swinging && player.swingingArm == hand, "原版必须使用指定的手挥动");
            context.assertTrue(player.getUsedItemHand() != hand, "物品使用手与挥动手必须不同");

            new VanillaAnimationScheduler().tick(server);
            var field = ServerPlayerAnimations.class.getDeclaredField("STATE");
            field.setAccessible(true /* flag */);
            var state = (AnimationSyncState) field.get(null);
            List<String> actions = state.snapshot(player.getUUID()).stream()
                    .map(request -> request.getAnimationId())
                    .filter(action -> !action.equals("jump"))
                    .toList();
            context.assertTrue(actions.equals(List.of(expected)), "预期 " + expected + "，实际 " + actions);
        } finally {
            // 假连接不能留到后续人体同步 tick。
            ServerPlayerAnimations.stopAll(player);
            server.getPlayerList().remove(player);
        }
        context.succeed();
    }

    private static void swingWithDifferentUseHand(ServerPlayer player, InteractionHand hand, Item main, Item off) {
        // 保留另一只手的物品使用标志，再通过原版入口开始挥手。
        player.setGameMode(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(main));
        player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(off));
        InteractionHand previousUseHand;
        if (hand == InteractionHand.MAIN_HAND) {
            previousUseHand = InteractionHand.OFF_HAND;
        } else {
            previousUseHand = InteractionHand.MAIN_HAND;
        }
        player.startUsingItem(previousUseHand);
        player.releaseUsingItem();
        player.swing(hand);
    }
}
