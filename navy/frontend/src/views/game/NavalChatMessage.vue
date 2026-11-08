<template>
  <article class="naval-chat-message" :class="{ mine }">
    <time v-if="showTime && timestamp" class="message-time" :datetime="timestamp.datetime">{{ timestamp.label }}</time>
    <div class="message-row"><NavalAvatar :person="person || message" :size="19" /><div class="message-body"><strong class="message-name">{{ message.name || person?.name || '指挥官' }}</strong><NavalChatBubble class="message-bubble" :bubble-style="message.bubbleStyle" :expires-at="message.bubbleExpiresAt" :mine="mine" :rich="rich"><slot><NavalMessage :text="message.text" /></slot></NavalChatBubble></div></div>
  </article>
</template>
<script setup>
import { computed } from 'vue'
import NavalAvatar from './NavalAvatar.vue'
import NavalChatBubble from './NavalChatBubble.vue'
import NavalMessage from './NavalMessage.vue'
import { navalChatTime } from '../../games/navalChatPresentation'
const props = defineProps({message:{type:Object,required:true}, person:Object, mine:Boolean, rich:Boolean, showTime:Boolean})
const timestamp = computed(() => navalChatTime(props.message.at))
</script>
<style scoped>
.naval-chat-message{padding:8px 0;font-size:13px;line-height:1.6;white-space:normal;overflow-wrap:anywhere}.message-time{display:block;text-align:center;font-size:10px;line-height:18px;margin:5px 0 16px;color:#92a2a6}.message-row{display:flex;align-items:flex-start;gap:9px;min-width:0}.message-row :deep(.naval-avatar){width:32px;height:32px;margin-top:2px}.message-body{display:flex;align-items:flex-start;flex-direction:column;min-width:0;max-width:calc(100% - 41px)}.message-name{font-size:10px;line-height:16px;font-weight:400;color:#85999f;overflow-wrap:anywhere;margin:0 0 5px}.message-bubble{max-width:100%}.mine .message-row{flex-direction:row-reverse}.mine .message-body{align-items:flex-end}.message-bubble :deep(.naval-message){max-width:100%}
@media(orientation:landscape) and (max-height:550px){.message-row :deep(.naval-avatar){width:28px;height:28px}.message-body{max-width:calc(100% - 37px)}}
</style>
