# 030 运行时流程日志与 MDC 接入（MVP）任务

## T1 完成异常处理 TODO

- `WebExceptionHandler` 五个异常处理方法改为直接返回 `ApiResponse<Void>`。
- 删除 `ResponseEntity` 导入与包装，使用 `@ResponseStatus` 声明 400、404、500。
- 删除类注释中的 TODO。
- 完成条件：代码中不再出现 `ResponseEntity`；异常状态与响应体语义不变。

## T2 MDC 基础能力

- 在 `claim-foundation-common` 新增 `MdcKeys`：`traceId`、`tenantCode`、`protocolVersion`、`flow`、`eventId`。
- 新增 `MdcContext`：写入、移除、清理；空值不写入。
- 新增包说明。
- 完成条件：模块编译通过，其他模块可直接复用键常量与工具方法。

## T3 流程日志切面

- 在 `claim-foundation-observability` 新增 `@FlowLog` 注解。
- 新增 `FlowLogAspect`：环绕被注解方法，写入 `flow`，记录开始、完成、失败与耗时，`finally` 恢复外层 `flow`。
- 失败日志只输出异常摘要，不输出堆栈与参数。
- 新增 `ObservabilityAutoConfiguration` 注册切面，并新增自动装配 imports 文件。
- POM 增加 `spring-boot-starter-aop`。
- 完成条件：切面对被注解的 Spring Bean 方法生效；异常原样抛出。

## T4 HTTP trace 与租户 MDC

- 在 `claim-foundation-observability` 新增 `TraceMdcFilter`：沿用或生成 `traceId`，写入 MDC，结束时清理；缺失时包装请求使下游读取生成值。
- 自动装配注册过滤器，顺序为 `HIGHEST_PRECEDENCE`。
- 在 `TenantContextFilter` 中写入 `tenantCode`、`protocolVersion`，`finally` 清理。
- 完成条件：HTTP 日志与响应使用同一 `traceId`；租户字段在请求结束后不残留。

## T5 用例流程注解

- 按设计表为 18 个用例入口方法添加 `@FlowLog`。
- 不修改方法签名、业务逻辑与既有业务日志。
- 完成条件：每个流程正常执行输出开始与完成日志，失败输出开始与失败日志。

## T6 MQ 消费者 MDC

- 三个消费者在消息解析后写入 `traceId`、`tenantCode`、`eventId`。
- `traceId` 为 null 或空白时使用 `eventId`。
- `finally` 清理 MDC，异常仍由消费者记录一条 ERROR 后原样抛出。
- 完成条件：MQ 消费日志带链路与租户字段，处理失败后 MDC 不残留。

## T7 日志格式与验证

- 在 `application.yml` 配置控制台日志格式，输出 `traceId`、`tenantCode`、`protocolVersion`、`eventId`、`flow`。
- 使用 JDK 21 构建相关模块。
- 重启运行栈，验证 HTTP 签收、查询、问题件、MQ 消费与调度日志。
- 完成条件：构建通过；日志字段齐全；响应 `traceId` 与日志一致；无敏感信息输出。
