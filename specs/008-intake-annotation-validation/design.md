# 008 签收必填校验注解化设计

## 一、规则归属

必填校验此前在一条链路上被写了三遍：租户校验器按租户手写一遍、用例层按标准命令再写一遍、领域层不写。改造后每类规则只有一个归属地：

| 规则 | 改造前所在位置 | 改造后归属 | 依据 |
|---|---|---|---|
| 报文节点与字段是否出现、是否空白 | `TenantARequestValidator`、`TenantBRequestValidator`、`TenantProtocolFieldGuard`（约 140 行） | 租户 request record 上的 `jakarta.validation` 注解 | 报文形状是租户协议层的知识，注解即文档，新增租户不写校验类 |
| 人员四字段合法、材料两字段合法 | `ClaimApplicationIntakeUseCase.validatePerson/validateMaterials` | `PersonInfo` 构造器与 `MaterialSubmission` 紧凑构造器 | 字段不变式由持有字段的值对象自守 |
| 租户编码、外部申请编号非空，至少一项材料 | `ClaimApplicationIntakeUseCase.validate` | `ClaimApplication.submit(...)` 领域工厂（"至少一项材料"另在租户层以 `@NotEmpty` 提前拒绝） | 聚合创建的前置条件属于聚合 |
| 标准命令整体非空 | `ClaimApplicationIntakeUseCase.validate` 首行 | 不设校验 | 命令由流水线工厂产出，返回 null 即违反工厂契约；不为不可能发生的分支写防御代码 |
| 违规如何变成响应 | 三处各写 `ClaimApplicationIntakeException.invalid` | 单一适配器 `BeanValidationTenantProtocolValidator` | 错误码与消息格式只在一处组装 |

用例层由此不再持有任何字段级校验，`ClaimApplicationIntakeUseCase` 只保留锁、事务外准备与唯一冲突重试。

## 二、改动清单

| 模块 | 文件 | 动作 |
|---|---|---|
| `claim-application-service` | `pom.xml` | 新增 `spring-boot-starter-validation` 直接依赖 |
| `interfaces/intake/tenantprotocol/core/stage` | 新增 `BeanValidationTenantProtocolValidator` | 注解约束违规到业务异常的适配器 |
| 同上 | `TenantProtocolValidator` | 接口职责注释改为"Bean Validation 约束校验" |
| 同上 | 删除 `TenantProtocolFieldGuard` | 手写字段守卫由注解替代 |
| `interfaces/intake/tenantprotocol/core/pipeline` | `AbstractTenantProtocolPipeline` | 校验节点默认装配注解校验器，新增 `validateTenantRules` 租户扩展点 |
| `tenants/tenant_a` | `TenantAIntakeRequest` | 主类型与两个嵌套 record 增加约束注解 |
| 同上 | 删除 `TenantARequestValidator` | 由注解与适配器替代 |
| 同上 | `TenantAProtocolPipeline` | 构造器注入 `jakarta.validation.Validator` 并装配适配器 |
| `tenants/tenant_b` | `TenantBIntakeRequest` | 主类型与三个嵌套 record 增加约束注解 |
| 同上 | 删除 `TenantBRequestValidator` | 同上 |
| 同上 | `TenantBProtocolPipeline` | 同上 |
| `usecase/intake` | `ClaimApplicationIntakeUseCase` | 删除 `validate`、`validatePerson`、`validateMaterials` 与四个失效 import，类注释改为"并发锁、事务外准备数据与唯一冲突恢复" |
| `domain/claim` | `PersonInfo` | 手写构造器并对四字段自守 |
| 同上 | `MaterialSubmission` | 紧凑构造器对两字段自守 |
| 同上 | `ClaimApplication` | `submit(...)` 对租户编码、外部申请编号、材料非空自守 |
| `specs`、`docs` | 003、006、架构文档 | 同步类职责与记录 |

数据库、Redis、MQ、对外接口契约均无变更。

## 三、注解约束映射

租户私有字段名一律不动，只在原有 record 上声明约束。消息使用中文短文，字段路径由 Bean Validation 生成。

### 租户 A

| 类型 | 字段 | 注解 | 说明 |
|---|---|---|---|
| `TenantAIntakeRequest` | `requestNo` | `@NotBlank(message = "不能为空")` | 外部申请编号 |
| 同上 | `applicant`、`insured`、`accident`、`payee` | `@NotNull(message = "不能缺失") @Valid` | 四个人员节点，缺节点与嵌套字段分别报路径 |
| 同上 | `attachments` | `@NotEmpty(message = "不能为空") @Valid` | 缺节点与空数组同报 `attachments 不能为空`；元素 `@NotNull` 拒绝 `[null]` |
| `TenantAPerson` | `fullName`、`sex`、`idType`、`idNo` | `@NotBlank` | 完整路径如 `applicant.fullName` |
| `TenantAAttachment` | `type`、`sourceUrl` | `@NotBlank` | 完整路径如 `attachments[0].type` |

### 租户 B

| 类型 | 字段 | 注解 | 说明 |
|---|---|---|---|
| `TenantBIntakeRequest` | `claimReference` | `@NotBlank` | 外部申请编号 |
| 同上 | `claimant`、`mainInsured`、`injured`、`beneficiary` | `@NotNull @Valid` | 四个人员节点 |
| 同上 | `evidences` | `@NotEmpty @Valid` | 材料数组，元素 `@NotNull` |
| `TenantBPerson` | `name`、`genderCode` | `@NotBlank` | 性别编码取值由转换器判定 |
| 同上 | `credential` | `@NotNull @Valid` | 证件为嵌套对象 |
| `TenantBCredential` | `type`、`number` | `@NotBlank` | 完整路径如 `claimant.credential.number` |
| `TenantBEvidence` | `documentCode`、`fileDownloadUrl` | `@NotBlank` | 完整路径如 `evidences[0].documentCode` |

### 与旧校验的等价性

| 旧校验点 | 新承载 | 结论 |
|---|---|---|
| `requireText(field)` | `@NotBlank` | 等价；空白串此前由用例层 `hasText` 拒绝，改造后同层拒绝，错误码与 400 不变 |
| `requirePresent(obj)` | `@NotNull` | 等价 |
| `requirePresent(list)` 仅判非空引用 | `@NotEmpty` | 收紧：缺节点与空数组都在租户层拒绝，错误码与 400 不变；聚合的"至少一项材料"不变式保留给非 HTTP 入口 |
| 循环内 `requirePresent(element)` | 集合元素上的 `@NotNull` | 等价，路径形如 `attachments[0].<list element>` |

转换器仍负责去空格与枚举编码映射，注解只判"是否缺失/空白"，不改写报文。

## 四、校验适配器

新增 `BeanValidationTenantProtocolValidator<R> implements TenantProtocolValidator<R>`，只做一件事：把约束违规翻译成平台业务异常。

```java
@Override
public void validate(R request) {
    validator.validate(request).stream()
            .min(Comparator.comparing(violation -> violation.getPropertyPath().toString()))
            .ifPresent(violation -> {
                throw ClaimApplicationIntakeException.invalid(
                        violation.getPropertyPath() + " " + violation.getMessage());
            });
}
```

要点：

- 错误码固定 `CLAIM_APPLICATION_INTAKE_INVALID`（HTTP 400），走既有 `ClaimApplicationIntakeException.invalid` 工厂，响应渲染器不改。
- 按字段路径字典序取第一条，等价于"先报最外层、再报嵌套"的稳定顺序，同一报文重复提交消息不变。
- 消息只含字段路径与注解文案，不含字段值，天然满足"日志与响应不得输出证件号"。
- 保留泛型参数 `<R>`：适配器实例与解析器、转换器共用同一 request 类型，装配错位无法通过编译。
- 不引入 `@Validated` 与 `MethodValidationPostProcessor`：校验发生在流水线内部，由模板方法显式调用，方法级切面会绕开业务异常体系。

## 五、流水线装配与租户扩展

`AbstractTenantProtocolPipeline` 的五段链路不变，校验节点拆成"默认段 + 扩展段"：默认段焊在模板里，租户不接触校验实现；扩展段留给注解表达不了的规则。

```java
protected AbstractTenantProtocolPipeline(String tenantCode, String protocolVersion, TenantProtocolParser<R> parser,
                                         TenantProtocolConverter<R> converter, Validator validator) {
    ...
    this.constraintValidator = new BeanValidationTenantProtocolValidator<>(validator);
}

public final ClaimApplicationIntakeCommand execute(String rawPayload) {
    ...
    constraintValidator.validate(request);   // 默认：注解约束，不可关闭
    validateTenantRules(request);            // 扩展：租户追加规则，默认空实现
    return converter.convert(tenantCode, request);
}
```

租户流水线只传 `jakarta.validation.Validator`，不引用任何校验实现：

```java
public TenantAProtocolPipeline(ObjectMapper objectMapper, Validator validator) {
    super(TENANT_CODE, PROTOCOL_VERSION, new TenantARequestParser(objectMapper),
            new TenantARequestConverter(), validator);
}
```

后续出现"材料类型与险种必须匹配""出险人不得与投保人相同"这类跨字段规则时，租户覆写 `validateTenantRules(R request)`，或在该方法里委托一个实现 `TenantProtocolValidator` 的规则类。扩展段在注解之后执行，入参已非空且结构完整。

不提供"替换默认校验器"的装配方式：注解声明在租户自己的 request 上，就是该租户的协议契约；允许替换等于允许契约静默失效。协议差异（某租户某字段可选）由该租户的 request 类型本身表达。

`jakarta.validation.Validator` 由 Spring Boot 的 `ValidationAutoConfiguration` 提供（`LocalValidatorFactoryBean`），流水线通过构造器注入，不使用字段注入与 `@Autowired`。

## 六、领域自守

```java
// PersonInfo
protected PersonInfo(String name, Gender gender, String certificateType, String certificateNo) {
    requireText(name, "姓名");
    if (gender == null) {
        throw new IllegalArgumentException("性别不能为空");
    }
    requireText(certificateType, "证件类型");
    requireText(certificateNo, "证件号");
    ...
}

// MaterialSubmission
public MaterialSubmission {
    requireText(materialType, "materialType");
    requireText(originalFileUrl, "originalFileUrl");
}

// ClaimApplication.submit
requireText(tenantCode, "tenantCode");
requireText(externalClaimApplicationNo, "externalClaimApplicationNo");
if (materials == null || materials.isEmpty()) {
    throw new IllegalArgumentException("首次签收至少需要一项材料");
}
```

异常类型选 `IllegalArgumentException` 而非 `ClaimApplicationIntakeException`：值对象同时被承保、材料链路复用，不应绑定"接入"语义；领域异常到业务码的映射由 006 T9 统一处理。HTTP 入路上这些分支不可达——报文形状已在租户层拦截，转换器只会用去空格后的非空值构造。

## 七、依赖与既有记录修订

`spring-boot-starter-validation` 恢复为 `claim-application-service` 的直接依赖，版本由 Spring Boot 父 POM 管理。它提供 `jakarta.validation` API 的 Hibernate Validator 实现与 EL 表达式引擎；缺失时注解不生效、`Validator` bean 不存在，`BeanValidationTenantProtocolValidator` 无法装配。

[006 T7](../006-project-review-remediation/tasks.md) 当初以"零引用"删除该依赖，前提在本 Spec 失效，故在 006 对应行加注说明由 008 恢复；[006 T10](../006-project-review-remediation/tasks.md) 的必填校验部分由本 Spec 交付。

## 八、对现有功能的影响

- 不变：错误码 `CLAIM_APPLICATION_INTAKE_INVALID`、HTTP 400、幂等键、锁、编号、事务边界、轨迹与 Outbox 写入、异步链路。
- 变化：缺字段与空材料等 400 响应的 `message` 文本，从"租户申请报文缺少字段：`applicant.fullName`"/"首次签收至少需要一项材料"改为"`applicant.fullName 不能为空`"/"`attachments 不能为空`"。`code`、HTTP 状态、响应结构不变。
- 不可达：`ClaimApplicationIntakeUseCase` 不再有校验分支，因此不存在"用例层校验失败"这条路径。

## 九、验证方案

1. `mvn -B -pl backend/claim-application-service -am clean package` 通过；随后全反应堆 `mvn -B clean package`。
2. 重启应用与网关，日志无新增 ERROR。
3. 经网关跑六条黄金路径：租户 A 首次提交、租户 A 同号重复、租户 B 首次提交、租户 B 同号重复、畸形 JSON、未支持租户；比对 HTTP 状态、`code`、`data` 结构与改造前一致。
4. 负向用例：A 缺 `applicant.fullName`、A 缺 `attachments`、A 空 `attachments`、B 缺 `claimant.credential`、B `evidences[0].documentCode` 为空格，逐条断言 400、错误码与消息含精确路径。
5. 数据库抽查：轨迹 STARTED 与终态行数相等，孤儿组 0；无新增列与表。
6. 验收 grep：手写校验器与 `TenantProtocolFieldGuard` 命中 0；用例层无 `validate` 与相关 import。

## 十、不做的事

- 不新增切面：traceId/MDC 由 006 T16 统一实现，本 Spec 不建 `@Aspect`。
- 不做租户协议参数化去重（006 T11）、异常与错误码合一（006 T8/T9）、黄金路径脚本化（006 T17）。
- 不为注解校验补单元测试：项目当前以运行栈黄金路径为验证口径，测试网由 006 T17 统一重建。
