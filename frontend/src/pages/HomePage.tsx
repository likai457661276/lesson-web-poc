import { AlertCircle, Check, FileSearch, ShieldCheck } from 'lucide-react'
import { useEffect, useRef, useState } from 'react'
import { Link, useMatch, useNavigate, useParams } from 'react-router-dom'
import { getParseJob, parseDocument } from '../api/documents'
import { DocumentLibrary } from '../components/DocumentLibrary'
import { DocumentUploader } from '../components/DocumentUploader'
import { LessonRenderer, type LessonRendererHandle } from '../components/LessonRenderer/LessonRenderer'
import { useDocumentLibrary } from '../hooks/useDocumentLibrary'
import type { ParseJob } from '../types/lesson-document'

const statusCopy = {
  pending: '任务已创建',
  processing: 'MinerU 正在识别内容结构',
  completed: '解析完成，已写入服务端文档库',
  failed: '解析失败',
}

export function HomePage() {
  const { documentId } = useParams()
  const editing = Boolean(useMatch('/documents/:documentId/edit'))
  const navigate = useNavigate()
  const [file, setFile] = useState<File | null>(null)
  const [job, setJob] = useState<ParseJob | null>(null)
  const [error, setError] = useState('')
  const rendererRef = useRef<LessonRendererHandle>(null)
  const completedJobIds = useRef(new Set<string>())
  const pendingDownloadIdRef = useRef<string | null>(null)
  const {
    items,
    listReady,
    document: serverDocument,
    loadError,
    libraryError,
    adoptParsedDocument,
    saveEdits,
    remove,
  } = useDocumentLibrary(documentId)
  const busy = job?.status === 'pending' || job?.status === 'processing'
  const previewDocument = serverDocument
  const readingLibrary = Boolean(documentId) && !previewDocument && !loadError && !busy

  useEffect(() => {
    if (!job || !busy) return
    const timer = window.setTimeout(async () => {
      try {
        setJob(await getParseJob(job.jobId))
      } catch (reason) {
        setError(reason instanceof Error ? reason.message : '状态查询失败')
      }
    }, 1800)
    return () => window.clearTimeout(timer)
  }, [job, busy])

  useEffect(() => {
    if (job?.status !== 'completed' || !job.document) return
    if (completedJobIds.current.has(job.jobId)) return
    completedJobIds.current.add(job.jobId)
    void (async () => {
      const available = await adoptParsedDocument(job.document!)
      if (available) navigate(`/documents/${job.document!.documentId}`)
    })()
  }, [adoptParsedDocument, job, navigate])

  useEffect(() => {
    if (pendingDownloadIdRef.current !== documentId || !previewDocument) return
    void rendererRef.current?.downloadDocx()
    pendingDownloadIdRef.current = null
  }, [documentId, previewDocument])

  const submit = async () => {
    if (!file) return
    setError('')
    try {
      navigate('/')
      setJob(await parseDocument(file))
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : '上传失败')
    }
  }

  const selectFile = (nextFile: File) => {
    if (!nextFile.name.toLowerCase().endsWith('.pdf')) {
      setFile(null)
      setJob(null)
      setError('仅支持 PDF 格式文件')
      return
    }
    setFile(nextFile)
    setJob(null)
    setError('')
  }

  const openDocument = (id: string, mode: 'view' | 'edit' = 'view') => {
    navigate(mode === 'edit' ? `/documents/${id}/edit` : `/documents/${id}`)
  }

  const downloadDocument = (id: string) => {
    if (id === documentId) {
      void rendererRef.current?.downloadDocx()
      return
    }
    pendingDownloadIdRef.current = id
    openDocument(id)
  }

  const deleteDocument = async (id: string) => {
    const item = items.find((entry) => entry.id === id)
    const label = item?.title || item?.sourceFileName || '该文档'
    if (!window.confirm(`删除「${label}」？文档将从服务端文档库隐藏。`)) return
    const deleted = await remove(id)
    if (deleted) {
      if (job?.document?.documentId === id) setJob(null)
      if (documentId === id) navigate('/')
    }
  }

  return (
    <main>
      <header className="app-header">
        <Link className="brand" to="/" aria-label="Lesson Web 首页">
          <span className="brand-mark">L</span>
          <span><strong>Lesson Web</strong><small>Document Pipeline PoC</small></span>
        </Link>
        <div className="system-status"><span /> API · MinerU</div>
      </header>

      <div className="workspace">
        <aside className="control-rail">
          <div className="intro-copy">
            <span className="eyebrow">文档标准化工作台</span>
            <h1>把教案转换为可渲染的 Web 文档。</h1>
            <p>上传源文件，经过 MinerU 解析与适配后，统一输出 LessonDocument v1。转换结果保存在服务端，可查看、编辑、下载或删除。</p>
          </div>
          <DocumentUploader
            file={file}
            disabled={Boolean(busy)}
            onFile={selectFile}
            onClear={() => { setFile(null); setJob(null); setError('') }}
            onSubmit={() => void submit()}
          />

          {(job || error) && (
            <section className={`job-status ${job?.status ?? 'failed'}`} aria-live="polite">
              <div className="status-icon">
                {job?.status === 'completed' ? <Check size={18} /> : error || job?.status === 'failed' ? <AlertCircle size={18} /> : <FileSearch size={18} />}
              </div>
              <div>
                <strong>{error || (job && statusCopy[job.status])}</strong>
                <span>{job?.error?.message ?? (busy ? '通常需要数十秒，请保持页面打开。' : job?.jobId)}</span>
              </div>
            </section>
          )}

          {libraryError && (
            <section className="job-status failed" aria-live="polite">
              <div className="status-icon"><AlertCircle size={18} /></div>
              <div>
                <strong>服务端文档库不可用</strong>
                <span>{libraryError}</span>
              </div>
            </section>
          )}

          <DocumentLibrary
            items={items}
            ready={listReady}
            activeId={documentId}
            disabled={Boolean(busy)}
            onView={(id) => openDocument(id)}
            onEdit={(id) => openDocument(id, 'edit')}
            onDownload={downloadDocument}
            onDelete={(id) => void deleteDocument(id)}
          />

          <div className="privacy-note">
            <ShieldCheck size={16} />
            <span>文档保存在服务端数据库；密钥仅由后端环境变量读取，不进入浏览器或仓库。</span>
          </div>
        </aside>

        <section className="preview-stage" aria-labelledby="preview-heading">
          <div className="preview-toolbar">
            <div><span className="section-kicker">02 / 输出</span><h2 id="preview-heading">Web 教案预览</h2></div>
            {previewDocument && <span className="block-count">{previewDocument.blocks.length} 个内容块</span>}
          </div>
          {previewDocument ? (
            <LessonRenderer
              key={previewDocument.documentId}
              ref={rendererRef}
              document={previewDocument}
              editable={editing}
              onEditableChange={(next) => openDocument(previewDocument.documentId, next ? 'edit' : 'view')}
              onDocumentChange={saveEdits}
            />
          ) : (
            <div className={`empty-preview ${busy ? 'is-processing' : ''}`}>
              <div className="paper-ghost"><span /><span /><span /><span /></div>
              <strong>
                {loadError || (busy ? '正在建立文档结构' : readingLibrary ? '正在读取服务端文档' : '等待教案文件')}
              </strong>
              <p>
                {loadError
                  ? '可以从左侧文档列表打开其他服务端文档，或重新上传解析。'
                  : busy
                    ? '识别标题、段落、表格、图片与公式…'
                    : readingLibrary
                      ? '正在从服务端数据库读取 LessonDocument。'
                      : '完成解析后，LessonDocument 将在这里逐块渲染，并写入服务端数据库。'}
              </p>
            </div>
          )}
        </section>
      </div>
    </main>
  )
}
