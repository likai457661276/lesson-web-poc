import { fireEvent, render, screen } from '@testing-library/react'
import { expect, it, vi } from 'vitest'
import { TableBlock } from './TableBlock'
import { ParagraphBlock } from './ParagraphBlock'
import { renderDocumentForExport } from './documentExport'
import { document } from '../../test/fixtures'

it('persists an edited caption without dropping table content', () => {
  const changed = vi.fn()
  render(<TableBlock block={{ id: 'table', type: 'table', html: '<table><caption>实验记录</caption><tbody><tr><td>观察内容</td></tr></tbody></table>' }} editable onChange={changed} />)
  const caption = screen.getByText('实验记录')
  caption.textContent = '修改后的记录'
  fireEvent.blur(caption)
  expect(changed).toHaveBeenCalledWith(expect.objectContaining({ html: expect.stringContaining('<caption>修改后的记录</caption>') }))
  expect(changed.mock.calls[0][0].html).toContain('观察内容')
})

it.each([
  '第 2 页需核对阅读顺序',
  '第 7 页，第 1 个内容块：未识别内容类型，已保留文字；请核对结构和内容完整性。',
])('keeps review note %s outside editable text and carries it into exports', (reviewNote) => {
  const block = { id: 'paragraph', type: 'paragraph' as const, text: '原始正文', reviewNote }
  const changed = vi.fn()
  render(<ParagraphBlock block={block} editable onChange={changed} />)
  expect(screen.getByRole('note').getAttribute('contenteditable')).toBeNull()
  fireEvent.blur(screen.getByText('原始正文'))
  expect(changed).toHaveBeenCalledWith(block)
  const html = renderDocumentForExport({ ...document, blocks: [block] }).outerHTML
  expect(html).toContain(`需人工复核：${reviewNote}`)
  expect(html).toContain('原始正文')
})
