import DOMPurify from 'dompurify'
import parse, { attributesToProps, domToReact, Element, type DOMNode, type HTMLReactParserOptions } from 'html-react-parser'
import type { TableBlock as Table } from '../../types/lesson-document'
import { EditableFormula } from './EditableFormula'

export function TableBlock({ block, editable }: { block: Table; editable: boolean }) {
  const safeHtml = DOMPurify.sanitize(block.html, {
    ADD_ATTR: ['data-latex', 'role', 'tabindex'],
  })
  const options: HTMLReactParserOptions = {
    replace(node) {
      if (node instanceof Element && node.attribs?.['data-latex']) {
        return <EditableFormula latex={node.attribs['data-latex']} />
      }
      if (node instanceof Element && (node.name === 'td' || node.name === 'th')) {
        const Tag = node.name
        return (
          <Tag
            {...attributesToProps(node.attribs)}
            className={`${node.attribs.class ?? ''} editable-copy`}
            contentEditable={editable}
            suppressContentEditableWarning
          >
            {domToReact(node.children as DOMNode[], options)}
          </Tag>
        )
      }
    },
  }
  const content = parse(safeHtml, options)

  return <div className="lesson-table">{content}</div>
}
