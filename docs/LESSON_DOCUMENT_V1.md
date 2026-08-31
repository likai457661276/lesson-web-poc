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
type ParagraphBlock = { id: string; type: 'paragraph'; text: string; reviewNote?: string | null }
type ListBlock = { id: string; type: 'list'; items: string[]; ordered: boolean }
type TableBlock = { id: string; type: 'table'; html: string }
type ImageBlock = { id: string; type: 'image'; src: string; alt?: string | null }
type FormulaBlock = { id: string; type: 'formula'; latex: string }
```

表格保留 HTML，以支持 `rowspan`、`colspan`；前端渲染前必须消毒。表格内公式使用 `<span data-latex="..."></span>` 占位，Renderer 将其转换为 KaTeX 公式，仅编辑模式允许修改。图片只保存站内 URL，禁止在 JSON 中内嵌 Base64。公式内容为 KaTeX 可接受的 LaTeX；编辑保存时可调用 `/api/formulas/validate` 进行结构校验（Symja）。

表格标题使用表格 HTML 内的 `<caption>` 保留，支持 Web 编辑与 DOCX 导出，不从标题文字推断课时编号或层级。DOCX 按单元格内原始节点顺序输出文字、图片及公式，不将图片集中追加到末尾。

Adapter 可从 Provider 的行内文字及版面信息恢复多栏内容：至少三行具有一致的列数和持续的栏间空隙，且行内文字拼接后必须与原段落完全一致（忽略空白）。确认后转换为一个按列阅读的通用 HTML 表格；坐标、Provider 字段不进入协议。已检测到多栏但文字无法完整核对时，保留原文并通过可选 `ParagraphBlock.reviewNote` 提示原页码和人工复核原因。Web 将提示放在可编辑正文之外，DOCX 同样保留提示。缺少可靠版面信息时不猜测分栏。

DOCX 支持中文公式文本和中文上下标，例如 `A_{底}` 与 `V_{\text{总体}}`。若公式无法转换为 Word 可编辑公式，单篇和批量导出均返回 `422 / DOCX_FORMULA_UNSUPPORTED`，前端显示失败，不再静默输出 LaTeX 源码。KaTeX 可渲染不代表当前 DOCX 转换器支持其全部命令。

DOCX 导出器限制单个表格最多 2000 行、100 列、10000 个展开单元格；`rowspan` 和 `colspan` 分别不得超过行数和列数上限。超限返回 `413 / TABLE_TOO_LARGE`，非法或超过有符号 64 位整数范围的跨行/跨列值返回 `422 / INVALID_TABLE_SPAN`。该限制在展开表格前执行，不依赖 HTML 请求长度。

DOCX 表格正文单元格默认顶端对齐，表头垂直居中，允许长行跨页。固定页宽内按各列累计内容量的平方根分配列宽并预留最小宽度；通栏内容不干扰列宽，其他合并单元格内容分摊到所占列。表格图片按单元格（含合并列）扣除内边距后的宽度等比缩小，不裁切、不放大。该排版规则仅消费通用 HTML，不使用文件名或 Provider 坐标。长短列内容不均造成的剩余空间不能在保留同一行对应关系时完全消除。

MinerU 上传地址申请、文件上传、状态轮询和结果下载的网络错误（包括 TLS、连接失败、超时和传输中断）均归类为 `MINERU_PARSE_FAILED`，不归类为 `ADAPTER_CONVERT_FAILED`；错误消息标识失败阶段，不包含签名 URL 或凭据。同步接口对应状态码为 502，异步任务在 `error.code` / `error.message` 中返回失败原因。

标题块的 `alignment` 表示源文档中的语义对齐方式。Adapter 应从 Provider 的版面几何信息通用推导；缺少可靠几何信息时使用 `left`，不得根据标题文字或文件名猜测。标题 `text` 可包含由可靠 OCR 行内几何恢复出的有效空白，Web Renderer 和 DOCX Exporter 通过保留空白还原标题内部的相对位置。各输出端使用语义对齐排版，不消费 Provider 坐标。

当前 Web Renderer 支持基础编辑：文档标题、标题块、段落、列表项、图片说明和表格单元格可通过编辑模式直接修改。解析完成的 `LessonDocument`、任务状态和资源元数据由 Java 后端保存到 MySQL；PDF、MinerU 原始结果及图片继续保存在服务端 `DATA_DIR`。前端不使用 IndexedDB 或 Blob 缓存，刷新、打开、编辑和删除均以服务端数据库为唯一真源，图片直接使用后端资源 URL。这些修改不会回写源 PDF。DOCX 导出必须从最新 `LessonDocument` 数据和服务端图片 URL 重建导出 HTML，不得以当前页面 DOM 作为持久化结果。

## Java 服务端文档库

- `GET /api/lesson-documents` 返回未删除文档摘要，字段为 `id`、`title`、`sourceFileName`、`sourceType`、`blockCount`、`createdAt` 和 `updatedAt`，按 `updatedAt` 倒序。
- `GET /api/lesson-documents/{documentId}` 返回完整 v1 文档及强 `ETag` 响应头，`Cache-Control: no-store`。ETag 为当前存储内容的 SHA-256 摘要，用于编辑并发控制，不是 `LessonDocument.version` 协议版本。
- `PUT /api/lesson-documents/{documentId}` 覆盖当前文档；路径 ID 必须与正文 `documentId` 一致，正文须通过 v1 校验，并通过 `If-Match` 传入上次读取或保存响应的完整 ETag（包括引号）。缺少该头返回 `428 / DOCUMENT_VERSION_REQUIRED`；内容已经变化返回 `409 / DOCUMENT_CONFLICT`，不覆盖服务端。成功返回文档及更新后的 ETag。数据库通过原内容的二进制原子比较防止检查后发生并发覆盖。
- 前端失焦后串行保存，保存期间合并待发送的最新草稿，并使用上一次成功保存返回的 ETag。保存失败保留内存草稿，显示失败并允许手动重试；冲突必须由用户选择是否放弃草稿、重新加载服务端版本，不自动覆盖或合并。未保存时拦截页面跳转，并提示刷新/关闭风险，不提供离线缓存。
- 单篇和批量 DOCX 导出均等待当前编辑保存成功，然后重新读取服务端文档；未应用的公式或文字编辑、保存失败和版本冲突均会阻止导出，不以未持久化草稿冒充服务端内容。
- `DELETE /api/lesson-documents/{documentId}` 软删除并返回 `204`。删除后，文档、任务和资源接口均返回 `404`；数据库记录和文件永久保留，不提供恢复或定期物理清理。

当前文档库全局共享，不自动迁移浏览器旧数据或导入历史任务目录。

上传成功后前端进入 `/jobs/{jobId}`。任务 ID 由 URL 保存，刷新或重新打开该 URL 时立即查询服务端并继续轮询；完成后刷新文档列表并替换为文档预览 URL。失败或不存在的任务显示查询结果/错误并提供返回上传页入口。文档与任务内容均不缓存到浏览器存储。

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
