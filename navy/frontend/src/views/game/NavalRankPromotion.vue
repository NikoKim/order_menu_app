<template>
  <section ref="dialog" class="rank-promotion" :class="{ ceremonial: promotion.kind === 'exam', reduced }" :data-phase="phase" :data-kind="promotion.kind" role="dialog" aria-modal="true" aria-label="军衔晋升" tabindex="-1" @keydown="onKey">
    <div class="promotion-scene">
      <div class="promotion-heading"><span /><Anchor v-if="promotion.kind === 'exam'" :size="24" /><ChevronsUp v-else :size="23" /><span /></div>
      <p class="promotion-kicker">{{ promotion.kind === 'exam' ? '晋升授衔' : '军衔晋升' }}</p>
      <div class="promotion-emblem" aria-hidden="true">
        <div v-if="promotion.kind === 'exam'" class="ceremony-frame"><i /><i /><i /><i /></div>
        <div v-if="promotion.kind === 'exam'" class="commission-wings"><i v-for="index in 6" :key="index" /></div>
        <NavalRankBadge class="previous-badge" :level="promotion.from.level" :size="180" />
        <NavalRankBadge class="awarded-badge" :level="promotion.to.level" :size="180" />
        <div class="insignia-flash"><i /><i /><i /><i /></div>
      </div>
      <div class="promotion-caption" aria-live="polite">
        <p class="previous-rank">{{ promotion.from.rank }} <small>Lv.{{ promotion.from.level }}</small></p>
        <div class="awarded-rank"><h2>{{ promotion.to.rank }}</h2><p>Lv.{{ promotion.to.level }}<span v-if="promotion.kind === 'exam'"><ShieldCheck :size="14" />考核通过</span></p></div>
      </div>
      <div class="promotion-route"><span>{{ promotion.from.rank }}</span><ArrowRight :size="15" /><strong>{{ promotion.to.rank }}</strong></div>
      <button class="promotion-continue" :class="{ ready: phase === 'awarded' }" :disabled="phase !== 'awarded'" @click="complete"><span>继续</span><ArrowRight :size="16" /></button>
    </div>
  </section>
</template>

<script setup>
import { nextTick, onMounted, onUnmounted, ref } from 'vue'
import { Anchor, ArrowRight, ChevronsUp, ShieldCheck } from 'lucide-vue-next'
import NavalRankBadge from './NavalRankBadge.vue'
import { playNavalRankSound } from '../../games/navalAudio'

const props = defineProps({promotion:{type:Object, required:true}})
const emit = defineEmits(['complete'])
const dialog = ref(null), phase = ref('arrival')
const reduced = Boolean(globalThis.matchMedia?.('(prefers-reduced-motion: reduce)').matches)
const timers = []
let stopSound, previousFocus, finished = false, disposed = false
function complete() { if (!finished) { finished = true; emit('complete') } }
function onKey(event) {
  if (event.key === 'Escape') { event.preventDefault(); complete() }
  if (event.key === 'Tab') { event.preventDefault(); dialog.value?.querySelector('button:not(:disabled)')?.focus() }
}
onMounted(async () => {
  previousFocus = document.activeElement
  await nextTick()
  if (disposed) return
  dialog.value?.focus({preventScroll:true})
  const exam = props.promotion.kind === 'exam'
  timers.push(setTimeout(() => { phase.value = 'exchange' }, reduced ? 200 : exam ? 2200 : 1250))
  timers.push(setTimeout(() => { phase.value = 'awarded' }, reduced ? 650 : exam ? 3900 : 2200))
  timers.push(setTimeout(complete, reduced ? 2000 : exam ? 8400 : 5000))
  try { stopSound = playNavalRankSound(props.promotion.kind) } catch { /* Audio device failures must not block the ceremony. */ }
})
onUnmounted(() => {
  disposed = true
  timers.forEach(clearTimeout); stopSound?.()
  if (previousFocus?.isConnected) previousFocus.focus?.({preventScroll:true})
})
</script>

<style scoped>
.rank-promotion{position:fixed;z-index:1600;inset:0;display:grid;place-items:center;overflow:hidden;padding:20px;background:#071114c9;backdrop-filter:blur(2px);color:#f7f4e9;outline:0;animation:promotion-veil .35s ease both;--badge-size:180px}
.promotion-scene{display:flex;flex-direction:column;align-items:center;justify-content:center;width:min(520px,100%);min-width:0;text-align:center}
.promotion-heading{display:flex;align-items:center;justify-content:center;gap:16px;color:#e3c489;width:220px;max-width:80%}.promotion-heading>span{height:1px;flex:1;background:#c8b78569}.promotion-kicker{font-size:14px;color:#e1d1aa;margin-top:12px;line-height:24px}
.promotion-emblem{position:relative;display:grid;place-items:center;width:230px;height:220px;max-width:100%;margin:8px 0;isolation:isolate}
.promotion-emblem:deep(.naval-rank-badge){position:absolute;width:var(--badge-size)!important;height:var(--badge-size)!important;filter:drop-shadow(0 7px 16px #0007)}
.previous-badge{opacity:1;transform:translateY(0) scale(1);transition:opacity .6s ease,transform .8s cubic-bezier(.3,.1,.2,1)}
.awarded-badge{opacity:0;transform:translateY(18px) scale(.62);transition:opacity .55s ease,transform 1.2s cubic-bezier(.18,1.35,.3,1)}
[data-phase=exchange] .previous-badge,[data-phase=awarded] .previous-badge{opacity:0;transform:translateY(-24px) scale(.72) rotate(-6deg)}
[data-phase=exchange] .awarded-badge,[data-phase=awarded] .awarded-badge{opacity:1;transform:translateY(0) scale(1)}
.promotion-caption{position:relative;min-height:76px;width:100%;display:grid;place-items:center}.previous-rank{font-size:25px;font-weight:600;transition:opacity .35s,transform .6s}.previous-rank small{font-size:12px;font-weight:400;color:#b7c1c0;margin-left:6px}.awarded-rank{position:absolute;inset:0;display:flex;flex-direction:column;align-items:center;justify-content:center;gap:4px;opacity:0;transform:translateY(10px);transition:opacity .55s,transform .7s}.awarded-rank h2{font-size:32px;line-height:42px;font-weight:650;color:#f2db9c;overflow-wrap:anywhere}.awarded-rank p{display:flex;align-items:center;gap:12px;justify-content:center;font-size:12px;color:#cfcebf}.awarded-rank p>span{display:inline-flex;align-items:center;gap:5px;color:#daca9d}
[data-phase=exchange] .previous-rank,[data-phase=awarded] .previous-rank{opacity:0;transform:translateY(-10px)}[data-phase=exchange] .awarded-rank,[data-phase=awarded] .awarded-rank{opacity:1;transform:translateY(0)}
.promotion-route{display:flex;align-items:center;justify-content:center;gap:14px;min-height:28px;margin-top:4px;font-size:11px;color:#9ca9a6}.promotion-route strong{color:#d7c594;font-weight:500}
.promotion-continue{display:inline-flex;align-items:center;justify-content:center;gap:15px;min-width:90px;min-height:36px;margin-top:18px;background:transparent;color:#f1deb3;font-size:12px;opacity:0;transition:opacity .4s}.promotion-continue.ready{opacity:1}.promotion-continue:disabled{pointer-events:none}.promotion-continue:hover{color:#fff}
.ceremony-frame{position:absolute;inset:5px 4px;border:1px solid #c4a66b42;opacity:0;transform:scale(1.09);animation:ceremony-arrive 1s .35s ease forwards}.ceremony-frame i{position:absolute;width:24px;height:24px;border-color:#dbc28c;border-style:solid;border-width:0}.ceremony-frame i:nth-child(1){left:-1px;top:-1px;border-left-width:2px;border-top-width:2px}.ceremony-frame i:nth-child(2){right:-1px;top:-1px;border-right-width:2px;border-top-width:2px}.ceremony-frame i:nth-child(3){left:-1px;bottom:-1px;border-left-width:2px;border-bottom-width:2px}.ceremony-frame i:nth-child(4){right:-1px;bottom:-1px;border-right-width:2px;border-bottom-width:2px}
.ceremonial .promotion-heading{width:280px}.ceremonial .promotion-emblem{height:244px;width:254px;margin-top:16px;margin-bottom:16px;--badge-size:188px}.ceremonial .previous-badge{transition-duration:1s,1.3s}.ceremonial .awarded-badge{transition-duration:.85s,1.65s}.ceremonial[data-phase=awarded] .ceremony-frame{border-color:#c7ac738a}
.commission-wings{position:absolute;inset:0 -58px;pointer-events:none}.commission-wings i{position:absolute;left:0;top:calc(50% - 14px);width:29px;height:2px;background:#d6bb7b;opacity:0;transform:scaleX(0);transform-origin:right;transition:transform .7s,opacity .7s}.commission-wings i:nth-child(2){top:50%;width:42px;transition-delay:.14s}.commission-wings i:nth-child(3){top:calc(50% + 14px);transition-delay:.28s}.commission-wings i:nth-child(n+4){left:auto;right:0;transform-origin:left}.commission-wings i:nth-child(5){top:50%;width:42px;transition-delay:.14s}.commission-wings i:nth-child(6){top:calc(50% + 14px);transition-delay:.28s}[data-phase=exchange] .commission-wings i,[data-phase=awarded] .commission-wings i{opacity:.7;transform:scaleX(1)}.ceremonial[data-phase=exchange] .awarded-badge{animation:commission-unveil 1.65s cubic-bezier(.2,.8,.3,1) both}
.insignia-flash{position:absolute;inset:10px;pointer-events:none}.insignia-flash i{position:absolute;left:50%;top:50%;height:1px;width:55px;background:#dfcc91;opacity:0;transform-origin:left center}.insignia-flash i:nth-child(1){--turn:-35deg}.insignia-flash i:nth-child(2){--turn:35deg}.insignia-flash i:nth-child(3){--turn:145deg}.insignia-flash i:nth-child(4){--turn:215deg}[data-phase=exchange] .insignia-flash i{animation:insignia-streak .8s .1s ease-out both}.ceremonial[data-phase=exchange] .insignia-flash i{animation-duration:1.3s;animation-delay:.3s}
@keyframes promotion-veil{from{opacity:0}to{opacity:1}}@keyframes ceremony-arrive{to{opacity:1;transform:scale(1)}}@keyframes insignia-streak{0%{opacity:0;transform:rotate(var(--turn)) translateX(40px) scaleX(.1)}30%{opacity:.8}100%{opacity:0;transform:rotate(var(--turn)) translateX(135px) scaleX(.1)}}
@keyframes commission-unveil{0%{opacity:0;transform:perspective(700px) rotateY(-30deg) translateY(16px) scale(.6)}65%{opacity:1;transform:perspective(700px) rotateY(7deg) translateY(0) scale(1.06)}100%{opacity:1;transform:perspective(700px) rotateY(0) translateY(0) scale(1)}}
@media(max-height:600px){.rank-promotion{padding:10px;--badge-size:128px}.promotion-heading{width:180px}.promotion-heading>svg{width:19px;height:19px}.promotion-kicker{font-size:12px;line-height:18px;margin-top:7px}.promotion-emblem{width:172px;height:148px;margin:4px 0}.promotion-caption{min-height:55px}.previous-rank{font-size:21px}.awarded-rank h2{font-size:26px;line-height:32px}.awarded-rank p{font-size:10px}.promotion-route{min-height:20px;margin:0;font-size:10px}.promotion-continue{margin-top:5px;min-height:30px}.ceremonial .promotion-heading{width:210px}.ceremonial .promotion-emblem{width:176px;height:160px;--badge-size:128px;margin:7px 0}.ceremony-frame{inset:2px}.ceremony-frame i{width:18px;height:18px}}
@media(max-height:340px){.rank-promotion{--badge-size:96px}.promotion-emblem{height:108px}.ceremonial .promotion-emblem{height:116px;width:140px;--badge-size:98px}.promotion-heading{display:none}.promotion-caption{min-height:49px}.promotion-kicker{margin:0}.promotion-continue{margin:0}}
.reduced,.reduced *{animation:none!important;transition-duration:.12s!important}
</style>
