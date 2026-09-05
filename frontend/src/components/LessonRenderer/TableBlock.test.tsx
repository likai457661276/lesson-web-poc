import { fireEvent, render, screen } from '@testing-library/react'
import { expect, it, vi } from 'vitest'
import { TableBlock } from './TableBlock'

it('persists an edited caption without dropping table content', () => {
  const changed = vi.fn()
  render(<TableBlock block={{ id: 'table', type: 'table', html: '<table><caption>实验记录</caption><tbody><tr><td>观察内容</td></tr></tbody></table>' }} editable onChange={changed} />)
  const caption = screen.getByText('实验记录')
  caption.textContent = '修改后的记录'
  fireEvent.blur(caption)
  expect(changed).toHaveBeenCalledWith(expect.objectContaining({ html: expect.stringContaining('<caption>修改后的记录</caption>') }))
  expect(changed.mock.calls[0][0].html).toContain('观察内容')
})
