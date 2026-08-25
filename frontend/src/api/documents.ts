import type { ParseJob } from '../types/lesson-document'

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
