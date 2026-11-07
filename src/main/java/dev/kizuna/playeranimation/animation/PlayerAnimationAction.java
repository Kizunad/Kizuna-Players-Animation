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
    /** 矛持握与冲刺蓄力由客户端本帧使用状态取样，左右手各自覆盖持物臂。 */
    SPEAR_CHARGE("spear_charge"),
    SPEAR_CHARGE_OFF("spear_charge_off"),
    /** 戳刺整段对应原版挥手进度，第一人称仍使用原版双手。 */
    SPEAR_STAB("spear_stab", 23),
    SPEAR_STAB_OFF("spear_stab_off", 23),
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
    DOOR_BREAKER_GUARD_OFF("door_breaker_guard_off"),
    /**
     * 行矢弓弩九式与拉空弓（docs/animation-specs/PROGRESS-wayfarer.md）。宿主起手时按真实时间播放，承接、换兵时用 start_tick
     * 前推到承接入口；整段全身，{@code _OFF} 为武器在副手。弓弦、箭的物品状态写在索引 item_states（运行时接线待 26.2）。
     * <p>满月（docs/animation-specs/wayfarer_full_draw.md）：射位换步、举弓过顶、分开落到满弓、撒放后左臂打开成「一」字残心，6.0 秒；3.60 秒撒放。
     */
    WAYFARER_FULL_DRAW("wayfarer_full_draw", 120),
    WAYFARER_FULL_DRAW_OFF("wayfarer_full_draw_off", 120),
    /** 连珠（docs/animation-specs/wayfarer_pearls.md）：攥箭三连射，弦手绕小圈搭下一支，5.0 秒；2.00、2.55、3.10 秒撒放。 */
    WAYFARER_PEARLS("wayfarer_pearls", 100),
    WAYFARER_PEARLS_OFF("wayfarer_pearls_off", 100),
    /** 弓梢（docs/animation-specs/wayfarer_bow_strike.md）：双手横弓上步戳击、蹬地后撤、回射一箭，5.0 秒；1.40 秒击中、2.70 秒撒放。 */
    WAYFARER_BOW_STRIKE("wayfarer_bow_strike", 100),
    WAYFARER_BOW_STRIKE_OFF("wayfarer_bow_strike_off", 100),
    /** 连弩（docs/animation-specs/wayfarer_repeater.md）：抵颊瞄准、五次推拉上弦连射，5.0 秒；1.60–3.20 秒每 0.40 秒撒放。 */
    WAYFARER_REPEATER("wayfarer_repeater", 100),
    WAYFARER_REPEATER_OFF("wayfarer_repeater_off", 100),
    /** 横风（docs/animation-specs/wayfarer_gale.md）：弩抵腰宽站、六发由右向左扇扫（副手版镜像），6.0 秒；2.20–3.95 秒每 0.35 秒撒放。 */
    WAYFARER_GALE("wayfarer_gale", 120),
    WAYFARER_GALE_OFF("wayfarer_gale_off", 120),
    /** 跪射（docs/animation-specs/wayfarer_kneeling.md）：右脚后撤单膝跪、膝上装填、三发，7.0 秒；2.60、3.70、4.80 秒撒放。 */
    WAYFARER_KNEELING("wayfarer_kneeling", 140),
    WAYFARER_KNEELING_OFF("wayfarer_kneeling_off", 140),
    /** 回马（docs/animation-specs/wayfarer_parthian.md）：向左转身背跑、起跳空中右转回身射，5.0 秒；2.05 秒撒放；位移由冲量负责。 */
    WAYFARER_PARTHIAN("wayfarer_parthian", 100),
    WAYFARER_PARTHIAN_OFF("wayfarer_parthian_off", 100),
    /** 箭雨（docs/animation-specs/wayfarer_arrow_rain.md）：宽站仰身 50° 五连射、每发双膝一送，7.0 秒；2.80–4.40 秒每 0.40 秒撒放。 */
    WAYFARER_ARROW_RAIN("wayfarer_arrow_rain", 140),
    WAYFARER_ARROW_RAIN_OFF("wayfarer_arrow_rain_off", 140),
    /** 天地同至（docs/animation-specs/wayfarer_convergence.md）：先仰射三发、再深弓步平射一发、弓垂到地，9.0 秒；只承接满月、箭雨施放。 */
    WAYFARER_CONVERGENCE("wayfarer_convergence", 180),
    WAYFARER_CONVERGENCE_OFF("wayfarer_convergence_off", 180),
    /** 拉空弓（docs/animation-specs/wayfarer_dry_draw.md）：训练，射位上不搭箭五次慢拉慢放，32.0 秒。 */
    WAYFARER_DRY_DRAW("wayfarer_dry_draw", 640),
    WAYFARER_DRY_DRAW_OFF("wayfarer_dry_draw_off", 640),
    /**
     * 五脏诀（docs/animation-specs/PROGRESS-evoker.md）。十五段整段全身、不分主副手，左主手由运行时镜像；
     * 宿主起手时按真实时间播放，时间线 cast 是效果开始帧，只作客户端通知。
     * 强身五式：金臂、厚土、心火、归水、舒筋。
     */
    EVOKER_METAL_ARMS("evoker_metal_arms", 100),
    EVOKER_EARTH_LEGS("evoker_earth_legs", 120),
    EVOKER_FIRE_HEART("evoker_fire_heart", 80),
    EVOKER_WATER_RETURN("evoker_water_return", 100),
    EVOKER_WOOD_SINEW("evoker_wood_sinew", 100),
    /** 附伤五式：锐金、震土、灼火、寒水、惊雷，共用 0.70 秒「唤」起手，3.5 秒。 */
    EVOKER_METAL_EDGE("evoker_metal_edge", 70),
    EVOKER_EARTH_QUAKE("evoker_earth_quake", 70),
    EVOKER_FIRE_BURN("evoker_fire_burn", 70),
    EVOKER_WATER_FROST("evoker_water_frost", 70),
    EVOKER_WOOD_THUNDER("evoker_wood_thunder", 70),
    /** 炼元五式：开立后同一呼吸周期练六遍，32.0 秒一次性。 */
    EVOKER_REFINE_LUNG("evoker_refine_lung", 640),
    EVOKER_REFINE_SPLEEN("evoker_refine_spleen", 640),
    EVOKER_REFINE_HEART("evoker_refine_heart", 640),
    EVOKER_REFINE_KIDNEY("evoker_refine_kidney", 640),
    EVOKER_REFINE_LIVER("evoker_refine_liver", 640),
    /**
     * 握持覆盖层（docs/animation-specs/evoker_grip.md）：手里有东西时与该式同起，只接管拿东西那只手的拇指和两组手指，
     * 整段保持架势握拳；宿主随主剪辑一起显式停止。
     */
    EVOKER_GRIP("evoker_grip"),
    EVOKER_GRIP_OFF("evoker_grip_off");

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
