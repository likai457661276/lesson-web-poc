import type { FormulaBlock as Formula } from '../../types/lesson-document'
import { EditableFormula } from './EditableFormula'

export function FormulaBlock({
  block,
  onChange,
  editable,
  onDraftChange,
}: {
  block: Formula
  editable: boolean
  onChange?: (block: Formula) => void
  onDraftChange?: (id: string, dirty: boolean) => void
}) {
  return (
    <div className="lesson-formula">
      <EditableFormula
        latex={block.latex}
        editable={editable}
        onDraftChange={onDraftChange}
        displayMode
        onChange={(latex) => onChange?.({ ...block, latex })}
      />
    </div>
  )
}
