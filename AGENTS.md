# Lesson Web PoC 项目约定

## 项目边界

本项目验证“文件上传 → MinerU → LessonDocument v1 → Web 渲染”。后端为 Java 实现，并提供 MySQL 服务端文档库。当前不引入用户系统、消息队列或 RAG。

## 技术环境

- 前端：React、TypeScript、Vite、React Router；Node 与 pnpm 版本以 `frontend/package.json` 的 `volta` 字段为准。
- Java 后端：Java 17、Spring Boot 2.6.4、MySQL 8.0.33、MyBatis-Plus 3.5.4.1、MyBatis XML 与 Druid 1.2.8；项目内 Maven Wrapper。JDK 版本以 `backend-java/pom.xml` 为准，存在 `.sdkmanrc` 时使用 SDKMAN 激活。构建前核验工具链。本地运行必须使用 `backend-java/run-local.sh`，不得直接执行 `./mvnw spring-boot:run`；该脚本负责加载 `backend-java/.env`、校验 `MINERU_API_KEY` 与 `DB_PASSWORD` 并激活项目 Java 环境。MySQL 通过 `backend-java/docker-compose.yml` 单独运行，不容器化 Java 服务。
- 前端包统一使用 `frontend/.npmrc` 中的镜像源。
- 密钥只能从环境变量读取，禁止写入源码、日志、示例或提交记录。

## 架构约束

- 前端只消费 `LessonDocument v1`，不得引用 MinerU 原始字段。
- Provider 调用放在 `backend-java/.../parser/` 与 `client/`，结果转换放在 `adapter/`。
- API Controller 保持轻量，流程编排放在 Java `service/`。
- 前端默认代理到 Java 后端 `http://127.0.0.1:10012`，通过 `frontend/.env.local` 中的 `VITE_API_PROXY_TARGET` 配置。
- Java 后端使用 Flyway 管理文档库表结构，数据库保存解析任务、`LessonDocument v1` 和资源元数据；PDF、MinerU 原始结果及图片仍保存于 `DATA_DIR`。前端不得使用 IndexedDB 或 Blob 缓存，服务端数据库是文档内容的唯一真源。
- PoC 只支持 heading、paragraph、list、table、image、formula 六类内容块。
- 变更协议时必须同步后端模型、前端类型、契约测试和 `docs/LESSON_DOCUMENT_V1.md`。

## 项目阶段与兼容策略

- 当前项目尚未上线，没有存量用户、线上数据或对外公开 API；已有本地文档和数据库记录均为可丢弃的开发/测试数据，不构成历史兼容要求。优先保证当前方案清晰、正确和易维护，无需过度兼容，不为旧版本保留兼容层。
- 为完成本项目的开发、修复或重构，允许在确有必要时清空本项目数据库的全部数据并重新初始化；不得为了保留开发/测试数据而引入兼容分支、双读双写或数据迁移。清空前必须通过只读检查确认连接和目标数据库确属本项目，范围不包括其他数据库、共享数据库实例或项目外文件；执行后必须说明清空范围及不可恢复性。允许清空不等于每次任务都默认清空。
- 允许在任务范围内直接进行破坏性调整，包括修改内部 API、数据结构、环境变量和模块边界；变更后应一次性同步所有调用方、测试与文档。
- 不引入双写、双读、旧字段别名、废弃期、版本协商、历史数据迁移脚本、旧配置回退或新旧实现并存，除非用户明确要求。Flyway 仍用于管理当前表结构和初始化，不为可丢弃的开发/测试数据增加历史兼容逻辑。
- 被新实现替代的代码、类型、接口、配置和测试应直接删除，避免以“可能以后需要”为由保留死代码或兼容分支。
- 只支持项目已声明的运行环境和依赖版本；不主动兼容旧版浏览器、旧版 Node、其他包管理器或未声明的部署方式。
- MinerU 等外部 Provider 的真实接口差异应在后端 Parser/Client 或 Adapter 内集中处理，不得把 Provider 兼容逻辑泄漏到 `LessonDocument v1` 或前端。
- `LessonDocument v1` 是当前前后端之间的唯一协议，而不是已发布的历史承诺；需要调整时直接更新协议，并同步后端模型、前端类型、实现、测试和 `docs/LESSON_DOCUMENT_V1.md`，无需保留旧协议解析能力。

## 文档版面还原规则

- Web 与 DOCX 等输出渲染器应在 Provider 可提供的信息范围内保留源文档的阅读顺序、标题层级、水平对齐、内容分组和相对空间关系；版面信息必须先由 Adapter 转换为 `LessonDocument v1` 的通用语义，再由各输出端消费。
- 禁止根据文件名、任务 ID、标题文字、章节编号或某一份样例文档编写条件分支，也禁止使用只对单一样例校准的偏移量、宽高或选择器。
- Provider 坐标、字段差异和版面推断只允许存在于后端 Adapter；前端和 DOCX 导出器不得读取 MinerU 原始坐标或字段，不得为特定文件添加样式类。
- 布局必须适配不同页宽、内容长度和屏幕尺寸。优先传递左对齐、居中、右对齐等语义及归一化关系，由响应式 CSS 实现；不得把 PDF 页面绝对坐标直接固化为 Web 像素位置。
- Provider 缺少足够版面信息时使用一致、可解释的语义默认值，不通过文本特征伪造“精确还原”；新增版面能力必须使用至少一组与当前样例文本无关的测试验证通用性。

## 验证命令

```bash
cd backend-java && ./mvnw test
cd frontend && pnpm lint && pnpm test && pnpm build
```
