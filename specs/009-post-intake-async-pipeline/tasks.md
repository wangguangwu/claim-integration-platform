# 009 任务清单

## 第 1 批 · 外部系统本地化

| 编号 | 任务 | 产物 | 完成条件 |
|---|---|---|---|
| T1 | 本地实现配置 | `infrastructure/integration/config/LocalIntegrationProperties`、`application.yml` 的 `claim.integration.local.*` | 五个配置项可注入；缺省值生效；`delay=0` 时不休眠 |
| T2 | 文件上传与承保三端口本地实现 | `LocalMaterialFileProcessor`、`LocalValidPolicyQuery`、`LocalAutoUnderwritingRequester`、`LocalUnderwritingResultQuery` | 四个类实现既有端口；调用打印一条 INFO 日志且不含证件号与文件地址；按配置返回结果；中断时恢复标记并抛出 |
| T3 | 删除假件与 HTTP 外部客户端 | 删除 `support/mock/**`、两个 Feign 客户端与四个 `Feign*` 实现、openfeign 依赖、`@EnableFeignClients`、`claim.integration.file-system.url`、`claim.integration.underwriting.url`、`claim.mock.*` | 编译通过；启动日志不再注册 mock 控制器；`grep -rn "mock/underwriting\|mock/file-system\|@FeignClient" backend` 命中 0 |

## 第 2 批 · 审核交付链路

| 编号 | 任务 | 产物 | 完成条件 |
|---|---|---|---|
| T4 | 交互类型扩展 | `InteractionType.AUDIT_DELIVERY(OUTBOUND, "AUDIT_SYSTEM")` | 枚举含新值，方向与目标系统声明在枚举上 |
| T5 | 审核交付端口与本地实现 | `domain/audit` 下的 `AuditDeliveryRequest`、`ServiceAuditSystemPort`、`infrastructure/audit/external/LocalAuditSystemClient` | 端口无框架依赖；本地实现在 `fail-audit-delivery=true` 时抛异常并打 ERROR 日志 |
| T6 | 审核交付用例与事务服务 | `usecase/audit/AuditDeliveryUseCase`、`AuditDeliveryTransactionService` | 判重、状态校验、外部调用、单事务写入 `STARTED/COMPLETED/REUSED` 轨迹与消费登记；消费组常量 `claim-application-audit-delivery-v1` |
| T7 | 消费函数与 binding | `interfaces/audit/messaging/AuditDeliveryConsumer`、`application.yml` 的 `function.definition` 与 `auditDelivery-in-0` | 函数名与 binding 一致；destination 为 `claim_application_audit_delivery_v1`；group 与契约表一致 |

## 第 3 批 · 消费失败口径

| 编号 | 任务 | 产物 | 完成条件 |
|---|---|---|---|
| T8 | 重投与死信配置 | `spring.cloud.stream.rocketmq.bindings.{materialProcess,auditDelivery}-in-0.consumer.push.max-reconsume-times`；两个消费函数的失败日志 | 两个 binding 显式声明重投次数并支持环境变量覆盖；消费函数捕获异常仅用于打印一条 ERROR（含 `consumerGroup`、`claimApplicationNo`、`eventId`）后原样抛出，不确认消费；死信主题名写入设计文档 |

## 第 4 批 · 补偿补齐

| 编号 | 任务 | 产物 | 完成条件 |
|---|---|---|---|
| T9 | 补偿策略扩展 | `ClaimApplicationCompensationPolicy#pendingAcceptTimeout`、`ClaimApplicationCompensationProperties`、`application.yml` | 新阈值可配置，默认 5 分钟 |
| T10 | PENDING_ACCEPT 滞留补偿 | `ClaimApplicationCompensationUseCase#redrivePendingAcceptClaimApplications`、调度新增一步 | 补偿直接调用材料处理用例，事件标识为 `compensation-<claimApplicationNo>-<attempt>`；单条失败不影响同批；调度步骤异常被隔离 |

## 第 5 批 · 文档同步

| 编号 | 任务 | 产物 | 完成条件 |
|---|---|---|---|
| T11 | 集成契约 | `docs/integration/claim-application-integration-contract.md`、`docs/architecture/ddd-standard-architecture.md`、`docs/architecture/claim-application-architecture.md` | MQ 表补死信主题与重投口径；外部系统一节改为"端口 + 本地实现"；架构文档同步本地实现与 `audit` 域 |
| T12 | 联调文档 | `docs/testing/application-async-material-underwriting.md` | 依赖 mock HTTP 入口的步骤改写为配置开关 + 重启；断言改为查轨迹与状态 |
| T13 | Spec 回链 | `specs/004-.../tasks.md`、`specs/006-.../tasks.md` | 004 增加"后续变更"说明；006 P2-2 标记由 009 交付 |

## 第 6 批 · 构建与运行栈验证

| 编号 | 任务 | 产物 | 完成条件 |
|---|---|---|---|
| T14 | 全反应堆构建 | `mvn -B clean package` | 9 个模块 SUCCESS |
| T15 | 黄金路径 | 运行栈验证记录 | 有保单与无保单两条路径到 `WAIT_AUDIT` 且含 `AUDIT_DELIVERY` 行为组；材料失败路径进入 `ACCEPT_FAILED` |
| T16 | 失败与补偿演练 | 运行栈验证记录 | 重复消息记 `REUSED`；开启交付失败后消息进死信；`pending-accept-timeout=10s` 时补偿推进成功；日志无证件号 |

## 说明

- 本 Spec 不写单元测试，验证以运行栈黄金路径与失败演练为准。
- 任务顺序即执行顺序，前置任务未完成不进入下一批。

## 运行栈验证记录（2026-10-29）

环境：MySQL 3306、Redis 6379、RocketMQ 9876、应用 8081、网关 8080，JDK 21，报文经网关提交。

| 场景 | 结果 |
|---|---|
| 全反应堆构建 | `mvn -B clean package` 9 个模块 SUCCESS |
| 无保单黄金路径 | `A2026102900000056` 经签收 → 材料处理 → 自动承保 → 补偿查询 → `WAIT_AUDIT`；轨迹含 `MATERIAL_PROCESS`、三段 `UNDERWRITING`（含 `compensation-<申请号>`）、`UNDERWRITING_CALLBACK`、`AUDIT_DELIVERY` 的 `STARTED/COMPLETED`；outbox 两条 `PUBLISHED`；`t_claim_consumed_event` 两个消费组各一条 |
| 有保单黄金路径 | `A2026102900000052` 直达 `WAIT_AUDIT`，只有一段 `UNDERWRITING` 分流轨迹，无 `UNDERWRITING_CALLBACK`，`AUDIT_DELIVERY` 交付完成 |
| 重复回调 | 对已 `WAIT_AUDIT` 的申请再次回调返回 `REUSED`，`AUDIT_DELIVERY_REQUESTED` 仍为 1 条 |
| Broker 停机后再恢复 | 签收返回 `SUCCESS` 且状态 `PENDING_ACCEPT`，outbox `retry_count` 递增、健康检查保持 `UP`；`pending-accept-timeout=10s` 时补偿以 `compensation-<申请号>-1` 直接把申请推进到 `UNDERWRITING`；Broker 恢复后待投事件补投为 `PUBLISHED`，重复消息记 `MATERIAL_PROCESS/REUSED` 且材料未重复上传 |
| 材料失败 | `MATERIAL_TYPE_REJECTED` 命中本地失败名单时申请进入 `ACCEPT_FAILED`，该材料 `FAILED` 且无上传地址，错误码 `LOCAL_UPLOAD_REJECTED`，其余材料 `PROCESSED` |
| 审核交付失败进入死信 | `fail-audit-delivery=true` 且 `max-reconsume-times=1` 时同一事件两次失败后进入 `%DLQ%claim-application-audit-delivery-v1`（`Max Offset=1`），不写 `AUDIT_DELIVERY` 轨迹，outbox 保持 `PUBLISHED` 不被重复投递 |
| 消费失败日志 | `A2026102900000057` 的失败演练中，每次投递各有一条「审核交付消费失败」ERROR，含 `consumerGroup=claim-application-audit-delivery-v1`、`claimApplicationNo`、`eventId` 与异常摘要，无证件号 |
| 重复消息 | 回拨 `claim-application-audit-delivery-v1` 消费位点重投同一事件，日志出现「审核交付事件已消费，跳过处理」，`t_claim_interaction_log` 与 `t_claim_consumed_event` 行数不变 |
| 敏感信息 | 上述运行日志中证件号、原始文件地址、上传地址命中 0 |

验证前清理了本地 Broker 中 007 改名前的历史报文：这些报文缺 `claimApplicationNo`，新消费者无法处理会反复重投，删除 `claim_application_audit_delivery_v1` 及其重试、死信主题后重建。该操作只影响本地联调数据，申请与事件状态以 MySQL 为准。
