import { act, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { createMemoryRouter, RouterProvider } from 'react-router-dom'
import { beforeEach, expect, it, vi } from 'vitest'
import App from '../App'
import { ApiError, getParseJob, getLessonDocument, listLessonDocuments, parseDocument, updateLessonDocument, exportHtmlToDocx, type DocumentSnapshot } from '../api/documents'
import { deferred, document } from '../test/fixtures'
import type { ParseJob } from '../types/lesson-document'

vi.mock('../api/documents', async (original) => ({
  ...await original<typeof import('../api/documents')>(),
  getLessonDocument: vi.fn(), listLessonDocuments: vi.fn(), parseDocument: vi.fn(),
  updateLessonDocument: vi.fn(), exportHtmlToDocx: vi.fn(),
  getParseJob: vi.fn(),
}))
vi.mock('../components/LessonRenderer/documentExport', async (original) => ({
  ...await original<typeof import('../components/LessonRenderer/documentExport')>(), downloadBlob: vi.fn(),
}))

beforeEach(() => {
  vi.resetAllMocks()
  vi.mocked(listLessonDocuments).mockResolvedValue([])
  vi.mocked(getLessonDocument).mockResolvedValue({ document, etag: 'v1' })
  vi.mocked(getParseJob).mockResolvedValue({ jobId: 'job-1', status: 'processing', sourceFileName: 'a.pdf', createdAt: '' })
})

function page(path = '/') {
  const router = createMemoryRouter([{ path: '*', element: <App /> }], { initialEntries: [path] })
  const result = render(<RouterProvider router={router} />)
  return { ...result, router }
}

it('locks submission immediately until upload finishes, including drop replacement', async () => {
  const upload = deferred<ParseJob>()
  vi.mocked(parseDocument).mockReturnValue(upload.promise)
  const { container } = page()
  const file = new File(['%PDF-1.7'], 'a.pdf', { type: 'application/pdf' })
  fireEvent.change(container.querySelector('input[type=file]')!, { target: { files: [file] } })
  const submit = screen.getByRole('button', { name: '开始解析' })
  fireEvent.click(submit)
  fireEvent.click(submit)
  fireEvent.drop(container.querySelector('.drop-zone')!, { dataTransfer: { files: [new File(['x'], 'b.pdf')] } })
  expect(parseDocument).toHaveBeenCalledTimes(1)
  expect(parseDocument).toHaveBeenCalledWith(file)
  expect((screen.getByRole('button', { name: '上传中…' }) as HTMLButtonElement).disabled).toBe(true)
  await act(async () => upload.reject(new Error('upload offline')))
  expect((screen.getByRole('button', { name: '开始解析' }) as HTMLButtonElement).disabled).toBe(false)
})

it('retains failed edits, blocks navigation, and refuses to export an unsaved draft', async () => {
  vi.mocked(updateLessonDocument).mockRejectedValue(new Error('save offline'))
  const { router } = page('/documents/doc-1/edit')
  const title = await screen.findByRole('heading', { name: 'Draft' })
  title.textContent = 'Unsaved title'
  fireEvent.input(title)
  fireEvent.blur(title)
  await screen.findByText('未保存：save offline')
  fireEvent.click(screen.getByRole('button', { name: '下载 DOCX' }))
  await waitFor(() => expect(screen.getByText('save offline')).toBeTruthy())
  expect(exportHtmlToDocx).not.toHaveBeenCalled()
  await act(async () => { await router.navigate('/') })
  expect(router.state.location.pathname).toBe('/documents/doc-1/edit')
  expect(screen.getByRole('button', { name: '留在当前文档' })).toBeTruthy()
  fireEvent.click(screen.getByRole('button', { name: '留在当前文档' }))
  expect(screen.getByRole('heading', { name: 'Unsaved title' })).toBeTruthy()
})

it('waits for pending saves before navigation and exports persisted content', async () => {
  const saving = deferred<DocumentSnapshot>()
  vi.mocked(updateLessonDocument).mockReturnValue(saving.promise)
  vi.mocked(exportHtmlToDocx).mockResolvedValue(new Blob(['docx']))
  const { router } = page('/documents/doc-1/edit')
  const title = await screen.findByRole('heading', { name: 'Draft' })
  title.textContent = 'Saved title'
  fireEvent.input(title)
  fireEvent.blur(title)
  fireEvent.click(screen.getByRole('button', { name: '下载 DOCX' }))
  expect(exportHtmlToDocx).not.toHaveBeenCalled()
  await act(async () => { await router.navigate('/documents/doc-1') })
  expect(router.state.location.pathname).toBe('/documents/doc-1/edit')
  const saved = { ...document, title: 'Saved title' }
  vi.mocked(getLessonDocument).mockResolvedValue({ document: saved, etag: 'v2' })
  await act(async () => saving.resolve({ document: saved, etag: 'v2' }))
  await waitFor(() => expect(router.state.location.pathname).toBe('/documents/doc-1'))
  await waitFor(() => expect(exportHtmlToDocx).toHaveBeenCalledWith(expect.any(String), 'Saved title.docx'))
})

it('warns before unloading an active uncommitted edit', async () => {
  page('/documents/doc-1/edit')
  const title = await screen.findByRole('heading', { name: 'Draft' })
  title.textContent = 'Not yet blurred'
  fireEvent.input(title)
  const event = new Event('beforeunload', { cancelable: true })
  window.dispatchEvent(event)
  expect(event.defaultPrevented).toBe(true)
  expect(updateLessonDocument).not.toHaveBeenCalled()
})

it('keeps the job URL after upload and restores completion after a full remount', async () => {
  vi.mocked(parseDocument).mockResolvedValue({ jobId: 'job-1', status: 'pending', sourceFileName: 'a.pdf', createdAt: '' })
  const first = page()
  fireEvent.change(first.container.querySelector('input[type=file]')!, {
    target: { files: [new File(['%PDF-1.7'], 'a.pdf', { type: 'application/pdf' })] },
  })
  fireEvent.click(screen.getByRole('button', { name: '开始解析' }))
  await waitFor(() => expect(first.router.state.location.pathname).toBe('/jobs/job-1'))
  first.unmount()
  vi.mocked(getParseJob).mockResolvedValue({ jobId: 'job-1', status: 'completed', sourceFileName: 'a.pdf', createdAt: '', document })
  const restored = page('/jobs/job-1')
  await waitFor(() => expect(restored.router.state.location.pathname).toBe('/documents/doc-1'))
  expect(await screen.findByRole('heading', { name: 'Draft' })).toBeTruthy()
  expect(getParseJob).toHaveBeenCalledWith('job-1', expect.any(AbortSignal))
  await act(async () => { await restored.router.navigate('/jobs/job-1') })
  await waitFor(() => expect(restored.router.state.location.pathname).toBe('/documents/doc-1'))
})

it('shows failed or missing jobs with a route back to uploading', async () => {
  vi.mocked(getParseJob).mockResolvedValue({ jobId: 'failed', status: 'failed', sourceFileName: 'a.pdf', createdAt: '', error: { code: 'MINERU_PARSE_FAILED', message: '解析超时' } })
  const failed = page('/jobs/failed')
  expect(await screen.findByText('解析超时')).toBeTruthy()
  failed.unmount()
  vi.mocked(getParseJob).mockRejectedValue(new ApiError('任务不存在', 404))
  page('/jobs/missing')
  expect(await screen.findByRole('button', { name: '恢复查询' })).toBeTruthy()
  expect(screen.getByRole('link', { name: '返回上传页' }).getAttribute('href')).toBe('/')
})
