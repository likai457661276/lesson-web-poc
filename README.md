# Lesson Web PoC

本项目验证“文件上传 → MinerU → `LessonDocument v1` → Web 渲染”闭环。仅支持 PDF 文件；前端只消费稳定的 `LessonDocument v1`，不读取 MinerU 原始结果。

## 目录

```text
backend/      Python 3.12 + FastAPI 实现
backend-java/ Java 17 + Spring Boot 2.6.4，实现解析与 MySQL 文档库
frontend/  Vite + React + Router 上传与 LessonDocument Renderer
data/      本地解析任务和提取资源（不提交任务数据）
example/   已提交的手工验收样本
docs/      协议、验收清单与历史方案
```

## 环境要求

- Node 24.14.0、pnpm 10.28.0（由 `frontend/package.json` 的 Volta 配置固定）
- Python 3.12 与 uv
- JDK 17 与 SDKMAN（Java 后端通过项目内 Maven Wrapper 构建）
- Docker / Docker Compose（Java 后端的 MySQL 独立运行于容器）

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

Java 后端先启动 MySQL，再运行本机 Java 服务：

```bash
cd backend-java
cp .env.example .env
# 编辑 .env，填写 MINERU_API_KEY 和 DB_PASSWORD
docker compose up -d mysql
java -version
./mvnw -version
./mvnw test
./run-local.sh
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

打开 `http://localhost:5173`。Vite 默认把 `/api` 代理到 Java 后端 `http://127.0.0.1:10012`。如需连接 Python 后端，在 `frontend/.env.local` 中显式设置 `VITE_API_PROXY_TARGET=http://127.0.0.1:10011` 后重新启动前端；Python 后端不提供服务端文档库接口。

## API

- `POST /api/documents/parse`：上传支持的文件并创建异步解析任务
- `GET /api/documents/{jobId}`：查询任务状态和 LessonDocument
- `POST /api/formulas/validate`：校验 LaTeX 结构并返回规范化结果（Python 使用 SymPy，Java 使用 Symja）
- `POST /api/documents/export-docx`：把当前 Web 预览 HTML 转换为 DOCX 下载
- `POST /api/documents/export-docx-batch`：仅 Java 支持，接收 `{"documents":[{"html":"<p>内容</p>","filename":"课程.docx"}]}`，返回包含各篇 DOCX 的 ZIP
- `GET /api/assets/{jobId}/{filename}`：读取提取图片
- `GET /api/health`：健康检查
- `GET /api/lesson-documents`：Java 后端文档摘要列表
- `GET /api/lesson-documents/{documentId}`：Java 后端读取完整文档
- `PUT /api/lesson-documents/{documentId}`：Java 后端保存编辑
- `DELETE /api/lesson-documents/{documentId}`：Java 后端软删除文档

任务文件写入 `data/jobs/{jobId}/`。Python 后端仍使用内存任务状态；Java 后端将任务、完整文档和资源元数据写入 MySQL，前端以 Java 服务端文档库为唯一真源。软删除仅设置数据库聚合根的删除标志，数据库明细和 `DATA_DIR/{jobId}` 文件不会物理删除。

文档列表支持勾选、全选后批量下载 DOCX（ZIP）。下载前等待已有编辑保存完成，再读取服务端最新文档，沿用单篇下载的 HTML 与图片内嵌流程，无需切换当前预览。每批支持 1–20 篇，HTML 合计最多 2500 万字符，生成的 DOCX 合计最多 100 MB；重名文件自动追加编号，任一文档失败则整批报错，可保留选择重试。批量接口不修改 LessonDocument v1 协议，Python 端不实现该接口。

## 验证

```bash
cd backend && uv run --frozen pytest
cd ../backend-java && ./mvnw test
cd ../frontend && pnpm lint && pnpm build
```

协议定义见 [docs/LESSON_DOCUMENT_V1.md](docs/LESSON_DOCUMENT_V1.md)，验收样本与记录方式见 [docs/TEST_CASES.md](docs/TEST_CASES.md)。
