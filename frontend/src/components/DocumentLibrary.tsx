import { Download, Eye, FileText, PencilLine, Trash2 } from 'lucide-react'
import { useState } from 'react'
import type { LessonDocumentSummary } from '../types/lesson-document'

interface Props {
  items: LessonDocumentSummary[]
  ready?: boolean
  activeId?: string | null
  disabled?: boolean
  batchDownloading?: boolean
  batchStatus?: string
  batchError?: string
  onView: (id: string) => void
  onEdit: (id: string) => void
  onDownload: (id: string) => void
  onBatchDownload: (ids: string[]) => void
  onDelete: (id: string) => void
}

function formatUpdatedAt(value: string) {
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return ''
  return new Intl.DateTimeFormat('zh-CN', {
    month: 'short',
    day: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  }).format(date)
}

export function DocumentLibrary({
  items,
  ready = true,
  activeId,
  disabled = false,
  batchDownloading = false,
  batchStatus = '',
  batchError = '',
  onView,
  onEdit,
  onDownload,
  onBatchDownload,
  onDelete,
}: Props) {
  const [selection, setSelection] = useState<string[]>([])
  const selectedIds = items.filter((item) => selection.includes(item.id)).map((item) => item.id)
  const allSelected = items.length > 0 && selectedIds.length === items.length
  const selectionDisabled = disabled || batchDownloading

  return (
    <section className="document-library" aria-labelledby="library-heading">
      <div className="section-kicker">03 / 服务端文档库</div>
      <div className="section-heading-row">
        <div>
          <h2 id="library-heading">文档列表</h2>
          <p>解析和编辑结果保存在服务端数据库，刷新或更换浏览器后仍可打开。</p>
        </div>
        <span className="format-note">{items.length} 篇</span>
      </div>
      {ready && items.length > 0 && (
        <div className="library-batch-toolbar">
          <label className="library-select-all">
            <input
              type="checkbox"
              checked={allSelected}
              ref={(input) => { if (input) input.indeterminate = selectedIds.length > 0 && !allSelected }}
              disabled={selectionDisabled}
              onChange={() => setSelection(allSelected ? [] : items.map((item) => item.id))}
            />
            全选
          </label>
          <span>已选 {selectedIds.length} 篇</span>
          <button
            type="button"
            disabled={selectionDisabled || selectedIds.length === 0 || selectedIds.length > 20}
            onClick={() => onBatchDownload(selectedIds)}
          >
            <Download size={13} />{batchDownloading ? '打包中…' : '批量下载'}
          </button>
          <p className="library-batch-hint">DOCX 打包为 ZIP，每批最多 20 篇。</p>
        </div>
      )}
      {batchStatus && <p className="library-batch-message" role="status">{batchStatus}</p>}
      {batchError && <p className="library-batch-message export-error" role="alert">{batchError}</p>}
      {!ready ? (
        <p className="library-empty">正在读取服务端文档…</p>
      ) : items.length === 0 ? (
        <p className="library-empty">暂无文档。完成一次解析后会出现在这里。</p>
      ) : (
        <ul className="library-list">
          {items.map((item) => {
            const active = item.id === activeId
            return (
              <li key={item.id}>
                <article className={`library-item ${active ? 'is-active' : ''}`}>
                  <div className="library-item-heading">
                    <input
                      className="library-item-checkbox"
                      type="checkbox"
                      aria-label={`选择文档：${item.title || item.sourceFileName}`}
                      checked={selectedIds.includes(item.id)}
                      disabled={selectionDisabled}
                      onChange={(event) => setSelection(event.target.checked
                        ? [...selectedIds, item.id]
                        : selectedIds.filter((id) => id !== item.id))}
                    />
                    <button
                      type="button"
                      className="library-item-main"
                      disabled={disabled}
                      aria-current={active ? 'page' : undefined}
                      onClick={() => onView(item.id)}
                    >
                      <span className="file-mark"><FileText size={18} /></span>
                      <span className="library-item-copy">
                        <strong>{item.title || item.sourceFileName}</strong>
                        <span>
                          {item.sourceFileName}
                          {' · '}
                          {formatUpdatedAt(item.updatedAt)}
                          {' · '}
                          {item.blockCount} 个内容块
                        </span>
                      </span>
                    </button>
                  </div>
                  <div className="library-item-actions">
                    <button type="button" disabled={disabled} onClick={() => onView(item.id)}>
                      <Eye size={13} />查看
                    </button>
                    <button type="button" disabled={disabled} onClick={() => onEdit(item.id)}>
                      <PencilLine size={13} />编辑
                    </button>
                    <button type="button" disabled={disabled} onClick={() => onDownload(item.id)}>
                      <Download size={13} />下载
                    </button>
                    <button
                      type="button"
                      className="is-danger"
                      disabled={disabled}
                      onClick={() => onDelete(item.id)}
                    >
                      <Trash2 size={13} />删除
                    </button>
                  </div>
                </article>
              </li>
            )
          })}
        </ul>
      )}
    </section>
  )
}
