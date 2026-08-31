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
    <div>
      {block.reviewNote && <p className="document-review-note" role="note">需人工复核：{block.reviewNote}</p>}
      <p
        className="lesson-paragraph editable-copy"
        contentEditable={editable}
        suppressContentEditableWarning
        onBlur={(event) => {
          if (!editable) return
          const next = event.currentTarget.textContent?.trim() ?? ''
          setText(next)
          onChange?.({ ...block, text: next })
        }}
      >
        {text}
      </p>
    </div>
  )
}
