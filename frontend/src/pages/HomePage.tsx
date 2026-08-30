import { AlertCircle, Check, FileSearch, ShieldCheck } from 'lucide-react'
import { useCallback, useEffect, useRef, useState } from 'react'
import { Link, useBlocker, useMatch, useNavigate, useParams } from 'react-router-dom'
import { exportDocumentsToZip, getLessonDocument, parseDocument, type DocxExportInput } from '../api/documents'
import { DocumentLibrary } from '../components/DocumentLibrary'
import { DocumentUploader } from '../components/DocumentUploader'
import { LessonRenderer, type LessonRendererHandle } from '../components/LessonRenderer/LessonRenderer'
import { downloadBlob, prepareDocxExport } from '../components/LessonRenderer/documentExport'
import { useDocumentLibrary } from '../hooks/useDocumentLibrary'
import { useParseJobPolling } from '../hooks/useParseJobPolling'
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
  const [uploading, setUploading] = useState(false)
  const uploadingRef = useRef(false)
  const [editorPending, setEditorPending] = useState(false)
  const editorPendingRef = useRef(false)
  const [batchDownloading, setBatchDownloading] = useState(false)
  const [batchStatus, setBatchStatus] = useState('')
  const [batchError, setBatchError] = useState('')
  const batchDownloadRef = useRef(false)
  const rendererRef = useRef<LessonRendererHandle>(null)
  const completedJobIds = useRef(new Set<string>())
  const pendingDownloadIdRef = useRef<string | null>(null)
  const {
    items,
    listReady,
    document: serverDocument,
    loadError,
    libraryError,
    refreshList,
    saveEdits,
    waitForSaves,
    remove,
    saveState, saveError, retrySave, reloadDocument, loadVersion,
  } = useDocumentLibrary(documentId)
  const polling = useParseJobPolling(job, setJob)
  const busy = job?.status === 'pending' || job?.status === 'processing'
  const controlsDisabled = uploading || Boolean(busy) || batchDownloading
  const unsaved = editorPending || saveState !== 'saved'
  const blocker = useBlocker(({ currentLocation, nextLocation }) =>
    unsaved && currentLocation.pathname !== nextLocation.pathname)
  const previewDocument = serverDocument
  const readingLibrary = Boolean(documentId) && !previewDocument && !loadError && !busy

  const onUnsavedChange = useCallback((pending: boolean) => {
    editorPendingRef.current = pending
    setEditorPending(pending)
  }, [])

  const ensureSaved = useCallback(async () => {
    if (editorPendingRef.current) throw new Error('请先完成当前文字或公式编辑，再导出文档')
    await waitForSaves()
  }, [waitForSaves])

  useEffect(() => {
    const preventUnload = (event: BeforeUnloadEvent) => {
      if (unsaved || editorPendingRef.current) {
        event.preventDefault()
        event.returnValue = ''
      }
    }
    window.addEventListener('beforeunload', preventUnload)
    return () => window.removeEventListener('beforeunload', preventUnload)
  }, [unsaved])

  useEffect(() => {
    if (blocker.state === 'blocked' && !unsaved) blocker.proceed()
  }, [blocker, unsaved])

  useEffect(() => {
    if (job?.status !== 'completed' || !job.document) return
    if (completedJobIds.current.has(job.jobId)) return
    completedJobIds.current.add(job.jobId)
    void refreshList()
    navigate(`/documents/${job.document.documentId}`)
  }, [refreshList, job, navigate])

  useEffect(() => {
    if (pendingDownloadIdRef.current !== documentId || !previewDocument) return
    void rendererRef.current?.downloadDocx()
    pendingDownloadIdRef.current = null
  }, [documentId, previewDocument])

  const submit = async () => {
    if (!file || uploadingRef.current || busy || batchDownloading || unsaved) return
    uploadingRef.current = true
    setUploading(true)
    setError('')
    setJob(null)
    try {
      navigate('/')
      setJob(await parseDocument(file))
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : '上传失败')
    } finally {
      uploadingRef.current = false
      setUploading(false)
    }
  }

  const selectFile = (nextFile: File) => {
    if (uploadingRef.current || busy || batchDownloading) return
    if (!nextFile.name.toLowerCase().endsWith('.pdf')) {
      setFile(null)
      setJob(null)
      setError('仅支持 PDF 格式文件')
      return
    }
    if (nextFile.size > 200 * 1024 * 1024) {
      setFile(null)
      setError('文件大小超过 200 MB 限制')
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
    try {
      await ensureSaved()
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : '请先保存当前编辑')
      return
    }
    const item = items.find((entry) => entry.id === id)
    const label = item?.title || item?.sourceFileName || '该文档'
    if (!window.confirm(`删除「${label}」？文档将从服务端文档库隐藏。`)) return
    const deleted = await remove(id)
    if (deleted) {
      if (job?.document?.documentId === id) setJob(null)
      if (documentId === id) navigate('/')
    }
  }

  const downloadSelectedDocuments = async (ids: string[]) => {
    if (batchDownloadRef.current || ids.length === 0 || ids.length > 20) return
    batchDownloadRef.current = true
    setBatchDownloading(true)
    setBatchError('')
    setBatchStatus('正在等待编辑保存…')
    try {
      await ensureSaved()
      const documents: DocxExportInput[] = []
      // Read persisted content without navigating away from the current document.
      for (const [index, id] of ids.entries()) {
        setBatchStatus(`正在准备文档 ${index + 1}/${ids.length}…`)
        const { document } = await getLessonDocument(id)
        documents.push(await prepareDocxExport(document))
      }
      setBatchStatus(`正在生成 ${ids.length} 篇 DOCX 并打包…`)
      const archive = await exportDocumentsToZip(documents)
      downloadBlob(archive, 'lesson-documents.zip')
      setBatchStatus(`已生成 ${ids.length} 篇文档的 ZIP，已开始下载。`)
    } catch (reason) {
      setBatchStatus('')
      setBatchError(reason instanceof Error ? reason.message : '批量下载失败，请重试')
    } finally {
      batchDownloadRef.current = false
      setBatchDownloading(false)
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
            disabled={controlsDisabled || unsaved}
            busyLabel={uploading ? '上传中…' : batchDownloading ? '打包中…' : busy ? '解析中…' : '请先保存编辑'}
            onFile={selectFile}
            onClear={() => { setFile(null); setJob(null); setError('') }}
            onSubmit={() => void submit()}
          />

          {(job || error || uploading) && (
            <section className={`job-status ${job?.status ?? 'failed'}`} aria-live="polite">
              <div className="status-icon">
                {job?.status === 'completed' ? <Check size={18} /> : error || job?.status === 'failed' ? <AlertCircle size={18} /> : <FileSearch size={18} />}
              </div>
              <div>
                <strong>{error || polling.error || (uploading ? '正在上传文件' : job && statusCopy[job.status])}</strong>
                <span>{job?.error?.message ?? (busy ? '通常需要数十秒，请保持页面打开。' : job?.jobId)}</span>
                {polling.paused && <button type="button" onClick={polling.resume}>恢复查询</button>}
              </div>
            </section>
          )}

          {blocker.state === 'blocked' && (
            <section className="job-status failed" role="alert">
              <div>
                <strong>{saveState === 'saving' ? '正在保存，完成后自动跳转' : '当前文档有未保存更改'}</strong>
                <button type="button" onClick={() => blocker.reset()}>留在当前文档</button>
                {saveState !== 'saving' && <button type="button" onClick={() => {
                  onUnsavedChange(false)
                  blocker.proceed()
                }}>放弃未保存更改并离开</button>}
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
            disabled={controlsDisabled}
            batchDownloading={batchDownloading}
            batchStatus={batchStatus}
            batchError={batchError}
            onView={(id) => openDocument(id)}
            onEdit={(id) => openDocument(id, 'edit')}
            onDownload={downloadDocument}
            onBatchDownload={(ids) => void downloadSelectedDocuments(ids)}
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
              key={`${previewDocument.documentId}-${loadVersion}`}
              ref={rendererRef}
              document={previewDocument}
              editable={editing}
              onEditableChange={(next) => openDocument(previewDocument.documentId, next ? 'edit' : 'view')}
              onDocumentChange={saveEdits}
              onUnsavedChange={onUnsavedChange}
              saveState={saveState}
              saveError={saveError}
              onRetrySave={retrySave}
              onReload={() => {
                if (!unsaved || window.confirm('放弃未保存的更改并重新加载服务端版本？')) {
                  onUnsavedChange(false)
                  reloadDocument()
                }
              }}
              getPersistedDocument={async () => {
                await ensureSaved()
                return (await getLessonDocument(previewDocument.documentId)).document
              }}
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
