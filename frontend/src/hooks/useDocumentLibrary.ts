import { useCallback, useEffect, useRef, useState } from 'react'
import {
  ApiError,
  deleteLessonDocument,
  getLessonDocument,
  listLessonDocuments,
  updateLessonDocument,
  type DocumentSnapshot,
} from '../api/documents'
import type { LessonDocument, LessonDocumentSummary } from '../types/lesson-document'

type SaveState = 'saved' | 'saving' | 'failed' | 'conflict'

export function useDocumentLibrary(documentId: string | undefined) {
  const [items, setItems] = useState<LessonDocumentSummary[]>([])
  const [listReady, setListReady] = useState(false)
  const [document, setDocument] = useState<LessonDocument | null>(null)
  const [loadError, setLoadError] = useState('')
  const [libraryError, setLibraryError] = useState('')
  const [saveError, setSaveError] = useState('')
  const [saveState, setSaveState] = useState<SaveState>('saved')
  const [loadVersion, setLoadVersion] = useState(0)
  const snapshotRef = useRef<DocumentSnapshot | null>(null)
  const pendingRef = useRef<LessonDocument | null>(null)
  const savingRef = useRef<Promise<void> | null>(null)
  const failureRef = useRef<Error | null>(null)

  const refreshList = useCallback(async () => {
    try {
      setItems(await listLessonDocuments())
      setLibraryError('')
    } catch (reason) {
      setLibraryError(reason instanceof Error ? reason.message : '无法读取服务端文档库')
    } finally {
      setListReady(true)
    }
  }, [])

  // oxlint-disable-next-line react/set-state-in-effect -- Initialize the remote library and its request lifecycle on mount.
  useEffect(() => { void refreshList() }, [refreshList])

  useEffect(() => {
    let cancelled = false
    // oxlint-disable-next-line react/set-state-in-effect -- A route/reload starts a new server snapshot; clear the previous draft before fetching.
    setDocument(null)
    setLoadError('')
    snapshotRef.current = null
    pendingRef.current = null
    failureRef.current = null
    setSaveState('saved')
    setSaveError('')
    if (documentId) {
      void getLessonDocument(documentId).then((next) => {
        if (cancelled) return
        snapshotRef.current = next
        setDocument(next.document)
      }).catch((reason) => {
        if (!cancelled) setLoadError(reason instanceof Error ? reason.message : '读取服务端文档失败')
      })
    }
    return () => { cancelled = true }
  }, [documentId, loadVersion])

  const startSaving = useCallback((): Promise<void> => {
    if (savingRef.current) return savingRef.current
    const operation = (async () => {
      setSaveState('saving')
      setSaveError('')
      while (pendingRef.current) {
        const next = pendingRef.current
        pendingRef.current = null
        try {
          const current = snapshotRef.current
          if (!current || current.document.documentId !== next.documentId) throw new Error('文档尚未加载，无法保存')
          const saved = await updateLessonDocument(next, current.etag)
          snapshotRef.current = saved
          if (!pendingRef.current) setDocument(saved.document)
        } catch (reason) {
          // Keep the latest draft. Never advance queued writes or rebase after a conflict.
          pendingRef.current ??= next
          const error = reason instanceof Error ? reason : new Error('保存编辑失败')
          failureRef.current = error
          setSaveState(error instanceof ApiError && error.status === 409 ? 'conflict' : 'failed')
          setSaveError(error.message)
          throw error
        }
      }
      failureRef.current = null
      setSaveState('saved')
      // List refresh errors are distinct from write failures.
      void refreshList()
    })()
    const tracked = operation.finally(() => { savingRef.current = null })
    savingRef.current = tracked
    void tracked.catch(() => {}) // waitForSaves still rejects; the UI exposes retry/reload.
    return tracked
  }, [refreshList])

  const saveEdits = useCallback((next: LessonDocument) => {
    setDocument(next)
    pendingRef.current = next
    if (!failureRef.current) void startSaving()
  }, [startSaving])

  const waitForSaves = useCallback(async () => {
    if (savingRef.current) await savingRef.current
    if (failureRef.current) throw failureRef.current
  }, [])

  const retrySave = useCallback(() => {
    if (failureRef.current instanceof ApiError && failureRef.current.status === 409) return
    failureRef.current = null
    void startSaving()
  }, [startSaving])

  const remove = useCallback(async (id: string) => {
    setLibraryError('')
    try {
      await waitForSaves()
      await deleteLessonDocument(id)
      await refreshList()
      return true
    } catch (reason) {
      setLibraryError(reason instanceof Error ? reason.message : '删除服务端文档失败')
      return false
    }
  }, [refreshList, waitForSaves])

  return {
    items, listReady,
    document: documentId && document?.documentId === documentId ? document : null,
    loadError, libraryError, saveError, saveState, loadVersion,
    refreshList, saveEdits, waitForSaves, retrySave, remove,
    reloadDocument: () => { if (!savingRef.current) setLoadVersion((value) => value + 1) },
  }
}
