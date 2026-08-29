import { useCallback, useEffect, useLayoutEffect, useRef, useState } from 'react'
import {
  cacheDocumentAssets,
  createAssetUrlMap,
  deleteCachedDocument,
  getCachedDocument,
  listCachedDocuments,
  listDocumentAssets,
  saveDocumentRecord,
  type CachedDocumentSummary,
} from '../storage/documentCache'
import type { LessonDocument } from '../types/lesson-document'

export function useDocumentLibrary(documentId: string | undefined) {
  const [items, setItems] = useState<CachedDocumentSummary[]>([])
  const [listReady, setListReady] = useState(false)
  const [document, setDocument] = useState<LessonDocument | null>(null)
  const [assetUrls, setAssetUrls] = useState<Record<string, string>>({})
  const [assetDocumentId, setAssetDocumentId] = useState<string | null>(null)
  const [loadError, setLoadError] = useState<{ id: string; message: string } | null>(null)
  const [cacheError, setCacheError] = useState('')
  const revokeRef = useRef(() => {})
  const activeIdRef = useRef(documentId)
  const saveQueueRef = useRef<Promise<void>>(Promise.resolve())

  // Update before passive effect cleanup/loads so a stale IndexedDB result can
  // never replace or revoke the assets of the route that is now on screen.
  useLayoutEffect(() => {
    activeIdRef.current = documentId
  }, [documentId])

  const refreshList = useCallback(async () => {
    setItems(await listCachedDocuments())
    setListReady(true)
  }, [])

  const applyAssets = useCallback(async (id: string) => {
    const assets = await listDocumentAssets(id)
    const mapped = createAssetUrlMap(assets)
    if (activeIdRef.current !== id) {
      mapped.revoke()
      return
    }
    revokeRef.current()
    revokeRef.current = mapped.revoke
    setAssetUrls(mapped.urls)
    setAssetDocumentId(id)
  }, [])

  useEffect(() => {
    // The state update happens after the IndexedDB promise settles; this effect
    // is the external-store subscription point, not a derived-state effect.
    // oxlint-disable-next-line react/set-state-in-effect
    void refreshList().catch((reason) => {
      setCacheError(reason instanceof Error ? reason.message : '无法读取本机文档缓存')
      setListReady(true)
    })
  }, [refreshList])

  useEffect(() => {
    if (!documentId) return

    let cancelled = false
    void (async () => {
      try {
        const record = await getCachedDocument(documentId)
        if (cancelled) return
        if (!record) {
          setDocument(null)
          setAssetUrls({})
          setAssetDocumentId(null)
          setLoadError({ id: documentId, message: '该文档不在本机缓存中，可能已被删除。' })
          return
        }
        setLoadError(null)
        setDocument(record.document)
        await applyAssets(documentId)
      } catch (reason) {
        if (!cancelled) {
          setDocument(null)
          setLoadError({
            id: documentId,
            message: reason instanceof Error ? reason.message : '读取缓存文档失败',
          })
        }
      }
    })()

    return () => {
      cancelled = true
      if (activeIdRef.current !== documentId) {
        revokeRef.current()
        revokeRef.current = () => {}
      }
    }
  }, [applyAssets, documentId])

  useEffect(() => () => revokeRef.current(), [])

  const cacheFromParse = useCallback(async (next: LessonDocument): Promise<boolean> => {
    setCacheError('')
    try {
      await saveDocumentRecord(next)
      await refreshList()
      if (activeIdRef.current === next.documentId) setDocument(next)
      // Finish the durable asset copy before navigating away from the parse
      // result, otherwise a refresh can interrupt the downloads and leave a
      // document that cannot be fully restored or exported offline.
      await cacheDocumentAssets(next)
      if (activeIdRef.current === next.documentId) await applyAssets(next.documentId)
      return true
    } catch (reason) {
      setCacheError(reason instanceof Error ? reason.message : '文档已解析，但写入本机缓存失败')
      return false
    }
  }, [applyAssets, refreshList])

  const saveEdits = useCallback((next: LessonDocument): Promise<void> => {
    setDocument(next)
    const operation = saveQueueRef.current.then(async () => {
      setCacheError('')
      await saveDocumentRecord(next)
      await refreshList()
    })
    const handled = operation.catch((reason) => {
      setCacheError(reason instanceof Error ? reason.message : '保存编辑失败')
    })
    saveQueueRef.current = handled
    return handled
  }, [refreshList])

  const remove = useCallback(async (id: string) => {
    setCacheError('')
    try {
      // Do not let an earlier edit finish after deletion and recreate the record.
      await saveQueueRef.current
      await deleteCachedDocument(id)
      if (activeIdRef.current === id) {
        setDocument(null)
        revokeRef.current()
        revokeRef.current = () => {}
        setAssetUrls({})
        setAssetDocumentId(null)
      }
      await refreshList()
    } catch (reason) {
      setCacheError(reason instanceof Error ? reason.message : '删除缓存文档失败')
    }
  }, [refreshList])

  return {
    items,
    listReady,
    document: documentId && document?.documentId === documentId ? document : null,
    assetUrls: documentId && document?.documentId === documentId && assetDocumentId === documentId ? assetUrls : {},
    assetsReady: Boolean(
      documentId
      && document?.documentId === documentId
      && assetDocumentId === documentId,
    ),
    loadError: documentId && loadError?.id === documentId ? loadError.message : '',
    cacheError,
    cacheFromParse,
    saveEdits,
    remove,
  }
}
