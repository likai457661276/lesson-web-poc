import { act, renderHook, waitFor } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiError, getLessonDocument, listLessonDocuments, updateLessonDocument, type DocumentSnapshot } from '../api/documents'
import { document, deferred } from '../test/fixtures'
import { useDocumentLibrary } from './useDocumentLibrary'

vi.mock('../api/documents', async (original) => ({
  ...await original<typeof import('../api/documents')>(),
  getLessonDocument: vi.fn(), listLessonDocuments: vi.fn(), updateLessonDocument: vi.fn(),
}))

beforeEach(() => {
  vi.resetAllMocks()
  vi.mocked(getLessonDocument).mockResolvedValue({ document, etag: 'v1' })
  vi.mocked(listLessonDocuments).mockResolvedValue([])
})

async function loadedLibrary() {
  const hook = renderHook(() => useDocumentLibrary(document.documentId))
  await waitFor(() => expect(hook.result.current.document).toEqual(document))
  return hook
}

describe('document saves', () => {
  it('serializes saves using each acknowledged ETag and retains the newest draft', async () => {
    const first = deferred<DocumentSnapshot>()
    const second = deferred<DocumentSnapshot>()
    vi.mocked(updateLessonDocument).mockReturnValueOnce(first.promise).mockReturnValueOnce(second.promise)
    const { result } = await loadedLibrary()
    const edit1 = { ...document, title: 'one' }
    const edit2 = { ...document, title: 'two' }
    act(() => result.current.saveEdits(edit1))
    act(() => result.current.saveEdits(edit2))
    expect(updateLessonDocument).toHaveBeenCalledTimes(1)
    await act(async () => first.resolve({ document: edit1, etag: 'v2' }))
    expect(updateLessonDocument).toHaveBeenNthCalledWith(2, edit2, 'v2')
    expect(result.current.document?.title).toBe('two')
    await act(async () => second.resolve({ document: edit2, etag: 'v3' }))
    await result.current.waitForSaves()
    expect(result.current.saveState).toBe('saved')
  })

  it('keeps failed drafts, rejects export waits, and retries only explicitly', async () => {
    const { result } = await loadedLibrary()
    vi.mocked(updateLessonDocument).mockRejectedValueOnce(new Error('offline'))
    act(() => result.current.saveEdits({ ...document, title: 'unsaved' }))
    await waitFor(() => expect(result.current.saveState).toBe('failed'))
    await expect(result.current.waitForSaves()).rejects.toThrow('offline')
    act(() => result.current.saveEdits({ ...document, title: 'latest' }))
    expect(updateLessonDocument).toHaveBeenCalledTimes(1)
    expect(result.current.document?.title).toBe('latest')
    vi.mocked(updateLessonDocument).mockResolvedValueOnce({ document: { ...document, title: 'latest' }, etag: 'v2' })
    act(() => result.current.retrySave())
    await waitFor(() => expect(result.current.saveState).toBe('saved'))
    expect(updateLessonDocument).toHaveBeenLastCalledWith({ ...document, title: 'latest' }, 'v1')
  })

  it('never rebases a conflict automatically and reload discards the draft explicitly', async () => {
    const { result } = await loadedLibrary()
    vi.mocked(updateLessonDocument).mockRejectedValueOnce(new ApiError('conflict', 409))
    act(() => result.current.saveEdits({ ...document, title: 'mine' }))
    await waitFor(() => expect(result.current.saveState).toBe('conflict'))
    act(() => result.current.retrySave())
    expect(updateLessonDocument).toHaveBeenCalledTimes(1)
    vi.mocked(getLessonDocument).mockResolvedValueOnce({ document: { ...document, title: 'theirs' }, etag: 'v2' })
    act(() => result.current.reloadDocument())
    await waitFor(() => expect(result.current.document?.title).toBe('theirs'))
    expect(result.current.saveState).toBe('saved')
  })

  it('does not classify a list refresh failure as a failed save', async () => {
    const { result } = await loadedLibrary()
    vi.mocked(listLessonDocuments).mockRejectedValueOnce(new Error('list offline'))
    vi.mocked(updateLessonDocument).mockResolvedValueOnce({ document, etag: 'v2' })
    act(() => result.current.saveEdits(document))
    await waitFor(() => expect(result.current.libraryError).toBe('list offline'))
    expect(result.current.saveState).toBe('saved')
    await expect(result.current.waitForSaves()).resolves.toBeUndefined()
  })
})
