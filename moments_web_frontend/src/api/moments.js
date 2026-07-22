// ===== Moments, Comments, Topics APIs =====
import client from './client'
import axios from 'axios'
import { API_BASE_URL } from '../config/endpoints'

// -- Feeds ---
export function getMoments(params = {}) {
  return client.get('/moments', { params })
}

export function getMomentDetail(id) {
  return client.get(`/moments/${id}`)
}

export function getMomentAvailability(id) {
  return client.get(`/moments/${id}/availability`)
}

export function createMoment(data) {
  return client.post('/moments', data)
}

export function searchMomentMentionUsers(query) {
  return client.get('/moments/mention-users', { params: { query } })
}

export function updateMoment(id, data) {
  return client.put(`/moments/${id}`, data)
}

export function deleteMoment(id) {
  return client.delete(`/moments/${id}`)
}

export function getMomentDrafts(params = {}) {
  return client.get('/moments/drafts', { params })
}

// --- Likes & Reactions ---
export function setMomentLike(id, active) {
  return client.put(`/moments/${id}/like`, { active })
}

export function likeMoment(id) {
  return client.post(`/moments/${id}/likes`)
}

export function unlikeMoment(id) {
  return client.delete(`/moments/${id}/likes`)
}

export function setMomentReaction(id, emoji, active) {
  return client.put(`/moments/${id}/reactions`, { emoji, active })
}

export function addMomentReaction(id, emoji) {
  return client.post(`/moments/${id}/reactions`, { emoji })
}

export function removeMomentReaction(id, emoji) {
  return client.delete(`/moments/${id}/reactions`, { params: { emoji } })
}

// --- Comments ---
export function getMomentComments(momentId, params = {}) {
  return client.get(`/moments/${momentId}/comments`, { params })
}

export function getMomentComment(momentId, commentId) {
  return client.get(`/moments/${momentId}/comments/${commentId}`)
}

export function addMomentComment(momentId, data) {
  return client.post(`/moments/${momentId}/comments`, data)
}

export function getCommentReplies(momentId, commentId, params = {}) {
  return client.get(`/moments/${momentId}/comments/${commentId}/replies`, { params })
}

export function deleteMomentComment(momentId, commentId) {
  return client.delete(`/moments/${momentId}/comments/${commentId}`)
}

export function setCommentLike(momentId, commentId, active) {
  return client.put(`/moments/${momentId}/comments/${commentId}/like`, { active })
}

// --- Topics ---
export function searchTopics(params = {}) {
  return client.get('/moments/topics', { params })
}

export function getTopicSuggestions(query) {
  return client.get('/moments/topics/suggestions', { params: { query } })
}

export function createTopic(name) {
  return client.post('/moments/topics', { name })
}

export function getTopicMoments(topicId, params = {}) {
  return client.get(`/moments/topics/${topicId}`, { params })
}

export function getTopicDetail(topicId) {
  return client.get(`/moments/topics/${topicId}/detail`)
}

// --- Privacy ---
export function getMomentPrivacy(targetUserId) {
  return client.get('/moments/privacy', { params: { targetUserId } })
}

export function updateMomentPrivacy(targetUserId, data) {
  return client.put(`/moments/users/${targetUserId}/privacy`, data)
}

export function getMyProfileVisibility() {
  return client.get('/moments/privacy/profile')
}

export function updateMyProfileVisibility(visibility) {
  return client.put('/moments/privacy/profile', { visibility })
}

export function getMomentProfileSummary(userId) {
  return client.get(`/moments/users/${userId}/summary`)
}

// --- Unread & Mark Read ---
export function getMomentUnreadCount() {
  return client.get('/moments/unread-count')
}

export function markMomentsRead(momentIds) {
  return client.put('/moments/read', { momentIds })
}

// --- Share ---
export function shareMoment(momentId, data) {
  return client.post(`/moments/${momentId}/share`, data)
}

export function recordExternalMomentShare(momentId, commentId = null) {
  return client.post(`/moments/${momentId}/external-shares`, { commentId })
}

export function getMomentUserCard(userId) {
  return client.get(`/moments/users/${userId}/card`)
}

// --- Image Upload (moments) ---
function createMomentUploadClient() {
  const formClient = axios.create({
    baseURL: API_BASE_URL,
    timeout: 60000
  })
  formClient.interceptors.request.use(config => {
    const token = localStorage.getItem('token')
    if (token) {
      config.headers.Authorization = `Bearer ${token}`
    }
    return config
  })
  formClient.interceptors.response.use(
    response => {
      const payload = response.data
      if (payload.code === 0) return payload.data
      const err = new Error(payload.message || '上传失败')
      err.code = payload.code
      throw err
    },
    error => {
      const message = error.response?.data?.message || error.message || '上传失败'
      const err = new Error(message)
      err.code = error.response?.status || 0
      throw err
    }
  )
  return formClient
}

const upClient = createMomentUploadClient()

export function uploadMomentImage(file) {
  const form = new FormData()
  form.append('image', file)
  return upClient.post('/moments/images', form, {
    headers: { 'Content-Type': 'multipart/form-data' }
  })
}

export function uploadMomentCommentImage(file) {
  const form = new FormData()
  form.append('image', file)
  return upClient.post('/moments/comment-images', form, {
    headers: { 'Content-Type': 'multipart/form-data' }
  })
}
