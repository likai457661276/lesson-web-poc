import { useState } from 'react'
import type { ParagraphBlock as Paragraph } from '../../types/lesson-document'

export function ParagraphBlock({
  block,
  editable,
  onChange,
}: {
  block: Paragraph
  editable: boolean
  onChange?: (block: Paragraph) => void
}) {
  const [text, setText] = useState(block.text)
  return (
    <p
      className="lesson-paragraph editable-copy"
      contentEditable={editable}
      suppressContentEditableWarning
      onBlur={(event) => {
        const next = event.currentTarget.textContent?.trim() || text
        setText(next)
        onChange?.({ ...block, text: next })
      }}
    >
      {text}
    </p>
  )
}
