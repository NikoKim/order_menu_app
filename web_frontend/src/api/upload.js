// ===== Upload APIs =====
import axios from 'axios'

const BASE_URL = '/api'

function createUploadClient() {
  const formClient = axios.create({
    baseURL: BASE_URL,
    timeout: 60000
  })
  formClient.interceptors.request.use(config => {
    const token = localStorage.getItem('token')
    if (token) {
      config.headers.Authorization = `Bearer ${token}`
    }
    return config
  })
  formClient.interceptors.response.use(
    response => {
      const payload = response.data
      if (payload.code === 0) return payload.data
      const err = new Error(payload.message || '上传失败')
      err.code = payload.code
      throw err
    },
    error => {
      const message = error.response?.data?.message || error.message || '上传失败'
      const err = new Error(message)
      err.code = error.response?.status || 0
      throw err
    }
  )
  return formClient
}

const upClient = createUploadClient()

export function uploadAvatar(file) {
  const form = new FormData()
  form.append('file', file)
  return upClient.post('/upload/avatar', form, {
    headers: { 'Content-Type': 'multipart/form-data' }
  })
}

export function uploadShopImage(file) {
  const form = new FormData()
  form.append('file', file)
  return upClient.post('/upload/shop', form, {
    headers: { 'Content-Type': 'multipart/form-data' }
  })
}

export function uploadMenuImage(shopId, file) {
  const form = new FormData()
  form.append('shopId', String(shopId))
  form.append('file', file)
  return upClient.post('/upload/menu', form, {
    headers: { 'Content-Type': 'multipart/form-data' }
  })
}

export function uploadFamilyImage(file) {
  const form = new FormData()
  form.append('file', file)
  return upClient.post('/upload/family', form, {
    headers: { 'Content-Type': 'multipart/form-data' }
  })
}
