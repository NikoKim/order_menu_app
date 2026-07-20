// ===== Shop APIs (商家端) =====
import client from './client'

// -- Shop --
export function getMyShop() {
  return client.get('/shop/me')
}

export function updateMyShop(data) {
  return client.put('/shop/me', data)
}

export function getShopNotifications() {
  return client.get('/shop/notifications')
}

// -- Categories --
export function getCategories() {
  return client.get('/shop/categories')
}

export function createCategory(data) {
  return client.post('/shop/categories', data)
}

export function updateCategory(id, data) {
  return client.put(`/shop/categories/${id}`, data)
}

export function deleteCategory(id) {
  return client.delete(`/shop/categories/${id}`)
}

// -- Dishes --
export function getDishes(params = {}) {
  return client.get('/shop/dishes', { params })
}

export function createDish(data) {
  return client.post('/shop/dishes', data)
}

export function updateDish(id, data) {
  return client.put(`/shop/dishes/${id}`, data)
}

export function updateDishStatus(id, status) {
  return client.put(`/shop/dishes/${id}/status`, { status })
}

export function deleteDish(id) {
  return client.delete(`/shop/dishes/${id}`)
}

export function updateShowcaseDishes(dishIds) {
  return client.put('/shop/dishes/showcase', dishIds)
}

// -- Orders --
export function getShopOrders(params = {}) {
  return client.get('/shop/orders', { params })
}

export function getShopOrderDetail(id) {
  return client.get(`/shop/orders/${id}`)
}

export function acceptOrder(id) {
  return client.put(`/shop/orders/${id}/accept`)
}

export function rejectOrder(id) {
  return client.put(`/shop/orders/${id}/reject`)
}

export function completeOrder(id) {
  return client.put(`/shop/orders/${id}/complete`)
}

// -- Feedback --
export function getShopFeedback(params = {}) {
  return client.get('/shop/feedback', { params })
}

export function replyFeedback(id, reply) {
  return client.put(`/shop/feedback/${id}/reply`, { reply })
}

// -- Family --
export function getFamilyMembers(params = {}) {
  return client.get('/shop/family/members', { params })
}

export function approveFamilyMember(id) {
  return client.put(`/shop/family/members/${id}/approve`)
}

export function removeFamilyMember(id) {
  return client.delete(`/shop/family/members/${id}`)
}
