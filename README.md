# Kizuna Players Animation

面向 Minecraft 玩家的多关节模型与动画模组，目标是让玩家模型的关节和动画更便于在 Blockbench 中制作与调整，改善 `.bbmodel` 制作体验。

## 当前状态

运行环境为 **Minecraft 1.20.1 / Fabric / Java 17**。已实现客户端动画运行时和服务端区域同步，包含校准资源、两段基础移动动画和一段打坐吐纳动画草稿。

- 25 个关节与 2 个手持挂点；粗臂、细臂分别读取 Bedrock 几何。
- 玩家自身 64×64 皮肤、逐面 UV、原版外层开关与 CPU 蒙皮。
- Bedrock 动画的线性、Catmull-Rom、step / pre-post 插值、循环与保持末帧。
- 原版姿态、循环姿态、一次性动作分层；按骨掩码、优先级及淡入淡出混合。
- 左主手镜像、时间线事件、第三人称持物和 `fpv` 手臂入口。
- Protobuf 播放／停止／回执、本人及实体追踪范围同步、进入范围时按原始起点补发。

已打包粗臂 / 细臂骨架（`models/player_skeleton_*.bbmodel` 与 `geo/player_skeleton.geo.json`）、五个校准动画，以及 `walk` / `run` / `meditate` 三段可预览的循环草稿。模型的 UV 与原版逐面一致性已用本仓读取器对照游戏内原版 `ModelPart` 数值验证；完整世界场景、第一人称效果与物品位置仍待游戏内验收。

## 构建与运行

```bash
./gradlew build
./gradlew runClient
```

构建产物为 `build/libs/kizuna-players-animation-0.1.0-SNAPSHOT.jar`。安装到 Fabric 1.20.1 的 `mods/`，同时安装 Fabric API。本地预览只需客户端安装；多人同步需要服务端和观看动画的客户端均安装，且客户端持有相同动画资源。未安装的客户端不会收到自定义动画包。

模型、动画和索引的路径、骨名、坐标及播放参数见 [资源接口](docs/asset-contract.md)。接口有变化时先修改这份文档；资源包可覆盖同一路径，F3+T 重载后旧播放状态会清空。

## 调试与调用

| 命令 | 用途 |
| --- | --- |
| `/kpa list` | 列出成功加载的动画 |
| `/kpa play <id>` | 在本地玩家上播放指定动画，支持补全 |
| `/kpa stop` | 按索引的淡出时间停止本地玩家动画 |
| `/kpa_server play <player> <id>` | 管理员在服务端启动区域同步动画 |
| `/kpa_server stop <player> [id]` | 管理员同步停止指定动作；省略 id 停止全部 |

基础行走和奔跑先手动预览：`/kpa play walk`（1.2 秒一周期）、`/kpa play run`（0.7 秒一周期）。它们是原地循环，尚未根据玩家移动状态自动切换。换动作前使用 `/kpa stop`。

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

区域沿用 Minecraft 实体追踪范围，另包含目标本人；不向其他维度或远处玩家广播。进入范围时补发当前登记动作与原始起始 tick，客户端接着现有进度播放，不补触发过去的时间线事件。停止、离线、换维度、重生及服务器关闭会清理对应登记状态。服务端不读取客户端动画资源，玩法侧应显式停止已结束的动作以释放登记；一次性动作的过期补发由客户端跳过。

`hit`、`release` 等事件仍只在客户端 tick 推进时通知，不直接触发服务端伤害。F3+T 会清空本地播放状态，需重新播放或离开并重新进入追踪范围恢复。同步精度沿用 Minecraft 世界时钟，不承诺各客户端逐帧一致。

## Protobuf 协议

本仓维护唯一的 [`player_animation.proto`](src/main/proto/kizuna/playeranimation/v1/player_animation.proto)，定义 `PlayRequest`、`StopRequest` 和 `Result`。停止请求明确区分指定动作和全部动作，结果按 `request_id` 关联。Fabric 通道 `kizuna_players_animation:play`、`:stop` 向客户端发送原始 Protobuf 字节，`:result` 回传处理结果；没有额外 JSON 或手写二进制消息。字段与执行语义见 [控制协议 v1](docs/asset-contract.md#protobuf-控制协议-v1)。服务端 API 和管理员命令触发同步，不开放客户端任意广播接口。

运行 `./gradlew generateProto protocolZip` 可生成 Java 类型及 `build/distributions/kizuna-players-animation-0.1.0-SNAPSHOT-proto.zip`；普通 `build` 也会生成此包。调用方从协议包中的同一份 `.proto` 生成类型，不另行维护协议副本。

## 验证与范围

`./gradlew test` 覆盖解析、未知输入、分层、镜像、插值、跨循环事件、矩阵以及内外层切口连续性。除数学夹具外，还直接检查已交付的双体型资源：左右手独立弯指后的内外层没有裸露切口，持物矩阵在混合摆臂姿态下与原版一致（含细臂肩部的 0.5 像素修正）。

协议测试固定 v1 消息的二进制样例，检查字段编号、停止范围、可选起始 tick、未知结果码和字段保留；另检查网络载荷边界、晚入范围进度与历史事件抑制。

已通过 48 项测试及独立服务端 + 两个真实 Fabric 客户端联机验证：范围内同步、远处排除、离开范围清理、重新进入恢复、停止、未知动作回执和换维度清理。公网延迟与多人压力尚未测试。

校准动画 `calib_zero`、`calib_axes`、`calib_item`、`calib_mirror`、`calib_layer` 已交付，仍需在游戏中逐帧对照 Blockbench。手持挂点 pivot 由原版 `HeldItemFeatureRenderer` 的平移推算得到，未经游戏内校准，不能视为已对齐原版持物位置。

本轮不做旧动画迁移、Bong 接线、盔甲／披风／鞘翅或其他模组的适配。现有装备层仍按原版方式绘制，不能认为它们已经随新增关节弯曲。`fpv` 动画活动期间由骨架手臂和挂点绘制物品，不接入原版地图、使用物品等专用第一人称动画。

参考源码与设计取舍见 [实现说明](docs/implementation-notes.md)。

## 开发原则

- 以玩家多关节与动画需求为核心，采用最简可用实现。
- 不做无需求支撑的扩展、抽象或依赖引入。
- 只在必要边界校验，避免过度防御性编程。

完整协作约定见 [AGENTS.md](AGENTS.md)。

## 许可证

[MIT License](LICENSE) · Copyright (c) 2026 Kizunad
