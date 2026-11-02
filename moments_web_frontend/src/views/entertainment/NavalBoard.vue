<template>
  <div class="naval-board" :class="{ enabled: interactive && loaded, 'is-loading': !loaded, mirrored: defenseSide === 'left' }">
    <div ref="host" class="phaser-host" @keydown="keyboard" />
    <div v-if="!loaded && !failure" class="board-loading" role="status"><span class="loading-radar" /><progress :value="progress" max="1" aria-label="海域素材载入进度" /><span>{{ Math.round(progress * 100) }}%</span></div>
    <div v-if="failure" class="board-failure" role="alert"><span>{{ failure }}</span><button type="button" @click="start">重新加载</button></div>
    <span v-if="loaded" class="board-coordinate">{{ coordinate }}</span>
    <slot v-if="loaded" />
  </div>
</template>

<script setup>
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { loadMilitaryArt } from '../../games/navalMilitaryArt'
const props = defineProps({ military: Boolean, militarySelected: Object, militaryAction: Object, militarySource: Object, militaryPreparing: Boolean, militaryEvent: Object, militaryAttacking: Boolean, player: Object, hidden: Boolean, interactive: Boolean, label: String, preview: Object, region: Object, event: Object, events: Array, selected: String, radar: Array, defenseSide: { type: String, default: 'left' } })
const militaryPlayer = computed(() => props.hidden ? { ...props.player, ships: (props.player?.ships || []).filter(s => s.hits?.length >= s.w * s.h) } : props.player)
const emit = defineEmits(['cell', 'hover', 'loading', 'ready', 'error', 'animation'])
const host = ref(null), hover = ref(null), progress = ref(0), loaded = ref(false), failure = ref('')
const coordinate = computed(() => hover.value ? `${String.fromCharCode(65 + hover.value.x)}${hover.value.y + 1}` : '--')
let renderer, disposed = false
function snapshot() { const outgoing=props.military&&props.militaryAttacking&&props.militaryEvent&&['fighter','bomber','torpedo','mine'].includes(props.militaryEvent.weapon)?[{...props.militaryEvent,id:`${props.militaryEvent.id}-sortie`,weapon:props.militaryEvent.weapon==='mine'?'mine':'military-sortie',aircraft:props.militaryEvent.weapon,cells:[],backfire:[]}]:[];return { military:props.military, militaryPreparing:props.militaryPreparing, player: props.player, hidden: props.hidden, interactive: props.interactive, label: props.label, preview: props.preview, region: props.region, event: props.event, events: [...(props.events||(props.event?[props.event]:[])).filter(e=>!props.military||e.weapon!=='mine'),...outgoing], selected: props.selected, radar: props.radar, defenseSide: props.defenseSide } }
function setLabel() { host.value?.querySelector('canvas')?.setAttribute('aria-label', `${props.label || '海域'}，12行12列，当前${coordinate.value}`) }
async function start() {
  renderer?.destroy(); renderer = null
  progress.value = 0; loaded.value = false; failure.value = ''
  try {
    if(props.military) await loadMilitaryArt()
    const { mountNavalGame } = await import('../../games/navalScene')
    if (disposed || !host.value) return
    renderer = mountNavalGame(host.value, snapshot(), {
      loading(value) { if (disposed) return; progress.value = value; emit('loading', value) },
      ready() { if (disposed) return; loaded.value = true; progress.value = 1; setLabel(); emit('ready') },
      error(message) { if (disposed) return; failure.value = message; emit('error', message) },
      animation(value) { if (!disposed) emit('animation', value) },
      hover(point) { hover.value = point; setLabel(); emit('hover', point) },
      cell(point) { if (props.interactive && loaded.value) emit('cell', point) },
      dragStart(point) { if (props.interactive && loaded.value) emit('cell', { ...point, dragStart: true }) }
    })
  } catch (error) { failure.value = error.message || '游戏引擎启动失败'; emit('error', failure.value) }
}
function keyboard(event) {
  if (!props.interactive || !loaded.value) return
  const directions = { ArrowLeft: [-1, 0], ArrowRight: [1, 0], ArrowUp: [0, -1], ArrowDown: [0, 1] }
  if (directions[event.key]) { event.preventDefault(); const [x, y] = directions[event.key]; renderer?.move(props.defenseSide === 'left' ? -x : x, y) }
  else if (event.key === 'Enter' || event.key === ' ') { event.preventDefault(); renderer?.choose() }
}
watch(() => [props.player, props.hidden, props.interactive, props.preview, props.region, props.event, props.events, props.selected, props.radar, props.defenseSide, props.militaryEvent, props.militaryAttacking], () => renderer?.update(snapshot()), { deep: true })
onMounted(start)
onUnmounted(() => { disposed = true; renderer?.destroy(); renderer = null; emit('animation', false) })
</script>

<style scoped>
.naval-board{position:relative;aspect-ratio:1;width:100%;min-width:0;background:#d3e7eb;border:1px solid #9ebfc8;border-radius:4px;overflow:hidden;isolation:isolate;box-shadow:inset 0 0 0 3px #e8f1f1}
.phaser-host{position:absolute;inset:0}.phaser-host:deep(canvas){display:block!important;width:100%!important;height:100%!important;image-rendering:pixelated;touch-action:none;outline-offset:-3px;margin:0!important}
.enabled .phaser-host:deep(canvas){cursor:crosshair}.board-coordinate{position:absolute;right:5px;bottom:2px;font:10px monospace;color:#69828b;pointer-events:none}
.board-loading,.board-failure{position:absolute;inset:0;display:flex;flex-direction:column;align-items:center;justify-content:center;gap:13px;padding:18px;background:#e5eff1;color:#5e838e;font-size:12px;text-align:center;z-index:2}.board-loading progress{width:48%;height:5px;accent-color:#d6954c}.loading-radar{width:35px;height:35px;border:1px solid #aecbd3;border-radius:50%;position:relative}.loading-radar:after{content:'';position:absolute;left:50%;top:50%;width:16px;height:1px;background:#7eafb6;transform-origin:left;animation:loading-sweep 1.2s linear infinite}.board-failure button{padding:7px 12px;border:1px solid #c0d5dc;background:white;border-radius:4px;color:#63828c;font-size:12px}@keyframes loading-sweep{to{transform:rotate(360deg)}}
</style>
<style scoped>
.mirrored .phaser-host:deep(canvas){transform:scaleX(-1)}
</style>
