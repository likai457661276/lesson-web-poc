import { AlertCircle, Check, FileSearch, ShieldCheck } from 'lucide-react'
import { useEffect, useState } from 'react'
import { getParseJob, parseDocument } from '../api/documents'
import { DocumentUploader } from '../components/DocumentUploader'
import { LessonRenderer } from '../components/LessonRenderer/LessonRenderer'
import type { ParseJob } from '../types/lesson-document'

const statusCopy = {
  pending: '任务已创建',
  processing: 'MinerU 正在识别内容结构',
  completed: '解析完成',
  failed: '解析失败',
}

export function HomePage() {
  const [file, setFile] = useState<File | null>(null)
  const [job, setJob] = useState<ParseJob | null>(null)
  const [error, setError] = useState('')
  const busy = job?.status === 'pending' || job?.status === 'processing'

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

  const submit = async () => {
    if (!file) return
    setError('')
    try {
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

  return (
    <main>
      <header className="app-header">
        <a className="brand" href="/" aria-label="Lesson Web 首页">
          <span className="brand-mark">L</span>
          <span><strong>Lesson Web</strong><small>Document Pipeline PoC</small></span>
        </a>
        <div className="system-status"><span /> API · MinerU</div>
      </header>

      <div className="workspace">
        <aside className="control-rail">
          <div className="intro-copy">
            <span className="eyebrow">文档标准化工作台</span>
            <h1>把教案转换为可渲染的 Web 文档。</h1>
            <p>上传源文件，经过 MinerU 解析与适配后，统一输出 LessonDocument v1。</p>
          </div>
          <DocumentUploader
            file={file}
            disabled={Boolean(busy)}
            onFile={selectFile}
            onClear={() => { setFile(null); setJob(null); setError('') }}
            onSubmit={submit}
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

          <div className="privacy-note"><ShieldCheck size={16} /><span>密钥仅由后端环境变量读取，不进入浏览器或仓库。</span></div>
        </aside>

        <section className="preview-stage" aria-labelledby="preview-heading">
          <div className="preview-toolbar">
            <div><span className="section-kicker">02 / 输出</span><h2 id="preview-heading">Web 教案预览</h2></div>
            {job?.document && <span className="block-count">{job.document.blocks.length} 个内容块</span>}
          </div>
          {job?.document ? (
            <LessonRenderer document={job.document} />
          ) : (
            <div className={`empty-preview ${busy ? 'is-processing' : ''}`}>
              <div className="paper-ghost"><span /><span /><span /><span /></div>
              <strong>{busy ? '正在建立文档结构' : '等待教案文件'}</strong>
              <p>{busy ? '识别标题、段落、表格、图片与公式…' : '完成解析后，LessonDocument 将在这里逐块渲染。'}</p>
            </div>
          )}
        </section>
      </div>
    </main>
  )
}
