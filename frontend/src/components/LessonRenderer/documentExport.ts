import DOMPurify from 'dompurify'
import type { DocxExportInput } from '../../api/documents'
import type { LessonBlock, LessonDocument } from '../../types/lesson-document'

function setClassName(element: HTMLElement, className: string) {
  element.className = className
}

function renderBlock(block: LessonBlock): HTMLElement {
  switch (block.type) {
    case 'heading': {
      const level = Math.min(Math.max(block.level, 1), 6)
      const heading = window.document.createElement(`h${level}`)
      setClassName(heading, `lesson-heading lesson-heading-${level} lesson-heading-${block.alignment}`)
      heading.textContent = block.text
      return heading
    }
    case 'paragraph': {
      const paragraph = window.document.createElement('p')
      setClassName(paragraph, 'lesson-paragraph')
      paragraph.textContent = block.text
      return paragraph
    }
    case 'list': {
      const list = window.document.createElement(block.ordered ? 'ol' : 'ul')
      setClassName(list, `lesson-list ${block.ordered ? 'ordered' : 'unordered'}`)
      for (const item of block.items) {
        const entry = window.document.createElement('li')
        entry.textContent = item
        list.append(entry)
      }
      return list
    }
    case 'table': {
      const wrapper = window.document.createElement('div')
      setClassName(wrapper, 'lesson-table')
      wrapper.innerHTML = DOMPurify.sanitize(block.html, {
        ADD_ATTR: ['data-latex', 'role', 'tabindex'],
      })
      return wrapper
    }
    case 'image': {
      const figure = window.document.createElement('figure')
      setClassName(figure, 'lesson-image')
      const image = window.document.createElement('img')
      image.src = block.src
      image.alt = block.alt ?? ''
      figure.append(image)
      if (block.alt) {
        const caption = window.document.createElement('figcaption')
        caption.textContent = block.alt
        figure.append(caption)
      }
      return figure
    }
    case 'formula': {
      const formula = window.document.createElement('div')
      setClassName(formula, 'lesson-formula')
      const value = window.document.createElement('span')
      value.dataset.latex = block.latex
      formula.append(value)
      return formula
    }
  }
}

/** Rebuild export markup exclusively from persisted document data. */
export function renderDocumentForExport(
  document: LessonDocument,
): HTMLElement {
  const article = window.document.createElement('article')
  setClassName(article, 'lesson-document')
  const blocks = window.document.createElement('div')
  setClassName(blocks, 'document-blocks')

  for (const block of document.blocks) {
    const wrapper = window.document.createElement('div')
    setClassName(
      wrapper,
      `document-block${block.type === 'heading' ? ` document-block-heading-${block.alignment}` : ''}`,
    )
    wrapper.append(renderBlock(block))
    blocks.append(wrapper)
  }

  article.append(blocks)
  return article
}

/** Use the same markup and image embedding for single and batch exports. */
export async function prepareDocxExport(document: LessonDocument): Promise<DocxExportInput> {
  const root = renderDocumentForExport(document)
  await Promise.all(Array.from(root.querySelectorAll('img')).map(async (image) => {
    if (image.src.startsWith('data:')) return
    const response = await fetch(image.src)
    if (!response.ok) throw new Error(`图片资源读取失败（HTTP ${response.status}）`)
    const blob = await response.blob()
    image.src = await new Promise<string>((resolve, reject) => {
      const reader = new FileReader()
      reader.onload = () => resolve(String(reader.result))
      reader.onerror = () => reject(reader.error)
      reader.readAsDataURL(blob)
    })
  }))
  // oxlint-disable-next-line no-control-regex -- Strip control characters that are invalid in filenames.
  const safeTitle = (document.title || 'lesson').replace(/[\u0000-\u001f<>:"/\\|?*]/g, '_').slice(0, 160)
  return { html: root.outerHTML, filename: `${safeTitle}.docx` }
}

export function downloadBlob(blob: Blob, filename: string) {
  const url = URL.createObjectURL(blob)
  const anchor = window.document.createElement('a')
  anchor.href = url
  anchor.download = filename
  anchor.click()
  // Keep the temporary download URL alive until the browser has started consuming it.
  window.setTimeout(() => URL.revokeObjectURL(url), 60_000)
}
