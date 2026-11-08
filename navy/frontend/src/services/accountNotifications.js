import { ref } from 'vue'
import { getNotificationSummary } from '../api/account'

export const notificationUnreadCount = ref(0)
let owner = null, pending = null, revision = 0, subscribers = 0, timer

function syncAccount() {
  const token = localStorage.getItem('navy_token')
  if (owner !== token) {
    owner = token
    revision++
    pending = null
    notificationUnreadCount.value = 0
  }
  return token
}

export function refreshNotificationSummary({ force = false } = {}) {
  const token = syncAccount()
  if (!token) return Promise.resolve(null)
  if (pending && !force) return pending.promise
  const request = { token, revision: ++revision }
  pending = request
  request.promise = getNotificationSummary().then(value => {
    if (syncAccount() === token && request.revision === revision) notificationUnreadCount.value = Number(value.unreadCount || 0)
    return value
  }).catch(() => null).finally(() => { if (pending === request) pending = null })
  return request.promise
}

export function updateNotificationUnread(count) {
  syncAccount()
  revision++
  pending = null
  notificationUnreadCount.value = Math.max(0, Number(count) || 0)
}

function visible() {
  if (document.visibilityState === 'visible') refreshNotificationSummary()
}

export function subscribeNotificationSummary() {
  syncAccount()
  subscribers++
  if (subscribers === 1) {
    visible()
    timer = setInterval(visible, 20000)
    document.addEventListener('visibilitychange', visible)
    window.addEventListener('focus', visible)
  }
  let active = true
  return () => {
    if (!active) return
    active = false
    if (--subscribers === 0) {
      clearInterval(timer)
      document.removeEventListener('visibilitychange', visible)
      window.removeEventListener('focus', visible)
    }
  }
}
