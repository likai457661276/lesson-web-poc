import katex from 'katex'
import type { FormulaBlock as Formula } from '../../types/lesson-document'

export function FormulaBlock({ block }: { block: Formula }) {
  const html = katex.renderToString(block.latex, { displayMode: true, throwOnError: false, strict: false })
  return <div className="lesson-formula" aria-label={block.latex} dangerouslySetInnerHTML={{ __html: html }} />
}
