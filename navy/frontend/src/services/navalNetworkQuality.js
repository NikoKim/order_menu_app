const healthy = () => ({ weak: false, latencyMs: null, unavailable: false })
const unavailable = () => ({ weak: true, latencyMs: null, unavailable: true })
export const NETWORK_STALE_MS = 15000

// Network frames are transient transport metadata, never part of saved game state.
export function createNavalNetworkQuality(clock = () => performance.now()) {
  let roomId = null, connected = false, seat = -1, people = [], players = [], lastFrameAt = clock(), lastTrafficAt = lastFrameAt, suspended = false
  let stale = false, interrupted = false, active = false
  const matches = row => people[row.seat] && row.personId === people[row.seat]
  const snapshot = () => ({ roomId, connected, players: structuredClone(players), self: !roomId || seat < 0 || !active ? healthy() : interrupted || stale ? unavailable() : { ...healthy(), ...players.find(row => row.seat === seat) } })
  return {
    snapshot,
    reset(id = null, isConnected = false) {
      roomId = id; connected = isConnected; seat = -1; people = []; players = []; stale = false; interrupted = false; active = false; lastFrameAt = clock(); lastTrafficAt = lastFrameAt
      return snapshot()
    },
    connection(value) {
      if (connected === value && (value || interrupted)) return snapshot()
      connected = value; interrupted = !value; players = []; stale = false; lastFrameAt = clock(); lastTrafficAt = lastFrameAt
      return snapshot()
    },
    room(state) {
      connected = true; interrupted = false
      lastTrafficAt = clock(); stale = false
      const previousSeat = seat, previousPerson = people[seat], wasActive = active
      active = !state?.phase || ['WAITING', 'PLACEMENT', 'SUPPLY', 'BATTLE'].includes(state.phase)
      seat = state?.role !== 'SPECTATOR' && Number.isInteger(state?.you) && state.you >= 0 && state.you <= 1 ? state.you : -1
      people = [0, 1].map(index => state?.players?.[index]?.occupied === false || state?.players?.[index]?.left === true ? '' : String(state?.players?.[index]?.id || ''))
      if (previousSeat !== seat || previousPerson !== people[seat] || wasActive !== active) { stale = false; lastFrameAt = clock() }
      players = active && seat >= 0 ? players.filter(matches) : []
      return snapshot()
    },
    receive(rows) {
      if (!connected || !roomId || !active || !Array.isArray(rows)) return snapshot()
      const seats = new Set(), next = []
      for (const row of rows) {
        if (!row || !Number.isInteger(row.seat) || row.seat < 0 || row.seat > 1 || seats.has(row.seat)) continue
        if (typeof row.personId !== 'string' || !matches(row)) continue
        if (row.latencyMs !== null && (!Number.isFinite(row.latencyMs) || row.latencyMs < 0 || row.latencyMs > 60000)) continue
        seats.add(row.seat)
        next.push({ seat: row.seat, personId: row.personId, weak: row.weak === true || row.unavailable === true, unavailable: row.unavailable === true, latencyMs: row.unavailable === true ? null : row.latencyMs === null ? null : Math.round(row.latencyMs) })
      }
      if (!next.some(row => row.seat === seat)) return snapshot()
      players = next; stale = false; lastFrameAt = clock(); lastTrafficAt = lastFrameAt
      return snapshot()
    },
    tick() {
      if (roomId && active && !suspended) {
        if (clock() - lastFrameAt >= NETWORK_STALE_MS) players = []
        stale = clock() - lastTrafficAt >= NETWORK_STALE_MS
      }
      return snapshot()
    },
    visibility(hidden) {
      suspended = hidden
      if (!hidden) { lastFrameAt = clock(); lastTrafficAt = lastFrameAt; stale = false; players = [] }
      return snapshot()
    }
  }
}
