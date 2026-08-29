# Lesson Web PoC Backend

FastAPI 服务负责本地文件保存、MinerU 精准解析 API 调用、结果资源归档和 `LessonDocument v1` 标准化。它与 `../backend-java/` 实现同一 API 契约；Python 默认监听 `10011`，Java 默认监听 `10012`，可同时启动。

## 配置

```bash
cp .env.example .env
```

必须填写 `MINERU_API_KEY`。该值由 `app/core/config.py` 从 `.env` 读取，不应提交真实密钥。

仅支持 PDF 输入文件，并在保存前校验 PDF 文件头。主要配置项：

- `FRONTEND_ORIGINS=http://localhost:5173,http://127.0.0.1:5173`
- `DATA_DIR=../data/jobs`
- `MAX_FILE_SIZE_MB=200`
- `MINERU_BASE_URL=https://mineru.net/api/v4`
- `MINERU_MODEL_VERSION=vlm`
- `MINERU_LANGUAGE=ch`
- `MINERU_POLL_INTERVAL_SECONDS=3`
- `MINERU_TIMEOUT_SECONDS=600`

## 本地运行

本地开发默认使用 `uv`：

```bash
uv sync --dev
uv run --frozen uvicorn app.main:app --reload --port 10011
```

测试：

```bash
uv run --frozen pytest
```

## Docker 运行

Compose 不区分开发和生产环境，仅保留单一配置：

```bash
docker compose up --build app
```

容器使用镜像内的冻结依赖和 Uvicorn 启动命令；本地热更新与测试仍使用 `uv`。

## MinerU 调用流程

服务使用 MinerU v4 精准解析接口：申请批量签名上传地址、PUT 上传、按 `batch_id` 轮询、下载结果 ZIP，并读取 `*_content_list.json`。Parser 保留供应商原始结构，Adapter 才负责生成 LessonDocument。

公式编辑可调用 `POST /api/formulas/validate`，使用 SymPy 检查 LaTeX 是否能转换为符号表达式。该检查不等价于 OCR 与原图一致性验证，最终仍需人工对照原页。

## DOCX 导出

`POST /api/documents/export-docx` 接收 JSON：

```json
{
  "html": "<h1>教案标题</h1><p>正文</p>",
  "filename": "教案.docx"
}
```

接口返回可直接下载的 DOCX。支持常见标题、源文档标题对齐与有效空白、段落、行内强调、列表、表格、公式 LaTeX 文本和 data URL 图片；脚本及嵌入对象会被忽略，远程图片不会由后端主动抓取。

导出文件默认使用并嵌入 Noto Sans SC Regular/Bold 字体子集，不依赖接收方电脑预装中文字体。字体源文件采用 SIL Open Font License 1.1，许可证与来源说明位于 `app/assets/fonts/`。
