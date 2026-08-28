<template>
  <Teleport to="body">
    <div
      v-if="visible"
      class="iv-backdrop"
      role="dialog"
      aria-modal="true"
      @click="scheduleClose"
      @touchstart.passive="handleTouchStart"
      @touchend.passive="handleTouchEnd"
    >
      <button class="iv-close" type="button" title="关闭" aria-label="关闭" @click.stop="closeNow"><X :size="24" /></button>
      <button
        v-if="sources.length > 1"
        class="iv-nav iv-prev"
        type="button"
        title="上一张"
        aria-label="上一张"
        :disabled="currentIndex === 0"
        @click.stop="previous"
      ><ChevronLeft :size="30" /></button>

      <div class="iv-stage" :class="{ zoomed }">
        <div v-if="!imageLoaded" class="iv-skeleton" aria-label="图片加载中"></div>
        <img
          :key="`${currentIndex}-${currentSrc}`"
          :src="currentSrc"
          :alt="alt"
          class="iv-image"
          :class="{ loaded: imageLoaded, zoomed }"
          :style="{ transformOrigin }"
          draggable="false"
          @load="imageLoaded = true"
          @error="imageLoaded = true"
          @dblclick.stop.prevent="toggleZoom"
        />
      </div>

      <button
        v-if="sources.length > 1"
        class="iv-nav iv-next"
        type="button"
        title="下一张"
        aria-label="下一张"
        :disabled="currentIndex === sources.length - 1"
        @click.stop="next"
      ><ChevronRight :size="30" /></button>
      <span v-if="sources.length > 1" class="iv-count">{{ currentIndex + 1 }}/{{ sources.length }}</span>
    </div>
  </Teleport>
</template>

<script setup>
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { ChevronLeft, ChevronRight, X } from 'lucide-vue-next'

const props = defineProps({
  visible: Boolean,
  src: { type: String, default: '' },
  images: { type: Array, default: () => [] },
  initialIndex: { type: Number, default: 0 },
  alt: { type: String, default: '' }
})
const emit = defineEmits(['close', 'change'])
const sources = computed(() => {
  const values = props.images?.length ? props.images : [props.src]
  return values.filter(Boolean)
})
const currentIndex = ref(0)
const currentSrc = computed(() => sources.value[currentIndex.value] || '')
const imageLoaded = ref(false)
const zoomed = ref(false)
const transformOrigin = ref('50% 50%')
let closeTimer = null
let touchStart = null
let suppressNextClick = false

function resetImageState() {
  imageLoaded.value = false
  zoomed.value = false
  transformOrigin.value = '50% 50%'
  clearTimeout(closeTimer)
}

function setIndex(index) {
  const nextIndex = Math.max(0, Math.min(sources.value.length - 1, index))
  if (nextIndex === currentIndex.value) return
  currentIndex.value = nextIndex
  resetImageState()
  emit('change', nextIndex)
}

function previous() { if (!zoomed.value) setIndex(currentIndex.value - 1) }
function next() { if (!zoomed.value) setIndex(currentIndex.value + 1) }

function scheduleClose() {
  if (suppressNextClick) {
    suppressNextClick = false
    return
  }
  clearTimeout(closeTimer)
  closeTimer = window.setTimeout(closeNow, 230)
}

function closeNow() {
  clearTimeout(closeTimer)
  closeTimer = null
  emit('close')
}

function toggleZoom(event) {
  clearTimeout(closeTimer)
  closeTimer = null
  if (zoomed.value) {
    zoomed.value = false
    transformOrigin.value = '50% 50%'
    return
  }
  const rect = event.currentTarget.getBoundingClientRect()
  const x = ((event.clientX - rect.left) / rect.width) * 100
  const y = ((event.clientY - rect.top) / rect.height) * 100
  transformOrigin.value = `${Math.max(0, Math.min(100, x))}% ${Math.max(0, Math.min(100, y))}%`
  zoomed.value = true
}

function handleTouchStart(event) {
  const touch = event.changedTouches?.[0]
  touchStart = touch ? { x: touch.clientX, y: touch.clientY } : null
}

function handleTouchEnd(event) {
  const touch = event.changedTouches?.[0]
  if (!touchStart || !touch || zoomed.value) return
  const dx = touch.clientX - touchStart.x
  const dy = touch.clientY - touchStart.y
  touchStart = null
  if (Math.abs(dx) < 48 || Math.abs(dx) <= Math.abs(dy)) return
  suppressNextClick = true
  clearTimeout(closeTimer)
  closeTimer = null
  if (dx < 0) next()
  else previous()
}

function handleKeydown(event) {
  if (!props.visible) return
  if (event.key === 'Escape') closeNow()
  if (event.key === 'ArrowLeft') previous()
  if (event.key === 'ArrowRight') next()
}

watch(() => [props.visible, props.initialIndex, props.src, ...(props.images || [])], () => {
  currentIndex.value = Math.max(0, Math.min(sources.value.length - 1, Number(props.initialIndex) || 0))
  resetImageState()
})

onMounted(() => window.addEventListener('keydown', handleKeydown))
onUnmounted(() => {
  clearTimeout(closeTimer)
  window.removeEventListener('keydown', handleKeydown)
})
</script>

<style scoped>
.iv-backdrop {
  position: fixed; inset: 0; z-index: 9999; overflow: hidden; background: rgba(0,0,0,0.92);
  display: flex; align-items: center; justify-content: center; cursor: zoom-out; touch-action: none;
  animation: iv-backdrop-in .2s ease both;
}
.iv-close {
  position: absolute; top: max(16px, env(safe-area-inset-top)); right: 16px; width: 44px; height: 44px;
  border: none; background: rgba(255,255,255,0.15); color: #fff;
  border-radius: 50%; cursor: pointer; display: flex; align-items: center; justify-content: center;
  z-index: 3;
}
.iv-stage { position: relative; display: grid; place-items: center; max-width: 96vw; max-height: 92dvh; }
.iv-skeleton { position: absolute; width: min(72vw, 620px); height: min(54dvh, 440px); border-radius: 6px; background: linear-gradient(100deg, #25272a 18%, #3a3d41 38%, #25272a 58%); background-size: 220% 100%; animation: iv-shimmer 1.25s linear infinite; }
.iv-image { max-width: 96vw; max-height: 92dvh; object-fit: contain; cursor: zoom-in; border-radius: 4px; opacity: 0; transform: scale(.985); transition: transform .3s cubic-bezier(.22,.8,.32,1), opacity .24s ease; user-select: none; }
.iv-image.loaded { opacity: 1; transform: scale(1); }
.iv-image.loaded.zoomed { transform: scale(2.35); cursor: zoom-out; }
.iv-nav { position: absolute; top: 50%; z-index: 3; width: 48px; height: 48px; display: grid; place-items: center; border-radius: 50%; background: rgba(255,255,255,.14); color: #fff; transform: translateY(-50%); }
.iv-prev { left: max(14px, env(safe-area-inset-left)); }
.iv-next { right: max(14px, env(safe-area-inset-right)); }
.iv-nav:disabled { opacity: .25; cursor: default; }
.iv-count { position: absolute; left: 50%; bottom: max(20px, env(safe-area-inset-bottom)); z-index: 3; padding: 5px 10px; border-radius: 6px; background: rgba(0,0,0,.62); color: #fff; font-size: 12px; transform: translateX(-50%); }
@keyframes iv-shimmer { to { background-position: -220% 0; } }
@keyframes iv-backdrop-in { from { opacity: 0; } to { opacity: 1; } }
@media (max-width: 640px) {
  .iv-nav { top: auto; bottom: max(16px, env(safe-area-inset-bottom)); width: 42px; height: 42px; transform: none; }
  .iv-prev { left: 18px; }
  .iv-next { right: 18px; }
}
</style>
