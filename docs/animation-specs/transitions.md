# 状态切换表

本表是第 4 节回归的唯一输入。`blend` 是淡入/淡出秒数；“专门过渡”表示第二阶段必须在
`.bbmodel` 中制作独立过渡段，不能用目标动作的第一帧硬切。上层一次性动作结束后回到
切换发生时的底层状态，不默认回站姿。

当前资源包含站立步态、原版使用动作、特殊姿态、战斗架势与各功法动作。
底层提供移动与支撑，上层依索引遮罩覆盖；停止上层后回到仍在推进的底层。
矛在客户端读取本帧使用和挥手状态，不增加服务端逐 tick 动画同步。

切换表中的五列行与 ArmPose 四列行由 `AnimationTransitionContractTest` 展开验证。
已交付资源、规格和真实客户端验收分别记录，切换回归不代替视觉验收。

## 允许切换

| 来源 | 目标 | 淡入/淡出 | 专门过渡 | 触发与约束 |
| --- | --- | ---: | :---: | --- |
| stand | walk / run / jump_takeoff / meditate | 0.12 / 0.15 | 否 | 输入或权威状态边沿 |
| walk | stand / run / jump_takeoff / meditate | 0.12 / 0.15 | 否 | 速度和状态边沿 |
| run | stand / walk / jump_takeoff | 0.10 / 0.14 | 否 | 速度阈值和状态边沿 |
| jump_takeoff | jump_air | 0.05 / 0.05 | 是 | 离地边沿；起跳屈膝缓冲 |
| jump_air | jump_land | 0.05 / 0.08 | 是 | 着地边沿 |
| jump_land | stand / walk / run | 0.08 / 0.15 | 是 | 着地后膝盖先吸收再回底层 |
| meditate | stand / walk / run | 0.6 / 0.6 | 是 | 吐纳会话启动/结束；移动先结束会话 |
| stand / walk / run | attack / mine / use_item / place_block | 0.04–0.1 / 0.12–0.16 | 否 | 原版进度驱动上层叠加 |
| stand / walk / run | equip_helmet / equip_chestplate / equip_leggings / equip_boots | 0.08 / 0.16 | 否 | 装备槽空→非空边沿 |
| attack / mine / use_item / place_block | stand / walk / run | 0.08 / 0.14 | 否 | 一次性动作结束，回当前底层 |
| equip_helmet / equip_chestplate / equip_leggings / equip_boots | stand / walk / run | 0.1 / 0.16 | 否 | 穿戴动作结束 |

斜杠分隔的来源或目标表示同一条规则对每个状态分别生效，测试展开为独立有向边。未列出的
有向边禁止；上层动作必须先结束再切入睡觉、飞行、游泳、攀爬或骑乘等原版状态。

## 剩余原版动作（设计稿，2026-10-26）

`sneak` / `sneak_walk` / `crouch_work` 三者由调度互斥选择（静止、移动、静止且有工具动作）。`climb`、`swim`、
`ride`、`sleep`、`elytra` 是原版特殊状态的全身动作，进入前上层动作必须已经结束（同上节规则）；`climb`、`swim`
按位移 scrub，`elytra` 按滑翔 tick scrub，其余为自由时钟循环。

| 来源 | 目标 | 淡入/淡出 | 专门过渡 | 触发与约束 | 状态 |
| --- | --- | ---: | :---: | --- | --- |
| stand / walk / run | sneak / sneak_walk | 0.2 / 0.2 | 否 | 按下潜行；静止选 sneak，移动选 sneak_walk |
| sneak | sneak_walk / crouch_work / stand / walk / jump_takeoff | 0.2 / 0.2 | 否 | 开始移动 / 开始工具动作 / 松开潜行 / 离地 |
| sneak_walk | sneak / walk / run / jump_takeoff | 0.2 / 0.2 | 否 | 停步 / 松开潜行 / 离地 |
| crouch_work | sneak / sneak_walk | 0.18 / 0.18 | 否 | 工具动作结束 / 开始移动 |
| stand / walk / jump_air | climb | 0.15 / 0.15 | 否 | `onClimbable()` 且未站稳；按竖直位移 scrub |
| climb | stand / walk / jump_air | 0.15 / 0.15 | 否 | 离开可攀爬方块或着地 |
| walk / run | swim | 0.3 / 0.3 | 否 | 进入泳姿；与原版 `swimAmount` 放倒同步 |
| swim | stand / walk | 0.3 / 0.3 | 否 | 离开泳姿 |
| stand / walk / jump_air | ride | 0.25 / 0.25 | 否 | `isPassenger()`（原版坐骑均为坐姿） |
| ride | stand | 0.25 / 0.25 | 否 | 下坐骑 |
| stand | sleep | 0.3 / 0.3 | 否 | 入睡；原版矩阵同时放倒身体 |
| sleep | stand | 0.3 / 0.3 | 否 | 醒来 |
| jump_air | elytra | 0.25 / 0.25 | 否 | `isFallFlying()` 且滑翔超过 4 tick |
| elytra | stand / walk / swim | 0.25 / 0.25 | 否 | 落地或入水 |

`head_follow` 是客户端每帧的加性视线层，不是可切换的状态，不进入本表；启用与关闭条件见
[head_follow](head_follow.md) 第 1、4 节。

## 叠加组合回归

以下组合必须用真实 `AnimationPlayer` 逐帧采样：`walk+attack`、`run+mine`、`walk+use_item`、
`run+crossbow_hold`、`jump_air+attack`、`crouch_work+use_item`、`meditate+spyglass`。
检查上下身骨骼没有断开，手臂不穿过躯干，停止上层后底层轨道继续推进；一次性动作结束走
淡出而不是瞬间复位。

设计中新增的组合（阶段 2 加入 Java 回归）：`walk+eat`、`sneak+drink`、`sneak_walk+block_shield`、
`ride+bow_draw`、`run+trident_charge`、`ride+attack`、`climb+place_block`（攀爬中只取单臂，待审阅）。

## 原版 ArmPose 使用动作

调度按原版 `AvatarRenderer.getArmPose` 的语义选 ID：`ItemUseAnimation.SPYGLASS → spyglass`、
`TOOT_HORN → toot_horn`、`EAT → eat`、`DRINK → drink`、`BLOCK → block_shield`、`BOW → bow_draw`、`TRIDENT → trident_charge`、使用中的弩 `→ crossbow_charge`、持已上弦且未使用的弩 `→ crossbow_hold`、
`BRUSH → brush`、`NONE` / `BUNDLE → use_item`；`SPEAR` 与 `STAB` 由客户端同帧分别采样 `spear_charge(_off)`、`spear_stab(_off)`；`NONE` 保留原版不摆臂行为，详见[资产契约](../asset-contract.md)。
物品在副手时选对应 `*_off`。驱动方式（已使用时长、装填进度、循环段）见各设计稿第 1 节。

| 来源 → 目标 | 淡入 / 淡出 | 触发 | 约束 |
| --- | ---: | --- | --- |
| stand / walk / run / crouch_work / jump_air → spyglass / toot_horn / brush / use_item | 0.08（brush 0.10）/ 0.15 | 开始使用物品 | 步态和 `crouch_work` 继续提供下身 |
| stand / walk / run / crouch_work / jump_air → crossbow_charge | 0.08 / 0.15 | 开始给弩上弦 | 按装填进度 scrub；满弦后保持末帧直到松手 |
| crossbow_charge → crossbow_hold | 0.08 / 0.08 | 松手且弩已上弦 | 由 `crossbow_hold` 的举弩过程衔接 |
| crossbow_charge → stand / walk / run / crouch_work / jump_air | — / 0.15 | 未满弦松手 | 原版不上弦 |
| stand / walk / run / crouch_work / jump_air → crossbow_hold | 0.08 / 0.15 | 手持已上弦的弩 | 视线俯仰加到头和双上臂 |
| crossbow_hold → stand / walk / run / crouch_work / jump_air | — / 0.15 | 射出或换下弩 | 射出不另做后坐 |

连续挖掘与连续放置在同一 ID 上重复 scrub：原版每 4 tick 重开挥动，剪辑 0–0.20 s 是可回绕
的循环，重开时游标向前接回 0，不做淡入（见 `mine_main` / `place_block` 设计稿第 1 节）。

剩余原版动作新增的 ArmPose 切换（2026-10-26 已实现；底层集合含潜行与骑乘）：

| 来源 | 目标 | 淡入 / 淡出 | 触发 | 约束 | 状态 |
| --- | --- | ---: | --- | --- | --- |

| 来源 → 目标 | 淡入 / 淡出 | 触发 | 约束 |
| --- | ---: | --- | --- |
| stand / walk / run / sneak / sneak_walk / crouch_work / ride / jump_air → eat / drink | 0.10 / 0.20 | 开始进食 / 饮用 | 按已使用 tick 取样；进食在 0.40–0.80 回绕，对齐进食音效 |


| 来源 → 目标 | 淡入 / 淡出 | 触发 | 约束 |
| --- | ---: | --- | --- |
| stand / walk / run / sneak / sneak_walk / crouch_work / ride / jump_air → bow_draw | 0.08 / 0.15 | 开始拉弓 | 按 `BowItem.getPowerForTime` scrub；满弓在 1.0–1.6 回绕 |


| 来源 → 目标 | 淡入 / 淡出 | 触发 | 约束 |
| --- | ---: | --- | --- |
| bow_draw → stand / walk / run / sneak / sneak_walk / crouch_work / ride / jump_air | — / 0.15 | 松手 | pull ≥ 0.1 时先播 1.6–1.9 撒放尾段再淡出 |


| 来源 → 目标 | 淡入 / 淡出 | 触发 | 约束 |
| --- | ---: | --- | --- |
| stand / walk / run / sneak / sneak_walk / crouch_work / ride / jump_air → block_shield | 0.06 / 0.15 | 开始持盾 | 0.25 s（5 tick）举盾到位，与原版格挡生效同步 |


| 来源 → 目标 | 淡入 / 淡出 | 触发 | 约束 |
| --- | ---: | --- | --- |
| stand / walk / run / sneak / sneak_walk / crouch_work / ride / jump_air → trident_charge | 0.08 / 0.15 | 开始蓄力 | 0.50 s（10 tick）后引到位；松手投掷时先播 1.70–2.15 跟随段（激流不播） |

## 战斗架势（2026-10-27 已交付资源）

设计稿见 [战斗架势总览](PROGRESS-combat-stance.md)。22 段资源已录入 `.bbmodel` 并导出；本仓库只交付资源、ID 和播放层次，**触发与“架势中”标志的同步由战斗 UI / 宿主负责**，
原版调度不会请求这些动作。下面五列 / 四列的行是已实现行（`AnimationTransitionContractTest` 解析并逐边模拟）；六列行是给调用方的调度约定。

### 底层：进入、退出与架势内切换

架势底层优先级 2（高于 `walk` / `run` 的 1）；进出过渡 `stance_enter` / `stance_exit` 优先级 3。方向按移动速度相对身体朝向的 4 个 90° 扇区选择，斜向取最近扇区，边界 10° 迟滞。

| 来源 | 目标 | 淡入/淡出 | 专门过渡 | 触发与约束 |
| --- | --- | ---: | :---: | --- |
| stand | stance_enter | 0.06 / 0.10 | 是 | 静止时进入架势；0.45 s 换脚（左脚上步、右脚后撤提踵） |
| stance_enter | stance_idle | 0.10 / 0.10 | 是 | 过渡播完（末帧即 stance_idle 首帧） |
| stance_enter | stance_walk_forward / stance_walk_back / stance_walk_left / stance_walk_right | 0.15 / 0.15 | 否 | 过渡中开始移动 |
| stance_idle / stance_enter | stance_exit | 0.10 / 0.10 | 是 | 静止时退出架势；0.45 s 换脚（右脚上步、左脚收回） |
| stance_exit | stand / walk | 0.10 / 0.15 | 是 | 过渡播完回站姿；过渡中开始移动淡入 walk |
| walk | stance_walk_forward / stance_walk_back / stance_walk_left / stance_walk_right | 0.15 / 0.15 | 否 | 移动中进入架势（步态本身在换脚，不播 stance_enter） |
| stance_walk_forward / stance_walk_back / stance_walk_left / stance_walk_right | walk | 0.25 / 0.25 | 否 | 移动中退出架势 |
| run | stance_run | 0.12 / 0.15 | 否 | 疾跑中进入架势 |
| stance_run | run | 0.12 / 0.15 | 否 | 疾跑中退出架势 |
| stance_idle | stance_walk_forward / stance_walk_back / stance_walk_left / stance_walk_right | 0.15 / 0.15 | 否 | 开始移动 |
| stance_walk_forward / stance_walk_back / stance_walk_left / stance_walk_right | stance_idle | 0.15 / 0.15 | 否 | 停步 |
| stance_walk_forward / stance_walk_back / stance_walk_left / stance_walk_right | stance_walk_forward / stance_walk_back / stance_walk_left / stance_walk_right | 0.12 / 0.12 | 否 | 方向扇区变化 |
| stance_idle / stance_walk_forward | stance_run | 0.12 / 0.12 | 否 | 开始疾跑（原版只允许向前疾跑） |
| stance_run | stance_walk_forward / stance_idle | 0.12 / 0.15 | 否 | 疾跑结束 / 停下 |
| stance_idle / stance_walk_forward / stance_walk_back / stance_walk_left / stance_walk_right / stance_run | jump_takeoff | 0.05 / 0.05 | 是 | 离地沿，`jump` 接管 |
| jump_land | stance_idle / stance_walk_forward / stance_walk_back / stance_walk_left / stance_walk_right / stance_run | 0.08 / 0.15 | 是 | 架势中落地，落地缓冲段播完淡回架势底层 |
| stance_idle / stance_walk_forward / stance_walk_back / stance_walk_left / stance_walk_right / stance_run | sneak / sneak_walk | 0.2 / 0.2 | 否 | 架势中按潜行：潜行底层优先，架势状态保留，松开潜行回架势底层 |

| 来源 | 目标 | 淡入/淡出 | 专门过渡 | 触发与约束 | 状态 |
| --- | --- | ---: | :---: | --- | --- |
| stance_idle / stance_walk_forward / stance_walk_back / stance_walk_left / stance_walk_right / stance_run | swim / climb / ride / sleep / elytra | 各目标状态的淡入 | 否 | 进入原版特殊状态时架势底层让位；离开后若仍在架势，回架势底层 | 调度约定 |
| 任何架势动作 | — | 立即 | 否 | 死亡、断线时直接停止，不播 stance_exit | 调度约定 |

### 架势上的一次性 / 保持动作

“静止全身”指底层是 `stance_idle` 时整段生效（这些剪辑的腿锁在同一组架势踝位）；底层是移动架势、进出过渡、闪避、轻步或跃步时只取上身——`AnimationPlayer` 已按此实现。

| 来源 → 目标 | 淡入 / 淡出 | 触发 | 约束 |
| --- | ---: | --- | --- |
| stance_idle / stance_walk_forward / stance_walk_back / stance_walk_left / stance_walk_right / stance_run → dodge_forward / dodge_back / dodge_left / dodge_right | 0.05 / 0.15 | 调用方发起闪避（方向扇区） | 全身；位移由调用方负责（剪辑先迈预备步，0.29 s 前后起跳）；0.80 s 播完淡回当前架势底层；闪避中再闪避交叉淡入 0.05 s |
| dodge_forward / dodge_back / dodge_left / dodge_right → stance_idle / stance_walk_forward / stance_walk_back / stance_walk_left / stance_walk_right / stance_run | 0.15 / 0.15 | 闪避播完 | 回当前架势底层 |
| stance_idle / stance_walk_forward / stance_walk_back / stance_walk_left / stance_walk_right / stance_run / jump_air → technique_dash_forward / technique_dash_back / technique_dash_left / technique_dash_right | 0.05 / 0.15 | 轻步起手开始 | 全身；贴地冲刺短跑，没有腾空段，按真实时间播放 1.15 s：起手 0.28 s（第 1 步短步 + 蹬地脚蹬伸，0.28 = 冲量）→ 第 2 步驱动步 → 0.60–0.80 s 制动步 → 0.84–1.01 s 蹬地脚撤回架势；不在功法收势时刻停止；空中施放（jump_air）从 0.28 开始，跳过起手 |
| technique_dash_forward / technique_dash_back / technique_dash_left / technique_dash_right → stance_idle / stance_walk_forward / stance_walk_back / stance_walk_left / stance_walk_right / stance_run | 0.15 / 0.15 | 制动与撤步播完（1.15 s） | 回当前架势底层 |
| stance_idle / stance_walk_forward / stance_walk_back / stance_walk_left / stance_walk_right / stance_run → block_one_hand / block_two_hand | 0.06 / 0.15 | 调用方进入格挡 | 静止全身、移动只取上身；0.35 s 举起，`loop_region [0.35, 1.95]`；空手用 block_two_hand |
| block_one_hand → block_one_hand_hit | 0.03 / 0.08 | `block_hit` 事件 | 叠在保持段之上（优先级 16），0.30 s 播完淡回保持段；连续受击交叉淡入 0.04 s 重播 |
| block_two_hand → block_two_hand_hit | 0.03 / 0.08 | `block_hit` 事件 | 同上 |
| stance_idle / stance_walk_forward / stance_walk_back / stance_walk_left / stance_walk_right / stance_run → technique_breath | 0.10 / 0.20 | 调息起手开始 | 静止全身、移动只取上身；起手被打断淡出 0.15 s |
| stance_idle / stance_walk_forward / stance_walk_back / stance_walk_left / stance_walk_right / stance_run → technique_charge | 0.08 / 0.15 | 蓄力起手开始 | 同上 |
| dodge_forward / dodge_back / dodge_left / dodge_right / technique_dash_forward / technique_dash_back / technique_dash_left / technique_dash_right → technique_breath / technique_charge | 0.10 / 0.15 | 功法起手开始 | 调息/蓄力只取上身叠加 |
| stance_idle / stance_walk_forward / stance_walk_back / stance_walk_left / stance_walk_right / stance_run / stance_enter / stance_exit → attack / mine / use_item / place_block | 0.04 / 0.14 | 原版输入 | 保留原版上层；在任何架势底层上都只取上身（包括 `stance_idle`）：原版剪辑的腿是并步零位，整段生效会把错步的脚拖回并步 |

### 破门斩

施放期间移速 ×0.4、不锁移动（2026-10-29 用户决定）；`mask: full`、优先级 24，任何底层上都整段生效，主动缓行时底层的走步不显示，服务端每次打击另调的原版挥手（攻击 20）不接管任何骨。
叩门、撞门起手不能被打断，没有打断边；式与式之间的承接（前一式淡出 0.30 s、后一式从承接入口起播）要从剪辑中途起播，现有切换回归只按剪辑起点模拟，等承接接线时再加。
2026-11-02 起手压缩（I7a）后的时长、承接入口和起手打断段按下表；做法见 [验收记录](../animation-acceptance.md#破门斩起手压缩i7a2026-11-02)。

| 来源 → 目标 | 淡入 / 淡出 | 触发 | 约束 |
| --- | ---: | --- | --- |
| stance_idle → door_breaker_knock / door_breaker_knock_off | 0.10 / 0.25 | 宿主在叩门起手时播放（副手出手播 `_off`） | 起止姿态等于 `stance_idle` 首帧；按真实时间播 2.55 s；撞门后承接从 0.10 s 起播 |
| door_breaker_knock / door_breaker_knock_off → stance_idle | 0.25 / 0.25 | 2.55 s 播完 | 淡回架势底层 |
| stance_idle → door_breaker_cleave / door_breaker_cleave_off | 0.10 / 0.25 | 宿主在破门起手时播放（副手出手播 `_off`） | 起止姿态等于 `stance_idle` 首帧；按真实时间播 4.4 s；起手被打断淡出 0.20 s；叩门、撞门后承接从 0.20 s 起播 |
| door_breaker_cleave / door_breaker_cleave_off → stance_idle | 0.25 / 0.25 | 4.4 s 播完 | 淡回架势底层 |
| stance_idle → door_breaker_ram / door_breaker_ram_off | 0.10 / 0.25 | 宿主在撞门起手时播放（副手出手播 `_off`） | 起止姿态等于 `stance_idle` 首帧；按真实时间播 4.25 s；冲刺段原地迈步、实体由冲量推动；掩门后承接从 0.20 s 起播 |
| door_breaker_ram / door_breaker_ram_off → stance_idle | 0.25 / 0.25 | 4.25 s 播完 | 淡回架势底层 |
| stance_idle → door_breaker_hook / door_breaker_hook_off | 0.10 / 0.25 | 宿主在破盾起手时播放（副手出手播 `_off`） | 起止姿态等于 `stance_idle` 首帧；按真实时间播 3.5 s；施放期间 0.4 倍移速走动时仍整段接管；叩门后承接从 0.20 s 起播 |
| door_breaker_hook / door_breaker_hook_off → stance_idle | 0.25 / 0.25 | 3.5 s 播完 | 淡回架势底层 |
| stance_idle → door_breaker_sweep / door_breaker_sweep_off | 0.10 / 0.25 | 宿主在横扫起手时播放（副手出手播 `_off`） | 起止姿态等于 `stance_idle` 首帧；按真实时间播 4.35 s；`root` 1.35–2.60 s 绕前脚原地转一整圈；起手被伤害打断淡出 0.20 s（1.35–1.70 s 打断时已转的 `root` 角会倒转回去）；叩门后承接从 0.20 s 起播 |
| door_breaker_sweep / door_breaker_sweep_off → stance_idle | 0.25 / 0.25 | 4.35 s 播完 | 淡回架势底层；末帧 `root` 为 0，淡出不倒转 |
| stance_idle → door_breaker_close / door_breaker_close_off | 0.10 / 0.25 | 宿主在掩门起手时播放（副手出手播 `_off`） | 起止姿态等于 `stance_idle` 首帧；按真实时间播 4.7 s；两次撤步原地迈步、实体由冲量推动（方向由目录给） |
| door_breaker_close / door_breaker_close_off → stance_idle | 0.25 / 0.25 | 4.7 s 播完 | 淡回架势底层 |
| stance_idle → door_breaker_chain / door_breaker_chain_off | 0.10 / 0.25 | 宿主在连门起手时播放（副手出手播 `_off`） | 起止姿态等于 `stance_idle` 首帧；按真实时间播 7.25 s；两次垫步、弓步原地做，实体由三次冲量推动（方向由目录给）；起手 0–0.80 s 被伤害打断淡出 0.20 s；万门一斩从 5.75 s 起承接，承接边等承接接线时再加 |
| door_breaker_chain / door_breaker_chain_off → stance_idle | 0.25 / 0.25 | 7.25 s 播完 | 淡回架势底层 |
| stance_idle → door_breaker_finale / door_breaker_finale_off | 0.10 / 0.25 | 宿主在万门一斩起手时播放（副手出手播 `_off`） | 起止姿态等于 `stance_idle` 首帧；按真实时间播 7.05 s，游戏里总作后式从 0.60 s 起播（承接接线与 `start_tick` 归整合会话）；起跳腾空由两次冲量推动；起手 0–2.10 s 被伤害打断淡出 0.20 s；破门、连门 → 万门一斩的承接边等承接接线时再加 |
| door_breaker_finale / door_breaker_finale_off → stance_idle | 0.25 / 0.25 | 7.05 s 播完 | 淡回架势底层 |
| door_breaker_cleave / door_breaker_chain / door_breaker_finale → door_breaker_guard | 0.10 / 0.25 | 双手式起手时另一只手非空，宿主同 tick 叠播 | 只接管左臂（左主手玩家镜像到右臂），其余骨照该式；承接时保持不重播 |
| door_breaker_cleave_off / door_breaker_chain_off / door_breaker_finale_off → door_breaker_guard_off | 0.10 / 0.25 | 同上（副手出手） | 只接管右臂 |
| door_breaker_guard / door_breaker_guard_off → stance_idle | 0.25 / 0.25 | 宿主在该式淡完后显式停止（[door_breaker_guard](door_breaker_guard.md) 第 9 节第 4 条） | 淡回架势持物位 |

功法上层（优先级 17）低于攻击（20）：施放调息/蓄力时出手，攻击接管手臂与躯干上段，功法剪辑继续计时，攻击结束后淡回功法当前时刻的姿态。

### 叠加组合回归

`AnimationSetContractTest` 用真实资源验证：`stance_idle` / `stance_walk_left` / `stance_run` + `attack_main`（腿保持架势底层）、`stance_idle` + `block_one_hand`（整段生效）、
`stance_walk_forward` + `technique_charge`（只取上身）、`stance_idle` + `door_breaker_cleave` + `door_breaker_guard`（覆盖层只接管左臂，左主手玩家镜像到右臂）。其余组合（`block_two_hand+block_two_hand_hit`、`dodge_left+technique_charge`、`technique_dash_forward+attack_main`、
`stance_enter+attack_main`、格挡中闪避）随调用方接线时补充。

## 轻身跃步（2026-10-30 已交付资源）

规格见 [technique_leap](technique_leap.md)。基础功法「轻步」状态「轻身」中跳跃键改为一次定点跃步；本仓库只交付资源、ID 和播放层次，**起跳、落地与中断时的 `START` / `STOP` 由宿主负责**，原版调度不会请求这段。
`mask: full`、优先级 17：盖住玩家下层的 `jump`（16），落地 `STOP` 后露出 `jump` 落地段 6 tick 作缓冲；怪物下层没有 `jump`，落地直接淡回底层。
`technique_leap` 在 `AnimationPlayer` 的移动底层集合里：攻击、穿戴、调息、蓄力等上身动作叠在跃步上只取上身，腿仍是跃步（玩家、怪物相同）。

| 来源 → 目标 | 淡入 / 淡出 | 触发 | 约束 |
| --- | ---: | --- | --- |
| stand / walk / run / jump_land / stance_idle / stance_walk_forward / stance_walk_back / stance_walk_left / stance_walk_right / stance_run → technique_leap | 0.05 / 0.10 | 跃步起跳（服务端 `START`） | 全身；0.40 s 蹬地前冲、收腿前伸后保持到落地；连跳时从 `jump` 落地段淡入首帧负载姿势 |
| technique_leap → jump_land / stand / stance_idle / swim / ride | 0.05 / 0.10 | 落地或中断（服务端 `STOP`） | 冻结后淡出 0.10 s；玩家落地露出 `jump` 落地段 6 tick；落水、上坐骑转 `swim` / `ride`；丢失 `STOP` 时一直保持末帧，不加兜底时长 |

## 千里腿（2026-10-31 制作中）

规格见 [PROGRESS-peddler](PROGRESS-peddler.md) 与各段页面：九式 + 蹲马步共 10 段，资源都已交付，`stance_idle` 往返见下面四列表。
`mask: full`、优先级 24，同破门斩：任何底层上都整段生效；`peddler_*` 不在 `AnimationPlayer` 的任何上层集合里，按索引就整段全身生效，不用改播放器。起手被伤害打断淡出 0.20 s 回架势（蹲马步另含宿主提前结束训练）。
`stance_idle` ↔ 各式两行写在四列表（`AnimationTransitionContractTest` 解析，每式 2 条有向边，10 段交付后 494 → 514）；六列设计行不解析。承接边要从剪辑中途起播，现有回归表达不了，等承接接线时再加（同破门斩）。承接关系、起手长度、打击时刻已和服务端设计稿 16 核对（出入见 PROGRESS）。

| 来源 → 目标 | 淡入 / 淡出 | 触发 | 约束 |
| --- | ---: | --- | --- |
| stance_idle → peddler_snap_kick | 0.10 / 0.25 | 宿主在弹腿起手时播放 | 起止姿态等于 `stance_idle` 首帧；按真实时间播 3.0 s；不作后式 |
| peddler_snap_kick → stance_idle | 0.25 / 0.25 | 3.0 s 播完 | 淡回架势底层 |
| stance_idle → peddler_push_kick | 0.10 / 0.25 | 宿主在正蹬起手时播放 | 起止姿态等于 `stance_idle` 首帧；按真实时间播 5.0 s；不作后式 |
| peddler_push_kick → stance_idle | 0.25 / 0.25 | 5.0 s 播完 | 淡回架势底层 |
| stance_idle → peddler_roundhouse | 0.10 / 0.25 | 宿主在鞭腿起手时播放 | 起止姿态等于 `stance_idle` 首帧；按真实时间播 5.0 s；承接入口 0.40 s |
| peddler_roundhouse → stance_idle | 0.25 / 0.25 | 5.0 s 播完 | 淡回架势底层 |
| stance_idle → peddler_ground_sweep | 0.10 / 0.25 | 宿主在扫堂起手时播放 | 起止姿态等于 `stance_idle` 首帧；按真实时间播 6.0 s；`root` 1.60–2.80 s 绕后脚向右转一整圈，转的期间打断会倒转；承接入口 0.40 s |
| peddler_ground_sweep → stance_idle | 0.25 / 0.25 | 6.0 s 播完 | 淡回架势底层；末帧 `root` 为 0 |
| stance_idle → peddler_triple_kick | 0.10 / 0.25 | 宿主在连环起手时播放 | 起止姿态等于 `stance_idle` 首帧；按真实时间播 6.0 s；前脚 1.00–3.40 s 不落地；承接入口 0.40 s |
| peddler_triple_kick → stance_idle | 0.25 / 0.25 | 6.0 s 播完 | 淡回架势底层 |
| stance_idle → peddler_mule_kick | 0.10 / 0.25 | 宿主在尥蹶起手时播放 | 起止姿态等于 `stance_idle` 首帧；按真实时间播 5.0 s；不作后式；整段关掉视线层（渲染器按 id） |
| peddler_mule_kick → stance_idle | 0.25 / 0.25 | 5.0 s 播完 | 淡回架势底层 |
| stance_idle → peddler_flying_kick | 0.10 / 0.25 | 宿主在飞踹起手时播放 | 起止姿态等于 `stance_idle` 首帧；按真实时间播 6.0 s；助跑原地迈步、实体由冲量推动；承接入口 0.40 s |
| peddler_flying_kick → stance_idle | 0.25 / 0.25 | 6.0 s 播完 | 淡回架势底层 |
| stance_idle → peddler_axe_kick | 0.10 / 0.25 | 宿主在下劈起手时播放 | 起止姿态等于 `stance_idle` 首帧；按真实时间播 6.0 s；承接入口 0.40 s |
| peddler_axe_kick → stance_idle | 0.25 / 0.25 | 6.0 s 播完 | 淡回架势底层 |
| stance_idle → peddler_whirlwind | 0.10 / 0.25 | 宿主在不留行起手时播放 | 起止姿态等于 `stance_idle` 首帧；按真实时间播 9.0 s；`root` 2.85–3.43 s 腾空向左转一整圈、3.50 s 换回 0°，转的期间打断会倒转；承接入口 1.20 s |
| peddler_whirlwind → stance_idle | 0.25 / 0.25 | 9.0 s 播完 | 淡回架势底层；末帧 `root` 为 0 |
| stance_idle → peddler_horse_stance | 0.10 / 0.25 | 宿主在蹲马步开始时播放 | 起止姿态等于 `stance_idle` 首帧；按真实时间播 32.0 s 一次性，不用 `loop_region`；不作前式、后式 |
| peddler_horse_stance → stance_idle | 0.25 / 0.25 | 32.0 s 播完 | 淡回架势底层；末帧 `root` 为 0 |

承接与打断的设计行（还没接线）：

| 来源 | 目标 | 淡入 / 淡出 | 触发 | 约束 | 状态 |
| --- | --- | ---: | --- | --- | --- |
| peddler_snap_kick | peddler_roundhouse / peddler_triple_kick | 0.30 / 0.25 | 承接（宿主在后一式起手时切换） | 前一式淡出 0.30 s，后一式从 0.40 s 起播 | 待承接接线 |
| peddler_mule_kick | peddler_ground_sweep | 0.30 / 0.25 | 承接 | 从 0.40 s 起播 | 待承接接线 |
| peddler_push_kick | peddler_flying_kick | 0.30 / 0.25 | 承接 | 从 0.40 s 起播 | 待承接接线 |
| peddler_ground_sweep | peddler_axe_kick | 0.30 / 0.25 | 承接 | 从 0.40 s 起播 | 待承接接线 |
| peddler_roundhouse / peddler_flying_kick | peddler_whirlwind | 0.30 / 0.25 | 承接 | 从 1.20 s 起播，跳过「聚」 | 待承接接线 |
| peddler_snap_kick / peddler_push_kick / peddler_ground_sweep / peddler_roundhouse / peddler_triple_kick / peddler_mule_kick / peddler_flying_kick / peddler_axe_kick / peddler_whirlwind / peddler_horse_stance | stance_idle | 0.20 | 起手被伤害打断（蹲马步含宿主提前结束） | 淡出中脚会滑回架势踝位；扫堂、不留行转的期间打断，已转的 `root` 角倒转回去 | 待打断接线 |

## 行矢（2026-10-31 起，2026-11-01 资源全部交付）

规格见 [PROGRESS-wayfarer](PROGRESS-wayfarer.md) 与各段页面：弓弩九式 + 拉空弓，主副手共 20 段，资源都已交付，见下面四列表。
`mask: full`、优先级 24，同破门斩：任何底层上都整段生效；施放期间移速 ×0.4（拉空弓为 0）、冲量照常，主动缓行时底层的走步不显示。武器在副手时宿主播 `_off`。服务端在施放期间让原版弓弩的拉弓、装填无效，弓弩外观改读动画的物品状态轨道（PROGRESS 待决定第 1 条）。
起手被伤害打断淡出 0.20 s 回架势：满月、连珠、横风、跪射、箭雨、天地同至、拉空弓能打断；弓梢、连弩、回马起手不能打断，没有打断边。
承接与换兵的时窗是前一式收势的后半段或收势结束后 0.5 s 内，前一式淡出 0.30 s，后一式从入口起播；关系、起手长度、撒放时刻已和服务端设计稿 14 §4、§4.2、§4.3 核对（出入见 PROGRESS）。
每交付一式，把 `stance_idle` ↔ 该式主副手版的两行写进四列表（`AnimationTransitionContractTest` 解析，每段 2 条有向边，20 段交付完 494 → 534，2026-11-01 已到 534；和千里腿合并后在 514 上加）；六列设计行不解析。承接、换兵边要从剪辑中途起播，现有回归表达不了，等承接接线时再加（同破门斩）。

| 来源 → 目标 | 淡入 / 淡出 | 触发 | 约束 |
| --- | ---: | --- | --- |
| stance_idle → wayfarer_full_draw / wayfarer_full_draw_off | 0.10 / 0.25 | 宿主在满月起手时播放 | 起止姿态等于 `stance_idle` 首帧；按真实时间播 6.0 s；起手 0–3.60 s 可打断 |
| wayfarer_full_draw / wayfarer_full_draw_off → stance_idle | 0.25 / 0.25 | 6.0 s 播完 | 淡回架势底层；双脚 4.75 s 已回架势踝位 |
| stance_idle → wayfarer_dry_draw / wayfarer_dry_draw_off | 0.10 / 0.25 | 宿主在拉空弓开始时播放（只给玩家） | 起止姿态等于 `stance_idle` 首帧；按真实时间播 32.0 s 一次性，不用 `loop_region`；起手 0–31.00 s 可打断；不作前式、后式，不换兵 |
| wayfarer_dry_draw / wayfarer_dry_draw_off → stance_idle | 0.25 / 0.25 | 32.0 s 播完 | 淡回架势底层；右脚 31.70 s 才落回架势踝位 |
| stance_idle → wayfarer_pearls / wayfarer_pearls_off | 0.10 / 0.25 | 宿主在连珠起手时播放 | 起止姿态等于 `stance_idle` 首帧；按真实时间播 5.0 s；起手 0–2.00 s 可打断 |
| wayfarer_pearls / wayfarer_pearls_off → stance_idle | 0.25 / 0.25 | 5.0 s 播完 | 淡回架势底层；右脚 3.95 s 已回架势踝位 |
| stance_idle → wayfarer_bow_strike / wayfarer_bow_strike_off | 0.10 / 0.25 | 宿主在弓梢起手时播放 | 起止姿态等于 `stance_idle` 首帧；按真实时间播 5.0 s；起手不能打断；两次撤步原地迈步、实体由冲量推动 |
| wayfarer_bow_strike / wayfarer_bow_strike_off → stance_idle | 0.25 / 0.25 | 5.0 s 播完 | 淡回架势底层；双脚 3.55 s 已回架势踝位（收势先右后左） |
| stance_idle → wayfarer_repeater / wayfarer_repeater_off | 0.10 / 0.25 | 宿主在连弩起手时播放 | 起止姿态等于 `stance_idle` 首帧；按真实时间播 5.0 s；起手不能打断 |
| wayfarer_repeater / wayfarer_repeater_off → stance_idle | 0.25 / 0.25 | 5.0 s 播完 | 淡回架势底层；前脚 3.90 s 已回架势踝位 |
| stance_idle → wayfarer_gale / wayfarer_gale_off | 0.10 / 0.25 | 宿主在横风起手时播放 | 起止姿态等于 `stance_idle` 首帧；按真实时间播 6.0 s；起手 0–2.20 s 可打断；副手版扇面先左后右，要服务端同步（PROGRESS 待决定第 13 条） |
| wayfarer_gale / wayfarer_gale_off → stance_idle | 0.25 / 0.25 | 6.0 s 播完 | 淡回架势底层；后脚 4.90 s 已回架势踝位 |
| stance_idle → wayfarer_kneeling / wayfarer_kneeling_off | 0.10 / 0.25 | 宿主在跪射起手时播放 | 起止姿态等于 `stance_idle` 首帧；按真实时间播 7.0 s；起手 0–2.60 s 可打断，跪着被打断在淡出里站起；跪下只是动画，碰撞箱不变 |
| wayfarer_kneeling / wayfarer_kneeling_off → stance_idle | 0.25 / 0.25 | 7.0 s 播完 | 淡回架势底层；右脚 5.85 s 已回架势踝位 |
| stance_idle → wayfarer_parthian / wayfarer_parthian_off | 0.10 / 0.25 | 宿主在回马起手时播放 | 起止姿态等于 `stance_idle` 首帧；按真实时间播 5.0 s；起手不能打断；三次冲量、跳起转身都原地做；主手版向左转身 175°（胯世界 +15° → −160°）、空中右转回射位，副手版方向相反，不转整圈、不用 `root`（PROGRESS 待决定第 4 条） |
| wayfarer_parthian / wayfarer_parthian_off → stance_idle | 0.25 / 0.25 | 5.0 s 播完 | 淡回架势底层；右脚 3.00 s 已回架势踝位 |
| stance_idle → wayfarer_arrow_rain / wayfarer_arrow_rain_off | 0.10 / 0.25 | 宿主在箭雨起手时播放 | 起止姿态等于 `stance_idle` 首帧；按真实时间播 7.0 s；起手 0–2.80 s 可打断；不作后式，总从 0 起播 |
| wayfarer_arrow_rain / wayfarer_arrow_rain_off → stance_idle | 0.25 / 0.25 | 7.0 s 播完 | 淡回架势底层；右脚 5.40 s 已回架势踝位 |
| stance_idle → wayfarer_convergence / wayfarer_convergence_off | 0.10 / 0.25 | 单独预览、回归 | 服务端只让满月、箭雨承接施放（从 1.20 s 起播），这条从 0 起播的边游戏里不出现；按真实时间播 9.0 s；起手 0–2.50 s 可打断 |
| wayfarer_convergence / wayfarer_convergence_off → stance_idle | 0.25 / 0.25 | 9.0 s 播完 | 淡回架势底层；双脚 6.86 s 已回架势踝位；之后不能承接、不能换兵 |

承接与换兵的设计行（还没接线；主副手各按后一式施放时武器所在的手选 `_off`）：

| 来源 | 目标 | 淡入 / 淡出 | 触发 | 约束 | 状态 |
| --- | --- | ---: | --- | --- | --- |
| wayfarer_bow_strike | wayfarer_pearls | 0.30 / 0.25 | 承接（宿主在后一式起手时切换） | 前一式淡出 0.30 s，后一式从 0.40 s 起播 | 待承接接线 |
| wayfarer_pearls | wayfarer_parthian | 0.30 / 0.25 | 承接 | 从 0.35 s 起播 | 待承接接线 |
| wayfarer_repeater | wayfarer_gale | 0.30 / 0.25 | 承接 | 从 0.40 s 起播 | 待承接接线 |
| wayfarer_full_draw / wayfarer_arrow_rain | wayfarer_convergence | 0.30 / 0.25 | 承接 | 从 1.20 s 起播，跳过「聚」；天地同至只能这样施放 | 待承接接线 |
| wayfarer_repeater / wayfarer_gale / wayfarer_kneeling | wayfarer_full_draw / wayfarer_pearls | 0.30 / 0.25 | 换兵（弩切到弓后起手） | 从 0.40 s 起播；物品在前一式淡出的 0.30 s 里由原版换掉，交叉淡入盖住 | 待承接接线 |
| wayfarer_repeater / wayfarer_gale / wayfarer_kneeling | wayfarer_bow_strike | 0.30 / 0.25 | 换兵 | 从 0.30 s 起播 | 待承接接线 |
| wayfarer_full_draw / wayfarer_pearls / wayfarer_bow_strike / wayfarer_parthian / wayfarer_arrow_rain | wayfarer_repeater / wayfarer_gale / wayfarer_kneeling | 0.30 / 0.25 | 换兵（弓切到弩后起手） | 从 0.40 s 起播；天地同至之后不能换兵 | 待承接接线 |

## 五脏诀（2026-11-01 已交付资源）

规格与进度见 [PROGRESS-evoker](PROGRESS-evoker.md)。十七段都用下面的四列行（`AnimationTransitionContractTest` 解析，现 556 条边：十五段进出 30 条，握持覆盖层叠上 30 条、撤下 2 条）。
十五段都是 `mask: full`、优先级 24、`hand: main`（左主手整段镜像），起止姿态等于 `stance_idle` 首帧，按真实时间播；起手被伤害打断时淡出 0.20 s 回架势。握持覆盖层优先级 25，只接管拿东西那只手的三片手指。

| 来源 → 目标 | 淡入 / 淡出 | 触发 | 约束 |
| --- | ---: | --- | --- |
| stance_idle → evoker_water_frost / evoker_metal_edge / evoker_earth_quake / evoker_fire_burn / evoker_wood_thunder | 0.10 / 0.25 | 宿主在附伤各式起手时播放 | 起止姿态等于 `stance_idle` 首帧；按真实时间播 3.5 s；共用起手「唤」0.00–0.70，不开立；`cast` 2.00 |
| evoker_water_frost / evoker_metal_edge / evoker_fire_burn → stance_idle | 0.25 / 0.25 | 3.5 s 播完 | 淡回架势底层；双脚整段不离架势踝位 |
| evoker_earth_quake → stance_idle | 0.25 / 0.25 | 3.5 s 播完 | 淡回架势底层；右脚 0.80 提膝、1.95 跺地（`step_r`），2.55 提踵落回后脚位（`step_r`），之后双脚在架势踝位 |
| evoker_wood_thunder → stance_idle | 0.25 / 0.25 | 3.5 s 播完 | 淡回架势底层；两脚跟 0.80–1.55 半提、脚趾前缘不动，1.55 起双脚在架势踝位；`clap` 1.40 |
| stance_idle → evoker_fire_heart / evoker_metal_arms | 0.10 / 0.25 | 宿主在强身各式开始时播放 | 起止姿态等于 `stance_idle` 首帧；按真实时间播（心火 4.0 s、金臂 5.0 s）；开立右脚 `step_r`（心火 0.35、金臂 0.40） |
| evoker_fire_heart → stance_idle | 0.25 / 0.25 | 4.0 s 播完 | 淡回架势底层；两脚跟 0.80–2.00 在开立位半提；`cast` 1.80；右脚 2.80 提踵落回后脚位（`step_r`），之后双脚在架势踝位 |
| evoker_metal_arms → stance_idle | 0.25 / 0.25 | 5.0 s 播完 | 淡回架势底层；右脚单撑 1.00–3.20，左脚 1.95–2.12 脚尖点地；`cast` 2.60；左脚 3.20 落开立位（`step_l`），右脚 3.70 提踵落回后脚位（`step_r`），之后双脚在架势踝位 |
| stance_idle → evoker_earth_legs / evoker_water_return / evoker_wood_sinew | 0.10 / 0.25 | 宿主在强身各式开始时播放 | 起止姿态等于 `stance_idle` 首帧；按真实时间播（厚土 6.0 s、归水 5.0 s、舒筋 5.0 s）；厚土、舒筋开立右脚 `step_r`（0.40、0.35），归水不开立、左脚 0.45 起上步 |
| evoker_earth_legs → stance_idle | 0.25 / 0.25 | 6.0 s 播完 | 淡回架势底层；左脚 0.80 离地、1.40 落左前（`step_l`），右脚 2.10 离地、2.70 落右前（`step_r`）；`cast` 3.20；左脚 4.02 落回前脚位（`step_l`），右脚 4.70 提踵落回后脚位（`step_r`），之后双脚在架势踝位 |
| evoker_water_return → stance_idle | 0.25 / 0.25 | 5.0 s 播完 | 淡回架势底层；左脚 0.90 脚跟着地（`step_l`），右脚整段不离地，1.30–1.90 半落跟、2.70–3.12 提回；`cast` 2.40；左脚 3.30 落回前脚位（`step_l`），之后双脚在架势踝位 |
| evoker_wood_sinew → stance_idle | 0.25 / 0.25 | 5.0 s 播完 | 淡回架势底层；左脚 2.50 脚跟着地成虚步（`step_l`）；`cast` 2.80；左脚 3.35 落回前脚位（`step_l`），右脚 3.75 提踵落回后脚位（`step_r`），之后双脚在架势踝位 |
| stance_idle → evoker_refine_lung / evoker_refine_spleen / evoker_refine_liver / evoker_refine_heart / evoker_refine_kidney | 0.10 / 0.25 | 宿主在炼元各式开始时播放 | 起止姿态等于 `stance_idle` 首帧；按真实时间播 32.0 s，一次性；进式右脚 0.20 离地、0.50 落开立位（`step_r`） |
| evoker_refine_lung / evoker_refine_spleen / evoker_refine_liver / evoker_refine_heart / evoker_refine_kidney → stance_idle | 0.25 / 0.25 | 32.0 s 播完 | 淡回架势底层；0.50–31.10 两脚在开立位不动；右脚 31.10 离地、31.40 提踵落回后脚位（`step_r`），之后双脚在架势踝位；打断时两脚在淡出里滑回架势位 |
| evoker_water_frost / evoker_metal_edge / evoker_earth_quake / evoker_fire_burn / evoker_wood_thunder → evoker_grip / evoker_grip_off | 0.10 / 0.25 | 起手时主手 / 副手有东西，宿主同 tick 叠播（两只手都有就两段都叠） | 只接管那只手的拇指和两组手指（`r_fingers` / `l_fingers`），整段保持架势握拳；手腕、手臂、挂点照主剪辑；中途不加不撤 |
| evoker_fire_heart / evoker_metal_arms / evoker_earth_legs / evoker_water_return / evoker_wood_sinew → evoker_grip / evoker_grip_off | 0.10 / 0.25 | 同上 | 同上 |
| evoker_refine_lung / evoker_refine_spleen / evoker_refine_liver / evoker_refine_heart / evoker_refine_kidney → evoker_grip / evoker_grip_off | 0.10 / 0.25 | 同上 | 同上；32 s 整段保持 |
| evoker_grip / evoker_grip_off → stance_idle | 0.25 / 0.25 | 主剪辑播完或被打断时宿主同时停止 | 手指淡回下层，同为握拳，看不出变化 |

## 矛的单臂覆盖层

| 来源 | 目标 | 淡入 / 淡出 | 专门过渡 | 触发与约束 |
| --- | --- | --- | --- | --- |
| stand / walk / run / sneak / sneak_walk / ride / swim | spear_charge / spear_charge_off / spear_stab / spear_stab_off | 0.10 / 0.15 | 否 | 持矛首帧或本帧戳刺，保持下身与另一只手 |
| spear_charge / spear_charge_off / spear_stab / spear_stab_off | stand / walk / run / sneak / sneak_walk / ride / swim | 0.10 / 0.15 | 否 | 换下矛后淡回持续推进的底层 |
| spear_charge / spear_charge_off | spear_stab / spear_stab_off | 0.05 / 0.10 | 否 | 戳刺优先于同侧蓄力 |
| spear_stab / spear_stab_off | spear_charge / spear_charge_off | 0.10 / 0.15 | 否 | 戳刺结束，持矛手回到当前使用时刻 |
