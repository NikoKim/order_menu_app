import client from './client'

const GUEST_KEY = 'navy_guest_token'
const GUEST_NAME_KEY = 'navy_guest_name'
let guestCreation = null

function guestConfig() {
  const token = localStorage.getItem(GUEST_KEY)
  return {
    skipAuthRedirect: true,
    headers: token ? { 'X-Guest-Token': token } : {}
  }
}

export async function ensureGuest() {
  if (localStorage.getItem('navy_token')) return null
  const existing = localStorage.getItem(GUEST_KEY)
  if (existing) return { token: existing, displayName: localStorage.getItem(GUEST_NAME_KEY) || '游客' }
  if (!guestCreation) {
    guestCreation = client.post('/guests', null, { skipAuthRedirect: true }).then(data => {
      if (!data?.token || typeof data.token !== 'string') throw new Error('暂时无法建立游客身份，请稍后重试')
      localStorage.setItem(GUEST_KEY, data.token)
      localStorage.setItem(GUEST_NAME_KEY, data.displayName || '游客')
      return data
    }).finally(() => { guestCreation = null })
  }
  return guestCreation
}

export async function withGuest(request) {
  const authenticated = Boolean(localStorage.getItem('navy_token'))
  await ensureGuest()
  const attemptedToken = guestToken()
  try {
    return await request()
  } catch (error) {
    if (authenticated || localStorage.getItem('navy_token') || Number(error.code) !== 401) throw error
    // Concurrent failures must not remove a replacement created by another request.
    if (guestToken() === attemptedToken) {
      localStorage.removeItem(GUEST_KEY)
      localStorage.removeItem(GUEST_NAME_KEY)
    }
    await ensureGuest()
    return request()
  }
}


export function guestToken() { return localStorage.getItem(GUEST_KEY) || "" }
