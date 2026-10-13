# Kizuna Players Animation

面向 Minecraft 玩家的多关节模型与动画模组，目标是让玩家模型的关节和动画更便于在 Blockbench 中制作与调整，改善 `.bbmodel` 制作体验。

## 当前状态

运行环境为 **Minecraft 1.20.1 / Fabric / Java 17**。已实现客户端模组接口与运行时，模型和正式动画由动画侧另行交付。

- 25 个关节与 2 个手持挂点；粗臂、细臂分别读取 Bedrock 几何。
- 玩家自身 64×64 皮肤、逐面 UV、原版外层开关与 CPU 蒙皮。
- Bedrock 动画的线性、Catmull-Rom、step / pre-post 插值、循环与保持末帧。
- 原版姿态、循环姿态、一次性动作分层；按骨掩码、优先级及淡入淡出混合。
- 左主手镜像、时间线事件、第三人称持物和 `fpv` 手臂入口。

已打包动画侧交付的校准资源：粗臂 / 细臂骨架（`models/player_skeleton_*.bbmodel` 与 `geo/player_skeleton.geo.json`）和五个校准动画，没有正式动画。模型的 UV 与原版逐面一致性已用本仓读取器对照游戏内原版 `ModelPart` 数值验证；弯折外观、第一人称效果与物品位置仍待游戏内验收。

## 构建与运行

```bash
./gradlew build
./gradlew runClient
```

构建产物为 `build/libs/kizuna-players-animation-0.1.0-SNAPSHOT.jar`。安装到 Fabric 1.20.1 客户端的 `mods/`，同时安装 Fabric API。这个阶段是客户端动画运行时，不需要服务端安装。

模型、动画和索引的路径、骨名、坐标及播放参数见 [资源接口](docs/asset-contract.md)。接口有变化时先修改这份文档；资源包可覆盖同一路径，F3+T 重载后旧播放状态会清空。

## 调试与调用

| 命令 | 用途 |
| --- | --- |
| `/kpa list` | 列出成功加载的动画 |
| `/kpa play <id>` | 在本地玩家上播放指定动画，支持补全 |
| `/kpa stop` | 按索引的淡出时间停止本地玩家动画 |

客户端主线程可以调用 `dev.kizuna.playeranimation.client.PlayerAnimations`：

```java
PlayerAnimations.play(playerUuid, "calib_axes");
PlayerAnimations.stop(playerUuid, "calib_axes");
PlayerAnimations.stopAll(playerUuid);
PlayerAnimations.EVENT.register((player, animation, event) -> {
    // 在玩法接入侧处理时间线通知；此回调不代表服务端伤害授权。
});
```

这里只对客户端已加载的玩家生效，不自动同步网络播放。`hit`、`release` 等事件在客户端 tick 推进时通知，渲染取样不会重复触发它们。

## 验证与范围

`./gradlew test` 覆盖解析、未知输入、分层、镜像、插值、跨循环事件、矩阵以及内外层切口连续性。测试用几何是数学回归夹具，不作为动画侧的骨架模板交付。

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
