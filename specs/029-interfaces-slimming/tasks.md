# 029 接口层瘦身（MVP）任务

## T1 删除承保回调入口

- 删除 `interfaces/web/UnderwritingCallbackController.java`。
- 删除 `interfaces/dto/UnderwritingCallbackRequest.java`。
- 保留 `UnderwritingCallbackUseCase`、`UnderwritingCallbackTransactionService` 与 `UnderwritingCompensationHandler`。
- 完成条件：`POST /api/claim/v1/callbacks/underwriting-completed` 无映射；补偿链路编译通过。

## T2 收敛 HTTP 异常处理

- 删除 `interfaces/web/ApiErrorCodeProvider.java`、`interfaces/web/BusinessErrorStatusRegistry.java`。
- 将 `WebExceptionHandler` 迁移到 `interfaces/web/advice/`。
- 删除 `BusinessErrorCode` 及 `ClaimApplicationIntakeErrorCode`、`ClaimApplicationErrorCode`、`ClaimApplicationIssueErrorCode`、`ClaimApplicationQueryErrorCode`、`ClaimApplicationAsyncErrorCode`、`WebErrorCode`。
- `BusinessException`、`DomainException`、`ApplicationException` 只保留消息与原始原因，不再携带错误码；清除全部调用点错误码实参与 import。
- 异常处理器按二元契约渲染：业务拒绝与请求非法 400、路由缺失 404、未预期异常 500，响应体 `code` 固定为 `FAILURE`。
- 四个控制器移除 `implements ApiErrorCodeProvider` 与两个错误码方法。
- 完成条件：字段校验失败、业务拒绝返回 400 `FAILURE`；未预期异常返回 500 `FAILURE`；成功响应为 200 `SUCCESS`。

## T3 删除响应渲染器

- 删除 `interfaces/web/render/ClaimApplicationResponseRenderer.java`、`ClaimApplicationIssueResponseRenderer.java` 及包说明。
- 签收控制器直接返回 `ApiResponse<ClaimApplicationIntakeResponse>`。
- 问题件控制器直接组装 `ApiResponse<ClaimApplicationIssueIntakeResponse>` 与 `ApiResponse<ClaimApplicationIssueCloseResponse>`。
- 问题件查询控制器直接组装 `ApiResponse<ClaimApplicationIssueQueryResponse>`。
- 完成条件：响应字段与 HTTP 状态不变，`interfaces/web/render` 目录移除。

## T4 收敛租户协议流水线

- 保留 `TenantProtocolPipeline` 接口与 `TenantProtocolPipelineRegistry`，租户路由行为不变。
- 删除 `AbstractTenantProtocolPipeline` 与 `TenantAProtocolPipeline`。
- 新增 `DefaultTenantProtocolPipeline`：绑定租户编码与协议版本，实现五阶段编排，处理开始时记录一条包含 `tenantCode` 与 `protocolVersion` 的 INFO 日志。
- 新增 `TenantAProtocolConfiguration`，把 `tenant-a` + `v1` 注册为默认流水线实例，装配租户 A 解析器、注解校验器、转换器与默认验签/解密实现。
- `interfaces/web/tenantprotocol/core/stage/` 迁移为 `interfaces/web/tenantprotocol/stage/`，保留三个阶段端口、注解校验器、默认验签与默认解密实现。
- 默认验签与解密实现各记录一条 INFO 日志后放行，不输出报文与敏感字段。
- 签收控制器继续通过注册中心路由，不直接依赖具体流水线。
- 完成条件：未接入租户返回 400 `FAILURE`；结果租户不一致返回 400 `FAILURE`；日志出现带租户信息的流水线日志与验签、解密各一条 INFO。

## T5 拆分 DTO 子包

- 新建 `interfaces/web/dto/request/`，放入 `TenantAIntakeRequest`、`ClaimApplicationIssueIntakeRequest`、`ClaimApplicationIssueCloseRequest`。
- 新建 `interfaces/web/dto/response/`，放入 `ApiResponse`、`ClaimApplicationIntakeResponse`、`ClaimApplicationIssueIntakeResponse`、`ClaimApplicationIssueCloseResponse`、`ClaimApplicationIssueQueryResponse`。
- 同步全部 import 与包说明。
- 完成条件：请求与响应记录只存在于 `interfaces/web/dto`，`interfaces` 根目录不存在 `dto` 包；编译通过。

## T6 更新 ArchUnit 规则

- 删除 `controllersMustDeclareApiErrorCodes` 规则与 `ApiErrorCodeProvider` 引用。
- 新增规则：`@RestControllerAdvice` 只允许位于 `interfaces.web.advice`。
- 新增规则：`@RestController` 只允许位于 `interfaces.web.controller`。
- 完成条件：原规则中除删除项外全部通过，两条入口规则通过。

## T7 同步文档

- 删除 `docs/architecture/ddd-standard-architecture.md` 及其入口链接。
- 仅在已修改文档出现已删除类名或失效路径时做最小修正，不做全量文档重构。
- 完成条件：不再存在 `ddd-standard-architecture.md` 的悬空链接；已修改文档不出现已删除类名、`render` 包与回调接口。

## T8 首轮构建与运行栈验证

- 执行 `mvn -pl backend/claim-application-service -am test`。
- 启动中间件与理赔申请服务，按黄金路径验证：签收 → 自动承保完成 → 问题件下发 → 问题件关闭 → 问题件查询 → 申请详情查询。
- 核对日志：流水线日志包含 `tenantCode` 与 `protocolVersion`，验签与解密各一条 INFO；未接入租户返回 400 `FAILURE`；字段校验失败返回 400 `FAILURE`；成功响应 `code` 为 `SUCCESS`。
- 完成条件：构建通过，ArchUnit 全绿，黄金路径全部返回预期状态与响应结构。

## T9 收口 interfaces 目录结构

- 将 4 个 `*Controller` 迁入 `interfaces/web/controller/`。
- 将 `interfaces/dto` 整体迁入 `interfaces/web/dto`，更新包名与全部 import。
- 将 `interfaces/web/tenantprotocol/core/pipeline` 迁入 `interfaces/web/tenantprotocol/pipeline`，删除空 `core` 目录。
- 保持 `interfaces/web/tenantprotocol/{stage,tenants/tenant_a}`，`tenant_a` 内继续平铺 Parser、Converter、Configuration。
- 将 `AuditSignedMessageParser`、`AuditSignedMessageAssembler` 迁入 `interfaces/consumer/message/`，消费者保留在 `interfaces/consumer` 根。
- 更新所有 `package-info`；不创建 `interfaces/common`，不新拆 `parser/converter/assembler`。
- 完成条件：目录与本节目标一致；不存在 `interfaces/dto`、`tenantprotocol/core`、`interfaces/common`；所有类仅包路径变化。

## T10 最终构建与回归

- 再次执行 `mvn -pl backend/claim-application-service -am test`。
- 重启理赔申请服务，按 T8 黄金路径回归。
- 核对 `@RestController` 全部位于 `interfaces.web.controller`，`@RestControllerAdvice` 位于 `interfaces.web.advice`。
- 完成条件：构建与 ArchUnit 全绿，黄金路径结果与结构调整前一致。
