<template>
  <div ref="host" class="naval-stickers">
    <button ref="trigger" class="sticker-toggle" type="button" title="舰队表情包" aria-label="展开舰队表情包" :aria-expanded="open" :disabled="disabled" @click="toggle"><SmilePlus :size="21" /></button>
    <Teleport :to="panelTarget || 'body'" :disabled="!panelTarget"><Transition name="sticker-picker"><section v-if="open" ref="picker" class="sticker-picker" aria-label="舰队表情包"><header><strong>舰队表情包</strong><button type="button" aria-label="收起表情包" title="收起表情包" @click="close"><X :size="17" /></button></header><div class="sticker-grid"><button v-for="sticker in NAVAL_STICKERS" :key="sticker.id" type="button" :title="sticker.name" :aria-label="`发送表情包：${sticker.name}`" :disabled="disabled" @click="send(sticker.id)"><img :src="sticker.src" :alt="sticker.name" loading="lazy" /><small>{{ sticker.name }}</small></button></div></section></Transition></Teleport>
  </div>
</template>
<script setup>
import { onMounted, onUnmounted, ref } from 'vue'
import { SmilePlus, X } from 'lucide-vue-next'
import { NAVAL_STICKERS } from '../../games/navalStickers'
defineProps({ disabled: Boolean, panelTarget: Object })
const emit = defineEmits(['send'])
const open = ref(false), host = ref(null), trigger = ref(null), picker = ref(null)
function toggle() { open.value = !open.value }
function close() { open.value = false; trigger.value?.focus() }
function send(id) { emit('send', id); close() }
function outside(event) { if (open.value && !host.value?.contains(event.target) && !picker.value?.contains(event.target)) open.value = false }
function key(event) { if (open.value && event.key === 'Escape') { event.stopPropagation(); close() } }
onMounted(() => { document.addEventListener('pointerdown', outside); document.addEventListener('keydown', key) })
onUnmounted(() => { document.removeEventListener('pointerdown', outside); document.removeEventListener('keydown', key) })
</script>
<style scoped>
.naval-stickers{position:relative;flex-shrink:0;display:flex;align-items:center}.sticker-toggle{width:32px;height:32px;display:grid;place-items:center;background:transparent;color:#5c8b79;border-radius:4px}.sticker-toggle:hover,.sticker-toggle[aria-expanded=true]{background:#e2eee9}.sticker-picker{position:fixed;z-index:750;box-sizing:border-box;background:#fbfdfb;box-shadow:0 8px 32px #214c4433;border:1px solid #d5e4dc;border-radius:6px;padding:8px;overflow:auto;overscroll-behavior:contain}.sticker-picker header{display:flex;align-items:center;justify-content:space-between;color:#4f7966;font-size:12px;height:26px}.sticker-picker header button{display:grid;place-items:center;width:26px;height:26px;background:transparent;color:#66827b}.sticker-grid{display:grid;grid-template-columns:repeat(4,minmax(0,1fr));gap:4px}.sticker-grid>button{min-width:0;padding:3px;background:transparent;border-radius:4px;display:flex;flex-direction:column;align-items:center;color:#789187}.sticker-grid>button:hover{background:#e2eee9}.sticker-grid img{width:100%;aspect-ratio:1;object-fit:contain;image-rendering:pixelated;pointer-events:none}.sticker-grid small{font-size:10px;white-space:nowrap}.sticker-toggle:disabled,.sticker-grid>button:disabled{opacity:.4}.sticker-picker-enter-active,.sticker-picker-leave-active{transition:opacity .18s,transform .18s}.sticker-picker-enter-from,.sticker-picker-leave-to{opacity:0;transform:translateY(6px)}
</style>
<style scoped>
@media(min-width:500px) and (max-height:450px){.sticker-grid{grid-template-columns:repeat(8,minmax(0,1fr))}}
@media(max-width:499px) and (min-height:451px){.sticker-grid img{height:48px;width:48px}.sticker-grid>button{padding:4px 3px}}
</style>
<style scoped>
.sticker-picker{position:relative;inset:auto;width:100%;max-height:220px;box-shadow:none;padding:8px 6px;border:0;background:#edf5f0;border-radius:4px;overflow:auto}.sticker-grid{gap:8px 6px}.sticker-grid>button{gap:8px;padding:6px 3px}.sticker-grid img{height:54px;width:54px;max-width:100%;flex-shrink:0}.sticker-grid small{line-height:16px;min-height:16px;display:block;font-size:11px}.sticker-picker-enter-active,.sticker-picker-leave-active{transition:opacity .2s,transform .2s}.sticker-picker-enter-from,.sticker-picker-leave-to{transform:translateY(-6px);opacity:0}@media(max-height:450px){.sticker-picker{max-height:145px}.sticker-grid img{height:38px;width:38px}.sticker-grid>button{gap:6px;padding:4px 2px}.sticker-picker header{height:24px}}
</style>
