# 第三方依赖

| 依赖 | 版本 | 许可与分发 |
|---|---|---|
| Protocol Buffers Java | 4.36.2 | BSD-3-Clause，作为嵌套运行库随安装 JAR 分发，保留上游 JAR 中的许可与版权声明；[来源与许可](https://github.com/protocolbuffers/protobuf/blob/v36.2/LICENSE) |
| protoc | 4.36.2 | BSD-3-Clause，仅构建时生成消息，不随模组分发 |
| Fabric API | 0.92.3+1.20.1 | Apache-2.0，由运行环境安装，不内嵌 |
| JUnit Jupiter | 5.11.4 | EPL-2.0，仅测试使用，不随模组分发 |

Minecraft、Gson 和 JOML 由游戏环境提供，遵循各自上游条款。设计参考与未移植声明见 [实现说明](docs/implementation-notes.md)。本仓原创代码仍按 MIT 许可。
