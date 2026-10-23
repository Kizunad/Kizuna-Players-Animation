package dev.kizuna.playeranimation.client;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.arguments.BoolArgumentType;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientEntityEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.command.CommandSource;
import net.minecraft.resource.ResourceType;
import net.minecraft.text.Text;
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
        ClientAnimationNetworking.register();
        WorldRenderEvents.AFTER_ENTITIES.register(FirstPersonBody::render);
        ResourceManagerHelper.get(ResourceType.CLIENT_RESOURCES).registerReloadListener(ASSETS);
        ClientTickEvents.END_CLIENT_TICK.register(PLAYBACK::tick);
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> PLAYBACK.clear());
        ClientEntityEvents.ENTITY_UNLOAD.register((entity, world) -> PLAYBACK.remove(entity.getUuid()));
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registry) -> dispatcher.register(
                ClientCommandManager.literal("kpa")
                        .then(ClientCommandManager.literal("firstperson")
                                .then(ClientCommandManager.argument("enabled", BoolArgumentType.bool())
                                        .executes(context -> {
                                            // 开关只影响当前客户端会话，便于对比身体与原版视角。
                                            boolean enabled = BoolArgumentType.getBool(context, "enabled");
                                            FirstPersonBody.setEnabled(enabled);
                                            context.getSource().sendFeedback(Text.literal(
                                                    enabled ? "已开启第一人称身体" : "已关闭第一人称身体"));
                                            return 1;
                                        })))
                        .then(ClientCommandManager.literal("play")
                                .then(ClientCommandManager.argument("animation", StringArgumentType.word())
                                        .suggests((context, builder) -> CommandSource.suggestMatching(ASSETS.animations().keySet(), builder))
                                        .executes(context -> {
                                            // 调试命令只影响本地玩家，不伪造联网广播或服务端动作。
                                            String id = StringArgumentType.getString(context, "animation");
                                            try {
                                                PlayerAnimations.play(context.getSource().getPlayer().getUuid(), id);
                                                context.getSource().sendFeedback(Text.literal("播放动画: " + id));
                                                return 1;
                                            } catch (IllegalArgumentException exception) {
                                                context.getSource().sendError(Text.literal(exception.getMessage()));
                                                return 0;
                                            }
                                        })))
                        .then(ClientCommandManager.literal("stop").executes(context -> {
                            PlayerAnimations.stopAll(context.getSource().getPlayer().getUuid());
                            context.getSource().sendFeedback(Text.literal("已停止玩家动画"));
                            return 1;
                        }))
                        .then(ClientCommandManager.literal("list").executes(context -> {
                            context.getSource().sendFeedback(Text.literal("已加载动画: " + String.join(", ", ASSETS.animations().keySet())));
                            return 1;
                        }))));
        LOG.info("玩家多关节动画运行时已初始化");
    }
}
