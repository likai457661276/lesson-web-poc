import katex from 'katex'
import { useEffect, useId, useMemo, useState } from 'react'
import { validateFormula, type FormulaValidationResult } from '../../api/documents'

interface EditableFormulaProps {
  latex: string
  editable: boolean
  displayMode?: boolean
  onChange?: (latex: string) => void
  onDraftChange?: (id: string, dirty: boolean) => void
}

function renderLatex(latex: string, displayMode: boolean): { html: string; error: string } {
  try {
    return {
      html: katex.renderToString(latex, {
        displayMode,
        throwOnError: true,
        strict: false,
      }),
      error: '',
    }
  } catch (reason) {
    return {
      html: '',
      error: reason instanceof Error ? reason.message : 'KaTeX 无法渲染该公式',
    }
  }
}

export function EditableFormula({ latex: initialLatex, editable, displayMode = false, onChange, onDraftChange }: EditableFormulaProps) {
  const editorId = useId()
  const [latex, setLatex] = useState(initialLatex)
  const [draft, setDraft] = useState(initialLatex)
  const [editing, setEditing] = useState(false)
  const [checking, setChecking] = useState(false)
  const [validation, setValidation] = useState<FormulaValidationResult | null>(null)
  const [requestError, setRequestError] = useState('')
  const rendered = useMemo(() => renderLatex(latex, displayMode), [displayMode, latex])
  const preview = useMemo(() => renderLatex(draft, displayMode), [displayMode, draft])

  useEffect(() => { onDraftChange?.(editorId, checking || (editing && draft !== latex)) }, [draft, editing, checking, latex, editorId, onDraftChange])
  useEffect(() => () => onDraftChange?.(editorId, false), [editorId, onDraftChange])

  const save = async () => {
    if (!editable || checking || preview.error || !draft.trim()) return
    setChecking(true)
    setRequestError('')
    try {
      const result = await validateFormula(draft)
      const nextLatex = result.normalizedLatex || draft.trim()
      setValidation(result)
      setLatex(nextLatex)
      setDraft(nextLatex)
      setEditing(false)
      onChange?.(nextLatex)
    } catch (reason) {
      setRequestError(reason instanceof Error ? reason.message : '公式校验失败')
    } finally {
      setChecking(false)
    }
  }

  if (!editable) {
    return <span className={`editable-formula-shell ${displayMode ? 'is-display' : ''}`} data-latex={latex}>
      {rendered.error ? <code>{latex}</code> : <span dangerouslySetInnerHTML={{ __html: rendered.html }} />}
    </span>
  }

  return (
    <span className={`editable-formula-shell ${displayMode ? 'is-display' : ''}`} contentEditable={false}>
      <button
        className={`editable-formula ${rendered.error ? 'has-error' : ''}`}
        data-latex={latex}
        type="button"
        aria-label={`编辑公式：${latex}`}
        title="点击编辑 LaTeX"
        onClick={() => { setDraft(latex); setEditing(true); setRequestError('') }}
      >
        {rendered.error ? <code>{latex}</code> : <span dangerouslySetInnerHTML={{ __html: rendered.html }} />}
        <span className="formula-edit-hint">编辑</span>
      </button>

      {validation && !editing && (
        <span className={`formula-validation-badge ${validation.parseable ? 'is-valid' : 'needs-review'}`}>
          {validation.parseable ? '结构已校验' : '需人工复核'}
        </span>
      )}

      {editing && (
        <span className="formula-editor" role="dialog" aria-label="LaTeX 公式编辑器">
          <span className="formula-editor-heading">编辑 LaTeX</span>
          <textarea
            aria-label="LaTeX 代码"
            value={draft}
            rows={3}
            spellCheck={false}
            disabled={checking}
            onChange={(event) => setDraft(event.target.value)}
          />
          <span className={`formula-live-preview ${preview.error ? 'has-error' : ''}`}>
            {preview.error ? preview.error : <span dangerouslySetInnerHTML={{ __html: preview.html }} />}
          </span>
          {requestError && <span className="formula-request-error">{requestError}</span>}
          <span className="formula-editor-note">保存时由后端检查结构；OCR 结果仍需结合原页人工复核。</span>
          <span className="formula-editor-actions">
            <button type="button" disabled={checking} onClick={() => setEditing(false)}>取消</button>
            <button type="button" disabled={Boolean(preview.error) || checking || !draft.trim()} onClick={save}>
              {checking ? '校验中…' : '校验并应用'}
            </button>
          </span>
        </span>
      )}
    </span>
  )
}
