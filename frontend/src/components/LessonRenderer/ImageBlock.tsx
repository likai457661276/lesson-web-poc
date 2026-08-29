import { useState } from 'react'
import type { ImageBlock as Image } from '../../types/lesson-document'
import { useAssetUrl } from './assetUrlContext'

export function ImageBlock({
  block,
  editable,
  onChange,
}: {
  block: Image
  editable: boolean
  onChange?: (block: Image) => void
}) {
  const [caption, setCaption] = useState(block.alt ?? '')
  const src = useAssetUrl(block.src)
  return (
    <figure className="lesson-image">
      <img src={src} alt={caption} loading="lazy" />
      {(caption || editable) && (
        <figcaption
          className="editable-copy"
          contentEditable={editable}
          suppressContentEditableWarning
          onBlur={(event) => {
            const next = event.currentTarget.textContent?.trim() || ''
            setCaption(next)
            onChange?.({ ...block, alt: next || null })
          }}
        >
          {caption}
        </figcaption>
      )}
    </figure>
  )
}
