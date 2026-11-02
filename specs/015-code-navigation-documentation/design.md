# 总体架构

本次以文档和 `package-info.java` 增强现有分层结构的可发现性。源码包名、模块依赖和运行时行为保持不变。

# 改动模块

| 位置 | 改动 |
| --- | --- |
| 根目录 `README.md` | 增加架构与代码导航文档入口。 |
| `claim-application-service` 分层根包 | 使用 `package-info.java` 声明模块、领域、用例、基础设施、入站适配、共享能力和框架支持的边界。 |
| `docs/architecture/code-navigation.md` | 记录模块地图、依赖方向、业务能力映射和排查路径。 |

# 核心链路设计

```mermaid
flowchart LR
    A[README 文档导航] --> B[代码导航文档]
    B --> C[按业务能力定位目录]
    C --> D[package-info 识别分层职责]
    D --> E[进入具体类与接口]
```

# 主要类或模块职责

| 分层 | 导航说明 |
| --- | --- |
| `domain` | 业务规则、聚合、领域事件和端口契约。 |
| `usecase` | 用例编排、事务边界和幂等处理。 |
| `infrastructure` | MyBatis-Plus、Redis、消息和第三方适配实现。 |
| `interfaces` | HTTP、消息、调度和租户协议入口。 |
| `shared` | 严格受控的跨业务契约与通用组件。 |
| `support` | 无业务规则的框架装配配置。 |

# 数据模型和数据库变更

无数据模型、数据库表或 MyBatis-Plus Mapper 变更。

# 接口设计

无 HTTP、消息或领域接口变更。

# 基础设施变更

无 Redis、MQ、第三方系统、依赖或运行时配置变更。

# 幂等、事务与异常处理

本次不修改签收幂等、事务边界、异常映射或异步投递逻辑；文档仅说明其现有代码位置。

# 对现有功能的影响

`package-info.java` 仅产生包元信息，README 和 Markdown 仅增强源码导航，不影响运行行为。
