export const BACKEND_ORIGIN = (import.meta.env.VITE_BACKEND_ORIGIN || '').trim().replace(/\/$/, '')
export const API_BASE_URL = `${BACKEND_ORIGIN}/api`
export function backendAssetUrl(path = '') {
  if (!path || /^https?:\/\//i.test(path)) return path
  return `${BACKEND_ORIGIN}${path.startsWith('/') ? path : `/${path}`}`
}
