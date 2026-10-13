import { describe, expect, it } from 'vitest'
import { normalizeRagResponse, aiErrorMessage, INSUFFICIENT_MESSAGE } from '../src/utils/rag'
import { ragResult, source } from './fixtures'

describe('RAG response boundary', () => {
  it('keeps cited and retrieved sources distinct with backend IDs, scores and DEMO flags', () => {
    const data = normalizeRagResponse(ragResult())
    expect(data.sources.map(item => item.citationId)).toEqual([1])
    expect(data.retrievedSources.map(item => item.citationId)).toEqual([1, 3])
    expect(data.sources[0]).toEqual(source())
    expect(data.grounded).toBe(true)
  })
  it.each(['NO_RETRIEVAL', 'NO_QUALIFIED_EVIDENCE', 'CITATION_VALIDATION_FAILED', 'MODEL_INSUFFICIENT_EVIDENCE'])('shows %s as insufficient evidence without cited sources', evidenceStatus => {
    const data = normalizeRagResponse(ragResult({ answer: '资料不足', grounded: false, insufficientEvidence: true, sources: [source()], evidenceStatus }))
    expect(data.sources).toEqual([])
    expect(data.answer).toBe(INSUFFICIENT_MESSAGE)
    expect(data.insufficientEvidence).toBe(true)
  })
  it.each([null, {}, { answer: '' }, ragResult({ sources: null }), ragResult({ retrievedCount: '2' })])('rejects malformed response %j', value => {
    expect(() => normalizeRagResponse(value)).toThrow('RAG 响应格式异常')
  })
  it.each([{ source: 'C:\\Users\\secret.md' }, { source: 'file:///C:/secret.md' }, { source: '../secret.md' }, { source: '/home/secret.md' }, { title: '' }, { chunkIndex: -1 }, { score: NaN }, { isDemo: undefined }, { usedInContext: false }])('does not certify invalid cited metadata %j', overrides => {
    expect(() => normalizeRagResponse(ragResult({ sources: [source(1, overrides)] }))).toThrow('引用来源响应格式异常')
  })
  it('does not display invalid retrieval metadata', () => {
    const data = normalizeRagResponse(ragResult({ grounded: false, insufficientEvidence: true, sources: [], retrievedSources: [source(4, { source: '/tmp/secret.md' })] }))
    expect(data.retrievedSources).toEqual([])
  })
  it('treats inconsistent successful response and DEMO flags as response failures', () => {
    for (const data of [ragResult({ grounded: false }), ragResult({ sources: [source(1, { isDemo: false })] }), ragResult({ sources: [source(1, { score: 0.1 })] })]) {
      expect(() => normalizeRagResponse(data)).toThrow('引用来源响应格式异常')
    }
  })
  it('does not certify duplicate or unreturned citation IDs', () => {
    for (const sources of [[source(), source()], [source(99)]]) {
      expect(() => normalizeRagResponse(ragResult({ sources }))).toThrow('引用来源响应格式异常')
    }
  })
  it.each([400, 403, 404, 500, 502, 503, 504])('maps HTTP %s to safe error text instead of insufficiency', status => {
    const text = aiErrorMessage({ response: { status, data: { message: 'API_KEY=private stack trace' } } }, 'rag')
    expect(text).not.toMatch(/private|stack|资料不足/)
    expect(text.length).toBeGreaterThan(8)
  })
  it('handles transport failure and timeout', () => {
    expect(aiErrorMessage({}, 'rag')).toContain('服务暂时不可用')
    expect(aiErrorMessage({ code: 'ECONNABORTED' }, 'agent')).toContain('响应超时')
  })
})
