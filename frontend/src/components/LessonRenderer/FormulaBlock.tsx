import type { FormulaBlock as Formula } from '../../types/lesson-document'
import { EditableFormula } from './EditableFormula'

export function FormulaBlock({
  block,
  onChange,
}: {
  block: Formula
  editable?: boolean
  onChange?: (block: Formula) => void
}) {
  return (
    <div className="lesson-formula">
      <EditableFormula
        latex={block.latex}
        displayMode
        onChange={(latex) => onChange?.({ ...block, latex })}
      />
    </div>
  )
}
