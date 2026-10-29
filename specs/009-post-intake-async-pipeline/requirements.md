# 009 签收后异步链路：审核交付、消费失败处理与外部系统本地实现

## 项目背景与目标

签收成功后平台已经具备 Outbox、材料处理消费与承保分流，但链路仍有三处断点：

1. `AUDIT_DELIVERY_REQUESTED` 只有生产端，没有任何消费者，事件投递出去后无人处理，申请停在 `WAIT_AUDIT` 而审核系统从未收到交付。
2. 消费失败沿用 binder 默认行为，没有显式的重投次数、死信口径和平台侧兜底，失败后状态可能长期滞留。
3. 文件系统、承保、审核三类外部依赖靠 `support/mock`（709 行）加两个 Feign 客户端支撑，本地必须自指 8081，且假件混在业务代码里。

本 Spec 的目标：

- 补齐审核交付消费链路，让事件真正走到"交付审核系统"。
- 把消费失败处理写成可执行规则：重投次数、死信主题、平台侧兜底补偿。
- 用进程内本地实现替代三类外部系统（打印交互日志 + 按配置休眠），删除 mock 假件与 Feign 客户端。
- 补齐 `PENDING_ACCEPT` 滞留补偿，覆盖材料处理消息丢失的场景。

## 业务规则

### 审核交付消费

1. `AUDIT_DELIVERY_REQUESTED` 由消费者组 `claim-application-audit-delivery-v1` 从 `claim_application_audit_delivery_v1` 消费，binding 名为 `auditDelivery`。
2. 消费者先按 `(消费者组, eventId)` 判重，已消费直接跳过，不重复调用审核系统。
3. 只有处于 `WAIT_AUDIT` 的申请才交付审核系统；状态不匹配按重复事件处理，记录 `REUSED` 轨迹后登记消费。
4. 交付不改变申请状态，`WAIT_AUDIT` 既是交付前状态也是本阶段终态。
5. 每次交付记录一组交互轨迹：`interactionType=AUDIT_DELIVERY`，`action=STARTED/COMPLETED/REUSED`，`requestId` 取消息 `eventId`。交付抛异常时按第 10 条不写轨迹、不登记消费，只由第 11 条留下 ERROR 日志后重投。
6. 申请不存在时按系统异常处理，不登记消费，抛出让 MQ 重投。

### 消费失败处理

7. 消费失败必须抛出异常交由 RocketMQ 重投，禁止捕获后确认消费。
8. 单条消息最多重投 3 次（`max-reconsume-times` 可配置），耗尽后由 broker 投递到死信主题 `%DLQ%<consumerGroup>`。
9. 平台不实现死信消费者；死信由人工排查后重放，重放依赖第 2 条与消费登记表的幂等保证。
10. 业务结果、交互轨迹与消费登记必须在同一本地事务提交，消费失败不得留下部分数据。
11. 消费失败必须留下 ERROR 日志，含 `eventId`、`claimApplicationNo`、`consumerGroup`；不得输出证件号、文件地址与报文原文。

### 平台侧兜底补偿

12. `PENDING_ACCEPT` 滞留超过阈值时，补偿直接调用材料处理用例，不经过 Outbox，避免滞留期间事件堆积。
13. 补偿使用稳定的事件标识 `compensation-<claimApplicationNo>-<claimApplicationAttempt>`，失败回滚后下一轮用同一标识重试，成功登记后不会重复处理。
14. `WAIT_AUDIT` 不设补偿：交付成功不改变状态，无法从状态判断"是否已交付"；交付失败由 MQ 重投与死信覆盖，不引入新的状态位。

### 外部系统本地实现

15. 文件上传、保单查询、自动承保请求、承保结果查询、审核交付五个端口各提供一个进程内实现，不发起任何 HTTP 调用。
16. 每次调用按配置休眠（默认 200 毫秒，0 表示不休眠），并打印一条 INFO 交互日志，含交互类型、`claimApplicationNo`、目标系统；端口入参不携带申请编号时该字段记 `-`。
17. 交互日志不得输出证件号、文件原始地址等敏感信息。
18. 返回结果由配置决定：保单是否存在、承保结果、需要拒绝的材料类型清单、审核交付是否故意失败。
19. 平台不再保留 Feign 客户端、`@EnableFeignClients`、openfeign 依赖与 mock HTTP 入口。

## 正常场景

1. 有保单：签收 → 材料处理（本地实现返回成功）→ 保单查询命中 → `WAIT_AUDIT` → 审核交付消费 → 轨迹 `AUDIT_DELIVERY` 的 `STARTED` 与 `COMPLETED`。
2. 无保单：签收 → 材料处理 → `UNDERWRITING` → 承保结果查询返回 `COMPLETED` → `WAIT_AUDIT` → 审核交付消费。
3. 重复消息：同一 `eventId` 再次投递，消费登记判定已消费后直接跳过，不重复调用外部实现，不新增轨迹，数据不变。
4. 状态不匹配的新事件（申请已不在 `WAIT_AUDIT`）：不调用外部实现，轨迹记 `REUSED` 并登记消费。
5. 重新申请：`ACCEPT_FAILED` 后用同一外部申请号重新提交，材料重新处理并继续后续链路。

## 异常场景

1. 材料上传返回失败：申请进入 `ACCEPT_FAILED`，材料轨迹记 `FAILED`，不进入承保分流。
2. 消费抛异常：消息被重投；重投耗尽进入死信；申请状态保持调用前状态，不留半成品数据。
3. 审核交付失败（配置开启）：连续重投后进入 `%DLQ%claim-application-audit-delivery-v1`。
4. 消息对应的申请不存在：按系统异常重投，最终进死信。
5. 状态与尝试次数不匹配的重复消息：记 `REUSED`，不重复上传材料、不重复推进状态。
6. 材料处理消息丢失导致申请长期停在 `PENDING_ACCEPT`：补偿在阈值后重新驱动材料处理并继续链路。

## 边界条件

1. 未知事件类型仍在投递端拒绝，不进入 MQ。
2. 事件载荷缺少 `claimApplicationAttempt` 时消费端拒绝并重投。
3. 补偿与真实消息并发：状态条件更新与消费登记唯一键保证只有一个执行者生效。
4. 本地实现休眠时长为 0 时不调用 `Thread.sleep`。
5. 本地实现被中断时必须恢复中断标记并抛出，不得吞掉 `InterruptedException`。

## 本次范围

- 审核交付消费链路：消费函数、用例、事务服务、交互类型、binding 配置。
- 消费失败口径：重投次数、死信主题、日志要求。
- 五个外部端口的进程内实现与配置项。
- `PENDING_ACCEPT` 滞留补偿。
- 删除 `support/mock`、两个 Feign 客户端、openfeign 依赖与相关配置。
- 契约文档、联调文档、004/006 Spec 的回链同步。

## 非本次范围

- 真实文件系统、承保系统、审核系统对接。
- 死信消费者与自动重放。
- 审核签收、问题件、结案、撤销等审核侧能力。
- traceId/MDC 切面（由 006 T16 承担）。
- 006 其余整改项（P1-4、P2-1、P3-*、P4-*）。

## 验收标准

1. 有保单与无保单两条路径都能从签收走到审核交付消费完成，`t_claim_interaction_log` 出现完整的 `AUDIT_DELIVERY` 行为组。
2. 同一 `eventId` 重复投递时审核交付不再调用外部实现：日志出现"已消费，跳过处理"，`t_claim_interaction_log` 与 `t_claim_consumed_event` 行数不变。
3. 开启审核交付失败开关后，日志可见重投次数与 `max-reconsume-times` 配置一致，最终消息进入 `%DLQ%claim-application-audit-delivery-v1`（broker 日志或控制台可见）。
4. 材料全部失败时申请进入 `ACCEPT_FAILED` 且不产生承保轨迹。
5. 把 `claim.compensation.pending-accept-timeout` 调到 10 秒后，人为丢弃材料消息，补偿在下一轮把申请推进到 `ACCEPTED` 或 `WAIT_AUDIT`。
6. 删除 mock 与服务自指 URL 后，全链路仍可跑通；`grep -rn "mock/underwriting\|mock/file-system\|@FeignClient"` 在 backend 命中 0。
7. `mvn -B clean package` 全反应堆通过；两服务重启日志无新增 ERROR（失败演练产生的 ERROR 除外）。
8. 所有外部调用只剩进程内实现，日志中不出现证件号与文件原始地址。
