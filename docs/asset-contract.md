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
- 缺少 UV 的面不画。预览用切口面可以保留，模组通过连续的蒙皮权重连接同一原版部件的相邻分段。
- 手掌、手指、脚跟和脚尖沿用户方案切分。沿 Z 切分时，前后面是端面，只有实际接触原前后边界的分段保留；左右、顶底面按 Z 对应的纹理坐标裁切。

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

## 挂点与范围

`r_item` / `l_item` 的精确 pivot 由几何交付；运行时叠加挂点的父级变换与原版第三人称物品 display。位置仍需 `calib_item` 在游戏中校准，不把推算值标为验收完成。

当前交付的 pivot 按 Yarn 1.20.1 `HeldItemFeatureRenderer` 推算：原版在手臂局部空间依次做 `Rx(-90°)·Ry(180°)`，再平移 `(±1/16, 0.125, -0.625)` 格；换算到手臂枢轴相对的像素偏移（模型空间 Y 向下）为 `(∓1, 10, -2)`，即右臂 `(-1, 10, -2)`、左臂 `(1, 10, -2)`。模板坐标（Y 向上）下 `r_item` 为 `肩枢轴 + (-1, -10, -2)`：粗臂 `(-6, 12, -2)`，细臂 `(-6, 11.5, -2)`，左侧 X 取反；原版持物姿势为 `pitch*0.5 - 0.31415927` rad，即上臂向前 18°。该值是推算，未经游戏内校准。

本轮实现玩家皮肤、外层开关、第三人称动画、手持物与 `fpv` 手臂播放。原版和第三方盔甲、披风、鞘翅、自定义 3D 装备的分骨与兼容接入，以及旧 PlayerAnimator 动画转换，不在本轮范围内。

## 验收状态

已交付骨架模板（粗臂、细臂）、64×64 调试皮肤 `models/player_debug_skin.png` 与 `calib_zero`、`calib_axes`、`calib_item`、`calib_mirror`、`calib_layer`；正式动画未交付。读取器加载、骨名父子与 UV 数值对照原版已验证；这五个动画的 Blockbench／游戏逐帧对照与挂点校准均待执行。自动化测试仅验证解析、插值、层叠、事件、矩阵和接缝连续性，不替代皮肤、挂点或关节形变的视觉验收。角度门禁与正式动画审图由动画侧负责，运行时不裁剪校准动画的旋转。
