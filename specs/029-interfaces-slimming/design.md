# 029 接口层瘦身（MVP）设计

## 1. 改动模块

仅改动 `backend/claim-application-service` 的 `interfaces` 包与对应 ArchUnit 规则、文档，不涉及 `application`、`domain`、`infrastructure` 的类结构调整。

| 动作 | 类/文件 | 说明 |
|---|---|---|
| 删除 | `interfaces/web/UnderwritingCallbackController` | 承保完成不再提供 HTTP 入口 |
| 删除 | `interfaces/dto/UnderwritingCallbackRequest` | 随入口删除 |
| 删除 | `interfaces/web/ApiErrorCodeProvider` | 控制器不再声明请求非法码与系统错误码 |
| 删除 | `interfaces/web/BusinessErrorStatusRegistry` | 状态映射不再需要 |
| 删除 | `shared/exception/BusinessErrorCode` 与各域错误码枚举 | 失败不再细分码值 |
| 删除 | `interfaces/web/advice/WebErrorCode` | 协议层通用错误码一并移除 |
| 删除 | `interfaces/web/render/ClaimApplicationResponseRenderer`、`ClaimApplicationIssueResponseRenderer` | 控制器直接返回 `ApiResponse` |
| 删除 | `interfaces/web/tenantprotocol/core/pipeline/AbstractTenantProtocolPipeline` | 抽象模板被默认实现取代 |
| 删除 | `interfaces/web/tenantprotocol/tenants/tenant_a/TenantAProtocolPipeline` | 租户子类被默认实现 + 注册配置取代 |
| 保留 | `interfaces/web/tenantprotocol/pipeline/TenantProtocolPipeline`、`TenantProtocolPipelineRegistry` | 每租户一条流水线的契约与路由入口 |
| 新增 | `interfaces/web/tenantprotocol/pipeline/DefaultTenantProtocolPipeline` | 默认流水线实现，承载五阶段编排 |
| 新增 | `interfaces/web/tenantprotocol/tenants/tenant_a/TenantAProtocolConfiguration` | 把 `tenant-a` + `v1` 注册为默认流水线实例 |
| 迁移 | `interfaces/web/WebExceptionHandler` → `interfaces/web/advice/WebExceptionHandler` | 异常处理集中到 advice 子包 |
| 迁移 | `interfaces/web/tenantprotocol/core/stage/*` → `interfaces/web/tenantprotocol/stage/*` | 去掉已无意义的 `core` 层 |
| 迁移 | `interfaces/web/tenantprotocol/core/pipeline/*` → `interfaces/web/tenantprotocol/pipeline/*` | 去掉 `core` 层 |
| 迁移 | `interfaces/dto/*` → `interfaces/web/dto/*` | DTO 只服务 HTTP 入口，随 `web` 存放 |
| 迁移 | `interfaces/web/*Controller` → `interfaces/web/controller/*` | HTTP 入口集中到 controller 子包 |
| 迁移 | `interfaces/consumer/AuditSignedMessageParser`、`AuditSignedMessageAssembler` → `interfaces/consumer/message/*` | 消息解析与装配从消费者入口中分离 |

保留不动：三个 MQ 消费者（置于 `interfaces/consumer` 根）、两个调度器、`TenantProtocolParser`、`TenantProtocolConverter`、`TenantProtocolValidator`、`BeanValidationTenantProtocolValidator`、验签与解密端口及默认实现。

## 1.1 目标目录结构

`interfaces` 是六边形架构中的驱动适配层，只允许入口与协议适配，不允许业务编排、事务、持久化或跨域聚合。一级目录按入口类型划分，`web` 内按角色划分：

```text
interfaces/
  web/
    controller/                4 个 HTTP 入口
    advice/                    WebExceptionHandler
    dto/
      request/                 租户与问题件请求
      response/                ApiResponse 与响应记录
    tenantprotocol/
      pipeline/                Pipeline、Registry、DefaultPipeline
      stage/                   五阶段端口与默认实现
      tenants/
        tenant_a/              Parser、Converter、Configuration（平铺）
  consumer/
    message/                   AuditSignedMessageParser、AuditSignedMessageAssembler
    <3 个 *Consumer>
  scheduler/                   2 个调度器
```

边界约束：

- `dto` 只被 `web` 使用，因此放在 `web/dto`；不建 `interfaces/common`，当前没有跨入口公共契约。
- `tenantprotocol` 只包装 HTTP 报文差异；当前只按租户平铺实现类，不预拆 `parser`、`converter`、`assembler`。第二个租户出现且差异集中在协议字段时，再评估按关注点归组。
- `consumer/message` 只放消息解析与命令装配，消费者入口留在 `consumer` 根，便于按 `*Consumer` 后缀识别入口。
- 包结构调整只改变位置，不改变任何类行为与调用顺序。

## 2. 核心业务链路

### 2.1 签收

所有租户共用 `POST /api/claim/v1/application/intake`，按请求头中的 `tenantCode` + `protocolVersion` 路由到对应流水线。

```
HTTP 请求
 → ClaimApplicationIntakeController
 → TenantProtocolPipelineRegistry.execute(TenantInfo, rawPayload)
     1. 按 tenantCode + protocolVersion 选出唯一流水线；无匹配或多条匹配均以业务异常拒绝
     2. 执行流水线并校验命令租户与上下文一致，不一致以业务异常拒绝
 → DefaultTenantProtocolPipeline.execute(rawPayload)
     1. INFO 日志：tenantCode、protocolVersion
     2. 验签：DefaultTenantProtocolSignatureVerifier，INFO 日志后放行
     3. 解密：DefaultTenantProtocolDecryptor，INFO 日志后原样返回
     4. 解析：TenantARequestParser → TenantAIntakeRequest
     5. 校验：BeanValidationTenantProtocolValidator
     6. 转换：TenantARequestConverter → ClaimApplicationIntakeCommand
 → ClaimApplicationIntakeUseCase
 → ClaimApplication 聚合
 → ApiResponse<ClaimApplicationIntakeResponse>
```

当前只有 `tenant-a` + `v1` 注册，实例为 `DefaultTenantProtocolPipeline`。新增租户时新增一条同类实例并绑定该租户的解析与转换组件，签收入口不变。

### 2.2 异常渲染

```
BusinessException → 400 FAILURE，message 为业务拒绝说明
MethodArgumentNotValidException / HttpMessageNotReadableException → 400 FAILURE
NoResourceFoundException → 404 FAILURE
其他 Exception → 500 FAILURE（ERROR 日志含堆栈，仅一次）
```

失败响应不再携带细分错误码，`ApiResponse.code` 固定为 `FAILURE`，`message` 说明原因且不包含敏感字段；成功固定为 `SUCCESS`。

### 2.3 其余入口

- 问题件下发/关闭：`ApiResponse<ClaimApplicationIssueIntakeResponse>`、`ApiResponse<ClaimApplicationIssueCloseResponse>`，构造逻辑从渲染器移入控制器。
- 问题件查询、申请详情查询：直接组装 `ApiResponse`。
- 承保完成：只保留补偿链路调用 `UnderwritingCallbackUseCase`，无 HTTP 入口。

## 3. 主要类职责

| 类 | 职责 |
|---|---|
| `ClaimApplicationIntakeController` | 读取租户上下文与链路标识，调用注册中心与签收用例，组装成功响应 |
| `ClaimApplicationIssueIntakeController` | 问题件下发与关闭入口，组装 `ApiResponse` |
| `ClaimApplicationIssueQueryController` | 问题件状态查询，租户上下文缺失时以业务异常拒绝 |
| `ClaimApplicationQueryController` | 申请详情读模型查询，保持现有返回结构 |
| `WebExceptionHandler` | 唯一 `@RestControllerAdvice`，按成功/失败二元契约渲染：业务拒绝与请求非法 400、路由缺失 404、未预期异常 500 |
| `TenantProtocolPipeline` | 每租户一条流水线的契约：`supports` 判断匹配，`execute` 输出受理命令 |
| `TenantProtocolPipelineRegistry` | 按租户编码与协议版本路由唯一流水线，并校验转换结果租户一致 |
| `DefaultTenantProtocolPipeline` | 默认实现：绑定租户编码与协议版本，记录带租户信息的 INFO 日志并编排五阶段 |
| `TenantAProtocolConfiguration` | 把 `tenant-a` + `v1` 注册为默认流水线实例，装配租户 A 的解析器、校验器与转换器 |
| `DefaultTenantProtocolSignatureVerifier` | 默认验签：INFO 日志说明已执行验签，不校验签名，原样放行 |
| `DefaultTenantProtocolDecryptor` | 默认解密：INFO 日志说明已执行解密，明文原样返回 |

验签与解密日志固定为“租户协议验签通过（默认实现）”与“租户协议解密完成（默认实现）”，流水线日志固定包含 `tenantCode` 与 `protocolVersion`；三类日志均不记录报文内容、签名、密钥、证件号。

## 4. 数据模型与数据库

无表结构、字段、索引、唯一约束变更。

## 5. 接口设计

| 接口 | 方法 | 变化 |
|---|---|---|
| `/api/claim/v1/application/intake` | POST | 无变化；所有租户共用，按请求头路由流水线 |
| `/api/claim/v1/callbacks/audit-issues` | POST | 无变化；响应改由控制器组装 |
| `/api/claim/v1/callbacks/audit-issues/{issueNo}/close` | POST | 无变化 |
| `/api/claim/v1/cases/{caseNo}/issue` | GET | 无变化 |
| `/api/claim/v1/application/{claimApplicationNo}/materials`、`/issues`、`/cases` | GET | 无变化 |
| `/api/claim/v1/callbacks/underwriting-completed` | POST | 删除 |

成功响应保持 `ApiResponse{code, message, traceId, data}`，`code` 为 `SUCCESS`；失败响应 `code` 为 `FAILURE`，按 HTTP 状态区分请求非法、路由缺失与系统异常。

## 6. 基础设施变更

- 无 Redis、MQ、数据库、外部系统变更。
- 仅新增流水线与验签/解密默认实现的 INFO 日志。

## 7. 幂等、事务与异常

- 签收幂等、唯一键冲突处理、问题件幂等与补偿语义全部沿用现有用例，不在本设计内调整。
- 事务边界不变，流水线只是入站报文转换，不参与事务。
- 异常风格不变：流水线抛 `BusinessException`，领域抛 `DomainException`，应用抛 `ApplicationException`。

## 8. 对现有功能的影响

- 承保完成失去主动 HTTP 触发入口，只由补偿链路推进；MVP 接受。
- 失败响应从细分错误码收敛为 `FAILURE` + HTTP 状态 + 可读说明；调用方不能再依赖错误码分支。
- 成功响应结构、状态码与字段含义完全不变。
- 租户路由行为不变：未接入租户仍被拒绝（400），新增租户通过新增流水线实例接入。
- ArchUnit 删除 `controllersMustDeclareApiErrorCodes`，新增“唯一异常处理器位于 `interfaces.web.advice`”与“控制器位于 `interfaces.web.controller`”规则，保证入口与异常处理不扩散。
- 文档只做最小同步：删除 `docs/architecture/ddd-standard-architecture.md`，其余文档仅在出现已删除类名或失效路径时修正，不做全量重构。
