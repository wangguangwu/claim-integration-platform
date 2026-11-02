# 023 架构边界收口与冻结 · 任务清单

## 任务批次

### Batch 1：契约与协议边界

#### T1：`shared.contract` 瘦身
- 产物：迁移 `UnderwritingCompletedCommand`
- 路径：`shared/contract/` → `usecase/underwriting/command/`
- 完成条件：
  - 新包为 `com.wangguangwu.claim.application.usecase.underwriting.command`
  - `UnderwritingCallbackController`、`UnderwritingCallbackUseCase`、`UnderwritingCallbackTransactionService`、`ClaimApplicationCompensationUseCase` 的 import 更新
  - 全仓无 `shared.contract.UnderwritingCompletedCommand` 引用
  - `shared/contract` 只剩四个跨模块契约
  - 编译通过

#### T2：租户协议五阶段
- 产物：新增验签/解密端口与默认实现，改造流水线
- 路径：`interfaces/intake/tenantprotocol/core/{stage,pipeline}`、`tenants/tenant_a`、`tenants/tenant_b`
- 完成条件：
  - 新增 `TenantProtocolSignatureVerifier`、`TenantProtocolDecryptor`
  - 新增 `DefaultTenantProtocolSignatureVerifier`、`DefaultTenantProtocolDecryptor`
  - `AbstractTenantProtocolPipeline` 按五阶段执行，删除 `verifySignature`、`decrypt` 模板方法
  - tenant-a、tenant-b 构造器装配默认实现
  - 默认实现不做任何校验、不改写报文
  - 编译通过，签收链路行为不变

#### T3：Policy 归属核实
- 产物：`ClaimApplicationIntakePolicy` JavaDoc 澄清
- 路径：`usecase/intake/port/ClaimApplicationIntakePolicy.java`
- 完成条件：
  - 注释明确“应用控制策略，不是领域业务规则”
  - 类位置、方法签名、实现类不变
  - 不新增 Policy

### Batch 2：异常与领域命名

#### T4：异常边界分层
- 产物：新增 `DomainException`、`ApplicationException`，迁移抛出点
- 路径：`domain/exception/`、`usecase/exception/`、`domain/issue/service/`、`usecase/{audit,issue,underwriting}/`
- 完成条件：
  - `DomainException`、`ApplicationException` 继承 `BusinessException`，构造器签名一致
  - `IssueDomainService` 的 5 处抛出改为 `DomainException`
  - 5 个用例类的 7 处抛出改为 `ApplicationException`
  - `interfaces` 与 `infrastructure` 的基类使用保持不变
  - `WebExceptionHandler` 无需修改，错误码与消息不变
  - 编译通过，相关单元测试通过

#### T5：`claimcase` 合并到 `claim`
- 产物：领域与持久化包迁移
- 路径：`domain/claimcase/` → `domain/claim/`、`domain/claim/port/`；`infrastructure/claimcase/persistence/` → `infrastructure/claim/persistence/`
- 完成条件：
  - `ClaimCaseRelation`、`ClaimCaseRelationRepository` 迁入目标包
  - 四个持久化类包名改为 `infrastructure.claim.persistence`
  - `AuditSignedTransactionService`、`ClaimApplicationIssueQueryUseCase` 与两个测试 import 更新
  - 全仓无 `domain.claimcase`、`infrastructure.claimcase` 引用
  - 数据库表、字段、Mapper 泛型不变
  - 编译通过

### Batch 3：规则、文档与验证

#### T6：ArchUnit 与文档冻结
- 产物：新增架构规则与文档回填
- 路径：`ClaimApplicationArchitectureTest`、`docs/architecture/`、`specs/022-architecture-deviation-cleanup/`
- 完成条件：
  - 新增 `shared` 不得反向依赖四层的规则
  - 新增 domain/usecase 不得直接依赖 `BusinessException` 的规则，排除新异常类与 `*Test`
  - 文档更新五阶段流水线、异常层级、claimcase 归属与新路径
  - 统计按实测回填类数、文件数与目录数
  - 022 中受影响的路径引用同步

#### T7：构建与运行栈回归
- 产物：验证结论
- 完成条件：
  - `JAVA_HOME` 指向 JDK 21 执行 `mvn -B -pl backend/claim-application-service -am clean package` 成功
  - ArchUnit 全部规则通过，既有单元测试通过
  - 应用与网关健康检查 `UP`
  - tenant-a 首次签收、同号幂等、tenant-b 签收正常
  - 承保回调、审核签收、案件关联写入与查询正常
  - 问题件下发、查询、关闭正常
  - HTTP 路径、JSON 字段、Topic、消费组、事件类型、库表与 Redis Key 均未变化
