const key = cell => `${cell.x},${cell.y}`

export function markSupportShots(player, support) {
  const cells = support.mode === 'tow' ? support.before?.hits || [] : support.mode === 'repair' ? (support.cells || []).map(key) : []
  const uncertain = new Set(cells)
  player.shots = player.shots.map(shot => shot.result === 'hit' && uncertain.has(key(shot)) ? { ...shot, uncertain: true } : shot)
}

export function recordNavalShot(player, shot) {
  const index = player.shots.findIndex(old => key(old) === key(shot))
  if (index < 0) player.shots.push(shot)
  else if (player.shots[index].uncertain) player.shots[index] = { ...shot }
}

// Upgrade old saves once; subsequent actions update only their affected cells.
export function normalizeShotMarkers(room) {
  if (room.shotMarkersVersion >= 1) return
  for (const event of room.events || []) {
    const own = room.players[event.player], enemy = room.players[1 - event.player]
    if (!own || !enemy) continue
    if (event.weapon === 'repairSupport') markSupportShots(own, event.support || {})
    else for (const [player, cells] of [[enemy, event.cells], [own, event.backfire]]) for (const shot of cells || []) {
      if (player.shots.some(old => old.uncertain && key(old) === key(shot))) recordNavalShot(player, shot)
    }
  }
  room.shotMarkersVersion = 1
}
