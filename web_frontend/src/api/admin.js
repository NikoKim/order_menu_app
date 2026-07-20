// ===== Admin APIs (管理员端) =====
import client from './client'

export function getUsers(params = {}) {
  return client.get('/admin/users', { params })
}

export function createUser(data) {
  return client.post('/admin/users', data)
}

export function updateUserStatus(userId, status) {
  return client.put(`/admin/users/${userId}/status`, { status })
}
