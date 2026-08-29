# Lesson Web PoC

本项目验证“文件上传 → MinerU → `LessonDocument v1` → Web 渲染”闭环。支持 PDF、PPT/PPTX、XLS/XLSX 与常见图片；前端只消费稳定的 `LessonDocument v1`，不读取 MinerU 原始结果。

## 目录

```text
backend/      Python 3.12 + FastAPI 实现
backend-java/ Java 17 + Spring Boot 等价实现
frontend/  Vite + React + Router 上传与 LessonDocument Renderer
data/      本地解析任务和提取资源（不提交任务数据）
example/   已提交的手工验收样本
docs/      协议、验收清单与历史方案
```

## 环境要求

- Node 24.14.0、pnpm 10.28.0（由 `frontend/package.json` 的 Volta 配置固定）
- Python 3.12 与 uv
- JDK 17 与 SDKMAN（Java 后端通过项目内 Maven Wrapper 构建）
- 可选：Docker / Docker Compose（仅 Python 后端提供 `backend/docker-compose.yml`）

前端 npm registry 和后端 PyPI 均已配置为国内镜像。

## 启动

两套实现共用 API 与 `LessonDocument v1` 契约，可同时启动：Python 监听 `10011`，Java 监听 `10012`。

Python 后端：

```bash
cd backend
cp .env.example .env
# 编辑 .env，填写 MINERU_API_KEY
uv sync --dev
uv run --frozen uvicorn app.main:app --reload --port 10011
```

Java 后端（若存在 `backend-java/.sdkmanrc`，先执行 `sdk env`）：

```bash
cd backend-java
java -version
./mvnw -version
./mvnw test
./mvnw spring-boot:run
```

Python 容器运行使用唯一的 Compose 配置，不区分 dev/prod：

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

打开 `http://localhost:5173`。Vite 默认把 `/api` 代理到 Python 后端 `http://localhost:10011`。
如需连接 Java 后端，在 `frontend/.env.local` 中设置
`VITE_API_PROXY_TARGET=http://localhost:10012` 后重新启动前端。

## API

- `POST /api/documents/parse`：上传支持的文件并创建异步解析任务
- `GET /api/documents/{jobId}`：查询任务状态和 LessonDocument
- `POST /api/formulas/validate`：校验 LaTeX 结构并返回规范化结果（Python 使用 SymPy，Java 使用 Symja）
- `POST /api/documents/export-docx`：把当前 Web 预览 HTML 转换为 DOCX 下载
- `GET /api/assets/{jobId}/{filename}`：读取提取图片
- `GET /api/health`：健康检查

任务数据写入 `data/jobs/{jobId}/`。两套后端的任务状态均存于内存，服务重启后无法继续查询此前任务。

## 验证

```bash
cd backend && uv run --frozen pytest
cd ../backend-java && ./mvnw test
cd ../frontend && pnpm lint && pnpm build
```

协议定义见 [docs/LESSON_DOCUMENT_V1.md](docs/LESSON_DOCUMENT_V1.md)，验收样本与记录方式见 [docs/TEST_CASES.md](docs/TEST_CASES.md)。
