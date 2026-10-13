# 玩家模型与动画接口

本文件是模组与动画侧的对接契约。目标环境为 Fabric 1.20.1 / Java 17；以用户提供的 25 关节方案为基础，补齐实际文件格式。模型、正式动画与校准资源由动画侧制作。接口变动先修改本文。

## 资源位置

命名空间统一为 `kizuna_players_animation`，资源由本模组或普通资源包提供：

| 路径（相对 `assets/kizuna_players_animation/`） | 内容 |
| --- | --- |
| `geo/player_skeleton.geo.json` | Bedrock 1.12.0 几何，包含 `geometry.player.classic` 和 `geometry.player.slim` 两个条目 |
| `animations/<动作名>.animation.json` | Bedrock 1.8.0 动画；键为 `animation.kizuna_players_animation.player.<id>` |
| `player_anim_index.json` | `{"animations": [...]}`，每条列出下表字段 |

`.bbmodel` 源文件放在 `models/`，运行时只读导出的 JSON。模型与动画未交付时不打包占位模型；资源缺失会提示日志并保留原版玩家渲染。资源热重载后清空旧播放状态，避免旧骨架与新资源混用。

`models/player_skeleton_classic.bbmodel` 包含可编辑的 `walk`（1.2 秒）和 `run`（0.7 秒）循环草稿，并内嵌原版 Steve 皮肤供预览。两段分别导出到 `animations/walk.animation.json`、`animations/run.animation.json`，共用既有骨架与 UV；模组中通过 `/kpa play walk`、`/kpa play run` 手动预览，尚未按玩家移动状态自动切换。循环保持原地，只有重心左右与上下起伏，不用 root 前进位移驱动实体。Steve 贴图来自 Minecraft 1.20.1 客户端，版权归 Mojang / Microsoft，不属于本项目 MIT 授权。

`meditate` 是新增的一段打坐吐纳循环草稿，仍使用上述 classic 模板与内嵌 Steve 皮肤，导出到 `animations/meditate.animation.json`。时长 6 秒，约 2.4 秒吸气、3.6 秒呼气；双腿盘坐、双手搭膝，root 与腿部轨道恒定，腰胸、头部和手指有轻微呼吸变化。前臂仅屈肘 8°，不做轴向扭转。索引为 `full`、`hand: none`、`fpv: false`，淡入淡出各 0.6 秒。无入座、起身、事件帧、缩放或玩法接线。

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

位移单位为像素、旋转为度、时间为秒。只接受有限数值，不执行 Molang。关键帧接受常量 `[x,y,z]` 或时间字符串映射；支持 `linear`、`catmullrom`、`step`，以及 Blockbench 实际导出的 `{pre, post, lerp_mode}`。`pre` 是到达关键帧前的值，`post` 是到达该时刻后的值，因而支持原生 step 导出。

`loop` 可省略或为 `false`（一次性）、`true`（循环）、`"hold_on_last_frame"`（保持）；`animation_length` 必须为正数。缩放省略或恒为 1。未知骨名报错并忽略该骨轨道；禁止给挂点写轨道。未知事件和无效字段值报错，不猜测替代含义。

索引条目：

| 字段 | 约定 |
| --- | --- |
| `id` | 唯一动作名，与动画键后缀一致 |
| `file` | 相对命名空间的 `animations/*.animation.json` 路径 |
| `mask` | `full`、`upper`、`lower`、`r_arm`、`l_arm` |
| `priority` | 整数，同层同骨优先级大者胜出；相同值以后开始者优先 |
| `blend_in` / `blend_out` | 非负秒数；一次性结束后冻结末帧并淡出，停止保持或循环动画也使用淡出 |
| `hand` | `main`、`off`、`both`、`none`；main 模板按右主手，off 模板按左副手；none 固定解剖左右 |
| `fpv` | 布尔，仅此标记为真的动画参与第一人称手臂播放 |

层序为原版姿态 → 循环姿态 → 一次性／保持动作。只有动画实际包含、且在 mask 内的骨参与接管，缺失的通道继承下层。`upper` 包含腰、胸、头、双臂；`lower` 包含胯、双腿；单臂包含上臂至手指。只有 `full` 可以改变 root。

左主手且 `hand != none` 时，交换左右骨与单臂 mask，位移 X 反号，旋转 Y/Z 反号（镜像不是把旋转 X 反号）。`step_l` / `step_r` 事件同时交换。第一人称只取手臂骨，不继承 root、腰和胸的动画位移。

`timeline` 的值是事件名或事件名数组，白名单为 `hit`、`release`、`step_l`、`step_r`、`cancel_ok`。事件按播放时钟推进发送，跨帧、跨循环均不漏发；渲染取样不发送事件。这是客户端通知接口，不直接触发服务端伤害。

## Protobuf 控制协议 v1

唯一协议源为 [`src/main/proto/kizuna/playeranimation/v1/player_animation.proto`](../src/main/proto/kizuna/playeranimation/v1/player_animation.proto)，Protobuf 包名为 `kizuna.playeranimation.v1`。动画仓库维护字段、编号与语义；调用方固定本仓版本，从同一文件生成代码，不手写或另建平行 schema。几何、关键帧与播放参数继续由已有 JSON 提供。

当前交付协议定义、Java 生成代码和独立协议包；尚未实现消息处理器或网络传输。下表是后续接线必须遵守的执行语义，Protobuf 解码本身不校验这些业务约束。

| 消息 | 字段与语义 |
| --- | --- |
| `PlayRequest` | `request_id`、`player_uuid`、`animation_id`；对应客户端主线程的 `PlayerAnimations.play` |
| `StopRequest` | `request_id`、`player_uuid`，以及 `target` 二选一：`animation_id` 停止指定动作，或 `all: true` 停止该玩家全部动作；对应 `stop` / `stopAll` |
| `Result` | 原样回传 `request_id`，用 `code` 表示处理结果；`detail` 是可省略的诊断文字，调用方按 code 分支，不解析文字 |

- `request_id` 必须非空，由调用方在待处理请求中保证唯一。它只用于关联结果，不提供去重；重复播放同名动作会让旧实例淡出，新实例从 0 秒开始。
- `player_uuid` 是带连字符的标准 UUID 字符串，例如 `00000000-0000-0000-0000-000000000001`，指定被渲染的玩家。播放要求该玩家和动画已在接收客户端加载。
- `animation_id` 是 `player_anim_index.json` 中的 `id`，例如 `meditate`，匹配 `[a-z0-9_./-]+`；不是文件路径或完整 Bedrock 动画键。掩码、优先级、循环、淡入淡出、镜像及第一人称参数沿用索引和动画资源，不在消息里重复配置。
- 停止必须显式选择目标；未设置 `target`、空 `animation_id` 或 `all: false` 均为无效请求，不能隐式停止全部。停止不存在或已经停止的动作仍成功，未建立播放器的玩家也视为无需停止；不会因此创建播放状态。
- 一条可解码的请求处理后返回一个 `Result`。`OK` 表示播放已启动或停止已交给播放器；动画可能仍在淡入或淡出，不表示播放结束，也不表示服务端玩法成功。无效请求原样回传能取得的 `request_id`，缺失时为空。

| `Result.Code` | 数值 | 语义 |
| --- | --- | --- |
| `CODE_UNSPECIFIED` | 0 | 未设置结果，不能当作成功 |
| `OK` | 1 | 请求已执行，包含无需操作的停止请求 |
| `INVALID_REQUEST` | 2 | 缺少必需值、UUID / 动作 ID 格式错误或停止目标无效 |
| `ANIMATION_NOT_FOUND` | 3 | 播放时动画未成功加载 |
| `PLAYER_NOT_LOADED` | 4 | 播放时玩家未在客户端世界加载 |

传输层负责路由消息类型与安排客户端主线程调用；本协议没有 gRPC 服务、网络通道、广播或权限协议。时间线 `hit` / `release` 等仍使用已有事件接口，不复用 `Result` 表示。

同一 v1 内只添加兼容字段；删除字段或枚举值必须同时 `reserved` 原编号和名称，禁止复用。新增必需语义或改变既有字段含义时升级包版本。未知字段可保留并忽略，未知结果码与零值不能按成功处理；未来新增的停止目标也不能在旧接收端退化成停止全部。

### 生成与分发

```bash
./gradlew generateProto protocolZip
```

构建固定使用同版 `protoc` / `protobuf-java` 4.36.2，Java 类型为 `dev.kizuna.playeranimation.protocol.pb.PlayerAnimationProtocol` 内的 `PlayRequest`、`StopRequest`、`Result`。生成代码位于 `build/generated/sources/proto/main/java/`，不提交；模组 JAR 内含生成类及 Protobuf 运行库。

`build/distributions/kizuna-players-animation-0.1.0-SNAPSHOT-proto.zip` 包含唯一 `.proto`、`descriptors/player-animation-v1.desc` 与 MIT License；`./gradlew build` 同时生成此包。调用方可以只消费该包，从中生成所需语言的类型，无需依赖 Minecraft 客户端。Java 调用方若直接依赖本模组已有类型，就不要再次生成同名 Java 类。

## 挂点与范围

`r_item` / `l_item` 的精确 pivot 由几何交付；运行时叠加挂点的父级变换与原版第三人称物品 display。位置仍需 `calib_item` 在游戏中校准，不把推算值标为验收完成。

当前交付的 pivot 按 Yarn 1.20.1 `HeldItemFeatureRenderer` 和 `PlayerEntityModel.setArmAngle` 推算：原版在手臂局部空间依次做 `Rx(-90°)·Ry(180°)`，再平移 `(±1/16, 0.125, -0.625)` 格；换算到手臂枢轴相对的像素偏移（模型空间 Y 向下）为 `(∓1, 10, -2)`，即右臂 `(-1, 10, -2)`、左臂 `(1, 10, -2)`。细臂持物还会先将右臂枢轴向内移 0.5 像素（左臂镜像），该修正只用于持物，不改变骨架肩枢轴。模板坐标（Y 向上）下 `r_item`：粗臂 `(-6, 12, -2)`，细臂 `(-5.5, 11.5, -2)`，左侧 X 取反；Bedrock 导出后右侧 X 为正。原版持物姿势为 `pitch*0.5 - 0.31415927` rad，即上臂向前 18°。挂点与原版持物矩阵通过自动化对照，游戏内视觉校准仍待执行。

本轮实现玩家皮肤、外层开关、第三人称动画、手持物与 `fpv` 手臂播放。原版和第三方盔甲、披风、鞘翅、自定义 3D 装备的分骨与兼容接入，以及旧 PlayerAnimator 动画转换，不在本轮范围内。

细臂挂点的 0.5 像素内移是零姿态位置。原版 `setArmAngle` 在手臂旋转前修正肩部位置；运行时在原版底层姿态中将偏移差值转换回挂点局部空间，后续整体、胸部、前臂和手腕动画都带动它，避免肩部绕 Y/Z 旋转后出现偏差。该修正只作用于挂点，不移动皮肤网格。

## 验收状态

已交付骨架模板（粗臂、细臂）、64×64 调试皮肤 `models/player_debug_skin.png` 与 `calib_zero`、`calib_axes`、`calib_item`、`calib_mirror`、`calib_layer`；正式动画未交付。读取器加载、骨名父子与 UV 数值对照原版已验证；这五个动画的 Blockbench／游戏逐帧对照与挂点校准均待执行。自动化测试仅验证解析、插值、层叠、事件、矩阵和接缝连续性，不替代皮肤、挂点或关节形变的视觉验收。角度门禁与正式动画审图由动画侧负责，运行时不裁剪校准动画的旋转。

另有 `walk` / `run` 两段基础动作草稿，已检查骨骼 UUID、循环首尾、导出数据一致性，并在 Minecraft 客户端预览屏中使用实际动画播放器与蒙皮渲染器录制 Steve 的三视角动图；尚未在完整世界场景中验收，也未接入自动行走／奔跑切换。

`meditate` 已检查循环首尾、导出一致性和双体型运行时采样：骨骼矩阵有限，root 与腿部稳定，含外层的网格不穿地；同样使用 Minecraft 预览屏录制三视角动图。前臂去除轴向扭转后的 GIF 视觉预览已获用户确认通过；完整世界场景及上述盘腿关节范围仍待验收。
