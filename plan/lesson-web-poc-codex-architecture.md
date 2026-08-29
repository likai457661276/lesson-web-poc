# Lesson Web PoC 项目架构技术方案

> 历史架构规划文档，保留用于追溯早期方案；目录、技术边界和阶段性结论可能已过时。当前项目规范以仓库根目录 `README.md`、`AGENTS.md` 与 `docs/LESSON_DOCUMENT_V1.md` 为准。

> 目标：使用 MinerU 将 Word / PDF / 图片类教案解析为统一的 `LessonDocument JSON`，再由前端渲染为 Web 教案。  
> 当前阶段仅验证完整技术链路，不接数据库、不接第三方 Java 服务、不接阿里云 Document Mind。

---

# 1. 项目目标

第一阶段只验证以下完整闭环：

```text
DOCX / PDF / Image
        ↓
      MinerU
        ↓
   MinerU Result
        ↓
 MinerU Adapter
        ↓
 LessonDocument v1
        ↓
  Frontend Renderer
        ↓
     Web 教案
```

核心验证点：

1. Word / PDF / 图片是否可以稳定解析。
2. 普通文字是否完整。
3. 标题层级是否可识别。
4. 表格是否能正确保留。
5. 表格内部图片是否能处理。
6. 普通图片是否能正确提取并显示。
7. 数学公式是否能转为可渲染格式。
8. 是否可以统一转换成稳定的 `LessonDocument v1`。
9. 前端是否可以完全基于 `LessonDocument` 渲染 Web 教案。

---

# 2. 当前阶段明确不做

PoC 阶段禁止过度扩展。

暂不实现：

- PostgreSQL
- MySQL
- Redis
- 向量数据库
- RAG
- Java 服务对接
- 用户体系
- 权限系统
- 多租户
- 教案 AI 分析
- AI 自动修改
- 阿里云 Document Mind
- EasyDoc
- 复杂工作流
- 消息队列
- Docker 集群
- 微服务拆分

第一阶段只验证：

> 文件上传 → MinerU 解析 → 标准化 JSON → 前端渲染

---

# 3. 推荐技术栈

## 3.1 后端

```text
Python 3.12
FastAPI
Pydantic v2
Uvicorn
MinerU
```

职责：

- 文件上传
- MinerU 调用
- MinerU 结果读取
- 图片资源管理
- `LessonDocument` 标准化
- API 输出

## 3.2 前端

```text
Vite
React
TypeScript
```

建议第一阶段尽量减少依赖。

必要能力：

- 文件上传
- 解析状态展示
- LessonDocument Renderer
- 图片显示
- HTML Table 渲染
- 数学公式渲染

公式渲染推荐：

```text
KaTeX
```

---

# 4. 推荐项目名称

```text
lesson-web-poc
```

目录：

```text
lesson-web-poc/
├── backend/
├── frontend/
├── data/
├── samples/
├── docs/
├── README.md
└── AGENTS.md
```

---

# 5. 整体项目结构

```text
lesson-web-poc/
│
├── backend/
│   ├── app/
│   │   ├── main.py
│   │   │
│   │   ├── api/
│   │   │   ├── documents.py
│   │   │   └── health.py
│   │   │
│   │   ├── parsers/
│   │   │   ├── base.py
│   │   │   └── mineru_parser.py
│   │   │
│   │   ├── adapters/
│   │   │   └── mineru_adapter.py
│   │   │
│   │   ├── models/
│   │   │   ├── lesson_document.py
│   │   │   └── parse_job.py
│   │   │
│   │   ├── services/
│   │   │   ├── document_service.py
│   │   │   └── asset_service.py
│   │   │
│   │   ├── storage/
│   │   │   └── local_storage.py
│   │   │
│   │   └── core/
│   │       ├── config.py
│   │       └── exceptions.py
│   │
│   ├── tests/
│   ├── pyproject.toml
│   └── README.md
│
├── frontend/
│   ├── src/
│   │   ├── api/
│   │   │   └── documents.ts
│   │   ├── components/
│   │   │   ├── DocumentUploader.tsx
│   │   │   └── LessonRenderer/
│   │   │       ├── LessonRenderer.tsx
│   │   │       ├── HeadingBlock.tsx
│   │   │       ├── ParagraphBlock.tsx
│   │   │       ├── ListBlock.tsx
│   │   │       ├── TableBlock.tsx
│   │   │       ├── ImageBlock.tsx
│   │   │       └── FormulaBlock.tsx
│   │   ├── pages/
│   │   │   └── HomePage.tsx
│   │   ├── types/
│   │   │   └── lesson-document.ts
│   │   └── App.tsx
│   ├── package.json
│   └── README.md
│
├── data/
│   └── jobs/
├── samples/
│   ├── docx/
│   ├── pdf/
│   └── images/
└── docs/
    ├── LESSON_DOCUMENT_V1.md
    └── TEST_CASES.md
```

---

# 6. 核心架构原则

## 6.1 前端禁止依赖 MinerU 原始结构

禁止：

```text
MinerU JSON
    ↓
Frontend
```

必须：

```text
MinerU JSON
    ↓
MinerUAdapter
    ↓
LessonDocument v1
    ↓
Frontend
```

MinerU 只是当前解析供应商。

未来可能替换为：

```text
MinerU
Aliyun Document Mind
EasyDoc
其他 Parser
```

但前端始终只认识：

```text
LessonDocument v1
```

---

# 7. Parser 抽象

定义统一解析接口：

```python
from abc import ABC, abstractmethod
from pathlib import Path

class DocumentParser(ABC):

    @abstractmethod
    async def parse(self, file_path: Path) -> dict:
        pass
```

当前实现：

```text
MinerUDocumentParser
```

未来可增加：

```text
AliyunDocumentParser
EasyDocDocumentParser
```

---

# 8. LessonDocument v1

这是整个项目最重要的数据协议。

建议第一版只支持 6 类 Block：

```text
heading
paragraph
list
table
image
formula
```

不要第一阶段设计过多类型。

---

# 9. LessonDocument v1 示例

```json
{
  "version": "1.0",
  "documentId": "test-001",
  "title": "勾股定理教学设计",
  "metadata": {
    "sourceType": "docx",
    "sourceFileName": "勾股定理教学设计.docx"
  },
  "blocks": [
    {
      "id": "block-001",
      "type": "heading",
      "level": 1,
      "text": "教学目标"
    },
    {
      "id": "block-002",
      "type": "paragraph",
      "text": "理解勾股定理，并能够解决简单实际问题。"
    },
    {
      "id": "block-003",
      "type": "formula",
      "latex": "a^2+b^2=c^2"
    },
    {
      "id": "block-004",
      "type": "image",
      "src": "/api/assets/test-001/image-001.png",
      "alt": "勾股定理示意图"
    },
    {
      "id": "block-005",
      "type": "table",
      "html": "<table><tr><th>教师活动</th><th>学生活动</th></tr></table>"
    }
  ]
}
```

---

# 10. Pydantic 数据模型

建议使用 discriminated union。

示意：

```python
class HeadingBlock(BaseModel):
    id: str
    type: Literal["heading"]
    level: int
    text: str


class ParagraphBlock(BaseModel):
    id: str
    type: Literal["paragraph"]
    text: str


class ImageBlock(BaseModel):
    id: str
    type: Literal["image"]
    src: str
    alt: str | None = None


class FormulaBlock(BaseModel):
    id: str
    type: Literal["formula"]
    latex: str


class TableBlock(BaseModel):
    id: str
    type: Literal["table"]
    html: str
```

最终：

```python
LessonBlock = Annotated[
    HeadingBlock
    | ParagraphBlock
    | ListBlock
    | TableBlock
    | ImageBlock
    | FormulaBlock,
    Field(discriminator="type")
]
```

---

# 11. 表格策略

PoC 阶段不建议重新设计复杂 Table Schema。

优先保留：

```json
{
  "type": "table",
  "html": "<table>...</table>"
}
```

原因：

1. MinerU 本身可以产生表格结构。
2. HTML 对 rowspan / colspan 支持成熟。
3. 可以降低第一阶段前端 Renderer 难度。
4. 可以保留复杂合并单元格。

未来如果 Agent 需要精细编辑表格，再升级为结构化 Table Model。

---

# 12. 图片策略

图片文件不编码进 JSON。

禁止：

```text
base64 image
```

使用：

```text
图片文件
+
URL
```

例如：

```json
{
  "type": "image",
  "src": "/api/assets/job-001/image-001.png"
}
```

本地结构：

```text
data/
└── jobs/
    └── job-001/
        ├── source.docx
        ├── mineru/
        ├── assets/
        │   ├── image-001.png
        │   └── image-002.jpg
        └── lesson-document.json
```

`lesson-document.json` 仅用于 PoC 调试，不视为数据库。

---

# 13. API 设计

第一阶段建议只实现 3 个 API。

## 13.1 上传并解析

```http
POST /api/documents/parse
Content-Type: multipart/form-data
```

参数：

```text
file
```

返回：

```json
{
  "jobId": "job-001",
  "status": "processing"
}
```

## 13.2 查询状态

```http
GET /api/documents/{jobId}
```

处理中：

```json
{
  "jobId": "job-001",
  "status": "processing"
}
```

成功：

```json
{
  "jobId": "job-001",
  "status": "completed",
  "document": {
    "version": "1.0",
    "blocks": []
  }
}
```

失败：

```json
{
  "jobId": "job-001",
  "status": "failed",
  "error": {
    "code": "MINERU_PARSE_FAILED",
    "message": "..."
  }
}
```

## 13.3 资源访问

```http
GET /api/assets/{jobId}/{filename}
```

用于：

- 图片
- MinerU 提取资源

---

# 14. Job 状态

第一阶段不使用数据库。

状态可以维护在内存：

```python
jobs: dict[str, ParseJob] = {}
```

同时结果文件写本地：

```text
data/jobs/{jobId}/
```

状态：

```text
pending
processing
completed
failed
```

PoC 不要求服务重启后恢复任务。

---

# 15. 后端处理流程

```text
POST /api/documents/parse
        ↓
生成 jobId
        ↓
保存 source 文件
        ↓
调用 MinerU
        ↓
获得 MinerU 输出
        ↓
MinerUAdapter
        ↓
LessonDocument
        ↓
Pydantic 校验
        ↓
保存 lesson-document.json
        ↓
status = completed
```

---

# 16. MinerUAdapter 职责

`mineru_adapter.py` 是当前阶段核心模块。

只负责：

```text
MinerU Result
        ↓
LessonDocument v1
```

不要：

- 调 API
- 操作数据库
- 操作前端
- 做业务判断

建议实现：

```python
class MinerUAdapter:

    def convert(self, mineru_result: dict) -> LessonDocument:
        ...
```

映射关系：

```text
MinerU title       → heading
MinerU text        → paragraph
MinerU list        → list
MinerU table       → table
MinerU image       → image
MinerU formula     → formula
```

---

# 17. 前端 Renderer

前端不得直接拼整份 HTML 字符串。

使用 Block Renderer：

```tsx
switch (block.type) {
  case "heading":
    return <HeadingBlock block={block} />

  case "paragraph":
    return <ParagraphBlock block={block} />

  case "table":
    return <TableBlock block={block} />

  case "image":
    return <ImageBlock block={block} />

  case "formula":
    return <FormulaBlock block={block} />
}
```

这样未来增加：

```text
animation
teaching_step
ai_comment
```

不会破坏现有 Renderer。

---

# 18. 前端页面第一版

只需要一个页面：

```text
┌─────────────────────────────────┐
│ 教案 Web 化 PoC                 │
├─────────────────────────────────┤
│                                 │
│        上传 Word / PDF          │
│                                 │
├─────────────────────────────────┤
│ 解析状态：处理中 / 完成 / 失败   │
├─────────────────────────────────┤
│                                 │
│          Web 教案预览            │
│                                 │
│ 教学目标                         │
│ ……                              │
│                                 │
│ [图片]                           │
│                                 │
│ [表格]                           │
│                                 │
│ a²+b²=c²                        │
│                                 │
└─────────────────────────────────┘
```

第一阶段不要做：

- 编辑器
- 富文本
- 拖拽
- 在线修改
- 多 Tab
- 复杂管理后台

---

# 19. 测试数据

准备 10～20 个真实样本。

目录：

```text
samples/
├── docx/
├── pdf/
└── images/
```

至少覆盖：

1. 纯文字 Word
2. Word + 普通表格
3. Word + 图片
4. Word + 表格内部图片
5. Word + 数学公式
6. PDF 电子文档
7. PDF + 表格
8. 扫描 PDF
9. 手机拍照
10. 复杂综合教案

---

# 20. PoC 验收标准

第一阶段成功的判断标准不是像素级还原。

目标：

> 保留教学内容、逻辑顺序和主要结构。

## 文本

- 无明显大段缺失
- 阅读顺序正确

## 标题

- 教学目标、教学重点、教学过程等能够基本识别

## 表格

- 行列关系基本正确
- rowspan / colspan 不严重错乱

## 图片

- 图片能够提取
- 图片能够在对应内容附近展示

## 公式

- 数学公式可以通过 KaTeX 渲染
- 关键公式不丢失

## 前端

- 不依赖 MinerU 原始数据结构
- 完全基于 LessonDocument v1 渲染

---

# 21. 错误处理

第一阶段至少定义：

```text
UNSUPPORTED_FILE
FILE_TOO_LARGE
MINERU_PARSE_FAILED
ADAPTER_CONVERT_FAILED
LESSON_DOCUMENT_INVALID
ASSET_NOT_FOUND
```

API 返回统一错误：

```json
{
  "error": {
    "code": "MINERU_PARSE_FAILED",
    "message": "Document parsing failed"
  }
}
```

---

# 22. 日志

日志至少记录：

```text
jobId
fileName
fileType
fileSize
parseStartedAt
parseFinishedAt
parseDuration
blockCount
imageCount
tableCount
formulaCount
status
errorCode
```

目的：

后续比较 MinerU 与阿里 Document Mind 时，可以直接复用。

---

# 23. 为阿里云预留 Provider 层

虽然第一阶段只用 MinerU，但目录必须保留 Parser 抽象。

未来：

```text
parsers/
├── base.py
├── mineru_parser.py
└── aliyun_parser.py
```

调用层只认：

```python
DocumentParser
```

不要在 `document_service.py` 中大量出现 MinerU 专属逻辑。

---

# 24. 第二阶段替换关系

当前：

```text
MinerU
   ↓
MinerUAdapter
   ↓
LessonDocument
```

未来：

```text
Aliyun Document Mind
        ↓
AliyunAdapter
        ↓
LessonDocument
```

前端：

```text
无需修改
```

---

# 25. 正式接 Java 时的架构

PoC：

```text
Parser
  ↓
LessonDocument
  ↓
Frontend
```

正式：

```text
Parser
  ↓
LessonDocument
  ↓
Java API
  ↓
Java 持久化
  ↓
Frontend
```

因此 PoC 阶段 `LessonDocument v1` 必须作为稳定协议设计。

---

# 26. Codex 开发顺序

Codex 必须按以下顺序执行。

## Task 1：初始化项目

创建：

```text
backend/
frontend/
data/
samples/
docs/
```

建立基础 README。

## Task 2：定义 LessonDocument v1

先完成：

```text
backend/app/models/lesson_document.py
frontend/src/types/lesson-document.ts
docs/LESSON_DOCUMENT_V1.md
```

前后端结构必须一致。

完成后写单元测试。

## Task 3：实现 Parser 抽象

创建：

```text
parsers/base.py
```

定义统一 `DocumentParser`。

## Task 4：接入 MinerU

实现：

```text
parsers/mineru_parser.py
```

输入：

```text
Path
```

输出：

```text
MinerU raw result
```

此阶段禁止直接生成前端 JSON。

## Task 5：实现 MinerUAdapter

创建：

```text
adapters/mineru_adapter.py
```

实现：

```text
MinerU Result
→ LessonDocument v1
```

必须覆盖：

```text
heading
paragraph
list
table
image
formula
```

## Task 6：实现本地资源管理

创建：

```text
services/asset_service.py
storage/local_storage.py
```

资源目录：

```text
data/jobs/{jobId}/
```

## Task 7：实现 DocumentService

负责：

```text
上传
→ Parser
→ Adapter
→ Pydantic Validate
→ 输出 LessonDocument
```

## Task 8：实现 API

实现：

```text
POST /api/documents/parse
GET /api/documents/{jobId}
GET /api/assets/{jobId}/{filename}
GET /api/health
```

## Task 9：实现前端上传页面

只做：

```text
选择文件
上传
显示处理状态
```

## Task 10：实现 LessonRenderer

按照 Block 类型渲染：

```text
heading
paragraph
list
table
image
formula
```

## Task 11：准备测试样本

至少准备：

```text
10 个不同复杂度样本
```

并记录测试结果。

## Task 12：端到端测试

验证：

```text
DOCX
 ↓
MinerU
 ↓
LessonDocument
 ↓
Frontend
 ↓
Web 教案
```

---

# 27. Codex 开发约束

必须遵守：

1. 不增加数据库。
2. 不增加 Redis。
3. 不增加消息队列。
4. 不接 Java。
5. 不接阿里。
6. 不做 RAG。
7. 不做 AI 分析。
8. 不重构 MinerU。
9. 不让前端依赖 MinerU 数据结构。
10. 每个 Task 完成后运行相关测试。
11. 不为了未来需求提前建立复杂抽象。
12. LessonDocument v1 是第一阶段唯一稳定协议。

---

# 28. 第一阶段完成后的结果

最终应该可以运行：

```text
打开 Web 页面
       ↓
上传 lesson.docx
       ↓
显示“正在解析”
       ↓
MinerU 完成
       ↓
LessonDocument v1
       ↓
浏览器显示 Web 教案
```

并正确展示：

```text
文字
标题
列表
表格
图片
数学公式
```

---

# 29. 后续阶段

只有第一阶段验收通过后再进行。

## Phase 2

MinerU 与阿里 Document Mind A/B 测试。

```text
MinerU
vs
Aliyun Document Mind
```

比较：

- 文本准确率
- 表格
- 图片
- 公式
- OCR
- 解析速度
- 成本
- 稳定性

## Phase 3

对接第三方 Java 服务：

```text
LessonDocument
      ↓
Java API
      ↓
第三方持久化
```

前端改为：

```text
Java API
  ↓
LessonDocument
  ↓
Renderer
```

---

# 30. 最终原则

整个 PoC 最重要的不是 MinerU。

真正需要稳定的是：

```text
LessonDocument v1
```

正确架构：

```text
Document Parser
       ↓
   Adapter
       ↓
LessonDocument
       ↓
   Renderer
```

而不是：

```text
MinerU
  ↓
Frontend
```

只要这一层隔离做好，后续无论换阿里云、EasyDoc 还是其他文档解析服务，前端和业务系统都可以保持稳定。
