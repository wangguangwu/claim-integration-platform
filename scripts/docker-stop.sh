#!/usr/bin/env bash

set -euo pipefail

# 脚本可从任意目录执行，因此先计算仓库根目录，不依赖调用方当前路径。
script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
project_root="$(cd "${script_dir}/.." && pwd)"
compose_file="${project_root}/deploy/docker-compose.yml"
local_env_file="${project_root}/deploy/.env"
example_env_file="${project_root}/deploy/.env.example"

# 与启动脚本保持同一环境文件选择规则，确保项目名、端口覆盖项一致。
if [[ -f "${local_env_file}" ]]; then
    env_file="${local_env_file}"
else
    env_file="${example_env_file}"
fi

if [[ ! -f "${compose_file}" || ! -f "${env_file}" ]]; then
    echo "缺少 Docker 编排或环境变量文件" >&2
    exit 1
fi

# 默认不传 -v，停止容器时保留数据库、Redis 和 RocketMQ 的本地数据卷。
docker compose --env-file "${env_file}" -f "${compose_file}" down
