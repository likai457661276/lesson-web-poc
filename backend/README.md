# Lesson Web PoC Backend

FastAPI 服务负责本地文件保存、MinerU 精准解析 API 调用、结果资源归档和 `LessonDocument v1` 标准化。

## 配置

```bash
cp .env.example .env
```

必须填写 `MINERU_API_KEY`。该值由 `app/core/config.py` 从 `.env` 读取，不应提交真实密钥。

主要可调项：

- `MINERU_MODEL_VERSION=vlm`
- `MINERU_TIMEOUT_SECONDS=600`
- `MAX_FILE_SIZE_MB=200`
- `DATA_DIR=../data/jobs`

## 本地运行

本地开发默认使用 `uv`：

```bash
uv sync --dev
uv run --frozen uvicorn app.main:app --reload --port 8000
```

测试：

```bash
uv run --frozen pytest
```

## Docker 运行

Compose 不区分开发和生产环境，仅保留单一配置：

```bash
docker compose up --build app
```

容器使用镜像内的冻结依赖和 Uvicorn 启动命令；本地热更新与测试仍使用 `uv`。

## MinerU 调用流程

服务使用 MinerU v4 精准解析接口：申请批量签名上传地址、PUT 上传、按 `batch_id` 轮询、下载结果 ZIP，并读取 `*_content_list.json`。Parser 保留供应商原始结构，Adapter 才负责生成 LessonDocument。
