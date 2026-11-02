# 018 审核签收 MQ 接入与问题件 HTTP 归位 · 设计方案

## 一、改动模块

| 模块 | 改动 |
|---|---|
| `domain/claimcase` | 新增案件关系聚合与仓储端口（`case` 是 Java 关键字，包名用 `claimcase`） |
| `domain/issue` | 仓储端口新增"按申请查询未关闭问题件"；错误码新增案件关系不存在 |
| `domain/integration/interaction` | `InteractionType` 新增 `AUDIT_SIGNED` |
| `usecase/audit` | 新增审核签收用例、事务服务、命令与结果模型 |
| `usecase/issue` | 新增按案件号查询问题件状态的用例与结果模型；012 的下发/关闭用例不修改 |
| `interfaces/audit/messaging` | 新增审核签收 Consumer 与消息 Assembler |
| `interfaces/issue/web` | 修正路径到 `/api/claim/v1/callbacks`；新增查询 Controller |
| `infrastructure/case/persistence` | 新增 MyBatis-Plus 持久化实现 |
| `infrastructure/issue/persistence` | 新增按申请查询未关闭问题件 |
| `shared/contract` | 新增审核签收载荷非法错误码 |
| 配置与脚本 | `application.yml`、`scripts/docker-start.sh`、契约、健康检查与死信告警文档 |

问题件 MQ 化方案整体撤回：不新增问题件 Topic、不新增问题件 Consumer、不新增回执。

## 二、核心链路

```text
审核系统
  ├─ claim_application_audit_signed_v1（MQ，事实通知）
  │     → AuditSignedConsumer（auditSigned-in-0）
  │     → AuditSignedMessageAssembler.toCommand
  │     → AuditSignedUseCase.record
  │     → AuditSignedTransactionService（单事务）
  │          ├─ t_claim_consumed_event 登记
  │          ├─ t_claim_case_relation 补插缺失案件号
  │          └─ t_claim_interaction_log 写 STARTED / COMPLETED
  │
  ├─ POST /api/claim/v1/callbacks/audit-issues（HTTP，命令）
  │     → ClaimApplicationIssueIntakeController
  │     → ClaimApplicationIssueIntakeUseCase.receive
  │
  ├─ POST /api/claim/v1/callbacks/audit-issues/{issueNo}/close（HTTP，命令）
  │     → ClaimApplicationIssueIntakeController
  │     → ClaimApplicationIssueCloseUseCase.close
  │
  └─ GET /api/claim/v1/cases/{caseNo}/issue（HTTP，只读）
        → ClaimApplicationIssueQueryController
        → ClaimApplicationIssueQueryUseCase.queryByCaseNo
        → t_claim_case_relation 反查申请 → t_claim_application_issue 查未关闭问题件
```

## 三、主要类职责

| 类 | 职责 |
|---|---|
| `AuditSignedConsumer` | 声明 `auditSigned` 消费函数；校验信封事件类型；失败时记录一条 ERROR 后原样抛出。不含映射与业务判断。 |
| `AuditSignedMessageAssembler` | 把 `ClaimApplicationEventEnvelope` 解析为 `AuditSignedCommand`；完成 `caseNos` 的类型、非空、长度与去重校验。 |
| `AuditSignedUseCase` | 消费登记预判、申请存在性校验、生成行为组编号，委托事务服务写库。 |
| `AuditSignedTransactionService` | 单事务内完成消费登记、案件关系补插、`AUDIT_SIGNED` 轨迹写入。 |
| `ClaimCaseRelation` | 案件关系领域对象，只允许通过静态工厂创建，字段非空校验在构造阶段完成。 |
| `ClaimCaseRelationRepository` | 案件关系仓储端口：按申请查案件号、按租户与案件号反查申请、批量插入。 |
| `ClaimApplicationIssueQueryUseCase` | 按案件号反查申请并返回未关闭问题件；只读，不写轨迹。 |
| `ClaimApplicationIssueQueryController` | 暴露 `GET /api/claim/v1/cases/{caseNo}/issue`，只做租户上下文与结果渲染。 |

映射方法放在 Assembler 而不是用例：它是"MQ 报文 → 用例命令"的协议适配，属于入站适配层。用例层因此不依赖 `ClaimApplicationEventEnvelope` 与 `Map`；报文结构变化只影响 Assembler 与 Consumer。

## 四、数据模型

### 案件关系

`t_claim_case_relation` 已存在，本次不改表结构：

| 字段 | 用途 |
|---|---|
| `tenant_code` | 租户隔离 |
| `claim_application_no` | 关联申请 |
| `case_no` | 审核签收返回的案件号 |
| `uk_tenant_claim_application_case` | `(tenant_code, claim_application_no, case_no)` 唯一，幂等兜底 |

Java 侧新增：

- `domain/claimcase/ClaimCaseRelation`：`tenantCode`、`claimApplicationNo`、`caseNo`。
- `domain/claimcase/port/ClaimCaseRelationRepository`：
  - `List<String> findCaseNos(String claimApplicationNo)`
  - `Optional<String> findClaimApplicationNo(String tenantCode, String caseNo)`
  - `void insertAll(List<ClaimCaseRelation> relations)`
- `infrastructure/claimcase/persistence`：DO、Mapper、Converter 与仓储实现。Mapper 只继承 `BaseMapper`，不写手写 SQL。

### 问题件查询

不新增表。`ClaimApplicationIssueRepository` 增加：

- `Optional<ClaimApplicationIssue> findOpenByClaimApplicationNo(String claimApplicationNo)`

实现使用 `is_open = 1` 条件，与现有 `hasOpenIssue` 同一数据来源。

## 五、接口设计

### 5.1 审核签收（MQ 入站）

Topic：`claim_application_audit_signed_v1`，消费组 `claim-application-audit-signed-v1`，binding `auditSigned-in-0`。

```json
{
  "eventId": "8f0d1a52-6a3e-4f0b-9d7e-2c1b5a6e9f10",
  "eventType": "AUDIT_SIGNED",
  "eventVersion": 1,
  "claimApplicationNo": "A2026110200000001",
  "tenantCode": "tenant-a",
  "occurredAt": "2026-11-02T10:00:00+08:00",
  "traceId": "b3a5c1d2e4f6",
  "payload": {
    "caseNos": ["CASE-2026-0001", "CASE-2026-0002"]
  }
}
```

| 结果 | 条件 |
|---|---|
| `RECORDED` | 至少插入一个新的案件号 |
| `REUSED` | 申请存在，但全部案件号已存在 |
| `SKIPPED` | 该 `eventId` 已被本消费组登记 |

### 5.2 问题件下发（HTTP）

`POST /api/claim/v1/callbacks/audit-issues`

```json
{
  "claimApplicationNo": "A2026110200000001",
  "issueNo": "ISSUE-2026-0001",
  "reason": "医疗票据缺失",
  "auditor": "auditor-01",
  "issueContent": "{\"missingMaterials\":[\"INVOICE\"]}"
}
```

成功响应沿用 012 的渲染结构：

```json
{
  "code": "SUCCESS",
  "message": "success",
  "traceId": "c4b6d2e8f0a1",
  "data": {
    "issueNo": "ISSUE-2026-0001",
    "claimApplicationNo": "A2026110200000001",
    "created": true
  }
}
```

### 5.3 问题件关闭（HTTP）

`POST /api/claim/v1/callbacks/audit-issues/{issueNo}/close`

```json
{
  "auditor": "auditor-01"
}
```

响应沿用 012 的关闭结果结构。

### 5.4 问题件状态查询（HTTP）

`GET /api/claim/v1/cases/{caseNo}/issue`，必须携带租户上下文。

有未关闭问题件：

```json
{
  "code": "SUCCESS",
  "message": "success",
  "traceId": "d5c7e9a1b3f2",
  "data": {
    "claimApplicationNo": "A2026110200000001",
    "caseNo": "CASE-2026-0001",
    "hasOpenIssue": true,
    "issueNo": "ISSUE-2026-0001",
    "reason": "医疗票据缺失",
    "raisedAt": "2026-11-02T10:05:00"
  }
}
```

无未关闭问题件：`hasOpenIssue=false`，`issueNo`、`reason`、`raisedAt` 为空。

案件关系不存在：404，错误码 `CASE_RELATION_NOT_FOUND`。

### 5.5 错误映射

| 场景 | HTTP 状态 | 错误码 |
|---|---|---|
| 载荷非法 | 400 | `ISSUE_INTAKE_INVALID` |
| 申请不存在 | 404 | `ISSUE_APPLICATION_NOT_FOUND` |
| 申请状态不合法 | 400 | `ISSUE_APPLICATION_STATUS_INVALID` |
| 已有未关闭问题件 | 409 | `ISSUE_ALREADY_OPEN` |
| 问题件不存在 | 404 | `ISSUE_NOT_FOUND` |
| 案件关系不存在 | 404 | `CASE_RELATION_NOT_FOUND` |

沿用 `ClaimApplicationIssueExceptionHandler`，只补 `CASE_RELATION_NOT_FOUND` 的映射。

## 六、MQ 配置

```yaml
spring:
  cloud:
    function:
      definition: materialProcess;auditDelivery;auditSigned
    stream:
      rocketmq:
        bindings:
          auditSigned-in-0:
            consumer:
              push:
                max-reconsume-times: ${CLAIM_APPLICATION_MQ_MAX_RECONSUME_TIMES:3}
      bindings:
        auditSigned-in-0:
          destination: claim_application_audit_signed_v1
          group: claim-application-audit-signed-v1
          content-type: application/json
```

- Topic 由 `scripts/docker-start.sh` 显式创建，8 读 8 写队列。
- 死信主题为 `%DLQ%claim-application-audit-signed-v1`。
- 问题件走 HTTP，不新增任何 binding 与 Topic。
- 网关 `Path=/api/claim/**` 已覆盖问题件新路径，不新增路由。

## 七、幂等、事务与异常

### 审核签收

- 第一层：`t_claim_consumed_event` 的 `(consumer_group, event_id)` 唯一键。
- 第二层：`t_claim_case_relation` 的 `(tenant_code, claim_application_no, case_no)` 唯一键。
- 事务内先查已有案件号，只插入缺失项；并发冲突由唯一键拦截，异常向上抛出，重投后按"已存在"跳过。
- 消费登记、案件关系、交互轨迹同事务提交。

### 问题件

- 沿用 012 的领域幂等与事务边界，代码不变，只改路径。
- HTTP 业务异常由现有异常处理器映射为 4xx；请求体字段校验失败与报文不可解析同样映射为 400 `ISSUE_INTAKE_INVALID`；系统异常映射为 5xx。

### 死信与告警

| 项 | 约定 |
|---|---|
| 死信主题 | `%DLQ%claim-application-audit-signed-v1` |
| 消息去留 | 保留在死信主题，本服务不消费、不自动重放 |
| 告警方式 | 运维监控平台对死信主题配置邮件告警 |
| 邮件内容 | 环境、死信主题、消费组、`eventId`、`claimApplicationNo`、告警时间、重放步骤 |
| 人工处理 | 通过 RocketMQ 控制台重放到原 Topic；重放前先排查日志中的失败原因 |

邮件发送不在应用内实现：SMTP 凭证、收件人与模板属于运维监控层，业务服务不承载。

## 八、为什么审核签收走 MQ、问题件走 HTTP

判据不是"是否跨系统"——两者都是跨系统；也不是"能不能查询"——查询是独立的读接口，MQ 方案同样可以补一个 GET。判据只有一条：**这次交互有没有可能被业务规则永久拒绝。**

- 审核签收是事实通知。案件已经在审核系统生成，我方只登记。除了申请不存在、载荷非法这类数据异常，没有正常业务拒绝路径。适合 MQ：重投、削峰、与生产者解耦。
- 问题件是命令。下发和关闭都可能因前置条件不满足被永久拒绝（状态不对、已有未关闭问题件、问题件不存在）。HTTP 的 4xx/5xx 让审核系统当场拿到裁决；单向 MQ 只能把拒绝混进重投与死信，靠邮件兜底，这是将就。

附带收益是把问题件的读写放在同一 HTTP 通道：审核系统只需一个客户端、一套鉴权；MQ 只承担审核签收这一条事实通道。

代价：审核方向有两种传输，审核系统需要同时具备 HTTP 客户端与 MQ 生产者。这是按语义拆分的必要成本；强行统一成一种传输，要么牺牲业务裁决，要么牺牲解耦。

## 九、对现有功能的影响

| 影响 | 说明 |
|---|---|
| 问题件 HTTP 路径 | 由 `/api/application/v1/callbacks/**` 改为 `/api/claim/v1/callbacks/**`，与集成契约一致，并接入网关 |
| 旧路径 | 不保留兼容别名；仓库内无调用方 |
| 012 用例与异常处理 | 业务实现不变，异常处理器只补 404 映射 |
| 申请状态机 | 不新增状态、不修改迁移规则，012 的问题件流转与测试保持不变 |
| 002 基线条款 | 原文"审核签收和问题件保持 HTTP 回调"改为"审核签收改 MQ，问题件保持 HTTP 并修正路径"，标注由 018 修订 |
| 集成契约文档 | 回调清单保留两个问题件接口并改正路径；新增审核签收 Topic 与死信告警约定 |
| 现有异步链路 | 材料处理、审核交付的 binding、消费组、死信配置不变 |
| 本地启动 | `docker-start.sh` 增加一个审核签收 Topic；不新增中间件依赖 |

## 十、验证方式

1. 启动 MySQL、Redis、RocketMQ，重启重新打包后的申请服务。
2. 用 RocketMQ 控制台或 `mqadmin sendMessage` 发送审核签收消息，核对案件关系、交互轨迹与消费登记。
3. 经网关 8080 调用问题件下发、关闭接口，核对状态流转与 4xx 错误码。
4. 调用按案件号查询接口，验证有未关闭问题件、无问题件、案件不存在三种结果。
5. 构造申请不存在的审核签收消息，确认真实重投后进入死信主题。
6. 按死信告警文档核对邮件字段与重放命令。
7. 执行全量 Maven 构建与 ArchUnit 测试。
