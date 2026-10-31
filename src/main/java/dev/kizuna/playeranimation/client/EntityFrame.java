package dev.kizuna.playeranimation.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.kizuna.playeranimation.model.SkinnedMesh;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.HumanoidArm;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/**
 * 一个实体本帧的 PA 渲染数据：extract 时算好骨骼与装备并挂到渲染状态上，
 * submit 时登记原版提交的各层模型，绘制钩子按模型实例换成蒙皮顶点。
 */
public final class EntityFrame {
    public static final RenderStateDataKey<EntityFrame> KEY =
            RenderStateDataKey.create(() -> PlayerAnimationClient.ID + ":frame");

    /** 已解析好的手持物与挂点矩阵，矩阵在原版实体变换之内叠加。 */
    public record HeldItem(ItemStackRenderState item, Matrix4f transform) {
        public void submit(PoseStack stack, SubmitNodeCollector collector, int light,
                           int overlay, int outlineColor) {
            stack.pushPose();
            // 挂点先跟随手掌矩阵，再交给物品自身的 display 变换。
            stack.last().pose().mul(transform);
            stack.last().normal().mul(new Matrix3f(transform));
            item.submit(stack, collector, light, overlay, outlineColor);
            stack.popPose();
        }
    }

    /** 玩家专属的装备显隐与第一人称状态，取自 extract 当刻。 */
    public record PlayerParts(boolean firstPersonPass, boolean vanillaHands, Set<EquipmentSlot> hiddenArmor,
                              boolean hideLeftBoot, boolean hideRightBoot, boolean emptyHands,
                              HeldItem right, HeldItem left) {
        public HeldItem held(HumanoidArm arm) {
            return arm == HumanoidArm.RIGHT ? right : left;
        }
    }

    /** 生物额外的装备：双手物品与可选的补画盔甲状态。 */
    public record MobParts(SkinnedMesh overlay, List<HeldItem> items, HumanoidRenderState armorState) { }

    private record Layer(List<SkinnedMesh.Surface> surfaces, Predicate<SkinnedMesh.Surface> visible) { }

    private record Submitted(Model<?> model, RenderType type) { }

    private final PlayerMeshRenderer.Frame pose;
    private final PlayerParts player;
    private final MobParts mob;
    private final Map<Model<?>, Layer> models = new IdentityHashMap<>();
    private final Map<Submitted, Layer> submits = new HashMap<>();

    private EntityFrame(PlayerMeshRenderer.Frame pose, PlayerParts player, MobParts mob) {
        this.pose = pose;
        this.player = player;
        this.mob = mob;
    }

    public static EntityFrame player(PlayerMeshRenderer.Frame pose, PlayerParts parts) {
        return new EntityFrame(pose, parts, null);
    }

    public static EntityFrame mob(PlayerMeshRenderer.Frame pose, MobParts parts) {
        return new EntityFrame(pose, null, parts);
    }

    public static EntityFrame of(Object state) {
        // 方块、头颅等非实体状态没有 PA 帧。
        if (state instanceof EntityRenderState entity) {
            return entity.getData(KEY);
        }
        return null;
    }

    public PlayerMeshRenderer.Frame pose() { return pose; }

    public PlayerParts player() { return player; }

    public MobParts mob() { return mob; }

    public static Set<EquipmentSlot> armorSlots() {
        return EnumSet.of(EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET);
    }

    public void registerBody(Model<?> model, RenderType type) {
        // 本体与覆盖层可能共用同一模型实例（村民职业层），按模型加渲染类型区分。
        if (player != null) {
            register(model, type, new Layer(pose.model().mesh().surfaces(),
                    surface -> bodyVisible(model, surface)));
            return;
        }
        register(model, type, new Layer(pose.model().mesh().surfaces(),
                surface -> MobAnimationRenderer.visible(model, surface)));
    }

    public void registerOverlay(Model<?> model, RenderType type) {
        // 只有生物有外层几何；玩家皮肤外层已在本体网格里。
        if (mob != null) {
            register(model, type, new Layer(mob.overlay().surfaces(),
                    surface -> MobAnimationRenderer.visible(model, surface)));
        }
    }

    public void registerArmor(Model<?> model, EquipmentSlot slot) {
        // 盔甲模型按槽位各有实例；材质、纹饰、附魔多次提交同一模型，按实例登记即可。
        if (!(model instanceof HumanoidModel<?> armor)) {
            return;
        }
        ArmorMesh mesh = ArmorMesh.cached(armor, pose.model());
        models.put(model, new Layer(mesh.surfaces(), surface -> armorVisible(armor, slot, surface)));
    }

    public boolean hidesArmor(EquipmentSlot slot) {
        // 第一人称身体这一趟不画头盔；其余由穿戴动作决定整件隐藏。
        if (player == null) {
            return false;
        }
        if (player.firstPersonPass() && slot == EquipmentSlot.HEAD) {
            return true;
        }
        return player.hiddenArmor().contains(slot);
    }

    public boolean draw(Model<?> model, RenderType type, PoseStack stack, VertexConsumer buffer, int light,
                        int overlay, int color) {
        // 先按提交登记查本体与覆盖层，再按模型实例查盔甲；都没有时交回原版绘制。
        Layer layer = submits.get(new Submitted(model, type));
        if (layer == null) {
            layer = models.get(model);
        }
        if (layer == null) {
            return false;
        }
        PlayerMeshRenderer.draw(pose, layer.surfaces(), stack.last(), buffer, light, overlay, color,
                layer.visible());
        return true;
    }

    private void register(Model<?> model, RenderType type, Layer layer) {
        submits.put(new Submitted(model, type), layer);
        // 发光描边由收集器另派一次描边类型的提交，同样要换成蒙皮网格；
        // 覆盖层与本体描边类型相同时保留先登记的本体轮廓。
        RenderType outline = outlineType(type);
        if (outline != null) {
            submits.putIfAbsent(new Submitted(model, outline), layer);
        }
    }

    private static RenderType outlineType(RenderType type) {
        // 与原版 SubmitNodeCollection 选描边类型的规则一致。
        if (type.isOutline()) {
            return type;
        }
        return type.outline().orElse(null);
    }

    private boolean bodyVisible(Model<?> model, SkinnedMesh.Surface surface) {
        // 原版部件显隐保留皮肤外层设置与旁观者状态；第一人称这一趟另按头部和原版手规则隐藏。
        if (!(model instanceof PlayerModel vanilla)) {
            return true;
        }
        if (!FirstPersonBody.visible(player, surface.bone())) {
            return false;
        }
        return PlayerMeshRenderer.visible(vanilla, surface.bone(), surface.outer());
    }

    private boolean armorVisible(HumanoidModel<?> armor, EquipmentSlot slot,
                                 SkinnedMesh.Surface surface) {
        if (!ArmorMesh.visible(armor, surface)) {
            return false;
        }
        if (player == null) {
            return true;
        }
        // 第一人称保留原版手时，盔甲袖子跟着隐藏。
        if (player.vanillaHands() && surface.bone().arm()) {
            return false;
        }
        // 靴子允许左右腿在不同穿戴阶段分别恢复。
        if (slot != EquipmentSlot.FEET) {
            return true;
        }
        if (surface.bone().skinPart().equals("l_leg")) {
            return !player.hideLeftBoot();
        }
        if (surface.bone().skinPart().equals("r_leg")) {
            return !player.hideRightBoot();
        }
        return true;
    }
}
