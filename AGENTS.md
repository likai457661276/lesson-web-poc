# Lesson Web PoC 项目约定

## 项目边界

本项目只验证“文件上传 → MinerU → LessonDocument v1 → Web 渲染”。不引入数据库、用户系统、消息队列、RAG 或 Java 服务。

## 技术环境

- 前端：React、TypeScript、Vite、React Router；Node 与 pnpm 版本以 `frontend/package.json` 的 `volta` 字段为准。
- 后端：Python 3.12、FastAPI、uv；Python 包统一使用阿里云 PyPI 源。
- 本地后端开发和测试统一使用 `uv`；Docker 仅保留单一 `backend/docker-compose.yml`。
- 前端包统一使用 `frontend/.npmrc` 中的镜像源。
- 密钥只能从环境变量读取，禁止写入源码、日志、示例或提交记录。

## 架构约束

- 前端只消费 `LessonDocument v1`，不得引用 MinerU 原始字段。
- Provider 调用放在 `backend/app/parsers/`，结果转换放在 `backend/app/adapters/`。
- API 路由保持轻量，流程编排放在 `backend/app/services/`。
- PoC 只支持 heading、paragraph、list、table、image、formula 六类内容块。
- 变更协议时必须同步后端模型、前端类型和 `docs/LESSON_DOCUMENT_V1.md`。

## 验证命令

```bash
cd backend && uv run --frozen pytest
cd frontend && pnpm lint && pnpm build
```
