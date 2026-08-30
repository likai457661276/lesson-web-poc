import { fireEvent, render, screen } from '@testing-library/react'
import { expect, it, vi } from 'vitest'
import { DocumentUploader } from './DocumentUploader'

it('disables the file picker, drop handler, clear and submit while busy', () => {
  const onFile = vi.fn()
  const onSubmit = vi.fn()
  const { container } = render(<DocumentUploader file={null} disabled onFile={onFile} onClear={vi.fn()} onSubmit={onSubmit} />)
  const input = container.querySelector('input')!
  expect(input.disabled).toBe(true)
  fireEvent.drop(container.querySelector('.drop-zone')!, { dataTransfer: { files: [new File(['pdf'], 'a.pdf')] } })
  fireEvent.click(screen.getByRole('button', { name: /拖入 PDF/ }))
  expect(onFile).not.toHaveBeenCalled()
  expect(onSubmit).not.toHaveBeenCalled()
})

it('resets the file input so selecting the same file again is possible', () => {
  const onFile = vi.fn()
  const { container } = render(<DocumentUploader file={null} disabled={false} onFile={onFile} onClear={vi.fn()} onSubmit={vi.fn()} />)
  const input = container.querySelector('input')!
  const file = new File(['pdf'], 'a.pdf')
  fireEvent.change(input, { target: { files: [file] } })
  expect(onFile).toHaveBeenCalledWith(file)
  expect(input.value).toBe('')
})
