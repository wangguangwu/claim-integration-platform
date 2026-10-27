# 本地基础服务检查说明

## 运行检查目标

本文档说明 `claim-local` 编排中 MySQL、Redis、RocketMQ 和 Application 服务的检查方式。先在仓库根目录运行 `./scripts/docker-start.sh`，再执行对应检查。

## 通用容器状态

```bash
docker compose --env-file deploy/.env.example -f deploy/docker-compose.yml ps -a
```

`mysql`、`redis`、`rocketmq-namesrv`、`rocketmq-broker`、`rocketmq-dashboard` 应显示为 `healthy`。本编排不保留额外的初始化容器；Topic 由 `scripts/docker-start.sh` 在 Broker 健康后创建。

在 Apple Silicon 上，Dashboard 不应显示 `AMD64` 标签。可执行以下命令确认容器架构：

```bash
docker image inspect apacherocketmq/rocketmq-dashboard:2.1.0 --format '{{.Architecture}}/{{.Os}}'
```

预期输出为 `arm64/linux`，Docker Desktop 容器卡片不出现 AMD64 模拟标识。

若创建了本地 `deploy/.env`，将上述命令中的 `deploy/.env.example` 替换为 `deploy/.env`。

## MySQL

### 健康检查

```bash
docker exec claim-mysql mysqladmin ping -h 127.0.0.1 -uclaim_app -pclaim_app
```

预期输出包含 `mysqld is alive`。

### 表结构检查

```bash
docker exec claim-mysql mysql -uclaim_app -pclaim_app -D claim_application_db -e "SHOW TABLES;"
```

预期存在 `application`、`material`、`issue`、`case_relation`、`interaction_log`、`outbox_event`、`consumed_event` 七张表。

## Redis

### ACL 与连通性检查

```bash
docker exec claim-redis redis-cli --user claim_app --pass claim_redis ping
```

预期输出为 `PONG`。如修改了 `deploy/.env` 中的 Redis 账号或密码，应使用对应值替换命令中的默认值。

## RocketMQ

### NameServer 与 Broker 检查

```bash
docker exec claim-rocketmq-broker sh mqadmin clusterList -n rocketmq-namesrv:9876
```

预期输出包含 `DefaultCluster` 与 `broker-a`。

### Topic 检查

```bash
docker exec claim-rocketmq-broker sh mqadmin topicList -n rocketmq-namesrv:9876
```

预期包含以下 Topic：

- `claim_application_material_process_v1`
- `claim_application_audit_delivery_v1`

## RocketMQ Dashboard

访问 [http://127.0.0.1:8082](http://127.0.0.1:8082)，使用 `deploy/.env` 或 `.env.example` 中的 `ROCKETMQ_DASHBOARD_USERNAME`、`ROCKETMQ_DASHBOARD_PASSWORD` 登录。

登录后可按以下路径检查：

| 页面 | 检查内容 |
|---|---|
| `Cluster` | NameServer 与 `broker-a` 是否在线 |
| `Topic` | 两个 Application Topic 是否存在、读写队列是否均为 8 |
| `Consumer` | 后续业务 Spec 接入后检查消费者组和消费堆积 |
| `Message` | 后续产生消息后按 Topic、Key 或时间检索消息 |

Dashboard 仅用于本地开发与排查，不替代生产环境的监控、权限与审计能力。

## Application 服务

Application 服务启动后执行：

```bash
curl -fsS http://127.0.0.1:8081/actuator/health
```

预期返回 `{"status":"UP"}`。若依赖服务不可用，健康检查会明确展示对应组件状态，不应伪造 `UP`。
