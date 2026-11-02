<template>
  <div ref="controls" :class="rotateOnly ? 'model-rotate-controls' : 'target-confirmation'" :style="position" :data-tutorial="rotateOnly ? 'naval-model-rotate' : 'naval-confirm-target'" role="group" :aria-label="rotateOnly ? '舰船旋转' : '确认目标'" @pointerdown.stop @click.stop>
    <button v-if="rotateOnly" class="model-rotate" :disabled="busy" title="旋转舰船" aria-label="旋转选中舰船" @click="$emit('rotate')"><RotateCw :size="17" /></button>
    <template v-else><button v-if="rotatable" class="preview-rotate" :disabled="busy" title="旋转舰船" aria-label="旋转预览舰船" @click="$emit('rotate')"><RotateCw :size="18" /></button><button :disabled="busy" title="确认使用" aria-label="确认使用" @click="$emit('confirm')"><Check :size="20" /></button>
    <button :disabled="busy" title="取消预览" aria-label="取消预览" @click="$emit('cancel')"><X :size="20" /></button></template>
  </div>
</template>
<script setup>
import { computed, onMounted, onBeforeUnmount, ref } from 'vue'
import { Check, RotateCw, X } from 'lucide-vue-next'
const props = defineProps({ point: { type: Object, required: true }, shape: Object, busy: Boolean, rotateOnly: Boolean, rotatable:Boolean })
defineEmits(['confirm', 'cancel', 'rotate'])
const controls = ref(null), boardSize = ref(300)
let observer
onMounted(() => {
  observer = new ResizeObserver(([entry]) => { boardSize.value = entry.contentRect.width })
  observer.observe(controls.value.parentElement)
})
onBeforeUnmount(() => observer?.disconnect())
const position = computed(() => {
  const scale = boardSize.value/624, shape = props.shape || {}, x = shape.x ?? props.point.x, y = shape.y ?? props.point.y
  const width = props.rotateOnly ? 34 : props.rotatable ? 114 : 78, height = props.rotateOnly ? 34 : 38
  const top = (24+y*48)*scale, bottom = (24+(y+(shape.h || 1))*48)*scale
  const mirrored = controls.value?.parentElement.classList.contains('mirrored')
  const cx = x+(shape.w || 1)/2
  const left = (24+(mirrored ? 12-cx : cx)*48)*scale-width/2
  return { left: `${Math.max(4,Math.min(boardSize.value-width-4,left))}px`, top: `${top >= height+8 ? top-height-5 : Math.min(boardSize.value-height-4,bottom+5)}px` }
})
</script>
<style scoped>
.target-confirmation>.preview-rotate{background:#e8eff1;color:#55717b}
.target-confirmation,.model-rotate-controls{position:absolute;z-index:15;display:flex;gap:4px;padding:3px;box-sizing:border-box;background:#fff;border:1px solid #98beb9;border-radius:5px;box-shadow:0 3px 9px #193d4933;animation:target-pop .15s ease-out}.target-confirmation>button{width:32px;height:30px;display:grid;place-items:center;border-radius:3px;color:#fff;background:#32886c;flex:none;padding:0}.target-confirmation>button:last-child{background:#f2e6e2;color:#b35c4b}.model-rotate-controls .model-rotate{position:static!important;transform:none!important;width:26px!important;height:26px!important;display:grid;place-items:center;padding:0;background:white;color:#a57539;border:0}@keyframes target-pop{from{opacity:0;transform:translateY(5px)}}
</style>
