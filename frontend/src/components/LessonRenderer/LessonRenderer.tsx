import type { LessonBlock, LessonDocument } from '../../types/lesson-document'
import { FormulaBlock } from './FormulaBlock'
import { HeadingBlock } from './HeadingBlock'
import { ImageBlock } from './ImageBlock'
import { ListBlock } from './ListBlock'
import { ParagraphBlock } from './ParagraphBlock'
import { TableBlock } from './TableBlock'

function BlockRenderer({ block }: { block: LessonBlock }) {
  switch (block.type) {
    case 'heading': return <HeadingBlock block={block} />
    case 'paragraph': return <ParagraphBlock block={block} />
    case 'list': return <ListBlock block={block} />
    case 'table': return <TableBlock block={block} />
    case 'image': return <ImageBlock block={block} />
    case 'formula': return <FormulaBlock block={block} />
  }
}

export function LessonRenderer({ document }: { document: LessonDocument }) {
  return (
    <article className="lesson-document">
      <header className="document-title">
        <span>{document.metadata.sourceType.toUpperCase()} · LessonDocument {document.version}</span>
        <h1>{document.title}</h1>
        <p>{document.metadata.sourceFileName}</p>
      </header>
      <div className="document-rule" />
      <div className="document-blocks">
        {document.blocks.map((block) => <div className="document-block" key={block.id}><BlockRenderer block={block} /></div>)}
      </div>
    </article>
  )
}
