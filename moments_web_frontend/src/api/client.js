import axios from 'axios'
import { API_BASE_URL } from '../config/endpoints'

const client = axios.create({
  baseURL: API_BASE_URL,
  timeout: 15000,
  headers: { 'Content-Type': 'application/json' }
})

function safeServerMessage(message, fallback = '操作未完成，请稍后重试') {
  const value = String(message || '').trim()
  if (!value) return fallback
  if (/\b(?:HTTP\s*)?[45]\d{2}\b|status code|internal server error|sql(?:syntax)?exception|\b[a-z]+exception\b|stack trace|\bat\s+[a-z_$][\w.$]*\([^)]*:\d+\)/i.test(value)) {
    return fallback
  }
  return value
}

function responseMessage(error) {
  const status = Number(error.response?.status || 0)
  const serverMessage = error.response?.data?.message
  if (status === 401) return '登录状态已失效，请重新登录'
  if (status === 403) return safeServerMessage(serverMessage, '当前账号没有执行此操作的权限')
  if (status === 404) return safeServerMessage(serverMessage, '目标内容不存在或已经失效')
  if (status === 409) return safeServerMessage(serverMessage, '当前状态已经变化，请刷新后重试')
  if (status === 429) return '操作过于频繁，请稍后再试'
  if (status >= 500) return '服务暂时无法完成该操作，请稍后重试'
  if (serverMessage) return safeServerMessage(serverMessage)
  if (error.code === 'ECONNABORTED') return '请求等待时间过长，请检查网络后重试'
  if (!error.response) return '无法连接服务，请检查网络后重试'
  return '操作未完成，请稍后重试'
}

function businessMessage(payload) {
  const code = Number(payload?.code || 0)
  if (code >= 500) return '服务暂时无法完成该操作，请稍后重试'
  return safeServerMessage(payload?.message)
}

client.interceptors.request.use(config => {
  if (String(config.url || '').includes('/entertainment/')) {
    config.timeout = 60000
  }
  const token = localStorage.getItem('token')
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

client.interceptors.response.use(
  response => {
    const payload = response.data
    if (payload && Number(payload.code) === 0) {
      return payload.data
    }
    const err = new Error(businessMessage(payload))
    err.code = payload?.code || 0
    throw err
  },
  error => {
    const hadToken = Boolean(localStorage.getItem('token'))
    if (error.response?.status === 401 && hadToken) {
      localStorage.removeItem('token')
      const currentPath = window.location.pathname
      if (!currentPath.includes('/login')) {
        window.location.href = currentPath.startsWith('/shop') ? '/shop/login'
          : currentPath.startsWith('/admin') ? '/admin/login'
          : '/customer/login'
      }
    }
    const err = new Error(responseMessage(error))
    err.code = error.response?.status || 0
    throw err
  }
)

export default client
