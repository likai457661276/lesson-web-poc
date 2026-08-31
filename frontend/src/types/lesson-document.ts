export type TextAlignment = 'left' | 'center' | 'right'
export type HeadingBlock = { id: string; type: 'heading'; level: number; text: string; alignment: TextAlignment }
export type ParagraphBlock = { id: string; type: 'paragraph'; text: string; reviewNote?: string | null }
export type ListBlock = { id: string; type: 'list'; items: string[]; ordered: boolean }
export type TableBlock = { id: string; type: 'table'; html: string }
export type ImageBlock = { id: string; type: 'image'; src: string; alt?: string | null }
export type FormulaBlock = { id: string; type: 'formula'; latex: string }

export type LessonBlock = HeadingBlock | ParagraphBlock | ListBlock | TableBlock | ImageBlock | FormulaBlock

export interface LessonDocument {
  version: '1.0'
  documentId: string
  title: string
  metadata: { sourceType: string; sourceFileName: string }
  blocks: LessonBlock[]
}

export type ParseStatus = 'pending' | 'processing' | 'completed' | 'failed'

export interface ParseJob {
  jobId: string
  status: ParseStatus
  sourceFileName: string
  createdAt: string
  document?: LessonDocument | null
  error?: { code: string; message: string } | null
}

export interface LessonDocumentSummary {
  id: string
  title: string
  sourceFileName: string
  sourceType: string
  blockCount: number
  createdAt: string
  updatedAt: string
}
