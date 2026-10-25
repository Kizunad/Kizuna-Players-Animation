# Kizuna Players Animation

面向 Minecraft 玩家的多关节模型与动画模组，目标是让玩家模型的关节和动画更便于在 Blockbench 中制作与调整，改善 `.bbmodel` 制作体验。

## 当前状态

运行环境为 **Minecraft 1.20.1 / Fabric / Java 17**。已实现客户端动画运行时和服务端区域同步，包含校准资源与基础移动/吐纳动画；玩家动作正在按验收规则重做。

- 25 个关节与 2 个手持挂点；粗臂、细臂分别读取 Bedrock 几何。
- 玩家自身 64×64 皮肤、逐面 UV、原版外层开关与 CPU 蒙皮。
- Bedrock 动画的线性、Catmull-Rom、step / pre-post 插值、循环与保持末帧。
- 原版姿态、循环姿态、一次性动作分层；按骨掩码、优先级及淡入淡出混合。
- 左主手镜像、时间线事件、第三人称持物、全身第一人称和独立 `fpv` 手臂入口。
- Protobuf 播放／停止／回执、本人及实体追踪范围同步、进入范围时按原始起点补发。

已打包粗臂 / 细臂骨架（`models/player_skeleton_*.bbmodel` 与 `geo/player_skeleton.geo.json`）及 55 段动画（含两段视线层）：五个校准、三段移动/吐纳基线，以及攻击、跳跃、穿戴、挖掘、放置、望远镜、号角、弩、刷子、普通使用和潜行操作支撑资源。关键帧以 classic `.bbmodel` 为唯一来源，原版六大部件姿态仍作为底层；打包完成不代表已通过游戏内视觉验收。

2026-10-23 修订：跳跃双腿错峰收伸；挖掘加重下砸；望远镜改为另一只手托住前臂并随俯仰联动；弩装填扩大拉弦肘的轮廓。按住潜行键静止操作工具时，`crouch_work` 提供前后错脚、重心后坐的支撑，工具动作只覆盖上半身，开始移动或结束操作时退出支撑。闪避、通用单手格挡、双手格挡及通用手持单手/双手格挡资源仍未制作。

2026-10-26：通用潜行改为自定义资源：静止 `sneak`（3 秒呼吸循环）与移动 `sneak_walk`（1.6 秒两步循环），与 `crouch_work` 由调度三选一；屈膝下蹲、胯后坐、脊柱分段前倾，脚底沿用原版潜行约 2.1 像素的下沉，头顶高度与 1.5 格碰撞箱一致。进食 `eat` 与饮用 `drink`（主副手）按已使用 tick 取样：进食在 0.40–0.80 秒循环咀嚼，咬下时刻对齐原版每 4 tick 的进食音效；饮用随 32 tick 逐渐仰头。按进度循环的动作跨接缝时客户端沿正方向插值，不倒放。拉弓 `bow_draw`（主副手）按原版拉弓进度（20 tick 满弓）拉弦：上身侧转 40°、弓臂朝向与原版一致、弦手拉到下颌下方，满弓后微颤保持，松手发射时播 0.3 秒撒放尾段；拉弓时双臂绕肩线跟随视线俯仰。游泳 `swim`（自由泳划臂、身体滚转、六次打腿、单侧换气）的划臂相位与原版肢体时钟同一公式（全速 26 tick 一周期）。攀爬 `climb` 按实际竖直位移驱动（每爬 1 格一个手脚交替循环，下滑倒放、挂住停住），抓握手与踩踏脚相对梯子不滑；攀爬中攻击/挖掘/放置只接管动作手臂。骑乘 `ride`（跨坐握缰随坐骑起伏）、睡眠 `sleep`（仰卧全身轻微呼吸）、鞘翅 `elytra`（流线型滑翔、双臂随气流颤动）在对应原版状态下循环播放，身体朝向与放倒仍由原版渲染矩阵负责。三叉戟蓄力 `trident_charge`（主副手）10 tick 后引到位（原版可投掷阈值），保持段掂戟，投掷时播 0.45 秒全身跟随，激流不播。持盾格挡 `block_shield`（主副手）0.25 秒（原版 5 tick 格挡生效）举盾到位，持盾手朝向与原版格挡姿势一致，之后压低重心呼吸保持。所有动作资源原先会覆盖原版转头/俯仰，现由加性视线层 `head_follow` / `head_follow_pitch` 在渲染时按原版视线角度叠加，转头带动胸腰、低头带动胸。其余剩余动作的设计稿与进度见 [`PROGRESS-remaining-vanilla.md`](docs/animation-specs/PROGRESS-remaining-vanilla.md)。

工具挥动、刷子和普通使用动作的视觉速度调整为 0.8 倍；默认挖掘循环由 4 tick 延长到 5 tick，保留完整动作和连续收势，单次点击不会多砸一次。真实破坏速度、使用判定与弓弩装填不变。第一人称默认复用第三人称身体和持物动画，低头可观察身躯；用 `/kpa firstperson false` 关闭，`/kpa firstperson true` 开启。地图/望远镜保留原版专用显示，睡眠、游泳/爬行、鞘翅等特殊视角暂不走全身通道，详见[资源与渲染契约](docs/asset-contract.md)。

Vanilla 盔甲已接入身体同一帧的多关节蒙皮，袖子、胸腰、护腿和靴子随关节变形，修正原先皮肤与盔甲姿态不一致的穿模来源。原版厚度、UV、皮革染色/覆盖层、纹饰、附魔光效及穿戴显隐继续保留；几何只在首次使用或资源重载后烘焙。[盔甲适配说明](docs/implementation-notes.md#vanilla-盔甲适配调查)记录参考来源、实现和验证边界。极端弯折下的局部厚度与相邻护甲片相交仍需游戏内观察。

## 构建与运行

```bash
./gradlew build
./gradlew runClient
```

构建产物为 `build/libs/kizuna-players-animation-0.1.0-SNAPSHOT.jar`，内含双体型骨架及动画资源，无需外置补丁资源包。客户端和服务器均须安装。Meridian 的 Framework、Human、UI、Items、Vanilla-Damage、Vanilla-Healing、Human-UI、Progression 与本包在各自适用端整套必装，开发装配由 Framework 的 `scripts/full-assembly.gradle` 统一提供。第三方分发许可见 [THIRD_PARTY_NOTICES](THIRD_PARTY_NOTICES.md)。

模型、动画和索引的路径、骨名、坐标及播放参数见 [资源接口](docs/asset-contract.md)。接口有变化时先修改这份文档；资源包可覆盖同一路径，F3+T 重载后旧播放状态会清空。

## 调试与调用

| 命令 | 用途 |
| --- | --- |
| `/kpa list` | 列出成功加载的动画 |
| `/kpa play <id>` | 在本地玩家上播放指定动画，支持补全 |
| `/kpa stop` | 按索引的淡出时间停止本地玩家动画 |
| `/kpa_server play <player> <id>` | 管理员在服务端启动区域同步动画 |
| `/kpa_server stop <player> [id]` | 管理员同步停止指定动作；省略 id 停止全部 |

基础行走与奔跑可手动预览：`/kpa play walk`（1.2 秒一周期）、`/kpa play run`（0.7 秒一周期）。Human 宿主复用实际运动观察器，仅在静止、行走、疾跑或权威吐纳状态改变时切换动作；吐纳期间移动先正式结束会话，再切到运动动画。视角转动不会结束吐纳。全身第一人称与第三人称均可观察全身动作，索引的 `fpv=false` 仅限制独立手臂入口。跳跃按竖直速度采样、落地保留 6 tick 缓冲，不按 tick 重启；骑乘、飞行、流体和攀爬等状态退出跳跃并清除缓冲。

打坐吐纳通过 `/kpa play meditate` 预览：盘腿静坐、双手搭膝，6 秒一周期，约 2.4 秒吸气、3.6 秒呼气。双腿与 root 固定，腰胸舒展、头部与手指轻微跟随；不含入座、起身或特效。停止使用 `/kpa stop`，索引中的 0.6 秒淡入淡出仅用于姿态过渡。

在 Blockbench 打开 [`models/player_skeleton_classic.bbmodel`](models/player_skeleton_classic.bbmodel)，进入 Animate 面板即可播放和编辑 `animation.kizuna_players_animation.player.walk` / `run` / `meditate`。该文件内嵌原版 Steve 皮肤；运行时继续使用玩家自己的皮肤。Steve 贴图来自 Minecraft 1.20.1 客户端，版权归 Mojang / Microsoft，不属于本项目 MIT 授权。

客户端主线程可以调用 `dev.kizuna.playeranimation.client.PlayerAnimations`：

```java
PlayerAnimations.play(playerUuid, "calib_axes");
PlayerAnimations.stop(playerUuid, "calib_axes");
PlayerAnimations.stopAll(playerUuid);
PlayerAnimations.EVENT.register((player, animation, event) -> {
    // 在玩法接入侧处理时间线通知；此回调不代表服务端伤害授权。
});
```

客户端入口只影响本机已加载的玩家。多人同步由服务端主线程调用 `dev.kizuna.playeranimation.server.ServerPlayerAnimations`：

```java
String requestId = ServerPlayerAnimations.play(serverPlayer, "meditate");
ServerPlayerAnimations.stop(serverPlayer, "meditate");
ServerPlayerAnimations.stopAll(serverPlayer);
ServerPlayerAnimations.RESULT.register((viewer, result) -> {
    // 每位接收者独立回报；这是客户端诊断，不能作为玩法成功判据。
});
```

区域沿用 Minecraft 实体追踪范围，另包含目标本人；不向其他维度或远处玩家广播。进入范围时补发当前登记动作与原始起始 tick，客户端接着现有进度播放，不补触发过去的时间线事件。停止、离线、换维度、重生及服务器关闭会清理对应登记状态。`PlayerAnimationAction` 目录中的一次性动作（即使通过字符串 ID 调用）按其资源时长由服务端自动发出停止并清理登记；循环、保持以及目录外自定义动作仍须在状态结束时显式停止。

追踪补发排到服务端 tick 末尾，避免早于实体生成包。通道协商允许等待最多 600 tick，协商中的快照与诊断回执保留待发；完成后缺少必需通道立即报错并断开连接，等待超时同样报错。未播放任何动作的连接也检查接线，不提供缺包运行模式。

`hit`、`release` 等事件仍只在客户端 tick 推进时通知，不直接触发服务端伤害。F3+T 会清空本地播放状态，需重新播放或离开并重新进入追踪范围恢复。同步精度沿用 Minecraft 世界时钟，不承诺各客户端逐帧一致。

## Protobuf 协议

本仓维护唯一的 [`player_animation.proto`](src/main/proto/kizuna/playeranimation/v1/player_animation.proto)，定义 `PlayRequest`、`StopRequest` 和 `Result`。停止请求明确区分指定动作和全部动作，结果按 `request_id` 关联。Fabric 通道 `kizuna_players_animation:play`、`:stop` 向客户端发送原始 Protobuf 字节，`:result` 回传处理结果；没有额外 JSON 或手写二进制消息。字段与执行语义见 [控制协议 v1](docs/asset-contract.md#protobuf-控制协议-v1)。服务端 API 和管理员命令触发同步，不开放客户端任意广播接口。

运行 `./gradlew generateProto protocolZip` 可生成 Java 类型及 `build/distributions/kizuna-players-animation-0.1.0-SNAPSHOT-proto.zip`；普通 `build` 也会生成此包。调用方从协议包中的同一份 `.proto` 生成类型，不另行维护协议副本。

## 验证与范围

`./gradlew test` 覆盖解析、未知输入、分层、镜像、插值、跨循环事件、矩阵以及内外层切口连续性。除数学夹具外，还直接检查已交付的双体型资源：左右手独立弯指后的内外层没有裸露切口，持物矩阵在混合摆臂姿态下与原版一致（含细臂肩部的 0.5 像素修正）。

协议测试固定 v1 消息的二进制样例，检查字段编号、停止范围、可选起始 tick、未知结果码和字段保留；另检查网络载荷边界、晚入范围进度与历史事件抑制。

`./gradlew runGametest` 使用真实服务端玩家核对主副手放置与攻击选择；首次运行须在 `build/gametest/eula.txt` 确认 Minecraft EULA。`./gradlew runClientTest` 先运行 GameTest，再复用其世界启动真实客户端，检查穿戴的晚加入、重复同步、新播放、靴子分侧与左主手镜像，并检查历史事件不补发。两者沿用整套模组装配，运行前需构建相邻仓库的安装产物；客户端回归单独报告于 `build/clienttest-results.txt`。无显示器的 Linux 环境可执行：

```bash
timeout 240s xvfb-run -a -s "-screen 0 1024x768x24" \
  env JAVA_HOME="/usr/lib/jvm/java-17-openjdk-amd64" LIBGL_ALWAYS_SOFTWARE=true \
  "./gradlew" runClientTest --console=plain
```

回归包含全部资源导出一致性、三段基线及本轮八段动作的双体型门禁、`AnimationPlayer` 状态切换/叠加和运行时测试。2026-10-23 已用 Java 17 直接编译全部主源码与测试并运行 JUnit，67 项通过；36 段导出一致性、31 段非校准动画的 62 组双体型门禁、防伪造回归通过。

2026-10-25 已用 Java 17 执行 Gradle `test build runClientTest`：现有 75 项 JUnit、4 项挥手 GameTest、16 项真实客户端穿戴回归及构建中的资源门禁全部通过。挥手选择读取原版挥动手；晚加入按资源时间线恢复穿戴状态，重复同步保留已显示的盔甲，靴子显隐遵循主副手模板与惯用手镜像。客户端回归在 Xvfb 下检查实际玩家和播放状态；Windows Native 逐项动作录制和双客户端视觉验收尚未执行。

2026-10-26 剩余原版动作一批（潜行、吃喝、视线层、盾牌、拉弓、三叉戟、骑乘、睡眠、鞘翅、攀爬、游泳）的导出一致性、双体型门禁与 `./gradlew test` 88 项已通过，游戏内录制与双客户端视觉验收尚未执行。

校准动画 `calib_zero`、`calib_axes`、`calib_item`、`calib_mirror`、`calib_layer` 已交付，仍需在游戏中逐帧对照 Blockbench。手持挂点 pivot 由原版 `HeldItemFeatureRenderer` 的平移推算得到，未经游戏内校准，不能视为已对齐原版持物位置。

动作设计见 [`docs/animation-specs/`](docs/animation-specs/) 和 [`transitions.md`](docs/animation-specs/transitions.md)。已制作动作按原版进度驱动，主副手继续使用镜像模板；未制作资源保持原版姿态。本轮离线预览位于 `build/anim-review/revision-20261023/`，其中 `combined/` 使用真实 Java 播放器输出潜行挖掘叠加与望远镜俯仰矩阵，包含左右主手和两种体型；预览产物不纳入版本控制。

参考源码与设计取舍见 [实现说明](docs/implementation-notes.md)。

## 开发原则

- 以玩家多关节与动画需求为核心，采用最简可用实现。
- 不做无需求支撑的扩展、抽象或依赖引入。
- 只在必要边界校验，避免过度防御性编程。

完整协作约定见 [AGENTS.md](AGENTS.md)。

## 许可证

[MIT License](LICENSE) · Copyright (c) 2026 Kizunad
