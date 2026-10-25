# 状态切换表

本表是第 4 节回归的唯一输入。`blend` 是淡入/淡出秒数；“专门过渡”表示第二阶段必须在
`.bbmodel` 中制作独立过渡段，不能用目标动作的第一帧硬切。上层一次性动作结束后回到
切换发生时的底层状态，不默认回站姿。

当前资源覆盖站立、步态、跳跃、冥想、攻击、挖掘、使用、放置和穿戴。潜行、游泳、攀爬、
骑乘、睡觉、鞘翅、吃喝、拉弓、三叉戟、扭头、闪避和格挡继续使用原版姿势；潜行时的工具
操作由 `crouch_work` 只接管支撑腿与躯干，工具动作仍按上层遮罩叠加。

2026-10-26 剩余原版动作第一阶段只写了设计稿（见下方“剩余原版动作（设计稿）”与 ArmPose 小节新增行）：
`sneak`、`sneak_walk`、`eat`、`drink`、`bow_draw`、`block_shield`、`trident_charge`、`climb`、`swim`、`ride`、
`sleep`、`elytra` 与视线叠加层 `head_follow`。设计稿行使用六列（末列“状态”），`AnimationTransitionContractTest` 只解析五列/ArmPose 四列的已实现行，
阶段 2 实现某个动作时，把对应行改回五列/四列格式并同步更新测试中的边数（`sneak` / `sneak_walk`、`eat` / `drink`、`block_shield`、`bow_draw`、`trident_charge`、`ride`、`sleep`、`elytra` 已实现，当前 183 条有向边）。
资源尚未制作，运行时仍使用原版姿势，不能当作已实现。

状态集合：`stand`、`walk`、`run`、`jump_takeoff`、`jump_air`、`jump_land`、`meditate`、
`attack`、`mine`、`use_item`、`place_block`、`equip_helmet`、`equip_chestplate`、
`equip_leggings`、`equip_boots`。设计中的状态：`sneak`、`sneak_walk`、`climb`、`swim`、`ride`、`sleep`、`elytra`，以及
上层 `eat`、`drink`、`bow_draw`、`block_shield`、`trident_charge`。

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
| stand / walk / jump_air | climb | 0.15 / 0.15 | 否 | `isClimbing()` 且未站稳；按竖直位移 scrub | 设计稿 |
| climb | stand / walk / jump_air | 0.15 / 0.15 | 否 | 离开可攀爬方块或着地 | 设计稿 |
| walk / run | swim | 0.3 / 0.3 | 否 | 进入泳姿；与原版 `leaningPitch` 放倒同步 | 设计稿 |
| swim | stand / walk | 0.3 / 0.3 | 否 | 离开泳姿 | 设计稿 |
| stand / walk / jump_air | ride | 0.25 / 0.25 | 否 | `hasVehicle()`（原版坐骑均为坐姿） |
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

调度按原版 `PlayerEntityRenderer.getArmPose` 的语义选 ID：`UseAction.SPYGLASS → spyglass`、
`TOOT_HORN → toot_horn`、`EAT → eat`、`DRINK → drink`、`BLOCK → block_shield`、`BOW → bow_draw`、`SPEAR → trident_charge`、使用中的弩 `→ crossbow_charge`、持已上弦且未使用的弩 `→ crossbow_hold`、
`BRUSH → brush`；其余持续使用 `→ use_item`。
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
| stand / walk / run / sneak / sneak_walk / crouch_work / ride / jump_air → bow_draw | 0.08 / 0.15 | 开始拉弓 | 按 `BowItem.getPullProgress` scrub；满弓在 1.0–1.6 回绕 |


| 来源 → 目标 | 淡入 / 淡出 | 触发 | 约束 |
| --- | ---: | --- | --- |
| bow_draw → stand / walk / run / sneak / sneak_walk / crouch_work / ride / jump_air | — / 0.15 | 松手 | pull ≥ 0.1 时先播 1.6–1.9 撒放尾段再淡出 |


| 来源 → 目标 | 淡入 / 淡出 | 触发 | 约束 |
| --- | ---: | --- | --- |
| stand / walk / run / sneak / sneak_walk / crouch_work / ride / jump_air → block_shield | 0.06 / 0.15 | 开始持盾 | 0.25 s（5 tick）举盾到位，与原版格挡生效同步 |


| 来源 → 目标 | 淡入 / 淡出 | 触发 | 约束 |
| --- | ---: | --- | --- |
| stand / walk / run / sneak / sneak_walk / crouch_work / ride / jump_air → trident_charge | 0.08 / 0.15 | 开始蓄力 | 0.50 s（10 tick）后引到位；松手投掷时先播 1.70–2.15 跟随段（激流不播） |

