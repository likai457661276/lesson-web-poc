import type { ParagraphBlock as Paragraph } from '../../types/lesson-document'

export function ParagraphBlock({ block }: { block: Paragraph }) {
  return <p className="lesson-paragraph">{block.text}</p>
}
