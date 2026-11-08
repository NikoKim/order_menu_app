<template>
 <Teleport :to="target"><Transition name="notice-dialog" @after-leave="$emit('closed')"><div v-if="open" class="notice-dialog-overlay" @click.self="$emit('close')" @keydown="keydown"><section ref="panel" class="notice-dialog" :role="role" aria-modal="true" :aria-label="title" tabindex="-1"><button v-if="dismissible" class="dialog-close" title="关闭" aria-label="关闭弹窗" @click="$emit('close')"><X :size="18" /></button><slot /></section></div></Transition></Teleport>
</template>
<script setup>
import { ref, watch, nextTick, onUnmounted } from 'vue'
import { X } from 'lucide-vue-next'
import { useFullscreenTarget } from '../composables/useFullscreenTarget'
const props=defineProps({open:Boolean,title:String,role:{type:String,default:'dialog'},dismissible:{type:Boolean,default:true}})
const emit=defineEmits(['close','closed']),panel=ref(null),target=useFullscreenTarget()
let previous
watch(()=>props.open,async open=>{if(open){previous=document.activeElement;await nextTick();panel.value?.focus()}else previous?.focus?.()})
function keydown(event){if(event.key==='Escape'&&props.dismissible){event.preventDefault();emit('close')}if(event.key==='Tab'){const elements=[...panel.value.querySelectorAll('a[href],button:not(:disabled),input:not(:disabled)')];if(!elements.length){event.preventDefault();return}const first=elements[0],last=elements.at(-1);if(event.shiftKey&&(document.activeElement===first||document.activeElement===panel.value)){event.preventDefault();last.focus()}else if(!event.shiftKey&&document.activeElement===last){event.preventDefault();first.focus()}}}
onUnmounted(()=>previous?.focus?.())
</script>
<style scoped>
.notice-dialog-overlay{position:fixed;inset:auto;top:var(--navy-visual-top,0px);left:var(--navy-visual-left,0px);width:var(--navy-visual-width,100vw);height:var(--navy-visual-height,100dvh);z-index:2147483646;display:grid;place-items:center;padding:20px;background:#233d4b66;backdrop-filter:blur(2px)}.notice-dialog{position:relative;width:min(420px,100%);max-height:calc(var(--navy-visual-height,100dvh) - 40px);overflow:auto;padding:28px;border:1px solid #d4e2e6;border-radius:8px;background:#f8fbfc;box-shadow:0 22px 60px #203a4929;outline:none;color:#416977}.dialog-close{position:absolute;right:12px;top:12px;width:30px;height:30px;display:grid;place-items:center;background:transparent;border-radius:4px;color:#7b959f;transition:background .2s}.dialog-close:hover{background:#e8f0f2}.notice-dialog-enter-active,.notice-dialog-leave-active{transition:opacity .24s ease}.notice-dialog-enter-active .notice-dialog,.notice-dialog-leave-active .notice-dialog{transition:transform .28s cubic-bezier(.2,.8,.3,1),opacity .24s ease}.notice-dialog-enter-from,.notice-dialog-leave-to{opacity:0}.notice-dialog-enter-from .notice-dialog,.notice-dialog-leave-to .notice-dialog{opacity:0;transform:translateY(14px) scale(.97)}@media(prefers-reduced-motion:reduce){.notice-dialog-enter-active,.notice-dialog-leave-active,.notice-dialog-enter-active .notice-dialog,.notice-dialog-leave-active .notice-dialog{transition-duration:.01s}}
</style>
