import type { FormulaBlock as Formula } from '../../types/lesson-document'
import { EditableFormula } from './EditableFormula'

export function FormulaBlock({ block }: { block: Formula; editable?: boolean }) {
  return <div className="lesson-formula"><EditableFormula latex={block.latex} displayMode /></div>
}
