import { fireEvent, render, screen } from '@testing-library/react'
import { expect, it, vi } from 'vitest'
import { document } from '../../test/fixtures'
import { LessonRenderer } from './LessonRenderer'
import { ParagraphBlock } from './ParagraphBlock'
import { renderDocumentForExport } from './documentExport'

it('edits plain paragraph text', () => {
  const block = { id: 'paragraph', type: 'paragraph' as const, text: '实验观察记录' }
  const changed = vi.fn()
  render(<ParagraphBlock block={block} editable onChange={changed} />)
  expect(screen.queryByRole('note')).toBeNull()
  const paragraph = screen.getByText(block.text)
  paragraph.textContent = '更新后的观察记录'
  fireEvent.blur(paragraph)
  expect(changed).toHaveBeenCalledWith({ ...block, text: '更新后的观察记录' })
})

it.each([false, true])('preserves paragraph content and order in reading, editing (%s) and export', (editable) => {
  const lesson = {
    ...document,
    blocks: [
      { id: 'first', type: 'paragraph' as const, text: '开始记录' },
      { id: 'last', type: 'paragraph' as const, text: '完成记录' },
    ],
  }
  const { container } = render(<LessonRenderer
    document={lesson}
    editable={editable}
    getPersistedDocument={async () => lesson}
    onUnsavedChange={vi.fn()}
    saveState="saved"
    saveError=""
    onRetrySave={vi.fn()}
    onReload={vi.fn()}
  />)
  const exported = renderDocumentForExport(lesson)
  for (const root of [container, exported]) {
    expect(Array.from(root.querySelectorAll('.document-block')).map((block) => block.textContent))
      .toEqual(['开始记录', '完成记录'])
    expect(root.querySelector('[role="note"]')).toBeNull()
  }
  expect(lesson.blocks).toHaveLength(2)
})
