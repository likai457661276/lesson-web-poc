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

当前 Web Renderer 支持会话内基础编辑：文档标题、标题块、段落、列表项、图片说明和表格单元格可通过编辑模式直接修改。PoC 不包含数据库，因此这些修改不会回写源文件，也不会跨页面刷新持久化。

## 示例

```json
{
  "version": "1.0",
  "documentId": "job-001",
  "title": "勾股定理教学设计",
  "metadata": {
    "sourceType": "docx",
    "sourceFileName": "勾股定理教学设计.docx"
  },
  "blocks": [
    { "id": "block-0001", "type": "heading", "level": 1, "text": "教学目标", "alignment": "left" },
    { "id": "block-0002", "type": "paragraph", "text": "理解勾股定理。" },
    { "id": "block-0003", "type": "formula", "latex": "a^2+b^2=c^2" }
  ]
}
```
