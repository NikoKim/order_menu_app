<template>
  <div class="page-container">
    <div class="page-header">
      <button class="back-btn" @click="$router.back()"><ArrowLeft :size="20" /></button>
      <h1 class="page-title">我的订单</h1>
    </div>

    <LoadingSpinner v-if="loading" />
    <div v-else-if="error" class="error-wrap"><EmptyState icon="warning" :title="error" action-text="重试" @action="loadOrders" /></div>
    <template v-else>
      <div v-if="orders.length" class="order-list-cards">
        <div v-for="order in orders" :key="order.id" class="card order-card"
          @click="router.push(`/customer/orders/${order.id}`)">
          <div class="order-top">
            <div>
              <div class="order-no">{{ order.shopName }}</div>
              <div class="order-meta">{{ order.createdAt?.replace('T', ' ').slice(0, 16) }}</div>
            </div>
            <StatusBadge :status="order.status" type="order" />
          </div>
          <div class="order-id"><span>订单号</span><b>{{ order.orderNo }}</b></div>
          <div class="order-bottom">
            <span class="amount-label">订单金额</span>
            <span class="order-amount">¥{{ Number(order.totalAmount || 0).toFixed(2) }}</span>
            <button v-if="order.status === 'SUBMITTED'" class="btn btn-outline btn-sm" @click.stop="handleCancel(order)">取消</button>
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
import { getMyOrders, cancelOrder } from '../../api/customer'
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

async function loadOrders() {
  loading.value = true
  error.value = ''
  try {
    const res = await getMyOrders({ page: page.value, pageSize: pageSize.value })
    orders.value = res.records || []
    total.value = res.total || 0
  } catch (e) { error.value = e.message }
  finally { loading.value = false }
}

async function handleCancel(order) {
  if (!confirm('确认取消此订单？')) return
  try { await cancelOrder(order.id); loadOrders() } catch (e) { alert(e.message) }
}

onMounted(loadOrders)
</script>

<style scoped>
.page-header { display: flex; align-items: center; gap: 10px; margin-bottom: 16px; }
.back-btn { width: 36px; height: 36px; display: inline-flex; align-items: center; justify-content: center; border-radius: 50%; background: transparent; color: var(--color-text); }
.page-container { max-width: 720px; }
.error-wrap { min-height: 30vh; display: flex; align-items: center; justify-content: center; }
.order-list-cards { display: flex; flex-direction: column; gap: 12px; }
.order-card { cursor: pointer; }
.order-top { display: flex; justify-content: space-between; align-items: flex-start; margin-bottom: 10px; }
.order-no { font-weight: 700; font-size: 15px; }
.order-meta { font-size: 12px; color: var(--color-text-light); margin-top: 2px; }
.order-id { display: flex; align-items: center; gap: 12px; padding: 10px 0; border-top: 1px solid var(--color-line); border-bottom: 1px solid var(--color-line); font-size: 12px; }
.order-id span, .amount-label { color: var(--color-text-muted); }
.order-id b { min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.order-bottom { display: flex; justify-content: flex-end; align-items: center; gap: 8px; padding-top: 12px; }
.order-amount { font-size: 18px; font-weight: 800; color: var(--color-primary); }
@media (max-width: 768px) { .order-card { padding: 14px; } }
</style>
