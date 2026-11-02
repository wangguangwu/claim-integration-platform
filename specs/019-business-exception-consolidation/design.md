# 019 业务异常收敛 · 设计

## 一、改动模块

| 模块 | 改动 |
|---|---|
| `shared/contract` | 新增 `BusinessErrorCode`、`BusinessException`；删除 `ClaimApplicationAsyncException` |
| `domain/claim/exception` | 错误码枚举实现统一接口；删除 `ClaimApplicationIntakeException` |
| `domain/issue/exception` | 错误码枚举实现统一接口；删除 `ClaimApplicationIssueException` |
| `shared/contract`（异步） | 错误码枚举实现统一接口 |
| `interfaces/web` | 新增 `ApiErrorCodeProvider`、`BusinessErrorStatusRegistry`、`WebExceptionHandler` |
| `interfaces/*/web` | 控制器声明接口错误码；删除三个异常处理器与渲染器失败方法 |
| `usecase/intake` | 事务服务用空结果表达存在性不一致；删除 `ClaimApplicationExistenceMismatchException` |
| 测试与文档 | 迁移断言、补映射完整性与控制器约束测试、更新架构文档 |

## 二、核心组件与职责

### BusinessErrorCode

```java
public interface BusinessErrorCode {

    String code();
}
```

只暴露错误码字符串。三个现有枚举实现该方法，返回 `name()`，错误码字符串不变，集成契约不受影响。

### BusinessException

```java
public class BusinessException extends RuntimeException {

    private final BusinessErrorCode errorCode;

    public BusinessException(BusinessErrorCode errorCode, String message);
    public BusinessException(BusinessErrorCode errorCode, String message, Throwable cause);
}
```

领域、用例、基础设施与入站适配都可以抛出；不依赖 Spring Web 类型，领域层不感知 HTTP。

### BusinessErrorStatusRegistry

入站层的唯一状态映射表，位于 `interfaces/web`：

| 错误码 | HTTP 状态 |
|---|---|
| `TENANT_PROTOCOL_UNSUPPORTED`、`TENANT_PROTOCOL_AMBIGUOUS`、`CLAIM_APPLICATION_INTAKE_INVALID` | 400 |
| `CLAIM_APPLICATION_INTAKE_BUSY` | 503 |
| `CLAIM_APPLICATION_INTAKE_SYSTEM_ERROR` | 500 |
| `AUDIT_DELIVERY_APPLICATION_NOT_FOUND` | 404 |
| `ISSUE_APPLICATION_NOT_FOUND`、`ISSUE_NOT_FOUND`、`CASE_RELATION_NOT_FOUND` | 404 |
| `ISSUE_APPLICATION_STATUS_INVALID`、`ISSUE_INTAKE_INVALID` | 400 |
| `ISSUE_ALREADY_OPEN` | 409 |
| `ISSUE_SYSTEM_ERROR` | 500 |
| `CALLBACK_INVALID`、`AUDIT_SIGNED_INVALID` | 400 |
| `CLAIM_APPLICATION_NOT_FOUND` | 404 |
| `CLAIM_APPLICATION_ASYNC_SYSTEM_ERROR` | 500 |

未登记的错误码查询时抛 `IllegalArgumentException`，不静默降级；完整性测试遍历三个枚举的全部常量，保证新增错误码必须同时补映射。

### ApiErrorCodeProvider

```java
public interface ApiErrorCodeProvider {

    BusinessErrorCode requestInvalidCode();

    BusinessErrorCode systemErrorCode();
}
```

四个 HTTP 入口控制器实现该接口：

| 控制器 | 请求非法码 | 系统错误码 |
|---|---|---|
| `ClaimApplicationIntakeController` | `CLAIM_APPLICATION_INTAKE_INVALID` | `CLAIM_APPLICATION_INTAKE_SYSTEM_ERROR` |
| `ClaimApplicationIssueIntakeController` | `ISSUE_INTAKE_INVALID` | `ISSUE_SYSTEM_ERROR` |
| `ClaimApplicationIssueQueryController` | `ISSUE_INTAKE_INVALID` | `ISSUE_SYSTEM_ERROR` |
| `UnderwritingCallbackController` | `CALLBACK_INVALID` | `CLAIM_APPLICATION_ASYNC_SYSTEM_ERROR` |

字段校验失败与报文不可解析发生在参数解析阶段，Spring 仍能提供 `HandlerMethod`，处理器据此取到当前控制器声明的错误码，因此不需要按 URL 做字符串匹配，也不改变各入口的对外错误码。

### WebExceptionHandler

唯一的 `@RestControllerAdvice`，位于 `interfaces/web`：

| 异常 | 状态 | 错误码 | 日志 |
|---|---|---|---|
| `BusinessException` | 注册表映射 | 异常携带的错误码 | WARN |
| `MethodArgumentNotValidException` | 400 | 控制器声明的请求非法码 | WARN |
| `HttpMessageNotReadableException` | 400 | 控制器声明的请求非法码 | WARN |
| 其他 `Exception` | 500 | 控制器声明的系统错误码 | ERROR |

消息规则：

- 业务异常使用异常自带消息。
- 字段校验失败使用第一个字段错误的默认消息；没有字段错误时使用“请求报文不合法”。
- 报文不可解析使用“请求报文不合法”。
- 未预期异常使用“系统处理失败”，堆栈只在 ERROR 日志输出一次。

拿不到 `HandlerMethod` 或控制器未实现接口时，记录 ERROR 并按 500 输出系统处理失败，不吞异常。

## 三、失败响应渲染

删除 `ClaimApplicationResponseRenderer#renderFailure` 与 `ClaimApplicationIssueResponseRenderer#renderFailure`，失败响应统一由 `WebExceptionHandler` 组装 `ApiResponse.failure`。两个渲染器只保留成功响应方法，不再持有状态码 `switch`。

## 四、内部重试信号改造

`ClaimApplicationIntakeTransactionService#intake` 返回 `Optional<ClaimApplicationIntakeOutcome>`：

- 判定为不存在：返回创建结果。
- 判定为存在但按外部申请号读不到：返回 `Optional.empty()`。

`ClaimApplicationIntakeUseCase` 收到空结果后置位 `forceCreate` 并进入下一次重试，行为与 018 一致。删除 `ClaimApplicationExistenceMismatchException`。

这样包内不再需要异常作为控制流；如果未来还有同类信号，继续用返回值表达，而不是新增异常类或单独目录。

## 五、删除清单

- `domain/claim/exception/ClaimApplicationIntakeException`
- `domain/issue/exception/ClaimApplicationIssueException`
- `shared/contract/ClaimApplicationAsyncException`
- `usecase/intake/ClaimApplicationExistenceMismatchException`
- `interfaces/intake/web/ClaimApplicationIntakeExceptionHandler`
- `interfaces/issue/web/ClaimApplicationIssueExceptionHandler`
- `interfaces/underwriting/web/UnderwritingCallbackExceptionHandler`
- 两个渲染器的失败方法与状态码 `switch`

## 六、对现有功能的影响

- 对外错误码、HTTP 状态码、消息语义与响应结构不变。
- MQ 消费行为不变：`BusinessException` 仍然向上抛出，由 binder 重投，耗尽后进入死信。
- 日志继续按业务拒绝 WARN、系统异常 ERROR 分级，敏感字段不出现在日志中。
- 领域层依赖方向不变，不引入 Spring Web 依赖。

## 七、测试策略

- 状态映射完整性测试：遍历 `ClaimApplicationIntakeErrorCode`、`ClaimApplicationIssueErrorCode`、`ClaimApplicationAsyncErrorCode` 全部常量，断言注册表均有映射。
- ArchUnit 新增规则：`interfaces` 下所有 `@RestController` 必须实现 `ApiErrorCodeProvider`。
- 既有用例测试迁移到 `BusinessException` 与错误码断言。
- 运行栈回归：签收非法与成功、承保回调 200/400/404、问题件下发/关闭/查询的正常与 4xx、审核签收重复与死信，逐项核对错误码与状态码。
