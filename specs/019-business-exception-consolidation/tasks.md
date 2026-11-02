# 019 业务异常收敛 · 任务清单

## 任务批次

### Batch 1：异常基线

#### T1：统一业务错误码接口与异常
- 产物：`BusinessErrorCode.java`、`BusinessException.java`
- 路径：`backend/claim-application-service/src/main/java/com/wangguangwu/claim/application/shared/contract/`
- 完成条件：
  - `BusinessErrorCode` 仅声明 `String code()`
  - `BusinessException` 携带 `BusinessErrorCode`，提供 `(code, message)` 与 `(code, message, cause)` 构造
  - 不依赖 Spring Web 类型，中文 JavaDoc
  - 编译通过

#### T2：错误码枚举实现统一接口
- 产物：修改 `ClaimApplicationIntakeErrorCode`、`ClaimApplicationIssueErrorCode`、`ClaimApplicationAsyncErrorCode`
- 完成条件：
  - 均 `implements BusinessErrorCode`
  - `code()` 返回 `name()`，枚举常量名与错误码字符串不变
  - 中文 JavaDoc 补充实现说明
  - 编译通过

### Batch 2：入站层统一处理

#### T3：HTTP 状态注册表
- 产物：`BusinessErrorStatusRegistry.java`
- 路径：`backend/claim-application-service/src/main/java/com/wangguangwu/claim/application/interfaces/web/`
- 完成条件：
  - 三个枚举全部 17 个错误码均有唯一映射
  - 未登记错误码抛 `IllegalArgumentException`，不静默降级
  - 映射表与 design §二 完全一致
  - 中文 JavaDoc，编译通过

#### T4：接口错误码声明
- 产物：`ApiErrorCodeProvider.java`；修改四个控制器
- 路径：`interfaces/web/`、`interfaces/intake/web/`、`interfaces/issue/web/`、`interfaces/underwriting/web/`
- 完成条件：
  - 接口声明 `requestInvalidCode()` 与 `systemErrorCode()`
  - 四个控制器的返回值与 design §二 表格一致
  - 控制器业务方法与成功响应不变
  - 编译通过

#### T5：全局异常处理器
- 产物：`WebExceptionHandler.java`
- 路径：`backend/claim-application-service/src/main/java/com/wangguangwu/claim/application/interfaces/web/`
- 完成条件：
  - 唯一 `@RestControllerAdvice`，处理 `BusinessException`、`MethodArgumentNotValidException`、`HttpMessageNotReadableException`、`Exception`
  - 状态码由注册表给出；请求非法与系统错误码由当前控制器声明给出
  - 日志分级、脱敏与 design §二 一致
  - 拿不到控制器时记录 ERROR 并返回 500
  - 中文 JavaDoc，编译通过

### Batch 3：清理与迁移

#### T6：删除旧异常与处理器
- 产物：删除三个业务异常类、三个异常处理器、两个渲染器失败方法与状态码 `switch`
- 完成条件：
  - 全仓无旧异常类引用
  - 全仓仅一个 `@RestControllerAdvice`
  - 渲染器只保留成功响应方法
  - 编译通过

#### T7：内部重试信号改返回值
- 产物：修改 `ClaimApplicationIntakeTransactionService`、`ClaimApplicationIntakeUseCase`；删除 `ClaimApplicationExistenceMismatchException`
- 完成条件：
  - 事务服务 `intake` 返回 `Optional<ClaimApplicationIntakeOutcome>`
  - 读不到申请时返回空，用例置位 `forceCreate` 重试
  - 重试上限、唯一键冲突恢复与缓存升级逻辑不变
  - 编译通过

#### T8：调用点迁移
- 产物：修改全部业务异常构造点
- 完成条件：
  - 应用接入流水线、领域服务、用例、锁实现、审核签收装配器均抛 `BusinessException`
  - 错误码与消息保持不变
  - 无 `ClaimApplicationIntakeException.invalid(...)` 等旧静态工厂残留
  - 编译通过

### Batch 4：测试与文档

#### T9：测试迁移与新增
- 产物：更新既有用例测试；新增 `BusinessErrorStatusRegistryTest`；在 ArchUnit 测试中新增控制器约束
- 完成条件：
  - 既有断言改为 `BusinessException` 与错误码
  - 映射完整性测试覆盖三个枚举全部常量
  - ArchUnit 规则校验所有控制器实现 `ApiErrorCodeProvider`
  - 全部测试通过

#### T10：架构文档同步
- 产物：修改 `docs/architecture/ddd-standard-architecture.md`
- 完成条件：
  - 业务异常约定改为 `BusinessException` + `BusinessErrorCode`
  - 说明 HTTP 状态映射在入站层注册表、请求非法与系统错误码由控制器声明
  - 说明内部重试信号使用返回值而非异常
  - 不出现过时的旧异常类名

### Batch 5：验证

#### T11：构建与运行栈回归
- 产物：无代码产物，输出验证结论
- 完成条件：
  - `JAVA_HOME` 指向 JDK 21 执行全量 Maven 构建成功
  - 现有测试与 ArchUnit 规则全部通过
  - 运行栈回归签收、承保回调、问题件、审核签收的错误码与状态码，与 018 契约逐项一致
  - 确认旧异常类与旧处理器无残留
