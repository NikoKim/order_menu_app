import { ref } from 'vue'
import { getNavalProgression } from '../api/naval'

export const ownNavalProgression = ref(null)
export const navalProgressionError = ref('')
export const navalProgressionLoading = ref(false)
let pending, accountToken = null

export function syncNavalProgressionAccount() {
  const token = localStorage.getItem('navy_token')
  if (token !== accountToken) {
    accountToken = token
    ownNavalProgression.value = null
    navalProgressionError.value = ''
    navalProgressionLoading.value = false
    pending = null
  }
  return token
}

export function refreshNavalProgression({ force = false } = {}) {
  const token = syncNavalProgressionAccount()
  if (!token) return Promise.resolve(null)
  if (!force && pending?.token === token) return pending.promise
  navalProgressionLoading.value = true
  navalProgressionError.value = ''
  const request = { token }
  pending = request
  request.promise = getNavalProgression().then(value => {
    if (syncNavalProgressionAccount() !== token || pending !== request) return null
    ownNavalProgression.value = value
    return value
  }).catch(error => {
    if (syncNavalProgressionAccount() !== token || pending !== request) return null
    navalProgressionError.value = error.message || '读取军衔与任务失败'
    return null
  }).finally(() => {
    if (pending === request) {
      navalProgressionLoading.value = false
      pending = null
    }
  })
  return request.promise
}
