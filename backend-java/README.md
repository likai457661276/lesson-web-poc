# Lesson Web PoC Java Backend

Java 17 + Spring Boot 2.6.4 后端。当前已实现上传落盘、MinerU 解析链路、ZIP 提取、资源读取、`LessonDocument v1` 转换、MySQL 服务端文档库，以及基于 Jsoup + docx4j 的 DOCX 导出。它与 `../backend/` 共享解析 API 契约；Java 默认监听 `10012`，Python 默认监听 `10011`，可同时启动。

DOCX 导出支持语义标题、段落与行内样式、嵌套列表、`rowspan`/`colspan` 表格、data URL 图片、可编辑 OMML 公式及 Noto Sans SC 字体嵌入。

## 环境与运行

- JDK 17
- Maven 3.9.11（项目内 Maven Wrapper 自动下载）
- Docker / Docker Compose（只用于运行 MySQL 8.0.33）

复制配置并填写本机密钥和数据库密码：

```bash
cp .env.example .env
# 编辑 .env：设置 MINERU_API_KEY、DB_PASSWORD
docker compose up -d mysql
java -version
./mvnw -version
./mvnw test
./run-local.sh
```

`run-local.sh` 会加载 `.env`、检查必填变量，并在存在 `.sdkmanrc` 时通过 SDKMAN 激活项目 JDK。服务默认监听 `http://localhost:10012`，健康检查为 `GET /api/health`。

`./mvnw test` 需要 Docker 正常运行，Testcontainers 1.21.4 会启动临时 MySQL 8.0.33，不读取或修改本机应用数据库。

## 环境变量

- `MINERU_API_KEY`：必填。
- `MINERU_BASE_URL`：默认 `https://mineru.net/api/v4`。
- `MINERU_MODEL_VERSION`：默认 `vlm`。
- `MINERU_LANGUAGE`：默认 `ch`。
- `MINERU_POLL_INTERVAL_SECONDS`：默认 `3`。
- `MINERU_TIMEOUT_SECONDS`：默认 `600`。
- `DATA_DIR`：默认 `../data/jobs`。
- `MAX_FILE_SIZE_MB`：默认 `200`。
- `FRONTEND_ORIGINS`：默认允许 `http://localhost:5173` 与 `http://127.0.0.1:5173`，用逗号分隔。
- `DB_HOST`：默认 `127.0.0.1`。
- `DB_PORT`：默认 `3306`，同时用于 Compose 端口映射。
- `DB_NAME`：默认 `lesson_web`。
- `DB_USERNAME`：默认 `lesson_web`。
- `DB_PASSWORD`：必填，不得提交真实密码。

仅支持 PDF 输入文件，并在保存前校验 PDF 文件头。

MySQL 使用命名 volume 保存数据，Compose 只运行数据库，不包含 Java 服务；默认仅绑定 `127.0.0.1:3306`，root 密码由镜像随机生成，应用账号使用本机 `DB_PASSWORD`。停止服务可执行 `docker compose stop mysql`；不要使用 `docker compose down -v`，除非明确要物理删除数据库数据。首次初始化后，修改 `.env` 中的账号或密码不会自动修改 volume 内已有数据库账号。

## 持久化与文档库

- JDBC 驱动/协议：MySQL Connector-J 8.0.33，`jdbc:mysql`。
- 持久化栈：MyBatis-Plus 3.5.4.1 + MyBatis XML + Druid 1.2.8。
- Flyway 创建 `lesson_document_conversion`、`lesson_document_content`、`lesson_document_asset` 三张表。
- `LessonDocument v1` 以 `LONGTEXT` 保存；PDF、MinerU 原始结果及图片保留在 `DATA_DIR`。
- 服务启动时，遗留的待处理/处理中任务会标记为失败，错误码为 `SERVICE_RESTARTED`，不会自动重试 MinerU。
- 删除文档只把聚合根 `del_flag` 更新为 `2`；列表、读取、编辑、任务和资源接口均隐藏已删除文档，但数据库记录和文件永久保留。
- 当前没有用户系统，文档库全局共享；不自动导入浏览器旧数据或 `DATA_DIR` 历史目录，也不提供恢复或物理清理接口。
- 文档读取/保存返回 `ETag`；保存必须提交 `If-Match`，缺失返回 428，版本冲突返回 409。通过原内容二进制原子比较保护并发更新，不需要历史兼容或数据迁移。
- 解析队列满载时返回 `503 / PARSE_QUEUE_FULL`，任务标记失败并移除尚未处理的上传文件；创建数据库任务失败也会清理该次上传。
- Flyway 使用直连 JDBC 执行迁移，业务访问仍使用 Druid；这是为了规避 Druid 1.2.8 将 Flyway 对受限 `performance_schema` 的可选探测判定为致命连接错误。

## 实现说明

- 公式接口使用 Symja 解析与符号化简；当前 Golden Matrix 与 Python SymPy 基准行为一致。
- DOCX 字体由纯 Java 的 FontBox 按文档字符生成 Regular/Bold 子集，并保留自动编号字符；字体资源为预置 Noto Sans SC 静态 TTF，构建和运行均不依赖 Python。子集省略未用字形，Word 中新增字符可能使用本机回退字体。字体来源、校验值和许可见 `src/main/resources/fonts/README.md`。
- DOCX 表格正文顶端对齐、表头居中，按整列内容量分配宽度，允许长行跨页；不使用样例特判，不改变单元格之间的行对应关系。
- MinerU 的 TLS、连接、超时和传输错误在客户端边界归类为 `MINERU_PARSE_FAILED`，错误消息包含失败阶段，不包含签名 URL。
- DOCX 表格在展开前校验：最多 2000 行、100 列、10000 个展开单元格，超限返回 `413 / TABLE_TOO_LARGE`，非法跨度返回 `422 / INVALID_TABLE_SPAN`。
