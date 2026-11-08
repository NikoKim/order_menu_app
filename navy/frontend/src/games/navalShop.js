import { ref } from 'vue'
import { equipInventoryItem, getShop, purchaseShopItem, useInventoryItem } from '../api/shop'
import { ownNavalProgression, refreshNavalProgression } from './navalProgression'
import { publishNavalAccountReward } from './navalAccountRewards'

export const navalShop = ref(null)
export const navalShopLoading = ref(false)
export const navalShopError = ref('')
export const navalShopActing = ref(false)
let owner = null, pending = null, action = null, revision = 0
const intents = new Map()

export function syncNavalShopAccount() {
  const token = localStorage.getItem('navy_token')
  if (token !== owner) {
    owner = token; revision++; pending = null; action = null; intents.clear(); navalShopActing.value = false
    navalShop.value = null; navalShopError.value = ''; navalShopLoading.value = false
  }
  return token
}

export function refreshNavalShop({ force = false } = {}) {
  const token = syncNavalShopAccount()
  if (!token) return Promise.resolve(null)
  if (pending && !force) return pending.promise
  const request = { token, revision: ++revision }
  pending = request; navalShopLoading.value = true; navalShopError.value = ''
  request.promise = getShop().then(value => {
    if (syncNavalShopAccount() !== token || request.revision !== revision) return null
    navalShop.value = value
    reconcileIntents(value)
    if (ownNavalProgression.value && Number(ownNavalProgression.value.coins) !== Number(value.coins)) refreshNavalProgression({ force: true })
    return value
  }).catch(error => {
    if (syncNavalShopAccount() === token && request.revision === revision) navalShopError.value = error.message || '暂时无法读取商城'
    return null
  }).finally(() => {
    if (pending === request) { pending = null; navalShopLoading.value = false }
  })
  return request.promise
}

function reconcileIntents(value) {
  for (const [key, intent] of intents) {
    if (intent.kind === 'equip' || intent.kind === 'use' && value.inventory?.some(entry => entry.id === intent.id && entry.status === 'USED') || intent.kind === 'purchase' && value.inventory?.some(entry => entry.purchaseRequestId === intent.requestId)) intents.delete(key)
  }
}

export function shopIntentId(kind, id) {
  syncNavalShopAccount()
  const key = `${kind}:${id ?? 'none'}`
  if (!intents.has(key)) intents.set(key, { requestId: crypto.randomUUID(), kind, id })
  return intents.get(key).requestId
}

export async function performShopAction(kind, id, intentId) {
  const token = syncNavalShopAccount()
  if (!token) throw new Error('请先登录账号')
  if (action) throw new Error('上一笔物品操作尚未完成，请稍候')
  const key = `${kind}:${id ?? 'none'}`
  const requestId = intentId || shopIntentId(kind, id), current = { token }
  const before = ownNavalProgression.value
  action = current; navalShopActing.value = true
  try {
    let value
    if (kind === 'purchase') value = await purchaseShopItem(id, requestId)
    else if (kind === 'use') value = await useInventoryItem(id, requestId)
    else if (kind === 'equip') value = await equipInventoryItem(id, requestId)
    else throw new Error('物品操作无效')
    if (syncNavalShopAccount() !== token) return null
    revision++; pending = null; navalShopLoading.value = false; navalShopError.value = ''
    navalShop.value = value; intents.delete(key); reconcileIntents(value)
    let after = await refreshNavalProgression({ force: true })
    if (!after) after = await refreshNavalProgression()
    if (kind === 'use') publishNavalAccountReward(before, after, {account:token,key:`shop:${requestId}`,source:'item',maxCoins:0,maxScore:100})
    return value
  } finally {
    if (action === current) { action = null; navalShopActing.value = false }
  }
}
