# 012 问题件链路 · 任务清单

## 任务批次

### Batch 1：领域模型基础

#### T1：问题件状态枚举
- 产物：`ClaimApplicationIssueStatus.java`
- 路径：`backend/claim-application-service/src/main/java/com/wangguangwu/claim/application/domain/issue/ClaimApplicationIssueStatus.java`
- 完成条件：
  - 枚举值：OPEN、CLOSED
  - 中文 JavaDoc
  - 编译通过

#### T2：问题件聚合根
- 产物：`ClaimApplicationIssue.java`
- 路径：`backend/claim-application-service/src/main/java/com/wangguangwu/claim/application/domain/issue/ClaimApplicationIssue.java`
- 完成条件：
  - 属性：issueNo、claimApplicationNo、reason、auditor、issueContent、status、closedAt
  - 方法：create(...) 静态工厂、close(auditor)、isOpen()、isClosed()
  - create() 校验参数非空，状态初始化为 OPEN
  - close() 校验状态为 OPEN，设置 CLOSED 和 closedAt
  - 中文 JavaDoc
  - 编译通过

#### T3：问题件仓储端口
- 产物：`ClaimApplicationIssueRepository.java`
- 路径：`backend/claim-application-service/src/main/java/com/wangguangwu/claim/application/domain/issue/ClaimApplicationIssueRepository.java`
- 完成条件：
  - 方法：findByIssueNo(issueNo)、hasOpenIssue(claimApplicationNo)、save(issue)
  - findByIssueNo 返回 Optional<ClaimApplicationIssue>
  - hasOpenIssue 返回 boolean
  - 中文 JavaDoc
  - 编译通过

#### T4：申请状态枚举补充 ISSUE
- 产物：修改 `ClaimApplicationStatus.java`
- 路径：`backend/claim-application-service/src/main/java/com/wangguangwu/claim/application/domain/claim/ClaimApplicationStatus.java`
- 完成条件：
  - 新增 ISSUE 枚举值
  - 更新 JavaDoc，说明 ISSUE 状态的含义
  - 编译通过

#### T5：申请聚合根补充问题件状态流转
- 产物：修改 `ClaimApplication.java`
- 路径：`backend/claim-application-service/src/main/java/com/wangguangwu/claim/application/domain/claim/ClaimApplication.java`
- 完成条件：
  - 新增 transitionToIssue()：校验当前状态为 WAIT_AUDIT，变更为 ISSUE
  - 新增 transitionToWaitAudit()：校验当前状态为 ISSUE，变更为 WAIT_AUDIT
  - 新增 isWaitAudit()：判断状态是否为 WAIT_AUDIT
  - 中文 JavaDoc
  - 编译通过

### Batch 2：基础设施层

#### T6：问题件数据对象
- 产物：`ClaimApplicationIssueDO.java`
- 路径：`backend/claim-application-service/src/main/java/com/wangguangwu/claim/application/infrastructure/issue/persistence/ClaimApplicationIssueDO.java`
- 完成条件：
  - 映射表 t_claim_application_issue
  - 字段：id、claimApplicationNo、issueNo、reason、auditor、issueContentJson、status、isOpen、closedAt、createdAt、updatedAt
  - 使用 MyBatis-Plus 注解
  - 使用 Lombok
  - 中文 JavaDoc
  - 编译通过

#### T7：问题件 Mapper
- 产物：`ClaimApplicationIssueMapper.java`
- 路径：`backend/claim-application-service/src/main/java/com/wangguangwu/claim/application/infrastructure/issue/persistence/ClaimApplicationIssueMapper.java`
- 完成条件：
  - 继承 BaseMapper<ClaimApplicationIssueDO>
  - 中文 JavaDoc
  - 编译通过

#### T8：问题件转换器
- 产物：`ClaimApplicationIssueConverter.java`
- 路径：`backend/claim-application-service/src/main/java/com/wangguangwu/claim/application/infrastructure/issue/persistence/ClaimApplicationIssueConverter.java`
- 完成条件：
  - 使用 MapStruct
  - DO ↔ ClaimApplicationIssue 转换
  - issueContentJson (String) ↔ issueContent (String) 直接映射
  - 中文 JavaDoc
  - 编译通过

#### T9：问题件仓储实现
- 产物：`ClaimApplicationIssueRepositoryImpl.java`
- 路径：`backend/claim-application-service/src/main/java/com/wangguangwu/claim/application/infrastructure/issue/persistence/ClaimApplicationIssueRepositoryImpl.java`
- 完成条件：
  - 实现 ClaimApplicationIssueRepository 端口
  - 依赖 ClaimApplicationIssueMapper、ClaimApplicationIssueConverter
  - findByIssueNo：按 issueNo 查询，转换为领域对象
  - hasOpenIssue：按 claimApplicationNo + isOpen=1 查询，判断是否存在
  - save：转换为 DO 后插入或更新
  - 使用 @Repository 注解
  - 中文 JavaDoc
  - 编译通过

### Batch 3：异常与错误码

#### T10：问题件错误码枚举
- 产物：`ClaimApplicationIssueErrorCode.java`
- 路径：`backend/claim-application-service/src/main/java/com/wangguangwu/claim/application/domain/issue/exception/ClaimApplicationIssueErrorCode.java`
- 完成条件：
  - 实现业务错误码接口（参考 ClaimApplicationIntakeErrorCode）
  - 枚举值：ISSUE_APPLICATION_NOT_FOUND、ISSUE_APPLICATION_STATUS_INVALID、ISSUE_ALREADY_OPEN、ISSUE_NOT_FOUND、ISSUE_INTAKE_INVALID、ISSUE_SYSTEM_ERROR
  - 中文 JavaDoc
  - 编译通过

#### T11：问题件业务异常
- 产物：`ClaimApplicationIssueException.java`
- 路径：`backend/claim-application-service/src/main/java/com/wangguangwu/claim/application/domain/issue/exception/ClaimApplicationIssueException.java`
- 完成条件：
  - 继承业务异常基类（参考 ClaimApplicationIntakeException）
  - 携带 ClaimApplicationIssueErrorCode
  - 中文 JavaDoc
  - 编译通过

### Batch 4：用例层

#### T12：问题件下发用例
- 产物：
  - `ClaimApplicationIssueIntakeCommand.java`
  - `ClaimApplicationIssueIntakeResult.java`
  - `ClaimApplicationIssueIntakeUseCase.java`
- 路径：`backend/claim-application-service/src/main/java/com/wangguangwu/claim/application/usecase/issue/`
- 完成条件：
  - Command：封装 claimApplicationNo、issueNo、reason、auditor、issueContent
  - Result：封装 issueNo、claimApplicationNo、created
  - UseCase：
    - 依赖 ClaimApplicationRepository、ClaimApplicationIssueRepository
    - receive(command) 方法
    - 校验申请存在且状态为 WAIT_AUDIT
    - 幂等判断：issueNo 已存在则返回 created=false
    - 校验无未关闭问题件
    - 创建问题件并保存
    - 申请状态变更为 ISSUE 并保存
    - 返回 created=true
    - 使用 @Transactional
  - 中文 JavaDoc
  - 编译通过

#### T13：问题件关闭用例
- 产物：
  - `ClaimApplicationIssueCloseCommand.java`
  - `ClaimApplicationIssueCloseResult.java`
  - `ClaimApplicationIssueCloseUseCase.java`
- 路径：`backend/claim-application-service/src/main/java/com/wangguangwu/claim/application/usecase/issue/`
- 完成条件：
  - Command：封装 issueNo、auditor
  - Result：封装 issueNo、closed
  - UseCase：
    - 依赖 ClaimApplicationIssueRepository、ClaimApplicationRepository
    - close(command) 方法
    - 查询问题件，不存在则抛 ISSUE_NOT_FOUND
    - 幂等判断：已 CLOSED 则返回 closed=false
    - 关闭问题件并保存
    - 查询关联申请，状态变更为 WAIT_AUDIT 并保存
    - 返回 closed=true
    - 使用 @Transactional
  - 中文 JavaDoc
  - 编译通过

### Batch 5：接口层

#### T14：问题件请求与响应 DTO
- 产物：
  - `ClaimApplicationIssueIntakeRequest.java`
  - `ClaimApplicationIssueCloseRequest.java`
  - `ClaimApplicationIssueIntakeResponse.java`
  - `ClaimApplicationIssueCloseResponse.java`
- 路径：`backend/claim-application-service/src/main/java/com/wangguangwu/claim/application/interfaces/intake/web/issue/`
- 完成条件：
  - IntakeRequest：claimApplicationNo、issueNo、reason、auditor、issueContent，JSR-303 校验注解
  - CloseRequest：auditor，JSR-303 校验注解
  - IntakeResponse：issueNo、claimApplicationNo、created
  - CloseResponse：issueNo、closed
  - 使用 Lombok
  - 中文 JavaDoc
  - 编译通过

#### T15：问题件响应渲染器
- 产物：`ClaimApplicationIssueResponseRenderer.java`
- 路径：`backend/claim-application-service/src/main/java/com/wangguangwu/claim/application/interfaces/intake/web/render/ClaimApplicationIssueResponseRenderer.java`
- 完成条件：
  - renderSuccess(traceId, result)：渲染成功响应
  - renderFailure(traceId, errorCode, errorMessage)：渲染失败响应
  - statusOf(errorCode)：错误码映射 HTTP 状态
    - ISSUE_APPLICATION_NOT_FOUND → 404
    - ISSUE_APPLICATION_STATUS_INVALID → 400
    - ISSUE_ALREADY_OPEN → 409
    - ISSUE_NOT_FOUND → 404
    - ISSUE_INTAKE_INVALID → 400
    - ISSUE_SYSTEM_ERROR → 500
  - 使用 @Component
  - 中文 JavaDoc
  - 编译通过

#### T16：问题件回调接口
- 产物：`ClaimApplicationIssueIntakeController.java`
- 路径：`backend/claim-application-service/src/main/java/com/wangguangwu/claim/application/interfaces/intake/web/issue/ClaimApplicationIssueIntakeController.java`
- 完成条件：
  - @RestController
  - @RequestMapping("/api/application/v1/callbacks")
  - POST /audit-issues：下发问题件
    - 接收 ClaimApplicationIssueIntakeRequest
    - 调用 ClaimApplicationIssueIntakeUseCase
    - 使用 ClaimApplicationIssueResponseRenderer 渲染响应
  - POST /audit-issues/{issueNo}/close：关闭问题件
    - 接收 ClaimApplicationIssueCloseRequest
    - 调用 ClaimApplicationIssueCloseUseCase
    - 使用 ClaimApplicationIssueResponseRenderer 渲染响应
  - 异常处理：捕获 ClaimApplicationIssueException，渲染失败响应
  - 中文 JavaDoc
  - 编译通过

### Batch 6：单元测试

#### T17：问题件领域模型测试
- 产物：`ClaimApplicationIssueTest.java`
- 路径：`backend/claim-application-service/src/test/java/com/wangguangwu/claim/application/domain/issue/ClaimApplicationIssueTest.java`
- 完成条件：
  - 测试 create()：状态为 OPEN
  - 测试 close()：状态变更为 CLOSED，记录 closedAt
  - 测试重复 close()：抛异常
  - 使用 AssertJ
  - 测试通过

#### T18：申请状态流转测试
- 产物：`ClaimApplicationIssueTransitionTest.java`
- 路径：`backend/claim-application-service/src/test/java/com/wangguangwu/claim/application/domain/claim/ClaimApplicationIssueTransitionTest.java`
- 完成条件：
  - 测试 WAIT_AUDIT → ISSUE：合法流转
  - 测试 ISSUE → WAIT_AUDIT：合法流转
  - 测试其他流转：抛异常
  - 使用 AssertJ
  - 测试通过

#### T19：问题件下发用例测试
- 产物：`ClaimApplicationIssueIntakeUseCaseTest.java`
- 路径：`backend/claim-application-service/src/test/java/com/wangguangwu/claim/application/usecase/issue/ClaimApplicationIssueIntakeUseCaseTest.java`
- 完成条件：
  - 首次下发：申请状态为 WAIT_AUDIT → 创建成功，状态变更为 ISSUE
  - 幂等下发：issueNo 已存在 → 返回成功，不重复创建
  - 申请不存在：抛 ISSUE_APPLICATION_NOT_FOUND
  - 申请状态不匹配：抛 ISSUE_APPLICATION_STATUS_INVALID
  - 已有未关闭问题件：抛 ISSUE_ALREADY_OPEN
  - 使用 Mockito
  - 测试通过

#### T20：问题件关闭用例测试
- 产物：`ClaimApplicationIssueCloseUseCaseTest.java`
- 路径：`backend/claim-application-service/src/test/java/com/wangguangwu/claim/application/usecase/issue/ClaimApplicationIssueCloseUseCaseTest.java`
- 完成条件：
  - 首次关闭：问题件状态为 OPEN → 关闭成功，申请状态回退 WAIT_AUDIT
  - 幂等关闭：问题件已 CLOSED → 返回成功，不重复处理
  - 问题件不存在：抛 ISSUE_NOT_FOUND
  - 使用 Mockito
  - 测试通过

### Batch 7：构建验证

#### T21：编译与测试验证
- 产物：构建日志
- 完成条件：
  - 执行 `mvn clean compile`，编译通过
  - 执行 `mvn test`，所有测试通过（包括新增测试和原有测试）
  - 无编译警告
  - 无测试失败

#### T22：代码格式化
- 产物：格式化后的代码
- 完成条件：
  - 使用 IDEA 默认格式化配置
  - 删除无用 import
  - 删除无用空行
  - 代码风格统一

## 任务依赖

```
T1-T5（领域模型） → T6-T9（基础设施） → T10-T11（异常） → T12-T13（用例） → T14-T16（接口） → T17-T20（测试） → T21-T22（验证）
```

## 预估工作量

- Batch 1：2 小时
- Batch 2：2 小时
- Batch 3：0.5 小时
- Batch 4：2 小时
- Batch 5：2 小时
- Batch 6：3 小时
- Batch 7：1 小时
- 总计：约 12.5 小时
