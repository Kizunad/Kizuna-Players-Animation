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
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.state.ArmedEntityRenderState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

public final class PlayerPlayback {
    private static final double TICKS_PER_SECOND = 20;
    private final Map<UUID, AnimationPlayer> players = new HashMap<>();
    private final Map<Integer, AnimationPlayer> entities = new HashMap<>();
    private final Map<Integer, AutomaticAnimationPlayer> automaticEntities = new HashMap<>();
    private final Map<EquipKey, EquipVisual> equipVisuals = new HashMap<>();
    private ClientLevel world;

    private static final class EquipVisual {
        final String animation;
        final EquipmentSlot slot;
        final ItemStack item;
        final HumanoidArm firstBootSide;
        boolean equipped;
        boolean rightBootEquipped;
        boolean leftBootEquipped;
        int equipEvents;

        EquipVisual(String animation, EquipmentSlot slot, ItemStack item, HumanoidArm firstBootSide) {
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
                rightBootEquipped = firstBootSide == HumanoidArm.RIGHT;
                leftBootEquipped = firstBootSide == HumanoidArm.LEFT;
            } else if (equipEvents == 2) {
                rightBootEquipped = true;
                leftBootEquipped = true;
                equipped = true;
            }
        }
    }
    private record EquipKey(UUID player, EquipmentSlot slot) { }

    public AnimationPlayer player(UUID id) { return players.get(id); }

    AnimationPlayer playerForRender(UUID id, ArmedEntityRenderState state, double now) {
        // 矛沿客户端已有的挥手和使用状态取样；没有其他动画时也需要持矛覆盖层。
        AnimationPlayer playback = players.get(id);
        if (playback == null && SpearPoses.holding(state)) {
            playback = new AnimationPlayer();
            players.put(id, playback);
        }
        if (playback != null) {
            SpearPoses.update(playback, state, now);
        }
        return playback;
    }

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
        prepareWorld(Minecraft.getInstance());
        var animations = PlayerAnimationClient.ASSETS.animations();
        var base = animations.get(request.getBaseAnimationId());
        var action = animations.get(request.getActionAnimationId());
        var automatic = automaticEntities.computeIfAbsent(entity.getId(), ignored -> new AutomaticAnimationPlayer());
        Handedness hand = entity.getMainArm() == HumanoidArm.LEFT ? Handedness.LEFT : Handedness.RIGHT;
        automatic.update(base, action, request.getActionProgress(), hand, now(0));
    }

    public void play(UUID playerId, String animationId) {
        play(playerId, animationId, null, null);
    }

    public void scrub(UUID playerId, String animationId, float normalizedProgress) {
        Minecraft client = Minecraft.getInstance();
        prepareWorld(client);
        var entry = PlayerAnimationClient.ASSETS.animations().get(animationId);
        if (entry == null) {
            throw new IllegalArgumentException("未加载动画: " + animationId);
        }
        Player entity = null;
        if (client.level != null) {
            entity = client.level.getPlayerByUUID(playerId);
        }
        if (!(entity instanceof AbstractClientPlayer player)) {
            throw new IllegalArgumentException("客户端未加载玩家: " + playerId);
        }
        AnimationPlayer playback = players.computeIfAbsent(playerId, ignored -> new AnimationPlayer());
        beginEquip(player, entry);
        playback.scrub(entry, now(0), normalizedProgress, player.getMainArm() == HumanoidArm.LEFT);
    }

    public void play(UUID playerId, String animationId, Long startTick, String playId) {
        play(playerId, animationId, startTick, playId, null);
    }

    public void play(UUID playerId, String animationId, Long startTick, String playId,
                     AnimationClip.Loop loopOverride) {
        Minecraft client = Minecraft.getInstance();
        prepareWorld(client);
        var entry = PlayerAnimationClient.ASSETS.animations().get(animationId);
        // 公共入口只处理已加载实体与已加载动作，错误直接返回调用方。
        if (entry == null) {
            throw new IllegalArgumentException("未加载动画: " + animationId);
        }
        Player entity = null;
        if (client.level != null) {
            entity = client.level.getPlayerByUUID(playerId);
        }
        if (!(entity instanceof AbstractClientPlayer player)) {
            throw new IllegalArgumentException("客户端未加载玩家: " + playerId);
        }
        AnimationPlayer playback = players.computeIfAbsent(playerId, ignored -> new AnimationPlayer());
        double time = now(0);
        Handedness handedness = player.getMainArm() == HumanoidArm.LEFT ? Handedness.LEFT : Handedness.RIGHT;
        double elapsedSeconds;
        if (startTick == null) {
            playback.play(entry, time, new PlaybackRequest(time, handedness, null, loopOverride));
            elapsedSeconds = 0;
        } else {
            double startSeconds = startTick / TICKS_PER_SECOND;
            elapsedSeconds = (client.level.getGameTime() - startTick) / TICKS_PER_SECOND;
            PlaybackRequest request = new PlaybackRequest(startSeconds, handedness, playId, loopOverride);
            if (!playback.synchronize(entry, time, request, elapsedSeconds)) {
                return;
            }
        }
        restartEquip(player, entry, elapsedSeconds);
    }

    public void scrubEntity(int entityId, String animationId, float normalizedProgress) {
        EntityPlayback target = entityPlayback(entityId, animationId);
        target.playback().scrub(target.entry(), now(0), normalizedProgress,
                target.entity().getMainArm() == HumanoidArm.LEFT);
    }

    private record EntityPlayback(LivingEntity entity, AnimationEntry entry, AnimationPlayer playback) { }

    private EntityPlayback entityPlayback(int entityId, String animationId) {
        // scrub 和自由播放共享实体、资源校验，避免两条入口接受不同的目标。
        Minecraft client = Minecraft.getInstance();
        prepareWorld(client);
        AnimationEntry entry = PlayerAnimationClient.ASSETS.animations().get(animationId);
        if (entry == null) {
            throw new IllegalArgumentException("未加载动画: " + animationId);
        }
        LivingEntity entity = null;
        if (client.level != null) {
            entity = living(client.level.getEntity(entityId));
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
        Handedness handedness = target.entity().getMainArm() == HumanoidArm.LEFT ? Handedness.LEFT : Handedness.RIGHT;
        if (startTick == null) {
            playback.play(target.entry(), time, new PlaybackRequest(time, handedness, null, loopOverride));
            return;
        }
        double startSeconds = startTick / TICKS_PER_SECOND;
        double elapsedSeconds = (target.entity().level().getGameTime() - startTick) / TICKS_PER_SECOND;
        PlaybackRequest request = new PlaybackRequest(startSeconds, handedness, playId, loopOverride);
        playback.synchronize(target.entry(), time, request, elapsedSeconds);
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

    public void tick(Minecraft client) {
        prepareWorld(client);
        if (client.level == null) {
            clear();
            return;
        }
        double time = now(0);
        tickEntities(time, client.level);
        // 事件回调允许启动新动画，用当前实体快照推进，避免改变本次遍历结构。
        for (var entry : new ArrayList<>(players.entrySet())) {
            UUID id = entry.getKey();
            if (client.level.getPlayerByUUID(id) == null) {
                players.remove(id);
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

    private void tickEntities(double time, ClientLevel clientWorld) {
        // 自动层也在逻辑时钟清理淡出实例；卸载后的实体 ID 不得继承旧姿态。
        automaticEntities.entrySet().removeIf(entry -> clientWorld.getEntity(entry.getKey()) == null);
        for (var automatic : automaticEntities.values()) {
            automatic.playback().advance(time, (animation, event) -> { });
        }
        for (var entry : new ArrayList<>(entities.entrySet())) {
            int id = entry.getKey();
            LivingEntity entity = living(clientWorld.getEntity(id));
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
        // 服务器起点只在收到播放请求时换算一次，正在播放的姿态不再跟随世界校时。
        return LocalAnimationClock.now(Minecraft.getInstance().level, tickDelta);
    }

    public void clear() {
        // 断线与资源重载必须同时清理动画与穿戴显隐。
        players.clear();
        entities.clear();
        automaticEntities.clear();
        equipVisuals.clear();
    }

    public void remove(UUID player) {
        // 实体卸载立即清理，防止同一 tick 重入视野时继承旧实例。
        players.remove(player);
        equipVisuals.keySet().removeIf(key -> key.player().equals(player));
    }

    public void remove(int entityId) {
        // 实体移除后 ID 可能被复用，不能把旧动作带给新实体。
        entities.remove(entityId);
        automaticEntities.remove(entityId);
    }

    private void prepareWorld(Minecraft client) {
        // 换维度时 UUID 仍相同，但时间和实体集合已改变。
        if (world != client.level) {
            clear();
            world = client.level;
        }
    }

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

    public ItemStack equipItem(UUID id, HumanoidArm arm, double now) {
        EquipVisual visual = equipVisuals.entrySet().stream()
                .filter(entry -> entry.getKey().player().equals(id) && !entry.getValue().equipped)
                .map(Map.Entry::getValue)
                .findFirst().orElse(null);
        if (visual == null) {
            return ItemStack.EMPTY;
        }
        String idValue = visual.animation.endsWith("_off") ? visual.animation.substring(0, visual.animation.length() - 4)
                : visual.animation;
        var entity = world == null ? null : world.getPlayerByUUID(id);
        if (!(entity instanceof AbstractClientPlayer player)) {
            return ItemStack.EMPTY;
        }
        HumanoidArm actionArm = visual.animation.endsWith("_off") ? player.getMainArm().getOpposite() : player.getMainArm();
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

    public boolean twoHandedItem(UUID id, HumanoidArm arm, double now) {
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
        var entity = world == null ? null : world.getPlayerByUUID(id);
        return entity instanceof AbstractClientPlayer client && arm == client.getMainArm();
    }

    private void beginEquip(AbstractClientPlayer player, AnimationEntry entry) {
        // 连续 scrub 沿用已有穿戴状态，自由播放则由播放器决定是否重启。
        String animationId = entry.id();
        EquipmentSlot slot = equipSlot(animationId);
        if (slot == null) {
            return;
        }
        EquipVisual current = equipVisuals.get(new EquipKey(player.getUUID(), slot));
        if (current != null && current.animation.equals(animationId)) {
            return;
        }
        restartEquip(player, entry, 0);
    }

    private void restartEquip(AbstractClientPlayer player, AnimationEntry entry, double elapsedSeconds) {
        String animationId = entry.id();
        EquipmentSlot slot = equipSlot(animationId);
        if (slot == null) {
            return;
        }
        HumanoidArm mainArm = player.getMainArm();
        HumanoidArm firstBootSide;
        if (animationId.endsWith("_off")) {
            firstBootSide = mainArm.getOpposite();
        } else {
            firstBootSide = mainArm;
        }
        EquipVisual visual = new EquipVisual(animationId, slot, player.getItemBySlot(slot).copy(), firstBootSide);
        equipVisuals.put(new EquipKey(player.getUUID(), slot), visual);
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
