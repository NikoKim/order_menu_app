import { ref } from 'vue'
import { navalRankSnapshot } from './navalRankPromotions'

export const navalAccountRewardQueue = ref([])
const seen = new Set()
let sequence = 0
export function clearNavalAccountRewards() { navalAccountRewardQueue.value = []; seen.clear() }
export function publishNavalAccountReward(before, after, {key = '', source = 'reward', maxCoins = Infinity, maxScore = Infinity, origin, account = localStorage.getItem('navy_token')} = {}) {
  if (!account || account !== localStorage.getItem('navy_token') || !before || !after) return null
  const score = Math.max(0, Math.min(maxScore, Number(after.score || 0) - Number(before.score || 0)))
  const coins = Math.max(0, Math.min(maxCoins, Number((Number(after.coins || 0) - Number(before.coins || 0)).toFixed(2))))
  if (!score && !coins) return null
  const identity = account + '|' + (key || `${source}:${after.score}:${after.coins}`)
  if (seen.has(identity)) return null
  seen.add(identity)
  if (seen.size > 256) seen.delete(seen.values().next().value)
  const fromScore = Number(after.score || 0) - score
  const event = {id:`reward-${++sequence}`, account, source, score, coins,
    before:{...navalRankSnapshot({score:fromScore}), coins:Number(after.coins || 0) - coins},
    after:{...navalRankSnapshot(after), coins:Number(after.coins || 0)}}
  if (Number.isFinite(origin?.x) && Number.isFinite(origin?.y)) event.origin = {x:origin.x,y:origin.y}
  navalAccountRewardQueue.value = [...navalAccountRewardQueue.value, event]
  return event
}
export function takeNavalAccountReward() {
  const account = localStorage.getItem('navy_token')
  while (navalAccountRewardQueue.value.length) {
    const [event, ...rest] = navalAccountRewardQueue.value
    navalAccountRewardQueue.value = rest
    if (event.account === account) return event
  }
  return null
}
