<template><nav class="community-actions" aria-label="海战社区操作"><button v-for="item in items" :key="item.id" type="button" :title="item.id === 'chat' && unread ? '聊天大厅有新消息' : item.label" :aria-label="item.label" :aria-description="item.id === 'chat' && unread ? '有未读消息' : undefined" :aria-pressed="active === item.id" :class="{ active: active === item.id }" @click="$emit('select', item.id)"><component :is="item.icon" :size="18" /><span v-if="labels">{{ item.label }}</span><i v-if="item.id === 'chat' && unread" class="lobby-unread-dot" aria-hidden="true" /></button></nav></template>
<script setup>
import { ChartNoAxesColumnIncreasing, DoorOpen, History, MessagesSquare, Star, Medal } from 'lucide-vue-next'
defineEmits(['select'])
import { computed } from 'vue'
const props = defineProps({ active: String, labels: Boolean, unread: Boolean, lobby: { type: Boolean, default: true }, medals: { type: Boolean, default: true } })
const items = computed(() => [{ id: 'rooms', label: '游戏房间', icon: DoorOpen }, { id: 'ranking', label: '排行榜', icon: ChartNoAxesColumnIncreasing }, { id: 'history', label: '历史战绩', icon: History }, ...(props.lobby ? [{ id: 'chat', label: '聊天大厅', icon: MessagesSquare }] : []), ...(props.medals ? [{ id: 'medals', label: '勋章墙', icon: Medal }] : []), { id: 'reviews', label: '玩家点评', icon: Star }])
</script>
<style scoped>
.community-actions{display:flex;align-items:center;gap:6px;margin-left:12px;margin-right:2px;flex-shrink:0}.community-actions button{width:34px;height:34px;display:flex;align-items:center;justify-content:center;gap:7px;padding:0;flex-shrink:0;border-radius:4px;color:#708b93;background:transparent}.community-actions button svg{flex-shrink:0}.community-actions button:hover,.community-actions button.active{background:#e4f0e9;color:#347b60}.community-actions:has(span){display:contents}.community-actions:has(span) button{width:100%;height:44px;justify-content:flex-start;padding:8px;font-size:12px;white-space:nowrap;background:#edf4f6}.community-actions span{white-space:nowrap}
@media(max-width:600px){.community-actions{gap:2px;margin-left:8px;margin-right:0}.community-actions button{width:30px;height:32px}}
</style>
<style scoped>
.community-actions button{position:relative}
.lobby-unread-dot{position:absolute;top:4px;right:4px;width:7px;height:7px;border-radius:50%;background:#df7159;box-shadow:0 0 0 2px #f6f9f8;pointer-events:none}
</style>
