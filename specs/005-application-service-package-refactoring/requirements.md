# 005 理赔申请服务包重构需求

## 功能目标

- 将 `claim-application-service` 的包组织定为"层级优先、层内分域"：根包是 `interfaces`、`usecase`、`domain`、`infrastructure` 四层，业务域（`intake`、`material`、`underwriting`、`integration`）作为每层的第二级子包。
- 让层职责从包路径第一段即可判定，同时保持现有业务行为、接口契约和基础设施配置不变。

## 目录组织决策记录

- 本 Spec 一度把目录改为"业务域优先、域内分层"，随后按服务负责人裁定回退为"层级优先、层内分域"，并与服务原始基线（`interfaces`/`usecase`/`domain`/`infrastructure`）保持同名，降低回滚成本。
- 该选择属于标准 DDD 不规定的项目方言：标准只约束层的职责、依赖方向与对象角色命名，两种组织形式同样合规。后续不得因目录顺序重新争论架构合规性，只按 design 的「目录词典」验收形状。

## 业务规则

- `intake` 域归属申请签收、幂等控制与租户协议接入；其聚合在 `domain/claim`。
- `material` 域归属材料模型、材料处理与文件系统适配。
- `underwriting` 域归属保单查询、自动承保和承保回调。
- `integration` 域归属 Outbox、消息消费、交互日志与异步补偿。
- 仅被多个业务域复用的能力允许进入 `shared`：跨域 HTTP 响应模型、通用加密工具、跨域异步异常，以及消息信封、跨域命令这类异步契约；配置和本地开发模拟能力进入 `support`。
- 跨域引用只能落在对方 `domain`、`shared`，或 `integration` 对外提供的记录装配用例；不得引用其他业务域的 `interfaces`、`infrastructure`，不得跨域传递 DO。
- 目录调整不得改变 HTTP 路径、MQ Topic、数据库结构、配置项、状态机、幂等和异常处理规则。
- 目录命名以标准 DDD 词典为准：`domain` 只放模型、端口、业务异常与错误码且零框架依赖；DO、ORM 数据映射、DO 与领域对象的转换器、仓储实现一律在 `infrastructure`；`*Mapper` 专指 ORM 数据映射，转换器不得叫 `Mapper`；`interfaces` 只放入站通道，出站适配器一律在 `infrastructure`。层内目录粒度与包名拼写属于项目方言，按 design 的「目录词典」裁定。
- 聚合根自己守住不变式：领域对象不得对层外暴露公开 setter，构造器私有，状态只能通过命名领域方法迁移；从数据库重建聚合必须走 owning 域提供的反水化工厂，不允许先建对象再逐字段改写。
- 一个聚合的创建知识只留在 owning 域：其他层不得在自己代码里 `new` 他域聚合，也不得逐字段拼装他域聚合状态，只能通过该域 `domain` 暴露的工厂方法创建。Outbox 事件与交互日志这类异步记录的装配只有一个入口（`usecase/integration/record` 的生命周期记录装配工厂），业务域不得自建同角色实现；装配入口自己也不得自带领域词汇，交互方向、目标系统与结果状态由 `domain` 的枚举回答，聚合本身由 `domain` 的工厂装配。
- 配置阈值是实现细节：用例需要的批量、退避与滞留判定阈值在 `domain` 声明 `*Policy` 端口，由 `infrastructure` 的 `*Properties` 绑定配置实现；`@ConfigurationProperties` 不得挨着用例放，否则 `usecase` 依赖 Spring，换配置来源要改用例。调度间隔由 `@Scheduled` 占位符读取，不进入策略端口。
- 聚合组装只发生在用例与领域：入站适配器（含租户协议防腐层）不得引用和组装聚合根，其产物是用例声明的 `*Command` 入参，由用例调用领域工厂创建聚合。
- 同一后缀在整棵树上只能表达一种角色：`*UseCase` 一律是用例入口具体类，不为无第二实现、无替换需求的用例保留单实现接口；名字只差一个词的类必须说明确实是两种角色，否则改名拆开。

## 正常场景

1. 应用可通过原有对外申请接口、材料异步处理和承保回调完成既有业务链路。
2. 单元测试可以在调整后的包路径下运行，并覆盖原有核心逻辑。
3. JMeter 仍通过网关暴露的对外接口执行，不直接调用应用服务端口。

## 异常场景

| 场景 | 期望处理 |
|---|---|
| 包迁移后存在旧 package import | 构建失败，修正全部生产和测试代码引用后再交付 |
| Spring 组件未被扫描 | 服务启动或上下文测试失败，保留根包扫描范围并修正组件路径 |
| Mock 被误删导致本地 Feign 调用失效 | 本次不删除 Mock，仅调整其包归属 |

## 边界条件

- 本次做包结构、import 与目录粒度的机械迁移，不修改业务规则、状态流转和对外契约；允许三类归位改动：按标准词典重命名角色含糊的类、按层归属调整 import、按聚合封装与所有权规则收敛领域对象的公开形态（去 setter、构造器私有、增反水化工厂、把他域构造移回 owning 域）。归位前后同一输入的响应码、落库结果与异步事件必须逐项一致。
- 当前本地 profile 依赖的 Mock 不拆分为独立服务；其独立化属于后续独立 Spec。
- 不删除 JMeter 脚本和压测说明；压测继续以网关为唯一入口。

## 本次范围

- 迁移生产代码与对应测试代码的 package 到"层级优先、层内分域"结构。
- 按标准 DDD 词典对齐目录：入站通道只放 `interfaces`，持久化角色按聚合分组，转换器类名去 `Mapper` 歧义。
- 按聚合封装与所有权规则归位领域对象：申请与材料聚合去公开 setter、构造器私有并增加反水化工厂，Outbox 与交互日志装配收敛到 `integration` 单一入口，材料聚合的创建移回 `material`，租户协议防腐层的产物改为签收命令。
- 按依赖倒置归位配置：`OutboxRelayProperties`、`ApplicationCompensationProperties` 移出 `usecase`，落到 `infrastructure/integration/config` 并实现 `domain` 新增的策略端口。
- 调整架构说明与本 Spec，使文档与目录结构一致。
- 编译和执行理赔申请服务相关测试。

## 非本次范围

- 新增或删除 HTTP、MQ、数据库和配置能力。
- Mock 独立服务化、Testcontainers/WireMock 替换。
- 修改任一既有业务规则或状态流转。
- 调整领域不变式失败的异常类型与对外错误码。当前不变式抛 `IllegalStateException`、由 `interfaces` 兜底映射为系统错误，属于既有异常处理规则，改动会改变对外错误语义，另立 Spec 处理。

## 验收标准

- 生产代码根包只有 `interfaces`、`usecase`、`domain`、`infrastructure`、`shared`、`support`，不保留"业务域优先"的旧根包壳、`.gitkeep` 占位和空目录。
- `mvn -B -pl backend/claim-application-service -am test` 通过。
- JMeter 脚本仍以 `127.0.0.1:8080` 和 `/api/application/v1/applications` 为默认入口。
- 无遗留旧包名引用，且无无关业务逻辑改动。
- 依赖方向检查通过：`domain` 不依赖框架也不依赖 `interfaces`/`usecase`/`infrastructure`；`infrastructure` 不依赖 `usecase`/`interfaces`；`usecase` 不依赖 `interfaces`/`infrastructure`（含不绑定 `@ConfigurationProperties`）；跨域不引用对方 `interfaces`/`infrastructure`；DO 只出现在 `infrastructure`。
- 目录与 design 的「目录词典」一致：`interfaces/<域>` 下只保留 `web`/`messaging`/`scheduling`/`tenantprotocol` 等入站通道，无 `inbound` 中转层；持久化角色位于 `infrastructure/<域>/persistence` 且按聚合分组；不存在以 `Mapper` 结尾的 DO↔领域对象转换器。
- 聚合封装与所有权检查通过：`domain` 聚合根不存在公开 setter 且构造器私有；代码不出现他域聚合的 `new` 或逐字段拼装，跨域创建一律走 owning 域的工厂方法；`ClaimApplication`、`Material`、`OutboxEvent`、`InteractionLog` 的构造点只出现在各自的 `domain` 包内，工厂调用点只出现在 `domain` 与 `usecase`；交互词汇（`INBOUND`/`OUTBOUND`、目标系统标识）不在 `domain/integration/interaction` 之外重复出现。
- `interfaces` 不引用任何聚合根：`grep -rh 'ClaimApplication' interfaces` 命中 0；租户协议流水线的产物是 `ApplicationIntakeCommand`。
- 经 `claim-gateway` 验证首次签收、同外部申请号重复提交、同号并发、`ACCEPT_FAILED` 重新申请与租户协议缺失错误映射，行为与迁移前一致。
