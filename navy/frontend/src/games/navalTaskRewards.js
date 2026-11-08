import { claimDailyTask } from '../api/account'
import { ownNavalProgression, refreshNavalProgression, syncNavalProgressionAccount } from './navalProgression'
import { publishNavalAccountReward } from './navalAccountRewards'

const claims = new Map()
export function navalTaskCoinOrigin(element, fallback = null) {
  if (!element || element.isConnected === false) return fallback
  const icon = element.querySelector?.('svg') || element
  const box = icon.getBoundingClientRect?.()
  if (!box || ![box.left,box.top,box.width,box.height].every(Number.isFinite) || box.width <= 0 || box.height <= 0) return fallback
  return {x:box.left + box.width / 2,y:box.top + box.height / 2}
}

export function claimNavalTaskReward(task, {sourceElement, origin} = {}) {
  const account = syncNavalProgressionAccount()
  const initialOrigin = navalTaskCoinOrigin(sourceElement, origin)
  let request
  request = (claims.get(account) || Promise.resolve()).catch(() => {}).then(() => performClaim(task, account, () => navalTaskCoinOrigin(sourceElement, initialOrigin))).finally(() => {
    if (claims.get(account) === request) claims.delete(account)
  })
  claims.set(account, request)
  return request
}

async function performClaim(task, account, readOrigin) {
  if (!account || syncNavalProgressionAccount() !== account) return null
  const before = ownNavalProgression.value
  const day = task.day || before?.day
  const awarded = await claimDailyTask(task.id, day)
  if (syncNavalProgressionAccount() !== account) return null
  const pending = refreshNavalProgression({force:true})
  if (awarded?.tasks) ownNavalProgression.value = awarded
  const latest = await pending, after = latest || awarded
  if (syncNavalProgressionAccount() !== account || !after?.tasks) return null
  publishNavalAccountReward(before, after, {account, key:`task:${day}:${task.id}`, source:'task', origin:readOrigin(), maxCoins:Number(task.coins) || 0, maxScore:Number(task.score) || 0})
  return after
}
