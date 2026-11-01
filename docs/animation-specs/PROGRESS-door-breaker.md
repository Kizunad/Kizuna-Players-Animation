# 破门斩动作进度

更新：2026-10-30。按「一次一段」制作，动作总稿在会话交接目录 `door-breaker-animation-2026-10-28.md`（八式 + 持物手覆盖层，共 18 段）。

| ID | 设计稿 | 类型与时长 | 层次（mask，优先级） | 驱动 | 骨 / 关键帧 | 门禁（粗臂/细臂） |
| --- | --- | --- | --- | --- | --- | --- |
| `door_breaker_knock` | [door_breaker_knock.md](door_breaker_knock.md) | 一次性 3.0 s（60 tick） | 全身（full，24） | 真实时间；承接从 0.30 s 起播 | 24 / 645（斧刃版） | 全部适用项通过 |
| `door_breaker_knock_off` | [door_breaker_knock_off.md](door_breaker_knock_off.md) | 一次性 3.0 s（60 tick） | 全身（full，24） | 同上 | 24 / 634（斧刃版） | 全部适用项通过 |
| `door_breaker_cleave` | [door_breaker_cleave.md](door_breaker_cleave.md) | 一次性 6.0 s（120 tick） | 全身（full，24） | 真实时间；承接从 0.40 s 起播 | 24 / 1,507（C′） | 全部适用项通过 |
| `door_breaker_cleave_off` | [door_breaker_cleave_off.md](door_breaker_cleave_off.md) | 一次性 6.0 s（120 tick） | 全身（full，24） | 同上 | 24 / 1,480（C′） | 全部适用项通过 |
| `door_breaker_ram` | [door_breaker_ram.md](door_breaker_ram.md) | 一次性 5.0 s（100 tick） | 全身（full，24） | 真实时间；承接从 0.40 s 起播；冲刺段原地迈步、实体由冲量推动 | 24 / 1,470 | 全部适用项通过 |
| `door_breaker_ram_off` | [door_breaker_ram_off.md](door_breaker_ram_off.md) | 一次性 5.0 s（100 tick） | 全身（full，24） | 同上 | 24 / 1,469 | 全部适用项通过 |
| `door_breaker_hook` | [door_breaker_hook.md](door_breaker_hook.md) | 一次性 4.5 s（90 tick） | 全身（full，24） | 真实时间；承接从 0.40 s 起播 | 24 / 1,176 | 全部适用项通过 |
| `door_breaker_hook_off` | [door_breaker_hook_off.md](door_breaker_hook_off.md) | 一次性 4.5 s（90 tick） | 全身（full，24） | 同上 | 24 / 1,139 | 全部适用项通过 |
| `door_breaker_sweep` | [door_breaker_sweep.md](door_breaker_sweep.md) | 一次性 6.0 s（120 tick） | 全身（full，24） | 真实时间；承接从 0.50 s 起播；`root` 绕左脚转一整圈 | 25 / 1,342（含 `root`） | 全部适用项通过 |
| `door_breaker_sweep_off` | [door_breaker_sweep_off.md](door_breaker_sweep_off.md) | 一次性 6.0 s（120 tick） | 全身（full，24） | 同上（向右转） | 25 / 1,348（含 `root`） | 全部适用项通过 |
| `door_breaker_close` | [door_breaker_close.md](door_breaker_close.md) | 一次性 5.0 s（100 tick） | 全身（full，24） | 真实时间；两次撤步原地迈步、实体由冲量推动 | 24 / 1,577 | 全部适用项通过 |
| `door_breaker_close_off` | [door_breaker_close_off.md](door_breaker_close_off.md) | 一次性 5.0 s（100 tick） | 全身（full，24） | 同上 | 24 / 1,527 | 全部适用项通过 |
| `door_breaker_chain` | [door_breaker_chain.md](door_breaker_chain.md) | 一次性 8.0 s（160 tick） | 全身（full，24） | 真实时间；两次垫步、弓步原地做，实体由三次冲量推动 | 24 / 2,290 | 全部适用项通过 |
| `door_breaker_chain_off` | [door_breaker_chain_off.md](door_breaker_chain_off.md) | 一次性 8.0 s（160 tick） | 全身（full，24） | 同上 | 24 / 2,259 | 全部适用项通过 |
| `door_breaker_finale` | [door_breaker_finale.md](door_breaker_finale.md) | 一次性 9.0 s（180 tick） | 全身（full，24） | 真实时间；只作后式，承接从 1.50 s 起播；模型只收腿离地，跳起、落地由实体两次冲量给 | 24 / 2,548 | 全部适用项通过 |
| `door_breaker_finale_off` | [door_breaker_finale_off.md](door_breaker_finale_off.md) | 一次性 9.0 s（180 tick） | 全身（full，24） | 同上 | 24 / 2,600 | 全部适用项通过 |
| `door_breaker_guard` | [door_breaker_guard.md](door_breaker_guard.md) | 保持，收臂 0.40 s | 左臂（l_arm，25） | 破门、连门、万门一斩起手时另一只手非空，同 tick 叠播；宿主显式停止 | 6 / 31 | 全部适用项通过（单臂覆盖层判据） |
| `door_breaker_guard_off` | [door_breaker_guard.md](door_breaker_guard.md) 第 7 节 | 保持，收臂 0.40 s | 右臂（r_arm，25） | 同上（叠在 `_off` 三式上） | 6 / 31 | 全部适用项通过（单臂覆盖层判据） |

GIF 与接触表：`build/anim-review/door-breaker/<id>/`（粗臂、细臂各一份，20 fps，三视角）；与 `walk`、`run` 上下 / 左右并排的版本在 `build/anim-review/door-breaker/compare/`。

## 本段改动

- 分析器：`IMPACT_WINDOW_IDS`、`FULL_BODY_IDS`、`GRIP_IDS` 加两段；运行时 `AnimationEntry` 的打击时段白名单同步。
- 渲染器：斧子预览道具与叩门接触表时刻。
- `PlayerAnimationAction`：`DOOR_BREAKER_KNOCK` / `DOOR_BREAKER_KNOCK_OFF`（60 tick）。`AnimationPlayer` 不需要改（`full` 且不属于上层规则，整段生效；优先级 24 高于攻击 20）。
- 测试：`AnimationSetContractTest` 打包集合加两段，新增「架势 + 叩门 + 原版挥手时叩门接管全部骨」；切换表加「破门斩」小节，`AnimationTransitionContractTest` 有向边 439 → 443；Gradle `checkAnimationGates` 加两段。

## 叩门持物修正（2026-10-28，只改预览与自查，动作关键帧未动）

- **核对依据**：游戏里斧子由 `PlayerMeshRenderer.item` 挂在 `r_item`/`l_item`（`Rig.itemTransform`：挂点矩阵 → `toVanilla` → 平移到挂点 → `rotX(−90°)·rotY(180°)`），再走原版 `handheld` 的 `thirdperson_righthand`（rotation `[0,−90,55]`、translation `[0,4,0.5]`、scale 0.85）。左手 display `[0,90,−55]` 经 `Transformation.apply` 左手取反后与右手相同，物品又在挂点局部 X=0 平面内，主副手几何一致。两段动画都没有 `r_item`/`l_item` 关键帧，斧子与手掌刚性相连，**不存在“部分帧手骨朝向与挂点对不上”**。
- **渲染器的问题**：旧预览把斧柄画在贴图对角线上（剑的端点 `(0,2.2,5.5)`→`(0,−1.2,−13.5)`），而 `iron_axe.png` 的木柄偏离对角线 1.5 贴图像素：换算后柄中线比旧画法偏 0.57 px、柄尾多画 1.84 px（旧图柄尾从拳背伸出 1.4 px，游戏里并不伸出），斧头也画远了约 2.8 px。已按贴图逐像素换算改为 `AXE_GRIP_CENTER`/`AXE_ALONG`/`AXE_HANDLE_S`/`AXE_HEAD`（`scripts/render_animation_gif.py`），柄切成约 2 px 小段分别排深度，穿过拳的部分才会被手指正确遮住。
- **自查**（原型 `grip_check.py`，`run.py` 每次跑，主副手都跑）：拳心取四指卷绕的握持轴（`*_fingers_a` 枢轴，掌宽中线、手掌块下沿）；柄中线到拳心的距离，3D 与 front/side/three_quarter 投影逐帧（60 Hz）算，门限 0.5 px（模型像素）。结果：主手 3D 最大 0.17 px，投影最大 0.17 px；副手相同。另报到拳块体积质心的距离恒为 0.64 px——四指卷向掌心一侧，是骨架握法决定的，转腕改变不了。
- **柄尾打击**：原版握点下柄尾端在挂点局部 `(0, 2.45, 3.59)`，**埋在拳内 0.42 px，不伸出**；设计稿用来对准门的“柄尾点” `(0,2.2,5.5)` 在真实柄尾外 1.84 px，叩到门的实际是拳背。未改打击面，待审阅定。
- **新发现（未改）**：按真实斧头几何，副手版斧头（斧背一侧）插进头盒，最深 1.51 px（t≈2.92），时段 0–0.33、0.62–0.78、0.90–1.15、1.72–2.40、2.80–3.0 s；t=0 与 t=3.0 是共用架势的左护手位，架势本身就会让副手斧头贴脸。主手版最小间隙 0.22 px，不穿。

## 叩门打击面返工（2026-10-29）

- 用户决定打击面改回斧背、腕顶限位、接受偏差约 20°。按真实斧子几何复核（原型 `probe_back3.py`）后发现：原版 `handheld` 握法下斧背法线约沿前臂指回肘（`r_item` 局部 `(0, 0.985, −0.174)`），斧背朝前就要求肘在拳前或与拳齐平。
- 第 8 节旧探针的“腕各分量限 60° / 90°”解，手腕实际弯折 113°～172°（拳折回贴着前臂），欧拉分量上限挡不住。按已交付动画的实际上限（弯折最大约 75°：`drink` 71.7°、叩门 68°；扭转约 90°）约束后，斧背只能到胸前约 5 px，偏差 23°～25°，大臂抬起、拳收回肩前、斧头横垂胸前，不像叩门。
- 方案对比图：`build/anim-review/door-breaker/knock-rework/axe-back-options.png`。叩门两段的 `.bbmodel` 关键帧、导出 JSON 和索引仍是柄尾版，未改动。
- **用户决定（2026-10-29）**：改用斧刃。单手、斧刃朝前、小臂短促连敲三下、斧子抬到肩高即落；不翻腕、不换握、不改 PA 接口；服务端数值和钝击语义不变。和破门靠单手轻敲对双手全身重劈区分。两页规格已按此重写，**待审**；柄尾版原稿备份在 `~/Code/Kizuna-remote-session/handoff/kpa-door-breaker-proto/out/spec-pommel-2026-10-28/`。

## 第 2 项：架势左护手位让头（2026-10-29，已完成）

- 左腕旋后 31°、拳外移 0.5 px，副手斧子在 `stance_idle` 里离头盒 2.6 px（原来插入 1.2 px）。
- 22 段战斗动作全部用新基准重新生成，只有左臂三条轨道变；离开护位的左手手势保持验收原样。
- 衔接逐对复查，门禁全部重跑：74 段、148 份报告通过；`./gradlew test checkAnimationGates checkAnimationAssets checkAnimationAntiForgery` 通过。
- 细节、仍插入的段落和产物路径见 [战斗架势总览](PROGRESS-combat-stance.md) 第 6 节。日志：`~/Code/Kizuna-remote-session/handoff/logs/anim-stance-guard.log`（Gradle）、`anim-stance-guard-gates-all.json`（全量门禁 JSON）。
- 下一步（已完成，见下节）：用新架势一次性求解并录入叩门主手、副手两段。

## 第 3 步：叩门斧刃版录入（2026-10-29）

- 在新架势上用 `d_knock.py` 一次性求解主手、副手两段，`install.py` 写入 `.bbmodel` 并导出；索引条目、`PlayerAnimationAction`、白名单都不变。
- 门禁：粗臂、细臂全部适用项通过；`./gradlew test checkAnimationGates checkAnimationAssets checkAnimationAntiForgery` 通过（测试 91 个，导出一致性 79 段）。
  日志：`~/Code/Kizuna-remote-session/handoff/logs/anim-door_breaker_knock.log`、`anim-door_breaker_knock-gates.json`。
- 与设计值的出入和实测见 [door_breaker_knock](door_breaker_knock.md) 第 11 节、[door_breaker_knock_off](door_breaker_knock_off.md)“制作结果”。
- 渲染器：叩门接触表时刻按规格第 10 节更新（对门 0.62、举 0.80 / 1.15、蓄满 1.52）。
- GIF 与接触表：`build/anim-review/door-breaker/<id>/`（粗臂、细臂，20 fps，三视角）；与 `walk`、`run` 并排的在 `build/anim-review/door-breaker/compare/`。

## 破门规格（2026-10-29，已审）

- 两页规格已写：[door_breaker_cleave](door_breaker_cleave.md)、[door_breaker_cleave_off](door_breaker_cleave_off.md)。
- 关键姿势用原型探针（`probe_cleave_legs.py`、`probe_cleave_arms.py` 等）按真实斧子几何与臂长核对过可达。
- 与动作稿的差异：
  - 双手叠握不滑手：原版斧柄在主手拳与斧头之间只容一只拳，横扫、连门、万门一斩同样受限；
  - 斧子举不过头顶，改为双手竖斧举到面前；
  - 击中时柄近水平、刃口朝前下，落在胯高；
  - 后脚提踵更高、不落跟；
  - 副手版躯干多拧。
- 第一轮审阅：第 1、3–8 项同意；举斧要求先探测过右肩的方案 C。
- C（斧头到头右后上方）探测 336 组不可行，规格仍按 A；可行的降级版 C′（右拧 35°、斧竖右颊旁、斜劈过中线）列为待定。
  对比图：`build/anim-review/door-breaker/cleave-raise/cleave-raise-A-vs-C.png`。
- **用户选定 C′**（2026-10-29）：右拧 35°、斧竖右颊旁、左手跨胸握、从右往左斜劈过中线、腰高击中。两页规格按 C′ 改写，第三轮已审。
  - 主手版：举与击中都通过全部自查。
  - 副手版：举斧那一帧探测 432 组，最好只能做到右手离柄 0.46 px、斧离头 0.70 px（低于 1 px），躯干回转只有 15°。
  - 对比图：`build/anim-review/door-breaker/cleave-raise/cleave-cprime-main-vs-off.png`。
  - A 案旧稿备份在 `kpa-door-breaker-proto/out/spec-cleave-*A-2026-10-29.md`。
- 第三轮审过（2026-10-29）：主手全部按规格，腕速超限就提前起腕、不放宽打击时段；副手接受举斧时斧离头 0.70 px、头多偏 16°、躯干回转 15°。

## 破门录入（2026-10-29）

- `d_cleave.py` 一次求解主手、副手两段，`install_cleave.py` 写入 `.bbmodel`、导出并登记索引：
  - `impact_window [3.00, 3.25]`、`unbalanced_windows [[0.60, 0.80], [4.22, 4.45]]`、`phases [[2.95, 3.30]]`；
  - 时间线 `0.85 step_l`、`3.2 hit`、`4.5 step_l`。
- 失衡窗口按实测重心出界帧（两段都是 0.617–0.783 s、4.233–4.433 s）各放宽约 1 帧，比规格占位值 `[[0.55, 0.85], [4.15, 4.50]]` 收窄。
- 第一次交付审阅：门禁、发力链、落点通过，出入第 1–5、8 条接受。退回两处自查：
  - 嵌住段刃口点漂移要 ≤ 0.3 px；
  - 两拳沿柄叠进要 ≤ 0.5 px；左手方向偏差定段、嵌住段要 ≤ 10°，下劈与松手前 ≤ 20°。

### 返修（2026-10-29）

- **嵌住段逐帧锁刃口点**：3.20–3.85 s 每 1/60 s 重解持物臂，刃口点锁在击中位置，晃、拧按样条曲线给柄线。实测漂移主手 0.00、副手 0.01 px。
- **两拳不叠、方向对齐**：
  - 举姿拳往中线、往下挪（主手 0.4 / 0.5 px，副手目标 1.2 / 1.6 px）；
  - 副手版举、定段头多抬 12°，下劈中途头先抬、晚转；
  - 叠握手 1.90 s 才握上（第一次交付 1.70 s）；
  - 刚握上与下劈段沿柄权重加大，下劈到嵌住段叠握手逐帧求解；
  - 副手版嵌住后躯干反震回左拧 6°，撬出后的双手帧放低。
- 返修后自查（主手 / 副手）：
  - 两拳沿柄最大 0.19 / 0.27 px；
  - 方向偏差：定段 5.2° / 5.6°，嵌住段 8.3° / 9.8°，下劈 17.0° / 17.0°，松手前 5.6° / 11.4°；
  - 斧离头最小 1.13 / 0.75 px。
  - 仍超出的：副手版下劈 3.067 s 一帧右手离柄线 0.74 px（柄线距离自查 0.5 px）；副手版刚握上 12.1°（不在定段）。
- 门禁：粗臂、细臂全部适用项通过。`./gradlew test checkAnimationGates checkAnimationAssets checkAnimationAntiForgery` 通过（测试 91 个，导出一致性 81 段）。
  日志：`~/Code/Kizuna-remote-session/handoff/logs/anim-door_breaker_cleave.log`、`anim-door_breaker_cleave-gates.json`。
- 关键帧 1,507 / 1,480（第一次交付 1,360 / 1,328，增加的是嵌住段逐帧锁定），超过设计 900–1,200。
- 其余出入（详见两页规格的制作结果）：提前起腕；副手版 3.09 s 中途帧；副手版右手 4.45 s 松开、4.80 s 斧落回胸前；击中时肘比设计屈。
- 原型改动：
  - 手臂穿模惩罚改为头、胸只罚真实穿入，腰、胯外扩 0.5 px；
  - 新增 `install_cleave.py`、`quickcheck.py`（构建后直接调分析器的峰值错开、角速度、粗 / 细臂穿模，并按段报叠握与斧离头）、`chain_cleave.py`（发力链峰值）、`probe_helper.py` / `probe_raise_grip.py` / `probe_bite.py`（叠握探针）；
  - `compare.py` 改为命令行指定 id。
- GIF 与接触表：`build/anim-review/door-breaker/door_breaker_cleave(_off)/`。与 `walk`、`run` 并排的在 `build/anim-review/door-breaker/compare/door_breaker_cleave(_off)-vs-walk-run-*`。

## 撞门规格（2026-10-29，已审）

- 破门返修审过：嵌住段刃口点 0.00 / 0.01 px，叠握与方向全部达标；副手 3.067 s 一帧 0.74 px 接受。GIF 交用户看。
- 两页规格已写：[door_breaker_ram](door_breaker_ram.md)、[door_breaker_ram_off](door_breaker_ram_off.md)。
- 施放已改为移速 ×0.4、不锁移动（设计稿末尾 2026-10-29）。撞门按原地迈步加冲量设计；验收只看冲量段脚不打滑，主动缓行不要求锁脚。
- 关键姿势（蓄、撞、击中、随）用原型探针 `probe_ram*.py` 按真实斧子几何、臂长核对。与动作稿的差异：
  - 斧的蓄位外摆在右胯外后方（刃口朝前下），不是正对身后；
  - 击中时躯干左转 30°、随时 40°（动作稿 15°、20°），刃口才朝左上；
  - 随的终点停在左前方头高，不收到左肩上方（会穿头 2.5～3.6 px）；
  - 下沉到随段前脚是前脚掌着地（全掌时前小腿穿地）；
  - 撞时左肘屈约 135°～140°；
  - 副手版右肩撞、蓄势左转 60°。
- 需要的 PA 改动（已同意，已做）：打击时段、失衡窗口、爆发窗口白名单加撞门；`slide_window` 契约说明加撞门；`FULL_BODY_IDS`、`GRIP_IDS`、`PlayerAnimationAction`、合同测试、Gradle 任务。
- 规格审过：第 9 节 1–7 条全部同意。

## 撞门录入（2026-10-29）

- `d_ram.py` 一次求解主手、副手两段，`install_ram.py` 写入 `.bbmodel`、导出并登记索引：
  - `impact_window [2.25, 2.45]`、`burst_windows [[1.50, 1.58]]`、`slide_window [1.55, 2.25]`、`phases [[1.45, 1.65], [2.20, 2.50]]`；
  - `unbalanced_windows` 按实测：主手 `[[1.60, 1.78], [1.83, 2.03]]`、副手 `[[1.83, 2.03], [3.33, 3.60]]`；
  - 时间线 `1.8 step_r`、`2.05 step_l`、`2.4 hit`、`3.65 step_r`。
- PA 改动：分析器 `FULL_BODY_IDS`、`GRIP_IDS`、`IMPACT_WINDOW_IDS`、`UNBALANCED_WINDOW_PREFIXES`、`BURST_WINDOW_PREFIXES` 加撞门；运行时 `AnimationEntry` 打击时段白名单；`PlayerAnimationAction.DOOR_BREAKER_RAM` / `_OFF`（100 tick）；`AnimationSetContractTest` 打包集合与破门斩整段生效测试；切换表加 `stance_idle` ↔ 撞门两行（`AnimationTransitionContractTest` 有向边 447 → 451）；Gradle `checkAnimationGates`；渲染器接触表时刻与斧子道具。
- 门禁：粗臂、细臂全部适用项通过。`./gradlew test checkAnimationGates checkAnimationAssets checkAnimationAntiForgery` 通过（测试 91 个，导出一致性 83 段）。
  日志：`~/Code/Kizuna-remote-session/handoff/logs/anim-door_breaker_ram.log`、`anim-door_breaker_ram-gates.json`。
- 自查（主手 / 副手）：斧离头最小 1.41 / 1.47 px；腕弯折 45.9° / 48.2°、扭转 42.9° / 42.4°；击中刃口与规格差约 2° / 5°；冲量段着地踝 Z 向漂移 ≤ 0.033 px；肘最小 39.6° / 50.9°（不反关节）。
- 主要出入（详见两页规格的制作结果）：
  - 下沉 1.8 px（规格 2.0），后脚提踵 −38°、脚趾再背伸，蹬地以向上为主：这副骨架脚只有约 1 px 长，提踵抬不高踝，后小腿下角会压地；
  - 前脚绕脚尖转（脚跟抬 14°），3.40 s 后才落回全掌；
  - 后脚跟外转没做（会超滑动门禁）；
  - 撩加 2.33 s 中途帧（上臂先前屈），前臂不扫过腰；
  - 蹬段 `phases` 声明了，但门禁按规则跳过（躯干、手臂链在该段不足 3 根骨动 ≥ 3°）；
  - 撞门 → 叩门承接边没加（切换回归不能从剪辑中途起播）；
  - 关键帧 1,470 / 1,469，超过设计 900–1,200。
- 原型：新增 `d_ram.py`、`install_ram.py`、`quick_ram.py`（构建后直接调分析器完整 report）、`diag_ram*.py`、`probe_ram*.py`。
- GIF 与接触表：`build/anim-review/door-breaker/door_breaker_ram(_off)/`，与 `walk`、`run` 并排的在 `compare/door_breaker_ram(_off)-vs-walk-run-*`。

## 破盾规格（2026-10-29）

- 两页规格：[door_breaker_hook](door_breaker_hook.md)、[door_breaker_hook_off](door_breaker_hook_off.md)。
- 资源 id 定为 `door_breaker_hook` / `door_breaker_hook_off`（与目录 presentation id、动作稿、Human 映射一致；交接原写 `door_breaker_shield_break`）。命中时刻一致：目录 `weapon_strike.at_millis` 2,050 ms = `hit` 2.05 s。目录没改。
- 区分：单手、先轻后重，全套唯一往回拉、唯一撤步后坐的式。
- 关键姿势（探、钩、拽）用原型探针 `probe_hook.py` 按真实斧子几何、臂长核对，主手、副手都可达，手臂穿模 0。与动作稿的差异：
  - 探到肩下（上臂前屈 67°），柄前倾 20° 让开头（动作稿 140°，过头穿头）；
  - 躯干：探左转 24.5° 送右肩，钩再左转 8°，拽右转回 64° 加后坐（动作稿只写后仰）；
  - 空手全程握拳：推掌要腕约 `(90, 30, 90)`，够不到；
  - 拽时胯只下沉 0.8 px、后脚保持前脚掌着地（脚跟放低时后小腿穿地）；
  - 拽时柄放平到上仰约 23°，肘才能屈到 115°；
  - 副手版探、钩只右转 10°、17.5°，回转 38.5°，拽的后坐收小、前腿近乎伸直。
- 需要的 PA 改动（已同意）：打击时段、失衡窗口白名单，`FULL_BODY_IDS`、`GRIP_IDS`，`PlayerAnimationAction`（90 tick），合同测试、Gradle 任务、接触表时刻。
- 规格审过：第 9 节 1–7 条全部同意。

## 破盾录入（2026-10-30）

- `d_hook.py` 一次求解主手、副手两段，`install_hook.py` 写入 `.bbmodel`、导出并登记索引：
  - `impact_window [1.85, 2.05]`、`phases [[1.80, 2.10], [2.05, 2.60]]`；没有冲量，不登记 `burst_windows`、`slide_window`；
  - `unbalanced_windows [[0.60, 0.75], [2.21, 2.35], [3.03, 3.15]]`：按实测重心出界帧（0.617–0.733、2.233–2.333、3.05–3.133 s）各放宽约 1 帧，主副手、粗细臂相同，比规格占位收窄；
  - 时间线 `0.8 step_r`、`2.05 hit`、`2.4 step_r`、`3.2 step_r`（后撤落地由规格 2.35 推后到 2.40）。
- 原型做法改了：每个通道写成最小抽搐「移动」序列，按 60 Hz 采样，再在导出插值贴合的前提下删减关键帧。稀疏设计关键帧直接导出时，Catmull-Rom 在长短段相邻处速度跳变，抽搐和峰值顺序都不过。
- PA 改动：
  - 分析器 `FULL_BODY_IDS`、`GRIP_IDS`、`IMPACT_WINDOW_IDS`、`UNBALANCED_WINDOW_PREFIXES` 加破盾；运行时 `AnimationEntry` 打击时段白名单同步；
  - `PlayerAnimationAction.DOOR_BREAKER_HOOK` / `_OFF`（90 tick）；
  - `AnimationSetContractTest` 打包集合与破门斩整段生效测试；切换表加 `stance_idle` ↔ 破盾两行（`AnimationTransitionContractTest` 有向边 451 → 455）；
  - Gradle `checkAnimationGates`；渲染器接触表时刻与斧子道具；
  - 契约与验收文档的白名单说明。
- 门禁：粗臂、细臂全部适用项通过。`./gradlew test checkAnimationGates checkAnimationAssets checkAnimationAntiForgery --rerun-tasks` 通过（测试 91 个，导出一致性 85 段）。
  日志：`~/Code/Kizuna-remote-session/handoff/logs/anim-door_breaker_hook.log`、`anim-door_breaker_hook-gates.json`。
- 自查（主手 / 副手）：
  - 斧离头最小 1.38 / 2.80 px；
  - 击中刃口夹角 0.4° / 0.3°，斧刃下角离目标 0.06 / 0.05 px；
  - 钩住到拽到位斧刃下角回升 0.11 / 0.09 px，不回退；
  - 腕弯折 46.2° / 39.6°、扭转 13.8° / 35.8°（副手扭转最大值在 0 s，即架势护手位）；
  - 柄线过拳心 0.17 px；肘最小 27.7° / 27.6°。
- 主要出入（详见两页规格的制作结果）：
  - 后撤步 2.14–2.40 s；外撤、后撤落地和收回起步前后脚跟放低（−28° / −18°），后小腿才不压地；
  - 调整段胯升高 0.30 px；
  - 拽段腰、胸、持物臂提前，加压高修正：按设计时刻躯干后仰会把斧子抬起，斧刃下角回升 0.91 px（上限 0.3）；
  - 胯回转单独提前到 2.08–2.42 s（副手 2.06–2.40），拽段仍是胯 → 腰 → 胸依次回转。只提前腰、胸的那一版胯峰值落在后面，门禁主链不含胯，没报；
  - 副手版拽住时胯只再后移 0.08 px（前小腿抽搐超限）；
  - 空手前顶的肘 59° / 51°（规格约 45° / 37°，拳位同规格）；
  - 破盾的承接边没加（切换回归不能从剪辑中途起播）；Human 映射没改。
- 原型：新增 `d_hook.py`、`install_hook.py`、`quick_hook.py`（构建后直接调分析器完整 report 并跑自查）、`diag_hook.py`、`com_hook.py`（重心出界帧）、`trace_beard.py`（斧刃下角轨迹）、`probe_hook.py`。
- GIF 与接触表：`build/anim-review/door-breaker/door_breaker_hook(_off)/`，与 `walk`、`run` 并排的在 `compare/door_breaker_hook(_off)-vs-walk-run-*`。
- 用户 2026-10-30 看 GIF 验收通过。

## 横扫规格（2026-10-30，已审）

- 两页规格：[door_breaker_sweep](door_breaker_sweep.md)、[door_breaker_sweep_off](door_breaker_sweep_off.md)。
- 资源 id `door_breaker_sweep` / `_off`，与目录 presentation id 一致。命中时刻一致：目录 3,350 / 3,700 ms = 动作稿 3.35 / 3.70 s。目录没改。
- 关键姿势（下垂、预摆前荡 / 击中、蓄、随）用原型探针 `probe_sweep3.py` 按真实斧子几何、臂长核对，主手、副手都可达，手臂穿模 0，斧离头盒 ≥ 4.8 px。
- 与动作稿最大的不同：**改为单手抡斧**。
  - 双手握柄时斧刃领不了转：两臂联合求解，副手离柄 5.5–12 px 或腕扭转 60°～110°，手臂插进躯干 2.3–5.8 px，斧离转轴只有 7–10 px（探针 `probe_sweep.py`、`probe_sweep2.py`）；
  - 头顶绕斧穿头，预摆改在身侧前后荡；
  - 目录两次打击都是 `can_two_hand: true`，动作稿也写双手：是第 9 节第 1 条，要审阅方定。
- 区分：全套唯一转整圈、单脚为轴、斧走一整圈、手臂伸直不发力；起手 3.35 s 最长。
- `root`：只在右脚悬空时转（3.00–4.25 s，右脚 3.00–4.30 离地），平滑起停、峰值 553°/s，绕左踝竖线平移补偿；4.30 s 用 pre / post 换回 0°。副手版向右转，转轴仍是左脚。
- 不登记打击时段（全段在 720°/s 内）。时间线只标末击 `3.7: hit`，同叩门先例和 Door-Breaker `DoorBreakerAnimationTimingTest` 的口径。
- 需要的 PA 改动（已同意）：
  - 导出器接受两个 `data_point` 并换号；
  - 采样器在帧左侧取 `pre`；
  - 分析器角速度、抽搐按折回的角度差算，曲线相关性先展开角度；
  - 白名单、`PlayerAnimationAction`（120 tick）、合同测试、Gradle 任务、契约与文档。
  - Java 运行时不改。
- `root` 回旋没在游戏里验证过。
- 规格审过（2026-10-30），主手、副手两页都通过，第 9 节九条：
  1. 选甲：单手抡斧、空手张开配重；目录不改，不探丙；
  2. `root` 3.00–4.25 s 平滑起停、峰值 553°/s、绕左踝平移补偿，同意；
  3. 不登记打击时段，同意；
  4. PA 改动全部同意（导出器两个 `data_point`、采样器帧左侧取 `pre`、分析器折回与展开、白名单、`PlayerAnimationAction`、合同测试、Gradle、契约与文档），Java 运行时不改；
  5. 起手被打断时 `root` 倒转接受；「`root` 淡出走最短角度」记入整合待办（下文「未做」），本会话不做；
  6. `root` 游戏内验证归整合录制；
  7. 失衡窗口按实测登记；
  8. 副手版向右转、仍以左脚为轴、躯干绝对角镜像、击中落后 16°，同意；
  9. `hit` 只标 3.7 末击，同意。

## 横扫录入（2026-10-30，已验收）

- `d_sweep.py` 一次求解主手、副手两段（先做主手版、门禁全过后用同一原型生成副手版），`install_sweep.py` 写入 `.bbmodel`、导出并登记索引：
  - `phases [[2.90, 3.40]]`；不登记 `impact_window`、`burst_windows`、`slide_window`；
  - `unbalanced_windows` 按实测重心出界帧（粗、细臂并集）各放宽约 1 帧：
    - 主手 `[[0.60, 0.77], [3.05, 3.39], [3.45, 3.57], [3.63, 3.72], [3.81, 3.92], [4.20, 4.25]]`；
    - 副手 `[[0.60, 0.77], [3.05, 3.09], [3.27, 3.37], [3.48, 3.54], [4.13, 4.25]]`；
  - 时间线 `0.8 step_r`、`3.7 hit`、`4.3 step_r`；
  - `root`：3.00–4.25 s 转 ∓360°，4.30 s 跳变帧（`.bbmodel` 两个 `data_point`，导出为 pre ∓360° / post 0°），绕左踝平移补偿。
- PA 改动（已同意，已做）：
  - 导出器接受两个 `data_point`；采样器帧左侧取 `pre`；分析器角度差折回、相关性先展开；`FULL_BODY_IDS`、`GRIP_IDS`、`UNBALANCED_WINDOW_PREFIXES` 加横扫；
  - `PlayerAnimationAction.DOOR_BREAKER_SWEEP` / `_OFF`（120 tick）；
  - `AnimationSetContractTest` 打包集合与破门斩整段生效测试；切换表加 `stance_idle` ↔ 横扫两行（`AnimationTransitionContractTest` 有向边 455 → 459）；
  - Gradle `checkAnimationGates`；渲染器接触表 22 个时刻与斧子道具；
  - 契约写明 `root` 用法、跳变帧的两个 `data_point`、失衡窗口白名单；验收文档加第五式。
  - Java 运行时没改（运行时 `AnimationEntry` 的打击时段白名单不用加）。
- 门禁：粗臂、细臂全部适用项通过。`./gradlew test checkAnimationGates checkAnimationAssets checkAnimationAntiForgery --rerun-tasks` 通过（测试 91 个，导出一致性 87 段）。
  日志：`~/Code/Kizuna-remote-session/handoff/logs/anim-door_breaker_sweep.log`、`anim-door_breaker_sweep-gates.json`。
- 自查（主手 / 副手）：
  - 斧离头盒最小 1.44 / 2.81 px；斧插入躯干 0 / 0.02 px；
  - 击中斧头点高 13.0 / 11.3 px、半径 18.6 / 14.6 px、刃口切向误差 1.0° / ≤ 0.1°；
  - 世界方位：3.35 s −1.5° / +9.8°（正前），3.70 s 160.4° / 173.5°（正后，±30° 内）；
  - 3.00–4.30 s 左踝世界漂移 0.099 / 0.097 px；
  - 右脚离地和 `root` 起转同在 3.00 s：起转第一帧（3.017 s）`root` 只转了 0.01°，右脚底 +0.009 / −0.025 px，之后右脚不着地；4.25–4.30 s `root` 停在 ∓360°，4.30 s 才落脚；
  - 腕弯折 49.0° / 48.9°、扭转 43.1° / 43.0°；肘最小 0°；
  - 柄线过拳心三维 0.17 px；主手三分之四视角读数 0.89 px 是斧柄正对镜头时检查改量点距的假象（主手页第 11 节第 7 条），姿势没改。
- 主要出入（详见两页规格的制作结果）：
  - 胯位置收小：前荡 `(0.6, −0.4, −1.2)`、蓄 `(0, −0.3, −0.6)`；移向转轴推后到 3.00–3.22 s；3.90–4.35 s 先收到 `(0, 0, −0.6)`。按设计值后小腿压地 −0.596 px；
  - 后腿膝内扣 −25°（外撤步、起转悬空时扣上、放开）；
  - 空手挂点随胯平移（不平移时左前臂插腰 2.3–2.6 px）；
  - 主手蓄的上臂 X 加 1.5°，起转段门禁主链才计入上臂；
  - 副手前荡上臂提前、腕推后（不改时斧柄尾插胯 0.32 px）；
  - 回旋段重心离左踝 1.9–3.7 px，没达到设计 1.8 px，按实测登记失衡窗口；
  - 关键帧 1,342 / 1,348，比设计 1,400–1,800 少；
  - 承接边没加；Human 映射没改。
- 原型：新增 `d_sweep.py`、`install_sweep.py`、`quick_sweep.py`（构建后直接调分析器完整 report 并跑自查）、`com_sweep.py`（重心出界帧）；规格阶段的探针 `probe_sweep.py`、`probe_sweep2.py`、`probe_sweep3.py`。
- GIF 与接触表：`build/anim-review/door-breaker/door_breaker_sweep(_off)/`，与 `walk`、`run` 并排的在 `compare/door_breaker_sweep(_off)-vs-walk-run-*`。
- `root` 回旋只做了离线验证，游戏内（实体朝向、碰撞、第一人称、观察者）归整合录制。
- 用户 2026-10-30 看接触表验收通过：放斧 → 前荡 → 回拉拧紧 → 起转 → 正前击 → 背身回旋 → 正后击 → 落脚 → 收势连贯；七条出入与失衡窗口按实测登记接受；柄线三分之四视角读数 0.89 px 的解释接受；「未做」里横扫的两条整合待办认可。

## 掩门规格（2026-10-30，已审）

- 两页规格：[door_breaker_close](door_breaker_close.md)、[door_breaker_close_off](door_breaker_close_off.md)。规格阶段没动关键帧、`.bbmodel` 和 PA 代码；制作见下文「掩门录入」。
- 资源 id `door_breaker_close` / `_off`，与目录 presentation id 一致。命中时刻一致：目录 2,600 ms = 动作稿 2.60 s。目录没改。
- 关键姿势（预备、推掌、撤步、蓄、预击、击中、随、落闩）用原型探针 `probe_close_keys.py` 按真实斧子几何、臂长核对，主手、副手都可达：手臂穿模 0，斧不插进躯干、腿，斧离头盒 ≥ 2.0 px；`probe_close_path.py` 粗算上撩的刃口领先；候选与否定结论在 `probe_close.py`。
- 区分：单手（目录 `can_two_hand: false`）、空手张开成掌（先推后按）、先轻后重；全套唯一**由下往上**发力（深蹲后坐、后腿蹬伸、胯升 1.9 px，斧头点由膝高升到肩高）、唯一按输入方向撤步。不过头：拳最高 y 22.3，低于架势持物位。
- 与动作稿最大的不同：
  - 蓄的刃口只能朝下后、斧头最低到膝高（原版握法刃口顺前臂）；
  - 蓄直接插值到击中一路斧背领先，所以上撩拆成翻斧（2.33–2.50 s）和顶（2.50–2.60 s，加预击姿势，拳沿前臂向前上顶 4 px）：顶段刃口领先，夹角主手 3.5°～17°、副手 12.5°～20.5°；
  - 随不过头（斧头停在头侧前下方）；左掌按斧头上面，不是斧背；推掌峰值 0.78 s；落闩只收后脚。
- 索引：`impact_window [2.31, 2.65]`（肘、腕超过 720°/s）；不登记 `burst_windows`；`unbalanced_windows` 占位 `[[0.78, 1.32], [3.18, 3.47]]`（制作按实测改）；`slide_window [0.75, 1.40]`；`phases [[2.45, 2.65]]`（副手腰只转 4°，主链按胸 → 上臂 → 肘 → 腕计）。
- 副手版躯干按相对架势的增量镜像（绝对角镜像的蓄压地 −0.31 px），推掌左转 24°、击中右转 10°、拳前移到 z −11.5 另求。
- 需要的 PA 改动（已同意）：打击时段白名单（分析器与运行时 `AnimationEntry`）、失衡窗口白名单、`FULL_BODY_IDS`、`GRIP_IDS`、`PlayerAnimationAction`（100 tick）、合同测试（有向边 459 → 463）、Gradle 任务、渲染器、契约（含 `slide_window` 那句）与文档。导出器、采样器、Java 运行时逻辑不改。
- 规格审过（2026-10-30），主手、副手两页都通过。主手页第 9 节十一条：
  1. 选甲（翻斧 + 顶），不探乙；
  2. 蓄的斧头高度与刃口朝向按探针结果；
  3. 推掌峰值 0.78 s、保持到 1.10 s，同意；
  4. 随不过头，同意；
  5. 按斧头上面，同意；
  6. 落闩只收后脚，同意；
  7. 索引字段按本稿：打击时段 `[2.31, 2.65]`、不登记爆发窗口、失衡窗口按实测、`phases` 只登记顶段；
  8. PA 改动全部同意，Java 运行时不改；
  9. 不做覆盖层，同意；
  10. 动画只做向后撤、冲量方向交给目录，同意；四向撤步不做；
  11. 承接边等接线时再加，同意。
- 副手页第 3 节六条出入全部接受。

## 掩门录入（2026-10-30，已验收）

- `d_close.py` 一次求解主手、副手两段（先做主手版、门禁全过后用同一原型生成副手版），`install_close.py` 写入 `.bbmodel`、导出并登记索引：
  - `impact_window [2.31, 2.65]`、`slide_window [0.75, 1.40]`、`phases [[2.45, 2.65]]`；不登记 `burst_windows`；
  - `unbalanced_windows` 按实测重心出界帧（粗、细臂并集）各放宽约 1 帧：
    - 主手 `[[0.83, 0.93], [1.05, 1.27]]`（落闩收脚没出界）；
    - 副手 `[[0.83, 0.93], [1.05, 1.27], [3.27, 3.40]]`（落闩收脚 3.283–3.383 s 出界）；
  - 时间线 `0.95 step_r`、`1.3 step_l`、`2.6 hit`、`3.45 step_r`。
- PA 改动（已同意，已做）：
  - 分析器 `IMPACT_WINDOW_IDS` 与运行时 `AnimationEntry` 打击时段白名单加两段（只加白名单）；`FULL_BODY_IDS`、`GRIP_IDS`、`UNBALANCED_WINDOW_PREFIXES` 加掩门；
  - `PlayerAnimationAction.DOOR_BREAKER_CLOSE` / `_OFF`（100 tick）；
  - `AnimationSetContractTest` 打包集合与破门斩整段生效测试；切换表加 `stance_idle` ↔ 掩门两行（`AnimationTransitionContractTest` 有向边 459 → 463）；
  - Gradle `checkAnimationGates`；渲染器接触表 21 个时刻与斧子道具；
  - 契约：打击时段白名单、`slide_window` 「当前为」那句、失衡窗口白名单；验收文档加第六式。
  - 导出器、采样器和 Java 运行时逻辑没改。
- 门禁：粗臂、细臂全部适用项通过。`./gradlew test checkAnimationGates checkAnimationAssets checkAnimationAntiForgery --rerun-tasks` 通过（测试 91 个、失败 0，导出一致性 89 段）。
  日志：`~/Code/Kizuna-remote-session/handoff/logs/anim-door_breaker_close.log`、`anim-door_breaker_close-gates.json`。
- 门禁读数（主手 / 副手）：
  - 主链峰值：腰 2.467 → 胸 2.483 → 上臂 2.517 → 肘 2.550 → 腕 2.567 s / 胸 2.483 → 上臂 2.517 → 肘 2.550 → 腕 2.567 s（副手腰不到 3°，门禁跳过）；
  - 角速度：时段内最大 876.8 / 1,019.9°/s，时段外 713.9 / 596.8°/s（主手左上臂 3.35 s 按斧，贴着 720°/s）；
  - 脚底最低 −0.193 / −0.225 px；支撑脚滑动 ≤ 0.403 px；手臂穿模 0。
- 自查（主手 / 副手）：
  - 斧离头盒最小 1.37 / 1.92 px（都在收段；0 s 架势本身主手 1.44 px）；斧插入躯干、腿 0；
  - 击中刃口仰角 33.7° / 32.6°、偏角 1.2° / 2.0°；
  - 刃口领先（斧头点速度与刃口夹角）：击中前 0.05 s 内最大 29.2° / 36.9°，没超过 45°；副手 2.53 s（击中前 0.07 s）47.4°，在自查范围外；
  - 落闩掌心离斧头上面 ≤ 0.03 px；持斧腕弯折 46.4° / 46.6°、扭转 41.2°；肘最小 0.2° / 10.6°；推掌腕弯折 70.3°；
  - 柄线过拳心三维 0.17 px；投影读数主手侧面 0.85 px、副手正面 1.02 / 侧面 0.62 / 三分之四 0.66 px，都是斧柄正对镜头的帧（投影长度 < 0.2），同横扫的解释，姿势没改。
- 主要出入（详见两页规格的制作结果）：
  - 空手预备、起推提前到 0.40 / 0.42 s（按设计 0.50 s 起推左上臂 966°/s），推掌峰值 0.78 s 不变；
  - 上撩分骨起止：顶段上臂、肘、腕同在 2.60 s 收住，起点 2.47 / 2.48 / 2.50 s 错开（止点错开时刃口落后 80° 以上）；翻腕收到 2.45 s；
  - 收段加 4.35 s 中途姿势、腕先起：直接插值时斧头从脸前擦过，插进头盒 2.0 px；
  - 后撤位抬 0.06 px（副手后脚趾压地 −0.274 px）；
  - 副手：回收右拳 z −9.5、按斧掌 3.14 / 3.16 s 起、顶段腕 2.51 s 起、击中段胸 2.37–2.55 s；
  - 关键帧 1,577 / 1,527，主手比目标 1,200–1,500 多约 5%；
  - 承接边没加；Human 映射没改；不做覆盖层。
- 原型：新增 `d_close.py`、`install_close.py`、`quick_close.py`、`com_close.py`；规格阶段的探针 `probe_close.py`、`probe_close_keys.py`、`probe_close_path.py`。
- GIF 与接触表：`build/anim-review/door-breaker/door_breaker_close(_off)/`（21 个时刻），与 `walk`、`run` 并排的在 `compare/door_breaker_close(_off)-vs-walk-run-*`。
- 只做了离线验证；游戏内效果（含冲量下撤步滑行、按其他方向输入时的撤步）没验证。
- 用户 2026-10-30 看接触表验收通过：推掌 → 连撤两步 → 深蹲藏斧 → 翻斧上顶 → 横斧落闩按掌 → 收势连贯；五条出入和四条提请注意的读数都接受（刃口领先 29° / 37° 在 45° 内；柄线投影读数是镜头假象；时段外 713.9°/s 在限内）；整合待办照旧。

## 连门规格（2026-10-30，已审）

- 两页规格：[door_breaker_chain](door_breaker_chain.md)、[door_breaker_chain_off](door_breaker_chain_off.md)。规格阶段没动关键帧、`.bbmodel` 和 PA 代码；制作见下文「连门录入」。
- 资源 id `door_breaker_chain` / `_off`，与目录 presentation id 一致。命中时刻一致：目录 1,700 / 3,200 / 5,000 ms = 动作稿 1.70 / 3.20 / 5.00 s。目录没改。
- 关键姿势（预备、蓄一、击一、低一、蓄二、击二、低二、蓄三、击三、随）用原型探针 `probe_chain_keys.py` 按真实斧子几何、臂长核对，主手、副手都可达：手臂穿模 0，斧不插进躯干、腿，关键姿势斧离头盒主手 ≥ 2.08 px、副手 ≥ 1.15 px；`probe_chain_path.py` 粗算三刀刃口领先、沿途间隙和角速度；候选在 `probe_chain.py`、`probe_chain_grid.py`，副手偏航扫描在 `probe_chain_offyaw.py`。
- 区分：双手叠握（目录 `can_two_hand: true`）、一刀比一刀重（躯干转幅 29° → 46° → 60°，前两刀原地垫步、第三刀弓步跨 6 px，×1.2 / ×1.2 / ×2）；全套唯一连环三刀、唯一三次冲量，躯干左右来回拧，每刀余势绕 ∞ 字变下一刀的蓄。不过头：拳最高 y 16.8，斧竖在头侧。
- 与动作稿最大的不同：
  - 不换脚（左脚始终在前），往前压交给三次冲量；
  - 反手的蓄在左胸前、斧头在左下巴旁（左肩上方时斧背领先 54° 或贴头）；
  - 第三刀的蓄同第一刀竖在右肩前、拧到右转 58°（右胯后方腰高够不到），横斩斜下约 18° 到腰高，击中刃口朝左下 41°；
  - 右转 24° 放在预备（左手跨胸握柄要先转），蓄一只后移重心、后仰；不滑手；`hit` 只标末击 5.0 s。
- 刃口领先粗算：主手 26.2° / 44.5° / 24.4°，副手 19.3° / 44.2° / 20.8°；击二贴着 45°（腕弯折顶在上限），副手击中前 0.02–0.04 s 47.5°～50.2°。
- 索引：`impact_window [4.78, 5.12]`（只登记第三刀；分析器和运行时只认一段，前两刀按 720°/s 做）；不登记 `burst_windows`；`unbalanced_windows` 占位四段 `[[1.54, 1.70], [3.04, 3.20], [4.80, 4.97], [5.98, 6.37]]`（后脚单撑，制作按实测改，见「连门录入」）；`slide_window [1.55, 5.10]`；`phases` 三段同动作稿。
- 副手版躯干按相对 root 的绝对角镜像（δ 35°～45°，增量镜像时右手跨不过胸握不到柄），与掩门副手版相反；蓄一沿用改前拳位，低二拳位前移 2 px（击二领先 55.9° → 44.2°）。副手版沿途斧离头盒三处不到 1 px（最近 0.56 px，1.55 s），制作时分骨起止、加中途姿势处理。
- 需要的 PA 改动（已同意）：打击时段白名单（分析器与运行时 `AnimationEntry`）、失衡窗口白名单、`FULL_BODY_IDS`、`GRIP_IDS`、`PlayerAnimationAction`（160 tick）、合同测试（有向边 463 → 467）、Gradle 任务、渲染器、契约（含 `slide_window` 那句）与文档。导出器、采样器、Java 运行时逻辑不改。
- 规格审过（2026-10-30），主手、副手两页都通过。主手页第 9 节十四条：
  1. 选甲：左脚始终在前，前两刀垫步、第三刀弓步 6 px；不探乙和甲′；
  2. 第三刀由右肩前竖斧起、斜下 18° 斩到腰高，同意；
  3. 反手蓄在左胸前，同意；
  4. 右转放在预备，同意；
  5. 后膝 100°，同意；
  6. 不滑手；
  7. `hit` 只标 5.0 s 末击；
  8. 打击时段只登记第三刀；「多段打击时段」记进整合待办（运行时改动，以后统一做，见「未做」）；
  9. 索引字段按本稿；
  10. PA 改动全部同意，Java 运行时不改；
  11. 无承接入口、可打断，同意；
  12. 不做覆盖层；
  13、14. 按规格写的规则：击中时刻刃口领先超过 45° 或沿途斧离头盒不到 1 px 做不到时写【待审】停下。
- 副手页第 3 节五条出入全部接受（绝对角镜像、蓄一沿用改前拳位、低二前移 2 px）。

## 连门录入（2026-10-30，已验收）

- `d_chain.py` 一次求解主手、副手两段（先做主手版、门禁全过后用同一原型生成副手版），`install_chain.py` 写入 `.bbmodel`、导出并登记索引：
  - `mask` full、优先级 24、`blend_in` 0.1、`blend_out` 0.25、`fpv` false；
  - `impact_window [4.78, 5.12]`（只第三刀）、`slide_window [1.55, 5.10]`、`phases [[1.50, 1.75], [3.00, 3.25], [4.80, 5.05]]`；不登记 `burst_windows`；
  - `unbalanced_windows` 按实测重心出界帧（粗、细臂并集）各放宽约 1 帧，两版相同：`[[1.57, 1.67], [3.07, 3.17], [4.83, 4.93], [6.05, 6.30]]`（实测出界 1.583–1.650、3.083–3.150、4.850–4.917、6.067–6.283 s）；
  - 时间线 `1.68 step_l`、`3.18 step_l`、`4.95 step_l`、`5.0 hit`、`6.35 step_l`。
- PA 改动（已同意，已做）：
  - 分析器 `IMPACT_WINDOW_IDS` 与运行时 `AnimationEntry` 打击时段白名单加两段（只加白名单）；`FULL_BODY_IDS`、`GRIP_IDS`、`UNBALANCED_WINDOW_PREFIXES` 加连门；
  - `PlayerAnimationAction.DOOR_BREAKER_CHAIN` / `_OFF`（160 tick）；
  - `AnimationSetContractTest` 打包集合与破门斩整段生效测试；切换表加 `stance_idle` ↔ 连门两行（`AnimationTransitionContractTest` 有向边 463 → 467）；
  - Gradle `checkAnimationGates`；渲染器接触表 26 个时刻与斧子道具；
  - 契约：打击时段白名单、`slide_window` 「当前为」那句、失衡窗口白名单；验收文档加第七式。
  - 导出器、采样器和 Java 运行时逻辑没改。
- 门禁：粗臂、细臂全部适用项通过。`./gradlew test checkAnimationGates checkAnimationAssets checkAnimationAntiForgery --rerun-tasks` 通过（测试 91 个、失败 0，导出一致性 91 段，门禁 132 条全部通过）。
  日志：`~/Code/Kizuna-remote-session/handoff/logs/anim-door_breaker_chain.log`、`anim-door_breaker_chain-gates.json`。
- 门禁读数（主手 / 副手）：
  - 主链峰值：
    - 发一 腰 1.583 → 胸 1.617 = 上臂 1.617 → 腕 1.633 s / 腰 1.617 = 胸 1.617 → 上臂 1.633 → 腕 1.650 s；
    - 发二 腰 3.083 → 上臂 3.100 → 胸 3.117 → 腕 3.133 s / 腰 3.083 → 胸 3.100 → 上臂 3.117 → 肘 3.133 → 腕 3.150 s；
    - 发三 胸 4.900 → 腰 4.917 = 上臂 4.917 → 肘 4.933 → 腕 5.050 s / 胸 4.900 → 腰 4.917 → 肘 4.933 → 腕 4.950 s（副手左上臂段内幅度不够，门禁跳过）；
    - 同帧、反序在门禁两帧容差内，规格第 6 节「相隔至少 1 帧」没全做到（主手页第 11 节第 9 条）；
  - 角速度：时段内最大 796.4 / 709.0°/s（左脚，弓步跨出），时段外 646.1 / 517.9°/s；
  - 抽搐：时段内 3.28 / 3.70×10⁶°/s³，时段外 1.41 / 1.37×10⁶°/s³；
  - 脚底最低 −0.229 / −0.213 px；支撑脚滑动 ≤ 0.393 px；手臂穿模 0。
- 自查（主手 / 副手）：
  - 斧离头盒最小 1.10 / 1.04 px，全段没有低于 1 px 的帧；斧插入躯干 0 / 0.09 px（容差 0.25），腿 0；
  - 击中刃口领先：主手 21.0° / 30.5° / 10.9°，副手 19.4° / 29.2° / 11.1°，都在 45° 内；副手击二前 0.06 s 52.2°，在自查的「击中前 0.05 s」之外；
  - 击三刃口朝左下 41.7° / 朝右下 36.7°；
  - 叠握离柄线：主手发一 1.567 s 单帧 0.37 px、副手发一 0.36 px，其余 ≤ 0.33 px；持斧腕弯折最大 48.9° / 49.6°、扭转 41.4° / 41.3°；叠握肘最小 −0.046° / −0.04°（删减关键帧的插值误差）；
  - 柄线过拳心三维 0.17 px；投影读数主手正面 0.98 px、副手正面 1.02 / 三分之四 0.88 px，都是斧柄正对镜头的帧（投影长度 < 0.2），同横扫、掩门的解释，姿势没改。
- 主要出入（详见两页规格的制作结果）：
  - 垫步 1.55 / 3.05 s 离地（设计 1.56 / 3.06 s，前小腿落地三阶导）；
  - 三刀躯干提前起（胯 1.44 / 2.94 / 4.75 s，设计 1.52 / 3.02 / 4.83 s），否则腰、胸峰值晚于上臂；持斧臂分骨起止，腕晚于上臂到（腕与上臂同时到时击二刃口领先 46°～53°）；
  - 胯位置击三停住；后脚弓步时多背屈（脚趾 −50°、脚底 −60°）；
  - 收势持斧在胸前偏右（预备那样竖在右肩前时左手够不到柄），收段加 7.15、7.27 s 中途姿势；
  - 叠握手：换支 3.55–3.85 s、平滑窗口、手指 0.26 s 握紧；主手发一单帧离柄 0.37 px 没再改（够柄会贴头或击一领先 35°）；
  - 副手：叠握手 0.533 s 握上；蓄一、蓄三本身挪离头（2.5 px），加六个挪离头 / 够柄中途姿势；回护手位中途姿势改小；收势拳偏右；
  - 关键帧 2,290 / 2,259，比目标 1,800–2,200 多约 4% / 3%；
  - 承接边没加；Human 映射没改；不做覆盖层。
- 原型：新增 `d_chain.py`、`install_chain.py`、`quick_chain.py`（门禁与自查）、`com_chain.py`（重心出界帧）、`keys_chain.py`（打包资源关键姿势读数）、`lead_chain.py`（刃口领先）；规格阶段的探针 `probe_chain.py`、`probe_chain_grid.py`、`probe_chain_keys.py`、`probe_chain_path.py`、`probe_chain_offyaw.py`。
- GIF 与接触表：`build/anim-review/door-breaker/door_breaker_chain(_off)/`（26 个时刻），与 `walk`、`run` 并排的在 `compare/door_breaker_chain(_off)-vs-walk-run-*`。
- 只做了离线验证；游戏内效果（含三次冲量下的垫步、弓步滑行，三刀之间的服务端结算）没验证。
- 用户 2026-10-30 看接触表验收通过：右肩竖斧 → 正手斜劈 → 左胸反手蓄 → 反手斜劈 → 右肩再蓄 → 弓步横斩 → 收势连贯，三刀与冲量时刻对得上；七条出入和副手改动全部接受；多段打击时段留整合待办。

## 万门一斩规格（2026-10-30，已审）

- 两页规格：[door_breaker_finale](door_breaker_finale.md)、[door_breaker_finale_off](door_breaker_finale_off.md)。规格阶段没动关键帧、`.bbmodel` 和 PA 代码；制作见下文「万门一斩录入」。
- 资源 id `door_breaker_finale` / `_off`，与目录 presentation id 一致。命中时刻一致：目录 `weapon_strike` 4,850 ms = 动作稿 4.85 s。目录没改。
- 关键姿势（预备、回摆、前荡、提斧、定、蹬伸、空、触地、击中、压低、拄斧、收脚、提起）用原型探针 `probe_finale_keys.py` 按真实斧子几何、臂长核对。主手全部可达：
  - 手臂穿模 0，斧不插进躯干、腿；
  - 斧离头盒 ≥ 1.14 px，左拳离柄线 ≤ 0.34 px；
  - 脚底最低 −0.09 px。
- 副手竖斧段（3.60–4.70 s）没有全部核对通过：
  - 定离头盒 0.90 px；
  - 空左前臂插腰 2.42 px（细臂门禁）；
  - 蹬伸、触地右拳离柄 0.36 / 0.37 px。
  - 另试两组定的拳位都没解决（`probe_finale_off_upright.py`），处理办法写在副手页第 3 节第 4 条待审。
- 副手其他待审：
  - 收势回左护手位时斧插头盒 0.27 px，加中途姿势；
  - 承接时躯干在前式淡出里左转 48°（预备 δ 再小右手握不稳）；
  - 触地胯建议降到 −1.5 px（前膝 18° → 38°）。
- 区分：双手叠握；最重（×2.5、4 m 90° 扇形、最多 6 个目标、劈 4 扇门，起手 4.05 s）；全套唯一跳起、唯一劈完虚脱。发力是从空中往下砸：落地下坐、躯干前折 28° → 42°、竖斧劈下叠在一起。
- 与动作稿最大的不同：
  - 不抡圆过头、不收到脑后，斧竖在右肩旁蓄（用户 2026-10-29 决定；右肩后方两手叠握够不到）；
  - 回摆只到右胯旁；
  - 不拄地：静段斧刃离地 1.3 px、拄斧段 2.9 px，双手叠握压在近平的斧柄上；
  - 起跳靠膝（本骨架提踵让踝下降），模型只收腿离地 2–3 px，跳的高度由实体冲量给。
- 粗算（`probe_finale_path.py main`）：
  - 击中刃口领先 12.1°；
  - 打击时段外各骨 ≤ 654°/s，打击时段内上臂 755°/s；
  - 沿途斧离头盒两处不到 1 px（4.75 s 0.37 px、8.05 s 0.35 px）；
  - 预备 → 前荡右前臂插腰（1.65 / 2.15 s）。
  - 这些都留到制作时用中途姿势处理，做不到再写【待审】。
- 索引：
  - `impact_window [4.70, 4.95]`（同动作稿）；
  - 不登记 `burst_windows`（蹬伸放到 0.13 s）；
  - `unbalanced_windows` 占位三段；
  - `slide_window [4.05, 4.85]`；
  - `phases` 两段同动作稿。
- 需要的 PA 改动（待审）：打击时段白名单（分析器与运行时 `AnimationEntry`）、失衡窗口白名单、`FULL_BODY_IDS`、`GRIP_IDS`、`PlayerAnimationAction`（180 tick）、合同测试（有向边 467 → 471）、Gradle 任务、渲染器、契约与文档。
- 主手页第 9 节十六条、副手页第 3 节七条待审。
- 规格审过（2026-10-30），两页都放行制作：
  - 主手页第 9 节十六条全部接受；第 4 条选甲（拄斧段斧刃离地 2.9 px、上身直起一半）；第 5 条选甲（3.95–4.08 s 蹬完，不登记 `burst_windows`）；
  - 副手页第 3 节：第 2 条接受（触地胯降到 −1.5 px，手臂重求）；第 4 条三样都允许（拳位外移 / 下移各 ≤ 1 px、空中后仰 8° → 最少 3°、δ 30°～45°），三样用尽仍不达标写【待审】停下；第 6 条选甲（承接淡出里左转 48°）；
  - 其余照规格。

## 万门一斩录入（2026-10-30，已验收）

- `d_finale.py` 一次求解主手、副手两段（先做主手版、门禁全过后用同一原型生成副手版），`install_finale.py` 写入 `.bbmodel`、导出并登记索引：
  - `mask` full、优先级 24、`blend_in` 0.1、`blend_out` 0.25、`fpv` false；
  - `impact_window [4.70, 4.95]`、`slide_window [4.05, 4.85]`、`phases [[3.95, 4.10], [4.65, 5.00]]`；不登记 `burst_windows`；
  - `unbalanced_windows` 按实测重心出界帧（粗、细臂并集）各放宽约 1 帧，两版相同：`[[1.60, 1.82], [4.63, 4.67], [6.25, 6.50]]`（实测出界 1.617–1.800、4.650、6.267–6.483 s）；
  - 时间线 `1.85 step_l`、`4.7 step_l + step_r`、`4.85 hit`、`6.55 step_l`。
- PA 改动（已同意，已做）：
  - 分析器 `IMPACT_WINDOW_IDS` 与运行时 `AnimationEntry` 打击时段白名单加两段（只加白名单）；`FULL_BODY_IDS`、`GRIP_IDS`、`UNBALANCED_WINDOW_PREFIXES` 加万门一斩；
  - `PlayerAnimationAction.DOOR_BREAKER_FINALE` / `_OFF`（180 tick）；
  - `AnimationSetContractTest` 打包集合与破门斩整段生效测试；切换表加 `stance_idle` ↔ 万门一斩两行（`AnimationTransitionContractTest` 有向边 467 → 471）；
  - Gradle `checkAnimationGates`；渲染器接触表 30 个时刻与斧子道具；
  - 契约：打击时段白名单、`slide_window` 「当前为」那句、失衡窗口白名单；验收文档加第八式。
  - 导出器、采样器和 Java 运行时逻辑没改。
- 门禁：粗臂、细臂全部适用项通过。`./gradlew test checkAnimationGates checkAnimationAssets checkAnimationAntiForgery --rerun-tasks` 通过（测试 91 个、失败 0，导出一致性 93 段，门禁 136 条全部通过）。
  日志：`~/Code/Kizuna-remote-session/handoff/logs/anim-door_breaker_finale.log`、`anim-door_breaker_finale-gates.json`。
- 门禁读数（主手 / 副手）：
  - 主链峰值（落发）：右上臂 4.800 → 腰 4.817 → 胸 4.833 → 右腕 4.850 → 右肘 4.867 s / 腰 4.817 → 胸 4.833 → 左上臂 4.850 = 左腕 4.850 s（副手左肘段内幅度不够，门禁跳过）；同帧、反序在两帧容差内；跃那段两版都因上身动得少被门禁跳过；
  - 角速度：时段内最大 1,104.8 / 1,701.4°/s（持斧手，4.85 s；上限 1,800），时段外 683.0 / 617.1°/s（主手右上臂前荡 / 副手前小腿迎地）；
  - 抽搐：时段内 6.81 / 4.50×10⁶°/s³（上限 7×10⁶），时段外 1.46 / 1.44×10⁶°/s³；
  - 脚底最低 −0.091 / −0.182 px；支撑脚滑动 ≤ 0.478 px；手臂穿模 0。
- 自查（主手 / 副手）：
  - 斧离头盒最小 1.02 / 1.00 px，全段没有低于 1 px 的帧；斧插入躯干、腿 0；
  - 击中刃口领先 6.8° / 14.8°（粗算 12.1° / 12.0°）；
  - 斧最低：静 1.24–1.51 / 1.44–1.70 px（上限 1.5），拄斧 2.86 / 2.75 px；
  - 叠握离柄线：主手荡 2.133 s 0.38 px、副手落发 4.817 s 0.37 px（上限 0.35），其余各段 ≤ 0.34 px；持斧腕弯折最大 48.8° / 49.5°、扭转 41.4° / 41.6°；叠握肘最小 −0.1°（删减关键帧的插值误差）；
  - 柄线过拳心三维 0.17 px；投影读数主手正面 0.81 px、副手正面 0.70 / 三分之四 0.96 px，都是斧柄正对镜头的帧（投影长度 < 0.2），姿势没改。
- 主要出入（详见两页规格的制作结果）：
  - 前脚 4.45 s 起迎地（设计 4.50 s，前膝伸直超速），脚底 4.650 / 4.667 s 进接地容差（设计 4.70 s），与服务端估算的着地约 4.65 s 对上；
  - 定到劈下前持斧臂停在定的一支（不按探针在蹬伸、空、触地换支），拳随身体起落，触地拳比探针低 2.1 px；
  - 劈下前按 4.77 s 躯干再挪离头 2.5 px，持斧臂 4.76 / 4.78 / 4.80 s 起（设计 4.79 / 4.81 / 4.82 s）；击中那一帧斧头点比探针高 2.4 / 3.2 px（腕 4.86 s 才经过击中姿势）；
  - 荡、提斧、腾空、劈下、收势加中途姿势，粗算的插腰和贴头都解决；
  - 叠握手 0.25 s 离开护手位（设计 0.30 s），两次换支（副手三次）与平滑窗口；
  - 喘改为抬胸（设计前倾），否则拄斧斧刃不到 2.9 px；
  - 规格第 3 节躯干表压低胯 X −20° 是笔误，应为 −22°；
  - 副手：只用了第 4 条第 1 样（拳位，往外 ≤ 0.9、往下 ≤ 1.0 px），后仰、δ 没动，竖斧段三项全部达标，没写【待审】；触地胯 −1.5 px；前脚迎地 90% 处到位、后脚腾空只抬 2.2 px、前迈落地脚跟抬 2°；劈下中途叠握点 11.15 px；收势提起本身挪离头、加 7.58 / 8.12 / 8.20 s 中途姿势；
  - 关键帧 2,548 / 2,600，在目标 2,300–2,700 内；
  - 承接边没加；Human 映射没改；不做覆盖层。
- 原型：新增 `d_finale.py`、`install_finale.py`、`quick_finale.py`（门禁与自查）、`com_finale.py`（重心出界帧）、`keys_finale.py`（打包资源关键姿势读数）；制作中的诊断探针 `legs_finale.py`、`arm_finale.py`、`speed_finale.py`、`lead_finale.py`、`bend_finale.py`、`helper_finale.py`、`diag_finale.py`、`peaks_finale.py`；规格阶段的探针 `probe_finale.py`、`probe_finale_grid.py`、`probe_finale_keys.py`、`probe_finale_path.py`、`probe_finale_legs.py`、`probe_finale_off_upright.py`、`probe_finale_off_delta.py`。
- GIF 与接触表：`build/anim-review/door-breaker/door_breaker_finale(_off)/`（30 个时刻），与 `walk`、`run` 并排的在 `compare/door_breaker_finale(_off)-vs-walk-run-*`。
- 只做了离线验证；游戏内效果（两次冲量下的跳起、落地与滑停，触地与实体落地是否对得上，被伤害打断）没验证。
- 用户 2026-10-30 看 GIF 验收通过。

## 持物手覆盖层规格（2026-10-30，已审）

- 规格：[door_breaker_guard](door_breaker_guard.md)（含副手版 `door_breaker_guard_off` 一节）。规格阶段没动关键帧、`.bbmodel`、PA 代码和索引。
- 范围：叠在破门、连门、万门一斩及副手版上（6 段）；横扫按已审规格是单手式，不叠，数据已核（审阅项第 2 条）。
- 护持姿势一套通用（盾、火把、普通物品）：上臂 (−40, 0, 33)、屈肘 70°、腕 (40, −20, −20)，手指等于各式 0 s 握拳；0.40 s 由肩到腕错开收臂，之后保持。
- 与动作稿最大的不同：盾**体侧竖持、盾面朝外**，不是「盾面朝前 / 收胸前」。三式斧路扫过胸前和脸前，盾面前转 16° 起斧就插进盾 2.5 px（需审阅方拍板）。
- 斧柄单手持握：覆盖层只改持物臂，持斧臂和躯干逐帧等于各式单独播放；叠握离柄线、叠握肘、柄线过拳心等叠握手自查对叠覆盖层时不适用。
- 穿模（6 段 × 3 种物品 × 粗 / 细臂，保持段逐帧）：全部留空，最紧是 16×16 满格贴图对胯 / 腰，粗臂 0.62 px、细臂 0.21 px。进场、自然结束、承接都干净；起手被打断时，同刻停在万门一斩 1.7–2.1 s 有约 0.1 s 盾扫进前腿胫 2.0 / 2.7 px，建议宿主在该式淡完后再停覆盖层（审阅项第 4 条）。
- 探针（临时）：`probe_guard.py`、`probe_guard_render.py`、`guard_scan/g5–g22.py`；预览在 `out/guard_preview/`。
- 只做了离线核对；手持工具、真实关键帧下的路径、游戏内效果都没核（前两项制作时已补，见下节）。
- 审阅结论（用户 2026-10-30）：
  1. 选甲：体侧竖持、盾面朝外；动作稿 §8 验收句改为「另一只手持盾时盾收在体侧，不穿斧柄和持斧臂」（写进规格与验收文档，交接目录里的动作稿本身没改）；
  2. 范围就是 6 段，横扫不叠；
  3. 一种姿势通用，接受；
  4. 停法选乙：该式淡完后覆盖层再停，宿主行为写给整合会话；
  5. 第 5–12 条全部接受；第 7 条架势帧持物穿头另记为 `stance_idle` 待办（见「未做」）。

## 持物手覆盖层录入（2026-10-30，已验收）

- 用户 2026-10-30 验收通过。
- 规格第 13 节记全部读数与出入，这里只列要点。
- PA 改动：
  - `.bbmodel` 加两段并导出；索引登记 `l_arm` / `r_arm`、优先级 25、淡入 0.10 / 淡出 0.25、`hold_on_last_frame`；
  - `PlayerAnimationAction.DOOR_BREAKER_GUARD` / `_OFF`（不带 tick，宿主显式停止）；
  - `AnimationSetContractTest` 打包集合，新增叠加回归 `doorBreakerGuardOwnsOnlyTheHoldingArm`（主手、副手版 × 右、左主手玩家，遮罩内等于覆盖层、遮罩外等于破门）；
  - 切换表加三行（`AnimationTransitionContractTest` 有向边 471 → 479），「叠加组合回归」加一条；
  - Gradle `checkAnimationGates` 加两段；
  - 分析器 `ARM_OVERLAY_IDS`：有效运动按覆盖臂三骨计数，错峰链改为覆盖臂，躯干 / 头、预备 / 跟随、主链峰值、着地、握持不适用并写明理由；
  - 契约写入覆盖层与宿主约定；验收文档破门斩批次加判据与验收句。
  - `AnimationPlayer`、`AnimationEntry`、导出器、采样器没动。
- 关键帧：上臂、前臂、手按五次缓动段（0.00–0.34、0.03–0.37、0.06–0.40 s）六等分取帧，catmullrom；手指恒为三式 0 s 握拳。6 根骨、每段 31 帧（规格估 50–60，逐帧离五次缓动 ≤ 0.33°）。
- 门禁（粗、细臂相同）：全部适用项通过。
  - 错峰上臂 0 s → 前臂 0.383 s → 手 0.40 s；相关对 0 / 3；
  - 肘 70.01 / 70.06°、腕 40°；
  - 534.9 / 553.8°/s、1.88 / 1.94×10⁵°/s³；
  - 手臂穿模 0。
- 第 5.3 节路径用真实关键帧重跑：
  - 比同步缓动深不超过 0.1 px，只有承接副手细臂前臂差 0.14 px（< 0.3）；
  - 停法乙下打断与自然结束只剩架势帧本身的接触；
  - 更正：规格进场「≥ 3.75 px，不碰躯干」只对盾成立，普通物品离持斧臂 2.98 px，满格贴图起点帧插头 1.1 px（该式 0 s 自带）。
- 自查：
  - 保持段间隙和规格第 5.2 节逐格相同，全部为正；
  - 腕弯折 ≤ 37.3°、扭转 ≤ 35.8°；
  - 手指差 0°。
- 手持工具补核（铁剑、铁镐、铁斧，原版 handheld display，按贴图像素建盒）：
  - 保持段全部留空，离斧最小 0.28 px；
  - 路径里只有承接起点帧破门副手版自身姿势铁剑擦头 0.20 px；
  - 不改姿势。
- 探针（临时）：`d_guard.py`、`install_guard.py`、`guard_render.py`、`guard_scan/g23.py`（路径重跑）、`g24.py`（自查）、`g25.py`（手持工具）；路径结果 `out/guard_real_paths.json`、`out/guard_tool_paths.json`。
- 合层预览 `build/anim-review/door-breaker/door_breaker_guard(_off)/`：
  - 6 段持盾 GIF（上合层、下该式单独）；
  - 18 张盾 / 火把 / 普通物品接触表；
  - 只有粗臂。
- 验证：`./gradlew test checkAnimationGates checkAnimationAssets checkAnimationAntiForgery --rerun-tasks` 通过（测试 92 个、失败 0；导出一致性 95 段；门禁 140 条全部通过）；日志 `~/Code/Kizuna-remote-session/handoff/logs/anim-door_breaker_guard.log`、`anim-door_breaker_guard-gates.json`。
- 只做了离线验证；游戏内效果要等宿主接线。

## 万门一斩时间线补 `2.3: hit`（2026-10-30，待验收）

- 来源：`handoff/finale-swing-strike.md` 第 2 条。目录（Door-Breaker 未提交改动）已加 2,300 ms 前荡轻击：扇形 2.5 m、60°，×0.8，最多 3 个目标，不破盾、不劈门。
- 改动：`.bbmodel` 两段 `markers` 加 `"2.3": ["hit"]`，原型 `d_finale.py` 的 `MARKERS` 同步，用仓库导出脚本导出两段。关键帧、索引、`impact_window` 都没动；`PlayerAnimationAction` 只改注释。
- 打击时段：`[2.20, 2.40]` 实测都在一次性上限内，不登记（读数见 [door_breaker_finale](door_breaker_finale.md) 第 11 节末）。
- 文档：规格两页的事件表、时间轴与索引节，契约破门斩段，验收文档万门一斩条目。

## 八式起手压缩（I7a，2026-11-02，待验收）

- 来源：`~/Code/Kizuna-remote-session/handoff/i7-small-fixes.md` I7a 与审阅方决定。目录八式起手减半、50 ms 向上取整；门禁放不下的掩门按门禁允许的最小值 450 ms（−40%）。
- 做法：发力段起点 S 及以后整段提前，S 之前按门禁余量非均匀重定时；腿链在 S 之前补帧（掩门主手版全身补），共加 7,722 个关键帧，原有关键帧数值没改。详见 [验收记录](../animation-acceptance.md#破门斩起手压缩i7a2026-11-02)。
- 各式：叩门 3.0 → 2.55 s、破门 6.0 → 4.4 s、撞门 5.0 → 4.25 s、破盾 4.5 → 3.5 s、横扫 6.0 → 4.35 s、掩门 5.0 → 4.7 s、连门 8.0 → 7.25 s、万门一斩 9.0 → 7.05 s；`hit` 与窗口见各式规格页首。
- 同步：`PlayerAnimationAction` 自然时长、合同测试命中时刻、渲染器接触表、契约、切换表、各式规格页首、覆盖层规格第 14 节（路径与自查重跑）。
- 门禁：打包资源 16 段 × 粗 / 细臂全部通过，打击时段内的最大值不变；覆盖层承接路径副手版满格贴图比破门单独播放深 0.41 px（> 0.3 px，已报）。
- 预览：`build/anim-review/2026-11-02/`（八式主副手三视图 GIF 与接触表）。
- 只做了离线验证。

## 未做

- 游戏内录制、双客户端一致性、`calib_item` 校准斧子朝向（几何按原版贴图与 display 换算，尚未在游戏里截图对照）。
- 整合待办（横扫）：运行时 `root` 淡出走最短角度（1.35–1.70 s 被伤害打断时不倒转，压缩前是 3.00–3.35 s），属于运行时改动，本会话不做；`root` 回旋的游戏内验证（实体朝向、碰撞、第一人称、观察者）归整合录制；Human 映射分支归整合会话。
- 整合待办（连门）：**多段打击时段**。`impact_window` 现在只能一段，连门只登记了第三刀（现 `[4.03, 4.37]`），前两刀按 720°/s 做；要三刀各一段，得同时改分析器读 `impact_window` 的格式和运行时 `AnimationEntry`（Java），以后统一做（主手页第 9 节第 8 条）。
- 整合待办（持物手覆盖层，宿主行为，见 [door_breaker_guard](door_breaker_guard.md) 第 1、9 节与契约）：
  - 另一只手是否非空只在破门、连门、万门一斩起手那一刻判断，判为非空就同 tick 播 `door_breaker_guard`（`_off` 三式配 `_off`）；
  - 破门、连门接万门一斩时覆盖层保持、不重播；叩门、撞门承接破门时随破门起播；
  - 停法乙：该式自然结束或被 `stop` 后再过 5 tick（等它淡完）才停覆盖层；
  - 施放中手变空照播空握，中途换手不加不撤；
  - 游戏内验收：三式及副手版拿盾、火把、方块类物品，承接不回弹，自然结束与打断的淡出，左主手玩家，观察者一致。
- `stance_idle` 待办（覆盖层审阅第 7 条）：架势帧持物穿头。副手版右手持盾、持 16×16 满格贴图时插头 1.6 / 2.5 px，主手版左手持满格贴图擦头 1.0 px；是架势本身拿着东西就有的，覆盖层前后都一样，要改架势。
- 爆发 / 失衡 / 滑行窗口白名单随各式再加。
- 原型脚本：`~/Code/Kizuna-remote-session/handoff/kpa-door-breaker-proto/`（`d_knock.py` / `d_cleave.py` 生成、`run.py` / `run_cleave.py` 原型门禁、`install.py` / `install_cleave.py` 写入 `.bbmodel` 并导出、`compare.py` 并排 GIF）。
