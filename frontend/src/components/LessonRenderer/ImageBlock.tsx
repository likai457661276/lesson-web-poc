import type { ImageBlock as Image } from '../../types/lesson-document'

export function ImageBlock({ block }: { block: Image }) {
  return <figure className="lesson-image"><img src={block.src} alt={block.alt ?? ''} loading="lazy" />{block.alt && <figcaption>{block.alt}</figcaption>}</figure>
}
