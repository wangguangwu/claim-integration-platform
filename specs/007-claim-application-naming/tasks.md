# 007 理赔申请命名全链路收敛任务

任务与 design 的映射表一一对应，N1–N4 可并行准备但必须同批提交，N5 与 N8 是门禁。

## 第 1 批 · 代码改名

| 编号 | 任务 | 产物 | 完成条件 |
|---|---|---|---|
| N1 | 25 个业务类型与启动类改名 | design §二 全表对应的新类文件与引用点 | 表内旧类名在 backend 命中 0；`git mv` 保留文件历史；模块构建通过 |
| N2 | 标识符与日志文本改名 | design §三 表内 `claimApplicationNo`、`externalClaimApplicationNo`、`claimApplicationAttempt`、局部 `claimApplication` | 三个旧标识符在 `.java` 命中 0；包名、`spring.application.name`、配置文件名未被误改；日志与异常文本键名统一 |
| N3 | 错误码常量与 MDC 键改名 | `ClaimApplicationIntakeErrorCode` 常量名、logback/MDC 键定义 | `APPLICATION_INTAKE_` 字面量命中 0；错误响应 `code` 值为新码；MDC 键为 `claimApplicationNo` |
| N4 | 对外字段与路径改名 | 响应 DTO、三类回调 DTO、信封与载荷键、出站 Feign 与假件路径变量 | 旧键名与旧路径在 backend 命中 0；未引入 `@JsonProperty` 兼容分支；租户私有入参键保持原名；网关 `/api/application/**` 路由保持不动 |

## 第 2 批 · 数据与文档

| 编号 | 任务 | 产物 | 完成条件 |
|---|---|---|---|
| N5 | 列与索引改名及本地卷迁移 | `deploy/mysql/init/02-application-schema.sql` 新列名与新索引名；一次性 `ALTER TABLE ... RENAME COLUMN` 脚本 | init 脚本与运行库 `information_schema` 列名集合一致；`application_no` 系列列命中 0；迁移前后各表行数与抽查主键一致；生成列表达式与索引覆盖列顺序不变 |
| N6 | 文档、脚本与历史 Spec 同步 | 集成契约文档（含 breaking 标注）、`docs/testing` 3 篇、`docs/architecture` 相关页、`scripts/jmeter/*.jmx` 的 JSONPath、specs 002/003/004/006 被触及条目 | 文档字段名、列名、路径与 design §三/§四 映射表逐条一致；002 信封冻结注释与 006 边界条目改为指向 007；压测脚本断言取新键名 |

## 第 3 批 · 验证门

| 编号 | 任务 | 产物 | 完成条件 |
|---|---|---|---|
| N7 | 全反应堆构建与服务重启 | 应用与网关新 jar | `mvn -B clean package` 通过；两服务重启日志无新增 ERROR |
| N8 | 黄金路径与历史数据比对 | 运行栈验证记录 | 六场景响应逐字段与改名前一致（除业务编号键名与错误码字面量）；改名前落库的历史申请、材料、轨迹、事件行在新代码下可读；轨迹 STARTED 与终态行数相等且孤儿组为 0 |

## 统一收口

1. `mvn -B clean package` 全反应堆通过。
2. 停应用 → 执行列改名迁移 → 起新 jar 与网关，日志无新增 ERROR。
3. 黄金路径六场景经网关逐项比对，含同号幂等与畸形 JSON。
4. 按 requirements 验收标准逐条 grep 计数，全部为 0 才算完成。
5. 提交信息中文，说明是命名收敛而非行为变更；未获授权不推送。

## 与其他 Spec 的关系

- 本 Spec 先于 006 第 3 轮（T8–T13）执行，避免在同一批类上产生二次 diff。
- 006 T14/T15（Outbox 索引与 Flyway 纳管）在改名后的列名上继续，索引名取本 Spec §四 的新名。
