import { useCallback, useEffect, useLayoutEffect, useRef, useState } from 'react'
import {
  deleteLessonDocument,
  getLessonDocument,
  listLessonDocuments,
  updateLessonDocument,
} from '../api/documents'
import type { LessonDocument, LessonDocumentSummary } from '../types/lesson-document'

export function useDocumentLibrary(documentId: string | undefined) {
  const [items, setItems] = useState<LessonDocumentSummary[]>([])
  const [listReady, setListReady] = useState(false)
  const [document, setDocument] = useState<LessonDocument | null>(null)
  const [loadError, setLoadError] = useState<{ id: string; message: string } | null>(null)
  const [libraryError, setLibraryError] = useState('')
  const activeIdRef = useRef(documentId)
  const saveQueueRef = useRef<Promise<void>>(Promise.resolve())
  const saveRevisionRef = useRef(0)

  useLayoutEffect(() => { activeIdRef.current = documentId }, [documentId])

  const refreshList = useCallback(async () => {
    setItems(await listLessonDocuments())
    setListReady(true)
  }, [])

  useEffect(() => {
    let cancelled = false
    void listLessonDocuments().then((next) => {
      if (cancelled) return
      setItems(next)
      setListReady(true)
    }).catch((reason) => {
      if (cancelled) return
      setLibraryError(reason instanceof Error ? reason.message : '无法读取服务端文档库')
      setListReady(true)
    })
    return () => { cancelled = true }
  }, [])

  useEffect(() => {
    if (!documentId) return
    let cancelled = false
    void getLessonDocument(documentId).then((next) => {
      if (cancelled) return
      setDocument(next)
      setLoadError(null)
    }).catch((reason) => {
      if (cancelled) return
      setDocument(null)
      setLoadError({ id: documentId, message: reason instanceof Error ? reason.message : '读取服务端文档失败' })
    })
    return () => { cancelled = true }
  }, [documentId])

  const adoptParsedDocument = useCallback(async (next: LessonDocument): Promise<boolean> => {
    setLibraryError('')
    try {
      await refreshList()
      if (activeIdRef.current === next.documentId) setDocument(next)
      return true
    } catch (reason) {
      setLibraryError(reason instanceof Error ? reason.message : '文档已解析，但刷新服务端文档库失败')
      return false
    }
  }, [refreshList])

  const saveEdits = useCallback((next: LessonDocument): Promise<void> => {
    const revision = ++saveRevisionRef.current
    setDocument(next)
    const operation = saveQueueRef.current.then(async () => {
      setLibraryError('')
      const saved = await updateLessonDocument(next)
      if (activeIdRef.current === saved.documentId && saveRevisionRef.current === revision) setDocument(saved)
      await refreshList()
    })
    const handled = operation.catch((reason) => {
      setLibraryError(reason instanceof Error ? reason.message : '保存编辑失败')
    })
    saveQueueRef.current = handled
    return handled
  }, [refreshList])

  const remove = useCallback(async (id: string) => {
    setLibraryError('')
    try {
      await saveQueueRef.current
      await deleteLessonDocument(id)
      if (activeIdRef.current === id) setDocument(null)
      await refreshList()
      return true
    } catch (reason) {
      setLibraryError(reason instanceof Error ? reason.message : '删除服务端文档失败')
      return false
    }
  }, [refreshList])

  return {
    items,
    listReady,
    document: documentId && document?.documentId === documentId ? document : null,
    loadError: documentId && loadError?.id === documentId ? loadError.message : '',
    libraryError,
    adoptParsedDocument,
    saveEdits,
    remove,
  }
}
