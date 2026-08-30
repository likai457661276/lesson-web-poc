import { Check, Download, PencilLine } from 'lucide-react'
import { useCallback, useEffect, useImperativeHandle, useRef, useState, type Ref } from 'react'
import { exportHtmlToDocx } from '../../api/documents'
import type { LessonBlock, LessonDocument } from '../../types/lesson-document'
import { FormulaBlock } from './FormulaBlock'
import { HeadingBlock } from './HeadingBlock'
import { ImageBlock } from './ImageBlock'
import { ListBlock } from './ListBlock'
import { ParagraphBlock } from './ParagraphBlock'
import { TableBlock } from './TableBlock'
import { downloadBlob, prepareDocxExport } from './documentExport'

function BlockRenderer({
  block,
  editable,
  onChange,
  onDraftChange,
}: {
  block: LessonBlock
  editable: boolean
  onChange: (block: LessonBlock) => void
  onDraftChange: (id: string, dirty: boolean) => void
}) {
  switch (block.type) {
    case 'heading': return <HeadingBlock block={block} editable={editable} onChange={onChange} />
    case 'paragraph': return <ParagraphBlock block={block} editable={editable} onChange={onChange} />
    case 'list': return <ListBlock block={block} editable={editable} onChange={onChange} />
    case 'table': return <TableBlock block={block} editable={editable} onChange={onChange} onDraftChange={onDraftChange} />
    case 'image': return <ImageBlock block={block} editable={editable} onChange={onChange} />
    case 'formula': return <FormulaBlock block={block} editable={editable} onChange={onChange} onDraftChange={onDraftChange} />
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
  getPersistedDocument,
  onUnsavedChange,
  saveState,
  saveError,
  onRetrySave,
  onReload,
  ref,
}: {
  document: LessonDocument
  editable?: boolean
  onEditableChange?: (editable: boolean) => void
  onDocumentChange?: (document: LessonDocument) => void
  getPersistedDocument: () => Promise<LessonDocument>
  onUnsavedChange: (dirty: boolean) => void
  saveState: 'saved' | 'saving' | 'failed' | 'conflict'
  saveError: string
  onRetrySave: () => void
  onReload: () => void
  ref?: Ref<LessonRendererHandle>
}) {
  const [title, setTitle] = useState(document.title)
  const [exporting, setExporting] = useState(false)
  const [exportError, setExportError] = useState('')
  const draftRef = useRef(document)
  const exportLockRef = useRef(false)
  const [pendingDraft, setPendingDraft] = useState(false)
  const dirtyFieldsRef = useRef(new Set<string>())
  const markDirty = useCallback((id: string, dirty: boolean) => {
    if (dirty) dirtyFieldsRef.current.add(id)
    else dirtyFieldsRef.current.delete(id)
    const pending = dirtyFieldsRef.current.size > 0
    setPendingDraft(pending)
    onUnsavedChange(pending)
  }, [onUnsavedChange])

  useEffect(() => () => onUnsavedChange(false), [onUnsavedChange])

  const persist = (next: LessonDocument) => {
    if (!editable) return
    draftRef.current = next
    onDocumentChange?.(next)
    markDirty('text', false)
  }

  const updateBlock = (block: LessonBlock) => {
    persist({
      ...draftRef.current,
      blocks: draftRef.current.blocks.map((item) => (item.id === block.id ? block : item)),
    })
  }

  const downloadDocx = async () => {
    if (exportLockRef.current) return
    exportLockRef.current = true
    setExporting(true)
    setExportError('')
    try {
      const persisted = await getPersistedDocument()
      const { html, filename } = await prepareDocxExport(persisted)
      const blob = await exportHtmlToDocx(html, filename)
      downloadBlob(blob, filename)
    } catch (reason) {
      setExportError(reason instanceof Error ? reason.message : 'DOCX 导出失败')
    } finally {
      exportLockRef.current = false
      setExporting(false)
    }
  }

  useImperativeHandle(ref, () => ({ downloadDocx }))

  return (
    <article className={`lesson-document ${editable ? 'is-editing' : ''}`} onInputCapture={(event) => {
      const target = event.target as HTMLElement
      if (editable && target.closest('[contenteditable="true"]') && !target.closest('textarea, input')) markDirty('text', true)
    }}>
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
        <div className={`document-save-status ${saveState === 'failed' || saveState === 'conflict' ? 'has-error' : ''}`} role="status">
          {saveState === 'failed' || saveState === 'conflict' ? `未保存：${saveError}`
            : pendingDraft ? '有未应用的编辑，请完成编辑或取消' : saveState === 'saving' ? '正在保存…' : '已保存到服务端'}
          {saveState === 'failed' && <button type="button" onClick={onRetrySave}>重试保存</button>}
          {(saveState === 'failed' || saveState === 'conflict') && <button type="button" onClick={onReload}>重新加载服务端版本</button>}
        </div>
        <header className="document-title">
          <span>{document.metadata.sourceType.toUpperCase()} · LessonDocument {document.version}</span>
          <h1
            className="editable-copy"
            contentEditable={editable}
            suppressContentEditableWarning
            onBlur={(event) => {
              if (!editable) return
              const nextTitle = event.currentTarget.textContent?.trim() ?? ''
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
              <BlockRenderer block={block} editable={editable} onChange={updateBlock} onDraftChange={markDirty} />
            </div>
          ))}
        </div>
    </article>
  )
}
