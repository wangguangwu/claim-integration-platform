# 024 理赔申请聚合重建

## 背景与目标

当前实现把材料（`Material`）与问题件（`ClaimApplicationIssue`）分别建模为独立聚合根，各自拥有 Repository 与领域服务。实际业务中，材料处理结果、问题件打开/关闭与理赔申请状态必须在同一事务内原子生效；跨聚合协调导致：

1. `IssueDomainService` 同时依赖两个聚合仓储，用条件更新补偿一致性，领域层承担了本应由聚合保证的不变式。
2. `ClaimApplication` 保存 `List<MaterialSubmission>`，落库后材料又由独立仓储管理，同一份材料被建模两次。
3. 状态推进依赖外部传入的 `expectedStatus`，并发控制散落在各用例。
4. 问题件在提交前发布进程内领域事件，且没有订阅者；对外事实实际依赖 Outbox。
5. 命令模型与查询模型混用：按申请号查询影像件、问题件、案件号时只能逐表拼装。
6. 携带既有申请的重复提交只识别了受理失败：问题件状态下租户补材被当作重复请求直接复用，影像件不更新，申请停在 `ISSUE` 无法继续流转。

目标：

- 以 `ClaimApplication` 为唯一聚合根，把材料与问题件收为聚合内实体，由根保证状态与子实体的一致性。
- 每个聚合只有一个 Repository 端口；一次事务只保存一次聚合。
- 用根上的版本号实现乐观锁，删除按 `expectedStatus` 的外部条件更新。
- 删除 `IssueDomainService` 与无订阅者的进程内问题件事件。
- 统一“携带既有申请”的签收处理：`ACCEPT_FAILED` 走重新申请，`ISSUE` 走补材，其余状态复用。
- 提供按平台申请编号查询“申请 + 影像件 / 问题件 / 案件号”的只读读模型接口。

## 业务规则

1. `ClaimApplication` 是理赔申请域唯一聚合根；`Material`、`ClaimApplicationIssue` 是聚合内实体，不得单独定义 Repository、领域服务或独立事务。
2. `ClaimCaseRelation` 是审核签收后追加的关联事实，不改变申请状态，不参与聚合加载和保存，但保留独立端口支撑幂等追加与查询。
3. 聚合内实体不持有 `claimApplicationNo`；归属由根决定，持久化时由转换器写入所属申请编号。
4. 材料处理结果、问题件打开/关闭与申请状态迁移必须在同一事务、同一次聚合保存中完成。
5. 申请状态只允许按既定状态机迁移：
   - `PENDING_ACCEPT → ACCEPTED`（全部材料处理成功）
   - `PENDING_ACCEPT → ACCEPT_FAILED`（任一材料处理失败）
   - `ACCEPTED → WAIT_AUDIT`（有有效保单）
   - `ACCEPTED → UNDERWRITING`（无有效保单）
   - `UNDERWRITING → WAIT_AUDIT`（承保完成）
   - `WAIT_AUDIT → ISSUE`（下发问题件）
   - `ISSUE → WAIT_AUDIT`（审核系统关闭问题件）
   - `ISSUE → PENDING_ACCEPT`（租户补材受理，同时关闭未关闭问题件）
6. 下发问题件必须满足：申请处于 `WAIT_AUDIT`，且当前没有未关闭问题件；同一申请同一时间最多一个未关闭问题件。
7. 审核系统首次关闭问题件必须满足：申请处于 `ISSUE`，问题件存在且未关闭；关闭后申请恢复 `WAIT_AUDIT`。
8. 问题件关闭是幂等操作：先定位指定问题件，若已关闭则直接返回 `closed=false`；若未关闭，再按规则 7 校验并关闭。重复关闭不重复改状态、不重复写轨迹。
9. 问题件历史全部保留，关闭后不删除；同一申请可先后产生多个问题件。
10. 重新申请只允许携带平台申请编号且申请处于 `ACCEPT_FAILED` 的提交：旧材料逻辑删除，按新批次写入新材料，受理尝试次数加 1，人员信息整体替换，状态回到 `PENDING_ACCEPT`，后续按首次签收链路重新处理材料并重新交付审核。
11. 租户补材只允许携带平台申请编号且申请处于 `ISSUE` 的提交：根关闭当前未关闭问题件（关闭人记为 `TENANT_SUPPLEMENT`），旧材料逻辑删除，按新批次写入全量影像件，受理尝试次数加 1，状态迁移到 `PENDING_ACCEPT`；补材不接收人员变更，四类人员保持原值，报文人员字段不生效、也不做比对与报错；影像件仍异步处理，处理完成后的承保分流与审核交付与首次签收一致。
12. 补材时若申请处于 `ISSUE` 但不存在未关闭问题件，按状态不合法拒绝，不写入任何变更。
13. 租户协议可以携带平台申请编号；携带时以“租户编码 + 外部申请编号”定位申请，并要求平台申请编号与定位结果一致；定位不到或编号不一致按请求非法拒绝（`CLAIM_APPLICATION_INTAKE_INVALID`），不得新建申请。
14. 未携带平台申请编号的提交只允许首次创建或重复请求复用；定位到既有申请时一律返回 `REUSED`，不得触发重新申请或补材。
15. 携带平台申请编号的提交按申请当前状态分流：`ACCEPT_FAILED` 走重新申请，`ISSUE` 走补材，其他状态一律按重复请求复用，不再改动人员、材料与状态。
16. 聚合持久化使用 `t_claim_application.version` 乐观锁；版本冲突时保存返回失败，由用例按幂等或并发冲突处理，不使用外部传入的期望状态。
17. 聚合根、材料、问题件、交互轨迹、消费登记和 Outbox 的写入必须由同一个 Application Service `@Transactional` 边界保护；任一写入失败时整体回滚，不得只提交根状态或版本号。
18. 问题件下发导致申请离开 `WAIT_AUDIT` 后，签收判定快照中该外部申请编号的可复用结论必须失效，避免后续补材被误判为重复请求。
19. 查询按平台申请编号进行，申请编号全局唯一；查询以当前租户上下文为前置条件，申请不属于当前租户时按申请不存在处理：
    - 影像件视图只返回未逻辑删除的材料，按主键升序。
    - 问题件视图返回该申请全部问题件（含已关闭），按创建时间倒序。
    - 案件视图返回该申请全部案件关联，按创建时间升序。
    - 查询不改变任何业务状态、不写交互轨迹。
20. 既有 HTTP 路径、既有请求/响应字段语义、MQ Topic/消费组/消息结构和响应包裹结构均不改变；租户协议新增向后兼容的可选平台申请编号字段，本次新增三个查询接口。
21. 错误码语义不变：申请不存在、状态不合法、问题件已存在、问题件不存在等沿用现有错误码；新增 `ClaimApplicationQueryErrorCode` 只服务新增查询接口，包含 `CLAIM_APPLICATION_QUERY_NOT_FOUND`（404）、`CLAIM_APPLICATION_QUERY_INVALID`（400）、`CLAIM_APPLICATION_QUERY_SYSTEM_ERROR`（500）。

## 正常场景

- 首次签收：根创建材料实体，一次插入申请与材料；写交互轨迹与 Outbox。
- 重新申请：租户携带一致的平台申请编号，根逻辑删除旧材料、替换人员、追加新批次材料并递增尝试次数，状态回到 `PENDING_ACCEPT`；一次保存完成，随后按首次签收链路处理。
- 租户补材：租户携带一致的平台申请编号，根关闭未关闭问题件、逻辑删除旧材料、保留原人员、追加新批次材料并递增尝试次数，状态从 `ISSUE` 迁移到 `PENDING_ACCEPT`；一次保存完成，随后异步处理影像件并重新交付审核。
- 携带平台编号的重复提交：申请处于可复用状态时直接返回原平台申请编号，不改动人员、材料与状态。
- 材料处理：事务外上传文件，事务内根应用每份材料的处理结果并决定 `ACCEPTED` / `ACCEPT_FAILED`；一次保存完成。
- 承保分流：根迁移到 `WAIT_AUDIT` 或 `UNDERWRITING`，保存成功后写审核交付 Outbox。
- 承保回调：根从 `UNDERWRITING` 迁移到 `WAIT_AUDIT`，保存成功后写审核交付 Outbox。
- 问题件下发：根校验状态与未关闭问题件，追加问题件实体并迁移到 `ISSUE`；一次保存完成，随后使签收快照失效。
- 审核系统关闭问题件：根定位未关闭问题件，关闭实体并迁移回 `WAIT_AUDIT`；一次保存完成。
- 审核签收：只追加案件关联与交互轨迹，不加载、不修改申请聚合。
- 查询影像件：返回申请摘要与未删除材料列表；申请不存在返回 404。
- 查询问题件：返回申请摘要与全部问题件；申请不存在返回 404。
- 查询案件号：返回申请摘要与全部案件关联；申请不存在返回 404。
- 查询跨租户申请：按申请不存在返回 404。

## 异常场景与边界条件

- 对同一申请并发下发问题件：只有一个保存成功；失败方重新读取，若同一 `issueNo` 已存在按幂等返回，否则返回状态不合法。
- 对同一申请并发关闭问题件：只有一个保存成功；失败方重新读取，若已关闭返回 `closed=false`。
- 材料处理消息重投：消费登记命中返回 `SKIPPED`；申请已不是 `PENDING_ACCEPT` 或尝试次数不匹配返回 `REUSED`；保存版本冲突同样返回 `REUSED`。
- 承保分流或回调并发执行：保存版本冲突返回 `REUSED`，不重复写 Outbox。
- 重新申请并发：保存版本冲突的一方重读申请，受理尝试次数已增大时按重复请求返回 `REUSED`，不覆盖已写入的人员与材料；尝试次数未变时返回 `CLAIM_APPLICATION_INTAKE_BUSY`（HTTP 503），不静默复用。
- 补材并发：保存版本冲突的一方重读申请，受理尝试次数已增大时按重复请求返回 `REUSED`，不关闭问题件、不覆盖已写入的人员与材料；尝试次数未变时返回 `CLAIM_APPLICATION_INTAKE_BUSY`（HTTP 503）。
- 补材与审核系统关闭并发：只有一方保存成功；补材失败方重读申请，受理尝试次数未变且状态已离开 `ISSUE`（审核系统已关闭问题件）时按状态不合法拒绝（`ISSUE_APPLICATION_STATUS_INVALID`，HTTP 400），不产生第二份材料；重读后仍在 `ISSUE` 时返回 `CLAIM_APPLICATION_INTAKE_BUSY`（HTTP 503），由租户重试。
- 未携带平台申请编号但定位到 `ACCEPT_FAILED` 或 `ISSUE` 申请：按重复请求复用，不写入任何变更。
- 补材时申请已不在 `ISSUE`：按状态不合法拒绝，不写入任何变更。
- 携带平台申请编号但定位不到申请：返回 `CLAIM_APPLICATION_INTAKE_INVALID`（HTTP 400），不新建申请。
- 携带平台申请编号但与定位结果不一致：返回 `CLAIM_APPLICATION_INTAKE_INVALID`（HTTP 400），不写入任何变更。
- 签收快照失效失败：只记录日志，不影响问题件下发结果；携带平台编号的提交不依赖快照，以数据库状态为准。
- 问题件关闭时申请已不在 `ISSUE`：返回状态不合法错误，不写入任何变更。
- 查询不存在的申请编号：返回 `CLAIM_APPLICATION_QUERY_NOT_FOUND`（HTTP 404）。
- 查询的申请属于其他租户：按不存在处理，返回 `CLAIM_APPLICATION_QUERY_NOT_FOUND`（HTTP 404），不暴露存在性。
- 查询到空集合（有申请但无材料/问题件/案件）：返回成功响应与空数组，不返回 404。
- 已逻辑删除的材料不出现在影像件视图；历史问题件与历史案件必须出现在对应视图中。
- 数据库版本列缺失或未初始化为 0：迁移脚本必须显式补齐，默认值为 0。

## 本次范围

- 领域包重组：`domain/claim` 下新增 `material`、`issue` 实体包，删除 `domain/material`、`domain/issue` 独立聚合包。
- 聚合根承载材料与问题件集合、版本号与新的状态方法；删除 `enterIssue`、`resumeAudit`、`accept`、`failAccept` 的公开使用。
- Repository 合并为单一 `ClaimApplicationRepository`：`insert`、`save`、按申请号/外部号/问题件号查询、编号与状态扫描。
- 删除 `IssueDomainService`、`MaterialRepository`、`ClaimApplicationIssueRepository` 与对应实现。
- 删除无订阅者的 `IssueRaisedEvent`、`IssueClosedEvent`、`IssueDomainEventPublisher` 及 Spring 实现与配置。
- 租户协议请求、签收命令与协议转换器追加可选平台申请编号；签收处置新增 `SUPPLEMENTED`。
- 签收事务服务补齐 `ISSUE` 补材分支：补材只替换材料，人员保持原值；重新申请仍替换人员。
- 问题件下发后使签收判定快照失效；携带平台编号的提交跳过可复用快照。
- 持久化类统一迁入 `infrastructure/claim/persistence`；新增读模型实现 `infrastructure/query/persistence`。
- 新增 `usecase/query` 包：详情查询用例、读模型端口与视图模型。
- 新增 `interfaces/claim/web/ClaimApplicationQueryController` 与三个查询接口；查询沿用 `TenantContext` 租户隔离，跨租户申请按不存在处理。
- 新增 `ClaimApplicationQueryErrorCode` 并登记 HTTP 状态映射。
- `t_claim_application` 增加 `version` 列及迁移脚本。
- ArchUnit 用例契约白名单补充 `usecase.query.port/model`。
- 受影响的既有单元测试按新 API 调整；删除被测类已不存在的测试。
- 架构、导航、集成与测试文档同步。

## 非本次范围

- 不引入事件溯源、领域事件总线、CQRS 独立数据库或 CDC 投影。
- 不新增读模型表，不改变现有四张业务表的结构（除 `version` 列）。
- 不改变 MQ Topic、消费组、事件类型、消息 JSON 与现有 HTTP 路径/字段。
- 不改变材料文件处理器、承保/保单端口、Redis 快照结构、锁与号段逻辑。
- 不引入细粒度权限模型；查询只沿用 `TenantContext` 的租户隔离，不做角色或数据权限改造。
- 不对子表增加版本号或逻辑删除字段。
- 不清理历史材料行，不改变逻辑删除语义。
- 不新增本 Spec 未定义的业务功能，不新增框架依赖。

## 验收标准

1. `domain/claim` 是唯一聚合根所在包；`domain/material`、`domain/issue` 目录不存在，`MaterialRepository`、`ClaimApplicationIssueRepository`、`IssueDomainService` 全仓无引用。
2. `ClaimApplication` 持有 `List<Material>` 与 `List<ClaimApplicationIssue>`，`restore` 能重建两类子实体；`submit`、`reapply`、`supplement` 按批次生成材料实体。
3. 问题件下发、关闭、材料处理、承保分流、回调、重新申请、补材均通过一次 `ClaimApplicationRepository.save` 完成；仓储接口不再提供 `updateStatus`、`updateForReapply`，也不再接收外部传入的期望状态。
4. `IssueDomainService` 与其进程内事件相关类被删除，构建无残留引用。
5. 并发保存通过 `version` 条件更新拦截：构造版本冲突时 `save` 返回 `false`，用例返回 `REUSED` 或明确冲突错误，不产生重复业务数据。
6. 三个新增查询接口返回约定字段；申请不存在或不属于当前租户返回 404；空子集合返回 200 + 空数组；查询不写交互轨迹。
7. 既有问题件按案件号查询接口响应结构不变，内部改为读模型实现。
8. 除本 Spec 明确的聚合收口、平台编号分流、租户补材和新增查询接口外，签收、材料、承保、审核签收、问题件下发/关闭链路在运行栈上行为与 023 保持一致。
9. 携带平台申请编号、申请状态为 `ACCEPT_FAILED` 的提交返回 `REAPPLIED`，状态回到 `PENDING_ACCEPT`，旧材料逻辑删除、人员与材料整体替换、尝试次数加 1。
10. 携带平台申请编号、申请状态为 `ISSUE` 的提交返回 `SUPPLEMENTED`，未关闭问题件被关闭（关闭人为 `TENANT_SUPPLEMENT`），旧材料逻辑删除、新批次材料写入、尝试次数加 1，状态迁移到 `PENDING_ACCEPT`；四类人员保持补材前的原值，即使报文携带不同人员也不生效。
11. 补材后影像件按异步链路重新处理，处理完成的应用重新经历承保分流/回调并交付审核；审核系统对已由补材关闭的问题件再次调用关闭返回 `closed=false`。
12. 携带平台申请编号但申请不存在或编号不一致时返回 `CLAIM_APPLICATION_INTAKE_INVALID`（HTTP 400），且不新建申请。
13. 问题件下发后，同一外部申请编号再次提交不再命中可复用快照；快照失效失败不影响下发结果。
14. ArchUnit 全部规则通过，新增查询端口与视图模型被白名单覆盖。
15. 全量 Maven 构建、既有测试（按新 API 调整后）与格式化通过，无遗留 TODO 与临时代码。
