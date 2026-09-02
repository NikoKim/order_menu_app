import client from './client'

const GUEST_KEY = 'entertainment_guest_token'
const GUEST_NAME_KEY = 'entertainment_guest_name'

function guestConfig() {
  const token = localStorage.getItem(GUEST_KEY)
  return {
    skipAuthRedirect: true,
    headers: token ? { 'X-Guest-Token': token } : {}
  }
}

export async function ensureEntertainmentGuest() {
  if (localStorage.getItem('token')) return null
  const existing = localStorage.getItem(GUEST_KEY)
  if (existing) return { token: existing, displayName: localStorage.getItem(GUEST_NAME_KEY) || '游客' }
  const data = await client.post('/entertainment/guests', null, { skipAuthRedirect: true })
  localStorage.setItem(GUEST_KEY, data.token)
  localStorage.setItem(GUEST_NAME_KEY, data.displayName)
  return data
}

export const getModes = () => client.get('/entertainment/modes', guestConfig())
export const getGames = params => client.get('/entertainment/games', { ...guestConfig(), params })
export const getGame = id => client.get(`/entertainment/games/${id}`, guestConfig())
export const getMyGames = params => client.get('/entertainment/my/games', { ...guestConfig(), params })
export const createGame = data => client.post('/entertainment/games', data)
export const updateGame = (id, data) => client.put(`/entertainment/games/${id}`, data)
export const publishGame = id => client.post(`/entertainment/games/${id}/publish`)
export const setGameStatus = (id, status) => client.put(`/entertainment/games/${id}/status`, { status })
export const deleteGame = id => client.delete(`/entertainment/games/${id}`)

export const startGameSession = (id, data = null) => client.post(`/entertainment/games/${id}/sessions`, data, guestConfig())
export const getDailyChallenge = id => client.get(`/entertainment/games/${id}/daily-challenge`, guestConfig())
export const getDailyChallengeById = id => client.get(`/entertainment/challenges/${id}`, guestConfig())
export const startDailyChallenge = (id, data = null) => client.post(`/entertainment/challenges/${id}/sessions`, data, guestConfig())
export const submitDailyChallenge = (id, sessionId) => client.post(`/entertainment/challenges/${id}/entries/${sessionId}`, null, guestConfig())
export const getDailyChallengeLeaderboard = id => client.get(`/entertainment/challenges/${id}/leaderboard`, guestConfig())
export const getGameSession = id => client.get(`/entertainment/sessions/${id}`, guestConfig())
export const syncGameSession = id => client.post(`/entertainment/sessions/${id}/sync`, null, guestConfig())
export const sendGameAction = (id, data) => client.post(`/entertainment/sessions/${id}/actions`, data, guestConfig())
export const getCreditStatus = id => client.get(`/entertainment/sessions/${id}/credit/status`, guestConfig())
export const borrowGameCredit = (id, data) => client.post(`/entertainment/sessions/${id}/credit/borrow`, data, guestConfig())
export const repayGameCredit = (id, data) => client.post(`/entertainment/sessions/${id}/credit/repay`, data, guestConfig())
export const performGameResearch = (id, data) => client.post(`/entertainment/sessions/${id}/research`, data, guestConfig())
export const extendGameLoan = (id, data) => client.post(`/entertainment/sessions/${id}/credit/extend`, data, guestConfig())
export const pauseGameSession = id => client.post(`/entertainment/sessions/${id}/pause`, null, guestConfig())
export const resumeGameSession = id => client.post(`/entertainment/sessions/${id}/resume`, null, guestConfig())
export const abandonGameSession = id => client.delete(`/entertainment/sessions/${id}`, guestConfig())
export const deleteGameSessionRecord = id => client.delete(`/entertainment/sessions/${id}/record`, guestConfig())
export const getLeaderboard = id => client.get(`/entertainment/games/${id}/leaderboard`, guestConfig())
export const getGameHistory = params => client.get('/entertainment/my/sessions', { ...guestConfig(), params })
export const getDefaultAiCredential = () => client.get('/entertainment/ai-default', { skipAuthRedirect: true })
export const saveDefaultAiCredential = aiCredential => client.put('/entertainment/ai-default', aiCredential)
export const deleteDefaultAiCredential = () => client.delete('/entertainment/ai-default')
export const getGameAiCredential = id => client.get(`/entertainment/games/${id}/ai-credential`, guestConfig())
export const saveGameAiCredential = (id, aiCredential) => client.put(`/entertainment/games/${id}/ai-credential`, aiCredential)
export const deleteGameAiCredential = id => client.delete(`/entertainment/games/${id}/ai-credential`)
export const getGamePlayKeys = id => client.get(`/entertainment/games/${id}/play-keys`)
export const createGamePlayKey = (id, data) => client.post(`/entertainment/games/${id}/play-keys`, data)
export const setGamePlayKeyStatus = (id, keyId, status) => client.put(`/entertainment/games/${id}/play-keys/${keyId}/status`, { status })
export const deleteGamePlayKey = (id, keyId) => client.delete(`/entertainment/games/${id}/play-keys/${keyId}`)
export const createGameRoom = (id, data = null) => client.post(`/entertainment/games/${id}/rooms`, data, guestConfig())
export const joinGameRoom = (inviteCode, playKey = null) => client.post('/entertainment/rooms/join', { inviteCode, playKey }, guestConfig())
export const getGameRoom = id => client.get(`/entertainment/rooms/${id}`, guestConfig())
export const configureRoomAi = (id, selection) => client.post(`/entertainment/rooms/${id}/ai-credential`, selection, guestConfig())
export const startGameRoom = (id, options = { useAi: false, useDefaultAi: false }) => client.post(`/entertainment/rooms/${id}/start`, options, guestConfig())
export const leaveGameRoom = id => client.post(`/entertainment/rooms/${id}/leave`, null, guestConfig())
export const closeGameRoom = id => client.delete(`/entertainment/rooms/${id}`, guestConfig())
export const removeGameRoomMember = (roomId, memberId) => client.delete(`/entertainment/rooms/${roomId}/members/${memberId}`, guestConfig())

export function entertainmentGuestToken() {
  return localStorage.getItem(GUEST_KEY) || ''
}
