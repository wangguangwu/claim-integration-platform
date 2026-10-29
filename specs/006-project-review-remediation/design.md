# 006 全项目评审整改设计

## 改动模块

| 模块 | 改动性质 |
|---|---|
| `backend/claim-application-service` | 配置归属、异常语义、校验单一化、去重、死代码删除、假件剥离 |
| `backend/claim-foundation` | 三个子模块合并为单模块，删除零类 `common` |
| `backend/claim-gateway` | 删除指向已删模块的路由与 sentinel/brave 依赖，traceId 写入 MDC |
| `backend/claim-case-service`、`backend/claim-ai-service` | 整模块删除 |
| `backend/claim-local-mock`（新增） | 承载本地联调假件，独立进程启动 |
| 根 `pom.xml` | 模块列表与依赖管理收敛 |
| `deploy/`、`scripts/` | 版本化迁移、环境变量对齐、黄金路径与契约检查脚本 |
| `docs/`、`specs/001`、`specs/002`、`specs/005` | 空头验收口径修正、模块与目录引用同步 |

## 一、配置与启动形态

现状：`application.yml` 只有一个文档分隔符，`claim.outbox/integration/compensation/mock` 全部落在 `on-profile: local` 之后，而 `OutboxRelayScheduler` 与 `ApplicationCompensationScheduler` 用无默认值的占位符读取调度间隔，非 local 启动即崩。

调整：拆成"基础文档 + local 覆盖文档"两段，与网关写法一致。

- 基础文档承载运行时必需项：调度间隔、批量、退避与滞留阈值、MQ 绑定与消费组、数据源与 Redis，全部用 `${CLAIM_*:默认}` 形式暴露环境变量；调度间隔与阈值都给出可运行默认值，任何 profile 都能解析。
- 外部系统地址（文件系统与承保）在基础文档不给默认值，只写 `${CLAIM_APPLICATION_FILE_SYSTEM_URL}` 与 `${CLAIM_APPLICATION_UNDERWRITING_URL}`；未配置时占位符无法解析，出站客户端创建即失败，错误信息直接点名该环境变量（实测消息 `http://${CLAIM_APPLICATION_FILE_SYSTEM_URL} is malformed`），不再退化为指向本服务自身。
- `claim.mock.enabled` 在基础文档默认 `false`，联调假件明细（材料失败类型、响应延迟、承保是否回调、回调延迟）在基础文档给保守默认值；local 文档只覆盖外部地址为自指与 `claim.mock.enabled: true`。
- Nacos 开关从"只在 local 关闭"改为三个环境变量驱动、默认开启，使 prod 形态可以在没有 Nacos 的环境里单独验证配置解析。
- 调度间隔仍由 `@Scheduled` 占位符直读，不进策略端口，保持 005 既有规则；两个 `*Properties` 中无 Java 读取点的 `fixedDelayMs` 字段删除。

## 二、异常与错误码

现状：`domain/claim/exception` 与 `shared/contract` 两套异常、8 个错误码，语义重叠；领域不变式失败与内部重试超限抛 `IllegalStateException`，被签收异常处理器统一成 500。

调整（单一套异常，落在 `shared/contract`）：

- `ApplicationBusinessException`（唯一业务异常）+ `ApplicationErrorCode`（唯一枚举，按域分段：`INTAKE_*`、`MATERIAL_*`、`UNDERWRITING_*`、`ASYNC_*`），提供 `invalid/busy/dependency` 三组语义化工厂，异常信息只携带申请号与租户码。
- 领域不变式失败改抛本域业务异常：`ClaimApplication` 的状态迁移违例、`Material` 的处理前置违例映射 `INTAKE_INVALID`/`MATERIAL_INVALID`；`ClaimApplicationIntakeUseCase` 的重试超限映射 `INTAKE_BUSY`（可重试语义）。
- 两个 `@RestControllerAdvice` 的映射规则：业务异常 → 400/409/503 与业务码；其余未知异常 → 500 与通用码，且只打印异常类型与申请号。
- 出站客户端解析异常保留 cause：租户报文 JSON 解析失败时以 cause 包装原始 `JsonProcessingException`，日志级别 warn，禁止打印报文原文。

## 三、校验与状态判定单一化

- 必填、长度、枚举合法性由 `PersonInfo` 与 `ClaimApplication.submit(...)` 自守；`MaterialSubmission` 自守材料清单非空。
- 租户层 `Validator` 收缩为"报文形状"校验：字段是否存在、协议版本是否支持，不再重复业务必填规则。
- 仓储不再判断"仅受理失败可重新申请"：期望状态由用例作为参数传入，仓储只做条件更新与影响行数返回，聚合保持唯一判定。

## 四、轨迹事务边界

- 材料处理、自动承保分流、承保完成通知与滞留补偿四个入口的行为轨迹，开始与结束都由各自的事务服务在同一事务内一次性写入：材料走 `MaterialProcessingTransactionService`，分流走 `UnderwritingTransactionService.recordAttempt`，回调走 `UnderwritingCallbackTransactionService`，补偿新增 `ApplicationCompensationTransactionService`（补偿属于 `integration` 域，不借用承保域的事务服务，避免跨域用例内部互依赖）。
- 外部调用仍在事务外执行，调用中断时不提交任何轨迹行，因此不会再出现只有开始的孤儿行为组。
- 记录装配仍只有一个入口（`usecase/integration/record` 的生命周期记录装配工厂），本次只调整写入位置，不新增装配实现。
- 材料上传循环保持在事务外，改为按材料批次幂等重传（以 `applicationNo + batchNo` 判重），避免消息重投造成重复上传。

## 五、租户接入去重

- 删除两个租户各自的 Parser，改为泛型 `JacksonTenantProtocolParser<R>`（`ObjectMapper` + `Class<R>`），租户差异降为注册表中的一项。
- 两个 Pipeline 合并为按 `tenantCode` 注册的一份配置化装配：`租户码 → request 类型 + Validator + Converter`。
- 角色装配合并为 `toPerson(Function<PersonInfo, T>)` 形式，复用 `Applicant::new` 等角色工厂；性别映射由 `switch` 改为常量 `Map<String, Gender>`。
- 目标：`tenants` 目录 471 行降至 260 行以内，tenant-a/tenant-b 响应逐字段不变。

## 六、消息契约与常量

- 事件载荷由 `Map<String,Object>` 字面量改为两端共用的类型化 record（`shared/contract`）：申请处理事件、审核投递事件、材料处理事件各自定义字段，序列化仍为 JSON，历史消息可继续被消费。
- MQ 主题、绑定名与消费组集中到 `shared/contract/MqContract`（或枚举），yml 只引用同名占位符；Outbox 投递显式设置事件类型作为消息 tag，与集成契约文档一致。
- 时间与映射风格：新增 `shared/infrastructure/BusinessClock`（固定业务时区），替换 8 处 `ZoneId.of("Asia/Shanghai")`；DO↔领域对象转换统一 MapStruct，仓储不再内联逐字段转换；`InteractionLog` 中的 512 截断长度下移到基础设施（列宽属于持久化细节）。

## 七、死代码与依赖删除

| 项 | 依据 | 处置 |
|---|---|---|
| `TenantApplicationResponseRenderer` SPI + `Router` | `implements` 命中仅 1 且被 Router 构造时过滤，分支恒不可达 | 删除，控制器直接依赖统一渲染器 |
| `ApplicationStatus.ISSUE/COMPLETED/REVOKED` | 限定引用计数 0 | 删除枚举值 |
| `OutboxEvent.restore(...)` | 与规范构造器逐字等价，唯一调用点在仓储 | 删除，走构造器 |
| `Material` 四参 `createPending` | 外部只调三参版 | 改 private |
| `PolicySnapshot` | `policyNo` 无读取点 | 端口签名改 boolean 语义，删除该类 |
| 两个 `*Properties.fixedDelayMs` | Java 侧 0 读取 | 删除字段 |
| `spring-ai`、`xxl-job-core`、gateway `sentinel`、`micrometer-tracing-bridge-brave`、`starter-validation` | 代码引用计数 0（校验为手写管线） | 从 pom 删除 |

## 八、模块收敛

- `claim-foundation` 合并为单模块 jar：保留 `TraceHeaders`、`TenantHeaders`、`TenantInfo`、`TenantContext`、`TenantContextFilter`、`TenantAutoConfiguration` 六个类与 `AutoConfiguration.imports`；删除零类的 `common` artifact，清理 8 处 pom 引用。头名常量必须留在 foundation，它是网关与业务服务之间的契约来源。
- 删除 `claim-case-service`、`claim-ai-service`：连带根 `pom.xml` 模块列表、网关两条路由、`.env.example` 与 health-check 文档中的 8082/8083 说明、001 Spec 相关条目、前端两个空骨架页。
- 新增 `backend/claim-local-mock`：迁出 `support/mock` 的 8 类 709 行，独立进程监听本地端口；假件对外接口不再返回证件号原文，改为按申请号查询。生产的两个 Feign 客户端地址通过环境变量指向该进程。

## 九、数据层

- `t_claim_outbox_event` 补两条索引：`(status, next_retry_at, created_at)` 覆盖待发扫描的排序列，`(status, updated_at)` 覆盖滞留发送中重置。
- 逻辑删除统一 `@TableLogic`，删除仓储里手写的 `.eq(deleted, false)`。
- `t_claim_application_issue`、`t_claim_case_relation` 两张零引用表随模块删除一并从 DDL 移除，不保留无主表。
- 引入 Flyway：现有 DDL 切成 `V1__baseline.sql`（已有库用 `baselineOnMigrate` 纳管，保证幂等），compose 的 init 只做建库；口令与库名一律来自环境变量，`.env.example` 与 yml 占位符同名 1:1。
- `findByApplicationNo` 维持不带租户条件：唯一约束 `uk_tenant_external_application` 保证申请号全局唯一，补一条注释说明该不变式来源。

## 十、可观测性与验证机制

- 新增 `logback-spring.xml`，日志格式含 `%X{traceId} %X{claimApplicationNo}`（键名取 007 收敛后的标识符）；入站 filter 与调度、消费入口负责 MDC set/clear；Outbox 投递把 traceId 写入消息头，消费端取出续链。
- 实测修正：应用日志的默认格式里已经带出一段 `traceId-spanId`（micrometer tracing 提供），但它与网关写入的 `X-Trace-Id` 是两个不同标识——同一次申请在网关侧与业务侧日志里对不上。本任务要求把网关的 `X-Trace-Id` 作为唯一链路标识来源：入站用它初始化 trace 上下文，缺失才自建，出站与 MQ 头继续透传同一个值。
- `management.endpoints.web.exposure.include` 增加 `metrics,prometheus`。
- `scripts/smoke/golden-path.sh`：把文档中的手工 curl 固化，端口统一 `${GATEWAY_PORT:-8080}`，补齐 `X-Protocol-Version`，逐步用退出码断言（首次签收、同号幂等、未支持租户、历史数据还原、材料失败→重新申请、异步抵达 `WAIT_AUDIT`、DB 行数校验）。
- `scripts/check-contract.sh`：静态断言 yml 中的 MQ destination 必须出现在 broker 建 topic 列表、消费组字符串两处一致、`@TableName` 集合等于 DDL 表集合、`.env.example` 变量与 yml 占位符 1:1。
- `scripts/loadtest.sh`：包一层 `jmeter -n`，保留响应码、业务码、`X-Trace-Id` 三重断言作为回归门，只打本地环境与虚构数据。

## 对现有功能的影响

- 对外 HTTP 路径、请求与响应结构、状态机语义、持久化 JSON 与既有列不变；历史数据仍可读。本条只约束 006 自身轮次；业务对象字段名与数据库列名的改名由 [007 命名全链路收敛](../007-claim-application-naming/design.md) 承接，并在 006 第 3 轮之前执行。
- MQ 主题与消费组名称不变，载荷字段名不变（改为类型化后序列化结果一致），历史在途消息可被新代码消费。
- 错误响应中，此前返回 500 的领域不变式失败与重试超限改为业务码与 4xx/503，属于对外语义修正，需在集成契约文档标注。
- 删除模块后，指向案件与诊断的两条网关路由消失；此前恒失败，不影响可用能力。
- 假件独立进程后，本地联调需同时启动该进程，`scripts/docker-start.sh` 与健康检查文档同步说明。
