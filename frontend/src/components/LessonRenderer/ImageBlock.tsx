import { useState } from 'react'
import type { ImageBlock as Image } from '../../types/lesson-document'

export function ImageBlock({ block, editable }: { block: Image; editable: boolean }) {
  const [caption, setCaption] = useState(block.alt ?? '')
  return (
    <figure className="lesson-image">
      <img src={block.src} alt={caption} loading="lazy" />
      {(caption || editable) && (
        <figcaption
          className="editable-copy"
          contentEditable={editable}
          suppressContentEditableWarning
          onBlur={(event) => setCaption(event.currentTarget.textContent?.trim() || '')}
        >
          {caption}
        </figcaption>
      )}
    </figure>
  )
}
