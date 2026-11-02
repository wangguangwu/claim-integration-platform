# 022 架构偏差清零 · 任务清单

## 任务批次

### Batch 1：代码归位

#### T1：领域服务去框架化
- 产物：修改 `IssueDomainService.java`；新增 `IssueDomainServiceConfiguration.java`
- 路径：`domain/issue/service/`、`infrastructure/issue/config/`
- 完成条件：
  - 删除 `IssueDomainService` 的 `org.springframework.stereotype.Service` import 与 `@Service`
  - 保留 `@Slf4j`、`@RequiredArgsConstructor`、字段、构造器、方法体与中文 JavaDoc
  - 新增 `@Configuration` 配置类，`@Bean` 方法名 `issueDomainService`，返回 `IssueDomainService`
  - 配置类只依赖三个领域端口，不引用 `usecase` 编排类
  - `grep -rn '@Service\|@Component\|@Repository' domain` 为 0
  - 编译通过，问题件相关用例测试通过

#### T2：问题件渲染器归位
- 产物：移动 `ClaimApplicationIssueResponseRenderer.java` 到 `interfaces/issue/web/render/`
- 路径：`interfaces/intake/web/render/`、`interfaces/issue/web/render/`、`interfaces/issue/web/`
- 完成条件：
  - 包声明改为 `interfaces.issue.web.render`，类名、`@Component` 与三个渲染方法不变
  - `ClaimApplicationIssueIntakeController`、`ClaimApplicationIssueQueryController` 的 import 修正
  - `interfaces/intake/web/render` 仍保留 `ClaimApplicationResponseRenderer`
  - 全仓无 `interfaces.intake.web.render.ClaimApplicationIssueResponseRenderer` 引用
  - 编译通过

#### T3：Mapper 命名统一
- 产物：重命名两个 Mapper；修改两个仓储实现的字段类型
- 路径：`infrastructure/issue/persistence/`、`infrastructure/claimcase/persistence/`
- 完成条件：
  - `ClaimApplicationIssueMapper` → `ClaimApplicationIssueDOMapper`
  - `ClaimCaseRelationMapper` → `ClaimCaseRelationDOMapper`
  - `@Mapper` 与 `BaseMapper<...DO>` 泛型不变，无 XML 映射改动
  - `ClaimApplicationIssueRepositoryImpl`、`ClaimCaseRelationRepositoryImpl` 字段类型同步更名
  - `find . -name '*Mapper.java' | grep -vc 'DOMapper\.java$'` 为 0
  - 编译通过

### Batch 2：文档回填

#### T4：自检口径与统计回填
- 产物：修改 `docs/architecture/ddd-standard-architecture.md`、`docs/architecture/claim-application-architecture.md`、`docs/architecture/code-navigation.md`
- 路径：`docs/architecture/`
- 完成条件：
  - 删除"当前已知偏差"小节，改为 16 项自检全部达到预期
  - 第 15 项期望值为 15，并按 design §5.1 列出六类白名单
  - 第 16 项命令排除注释行，期望值为 0
  - 统计更新为生产类 186、文件 196、目录 108，`infrastructure/issue` 为 6
  - 渲染器路径说明区分租户响应与问题件响应
  - 包结构示例补充 `interfaces/issue/web/render`
  - 文档中不存在旧 Mapper 名与旧渲染器包路径

### Batch 3：验证

#### T5：构建、ArchUnit 与自检
- 产物：无代码产物，输出验证结论
- 完成条件：
  - `JAVA_HOME` 指向 JDK 21 执行 `mvn -B -pl backend/claim-application-service -am clean package` 成功
  - `ClaimApplicationArchitectureTest` 7 条规则通过
  - 既有单元测试全部通过
  - 逐条执行 16 项自检：第 8 项 6、第 8b 项 2、第 15 项 15，其余为 0
  - 再次统计生产类、文件、目录数与文档一致

#### T6：运行栈回归
- 产物：无代码产物，输出验证结论
- 完成条件：
  - 重启应用与网关，`/actuator/health` 均为 `UP`，`IssueDomainService` Bean 注入成功
  - 经网关完成问题件下发、按案件号查询、关闭，响应字段与 022 前一致
  - 完成一次签收与一次审核签收，材料、承保、审核交付、案件关联与消费登记正常
  - 两个重命名 Mapper 对应的读写路径正常，无 MyBatis 绑定异常
  - 确认 HTTP 路径、JSON 字段、Topic、消费组、事件类型、数据库与 Redis 键均未变化
