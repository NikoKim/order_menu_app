<template><nav class="community-actions" aria-label="海战社区操作"><button v-for="item in items" :key="item.id" type="button" class="navy-toolbar-button" :title="item.id === 'chat' && unread ? '聊天大厅有新消息' : item.label" :aria-label="item.label" :aria-description="item.id === 'chat' && unread ? '有未读消息' : undefined" :aria-pressed="active === item.id" :class="{ active: active === item.id }" @click="$emit('select', item.id)"><component :is="item.icon" :size="18" /><span v-if="labels">{{ item.label }}</span><i v-if="item.id === 'chat' && unread" class="lobby-unread-dot" aria-hidden="true" /></button></nav></template>
<script setup>
import { ChartNoAxesColumnIncreasing, DoorOpen, History, MessagesSquare, Star, UserRound } from 'lucide-vue-next'
defineEmits(['select'])
import { computed } from 'vue'
const props = defineProps({ active: String, labels: Boolean, unread: Boolean, lobby: { type: Boolean, default: true }, profile: { type: Boolean, default: true } })
const items = computed(() => [{ id: 'rooms', label: '游戏房间', icon: DoorOpen }, { id: 'ranking', label: '排行榜', icon: ChartNoAxesColumnIncreasing }, { id: 'history', label: '历史战绩', icon: History }, ...(props.lobby ? [{ id: 'chat', label: '聊天大厅', icon: MessagesSquare }] : []), ...(props.profile ? [{ id: 'profile', label: '个人主页', icon: UserRound }] : []), { id: 'reviews', label: '玩家点评', icon: Star }])
</script>
<style scoped>
.community-actions{display:flex;align-items:center;gap:6px;margin-left:12px;margin-right:2px;flex-shrink:0}.community-actions button{width:var(--navy-toolbar-size);height:var(--navy-toolbar-size);display:flex;align-items:center;justify-content:center;gap:7px;padding:0;flex-shrink:0;border-radius:4px;color:var(--color-toolbar);background:transparent}.community-actions button svg{flex-shrink:0}.community-actions button:hover,.community-actions button.active{background:var(--color-primary-bg);color:var(--color-primary-strong)}.community-actions:has(span){display:contents}.community-actions:has(span) button{width:100%;height:44px;justify-content:flex-start;padding:8px;font-size:12px;white-space:nowrap;background:#edf4f6}.community-actions span{white-space:nowrap}
@media(max-width:600px){.community-actions{gap:2px;margin-left:8px;margin-right:0}.community-actions button{width:var(--navy-toolbar-size);height:var(--navy-toolbar-size)}}
</style>
<style scoped>
.community-actions button{position:relative}
.lobby-unread-dot{position:absolute;top:4px;right:4px;width:7px;height:7px;border-radius:50%;background:#df7159;box-shadow:0 0 0 2px #f6f9f8;pointer-events:none}
</style>
