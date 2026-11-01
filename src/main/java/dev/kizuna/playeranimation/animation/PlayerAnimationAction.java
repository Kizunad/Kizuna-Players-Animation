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
    /** 游泳按原版肢体速度累计相位 scrub 循环资源，离开水中泳姿时停止。 */
    SWIM("swim"),
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
    EQUIP_BOOTS_OFF("equip_boots_off", 51),
    /**
     * 战斗架势（docs/animation-specs/PROGRESS-combat-stance.md）。本仓库只交付资源和显式接口：
     * 架势状态、闪避、格挡、受击与功法的触发由战斗 UI / 宿主决定，原版调度不会请求这些动作。
     * 底层循环与进出过渡由调用方显式停止。
     */
    STANCE_IDLE("stance_idle"),
    STANCE_ENTER("stance_enter"),
    STANCE_EXIT("stance_exit"),
    STANCE_WALK_FORWARD("stance_walk_forward"),
    STANCE_WALK_BACK("stance_walk_back"),
    STANCE_WALK_LEFT("stance_walk_left"),
    STANCE_WALK_RIGHT("stance_walk_right"),
    STANCE_RUN("stance_run"),
    DODGE_FORWARD("dodge_forward", 16),
    DODGE_BACK("dodge_back", 16),
    DODGE_LEFT("dodge_left", 16),
    DODGE_RIGHT("dodge_right", 16),
    /** 轻步是贴地冲刺短跑（没有腾空段），按真实时间播完 1.15 秒剪辑：起手、两步冲刺、制动、撤回架势。 */
    TECHNIQUE_DASH_FORWARD("technique_dash_forward", 23),
    TECHNIQUE_DASH_BACK("technique_dash_back", 23),
    TECHNIQUE_DASH_LEFT("technique_dash_left", 23),
    TECHNIQUE_DASH_RIGHT("technique_dash_right", 23),
    /** 格挡是保持动作，调用方结束格挡时停止；受击后坐叠在保持段之上，0.30 秒播完。 */
    BLOCK_ONE_HAND("block_one_hand"),
    BLOCK_ONE_HAND_HIT("block_one_hand_hit", 6),
    BLOCK_TWO_HAND("block_two_hand"),
    BLOCK_TWO_HAND_HIT("block_two_hand_hit", 6),
    TECHNIQUE_BREATH("technique_breath", 40),
    TECHNIQUE_CHARGE("technique_charge", 40),
    /**
     * 轻身跃步（docs/animation-specs/technique_leap.md）：0.40 s 蹬地前冲、收腿前伸后保持到落地，
     * 宿主在落地或中断时停止；落地缓冲由下层 {@code jump} 承担。
     */
    TECHNIQUE_LEAP("technique_leap"),
    /**
     * 破门斩（docs/animation-specs/door_breaker_knock.md）。宿主起手时按真实时间播放，承接时用 start_tick
     * 前推到承接入口；整段全身，优先级高于服务端每次打击另调的原版挥手。
     */
    DOOR_BREAKER_KNOCK("door_breaker_knock", 51),
    DOOR_BREAKER_KNOCK_OFF("door_breaker_knock_off", 51),
    /** 破门（docs/animation-specs/door_breaker_cleave.md）：双手右拧斜劈、嵌门晃撬，4.4 秒；1.60 秒击中。 */
    DOOR_BREAKER_CLEAVE("door_breaker_cleave", 88),
    DOOR_BREAKER_CLEAVE_OFF("door_breaker_cleave_off", 88),
    /** 撞门（docs/animation-specs/door_breaker_ram.md）：低身冲两步、肩撞、回身横撩，4.25 秒；1.65 秒击中；冲刺段原地迈步，位移由冲量负责。 */
    DOOR_BREAKER_RAM("door_breaker_ram", 85),
    DOOR_BREAKER_RAM_OFF("door_breaker_ram_off", 85),
    /** 破盾（docs/animation-specs/door_breaker_hook.md）：单手越沿一钩、撤步后坐拽回，3.5 秒；1.05 秒击中。 */
    DOOR_BREAKER_HOOK("door_breaker_hook", 70),
    DOOR_BREAKER_HOOK_OFF("door_breaker_hook_off", 70),
    /** 横扫（docs/animation-specs/door_breaker_sweep.md）：单手抡斧、绕前脚原地旋身一整圈，4.35 秒；2.05 秒末击，root 1.35–2.60 秒转 360°。 */
    DOOR_BREAKER_SWEEP("door_breaker_sweep", 87),
    DOOR_BREAKER_SWEEP_OFF("door_breaker_sweep_off", 87),
    /** 掩门（docs/animation-specs/door_breaker_close.md）：推掌撤两步、深蹲后坐、翻斧向前上顶撩、横斧落闩，4.7 秒；2.30 秒击中。 */
    DOOR_BREAKER_CLOSE("door_breaker_close", 94),
    DOOR_BREAKER_CLOSE_OFF("door_breaker_close_off", 94),
    /** 连门（docs/animation-specs/door_breaker_chain.md）：双手叠握连斩三刀、左右来回拧，前两刀垫步、第三刀弓步横斩，7.25 秒；4.25 秒末击。 */
    DOOR_BREAKER_CHAIN("door_breaker_chain", 145),
    DOOR_BREAKER_CHAIN_OFF("door_breaker_chain_off", 145),
    /** 万门一斩（docs/animation-specs/door_breaker_finale.md）：双手叠握荡斧、竖斧蹲蓄、起跳、落地竖劈、折身喘气，7.05 秒；1.20 秒前荡轻击、2.90 秒落地竖劈击中。 */
    DOOR_BREAKER_FINALE("door_breaker_finale", 141),
    DOOR_BREAKER_FINALE_OFF("door_breaker_finale_off", 141),
    // 千里腿九式与蹲马步（docs/animation-specs/PROGRESS-peddler.md）：宿主起手时按真实时间播放，承接时用 start_tick
    // 前推到承接入口；整段全身，不分主副手（左主手由运行时整段镜像）。
    /** 弹腿：后坐提膝、小腿弹出又收回，3.0 秒；1.05 秒击中。 */
    PEDDLER_SNAP_KICK("peddler_snap_kick", 60),
    /** 正蹬：提膝过胸、送胯蹬出，5.0 秒；1.95 秒击中。 */
    PEDDLER_PUSH_KICK("peddler_push_kick", 100),
    /** 扫堂：仆步贴地、绕后脚旋扫一整圈，6.0 秒；1.85、2.35 秒击中，root 1.60–2.80 秒转 360°。 */
    PEDDLER_GROUND_SWEEP("peddler_ground_sweep", 120),
    /** 鞭腿：碾脚翻胯、后腿横抽到头高，5.0 秒；2.00 秒击中。 */
    PEDDLER_ROUNDHOUSE("peddler_roundhouse", 100),
    /** 连环：单腿站、前腿低中高连踢三下，6.0 秒；1.55、2.15、2.85 秒击中。 */
    PEDDLER_TRIPLE_KICK("peddler_triple_kick", 120),
    /** 尥蹶：俯身两拳撑地、双腿向后上蹬，5.0 秒；1.45 秒击中；整段不叠视线层。 */
    PEDDLER_MULE_KICK("peddler_mule_kick", 100),
    /** 飞踹：原地小跑两步、单脚起跳、空中侧踹，6.0 秒；1.75 秒击中，位移由冲量负责。 */
    PEDDLER_FLYING_KICK("peddler_flying_kick", 120),
    /** 下劈：直腿高举、脚跟劈下，6.0 秒；2.45 秒击中。 */
    PEDDLER_AXE_KICK("peddler_axe_kick", 120),
    /** 不留行：摆臂起跳、空中转一整圈击响、落地扶膝喘气，9.0 秒；3.05、3.25 秒击中，root 2.85–3.43 秒转 360°。 */
    PEDDLER_WHIRLWIND("peddler_whirlwind", 180),
    /** 蹲马步（训练）：进桩、站桩 30 秒、收桩，32.0 秒。 */
    PEDDLER_HORSE_STANCE("peddler_horse_stance", 640),
    /**
     * 持物手覆盖层（docs/animation-specs/door_breaker_guard.md）：破门、连门、万门一斩起手时另一只手非空，
     * 与该式同起，只接管持物那条手臂（主手版 l_arm、副手版 r_arm），0.40 秒收到体侧后保持；
     * 承接时不重播，宿主在该式淡完后显式停止。
     */
    DOOR_BREAKER_GUARD("door_breaker_guard"),
    DOOR_BREAKER_GUARD_OFF("door_breaker_guard_off");

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
