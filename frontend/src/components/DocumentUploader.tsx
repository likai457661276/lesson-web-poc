import { FileText, LoaderCircle, Upload, X } from 'lucide-react'
import { useRef, useState } from 'react'

interface Props {
  file: File | null
  disabled: boolean
  onFile: (file: File) => void
  onClear: () => void
  onSubmit: () => void
}

const ACCEPT = '.pdf,.png,.jpg,.jpeg,.jp2,.webp,.gif,.bmp,.ppt,.pptx,.xls,.xlsx'

export function DocumentUploader({ file, disabled, onFile, onClear, onSubmit }: Props) {
  const inputRef = useRef<HTMLInputElement>(null)
  const [dragging, setDragging] = useState(false)

  return (
    <section className="upload-panel" aria-labelledby="upload-heading">
      <div className="section-kicker">01 / 输入</div>
      <div className="section-heading-row">
        <div>
          <h2 id="upload-heading">上传教案</h2>
          <p>支持 PDF、PPT、Excel 与常见图片，单文件最大 200 MB。</p>
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
          const nextFile = event.dataTransfer.files[0]
          if (nextFile) onFile(nextFile)
        }}
      >
        <input
          ref={inputRef}
          type="file"
          accept={ACCEPT}
          hidden
          onChange={(event) => {
            const nextFile = event.target.files?.[0]
            if (nextFile) onFile(nextFile)
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
          <button type="button" className="drop-trigger" onClick={() => inputRef.current?.click()}>
            <span className="upload-icon"><Upload size={24} /></span>
            <strong>拖入文件，或点击选择</strong>
            <span>源文件仅用于本次 PoC 解析</span>
          </button>
        )}
      </div>
      <button type="button" className="primary-button" onClick={onSubmit} disabled={!file || disabled}>
        {disabled ? <LoaderCircle className="spin" size={18} /> : <Upload size={18} />}
        {disabled ? '解析中' : '开始解析'}
      </button>
    </section>
  )
}
