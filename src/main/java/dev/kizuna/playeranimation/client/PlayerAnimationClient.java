package dev.kizuna.playeranimation.client;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.arguments.BoolArgumentType;
import dev.kizuna.playeranimation.api.PlayerAnimationHostImpl;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientEntityEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.PackType;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class PlayerAnimationClient implements ClientModInitializer {
    public static final String ID = "kizuna_players_animation";
    public static final Logger LOG = LoggerFactory.getLogger(ID);
    public static final PlayerPlayback PLAYBACK = new PlayerPlayback();
    public static final PlayerAssets ASSETS = new PlayerAssets();

    @Override
    public void onInitializeClient() {
        // 资源和时钟使用 Fabric 生命周期，渲染入口只负责取样与提交顶点。
        PlayerAnimationHostImpl.bind(EntityAnimationApi::exchange);
        ClientAnimationNetworking.register();
        LevelRenderEvents.COLLECT_SUBMITS.register(FirstPersonBody::render);
        ResourceLoader.get(PackType.CLIENT_RESOURCES).registerReloadListener(PlayerAssets.id("player_assets"), ASSETS);
        ClientTickEvents.END_CLIENT_TICK.register(PLAYBACK::tick);
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> PLAYBACK.clear());
        ClientEntityEvents.ENTITY_UNLOAD.register((entity, world) -> {
            PLAYBACK.remove(entity.getUUID());
            PLAYBACK.remove(entity.getId());
        });
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registry) -> dispatcher.register(
                ClientCommands.literal("kpa")
                        .then(ClientCommands.literal("firstperson")
                                .then(ClientCommands.argument("enabled", BoolArgumentType.bool())
                                        .executes(context -> {
                                            // 开关只影响当前客户端会话，便于对比身体与原版视角。
                                            boolean enabled = BoolArgumentType.getBool(context, "enabled");
                                            FirstPersonBody.setEnabled(enabled);
                                            context.getSource().sendFeedback(Component.literal(
                                                    enabled ? "已开启第一人称身体" : "已关闭第一人称身体"));
                                            return 1;
                                        })))
                        .then(ClientCommands.literal("play")
                                .then(ClientCommands.argument("animation", StringArgumentType.word())
                                        .suggests((context, builder) -> SharedSuggestionProvider.suggest(ASSETS.animations().keySet(), builder))
                                        .executes(context -> {
                                            // 调试命令只影响本地玩家，不伪造联网广播或服务端动作。
                                            String id = StringArgumentType.getString(context, "animation");
                                            try {
                                                PlayerAnimations.play(context.getSource().getPlayer().getUUID(), id);
                                                context.getSource().sendFeedback(Component.literal("播放动画: " + id));
                                                return 1;
                                            } catch (IllegalArgumentException exception) {
                                                context.getSource().sendError(Component.literal(exception.getMessage()));
                                                return 0;
                                            }
                                        })))
                        .then(ClientCommands.literal("stop").executes(context -> {
                            PlayerAnimations.stopAll(context.getSource().getPlayer().getUUID());
                            context.getSource().sendFeedback(Component.literal("已停止玩家动画"));
                            return 1;
                        }))
                        .then(ClientCommands.literal("list").executes(context -> {
                            context.getSource().sendFeedback(Component.literal("已加载动画: " + String.join(", ", ASSETS.animations().keySet())));
                            return 1;
                        }))));
        LOG.info("玩家多关节动画运行时已初始化");
    }
}
