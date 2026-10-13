import request from '../utils/request'
import { normalizeRagResponse } from '../utils/rag'

export async function askRag(question, topK = 3) {
  // This endpoint is intentionally available only through the local Vite dev server.
  if (!import.meta.env.DEV) throw new Error('RAG 问答仅在本机开发环境启用')
  const data = await request.post('/dev/rag/ask', { question: question.trim(), topK }, {
    baseURL: '', timeout: 180000, safeAiErrors: true,
  })
  return normalizeRagResponse(data)
}
