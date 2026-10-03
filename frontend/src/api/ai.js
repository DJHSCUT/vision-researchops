import request from '../utils/request'

export async function sendAiMessage(message) {
  // 一次回答可能包含模型与工具的多轮处理，只延长 AI 请求的超时。
  const data = await request.post('/ai/chat', { message }, { timeout: 180000 })
  if (typeof data?.content !== 'string' || !data.content.trim()) {
    const error = new Error('AI 返回内容为空')
    error.userMessage = 'AI 未返回有效回答，请稍后重试。'
    throw error
  }
  return data.content
}
