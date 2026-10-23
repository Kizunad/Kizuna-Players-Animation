package dev.kizuna.playeranimation.animation;

/**
 * 已交付的玩家动作目录。原版状态由运行时观察器选择；没有原版触发语义的动作仍通过该目录提供显式接口。
 */
public enum PlayerAnimationAction {
    /** 潜行静止操作时的支撑姿态；结束操作或移动时显式停止。 */
    CROUCH_WORK("crouch_work"),
    /** 跳跃是一段 0.90 秒剪辑，调度通过竖直速度 scrub。 */
    JUMP("jump", 18),
    ATTACK_MAIN("attack_main", 10),
    ATTACK_OFF("attack_off", 10),
    MINE_MAIN("mine_main", 10),
    MINE_OFF("mine_off", 10),
    SPYGLASS("spyglass", 47),
    SPYGLASS_OFF("spyglass_off", 47),
    TOOT_HORN("toot_horn", 52),
    TOOT_HORN_OFF("toot_horn_off", 52),
    CROSSBOW_CHARGE("crossbow_charge", 25),
    CROSSBOW_CHARGE_OFF("crossbow_charge_off", 25),
    CROSSBOW_HOLD("crossbow_hold", 8),
    CROSSBOW_HOLD_OFF("crossbow_hold_off", 8),
    BRUSH("brush", 19),
    BRUSH_OFF("brush_off", 19),
    USE_ITEM("use_item", 8),
    USE_ITEM_OFF("use_item_off", 8),
    PLACE_BLOCK("place_block", 10),
    PLACE_BLOCK_OFF("place_block_off", 10),
    EQUIP_HELMET("equip_helmet", 26),
    EQUIP_HELMET_OFF("equip_helmet_off", 26),
    EQUIP_CHESTPLATE("equip_chestplate", 30),
    EQUIP_CHESTPLATE_OFF("equip_chestplate_off", 30),
    EQUIP_LEGGINGS("equip_leggings", 50),
    EQUIP_LEGGINGS_OFF("equip_leggings_off", 50),
    EQUIP_BOOTS("equip_boots", 51),
    EQUIP_BOOTS_OFF("equip_boots_off", 51);

    private final String id;
    private final int naturalDurationTicks;

    PlayerAnimationAction(String id) {
        this(id, 0);
    }

    PlayerAnimationAction(String id, int naturalDurationTicks) {
        this.id = id;
        this.naturalDurationTicks = naturalDurationTicks;
    }

    public String id() {
        return id;
    }

    /**
     * 一次性动作的资源时长，按服务端世界时钟取整到足以覆盖末帧的 tick 数。
     * 返回 0 表示动作必须由状态结束或玩法显式停止。
     */
    public int naturalDurationTicks() {
        return naturalDurationTicks;
    }

    public boolean naturallyEnds() {
        return naturalDurationTicks > 0;
    }

    /**
     * 动作资源是否已按验收规则交付并随 JAR 打包。未交付的动作保留目录项与显式接口，
     * 但原版调度不会请求它们，对应状态继续使用原版姿态。资源交付后从 PENDING 移除。
     */
    public boolean delivered() {
        return !PENDING.contains(this);
    }

    private static final java.util.Set<PlayerAnimationAction> PENDING = java.util.EnumSet.noneOf(PlayerAnimationAction.class);

    public static PlayerAnimationAction find(String id) {
        for (PlayerAnimationAction action : values()) {
            if (action.id.equals(id)) {
                return action;
            }
        }
        return null;
    }
}
