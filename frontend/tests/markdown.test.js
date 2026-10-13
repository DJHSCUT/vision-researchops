import { describe, expect, it } from 'vitest'
import { renderMarkdown } from '../src/utils/markdown'
import { source } from './fixtures'

function render(text, sources = []) {
  const element = document.createElement('div')
  element.innerHTML = renderMarkdown(text, sources)
  return element
}

describe('safe answer Markdown', () => {
  it('renders headings, paragraphs, bold, fenced code, lists, tables and breaks', () => {
    const el = render('# 标题\n\n**重点**\n换行\n\n- 一\n- 二\n\n```js\nconst x = 1\n```\n\n| A | B |\n|---|---|\n| 1 | 2 |')
    for (const tag of ['h1', 'p', 'strong', 'br', 'li', 'pre code', 'table', 'th', 'td']) expect(el.querySelector(tag)).not.toBeNull()
  })
  it.each(['<script>window.hacked=true</script>', '<img src=x onerror=alert(1)>', '<svg onload=alert(1)>', '<iframe srcdoc="<script>alert(1)</script>"></iframe>', '<button data-citation-id="1" onclick="alert(1)">伪造引用</button>'])('escapes raw HTML: %s', text => {
    const el = render(text, [source()])
    expect(el.querySelector('script,img,svg,iframe,button')).toBeNull()
    expect(el.textContent).toContain('<')
  })
  it.each(['javascript:alert(1)', 'vbscript:msgbox(1)', 'data:text/html;base64,eA==', 'file:///C:/Users/private', '//example.org', '/docs/rag-demo/x.md', 'https://user:password@example.org'])('does not link unsafe or relative URL %s', href => {
    expect(render(`[链接](${href})`).querySelector('a')).toBeNull()
  })
  it('adds safe attributes to HTTP links', () => {
    const anchor = render('[文档](https://example.org/docs)').querySelector('a')
    expect(anchor.getAttribute('rel')).toBe('noopener noreferrer')
    expect(anchor.target).toBe('_blank')
    expect(anchor.href).toBe('https://example.org/docs')
  })
  it('does not load remote images', () => {
    const el = render('![示意图](https://example.org/track.png)')
    expect(el.querySelector('img')).toBeNull()
    expect(el.textContent).toContain('示意图')
  })
  it('only links verified source IDs; preserves gaps and ignores code and link labels', () => {
    const el = render('[1] [2] [3] `[1]`\n\n```\n[3]\n```\n\n[链接 [1]](https://example.org)', [source(1), source(3)])
    expect([...el.querySelectorAll('[data-citation-id]')].map(node => node.dataset.citationId)).toEqual(['1', '3'])
    expect(el.textContent).toContain('[2]')
  })
  it('redacts local absolute paths in answers and code', () => {
    const el = render('C:\\Users\\Deng\\secret.md file:///C:/Users/Deng/private.md\n`/home/private/key`')
    expect(el.textContent).not.toMatch(/Deng|private|secret/)
    expect(el.textContent).toContain('本机路径已隐藏')
  })
})
