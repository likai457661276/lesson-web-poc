import type { LessonDocument, LessonDocumentSummary, ParseJob } from '../types/lesson-document'

export interface FormulaValidationResult {
  latex: string
  normalizedLatex: string
  parseable: boolean
  symbolicExpression?: string | null
  equivalentToReference?: boolean | null
  message: string
}

export class ApiError extends Error {
  status: number
  constructor(message: string, status: number) {
    super(message)
    this.status = status
  }
}

export interface DocumentSnapshot {
  document: LessonDocument
  etag: string
}

async function request(path: string, options: RequestInit = {}, timeoutMs = 30_000): Promise<Response> {
  const signals = [AbortSignal.timeout(timeoutMs)]
  if (options.signal) signals.push(options.signal)
  const response = await fetch(path, { ...options, signal: AbortSignal.any(signals) })
  if (!response.ok) throw new ApiError(await parseError(response), response.status)
  return response
}

async function parseError(response: Response): Promise<string> {
  const payload = await response.json().catch(() => null)
  return payload?.error?.message ?? payload?.detail?.[0]?.msg ?? `请求失败（HTTP ${response.status}）`
}

async function snapshot(response: Response): Promise<DocumentSnapshot> {
  const etag = response.headers.get('ETag')
  if (!etag) throw new Error('服务端未返回文档 ETag，无法安全编辑')
  return { document: await response.json(), etag }
}

export async function parseDocument(file: File): Promise<ParseJob> {
  const body = new FormData()
  body.append('file', file)
  const response = await request('/api/documents/parse', { method: 'POST', body }, 600_000)
  return response.json()
}

export async function getParseJob(jobId: string, signal?: AbortSignal): Promise<ParseJob> {
  const response = await request(`/api/documents/${jobId}`, { signal })
  return response.json()
}

export async function listLessonDocuments(): Promise<LessonDocumentSummary[]> {
  const response = await request('/api/lesson-documents')
  return response.json()
}

export async function getLessonDocument(id: string): Promise<DocumentSnapshot> {
  return snapshot(await request(`/api/lesson-documents/${id}`, { cache: 'no-store' }))
}

export async function updateLessonDocument(document: LessonDocument, etag: string): Promise<DocumentSnapshot> {
  const response = await request(`/api/lesson-documents/${document.documentId}`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json', 'If-Match': etag },
    body: JSON.stringify(document),
  })
  return snapshot(response)
}

export async function deleteLessonDocument(id: string): Promise<void> {
  await request(`/api/lesson-documents/${id}`, { method: 'DELETE' })
}

export async function validateFormula(latex: string): Promise<FormulaValidationResult> {
  const response = await request('/api/formulas/validate', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ latex }),
  })
  return response.json()
}

export async function exportHtmlToDocx(html: string, filename: string): Promise<Blob> {
  const response = await request('/api/documents/export-docx', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ html, filename }),
  }, 120_000)
  return response.blob()
}

export interface DocxExportInput {
  html: string
  filename: string
}

export async function exportDocumentsToZip(documents: DocxExportInput[]): Promise<Blob> {
  const response = await request('/api/documents/export-docx-batch', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ documents }),
  }, 600_000)
  return response.blob()
}
