package dev.kizuna.playeranimation.client;

import dev.kizuna.playeranimation.model.BoneId;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/** 第一人称复用世界中的玩家渲染，视线与交互位置仍由原版相机决定。 */
public final class FirstPersonBody {
    // 单位为方块，仅移动本次绘制的模型，给相机近裁面留出空间。
    private static final double BODY_BACK_OFFSET = 0.25;
    private static final double CROUCH_BACK_OFFSET = 0.30;
    private static boolean enabled = true;
    private static AbstractClientPlayer renderingPlayer;

    private FirstPersonBody() { }

    public static void setEnabled(boolean value) { enabled = value; }

    public static boolean active(AbstractClientPlayer player) {
        Minecraft client = Minecraft.getInstance();
        // 横躺/旋转类姿态需要各自的相机空间适配，当前保留其原版视角。
        return enabled && client.options.getCameraType().isFirstPerson()
                && client.getCameraEntity() == player && player.isAlive() && !player.isSpectator()
                && !player.isSleeping() && !player.isFallFlying() && !player.isAutoSpinAttack()
                && !player.isVisuallySwimming() && player.getSwimAmount(client.getFrameTime()) == 0
                && PlayerAnimationClient.ASSETS.model("slim".equals(player.getModelName())) != null;
    }

    public static boolean rendering(Entity entity) { return renderingPlayer == entity; }

    public static boolean vanillaHands(AbstractClientPlayer player) {
        // 地图需要原版动态内容面板，望远镜瞄准需要原版镜片遮罩。
        return player.getMainHandItem().is(Items.FILLED_MAP)
                || player.getOffhandItem().is(Items.FILLED_MAP) || player.isScoping();
    }

    public static boolean visible(Entity entity, BoneId bone) {
        // 只限制这一趟本地身体绘制，第三人称和物品栏预览保留完整头部。
        return !rendering(entity) || bone != BoneId.HEAD
                && (!bone.arm() || !vanillaHands(renderingPlayer));
    }

    static Vec3 bodyOffset(float yawDegrees, boolean crouching) {
        // Minecraft 偏航零度朝 +Z；模型偏移取身体朝向的反方向。
        double radians = Math.toRadians(yawDegrees);
        double distance = crouching ? CROUCH_BACK_OFFSET : BODY_BACK_OFFSET;
        return new Vec3(Math.sin(radians) * distance, 0, -Math.cos(radians) * distance);
    }

    public static void render(WorldRenderContext context) {
        Minecraft client = Minecraft.getInstance();
        if (!(context.camera().getEntity() instanceof AbstractClientPlayer player)
                || context.camera().isDetached() || !active(player)) {
            return;
        }
        float delta = context.tickDelta();
        var dispatcher = client.getEntityRenderDispatcher();
        PlayerRenderer renderer = (PlayerRenderer) dispatcher.getRenderer(player);
        Vec3 position = player.getPosition(delta).subtract(context.camera().getPosition())
                .add(renderer.getRenderOffset(player, delta))
                .add(bodyOffset(Mth.rotLerp(delta, player.yBodyRotO, player.yBodyRot),
                        player.isCrouching()));
        var matrices = context.matrixStack();
        matrices.pushPose();
        renderingPlayer = player;
        try {
            // 直接调用玩家渲染器，沿用皮肤、骨架、装备和持物；不补画影子或碰撞箱。
            matrices.translate(position.x, position.y, position.z);
            renderer.render(player, player.getViewYRot(delta), delta, matrices, context.consumers(),
                    dispatcher.getPackedLightCoords(player, delta));
        } finally {
            renderingPlayer = null;
            matrices.popPose();
        }
    }
}
