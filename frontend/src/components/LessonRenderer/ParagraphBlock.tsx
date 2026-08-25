import { useState } from 'react'
import type { ParagraphBlock as Paragraph } from '../../types/lesson-document'

export function ParagraphBlock({ block, editable }: { block: Paragraph; editable: boolean }) {
  const [text, setText] = useState(block.text)
  return (
    <p
      className="lesson-paragraph editable-copy"
      contentEditable={editable}
      suppressContentEditableWarning
      onBlur={(event) => setText(event.currentTarget.textContent?.trim() || text)}
    >
      {text}
    </p>
  )
}
