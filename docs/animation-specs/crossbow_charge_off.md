# crossbow_charge_off：副手持弩上弦（crossbow_charge 的镜像）

> 审阅状态：随 [crossbow_charge](crossbow_charge.md) 一起审阅；本页只说明镜像规则，不单独设计关键帧。

2026-10-23 已同步主手的拉弦肘外展修订，使右臂勾弦、拉回、挂弦的轮廓更明显；沿用原版装填时间，效果待游戏内验收。

- **来源**：`.bbmodel` 中的 `crossbow_charge_off` 由 `crossbow_charge` 的关键帧逐帧镜像得到，规则与[资产契约](../asset-contract.md)的左主手镜像一致：交换 `r_*` / `l_*` 骨，旋转 Y、Z 取反，位移 X 取反，旋转 X 不变；无时间线事件。
- **触发**：物品在副手、原版对副手臂套用同一 ArmPose 时调用；时长、阶段、驱动方式与 `crossbow_charge` 完全相同。
- **索引**：与 `crossbow_charge` 相同，只把 `hand` 改为 `off`。
- **门禁与 GIF**：单独导出、单独跑门禁并出 GIF。
- 修改主手动作后必须重新生成本动作，不允许只改一边。
