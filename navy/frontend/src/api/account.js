import client from './client'

export const getNotifications = beforeId => client.get('/notifications', { params: beforeId ? { beforeId } : {} })
export const getNotificationSummary = () => client.get('/notifications/summary')
export const readNotification = id => client.post(`/notifications/${id}/read`)
export const claimNotification = id => client.post(`/notifications/${id}/claim`)
export const claimDailyTask = (id, day) => client.post(`/naval/progression/tasks/${encodeURIComponent(id)}/claim`, { day })
export const searchAccounts = query => client.get('/admin/users', { params: { query } })
export const sendNotification = payload => client.post('/admin/notifications', payload)
export const getAdminShopItems = () => client.get('/admin/shop-items')
export const saveAccountSettings = settings => client.patch('/account/settings', settings)
