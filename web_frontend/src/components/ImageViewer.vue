<template>
  <Teleport to="body">
    <div v-if="visible" class="iv-backdrop" @click="close">
      <button class="iv-close" @click="close" title="关闭"><X :size="24" /></button>
      <img :src="src" :alt="alt" class="iv-image" @click.stop />
    </div>
  </Teleport>
</template>

<script setup>
import { X } from 'lucide-vue-next'
defineProps({ visible: Boolean, src: String, alt: String })
const emit = defineEmits(['close'])
function close() { emit('close') }
</script>

<style scoped>
.iv-backdrop {
  position: fixed; inset: 0; z-index: 9999; background: rgba(0,0,0,0.92);
  display: flex; align-items: center; justify-content: center;
  cursor: pointer;
}
.iv-close {
  position: absolute; top: 16px; right: 16px; width: 44px; height: 44px;
  border: none; background: rgba(255,255,255,0.15); color: #fff;
  border-radius: 50%; cursor: pointer; display: flex; align-items: center; justify-content: center;
  z-index: 1;
}
.iv-image { max-width: 96vw; max-height: 92dvh; object-fit: contain; cursor: default; border-radius: 4px; }
</style>
