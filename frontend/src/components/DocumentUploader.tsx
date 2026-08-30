import { FileText, LoaderCircle, Upload, X } from 'lucide-react'
import { useRef, useState } from 'react'

interface Props {
  file: File | null
  disabled: boolean
  busyLabel?: string
  onFile: (file: File) => void
  onClear: () => void
  onSubmit: () => void
}

const ACCEPT = 'application/pdf,.pdf'

export function DocumentUploader({ file, disabled, busyLabel = '处理中…', onFile, onClear, onSubmit }: Props) {
  const inputRef = useRef<HTMLInputElement>(null)
  const [dragging, setDragging] = useState(false)

  return (
    <section className="upload-panel" aria-labelledby="upload-heading">
      <div className="section-kicker">01 / 输入</div>
      <div className="section-heading-row">
        <div>
          <h2 id="upload-heading">上传教案</h2>
          <p>仅支持 PDF 格式，单文件最大 200 MB。</p>
        </div>
        <span className="format-note">MinerU · VLM</span>
      </div>
      <div
        className={`drop-zone ${dragging ? 'is-dragging' : ''} ${file ? 'has-file' : ''}`}
        onDragEnter={(event) => { event.preventDefault(); setDragging(true) }}
        onDragOver={(event) => event.preventDefault()}
        onDragLeave={() => setDragging(false)}
        onDrop={(event) => {
          event.preventDefault()
          setDragging(false)
          if (disabled) return
          const nextFile = event.dataTransfer.files[0]
          if (nextFile) onFile(nextFile)
        }}
      >
        <input
          ref={inputRef}
          type="file"
          accept={ACCEPT}
          hidden
          disabled={disabled}
          onChange={(event) => {
            const nextFile = event.target.files?.[0]
            if (nextFile && !disabled) onFile(nextFile)
            event.target.value = ''
          }}
        />
        {file ? (
          <>
            <div className="file-mark"><FileText size={22} /></div>
            <div className="file-copy">
              <strong>{file.name}</strong>
              <span>{(file.size / 1024 / 1024).toFixed(2)} MB · 准备解析</span>
            </div>
            <button type="button" className="icon-button" onClick={onClear} disabled={disabled} aria-label="移除文件">
              <X size={18} />
            </button>
          </>
        ) : (
          <button type="button" className="drop-trigger" disabled={disabled} onClick={() => inputRef.current?.click()}>
            <span className="upload-icon"><Upload size={24} /></span>
            <strong>拖入 PDF 文件，或点击选择</strong>
            <span>源文件仅用于本次 PoC 解析</span>
          </button>
        )}
      </div>
      <button type="button" className="primary-button" onClick={onSubmit} disabled={!file || disabled}>
        {disabled ? <LoaderCircle className="spin" size={18} /> : <Upload size={18} />}
        {disabled ? busyLabel : '开始解析'}
      </button>
    </section>
  )
}
