# 玩家模型与动画接口

本文件是模组与动画侧的对接契约。目标环境为 Fabric 26.2 / Java 25；以用户提供的 25 关节方案为基础，补齐实际文件格式。模型、正式动画与校准资源由动画侧制作。接口变动先修改本文。

## 资源位置

命名空间统一为 `kizuna_players_animation`，资源由本模组或普通资源包提供：

| 路径（相对 `assets/kizuna_players_animation/`） | 内容 |
| --- | --- |
| `geo/player_skeleton.geo.json` | Bedrock 1.12.0 几何，包含 `geometry.player.classic` 和 `geometry.player.slim` 两个条目 |
| `animations/<动作名>.animation.json` | Bedrock 1.8.0 动画；键为 `animation.kizuna_players_animation.player.<id>` |
| `player_anim_index.json` | `{"animations": [...]}`，每条列出下表字段 |

`.bbmodel` 源文件放在 `models/`，运行时只读导出的 JSON。模型与动画未交付时不打包占位模型；资源缺失会提示日志并保留原版玩家渲染。资源热重载后清空旧播放状态，避免旧骨架与新资源混用。

`models/player_skeleton_classic.bbmodel` 包含可编辑的 `walk`（1.2 秒）和 `run`（0.7 秒）循环草稿，并内嵌原版 Steve 皮肤供预览。两段分别导出到 `animations/walk.animation.json`、`animations/run.animation.json`，共用既有骨架与 UV；模组中通过 `/kpa play walk`、`/kpa play run` 手动预览。Human 宿主按实际运动与权威吐纳状态统一调度，仅在动作改变时调用播放或停止。循环保持原地，只有重心左右与上下起伏，不用 root 前进位移驱动实体。Steve 贴图来自 Minecraft 1.20.1 客户端，版权归 Mojang / Microsoft，不属于本项目 MIT 授权。

`meditate` 是一段打坐吐纳循环草稿，仍使用上述 classic 模板与内嵌 Steve 皮肤，导出到 `animations/meditate.animation.json`。时长 6 秒，约 2.4 秒吸气、3.6 秒呼气；双腿盘坐、双手搭膝，root 与腿部轨道恒定，腰胸、头部和手指有轻微呼吸变化。前臂仅屈肘 8°，不做轴向扭转。索引为 `full`、`hand: none`、`fpv: false`，淡入淡出各 0.6 秒。Human 正式吐纳会话启动时播放，真实位移先执行权威结束与资源结算，再切换运动动画；转头不结束。动作资源本身不含入座、起身、事件帧或缩放。

旧 `animations/player_actions.animation.json` 已从打包资源和索引移除。动作设计文件位于 [`docs/animation-specs/`](animation-specs/)，classic `.bbmodel` 是关键帧源文件，运行时 JSON 均从该文件导出。索引中当前交付跳跃、攻击、挖掘、普通使用物品、放置方块、穿戴、望远镜、号角、弩装填/持弩、刷子，以及潜行操作支撑 `crouch_work`、通用潜行 `sneak` / `sneak_walk`、进食 `eat(_off)`、饮用 `drink(_off)`、持盾格挡 `block_shield(_off)`、拉弓 `bow_draw(_off)`、三叉戟蓄力 `trident_charge(_off)`、骑乘 `ride`、睡眠 `sleep`、鞘翅 `elytra`、攀爬 `climb`、游泳 `swim`；另含行走、奔跑、吐纳和校准资源。闪避、通用单手格挡、双手格挡及通用手持单手/双手格挡动作均未制作，继续使用原版姿态。主副手剪辑标记 `hand: main/off`。自动门禁通过不代表游戏内视觉验收完成，当前视觉验收状态见文末。

盘腿使用大腿三轴组合旋转，其中 X 约 130°–133°、Z 约 −77° / +91°，超出原方案的初稿逐轴范围。因此本动作仍是坐姿预览草稿，不能标记为已通过原方案角度门禁；正式验收前需校准盘腿姿态的关节范围。

## 骨架与几何

| 骨名 | 父骨 |
| --- | --- |
| `root` | 无 |
| `pelvis` | `root` |
| `waist` | `pelvis` |
| `chest` | `waist` |
| `head` | `chest` |
| `r_upper_arm` / `l_upper_arm` | `chest` |
| `r_forearm` / `l_forearm` | 对应 `*_upper_arm` |
| `r_hand` / `l_hand` | 对应 `*_forearm` |
| `r_thumb` / `l_thumb`、`r_fingers_a` / `l_fingers_a`、`r_fingers_b` / `l_fingers_b` | 对应 `*_hand` |
| `r_item` / `l_item` | 对应 `*_hand`（空骨，不允许动画轨道） |
| `r_thigh` / `l_thigh` | `pelvis` |
| `r_shin` / `l_shin` | 对应 `*_thigh` |
| `r_foot` / `l_foot` | 对应 `*_shin` |
| `r_toe` / `l_toe` | 对应 `*_foot` |

骨名和父子关系固定，枢轴以各自几何文件为准。`root` 无方块，两个挂点为空骨。不能用粗臂几何缩放出细臂，也不在运行时猜测枢轴偏移。

- 模板坐标：像素为单位，脚底原点，Y 向上、前方为 −Z、玩家右侧为 −X。
- **JSON 必须直接使用 Blockbench 的 Bedrock 导出器**。导出器将几何 X 与位移 X 反号，将旋转 X/Y 反号；不能把动画面板数值直接当作导出值。运行时恢复 Blockbench 坐标后按 ZYX 欧拉顺序处理，再转换到 Java 玩家模型空间。
- 零姿态骨骼与方块旋转为零、无缩放，方块轴对齐；切分沿原方案的整像素边界。该约束用于自动生成切口两侧一致的蒙皮权重。
- 纹理固定 64×64，方块使用逐面 `uv` / `uv_size`，允许负 `uv_size`。不使用 box UV 重排切分后的皮肤，也不增加材质或纹理图集。
- 方块的 `inflate` 为 0 表示内层；外层头部为 0.5，其余为 0.25。内外层使用相同的未膨胀方块和权重。运行时按完整原版部件的包围盒统一外扩，再计算各分段边界，保持原版外层的像素比例；不对每段独立膨胀。Blockbench 中逐块 inflate 的预览在切口可能交叠，外层验收应以完整部件外扩后的结果为准。
- 缺少 UV 的面不画。同一原版部件的父子分段通过连续蒙皮权重连接；独立运动的相邻手指是兄弟骨，不能靠父子蒙皮封住彼此的切口。
- 手掌、手指、脚跟和脚尖沿用户方案切分。沿 Z 切分时，原前后面的 UV 只给实际接触原边界的端面；左右、顶底面按 Z 对应的纹理坐标裁切。拇指与两组手指之间的内部切面必须在内外层都保留，采样对应原版手臂底面中间两行；这些面随各自手指变形，避免独立弯曲时露出空腔，不改动原有外表面的 UV。

## 动画与播放

2026-10-23 姿态修订约定：`jump` 只用于正常陆地离地、下落与落地缓冲；死亡、骑乘、睡眠、飞行、入水/岩浆和攀爬会立即退出跳跃调度并清除落地状态。空中双腿前后错开、分时收伸，不改变实体物理轨迹。

按潜行键挖掘或使用工具时，上层动作只接管 `upper`，原版潜行姿态继续作为底层。静止操作使用 `crouch_work` 错脚承重循环（右脚稍后、左脚稍前，左主手镜像）；开始移动、松开潜行、结束操作或离地时淡回原版底层。`crouch_work` 只负责操作时的支撑姿态。通用潜行由 `VanillaAnimationScheduler` 在着地、非骑乘/入水/攀爬的潜行姿态下播放：水平静止选 `sneak`，移动选 `sneak_walk`，有工具动作且静止时选 `crouch_work`，三者同一时刻只播一个；三者都属于移动/支撑底层，上层动作只取 `upper`。索引为 `full`、优先级 2（高于宿主的 `walk`/`run`）、淡入淡出 0.20 秒、`hand: none`。脚底锁在模型地面，游戏中随原版潜行位置偏移下沉约 2.13 像素，头顶与 1.5 格碰撞箱一致；动作资源仍会覆盖原版视线，视线叠加层见 [head_follow](animation-specs/head_follow.md)。站立 `use_item` 的资源不因此改变。

挖掘修订采用更明确的举镐、下砸、压腕和回举；望远镜由另一只手托住持镜前臂，视线俯仰时双臂绕胸部坐标空间中的共同肩线旋转，保持支撑接触；弩装填强化勾弦、拉回、挂弦三个姿势的区别。上述关键帧仍以 classic `.bbmodel` 为唯一来源，主副手同时导出，自动门禁与离线预览不等同于游戏内验收。

位移单位为像素、旋转为度、时间为秒。只接受有限数值，不执行 Molang。关键帧接受常量 `[x,y,z]` 或时间字符串映射；支持 `linear`、`catmullrom`、`step`，以及 Blockbench 实际导出的 `{pre, post, lerp_mode}`。`pre` 是到达关键帧前的值，`post` 是到达该时刻后的值，因而支持原生 step 导出。`.bbmodel` 里的跳变帧写两个 data_point（[0] 前值、[1] 后值），`scripts/export_bbmodel_animation.py` 导出为同一时刻的 `pre` / `post`。

`loop` 可省略或为 `false`（一次性）、`true`（循环）、`"hold_on_last_frame"`（保持）；`animation_length` 必须为正数。缩放省略或恒为 1。未知骨名报错并忽略该骨轨道；禁止给挂点写轨道。未知事件和无效字段值报错，不猜测替代含义。

原版动作核对：攻击、挖掘、普通使用物品、放置方块、望远镜、号角、弩装填/持弩、刷子和跳跃由 `VanillaAnimationScheduler` 从原版实体字段产出动作 ID 与归一化进度，客户端按 scrub 取样对应资源。游泳、闪避和盾牌以外的格挡不请求自定义动作，继续使用原版姿态或渲染。攻击与放置剪辑前 `0.60` 的归一化进度跟随挥手进度，结束后按原版挥手时长推进 `0.60→1.00` 的收势尾段；挖掘采用完整循环与连续收势的独立游标。ArmPose 对应 `spyglass`、`toot_horn`、`crossbow_charge`、`crossbow_hold`、`brush`，`ItemUseAnimation.BOW` 对应 `bow_draw`（拉弦段剪辑秒数 = `BowItem.getPowerForTime`，满弓后已使用秒数在 `[1.0, 1.6]` 回绕；松手且拉弓进度 ≥ 0.1 时调度按真实时间推进 1.6→1.9 秒撒放尾段后停止；视线俯仰同望远镜按共同肩线刚性旋转双臂），`ItemUseAnimation.TRIDENT` 对应 `trident_charge`（已使用 tick / 20，`loop_region [0.50, 1.70]`；已使用 ≥ 10 tick 且非激流的松手按真实时间推进 1.70→2.15 秒投掷跟随），`ItemUseAnimation.BLOCK` 对应 `block_shield`（已使用 tick / 20，`loop_region [0.35, 1.95]`），`ItemUseAnimation.EAT` / `DRINK` 对应 `eat` / `drink`（剪辑时间 = 已使用 tick / 20；`eat` 在 `loop_region [0.40, 0.80]` 回绕，`drink` 1.60 秒后保持末帧），`ItemUseAnimation.NONE`（普通及模组物品）与 26.2 新增的 `BUNDLE`（收纳袋按住倒出物品，原版第三人称没有专属 ArmPose）使用 `use_item`；26.2 新增的 `ItemUseAnimation.SPEAR`（矛蓄力冲刺）不请求自定义动作，继续使用原版矛姿态。调度对每个 `ItemUseAnimation` 值都有显式决定、不设兜底分支，升级 Minecraft 出现新值时先在本节补写。scrub 目标跨过 `loop_region` 接缝或 `loop: true` 资源的首尾时，客户端把目标暂放在接缝另一侧沿最短方向插值，不倒放整段。跳跃使用单段 `jump`：离地保存 `vy0`，空中以 `clamp(0.30 × (1 − vy / vy0), 0, 0.60) / 0.90` scrub，落地从 `0.60` 播到 `0.90` 的缓冲段后才停止。

挥手路径以原版 `swinging` 和 `swingingArm` 确定正在挥动的手，并按该手物品选择攻击、挖掘或放置模板；持续使用物品的路径仍读取 `getUsedItemHand()`，两种手状态互不替代。挥手模板再按该手物品的 `SwingAnimation` 类型（26.2 新增数据组件，空手与未声明的物品为 `WHACK`）显式决定：`WHACK` 选攻击、挖掘或放置模板；`STAB`（矛戳刺）与 `NONE`（原版不摆臂）不请求自定义动作，同时丢弃上一次挥手的收势，保留原版姿态。

矛（26.2 新增）现状：蓄力冲刺与戳刺都交还原版手臂姿态（`SpearAnimations`）；持矛未使用时原版同样给出前举的矛姿态，作为 PA 底层保留；无上层动作时，行走、奔跑、潜行等底层剪辑仍按各自轨道覆盖手臂。迁移后待办：F2 PA 持物层尚未复刻原版矛的手持物变换（第三人称 `ItemInHandLayer` 的 `SpearAnimations.thirdPersonAttackItem` 与 `ArmPose.animateUseItem`，第一人称矛动画同样未接），矛的朝向与原版不同；F3 矛专属剪辑 `spear_charge(_off)`、`spear_stab` 待动画侧设计交付，进度对齐 `KineticWeapon` 的延迟与伤害窗口，交付前保持上述原版行为。

导出与门禁命令：`python3 scripts/export_bbmodel_animation.py --check` 检查 `.bbmodel` 与全部打包 JSON；`python3 scripts/analyze_animation.py walk run meditate` 按 60 Hz 采样基线的正向运动学门禁；`python3 scripts/render_animation_gif.py walk run meditate --fps 12` 从 `.bbmodel` 直接输出粗臂/细臂三视角 GIF 和接触表。当前环境没有 Blockbench 可执行文件，因此使用仓库内 Pillow 工具完成离线 GIF；有图形界面时仍可用 Blockbench 自带导出复核。

索引条目：

| 字段 | 约定 |
| --- | --- |
| `id` | 唯一动作名，与动画键后缀一致 |
| `file` | 相对命名空间的 `animations/*.animation.json` 路径 |
| `mask` | `full`、`upper`、`lower`、`r_arm`、`l_arm` |
| `priority` | 整数，同层同骨优先级大者胜出；相同值以后开始者优先 |
| `blend_in` / `blend_out` | 非负秒数；一次性结束后冻结末帧并淡出，停止保持或循环动画也使用淡出 |
| `hand` | `main`、`off`、`both`、`none`；main 模板按右主手，off 模板按左副手；none 固定解剖左右 |
| `fpv` | 布尔，仅此标记为真的动画参与独立第一人称手臂播放；全身第一人称直接复用第三人称姿态，不受此标记限制 |
| `impact_window` | 可选，`[开始秒, 结束秒]`，剪辑时间。只供门禁声明打击时段，运行时不读取；当前白名单为 `attack_main/off`、`mine_main/off`、`door_breaker_knock(_off)`、`door_breaker_cleave(_off)`、`door_breaker_ram(_off)`、`door_breaker_hook(_off)`、`door_breaker_close(_off)`、`door_breaker_chain(_off)`、`door_breaker_finale(_off)` |
| `loop_region` | 可选，`[开始秒, 结束秒]`，剪辑时间。scrub 进度到达结束点后在该区间回绕；仅用于保持类的可重复动作（例如 `spyglass`、`brush`） |
| `layer` | 可选，只接受 `look`：加性视线层。渲染器每帧按原版视线角度取样并把旋转加到已混合姿态上，不经过播放、优先级与同步；当前为 `head_follow`、`head_follow_pitch` |
| `phases` | 可选，阶段数组或名称到 `[开始秒, 结束秒]` 的映射；仅供门禁在每个声明阶段内按主动作方向检查峰值顺序，时间必须落在剪辑范围内 |
| `unbalanced_windows` | 可选，`[[开始秒, 结束秒], ...]`。只供门禁声明失衡时段（窗口内不查重心投影），运行时不读取；只允许 `dodge_*`、`technique_dash_*`、`door_breaker_cleave(_off)`、`door_breaker_ram(_off)`、`door_breaker_hook(_off)`、`door_breaker_sweep(_off)`、`door_breaker_close(_off)`、`door_breaker_chain(_off)`、`door_breaker_finale(_off)` |
| `burst_windows` | 可选，`[[开始秒, 结束秒]]`，至多一段且 ≤ 0.08 s。只供门禁声明蹬伸爆发时段（窗口内角速度 / 角加加速度上限放宽到 1,500°/s、6×10⁶°/s³），运行时不读取；只允许 `dodge_*`、`technique_dash_*`、`door_breaker_ram(_off)` |
| `slide_window` | 可选，`[开始秒, 结束秒]`。只供游戏内验收：声明该段脚底随实体在世界空间同步滑行不算穿帮，运行时不读取；当前为 `technique_dash_*` `[0.28, 1.01]`（冲刺短跑：着地脚在模型空间锁定，实体被功法推动时随之滑行）、`door_breaker_ram(_off)` `[0.80, 1.50]`（第一次冲量到冲量衰减；施放中 0.4 倍主动移动不属于冲量段，不要求锁脚）、`door_breaker_close(_off)` `[0.45, 1.10]`（冲量一到冲量二衰减，两步原地后撤）、`door_breaker_chain(_off)` `[0.80, 4.35]`（冲量一到冲量三衰减，两次垫步与弓步）、`door_breaker_finale(_off)` `[2.10, 2.90]`（起跳两次冲量到落地后实体滑停） |
| `reaction` | 可选，只接受 `hit`，只用于 `*_hit` 受击后坐：门禁对被动动作免查反向预备，跟随与回落照查；运行时不读取 |

战斗架势（2026-10-27）：`stance_*`、`dodge_*`、`technique_dash_*`、`block_*_hand(_hit)`、`technique_breath/charge` 只交付资源与 `PlayerAnimationAction` 显式接口，触发由战斗 UI / 宿主负责。
破门斩（2026-10-28 起）：`door_breaker_knock(_off)`（叩门，2.55 s，51 tick）、`door_breaker_cleave(_off)`（破门，4.4 s，88 tick）、`door_breaker_ram(_off)`（撞门，4.25 s，85 tick）、`door_breaker_hook(_off)`（破盾，3.5 s，70 tick）、`door_breaker_sweep(_off)`（横扫，4.35 s，87 tick）、`door_breaker_close(_off)`（掩门，4.7 s，94 tick）、`door_breaker_chain(_off)`（连门，7.25 s，145 tick）与 `door_breaker_finale(_off)`（万门一斩，7.05 s，141 tick）都是 `mask: full`、优先级 24，只交付资源与 `PlayerAnimationAction` 显式接口，宿主在功法起手时按真实时间播放；2026-11-02 起手压缩（I7a）后的时长与击中时刻见 [验收记录](animation-acceptance.md#破门斩起手压缩i7a2026-11-02)；
它们不属于下面的上层规则，任何底层上都整段生效，服务端每次打击另调的原版挥手（攻击，优先级 20）不接管任何骨。施放期间移速 ×0.4、不锁移动；撞门冲刺段、掩门两次撤步、连门两次垫步与弓步原地迈步、万门一斩起跳与腾空，位移由冲量负责。
万门一斩时间线有两次 `hit`：`1.2` 前荡轻击、`2.9` 落地竖劈，对应目录两次 `weapon_strike`（1,200 / 2,900 ms）；同其他 `hit` 一样只是客户端通知，伤害由服务端目录结算。
持物手覆盖层 `door_breaker_guard(_off)`（2026-10-30，见 [设计稿](animation-specs/door_breaker_guard.md)）：破门、连门、万门一斩是双手持斧，起手时另一只手非空，宿主在同一 tick 叠播覆盖层（主手版配 `door_breaker_guard`，`_off` 版配 `door_breaker_guard_off`）。
覆盖层 `mask` 为 `l_arm` / `r_arm`、优先级 25、淡入 0.10 s、淡出 0.25 s、`hand` 与所配的式相同，0.40 s 把持物臂收到体侧护持（盾竖持、盾面朝外）后 `hold_on_last_frame`，不带 tick，宿主显式停止；遮罩不含 `*_item` 挂点，持物随手。
宿主约定（归整合会话接线）：另一只手是否非空只在双手式起手那一刻判断，判为非空就同 tick 起播；破门、连门接万门一斩时覆盖层保持、不重播（同 id 重播会从 0 s 重来）；叩门、撞门承接破门时随破门起播，覆盖层从自己的 0 s 起；自然结束或被打断时先停该式，等它淡完（0.25 s，5 tick）再停覆盖层；施放中手变空时覆盖层照播，手里空握。
轻身跃步 `technique_leap`（2026-10-30，见 [规格](animation-specs/technique_leap.md)）：`mask: full`、优先级 17、淡入 0.05 s、淡出 0.10 s、`hand: main`、`fpv: false`；0.40 s 蹬地前冲、收腿前伸后 `hold_on_last_frame`，没有 `timeline`，不带 tick。
宿主在跃步起跳时开始播放，落地或中断（落水、上坐骑、死亡、飞满 200 tick）时显式停止；丢失停止时一直保持末帧，不加兜底时长。落地缓冲由玩家下层原版调度的 `jump`（优先级 16）承担：跃步整段盖住 `jump`，停止后冻结淡出，露出 `jump` 落地段 6 tick；怪物下层没有 `jump`，落地直接淡回底层，不做缓冲。
施放破门斩时起跳，跃步被式（24 / 25）整段盖住，腿照式的剪辑站在空中；是否由宿主禁止施放中起跳待定。
`AnimationPlayer` 的遮罩规则：原版上层（攻击、挖掘、使用等）在任何架势底层（含静止 `stance_idle`）、闪避、轻步、跃步和格挡上都只取 `upper`；
格挡、受击、调息、蓄力在静止 `stance_idle` 上整段生效，在移动架势底层、进出过渡、闪避、轻步、跃步或原版移动底层上只取 `upper`。

层序为原版姿态 → 循环姿态 → 一次性／保持动作。只有动画实际包含、且在 mask 内的骨参与接管，缺失的通道继承下层。`upper` 包含腰、胸、头、双臂；`lower` 包含胯、双腿；单臂包含上臂至手指。只有 `full` 可以改变 root。

`root` 目前只有横扫 `door_breaker_sweep(_off)` 在用（2026-10-30）：只转 Y（主手版 0° → −360° 左转，副手版 0° → +360° 右转，1.35–2.60 s），位置按已转角绕左踝竖线平移补偿，使转轴脚的世界位置不动；2.65 s 用跳变帧（pre ±360°、post 0°）换回 0°，末帧为 0，播完淡出不倒转。淡入淡出按欧拉角线性混合，起转后被打断时已转的角度会在淡出时倒转回去；运行时尚未做最短角度淡出。

左主手且 `hand != none` 时，交换左右骨与单臂 mask，位移 X 反号，旋转 Y/Z 反号（镜像不是把旋转 X 反号）。`step_l` / `step_r` 事件同时交换。独立 `fpv` 手臂只取手臂骨，不继承 root、腰和胸的动画位移。

全身第一人称默认开启，可用客户端命令 `/kpa firstperson false` 关闭、`/kpa firstperson true` 恢复（当前客户端会话有效）。它在世界实体阶段复用第三人称渲染器、完整骨架和持物挂点，隐藏本地头、帽、头部装备及插入身体的箭/蜂刺，身体沿朝向略微后移；相机、碰撞和交互坐标保持原版。普通手臂和持物只绘制一次；地图及望远镜瞄准保留原版专用显示，身体仍可见。玩家持物在 `PlayerItemInHandLayer.submitArmWithItem` 入口统一处理，包含原版望远镜特例：全身第一人称瞄准不补画头部镜筒，第三人称使用同帧手掌挂点绘制一份。睡眠、死亡、旁观、游泳/爬行过渡、鞘翅和激流等特殊视角暂不启用全身通道，关闭时沿用原版及已有 `fpv` 手臂入口。

工具挥动 `mine_main/off` 采用 0.8 倍视觉时钟：默认完整循环由 4 tick 延长到 5 tick，急迫/挖掘疲劳仍按原版挥手时长缩放。连续挥手只推进同一游标，不随原版重挥回零；松手后从当前位置以相同速度完成剪辑收势。刷子及普通 `use_item` 的视觉时钟也乘 0.8；攻击、放置、弩装填和真实挖掘/使用判定不变，

`timeline` 的值是事件名或事件名数组，白名单为 `hit`、`release`、`step_l`、`step_r`、`cancel_ok`、`equip`。`equip` 是穿戴动作唯一的层切换点：事件前隐藏对应盔甲层并渲染手中盔甲物品，事件后恢复盔甲层。胸甲、头盔、护腿和靴子均遵循同一约定；靴子层按左右腿分别隐藏。事件按播放时钟推进发送，跨帧、跨循环均不漏发；渲染取样不发送事件。这是客户端通知接口，不直接触发服务端伤害。

穿戴同步从当前进度恢复已经越过的 `equip` 所对应的内部显隐状态，不向公开事件接口补发历史回调。相同起点与请求 ID 的重复同步保留现有显隐状态；新播放才重新初始化。靴子依次恢复两侧：主手模板先恢复惯用手同侧，副手模板先恢复另一侧，左主手随骨架一起镜像；两次事件之间仍持有第二只靴子。

### 原版特殊状态循环（2026-10-26）

`VanillaAnimationScheduler` 在 `isSleeping()` 时播放 `sleep`（优先级 5、淡入淡出 0.30 秒），滑翔超过 4 tick
（与原版滑翔头部切换同一阈值）时播放 `elytra`（优先级 4、0.25 秒），骑乘时播放 `ride`（`Entity.shouldRiderSit` 仅客户端存在，原版坐骑默认坐姿，服务端按 `isPassenger()` 判断）
（优先级 3、0.25 秒），三者互斥，均为 `full`、`hand: none` 自由时钟循环，状态结束时停止。资源在身体空间制作，
睡眠放倒、滑翔俯仰与倾斜、坐骑高度仍由原版渲染矩阵负责；骑乘与滑翔是底层，上层动作只取 `upper`。

### 攀爬（2026-10-26）

`onClimbable()` 且未着地、未骑乘时，调度累计实体竖直位移（格），以 `frac(累计)` scrub `climb`（`loop: true`，1.0 秒 = 1 格）；
下滑相位倒退，挂住不动时相位不变，着地或离开可攀爬方块即停止并重置。资源按“面朝梯子”制作，侧身或背对攀爬时手会抓空
（已知限制，不做朝向判断）。攀爬激活时 `attack*`、`mine*`、`place_block*` 只接管动作手臂（主手模板 `r_arm`、副手模板 `l_arm`），
其他上层动作仍取 `upper`。

### 游泳（2026-10-26）

水中泳姿（`isVisuallySwimming()` 且 `isInWater()`）时，调度按原版 `LivingEntity.calculateEntityAnimation` 同一公式每 tick 累计
`min(三维位移 × 4, 1) / 26` 作为相位并 scrub `swim`（`loop: true`，1.3 秒 = 原版 26 单位划臂周期）；陆地爬行不触发，保持原版。
资源在身体空间制作（头向 +Y），放倒与俯仰由原版矩阵负责。

### 视线叠加层（2026-10-26）

所有动作资源的头部旋转轨道都会覆盖原版视线。视线层修复此问题：启用时底层姿态去掉原版头部偏航与俯仰（保留潜行下移等枢轴位移），
各资源照常混合与俯仰补偿后，`LookLayer` 取原版 `head.yaw`（头相对身体，钳制到 ±50°）与 `head.pitch`（±90°，正值低头），
分别以 `t = (yaw + 50) / 100`、`t = (pitch + 90) / 180` 取样 `head_follow` / `head_follow_pitch` 并把面板旋转直接相加。
转到 ±50° 时头约 30°、胸 12°、腰 8°，俯仰 ±90° 时头 62°、胸 16°、腰 12°，合计等于原版角度；双臂随胸小幅反向。
睡眠、泳姿与爬行过渡、鞘翅、激流、死亡和独立 `fpv` 手臂入口不启用；望远镜、号角、持弩、拉弓已在头部补偿俯仰，此时只叠偏航；
饮用时俯仰层按 0.5 权重叠加。两段资源须同时存在并声明 `layer: look`，否则保持原版视线。服务端不发送视线层请求。

## Protobuf 控制协议 v1

唯一协议源为 [`src/main/proto/kizuna/playeranimation/v1/player_animation.proto`](../src/main/proto/kizuna/playeranimation/v1/player_animation.proto)，Protobuf 包名为 `kizuna.playeranimation.v1`。动画仓库维护字段、编号与语义；调用方固定本仓版本，从同一文件生成代码，不手写或另建平行 schema。几何、关键帧与播放参数继续由已有 JSON 提供。

服务端安装本模组后，通过 Fabric play 通道把 Protobuf 请求发给目标玩家本人和正在追踪该玩家的客户端；客户端处理后用 Protobuf `Result` 回报服务端。玩法侧在服务端主线程调用 `ServerPlayerAnimations`，不从客户端事件推导服务端伤害。Protobuf 解码本身不校验下列业务约束。

| 消息 | 字段与语义 |
| --- | --- |
| `PlayRequest` | `request_id`、`player_uuid`、`animation_id`，可选 `start_tick` 或 `normalized_progress`；后者对应客户端主线程的 `PlayerAnimations.scrub`，范围为 `[0,1]` |
| `StopRequest` | `request_id`、`player_uuid`，以及 `target` 二选一：`animation_id` 停止指定动作，或 `all: true` 停止该玩家全部动作；对应 `stop` / `stopAll` |
| `Result` | 原样回传 `request_id`，用 `code` 表示处理结果；`detail` 是可省略的诊断文字，调用方按 code 分支，不解析文字 |

- `request_id` 必须是 1–128 个字符，由调用方在待处理请求中保证唯一。它用于关联结果；超过上限的无效请求返回空 ID，无法关联。不携带 `start_tick` 时，每次请求都重新播放。服务端同步携带 `start_tick`，同一次播放的补发沿用原请求 ID 与起点，客户端不会重置已经存在的同一实例；显式重播使用新 ID，旧实例淡出。
- `player_uuid` 是带连字符的标准 UUID 字符串，例如 `00000000-0000-0000-0000-000000000001`，指定被渲染的玩家。播放要求该玩家和动画已在接收客户端加载。
- `animation_id` 是 `player_anim_index.json` 中的 `id`，例如 `meditate`，匹配 `[a-z0-9_./-]+`；不是文件路径或完整 Bedrock 动画键。掩码、优先级、循环、淡入淡出、镜像及第一人称参数沿用索引和动画资源，不在消息里重复配置。
- `start_tick` 是目标玩家所在世界的非负总 tick，单位为 1/20 秒。服务端发送时设置；客户端按同一世界时钟从相应进度开始播放，避免后来进入追踪范围的玩家从头看到循环动作。旧调用方不设置时从收包时刻开始。补发时跳过已经过去的时间线事件；`normalized_progress` 是原版状态 scrub 的互斥路径，收到后不使用自由时钟，连续同 ID 请求只更新游标。`PlayerAnimationAction` 目录中的一次性动作由服务端按资源时长自动停止并移出补发快照，结束已久的一次性动作也不再显示。目录外的自定义动作仍须显式停止循环、保持或一次性动作；返回 `OK` 表示该同步请求已处理。
- 停止必须显式选择目标；未设置 `target`、空 `animation_id` 或 `all: false` 均为无效请求，不能隐式停止全部。停止不存在或已经停止的动作仍成功，未建立播放器的玩家也视为无需停止；不会因此创建播放状态。
- 一条可解码的请求处理后返回一个 `Result`。`OK` 表示播放已处理或停止已交给播放器；动画可能仍在淡入或淡出，不表示播放结束，也不表示服务端玩法成功。无效请求原样回传能取得且长度合法的 `request_id`，缺失或过长时为空。

| `Result.Code` | 数值 | 语义 |
| --- | --- | --- |
| `CODE_UNSPECIFIED` | 0 | 未设置结果，不能当作成功 |
| `OK` | 1 | 请求已执行，包含无需操作的停止请求 |
| `INVALID_REQUEST` | 2 | 缺少必需值、UUID / 动作 ID 格式错误或停止目标无效 |
| `ANIMATION_NOT_FOUND` | 3 | 播放时动画未成功加载 |
| `PLAYER_NOT_LOADED` | 4 | 播放时玩家未在客户端世界加载 |

通道为 `kizuna_players_animation:play`（S2C `PlayRequest`）、`:stop`（S2C `StopRequest`）和 `:result`（C2S `Result`）。Fabric 自定义载荷内直接存放 Protobuf 字节，不再嵌套长度前缀；消息上限 32 KiB。服务端以 Fabric 实体追踪范围确定接收者，并另发给目标玩家本人；新追踪者在服务端 tick 末尾收到仍登记的动作及其原始 `start_tick`。停止动作从服务端登记状态删除。通道必需接通：正常握手最多等待 600 tick，期间保留待补发快照与回执；超时或接通后丢失通道报错并断开，不提供缺包运行路径。`Result` 仅记录客户端处理结果，不用于服务端玩法授权；网络接线没有 gRPC、客户端任意请求广播或权限协议。时间线 `hit` / `release` 等仍使用已有客户端事件接口。

服务端只保存每位玩家每个动作的最后一次播放请求，不读取客户端资源包，也不运行骨架。`PlayerAnimationAction` 目录中的一次性动作同时登记资源时长，到期后由服务端走统一停止协议并移除；循环、保持和目录外动作由玩法显式停止。补发保留最后一次调用顺序；不重建停止前的淡出实例。时钟精度沿用 Minecraft 世界时间同步，不承诺逐帧一致。

`ServerPlayerAnimations.play(player, id)`、`stop(player, id)`、`stopAll(player)` 必须在服务端主线程调用，返回本次请求 ID。`ServerPlayerAnimations.RESULT` 提供接收客户端与其上报结果；只接收发给该客户端且尚在 30 秒（600 tick）回执窗口内的请求结果。每个实际收到请求的客户端分别回应，不做全体成功聚合、自动重试或玩法判定；补发仍可产生原 ID 的新回执。无法解码的包记录错误并丢弃；可解码但业务字段无效的请求返回 `INVALID_REQUEST`。服务器不接收 C2S 播放／停止请求。管理员可用 `/kpa_server play <player> <id>`、`/kpa_server stop <player> [id]` 验证广播，权限等级为 2；已有 `/kpa` 命令仍仅用于本地预览。`ServerPlayerAnimations.animationIds()` 按索引顺序返回本模组自带 `player_anim_index.json` 中的全部 id，供服务端指令补全（`/kpa_server` 的 id 参数即取此列表）；资源包不影响它，播放请求也不据此校验，模组内缺少该索引时直接报错。

同一 v1 内只添加兼容字段；删除字段或枚举值必须同时 `reserved` 原编号和名称，禁止复用。新增必需语义或改变既有字段含义时升级包版本。未知字段可保留并忽略，未知结果码与零值不能按成功处理；未来新增的停止目标也不能在旧接收端退化成停止全部。

### 生成与分发

```bash
./gradlew generateProto protocolZip
```

构建固定使用同版 `protoc` / `protobuf-java` 4.36.2，Java 类型为 `dev.kizuna.playeranimation.protocol.pb.PlayerAnimationProtocol` 内的 `PlayRequest`、`StopRequest`、`Result`。生成代码位于 `build/generated/sources/proto/main/java/`，不提交；模组 JAR 内含生成类及 Protobuf 运行库。

`build/distributions/kizuna-players-animation-0.1.0-SNAPSHOT-proto.zip` 包含本仓的唯一协议源文件、`descriptors/player-animation-v1.desc` 与 MIT License；`./gradlew build` 同时生成此包。调用方可以只消费该包，从中生成所需语言的类型，无需依赖 Minecraft 客户端。Java 调用方若直接依赖本模组已有类型，就不要再次生成同名 Java 类。

## 实体字节入口 v1

唯一协议源为 [`entity_animation.proto`](../src/main/proto/kizuna/playeranimation/v1/entity_animation.proto)，Java 类型为 `dev.kizuna.playeranimation.protocol.pb.EntityAnimationProtocol`。Fabric entrypoint 名为 `kizuna_player_animation`，类型为 `dev.kizuna.playeranimation.api.PlayerAnimationHost`，唯一操作为 `byte[] exchange(byte[] payload)`。调用方只依赖此接口及生成协议，不引用 PA 的渲染、骨架或播放器实现。入口必须在客户端初始化完成后、客户端主线程调用。

| 操作 | 字段与行为 |
| --- | --- |
| `RegisterGeometry` | `geometry_id`、UTF-8 `geometry_json`、`entity_types`，以及可选的 `overlay_geometry_id`。同一 JSON 中的本体与外层共享骨序和枢轴；完成校验后按实体类型替换绑定。玩家仍使用原有玩家模型。 |
| `Play` | `entity_id` 是当前客户端世界的运行时实体 ID；`animation_id` 引用已加载索引；`start_tick`、`normalized_progress`、`loop` 均使用 presence。目标须为已加载玩家，或已注册几何的已加载生物。 |
| `Stop` | `entity_id` 加 `animation_id` / `all: true` 二选一；缺少目标、空动作名、`all: false` 返回无效请求。停止不存在的状态仍成功，不创建播放器。 |
| `Clear` | 立即移除指定实体的客户端播放状态；用于卸载清理，不保留停止淡出。 |
| `Automatic` | 客户端本地选择的 `base_animation_id` 循环与可选 `action_animation_id` / `action_progress`。只适用于已注册生物；进度为有限 `[0,1]`。同名底层不重启，换动作沿用资源淡入淡出。自动层从中立骨姿态取样，显式 `Play` 在其上混合，结束后自然露出自动层；`Stop` 只停止显式动作，`Clear` 同时清理两层。此操作不通过网络发送。 |

`entity_types` 按 Minecraft `ResourceLocation` 规范化；省略命名空间时使用 `minecraft`，例如 `zombie` 与 `minecraft:zombie` 绑定同一实体类型。注册会先校验全部类型，再替换绑定，失败不会留下部分映射。

`normalized_progress` 范围为有限的 `[0,1]`，存在时优先使用 scrub，忽略自由时钟的 `start_tick` 和 `loop`。否则 `start_tick` 使用世界总 tick（1/20 秒），调用方提供非负起点；缺省时从当前时刻重播。携带起点时，同一动作、请求 ID、起点的补发不重置现有实例，并跳过过去的时间线事件。`loop` 缺省沿用资源，显式 `true` 为重复，显式 `false` 为一次播放，二者不能与“未设置”混淆。`request_id` 用于关联响应与带起点补发；此入口不沿用玩家网络协议的非空 ID 校验。

每次调用返回 `EntityAnimationResponse`：正常解析的业务响应回传 `request_id`，`detail` 只用于诊断。非法 protobuf、线程错误和入口捕获的参数异常可能返回空 ID。

| Code | 数值 | 含义 |
| --- | --- | --- |
| `CODE_UNSPECIFIED` | 0 | 未指定，不能视为成功 |
| `OK` | 1 | 注册或播放控制已处理，不代表播放结束 |
| `INVALID_REQUEST` | 2 | 非法字节、线程错误、缺少操作、进度无效、未注册几何或停止范围无效 |
| `GEOMETRY_INVALID` | 3 | 几何解析、实体类型或本体与外层绑定校验失败 |
| `ANIMATION_NOT_FOUND` | 4 | 动作未加载 |
| `ENTITY_NOT_LOADED` | 5 | 播放目标未加载或不是 LivingEntity |
| `UNBOUND` | 6 | PA 客户端处理器尚未绑定 |

实体几何沿用 `format_version: 1.12.0`、完整固定骨名和父子关系，共 24 个可见骨、root 与两个物品挂点；root 和挂点不带盒。玩家几何继续要求 64×64 及原有外层厚度；实体几何允许原版纹理宽高与 dilation，UV 仍必须在纹理边界内。实体 cube 可附 `part`（原版子部件路径）、`inflate_origin` / `inflate_size`（切段前完整盒的膨胀边界）、`rotation` / `pivot`（静态子部件旋转）。这些扩展用于保留鼻子、耳朵、袍子、帽沿和逐面 UV；不改变动画骨层级。可见骨不强制拥有玩家式内外两层。

PA 用同一帧采样矩阵绘制实体本体、标准 `RenderLayer.renderColoredCutoutModel` 覆盖层、原版盔甲与手持物。本体及覆盖层材质仍由原版 renderer 选择，覆盖层可用独立几何，部件显隐沿用原版模型。没有生物播放状态时保留原版渲染。玩家调用仍走玩家播放器、装备显隐和 `PlayerAnimations.EVENT`；生物使用独立实体播放器，不发出玩家事件，卸载和世界切换清理状态。

此入口是进程内调用，不实现服务端同步。Mobs Addon 负责自身 S2C 通道、追踪补发、停止与卸载清理；网络载荷上限由附属控制为 32 KiB，资源注册不经此网络通道。附属的 PA 响应只记入客户端日志，没有服务端结果回执。附属直接从 `-PkpaDir` 指向的 PA 唯一 proto 生成协议；当前使用本地 `0.1.0-SNAPSHOT` 构建，尚未声明已发布的固定版本。字段演进继续遵守 v1 兼容与编号保留规则。

## 挂点与范围

`r_item` / `l_item` 的精确 pivot 由几何交付；运行时叠加挂点的父级变换与原版第三人称物品 display。位置仍需 `calib_item` 在游戏中校准，不把推算值标为验收完成。

当前交付的 pivot 按 1.20.1 原版 `ItemInHandLayer` 和 `PlayerModel.translateToHand` 推算：原版在手臂局部空间依次做 `Rx(-90°)·Ry(180°)`，再平移 `(±1/16, 0.125, -0.625)` 格；换算到手臂枢轴相对的像素偏移（模型空间 Y 向下）为 `(∓1, 10, -2)`，即右臂 `(-1, 10, -2)`、左臂 `(1, 10, -2)`。细臂持物还会先将右臂枢轴向内移 0.5 像素（左臂镜像），该修正只用于持物，不改变骨架肩枢轴。模板坐标（Y 向上）下 `r_item`：粗臂 `(-6, 12, -2)`，细臂 `(-5.5, 11.5, -2)`，左侧 X 取反；Bedrock 导出后右侧 X 为正。原版持物姿势为 `pitch*0.5 - 0.31415927` rad，即上臂向前 18°。挂点与原版持物矩阵通过自动化对照，游戏内视觉校准仍待执行。

当前实现玩家皮肤、外层开关、第三人称动画、手持物、全身第一人称与独立 `fpv` 手臂播放；Vanilla 盔甲保留原版材质与渲染生命周期，顶点使用身体同一帧的多关节蒙皮，并由动作资源提供穿戴姿势。双手动作的物品矩阵取 `r_item` 与 `l_item` 两挂点的世界位置中点，只渲染一次，避免左右各画一件。穿戴动作按 `equip` 事件隐藏和恢复装备层，事件前物品从对应主/副手挂点开始，双手阶段切换到两挂点中点。披风、自定义 3D 装备分骨与旧 PlayerAnimator 动画转换仍不在范围内。

Vanilla 盔甲适配约定：从原版内、外层模型的绑定几何读取面、厚度与归一化 UV，按像素细分；以未膨胀位置选择现有皮肤分段骨并复用蒙皮权重。逐帧只读取身体同一次渲染的骨骼矩阵，皮革染色及覆盖层、纹饰与附魔光效共用该网格。头盔保持刚体，袖子、胸腰和腿靴跟随对应关节；细臂仍使用原版四像素宽的盔甲袖子，肩高对齐细臂骨架。穿戴事件、第一人称头盔/手臂显隐及未播放动画的非玩家实体渲染保持各自既有规则。几何缓存随资源重载清空；不新增盔甲动画、独立骨架或材质注册表。此约定限于 Vanilla 盔甲，不承诺任意自定义装备模型；实际弯折厚度仍需游戏内验收。

细臂挂点的 0.5 像素内移是零姿态位置。原版 `translateToHand` 在手臂旋转前修正肩部位置；运行时在原版底层姿态中将偏移差值转换回挂点局部空间，后续整体、胸部、前臂和手腕动画都带动它，避免肩部绕 Y/Z 旋转后出现偏差。该修正只作用于挂点，不移动皮肤网格。

## 验收状态

服务端同步于 2026-10-14 使用一个独立 Fabric 1.20.1 服务端和两个真实 Fabric 客户端验证：目标本人和范围内观察者播放／停止成功并回传 Protobuf 结果；观察者移到 1024 格外后不接收新动作且清空卸载实体的播放器；重新进入范围恢复已登记动作；未知动作返回 `ANIMATION_NOT_FOUND`；换维度清理双方状态。48 项自动化测试通过，含原有动画／模型测试、协议字节格式、输入边界、补发进度、重播优先级及历史事件抑制。未覆盖真实公网延迟、跨版本客户端或大量玩家压力测试。

当前打包骨架模板（粗臂、细臂）、64×64 调试皮肤 `models/player_debug_skin.png` 和 55 段索引动画（2026-10-26 增加剩余原版动作与两段视线层），包括五段校准、三段基线与已交付玩家动作。旧聚合动作文件已移除，新动作均由 classic `.bbmodel` 分段导出。2026-10-23 本轮姿态修订、潜行操作支撑与跳跃调度已实现；导出一致性、全部非校准动作的双体型数值门禁及 Java 回归通过。本轮游戏内视觉验收尚未执行，自动化测试不替代皮肤、挂点或关节形变的视觉验收。

`walk` / `run` 已检查骨骼 UUID、循环首尾、导出数据一致性，并在 Minecraft 客户端预览屏中使用实际动画播放器与蒙皮渲染器录制 Steve 的三视角动图。2026-10-21 完整世界验收确认 Human 运动观察器按实际输入自动切换行走、疾跑和停止，持续运动沿用同一播放请求。位置包间隙采用两 tick 停步确认，第三个静止样本才停止动作。

`meditate` 已检查循环首尾、导出一致性和双体型运行时采样：骨骼矩阵有限，root 与腿部稳定，含外层的网格不穿地；同样使用 Minecraft 预览屏录制三视角动图。前臂去除轴向扭转后的 GIF 视觉预览已获用户确认通过。2026-10-21 完整世界验收确认正式吐纳驱动外世界打坐、开脉光迹附着骨架、转头保持吐纳，以及实际移动先正式结束吐纳再切行走；逐帧盘腿关节和挂点校准仍待完成。

本轮姿态修订尚未进行 Windows Native 第三人称录制，也未宣称视觉验收通过；游戏内仍需逐段录制双客户端并核验关节、挂点与动作衔接。
