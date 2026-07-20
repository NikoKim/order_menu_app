<template>
  <div class="page-container">
    <div class="page-header">
      <h1 class="page-title">消息</h1>
    </div>

    <div class="chat-tabs">
      <button class="tab-btn" :class="{ active: tab === 'chat' }" @click="tab = 'chat'">
        聊天
        <span v-if="chatUnread" class="tab-badge">{{ chatUnread > 99 ? '99+' : chatUnread }}</span>
      </button>
      <button class="tab-btn" :class="{ active: tab === 'moments' }" @click="tab = 'moments'">
        动态
        <span v-if="momentUnread" class="tab-badge">{{ momentUnread > 99 ? '99+' : momentUnread }}</span>
      </button>
      <div class="tab-indicator" :class="{ right: tab === 'moments' }"></div>
    </div>

    <div v-show="tab === 'chat'">
      <ChatConversations variant="shop" />
    </div>

    <div v-show="tab === 'moments'">
      <MomentFeed />
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted } from 'vue'
import { getConversations } from '../../api/messages'
import { getMomentUnreadCount } from '../../api/moments'
import ChatConversations from '../chat/ChatConversations.vue'
import MomentFeed from '../moments/MomentFeed.vue'

const tab = ref('chat')
const chatUnread = ref(0)
const momentUnread = ref(0)

let pollTimer = null

async function loadBadges() {
  try {
    const convs = await getConversations()
    chatUnread.value = convs.reduce((sum, c) => sum + (c.unreadCount || 0), 0)
  } catch { }
  try {
    const res = await getMomentUnreadCount()
    momentUnread.value = res.relatedUnreadCount || 0
  } catch { }
}

onMounted(() => {
  loadBadges()
  pollTimer = setInterval(loadBadges, 5000)
})
onUnmounted(() => clearInterval(pollTimer))
</script>

<style scoped>
.page-header { display: flex; align-items: center; margin-bottom: 14px; }
.page-title { font-size: 20px; font-weight: 700; }

.chat-tabs {
  display: flex; position: relative; margin-bottom: 14px;
  background: #fff; border-radius: var(--radius-lg); padding: 4px;
  border: 1px solid var(--color-line-light);
}
.tab-btn {
  flex: 1; padding: 10px; border: none; background: none; font-size: 14px;
  font-weight: 600; color: var(--color-text-muted); cursor: pointer;
  border-radius: 10px; transition: all 0.25s; position: relative; z-index: 1;
  display: flex; align-items: center; justify-content: center; gap: 6px;
}
.tab-btn.active { color: var(--color-text); font-weight: 700; }

.tab-badge {
  min-width: 18px; height: 18px; border-radius: 9px;
  background: var(--color-error); color: #fff; font-size: 11px; font-weight: 700;
  display: inline-flex; align-items: center; justify-content: center; padding: 0 5px;
}

@media (max-width: 768px) {
  .page-container { padding: max(10px, env(safe-area-inset-top)) 12px 12px; }
  .page-header { padding-top: 4px; }
}
</style>
