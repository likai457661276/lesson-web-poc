# Lesson Web PoC Java Backend

Java 17 + Spring Boot 后端。当前已实现上传落盘、内存任务状态、MinerU 解析链路、ZIP 提取、资源读取、`LessonDocument v1` 转换，以及基于 Jsoup + docx4j 的 DOCX 导出。它与 `../backend/` 共享 API 契约；Java 默认监听 `10012`，Python 默认监听 `10011`，可同时启动。

DOCX 导出支持语义标题、段落与行内样式、嵌套列表、`rowspan`/`colspan` 表格、data URL 图片、可编辑 OMML 公式及 Noto Sans SC 字体嵌入。

## 环境与运行

- JDK 17
- Maven 3.9.11（项目内 Maven Wrapper 自动下载）

若本地存在 `.sdkmanrc`，先执行 `sdk env` 激活 JDK。随后运行：

```bash
java -version
./mvnw -version
./mvnw test
./mvnw package
./mvnw spring-boot:run
```

服务默认监听 `http://localhost:10012`，健康检查为 `GET /api/health`。

## MinerU 配置

配置全部从环境变量读取：

- `MINERU_API_KEY`：必填。
- `MINERU_BASE_URL`：默认 `https://mineru.net/api/v4`。
- `MINERU_MODEL_VERSION`：默认 `vlm`。
- `MINERU_LANGUAGE`：默认 `ch`。
- `MINERU_POLL_INTERVAL_SECONDS`：默认 `3`。
- `MINERU_TIMEOUT_SECONDS`：默认 `600`。
- `DATA_DIR`：默认 `../data/jobs`。
- `MAX_FILE_SIZE_MB`：默认 `200`。

支持格式默认为 PDF、PPT/PPTX、XLS/XLSX 与常见图片。Java 服务使用 `application.yml` 中的 `lesson.allowed-extensions` 配置该列表；若需通过环境变量覆盖，使用 Spring 的规范名 `LESSON_ALLOWED_EXTENSIONS`。

启动示例：

```bash
export MINERU_API_KEY="your-token"
./mvnw spring-boot:run
```

## 实现说明

- 公式接口使用 Symja 解析与符号化简；当前 Golden Matrix 与 Python SymPy 基准行为一致。
- 当前嵌入完整字体文件；按文档字符生成字体子集属于后续体积优化，不影响字体可用性。
- 任务状态保存在内存中，服务重启后无法继续查询此前任务；任务文件仍保存于 `DATA_DIR`。
