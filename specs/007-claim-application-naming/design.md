# 007 理赔申请命名全链路收敛设计

改名是一次横切变更，实现方式是"先建立映射表，再按表机械替换，最后跑运行栈比对"。本文的三张映射表是唯一执行依据，代码与文档不得出现表外的改法。

## 一、基线冲突表（需先裁定，已按 2026-10-29 裁定结论填写）

| # | 被推翻的既有约束 | 出处 | 冲突点 | 裁定 |
|---|---|---|---|---|
| 1 | 异步消息信封"字段名不允许修改" | `shared/contract/ApplicationEventEnvelope` 类注释 + specs 002 | 信封字段 `applicationNo` 改为 `claimApplicationNo` | 解除冻结，随本次改名；本服务是信封的唯一生产方与消费方，`eventVersion` 维持 1 |
| 2 | 对外 HTTP 路径、请求与响应结构不变 | specs 006 requirements 边界条件、design §十 影响节 | 响应业务编号键与材料/撤销路径段改名 | 由 007 承接，006 该条只约束 006 自身轮次 |
| 3 | 不改表结构既有列语义、历史行由新代码还原 | specs 006 requirements 边界条件 | 列改名 | 列只做改名（`RENAME COLUMN`），类型、可空性、默认值、生成列表达式与索引覆盖顺序不变，历史行仍可读 |
| 4 | 载荷字段名不变、历史在途消息可被新代码消费 | specs 006 design §十 影响节 | 载荷字符串键改名 | 改名窗口内清空在途消息后验证，不保留双键兼容分支 |
| 5 | 租户可见错误码字面量 | specs 003 错误码表、契约文档 | `APPLICATION_INTAKE_*` → `CLAIM_APPLICATION_INTAKE_*` | 随改名，契约文档标注 breaking |

## 二、类型映射表（25 + 1 个类）

| 现名 | 新名 | 所在包 |
|---|---|---|
| `ApplicationStatus` | `ClaimApplicationStatus` | domain/claim |
| `ApplicationRepository` | `ClaimApplicationRepository` | domain/claim/port |
| `ApplicationExistenceQuery` | `ClaimApplicationExistenceQuery` | domain/claim/port |
| `ApplicationIntakeLock` | `ClaimApplicationIntakeLock` | domain/claim/port |
| `ApplicationNoGenerator` | `ClaimApplicationNoGenerator` | domain/claim/port |
| `ApplicationIntakeException` | `ClaimApplicationIntakeException` | domain/claim/exception |
| `ApplicationIntakeErrorCode` | `ClaimApplicationIntakeErrorCode` | domain/claim/exception |
| `ApplicationEventType` | `ClaimApplicationEventType` | domain/integration/outbox |
| `ApplicationCompensationPolicy` | `ClaimApplicationCompensationPolicy` | domain/integration/compensation |
| `ApplicationEventEnvelope` | `ClaimApplicationEventEnvelope` | shared/contract |
| `ApplicationAsyncException` | `ClaimApplicationAsyncException` | shared/contract |
| `ApplicationAsyncErrorCode` | `ClaimApplicationAsyncErrorCode` | shared/contract |
| `ApplicationIntakeController` | `ClaimApplicationIntakeController` | interfaces/intake/web |
| `ApplicationIntakeExceptionHandler` | `ClaimApplicationIntakeExceptionHandler` | interfaces/intake/web |
| `ApplicationIntakeResponse` | `ClaimApplicationIntakeResponse` | interfaces/intake/web |
| `ApplicationResponseRenderer` | `ClaimApplicationResponseRenderer` | interfaces/intake/web/render |
| `ApplicationCompensationScheduler` | `ClaimApplicationCompensationScheduler` | interfaces/integration/scheduling |
| `ApplicationCompensationUseCase` | `ClaimApplicationCompensationUseCase` | usecase/integration/compensation |
| `ApplicationCompensationTransactionService` | `ClaimApplicationCompensationTransactionService` | usecase/integration/compensation |
| `ApplicationLifecycleRecordFactory` | `ClaimApplicationLifecycleRecordFactory` | usecase/integration/record |
| `ApplicationExistenceMismatchException` | `ClaimApplicationExistenceMismatchException` | usecase/intake（包内私有） |
| `ApplicationDO` | `ClaimApplicationDO` | infrastructure/intake/persistence |
| `ApplicationDOMapper` | `ClaimApplicationDOMapper` | infrastructure/intake/persistence |
| `ApplicationDataConverter` | `ClaimApplicationDataConverter` | infrastructure/intake/persistence |
| `ApplicationCompensationProperties` | `ClaimApplicationCompensationProperties` | infrastructure/integration/config |
| `ApplicationServiceApplication` | `ClaimApplicationServiceApplication` | 服务根包（启动类） |

不改的名字：`ApiResponse`（平台通用响应壳，不指代业务对象）、`ClaimApplication*` 既有类型、`Material*`/`Outbox*`/`InteractionLog*`/`Tenant*` 系列。

## 三、标识符与字段映射表

| 现名 | 新名 | 命中量 | 触及层 |
|---|---|---|---|
| `applicationNo` | `claimApplicationNo` | 283 处 / 122 文件 | 全部层 |
| `externalApplicationNo` | `externalClaimApplicationNo` | 47 处 | domain、DO、契约 |
| `applicationAttempt` | `claimApplicationAttempt` | 19 处 | domain、DO、事件载荷 |
| 局部变量与参数 `application` | `claimApplication` | 136 文件内出现 | 全部层 |
| getter `getApplicationNo()` 等 | `getClaimApplicationNo()` | 随 Lombok 与手写 getter 联动 | DO、领域对象 |
| 日志文本 `applicationNo=` | `claimApplicationNo=` | 与上同批 | 全部层 |
| 异常与错误信息文本中的"applicationNo" | "claimApplicationNo" | 约 10 处 | domain、usecase |
| 错误码常量 `APPLICATION_INTAKE_BUSY` / `_INVALID` / `_SYSTEM_ERROR` | `CLAIM_APPLICATION_INTAKE_BUSY` / `_INVALID` / `_SYSTEM_ERROR` | 枚举定义 +  advice 分支 | 对外码值 |
| MDC 键 `applicationNo`（006 §十） | `claimApplicationNo` | 1 处定义 | logback 与 filter |

## 四、外部契约与数据库映射表

### 4.1 数据库（`deploy/mysql/init/02-application-schema.sql` + 本地卷一次性迁移）

| 表 | 现列名 | 新列名 |
|---|---|---|
| `t_claim_application` | `application_no` | `claim_application_no` |
| `t_claim_application` | `external_application_no` | `external_claim_application_no` |
| `t_claim_application` | `application_attempt` | `claim_application_attempt` |
| `t_claim_application_material` | `application_no` | `claim_application_no` |
| `t_claim_application_issue` | `application_no` | `claim_application_no` |
| `t_claim_application_issue` | `open_application_no`（生成列） | `open_claim_application_no`（表达式同步） |
| `t_claim_case_relation` | `application_no` | `claim_application_no` |
| `t_claim_interaction_log` | `application_no` | `claim_application_no` |
| `t_claim_outbox_event` | `application_no` | `claim_application_no` |
| `t_claim_consumed_event` | `application_no` | `claim_application_no` |

索引同步改名：`uk_application_no` → `uk_claim_application_no`、`uk_tenant_external_application` → `uk_tenant_external_claim_application`、`idx_application_status_updated_at` → `idx_claim_application_status_updated_at`、`idx_material_application_batch`/`_deleted`、`uk_open_issue_application`、`idx_issue_application_created_at`、`uk_tenant_application_case`、`idx_case_relation_application`、`idx_interaction_application_occurred_at`、`idx_outbox_application_created_at`、`idx_consumed_application` 内的 `application` 段一律改为 `claim_application`，列顺序与覆盖列不变。

MySQL 8 列改名语法为 `ALTER TABLE ... RENAME COLUMN old TO new`，不重写数据；生成列 `open_application_no` 需 `DROP COLUMN` 后按新名重建，索引依赖它的先 drop 索引。本地卷与 init 脚本必须同时改，否则新卷与已有卷列名不一致。

### 4.1.1 入库的枚举值与 JSON 载荷键（改名即改数据，必须带 UPDATE 迁移）

实测本地卷已有数据：理赔申请 79 行、材料 94 行、交互轨迹 910 行、Outbox 153 行、消费登记 72 行。以下字符串是枚举 `name()` 直接入库的结果，改名后旧值会在新代码 `valueOf` 处抛异常，必须与列改名同批迁移：

| 位置 | 现值 | 新值 | 迁移方式 |
|---|---|---|---|
| `t_claim_outbox_event.event_type` | `APPLICATION_CREATED`、`APPLICATION_REAPPLIED` | `CLAIM_APPLICATION_CREATED`、`CLAIM_APPLICATION_REAPPLIED` | `UPDATE` 全表；`AUDIT_DELIVERY_REQUESTED` 不含该前缀，不动 |
| `t_claim_interaction_log.interaction_type` | `APPLICATION_INTAKE` | `CLAIM_APPLICATION_INTAKE` | `UPDATE` 命中行；`MATERIAL_PROCESS`、`UNDERWRITING`、`UNDERWRITING_CALLBACK` 不动 |
| `t_claim_outbox_event.payload`（JSON 文本） | `"applicationNo"`、`"applicationAttempt"` 键 | `"claimApplicationNo"`、`"claimApplicationAttempt"` | 仅对未投递成功的行做键名替换，已 `PUBLISHED` 的行作为历史留档不改写 |

在途消息处理：改名窗口内不得残留旧键名的待投递与待消费消息，验证前用一次性查询确认 `status <> 'PUBLISHED'` 的行数为 0 或已全部改写。

`RedisMySqlApplicationNoGenerator` 的编号前缀常量 `APPLICATION_NO_PREFIX`（值为 `"A"`）是内部标识符，随 N2 改名为 `CLAIM_APPLICATION_NO_PREFIX`，编号值本身不变。

### 4.2 对外字段与路径

| 位置 | 现名 | 新名 | 影响方 |
|---|---|---|---|
| 签收响应 `data` | `applicationNo` | `claimApplicationNo` | 全部租户 |
| 补充材料/撤销路径 | `/api/application/v1/applications/{applicationNo}/...` | `/api/application/v1/claim-applications/{claimApplicationNo}/...` | 全部租户（网关按前缀匹配，不需改路由） |
| 承保完成/审核签收/问题件回调入参 | `applicationNo` | `claimApplicationNo` | 承保、审核系统 |
| MQ 信封字段 | `applicationNo` | `claimApplicationNo` | 消费方（本服务自身） |
| 事件载荷与轨迹明细字符串键 | `"applicationNo"` | `"claimApplicationNo"` | 消费方、排障脚本 |
| Feign 出站路径 | `/mock/underwriting/v1/auto-underwritings/{applicationNo}` | `.../{claimApplicationNo}` | 假件进程（同仓同步） |
| 网关路由 | `Path=/api/application/**` | 不改 | 资源段改名落在既有前缀内，路由无需变动 |
| 压测与冒烟脚本 | `$.data.applicationNo` JSONPath | `$.data.claimApplicationNo` | `scripts/jmeter/*.jmx` |
| Redis 键、库名、MQ 主题与消费组、编号前缀 `A` | 不改 | 不改 | — |

`requestNo` 等租户私有入参键不改；租户侧对接方需要的是字段名变更通知，不属于本仓代码改动。

## 五、实现顺序与要点

1. 类型改名：`git mv` 文件 + 类名/构造/JavaDoc/`@Slf4j` 无关，逐个引用点替换；启动类改名后 `@SpringBootApplication` 扫描根不变。
2. 标识符改名：按映射表对 `.java` 做整词替换，`applicationNo`→`claimApplicationNo` 先于裸 `application` 变量替换，避免子串误伤；`Tenant*IntakeRequest` 内的租户键（`requestNo`）不在替换集合内。
3. Jackson 边界校验：记录类型组件名即 JSON 键名，改名即改键名；租户私有入参 DTO 若组件名恰好被替换，必须用 `@JsonProperty` 固定原键名——本次通过"不改租户键名"的替换白名单规避，不引入兼容注解。
4. 持久层：`@TableField` 显式列名与新列名对齐，MyBatis-Plus 逻辑删除与生成列表达式同步。
5. DDL 与本地卷：init 脚本改列名与索引名，另用一次性 `ALTER TABLE ... RENAME COLUMN` 迁移已有数据卷，迁移前后各取一次行数与关键行摘要做比对。
6. 文档同步：`docs/integration/claim-application-integration-contract.md`（含 breaking 标注）、`docs/testing/` 3 篇、`docs/architecture/` 相关页、specs 002/003/004/006 被触及条目、`scripts/` 压测与冒烟脚本。
7. 运行栈验证：全反应堆构建 → 重启应用与网关 → 黄金路径六场景逐字段比对 → 历史行还原抽查 → 轨迹孤儿组与 Outbox 计数校验。

## 六、风险与回退

- 主要风险是整词替换误伤：`application` 同时出现在包名 `com.wangguangwu.claim.application`、`spring.application.name` 与 `application.yml` 字符串中，替换必须以词边界 + 文件类型 + 白名单三重约束，且包名与配置文件名不改。
- 列改名与代码必须同批生效：代码先上、库未迁移会直接报 `Unknown column`；本地卷迁移脚本纳入同一次收口，执行顺序为"停应用 → 改列 → 起新 jar"。
- 回退方式：代码按提交回滚；数据库回滚用同一批 `RENAME COLUMN` 反向执行，列改名不产生数据丢失。
- 对 006 的影响：本 Spec 应先于 006 第 3 轮（T8–T13）执行，否则 T8/T11/T12 会在被改名的同一批类上产生二次 diff。
