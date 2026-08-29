import type { LessonDocument } from '../types/lesson-document'

const DB_NAME = 'lesson-web-poc'
const DB_VERSION = 1

export interface CachedDocumentSummary {
  id: string
  title: string
  sourceFileName: string
  sourceType: string
  blockCount: number
  createdAt: string
  updatedAt: string
}

export interface CachedDocumentRecord extends CachedDocumentSummary {
  document: LessonDocument
}

interface CachedAssetRecord {
  id: string
  documentId: string
  src: string
  blob: Blob
}

export interface CachedAsset {
  src: string
  blob: Blob
}

const SRC_ATTR = /\bsrc\s*=\s*(?:"([^"]+)"|'([^']+)')/gi

export function collectAssetSrcs(document: LessonDocument): string[] {
  const srcs = new Set<string>()
  for (const block of document.blocks) {
    if (block.type === 'image') {
      addAssetSrc(srcs, block.src)
    }
    if (block.type === 'table') {
      for (const match of block.html.matchAll(SRC_ATTR)) {
        addAssetSrc(srcs, match[1] || match[2] || '')
      }
    }
  }
  return [...srcs]
}

export function createAssetUrlMap(assets: CachedAsset[]): { urls: Record<string, string>; revoke: () => void } {
  const urls: Record<string, string> = {}
  for (const asset of assets) {
    urls[asset.src] = URL.createObjectURL(asset.blob)
  }
  return {
    urls,
    revoke: () => {
      for (const url of Object.values(urls)) {
        URL.revokeObjectURL(url)
      }
    },
  }
}

function addAssetSrc(srcs: Set<string>, src: string) {
  const value = src.trim()
  if (!value || value.startsWith('data:') || value.startsWith('blob:')) return
  srcs.add(value)
}

function assetKey(documentId: string, src: string) {
  return `${documentId}::${src}`
}

function toSummary(record: CachedDocumentRecord): CachedDocumentSummary {
  return {
    id: record.id,
    title: record.title,
    sourceFileName: record.sourceFileName,
    sourceType: record.sourceType,
    blockCount: record.blockCount,
    createdAt: record.createdAt,
    updatedAt: record.updatedAt,
  }
}

function requestToPromise<T>(request: IDBRequest<T>): Promise<T> {
  return new Promise((resolve, reject) => {
    request.onsuccess = () => resolve(request.result)
    request.onerror = () => reject(request.error ?? new Error('IndexedDB 请求失败'))
  })
}

function transactionDone(tx: IDBTransaction): Promise<void> {
  return new Promise((resolve, reject) => {
    tx.oncomplete = () => resolve()
    tx.onerror = () => reject(tx.error ?? new Error('IndexedDB 事务失败'))
    tx.onabort = () => reject(tx.error ?? new Error('IndexedDB 事务已中止'))
  })
}

function openDb(): Promise<IDBDatabase> {
  return new Promise((resolve, reject) => {
    const request = indexedDB.open(DB_NAME, DB_VERSION)
    request.onupgradeneeded = () => {
      const db = request.result
      if (!db.objectStoreNames.contains('documents')) {
        db.createObjectStore('documents', { keyPath: 'id' })
      }
      if (!db.objectStoreNames.contains('assets')) {
        const assets = db.createObjectStore('assets', { keyPath: 'id' })
        assets.createIndex('documentId', 'documentId', { unique: false })
      }
    }
    request.onsuccess = () => resolve(request.result)
    request.onerror = () => reject(request.error ?? new Error('无法打开本机文档缓存'))
  })
}

async function withDb<T>(fn: (db: IDBDatabase) => Promise<T>): Promise<T> {
  const db = await openDb()
  try {
    return await fn(db)
  } finally {
    db.close()
  }
}

async function runTransaction<T>(
  db: IDBDatabase,
  storeNames: string | string[],
  mode: IDBTransactionMode,
  run: (tx: IDBTransaction) => Promise<T> | T,
): Promise<T> {
  const tx = db.transaction(storeNames, mode)
  const done = transactionDone(tx)
  const result = await run(tx)
  await done
  return result
}

export async function listCachedDocuments(): Promise<CachedDocumentSummary[]> {
  return withDb(async (db) => {
    const records = await runTransaction(db, 'documents', 'readonly', (tx) => (
      requestToPromise(tx.objectStore('documents').getAll() as IDBRequest<CachedDocumentRecord[]>)
    ))
    return records
      .map(toSummary)
      .sort((left, right) => right.updatedAt.localeCompare(left.updatedAt))
  })
}

export async function getCachedDocument(id: string): Promise<CachedDocumentRecord | null> {
  return withDb(async (db) => {
    const record = await runTransaction(db, 'documents', 'readonly', (tx) => (
      requestToPromise(tx.objectStore('documents').get(id) as IDBRequest<CachedDocumentRecord | undefined>)
    ))
    return record ?? null
  })
}

export async function listDocumentAssets(documentId: string): Promise<CachedAsset[]> {
  return withDb(async (db) => {
    const records = await runTransaction(db, 'assets', 'readonly', (tx) => (
      requestToPromise(
        tx.objectStore('assets').index('documentId').getAll(documentId) as IDBRequest<CachedAssetRecord[]>,
      )
    ))
    return records.map((record) => ({ src: record.src, blob: record.blob }))
  })
}

export async function saveDocumentRecord(document: LessonDocument): Promise<CachedDocumentRecord> {
  const now = new Date().toISOString()
  return withDb(async (db) => runTransaction(db, 'documents', 'readwrite', async (tx) => {
    const store = tx.objectStore('documents')
    const existing = await requestToPromise(store.get(document.documentId) as IDBRequest<CachedDocumentRecord | undefined>)
    const record: CachedDocumentRecord = {
      id: document.documentId,
      title: document.title,
      sourceFileName: document.metadata.sourceFileName,
      sourceType: document.metadata.sourceType,
      blockCount: document.blocks.length,
      createdAt: existing?.createdAt ?? now,
      updatedAt: now,
      document,
    }
    store.put(record)
    return record
  }))
}

export async function cacheDocumentAssets(document: LessonDocument): Promise<void> {
  const srcs = collectAssetSrcs(document)
  const blobs = await Promise.all(srcs.map(async (src) => {
    try {
      const response = await fetch(src, { signal: AbortSignal.timeout(15_000) })
      if (!response.ok) return null
      return { src, blob: await response.blob() }
    } catch {
      return null
    }
  }))
  const saved = blobs.filter((item): item is CachedAsset => item !== null)
  await withDb(async (db) => {
    await runTransaction(db, 'assets', 'readwrite', async (tx) => {
      const store = tx.objectStore('assets')
      const existing = await requestToPromise(
        store.index('documentId').getAll(document.documentId) as IDBRequest<CachedAssetRecord[]>,
      )
      const currentSrcs = new Set(srcs)
      for (const record of existing) {
        if (!currentSrcs.has(record.src)) store.delete(record.id)
      }
      for (const asset of saved) {
        const record: CachedAssetRecord = {
          id: assetKey(document.documentId, asset.src),
          documentId: document.documentId,
          src: asset.src,
          blob: asset.blob,
        }
        store.put(record)
      }
    })
  })
}

export async function deleteCachedDocument(id: string): Promise<void> {
  await withDb(async (db) => {
    await runTransaction(db, ['documents', 'assets'], 'readwrite', async (tx) => {
      tx.objectStore('documents').delete(id)
      const assetStore = tx.objectStore('assets')
      const keys = await requestToPromise(assetStore.index('documentId').getAllKeys(id))
      for (const key of keys) {
        assetStore.delete(key)
      }
    })
  })
}
