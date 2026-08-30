# LessonDocument v1

`LessonDocument v1` 是 Parser/Adapter 和前端 Renderer 之间的唯一稳定协议。JSON 字段统一使用 camelCase。

## 根对象

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `version` | `"1.0"` | 协议版本 |
| `documentId` | string | 本次解析任务 ID |
| `title` | string | 文档标题 |
| `metadata.sourceType` | string | 源文件扩展名 |
| `metadata.sourceFileName` | string | 原始文件名 |
| `blocks` | LessonBlock[] | 按阅读顺序排列的内容块 |

## 内容块

所有内容块都包含 `id` 和作为判别字段的 `type`。

```ts
type HeadingBlock = { id: string; type: 'heading'; level: 1 | 2 | 3 | 4 | 5 | 6; text: string; alignment: 'left' | 'center' | 'right' }
type ParagraphBlock = { id: string; type: 'paragraph'; text: string }
type ListBlock = { id: string; type: 'list'; items: string[]; ordered: boolean }
type TableBlock = { id: string; type: 'table'; html: string }
type ImageBlock = { id: string; type: 'image'; src: string; alt?: string | null }
type FormulaBlock = { id: string; type: 'formula'; latex: string }
```

表格保留 HTML，以支持 `rowspan`、`colspan`；前端渲染前必须消毒。表格内公式使用 `<span data-latex="..."></span>` 占位，Renderer 将其转换为可编辑 KaTeX 公式。图片只保存站内 URL，禁止在 JSON 中内嵌 Base64。公式内容为 KaTeX 可接受的 LaTeX；编辑保存时可调用 `/api/formulas/validate` 进行 SymPy 结构校验。

标题块的 `alignment` 表示源文档中的语义对齐方式。Adapter 应从 Provider 的版面几何信息通用推导；缺少可靠几何信息时使用 `left`，不得根据标题文字或文件名猜测。标题 `text` 可包含由可靠 OCR 行内几何恢复出的有效空白，Web Renderer 和 DOCX Exporter 通过保留空白还原标题内部的相对位置。各输出端使用语义对齐排版，不消费 Provider 坐标。

当前 Web Renderer 支持基础编辑：文档标题、标题块、段落、列表项、图片说明和表格单元格可通过编辑模式直接修改。解析完成的 `LessonDocument`、任务状态和资源元数据由 Java 后端保存到 MySQL；PDF、MinerU 原始结果及图片继续保存在服务端 `DATA_DIR`。前端不使用 IndexedDB 或 Blob 缓存，刷新、打开、编辑和删除均以服务端数据库为唯一真源，图片直接使用后端资源 URL。这些修改不会回写源 PDF。DOCX 导出必须从最新 `LessonDocument` 数据和服务端图片 URL 重建导出 HTML，不得以当前页面 DOM 作为持久化结果。服务端文档库仅由 Java 后端提供，Python 后端不实现对应接口。

## Java 服务端文档库

- `GET /api/lesson-documents` 返回未删除文档摘要，字段为 `id`、`title`、`sourceFileName`、`sourceType`、`blockCount`、`createdAt` 和 `updatedAt`，按 `updatedAt` 倒序。
- `GET /api/lesson-documents/{documentId}` 返回完整 v1 文档。
- `PUT /api/lesson-documents/{documentId}` 覆盖当前文档；路径 ID 必须与正文 `documentId` 一致，正文须通过 v1 校验。前端失焦后串行保存，最后写入生效，不包含离线同步或版本冲突协议。
- `DELETE /api/lesson-documents/{documentId}` 软删除并返回 `204`。删除后，文档、任务和资源接口均返回 `404`；数据库记录和文件永久保留，不提供恢复或定期物理清理。

当前文档库全局共享，不自动迁移浏览器旧数据或导入历史任务目录。

## 示例

```json
{
  "version": "1.0",
  "documentId": "job-001",
  "title": "勾股定理教学设计",
  "metadata": {
    "sourceType": "pdf",
    "sourceFileName": "勾股定理教学设计.pdf"
  },
  "blocks": [
    { "id": "block-0001", "type": "heading", "level": 1, "text": "教学目标", "alignment": "left" },
    { "id": "block-0002", "type": "paragraph", "text": "理解勾股定理。" },
    { "id": "block-0003", "type": "formula", "latex": "a^2+b^2=c^2" }
  ]
}
```
