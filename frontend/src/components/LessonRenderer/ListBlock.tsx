import { useState } from 'react'
import type { ListBlock as List } from '../../types/lesson-document'

export function ListBlock({ block, editable }: { block: List; editable: boolean }) {
  const [items, setItems] = useState(block.items)
  const Tag = block.ordered ? 'ol' : 'ul'
  return (
    <Tag className="lesson-list">
      {items.map((item, index) => (
        <li
          className="editable-copy"
          contentEditable={editable}
          suppressContentEditableWarning
          key={`${block.id}-${index}`}
          onBlur={(event) => {
            const next = [...items]
            next[index] = event.currentTarget.textContent?.trim() || item
            setItems(next)
          }}
        >
          {item}
        </li>
      ))}
    </Tag>
  )
}
