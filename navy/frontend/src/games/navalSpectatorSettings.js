export function navalSpectatorCooldown(room, now = Date.now()) {
  const until = Number(room?.spectatorSettings?.cooldownUntil)
  return Number.isFinite(until) ? Math.max(0, Math.ceil((until - now) / 1000)) : 0
}

export function createNavalSpectatorNotices() {
  let scope = '', round = 0
  const revisions = new Map()
  return {
    observe(room, {baseline = false} = {}) {
      if (!room?.id) return null
      const currentScope = `${room.id}:${room.role || ''}:${room.you}:${room.players?.[room.you]?.id || ''}`
      const currentRound = Number(room.round) || 1, meta = room.spectatorSettings
      const revision = Number(meta?.revision) || 0, previous = revisions.get(currentScope)
      const initial = baseline || scope !== currentScope || round !== currentRound || previous == null
      scope = currentScope; round = currentRound
      revisions.set(currentScope, Math.max(previous || 0, revision))
      if (revisions.size > 24) revisions.delete(revisions.keys().next().value)
      if (initial || revision <= (previous || 0) || Number(meta?.round) !== currentRound || room.mode !== 'MULTIPLAYER' || room.role === 'SPECTATOR' || ![0,1].includes(room.you) || meta?.changedBy !== 1 - room.you || room.allowSpectators !== true) return null
      return room.phase === 'BATTLE' ? '对方已开启观战，本局不计入任务' : '对方已开启观战'
    },
    reset() { scope = ''; round = 0; revisions.clear() }
  }
}
