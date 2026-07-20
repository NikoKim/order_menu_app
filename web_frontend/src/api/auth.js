// ===== Auth APIs =====
import client from './client'

export function login(nickname, password, role) {
  return client.post('/auth/login', { nickname, password, role })
}

export function customerRegister(nickname, password) {
  return client.post('/auth/customer/register', { nickname, password })
}

// ===== User APIs =====
export function getCurrentUser() {
  return client.get('/users/me')
}

export function updateCurrentUser(data) {
  return client.put('/users/me', data)
}
