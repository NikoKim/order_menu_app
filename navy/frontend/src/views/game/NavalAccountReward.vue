<template>
  <section ref="layer" class="account-reward-presentation" :class="{ floating:!embedded, 'only-wallet':!receipt.score && !settled, invisible:externalWallet && !receipt.score && !settled }" :data-reward-id="receipt.id" :data-state="settled ? 'settled' : finished ? 'complete' : paused ? 'paused' : 'playing'" aria-label="奖励到账">
    <div v-if="receipt.score > 0 || settled" class="experience-gain"><NavalRankBadge :level="displayRank.level" :size="36" /><div class="experience-track"><strong>{{ displayRank.rank }}</strong><div class="experience-meter" :class="{ gaining:!settled && !xpComplete && !paused }" role="progressbar" aria-label="等级分进度" :aria-valuenow="xpValue" :aria-valuemin="range.min" :aria-valuemax="range.max"><span :style="{width:progress + '%'}" /></div></div><span v-if="!settled" class="experience-add" :class="{ visible:xpComplete }"><TrendingUp :size="12" />+{{ receipt.score }}</span></div>
    <div v-if="!externalWallet" ref="wallet" class="reward-wallet-presentation"><Wallet :size="16" /><span>账户金币</span><output>{{ money(displayedCoins) }}</output><strong class="wallet-add" :class="{ visible:finished && receipt.coins > 0 }">+{{ receipt.coins }}</strong></div>
    <Teleport :to="flightTarget"><div class="naval-coin-flight-layer" aria-hidden="true"><span v-for="index in flyingCoins" :key="index" class="reward-flying-coin" :style="coinStyle(index)"><Coins :size="12" /></span></div></Teleport>
  </section>
</template>
<script setup>
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import { Coins, TrendingUp, Wallet } from 'lucide-vue-next'
import NavalRankBadge from './NavalRankBadge.vue'
import { createNavalUnitCounter, unitCounterPlan } from '../../games/navalUnitCounter'
import { playNavalExperienceSound, playNavalWagerSound } from '../../games/navalAudio'
import { useFullscreenTarget } from '../../composables/useFullscreenTarget'

const props = defineProps({receipt:{type:Object,required:true}, embedded:Boolean, externalWallet:Boolean, settled:Boolean, balance:{default:null}, paused:Boolean})
const emit = defineEmits(['complete'])
const flightTarget = useFullscreenTarget()
const layer = ref(null), wallet = ref(null), elapsed = ref(0), displayedCoins = ref(0), xpComplete = ref(false), coinsComplete = ref(false), finished = ref(false)
const points = ref({from:{x:0,y:0},to:{x:0,y:0}})
const limits = [300,600,1000,1500,2000,2800,3800,4800,6000,7500,9000,12000]
const displayRank = computed(() => props.receipt.display || props.receipt.after)
const range = computed(() => ({min:displayRank.value.level === 1 ? 0 : limits[displayRank.value.level - 2] + 1, max:limits[displayRank.value.level - 1]}))
const xpTarget = computed(() => Math.max(range.value.min, Math.min(range.value.max, props.receipt.after.score)))
const xpStart = computed(() => Math.max(range.value.min, Math.min(props.receipt.before.score, xpTarget.value)))
const xpValue = computed(() => Math.round(xpStart.value + (xpTarget.value - xpStart.value) * Math.min(1, elapsed.value / 1400)))
const progress = computed(() => Math.max(0, Math.min(100, (xpValue.value - range.value.min) / Math.max(1, range.value.max - range.value.min) * 100)))
const flyingCoins = computed(() => !props.externalWallet && props.receipt.coins > 0 && !finished.value ? [1,2,3,4].filter(index => elapsed.value >= index * 100 && elapsed.value < index * 100 + 600) : [])
const money = cents => (Math.round(cents || 0) / 100).toLocaleString('zh-CN',{maximumFractionDigits:2})
let counter, frameId, lastTime, stopXp, stopCoins, holdTimer, holdStarted, observer, holdRemaining = 900, completionSent = false, disposed = false, revision = 0, counterStarted = false
const reduced = Boolean(globalThis.matchMedia?.('(prefers-reduced-motion: reduce)').matches)
function measure() {
  if (!layer.value || !wallet.value) return
  const box = layer.value.getBoundingClientRect(), target = (wallet.value.querySelector('output') || wallet.value).getBoundingClientRect()
  const to = {x:target.left + target.width / 2 - box.left,y:target.top + target.height / 2 - box.top}
  const origin = props.receipt.origin
  points.value = {to,from:Number.isFinite(origin?.x) && Number.isFinite(origin?.y) ? {x:origin.x-box.left,y:origin.y-box.top} : {x:to.x-90,y:to.y}}
}
function coinStyle(index) {
  const step = Math.max(0,Math.min(1,(elapsed.value-index*100)/600))
  const ease = 1 - (1-step) ** 2, {from,to} = points.value
  const box = layer.value?.getBoundingClientRect() || {left:0,top:0}
  return {left:`${box.left+from.x+(to.x-from.x)*ease}px`,top:`${box.top+from.y+(to.y-from.y)*ease-Math.sin(step*Math.PI)*28}px`,opacity:Math.min(1,step*6,(1-step)*6),transform:`translate(-50%,-50%) rotate(${step*200}deg)`}
}
function clear() { cancelAnimationFrame(frameId); clearTimeout(holdTimer); holdTimer = null; holdStarted = null; counter?.stop(); stopXp?.(); stopCoins?.(); lastTime = null }
function complete(version = revision, id = props.receipt.id) {
  if (disposed || version !== revision || completionSent) return
  completionSent = true
  emit('complete', id)
}
function holdCompletion() {
  if (props.paused || holdTimer != null || completionSent) return
  const version = revision, id = props.receipt.id
  holdStarted = performance.now()
  holdTimer = setTimeout(() => { holdTimer = null; holdStarted = null; holdRemaining = 0; complete(version, id) }, holdRemaining)
}
function finish() {
  finished.value = true; stopXp?.(); stopCoins?.()
  if (props.embedded) complete()
  else holdCompletion()
}
function sounds() {
  try { if (props.receipt.score) stopXp = playNavalExperienceSound(elapsed.value/1000) } catch { }
  try { if (props.receipt.coins && !props.externalWallet) stopCoins = playNavalWagerSound('win',elapsed.value/1000) } catch { }
}
function tick(time) {
  if (disposed || props.paused || finished.value) return
  if (lastTime != null) elapsed.value += Math.max(0,time-lastTime)
  lastTime = time
  if (!counterStarted && props.receipt.coins && !props.externalWallet && elapsed.value >= 350) { counterStarted = true; counter.start() }
  xpComplete.value = !props.receipt.score || elapsed.value >= 1400
  const coinDuration = props.receipt.coins ? unitCounterPlan(Math.round(props.receipt.before.coins*100),Math.round(props.receipt.after.coins*100)).duration : 0
  if (xpComplete.value && coinsComplete.value && elapsed.value >= Math.max(1800,coinDuration+400)) {
    finish(); return
  }
  frameId = requestAnimationFrame(tick)
}
function resume() { if (!props.paused && !props.settled) { if (finished.value) { holdCompletion(); return } lastTime = null; if(counterStarted)counter?.resume(); sounds(); frameId = requestAnimationFrame(tick) } }
async function start() {
  const version = ++revision
  clear(); holdRemaining=900;completionSent=false;counterStarted=false;elapsed.value=0;finished.value=false;xpComplete.value=!props.receipt.score;coinsComplete.value=!props.receipt.coins || props.externalWallet
  displayedCoins.value = Math.round((props.receipt.coins ? props.receipt.before.coins : props.balance ?? props.receipt.after.coins)*100)
  if(props.settled){elapsed.value=1400;xpComplete.value=true;coinsComplete.value=true;finished.value=true;return}
  counter = createNavalUnitCounter({fromCents:displayedCoins.value,toCents:props.receipt.coins ? Math.round(props.receipt.after.coins*100) : displayedCoins.value,onChange:value=>{displayedCoins.value=value},onComplete:()=>{coinsComplete.value=true}})
  await nextTick(); if (disposed || version !== revision) return
  measure()
  if (reduced) { counter.finish();elapsed.value=1400;xpComplete.value=true;coinsComplete.value=true;finish();return }
  if (props.externalWallet || !props.receipt.coins) counter.stop()
  resume()
}
watch(() => props.receipt.id,start)
watch(() => props.paused,value=>{cancelAnimationFrame(frameId);counter?.pause();stopXp?.();stopCoins?.();lastTime=null;if(value && holdTimer != null){clearTimeout(holdTimer);holdTimer=null;holdRemaining=Math.max(0,holdRemaining-(performance.now()-holdStarted));holdStarted=null}if(!value)resume()})
onMounted(()=>{observer=new ResizeObserver(measure);observer.observe(layer.value);if(wallet.value)observer.observe(wallet.value);start()})
onUnmounted(()=>{disposed=true;revision++;observer?.disconnect();clear()})
</script>
<style scoped>
.account-reward-presentation{position:relative;display:flex;align-items:center;justify-content:space-between;gap:16px;min-width:0;min-height:48px;width:100%;color:#66838c}.account-reward-presentation.floating{position:fixed;z-index:1000;top:calc(var(--navy-visual-top,0px) + 60px);left:50%;transform:translateX(-50%);width:min(480px,calc(var(--navy-visual-width,100vw) - 24px));padding:11px 15px;background:#f9fcfcf7;border:1px solid #dce6e8;border-radius:6px;box-shadow:0 5px 24px #294a541c;pointer-events:none}.account-reward-presentation.invisible{display:none}
.experience-gain{display:flex;align-items:center;gap:8px;min-width:0;flex:1}.experience-track{min-width:0;flex:1;max-width:270px}.experience-track strong{display:block;font-size:12px;font-weight:600;color:#9e793f;margin-bottom:5px}.experience-meter{width:100%;height:5px;border-radius:3px;background:#e8e5db;overflow:hidden}.experience-meter>span{display:block;height:100%;background:#d7a24f;box-shadow:0 0 7px #e0bc73;min-width:1px}.experience-add{display:inline-flex;align-items:center;gap:3px;flex-shrink:0;font-size:11px;color:#ba873e;opacity:0}.experience-add.visible{opacity:1}.reward-wallet-presentation{display:flex;align-items:center;gap:5px;flex-shrink:0;min-width:0;color:#a67e3d}.reward-wallet-presentation>span{font-size:9px;color:#92a4a8}.reward-wallet-presentation>output{font-size:14px;font-weight:650;font-variant-numeric:tabular-nums;white-space:nowrap}.wallet-add{min-width:28px;font-size:11px;font-weight:600;color:#608f78;opacity:0}.wallet-add.visible{opacity:1}.reward-flying-coin{position:absolute;display:grid;place-items:center;width:18px;height:18px;border:1px solid #d2a353;border-radius:50%;background:#ffe4aa;color:#a7782b;pointer-events:none}.only-wallet{justify-content:flex-end}
@media(max-height:600px) and (orientation:landscape){.account-reward-presentation{min-height:32px;gap:10px}.experience-gain{gap:5px}.experience-gain:deep(.naval-rank-badge){width:27px!important;height:27px!important}.experience-track strong{font-size:10px;margin-bottom:3px}.experience-meter{height:4px}.experience-add{font-size:9px}.reward-wallet-presentation{gap:3px}.reward-wallet-presentation>span{font-size:8px}.reward-wallet-presentation>output{font-size:12px}.wallet-add{font-size:10px;min-width:24px}.account-reward-presentation.floating{top:calc(var(--navy-visual-top,0px) + 44px);min-height:50px}}
@media(max-width:450px){.account-reward-presentation{gap:8px}.reward-wallet-presentation>span{display:none}.experience-track{max-width:130px}.experience-add{font-size:9px}}
.experience-meter{position:relative}.experience-meter.gaining:after{content:'';position:absolute;top:0;bottom:0;left:-22px;width:22px;background:#fff3c8b3;box-shadow:0 0 8px #f6d487;animation:experience-flow .7s ease-in-out 2}@keyframes experience-flow{to{left:100%}}
@media(prefers-reduced-motion:reduce){.reward-flying-coin,.experience-meter:after{display:none}}
</style>
