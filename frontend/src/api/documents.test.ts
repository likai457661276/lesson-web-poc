import { afterEach, expect, it, vi } from 'vitest'
import { ApiError, getLessonDocument, updateLessonDocument } from './documents'
import { document } from '../test/fixtures'

afterEach(() => vi.unstubAllGlobals())

it('reads the ETag and submits it unchanged through If-Match', async () => {
  const fetch = vi.fn().mockResolvedValue(new Response(JSON.stringify(document), {
    status: 200, headers: { 'Content-Type': 'application/json', ETag: '"revision"' },
  }))
  vi.stubGlobal('fetch', fetch)
  const result = await getLessonDocument(document.documentId)
  expect(result).toEqual({ document, etag: '"revision"' })
  fetch.mockResolvedValueOnce(new Response(JSON.stringify(document), { headers: { ETag: '"next"' } }))
  await updateLessonDocument(document, result.etag)
  expect(fetch).toHaveBeenLastCalledWith('/api/lesson-documents/doc-1', expect.objectContaining({
    method: 'PUT', headers: { 'Content-Type': 'application/json', 'If-Match': '"revision"' },
    signal: expect.any(AbortSignal),
  }))
})

it('fails closed if the server does not provide an ETag', async () => {
  vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(JSON.stringify(document))))
  await expect(getLessonDocument(document.documentId)).rejects.toThrow('ETag')
})

it('preserves conflict status and validation error details for the UI', async () => {
  const fetch = vi.fn().mockResolvedValueOnce(new Response(JSON.stringify({ error: { message: 'conflict' } }), { status: 409 }))
    .mockResolvedValueOnce(new Response(JSON.stringify({ detail: [{ msg: 'invalid heading' }] }), { status: 422 }))
  vi.stubGlobal('fetch', fetch)
  await expect(updateLessonDocument(document, 'v1')).rejects.toEqual(new ApiError('conflict', 409))
  await expect(updateLessonDocument(document, 'v1')).rejects.toThrow('invalid heading')
})
