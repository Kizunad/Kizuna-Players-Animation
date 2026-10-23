package dev.kizuna.playeranimation.client;

import dev.kizuna.playeranimation.model.BoneId;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.entity.Entity;
import net.minecraft.item.Items;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/** 第一人称复用世界中的玩家渲染，视线与交互位置仍由原版相机决定。 */
public final class FirstPersonBody {
    // 单位为方块，仅移动本次绘制的模型，给相机近裁面留出空间。
    private static final double BODY_BACK_OFFSET = 0.25;
    private static final double CROUCH_BACK_OFFSET = 0.30;
    private static boolean enabled = true;
    private static AbstractClientPlayerEntity renderingPlayer;

    private FirstPersonBody() { }

    public static void setEnabled(boolean value) { enabled = value; }

    public static boolean active(AbstractClientPlayerEntity player) {
        MinecraftClient client = MinecraftClient.getInstance();
        // 横躺/旋转类姿态需要各自的相机空间适配，当前保留其原版视角。
        return enabled && client.options.getPerspective().isFirstPerson()
                && client.getCameraEntity() == player && player.isAlive() && !player.isSpectator()
                && !player.isSleeping() && !player.isFallFlying() && !player.isUsingRiptide()
                && !player.isInSwimmingPose() && player.getLeaningPitch(client.getTickDelta()) == 0
                && PlayerAnimationClient.ASSETS.model("slim".equals(player.getModel())) != null;
    }

    public static boolean rendering(Entity entity) { return renderingPlayer == entity; }

    public static boolean vanillaHands(AbstractClientPlayerEntity player) {
        // 地图需要原版动态内容面板，望远镜瞄准需要原版镜片遮罩。
        return player.getMainHandStack().isOf(Items.FILLED_MAP)
                || player.getOffHandStack().isOf(Items.FILLED_MAP) || player.isUsingSpyglass();
    }

    public static boolean visible(Entity entity, BoneId bone) {
        // 只限制这一趟本地身体绘制，第三人称和物品栏预览保留完整头部。
        return !rendering(entity) || bone != BoneId.HEAD
                && (!bone.arm() || !vanillaHands(renderingPlayer));
    }

    static Vec3d bodyOffset(float yawDegrees, boolean crouching) {
        // Minecraft 偏航零度朝 +Z；模型偏移取身体朝向的反方向。
        double radians = Math.toRadians(yawDegrees);
        double distance = crouching ? CROUCH_BACK_OFFSET : BODY_BACK_OFFSET;
        return new Vec3d(Math.sin(radians) * distance, 0, -Math.cos(radians) * distance);
    }

    public static void render(WorldRenderContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (!(context.camera().getFocusedEntity() instanceof AbstractClientPlayerEntity player)
                || context.camera().isThirdPerson() || !active(player)) {
            return;
        }
        float delta = context.tickDelta();
        var dispatcher = client.getEntityRenderDispatcher();
        PlayerEntityRenderer renderer = (PlayerEntityRenderer) dispatcher.getRenderer(player);
        Vec3d position = player.getLerpedPos(delta).subtract(context.camera().getPos())
                .add(renderer.getPositionOffset(player, delta))
                .add(bodyOffset(MathHelper.lerpAngleDegrees(delta, player.prevBodyYaw, player.bodyYaw),
                        player.isInSneakingPose()));
        var matrices = context.matrixStack();
        matrices.push();
        renderingPlayer = player;
        try {
            // 直接调用玩家渲染器，沿用皮肤、骨架、装备和持物；不补画影子或碰撞箱。
            matrices.translate(position.x, position.y, position.z);
            renderer.render(player, player.getYaw(delta), delta, matrices, context.consumers(),
                    dispatcher.getLight(player, delta));
        } finally {
            renderingPlayer = null;
            matrices.pop();
        }
    }
}
