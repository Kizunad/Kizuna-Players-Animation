package dev.kizuna.playeranimation.animation;

/**
 * 已交付的玩家动作目录。原版状态由运行时观察器选择；没有原版触发语义的动作仍通过该目录提供显式接口。
 */
public enum PlayerAnimationAction {
    /** 潜行静止操作时的支撑姿态；结束操作或移动时显式停止。 */
    CROUCH_WORK("crouch_work"),
    /** 静止潜行的低重心呼吸循环；与潜行行走、潜行操作支撑三选一，状态结束时显式停止。 */
    SNEAK("sneak"),
    /** 潜行行走循环；停步、松开潜行或离地时显式停止。 */
    SNEAK_WALK("sneak_walk"),
    /** 骑乘、睡眠、鞘翅滑翔是原版状态循环，状态结束时显式停止。 */
    RIDE("ride"),
    SLEEP("sleep"),
    ELYTRA("elytra"),
    /** 攀爬按累计竖直位移 scrub 循环资源，离开可攀爬方块或着地时停止。 */
    CLIMB("climb"),
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
    /** 进食与饮用按已使用 tick 取样，使用结束时由调度停止。 */
    EAT("eat"),
    EAT_OFF("eat_off"),
    DRINK("drink"),
    DRINK_OFF("drink_off"),
    /** 持盾格挡按已使用 tick 取样，0.25 秒（5 tick）举盾到位，松手时由调度停止。 */
    BLOCK_SHIELD("block_shield"),
    BLOCK_SHIELD_OFF("block_shield_off"),
    /** 拉弓按原版拉弓进度取样，松手发射后由调度推进撒放尾段再停止。 */
    BOW_DRAW("bow_draw"),
    BOW_DRAW_OFF("bow_draw_off"),
    /** 三叉戟蓄力 10 tick 后引到位，投掷后由调度推进跟随段再停止。 */
    TRIDENT_CHARGE("trident_charge"),
    TRIDENT_CHARGE_OFF("trident_charge_off"),
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
