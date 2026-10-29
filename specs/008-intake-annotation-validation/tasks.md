# 008 签收必填校验注解化任务

任务与 design 章节一一对应。V1–V4 是代码批次，V5 是文档批次，V6 是验收门禁。

## 第 1 批 · 注解与适配器

| 编号 | 任务 | 产物 | 完成条件 |
|---|---|---|---|
| V1 | 恢复校验依赖 | `claim-application-service/pom.xml` 中的 `spring-boot-starter-validation` | 依赖树含 `hibernate-validator`；模块编译通过 |
| V2 | 租户 request 注解化 | `TenantAIntakeRequest`、`TenantBIntakeRequest` 及其五个嵌套 record 的约束注解 | design §三 映射表逐条落地；字段名与嵌套结构未变；无手写判空新增 |
| V3 | 注解适配器与流水线装配 | 新增 `BeanValidationTenantProtocolValidator`；`AbstractTenantProtocolPipeline` 默认装配该校验器并新增 `validateTenantRules` 扩展点；`TenantAProtocolPipeline`、`TenantBProtocolPipeline` 注入 `Validator` | 两租户流水线不引用校验实现类，默认校验由模板装配；扩展点默认空实现且只在注解校验之后执行；`TenantProtocolValidator` 注释同步；无 `@Autowired` 字段注入 |

## 第 2 批 · 删除重复实现与领域自守

| 编号 | 任务 | 产物 | 完成条件 |
|---|---|---|---|
| V4 | 删除手写校验器 | 删除 `TenantARequestValidator`、`TenantBRequestValidator`、`TenantProtocolFieldGuard` | 三个类名在 `backend/` 命中 0；`tenants` 下无 `validate` 方法 |
| V5 | 用例层去校验 | `ClaimApplicationIntakeUseCase` 删除 `validate`、`validatePerson`、`validateMaterials` 与四个失效 import | 类内无字段级校验；`StringUtils`、`PersonInfo`、`ClaimApplicationIntakeException`、`java.util.List` 不再被 import |
| V6 | 领域自守 | `PersonInfo` 构造器、`MaterialSubmission` 紧凑构造器、`ClaimApplication.submit` | 非法输入在构造阶段抛 `IllegalArgumentException`；四类人员与两租户转换器编译通过 |

## 第 3 批 · 文档与记录

| 编号 | 任务 | 产物 | 完成条件 |
|---|---|---|---|
| V7 | 003 Spec 同步 | `specs/003-application-intake/{design,tasks}.md` 的类职责、五段链路第 4 步、租户组件清单 | `TenantProtocolFieldGuard` 与每租户 Validator 的现状描述改为注解化，并指向 008 |
| V8 | 006 记录修订 | `specs/006-project-review-remediation/tasks.md` T7、T10 | T7 加注校验依赖由 008 恢复及原因；T10 标注必填校验部分由 008 交付 |
| V9 | 架构文档同步 | `docs/architecture/ddd-standard-architecture.md` 报文防腐组件行 | 示例改为注解化表述，不再列已删除的 `*FieldGuard` |

## 第 4 批 · 验收门禁

| 编号 | 任务 | 产物 | 完成条件 |
|---|---|---|---|
| V10 | 构建 | 应用与网关新 jar | `mvn -B clean package` 全反应堆通过 |
| V11 | 运行栈黄金路径 | 六场景验证记录 | 两服务重启日志无新增 ERROR；六条黄金路径 HTTP 状态、`code`、`data` 结构与改造前一致 |
| V12 | 负向报文验证 | 五条缺字段/空材料用例记录 | 均返回 400 与 `CLAIM_APPLICATION_INTAKE_INVALID`，消息含精确字段路径（含数组下标） |
| V13 | 验收 grep 与数据抽查 | grep 计数与库表抽查 | design §九 第 5、6 条全部满足；无新增列与表 |

## 统一收口

1. 全反应堆 `mvn -B clean package` 通过，无编译告警新增。
2. 停应用 → 起新 jar 与网关 → 六场景 + 五条负向用例经网关执行。
3. 按 requirements 验收标准逐条 grep，不得出现例外白名单。
4. 代码格式化后提交，提交信息中文，说明"必填校验注解化 + 领域自守，错误码与状态不变"；未获授权不推送。

## 与其他 Spec 的关系

- 承接 006 T10 的必填校验部分；T10 明细行由本 Spec 完成后的状态描述。
- 修订 006 T7 的一条结论：`spring-boot-starter-validation` 由"零引用删除"改为"作为直接依赖恢复"。
- traceId/MDC 切面仍由 006 T16 承担，本 Spec 不建切面。
