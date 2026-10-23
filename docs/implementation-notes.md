# 实现说明与参考代码

## 参考依据

本项目采用 MIT，参考下列项目的设计与文件格式，运行时使用 Fabric API、Protobuf 及 Minecraft 自带的 Gson、JOML；没有引入下列项目作为动画依赖。第三方分发说明见 [THIRD_PARTY_NOTICES](../THIRD_PARTY_NOTICES.md)。

| 项目与源码 | 借鉴内容 | 处理方式 |
| --- | --- | --- |
| [Epic Fight：Armature、Joint、SkinnedMesh、PPlayerRenderer](https://github.com/Antikythera-Studios/epicfight/tree/9d25f19c121c5e51b1db9efe01833a563706eeee/src/main/java/yesman/epicfight) | 父子关节矩阵、顶点权重蒙皮、玩家皮肤和原版可见性 | GPL-3.0；只参考设计，独立实现本仓代码，未移植源码或资源 |
| [PlayerAnimator：玩家及手持物 Mixin](https://github.com/KosmX/minecraftPlayerAnimator/tree/a17ecb85e7978e63286697bc0f5ff8a7348ef055/minecraft/common/src/main/java/dev/kosmx/playerAnim/mixin) | 原版玩家姿态作为底层、渲染和持物入口 | MIT；采用原版渲染生命周期，不接入旧动画格式 |
| [First-person Model 1.20](https://github.com/tr7zw/FirstPersonModel/tree/9cb468eac0dd87c3d4825644fb7e092b65e513b8/Shared/src/main/java/dev/tr7zw/firstperson) | 世界中补画本地玩家、隐藏头部/装备、身体后移及取消重复手臂 | MIT，Copyright (c) 2019 tr7zw；参考设计独立实现，以 Fabric 世界渲染事件复用本仓玩家渲染，不引入其配置和兼容框架 |
| [GeckoLib](https://github.com/bernie-g/geckolib)，本地核对 `geckolib-fabric-1.20.1:4.4.9` 源码包 | `BakedModelFactory`、`RenderUtils`、`KeyFramesAdapter` 的 Bedrock 几何与事件语义 | MIT；核实格式，不依赖完整动画框架 |
| [Blockbench 导出器与关键帧代码](https://github.com/JannisX11/blockbench/tree/7c92e27d237e365110030d73e92c5d1605f0d311/js) | 几何 X 镜像、旋转 X/Y 反号、顶底 UV、pre/post 和循环 Catmull-Rom | 按实际导出规则实现读取器；不把面板数值与 JSON 数值混为一谈 |

还核对了 Yarn `1.20.1+build.10` 中原版 `LivingEntityRenderer`、`HeldItemFeatureRenderer`、`PlayerEntityModel` 的调用顺序与矩阵入口。

## 最小实现

- 单个 Fabric 工程，客户端实现资源、播放与渲染，服务端实现播放登记与 Protobuf 同步，不引入动画表达式引擎、通用插件接口或跨加载器模块。
- 骨名及父子关系固定；粗细臂枢轴、方块和 UV 由交付几何决定。动画资源只在重载时读取，逐帧仅取样与绘制。
- 顶点在绑定姿态的全局像素空间保存，每节矩阵按父骨变换、位移、枢轴、ZYX 旋转、反向枢轴组合。原版六部件的矩阵先转换成本骨架的局部姿态，避免额外重写走路、蹲伏和抬手动作。
- 同一原版部件的相邻切口采用线性混合蒙皮。子段切口顶点继承父段在相同位置的权重，向子段内部 1 像素过渡至子骨；切口顶点位置由双方共同决定，肘与腕可以同时弯曲。脚尖还继承脚跟在脚踝处的权重。
- 内外层以未膨胀坐标计算相同权重。外层先按完整原版部件包围盒统一外扩，再计算分段位置，保留原版外层沿整面的像素比例。每像素细分表面使关节带具备变形顶点，UV 仍连续采样原面，不增加皮肤图集。
- `AnimationPlayer` 只做时间、层叠和事件；Minecraft 生命周期、渲染与资源重载留在 `client` / `mixin` 包，便于用纯数值回归验证骨架与播放行为。
- `AnimationPlayer.scrub` / `PlayerPlayback.scrub` 为原版进度动作提供归一化取样；同一动作 ID 连续更新游标而不重建淡入，客户端在服务端 tick 之间以 `tickDelta` 插值，停止时冻结当前游标再按 `blend_out` 淡回底层。`mine_*` 跨 0.20 秒循环接缝正向插值，`place_block*` 回绕直接接回零点，两者均不触发重开淡入。
- 时间线在客户端 tick 推进，渲染可以多次取样而不重复发送事件；回调在遍历结束后触发，允许调用方启动或停止动画。
- 服务端不读取客户端动画资源；`VanillaAnimationScheduler` 从 `handSwingProgress`、物品使用剩余/最大时间、弩拉力、潜行操作和跳跃状态生成已交付动作的 scrub 请求，客户端缺资源仍按既有回执报错。吃喝、拉弓、三叉戟、通用潜行、游泳、攀爬、骑乘、睡觉、鞘翅、扭头带动身体、闪避和格挡不生成自定义动作请求，继续使用原版姿态。动作目录中的一次性动作在 Java 目录登记对应资源时长，到期沿既有停止协议清理同步快照，循环、保持和目录外动作仍由状态或玩法显式停止。
- jump 使用单段剪辑和竖直速度归一化，落地保留 6 tick 缓冲；attack、place 的前 0.60 进度跟随挥手，结束后自走收势。mine 使用 0.8 倍独立游标，原版重挥只请求下一轮完整循环，单次点击和松手从当前游标收势。调度按原版 `UseAction` 对齐 PlayerModel 的 ArmPose：望远镜按已用时长并在 `[0.35,2.35]` 回绕，号角按已用时长，弩按真实拉力/持弩时钟，刷子按 0.8 倍时钟并在 `[0.30,0.80]` 回绕，普通物品 `use_item` 也按 0.8 倍时钟；现有专属姿态的副手均选 `_off`。
- 跳跃允许条件与着地标记分开处理：骑乘、飞行、睡眠、流体、攀爬、死亡和旁观状态不能因 `onGround=false` 进入跳跃；退出时清除整个周期，缓冲中再次起跳重新按速度采样。
- 潜行操作的腿部由原版底层或 `crouch_work` 承担，工具动作动态使用 `upper` 遮罩，底层晚于工具启动也有效。静止判断取服务端相邻 tick 的实际水平位置差；开始移动时停止锁脚支撑。支撑关键帧在制作时求解，运行时沿用已有分层，不引入 IK 系统或并行骨架定义。
- 望远镜托臂接触通过双肩共轴旋转保持；先把 Minecraft 俯仰取反到模型空间，再左乘共同旋转，避免分别增加局部欧拉 X 导致双臂分离。号角和持弩也沿用同一坐标符号转换。
- 穿戴视觉状态按装备槽边沿建立，`equip` 时间线事件切换盔甲层；头盔为 `0.00–0.28 s` 单手、胸甲为 `0.00–0.30 s` 单手、护腿为 `0.00–0.34 s` 单手，靴子为 `0.00–0.30 s` 单手，之后直到事件均取两挂点中点。护腿事件在 `2.02 s`；靴子由第一个/第二个 `equip` 事件分别恢复右/左靴（副手动作顺序交换），不使用固定第二事件时间。`ArmorFeatureRendererMixin` 对头、胸、腿和左右靴子部分分别隐藏，事件后交回原版层。
- `FirstPersonBody` 在 Fabric `AFTER_ENTITIES` 中调用同一个玩家渲染器，复用完整姿态和持物帧；渲染上下文用 `try/finally` 收回。只过滤本地这一趟的头部和近相机附加层，不修改实体位置或相机。关闭全身通道仍保留既有 `fpv` 入口；地图和望远镜使用原版专用手部显示。
- 玩家持物拦截位于 `PlayerHeldItemFeatureRenderer.renderItem`，在原版望远镜专属分支之前统一处理。仅拦截父类 `HeldItemFeatureRenderer` 会漏过正在使用的望远镜，导致全身第一人称额外绘制头部镜筒，第三人称也无法使用同帧手掌挂点。主副手和左右惯用手复用同一处理，不另加望远镜绘制路径。

## Vanilla 盔甲适配调查

2026-10-23 按源码核对；以下保留参考方案，Vanilla 盔甲已按本仓骨架独立实现蒙皮，没有引入这些项目的代码、模型或依赖。

| 项目 | 实际做法 | 对本仓的意义 |
| --- | --- | --- |
| [Epic Fight 1.20.1：HumanoidModelBaker](https://github.com/Antikythera-Studios/epicfight/blob/9d25f19c121c5e51b1db9efe01833a563706eeee/src/main/java/yesman/epicfight/api/client/model/transformer/HumanoidModelBaker.java) | 原版盔甲直接选择预制的 `helmet/chestplate/leggins/boots` 蒙皮网格；自定义 humanoid 模型才经过转换器或自定义资源 | 原版套装可以使用同一组网格配不同材质，无须为每种金属创建独立动画 |
| [Epic Fight：VanillaModelTransformer](https://github.com/Antikythera-Studios/epicfight/blob/9d25f19c121c5e51b1db9efe01833a563706eeee/src/main/java/yesman/epicfight/api/client/model/transformer/VanillaModelTransformer.java) 与 [WearableItemLayer](https://github.com/Antikythera-Studios/epicfight/blob/9d25f19c121c5e51b1db9efe01833a563706eeee/src/main/java/yesman/epicfight/client/renderer/patched/layer/WearableItemLayer.java) | 从绑定姿态取 cube、细分胸部/肢体并分配关节权重；绘制时传入玩家同一套骨骼矩阵，皮革染色/覆盖层、纹饰和附魔光效也画同一个变形网格 | 与本仓线性蒙皮路线最接近。GPL-3.0 源码仅作设计参考，不复制其代码或模型 |
| [PlayerAnimator：BipedEntityModelMixin](https://github.com/KosmX/minecraftPlayerAnimator/blob/a17ecb85e7978e63286697bc0f5ff8a7348ef055/minecraft/common/src/main/java/dev/kosmx/playerAnim/mixin/BipedEntityModelMixin.java) 与 [BendHelper](https://github.com/KosmX/minecraftPlayerAnimator/blob/a17ecb85e7978e63286697bc0f5ff8a7348ef055/minecraft/common/src/main/java/dev/kosmx/playerAnim/impl/animation/BendHelper.java) | 给所有 humanoid 模型的身体、双臂、双腿注册 Bendy-Lib 弯曲器，复制玩家到盔甲时继续传递动画来源；Bendy-Lib 在 `ModelPart.copyTransform` 中复制 cube 的变形状态 | 保留原版盔甲 cuboid 和绘制流程，但不是本仓 25 骨权重蒙皮，直接加依赖不能让两套变形自然一致 |

Bendy-Lib 的复制路径另核对本地 Maven 源码包 `io.github.kosmx.bendy-lib:bendy-lib-fabric:4.0.0` 的 `IModelPartMixin.copyTransformExtended`：遍历源/目标 cuboid，调用 `copyStateFrom`。PlayerAnimator 为 MIT；这里没有引入其实现或依赖。

原穿模原因：皮肤已替换成多关节 `SkinnedMesh`，持物读取 `PlayerPlayback.Frame`；盔甲此前只处理穿戴事件和第一人称显隐，仍复制原版六部件姿态，未读取同帧骨骼矩阵。因此弯肘、屈膝、扭腰时，皮肤与盔甲使用不同变形路径。

本轮实现限于 Vanilla 盔甲：`ArmorMesh` 通过原版 `Cuboid.renderCuboid` 读取绑定几何与 UV，按原始像素细分；权重在未膨胀位置选取皮肤分段骨，再复用 `SkinnedMesh.weights`，避免盔甲厚度挪动关节。细臂肩高来自骨架，袖子保留原版四像素宽。头盔随头刚性运动，胸甲与袖子随胸腰/肘腕，护腿与靴子随膝踝。

`ArmorFeatureRendererMixin` 只替换 `renderArmorParts`、`renderTrim`、`renderGlint` 的最终模型提交，所有材质、染色、图集 UV 包装与显隐继续由原版提供；蒙皮绘制复用皮肤的提交路径及同一个 `Frame.matrices`。帧取样移到原版 `setAngles` 之后、皮肤可见性判断之前，隐身玩家的盔甲/持物也能读取新姿态。几何按实际模型和骨架缓存，资源重载时清空；不新增动画、材质表或另一套骨架。

纹饰提交必须保留 `SpriteTexturedVertexConsumer`：其单属性方法返回底层缓冲，连续调用 `vertex(...).color(...).texture(...)` 会绕过图集 UV 映射，采样到其他纹饰区域。用户反馈彩色碎片与细线后，真实原版包装器回归复现了 UV 错误；现统一转换位置和法线后，一次向原包装器提交完整顶点。几何正确及启动无异常不能证明材质路径正确，该回归独立验证图集坐标。

验收需要包含：原版零姿态几何与 64×32 盔甲 UV 对照；粗/细臂与左右主手；潜行挖掘、望远镜、跳跃极限姿态；皮革染色、纹饰、附魔及穿戴事件；第一人称头盔隐藏和第三人称恢复。与皮肤使用相同矩阵能消除姿态失配，但弯折内侧的厚度挤压、相邻护甲片相交仍需实际游戏观察，不能承诺完全不穿模。

## 已发现并处理的边界

- 左右镜像需要交换骨名，并反转位移 X 和旋转 Y/Z；直接把旋转 X 反号会改变前后弯曲。
- Blockbench 的 step 可导出成下一帧的 `pre/post`，不能只识别 `lerp_mode: step`；循环 Catmull-Rom 还需要首尾相邻帧的切线。
- JOML 1.10.5 的 `Quaternionf.getEulerAnglesZYX` 中 X 分量分母使用了错误符号，复合旋转回归发现 20° 被还原成约 17.38°。本仓直接从矩阵提取 ZYX，覆盖 ±90° 万向锁，并与原版 `ModelPart.rotate` 结果对照。
- 模型空间的 Y 反射会翻转面绕序，因此同时调整四边形绕序和法线方向。

## 验证边界

2026-10-23 已制作本轮姿态修订与 `crouch_work`，执行 `.bbmodel` 导出一致性、60 Hz 正向运动学门禁、真实播放器叠加取样和三视角 GIF 输出。此前 36 段导出和全部 31 段非校准动画的双体型门禁通过，修复了速度门禁失败未计入总结果的问题，未放宽阈值。0.8 倍工具时钟与全身第一人称版本的 72 项 JUnit 已通过；本轮增加盔甲回归后，标准 Java 17 Gradle `test build` 的 75 项 JUnit、资源一致性、构建中指定的双体型门禁及反伪造检查全部通过。

盔甲回归使用实际交付骨架和 Minecraft 原版模型，覆盖粗/细臂、内/外层膨胀及原版混合姿态，逐顶点核对轮廓、UV 与镜像法线；同时检查腰胸、肘腕、膝踝弯折后的网格闭合、独立关节驱动，以及显隐与资源重载缓存生命周期。

此前 Windows Native 启动发现并修正了附加层 Redirect 的泛型擦除签名，第一人称版本成功进入单人世界并正常保存退出；用户反馈第一人称“还可以”。首版盔甲实现通过 Windows Native 启动，使用独立存档副本穿戴染色皮革胸甲、纹饰和附魔装备，完成第一/第三人称切换、打坐预览及隐身/恢复，并由游戏保存截图。日志确认加载 36 段动画、进入世界和执行这些操作，未记录 Mixin 或渲染异常，但随后用户截图显示彩色碎片与细线，暴露了上述纹饰 UV 提交缺陷。离线实例仍有启动时的认证 401 及既有可选类警告。首版 JAR 的 SHA-256 为 `ccf0d3934c2466fa7abca5bc8a7090f0f1d63b032e7cd405991d96d4bde492bb`，本地记录保留在 `build/native-test/armor-20261023/`。

纹饰修正版已完成 Java 17 Gradle `test build`：76 项 JUnit、资源一致性、构建门禁与反伪造检查全部通过。新增原版 `SpriteTexturedVertexConsumer` 回归在修复前失败、修复后通过；修正版也已进入 Windows Native 穿甲测试世界，日志没有新增运行异常。构建与部署 JAR 哈希一致，为 `55386f5b6898b72f8bb1a8732fc1b79abdd857a145c823e1a64a688aa0a17888`；入口和运行记录位于 `build/native-test/armor-trim-20261023/`。上述测试产物均不纳入版本控制。

望远镜重复渲染修复后再次通过同一套 76 项 JUnit 与构建检查，并使用独立 Windows Native 存档和一次性探针执行真实物品使用。主/副手 × 左/右惯用手 × 第一/第三人称共 8 种组合：旧版全部复现头部镜筒绘制；修正版 524 个采样帧全部通过，第一人称每帧镜筒模型为 0、瞄准遮罩为 1，第三人称每帧仅绘制 1 个正确左右手模式的镜筒，头部模式为 0。探针未加入产品与长期测试集；结果保存在 `build/native-test/spyglass-20261023/probe-before.json` 与 `probe-fixed.json`，对应修正版 JAR 的 SHA-256 为 `338fc0a77a741098b30f2b60f6a59c6cf13ed9df8e21aed77a6d3733e38a9ad9`。这些计数验证绘制路径，镜筒与手掌的视觉贴合仍以游戏画面为准。

截图查看工具未提供可读取的图像，因此本轮实机检查仅确认运行路径和日志，未作出盔甲穿模、材质观感或动作美观的人工视觉验收结论。数学测试和离线 GIF 证明采样/渲染工具的输入输出契约，不能代替真实游戏中的局部厚度、挂点与极端弯折观察。当前构建不是已发布版本。
