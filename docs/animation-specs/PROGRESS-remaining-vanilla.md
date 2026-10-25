# 剩余原版动作进度（分支 `anim/remaining-vanilla`）

更新：2026-10-26。本页只记录这一批动作的制作进度，接口与验收规则仍以 [资产契约](../asset-contract.md) 和
[制作与验收规则](../animation-acceptance.md) 为准。

## 阶段 1：设计稿（已完成，待审阅）

| 动作 | 设计稿 | 原型门禁（设计期，仓库分析器同算法） | 骨 / 关键帧（预估或原型） |
| --- | --- | --- | --- |
| `sneak` | [sneak.md](sneak.md) | 通过（循环；站立检查需分析器扩展，自查踝漂移 0.00 px） | 24 / 155 |
| `sneak_walk` | [sneak_walk.md](sneak_walk.md) | 通过（循环步态支撑需分析器扩展） | 24 / 273 |
| `eat` / `eat_off` | [eat.md](eat.md) / [eat_off.md](eat_off.md) | 主手通过；握持门禁需加入 `GRIP_IDS` | 23 / 138 |
| `drink` / `drink_off` | [drink.md](drink.md) / [drink_off.md](drink_off.md) | 主手通过 | 23 / 136 |
| `block_shield` / `_off` | [block_shield.md](block_shield.md) / [block_shield_off.md](block_shield_off.md) | 主手通过 | 23 / 136 |
| `bow_draw` / `_off` | [bow_draw.md](bow_draw.md) / [bow_draw_off.md](bow_draw_off.md) | 首版未过（穿模、角速度），已给出修订姿势 | 预估 23–25 / 180 |
| `trident_charge` / `_off` | [trident_charge.md](trident_charge.md) / [trident_charge_off.md](trident_charge_off.md) | 首版三项未过（错峰、角速度、着地），已给出修订方法 | 23 / 196 |
| `climb` | [climb.md](climb.md) | 未跑（关键姿势表） | 预估 25 / 220 |
| `swim` | [swim.md](swim.md) | 未跑 | 预估 25 / 260 |
| `ride` | [ride.md](ride.md) | 未跑 | 预估 24 / 140 |
| `sleep` | [sleep.md](sleep.md) | 未跑；有效运动判据待审 | 预估 20 / 80 |
| `elytra` | [elytra.md](elytra.md) | 未跑 | 预估 24 / 150 |
| `head_follow`（+ `head_follow_pitch`） | [head_follow.md](head_follow.md) | 未跑；姿势层判据待审 | 预估 7 / 150 |

切换表：[transitions.md](transitions.md) 新增六列“设计稿”行，未改动现有 108 条回归边。

## 阶段 2：制作与验证（未开始，等待审阅意见）

建议顺序：`sneak` / `sneak_walk` → `eat` / `drink` → `block_shield` → `bow_draw` → `trident_charge` →
`head_follow`（运行时接口）→ `climb` / `swim` → `ride` / `sleep` / `elytra`。

每个动作：按审阅后的设计稿在 `models/player_skeleton_classic.bbmodel` 录入关键帧（副手逐帧镜像）→
`python3 scripts/export_bbmodel_animation.py` 导出 → `--check` → `python3 scripts/analyze_animation.py <ids>` →
`python3 scripts/render_animation_gif.py <ids> --fps 12`（输出到 `build/anim-review/<日期>/`）→ 接入运行时并补回归 → 提交推送。

## 需要改 Java / 分析器的接口缺口（汇总）

1. `PlayerAnimationAction`：新动作必须先入目录；`syncScrub` 对目录外 ID 会因 `find()` 返回 `null` 而空指针。
2. `VanillaAnimationScheduler`：`EAT/DRINK/BOW/SPEAR/BLOCK` 改为返回新 ID 并给出各自进度；新增潜行三选一、攀爬/游泳位移相位、
   骑乘/睡眠/鞘翅状态边沿；拉弓与三叉戟松手后的尾段推进（复用攻击尾段思路）。
3. `AnimationPlayer`：`upperOverlay` 加入吃喝/拉弓/格挡/三叉戟；`lowerBase` 加入 `sneak`、`sneak_walk`、`ride`、`climb`、`swim`；
   循环资源的 scrub 插值需要按最短回绕路径（攀爬/游泳）。
4. `PlayerMeshRenderer`：加性视线层（`head_follow`），底层去掉原版视线后再叠加；`applyArmPosePitch` 加入 `bow_draw`
   并与视线层去重。契约新增索引字段 `layer: look`。
5. `scripts/analyze_animation.py`：`GRIP_IDS`、`FULL_BODY_IDS`、`NON_STANDING_IDS` 补新 ID；`sneak` 启用站立循环检查、
   `sneak_walk` 启用步态支撑检查；`bow_draw` 的错峰主链改为拉弦臂；`layer: look` 的姿势层判据。
6. 文档不一致（既有）：`animation-acceptance.md` 写“展开 199 条有向边”，测试实际断言 108 条。

## 待审阅决定

- 潜行沿用原版 2.13 px 下沉（推荐）还是 `root` 补偿（[sneak](sneak.md) 第 1 节）。
- 拉弓侧身 65°、`upper` 叠加时扭转由腰胸承担、是否要撒放尾段（[bow_draw](bow_draw.md) 第 10 节）。
- 三叉戟投掷：拉长跟随段（推荐）还是申请打击时段白名单（[trident_charge](trident_charge.md) 第 4 节）。
- 攀爬朝向假设与攀爬中单臂叠加（[climb](climb.md) 第 6、8 节）。
- 睡眠与视线层的有效运动判据（[sleep](sleep.md) 第 8 节、[head_follow](head_follow.md) 第 6 节）。
- 饮用 40 tick 蜂蜜瓶按真实 tick 对齐（[drink](drink.md) 第 9 节）。

## 设计期原型脚本

原型轨道与 FK/IK 脚本不入库，保存在本机 `~/Code/Kizuna-remote-session/handoff/kpa-remaining-vanilla-proto/`
（`d_sneak.py`、`d_eat.py`、`d_combat.py` 生成本批设计稿附表）。
