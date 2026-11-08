<template>
  <div class="naval-chat-composer" :class="{ mobile, 'keyboard-open':keyboardOpen }">
    <div class="chat-compose"><div class="chat-tools" :inert="disabled || undefined"><NavalStickers :disabled="disabled" :panel-target="stickerTray" @send="$emit('sticker', $event)" /><NavalEmoji @select="appendEmoji" /></div><form class="message-compose" @submit.prevent="submit"><input ref="input" :value="modelValue" :aria-label="label" :placeholder="placeholder" maxlength="300" :disabled="disabled" @input="$emit('update:modelValue', $event.target.value.slice(0, 300))" /><button type="submit" :disabled="disabled || !modelValue.trim()" class="send-button" :aria-label="`发送${label}`" title="发送"><Send :size="18" /></button></form></div>
    <div ref="stickerTray" class="sticker-tray" />
  </div>
</template>
<script setup>
import { ref } from 'vue'
import { Send } from 'lucide-vue-next'
import NavalEmoji from './NavalEmoji.vue'
import NavalStickers from './NavalStickers.vue'
const props = defineProps({modelValue:{type:String,default:''}, disabled:Boolean, mobile:Boolean, keyboardOpen:Boolean, label:{type:String,default:'消息'}, placeholder:{type:String,default:'发送消息'}})
const emit = defineEmits(['update:modelValue','send','sticker'])
const stickerTray = ref(null), input = ref(null)
function appendEmoji(emoji) { if (!props.disabled) emit('update:modelValue', (props.modelValue + emoji).slice(0, 300)) }
function submit() { if (!props.disabled && props.modelValue.trim()) emit('send') }
defineExpose({focus:() => input.value?.focus()})
</script>
<style scoped>
.naval-chat-composer{flex-shrink:0;min-width:0;container-type:inline-size}.chat-compose{display:flex;flex-direction:column;gap:4px;min-width:0}.chat-tools{display:flex;align-items:center;gap:6px;flex-shrink:0;min-width:0}.chat-tools :deep(.naval-emoji){padding:0;gap:2px;min-width:0}.chat-tools :deep(.naval-emoji button){width:26px;height:28px;font-size:18px}.chat-tools[inert]{opacity:.5}.message-compose{display:flex;gap:8px;min-width:0;margin:0;flex-shrink:0;min-height:38px}.message-compose input{flex:1;min-width:0;width:0;padding:9px;border:1px solid #dce6e9;border-radius:4px;background:#fff;color:#55717c;font:inherit;font-size:13px}.send-button{display:grid;place-items:center;width:38px;flex:0 0 38px;border:0;border-radius:4px;background:var(--color-primary,#ec8b33);color:#fff}.send-button:disabled{opacity:.4;cursor:default}.sticker-tray{min-height:0}.sticker-tray:empty{display:none}.sticker-tray:not(:empty){height:min(220px,38dvh);min-height:70px;margin-top:4px;overflow:auto;overscroll-behavior:contain}.mobile input{font-size:16px;min-height:38px;scroll-margin-block:8px;box-sizing:border-box}.keyboard-open .chat-tools,.keyboard-open .sticker-tray{display:none}
@media(orientation:landscape) and (max-height:550px){.message-compose input{padding:6px 8px}.chat-tools :deep(.naval-emoji button){width:25px;height:28px;font-size:17px}.sticker-tray:not(:empty){height:145px;min-height:110px}.naval-chat-composer:has(.sticker-picker) .chat-tools{display:none}}
@container(min-width:520px){.mobile .chat-compose{flex-direction:row;align-items:center;gap:12px}.mobile .message-compose{flex:1}.mobile.keyboard-open .chat-compose{display:block}}
</style>
