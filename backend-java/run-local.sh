#!/usr/bin/env zsh

set -euo pipefail

script_dir=${0:A:h}
env_file="$script_dir/.env"
cd "$script_dir"

if [[ ! -f "$env_file" ]]; then
  print -u2 "缺少本机配置文件：$env_file"
  exit 1
fi

set -a
source "$env_file"
set +a

if [[ -z ${MINERU_API_KEY:-} ]]; then
  print -u2 "请先在 $env_file 中设置 MINERU_API_KEY"
  exit 1
fi

if [[ -z ${DB_PASSWORD:-} ]]; then
  print -u2 "请先在 $env_file 中设置 DB_PASSWORD"
  exit 1
fi

if [[ -s "$HOME/.sdkman/bin/sdkman-init.sh" ]]; then
  set +u
  source "$HOME/.sdkman/bin/sdkman-init.sh"
  sdk env
  set -u
fi

exec "$script_dir/mvnw" spring-boot:run
