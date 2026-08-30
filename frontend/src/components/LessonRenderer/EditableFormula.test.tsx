import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { expect, it, vi } from 'vitest'
import { TableBlock } from './TableBlock'
import { FormulaBlock } from './FormulaBlock'
import { EditableFormula } from './EditableFormula'
import { validateFormula } from '../../api/documents'

vi.mock('../../api/documents', async (original) => ({
  ...await original<typeof import('../../api/documents')>(), validateFormula: vi.fn(),
}))

it('renders standalone and table formulas without editing controls in read-only mode', () => {
  const onChange = vi.fn()
  const { container } = render(<>
    <FormulaBlock block={{ type: 'formula', id: 'f', latex: 'x^2' }} editable={false} onChange={onChange} />
    <TableBlock block={{ type: 'table', id: 't', html: '<table><tr><td><span data-latex="y^2"></span></td></tr></table>' }} editable={false} onChange={onChange} />
  </>)
  expect(screen.queryAllByRole('button', { name: /编辑公式/ })).toHaveLength(0)
  fireEvent.blur(container.querySelector('td')!)
  expect(onChange).not.toHaveBeenCalled()
  expect(container.querySelectorAll('.katex')).toHaveLength(2)
})

it('tracks uncommitted formula drafts and clears the warning when cancelled', async () => {
  const onDraftChange = vi.fn()
  render(<EditableFormula latex="x^2" editable onDraftChange={onDraftChange} />)
  fireEvent.click(screen.getByRole('button', { name: /编辑公式/ }))
  fireEvent.change(screen.getByRole('textbox'), { target: { value: 'x^3' } })
  await waitFor(() => expect(onDraftChange).toHaveBeenLastCalledWith(expect.any(String), true))
  fireEvent.click(screen.getByRole('button', { name: '取消' }))
  await waitFor(() => expect(onDraftChange).toHaveBeenLastCalledWith(expect.any(String), false))
})

it('persists the newly validated table formula without waiting for a DOM timer', async () => {
  vi.mocked(validateFormula).mockResolvedValue({ latex: 'x^3', normalizedLatex: 'x^3', parseable: true, message: 'ok' })
  const onChange = vi.fn()
  render(<TableBlock block={{ type: 'table', id: 't', html: '<table><tr><td><span data-latex="x^2"></span></td></tr></table>' }} editable onChange={onChange} />)
  fireEvent.click(screen.getByRole('button', { name: /编辑公式/ }))
  fireEvent.change(screen.getByRole('textbox'), { target: { value: 'x^3' } })
  fireEvent.click(screen.getByRole('button', { name: '校验并应用' }))
  await waitFor(() => expect(onChange).toHaveBeenCalled())
  const saved = onChange.mock.calls[0][0]
  expect(saved.html).toContain('data-latex="x^3"')
  expect(saved.html).not.toContain('data-latex="x^2"')
  expect(saved.html).not.toContain('formula-editor')
})
