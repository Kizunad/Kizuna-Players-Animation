# 药刀动作进度

更新：2026-11-11 凌晨（**开刃、切药都已用户验收通过**；已按派单第九节接进游戏，PA、BS3D、Brewing 都没提交）。
规格在派单第六节确认，第 8 节十条照建议定；同日用户定物品栏画展开（第 2 节第 3 条）。用户看过开刃、切药第一版后定「开刃可以，切药选甲」（派单第八节，本页第 8 节第 11 条）：
切药在手里把刀转平，PA 加了索引 `item_rotation`（第 6 节「已改」，已提交 `0474b77`）。
11-11 凌晨照派单第九节接线（第 6 节「接线已改」）；真实客户端截图和实测在交接目录 `handoff/brewing-models-r2-20261110/README.md` 第十一节。
派单 `~/Code/Kizuna-remote-session/handoff/i5-c4-c8-work/BREWING-MODELS-R2-TASK.md` 第四、六、八节（下称「派单」）。
服务端设计稿 `handoff/pa-cloud-anim/22-Brewing-Addon.md`（下称「设计稿」）§4.3、声音表。

| ID | 规格 | 用途 | 类型与时长 | 刀刃 | 状态 |
| --- | --- | --- | --- | --- | --- |
| `herb_knife_draw` | [规格](herb_knife_draw.md) | 主手切到药刀：甩腕开刃 | 一次性 0.60 s（12 tick） | 0.20 → 0.26 s 由合到开 | 用户验收通过（11-10），关键帧不再改 |
| `herb_knife_cut` | [规格](herb_knife_cut.md) | 切药：主手药刀、副手药材，按住右键一秒一刀 | 一次性 1.00 s（20 tick），连切重播 | 全程开；刀在手里转 −54°（`item_rotation`） | 用户验收通过（11-10），关键帧不再改 |
| 刀刃开合 `open` | [规格](herb_knife_blade.md) | 药刀模型自己的剪辑，BS3D 按进度画 | 进度驱动，不按时间播放 | 进度 0 合、1 开 | 已做（11-11 接线，未提交）；游戏内截图核过开合方向 |
| 收刀合刃 | 本页第 5 节 | 切走药刀时 | 不做动作（方案 A） | — | 已定（第 8 节第 4 条） |

两段玩家动作共同：`loop: false`、`mask: full`（加进 `upperOverlay`，移动中只取上身）、`hand: main`（左主手玩家整段镜像）、
`fpv: false`（第一人称全身沿用第三人称姿势）。

## 依据

- 设计稿 §4.3：「主手拿药刀、副手拿药材，右键，切成片｜一刀 1 s，一个药材切出一份」；声音表 `knife.cut` → `entity.sheep.shear`，音量 0.5、音高 1.2。
- 派单第四节：两种动画（刀的开合由 BS3D 播 `.bbmodel` 剪辑；玩家的甩腕开刃、切药），收刀合刃在规格里提方案；先交规格，确认后做关键帧和 GIF，用户认可后才进游戏；
  触发「照 C5 行药诀接动画的现有做法，不新开协议」。
- Bong `c01576513e`（PR #2148，`11cf45b17`，只读 `git show`）的 `herb_knife_unfold`、`herb_knife_slash`、`herb_harvest`、`herb_knife_carry_walk` 只当参考：
  - unfold：10 tick「摸刀 → 沉腕 → 抡臂 → 翻腕 → 架势」，腕留到最后一格才翻，链条末端单独占一个峰；
    它自己写明刀是半开建模、没有可转的刃骨，「刃弹开」在几何上不存在，只能靠腕的翻转暗示。我们这次有刃骨，刃真的转开；
  - slash / harvest：弯刃「吃的是拉不是砸」，钩住往回拉着割，首帧 = 末帧 = 持刀架势。
- 本仓：[验收](../animation-acceptance.md)、[共用约定](conventions.md)、[资产契约](../asset-contract.md)（`item_states`）、[过渡表](transitions.md)；格式照 [PROGRESS-witch](PROGRESS-witch.md)、[witch_brew_feed_off](witch_brew_feed_off.md)。
- 派单第八节：用户选甲，切药在手里把刀转平约 −40°（仿 `SpearItemTransforms`），PA 加运行时、门禁、渲染都认的 `item_rotation`；刃口压在药材上沿往回、往下加腕掌屈拉切，药材横拿，时间轴不变。
- 基线：PA `1aa1f7b`（工作区有第 6 节「已改」的 `item_rotation` 改动和本页四个规格，都没提交）、BS3D `09df44e`、Brewing `4bffd7f`（都干净，只读）。
  接线基线：PA `0474b77`、BS3D `09df44e`、Brewing `4bffd7f`。

## 1. 26.2 物品模型能不能拿到持有者

**能，但只有物品模型树和数值属性拿得到，特殊模型渲染器拿不到。** 以下签名都从本机 26.2 的 `minecraft-merged.jar` 用 `javap` 和 vineflower 反编译核对过：

| 位置 | 签名 | 有没有持有者 |
| --- | --- | --- |
| 物品模型 | `ItemModel.update(ItemStackRenderState, ItemStack, ItemModelResolver, ItemDisplayContext, ClientLevel, ItemOwner, int seed)` | 有 |
| 数值属性（`range_dispatch` 用） | `RangeSelectItemModelProperty.get(ItemStack, ClientLevel, ItemOwner, int seed)` → `float` | 有 |
| 特殊模型渲染器（`minecraft:special`，BS3D 的 `bs3d:placed` 就是这种） | `SpecialModelRenderer<T>.extractArgument(ItemStack)`；`submit(T, PoseStack, SubmitNodeCollector, int light, int overlay, boolean foil, int outline)` | **没有**，只拿到物品 |

- `ItemOwner` 有 `level()`、`position()`、`getVisualRotationYInDegrees()`，默认方法 `asLivingEntity()`；`Entity implements ItemOwner`，所以拿在手里时就是那个玩家或生物，展示框里是展示框实体（`asLivingEntity()` 为空）。
- 手里的物品确实带着持有者解析：原版提取 `state.rightHandItemState` 时传的是实体；PA 的 `PlayerMeshRenderer.heldItem` 直接用这份状态，穿戴动作换手持物时也调用 `updateForLiving(rendered, equip, displayContext(arm), player)`。
- 注册入口：**更正**（11-11 接线时核对反编译）`ItemModels.ID_MAPPER` 是公开的，`RangeSelectItemModelProperties.ID_MAPPER` 是 `private static final`，和条件属性那张表一样要访问器；PA 新加 `mixin/RangeSelectItemPropertiesAccess`，照已有的 `ConditionalItemPropertiesAccess` 写。
- **「切到药刀多少 tick」从哪来**：用甩腕开刃这段剪辑自己的时钟，经 PA 的 `item_states` 交给物品模型，和手臂同一个时钟、同一个混合权重，不会对不上。
  不用原版计数：`Player.attackStrengthTicker` 换物品时清零，但每次左键攻击也清零（一砍刀刃就会合上再开）；
  `ItemInHandRenderer` 的换手进度只有本机玩家第一人称有，而且 PA 第一人称画全身、不用原版手。

## 2. 刀刃开合怎么接（已定）

数据从左到右走一遍，每一环都是现成机制或小扩展，**不新开网络协议**：

1. **剪辑声明刀刃值**：PA 索引 `item_states` 加一个通道 `open`（`[0, 1]`，线性插值，和 `pull` 同类）。甩腕开刃写 `{"open": [[0.0, 0], [0.20, 0], [0.26, 1]]}`，切药写 `{"open": [[0.0, 1]]}`。
   取值规则沿用现有的：该手最高播放层里权重 ≥ 0.5 且带 `item_states` 的那层；那层没有这个通道就交回物品模型（下一条的 `idle`）。
2. **PA 新增数值属性** `kizuna_players_animation:item_state`，参数 `channel`（通道名）、`idle`（没有剪辑给值时用的值）：
   `get` 里取 `owner.asLivingEntity()`，按物品在主手还是副手查上一条的值，查不到返回 `idle`。
   现在 `AnimatedItemModels.value` 只认弓和弩（给原版谓词的混入用），要拆出一条不限物品种类的通用取值，弓弩的混入在各自调用处保留种类判断。
3. **Brewing 物品定义**：`items/herb_knife.json` 仍按 `minecraft:display_context` 选，三支都指 `herb_knife` 一份模型：
   - `gui`（物品栏）固定画 `progress: 1`，展开（用户 11-10 定）；
   - `ground`（掉落物）固定画 `progress: 0`，收刃；
   - `fallback`（手里、展示框、头上）改成 `minecraft:range_dispatch`，属性用上一条，`idle: 1`（平时手里是开的）；
     8 档阈值 0.125、0.25 … 1.0，每档一个 `minecraft:special` → `bs3d:placed`，带同值的 `progress`；`range_dispatch` 自己的 `fallback` 是 `progress: 0`。
4. **BS3D 放置模型支持开合剪辑**：
   - `PlacedModels.entries()` 现在给开、合都传 `""`（禁用）。改成开传 `null`（按约定名找 `open`，没有就不加载），合仍传 `""`；火盆、炉座没有剪辑，不受影响；
   - `bs3d:placed` 的编解码加可选 `progress`（`[0, 1]`，缺省 0），`submit` 把它传给 `asset.render(…, progress, …)`（现在固定传 0）；
   - BS3D 按 `BagAnimation.Sample(false, progress)` 取 `open` 剪辑第 `progress × 长度` 秒，没有合剪辑时合方向用开剪辑倒放，现有逻辑不用改；
   - 文档 `docs/wiki/Model-Resources.md` 第 92 行「放置模型…没有开合动画和动作音效」改成「可选 `open` 剪辑，按物品定义的 `progress` 取样」。
5. **药刀模型**：刃的两组枢轴挪到转轴 `(8, 12, 8)`，加 `open` 剪辑，见 [herb_knife_blade](herb_knife_blade.md)。

**为什么分 8 档**：开刃那一下只有 0.06 s，60 帧下画 3–4 帧，8 档每档 4°，刃尖每档约 0.47 px，看不出台阶；档数只影响物品定义的长度。

**另一种做法（不建议）**：BS3D 加一个自己的物品模型类型（`ItemModel` 而不是特殊渲染器），在 `update` 里直接拿持有者算连续进度。
少写一份 8 档的 JSON，但要碰原版烘焙流程的内部类型，BS3D 代码多、测试面大，换来的平滑在 0.06 s 里看不出来。

**收刃模型并掉**（第 8 节第 5 条）：有了剪辑以后 `herb_knife` 在 `progress: 0` 就是收刃，掉落物改指它，删掉 `herb_knife_folded.bbmodel` 和导入脚本里生成它的那一步。

## 3. 触发怎么接

照 C5 的做法：服务端调用 PA 的 `ServerPlayerAnimations.play(player, PlayerAnimationAction.…)`，PA 已有的下发和客户端补帧（`elapsed = 游戏刻 − startTick`）负责同步，不加新包。
C5 是功法目录里的 `presentation` 效果经 Human 的 `HumanTechniquePresentation` 走到同一组服务端调用（`playFrom`）；切药、开刃不是功法，Brewing 直接调用，和 Human 的 `HumanPlayerAnimation` 调 `ServerPlayerAnimations.play` 一样。

- **切药**（服务端，`HerbKnifeItem`）：
  - `use`：主手药刀且副手是药材时 `player.startUsingItem(hand)`，返回 `InteractionResult.CONSUME`（不挥手）；服务端同时 `play(HERB_KNIFE_CUT)`；
  - `getUseDuration` 返回 20，`getUseAnimation` 用 `NONE`（PA 调度照常放 `use_item`，被优先级更高的切药盖住）；
  - `onUseTick` 在用了 11 tick（0.55 s，刀穿过药材那一刻）时结算：扣药材、刀耗损 1、给成品、放 `knife.cut`；
  - 用满 20 tick 正常结束；中途松手 `releaseUsing` 里 `stop(HERB_KNIFE_CUT)`，11 tick 前松手不出成品；
  - 按住右键：26.2 `Minecraft.handleKeybinds` 在「按着使用键、`rightClickDelay == 0`、没在使用」时下一 tick 再次 `startUseItem`（反编译核对过），所以一秒一刀自然连上；使用中左键会被原版吞掉。
- **甩腕开刃**（服务端，Brewing 每 tick 检查在线玩家）：主手物品从「不是药刀」变成「药刀」时 `play(HERB_KNIFE_DRAW)`；玩家上线、换维度时先记下当前主手不播。副手拿药刀不播。
  11-11 起（第 8 节第 13 条）：本人客户端每 tick 末尾做同一检测并本地播放，服务端那份改调 `playForTrackers`，只给追踪者。
- **本机一两 tick 的闪动**（风险，第 8 节第 3 条）：本机切快捷栏是客户端立即生效的，剪辑要等服务端回来（单人约 1 tick）。这一两 tick 里没有剪辑，属性返回 `idle`，刀先是开的，剪辑到了变合，再在 0.20 s 甩开。
  旁观者的装备包和播放包同一 tick 发，基本看不到。建议先按服务端做，录屏看；看得出来再选：
  - 只对开刃改成客户端各自检测并本地播放（每个客户端看到某玩家主手换成药刀时自己播，不经服务端，零延迟，旁观者也一致；PA 客户端 `PlayerAnimations.play` 现在只给调试命令用）；
  - 或 PA 属性加 `pending` 值：持有者主手刚变成该物品、还没收到剪辑的头几 tick 返回 `pending`（0）。要在 PA 客户端记每个实体主手的变化时刻，首次看见的实体不能算「刚换」。

## 4. 共用约束

**K1 起止。**
- 甩腕开刃：首帧、末帧都是原版持物位，取 [use_item](use_item.md) 导出资源 0 s 的逐骨值（含手指），不取设计值；速度为零。
- 切药：首帧、末帧都是「待切位」（[herb_knife_cut](herb_knife_cut.md) 第 3 节），连切时新旧实例在同一姿势上交接；从持物位进出靠淡入 0.20 s、淡出 0.25 s。
- 不动 `root`；首尾两端逐帧出关键帧（五脏诀制作实测：均匀 Catmull-Rom 在疏密不匀的键之间会抬高短段末端速度）。

**K2 手。** 只认主手：设计稿 §4.3 写的是主手药刀、副手药材；现在的 `HerbKnifeItem.use` 两只手都认，接线时改成只认主手（第 8 节第 2 条）。右手全程握刀（架势拳，切药收紧 4–6°）；切药时左手握药材（握拳）。

**K3 全身协调**（记忆 `animation-quality-bar`）：两段都是全身一次性动作，≥ 10 根骨在动，过胯腰胸头躯干门禁；腿用脚底锁定求，脚不滑。

**K4 预备、过冲、错峰**：主链 `腰 → 胸 → 右上臂 → 右前臂 → 右手`，最快那一下前有 ≥ 3° 反向、后有 ≥ 3° 过冲；`phases` 只框主链那一段。腕最快不超过 720°/s。

**K5 刀刃和手的时刻对齐**：刀刃值只来自剪辑的 `open` 轨道，开刃时刻写在索引里，不写进 Java；改时刻只改索引，物品模型跟着走。

**K6 classic、slim 两套**都出。PA 每个 ID 只有一份动画资源，两种体型共用同一套轨道（[验收](../animation-acceptance.md)「slim 使用同一动画轨道」）；slim 手臂细 1 px、肩肘腕和持物挂点低 0.5 px、内移 0.5 px，所以切药求解时同一组姿势在两副骨架上一起查：刀切进药材的深度、刀柄不进药材、臂对躯干，门禁和 GIF 两套都跑。

**K7 第一人称可读**：PA 第一人称画全身、镜头是原版眼位。切药原来估两只手在 y 17–21，实做为了刀不挡脸放低到 y 14.5–17.6（右拳挂点 z −7 到 −8.5，药片中心 `(−0.55, 12.97, −5.31)`）。
按眼位 y 25.9、z 0 粗估，低头约 33° 药材才进画面下沿（[herb_knife_cut](herb_knife_cut.md) 第 6 节）；**游戏内实测 45°**，50° 药材露得最多（上半截），classic 60°、slim 70° 起被前倾的躯干顶面挡住（同节「游戏内实测」）。11-11 用户选乙、切药第一人称不取躯干轨道后，30° 药材进画面，一直低到 90° 都看得到（同节「改后实测」）。甩腕开刃在持物位附近，平视就看得到刀。

**K8 管线**：新动画从 `~/Code/MotionBricks-Intergrate-Test` 出，照行药诀的 `author_witch_pa.py` / `witch_pa_data.py` 写一套药刀的作者脚本和数据（事件、峰值时刻），`.bbmodel` 为准，导出后过 `scripts/analyze_animation.py`。
Bong 的四段只看节奏和姿态，不拷关键帧。

## 5. 收刀合刃怎么表现

原版切快捷栏是瞬间换手持物，没有「收」的过程；PA 第一人称全身也不画原版的换手下沉。三个方案：

| 方案 | 做法 | 代价 |
| --- | --- | --- |
| **A（已定）不做动作** | 切走就没了；收刃只在掉落物里看得到（物品栏按用户要求画展开） | 没有 |
| B 换手前收一下 | 切走药刀时，手里继续画旧药刀约 0.3 s，放一段 `herb_knife_stow`（刃合上、手往右胯一收），之后才出现新物品 | PA 要加通用的「手持物延后替换」（和穿戴动作的 `EquipVisual` 同类但方向相反）；滚快捷栏时每格都要等 0.3 s，手感变钝 |
| C 主动折刀 | 加一个操作（例如潜行右键）把刀折起来，折着不能切，状态存在物品上 | 设计稿没有这个玩法；要加物品状态和交互，等于新功能 |

A 的理由：B 的延迟和快速换物冲突，C 是新玩法。主会话定 A；验收时用户如果觉得「刀凭空消失」突兀，再考虑 B。

## 6. 要改的文件

**PA 已改（11-10 夜，派单第八节准许，只限 `item_rotation` 通道和它的测试、门禁、渲染；未提交，等主会话审）**
- 新 `animation/ItemRotation.java`：索引 `item_rotation` 的解析和取值（record，相邻键 smoothstep，首键前取首值、末键后保持；只认 `hand: main/off`，键格式、时间不对拒收）；
- `animation/AnimationEntry.java`：多一个 `itemRotation` 字段，`readItemRotation` 在资源边界读一次；
- `animation/AnimationPlayer.java`：`blendSockets` 按层混合持物挂点旋转。驱动这条手臂的最高层拥有挂点，那层没写字段就转回原版握法，随层淡入淡出；左撇子随剪辑镜像（`blockbenchRotation(raw, mirror)`）；`itemSocket(hand)` 给渲染取值；
- 新测试 `src/test/java/dev/kizuna/playeranimation/ItemRotationTest.java` 四条：smoothstep 和两端保持、坏轨道和没绑手拒收、主手转右挂点且左撇子镜像、挂点跟着拥有手臂的层并随它淡出；
- `scripts/animation_analysis.py`：`attach_item_rotation` 把索引字段注入采样用的动画字典，`AnimationSampler` 把它当 `r_item`/`l_item` 的旋转轨道（和运行时同样的 smoothstep、X/Y 反号）；
- `scripts/analyze_animation.py`：`validate_item_rotation` 校验，门禁采样前注入，挂点旋转一起进角速度、角加加速度检查；
- `scripts/render_animation_gif.py`：预览时挂在挂点上的道具跟着转；
- `scripts/test_animation_antiforgery.py`：40° 用 0.2 s 放行、用 0.05 s 被 720°/s 拒绝，挂点前方的点跟着转、挂点本身不漂；
- `docs/asset-contract.md`：`item_rotation` 一行。
- 验证：`flock /tmp/kizuna-gradle.lock ./gradlew --no-daemon test checkAnimationAntiForgery checkAnimationAssets` BUILD SUCCESSFUL，125 个测试 0 失败（`ItemRotationTest` 4/4），防伪回归通过，导出一致性 156 段。

**接线已改（11-11 凌晨，派单第九节；三仓都没提交，等主会话审）**

下面原是接线前列的计划，都已照做。和计划不同的：PA 登记数值属性要新加访问器 `mixin/RangeSelectItemPropertiesAccess`（第 1 节更正）；
`.bbmodel` 只改 classic，slim 和 classic 共用同一份动画 JSON（门禁两副骨架结果相同）；
Brewing GameTest 记录发给假玩家的 PA 包时只留 `player_uuid` 是该玩家自己的、按 `request_id` 去重（同一场测试里别的假玩家的动作和开始追踪时的补发都会到）。

**PA**
- `animation/ItemStates.java`：`Channel` 加 `OPEN("open", LINEAR)`；
- `client/AnimatedItemModels.java`：拆出不限物品种类的取值；
- 新 `client/AnimationItemStateProperty.java`（数值属性，`channel`、`idle` 两个字段），客户端初始化时登记到 `RangeSelectItemModelProperties.ID_MAPPER`；
- `animation/PlayerAnimationAction.java`：`HERB_KNIFE_DRAW("herb_knife_draw", 12)`、`HERB_KNIFE_CUT("herb_knife_cut", 20)`；
- `animation/AnimationPlayer.java`：`upperOverlay` 加 `herb_knife` 前缀；
- 索引两条（切药带 `item_rotation` 54）、动画 JSON 两段、`.bbmodel`（classic、slim）、`scripts/analyze_animation.py` 的 `ITEM_STATE_CHANNELS` 加 `open`、两个 ID 进全身和握持名单；
- 文档：`asset-contract.md` 的 `item_states` 一行、`transitions.md` 加下表四行、本页和两段规格回填实测；
- 测试：`ItemStates` 解析 `open`、属性无剪辑返回 `idle`、有剪辑按手取值；动作集合约测试补两个 ID。

**BS3D**
- `PlacedModels.entries()` 开剪辑传 `null`；`PlacedItemModels` 编解码加 `progress`、`submit` 传进度；
- 顺手：`getExtents` 按已加载模型的外框报包围盒（第二轮报告里掉落药刀浮高 0.16 格的原因，第 8 节第 6 条）；
- 测试：编解码带不带 `progress`、放置模型带 `open` 剪辑时按进度取样；wiki 两处。

**Brewing**
- 导入脚本：刃两组枢轴改 `(8, 12, 8)`、生成 `open` 剪辑，参数记进 `sources.json`；删掉生成收刃模型的一步和 `herb_knife_folded.bbmodel`；
- `items/herb_knife.json`：`gui` 进度 1、`ground` 进度 0、其余 `range_dispatch` 八档（第 2 节第 3 条）；
- `HerbKnifeItem`：只认主手、改成按住使用（第 3 节）、放 `knife.cut`；每 tick 主手检测播开刃；
- 依赖：`fabric.mod.json` 加 `kizuna_players_animation`，`settings.gradle` 组合 PA（照 Threatening），`fabric` 模块 `implementation` PA；
- GameTest：现在的切药测试直接调 `knife.use` 一次就出成品，要改成真实使用（开始使用 → 推进到第 11 tick 出成品；第 10 tick 松手不出），再加开刃播放的断言。

**过渡表要加的四行**（落地时写进 `transitions.md`）：

| 来源 | 目标 | 淡入/淡出 | 专门过渡 | 触发与约束 |
| --- | --- | ---: | :---: | --- |
| stand / walk / run / sneak / sneak_walk | herb_knife_draw | 0.08 / 0.20 | 否 | 服务端看到主手由非药刀变成药刀；移动中只取上身 |
| stand / walk / run / sneak / sneak_walk | herb_knife_cut | 0.20 / 0.25 | 否 | 主手药刀、副手药材开始使用；连切同 ID 重播，新旧实例在待切位交接 |
| herb_knife_draw | herb_knife_cut | 0.20 / 0.25 | 否 | 开刃没播完就右键：切药接管，刀刃值在切药权重过 0.5 时跳到 1 |
| herb_knife_draw | attack_main | 0.04 / 0.14 | 否 | 开刃中左键：挥砍盖住，开刃在下面继续走，刀刃照开刃轨道 |

## 7. 探针

切药求解（11-10 夜）的探针在 `~/Code/Kizuna-remote-session/handoff/herb-knife-anim-20261110/probes/`，不入库：`cutsolve.py`（20 个参数，两副骨架一起算罚分，Nelder–Mead）、`cutcheck.py`（逐 1/60 s 量导出资源的刀对药材、挡脸、穿模）、`cutkeys.py`（接触表时刻实测），求解记录 `cut-solutions-20261110.md`。

下面是做规格时的初探。一次性脚本放在交接目录 `~/Code/Kizuna-remote-session/handoff/brewing-models-r2-20261110/anim-probe/`，不入库（`chain.py` 物品挂点链、`probe.py` 刀点和持物位、`probe_cut4.py` 待切位初探）；FK 用原型 `handoff/kpa-door-breaker-proto/proto.py`（只读导入），骨架换成 PA main 的 `player_skeleton_classic.bbmodel`。
物品挂点链照 PA `Rig.itemTransform`（`toVanilla` 后绕 X −90°、Y 180°）、26.2 `ItemTransform.apply`（平移 → XYZ 旋转 → 缩放 → 平移 −0.5）、BS3D `PlacedModels.place`（朝北时不转，原点 `(8, 0, 8)`）。
先用原版 `handheld` 剑的显示变换校验这条链：剑尖算出来在 `r_item` 局部 `(0, −0.96, −12.3)`，剑首 `(0, 1.96, 4.3)`，和共用约定里的估计 `(0, −1.2, −13.5)`、`(0, 2.2, 5.5)` 同向同量级（约定取的是贴图角点）。

| 点（`r_item` 局部，px） | 开 | 收（刃转 −32°） |
| --- | --- | --- |
| 转轴（刀柄前端） | `(0, 3.28, −2.87)` | 同左 |
| 刃口最低点 | `(0.08, −2.06, 1.33)` | 刃收进柄侧，刃口 y 0.65–2.21 |
| 刀柄尾 | `(0.68, 1.38, 3.85)` | 同左 |

- 拳到转轴 4.36 px。开着时刃从转轴往后下垂，和刀柄约 36°；收刃时贴着刀柄。**更正**（11-10 做关键帧时对着模型方块核过）：刃口（浅色 `blade_edge` 方块）在刃弯的**外侧**、朝下前，不在凹侧；刃尖弯回到拳头下沿。原来写的「刃口在凹侧、钩住往回拉时刃口朝人」是照 Bong 脚本的文字说明推的，不对，切药因此改成「刃口压在药材上沿往回拉」（派单第八节）。
- 刃绕 `r_item` 局部 X 正转 32° 是由合到开：手握着刀时，刃尖由贴柄转向前下方。
- 原版持物位（`use_item` 0 s）：拳 `(−6.1, 13.8, −5.7)`，转轴 `(−6.2, 18.0, −6.8)`，刃口最低点 `(−6.0, 11.4, −5.5)`。刀柄从拳往前上立起，刃挂在拳前，最低点离大腿前面约 3.5 px。
- 副手毒马铃薯（原版 `item/generated` 第三人称变换）：中心在 `l_item` 局部 `(0, 1, −3)`，约 5.5 px 见方的薄片，片的法线是局部 Y。
- 切药待切位初探：左上臂 `(0, 30, 0)`、左肘 60°、左手 `(0, 0, −35)` 时药材中心 `(1.2, 19.5, −6.5)`，药片前倾约 38°（原版手持物在前举前臂上本来就前倾）。
  右手求「转轴在药材顶边远侧上方 0.8 px、前 1.2 px，刃垂在远侧」：位置误差 ≤ 0.1 px 可解，但右腕顶到 ±60° 边上。
  这个初探没求出可用姿势；实做时刀改成在手里转平，药材横拿、再低约 6.5 px，见 [herb_knife_cut](herb_knife_cut.md) 第 3 节。

只查了位置和可达，没算角速度、错峰、重心。规格里的角度都是设计值，以制作时原型求解和导出资源的门禁实测为准。

## 8. 决定（11-10 主会话照建议定，派单第六节）

| # | 问题 | 定的做法 |
| --- | --- | --- |
| 1 | 刀刃接法：PA `open` 通道 + 数值属性 + `range_dispatch` 八档 + BS3D 放置模型 `progress`（第 2 节），还是 BS3D 自己的物品模型类型 | 前者 |
| 2 | 只认主手药刀、副手药材（照设计稿，现代码两手都认） | 只认主手 |
| 3 | 开刃由服务端触发（照 C5），本机切换时可能闪一两 tick 开刃 | 服务端触发；录屏看闪动，明显再改客户端本地播放 |
| 4 | 收刀合刃 | 方案 A，不做动作 |
| 5 | 有了 `open` 剪辑后删掉收刃模型 `herb_knife_folded` | 删；掉落物改用 `herb_knife` 进度 0，物品栏固定进度 1（用户定展开） |
| 6 | BS3D 放置物品按模型外框报包围盒（掉落药刀浮高 0.16 格） | 这次一起改 |
| 7 | 切药结算放在 0.55 s（刀穿过药材）还是 1.0 s（用满） | 0.55 s，看到的那一刀就是出成品的那一刀；节奏仍是一秒一刀 |
| 8 | 开刃要不要声音（设计稿声音表没有） | 先不加，验收时再看；要加就指向原版声音事件 |
| 9 | 开刃没播完就右键切药，要不要拦 | 不拦，切药把刀刃值直接给 1 |
| 10 | Brewing 加 PA 硬依赖（照 Threatening 的组合方式） | 加；Kizuna 附属之间都是硬依赖，不写缺 PA 的分支 |
| 11 | 切药刃口够不着药材（刃只张开 36°、刃口在外侧）：甲 PA 按剪辑转刀、乙改 Brewing 显示变换、丙只靠手腕 | 甲（用户 11-10「切药选甲」，派单第八节）；实做转 −54°，比派单的约 −40° 多 14°，换右腕 ≤ 45° |
| 12 | 第一人称切药前倾 43°，躯干顶面挡住药材（11-11 实拍） | 用户 11-11 选乙：切药索引 `fpv_torso: false`，全身第一人称不取胯腰胸头，只留手臂和刀、药材；第三人称、其他玩家、关键帧、门禁不变 |
| 13 | 开刃第一 tick 刀开着（第 3 条实拍看得出） | 改本人客户端本地播放：Brewing 客户端在主手换成药刀的 tick 用 `PlayerAnimations.play`，服务端改 `playForTrackers` 不发本人；持物状态在剪辑从原版淡入时首帧即用剪辑值（`asset-contract.md` `item_states`）；换走药刀照旧播满再淡出 |

## 9. 没做的、未测的

- 开刃、切药用户 11-10 验收通过；PA `item_rotation` 已提交 `0474b77`。第 6 节「接线已改」三仓都没提交。
- 游戏内实测过（2026-11-11 真实客户端截图，单人游戏，classic、slim 皮肤各一轮，截图和读数见交接目录 README 第十一节）：
  - 开刃 0.10 / 0.23 / 0.60 s 合 → 半开 → 开，0.23 s 刃根没看到缝；切药刀在手里转平、压在副手毒马铃薯上沿，`item_rotation` 方向对；
  - 刀刃 `open` 由剪辑驱动：物品栏、展示框、人偶手持展开，掉落合上；
  - 第一人称（K7）：低头 45° 药材进画面（粗估 33°），classic 60°、slim 70° 起被躯干顶面挡住；
    第 8 节第 12 条改后（交接目录 README 第十二节）：30° 药材进画面，低到 90° 都看得到；
  - 第 3 节的闪动：按快捷栏键后 1 个客户端 tick 画原版握姿、刀开着，下一 tick 剪辑到、刀合上再开。
    第 8 节第 13 条改后：按数字键后第 1 tick 剪辑已在播、刀是合的；服务端完全不知道换刀时本机照样播；晚 6 tick 才到服务端也不重播。
    滚轮换刀在 tick 外改槽位，到下一 tick 末尾检测前还会有不到 1 tick 的帧按原版握姿画开着的刀，主会话 11-11 接受。
- 未测：连切三刀的交接、第一刀淡入和最后一刀淡出；`item_rotation` 随切药淡出转回原版握法（截图只有定格和一刀的真实触发）；
  左撇子镜像（只有单元测试）；联机时别的玩家看到的开刃延迟（广播由 Brewing GameTest 覆盖）；刀刃剪辑在 Blockbench 里的预览方向（见 [herb_knife_blade](herb_knife_blade.md) 第 3 节）。
- PA 完整装配 GameTest 当时被 Brewing 已有的 `BrewEffectResume.resume` 崩溃挡住（C4e 日志同栈），接线用 PA 单独装配验证；C6a 修正后 11-11 完整装配 6/6 通过。
