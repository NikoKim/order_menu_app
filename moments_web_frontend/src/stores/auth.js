import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { login as apiLogin, getCurrentUser } from '../api/auth'

export const useAuthStore = defineStore('auth', () => {
  const user = ref(null)
  const token = ref(localStorage.getItem('token') || '')
  const loading = ref(false)

  const isLoggedIn = computed(() => !!token.value)
  const role = computed(() => user.value?.role || '')
  const userName = computed(() => user.value?.nickname || '')

  async function loginAction(nickname, password, role) {
    loading.value = true
    try {
      const data = await apiLogin(nickname, password, role)
      token.value = data.token
      user.value = data.user
      localStorage.setItem('token', data.token)
      localStorage.setItem('user', JSON.stringify(data.user))
      return data
    } finally {
      loading.value = false
    }
  }

  async function fetchMe() {
    try {
      const data = await getCurrentUser()
      user.value = data
      return data
    } catch (e) {
      logout()
      throw e
    }
  }

  function logout() {
    token.value = ''
    user.value = null
    localStorage.removeItem('token')
    localStorage.removeItem('user')
  }

  function restoreSession() {
    const saved = localStorage.getItem('user')
    if (saved) {
      try {
        user.value = JSON.parse(saved)
      } catch {
        user.value = null
      }
    }
  }

  return { user, token, loading, isLoggedIn, role, userName, loginAction, fetchMe, logout, restoreSession }
})
