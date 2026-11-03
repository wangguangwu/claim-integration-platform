# 024 理赔申请聚合重建 · 任务清单

## 执行顺序与编译检查点

- 批次顺序即执行顺序。
- T2–T6 属于同一次聚合重构窗口：领域模型、持久化与旧调用方需要一起切换，允许中间态不编译；**T7 完成时必须恢复模块编译通过**。
- T15 是唯一的旧模型清理任务，必须在 T7–T14 全部完成后执行；在此之前，旧公开状态方法、旧仓储方法与旧仓储类作为过渡保留，避免提前删除导致未迁移链路编译失败。

## 任务批次

### Batch 1：数据库与领域模型

#### T1：申请主表增加版本列
- 产物：建表脚本与迁移脚本。
- 路径：`deploy/mysql/init/02-application-schema.sql`、`deploy/mysql/migration/024-add-claim-application-version.sql`。
- 完成条件：
  - `t_claim_application` 增加 `version BIGINT UNSIGNED NOT NULL DEFAULT 0`，位置在 `claim_application_attempt` 之后。
  - 迁移脚本头部写明一次性执行方式与执行命令；执行前通过 `information_schema.COLUMNS` 检查列是否已存在，MySQL 8 不使用 `ADD COLUMN IF NOT EXISTS`。
  - 历史数据版本均初始化为 `0`，子表结构不变。

#### T2：材料实体迁入申请聚合
- 产物：`domain/claim/material/{Material,MaterialStatus,MaterialProcessingResult}`。
- 路径：`domain/material/**` 下实体类迁入 `domain/claim/material/**`；`MaterialRepository` 端口暂留原位置。
- 完成条件：
  - `Material` 不再持有 `claimApplicationNo`，提供创建、重建、应用处理结果和逻辑删除行为。
  - `MaterialProcessingResult` 约束成功结果必须含上传地址。
  - 全仓 import 同步更新；类注释与异常信息同步为聚合内实体语义。

#### T3：问题件实体迁入申请聚合
- 产物：`domain/claim/issue/{ClaimApplicationIssue,ClaimApplicationIssueStatus,exception/ClaimApplicationIssueErrorCode}`。
- 路径：`domain/issue/**` 下实体与错误码迁入 `domain/claim/issue/**`；`ClaimApplicationIssueRepository`、领域服务与进程内事件暂留原位置。
- 完成条件：
  - `ClaimApplicationIssue` 不再持有 `claimApplicationNo`，保留创建、重建、关闭与状态判断行为。
  - 错误码常量和既有错误消息不变，仅调整包路径；全仓 import 同步更新。
  - 受影响测试 `ClaimApplicationIssueTest` 同步迁移包路径并适配 `create` 签名。

#### T4：重建 `ClaimApplication` 聚合根
- 产物：新的 `ClaimApplication` 聚合行为与领域单元测试。
- 路径：`domain/claim/ClaimApplication.java`、`src/test/java/**/domain/claim/`。
- 完成条件：
  - 根持有版本号、当前有效材料和全部问题件，`restore` 可重建完整聚合。
  - 实现 `reapply`、`supplement`、`applyMaterialResults`、`raiseIssue`、`closeIssue` 与版本回填方法。
  - `reapply` 与 `supplement` 的人员替换规则严格区分；补材关闭人固定为 `TENANT_SUPPLEMENT`。
  - `applyMaterialResults` 校验结果与全部待处理材料一一对应，拒绝重复、缺失、未知或非待处理材料结果。
  - `closeIssue` 对已关闭问题件直接返回 `false`；仅未关闭问题件要求申请处于 `ISSUE`。
  - 新增并复用无框架依赖的 `AssertUtil`，替换聚合、子实体和值对象中重复的非空、非空白和非空集合校验；字段校验仍抛 `IllegalArgumentException` 且错误文本不变，状态迁移与材料结果等业务拒绝改抛 `DomainException` 并使用 `ClaimApplicationErrorCode` / `ClaimApplicationIssueErrorCode`。
  - `getMaterials()` 与 `getIssues()` 返回只读快照；`restore` 只允许持久化层与领域测试调用，由 ArchUnit 规则约束。
  - 旧公开状态迁移方法 `accept`、`failAccept`、`enterIssue`、`resumeAudit` 暂不删除，供 T7–T14 未迁移链路过渡使用；T15 统一删除。
  - 重写 `ClaimApplicationIssueTransitionTest` 使用 `raiseIssue` / `closeIssue` / `supplement` / `applyMaterialResults`，覆盖状态机、幂等关闭、补材与材料失败；测试执行随 T7 编译检查点一起验证。

### Batch 2：聚合持久化与仓储

#### T5：持久化类归并与转换器改造
- 产物：`infrastructure/claim/persistence` 下的根、材料、问题件、案件关联 DO/Mapper/Converter。
- 路径：由 `infrastructure/{intake,material,issue,claim}/persistence` 迁入。
- 完成条件：
  - `ClaimApplicationDO` 映射 `version` 列。
  - 转换器以根传入的申请编号持久化材料与问题件，读方向按新 `restore` 签名重建聚合（本任务先按空子集合加载，完整加载由 T6 实现）。
  - `PersonInfoJsonConverter` 迁入 `infrastructure/claim/persistence`。
  - 旧持久化包、旧 import 和空目录清理完成。

#### T6：重写聚合仓储
- 产物：`MyBatisClaimApplicationRepository`。
- 路径：`infrastructure/claim/persistence/MyBatisClaimApplicationRepository.java`。
- 完成条件：
  - 实现 `insert`、`save`、按申请号/外部号/问题件号查询、编号查询和补偿扫描。
  - `save` 按 `id + version` 条件更新根；更新行数为 0 时返回 `false` 且不写子表；成功保存后回填新版本。
  - 加载时只取未删除材料、取全部问题件。
  - `updateStatus`、`updateForReapply` 作为过渡方法保留，实现改为供未迁移用例使用；T15 删除。

### Batch 3：写用例改造

#### T7：签收、重新申请与补材分流（编译检查点）
- 产物：签收 UseCase、事务服务、租户请求/转换器、Command 与处置结果。
- 路径：`usecase/intake/`、`interfaces/intake/tenantprotocol/`。
- 完成条件：
  - 两个租户请求、转换器和 Command 追加可选 `claimApplicationNo`；空串按未携带处理。
  - 未携带平台申请编号时，已存在申请一律 `REUSED`；携带编号时按“租户编码 + 外部申请编号”查库并严格比对，读不到或不一致抛 `CLAIM_APPLICATION_INTAKE_INVALID`，不新建。
  - 仅携带且校验通过平台申请编号的 `ACCEPT_FAILED` 走 `REAPPLIED`，`ISSUE` 走 `SUPPLEMENTED`；携带平台编号的提交跳过快照查询。
  - 首次签收、重新申请和补材均只调用一次聚合 `insert` 或 `save`，并在同一事务内写交互轨迹与 Outbox；补材复用 `CLAIM_APPLICATION_REAPPLIED` 事件。
  - `save=false` 时事务服务抛内部冲突信号并回滚本次事务（不留半截轨迹），用例在保存事务之外调用独立解析入口（新事务）重读分类：尝试次数增大返回 `REUSED` 并写复用轨迹；补材场景状态离开 `ISSUE` 返回 `ISSUE_APPLICATION_STATUS_INVALID`；其余返回 `CLAIM_APPLICATION_INTAKE_BUSY`。
  - 冲突重读不得复用保存失败的原事务，避免 `REPEATABLE_READ` 读到旧快照。
  - 补材与重新申请保存成功后调用 `markMutable`，失效失败只记日志。
  - `ClaimApplicationIntakeOutcome` 与快照升级逻辑适配 `SUPPLEMENTED`，不再把补材结果写入可复用快照。
  - 本任务完成后模块恢复编译，`ClaimApplicationIntakeUseCaseTest` 按新协作者调整并通过。

#### T8：材料处理
- 产物：材料处理 UseCase 与事务服务。
- 路径：`usecase/material/`。
- 完成条件：
  - 事务外上传当前聚合的待处理材料，组装完整 `MaterialProcessingResult` 集合。
  - 事务内重新加载聚合，校验受理尝试和 `PENDING_ACCEPT`，调用 `applyMaterialResults` 后一次 `save`。
  - 聚合保存、消费登记、交互轨迹和后续 Outbox 在同一事务中成功或回滚。
  - 保留 `SKIPPED`、`REUSED`、成功后承保分流语义；不再调用 `accept` / `failAccept` / `updateStatus`。

#### T9：承保分流与承保回调
- 产物：承保事务服务与回调事务服务。
- 路径：`usecase/underwriting/`。
- 完成条件：
  - 根完成状态迁移后调用 `save`；保存失败按 `REUSED` 处理。
  - 仅保存成功才写审核交付 Outbox，聚合保存、交互轨迹和 Outbox 同事务。
  - 不再调用 `updateStatus`，补偿扫描与重放语义不变。

#### T10：问题件下发与关闭
- 产物：问题件下发与关闭用例调整。
- 路径：`usecase/issue/`。
- 完成条件：
  - 下发通过根 `raiseIssue` 后一次 `save`；保存冲突时由用例在保存事务之外重读，按相同 `issueNo` 幂等或状态冲突处理。
  - 关闭先定位 Issue；已关闭返回 `closed=false`，未关闭才要求申请处于 `ISSUE` 并调用根 `closeIssue`；保存冲突同样在事务外重读。
  - 冲突重读不得复用保存失败的原事务。
  - 下发成功后调用签收快照失效；失效异常只记录一次日志，不回滚领域变更。
  - 聚合保存、交互轨迹和消费/幂等登记同事务；不再依赖 `IssueDomainService`。

#### T11：审核签收引用调整
- 产物：`AuditSignedTransactionService`。
- 完成条件：
  - 仅调整 `ClaimCaseRelation` 的 import 和持久化路径。
  - 继续只追加关联和交互轨迹，不加载或保存申请聚合。

### Batch 4：读模型与接口

#### T12：详情查询用例与视图模型
- 产物：`usecase/query/**`。
- 完成条件：
  - 新增详情读模型端口与申请、材料、问题件、案件视图 record；端口方法均携带 `tenantCode`。
  - 查询用例对申请不存在或租户不匹配抛 `ApplicationException(CLAIM_APPLICATION_QUERY_NOT_FOUND)`。
  - 既有按案件号的问题件查询改为先按租户与案件号解析申请编号，再调用 `findOpenIssue`，对外响应不变。
  - 查询不加载领域聚合、不写交互轨迹。
  - 同步把 `..usecase.query.port..`、`..usecase.query.model..` 加入 ArchUnit 契约白名单。

#### T13：读模型基础设施实现
- 产物：`MyBatisClaimApplicationDetailQuery`。
- 路径：`infrastructure/query/persistence/`。
- 完成条件：
  - 直接使用 DO Mapper 查询申请、未删除材料、全部问题件和案件关联。
  - 根查询同时带 `claim_application_no` 与 `tenant_code` 条件；租户不匹配按不存在返回空。
  - 排序、空集合和申请不存在行为符合 Requirements；不写任何表。

#### T14：查询接口与错误码
- 产物：查询 Controller、查询错误码和 HTTP 状态映射。
- 完成条件：
  - 在 `/api/claim/v1/application/{claimApplicationNo}/{materials|issues|cases}` 提供三个 GET 接口，返回统一 `ApiResponse`。
  - 控制器从 `TenantContext` 取租户，缺少上下文返回 `CLAIM_APPLICATION_QUERY_INVALID`；实现 `ApiErrorCodeProvider`。
  - 新增 `ClaimApplicationQueryErrorCode`（`CLAIM_APPLICATION_QUERY_NOT_FOUND` / `CLAIM_APPLICATION_QUERY_INVALID` / `CLAIM_APPLICATION_QUERY_SYSTEM_ERROR`）并登记状态映射。
  - 更新 `BusinessErrorStatusRegistryTest` 的期望映射；既有问题件接口响应保持不变；编译通过。

### Batch 5：旧模型清理

#### T15：删除独立材料与问题件模型
- 产物：旧聚合、仓储、领域服务、事件与过渡方法的清理结果。
- 路径：`domain/{material,issue}`、`infrastructure/{material,issue,intake}`。
- 完成条件：
  - 删除 `MaterialRepository`、`ClaimApplicationIssueRepository`、`IssueDomainService`、`IssueDomainServiceConfiguration`、无订阅者的问题件事件与发布器。
  - 删除 `ClaimApplicationIntakeMaterialFactory`。
  - 删除聚合上的 `accept`、`failAccept`、`enterIssue`、`resumeAudit`，删除端口与仓储上的 `updateStatus`、`updateForReapply`。
  - 删除被测类已不存在的测试（含 `IssueDomainServiceTest`）；全仓无旧类、旧包和旧 Repository 引用，编译通过。

### Batch 6：测试、文档与验证

#### T16：测试与架构规则
- 产物：迁移后的测试与 ArchUnit 规则。
- 完成条件：
  - 覆盖材料结果完整性、材料失败、问题件首次/重复关闭、重新申请、补材、版本冲突和过期消息。
  - 覆盖聚合子表、交互轨迹、消费登记或 Outbox 任一写入失败时的事务回滚。
  - 覆盖签收并发三类结果：同类请求 `REUSED`、补材遇审核关闭 `ISSUE_APPLICATION_STATUS_INVALID`、其余冲突 `CLAIM_APPLICATION_INTAKE_BUSY`。
  - 覆盖查询跨租户 404、空集合、不写交互轨迹；ArchUnit 全部规则通过。

#### T17：文档同步
- 产物：架构、导航、集成与测试文档。
- 完成条件：
  - 聚合边界、平台编号强制分流、补材、读模型、乐观锁、租户隔离与事务边界与最终代码一致。
  - 删除独立材料/问题件聚合与 `IssueDomainService` 的旧描述。

#### T18：构建与运行栈回归
- 产物：验证结论。
- 完成条件：
  - 使用 JDK 21 执行 `mvn -B -pl backend/claim-application-service -am clean package` 成功。
  - 既有库已按迁移脚本补齐 `version` 列并验证默认值为 0。
  - ArchUnit、单元测试、并发测试、事务回滚测试与运行栈黄金链路通过。
  - 首签、同号复用、携带平台编号重新申请、携带平台编号补材、材料处理、承保、审核签收、问题件、读模型查询（含跨租户 404）均符合 Requirements。
