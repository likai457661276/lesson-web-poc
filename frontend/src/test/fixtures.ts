import type { LessonDocument } from '../types/lesson-document'

export const document: LessonDocument = {
  version: '1.0', documentId: 'doc-1', title: 'Draft',
  metadata: { sourceType: 'pdf', sourceFileName: 'lesson.pdf' },
  blocks: [{ id: 'p1', type: 'paragraph', text: 'Original paragraph' }],
}

export function deferred<T>() {
  let resolve!: (value: T) => void
  let reject!: (reason: Error) => void
  const promise = new Promise<T>((yes, no) => { resolve = yes; reject = no })
  return { promise, resolve, reject }
}
