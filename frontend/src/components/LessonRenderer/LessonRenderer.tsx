import { Check, Download, PencilLine } from 'lucide-react'
import { useRef, useState } from 'react'
import { exportHtmlToDocx } from '../../api/documents'
import type { LessonBlock, LessonDocument } from '../../types/lesson-document'
import { FormulaBlock } from './FormulaBlock'
import { HeadingBlock } from './HeadingBlock'
import { ImageBlock } from './ImageBlock'
import { ListBlock } from './ListBlock'
import { ParagraphBlock } from './ParagraphBlock'
import { TableBlock } from './TableBlock'

function sourceHref(sourceUrl: string, sourceType: string, page?: number | null): string {
  return sourceType.toLowerCase() === 'pdf' && page
    ? `${sourceUrl}#page=${page}`
    : sourceUrl
}

function BlockRenderer({ block, editable }: { block: LessonBlock; editable: boolean }) {
  switch (block.type) {
    case 'heading': return <HeadingBlock block={block} editable={editable} />
    case 'paragraph': return <ParagraphBlock block={block} editable={editable} />
    case 'list': return <ListBlock block={block} editable={editable} />
    case 'table': return <TableBlock block={block} editable={editable} />
    case 'image': return <ImageBlock block={block} editable={editable} />
    case 'formula': return <FormulaBlock block={block} editable={editable} />
  }
}

export function LessonRenderer({ document }: { document: LessonDocument }) {
  const [editable, setEditable] = useState(false)
  const [title, setTitle] = useState(document.title)
  const [exporting, setExporting] = useState(false)
  const [exportError, setExportError] = useState('')
  const documentRef = useRef<HTMLElement>(null)
  const pageGroups = document.blocks.reduce<Array<{ key: string; sourcePage?: number | null; blocks: LessonBlock[] }>>((groups, block) => {
    const key = block.groupId ?? (block.sourcePage ? `page-${block.sourcePage}` : 'document')
    const current = groups.at(-1)
    if (current?.key === key) current.blocks.push(block)
    else groups.push({ key, sourcePage: block.sourcePage, blocks: [block] })
    return groups
  }, [])

  const downloadDocx = async () => {
    if (!documentRef.current || exporting) return
    setExporting(true)
    setExportError('')
    try {
      const clone = documentRef.current.cloneNode(true) as HTMLElement
      clone.querySelectorAll('[data-latex]').forEach((formula) => {
        const replacement = window.document.createElement('span')
        replacement.dataset.latex = formula.getAttribute('data-latex') ?? ''
        formula.replaceWith(replacement)
      })
      clone.querySelectorAll('.document-edit-toolbar, .document-title, .document-rule, .source-page-header, .block-review-note, .formula-editor, .formula-edit-hint, .formula-validation-badge, .table-scroll-hint, button').forEach((node) => node.remove())
      clone.querySelectorAll('[contenteditable]').forEach((node) => node.removeAttribute('contenteditable'))
      await Promise.all(Array.from(clone.querySelectorAll('img')).map(async (image) => {
        if (image.src.startsWith('data:')) return
        try {
          const response = await fetch(image.src)
          if (!response.ok) return
          const blob = await response.blob()
          image.src = await new Promise<string>((resolve, reject) => {
            const reader = new FileReader()
            reader.onload = () => resolve(String(reader.result))
            reader.onerror = () => reject(reader.error)
            reader.readAsDataURL(blob)
          })
        } catch {
          // The backend will keep the image alt text when an asset cannot be embedded.
        }
      }))
      const exportedTitle = title || 'lesson'
      const safeTitle = exportedTitle.replace(/[<>:"/\\|?*]/g, '_')
      const filename = `${safeTitle}.docx`
      const blob = await exportHtmlToDocx(clone.outerHTML, filename)
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

  return (
    <article className={`lesson-document ${editable ? 'is-editing' : ''}`} ref={documentRef}>
      <div className="document-edit-toolbar">
        <span className={exportError ? 'export-error' : ''}>{exportError || (editable ? '编辑模式：点击文字或表格单元格即可修改' : '内容可在浏览器内基础编辑并导出')}</span>
        <div className="document-toolbar-actions">
          <button className="export-button" type="button" disabled={exporting} onClick={downloadDocx}>
            <Download size={15} />{exporting ? '生成中…' : '下载 DOCX'}
          </button>
          <button className="edit-button" type="button" onClick={() => setEditable((value) => !value)}>
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
          onBlur={(event) => setTitle(event.currentTarget.textContent?.trim() || title)}
        >
          {title}
        </h1>
        <p>{document.metadata.sourceFileName}</p>
      </header>
      <div className="document-rule" />
      <div className="document-blocks">
        {pageGroups.map((group) => (
          <section className="lesson-source-page" data-source-page={group.sourcePage ?? undefined} key={group.key}>
            {group.sourcePage && (
              <div className="source-page-header">
                <span>源文档第 {group.sourcePage} 页</span>
                {document.metadata.sourceUrl && (
                  <a
                    href={sourceHref(document.metadata.sourceUrl, document.metadata.sourceType, group.sourcePage)}
                    target="_blank"
                    rel="noreferrer"
                  >
                    {document.metadata.sourceType.toLowerCase() === 'pdf' ? '对照原页' : '打开源文件'}
                  </a>
                )}
              </div>
            )}
            {group.blocks.map((block) => (
              <div
                className={`document-block${block.type === 'heading' ? ` document-block-heading-${block.alignment}` : ''}`}
                key={block.id}
              >
                {block.reviewRequired && (
                  <div className="block-review-note" role="note">
                    <strong>版面识别需复核</strong>
                    <span>{block.reviewReason ?? '请结合源文档确认阅读顺序。'}</span>
                  </div>
                )}
                <BlockRenderer block={block} editable={editable} />
              </div>
            ))}
          </section>
        ))}
      </div>
    </article>
  )
}
