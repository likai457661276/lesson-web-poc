import DOMPurify from 'dompurify'
import type { TableBlock as Table } from '../../types/lesson-document'

export function TableBlock({ block }: { block: Table }) {
  return <div className="lesson-table" dangerouslySetInnerHTML={{ __html: DOMPurify.sanitize(block.html) }} />
}
