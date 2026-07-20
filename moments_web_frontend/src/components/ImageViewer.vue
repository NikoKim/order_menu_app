<template>
  <Teleport to="body">
    <div v-if="visible" class="iv-backdrop" @click="scheduleClose">
      <button class="iv-close" type="button" title="关闭" aria-label="关闭" @click.stop="closeNow"><X :size="24" /></button>
      <div class="iv-stage" :class="{ zoomed }">
        <div v-if="!imageLoaded" class="iv-skeleton" aria-label="图片加载中"></div>
        <img
          :src="src"
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
    </div>
  </Teleport>
</template>

<script setup>
import { ref, watch, onUnmounted } from 'vue'
import { X } from 'lucide-vue-next'

const props = defineProps({ visible: Boolean, src: String, alt: String })
const emit = defineEmits(['close'])
const imageLoaded = ref(false)
const zoomed = ref(false)
const transformOrigin = ref('50% 50%')
let closeTimer = null

function scheduleClose() {
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

watch(() => [props.visible, props.src], () => {
  imageLoaded.value = false
  zoomed.value = false
  transformOrigin.value = '50% 50%'
  clearTimeout(closeTimer)
})

onUnmounted(() => clearTimeout(closeTimer))
</script>

<style scoped>
.iv-backdrop {
  position: fixed; inset: 0; z-index: 9999; overflow: hidden; background: rgba(0,0,0,0.92);
  display: flex; align-items: center; justify-content: center; cursor: zoom-out;
  animation: iv-backdrop-in .2s ease both;
}
.iv-close {
  position: absolute; top: max(16px, env(safe-area-inset-top)); right: 16px; width: 44px; height: 44px;
  border: none; background: rgba(255,255,255,0.15); color: #fff;
  border-radius: 50%; cursor: pointer; display: flex; align-items: center; justify-content: center;
  z-index: 2;
}
.iv-stage { position: relative; display: grid; place-items: center; max-width: 96vw; max-height: 92dvh; }
.iv-skeleton { position: absolute; width: min(72vw, 620px); height: min(54dvh, 440px); border-radius: 6px; background: linear-gradient(100deg, #25272a 18%, #3a3d41 38%, #25272a 58%); background-size: 220% 100%; animation: iv-shimmer 1.25s linear infinite; }
.iv-image { max-width: 96vw; max-height: 92dvh; object-fit: contain; cursor: zoom-in; border-radius: 4px; opacity: 0; transform: scale(.985); transition: transform .3s cubic-bezier(.22,.8,.32,1), opacity .24s ease; user-select: none; }
.iv-image.loaded { opacity: 1; transform: scale(1); }
.iv-image.loaded.zoomed { transform: scale(2.35); cursor: zoom-out; }
@keyframes iv-shimmer { to { background-position: -220% 0; } }
@keyframes iv-backdrop-in { from { opacity: 0; } to { opacity: 1; } }
</style>
