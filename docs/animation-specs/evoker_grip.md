# evoker_grip / evoker_grip_off：五脏诀握持覆盖层（手里有东西时保持握拳）

> 状态：**已实现，门禁通过，待游戏内验收**（2026-11-01）。规格 2026-11-01 审过（待决定全部按建议）；两段关键帧已录入 `player_skeleton_classic.bbmodel`（细臂共用同一导出）并导出，实测与出入见第 10 节；没在游戏里看过，宿主叠播接线归整合会话。轴向、坐标和面板值约定见 [共用约定](conventions.md)；架势基准见 [stance_idle](stance_idle.md) 第 3 节；十七段共用的约束（握持见 E10、E12）与待决定见 [PROGRESS-evoker](PROGRESS-evoker.md)。本页同时写主手版 `evoker_grip` 与副手版 `evoker_grip_off`，同 [door_breaker_guard](door_breaker_guard.md) 一页写两段。
> 来源：五脏诀动作稿 `~/Code/Kizuna-remote-session/handoff/pa-cloud-anim/19-Players-Animation-Evoker.md` §1、§4、§6、§7、§9；运行时（只读）`AnimationEntry`、`AnimationPlayer`；分析器（只读）`scripts/analyze_animation.py` 的 `GRIP_IDS`、`ARM_OVERLAY_IDS`。
>
> **与动作稿最大的不同**：整段保持架势握拳（两帧同值），不是动作稿 §4 的「从张开收到握持」：十五段起手时主剪辑本来就是握拳，覆盖层先张后握会让拿东西的手闪一下（待决定第 5 条）。

## 1. 用途与触发

- **做什么**：五脏诀十五段都要换手型（掌、立掌、猿钩、剑指……），手里拿着剑、火把等东西时，东西会挂在张开的手指上。宿主同时叠播覆盖层，只接管拿东西那只手的拇指和两组手指，保持握拳；手腕、手臂、躯干仍照主剪辑。
- **哪只手**：
  - 主手有东西：叠 `evoker_grip`（`mask: r_fingers`）；
  - 副手有东西：叠 `evoker_grip_off`（`mask: l_fingers`）；
  - 两只手都有：两段都叠。
- **左主手玩家**：两段都写 `hand: main`，同所配的主剪辑（十五段都是 `main`；同 `door_breaker_guard` 按所配的式取 `hand` 的做法）。运行时整段镜像时，遮罩先按源骨判断再换到镜像骨，`r_fingers` 落到左手，正是左主手玩家的主手，不另做资源。
- **宿主怎么播**（宿主行为，本会话不改，写给整合会话）：
  1. 起手那一刻判断手里有没有东西，有就和主剪辑在同一 tick 播，`start_tick` 相同；
  2. 中途换手、东西用完，首版不加不撤（同 `door_breaker_guard`）；
  3. 主剪辑播完或被打断时，宿主同时停覆盖层，覆盖层按自己的 `blend_out` 0.25 s 淡出，与主剪辑的淡出一起走。
- **层次**：主剪辑优先级 24，覆盖层 25，后叠；遮罩只含三片手指，不含 `*_item` 挂点，东西照旧挂在手上、随手腕走。

## 2. 时间轴

| 段 | 时间 (s) | 内容 |
| --- | --- | --- |
| 保持 | 0.00–0.20 | 两帧（0.00、0.20）同值，等于 `stance_idle` 0 s 的握拳 |
| 末帧 | 0.20 起 | `hold_on_last_frame`，直到宿主停止 |

- 不带 tick（宿主显式停止，同 `door_breaker_guard`、`technique_leap`）；没有 `timeline` 事件。
- 0.20 s 只为让资源有长度；两帧同值，曲线是常数。

## 3. 姿势

| 骨 | 主手版（`evoker_grip`） | 副手版（`evoker_grip_off`） |
| --- | --- | --- |
| `thumb` | `r_thumb` (10, 0, 50) | `l_thumb` (8, 0, −48) |
| `fingers_a` | `r_fingers_a` (0, 0, 80) | `l_fingers_a` (0, 0, −78) |
| `fingers_b` | `r_fingers_b` (0, 0, 86) | `l_fingers_b` (0, 0, −84) |

- 取 `stance_idle` 0 s 的两只拳各自的值（两只拳本来就不对称，不互为镜像），也就是十五段首帧、末帧的手指。
- `stance_idle` 的拳以后改了，覆盖层跟着改（原型从架势首帧读，不手抄）。

## 4. 与主剪辑共存

- **起手**：主剪辑 0.05–0.30 拳松成掌，覆盖层淡入 0.10 s。0.05–0.10 主剪辑的手指张开到约 7°，覆盖层权重同时升到 1，叠出来偏离握拳最多约 0.5°（按线性淡入、余弦缓动粗算）；之后拿东西的手一直是拳，看不出闪。
- **换手型的式**：拿东西的那只手手腕、前臂照主剪辑翻转、立掌、成钩，只是手指是拳。几处会有穿插（E10）：
  - 锐金剑指抹锋、灼火搓掌、惊雷击掌、心炼捧掌：另一只手会碰到或穿进拿着的东西；
  - 肾炼摩腰：东西会穿进身体；
  - 心火猿钩、舒筋虎爪、立掌：东西跟着手腕翻。
  动作稿 §9 首版不藏，本稿照此；验收时用户看合成预览定（第 8 节）。
- **两手相触门禁**（E9）只查主剪辑本身，不把覆盖层合进去：合上去以后，握着东西的拳和另一只手的间隙会变，这由游戏内看。
- **结束**：覆盖层淡出时手指从拳回到下层（主剪辑末帧或架势），两者都是同一组握拳值，看不出变化。

## 5. 衔接

- 十五段每段 → `evoker_grip`、→ `evoker_grip_off`：叠上，0.10 / 0.25；
- `evoker_grip`、`evoker_grip_off` → `stance_idle`：撤下，0.25 / 0.25；
- 切换行见 [transitions](transitions.md)「五脏诀」。

## 6. 门禁判据（分析器新增 `FINGER_OVERLAY_IDS`）

两段加入 `GRIP_IDS`（握持门禁按 `_off` 后缀选手，主手版查右手、副手版查左手），另立 `FINGER_OVERLAY_IDS = {"evoker_grip": "r", "evoker_grip_off": "l"}`，判据同单臂覆盖层 `ARM_OVERLAY_IDS` 的写法：

| 门禁 | 适用 | 说明 |
| --- | --- | --- |
| 有效运动骨骼 | 改判 | 不要求运动：覆盖手三片手指的旋转绝对值都 ≥ 3°（握拳 48–86°）即通过，最少 3 根 |
| 躯干链 / 头 | 不适用 | 遮罩不含躯干与头 |
| 双臂双腿（全身动作） | 不适用 | 不加入 `FULL_BODY_IDS` |
| 握持手指 | 适用 | 覆盖手手指有握持姿势（现有门禁，不改） |
| 预备 / 跟随 | 不适用 | 静止保持，没有主动作峰值 |
| 主链峰值错开 | 不适用 | 同上 |
| 曲线相关性 | 适用 | 常数曲线不计入，自然通过 |
| 多轴 | 不适用 | 静止保持 |
| 关节角度 | 适用 | 手指不在受限骨里，其余骨为零位 |
| 角速度 / 抽搐 | 适用 | 速度为零 |
| 手臂穿模 | 适用 | 其余骨为零位，同 `door_breaker_guard` |
| 着地 / 滑动 / 重心 | 不适用 | 遮罩不含腿 |
| 两手相触 | 不适用 | 不声明 |
| 循环接缝 | 不适用 | `hold_on_last_frame` 不是循环（分析器 `loop` 只认 `true`） |

- `scripts/test_animation_antiforgery.py` 加一条回归：手指覆盖层里三片手指都是 0°（张开）时拒绝，握拳放行。
- 分析器改动在制作阶段做，本页只定判据。

## 7. 索引条目

```json
{
  "id": "evoker_grip",
  "file": "animations/evoker_grip.animation.json",
  "mask": "r_fingers", "priority": 25, "blend_in": 0.10, "blend_out": 0.25,
  "hand": "main", "fpv": false
},
{
  "id": "evoker_grip_off",
  "file": "animations/evoker_grip_off.animation.json",
  "mask": "l_fingers", "priority": 25, "blend_in": 0.10, "blend_out": 0.25,
  "hand": "main", "fpv": false
}
```

- 动画文件 `loop: "hold_on_last_frame"`、`animation_length: 0.2`，只含覆盖手的三片手指。
- `r_fingers` / `l_fingers` 是新 mask：`AnimationEntry.Mask` 加 `R_FINGERS`、`L_FINGERS`，`asset-contract.md` 的 mask 表同步（[PROGRESS](PROGRESS-evoker.md)「制作时要改的 PA 文件」）。
- 不登记 `phases`、`impact_window`、`burst_windows`、`unbalanced_windows`、`hand_contact_windows`。
- `PlayerAnimationAction` 两段不带 tick。

## 8. 自查与验收

**自查（原型每次跑，不入门禁）**：
- 两帧逐值相同，等于 `stance_idle` 0 s 对应手的三片手指；
- 资源只含三片手指轨道。

**合成预览**：渲染脚本不支持叠层。制作时用临时探针把覆盖层合进惊雷、灼火、心炼的打包资源出预览（同破门斩 `probe_guard_render.py` 的做法，不入库），给用户看拿东西的手在击掌、搓掌、捧掌时的样子。物品穿插、视觉效果都要进游戏看。

**接触表**：覆盖层单独只有 1 个时刻（0 s）；合成预览取惊雷 1.40、灼火 1.1375、心炼 3.00 三帧。

## 9. 待审阅方确认

1. 整段保持握拳，不做「从张开收到握持」（待决定第 5 条）。
2. 新 mask `r_fingers` / `l_fingers` 与 `FINGER_OVERLAY_IDS` 判据（第 6、7 节）。

## 10. 制作实测（2026-11-01）

原型 `kpa-door-breaker-proto/evoker/d_grip.py`（交接目录，不入库）从原型的架势设计姿势 `common.BASE` 取覆盖手三片手指，0.00、0.20 两帧同值；`install_ev.py grip grip:off` 装进 `player_skeleton_classic.bbmodel` 后导出。

**资源**：两段都是 `loop: "hold_on_last_frame"`、`animation_length: 0.2`，各只有覆盖手三条旋转轨道、各两帧，逐值相同：
- `evoker_grip`：`r_thumb` (10, 0, 50)、`r_fingers_a` (0, 0, 80)、`r_fingers_b` (0, 0, 86)（面板值；导出 JSON 的 X 取反）；
- `evoker_grip_off`：`l_thumb` (8, 0, −48)、`l_fingers_a` (0, 0, −78)、`l_fingers_b` (0, 0, −84)。

索引同第 7 节，没有 `phases`、`timeline` 和各类窗口。

**门禁**（粗、细臂两套相同）：全部通过。
- 有效运动按覆盖手三片手指的保持角度判：三片都 ≥ 3°（48–86°），通过。报告里 `count` 仍是按运动幅度数的骨数（0），判定看 `FINGER_OVERLAY_IDS` 分支。
- 握持手指：主手版右手 50 / 80 / 86°、副手版左手 48 / 78 / 84°，通过。
- 关节角度：其余骨都是零位；角速度、抽搐都是 0；手臂穿模 0。
- 曲线相关：没有运动曲线，分析器记 0 条有效曲线、标不适用（第 6 节写的「常数曲线不计入、自然通过」就是这个结果）。
- 躯干链、预备 / 跟随、错峰、着地、两手相触、循环接缝：不适用（理由同第 6 节）。

**自查**：
- 两帧逐值相同；资源只含三片手指轨道。
- 拳和十五段首帧、末帧的手指逐值相同（导出资源对过，两只手都对上）。
- 起手淡入：按 `AnimationPlayer` 的线性淡入 0.10 s，把十五段 0–0.20 s 的手指和覆盖层叠起来，偏离握拳最多 0.44°（附伤五式、心火 0.083 s 右 `fingers_b`，主剪辑这时已张开 2.6°）；炼元、金臂 0.24–0.26°，厚土、归水、舒筋 ≤ 0.07°。第 4 节估约 0.5°。
- 合成预览（临时探针，不入库）：覆盖层换进惊雷 1.40、灼火 1.1375、心炼 3.00 的打包资源，覆盖手挂一把剑，每个时刻四格（原样 / 叠主手版、原样 / 叠副手版），粗、细臂各一张接触表，另出三段叠上后的 GIF（心炼只出 0–6 s）。

**与规格的出入**：
- **拳取架势第 3 节的设计值**，不是导出 `stance_idle` 0 s 那一帧：`stance_idle` 是 2 s 循环，手指随呼吸在设计值上下各约 3° 摆（右拇指 47.15–52.85°、右 `fingers_a` 76.44–83.56°、右 `fingers_b` 82.76–89.24°；左手同样），0 s 正好在最松的一头。第 2、3 节写的「等于 `stance_idle` 0 s 的握拳」按第 3 节的表值做，也就是十五段首帧、末帧的手指；覆盖层淡出回到架势时手指有约 3° 以内的差，由下层的呼吸摆动带过。
- 合成预览里覆盖手的剑：惊雷击掌、灼火搓掌时剑刃朝上后方，正视、侧视都被头挡住（穿进或紧贴头部）；心炼托掌时剑从拿剑的手朝体侧横出，不碰另一只手。东西挂在手上、随手腕走，原样和叠覆盖层两格位置一样，穿插来自主剪辑的手腕姿势（第 4 节、E10，动作稿 §9 首版不藏），由用户看预览和进游戏定。

**输出**：`build/anim-review/2026-11-01/evoker_grip/`、`evoker_grip_off/`（单段 GIF、慢放、0 s 接触表）；合成预览在 `build/anim-review/2026-11-01/evoker_grip_preview/`。
