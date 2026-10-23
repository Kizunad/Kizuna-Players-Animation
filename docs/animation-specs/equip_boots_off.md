# equip_boots_off：副手穿靴子（equip_boots 的镜像）

> 审阅状态：随 [equip_boots](equip_boots.md) 一起审阅；本页只说明镜像规则，不单独设计关键帧。

- **来源**：`.bbmodel` 中的 `equip_boots_off` 由 `equip_boots` 的关键帧逐帧镜像得到，规则与[资产契约](../asset-contract.md)的左主手镜像一致：交换 `r_*` / `l_*` 骨，旋转 Y、Z 取反，位移 X 取反，旋转 X 不变；`step_l` / `step_r` 事件互换，其他事件时刻不变。
- **触发**：主手模板换成副手持物时调用（例如副手持盔甲右键装备、副手挥动），时长、阶段、驱动方式与 `equip_boots` 完全相同。
- **索引**：与 `equip_boots` 相同，只把 `hand` 改为 `off`。
- **门禁与 GIF**：单独导出、单独跑门禁并出 GIF；握持检查改查左手手指。
- 修改主手动作后必须重新生成本动作，不允许只改一边。
