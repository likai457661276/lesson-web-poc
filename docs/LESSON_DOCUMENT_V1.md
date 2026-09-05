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

表格保留 HTML，以支持 `rowspan`、`colspan`；前端渲染前必须消毒。表格内公式使用 `<span data-latex="..."></span>` 占位，Renderer 将其转换为 KaTeX 公式，仅编辑模式允许修改。图片只保存站内 URL，禁止在 JSON 中内嵌 Base64。公式内容为 KaTeX 可接受的 LaTeX；编辑保存时可调用 `/api/formulas/validate` 进行结构校验（Symja）。

表格标题使用表格 HTML 内的 `<caption>` 保留，支持 Web 编辑与 DOCX 导出，不从标题文字推断课时编号或层级。DOCX 按单元格内原始节点顺序输出文字、图片及公式，保留段落、列表和嵌套表格；内表行不参与外表展开，标题仅输出一次并写入 `keepNext` 同页排版请求。嵌套表格使用父单元格扣除左右内边距后的可用宽度，末尾保留 Word 必需的段落。实际分页由客户端决定；LibreOffice 中嵌套表格标题在父表行跨页时仍可能与内表分离。

Adapter 保留 Provider 返回的内容块边界和顺序，不根据左边缘相近、文字块数量或表格宽度，把表格前面的标题和正文合并成表格行。标题层级保持为 heading，表格只包含 Provider 实际提供的表格内容与 caption。

Adapter 可从 Provider 的行内文字及版面信息恢复多栏内容：至少三行具有一致的 2～4 列和持续的栏间空隙，且行内文字拼接后必须与原段落完全一致（忽略空白）。确认后转换为一个按列阅读的通用 HTML 表格；坐标、Provider 字段不进入协议。存在一致栏间空隙，但行数不足、其他行缺列、含非文字 span 或文字无法完整核对时，保留原始段落，不生成或持久化复核提示。缺少可靠版面信息时不猜测分栏；跨块、跨页多栏和未检测到的复杂版面仍依赖 Provider 阅读顺序。

未知内容类型只有纯文本且不含无法表达的结构时，转换为普通 paragraph，保留 text，不把未知 Provider 类型传到前端，也不附加诊断字段。段落仅包含 id、type 和 text；Web 与 DOCX 只输出正文。

解析完整性检查：空结果返回 `DOCUMENT_CONTENT_EMPTY`；非对象内容块、缺失图片资源（含表格内图片）、缺少 HTML 或有效单元格的表格、无法转换的列表条目、空标题/公式及无法保留的未知结构返回 `DOCUMENT_CONTENT_INCOMPLETE`。异常状态码为 422；当前上传接口是异步接口，上传受理仍为 202，任务查询为 200，随后通过 `status: failed` 与 `error.code` / `error.message` 报告转换失败，不保存部分文档为成功。消息只包含位置和失败原因，不暴露 Provider 路径或资源 URL。原始解析结果仍保留用于诊断。表格只有截图而没有结构化内容时明确失败，不用截图冒充可编辑表格。

MinerU 跨页表格可能留下无 HTML、文字、图片、标题或脚注的空片段。只有同页几何唯一匹配的 `para_blocks` 表格内所有 `table_body` 都明确标记 `lines_deleted: true` 且 `lines` 为空时，Adapter 才跳过此片段，不生成空段落、提示或虚构单元格，不据此宣称上游已完整合并。其他可用内容的顺序保持不变。缺少标记、坐标不匹配或匹配不唯一时仍按无法转换处理；整份文档只含已删除空片段而没有可用内容时仍返回 `DOCUMENT_CONTENT_EMPTY`。前端和导出器无需识别或过滤 Provider 空片段。

DOCX 支持中文公式文本和中文上下标，例如 `A_{底}` 与 `V_{\text{总体}}`，以及上/下划线和上方重音（如 `\overline{PQ}`、`\underline{uv}`、`\hat{z}`、`\vec{v}`）。这些装饰输出为 Word 原生公式结构，不作为尾随字符拼接。未支持的 MathML 节点或装饰结构明确拒绝，不递归摊平为看似成功的公式。若公式无法转换为 Word 可编辑公式，单篇和批量导出均返回 `422 / DOCX_FORMULA_UNSUPPORTED`，前端显示失败，不输出 LaTeX 源码替代。KaTeX 可渲染不代表当前 DOCX 转换器支持其全部命令；矩阵等未支持结构仍可能被拒绝。

DOCX 图片接受内嵌 PNG/JPEG/GIF；资源获取失败由前端阻止导出，服务端解码失败、格式不受支持或图片数据损坏返回 `422 / DOCX_IMAGE_INVALID`。有无 alt 均不跳过图片或用说明文字冒充图片；批量中的任一图片失败也不返回部分 ZIP。

DOCX 导出器限制单个表格最多 2000 行、100 列、10000 个展开单元格；`rowspan` 和 `colspan` 分别不得超过行数和列数上限。超限返回 `413 / TABLE_TOO_LARGE`，非法或超过有符号 64 位整数范围的跨行/跨列值返回 `422 / INVALID_TABLE_SPAN`。该限制在展开表格前执行，不依赖 HTML 请求长度。

DOCX 表格正文单元格默认顶端对齐，表头垂直居中，允许长行跨页。固定页宽内按各列累计内容量的平方根分配列宽并预留最小宽度；通栏内容不干扰列宽，其他合并单元格内容分摊到所占列。表格图片按单元格（含合并列）扣除内边距后的宽度等比缩小，不裁切、不放大。该排版规则仅消费通用 HTML，不使用文件名或 Provider 坐标。长短列内容不均造成的剩余空间不能在保留同一行对应关系时完全消除。

表格的几何上限不代表所有列数都能适合纸张；若可用宽度不足以容纳各列内边距及正文，则返回 `422 / DOCX_TABLE_TOO_NARROW`，不生成零宽或负宽单元格。当前仍统一使用 Letter 纵向纸张进行语义重排，不承诺还原源 PDF 的纸型、横向页面或原始分页。

DOCX 保留标题 1–6 级，并写入对应 Word 大纲级别。协议标题、段落及表格标题中的连续空格、换行保留为真实空白和 Word 换行，制表符转为 Word tab；普通表格 HTML 的非预格式化文本仍按 HTML 规则折叠排版空白。每个独立或嵌套列表创建自己的编号实例，默认从 1 开始；支持 HTML `ol[start]`，列表续段保持顺序和缩进。倒序、自定义编号样式、`li[value]`、无效起号及超过 Word 九级列表的嵌套返回 `422 / DOCX_LIST_UNSUPPORTED`，不静默改写编号。

MinerU 上传地址申请、文件上传、状态轮询和结果下载的网络错误（包括 TLS、连接失败、超时和传输中断）均归类为 `MINERU_PARSE_FAILED`，不归类为 `ADAPTER_CONVERT_FAILED`；错误消息标识失败阶段，不包含签名 URL 或凭据。同步接口对应状态码为 502，异步任务在 `error.code` / `error.message` 中返回失败原因。

标题块的 `alignment` 表示源文档中的语义对齐方式。Adapter 应从 Provider 的版面几何信息通用推导；缺少可靠几何信息时使用 `left`，不得根据标题文字或文件名猜测。标题 `text` 可包含由可靠 OCR 行内几何恢复出的有效空白，Web Renderer 和 DOCX Exporter 通过保留空白还原标题内部的相对位置。各输出端使用语义对齐排版，不消费 Provider 坐标。

Provider content/model 的 0～1 和 0～1000 页面坐标由同一个 Adapter 几何工具归一化；layout.json 的坐标按其 page_size 换算。无效、非有限或越界坐标不参与布局推断。不能把未声明的像素/点坐标当作第三种输入格式自动猜测。

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
