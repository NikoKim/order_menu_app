const clone = value => JSON.parse(JSON.stringify(value))
export const NAVAL_RECONNECT_MS = 60000
export const NAVAL_RECONNECT_LIMIT = 3
export const navalBattlePaused = room => room?.phase === 'BATTLE' && room.pause?.active === true

const reconnectCount = player => Number.isFinite(Number(player?.reconnectCount)) ? Math.max(0,Math.floor(Number(player.reconnectCount))) : 0
const reconnectFields = count => ({reconnectCount:count,reconnectRemaining:Math.max(0,NAVAL_RECONNECT_LIMIT-count)})

function normalizeLocalPause(current) {
  if(!navalBattlePaused(current) || current.pause.graceMs===NAVAL_RECONNECT_MS && current.pause.maxReconnects===NAVAL_RECONNECT_LIMIT)return current
  const room=clone(current), startedAt=Number(room.pause.startedAt)
  room.pause.graceMs=NAVAL_RECONNECT_MS;room.pause.maxReconnects=NAVAL_RECONNECT_LIMIT
  if(Number.isFinite(startedAt))room.pause.until=startedAt+NAVAL_RECONNECT_MS
  for(const entry of room.pause.players || []) {
    const player=room.players[entry.seat], count=Math.max(1,reconnectCount(player))
    Object.assign(entry,reconnectFields(count),{until:room.pause.until})
    if(player)Object.assign(player,reconnectFields(count),{reconnectUntil:room.pause.until})
  }
  return room
}

function finishLocalDisconnect(current, seat, now, limited=false) {
  const room=clone(current)
  room.phase='FINISHED';room.winner=1-seat;room.finishReason='DISCONNECTED';room.finishedAt=now
  room.deadline=0;room.presentationUntil=0;room._aiNextAt=null;room.nextAiAt=null
  room.pause={active:false,graceMs:NAVAL_RECONNECT_MS,maxReconnects:NAVAL_RECONNECT_LIMIT,players:[]}
  if(room.players[seat]) { room.players[seat].left=true;room.players[seat].connected=false;room.players[seat].reconnectUntil=0 }
  const military=room.ruleSet==='MILITARY', sequence=military?'sequence':'_sequence'
  room[sequence]=(room[sequence]||0)+1
  room.events=[...(room.events||[]),{id:military?room[sequence]:`event-${room[sequence]}`,player:seat,weapon:'disconnected',cells:[],text:limited?'本局第4次断线，直接判负':'离开超过60秒，未及时返回，判负'}]
  return room
}

export function navalPauseSeconds(room, now = Date.now()) {
  const remaining=Number(room?.pause?.until)-now
  return navalBattlePaused(room) && Number.isFinite(remaining) ? Math.max(0,Math.ceil(remaining/1000)) : 0
}

export function pauseLocalBattle(current, now = Date.now()) {
  if (current?.phase !== 'BATTLE') return current
  if (navalBattlePaused(current)) return normalizeLocalPause(current)
  const room = clone(current), seat = room.you ?? 0
  const count=reconnectCount(room.players[seat])+1
  if(room.players[seat])Object.assign(room.players[seat],reconnectFields(count))
  if(count>NAVAL_RECONNECT_LIMIT)return finishLocalDisconnect(room,seat,now,true)
  room.pause = {active:true, startedAt:now, until:now + NAVAL_RECONNECT_MS,
    graceMs:NAVAL_RECONNECT_MS,maxReconnects:NAVAL_RECONNECT_LIMIT,
    remainingMs:Math.max(0, Number(room.deadline || now) - now),
    presentationRemainingMs:Math.max(0, Number(room.presentationUntil || now) - now),
    players:[{seat, personId:room.players[seat]?.id || 'local-player', until:now + NAVAL_RECONNECT_MS,...reconnectFields(count)}]}
  if (room.players[seat]) { room.players[seat].connected = false; room.players[seat].reconnectUntil = room.pause.until }
  return room
}

export function expireLocalBattlePause(current, now = Date.now()) {
  const room=normalizeLocalPause(current)
  if (!navalBattlePaused(room) || now < Number(room.pause.until)) return room
  return finishLocalDisconnect(room,room.pause.players[0]?.seat??0,now)
}

export function resumeLocalBattle(current, now = Date.now()) {
  const expired = expireLocalBattlePause(current, now)
  if (!navalBattlePaused(expired)) return expired
  const room = clone(expired), elapsed = Math.max(0, now - Number(room.pause.startedAt))
  // Shift every running deadline together, including an AI or presentation already in flight.
  for (const key of ['deadline','presentationUntil','_aiNextAt','nextAiAt']) if (Number.isFinite(room[key]) && room[key] > 0) room[key] += elapsed
  for (const {seat} of room.pause.players) if (room.players[seat]) { room.players[seat].connected = true; room.players[seat].reconnectUntil = 0 }
  room.pause = {active:false,graceMs:NAVAL_RECONNECT_MS,maxReconnects:NAVAL_RECONNECT_LIMIT,players:[]}
  return room
}
