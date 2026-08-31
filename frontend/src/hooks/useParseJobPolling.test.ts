import { act, renderHook } from '@testing-library/react'
import { useState } from 'react'
import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import { getParseJob } from '../api/documents'
import type { ParseJob } from '../types/lesson-document'
import { useParseJobPolling } from './useParseJobPolling'

vi.mock('../api/documents', async (original) => ({
  ...await original<typeof import('../api/documents')>(), getParseJob: vi.fn(),
}))
const pending: ParseJob = { jobId: 'job-1', status: 'pending', sourceFileName: 'a.pdf', createdAt: '' }
beforeEach(() => { vi.resetAllMocks(); vi.useFakeTimers() })
afterEach(() => vi.useRealTimers())
function pollingHook() {
  return renderHook(() => {
    const [job, setJob] = useState(pending)
    return { job, polling: useParseJobPolling(job, setJob) }
  })
}

it('continues polling after one network failure and stops after completion', async () => {
  vi.mocked(getParseJob).mockRejectedValueOnce(new Error('offline'))
    .mockResolvedValueOnce({ ...pending, status: 'processing' })
    .mockResolvedValueOnce({ ...pending, status: 'completed' })
  const { result, unmount } = pollingHook()
  await act(async () => { await vi.advanceTimersByTimeAsync(0) })
  expect(result.current.polling.error).toContain('重试')
  await act(async () => { await vi.advanceTimersByTimeAsync(3600) })
  expect(result.current.job.status).toBe('processing')
  await act(async () => { await vi.advanceTimersByTimeAsync(1800) })
  expect(result.current.job.status).toBe('completed')
  await act(async () => { await vi.advanceTimersByTimeAsync(10000) })
  expect(getParseJob).toHaveBeenCalledTimes(3)
  unmount()
})

it('pauses after three failures and allows resuming the same job', async () => {
  vi.mocked(getParseJob).mockRejectedValue(new Error('offline'))
  const { result, unmount } = pollingHook()
  await act(async () => { await vi.advanceTimersByTimeAsync(1800 + 3600 + 7200) })
  expect(result.current.polling.paused).toBe(true)
  expect(getParseJob).toHaveBeenCalledTimes(3)
  vi.mocked(getParseJob).mockResolvedValue({ ...pending, status: 'completed' })
  act(() => result.current.polling.resume())
  await act(async () => { await vi.advanceTimersByTimeAsync(1800) })
  expect(result.current.job.status).toBe('completed')
  unmount()
})

it('aborts in-flight requests when unmounted', async () => {
  vi.mocked(getParseJob).mockImplementation(() => new Promise(() => {}))
  const { unmount } = pollingHook()
  await act(async () => { await vi.advanceTimersByTimeAsync(1800) })
  const signal = vi.mocked(getParseJob).mock.calls[0][1]!
  unmount()
  expect(signal.aborted).toBe(true)
})
