import MarkdownIt from 'markdown-it'
import { safeDisplayText } from './rag'

// No untrusted HTML or third-party renderer plugins. All text is escaped by markdown-it.
const md = new MarkdownIt({ html: false, breaks: true, linkify: false, typographer: false })
md.validateLink = href => {
  try {
    const url = new URL(href)
    return ['https:', 'http:'].includes(url.protocol) && !url.username && !url.password
  } catch { return false }
}
md.renderer.rules.image = (tokens, index) => md.utils.escapeHtml(tokens[index].content)
md.renderer.rules.link_open = (tokens, index, options, env, renderer) => {
  tokens[index].attrSet('target', '_blank')
  tokens[index].attrSet('rel', 'noopener noreferrer')
  return renderer.renderToken(tokens, index, options)
}
md.renderer.rules.link_close = () => '</a>'
const renderInline = md.renderer.renderInline.bind(md.renderer)
// Work at token level: citations inside code, images, or links are never interactive.
md.renderer.renderInline = (tokens, options, env) => {
  let linkDepth = 0
  const safeTokens = []
  for (const token of tokens) {
    if (token.type === 'link_open') linkDepth++
    if (token.type === 'text' && !linkDepth) {
      let start = 0
      for (const match of token.content.matchAll(/\[(\d+)\]/g)) {
        if (!env.citationIds.has(match[1])) continue
        if (match.index > start) {
          const before = new token.constructor('text', '', 0)
          before.content = token.content.slice(start, match.index)
          safeTokens.push(before)
        }
        const citation = new token.constructor('citation', 'button', 0)
        citation.content = match[1]
        safeTokens.push(citation)
        start = match.index + match[0].length
      }
      if (start) {
        const after = new token.constructor('text', '', 0)
        after.content = token.content.slice(start)
        safeTokens.push(after)
      } else safeTokens.push(token)
    } else safeTokens.push(token)
    if (token.type === 'link_close') linkDepth--
  }
  return renderInline(safeTokens, options, env)
}
md.renderer.rules.citation = (tokens, index) => {
  const id = tokens[index].content // digits from a verified source ID only
  return `<button type="button" class="citation-link" data-citation-id="${id}" aria-label="查看引用来源 ${id}">[${id}]</button>`
}

export function renderMarkdown(content, sources = []) {
  return md.render(safeDisplayText(content), {
    citationIds: new Set(sources.filter(source => Number.isInteger(source.citationId) && source.citationId > 0)
      .map(source => String(source.citationId))),
  })
}
