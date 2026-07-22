<template>
  <div class="page-container sub-page">
    <header class="page-header back-header">
      <button class="icon-btn" title="返回" @click="router.back()"><ArrowLeft :size="22" /></button>
      <h1 class="page-title">消息通知</h1>
      <button v-if="hasUnread" class="read-all" @click="handleReadAll">全部已读</button>
    </header>
    <LoadingSpinner v-if="loading" />
    <EmptyState v-else-if="error" icon="warning" :title="error" action-text="重试" @action="load()" />
    <div v-else-if="notifications.length" class="notification-list">
      <button v-for="notice in notifications" :key="notice.id" class="notice-row" :class="{ unread: !notice.readAt && !notice.read }" @click="openNotice(notice)">
        <span class="notice-icon"><Bell :size="20" /></span>
        <span class="notice-copy">
          <strong>{{ notice.title || typeLabel(notice.type) }}</strong>
          <span>{{ notice.content || notice.message }}</span>
          <small>{{ formatTime(notice.createdAt) }}</small>
        </span>
        <span v-if="!notice.readAt && !notice.read" class="unread-dot"></span>
        <ChevronRight v-else :size="17" class="chevron" />
      </button>
    </div>
    <EmptyState v-else title="暂无消息通知" />
  </div>
</template>

<script setup>
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ArrowLeft, Bell, ChevronRight } from 'lucide-vue-next'
import { getNotifications, markAllNotificationsRead, markNotificationRead } from '../../api/messages'
import LoadingSpinner from '../../components/LoadingSpinner.vue'
import EmptyState from '../../components/EmptyState.vue'
import { formatMomentTime } from '../../composables/useMomentTime'
import { connectCustomerWebSocket, subscribeCustomerWebSocket } from '../../services/customerWebSocket'

const router = useRouter()
const notifications = ref([])
const loading = ref(true)
const error = ref('')
const hasUnread = computed(() => notifications.value.some(item => !item.readAt && !item.read))
let unsubscribeWs

async function load(silent = false) {
  if (!silent) loading.value = true
  error.value = ''
  try {
    const data = await getNotifications()
    const rows = data?.notifications || data?.records || (Array.isArray(data) ? data : [])
    notifications.value = rows.map(normalizeNotification)
  } catch (e) { error.value = e.message }
  finally { loading.value = false }
}

function normalizeNotification(notice) {
  let payload = {}
  try { payload = typeof notice.payload === 'string' ? JSON.parse(notice.payload) : (notice.payload || {}) } catch { }
  return { ...notice, ...payload }
}

function typeLabel(type) {
  const labels = { ORDER: '订单通知', FAMILY: '家庭通知', FEEDBACK: '留言通知', CHAT: '聊天通知', MOMENT: '动态通知' }
  return labels[type] || '消息通知'
}

const formatTime = formatMomentTime

async function openNotice(notice) {
  if (!notice.readAt && !notice.read) {
    try { await markNotificationRead(notice.id); notice.read = true; notice.readAt = new Date().toISOString() } catch { }
  }
  const role = JSON.parse(localStorage.getItem('user') || '{}')?.role
  const prefix = role === 'SHOP' ? '/shop' : '/customer'
  if (notice.orderId) router.push(`${prefix}/orders/${notice.orderId}`)
  else if (notice.momentId) router.push({ path: `${prefix}/moments/${notice.momentId}`, query: notice.commentId ? { commentId: notice.commentId } : {} })
  else if (notice.conversationId) router.push(`${prefix}/chat/${notice.conversationId}`)
}

async function handleReadAll() {
  await markAllNotificationsRead()
  notifications.value.forEach(item => { item.read = true; item.readAt = item.readAt || new Date().toISOString() })
}

onMounted(() => {
  load()
  connectCustomerWebSocket()
  unsubscribeWs = subscribeCustomerWebSocket('NOTIFICATION', message => {
    if (message.action === 'CREATED' || message.action === 'READ_CHANGED') load(true)
  })
})
onUnmounted(() => unsubscribeWs?.())
</script>

<style scoped>
.sub-page { max-width: 720px; }
.back-header { flex-wrap: nowrap; justify-content: flex-start; }
.icon-btn { width: 40px; height: 40px; flex: 0 0 auto; display: inline-flex; align-items: center; justify-content: center; background: transparent; border-radius: var(--radius); }
.read-all { margin-left: auto; min-height: 40px; padding: 0 8px; background: transparent; color: var(--color-primary); font-size: 13px; }
.notification-list { overflow: hidden; background: #fff; border: 1px solid var(--color-line-light); border-radius: var(--radius-lg); }
.notice-row { width: 100%; min-height: 78px; display: flex; align-items: center; gap: 12px; padding: 12px 14px; text-align: left; background: #fff; color: var(--color-text); }
.notice-row + .notice-row { border-top: 1px solid var(--color-line); }
.notice-row.unread { background: #fffaf4; }
.notice-icon { width: 38px; height: 38px; display: inline-flex; align-items: center; justify-content: center; border-radius: var(--radius); color: var(--color-primary); background: var(--color-primary-bg); flex: 0 0 auto; }
.notice-copy { flex: 1; min-width: 0; display: flex; flex-direction: column; }
.notice-copy strong { font-size: 14px; }
.notice-copy span { font-size: 13px; color: var(--color-text-muted); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.notice-copy small { margin-top: 4px; color: var(--color-text-light); }
.unread-dot { width: 8px; height: 8px; border-radius: 50%; background: var(--color-error); }
.chevron { color: var(--color-text-light); }
@media (max-width: 768px) { .sub-page { padding-top: max(10px, env(safe-area-inset-top)); } }
</style>
