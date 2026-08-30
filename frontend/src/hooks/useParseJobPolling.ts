import { useEffect, useState } from 'react'
import { ApiError, getParseJob } from '../api/documents'
import type { ParseJob } from '../types/lesson-document'

export function useParseJobPolling(job: ParseJob | null, onJob: (job: ParseJob) => void) {
  const [error, setError] = useState('')
  const [paused, setPaused] = useState(false)
  const [attempt, setAttempt] = useState(0)
  const jobId = job?.jobId
  const busy = job?.status === 'pending' || job?.status === 'processing'

  useEffect(() => {
    // oxlint-disable-next-line react/set-state-in-effect -- Reset network retry state when the task or manual polling attempt changes.
    setError('')
    setPaused(false)
    if (!jobId || !busy) return
    const controller = new AbortController()
    let timer: number
    let failures = 0
    const poll = async () => {
      try {
        const next = await getParseJob(jobId, controller.signal)
        if (controller.signal.aborted) return
        failures = 0
        setError('')
        onJob(next)
        if (next.status === 'pending' || next.status === 'processing') timer = window.setTimeout(poll, 1800)
      } catch (reason) {
        if (controller.signal.aborted) return
        failures++
        const terminal = reason instanceof ApiError && reason.status >= 400 && reason.status < 500
          && reason.status !== 408 && reason.status !== 429
        if (terminal || failures >= 3) {
          setPaused(true)
          setError('状态查询已暂停；后台任务可能仍在处理，可点击恢复查询。')
        } else {
          setError(`状态查询失败，正在重试（${failures}/3）…`)
          timer = window.setTimeout(poll, 1800 * 2 ** failures)
        }
      }
    }
    timer = window.setTimeout(poll, 1800)
    return () => { controller.abort(); window.clearTimeout(timer) }
  }, [jobId, busy, onJob, attempt])

  return { error, paused, resume: () => setAttempt((value) => value + 1) }
}
