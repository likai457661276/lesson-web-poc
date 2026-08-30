import { useState } from 'react'
import type { HeadingBlock as Heading } from '../../types/lesson-document'

export function HeadingBlock({
  block,
  editable,
  onChange,
}: {
  block: Heading
  editable: boolean
  onChange?: (block: Heading) => void
}) {
  const [text, setText] = useState(block.text)
  const level = Math.min(Math.max(block.level, 1), 6)
  const Tag = `h${level}` as keyof React.JSX.IntrinsicElements
  return (
    <Tag
      className={`lesson-heading lesson-heading-${level} lesson-heading-${block.alignment} editable-copy`}
      contentEditable={editable}
      suppressContentEditableWarning
      onBlur={(event) => {
        if (!editable) return
        const next = event.currentTarget.textContent ?? text
        setText(next)
        onChange?.({ ...block, text: next })
      }}
    >
      {text}
    </Tag>
  )
}
