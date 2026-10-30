# 破门斩动作进度

更新：2026-10-29。按「一次一段」制作，动作总稿在会话交接目录 `door-breaker-animation-2026-10-28.md`（八式 + 持物手覆盖层，共 18 段）。

| ID | 设计稿 | 类型与时长 | 层次（mask，优先级） | 驱动 | 骨 / 关键帧 | 门禁（粗臂/细臂） |
| --- | --- | --- | --- | --- | --- | --- |
| `door_breaker_knock` | [door_breaker_knock.md](door_breaker_knock.md) | 一次性 3.0 s（60 tick） | 全身（full，24） | 真实时间；承接从 0.30 s 起播 | 24 / 645（斧刃版） | 全部适用项通过 |
| `door_breaker_knock_off` | [door_breaker_knock_off.md](door_breaker_knock_off.md) | 一次性 3.0 s（60 tick） | 全身（full，24） | 同上 | 24 / 634（斧刃版） | 全部适用项通过 |
| `door_breaker_cleave` | [door_breaker_cleave.md](door_breaker_cleave.md) | 一次性 6.0 s（120 tick） | 全身（full，24） | 真实时间；承接从 0.40 s 起播 | 24 / 1,507（C′） | 全部适用项通过 |
| `door_breaker_cleave_off` | [door_breaker_cleave_off.md](door_breaker_cleave_off.md) | 一次性 6.0 s（120 tick） | 全身（full，24） | 同上 | 24 / 1,480（C′） | 全部适用项通过 |
| `door_breaker_ram` | [door_breaker_ram.md](door_breaker_ram.md) | 一次性 5.0 s（100 tick） | 全身（full，24） | 真实时间；承接从 0.40 s 起播；冲刺段原地迈步、实体由冲量推动 | 24 / 1,470 | 全部适用项通过 |
| `door_breaker_ram_off` | [door_breaker_ram_off.md](door_breaker_ram_off.md) | 一次性 5.0 s（100 tick） | 全身（full，24） | 同上 | 24 / 1,469 | 全部适用项通过 |

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

## 未做

- 游戏内录制、双客户端一致性、`calib_item` 校准斧子朝向（几何按原版贴图与 display 换算，尚未在游戏里截图对照）。
- 其余五式（破盾、横扫、掩门、连门、万门一斩）、持物手覆盖层；多段打击时段、爆发 / 失衡 / 滑行窗口白名单随各式再加。
- 原型脚本：`~/Code/Kizuna-remote-session/handoff/kpa-door-breaker-proto/`（`d_knock.py` / `d_cleave.py` 生成、`run.py` / `run_cleave.py` 原型门禁、`install.py` / `install_cleave.py` 写入 `.bbmodel` 并导出、`compare.py` 并排 GIF）。
