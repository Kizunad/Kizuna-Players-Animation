package dev.kizuna.playeranimation.client;

import dev.kizuna.playeranimation.animation.AnimationEntry;
import dev.kizuna.playeranimation.animation.AnimationPlayer;
import dev.kizuna.playeranimation.animation.AnimationClip;
import dev.kizuna.playeranimation.animation.AutomaticAnimationPlayer;
import dev.kizuna.playeranimation.protocol.pb.EntityAnimationProtocol.Automatic;
import dev.kizuna.playeranimation.animation.AnimationPlayer.Handedness;
import dev.kizuna.playeranimation.animation.AnimationPlayer.PlaybackRequest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Arm;

public final class PlayerPlayback {
    private static final double TICKS_PER_SECOND = 20;
    private final Map<UUID, AnimationPlayer> players = new HashMap<>();
    private final Map<Integer, AnimationPlayer> entities = new HashMap<>();
    private final Map<Integer, AutomaticAnimationPlayer> automaticEntities = new HashMap<>();
    private final Map<UUID, PlayerMeshRenderer.Frame> frames = new HashMap<>();
    private final Map<EquipKey, EquipVisual> equipVisuals = new HashMap<>();
    private ClientWorld world;

    private static final class EquipVisual {
        final String animation;
        final EquipmentSlot slot;
        final ItemStack item;
        final Arm firstBootSide;
        boolean equipped;
        boolean rightBootEquipped;
        boolean leftBootEquipped;
        int equipEvents;

        EquipVisual(String animation, EquipmentSlot slot, ItemStack item, Arm firstBootSide) {
            this.animation = animation;
            this.slot = slot;
            this.item = item;
            this.firstBootSide = firstBootSide;
        }

        void markEquipped() {
            // 两只靴子按模板与惯用手依次穿上，历史恢复与实时事件使用同一规则。
            equipEvents++;
            if (slot != EquipmentSlot.FEET) {
                equipped = true;
            } else if (equipEvents == 1) {
                rightBootEquipped = firstBootSide == Arm.RIGHT;
                leftBootEquipped = firstBootSide == Arm.LEFT;
            } else if (equipEvents == 2) {
                rightBootEquipped = true;
                leftBootEquipped = true;
                equipped = true;
            }
        }
    }
    private record EquipKey(UUID player, EquipmentSlot slot) { }

    public AnimationPlayer player(UUID id) { return players.get(id); }

    public AnimationPlayer entity(int id) { return entities.get(id); }

    public AnimationPlayer automaticEntity(int id) {
        // 自动与显式动作分开取样，资源内部优先级不能反转宿主的显式请求。
        AutomaticAnimationPlayer automatic = automaticEntities.get(id);
        if (automatic == null) {
            return null;
        }
        return automatic.playback();
    }

    public void automaticEntity(LivingEntity entity, Automatic request) {
        // 入口已经校验资源与进度，重复请求只推进动作游标。
        prepareWorld(MinecraftClient.getInstance());
        var animations = PlayerAnimationClient.ASSETS.animations();
        var base = animations.get(request.getBaseAnimationId());
        var action = animations.get(request.getActionAnimationId());
        var automatic = automaticEntities.computeIfAbsent(entity.getId(), ignored -> new AutomaticAnimationPlayer());
        Handedness hand = entity.getMainArm() == Arm.LEFT ? Handedness.LEFT : Handedness.RIGHT;
        automatic.update(base, action, request.getActionProgress(), hand, now(0));
    }

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
        PlayerEntity entity = null;
        if (client.world != null) {
            entity = client.world.getPlayerByUuid(playerId);
        }
        if (!(entity instanceof AbstractClientPlayerEntity player)) {
            throw new IllegalArgumentException("客户端未加载玩家: " + playerId);
        }
        AnimationPlayer playback = players.computeIfAbsent(playerId, ignored -> new AnimationPlayer());
        beginEquip(player, entry);
        playback.scrub(entry, now(0), normalizedProgress, player.getMainArm() == Arm.LEFT);
    }

    public void play(UUID playerId, String animationId, Long startTick, String playId) {
        play(playerId, animationId, startTick, playId, null);
    }

    public void play(UUID playerId, String animationId, Long startTick, String playId,
                     AnimationClip.Loop loopOverride) {
        MinecraftClient client = MinecraftClient.getInstance();
        prepareWorld(client);
        var entry = PlayerAnimationClient.ASSETS.animations().get(animationId);
        // 公共入口只处理已加载实体与已加载动作，错误直接返回调用方。
        if (entry == null) {
            throw new IllegalArgumentException("未加载动画: " + animationId);
        }
        PlayerEntity entity = null;
        if (client.world != null) {
            entity = client.world.getPlayerByUuid(playerId);
        }
        if (!(entity instanceof AbstractClientPlayerEntity player)) {
            throw new IllegalArgumentException("客户端未加载玩家: " + playerId);
        }
        AnimationPlayer playback = players.computeIfAbsent(playerId, ignored -> new AnimationPlayer());
        double time = now(0);
        Handedness handedness = player.getMainArm() == Arm.LEFT ? Handedness.LEFT : Handedness.RIGHT;
        double elapsedSeconds;
        if (startTick == null) {
            playback.play(entry, time, new PlaybackRequest(time, handedness, null, loopOverride));
            elapsedSeconds = 0;
        } else {
            double startSeconds = startTick / TICKS_PER_SECOND;
            PlaybackRequest request = new PlaybackRequest(startSeconds, handedness, playId, loopOverride);
            if (!playback.synchronize(entry, time, request)) {
                return;
            }
            elapsedSeconds = time - startSeconds;
        }
        restartEquip(player, entry, elapsedSeconds);
    }

    public void scrubEntity(int entityId, String animationId, float normalizedProgress) {
        EntityPlayback target = entityPlayback(entityId, animationId);
        target.playback().scrub(target.entry(), now(0), normalizedProgress,
                target.entity().getMainArm() == Arm.LEFT);
    }

    private record EntityPlayback(LivingEntity entity, AnimationEntry entry, AnimationPlayer playback) { }

    private EntityPlayback entityPlayback(int entityId, String animationId) {
        // scrub 和自由播放共享实体、资源校验，避免两条入口接受不同的目标。
        MinecraftClient client = MinecraftClient.getInstance();
        prepareWorld(client);
        AnimationEntry entry = PlayerAnimationClient.ASSETS.animations().get(animationId);
        if (entry == null) {
            throw new IllegalArgumentException("未加载动画: " + animationId);
        }
        LivingEntity entity = null;
        if (client.world != null) {
            entity = living(client.world.getEntityById(entityId));
        }
        if (entity == null) {
            throw new IllegalArgumentException("客户端未加载生物: " + entityId);
        }
        AnimationPlayer playback = entities.computeIfAbsent(entityId, ignored -> new AnimationPlayer());
        return new EntityPlayback(entity, entry, playback);
    }

    public void playEntity(int entityId, String animationId, Long startTick, String playId,
                           AnimationClip.Loop loopOverride) {
        EntityPlayback target = entityPlayback(entityId, animationId);
        AnimationPlayer playback = target.playback();
        double time = now(0);
        Handedness handedness = target.entity().getMainArm() == Arm.LEFT ? Handedness.LEFT : Handedness.RIGHT;
        if (startTick == null) {
            playback.play(target.entry(), time, new PlaybackRequest(time, handedness, null, loopOverride));
            return;
        }
        double startSeconds = startTick / TICKS_PER_SECOND;
        PlaybackRequest request = new PlaybackRequest(startSeconds, handedness, playId, loopOverride);
        playback.synchronize(target.entry(), time, request);
    }

    public void stop(UUID playerId, String animationId) {
        AnimationPlayer player = players.get(playerId);
        // 未播放的实体无需创建空控制器。
        if (player != null) {
            player.stop(animationId, now(0));
        }
    }

    public void stopEntity(int entityId, String animationId) {
        AnimationPlayer animation = entities.get(entityId);
        if (animation != null) {
            animation.stop(animationId, now(0));
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
        tickEntities(time, client.world);
        // 事件回调允许启动新动画，用当前实体快照推进，避免改变本次遍历结构。
        for (var entry : new ArrayList<>(players.entrySet())) {
            UUID id = entry.getKey();
            if (client.world.getPlayerByUuid(id) == null) {
                players.remove(id);
                frames.remove(id);
                equipVisuals.keySet().removeIf(key -> key.player().equals(id));
                continue;
            }
            AnimationPlayer playback = entry.getValue();
            playback.advance(time, (animation, event) -> {
                if (event.equals("equip")) {
                    markEquip(id, animation);
                }
                PlayerAnimations.EVENT.invoker().onEvent(id, animation, event);
            });
            equipVisuals.entrySet().removeIf(visual -> visual.getKey().player().equals(id)
                    && !playback.hasActive(visual.getValue().animation, time));
            if (playback.empty()) {
                players.remove(id);
            }
        }
    }

    private void tickEntities(double time, ClientWorld clientWorld) {
        // 自动层也在逻辑时钟清理淡出实例；卸载后的实体 ID 不得继承旧姿态。
        automaticEntities.entrySet().removeIf(entry -> clientWorld.getEntityById(entry.getKey()) == null);
        for (var automatic : automaticEntities.values()) {
            automatic.playback().advance(time, (animation, event) -> { });
        }
        for (var entry : new ArrayList<>(entities.entrySet())) {
            int id = entry.getKey();
            LivingEntity entity = living(clientWorld.getEntityById(id));
            if (entity == null) {
                entities.remove(id);
                continue;
            }
            // 玩家事件监听方持有玩家装备状态，生物事件只推进播放器内部时间。
            AnimationPlayer playback = entry.getValue();
            playback.advance(time, (animation, event) -> { });
            if (playback.empty()) {
                entities.remove(id);
            }
        }
    }

    public static double now(float tickDelta) {
        // 世界总 tick 不受 /time set 昼夜时间影响，暂停时也不会继续推进动画。
        var world = MinecraftClient.getInstance().world;
        if (world == null) {
            return 0;
        }
        return (world.getTime() + tickDelta) / TICKS_PER_SECOND;
    }

    public void clear() {
        // 断线与资源重载必须同时清理动画和用于挂载手持物的帧数据。
        players.clear();
        entities.clear();
        automaticEntities.clear();
        frames.clear();
        equipVisuals.clear();
    }

    public void remove(UUID player) {
        // 实体卸载立即清理，防止同一 tick 重入视野时继承旧实例。
        players.remove(player);
        frames.remove(player);
        equipVisuals.keySet().removeIf(key -> key.player().equals(player));
    }

    public void remove(int entityId) {
        // 实体移除后 ID 可能被复用，不能把旧动作带给新实体。
        entities.remove(entityId);
        automaticEntities.remove(entityId);
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

    private static LivingEntity living(Entity entity) {
        return entity instanceof LivingEntity value ? value : null;
    }

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

    private void beginEquip(AbstractClientPlayerEntity player, AnimationEntry entry) {
        // 连续 scrub 沿用已有穿戴状态，自由播放则由播放器决定是否重启。
        String animationId = entry.id();
        EquipmentSlot slot = equipSlot(animationId);
        if (slot == null) {
            return;
        }
        EquipVisual current = equipVisuals.get(new EquipKey(player.getUuid(), slot));
        if (current != null && current.animation.equals(animationId)) {
            return;
        }
        restartEquip(player, entry, 0);
    }

    private void restartEquip(AbstractClientPlayerEntity player, AnimationEntry entry, double elapsedSeconds) {
        String animationId = entry.id();
        EquipmentSlot slot = equipSlot(animationId);
        if (slot == null) {
            return;
        }
        Arm mainArm = player.getMainArm();
        Arm firstBootSide;
        if (animationId.endsWith("_off")) {
            firstBootSide = mainArm.getOpposite();
        } else {
            firstBootSide = mainArm;
        }
        EquipVisual visual = new EquipVisual(animationId, slot, player.getEquippedStack(slot).copy(), firstBootSide);
        equipVisuals.put(new EquipKey(player.getUuid(), slot), visual);
        // 同步播放器已跳过历史事件，只恢复内部显隐；起点的零秒事件仍留给正常 tick。
        if (elapsedSeconds <= 0) {
            return;
        }
        for (var event : entry.clip().events()) {
            if (event.name().equals("equip") && event.seconds() <= elapsedSeconds) {
                visual.markEquipped();
            }
        }
    }

    private void markEquip(UUID id, String animationId) {
        // 实时事件只推进同一玩家、同一穿戴动画的视觉状态。
        equipVisuals.entrySet().stream()
                .filter(entry -> entry.getKey().player().equals(id))
                .map(Map.Entry::getValue)
                .filter(visual -> visual.animation.equals(animationId))
                .forEach(EquipVisual::markEquipped);
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
