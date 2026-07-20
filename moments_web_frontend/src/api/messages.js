// ===== Messages, Conversations, Notifications APIs =====
import client from './client'
import { apiUrl } from '../config/endpoints'

// --- Conversations ---
export function getConversations() {
  return client.get('/messages/conversations')
}

export function startDirectChatWithUser(userId) {
  return client.post(`/messages/direct/users/${userId}`)
}

export function startDirectChatWithShop(shopId) {
  return client.post(`/messages/direct/shops/${shopId}`)
}

export function searchChatUsers(nickname) {
  return client.get('/messages/users', { params: { nickname } })
}

// --- Conversation Preferences ---
export function getConversationPreferences(conversationId) {
  return client.get(`/messages/conversations/${conversationId}/preferences`)
}

export function updateConversationPreferences(conversationId, data) {
  return client.put(`/messages/conversations/${conversationId}/preferences`, data)
}

export function setConversationMute(conversationId, muted) {
  return client.put(`/messages/conversations/${conversationId}/mute`, { muted })
}

export function hideConversation(conversationId, params = {}) {
  return client.delete(`/messages/conversations/${conversationId}`, { params })
}

// --- Chat Messages ---
export function getMessages(conversationId, params = {}) {
  return client.get(`/messages/conversations/${conversationId}/messages`, { params })
}

export function sendMessage(conversationId, data) {
  return client.post(`/messages/conversations/${conversationId}/messages`, data)
}

export function recallMessage(conversationId, messageId) {
  return client.post(`/messages/conversations/${conversationId}/messages/${messageId}/recall`)
}

export function deleteMessage(conversationId, messageId) {
  return client.delete(`/messages/conversations/${conversationId}/messages/${messageId}`)
}

// Chat images — use a separate fetch call with auth header since they return binary
export function getChatImageUrl(conversationId, filename) {
  const token = localStorage.getItem('token')
  return fetch(apiUrl(`/messages/conversations/${conversationId}/images/${filename}`), {
    headers: { Authorization: `Bearer ${token}` }
  }).then(r => {
    if (!r.ok) throw new Error('图片加载失败')
    return r.blob()
  }).then(blob => URL.createObjectURL(blob))
}

// --- User Profile ---
export function getChatUserProfile(userId, conversationId) {
  return client.get(`/messages/users/${userId}/profile`, { params: { conversationId } })
}

// --- Blocks ---
export function getBlockStatus(userId) {
  return client.get(`/messages/blocks/users/${userId}`)
}

export function blockUser(userId) {
  return client.post(`/messages/blocks/users/${userId}`)
}

export function unblockUser(userId) {
  return client.delete(`/messages/blocks/users/${userId}`)
}

// --- Notifications ---
export function getNotifications() {
  return client.get('/messages/notifications')
}

export function getNotificationUnreadCount() {
  return client.get('/messages/notifications/unread-count')
}

export function markNotificationRead(notificationId) {
  return client.put(`/messages/notifications/${notificationId}/read`)
}

export function markAllNotificationsRead() {
  return client.put('/messages/notifications/read-all')
}

// --- Push Devices ---
export function registerPushDevice(data) {
  return client.post('/messages/push-devices', data)
}

export function unregisterPushDevice(token) {
  return client.delete(`/messages/push-devices/${token}`)
}

// --- Family Groups ---
export function createFamilyGroup() {
  return client.post('/messages/family-groups')
}

export function getFamilyGroup(conversationId) {
  return client.get(`/messages/family-groups/${conversationId}`)
}

export function getFamilyGroupByShop(shopId) {
  return client.get(`/messages/family-groups/by-shop/${shopId}`)
}

export function updateFamilyGroup(conversationId, data) {
  return client.put(`/messages/family-groups/${conversationId}`, data)
}

export function dissolveFamilyGroup(conversationId) {
  return client.post(`/messages/family-groups/${conversationId}/dissolve`)
}

export function restoreFamilyGroup(conversationId) {
  return client.post(`/messages/family-groups/${conversationId}/restore`)
}

export function joinFamilyGroup(conversationId) {
  return client.post(`/messages/family-groups/${conversationId}/join`)
}

export function approveMembership(conversationId) {
  return client.post(`/messages/family-groups/${conversationId}/approve-membership`)
}

export function declineMembership(conversationId) {
  return client.post(`/messages/family-groups/${conversationId}/decline-membership`)
}

export function approveMember(conversationId, userId) {
  return client.post(`/messages/family-groups/${conversationId}/members/${userId}/approve`)
}

export function rejectMember(conversationId, userId) {
  return client.post(`/messages/family-groups/${conversationId}/members/${userId}/reject`)
}

export function leaveFamilyGroup(conversationId) {
  return client.post(`/messages/family-groups/${conversationId}/leave`)
}

export function removeFamilyMember(conversationId, userId) {
  return client.post(`/messages/family-groups/${conversationId}/members/${userId}/remove`)
}

export function muteFamilyGroup(conversationId, muted) {
  return client.put(`/messages/family-groups/${conversationId}/mute`, { muted })
}

// --- Essences ---
export function getEssences(conversationId) {
  return client.get(`/messages/family-groups/${conversationId}/essences`)
}

export function getLatestUnreadEssence(conversationId) {
  return client.get(`/messages/family-groups/${conversationId}/essences/latest-unread`)
}

export function addEssence(conversationId, messageId) {
  return client.post(`/messages/family-groups/${conversationId}/essences/${messageId}`)
}

export function removeEssence(conversationId, messageId) {
  return client.delete(`/messages/family-groups/${conversationId}/essences/${messageId}`)
}

export function pinEssence(conversationId, messageId, pinned) {
  return client.put(`/messages/family-groups/${conversationId}/essences/${messageId}/pin`, { pinned })
}

export function likeEssence(conversationId, messageId, liked) {
  return client.put(`/messages/family-groups/${conversationId}/essences/${messageId}/like`, { liked })
}

export function markEssenceRead(conversationId, data) {
  return client.put(`/messages/family-groups/${conversationId}/essences/read`, data || {})
}

// --- Shares ---
export function shareDishToChat(data) {
  return client.post('/messages/dish-shares', data)
}

export function shareOrderToChat(data) {
  return client.post('/messages/order-shares', data)
}

export function shareShopToChat(data) {
  return client.post('/messages/shop-shares', data)
}

export function shareMomentToChat(data) {
  return client.post('/messages/moment-shares', data)
}
