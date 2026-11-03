# 030 运行时流程日志与 MDC 接入（MVP）

## 1. 背景与目标

`claim-application-service` 的核心链路缺少统一的流程边界日志：用例执行时只能看到零散的业务节点日志，无法判断某个流程何时开始、何时结束、耗时多少；HTTP 入口没有把链路标识与租户信息放进日志上下文，MQ 消费与定时调度也没有可关联的 MDC 字段。`WebExceptionHandler` 还残留一条 TODO：异常处理方法返回 `ResponseEntity`，与项目统一返回模型不一致。

目标：

- 统一核心流程日志：每个流程记录开始、完成与失败，包含流程名与耗时。
- 接入 MDC：`traceId`、`tenantCode`、`protocolVersion`、`flow`、`eventId` 进入日志上下文，并随日志格式输出。
- 完成 `WebExceptionHandler` 的 TODO：直接返回 `ApiResponse<Void>`，HTTP 状态由注解声明。
- 不改变任何接口、消息、数据库、状态机与既有业务日志语义。

## 2. 业务规则

1. `WebExceptionHandler` 的所有异常处理方法直接返回 `ApiResponse<Void>`，HTTP 状态由 `@ResponseStatus` 声明，不再使用 `ResponseEntity`；业务拒绝与请求非法仍为 400，路由不存在仍为 404，未预期异常仍为 500。
2. 核心流程在开始执行时记录一条 INFO 日志「流程开始执行」，正常结束时记录一条 INFO 日志「流程执行完成」，抛出异常时记录一条 WARN 日志「流程执行失败」；完成与失败日志包含流程名与耗时。
3. 流程日志由 `@FlowLog` 注解与统一切面产生；流程名由注解声明，切面不得硬编码具体业务流程名。
4. `@FlowLog` 加在用例的公开入口方法上，覆盖 HTTP 入口、MQ 消费与调度触发三类来源；私有方法与类内自调用不产生额外流程日志。
5. MDC 键固定为 `traceId`、`tenantCode`、`protocolVersion`、`flow`、`eventId`；没有值的键不写入。
6. HTTP 入口：请求头 `X-Trace-Id` 有值时沿用，缺失时生成 UUID；响应中的 `traceId` 与日志中的 `traceId` 一致；请求结束后清理 MDC。
7. 租户上下文过滤器把 `tenantCode` 与 `protocolVersion` 写入 MDC，请求结束后清理。
8. MQ 消费者从消息信封写入 `traceId`、`tenantCode`、`eventId`；`traceId` 缺失时使用 `eventId`；处理结束后清理 MDC。
9. 调度任务不直接写流程日志，由被调用的用例方法上的 `@FlowLog` 产生开始与完成日志。
10. 嵌套流程结束后恢复外层 `flow`，不得把内层流程名泄漏到外层后续日志。
11. 日志格式至少包含时间、级别、线程、`traceId`、`tenantCode`、`flow`、Logger 名称与消息。
12. 日志不得输出请求正文、租户报文、签名、密钥、完整证件号、消息载荷；异常日志只输出摘要，堆栈仍由原处理点输出一次。
13. MQ Topic、消费组、消息结构、消费去重与重投语义不变；数据库结构、调度周期与业务状态机不变。

## 3. 正常场景

- 租户提交签收报文：HTTP 过滤器写入 `traceId`，租户过滤器写入 `tenantCode` 与 `protocolVersion`，签收用例记录开始与完成日志，响应返回同一 `traceId`。
- 审核交付、材料处理、审核签收消息到达：消费者写入 `traceId`、`tenantCode`、`eventId`，对应用例记录开始与完成日志。
- 问题件下发、关闭、查询与申请详情查询：对应用例记录开始与完成日志。
- 补偿调度与 Outbox 投递调度：每个被调用用例方法记录开始与完成日志。
- 请求未携带 `X-Trace-Id`：生成 UUID，日志与响应使用同一标识。
- 流程正常返回：完成日志包含流程名与实际耗时。

## 4. 异常场景

- 流程抛出业务异常或系统异常：记录一条 WARN 失败日志（含流程名、耗时、异常摘要）后原样抛出，不吞异常、不改变异常类型。
- 字段校验失败、报文不可解析、业务拒绝：HTTP 状态与响应体语义保持 030 之前的行为。
- 消息处理失败：消费者写一条 ERROR 后原样抛出，交由 binder 重投，MDC 在抛出前清理。
- 流程执行失败时，MDC 仍必须在 `finally` 中清理，避免线程复用时串日志。

## 5. 边界条件

- 缺少租户请求头：`tenantCode` 与 `protocolVersion` 不写入 MDC，日志对应位置为空。
- 消息缺少 `traceId`：使用 `eventId` 作为 `traceId`。
- 消息缺少 `eventId`：`eventId` 不写入 MDC，流程日志仍正常记录。
- 嵌套流程：内层流程完成后恢复外层 `flow`；最外层完成后移除 `flow`。
- 调度轮次没有待处理数据：仍记录开始与完成日志，日志级别为 INFO。
- 非 Spring 代理调用或类内自调用：不产生流程日志，不影响业务行为。

## 6. 本次范围

- `foundation-common` 新增 MDC 键常量与上下文工具。
- `foundation-observability` 新增 `@FlowLog`、流程日志切面、HTTP trace 过滤器、自动装配与 AOP 依赖。
- `foundation-tenant` 的租户上下文过滤器写入并清理租户 MDC 字段。
- `claim-application-service` 的用例入口方法添加 `@FlowLog`，三个 MQ 消费者写入并清理消息 MDC 字段。
- `claim-application-service` 配置日志格式输出 MDC 字段。
- 完成 `WebExceptionHandler` 的 TODO。
- 同步 030 三件套。

## 7. 非本次范围

- 不引入日志文件、ELK、SkyWalking、Zipkin、Micrometer Tracing 等日志采集或链路追踪后端。
- 不修改业务流程、状态机、事务边界、MQ 契约与数据库。
- 不重写已有业务节点日志，不改变其业务字段。
- 不新增 HTTP 接口、消息字段或配置项语义。
- 不新增业务单元测试。

## 8. 验收标准

1. `WebExceptionHandler` 不再出现 `ResponseEntity`，400/404/500 状态与响应体语义不变。
2. 每个加 `@FlowLog` 的用例入口在正常执行时输出开始与完成两条日志，失败时输出开始与失败两条日志。
3. 完成与失败日志包含流程名与耗时。
4. 请求未携带 `X-Trace-Id` 时，响应 `traceId` 非空，且与同一请求的日志一致。
5. HTTP 请求日志中出现 `tenantCode` 与 `protocolVersion`。
6. MQ 消费日志中出现 `traceId`、`tenantCode`、`eventId`。
7. 流程失败时异常原样抛出，MDC 被清理。
8. 日志中不出现请求正文、报文、签名、密钥、证件号与消息载荷。
9. 构建与运行栈黄金路径验证通过。
