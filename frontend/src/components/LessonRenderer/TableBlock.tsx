import DOMPurify from 'dompurify'
import parse, { attributesToProps, domToReact, Element, type DOMNode, type HTMLReactParserOptions } from 'html-react-parser'
import { useRef } from 'react'
import type { TableBlock as Table } from '../../types/lesson-document'
import { useAssetUrl } from './assetUrlContext'
import { EditableFormula } from './EditableFormula'

function serializeEditedTable(table: HTMLTableElement): string {
  const clone = table.cloneNode(true) as HTMLTableElement
  clone.querySelectorAll('.formula-editor, .formula-edit-hint, .formula-validation-badge').forEach((node) => node.remove())
  clone.querySelectorAll('[data-latex]').forEach((node) => {
    const span = window.document.createElement('span')
    span.setAttribute('data-latex', node.getAttribute('data-latex') ?? '')
    node.replaceWith(span)
  })
  clone.querySelectorAll('[contenteditable]').forEach((node) => node.removeAttribute('contenteditable'))
  clone.querySelectorAll('.editable-copy').forEach((node) => {
    node.classList.remove('editable-copy')
    if (!node.className.trim()) node.removeAttribute('class')
  })
  return clone.outerHTML
}

function TableImage({ src, alt, className }: { src: string; alt?: string; className?: string }) {
  const resolved = useAssetUrl(src)
  return <img src={resolved} alt={alt} className={className} />
}

export function TableBlock({
  block,
  editable,
  onChange,
}: {
  block: Table
  editable: boolean
  onChange?: (block: Table) => void
}) {
  const wrapperRef = useRef<HTMLDivElement>(null)
  const persist = () => {
    const table = wrapperRef.current?.querySelector('table')
    if (!table || !onChange) return
    onChange({ ...block, html: serializeEditedTable(table) })
  }

  const safeHtml = DOMPurify.sanitize(block.html, {
    ADD_ATTR: ['data-latex', 'role', 'tabindex'],
  })
  const options: HTMLReactParserOptions = {
    replace(node) {
      if (node instanceof Element && node.attribs?.['data-latex']) {
        return (
          <EditableFormula
            latex={node.attribs['data-latex']}
            onChange={() => window.setTimeout(persist, 0)}
          />
        )
      }
      if (node instanceof Element && node.name === 'img') {
        return (
          <TableImage
            src={node.attribs.src ?? ''}
            alt={node.attribs.alt}
            className={node.attribs.class}
          />
        )
      }
      if (node instanceof Element && (node.name === 'td' || node.name === 'th')) {
        const Tag = node.name
        return (
          <Tag
            {...attributesToProps(node.attribs)}
            className={`${node.attribs.class ?? ''} editable-copy`}
            contentEditable={editable}
            suppressContentEditableWarning
            onBlur={persist}
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
