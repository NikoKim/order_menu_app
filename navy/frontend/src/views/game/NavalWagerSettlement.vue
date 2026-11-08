<template>
  <div ref="layer" class="wager-transfer" :class="[plan?.kind, { running:started && !complete, complete, 'split-layout':split, 'transfer-paused':paused, 'own-left':seat === 0, 'own-right':seat === 1 }]" :data-state="!ready ? 'loading' : complete ? 'complete' : started ? 'playing' : 'pending'" aria-label="赌注金币到账">
    <Teleport v-if="split && potTarget" :to="potTarget"><Transition name="split-pot"><div v-if="initialPotVisible" ref="pot" class="transfer-pot split-pot"><Coins :size="17" /><span>奖池</span><strong>{{ format(frame?.potCents ?? plan?.potCents ?? 0) }}</strong></div></Transition></Teleport>
    <div v-if="!split && hasWager" ref="pot" class="transfer-pot"><Coins :size="17" /><span>奖池</span><strong>{{ format(frame?.potCents ?? plan?.potCents ?? 0) }}</strong></div>
    <div ref="wallet" class="transfer-wallet" :class="{ 'wallet-pulse':pulse }"><Wallet :size="17" /><span>账户金币</span><output aria-label="结算后的账户金币">{{ ready ? format(displayedBalance) : '—' }}</output><strong v-if="complete && plan?.amountCents > 0" class="wallet-change" aria-label="本次金币变动">{{ plan?.kind === 'win' ? '+' : '-' }}{{ format(plan?.amountCents) }}</strong></div>
    <Teleport :to="flightTarget"><div class="naval-coin-flight-layer" :class="[plan?.kind, { 'reduce-motion':reduced, 'transfer-paused':paused }]" data-flight-kind="settlement" aria-hidden="true"><span v-for="coin in activeCoins" :key="coin.id" class="settlement-flying-coin" :style="coinStyle(coin)"><Coins :size="13" /></span></div></Teleport>
    <span class="sr-only" role="status">{{ complete ? hasWager ? '赌注已结算' : '账户金币已更新' : '' }}</span>
  </div>
</template>

<script setup>
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import { Coins, Wallet } from 'lucide-vue-next'
import { wagerSettlementPlan, walletSettlementPlan, wagerTransferFrame, claimWagerPresentation, hasWagerPresentation } from '../../games/navalWagerSettlement'
import { createNavalUnitCounter } from '../../games/navalUnitCounter'
import { playNavalWagerSound } from '../../games/navalAudio'
import { useFullscreenTarget } from '../../composables/useFullscreenTarget'

const props = defineProps({wager:Object, seat:Number, balance:{default:null}, gameKey:String, accountKey:String, paused:Boolean, reward:Object,split:Boolean,potTarget:[Object,String]})
const emit = defineEmits(['complete', 'reward-complete','pot-visible'])
const flightTarget = useFullscreenTarget()
const layer = ref(null), pot = ref(null), wallet = ref(null), elapsed = ref(0), started = ref(false), complete = ref(false)
const initialPlan = ref(null), rewardPlan = ref(null), points = ref({pot:{x:0,y:0}, wallet:{x:0,y:0}})
const viewportOffset = ref({x:0,y:0})
const transferred = ref(0), counterDone = ref(false)
const initialPotVisible = ref(false)
const hasWager = computed(() => props.wager?.amount > 0 || props.wager?.pot > 0)
const currentPlan = computed(() => wagerSettlementPlan(props.wager, props.seat, props.balance) || walletSettlementPlan(props.balance, props.wager?.status==='REFUNDED'?0:props.wager?.pot))
const plan = computed(() => rewardPlan.value || initialPlan.value || currentPlan.value)
const ready = computed(() => currentPlan.value?.balanceCents != null)
const frame = computed(() => {
  const value = wagerTransferFrame(plan.value, transferred.value)
  if (value && rewardPlan.value) value.potCents = wagerTransferFrame(initialPlan.value, initialPlan.value?.amountCents)?.potCents || 0
  return value
})
const displayedBalance = computed(() => complete.value || !started.value && (reduced || hasWagerPresentation(props.accountKey,props.gameKey)) ? plan.value?.balanceCents : frame.value?.balanceCents ?? currentPlan.value?.balanceCents)
const pulse = computed(() => started.value && !complete.value && plan.value?.coins.some(coin => Math.abs(elapsed.value - coin.startsAt - coin.travel) < 75))
const activeCoins = computed(() => started.value && !complete.value ? (plan.value?.coins || []).filter(coin => elapsed.value >= coin.startsAt && elapsed.value < coin.startsAt + coin.travel) : [])
const format = cents => (Math.round(cents || 0) / 100).toLocaleString('zh-CN', {maximumFractionDigits:2})
let frameId, startTimer, observer, lastTime, stopSound, counter, disposed = false, mounted = false, generation = 0
let initialComplete = false, activeRewardId
const rewardQueue = [], seenRewards = new Set()
const reduced = Boolean(globalThis.matchMedia?.('(prefers-reduced-motion: reduce)').matches)
function measure() {
  if (!layer.value) return
  const box = layer.value.getBoundingClientRect()
  viewportOffset.value = {x:box.left, y:box.top}
  const center = element => { if(!element)return {x:box.width/2,y:box.height/2};const target = element.getBoundingClientRect(); return {x:target.left + target.width / 2 - box.left, y:target.top + target.height / 2 - box.top} }
  const taskCoins = layer.value.closest('.settlement-content')?.querySelector('[data-task-coins]')
  const origin = rewardPlan.value?.origin
  points.value = {pot:center(pot.value), wallet:center(wallet.value), reward:Number.isFinite(origin?.x) && Number.isFinite(origin?.y)
    ? {x:origin.x - box.left, y:origin.y - box.top}
    : taskCoins ? center(taskCoins) : {x:box.width / 2, y:box.height + 40}}
}
function coinStyle(coin) {
  const value = Math.max(0, Math.min(1, (elapsed.value - coin.startsAt) / coin.travel))
  const from = rewardPlan.value ? points.value.reward
    : points.value[plan.value.kind === 'win' ? 'pot' : 'wallet']
  const to = points.value[plan.value.kind === 'win' ? 'wallet' : 'pot']
  const ease = 1 - Math.pow(1 - value, 2)
  const x = from.x + (to.x - from.x) * ease
  const y = from.y + (to.y - from.y) * value + Math.sin(value * Math.PI) * (plan.value.kind === 'win' ? -17 - coin.id % 3 * 3 : 14 + coin.id % 3 * 3)
  return {left:viewportOffset.value.x + x + 'px', top:viewportOffset.value.y + y + 'px', opacity:Math.min(1, value * 7, (1 - value) * 6), transform:`translate(-50%,-50%) rotate(${value * (plan.value.kind === 'win' ? 230 : -230)}deg) scale(${.8 + Math.sin(value * Math.PI) * .2})`}
}
function playSound() { try { stopSound = playNavalWagerSound(plan.value.kind, elapsed.value / 1000) } catch { } }
function finishPresentation() {
  transferred.value = plan.value?.amountCents || 0
  initialPotVisible.value = false
  complete.value = true; stopSound?.()
  if (activeRewardId != null) { emit('reward-complete', activeRewardId); activeRewardId = undefined }
  else if (!initialComplete) { initialComplete = true; emit('complete') }
  nextTick(startReward)
}
function startReward() {
  if (disposed || !mounted || !initialComplete || !complete.value || props.paused || !rewardQueue.length) return
  const reward = rewardQueue.shift(), before = Number(reward.before?.coins), after = Number(reward.after?.coins)
  const value = Math.round((after - before) * 100) / 100
  const next = value > 0 ? wagerSettlementPlan({status:'PAID', winner:0, pot:value, players:[{seat:0,stake:0,payout:value}]}, 0, after) : null
  if (!next || !Number.isFinite(before) || !Number.isFinite(after)) { emit('reward-complete', reward.id); nextTick(startReward); return }
  counter?.stop(); counter = undefined; cancelAnimationFrame(frameId)
  elapsed.value = 0; transferred.value = 0; counterDone.value = false
  rewardPlan.value = {...next, origin:reward.origin}; activeRewardId = reward.id; complete.value = false
  measure()
  const unseen = claimWagerPresentation(props.accountKey, `${props.gameKey}:reward:${reward.id}`)
  if (reduced || !unseen) { finishPresentation(); return }
  resume()
}
function tick(time) {
  if (disposed || complete.value) return
  measure()
  if (lastTime != null) elapsed.value = Math.min(plan.value.duration, elapsed.value + Math.max(0, time - lastTime))
  lastTime = time
  if (!counter && elapsed.value >= plan.value.counterStart) {
    counter = createNavalUnitCounter({fromCents:0, toCents:plan.value.amountCents,
      onChange:value => { transferred.value = value }, onComplete:() => { counterDone.value = true }})
    counter.start()
  }
  if (elapsed.value >= plan.value.duration && counterDone.value) { finishPresentation(); return }
  frameId = requestAnimationFrame(tick)
}
function resume() {
  lastTime = null
  if (started.value && !complete.value && !props.paused) { measure();playSound(); counter?.resume(); frameId = requestAnimationFrame(tick) }
}
async function schedule() {
  const request = ++generation
  clearTimeout(startTimer)
  if (!mounted || started.value || props.paused || !ready.value || props.split && currentPlan.value.kind!=='idle' && !props.potTarget) return
  await nextTick()
  if (disposed || props.paused || started.value || request !== generation) return
  measure()
  startTimer = setTimeout(async () => {
    if (disposed || started.value || props.paused || !ready.value || request !== generation) return
    initialPlan.value = currentPlan.value
    started.value = true
    if(initialPlan.value.kind==='idle'){finishPresentation();return}
    const unseen = claimWagerPresentation(props.accountKey, props.gameKey)
    if (reduced || !unseen) { finishPresentation(); return }
    if(props.split){initialPotVisible.value=true;await nextTick();if(disposed || request!==generation)return}
    resume()
  }, 350)
}
watch([ready, () => props.gameKey, () => props.potTarget], schedule)
watch(initialPotVisible,value=>emit('pot-visible',value),{flush:'sync'})
watch(pot,(value,previous)=>{if(observer){if(previous)observer.unobserve(previous);if(value)observer.observe(value)}measure()},{flush:'post'})
watch(() => props.reward, reward => {
  if (!reward?.id || seenRewards.has(reward.id)) return
  seenRewards.add(reward.id); rewardQueue.push(reward); startReward()
}, {immediate:true})
watch(() => props.paused, paused => {
  generation++; clearTimeout(startTimer); cancelAnimationFrame(frameId); counter?.pause(); stopSound?.(); lastTime = null
  if (!paused) { if (started.value) { if (complete.value) startReward(); else resume() } else schedule() }
})
onMounted(() => { mounted = true; observer = new ResizeObserver(measure); [layer.value, pot.value, wallet.value].filter(Boolean).forEach(element => observer.observe(element)); schedule() })
onUnmounted(() => { disposed = true; clearTimeout(startTimer); cancelAnimationFrame(frameId); counter?.stop(); observer?.disconnect(); stopSound?.();if(initialPotVisible.value)emit('pot-visible',false) })
</script>

<style scoped>
.wager-transfer{position:relative;isolation:isolate;display:grid;grid-template-columns:minmax(0,1fr) auto minmax(0,1fr);align-items:center;gap:18px;min-width:0;width:100%;min-height:46px;color:#748b92;font-size:11px}
.wager-transfer.split-layout{display:flex;justify-content:flex-start;gap:0}.wager-transfer.split-layout.own-right{justify-content:flex-end}.split-pot{grid-column:auto;grid-row:auto}.split-pot-enter-active,.split-pot-leave-active{transition:opacity .22s ease}.split-pot-enter-from,.split-pot-leave-to{opacity:0}
.transfer-pot{grid-column:2;grid-row:1;justify-self:center}.transfer-wallet{grid-column:3;grid-row:1;justify-self:end}.own-left .transfer-wallet{grid-column:1;justify-self:start}
.transfer-pot,.transfer-wallet{display:grid;grid-template-columns:18px auto;align-items:center;gap:1px 6px;min-width:0}.transfer-pot>svg,.transfer-wallet>svg{grid-row:1/3;color:#b88a42}.transfer-pot>span,.transfer-wallet>span{font-size:9px;line-height:13px;color:#91a2a6}.transfer-pot>strong,.transfer-wallet>output{font-variant-numeric:tabular-nums;line-height:20px;font-size:16px;font-weight:650;color:#9c783c;white-space:nowrap}
.transfer-wallet{padding:3px 0;transition:transform .12s}.transfer-wallet.wallet-pulse{transform:scale(1.035)}.wallet-change{grid-column:3;grid-row:1/3;align-self:center;margin-left:5px;font-size:12px;font-weight:600;white-space:nowrap;color:#568574;animation:wallet-delta-in .25s ease both}.loss .wallet-change{color:#b37565}@keyframes wallet-delta-in{from{opacity:0;transform:translateY(3px)}to{opacity:1;transform:translateY(0)}}
.settlement-flying-coin{position:absolute;z-index:2;display:grid;place-items:center;width:22px;height:22px;border:1px solid #dba24a;border-radius:50%;background:#ffe3a2;color:#b47a27;box-shadow:inset 0 0 0 2px #fff0be,0 2px 4px #80572c1c;pointer-events:none;will-change:transform,left,top}.loss .settlement-flying-coin{filter:saturate(.7)}
@media(max-height:600px) and (orientation:landscape){.wager-transfer{min-height:28px;gap:12px}.transfer-pot,.transfer-wallet{grid-template-columns:15px auto;gap:0 4px}.transfer-pot>svg,.transfer-wallet>svg{width:15px;height:15px}.transfer-pot>span,.transfer-wallet>span{font-size:8px;line-height:10px}.transfer-pot>strong,.transfer-wallet>output{font-size:12px;line-height:15px}.wallet-change{font-size:10px}.settlement-flying-coin{width:17px;height:17px}.settlement-flying-coin>svg{width:11px;height:11px}}
@media(max-width:500px){.wager-transfer{gap:10px}.transfer-pot>strong,.transfer-wallet>output{font-size:14px}.wallet-change{font-size:11px}}
@media(max-height:350px) and (orientation:landscape){.wager-transfer{min-height:20px;gap:8px}.transfer-pot,.transfer-wallet{display:flex;gap:4px;padding:0}.transfer-pot>svg,.transfer-wallet>svg{width:13px;height:13px}.transfer-pot>strong,.transfer-wallet>output{font-size:11px;line-height:16px}.wallet-change{font-size:10px}.transfer-pot>span,.transfer-wallet>span{font-size:8px;white-space:nowrap}}
@media(prefers-reduced-motion:reduce){.transfer-wallet{transition:none}.wallet-change{animation:none}.settlement-flying-coin{display:none}}
@media(prefers-reduced-motion:reduce){.split-pot-enter-active,.split-pot-leave-active{transition:none}}
</style>
