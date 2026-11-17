# villager_dummy_drill：打木桩

袖手拳 · 打木桩，一次性 32.0 s（640 tick）。拍、拦、打，二十组。设计见动作稿 `handoff/pa-cloud-anim/24-Players-Animation-Villager.md` §5.11，进度和制作办法见 [PROGRESS-villager](PROGRESS-villager.md)。

## 1. 索引条目

```json
{
  "id": "villager_dummy_drill",
  "file": "animations/villager_dummy_drill.animation.json",
  "mask": "full",
  "priority": 24,
  "blend_in": 0.1,
  "blend_out": 0.25,
  "hand": "main",
  "fpv": false,
  "phases": [[1.0, 31.0]]
}
```

首帧、末帧等于 [stance_idle](stance_idle.md) 首帧；左主手玩家由运行时整段镜像。

## 2. 时间轴

| 段 | 时间 (s) | 做什么 |
| --- | --- | --- |
| 摆架 | 0.00–1.00 | 前脚上步（`step_l` 0.40），后脚跟落下，站成前后小马步，两手抬到身前 |
| 打桩 | 1.00–31.00 | 两脚不动，二十组，每组 1.5 s，从 1.00 + 1.5k 起：前手立掌拍（`hit` +0.30）、后手前臂横着往下拦（`hit` +0.80）、前拳直打（`hit` +1.30）；每一下胯小转 |
| 收 | 31.00–32.00 | 前脚收回（`step_l` 31.40），两手回架势 |

## 3. 制作实测（2026-11-16）

- 60 次 `hit` 和服务端每一下的击打声对齐。
- 草稿直接过门禁，进 PA 时姿势没有改：最大角速度 600°/s、角加加速度 736,909°/s³，支撑脚滑动右 0.27、左 0.21 px。
- 桩按身前 1 格做；桩远一些时手打在空中（动作稿已知的小问题）。

## 4. 没验证的

- 和木人桩实体的相对位置只在游戏内录像里看。
