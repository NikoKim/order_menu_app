<template>
  <section ref="root" class="naval-replay" aria-label="对局回放">
    <header class="replay-toolbar"><div><Film :size="18" /><h2>{{ detail.ruleSet === 'MILITARY' ? '军事模式' : '经典模式' }}回放</h2><span>{{ detail.mode === 'AI' ? '人机对战' : '人人对战' }}</span></div><nav><button aria-label="聊天大厅" :title="lobbyUnread ? '聊天大厅有新消息' : '聊天大厅'" :aria-description="lobbyUnread ? '有未读消息' : undefined" @click="$emit('chat')"><MessagesSquare :size="20" /><i v-if="lobbyUnread" class="lobby-unread-dot" aria-hidden="true" /></button><button aria-label="退出回放" title="退出回放" @click="$emit('close')"><X :size="20" /></button></nav></header>
    <p v-if="error" class="replay-error" role="alert">{{ error }}</p>
    <div class="replay-caption" role="status"><Transition name="replay-caption" mode="out-in"><span v-if="captionVisible" :key="`${cursor}-${ready}`">{{ !ready ? '正在载入回放素材...' : currentEvent ? `${detail.players[currentEvent.player]?.name} · ${currentEvent.weapon==='depthCharge'?'深水炸弹':ITEMS[currentEvent.weapon]?.name || '舰炮'} · ${currentEvent.text}` : '布阵完成' }}</span></Transition></div>
    <div class="replay-boards" :style="{ maxWidth: `${boardsWidth}px` }"><section v-for="(person, index) in snapshot.players" :key="index"><header><NavalAvatar :person="person" :size="18" /><strong>{{ person.name }}</strong><span>{{ person.ships.filter(ship => ship.hits.length < ship.w * ship.h).length }} / {{ detail.ruleSet==='MILITARY' ? person.ships.length : 10 }}</span></header><NavalBoard :military="detail.ruleSet==='MILITARY'" :military-event="currentEvent" :military-attacking="currentEvent?.player===index" :radar="snapshot.players[1-index]?.radar || []" :key="`${resetKey}-${index}`" :player="person" :label="`${person.name}回放海域`" :events="boardEvents(index)" :defense-side="index === 0 ? 'left' : 'right'" @animation="animations[index] = $event" @ready="loaded[index] = true" @error="onError" /></section></div>
    <footer class="replay-controls"><button aria-label="从头回放" title="从头回放" :disabled="animating" @click="restart"><RotateCcw :size="19" /></button><button class="play-control" :aria-label="playing ? '暂停回放' : '自动播放'" :title="playing ? '暂停回放' : '自动播放'" :disabled="!ready || !attacks.length" @click="togglePlay"><Pause v-if="playing" :size="20" /><Play v-else :size="20" /></button><button aria-label="下一次攻击" title="下一次攻击" :disabled="!ready || animating || stepping || cursor >= attacks.length" @click="next"><StepForward :size="21" /></button><progress :value="cursor" :max="Math.max(1, attacks.length)" aria-label="回放进度" /><span>{{ cursor }} / {{ attacks.length }}</span></footer>
  </section>
</template>
<script setup>
import { computed, nextTick, onMounted, onUnmounted, reactive, ref, watch } from 'vue'
import { Film, MessagesSquare, Pause, Play, RotateCcw, StepForward, X } from 'lucide-vue-next'
import NavalAvatar from './NavalAvatar.vue'
import NavalBoard from './NavalBoard.vue'
import { ITEMS } from '../../games/navalEngine'
import { replayAttacks, replaySnapshot } from '../../games/navalReplay'
import { navalBoardEvents } from '../../games/navalPresentation'
const props = defineProps({ detail: { type: Object, required: true }, lobbyUnread: Boolean, blocked: Boolean })
defineEmits(['close', 'chat'])
const cursor = ref(0), playing = ref(false), stepping = ref(false), resetKey = ref(0), error = ref('')
const root = ref(null), boardsWidth = ref(1200), captionVisible = ref(true)
const animations = reactive([false, false]), loaded = reactive([false, false])
const attacks = computed(() => replayAttacks(props.detail)), snapshot = computed(() => replaySnapshot(props.detail, cursor.value))
const currentEvent = computed(() => attacks.value[cursor.value - 1]), animating = computed(() => animations.some(Boolean)), ready = computed(() => loaded.every(Boolean) && !error.value && !props.blocked)
const boardEvents = index => navalBoardEvents(attacks.value.slice(0, cursor.value),index)
let playTimer, stepTimer, captionTimer, observer
function resizeBoards() {
  const element = root.value
  if (!element) return
  const height = selector => element.querySelector(selector)?.getBoundingClientRect().height || 0
  const gap = parseFloat(getComputedStyle(element).gap) || 0
  const boardGap = parseFloat(getComputedStyle(element.querySelector('.replay-boards')).columnGap) || 0
  const available = element.clientHeight - height('.replay-toolbar') - height('.replay-caption') - height('.replay-controls') - height('.replay-boards header') - gap * 3
  boardsWidth.value = Math.max(0, available) * 2 + boardGap
}
onMounted(() => { observer = new ResizeObserver(resizeBoards); observer.observe(root.value); resizeBoards() })
watch([cursor, ready], () => { clearTimeout(captionTimer); captionVisible.value = true; if (ready.value) captionTimer = setTimeout(() => { captionVisible.value = false }, 4000) })
function onError(message) { error.value = message; playing.value = false }
async function next() { if (!ready.value || animating.value || stepping.value || cursor.value >= attacks.value.length) return; stepping.value = true; cursor.value++; await nextTick(); clearTimeout(stepTimer); stepTimer = setTimeout(() => { stepping.value = false }, 650) }
function restart() { clearTimeout(playTimer); clearTimeout(stepTimer); playing.value = false; stepping.value = false; cursor.value = 0; loaded.fill(false); animations.fill(false); resetKey.value++ }
function togglePlay() { if (cursor.value >= attacks.value.length) { restart(); playing.value = true } else playing.value = !playing.value }
watch([playing, ready, animating, stepping, cursor], () => { clearTimeout(playTimer); if (cursor.value >= attacks.value.length && !animating.value && !stepping.value) { playing.value = false; return } if (playing.value && ready.value && !animating.value && !stepping.value) playTimer = setTimeout(next, 850) }, { flush: 'post' })
onUnmounted(() => { clearTimeout(playTimer); clearTimeout(stepTimer); clearTimeout(captionTimer); observer?.disconnect() })
</script>
<style scoped>
.naval-replay{display:flex;flex-direction:column;min-width:0;min-height:0;height:calc(100dvh - 100px);gap:10px;color:#426775}.replay-toolbar{display:flex;align-items:center;justify-content:space-between;gap:10px;flex-shrink:0;min-height:38px}.replay-toolbar>div,.replay-toolbar nav{display:flex;align-items:center;gap:10px}.replay-toolbar h2{font-size:16px}.replay-toolbar span{font-size:11px;color:#90a19f}.replay-toolbar button,.replay-controls button{display:grid;place-items:center;width:34px;height:34px;flex-shrink:0;background:transparent;color:#63847a;border-radius:4px}.replay-toolbar button:hover,.replay-controls button:hover{background:#e4efe8}.replay-boards{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:16px;width:min(100%,calc((100dvh - 245px)*2 + 16px));margin:auto;min-height:0}.replay-boards>section{min-width:0}.replay-boards header{display:flex;align-items:center;gap:7px;font-size:12px;height:32px;min-width:0}.replay-boards header strong{overflow:hidden;text-overflow:ellipsis;white-space:nowrap}.replay-boards header>span{margin-left:auto;font-size:11px;white-space:nowrap}.replay-caption{font-size:11px;line-height:1.5;min-height:32px;max-height:32px;overflow:hidden;flex-shrink:0}.replay-controls{display:flex;align-items:center;justify-content:center;gap:8px;min-height:40px;flex-shrink:0}.replay-controls button:disabled{opacity:.3}.replay-controls .play-control{background:#dfede5;color:#39735a}.replay-controls progress{width:min(300px,40%);height:4px;accent-color:#5c9374}.replay-controls>span{min-width:56px;font-size:12px;text-align:right;font-variant-numeric:tabular-nums}.replay-error{font-size:12px;color:#ae5b47}@media(max-width:700px){.naval-replay{height:calc(100dvh - 78px);gap:6px}.replay-toolbar h2{font-size:14px}.replay-toolbar span{display:none}.replay-boards{gap:8px;width:min(100%,calc((100dvh - 210px)*2 + 8px))}.replay-boards header{font-size:10px;gap:4px}.replay-boards header :deep(.naval-avatar){width:22px;height:22px}.replay-controls{gap:5px}}@media(max-height:500px){.naval-replay{height:calc(100dvh - 70px);gap:4px}.replay-boards{width:min(100%,calc((100dvh - 182px)*2 + 12px))}.replay-caption{min-height:16px;max-height:16px}.replay-toolbar{min-height:30px}.replay-controls{min-height:32px}}
</style>
<style scoped>
.naval-replay{height:100%;gap:6px;overflow:hidden}
.replay-toolbar button{position:relative}.lobby-unread-dot{position:absolute;top:3px;right:3px;width:7px;height:7px;border-radius:50%;background:#df7159;box-shadow:0 0 0 2px #f6f9f8;pointer-events:none}
.replay-boards{width:100%;margin:0 auto;flex-shrink:0}
.replay-caption{position:relative;z-index:1;min-height:20px;max-height:20px;white-space:nowrap;text-overflow:ellipsis}
.replay-caption>span{display:block;overflow:hidden;text-overflow:ellipsis}
.replay-caption-enter-active,.replay-caption-leave-active{transition:opacity .3s ease}
.replay-caption-enter-from,.replay-caption-leave-to{opacity:0}
@media(max-height:500px){.naval-replay{gap:3px}.replay-boards{gap:8px}.replay-toolbar{min-height:26px;height:26px}.replay-toolbar button{width:26px;height:26px}.replay-boards header{height:24px;font-size:10px}.replay-caption{min-height:17px;max-height:17px;font-size:10px}.replay-controls{min-height:28px;height:28px}.replay-controls button{width:28px;height:28px}}
@media(prefers-reduced-motion:reduce){.replay-caption-enter-active,.replay-caption-leave-active{transition:none}}
</style>
