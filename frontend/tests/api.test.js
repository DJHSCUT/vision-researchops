import { afterEach, describe, expect, it, vi } from 'vitest'
import { AxiosError } from 'axios'
vi.mock('element-plus', () => ({ ElMessage: { error: vi.fn() } }))
import { ElMessage } from 'element-plus'
import request from '../src/utils/request'
import { sendAiMessage } from '../src/api/ai'
import { askRag } from '../src/api/rag'
import { ragResult } from './fixtures'

const adapter = request.defaults.adapter
afterEach(() => { request.defaults.adapter = adapter; vi.unstubAllEnvs() })
function respond(data, code = 200) {
  const seen = vi.fn(async config => ({ status: 200, statusText: 'OK', config, headers: {}, data: { code, data, message: 'internal API_KEY=secret' } }))
  request.defaults.adapter = seen
  return seen
}

describe('AI API requests with real Axios interceptors and mocked transport', () => {
  it('retains the Agent endpoint, request and 180s timeout', async () => {
    const seen = respond({ content: '**PSNR**: 30' })
    expect(await sendAiMessage('R-4 的 PSNR？')).toBe('**PSNR**: 30')
    const config = seen.mock.calls[0][0]
    expect(config.baseURL).toBe('/api')
    expect(config.url).toBe('/ai/chat')
    expect(JSON.parse(config.data)).toEqual({ message: 'R-4 的 PSNR？' })
    expect(config.timeout).toBe(180000)
  })
  it('sends trimmed RAG question and topK=3 outside /api, decodes ApiResponse', async () => {
    const seen = respond(ragResult())
    expect((await askRag('  视角覆盖不足？  ')).sources).toHaveLength(1)
    const config = seen.mock.calls[0][0]
    expect(config.url).toBe('/dev/rag/ask')
    expect(config.baseURL).toBe('')
    expect(JSON.parse(config.data)).toEqual({ question: '视角覆盖不足？', topK: 3 })
  })
  it('does not invoke the dev endpoint in a production build', async () => {
    vi.stubEnv('DEV', false)
    const seen = respond(ragResult())
    await expect(askRag('问题')).rejects.toThrow('本机开发环境')
    expect(seen).not.toHaveBeenCalled()
  })
  it.each([400, 503])('propagates HTTP %s and suppresses unsafe backend toast', async status => {
    request.defaults.adapter = async config => { throw new AxiosError('failure', 'ERR_BAD_RESPONSE', config, {}, { status, data: { message: 'API_KEY=secret C:\\Users\\private stack' } }) }
    const error = await askRag('问题').catch(error => error)
    expect(error.response.status).toBe(status)
    expect(error.userMessage).not.toContain('secret')
    expect(ElMessage.error).not.toHaveBeenCalled()
  })
  it('preserves envelope error code and hides its internal message', async () => {
    respond(null, 503)
    const error = await askRag('问题').catch(error => error)
    expect(error.response.status).toBe(503)
    expect(error.userMessage).not.toContain('secret')
    expect(ElMessage.error).not.toHaveBeenCalled()
  })
  it('rejects malformed envelopes and empty Agent content', async () => {
    request.defaults.adapter = async config => ({ status: 200, config, data: { content: 'invalid' } })
    await expect(sendAiMessage('问题')).rejects.toThrow('响应格式异常')
    respond({ content: '' })
    await expect(sendAiMessage('问题')).rejects.toThrow('内容为空')
  })
  it('keeps existing business API error notifications', async () => {
    respond(null, 400)
    await expect(request.get('/projects')).rejects.toThrow('internal API_KEY=secret')
    expect(ElMessage.error).toHaveBeenCalledWith('internal API_KEY=secret')
  })
})
