package dev.kizuna.playeranimation.client;

import dev.kizuna.playeranimation.animation.AnimationPlayer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Arm;

public final class PlayerPlayback {
    private final Map<UUID, AnimationPlayer> players = new HashMap<>();
    private final Map<UUID, PlayerMeshRenderer.Frame> frames = new HashMap<>();
    private final Map<EquipKey, EquipVisual> equipVisuals = new HashMap<>();
    private ClientWorld world;

    private static final class EquipVisual {
        final String animation;
        final EquipmentSlot slot;
        final ItemStack item;
        boolean equipped;
        boolean rightBootEquipped;
        boolean leftBootEquipped;
        int equipEvents;

        EquipVisual(String animation, EquipmentSlot slot, ItemStack item) {
            this.animation = animation;
            this.slot = slot;
            this.item = item;
        }
    }
    private record EquipKey(UUID player, EquipmentSlot slot) { }

    public AnimationPlayer player(UUID id) { return players.get(id); }

    public void play(UUID playerId, String animationId) {
        play(playerId, animationId, null, null);
    }

    public void scrub(UUID playerId, String animationId, float normalizedProgress) {
        MinecraftClient client = MinecraftClient.getInstance();
        prepareWorld(client);
        var entry = PlayerAnimationClient.ASSETS.animations().get(animationId);
        if (entry == null) {
            throw new IllegalArgumentException("未加载动画: " + animationId);
        }
        var entity = client.world == null ? null : client.world.getPlayerByUuid(playerId);
        if (!(entity instanceof AbstractClientPlayerEntity player)) {
            throw new IllegalArgumentException("客户端未加载玩家: " + playerId);
        }
        AnimationPlayer playback = players.computeIfAbsent(playerId, ignored -> new AnimationPlayer());
        beginEquip(player, animationId);
        playback.scrub(entry, now(0), normalizedProgress, player.getMainArm() == Arm.LEFT);
    }

    public void play(UUID playerId, String animationId, Long startTick, String playId) {
        MinecraftClient client = MinecraftClient.getInstance();
        prepareWorld(client);
        var entry = PlayerAnimationClient.ASSETS.animations().get(animationId);
        // 公共入口只处理已加载实体与已加载动作，错误直接返回调用方。
        if (entry == null) {
            throw new IllegalArgumentException("未加载动画: " + animationId);
        }
        var entity = client.world == null ? null : client.world.getPlayerByUuid(playerId);
        if (!(entity instanceof AbstractClientPlayerEntity player)) {
            throw new IllegalArgumentException("客户端未加载玩家: " + playerId);
        }
        AnimationPlayer playback = players.computeIfAbsent(playerId, ignored -> new AnimationPlayer());
        beginEquip(player, animationId, true);
        if (startTick == null) {
            playback.play(entry, now(0), player.getMainArm() == Arm.LEFT);
        } else {
            playback.synchronize(entry, now(0), startTick / 20d, player.getMainArm() == Arm.LEFT, playId);
        }
    }

    public void stop(UUID playerId, String animationId) {
        AnimationPlayer player = players.get(playerId);
        // 未播放的实体无需创建空控制器。
        if (player != null) {
            player.stop(animationId, now(0));
        }
    }

    public void tick(MinecraftClient client) {
        prepareWorld(client);
        // 渲染帧只在同一次本体与物品绘制之间共享，不持有离开视野的实体。
        frames.clear();
        if (client.world == null) {
            clear();
            return;
        }
        double time = now(0);
        // 事件回调允许启动新动画，用当前实体快照推进，避免改变本次遍历结构。
        for (var entry : new ArrayList<>(players.entrySet())) {
            UUID id = entry.getKey();
            if (client.world.getPlayerByUuid(id) == null) {
                players.remove(id);
                frames.remove(id);
                equipVisuals.keySet().removeIf(key -> key.player().equals(id));
                continue;
            }
            entry.getValue().advance(time, (animation, event) -> {
                if (event.equals("equip")) {
                    markEquip(id, animation);
                }
                PlayerAnimations.EVENT.invoker().onEvent(id, animation, event);
            });
            equipVisuals.entrySet().removeIf(visual -> visual.getKey().player().equals(id)
                    && !entry.getValue().hasActive(visual.getValue().animation, time));
            if (entry.getValue().empty()) {
                players.remove(id);
            }
        }
    }

    public static double now(float tickDelta) {
        // 世界总 tick 不受 /time set 昼夜时间影响，暂停时也不会继续推进动画。
        var world = MinecraftClient.getInstance().world;
        return world == null ? 0 : (world.getTime() + tickDelta) / 20d;
    }

    public void clear() {
        // 断线与资源重载必须同时清理动画和用于挂载手持物的帧数据。
        players.clear();
        frames.clear();
        equipVisuals.clear();
    }

    public void remove(UUID player) {
        // 实体卸载立即清理，防止同一 tick 重入视野时继承旧实例。
        players.remove(player);
        frames.remove(player);
        equipVisuals.keySet().removeIf(key -> key.player().equals(player));
    }

    private void prepareWorld(MinecraftClient client) {
        // 换维度时 UUID 仍相同，但时间和实体集合已改变。
        if (world != client.world) {
            clear();
            world = client.world;
        }
    }

    public void beginRender(UUID id) { frames.remove(id); }
    public void frame(UUID id, PlayerMeshRenderer.Frame frame) { frames.put(id, frame); }
    public PlayerMeshRenderer.Frame frame(UUID id) { return frames.get(id); }

    public boolean shouldHideArmor(UUID id, EquipmentSlot slot) {
        EquipVisual visual = equipVisuals.get(new EquipKey(id, slot));
        if (visual == null || visual.slot != slot) {
            return false;
        }
        return slot == EquipmentSlot.FEET
                ? !visual.rightBootEquipped && !visual.leftBootEquipped
                : !visual.equipped;
    }

    /** 返回靴子指定侧是否仍应隐藏；右、左靴可在各自时间点恢复。 */
    public boolean shouldHideBoot(UUID id, boolean left) {
        EquipVisual visual = equipVisuals.get(new EquipKey(id, EquipmentSlot.FEET));
        if (visual == null || !visual.animation.startsWith("equip_boots")) {
            return false;
        }
        return left ? !visual.leftBootEquipped : !visual.rightBootEquipped;
    }

    public ItemStack equipItem(UUID id) {
        EquipVisual visual = equipVisuals.entrySet().stream()
                .filter(entry -> entry.getKey().player().equals(id))
                .map(Map.Entry::getValue)
                .filter(value -> value.item != null && !value.equipped)
                .findFirst().orElse(null);
        return visual == null || visual.equipped ? ItemStack.EMPTY : visual.item;
    }

    public ItemStack equipItem(UUID id, Arm arm, double now) {
        EquipVisual visual = equipVisuals.entrySet().stream()
                .filter(entry -> entry.getKey().player().equals(id) && !entry.getValue().equipped)
                .map(Map.Entry::getValue)
                .findFirst().orElse(null);
        if (visual == null) {
            return ItemStack.EMPTY;
        }
        String idValue = visual.animation.endsWith("_off") ? visual.animation.substring(0, visual.animation.length() - 4)
                : visual.animation;
        var entity = world == null ? null : world.getPlayerByUuid(id);
        if (!(entity instanceof AbstractClientPlayerEntity player)) {
            return ItemStack.EMPTY;
        }
        Arm actionArm = visual.animation.endsWith("_off") ? player.getMainArm().getOpposite() : player.getMainArm();
        float progress = progress(id, visual.animation, now);
        EquipTiming timing = timing(idValue);
        float duration = PlayerAnimationClient.ASSETS.animations().get(visual.animation).clip().duration();
        if (duration <= 0 || progress >= 1) {
            return ItemStack.EMPTY;
        }
        float handProgress = timing.singleHandSeconds / duration;
        if (progress < handProgress) {
            return arm == actionArm ? visual.item : ItemStack.EMPTY;
        }
        // equip 事件是唯一的层切换点；靴子在第一个事件后仍持有第二只，
        // 因而不能用固定秒数提前隐藏手中物品。
        return visual.equipped ? ItemStack.EMPTY : visual.item;
    }

    public boolean equipUsesMidpoint(UUID id, double now) {
        EquipVisual visual = equipVisuals.entrySet().stream()
                .filter(entry -> entry.getKey().player().equals(id))
                .map(Map.Entry::getValue)
                .filter(value -> !value.equipped)
                .findFirst().orElse(null);
        if (visual == null) {
            return false;
        }
        String base = visual.animation.endsWith("_off")
                ? visual.animation.substring(0, visual.animation.length() - 4) : visual.animation;
        EquipTiming timing = timing(base);
        var entry = PlayerAnimationClient.ASSETS.animations().get(visual.animation);
        return entry != null && !visual.equipped
                && progress(id, visual.animation, now) >= timing.singleHandSeconds / entry.clip().duration();
    }

    public boolean twoHandedItem(UUID id, Arm arm, double now) {
        AnimationPlayer animation = players.get(id);
        if (animation == null) {
            return false;
        }
        if (equipActive(id)) {
            return equipUsesMidpoint(id, now);
        }
        if (!animation.hasTwoHandedAction(now)) {
            return false;
        }
        var entity = world == null ? null : world.getPlayerByUuid(id);
        return entity instanceof AbstractClientPlayerEntity client && arm == client.getMainArm();
    }

    private void beginEquip(AbstractClientPlayerEntity player, String animationId) {
        beginEquip(player, animationId, false);
    }

    private void beginEquip(AbstractClientPlayerEntity player, String animationId, boolean restart) {
        EquipmentSlot slot = equipSlot(animationId);
        if (slot == null) {
            return;
        }
        EquipVisual current = equipVisuals.get(new EquipKey(player.getUuid(), slot));
        if (!restart && current != null && current.animation.equals(animationId)) {
            return;
        }
        equipVisuals.put(new EquipKey(player.getUuid(), slot), new EquipVisual(animationId, slot,
                player.getEquippedStack(slot).copy()));
    }

    private void markEquip(UUID id, String animationId) {
        equipVisuals.entrySet().stream().filter(entry -> entry.getKey().player().equals(id)
                && entry.getValue().animation.equals(animationId))
                .forEach(entry -> {
                    EquipVisual visual = entry.getValue();
                    visual.equipEvents++;
                    if (visual.slot != EquipmentSlot.FEET) {
                        visual.equipped = true;
                    } else if (visual.equipEvents == 1) {
                        boolean offHandTemplate = visual.animation.endsWith("_off");
                        if (offHandTemplate) {
                            visual.leftBootEquipped = true;
                        } else {
                            visual.rightBootEquipped = true;
                        }
                    } else if (visual.equipEvents == 2) {
                        boolean offHandTemplate = visual.animation.endsWith("_off");
                        if (offHandTemplate) {
                            visual.rightBootEquipped = true;
                        } else {
                            visual.leftBootEquipped = true;
                        }
                        visual.equipped = true;
                    }
                });
    }

    private float progress(UUID id, String animationId, double now) {
        AnimationPlayer player = players.get(id);
        return player == null ? 0 : player.normalizedProgress(animationId, now);
    }

    private boolean equipActive(UUID id) {
        return equipVisuals.entrySet().stream()
                .anyMatch(entry -> entry.getKey().player().equals(id) && !entry.getValue().equipped);
    }

    private static EquipmentSlot equipSlot(String animationId) {
        String id = animationId.endsWith("_off") ? animationId.substring(0, animationId.length() - 4) : animationId;
        return switch (id) {
            case "equip_helmet" -> EquipmentSlot.HEAD;
            case "equip_chestplate" -> EquipmentSlot.CHEST;
            case "equip_leggings" -> EquipmentSlot.LEGS;
            case "equip_boots" -> EquipmentSlot.FEET;
            default -> null;
        };
    }

    private record EquipTiming(float singleHandSeconds) { }

    private static EquipTiming timing(String animationId) {
        return switch (animationId) {
            case "equip_helmet" -> new EquipTiming(0.28f);
            case "equip_chestplate" -> new EquipTiming(0.30f);
            case "equip_leggings" -> new EquipTiming(0.34f);
            case "equip_boots" -> new EquipTiming(0.30f);
            default -> throw new IllegalArgumentException("未知穿戴动作: " + animationId);
        };
    }
}
