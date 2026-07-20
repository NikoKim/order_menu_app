// ===== Customer APIs (顾客端) =====
import client from './client'

// -- Shops (public) --
export function getShops(params = {}) {
  return client.get('/shops', { params })
}

export function getShopDetail(id) {
  return client.get(`/shops/${id}`)
}

export function getShopMenu(id) {
  return client.get(`/shops/${id}/menu`)
}

// -- Dishes (public) --
export function getDishDetail(id) {
  return client.get(`/dishes/${id}`)
}

// -- Orders --
export function createOrder(data) {
  return client.post('/orders', data)
}

export function getMyOrders(params = {}) {
  return client.get('/orders/my', { params })
}

export function getOrderDetail(id) {
  return client.get(`/orders/${id}`)
}

export function cancelOrder(id) {
  return client.put(`/orders/${id}/cancel`)
}

// -- Feedback --
export function submitFeedback(data) {
  return client.post('/feedback', data)
}

export function getMyFeedback(params = {}) {
  return client.get('/feedback/my', { params })
}

// -- Family --
export function getMyFamilies() {
  return client.get('/families/my')
}

export function joinFamily(familyCode) {
  return client.post('/families/join', { familyCode })
}

export function leaveFamily(shopId) {
  return client.delete(`/families/${shopId}`)
}
