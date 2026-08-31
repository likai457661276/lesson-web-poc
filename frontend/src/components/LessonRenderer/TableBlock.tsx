import DOMPurify from 'dompurify'
import parse, { attributesToProps, domToReact, Element, type DOMNode, type HTMLReactParserOptions } from 'html-react-parser'
import { useRef } from 'react'
import type { TableBlock as Table } from '../../types/lesson-document'
import { EditableFormula } from './EditableFormula'

function serializeEditedTable(table: HTMLTableElement, changedFormula?: { index: number; latex: string }): string {
  const clone = table.cloneNode(true) as HTMLTableElement
  clone.querySelectorAll('.formula-editor, .formula-edit-hint, .formula-validation-badge').forEach((node) => node.remove())
  clone.querySelectorAll('[data-latex]').forEach((node, index) => {
    const span = window.document.createElement('span')
    span.setAttribute('data-latex', changedFormula?.index === index ? changedFormula.latex : node.getAttribute('data-latex') ?? '')
    node.replaceWith(span)
  })
  clone.querySelectorAll('[contenteditable]').forEach((node) => node.removeAttribute('contenteditable'))
  clone.querySelectorAll('.editable-copy').forEach((node) => {
    node.classList.remove('editable-copy')
    if (!node.className.trim()) node.removeAttribute('class')
  })
  return clone.outerHTML
}

export function TableBlock({
  block,
  editable,
  onChange,
  onDraftChange,
}: {
  block: Table
  editable: boolean
  onChange?: (block: Table) => void
  onDraftChange?: (id: string, dirty: boolean) => void
}) {
  const wrapperRef = useRef<HTMLDivElement>(null)
  const persist = (changedFormula?: { index: number; latex: string }) => {
    const table = wrapperRef.current?.querySelector('table')
    if (!editable || !table || !onChange) return
    onChange({ ...block, html: serializeEditedTable(table, changedFormula) })
  }

  const safeHtml = DOMPurify.sanitize(block.html, {
    ADD_ATTR: ['data-latex', 'role', 'tabindex'],
  })
  let nextFormulaIndex = 0
  const options: HTMLReactParserOptions = {
    replace(node) {
      if (node instanceof Element && node.attribs?.['data-latex']) {
        const formulaIndex = nextFormulaIndex++
        return (
          <EditableFormula
            latex={node.attribs['data-latex']}
            editable={editable}
            onDraftChange={onDraftChange}
            onChange={(latex) => persist({ index: formulaIndex, latex })}
          />
        )
      }
      if (node instanceof Element && node.name === 'img') {
        return (
          <img
            src={node.attribs.src ?? ''}
            alt={node.attribs.alt}
            className={node.attribs.class}
          />
        )
      }
      if (node instanceof Element && (node.name === 'td' || node.name === 'th' || node.name === 'caption')) {
        const Tag = node.name
        return (
          <Tag
            {...attributesToProps(node.attribs)}
            className={`${node.attribs.class ?? ''} editable-copy`}
            contentEditable={editable}
            suppressContentEditableWarning
            onBlur={(event) => {
              if (event.relatedTarget instanceof Node && event.currentTarget.contains(event.relatedTarget)) return
              if (wrapperRef.current?.querySelector('.formula-editor')) return
              persist()
            }}
          >
            {domToReact(node.children as DOMNode[], options)}
          </Tag>
        )
      }
    },
  }
  const content = parse(safeHtml, options)

  return <div className="lesson-table" ref={wrapperRef}>{content}</div>
}
