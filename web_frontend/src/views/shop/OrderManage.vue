<template>
  <div class="page-container">
    <div class="page-header">
      <button class="back-btn" @click="$router.back()"><ArrowLeft :size="20" /></button>
      <h1 class="page-title">订单管理</h1>
    </div>

    <div class="filters" role="tablist">
      <button v-for="item in statusFilters" :key="item.value" :class="{ active: filterStatus === item.value }" @click="filterStatus = item.value; page = 1; loadOrders()">{{ item.label }}</button>
    </div>

    <LoadingSpinner v-if="loading" />
    <div v-else-if="error" class="error-wrap">
      <EmptyState icon="warning" :title="error" action-text="重试" @action="loadOrders" />
    </div>
    <template v-else>
      <div class="order-list-cards" v-if="orders.length">
        <div v-for="order in orders" :key="order.id" class="card order-card"
          @click="router.push(`/shop/orders/${order.id}`)">
          <div class="order-card-top">
            <div>
              <div class="order-no">{{ order.orderNo }}</div>
              <div class="order-time">{{ order.createdAt?.slice(0, 16) }}</div>
            </div>
            <StatusBadge :status="order.status" type="order" />
          </div>
          <div class="order-card-bottom">
            <span class="order-amount">{{ order.totalAmount || '--' }}</span>
            <div class="order-actions" @click.stop>
              <button v-if="order.status === 'SUBMITTED'" class="btn btn-primary btn-sm" @click="handleAction(order, 'accept')">接单</button>
              <button v-if="order.status === 'SUBMITTED'" class="btn btn-outline btn-sm" @click="handleAction(order, 'reject')">拒单</button>
              <button v-if="order.status === 'ACCEPTED'" class="btn btn-primary btn-sm" @click="handleAction(order, 'complete')">完成</button>
            </div>
          </div>
        </div>
      </div>
      <EmptyState v-else icon="document" title="暂无订单" />
      <Pagination :page="page" :page-size="pageSize" :total="total" @change="p => { page = p; loadOrders() }" />
    </template>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ArrowLeft } from 'lucide-vue-next'
import { getShopOrders, acceptOrder, rejectOrder, completeOrder } from '../../api/shop'
import StatusBadge from '../../components/StatusBadge.vue'
import Pagination from '../../components/Pagination.vue'
import EmptyState from '../../components/EmptyState.vue'
import LoadingSpinner from '../../components/LoadingSpinner.vue'

const router = useRouter()
const orders = ref([])
const loading = ref(true)
const error = ref('')
const page = ref(1)
const pageSize = ref(20)
const total = ref(0)
const filterStatus = ref('')
const statusFilters = [
  { value: '', label: '全部' }, { value: 'SUBMITTED', label: '待接单' },
  { value: 'ACCEPTED', label: '已接单' }, { value: 'COMPLETED', label: '已完成' },
  { value: 'CANCELLED', label: '已取消' }, { value: 'REJECTED', label: '已拒绝' }
]

async function loadOrders() {
  loading.value = true
  error.value = ''
  try {
    const res = await getShopOrders({ page: page.value, pageSize: pageSize.value, status: filterStatus.value || undefined })
    orders.value = res.records || []
    total.value = res.total || 0
  } catch (e) {
    error.value = e.message
  } finally { loading.value = false }
}

async function handleAction(order, action) {
  try {
    if (action === 'accept') await acceptOrder(order.id)
    else if (action === 'reject') await rejectOrder(order.id)
    else if (action === 'complete') await completeOrder(order.id)
    loadOrders()
  } catch (e) { alert(e.message) }
}

onMounted(loadOrders)
</script>

<style scoped>
.page-header { display: flex; align-items: center; gap: 10px; margin-bottom: 16px; }
.back-btn { width: 36px; height: 36px; display: inline-flex; align-items: center; justify-content: center; border-radius: 50%; background: transparent; color: var(--color-text); }
.page-container { max-width: 720px; }
.filters { display: flex; overflow-x: auto; margin: 0 -16px 16px; padding: 0 16px; border-bottom: 1px solid var(--color-line); scrollbar-width: none; }
.filters::-webkit-scrollbar { display: none; }
.filters button { flex: 0 0 auto; min-height: 44px; padding: 0 15px; background: transparent; color: var(--color-text-muted); border-bottom: 2px solid transparent; font-size: 14px; }
.filters button.active { color: var(--color-primary); border-bottom-color: var(--color-primary); font-weight: 700; }
.error-wrap { min-height: 30vh; display: flex; align-items: center; justify-content: center; }
.order-list-cards { display: flex; flex-direction: column; gap: 12px; }
.order-card { cursor: pointer; }
.order-card-top { display: flex; justify-content: space-between; align-items: flex-start; margin-bottom: 10px; }
.order-no { font-weight: 700; font-size: 15px; }
.order-time { font-size: 12px; color: var(--color-text-light); margin-top: 2px; }
.order-card-bottom { display: flex; justify-content: space-between; align-items: center; }
.order-amount { font-size: 18px; font-weight: 800; color: var(--color-primary); }
.order-actions { display: flex; gap: 8px; }
</style>
