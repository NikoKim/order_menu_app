export const NAVAL_RANKS = ['下士', '中士', '上士', '少尉', '中尉', '上尉', '少校', '中校', '上校', '少将', '中将', '上将']
const LIMITS = [300, 600, 1000, 1500, 2000, 2800, 3800, 4800, 6000, 7500, 9000, 12000]
const EXAM_LEVELS = new Set([4, 7, 10])

export function navalRankSnapshot(profile) {
  if (!profile || !Number.isFinite(Number(profile.level)) && !Number.isFinite(Number(profile.score))) return null
  const score = Math.max(0, Number(profile.score) || 0)
  const index = LIMITS.findIndex(limit => score <= limit)
  const level = Math.max(1, Math.min(12, Math.trunc(Number(profile.level)) || (index < 0 ? 12 : index + 1)))
  return {level, score, rank:NAVAL_RANKS[level - 1]}
}

export function navalRankChange(previous, current, {exam = false, key = ''} = {}) {
  const from = navalRankSnapshot(previous), to = navalRankSnapshot(current)
  if (!from || !to || to.level <= from.level) return null
  return {from, to, key:key || `rank-${from.level}-${to.level}`, kind:exam || EXAM_LEVELS.has(to.level) ? 'exam' : 'normal'}
}

export function rankPromotionCanPlay({phase, settlementOpen = false, busy = false, replay = false} = {}) {
  return !busy && !replay && (!phase || phase === 'FINISHED' && settlementOpen)
}

export function createNavalRankTracker() {
  let baseline = null
  const pending = [], announced = new Set()
  return {
    observe(profile, options = {}) {
      const next = navalRankSnapshot(profile)
      if (!next) return null
      if (baseline && next.level < baseline.level) return null
      const supplied = navalRankSnapshot(options.previous)
      const previous = [baseline, supplied].filter(rank => rank && rank.level < next.level)
        .sort((left, right) => right.level - left.level || right.score - left.score)[0] || baseline || supplied
      const change = navalRankChange(previous, next, options)
      baseline = next
      if (!change || announced.has(next.level)) return null
      announced.add(next.level)
      pending.push(change)
      return change
    },
    take:() => pending.shift() || null,
    get size() { return pending.length },
    reset() { baseline = null; pending.length = 0; announced.clear() }
  }
}
