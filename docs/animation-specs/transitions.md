# 状态切换表

本表是第 4 节回归的唯一输入。`blend` 是淡入/淡出秒数；“专门过渡”表示第二阶段必须在
`.bbmodel` 中制作独立过渡段，不能用目标动作的第一帧硬切。上层一次性动作结束后回到
切换发生时的底层状态，不默认回站姿。

当前资源覆盖站立、步态、跳跃、冥想、攻击、挖掘、使用、放置和穿戴。潜行、游泳、攀爬、
骑乘、睡觉、鞘翅、吃喝、拉弓、三叉戟、扭头、闪避和格挡继续使用原版姿势；潜行时的工具
操作由 `crouch_work` 只接管支撑腿与躯干，工具动作仍按上层遮罩叠加。

状态集合：`stand`、`walk`、`run`、`jump_takeoff`、`jump_air`、`jump_land`、`meditate`、
`attack`、`mine`、`use_item`、`place_block`、`equip_helmet`、`equip_chestplate`、
`equip_leggings`、`equip_boots`。

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

## 叠加组合回归

以下组合必须用真实 `AnimationPlayer` 逐帧采样：`walk+attack`、`run+mine`、`walk+use_item`、
`run+crossbow_hold`、`jump_air+attack`、`crouch_work+use_item`、`meditate+spyglass`。
检查上下身骨骼没有断开，手臂不穿过躯干，停止上层后底层轨道继续推进；一次性动作结束走
淡出而不是瞬间复位。

## 原版 ArmPose 使用动作

调度按原版 `PlayerEntityRenderer.getArmPose` 的语义选 ID：`UseAction.SPYGLASS → spyglass`、
`TOOT_HORN → toot_horn`、使用中的弩 `→ crossbow_charge`、持已上弦且未使用的弩 `→ crossbow_hold`、
`BRUSH → brush`；其余持续使用 `→ use_item`。EAT / DRINK / BOW / SPEAR / BLOCK 保持原版姿势。
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
