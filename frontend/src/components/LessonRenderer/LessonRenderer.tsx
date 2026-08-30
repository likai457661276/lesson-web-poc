import { Check, Download, PencilLine } from 'lucide-react'
import { useImperativeHandle, useRef, useState, type Ref } from 'react'
import { exportHtmlToDocx } from '../../api/documents'
import type { LessonBlock, LessonDocument } from '../../types/lesson-document'
import { FormulaBlock } from './FormulaBlock'
import { HeadingBlock } from './HeadingBlock'
import { ImageBlock } from './ImageBlock'
import { ListBlock } from './ListBlock'
import { ParagraphBlock } from './ParagraphBlock'
import { TableBlock } from './TableBlock'
import { renderDocumentForExport } from './documentExport'

function BlockRenderer({
  block,
  editable,
  onChange,
}: {
  block: LessonBlock
  editable: boolean
  onChange: (block: LessonBlock) => void
}) {
  switch (block.type) {
    case 'heading': return <HeadingBlock block={block} editable={editable} onChange={onChange} />
    case 'paragraph': return <ParagraphBlock block={block} editable={editable} onChange={onChange} />
    case 'list': return <ListBlock block={block} editable={editable} onChange={onChange} />
    case 'table': return <TableBlock block={block} editable={editable} onChange={onChange} />
    case 'image': return <ImageBlock block={block} editable={editable} onChange={onChange} />
    case 'formula': return <FormulaBlock block={block} editable={editable} onChange={onChange} />
  }
}

export type LessonRendererHandle = {
  downloadDocx: () => Promise<void>
}

export function LessonRenderer({
  document,
  editable = false,
  onEditableChange,
  onDocumentChange,
  ref,
}: {
  document: LessonDocument
  editable?: boolean
  onEditableChange?: (editable: boolean) => void
  onDocumentChange?: (document: LessonDocument) => void | Promise<void>
  ref?: Ref<LessonRendererHandle>
}) {
  const [title, setTitle] = useState(document.title)
  const [exporting, setExporting] = useState(false)
  const [exportError, setExportError] = useState('')
  const draftRef = useRef(document)

  const persist = (next: LessonDocument) => {
    draftRef.current = next
    void onDocumentChange?.(next)
  }

  const updateBlock = (block: LessonBlock) => {
    persist({
      ...draftRef.current,
      blocks: draftRef.current.blocks.map((item) => (item.id === block.id ? block : item)),
    })
  }

  const downloadDocx = async () => {
    if (exporting) return
    setExporting(true)
    setExportError('')
    try {
      const exportDocument = draftRef.current
      const exportRoot = renderDocumentForExport(exportDocument)
      await Promise.all(Array.from(exportRoot.querySelectorAll('img')).map(async (image) => {
        if (image.src.startsWith('data:')) return
        const response = await fetch(image.src)
        if (!response.ok) throw new Error(`图片资源读取失败（HTTP ${response.status}）`)
        const blob = await response.blob()
        image.src = await new Promise<string>((resolve, reject) => {
          const reader = new FileReader()
          reader.onload = () => resolve(String(reader.result))
          reader.onerror = () => reject(reader.error)
          reader.readAsDataURL(blob)
        })
      }))
      const exportedTitle = exportDocument.title || 'lesson'
      const safeTitle = exportedTitle.replace(/[<>:"/\\|?*]/g, '_')
      const filename = `${safeTitle}.docx`
      const blob = await exportHtmlToDocx(exportRoot.outerHTML, filename)
      const url = URL.createObjectURL(blob)
      const anchor = window.document.createElement('a')
      anchor.href = url
      anchor.download = filename
      anchor.click()
      window.setTimeout(() => URL.revokeObjectURL(url), 0)
    } catch (reason) {
      setExportError(reason instanceof Error ? reason.message : 'DOCX 导出失败')
    } finally {
      setExporting(false)
    }
  }

  useImperativeHandle(ref, () => ({ downloadDocx }))

  return (
    <article className={`lesson-document ${editable ? 'is-editing' : ''}`}>
        <div className="document-edit-toolbar">
          <span className={exportError ? 'export-error' : ''}>{exportError || (editable ? '编辑模式：点击文字或表格单元格即可修改，离开后会保存到服务端' : '内容可在浏览器内基础编辑并导出；结果保存在服务端')}</span>
          <div className="document-toolbar-actions">
            <button className="export-button" type="button" disabled={exporting} onClick={() => void downloadDocx()}>
              <Download size={15} />{exporting ? '生成中…' : '下载 DOCX'}
            </button>
            <button
              className="edit-button"
              type="button"
              onClick={() => onEditableChange?.(!editable)}
            >
              {editable ? <Check size={15} /> : <PencilLine size={15} />}
              {editable ? '完成编辑' : '编辑内容'}
            </button>
          </div>
        </div>
        <header className="document-title">
          <span>{document.metadata.sourceType.toUpperCase()} · LessonDocument {document.version}</span>
          <h1
            className="editable-copy"
            contentEditable={editable}
            suppressContentEditableWarning
            onBlur={(event) => {
              const nextTitle = event.currentTarget.textContent?.trim() || title
              setTitle(nextTitle)
              persist({ ...draftRef.current, title: nextTitle })
            }}
          >
            {title}
          </h1>
          <p>{document.metadata.sourceFileName}</p>
        </header>
        <div className="document-rule" />
        <div className="document-blocks">
          {document.blocks.map((block) => (
            <div
              className={`document-block${block.type === 'heading' ? ` document-block-heading-${block.alignment}` : ''}`}
              key={block.id}
            >
              <BlockRenderer block={block} editable={editable} onChange={updateBlock} />
            </div>
          ))}
        </div>
    </article>
  )
}
