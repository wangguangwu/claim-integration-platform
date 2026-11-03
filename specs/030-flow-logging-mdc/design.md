# 030 运行时流程日志与 MDC 接入（MVP）设计

## 1. 改动模块

| 模块 | 改动 |
| --- | --- |
| `claim-foundation-common` | 新增 MDC 键常量与上下文读写工具，供租户、可观测性与应用层共用 |
| `claim-foundation-observability` | 新增 `@FlowLog`、流程日志切面、HTTP trace 过滤器、自动装配；POM 增加 AOP 与 Servlet/Web 编译依赖 |
| `claim-foundation-tenant` | 租户上下文过滤器写入 `tenantCode`、`protocolVersion`，请求结束清理 |
| `claim-application-service` | 用例入口加 `@FlowLog`；MQ 消费者写入消息 MDC；配置日志格式；完成 `WebExceptionHandler` TODO |
| `specs/030-flow-logging-mdc` | 需求、设计与任务 |

## 2. 核心链路

### 2.1 HTTP 请求

```text
TraceMdcFilter(order = HIGHEST_PRECEDENCE)
  → 解析 X-Trace-Id，缺失时生成 UUID 并包装请求
  → MDC.traceId
TenantContextFilter(order = HIGHEST_PRECEDENCE + 10)
  → 绑定 TenantContext
  → MDC.tenantCode / MDC.protocolVersion
Controller → UseCase(@FlowLog)
  → 切面写入 MDC.flow，记录开始
  → 业务执行
  → 切面记录完成/失败，恢复 flow
TenantContextFilter finally
  → 清理 TenantContext、tenantCode、protocolVersion
TraceMdcFilter finally
  → 清理 traceId
```

- `TraceMdcFilter` 位于 `foundation-observability`，通过 `@AutoConfiguration` 注册。
- 缺失 `X-Trace-Id` 时，用 `HttpServletRequestWrapper` 让下游控制器与异常处理器读到生成值，响应 `traceId` 与日志一致。
- 租户过滤器只清理自己写入的键，trace 过滤器只清理 `traceId`，避免相互覆盖。

### 2.2 MQ 消费

```text
Consumer
  → 解析消息
  → MDC.traceId（缺失时用 eventId）、tenantCode、eventId
  → UseCase(@FlowLog)
  → finally 清理 MDC
```

- 解析失败时只清理并原样抛出，保留消费者现有 ERROR 日志。
- 三个消费者分别处理审核签收、审核交付与材料处理消息。

### 2.3 定时调度

- `ClaimApplicationCompensationScheduler` 与 `OutboxRelayScheduler` 不新增流程日志。
- 被调用的 `ClaimApplicationCompensationUseCase` 五个方法与 `OutboxRelayUseCase.relayOnce` 加 `@FlowLog`，由切面统一记录流程边界。
- 调度器保留现有的异常兜底与业务结果日志。

## 3. 主要类职责

| 类 | 职责 |
| --- | --- |
| `MdcKeys` | 定义 `traceId`、`tenantCode`、`protocolVersion`、`flow`、`eventId` 五个 MDC 键 |
| `MdcContext` | 写入、移除、清理 MDC；空值不写入 |
| `FlowLog` | 方法级流程日志注解，声明流程名 |
| `FlowLogAspect` | 环绕被注解方法，写 `flow`、记录开始/完成/失败与耗时，`finally` 恢复外层 `flow` |
| `TraceMdcFilter` | HTTP 请求解析或生成 `traceId`，写入并清理 MDC |
| `ObservabilityAutoConfiguration` | 注册切面与 trace 过滤器；按 Servlet/AOP 条件装配 |
| `TenantContextFilter` | 在原有租户上下文基础上写入并清理租户 MDC 字段 |

## 4. MDC 字段

| 键 | 来源 | 说明 |
| --- | --- | --- |
| `traceId` | HTTP 头或生成的 UUID；MQ 消息 `traceId`，缺失用 `eventId` | 链路标识 |
| `tenantCode` | HTTP 租户头；MQ 消息 | 租户编码 |
| `protocolVersion` | HTTP 租户头 | 协议版本 |
| `flow` | `@FlowLog.value()` | 当前流程名 |
| `eventId` | MQ 消息 | 消息事件标识 |

## 5. 流程日志

切面按以下规则输出：

```text
INFO  流程开始执行，flow={}
INFO  流程执行完成，flow={}，durationMs={}
WARN  流程执行失败，flow={}，durationMs={}，reason={}
```

- `reason` 只输出异常摘要（`异常类名: 消息`），不输出堆栈。
- 切面不打印方法参数与返回值，避免请求正文、证件号与消息载荷进入日志。
- `flow` 写入 MDC 前保存旧值，结束后恢复；外层没有旧值时移除。
- 业务异常与系统异常都记录失败日志后原样抛出，不改变异常类型。

### 5.1 注解位置

| 类 | 方法 | 流程名 |
| --- | --- | --- |
| `ClaimApplicationIntakeUseCase` | `intake` | 理赔申请签收 |
| `AuditSignedUseCase` | `record` | 审核签收处理 |
| `AuditDeliveryUseCase` | `deliver` | 审核交付 |
| `MaterialProcessingUseCase` | `process` | 材料处理 |
| `UnderwritingUseCase` | `dispatch` | 承保分流 |
| `UnderwritingCallbackUseCase` | `complete` | 承保完成推进 |
| `ClaimApplicationIssueIntakeUseCase` | `receive` | 问题件下发 |
| `ClaimApplicationIssueCloseUseCase` | `close` | 问题件关闭 |
| `ClaimApplicationIssueQueryUseCase` | `queryByCaseNo` | 问题件状态查询 |
| `ClaimApplicationDetailQueryUseCase` | `findWithMaterials` | 申请材料查询 |
| `ClaimApplicationDetailQueryUseCase` | `findWithIssues` | 申请问题件查询 |
| `ClaimApplicationDetailQueryUseCase` | `findWithCases` | 申请案件查询 |
| `OutboxRelayUseCase` | `relayOnce` | Outbox 事件投递 |
| `ClaimApplicationCompensationUseCase` | `resetStuckSendingEvents` | 补偿-投递滞留重置 |
| `ClaimApplicationCompensationUseCase` | `redrivePendingAcceptClaimApplications` | 补偿-待受理滞留 |
| `ClaimApplicationCompensationUseCase` | `redispatchAcceptedClaimApplications` | 补偿-已受理滞留 |
| `ClaimApplicationCompensationUseCase` | `recoverUnderwritingClaimApplications` | 补偿-承保中滞留 |
| `ClaimApplicationCompensationUseCase` | `recoverWaitAuditClaimApplications` | 补偿-等待审核滞留 |

## 6. 自动装配

- `claim-foundation-observability` 新增 `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`。
- 切面装配以 AspectJ 类存在为条件；HTTP 过滤器以 Servlet Web 应用与 `OncePerRequestFilter` 存在为条件。
- AOP 代理由 Spring Boot 默认 `AopAutoConfiguration` 开启，只需要增加 `spring-boot-starter-aop` 依赖。

## 7. 异常处理 TODO

`WebExceptionHandler` 改为：

```text
@ExceptionHandler(BusinessException.class)
@ResponseStatus(HttpStatus.BAD_REQUEST)
public ApiResponse<Void> handleBusinessException(...)
```

- 删除 `ResponseEntity` 依赖与所有 `ResponseEntity.status(...).body(...)` 包装。
- 每个处理方法用 `@ResponseStatus` 声明 400、404 或 500。
- 日志与响应内容保持不变。

## 8. 日志格式

在 `application.yml` 配置控制台格式，追加 MDC 字段：

```text
%d{yyyy-MM-dd HH:mm:ss.SSS} %-5level [%thread] [traceId=%X{traceId:-}] [tenantCode=%X{tenantCode:-}] [protocolVersion=%X{protocolVersion:-}] [eventId=%X{eventId:-}] [flow=%X{flow:-}] %logger{36} - %msg%n
```

缺失字段输出为空，不输出 `null`。

## 9. 敏感信息

- 流程日志只输出流程名、耗时与异常摘要。
- MDC 只承载链路与租户标识，不承载证件号、姓名、报文或密钥。
- 现有业务日志中的证件号与报文输出规则不变，本次不新增敏感字段输出。

## 10. 对现有功能的影响

- 不改变 HTTP 响应结构、MQ 契约、数据库结构与业务状态机。
- 未携带 `X-Trace-Id` 的请求，响应 `traceId` 从空值变为生成的 UUID；这是本次 MDC 接入的直接结果。
- 新增 AOP 代理后，用例仍以单例 Bean 注入，事务与异常行为不变。
- 调度类流程每轮输出开始与完成日志，可通过日志级别调整，不影响调度逻辑。
