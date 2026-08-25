import type { ListBlock as List } from '../../types/lesson-document'

export function ListBlock({ block }: { block: List }) {
  const Tag = block.ordered ? 'ol' : 'ul'
  return <Tag className="lesson-list">{block.items.map((item, index) => <li key={`${block.id}-${index}`}>{item}</li>)}</Tag>
}
