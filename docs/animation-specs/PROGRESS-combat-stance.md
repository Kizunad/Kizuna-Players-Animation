# 战斗架势与基础功法动作进度

更新：2026-10-27。本批 22 段动作**已录入 `models/player_skeleton_classic.bbmodel` 并导出**，仓库门禁（粗臂、细臂）、导出一致性与 `./gradlew test build` 全部通过；
GIF 与接触表在 `build/anim-review/combat-stance/<id>/`（粗臂、细臂各一份，三视角）。**未做**：游戏内验收与双客户端录制；触发与“架势中”标志的同步属于战斗 UI / 宿主，本仓库只交付资源、`PlayerAnimationAction` 显式接口和播放层次。

接口与验收规则以 [资产契约](../asset-contract.md)、[制作与验收规则](../animation-acceptance.md)（第 3 节“战斗架势批次的门禁改动”）和 [共用约定](conventions.md) 为准；状态切换见 [切换表](transitions.md) 的“战斗架势”节。

## 1. 清单

| ID | 设计稿 | 类型与时长 | 层次（mask，优先级） | 驱动 | 骨 / 关键帧（原型） | 门禁（粗臂/细臂） |
| --- | --- | --- | --- | --- | --- | --- |
| `stance_idle` | [stance_idle.md](stance_idle.md) | 循环 2.0 s | 底层（full，2） | 自由时钟 | 24 / 233 | 全部适用项通过 |
| `stance_enter` | [stance_enter.md](stance_enter.md) | 过渡 0.45 s（末帧保持） | 底层过渡（full，3） | 真实时间 | 24 / 325 | 全部适用项通过 |
| `stance_exit` | [stance_exit.md](stance_exit.md) | 过渡 0.45 s（末帧保持） | 底层过渡（full，3） | 真实时间 | 24 / 332 | 全部适用项通过 |
| `stance_walk_forward` | [stance_walk_forward.md](stance_walk_forward.md) | 循环 0.8 s | 底层（full，2） | 自由时钟 | 24 / 459 | 全部适用项通过 |
| `stance_walk_back` | [stance_walk_back.md](stance_walk_back.md) | 循环 0.8 s | 底层（full，2） | 自由时钟 | 24 / 459 | 全部适用项通过 |
| `stance_walk_left` | [stance_walk_left.md](stance_walk_left.md) | 循环 0.8 s | 底层（full，2） | 自由时钟 | 24 / 459 | 全部适用项通过 |
| `stance_walk_right` | [stance_walk_right.md](stance_walk_right.md) | 循环 0.8 s | 底层（full，2） | 自由时钟 | 24 / 459 | 全部适用项通过 |
| `stance_run` | [stance_run.md](stance_run.md) | 循环 0.6 s | 底层（full，2） | 自由时钟 | 24 / 383 | 全部适用项通过 |
| `dodge_forward` | [dodge_forward.md](dodge_forward.md) | 一次性 0.70 s | 全身（full，16） | 真实时间 | 24 / 425 | 全部适用项通过 |
| `dodge_back` | [dodge_back.md](dodge_back.md) | 一次性 0.70 s | 全身（full，16） | 真实时间 | 24 / 440 | 全部适用项通过 |
| `dodge_left` | [dodge_left.md](dodge_left.md) | 一次性 0.70 s | 全身（full，16） | 真实时间 | 24 / 431 | 全部适用项通过 |
| `dodge_right` | [dodge_right.md](dodge_right.md) | 一次性 0.70 s | 全身（full，16） | 真实时间 | 24 / 435 | 全部适用项通过 |
| `technique_dash_forward` | [technique_dash_forward.md](technique_dash_forward.md) | 一次性 0.78 s | 全身（full，16） | 起手时间 / 竖直速度 / 落地时间 | 24 / 426 | 全部适用项通过 |
| `technique_dash_back` | [technique_dash_back.md](technique_dash_back.md) | 一次性 0.78 s | 全身（full，16） | 同上 | 24 / 443 | 全部适用项通过 |
| `technique_dash_left` | [technique_dash_left.md](technique_dash_left.md) | 一次性 0.78 s | 全身（full，16） | 同上 | 24 / 438 | 全部适用项通过 |
| `technique_dash_right` | [technique_dash_right.md](technique_dash_right.md) | 一次性 0.78 s | 全身（full，16） | 同上 | 24 / 428 | 全部适用项通过 |
| `block_one_hand` | [block_one_hand.md](block_one_hand.md) | 保持 1.95 s（循环 0.35–1.95） | 上层（full，15；移动取上身） | 格挡状态时长 | 24 / 206 | 全部适用项通过 |
| `block_one_hand_hit` | [block_one_hand_hit.md](block_one_hand_hit.md) | 一次性 0.30 s | 上层（full，16；移动取上身） | `block_hit` 事件 | 24 / 152 | 全部适用项通过 |
| `block_two_hand` | [block_two_hand.md](block_two_hand.md) | 保持 1.95 s（同上） | 上层（full，15）；全身动作 | 格挡状态时长 | 24 / 206 | 全部适用项通过 |
| `block_two_hand_hit` | [block_two_hand_hit.md](block_two_hand_hit.md) | 一次性 0.30 s | 上层（full，16）；全身动作 | `block_hit` 事件 | 24 / 152 | 全部适用项通过 |
| `technique_breath` | [technique_breath.md](technique_breath.md) | 一次性 2.0 s | 静止 full / 移动取上身（17） | 真实时间；对齐功法时可按起手 1.10 / 收势 0.90 分段映射 | 23 / 374 | 全部适用项通过 |
| `technique_charge` | [technique_charge.md](technique_charge.md) | 一次性 2.0 s | 静止 full / 移动取上身（17） | 真实时间；对齐功法时可按起手 1.60 / 收势 0.40 分段映射 | 23 / 451 | 全部适用项通过 |

合计 8116 个关键帧。门禁列是导出资源在 `scripts/analyze_animation.py` 上的实测（Gradle `checkAnimationGates` 已加入这 22 段）。

## 2. 架势基准

细节见 [stance_idle](stance_idle.md) 第 3 节。右手为主手，左主手按主副手镜像模板整套左右互换。左（前）脚全掌；右（后）脚提踵（脚底 −35°、脚趾背伸 10°，脚趾前缘承重），踝抬到 `(−1.7, 1.72, 3.2)`；
胯 `(0.15, −1.8, 0.6)` / `(−6, 12, 0)`，双膝屈 58° / 75°；前拳在左肩前方下巴高、拳峰朝前，后拳护右下颌侧前方、肘下沉贴肋，两拳不重叠；头收下巴。

## 3. 审阅决定

设计稿两轮审阅的全部决定已落实（第 2 版设计稿记录了 Q1–Q17）；复审决定：N1 进出架势 0.45 s；N2 `unbalanced_windows`；N3 `slide_window` 只作游戏内验收口径；N4 疾跑全掌；
N5 受击是被动动作，去掉“迎劲”，索引声明 `reaction: hit` 由门禁免查预备（跟随/回落照查）。

## 4. 制作阶段的改动

- **分析器**（`scripts/analyze_animation.py`）：恢复判定按“首帧或零姿态”；着地滑动按每段连续着地统计；侧滑步查 X 向；新增 `unbalanced_windows`（仅闪避/轻步）与 `reaction: hit`（仅 `*_hit`）两个索引字段；扩充 `STANDING_LOOP_IDS`、`GAIT_IDS`、`FULL_BODY_IDS`、`GRIP_IDS`。
  全部 72 段已交付动作（不含校准）重新跑过，粗臂、细臂均通过；`test_animation_antiforgery.py` 通过。规则写进了验收文档第 3 节。
- **渲染器**（`scripts/render_animation_gif.py`）：接触表膝、肘的白缝是离线刚性 cube 的切口楔形缝，游戏内 `SkinnedMesh` 蒙皮不会露缝。渲染器按同样规则在同一皮肤部件的父子切口补 1 像素过渡带，粗臂、细臂都已消除。
- **运行时**（`AnimationPlayer`）：原版上层在任何架势底层（含 `stance_idle`）、闪避、轻步、格挡上只取 `upper`；格挡、受击、调息、蓄力在 `stance_idle` 上整段生效，移动时只取 `upper`。`PlayerAnimationAction` 新增 22 个显式接口（一次性动作带目录时长：闪避 14、轻步 16、受击 6、调息 / 蓄力 40 tick）。
- **测试**：`AnimationSetContractTest` 更新打包集合并新增两条架势叠加用例；`AnimationTransitionContractTest` 的有向边从 193 条更新为 439 条；Gradle `checkAnimationGates` 加入 22 段。
- **索引**：`player_anim_index.json` 新增 22 条（字段见资产契约）。

## 5. 用户验收返工（2026-10-27）

用户验收 22 段中 12 段通过；以下 10 段按反馈返工，其余 12 段未改动（`.bbmodel` 动画数据、导出 JSON、索引条目逐字未变）。

- **`dodge_*`**（“做一下腿部发力起跳和落地腿部回收动能，以及上身保持平衡”）与 **`technique_dash_*`**（同上，并且“为什么这些腿部都没有动作？”）：
  改为完整腿部动作链——蓄力下蹲 → 蹬伸起跳（髋膝踝三关节伸展，蹬地脚提踵、脚趾背伸贴地，最后离地）→ 空中收腿 → 落地前伸腿迎地 → 先落脚、后落脚依次触地 → 屈膝缓冲 → 回架势；
  上身反向配重（下蹲时躯干前压、双臂后摆，蹬伸时躯干伸展、手臂送出，落地时躯干反向回收，头在躯干上反向稳定）。蹬地脚：前向后脚正蹬、后撤前脚蹬、左向后脚侧蹬、右向前脚侧蹬。
  `root` 不动，离地只靠胯高与腿的姿态表达。腿部由新的生成器 `chain.py` 求解（胯 C² 样条 + 每只脚“着地锁定 / 空中鼓包”踝轨迹 + 60 Hz IK + σ = 2 帧平滑 + 脚趾贴地逐帧求解）。
  闪避剪辑由 0.605 s 延长到 **0.70 s**（`PlayerAnimationAction` 13 → 14 tick），理由见各设计稿第 1 节；轻步时长与驱动不变（0.78 s）。
- **爆发窗口**：索引新字段 `burst_windows`（每段至多一段、≤ 0.08 s，只允许这 8 段），窗口内 1,500°/s、6×10⁶°/s³，窗口外照常；分析器、验收文档第 3 节、资产契约与回归脚本同步。
  各段声明与实测见设计稿第 6、7 节。失衡窗口随新时间表改为闪避 `[[0.16, 0.26], [0.40, 0.50]]`、轻步 `[[0.08, 0.18], [0.38, 0.49]]`。
- **`technique_breath` / `technique_charge`**（“感觉可以把动画延长一点，改成2秒即可”）：剪辑改为 **2.0 s**（`PlayerAnimationAction` 20 / 16 → 40 tick），按 2 s 重排节奏：
  调息加长深吸、加入吸满停顿与长吐气；蓄力加入“合住”停顿、放慢“开”、定劲段拉长为弱颤 → 强颤。功法占位时序（起手 0.5 / 0.6 s）与 2 s 表现不一致，见设计稿第 1 节，待功法侧决定。
- **渲染器**：接触表为闪避、轻步固定十个阶段时刻（下蹲、离地、收腿、迎地、先落、跟上、缓冲等），不再均匀取样。

## 6. 仍待解决

- 游戏内 Windows Native 录制、双客户端一致性、`slide_window` 段的世界空间滑行观感，需要触发方接线后才能做。
- 架势状态同步、方向扇区调度、受击事件、轻步按竖直速度取样等调度在战斗 UI / 宿主侧实现。
- 待功法侧决定：调息 / 蓄力的占位时序（起手 0.5 / 0.6 s）与 2.0 s 表现如何对齐；轻步起手 0.1 s 限制了下蹲深度（0.9 px），若要更明显的下蹲需要加长起手。

## 7. 设计期原型脚本

`~/Code/Kizuna-remote-session/handoff/kpa-combat-stance-proto/`：`d_*.py` 生成各段，`install.py` 写入 `.bbmodel` 并调用仓库导出脚本、登记索引，`summary.py` 汇总门禁与关键帧表，`w_*.py` 生成本目录的设计稿；`chain.py` 是闪避 / 轻步的腿部动作链生成器，`install.py <id...>` 可只重装指定动作。
