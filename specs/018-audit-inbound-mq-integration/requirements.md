# 项目背景与目标

## 功能目标

按交互语义拆分审核方向的接入方式：

- 审核签收是事实通知，改为 MQ：新增入站 Topic `claim_application_audit_signed_v1`，一条消息可携带同一申请下的多个案件号。
- 问题件是带前置条件的命令，保持 HTTP：修正现有入口路径并接入网关，新增按案件号查询问题件状态的只读接口。
- 补齐 `t_claim_case_relation` 的领域模型与持久化，支撑"一个申请多个案件号"以及案件号反查申请。
- 审核签收消费失败沿用重投与死信，死信由告警邮件转人工。

## 业务规则

### 审核签收（MQ）

1. 信封 `claimApplicationNo`、`tenantCode` 必须非空；申请必须已存在。
2. `payload.caseNos` 必须为非空数组；每个元素去掉首尾空白后非空且长度不超过 64 个字符；重复值去重后落库。
3. 一条消息的全部案件关系、交互轨迹与消费登记在同一数据库事务内提交，任一步失败整体回滚。
4. 幂等以消费登记 `(consumerGroup, eventId)` 为准：同一事件重复投递直接跳过，不重复写轨迹与案件关系。
5. 案件关系唯一键为 `(tenant_code, claim_application_no, case_no)`。同一申请的不同签收消息重复携带同一案件号时不新增行，只补充缺失的案件号。
6. 审核签收只记录案件关联事实，不改变申请状态。签收前后申请均保持 `WAIT_AUDIT`；案件生命周期由案件服务承载，理赔申请服务不维护案件状态。
7. 交互轨迹新增类型 `AUDIT_SIGNED`，方向为入站，目标系统为 `AUDIT_SYSTEM`。

### 问题件（HTTP）

8. 保留问题件 HTTP 入口，路径统一到网关已覆盖的 `/api/claim/**` 下：
    - 下发：`POST /api/claim/v1/callbacks/audit-issues`
    - 关闭：`POST /api/claim/v1/callbacks/audit-issues/{issueNo}/close`
9. 原 `/api/application/v1/callbacks/**` 路径删除，不保留兼容别名；仓库内无调用方依赖旧路径。
10. 响应语义：
    - 受理成功返回 2xx，响应体携带 `issueNo`、`claimApplicationNo` 与是否新建。
    - 业务规则不满足（申请不存在、状态不合法、已有未关闭问题件、问题件不存在、载荷非法）返回 4xx 与明确错误码。
    - 系统故障（数据库不可用等）返回 5xx。
11. 问题件业务规则沿用 012：
    - 下发要求申请存在、处于 `WAIT_AUDIT`、且同一申请没有未关闭问题件。
    - 关闭要求问题件存在；已关闭按幂等处理；关闭后申请恢复 `WAIT_AUDIT`。
12. 幂等沿用 012 的领域判断：`issueNo` 全局唯一、重复下发按已存在返回、重复关闭按 `isClosed` 返回。
13. 问题件不写消费登记表，不走 MQ，不产生死信。

### 问题件状态查询（HTTP）

14. 新增只读接口 `GET /api/claim/v1/cases/{caseNo}/issue`。
15. 查询必须携带租户上下文，按 `(tenant_code, case_no)` 在 `t_claim_case_relation` 中反查平台申请编号。
16. 返回内容：
    - 案件存在且有未关闭问题件：`hasOpenIssue=true`，并返回 `issueNo`、`reason`、`raisedAt`。
    - 案件存在且无未关闭问题件：`hasOpenIssue=false`，其余问题件字段为空。
    - 案件关系不存在：返回 404 与明确错误码。
17. 查询接口只读，不改变任何状态，不写交互轨迹。

### 失败与告警

18. 审核签收消费异常记录一条 ERROR 后原样抛出，按 `max-reconsume-times` 重投；耗尽后进入 `%DLQ%claim-application-audit-signed-v1`。
19. 本服务不消费死信主题；告警邮件由运维监控平台对死信主题配置，内容包含环境、死信主题、消费组、`eventId`、`claimApplicationNo` 与处理建议；人工确认后通过 RocketMQ 控制台重放。
20. 问题件是同步 HTTP，失败直接通过响应码反馈给审核系统，不进入 MQ 重投与死信。

### 消费与适配

21. 审核签收 Topic 消费组为 `claim-application-audit-signed-v1`。
22. 消息信封统一使用 `ClaimApplicationEventEnvelope`；入站适配层把 `payload` 转换为强类型命令，用例层不得出现 Map、Message 或 MQ 相关类型。

## 正常场景

- 审核签收一条消息携带 2 个案件号：新增 2 行案件关系、1 组 `AUDIT_SIGNED` 轨迹、1 条消费登记。
- 同一事件重复投递：不新增案件关系、不新增轨迹，仅记录跳过日志。
- 同一申请先后两条签收消息，案件号部分重叠：只补充缺失的案件号。
- 问题件下发成功：返回 2xx，申请由 `WAIT_AUDIT` 进入 `ISSUE`。
- 问题件关闭成功：返回 2xx，申请由 `ISSUE` 恢复 `WAIT_AUDIT`。
- 查询某案件：返回该申请当前是否有未关闭问题件。

## 异常场景与边界条件

- 审核签收申请不存在：抛出业务异常，消息重投，耗尽后进入死信。
- 审核签收 `caseNos` 为空、元素为空白、元素超长或元素不是字符串：消息判为非法，重投后进入死信。
- 问题件下发时申请状态不是 `WAIT_AUDIT`：返回 4xx，错误码 `ISSUE_APPLICATION_STATUS_INVALID`。
- 问题件下发时已有未关闭问题件：返回 4xx，错误码 `ISSUE_ALREADY_OPEN`。
- 问题件关闭时问题件不存在：返回 4xx，错误码 `ISSUE_NOT_FOUND`。
- 问题件请求载荷非法：返回 4xx，错误码 `ISSUE_INTAKE_INVALID`。
- 查询的案件号不存在于案件关系表：返回 404，不返回空壳 200。
- 查询时案件存在但从未下发问题件：返回 200 与 `hasOpenIssue=false`。
- 并发投递同一审核签收事件：消费登记唯一键保证一次生效。
- 并发下发同一 `issueNo`：`issueNo` 唯一键保证不重复建单，后到请求按幂等返回。
- 并发下发不同问题件到同一申请：`uk_open_issue_claim_application` 唯一键保证只有一个未关闭问题件。

## 本次范围

- 审核签收入站 Topic 的 Consumer、消息 Assembler 与 MQ 配置。
- 审核签收用例、事务服务、结果模型与错误码。
- 案件关系领域对象、仓储端口、MyBatis-Plus 持久化实现。
- `InteractionType.AUDIT_SIGNED` 与交互轨迹写入。
- 问题件 HTTP 入口路径修正与网关可达性验证。
- 问题件状态查询用例与只读接口。
- 死信主题命名、告警邮件字段与重放步骤的运维文档。
- 集成契约、环境脚本、健康检查清单与 002 基线条款修订。
- 单元测试与本地运行栈验证。

## 非本次范围

- 问题件 MQ 化与回执 Topic。
- 审核签收后的结案、撤销。
- 应用内邮件发送实现；邮件由运维监控平台承载。
- 自动消费死信或自动重放。
- 审核系统真实 HTTP 客户端；`LocalAuditSystemClient` 保持进程内实现。
- 案件服务自身的案件生命周期与查询接口。
- 前端页面。

## 验收标准

- 向 `claim_application_audit_signed_v1` 发送携带 2 个案件号的消息：`t_claim_case_relation` 新增 2 行，`t_claim_interaction_log` 新增 STARTED/COMPLETED 两行，`t_claim_consumed_event` 新增 1 行。
- 重发同一签收事件：上述三张表行数不变，日志出现"已消费，跳过处理"。
- 审核签收申请不存在：消息连续重投后进入 `%DLQ%claim-application-audit-signed-v1`。
- 经网关调用 `POST /api/claim/v1/callbacks/audit-issues` 与关闭接口：状态流转与 012 一致，返回 2xx。
- 状态不合法时返回 4xx 与对应错误码，不进入死信。
- `GET /api/claim/v1/cases/{caseNo}/issue` 分别验证有未关闭问题件、无问题件、案件不存在三种结果。
- 原 `/api/application/v1/callbacks/**` 路径返回 404。
- 死信告警文档包含主题、消费组、邮件字段、监控配置方式与重放命令。
- 全量 Maven 构建通过，现有测试与 ArchUnit 4 条分层规则全部通过。
