# 022 架构偏差清零 · 设计

## 一、改动模块

| 模块 | 改动 |
|---|---|
| `domain/issue/service` | `IssueDomainService` 去掉 `@Service` 与 Spring import，保留 `@Slf4j`、`@RequiredArgsConstructor` |
| `infrastructure/issue/config` | 新增 `IssueDomainServiceConfiguration`，以 `@Bean` 装配领域服务 |
| `interfaces/intake/web/render` | 移出 `ClaimApplicationIssueResponseRenderer` |
| `interfaces/issue/web/render` | 接收 `ClaimApplicationIssueResponseRenderer`，包名改为问题件域 |
| `interfaces/issue/web` | 两个控制器更新渲染器 import |
| `infrastructure/issue/persistence` | `ClaimApplicationIssueMapper` 重命名为 `ClaimApplicationIssueDOMapper`，仓储实现改字段类型 |
| `infrastructure/claimcase/persistence` | `ClaimCaseRelationMapper` 重命名为 `ClaimCaseRelationDOMapper`，仓储实现改字段类型 |
| `docs/architecture` | 自检第 15、16 项口径，统计数字，渲染器路径，已知偏差段落 |

## 二、领域服务去框架化

### 2.1 现状

`IssueDomainService` 位于 `domain/issue/service`，只依赖三个领域端口，但带 `@Service`，使 `domain` 出现唯一的 Spring 框架依赖。直接删除注解会导致用例注入不到 Bean，因此需要把装配职责移出领域。

### 2.2 目标结构

```java
package com.wangguangwu.claim.application.infrastructure.issue.config;

/**
 * 问题件领域服务的装配入口，领域服务本身保持零框架依赖。
 *
 * @author wangguangwu
 * @since 2026-11-02
 */
@Configuration
public class IssueDomainServiceConfiguration {

    /**
     * 装配问题件领域服务，依赖全部来自领域端口。
     *
     * @param claimApplicationRepository      申请聚合仓储
     * @param claimApplicationIssueRepository 问题件聚合仓储
     * @param issueDomainEventPublisher       领域事件发布端口
     * @return 问题件领域服务
     */
    @Bean
    public IssueDomainService issueDomainService(ClaimApplicationRepository claimApplicationRepository,
                                                 ClaimApplicationIssueRepository claimApplicationIssueRepository,
                                                 IssueDomainEventPublisher issueDomainEventPublisher) {
        return new IssueDomainService(
                claimApplicationRepository,
                claimApplicationIssueRepository,
                issueDomainEventPublisher);
    }
}
```

`IssueDomainService` 只删除 `import org.springframework.stereotype.Service` 与 `@Service`，类名、字段、构造器、方法、日志与事务语义不变。Bean 名仍为 `issueDomainService`，类型不变，两个用例按类型注入，不需要改动。

### 2.3 方案取舍

| 方案 | 结论 |
|---|---|
| 保留 `@Service` | 否决：违反 021 基线"domain 零框架依赖" |
| 把领域服务移到 `usecase` | 否决：它协调两个聚合并发布领域事件，属于领域服务，移动会改变归属 |
| 由用例直接 `new` | 否决：重复构造、破坏依赖注入，且两个用例都要改 |
| `infrastructure/issue/config` 配置 Bean | 采用：装配属基础设施职责，问题件专属装配与问题件适配器同域 |

## 三、问题件渲染器归位

### 3.1 移动

`interfaces/intake/web/render/ClaimApplicationIssueResponseRenderer.java` → `interfaces/issue/web/render/ClaimApplicationIssueResponseRenderer.java`。

- 包声明改为 `com.wangguangwu.claim.application.interfaces.issue.web.render`。
- 类名、`@Component`、三个渲染方法与响应结构不变。
- `interfaces/intake/web/render` 仍保留 `ClaimApplicationResponseRenderer`，不删除该目录。

### 3.2 引用修复

| 文件 | 修改 |
|---|---|
| `interfaces/issue/web/ClaimApplicationIssueIntakeController.java` | import 改为 `interfaces.issue.web.render` |
| `interfaces/issue/web/ClaimApplicationIssueQueryController.java` | import 改为 `interfaces.issue.web.render` |

移动后 `interfaces/issue` 内不再出现 `interfaces/intake` 引用，自检第 5 项归零。

## 四、Mapper 命名统一

| 原名称 | 新名称 |
|---|---|
| `ClaimApplicationIssueMapper` | `ClaimApplicationIssueDOMapper` |
| `ClaimCaseRelationMapper` | `ClaimCaseRelationDOMapper` |

- 两份文件仍在原包，接口体只改接口名，`@Mapper` 与 `BaseMapper<...DO>` 泛型不变。
- `ClaimApplicationIssueRepositoryImpl` 的字段类型改为 `ClaimApplicationIssueDOMapper`。
- `ClaimCaseRelationRepositoryImpl` 的字段类型改为 `ClaimCaseRelationDOMapper`。
- 字段名保留 `issueMapper`、`relationMapper`，不按 Bean 名注入；MyBatis 通过 `@Mapper` 扫描接口，重命名不改变 SQL 与映射。
- 全仓无 XML 映射文件，无 `@Qualifier` 或按名称注入。

## 五、自检口径校准

### 5.1 第 15 项

命令保持 `grep -rn '^\s*public [A-Z][A-Za-z0-9]*(' --include='*.java' .`，期望值固定为 15，并按类别列出白名单：

| 类别 | 数量 | 原因 |
|---|---:|---|
| `BusinessException` | 2 | 需要 `super(message)` 与 `super(message, cause)` |
| `RedissonClaimApplicationIntakeLock` | 1 | 需要按配置初始化降级信号量 |
| `RedisAvailabilityCircuit` | 1 | 需要把配置换算成纳秒并初始化日志节流 |
| `Local*` 外部实现 | 5 | 需要按配置决定休眠与失败 |
| 四个人员角色值对象 | 4 | 需要 `super(...)` 传递基础字段 |
| 两个租户协议流水线 | 2 | 需要 `super(...)` 装配组件 |

新增配置类的 `@Bean` 方法签名是 `public IssueDomainService issueDomainService(...)`，不匹配该正则，不会抬高计数。

### 5.2 第 16 项

原命令把 Javadoc 中的 `{@code PersonInfo}` 计入结果。改为排除注释行：

```bash
grep -rn 'PersonInfo' --include='*.java' domain/claim/ClaimApplication.java usecase/intake/command \
  interfaces/intake/tenantprotocol | grep -vE ':[0-9]+:\s*\*' | wc -l
```

期望值为 0；若代码中真的在参数或字段位置声明 `PersonInfo`，结果仍会大于 0。

### 5.3 文档改写

- 删除"当前已知偏差"小节，改为"16 项全部达到预期"。
- 第 15 项期望值从"8 处白名单"改为 15，并保留上表。
- 第 16 项命令替换为排除注释的版本。

## 六、统计口径变化

新增一个配置类，其余为移动或重命名：

| 指标 | 022 前 | 022 后 |
|---|---:|---:|
| 生产类（不含 `package-info`） | 185 | 186 |
| Java 文件（含 `package-info`） | 195 | 196 |
| 目录 | 106 | 108 |
| `infrastructure/issue` 类数 | 5 | 6 |

`domain`、`usecase` 的文件数不变，`interfaces` 因新增 `interfaces/issue/web/render` 目录而目录数 +1，`infrastructure` 因新增配置类文件数 +1、目录数 +1。

## 七、文档同步

| 文档 | 修改 |
|---|---|
| `docs/architecture/ddd-standard-architecture.md` | 统计数字、自检第 15/16 项、删除已知偏差、补充 `interfaces/issue/web/render` 示例 |
| `docs/architecture/claim-application-architecture.md` | 渲染器说明拆分为租户响应与问题件响应两个路径 |
| `docs/architecture/code-navigation.md` | 若存在问题件渲染器或 Mapper 路径示例，同步为新路径 |

## 八、实现顺序

1. `IssueDomainService` 去注解，新增配置类，启动编译验证。
2. 移动渲染器并修复两个 import。
3. 重命名两个 Mapper 并修复仓储字段类型。
4. 回填文档统计与自检口径。
5. 全量构建、ArchUnit、16 项自检。
6. 运行栈回归：启动装配、问题件下发/查询/关闭、签收与审核签收。

前三步互不依赖，但按“领域 → 入站 → 基础设施”顺序执行，便于每步独立编译定位问题。

## 九、行为不变性

| 维度 | 保证方式 |
|---|---|
| 领域规则与状态机 | 只删注解，方法体不变 |
| Bean 类型与名称 | `@Bean` 方法名 `issueDomainService`，返回类型不变 |
| HTTP 路径与响应 | 渲染器只改包，方法与字段不变 |
| MyBatis SQL | Mapper 只改接口名，泛型与 `@Mapper` 不变 |
| 事务边界 | `@Transactional` 仍在用例事务服务上，不移动 |
| MQ 与 Outbox | 不涉及 |
| 数据库与 Redis | 不涉及 |

## 十、风险与应对

| 风险 | 应对 |
|---|---|
| 配置类未被扫描，领域服务 Bean 缺失 | 配置类放在根包下的 `infrastructure`，启动验证注入成功 |
| 渲染器移动后残留旧 import | 全仓 grep 旧包路径为 0，编译验证 |
| Mapper 重命名影响按名称注入 | 已确认只有构造器按类型注入，无 XML；启动与读写验证 |
| 自检计数被 `@Bean` 方法污染 | 执行第 15 项，确认仍为 15 |
| 统计沿用 021 数字 | 重新执行文件、类、目录统计后回填 |

## 十一、验收映射

| 验收标准 | 验证方式 |
|---|---|
| 1 | 自检第 1 项为 0，`grep @Service domain` 为 0 |
| 2 | 应用启动，问题件下发/查询/关闭通过网关成功 |
| 3 | 自检第 5 项为 0，响应字段与 021 回归一致 |
| 4 | 自检第 7 项为 0，问题件与案件关联读写成功 |
| 5 | 16 项自检逐条执行，期望值匹配 |
| 6 | `mvn -B -pl backend/claim-application-service -am clean package` 成功，ArchUnit 7 条通过 |
| 7 | `find` 统计类数、文件数、目录数与文档一致 |
