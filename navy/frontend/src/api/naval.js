import client from './client'
import { withGuest, guestToken } from './guest'
import { API_BASE_URL } from '../config/endpoints'
import { lobbyFeed } from '../services/navalLobbyFeed'
import { hasLiveRoom, waitForRoomVersion } from '../services/roomSync'
const lobbyIdentity=()=>localStorage.getItem('navy_token')||guestToken()

const root = '/naval'
export const getNavalRankingPage = (mode,metric,ruleSet,page,pageSize) => client.get(`${root}/leaderboard/page`,{...config(),params:{mode,metric,ruleSet,page,pageSize}})
export const heartbeatNavalRoom = id => withGuest(()=>client.post(`${root}/rooms/${encodeURIComponent(id)}/heartbeat`,null,config()))
export const getMilitaryTemplate = () => client.get(`${root}/military-template`, {skipAuthRedirect:true,timeout:2000,signal:AbortSignal.timeout(2000)})
export const getNavalMedals = player => withGuest(() => client.get(`${root}/medals`, {...config(),params:{player}}))
export const getNavalProgression = () => client.get(`${root}/progression`, config())
export const equipNavalMedal = medalId => withGuest(() => client.put(`${root}/medals`, {medalId}, config()))
const config = () => ({ skipAuthRedirect: true, headers: { 'X-Guest-Token': guestToken() } })
export const getNavalLeaderboard = (mode, metric, ruleSet = 'CLASSIC', page = 1, pageSize = 100) => client.get(`${root}/leaderboard`, { ...config(), params: { mode, metric, ruleSet, page, pageSize } })
export const getNavalHistory = id => withGuest(() => client.get(`${root}/history/${encodeURIComponent(id)}`, config()))
export const getNavalLobbyMessages = () => withGuest(() => lobbyFeed.read(lobbyIdentity(),params=>client.get(`${root}/lobby/sync`,{...config(),params})))
export const sendNavalLobbyMessage = value => withGuest(() => lobbyFeed.post(lobbyIdentity(),afterId=>client.post(`${root}/lobby/messages`,typeof value==='string'?{text:value}:value,{...config(),params:{afterId}})))
export const getNavalReviews = params => withGuest(() => client.get(`${root}/reviews`, { ...config(), params }))
export const saveNavalReview = data => client.put(`${root}/reviews/me`, data, config())
export const deleteNavalReview = () => client.delete(`${root}/reviews/me`, config())
export const getNavalRooms = () => client.get(`${root}/rooms`, config())
export const enterNavalRoom = (id, inviteCode = '', quotedWager) => withGuest(() => client.post(`${root}/rooms/${encodeURIComponent(id)}/enter`, { inviteCode, ...(Number.isFinite(quotedWager)?{wager:quotedWager}:{}) }, config()))
const localPublications = new Map()
export const invalidateLocalNavalPublication = id => localPublications.delete(id)
export async function publishLocalNavalRoom(room) {
 if(room.allowSpectators===false){
  if(!localPublications.has(room.id)||localPublications.get(room.id)==='hidden')return {spectators:[]}
  const result=await withGuest(()=>client.put(`${root}/rooms/local/access`,{id:room.id},config()))
  localPublications.set(room.id,'hidden')
  return result
 }
 const payload = {
  id: room.id, ruleSet: room.ruleSet || 'CLASSIC', round: room.round || 1, phase: room.phase, deadline: room.deadline, turn: room.turn, winner: room.winner,
  allowSpectators: room.allowSpectators !== false, presentationUntil: room.presentationUntil || 0, pause:room.pause,
  players: room.players.map(p => ({ name: p.name, stage: p.stage, ready: p.ready, left: Boolean(p.left), ships: p.ships, shots: p.shots, mines: p.mines, scrapMines:p.scrapMines||[], aa: p.aa, inventory: p.inventory, coins: p.coins, radar: p.radar })),
  events: (room.events || []).slice(-1000).map(e => ({ id: Number(String(e.id).replace('event-', '')), player: e.player, weapon: e.weapon, cells: e.cells || [], backfire: e.backfire || [], text: e.text, x: e.x, y: e.y, row: e.row, targets: e.targets || [], route: e.route || [], torpedoes: e.torpedoes || [], destroyed: Boolean(e.destroyed), support:e.support, shipId: e.shipId, plane: e.plane || 0 }))
 }
 const signature=JSON.stringify(payload)
 if(localPublications.get(room.id)===signature)return {spectators:room.spectators||[]}
 const result=await withGuest(()=>client.put(`${root}/rooms/local`,payload,config()))
 if(localPublications.size>20)localPublications.clear()
 localPublications.set(room.id,signature)
 return result
}
export async function createNavalRoom(options) {
  return withGuest(() => client.post(`${root}/rooms`, options, config()))
}
export async function joinNavalRoom(inviteCode, quotedWager) {
  return withGuest(() => client.post(`${root}/rooms/join`, { inviteCode, ...(Number.isFinite(quotedWager)?{wager:quotedWager}:{}) }, config()))
}
export const getNavalInviteTerms = (inviteCode,seat) => withGuest(() => client.get(`${root}/rooms/invite/${encodeURIComponent(inviteCode)}/terms`, { ...config(), params:{seat} }))
export async function spectateNavalRoom(inviteCode, viewMode = 'BOTH') {
  return withGuest(() => client.post(`${root}/rooms/spectate`, { inviteCode, viewMode }, config()))
}
export async function getNavalRoom(id, version = -1) {
  return withGuest(() => client.get(`${root}/rooms/${encodeURIComponent(id)}`, {...config(),params:{version}}))
}
export const navalAction = (id, action) => {
 const request=JSON.parse(JSON.stringify({...action,requestId:action.requestId||crypto.randomUUID()}))
 return withGuest(async()=>{
  const compact=hasLiveRoom(id)&&['ATTACK','DEPLOY','LAYOUT_READY','READY','CHAT','PRESENTED','SPECTATORS'].includes(action.type)
  let result
  for(let attempt=0;attempt<2;attempt++){
    try{result=await client.post(`${root}/rooms/${encodeURIComponent(id)}/actions`,request,{...config(),params:{compact}});break}
    catch(error){if(attempt||error.code&&Number(error.code)<500)throw error;await new Promise(resolve=>setTimeout(resolve,350))}
  }
  if(!result.accepted)return result
  if(result.id&&result.id!==id)return client.get(`${root}/rooms/${encodeURIComponent(result.id)}`,config())
  try{return await waitForRoomVersion(id,result.version)}catch{return client.get(`${root}/rooms/${encodeURIComponent(id)}`,config())}
 })}
export function notifyNavalConnection(id, pageToken, connected) {
  const token = localStorage.getItem('navy_token')
  return fetch(`${API_BASE_URL}${root}/rooms/${encodeURIComponent(id)}/connection`, {
    method: 'POST', keepalive: true,
    headers: { 'Content-Type': 'application/json', ...(token ? { Authorization: `Bearer ${token}` } : {}), 'X-Guest-Token': guestToken() },
    body: JSON.stringify({ pageToken, connected })
  }).then(async response => response.ok ? (await response.json()).data : null).catch(() => null)
}
export const getNavalProfile = () => client.get(`${root}/profile`, config())
export const saveNavalProfile = profile => client.put(`${root}/profile`, profile, config())
export async function heartbeatNavalPresence(pageToken) {
  return withGuest(() => client.post(`${root}/presence`, { pageToken }, config()))
}
export function leaveNavalPresence(pageToken) {
  const token = localStorage.getItem('navy_token')
  return fetch(`${API_BASE_URL}${root}/presence/${encodeURIComponent(pageToken)}`, {
    method: 'DELETE', keepalive: true,
    headers: { ...(token ? { Authorization: `Bearer ${token}` } : {}), 'X-Guest-Token': guestToken() }
  }).catch(() => {})
}
