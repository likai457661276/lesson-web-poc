# Lesson Web PoC

把 Word、PDF、PPT、Excel 或图片类教案交给 MinerU 解析，转换成稳定的 `LessonDocument v1`，再由 React 前端逐块渲染为 Web 教案。

## 目录

```text
backend/   FastAPI API、MinerU Parser、Adapter 与本地资源存储
frontend/  Vite + React + Router 上传与 LessonDocument Renderer
data/      本地解析任务和提取资源（不提交任务数据）
samples/   手工准备的验收样本
docs/      协议和测试清单
```

## 环境要求

- Volta 2.x（Node 24.14.0、pnpm 10.28.0 由 `frontend/package.json` 固定）
- Python 3.12 与 uv
- 可选：Docker / Docker Compose（单一 `docker-compose.yml`）

前端 npm registry 和后端 PyPI 均已配置为国内镜像。

## 启动

后端：

```bash
cd backend
cp .env.example .env
# 编辑 .env，填写 MINERU_API_KEY
uv sync --dev
uv run --frozen uvicorn app.main:app --reload --port 8000
```

容器运行使用唯一的 Compose 配置，不区分 dev/prod：

```bash
cd backend
cp .env.example .env
docker compose up --build app
```

前端：

```bash
cd frontend
pnpm install --frozen-lockfile
pnpm dev
```

打开 `http://localhost:5173`。Vite 会把 `/api` 代理到 `http://localhost:8000`。

## API

- `POST /api/documents/parse`：上传文件并创建解析任务
- `GET /api/documents/{jobId}`：查询任务状态和 LessonDocument
- `POST /api/formulas/validate`：使用 SymPy 校验 LaTeX 结构并返回规范化结果
- `POST /api/documents/export-docx`：把当前 Web 预览 HTML 转换为 DOCX 下载
- `GET /api/assets/{jobId}/{filename}`：读取提取图片
- `GET /api/health`：健康检查

任务数据写入 `data/jobs/{jobId}/`，服务重启后不恢复内存中的任务状态。
