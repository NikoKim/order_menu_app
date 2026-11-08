export function navalChatPerson(message, room) {
  const name = message?.name || '指挥官'
  const person = message?.senderId && [...(room?.players || []), ...(room?.spectators || [])]
    .find(candidate => candidate.id === message.senderId)
  return person ? {...person, name:message.name || person.name || name} : {name, registered:false}
}

export function navalChatTimeVisible(messages, index) {
  const at = Number(messages[index]?.at)
  return at > 0 && Number.isFinite(at) && (index === 0 || at - Number(messages[index - 1]?.at || 0) > 60000)
}

export function navalChatTime(value, now = Date.now()) {
  const timestamp = Number(value)
  if (!Number.isFinite(timestamp) || timestamp <= 0) return null
  const at = new Date(timestamp), today = new Date(now)
  if (!Number.isFinite(at.getTime())) return null
  return {datetime:at.toISOString(), label:at.toLocaleString('zh-CN', {
    ...(at.toDateString() === today.toDateString() ? {} : {month:'2-digit', day:'2-digit'}),
    hour:'2-digit', minute:'2-digit', hour12:false
  })}
}
