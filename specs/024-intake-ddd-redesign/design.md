# 024 理赔申请聚合重建 · 设计

## 1. 模型裁定

| 对象 | 裁定 | 理由 |
| --- | --- | --- |
| `ClaimApplication` | 唯一聚合根 | 材料结果、问题件打开/关闭都要原子修改申请状态，一致性边界就是申请 |
| `Material` | 聚合内实体 | 材料处理结果决定 `ACCEPTED/ACCEPT_FAILED`，不能脱离根单独提交 |
| `ClaimApplicationIssue` | 聚合内实体 | 问题件打开/关闭必须与 `WAIT_AUDIT/ISSUE` 迁移同事务 |
| `ClaimCaseRelation` | 聚合外追加事实 | 审核签收只追加关联，不改变申请状态；它不是本服务的聚合根，不参与申请聚合加载和保存 |
| 查询场景 | 读模型 | 查询组合不等于聚合组合，不为读操作加载并组装聚合 |

本 Spec 取代 023 中“`Material`、`ClaimApplicationIssue` 为独立聚合”的布局结论；023 的其他边界裁定（端口归属、异常层级、五阶段流水线）保持有效。

## 2. 目标包结构

```text
domain/claim
├── ClaimApplication.java
├── ClaimApplicationStatus.java
├── ClaimCaseRelation.java
├── MaterialSubmission.java
├── material
│   ├── Material.java
│   ├── MaterialStatus.java
│   └── MaterialProcessingResult.java
├── issue
│   ├── ClaimApplicationIssue.java
│   ├── ClaimApplicationIssueStatus.java
│   └── exception/ClaimApplicationIssueErrorCode.java
├── person
│   └── ...
├── exception
│   ├── ClaimApplicationErrorCode.java
│   ├── ClaimApplicationIntakeErrorCode.java
│   └── ClaimApplicationQueryErrorCode.java
└── port
    ├── ClaimApplicationRepository.java
    └── ClaimCaseRelationRepository.java

usecase/query
├── ClaimApplicationDetailQueryUseCase.java
├── port/ClaimApplicationDetailQuery.java
└── model
    ├── ClaimApplicationSummary.java
    ├── ClaimApplicationWithMaterials.java
    ├── ClaimApplicationWithIssues.java
    ├── ClaimApplicationWithCases.java
    ├── MaterialView.java
    ├── IssueView.java
    └── CaseView.java

infrastructure/claim/persistence
├── ClaimApplicationDO.java / ClaimApplicationDOMapper.java / ClaimApplicationDataConverter.java
├── PersonInfoJsonConverter.java
├── MaterialDO.java / MaterialDOMapper.java / MaterialDataConverter.java
├── ClaimApplicationIssueDO.java / ClaimApplicationIssueDOMapper.java / ClaimApplicationIssueConverter.java
├── ClaimCaseRelationDO.java / ClaimCaseRelationDOMapper.java / ClaimCaseRelationConverter.java
├── MyBatisClaimApplicationRepository.java
└── ClaimCaseRelationRepositoryImpl.java

infrastructure/query/persistence
└── MyBatisClaimApplicationDetailQuery.java

interfaces/claim/web
└── ClaimApplicationQueryController.java
```

## 3. 聚合设计

### 3.1 聚合根字段

| 字段 | 说明 |
| --- | --- |
| `id` | 数据库主键，仅持久化使用 |
| `version` | 乐观锁版本号，`null` 表示尚未持久化 |
| `tenantCode`、`claimApplicationNo`、`externalClaimApplicationNo` | 身份字段 |
| `status`、`claimApplicationAttempt` | 生命周期状态与受理尝试次数 |
| `applicant`、`primaryInsured`、`accidentPerson`、`payee` | 标准化人员信息 |
| `List<Material> materials` | 当前有效材料（已逻辑删除的历史材料不加载） |
| `List<ClaimApplicationIssue> issues` | 全部问题件历史，含已关闭 |

子实体不再持有 `claimApplicationNo`；归属由根决定，转换器持久化时写入根的申请编号。

### 3.2 根方法

```java
public static ClaimApplication submit(String tenantCode, String externalClaimApplicationNo,
        Applicant applicant, PrimaryInsured primaryInsured, AccidentPerson accidentPerson, Payee payee,
        List<MaterialSubmission> materials, String batchNo);

public static ClaimApplication restore(Long id, String tenantCode, String claimApplicationNo,
        String externalClaimApplicationNo, ClaimApplicationStatus status, Integer claimApplicationAttempt,
        Applicant applicant, PrimaryInsured primaryInsured, AccidentPerson accidentPerson, Payee payee,
        Long version, List<Material> materials, List<ClaimApplicationIssue> issues);

public void bindClaimApplicationNo(String claimApplicationNo);
public void bindId(Long id);
public void bindVersion(Long version);              // 仅供持久化层回填插入版本或同步保存后的版本

public void reapply(ClaimApplication submitted, String batchNo);
public void supplement(ClaimApplication submitted, String batchNo);
public void applyMaterialResults(List<MaterialProcessingResult> results);

public void waitAudit();
public void startUnderwriting();
public void finishUnderwriting();

public void raiseIssue(String issueNo, String reason, String auditor, String issueContent);
public boolean closeIssue(String issueNo, String auditor);

public boolean isReapplicable();
public boolean isSupplementable();
```


- `submit` 校验租户、外部编号、材料非空，并按 `batchNo` 把每份 `MaterialSubmission` 转成 `Material` 实体。
- `bindClaimApplicationNo` 绑定编号、置 `PENDING_ACCEPT`、尝试次数置 1。
- `reapply` 仅允许 `ACCEPT_FAILED`：当前材料全部 `softDelete()`，按新批次追加材料，人员信息整体替换，尝试次数加 1，状态回 `PENDING_ACCEPT`。
- `supplement` 仅允许 `ISSUE` 且存在未关闭问题件：先用 `SUPPLEMENT_AUDITOR` 关闭当前未关闭问题件，旧材料全部 `softDelete()`，按新批次追加材料，尝试次数加 1，状态由 `ISSUE` 迁移到 `PENDING_ACCEPT`；人员信息保持原值，`submitted` 中的四类人员字段一律忽略，也不做一致性校验；状态不符或缺少未关闭问题时抛 `DomainException(ISSUE_APPLICATION_STATUS_INVALID)`。
- 重新申请与补材的差别只在人员：`reapply` 用 `submitted` 的人员整体替换，`supplement` 保留根上原人员。
- `SUPPLEMENT_AUDITOR`（`TENANT_SUPPLEMENT`）作为根上的公有常量，标识“租户补材关闭问题件”这一非审核系统行为。
- `applyMaterialResults` 仅允许 `PENDING_ACCEPT`：按 `materialId` 定位实体并写入处理结果，再根据全部材料状态迁移到 `ACCEPTED` 或 `ACCEPT_FAILED`。
- `raiseIssue` 校验 `WAIT_AUDIT` 且无未关闭问题件，追加问题件实体并迁移到 `ISSUE`。
- `closeIssue` 按 `issueNo` 定位问题件；不存在抛 `ISSUE_NOT_FOUND`，已关闭时不校验申请状态、直接返回 `false`（幂等）；未关闭时必须校验申请为 `ISSUE`，然后关闭实体并迁移回 `WAIT_AUDIT`。
- `enterIssue`、`resumeAudit`、`accept`、`failAccept` 不再公开，状态迁移只由上述业务方法触发。
- 状态迁移非法、材料结果集不合法与材料自身状态非法统一抛 `DomainException`：申请级错误使用 `ClaimApplicationErrorCode`（`CLAIM_APPLICATION_STATUS_INVALID`、`CLAIM_APPLICATION_MATERIAL_RESULT_INVALID`、`CLAIM_APPLICATION_MATERIAL_STATUS_INVALID`），问题件级错误使用 `ClaimApplicationIssueErrorCode`（`ISSUE_STATUS_INVALID`）。JDK 异常只保留给真正的编程错误，例如主键重复绑定、批次编号不一致或持久化前置条件不满足。
- `getMaterials()` 与 `getIssues()` 返回只读快照，调用方不能通过集合引用增删聚合内实体；实体状态迁移只能经根方法触发，由 ArchUnit 规则约束。
- `restore` 是持久化反水化入口，只允许 `infrastructure/claim/persistence` 与领域测试调用；其他层只能经 `submit` 或仓储加载获得聚合。
- 可复用签收结论由 `ClaimApplicationStatus.isReusableAfterIntake()` 回答，快照模型只负责携带结论，不再自行维护状态集合。
- 字段非空、非空白、集合非空和布尔前置条件统一复用 `claim-foundation-common` 的 `AssertUtil`；该工具只抛 `IllegalArgumentException`，不得改写既有异常类型或错误文本。

### 3.3 子实体

| 实体 | 方法 | 约束 |
| --- | --- | --- |
| `Material` | `createPending(batchNo, MaterialSubmission)`、`restore(...)`、`applyProcessResult(boolean, String)`、`softDelete()` | 仅 `PENDING_PROCESS` 可写处理结果；重新申请与补材时写 `deleted=true` 与删除时间 |
| `ClaimApplicationIssue` | `create(issueNo, reason, auditor, issueContent)`、`restore(...)`、`close(auditor)`、`isOpen()`、`isClosed()` | 字段校验保持 012 规则（原因 ≤512、审核人 ≤128）；仅 `OPEN` 可关闭 |

`MaterialProcessingResult` 为值对象：

```java
public record MaterialProcessingResult(Long materialId, boolean succeeded, String uploadedFileUrl) {
}
```

成功结果必须携带非空 `uploadedFileUrl`，失败结果忽略该字段。

`applyMaterialResults` 必须校验结果集与当前所有 `PENDING_PROCESS` 材料一一对应：材料 ID 不得重复、不得缺失、不得包含未知或非待处理材料。校验通过后才应用结果并依据全部材料状态决定 `ACCEPTED` 或 `ACCEPT_FAILED`，避免部分结果导致申请提前受理成功。

## 4. 持久化设计

### 4.1 Repository 端口

```java
public interface ClaimApplicationRepository {

    Optional<ClaimApplication> findByTenantAndExternalClaimApplicationNo(String tenantCode, String externalClaimApplicationNo);
    Optional<ClaimApplication> findByClaimApplicationNo(String claimApplicationNo);
    Optional<ClaimApplication> findByIssueNo(String issueNo);
    Optional<String> findMaxClaimApplicationNoByPrefix(String numberPrefix);
    List<ClaimApplication> findByStatusUpdatedBefore(ClaimApplicationStatus status,
                                                     LocalDateTime updatedBefore, int limit);

    void insert(ClaimApplication claimApplication);
    boolean save(ClaimApplication claimApplication);
}
```

- 删除 `updateForReapply`、`updateStatus`；并发判断只依赖根的 `version`。
- `findByClaimApplicationNo` / `findByTenantAndExternal...` / `findByIssueNo` 返回完整聚合：根 + 未删除材料 + 全部问题件。
- `findByIssueNo` 先按 `issue_no` 定位所属申请编号，再按申请编号加载聚合；问题件存在但申请缺失时返回空。
- `findByStatusUpdatedBefore` 同样返回完整聚合，补偿链路只用其中的编号与尝试次数。

### 4.2 `save` 算法

1. `save` 必须在 Application Service 已开启的同一 `@Transactional` 事务中调用；聚合保存、交互轨迹、消费登记和 Outbox 写入同属该事务。
2. `id` 为空直接拒绝；`version` 为空表示未持久化，拒绝。
3. 条件更新根：
   `WHERE id = ? AND version = ?`，更新状态、尝试次数、四类人员 JSON，并 `version = version + 1`。
4. 影响行数为 0 返回 `false`，不写任何子表。
5. 材料：`id` 为空插入，否则按 `id` 更新状态、上传地址、`deleted`、`deleted_at`。
6. 问题件：`id` 为空插入，否则按 `id` 更新状态、审核人、关闭时间；原因与详情不可变。
7. 全部成功后调用 `bindVersion(version + 1)` 同步内存版本，返回 `true`；任一子表、交互轨迹、消费登记或 Outbox 写入失败时事务整体回滚。
8. 同一事务只允许对同一聚合实例调用一次 `save`；保存成功后的实例不再复用。

`insert` 写入根（`version=0`）与全部材料，问题件在首签时为空；插入后回填主键并调用 `bindVersion(0L)`，避免后续保存因版本为空被拒绝。

### 4.3 转换

- `ClaimApplicationDataConverter` 组装根 DO；读方向调用 `ClaimApplication.restore` 并接收材料、问题件列表。
- `MaterialDataConverter` 读写方向都显式调用 `Material.restore`，持久化时由根传入 `claimApplicationNo`。
- `ClaimApplicationIssueConverter` 同样由根传入 `claimApplicationNo`，`is_open` 由状态推导。
- `PersonInfoJsonConverter` 随申请持久化整体迁入 `infrastructure/claim/persistence`。

## 5. 核心链路

### 5.1 签收、重新申请与补材

```text
ClaimApplicationIntakeUseCase
  → MaterialBatchNoGenerator.nextBatchNo()
  → ClaimApplication.submit(..., materials, batchNo)
  → ClaimApplicationIntakeTransactionService
      → 不存在：insert(聚合)              // 根 + 材料一次写入
      → 存在：按状态调用 reapply / supplement 后 save(聚合)
      → Outbox / 交互轨迹
```

- 删除 `ClaimApplicationIntakeMaterialFactory` 与 `MaterialRepository` 依赖。
- 批次编号在首次签收、重新申请、补材时都重新生成。

新增可选字段：`TenantAIntakeRequest`、`TenantBIntakeRequest`、`ClaimApplicationIntakeCommand` 各追加 `claimApplicationNo`（非必填、无格式约束，空串按未携带处理），由两个转换器透传。

存在申请的判定与分流：

| 条件 | 处理 | 结果 |
| --- | --- | --- |
| 未携带平台编号且快照判定可复用 | 只写交互轨迹 | `REUSED` |
| 未携带平台编号且数据库无记录 | 新建申请 | `CREATED` |
| 未携带平台编号、定位到任意既有申请 | 不修改人员、材料与状态 | `REUSED` |
| 携带平台编号 | 跳过快照，直接按“租户编码 + 外部编号”读库；编号必须一致 | 按状态走 `REAPPLIED` / `SUPPLEMENTED` / `REUSED` |
| 携带平台编号但读不到或编号不一致 | 抛 `CLAIM_APPLICATION_INTAKE_INVALID` | 400，不新建 |

- 携带平台编号的提交不经过 `ClaimApplicationIntakeSnapshotQuery.lookup`，避免复用陈旧的可复用结论；锁仍按外部申请编号持有。
- 事务服务新增 `intakeExisting(submitted, expectedClaimApplicationNo, requestId, traceId)`：读不到或编号不一致直接抛业务异常，不做创建重试。
- 仅携带且校验通过平台申请编号的提交才按状态分流：`isReapplicable()` → `reapply`；`isSupplementable()` → `supplement`；其余 → `REUSED`。未携带平台申请编号时，既有申请一律 `REUSED`。
- 补材只由携带平台编号的提交触发：未携带时无法区分“补材”与“重复签收”，`ISSUE` 状态按复用处理。
- 签收锁按外部申请编号串行化同类请求，`save` 返回 `false` 说明有不持锁链路（审核系统关闭问题件）或锁失效后的并发写入。事务服务按“租户编码 + 外部编号”重读聚合，并与调用根方法前的受理尝试次数比较：
  - 重读后尝试次数已增大：另一同类请求已成功，返回 `REUSED`，不覆盖已写入的人员与材料；
  - 补材场景下尝试次数未变且状态已离开 `ISSUE`：审核系统已关闭问题件，抛 `ApplicationException(ISSUE_APPLICATION_STATUS_INVALID)`，不写第二份材料；
  - 其余情况（尝试次数未变且仍满足前置条件）：抛 `ApplicationException(CLAIM_APPLICATION_INTAKE_BUSY)`，由租户重试，不静默复用。
- 上述重读必须在保存失败的本地事务之外进行：事务服务在 `save` 返回 `false` 时抛内部冲突信号并回滚本次事务，避免留下半截交互轨迹；用例捕获后调用独立的冲突解析入口（新事务）重读并分类，只有 `REUSED` 才写“开始 + 复用”轨迹。MySQL 默认 `REPEATABLE_READ` 下，原事务内重读仍会返回旧快照。
- 补材只替换材料：报文仍按租户协议携带人员字段，但落库人员保持聚合原值，人员字段在补材路径下不生效。
- 补材成功后在同一事务写入 `CLAIM_APPLICATION_REAPPLIED` Outbox 事件，复用既有材料处理 Topic、消费组与消息结构；消费者按尝试次数处理新批次材料，随后自然进入承保分流与审核交付。
- 重新申请与补材保存成功后，同样调用 `ClaimApplicationIntakeSnapshotQuery.markMutable`，覆盖问题件下发时失效失败可能残留的可复用快照；失效失败只记日志，不影响落库结果。

### 5.2 材料处理

```text
MaterialProcessingUseCase
  → findByClaimApplicationNo（聚合含材料）
  → 事务外逐份上传，生成 List<MaterialProcessingResult>
  → MaterialProcessingTransactionService
      → 消费登记
      → 重新加载聚合，校验 PENDING_ACCEPT 与尝试次数
      → root.applyMaterialResults(results)
      → save
```

- 事务服务改为接收申请编号、尝试次数与结果列表，不再接收事务外加载的聚合。
- `save` 返回 `false`、状态不匹配或尝试次数不匹配统一记为 `REUSED`。
- 成功返回 `ACCEPTED` 后仍由用例触发承保分流。

### 5.3 承保分流与回调

- `UnderwritingTransactionService.promoteToWaitAudit` / `startUnderwriting`：根迁移后 `save`，失败返回 `false`；`promoteToWaitAudit` 只在保存成功后写审核交付 Outbox。
- `UnderwritingCallbackTransactionService.complete`：根 `finishUnderwriting` 后 `save`，失败按 `REUSED` 处理，不写 Outbox。
- 补偿链路继续按 `PENDING_ACCEPT`、`ACCEPTED`、`UNDERWRITING` 扫描，行为不变。

### 5.4 问题件

```text
ClaimApplicationIssueIntakeUseCase
  → findByIssueNo 幂等预检
  → findByClaimApplicationNo
  → root.raiseIssue(...)
  → save；false 时重读问题件，按幂等或并发冲突处理

ClaimApplicationIssueCloseUseCase
  → findByIssueNo
  → root.closeIssue(issueNo, auditor)
  → save；false 时重读，已关闭返回 closed=false
```

- 删除 `IssueDomainService`、`ClaimApplicationIssueRepository` 与进程内问题件事件。
- 同一申请最多一个未关闭问题件由两层保证：根内实体检查 + 数据库 `uk_open_issue_claim_application`。
- 问题件下发仍保留 `DuplicateKeyException` 幂等兜底。
- 下发保存成功后，用聚合上的租户编码与外部申请编号调用 `ClaimApplicationIntakeSnapshotQuery.markMutable`，把可复用结论降级为“存在但状态可变”；失效失败只记日志，不影响下发结果。
- `ClaimApplicationIntakeSnapshot` 的注释同步修正：`WAIT_AUDIT` 可因问题件回退，可复用结论依靠下发时主动失效维持，不再宣称状态单调。
- 补材关闭的问题件与审核系统关闭共用同一实体状态：审核系统随后调用关闭接口时，根按已关闭幂等返回 `closed=false`，不回退状态。
- 下发或关闭的 `save` 返回 `false` 时，重读由用例在保存事务之外发起，不能在原事务内重读；`REPEATABLE_READ` 下同事务快照看不到并发赢家。

### 5.5 审核签收

`AuditSignedTransactionService` 保持只追加 `ClaimCaseRelation` 与交互轨迹；不加载聚合、不修改状态。仅调整 import。

## 6. 读模型与接口

### 6.1 端口与模型

```java
public interface ClaimApplicationDetailQuery {

    Optional<ClaimApplicationWithMaterials> findWithMaterials(String tenantCode, String claimApplicationNo);
    Optional<ClaimApplicationWithIssues> findWithIssues(String tenantCode, String claimApplicationNo);
    Optional<ClaimApplicationWithCases> findWithCases(String tenantCode, String claimApplicationNo);
    Optional<IssueView> findOpenIssue(String tenantCode, String claimApplicationNo);
}
```

视图字段：

- `ClaimApplicationSummary`：租户、平台编号、外部编号、状态、尝试次数、创建时间、更新时间。
- `MaterialView`：批次号、类型、原始地址、上传地址、状态、创建时间。
- `IssueView`：问题件号、原因、审核人、详情 JSON、状态、关闭时间、创建时间。
- `CaseView`：案件号、创建时间。

`ClaimApplicationDetailQueryUseCase` 以当前租户与申请编号入参，查无申请或租户不匹配都抛 `ApplicationException(CLAIM_APPLICATION_QUERY_NOT_FOUND)`；`ClaimApplicationIssueQueryUseCase` 改为先按租户与案件号解析申请编号，再调用 `findOpenIssue`，对外响应不变。

### 6.2 基础设施实现

`MyBatisClaimApplicationDetailQuery` 直接使用四个 DO Mapper：

- 根按 `claim_application_no` 与 `tenant_code` 双条件 `selectOne` 组装摘要；不存在或租户不匹配返回空。
- 材料按 `claim_application_no` + `deleted=0`，主键升序。
- 问题件按 `claim_application_no`，创建时间倒序、主键倒序。
- 案件关联按 `claim_application_no`，创建时间升序、主键升序。
- 未关闭问题件按 `is_open=1` 查询。

读模型实现不加载领域聚合、不写任何表。

### 6.3 HTTP 接口

`ClaimApplicationQueryController`（`/api/claim/v1/application`）：

| 方法 | 路径 | 返回 |
| --- | --- | --- |
| GET | `/{claimApplicationNo}/materials` | 申请摘要 + 未删除材料 |
| GET | `/{claimApplicationNo}/issues` | 申请摘要 + 全部问题件 |
| GET | `/{claimApplicationNo}/cases` | 申请摘要 + 全部案件关联 |

- 统一使用 `ApiResponse.success(traceId, data)`。
- 控制器从 `TenantContext` 取当前租户；缺少租户上下文抛 `ApplicationException(CLAIM_APPLICATION_QUERY_INVALID)`（400）。
- 控制器实现 `ApiErrorCodeProvider`：请求非法码 `CLAIM_APPLICATION_QUERY_INVALID`，系统错误码 `CLAIM_APPLICATION_QUERY_SYSTEM_ERROR`。
- 新增错误码 `ClaimApplicationQueryErrorCode`：`CLAIM_APPLICATION_QUERY_NOT_FOUND` → 404、`CLAIM_APPLICATION_QUERY_INVALID` → 400、`CLAIM_APPLICATION_QUERY_SYSTEM_ERROR` → 500；不复用异步链路的 `CLAIM_APPLICATION_NOT_FOUND`，避免对外错误码字符串重复。

## 7. 删除与迁移清单

| 动作 | 对象 |
| --- | --- |
| 删除 | `domain/material/**`、`domain/issue/**`（类迁入 `domain/claim/{material,issue}`） |
| 删除 | `IssueDomainService`、`IssueDomainServiceConfiguration`、`IssueRaisedEvent`、`IssueClosedEvent`、`IssueDomainEventPublisher`、`SpringIssueDomainEventPublisher` |
| 删除 | `MaterialRepository`、`MyBatisMaterialRepository`、`ClaimApplicationIssueRepository`、`ClaimApplicationIssueRepositoryImpl` |
| 删除 | `ClaimApplicationIntakeMaterialFactory` |
| 迁移 | `infrastructure/{intake,material,issue}/persistence/**` → `infrastructure/claim/persistence/**` |
| 迁移 | `PersonInfoJsonConverter` → `infrastructure/claim/persistence` |
| 重写 | `MyBatisClaimApplicationRepository`：实现 `insert` / `save` / 聚合加载 |
| 重写 | 三个问题件用例与问题件查询用例，改依赖聚合仓储与读模型 |
| 修改 | 租户 request、协议转换器、签收命令、签收用例与事务服务：追加可选平台编号，补齐补材分流与 `SUPPLEMENTED` |
| 新增 | `usecase/query/**`、`infrastructure/query/persistence/**`、`interfaces/claim/web/**` |

## 8. 规则、配置与数据库

- ArchUnit：`USE_CASE_CONTRACT_PACKAGES` 增加 `..usecase.query.port..`、`..usecase.query.model..`；其余规则不变。
- `BusinessErrorStatusRegistry` 登记三个查询错误码，映射完整性测试继续覆盖。
- 不新增 Spring 配置、Redis Key、MQ 绑定或线程池。
- `deploy/mysql/init/02-application-schema.sql`：`t_claim_application` 增加
  `version BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '乐观锁版本号'`。
- 新增 `deploy/mysql/migration/024-add-claim-application-version.sql`：
  `ALTER TABLE t_claim_application ADD COLUMN version ... AFTER claim_application_attempt;`
- 迁移脚本为一次性手工执行：既有环境部署前执行，执行前检查 `information_schema.COLUMNS`（MySQL 8 不支持 `ADD COLUMN IF NOT EXISTS`）；全新环境由 init 脚本直接建列。
- 子表结构不变；`t_claim_application_issue` 的生成列唯一索引继续作为未关闭问题件兜底。

## 9. 影响与文档

- 对外契约：既有 HTTP 路径、请求/响应字段、MQ Topic/消费组/消息 JSON 不变；租户申请报文只新增可选字段 `claimApplicationNo`，签收响应包裹结构不变，另新增三个查询接口。
- 运行行为：状态机新增 `ISSUE → PENDING_ACCEPT`（租户补材）；`ISSUE → WAIT_AUDIT` 仅由审核系统关闭触发；幂等、错误码与重试语义不变；并发控制从“期望状态条件更新”改为“版本条件更新”。
- 测试调整：
  - `ClaimApplicationIssueTransitionTest` 改用 `raiseIssue` / `closeIssue` / `supplement` / `applyMaterialResults`。
  - `ClaimApplicationIssueTest` 迁移到 `domain/claim/issue` 包并适配 `create` 签名。
  - `IssueDomainServiceTest` 随被测类删除；其业务场景并入根状态流转测试。
  - 问题件三个用例测试与 `ClaimApplicationIntakeUseCaseTest` 按新协作者与 `save` 调整；补材分流补 `SUPPLEMENTED`、补材后人员保持原值、平台编号不一致返回 400、快照失效四类场景。
  - 签收并发补三类用例：同类请求 `save=false` 且尝试次数增大返回 `REUSED`；补材与审核关闭并发返回 `ISSUE_APPLICATION_STATUS_INVALID`；其余版本冲突返回 `CLAIM_APPLICATION_INTAKE_BUSY`。
  - `BusinessErrorStatusRegistryTest` 的期望映射补齐三个查询错误码；查询接口补跨租户 404、空集合与不写交互轨迹用例。
- 文档同步：`docs/architecture/{claim-application-architecture,ddd-standard-architecture,code-navigation}.md`、`docs/testing/audit-signed-and-issue-query.md`、`docs/integration/claim-application-integration-contract.md`。
