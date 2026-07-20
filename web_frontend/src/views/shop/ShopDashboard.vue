<template>
  <div class="page-container dashboard-page">
    <div v-if="loading" class="loading-wrap"><LoadingSpinner /></div>
    <EmptyState v-else-if="error" icon="warning" :title="error" action-text="重试" @action="loadData" />
    <template v-else>
      <div class="page-header">
        <h1 class="page-title">工作台</h1>
      </div>

      <section class="shop-summary">
        <span class="shop-summary-icon"><Store :size="28" /></span>
        <div><strong>{{ shop?.name || '商家工作台' }}</strong><span class="open-status" :class="{ closed: shop?.businessStatus !== 'OPEN' }"><i></i>{{ shop?.businessStatus === 'OPEN' ? '营业中' : '休息中' }}</span></div>
      </section>

      <button v-if="pendingOrders" class="pending-alert" @click="router.push('/shop/orders?status=SUBMITTED')">
        <BellRing :size="21" /><strong>您有 {{ pendingOrders }} 笔新订单待处理</strong><ChevronRight :size="18" />
      </button>

      <section class="data-card">
        <span>今日实收金额 (元)</span>
        <strong>{{ todayAmount }}</strong>
        <div class="sub-metrics"><span>今日成单<b>{{ todayCompleted.length }}</b></span><span>待接单<b>{{ pendingOrders }}</b></span></div>
      </section>

      <h2 class="section-heading">快捷应用</h2>
      <section class="shortcut-grid">
        <router-link v-for="item in shortcuts" :key="item.title" :to="item.to" class="shortcut-item">
          <span class="shortcut-icon"><component :is="item.icon" :size="23" /></span>
          <strong>{{ item.title }}</strong>
          <span v-if="item.badge" class="shortcut-badge">{{ item.badge > 99 ? '99+' : item.badge }}</span>
        </router-link>
      </section>
    </template>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import {
  Bell, BellRing, ChevronRight, Clock3, House, MessageCircle,
  GalleryHorizontalEnd, MessageSquareText, ReceiptText, Store, Utensils
} from 'lucide-vue-next'
import { getConversations, getNotificationUnreadCount } from '../../api/messages'
import { getDishes, getMyShop, getShopFeedback, getShopNotifications, getShopOrders } from '../../api/shop'
import LoadingSpinner from '../../components/LoadingSpinner.vue'
import EmptyState from '../../components/EmptyState.vue'

const router = useRouter()
const shop = ref(null)
const orders = ref([])
const onSaleDishes = ref(0)
const pendingOrders = ref(0)
const pendingFamily = ref(0)
const pendingFeedback = ref(0)
const unreadChats = ref(0)
const unreadNotifications = ref(0)
const loading = ref(true)
const error = ref('')

const todayKey = computed(() => new Date().toISOString().slice(0, 10))
const todayCompleted = computed(() => orders.value.filter(order => order.status === 'COMPLETED' && order.createdAt?.slice(0, 10) === todayKey.value))
const todayAmount = computed(() => todayCompleted.value.reduce((sum, order) => sum + Number(order.totalAmount || 0), 0).toFixed(2))
const shortcuts = computed(() => [
  { icon: Utensils, title: `菜品管理 (${onSaleDishes.value})`, to: '/shop/dishes' },
  { icon: ReceiptText, title: '订单处理', to: '/shop/orders', badge: pendingOrders.value },
  { icon: GalleryHorizontalEnd, title: '首页展示', to: '/shop/showcase' },
  { icon: House, title: '家庭管理', to: '/shop/family', badge: pendingFamily.value },
  { icon: Clock3, title: '营业状态', to: '/shop/profile' },
  { icon: MessageCircle, title: '聊天', to: '/shop/chat', badge: unreadChats.value },
  { icon: MessageSquareText, title: '顾客反馈', to: '/shop/feedback', badge: pendingFeedback.value },
  { icon: Bell, title: '消息通知', to: '/shop/notifications', badge: unreadNotifications.value }
])

async function loadData() {
  loading.value = true; error.value = ''
  try {
    const [shopData, orderData, dishData, noticeData, feedbackData, conversations, unread] = await Promise.all([
      getMyShop(), getShopOrders({ page: 1, pageSize: 100 }), getDishes({ page: 1, pageSize: 1, status: 'ON_SALE' }),
      getShopNotifications(), getShopFeedback({ page: 1, pageSize: 1, status: 'PENDING' }), getConversations(), getNotificationUnreadCount()
    ])
    shop.value = shopData
    orders.value = orderData.records || []
    onSaleDishes.value = dishData.total || 0
    pendingOrders.value = noticeData.pendingOrders || 0
    pendingFamily.value = noticeData.pendingFamilyMembers || 0
    pendingFeedback.value = feedbackData.total || 0
    unreadChats.value = conversations.filter(item => !item.muted).reduce((sum, item) => sum + (item.unreadCount || 0), 0)
    unreadNotifications.value = unread.unreadCount ?? unread.count ?? unread ?? 0
  } catch (e) { error.value = e.message }
  finally { loading.value = false }
}

onMounted(loadData)
</script>

<style scoped>
.loading-wrap { min-height: 50vh; display: flex; align-items: center; justify-content: center; }
.page-header { display: flex; align-items: center; margin-bottom: 14px; }
.page-title { font-size: 20px; font-weight: 700; }
.shop-summary, .data-card, .shortcut-grid { background: #fff; border: 1px solid var(--color-line-light); border-radius: var(--radius-lg); }
.shop-summary { display: flex; align-items: center; gap: 14px; padding: 18px; margin-bottom: 14px; }
.shop-summary-icon { width: 52px; height: 52px; display: inline-flex; align-items: center; justify-content: center; color: var(--color-primary); background: var(--color-primary-bg); border-radius: var(--radius-lg); }
.shop-summary strong { font-size: 17px; font-weight: 700; }
.open-status { margin-top: 5px; display: inline-flex; align-items: center; gap: 6px; color: var(--color-success); font-size: 13px; font-weight: 700; }
.open-status i { width: 8px; height: 8px; border-radius: 50%; background: currentColor; }
.open-status.closed { color: var(--color-text-muted); }
.pending-alert { width: 100%; min-height: 48px; margin-top: 14px; padding: 10px 14px; display: flex; align-items: center; gap: 10px; text-align: left; color: var(--color-primary); background: var(--color-primary-bg); border: 1px solid var(--color-primary-border); border-radius: var(--radius-lg); }
.pending-alert strong { flex: 1; font-size: 14px; }
.data-card { margin-top: 14px; padding: 18px; }
.data-card > span { color: var(--color-text-muted); font-size: 13px; }
.data-card > strong { display: block; margin: 6px 0 16px; font-size: 32px; }
.sub-metrics { display: grid; grid-template-columns: 1fr 1fr; border-top: 1px solid var(--color-line); padding-top: 14px; }
.sub-metrics span { display: flex; align-items: baseline; justify-content: center; gap: 8px; color: var(--color-text-muted); font-size: 13px; }
.sub-metrics span + span { border-left: 1px solid var(--color-line); }
.sub-metrics b { color: var(--color-text); font-size: 18px; }
.section-heading { margin: 20px 4px 10px; font-size: 17px; }
.shortcut-grid { display: grid; grid-template-columns: repeat(4, 1fr); padding: 10px; }
.shortcut-item { position: relative; min-height: 102px; display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 9px; color: var(--color-text); border-radius: var(--radius); }
.shortcut-item:hover { background: #fafafa; }
.shortcut-icon { width: 44px; height: 44px; display: inline-flex; align-items: center; justify-content: center; color: var(--color-primary); background: var(--color-primary-bg); border-radius: var(--radius-lg); }
.shortcut-item strong { font-size: 13px; font-weight: 600; text-align: center; }
.shortcut-badge { position: absolute; top: 9px; left: calc(50% + 13px); min-width: 18px; height: 18px; padding: 0 4px; display: inline-flex; align-items: center; justify-content: center; color: #fff; background: var(--color-error); border-radius: 9px; font-size: 10px; font-weight: 700; }
@media (max-width: 768px) {
  .dashboard-page { padding-top: max(14px, env(safe-area-inset-top)); }
  .shop-summary { padding: 16px; }
  .shortcut-grid { grid-template-columns: repeat(2, 1fr); padding: 8px; }
  .shortcut-item { min-height: 98px; }
  .data-card > strong { font-size: 30px; }
}
</style>
