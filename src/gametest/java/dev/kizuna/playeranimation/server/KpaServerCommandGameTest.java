package dev.kizuna.playeranimation.server;

import com.mojang.brigadier.suggestion.Suggestion;
import java.util.List;
import java.util.Set;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

public final class KpaServerCommandGameTest implements FabricGameTest {
    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void animationArgumentsCompleteFromBuiltInIndex(GameTestHelper context) {
        // 补全来自模组自带索引：基础运动与原版动作都要在列，play 与 stop 列出同一份 id。
        List<String> ids = ServerPlayerAnimations.animationIds();
        context.assertTrue(ids.containsAll(List.of("walk", "run", "meditate", "attack_main")),
                "内置索引应包含基础运动与原版动作，实际 " + ids);
        var dispatcher = context.getLevel().getServer().getCommands().getDispatcher();
        CommandSourceStack source = context.getLevel().getServer().createCommandSourceStack();
        for (String input : List.of("kpa_server play @s ", "kpa_server stop @s ")) {
            var parsed = dispatcher.parse(input, source);
            List<String> suggested = dispatcher.getCompletionSuggestions(parsed).join().getList().stream()
                    .map(Suggestion::getText)
                    .toList();
            context.assertTrue(suggested.size() == ids.size() && Set.copyOf(suggested).equals(Set.copyOf(ids)),
                    input + "的补全应与内置索引一致，实际 " + suggested);
        }
        context.succeed();
    }
}
