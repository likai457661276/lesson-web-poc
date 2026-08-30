import type { LessonDocument, LessonDocumentSummary, ParseJob } from '../types/lesson-document'

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

export async function listLessonDocuments(): Promise<LessonDocumentSummary[]> {
  const response = await fetch('/api/lesson-documents')
  if (!response.ok) throw new Error(await parseError(response))
  return response.json()
}

export async function getLessonDocument(id: string): Promise<LessonDocument> {
  const response = await fetch(`/api/lesson-documents/${id}`)
  if (!response.ok) throw new Error(await parseError(response))
  return response.json()
}

export async function updateLessonDocument(document: LessonDocument): Promise<LessonDocument> {
  const response = await fetch(`/api/lesson-documents/${document.documentId}`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(document),
  })
  if (!response.ok) throw new Error(await parseError(response))
  return response.json()
}

export async function deleteLessonDocument(id: string): Promise<void> {
  const response = await fetch(`/api/lesson-documents/${id}`, { method: 'DELETE' })
  if (!response.ok) throw new Error(await parseError(response))
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

export interface DocxExportInput {
  html: string
  filename: string
}

export async function exportDocumentsToZip(documents: DocxExportInput[]): Promise<Blob> {
  const response = await fetch('/api/documents/export-docx-batch', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ documents }),
  })
  if (!response.ok) throw new Error(await parseError(response))
  return response.blob()
}
