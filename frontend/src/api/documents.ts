import type { ParseJob } from '../types/lesson-document'

export interface FormulaValidationResult {
  latex: string
  normalizedLatex: string
  parseable: boolean
  symbolicExpression?: string | null
  equivalentToReference?: boolean | null
  message: string
}

async function parseError(response: Response): Promise<string> {
  const payload = await response.json().catch(() => null)
  return payload?.error?.message ?? `请求失败（HTTP ${response.status}）`
}

export async function parseDocument(file: File): Promise<ParseJob> {
  const body = new FormData()
  body.append('file', file)
  const response = await fetch('/api/documents/parse', { method: 'POST', body })
  if (!response.ok) throw new Error(await parseError(response))
  return response.json()
}

export async function getParseJob(jobId: string): Promise<ParseJob> {
  const response = await fetch(`/api/documents/${jobId}`)
  if (!response.ok) throw new Error(await parseError(response))
  return response.json()
}

export async function validateFormula(latex: string): Promise<FormulaValidationResult> {
  const response = await fetch('/api/formulas/validate', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ latex }),
  })
  if (!response.ok) throw new Error(await parseError(response))
  return response.json()
}

export async function exportHtmlToDocx(html: string, filename: string): Promise<Blob> {
  const response = await fetch('/api/documents/export-docx', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ html, filename }),
  })
  if (!response.ok) throw new Error(await parseError(response))
  return response.blob()
}
