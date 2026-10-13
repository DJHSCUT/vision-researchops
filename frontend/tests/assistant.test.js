import { afterEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { ElButton, ElInput } from 'element-plus'
vi.mock('../src/api/ai', () => ({ sendAiMessage: vi.fn() }))
vi.mock('../src/api/rag', () => ({ askRag: vi.fn() }))
import { sendAiMessage } from '../src/api/ai'
import { askRag } from '../src/api/rag'
import AiAssistantView from '../src/views/AiAssistantView.vue'
import AiChatMessage from '../src/components/AiChatMessage.vue'
import { ragResult, source } from './fixtures'

let wrapper
afterEach(() => { wrapper?.unmount(); vi.unstubAllEnvs() })
function page() {
  wrapper = mount(AiAssistantView, { global: { components: { ElButton, ElInput } } })
  return wrapper
}
async function switchMode(mode) {
  await wrapper.findAll('.mode-switch button')[mode === 'rag' ? 1 : 0].trigger('click')
}
async function submit(text) {
  await wrapper.get('textarea').setValue(text)
  await wrapper.get('textarea').trigger('keydown', { key: 'Enter' })
  await flushPromises()
}

describe('AI assistant modes with mocked external APIs', () => {
  it('defaults to Agent, preserves query API and renders Markdown', async () => {
    sendAiMessage.mockResolvedValue('**PSNR** 为 30。')
    page()
    expect(wrapper.get('.mode-switch button').attributes('aria-pressed')).toBe('true')
    await submit('R-4 的 PSNR 和 SSIM 是多少？')
    expect(sendAiMessage).toHaveBeenCalledWith('R-4 的 PSNR 和 SSIM 是多少？')
    expect(askRag).not.toHaveBeenCalled()
    expect(wrapper.get('.markdown-body strong').text()).toBe('PSNR')
  })
  it('sends RAG question with topK=3, renders answer and only backend-cited IDs', async () => {
    askRag.mockResolvedValue(ragResult())
    page(); await switchMode('rag')
    expect(wrapper.get('textarea').attributes('maxlength')).toBe('2000')
    expect(wrapper.text()).toContain('视角覆盖不足')
    await submit('  为什么重建缺失？  ')
    expect(askRag).toHaveBeenCalledWith('为什么重建缺失？', 3)
    expect(sendAiMessage).not.toHaveBeenCalled()
    expect(wrapper.get('.markdown-body table').exists()).toBe(true)
    expect(wrapper.findAll('.cited-sources .source-card')).toHaveLength(1)
    expect(wrapper.get('.cited-sources').text()).toContain('[1]')
    expect(wrapper.get('.cited-sources').text()).not.toContain('[3]')
    expect(wrapper.get('.retrieval-details').text()).toContain('检索编号 3')
    expect(wrapper.get('.source-path').text()).toBe('docs/rag-demo/01-multiview-coverage.md')
    expect(wrapper.get('.source-meta').text()).toContain('Score 0.796')
    expect(wrapper.get('.demo-tag').text()).toBe('DEMO')
    expect(wrapper.find('.source-path a').exists()).toBe(false)
  })
  it('opens and focuses the matching card when clicking a citation', async () => {
    askRag.mockResolvedValue(ragResult())
    page(); await switchMode('rag'); await submit('覆盖不足？')
    const details = wrapper.get('.source-details').element
    details.open = false
    const card = wrapper.get('[data-source-id="1"]').element
    card.scrollIntoView = vi.fn()
    const focus = vi.spyOn(card, 'focus')
    await wrapper.get('[data-citation-id="1"]').trigger('click')
    expect(details.open).toBe(true)
    expect(card.scrollIntoView).toHaveBeenCalled()
    expect(focus).toHaveBeenCalled()
  })
  it('does not make retrieved-only IDs interactive citations', async () => {
    askRag.mockResolvedValue(ragResult({ answer: '回答。[1] 未验证编号 [3]' }))
    page(); await switchMode('rag'); await submit('问题')
    expect(wrapper.find('[data-citation-id="3"]').exists()).toBe(false)
  })
  it('shows insufficient evidence with no invented sources', async () => {
    askRag.mockResolvedValue(ragResult({ answer: '当前知识库没有足够的相关资料支持可靠回答。', sources: [], grounded: false, insufficientEvidence: true, retrievedCount: 0, retrievedSources: [], evidenceStatus: 'NO_RETRIEVAL' }))
    page(); await switchMode('rag'); await submit('如何烤蛋糕？')
    expect(wrapper.get('[role="status"]').text()).toContain('资料不足')
    expect(wrapper.find('.cited-sources').exists()).toBe(false)
    expect(wrapper.find('[role="alert"]').exists()).toBe(false)
  })
  it.each([400, 503])('shows HTTP %s as an independent safe error and supports retry', async status => {
    askRag.mockRejectedValueOnce({ response: { status, data: { message: 'C:\\Users\\private API_KEY=secret java.stack' } } }).mockResolvedValueOnce(ragResult())
    page(); await switchMode('rag'); await submit('覆盖不足？')
    const alert = wrapper.get('[role="alert"]').text()
    expect(alert).toContain(status === 400 ? '请求参数' : '服务暂时不可用')
    expect(alert).not.toMatch(/secret|private|java.stack|资料不足/)
    await wrapper.get('.retry-button').trigger('click'); await flushPromises()
    expect(askRag).toHaveBeenCalledTimes(2)
    expect(wrapper.findAll('.is-user')).toHaveLength(1)
    expect(wrapper.find('.retry-button').exists()).toBe(false)
  })
  it('keeps pending responses and drafts in their original modes after repeated switches', async () => {
    let resolveAgent
    sendAiMessage.mockImplementation(() => new Promise(resolve => { resolveAgent = resolve }))
    askRag.mockResolvedValue(ragResult())
    page(); await submit('Agent 问题')
    await wrapper.get('textarea').setValue('Agent 草稿')
    await switchMode('rag')
    expect(wrapper.find('.chat-message').exists()).toBe(false)
    await submit('RAG 问题')
    await wrapper.get('textarea').setValue('RAG 草稿')
    resolveAgent('仅 Agent 回答'); await flushPromises()
    expect(wrapper.text()).not.toContain('仅 Agent 回答')
    await switchMode('agent')
    expect(wrapper.text()).toContain('仅 Agent 回答')
    expect(wrapper.text()).not.toContain('RAG 问题')
    expect(wrapper.get('textarea').element.value).toBe('Agent 草稿')
    await switchMode('rag')
    expect(wrapper.get('textarea').element.value).toBe('RAG 草稿')
    expect(wrapper.text()).not.toContain('Agent 问题')
  })
  it('blocks duplicate submissions while waiting, retains Shift+Enter and IME behavior', async () => {
    sendAiMessage.mockImplementation(() => new Promise(() => {}))
    page(); await wrapper.get('textarea').setValue('问题')
    await wrapper.get('textarea').trigger('keydown', { key: 'Enter', shiftKey: true })
    await wrapper.get('textarea').trigger('keydown', { key: 'Enter', isComposing: true })
    await wrapper.get('textarea').trigger('compositionstart')
    await wrapper.get('textarea').trigger('keydown', { key: 'Enter' })
    expect(sendAiMessage).not.toHaveBeenCalled()
    await wrapper.get('textarea').trigger('compositionend')
    await wrapper.get('textarea').trigger('keydown', { key: 'Enter' }); await flushPromises()
    await submit('第二个问题')
    expect(sendAiMessage).toHaveBeenCalledTimes(1)
    expect(wrapper.get('button[type="submit"]').attributes('disabled')).toBeDefined()
  })
  it('starts empty after remount; does not create persistent history', async () => {
    sendAiMessage.mockResolvedValue('回答')
    page(); await submit('旧问题'); wrapper.unmount(); page()
    expect(wrapper.find('.chat-message').exists()).toBe(false)
    expect(wrapper.text()).toContain('刷新即清空')
  })
  it('disables the RAG dev mode in production', () => {
    vi.stubEnv('DEV', false); page()
    expect(wrapper.findAll('.mode-switch button')[1].attributes('disabled')).toBeDefined()
  })
  it('keeps long Markdown, titles and paths in wrapping containers without download links', () => {
    wrapper = mount(AiChatMessage, { props: { message: { role: 'assistant', status: 'complete', content: '长回答'.repeat(5000), rag: ragResult({ sources: [source(1, { title: '长标题'.repeat(100), source: `docs/${'long-name'.repeat(100)}.md` })] }) } } })
    expect(wrapper.get('.markdown-body').text()).toHaveLength(15000)
    expect(wrapper.get('.source-title').text()).toContain('长标题')
    expect(wrapper.find('.source-card a').exists()).toBe(false)
  })
})
