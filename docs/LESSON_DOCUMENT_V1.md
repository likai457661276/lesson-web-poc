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
type HeadingBlock = { id: string; type: 'heading'; level: 1 | 2 | 3 | 4 | 5 | 6; text: string }
type ParagraphBlock = { id: string; type: 'paragraph'; text: string }
type ListBlock = { id: string; type: 'list'; items: string[]; ordered: boolean }
type TableBlock = { id: string; type: 'table'; html: string }
type ImageBlock = { id: string; type: 'image'; src: string; alt?: string | null }
type FormulaBlock = { id: string; type: 'formula'; latex: string }
```

表格保留 HTML，以支持 `rowspan`、`colspan`；前端渲染前必须消毒。图片只保存站内 URL，禁止在 JSON 中内嵌 Base64。公式内容为 KaTeX 可接受的 LaTeX。

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
    { "id": "block-0001", "type": "heading", "level": 1, "text": "教学目标" },
    { "id": "block-0002", "type": "paragraph", "text": "理解勾股定理。" },
    { "id": "block-0003", "type": "formula", "latex": "a^2+b^2=c^2" }
  ]
}
```
