# Java 后端项目规则

适用于 `backend-java/` 及其子目录。

## 本地启动

- 本地运行 Java 服务必须使用 `./run-local.sh`，禁止直接执行 `./mvnw spring-boot:run`。
- `run-local.sh` 会加载同目录 `.env`、校验 `MINERU_API_KEY`，并通过 SDKMAN 激活项目 Java 环境；不得绕过这些步骤。
- `.env` 仅用于本机配置，严禁提交、输出或写入示例文件。

## 工具链与验证

- Java 版本以 `pom.xml` 为准；构建前运行 `java -version` 与 `./mvnw -version` 核验工具链。
- 使用 Maven Wrapper 执行构建和测试：`./mvnw test`。
