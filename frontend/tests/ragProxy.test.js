// @vitest-environment node
import { describe, expect, it, vi } from 'vitest'
import { guardLocalRagProxy } from '../dev/ragProxy'
import config from '../vite.config'

function guard(overrides = {}) {
  const req = { url: '/dev/rag/ask', socket: { remoteAddress: '127.0.0.1' }, headers: { host: '127.0.0.1:5173', origin: 'http://127.0.0.1:5173' }, ...overrides }
  const res = { setHeader: vi.fn(), end: vi.fn() }
  const next = vi.fn()
  guardLocalRagProxy(req, res, next)
  return { res, next }
}

describe('local development proxy', () => {
  it('binds loopback and proxies only the exact ask route without rewriting or forwarding identity', () => {
    expect(config.server.host).toBe('127.0.0.1')
    const [path, proxy] = Object.entries(config.server.proxy).find(([path]) => path.includes('dev/rag'))
    expect(new RegExp(path).test('/dev/rag/ask?x=1')).toBe(true)
    for (const url of ['/dev/rag/ask-other', '/dev/rag/demo/import', '/dev/rag/search']) expect(new RegExp(path).test(url)).toBe(false)
    expect(proxy.target).toBe('http://127.0.0.1:8080')
    expect(proxy.xfwd).toBe(false)
    expect(proxy.rewrite).toBeUndefined()
    expect(config.preview.proxy).toEqual({})
  })
  it('allows a local same-origin request', () => { expect(guard().next).toHaveBeenCalledOnce() })
  it.each(['192.168.1.20', '10.0.0.1'])('rejects remote requester %s', remoteAddress => {
    const result = guard({ socket: { remoteAddress } })
    expect(result.res.statusCode).toBe(403)
    expect(result.next).not.toHaveBeenCalled()
  })
  it.each(['forwarded', 'x-forwarded-for', 'x-real-ip'])('rejects rather than strips %s', name => {
    const result = guard({ headers: { host: '127.0.0.1:5173', [name]: '127.0.0.1' } })
    expect(result.res.statusCode).toBe(403)
  })
  it('rejects foreign Origin and DNS-rebinding Host', () => {
    for (const headers of [{ host: '127.0.0.1:5173', origin: 'https://other.example' }, { host: 'other.example:5173' }]) expect(guard({ headers }).res.statusCode).toBe(403)
  })
  it('does not intercept business API requests', () => {
    expect(guard({ url: '/api/ai/chat', socket: { remoteAddress: '10.0.0.1' } }).next).toHaveBeenCalledOnce()
  })
})
