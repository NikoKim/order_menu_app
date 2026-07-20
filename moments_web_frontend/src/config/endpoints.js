const PRODUCTION_BACKEND_ORIGIN = 'https://order-menu-backend.zsj-unknown.top'

const configuredOrigin = (import.meta.env.VITE_BACKEND_ORIGIN || '').trim()

export const BACKEND_ORIGIN = (
  configuredOrigin || (import.meta.env.PROD ? PRODUCTION_BACKEND_ORIGIN : '')
).replace(/\/$/, '')

export const API_BASE_URL = `${BACKEND_ORIGIN}/api`

export function apiUrl(path = '') {
  const normalized = path.startsWith('/') ? path : `/${path}`
  return `${API_BASE_URL}${normalized}`
}

export function backendAssetUrl(path = '') {
  if (!path || /^https?:\/\//i.test(path)) return path
  const normalized = path.startsWith('/') ? path : `/${path}`
  return `${BACKEND_ORIGIN}${normalized}`
}
