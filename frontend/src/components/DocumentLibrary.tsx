import { Download, Eye, FileText, PencilLine, Trash2 } from 'lucide-react'
import type { CachedDocumentSummary } from '../storage/documentCache'

interface Props {
  items: CachedDocumentSummary[]
  ready?: boolean
  activeId?: string | null
  disabled?: boolean
  onView: (id: string) => void
  onEdit: (id: string) => void
  onDownload: (id: string) => void
  onDelete: (id: string) => void
}

function formatCachedAt(value: string) {
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
  onView,
  onEdit,
  onDownload,
  onDelete,
}: Props) {
  return (
    <section className="document-library" aria-labelledby="library-heading">
      <div className="section-kicker">03 / 本机缓存</div>
      <div className="section-heading-row">
        <div>
          <h2 id="library-heading">文档列表</h2>
          <p>解析完成的 LessonDocument 保存在当前浏览器，刷新后仍可打开。</p>
        </div>
        <span className="format-note">{items.length} 篇</span>
      </div>
      {!ready ? (
        <p className="library-empty">正在读取本机文档…</p>
      ) : items.length === 0 ? (
        <p className="library-empty">暂无缓存文档。完成一次解析后会出现在这里。</p>
      ) : (
        <ul className="library-list">
          {items.map((item) => {
            const active = item.id === activeId
            return (
              <li key={item.id}>
                <article className={`library-item ${active ? 'is-active' : ''}`}>
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
                        {formatCachedAt(item.updatedAt)}
                        {' · '}
                        {item.blockCount} 个内容块
                      </span>
                    </span>
                  </button>
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
