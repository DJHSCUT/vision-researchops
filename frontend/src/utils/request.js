import axios from 'axios'
import { ElMessage } from 'element-plus'

const request = axios.create({ baseURL: '/api', timeout: 15000 })

// Keep errors available to pages so an unavailable service never looks like an empty list.
function rejectWithMessage(error, message) {
  error.userMessage = message
  ElMessage.error(message)
  return Promise.reject(error)
}

request.interceptors.response.use(
  (response) => {
    if (response.status === 204) return null
    const body = response.data
    if (!body || typeof body.code !== 'number' || !Object.hasOwn(body, 'data')) {
      return rejectWithMessage(new Error('Invalid API response'), '服务器响应格式异常，请稍后重试。')
    }
    if (body.code < 200 || body.code >= 300) {
      return rejectWithMessage(new Error(body.message), body.message || '请求失败，请稍后重试。')
    }
    return body.data
  },
  (error) => {
    const backendMessage = error.response?.data?.message
    const fallback = {
      400: '请求参数错误，请检查输入。',
      404: '研究项目不存在。',
      500: '服务暂时不可用，请确认后端及数据库已正常启动。',
      502: '无法连接后端，请确认 Spring Boot 已在 8080 端口启动。',
      503: '服务暂时不可用，请稍后重试。',
      504: '后端响应超时，请稍后重试。',
    }
    const message = backendMessage || (error.response
      ? fallback[error.response.status] || '请求失败，请稍后重试。'
      : error.code === 'ECONNABORTED'
        ? '请求超时，请检查后端服务后重试。'
        : '无法连接服务，请检查网络及后端服务是否已启动。')
    return rejectWithMessage(error, message)
  },
)

export default request
