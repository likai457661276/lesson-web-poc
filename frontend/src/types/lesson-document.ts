export type TextAlignment = 'left' | 'center' | 'right'
export type BlockContext = {
  id: string
  sourcePage?: number | null
  groupId?: string | null
  reviewRequired?: boolean
  reviewReason?: string | null
}
export type HeadingBlock = BlockContext & { type: 'heading'; level: number; text: string; alignment: TextAlignment }
export type ParagraphBlock = BlockContext & { type: 'paragraph'; text: string }
export type ListBlock = BlockContext & { type: 'list'; items: string[]; ordered: boolean }
export type TableBlock = BlockContext & { type: 'table'; html: string }
export type ImageBlock = BlockContext & { type: 'image'; src: string; alt?: string | null }
export type FormulaBlock = BlockContext & { type: 'formula'; latex: string }

export type LessonBlock = HeadingBlock | ParagraphBlock | ListBlock | TableBlock | ImageBlock | FormulaBlock

export interface LessonDocument {
  version: '1.0'
  documentId: string
  title: string
  metadata: { sourceType: string; sourceFileName: string; sourceUrl?: string | null }
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
