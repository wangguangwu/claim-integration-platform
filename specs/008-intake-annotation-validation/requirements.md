# 008 签收必填校验注解化需求

## 功能目标

- 把签收链路上"必填校验"从三处手写代码收敛为每类规则唯一归属：报文形状由租户 request 上的 Bean Validation 注解声明，业务不变式由领域类型在构造处自守，用例层不再重复校验。
- 用注解替代两份按租户手写的 `*RequestValidator` 与一个 `TenantProtocolFieldGuard`，新增租户只写 request record 上的注解，不新增校验类。
- 校验失败的错误码与 HTTP 状态保持不变，响应只变化 `message` 文本，且 `message` 必须携带精确字段路径，便于租户定位报文问题。

## 决策记录

- 2026-10-29 服务负责人裁定：签收链路的校验逻辑优先用注解、切面与设计模式表达，不再以手写 if 链承载报文形状校验。
- 本 Spec 只落地"注解化必填校验 + 领域自守"一项。traceId/MDC 透传切面不在此实现，由 [006 T16](../006-project-review-remediation/tasks.md) 统一承担，避免两处切面抢同一批入口。
- 校验能力依赖 `spring-boot-starter-validation` 恢复为直接依赖。006 T7 当初以"零引用"为由删除该依赖，依据是当时项目中不存在任何 `jakarta.validation` 使用；本次注解化后该前提失效，记录在 006 中同步修订。
- 本 Spec 承接 [006 T10](../006-project-review-remediation/tasks.md) 中"必填校验单一化、非法输入在领域构造即失败"的部分，T10 的其余口径（仓储去状态判定）已在 005 完成。

## 业务规则

1. 租户报文必填约束以 `jakarta.validation` 注解声明在租户 request 类型及其嵌套 record 上，不得在方法体内逐字段手写判空。
2. 校验失败统一抛 `ClaimApplicationIntakeException.invalid(...)`：错误码固定 `CLAIM_APPLICATION_INTAKE_INVALID`，HTTP 状态 400，与本次改造前一致。
3. 校验失败消息格式固定为 `<字段路径> <注解消息>`，字段路径沿用 Bean Validation 的属性路径（嵌套字段用 `.`、集合元素带下标，如 `attachments[0].type`），不携带原始报文内容与证件号。
4. 同一报文存在多条违规时，按字段路径字典序取第一条，保证同一报文多次提交返回同一消息。
5. 空白字符串与纯空格视为缺失；字段去空格仍由租户转换器负责，不在校验阶段改写报文。
6. 业务不变式在领域类型构造处唯一实现：`PersonInfo` 自守姓名、性别、证件类型、证件号；`MaterialSubmission` 自守材料类型与初始文件地址；`ClaimApplication.submit(...)` 自守租户编码、外部申请编号非空且至少一项材料。
7. 用例层不得重复领域已守的校验；`ClaimApplicationIntakeUseCase` 不承载任何字段级校验。
8. 每个租户的校验规则只允许存在一份实现。删除手写校验器后，不得保留"注解 + 手写"双份校验。
9. 校验实现不得吞异常：注解约束违规必须转成平台业务异常，不得把 `ConstraintViolationException` 泄漏为 500。

## 正常场景

1. 租户 A 合法报文经网关提交，返回 200，`data.claimApplicationNo` 与改造前一致，申请、材料、轨迹、事件落库条数不变。
2. 租户 B 合法报文经网关提交，返回 200，字段映射与改造前一致。
3. 同一租户外部申请编号重复提交，返回同一 `claimApplicationNo`，不产生重复行。
4. 合法报文但 `attachments` 为空数组：由"至少一项材料"的约束在租户层拒绝，返回 400 与既有错误码；聚合仍保留同一不变式，供非 HTTP 入口使用。

## 异常场景

| 场景 | 期望处理 |
|---|---|
| 租户 A 缺 `applicant.fullName` | 400，`CLAIM_APPLICATION_INTAKE_INVALID`，message 为 `applicant.fullName 不能为空` |
| 租户 A 缺 `applicant` 节点 | 400，message 为 `applicant 不能缺失` |
| 租户 A 缺 `attachments` 节点 | 400，message 为 `attachments 不能为空`（与空数组同属"至少一项材料"） |
| 租户 A `attachments[0].sourceUrl` 缺失 | 400，message 为 `attachments[0].sourceUrl 不能为空` |
| 租户 A `attachments` 含 `null` 元素 | 400，message 为 `attachments[0].<list element> 不能缺失` |
| 租户 B 缺 `claimant.credential` | 400，message 为 `claimant.credential 不能缺失` |
| 租户 B `evidences[0].documentCode` 为纯空格 | 400，message 为 `evidences[0].documentCode 不能为空` |
| 性别编码不在约定集合内 | 400，沿用转换器消息 `sex 仅支持 M 或 F` / `genderCode 仅支持 1 或 2` |
| 报文不是合法 JSON 或为空 | 400，沿用解析器与流水线既有消息，错误码不变 |
| 多条违规同时出现 | 400，按字段路径字典序返回第一条 |

## 边界条件

- 字段路径稳定性：嵌套对象、集合元素下标、空集合都不改变路径格式。
- 租户私有字段名不因校验改造而变更：`requestNo`、`applicant`、`attachments`、`claimReference`、`credential`、`evidences` 等保持原样。
- 校验只发生一次：同一报文不得在流水线与用例层各校验一遍。
- 改造不新增数据库列、表、Redis 键或 MQ 主题；历史数据读取不受影响。

## 本次范围

- 恢复 `spring-boot-starter-validation` 直接依赖。
- 两个租户 request record 及其嵌套 record 增加 Bean Validation 注解。
- 新增一个通用"注解约束违规 → 平台业务异常"校验适配器，替代两份手写租户校验器。
- 删除 `TenantARequestValidator`、`TenantBRequestValidator`、`TenantProtocolFieldGuard`。
- 删除 `ClaimApplicationIntakeUseCase` 中的 `validate`、`validatePerson`、`validateMaterials` 及其 import。
- 领域自守：`PersonInfo`、`MaterialSubmission`、`ClaimApplication.submit`。
- 同步 003 Spec、架构文档、006 T7/T10 记录。

## 非本次范围

- traceId/MDC/日志切面：由 006 T16 实现。
- 租户协议两套实现参数化去重（Parser/Pipeline 泛型化）：由 006 T11 承担。
- 两套异常与错误码合一、领域异常映射业务码：由 006 T8/T9 承担。
- 方法级校验（`@Validated` + `MethodValidationPostProcessor`）、统一异常处理 advice 改造：本 Spec 不需要，流水线内部已按业务异常出栈。
- 新增租户协议、外部接口契约变更、数据库与基础设施变更。

## 验收标准

1. `grep -rn "TenantProtocolFieldGuard\|TenantARequestValidator\|TenantBRequestValidator" backend/` 命中 0。
2. 两个租户 request 类型均声明了 `jakarta.validation` 约束注解；`tenants` 目录下不存在任何手写 `validate` 方法。
3. `ClaimApplicationIntakeUseCase` 内不存在 `validate`/`validatePerson`/`validateMaterials`，且不再 import `StringUtils`、`PersonInfo`、`ClaimApplicationIntakeException`、`java.util.List`。
4. `PersonInfo`、`MaterialSubmission`、`ClaimApplication.submit` 对非法输入在构造阶段抛出 `IllegalArgumentException`。
5. 经网关的六条黄金路径响应码、`code`、`data` 结构与改造前一致；缺字段报文返回 400 且 message 含精确字段路径。
6. `mvn -B clean package` 全反应堆通过；两服务重启日志无新增 ERROR；数据库无新增列与表。
