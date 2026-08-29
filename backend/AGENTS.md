# AGENTS.md

本文件定义 Python 后端的项目级代理协作规范。

适用范围：`backend/` 及其所有子目录。

当用户指令与本文件冲突时，以用户指令为准。

## 1. 项目定位

这是 Lesson Web PoC 的 Python 3.12 + FastAPI 后端，实现文件上传、MinerU 解析、`LessonDocument v1` 转换、资源读取、公式校验和 DOCX 导出。它与 `backend-java/` 共享 API 和协议契约；Python 默认端口为 `10011`，Java 默认端口为 `10012`，可同时运行。

代码主目录：

- `app/`：应用主代码；Provider 原始结构不得越过 Parser/Adapter 泄漏到 API 或前端
- `tests/`：测试
- `README.md`：Python 后端说明与本地运行文档
- `pyproject.toml`：依赖、构建与测试配置
- `Dockerfile`：容器镜像构建定义
- `docker-compose.yml`：统一容器编排配置

## 2. 运行环境

在本仓库内执行任务时，默认按以下环境理解：

- 操作系统：macOS
- Shell：`zsh`
- Python：`3.12`
- 依赖管理：`uv`
- 本地默认运行与测试方式：`uv`

若任务明显涉及跨平台兼容性、容器行为或 CI 环境，不得直接假设本地行为等同于 Linux 容器行为，需单独说明。

## 3. 工作方式

所有任务遵循以下状态流转：

`INIT -> ANALYSIS -> EXECUTION -> COMPLETED`

若遇到阻塞，可进入：

- `FAILED`：已尝试执行，但因错误未完成
- `ABORTED`：缺少关键条件、继续执行风险过高，主动停止

执行要求：

1. 先分析再执行，不要跳过上下文确认。
2. 优先做小步、可审查的修改。
3. 非必要不扩大改动面，不顺手重构无关代码。
4. 如果发现用户已有未提交改动，默认保留，不得覆盖或回滚。

## 4. 目录职责约定

### `app/`

- `main.py` 负责应用入口与 FastAPI 装配。
- `core/config.py` 负责配置读取与集中管理。
- `api/` 负责 HTTP 路由层，不在此处堆积复杂业务逻辑。
- `parsers/` 只负责调用文档解析 Provider 并返回供应商原始结构。
- `adapters/` 只负责把 Provider 结果转换为 `LessonDocument v1`。
- `services/` 负责核心业务逻辑与服务编排。
- `models/` 负责 Pydantic 协议和任务状态模型。
- `storage/` 负责本地任务与资源文件读写。
- `services/docx_export_service.py` 负责从前端 HTML 导出 DOCX；不得读取 MinerU 原始字段或坐标。

### `tests/`

- 测试文件命名以 `test_*.py` 为准。
- 新增或修改业务逻辑时，优先补充对应测试。
- 若无法补测，需在结果说明中明确原因和风险。

## 5. 开发约束

### Python 与依赖

- 使用 `uv` 执行安装、运行和测试，不要改用 `pip install` 作为默认路径。
- 依赖声明以 `pyproject.toml` 为准。
- `uv` 默认使用 `pyproject.toml` 中配置的阿里云 PyPI 源。
- 非用户明确要求，不主动升级大版本依赖。
- Docker 相关改动应保持 `Dockerfile` 与单一 `docker-compose.yml` 一致，不新增 dev/prod 分支配置。

### 代码改动

- 保持现有 API、Parser、Adapter、Model、Service 与 Storage 分层。
- 优先复用现有模块，不重复创建相近职责的新文件。
- 不为“看起来更完整”而引入额外抽象。
- 修改接口契约、环境变量或错误语义时，必须同步 Python 调用方、Java 等价实现、前端、测试与文档；不保留旧接口兼容层。

### 配置与密钥

- 严禁把真实密钥写入仓库文件。
- `.env.example` 只放示例占位值，不放真实凭据。

## 6. 常用命令

优先使用以下命令：

```bash
uv sync --dev
uv run --frozen pytest
uv run --frozen uvicorn app.main:app --reload --port 10011
docker compose up --build app
```

若仅运行单测，可使用：

```bash
uv run --frozen pytest tests/test_health.py
```

## 7. 文档与接口变更

出现以下情况时，应同步更新 `README.md` 或相关文档：

- 新增、删除或修改 HTTP 接口
- 修改环境变量
- 修改本地启动方式
- 修改结构目录或关键约定
- 修改 Docker 启动方式

若改动只涉及内部实现且外部行为不变，可不更新 README，但应确保命名与代码可读性足够清晰。协议变更还必须更新仓库根目录的 `docs/LESSON_DOCUMENT_V1.md`。

## 8. 测试与验证

默认验证策略：

1. 能跑测试时，优先运行最小必要测试。
2. 若修改影响接口或配置流程，优先补充或运行对应测试。
3. 若环境限制导致无法执行验证，必须明确说明未验证项。

## 9. 禁止事项

除非用户明确要求，否则不要执行以下操作：

- 删除大量文件或目录
- 修改系统级环境配置
- 安装或卸载全局工具
- 重写无关模块
- 提交真实密钥、证书或令牌
- 使用破坏性 git 命令回滚用户现有改动

## 10. 结果说明要求

完成任务后，应尽量说明：

- 改了什么
- 为什么这么改
- 如何验证
- 是否存在未覆盖风险
