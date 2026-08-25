import type { HeadingBlock as Heading } from '../../types/lesson-document'

export function HeadingBlock({ block }: { block: Heading }) {
  const level = Math.min(Math.max(block.level, 1), 6)
  const Tag = `h${level}` as keyof React.JSX.IntrinsicElements
  return <Tag className={`lesson-heading lesson-heading-${level}`}>{block.text}</Tag>
}
