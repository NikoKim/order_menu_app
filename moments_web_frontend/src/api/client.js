import axios from 'axios'
import { API_BASE_URL } from '../config/endpoints'

const client = axios.create({
  baseURL: API_BASE_URL,
  timeout: 15000,
  headers: { 'Content-Type': 'application/json' }
})

client.interceptors.request.use(config => {
  const token = localStorage.getItem('token')
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

client.interceptors.response.use(
  response => {
    const payload = response.data
    if (payload.code === 0) {
      return payload.data
    }
    const err = new Error(payload.message || '请求失败')
    err.code = payload.code
    throw err
  },
  error => {
    if (error.response?.status === 401) {
      localStorage.removeItem('token')
      const currentPath = window.location.pathname
      if (!currentPath.includes('/login')) {
        window.location.href = currentPath.startsWith('/shop') ? '/shop/login'
          : currentPath.startsWith('/admin') ? '/admin/login'
          : '/customer/login'
      }
    }
    const message = error.response?.data?.message || error.message || '网络错误'
    const err = new Error(message)
    err.code = error.response?.status || 0
    throw err
  }
)

export default client
