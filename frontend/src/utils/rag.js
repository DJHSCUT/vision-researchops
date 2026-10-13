export const INSUFFICIENT_MESSAGE = '当前知识库没有足够的相关资料支持可靠回答。'

// Display only; never turn filesystem identifiers into URLs.
export function safeDisplayText(text) {
  return String(text ?? '')
    .replace(/file:\/{2,3}[^\s<>"'`]+/gi, '〔本机路径已隐藏〕')
    .replace(/\b[a-z]:[\\/][^\s<>"'`]+/gi, '〔本机路径已隐藏〕')
    .replace(/\\\\[^\s<>"'`]+/g, '〔本机路径已隐藏〕')
    .replace(/(?<![\w/:.])\/(?:Users|home|tmp|var|mnt|opt|root)\/[^\s<>"'`]+/gi, '〔本机路径已隐藏〕')
}

function validSource(item) {
  if (!item || !Number.isInteger(item.citationId) || item.citationId < 1
    || typeof item.title !== 'string' || !item.title.trim()
    || typeof item.source !== 'string' || !item.source.trim()
    || !Number.isInteger(item.chunkIndex) || item.chunkIndex < 0
    || !Number.isFinite(item.score) || item.score < 0 || item.score > 1
    || typeof item.isDemo !== 'boolean' || typeof item.usedInContext !== 'boolean') return false
  const path = item.source
  return !/^[\\/]|[:\\\u0000-\u001f?#]/.test(path)
    && !path.split('/').some(segment => !segment || segment === '.' || segment === '..')
}

export function normalizeRagResponse(data) {
  if (!data || typeof data.answer !== 'string' || !data.answer.trim()
    || !Array.isArray(data.sources) || !Array.isArray(data.retrievedSources)
    || typeof data.grounded !== 'boolean' || typeof data.insufficientEvidence !== 'boolean'
    || !Number.isInteger(data.retrievedCount) || data.retrievedCount < 0
    || typeof data.evidenceStatus !== 'string') throw new Error('RAG 响应格式异常')
  const retrievedSources = data.retrievedSources.filter(validSource)
    .map(item => ({ ...item, title: safeDisplayText(item.title) }))
  const uniqueIds = new Set(data.sources.map(item => item?.citationId))
  const sourcesValid = data.sources.length > 0 && uniqueIds.size === data.sources.length
    && data.sources.every(item => validSource(item) && item.usedInContext
      && retrievedSources.some(hit => hit.citationId === item.citationId && hit.usedInContext
        && hit.source === item.source && hit.chunkIndex === item.chunkIndex && hit.title === safeDisplayText(item.title)
        && hit.isDemo === item.isDemo && hit.score === item.score))
  const grounded = data.grounded && !data.insufficientEvidence && sourcesValid
  // A malformed successful response is a service/data error, not a knowledge shortage.
  if (!data.insufficientEvidence && !grounded) throw new Error('RAG 引用来源响应格式异常')
  return {
    ...data, answer: grounded ? safeDisplayText(data.answer) : INSUFFICIENT_MESSAGE,
    grounded, insufficientEvidence: !grounded,
    sources: grounded ? data.sources.map(item => ({ ...item, title: safeDisplayText(item.title) })) : [],
    retrievedSources,
    evidenceStatus: data.evidenceStatus,
  }
}

export function aiErrorMessage(error, mode) {
  const label = mode === 'rag' ? '科研知识问答' : 'AI'
  switch (error.response?.status) {
    case 400: return '请求参数不正确，请检查问题长度和输入后重试。'
    case 403: return '科研知识问答仅允许可信本机开发访问，请检查本机服务配置。'
    case 404: return `${label}接口未启用，请检查后端服务与启用配置。`
    default: return error.code === 'ECONNABORTED'
      ? `${label}服务响应超时，请稍后重试。`
      : `${label}服务暂时不可用，请检查服务后重试。`
  }
}
