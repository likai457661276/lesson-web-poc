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

## 项目阶段与兼容策略

- 当前项目尚未上线，没有存量用户、线上数据或对外公开 API；默认优先保证当前方案清晰、正确和易维护，不为假设中的旧版本保留兼容层。
- 允许在任务范围内直接进行破坏性调整，包括修改内部 API、数据结构、环境变量和模块边界；变更后应一次性同步所有调用方、测试与文档。
- 不引入双写、双读、旧字段别名、废弃期、版本协商、迁移脚本、旧配置回退或新旧实现并存，除非用户明确要求，或仓库中已存在必须保留的真实存量数据。
- 被新实现替代的代码、类型、接口、配置和测试应直接删除，避免以“可能以后需要”为由保留死代码或兼容分支。
- 只支持项目已声明的运行环境和依赖版本；不主动兼容旧版浏览器、旧版 Node、旧版 Python、其他包管理器或未声明的部署方式。
- MinerU 等外部 Provider 的真实接口差异应在 `backend/app/parsers/` 或 `backend/app/adapters/` 内集中处理，不得把 Provider 兼容逻辑泄漏到 `LessonDocument v1` 或前端。
- `LessonDocument v1` 是当前前后端之间的唯一协议，而不是已发布的历史承诺；需要调整时直接更新协议，并同步后端模型、前端类型、实现、测试和 `docs/LESSON_DOCUMENT_V1.md`，无需保留旧协议解析能力。

## 文档版面还原规则

- Web 渲染应在 Provider 可提供的信息范围内保留源文档的阅读顺序、标题层级、水平对齐、内容分组和相对空间关系；版面信息必须先由 Adapter 转换为 `LessonDocument v1` 的通用语义，再由前端消费。
- 禁止根据文件名、任务 ID、标题文字、章节编号或某一份样例文档编写条件分支，也禁止使用只对单一样例校准的偏移量、宽高或选择器。
- Provider 坐标、字段差异和版面推断只允许存在于 `backend/app/adapters/`；前端不得读取 MinerU 原始坐标或字段，不得为特定文件添加样式类。
- 布局必须适配不同页宽、内容长度和屏幕尺寸。优先传递左对齐、居中、右对齐等语义及归一化关系，由响应式 CSS 实现；不得把 PDF 页面绝对坐标直接固化为 Web 像素位置。
- Provider 缺少足够版面信息时使用一致、可解释的语义默认值，不通过文本特征伪造“精确还原”；新增版面能力必须使用至少一组与当前样例文本无关的测试验证通用性。

## 验证命令

```bash
cd backend && uv run --frozen pytest
cd frontend && pnpm lint && pnpm build
```
