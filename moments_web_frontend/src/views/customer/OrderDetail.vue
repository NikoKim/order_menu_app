<template>
  <div class="page-container">
    <div class="page-header">
      <button class="back-btn" @click="$router.back()"><ArrowLeft :size="20" /></button>
      <h1 class="page-title">订单详情</h1>
    </div>

    <LoadingSpinner v-if="loading" />
    <div v-else-if="error" class="error-wrap"><EmptyState icon="warning" :title="error" action-text="重试" @action="loadDetail" /></div>
    <template v-else-if="order">
      <div class="card" style="margin-bottom:12px">
        <div class="detail-header">
          <div>
            <div class="detail-no">{{ order.orderNo }}</div>
            <div class="detail-meta">{{ order.shopName }} · {{ order.createdAt?.slice(0, 16) }}</div>
          </div>
          <StatusBadge :status="order.status" type="order" />
        </div>
      </div>

      <div class="card" style="margin-bottom:12px">
        <div class="section-title">商品详情</div>
        <div v-for="item in order.items" :key="item.dishId" class="order-item">
          <div>
            <div class="item-name">{{ item.dishName }} x{{ item.quantity }}</div>
            <div class="item-opts" v-if="item.selectedOptions">{{ item.selectedOptions }}</div>
          </div>
          <span class="item-subtotal">{{ item.subtotalAmount }}</span>
        </div>
        <div class="order-total">
          <span>合计</span>
          <span class="total-amount">{{ order.totalAmount }}</span>
        </div>
      </div>

      <div class="card" v-if="order.remark" style="margin-bottom:12px">
        <div class="section-title">备注</div>
        <p style="font-size:14px">{{ order.remark }}</p>
      </div>

      <button class="btn btn-outline btn-block btn-lg share-order" @click="$router.push(`/customer/orders/${order.id}/share`)"><Share2 :size="18" />分享订单</button>

      <button v-if="order.status === 'SUBMITTED'" class="btn btn-outline btn-block btn-lg"
        style="color:var(--color-error);border-color:var(--color-error)" @click="handleCancel">取消订单</button>
    </template>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { ArrowLeft, Share2 } from 'lucide-vue-next'
import { getOrderDetail, cancelOrder } from '../../api/customer'
import StatusBadge from '../../components/StatusBadge.vue'
import LoadingSpinner from '../../components/LoadingSpinner.vue'
import EmptyState from '../../components/EmptyState.vue'
import { confirmNotice, notifyError } from '../../services/notice'

const route = useRoute()
const order = ref(null)
const loading = ref(true)
const error = ref('')

async function loadDetail() {
  loading.value = true
  error.value = ''
  try { order.value = await getOrderDetail(route.params.id) }
  catch (e) { error.value = e.message }
  finally { loading.value = false }
}

async function handleCancel() {
  if (!await confirmNotice('确认取消此订单？')) return
  try { await cancelOrder(order.value.id); loadDetail() } catch (e) { notifyError(e.message) }
}

onMounted(loadDetail)
</script>

<style scoped>
.page-header { display: flex; align-items: center; gap: 10px; margin-bottom: 16px; }
.back-btn { width: 36px; height: 36px; display: inline-flex; align-items: center; justify-content: center; border-radius: 50%; background: transparent; color: var(--color-text); }
.page-container { max-width: 720px; }
.error-wrap { min-height: 30vh; display: flex; align-items: center; justify-content: center; }
.share-order { margin-bottom: 10px; }
.detail-header { display: flex; justify-content: space-between; align-items: flex-start; }
.detail-no { font-size: 18px; font-weight: 800; }
.detail-meta { font-size: 12px; color: var(--color-text-light); margin-top: 2px; }
.section-title { font-size: 15px; font-weight: 700; margin-bottom: 10px; color: var(--color-text-muted); }
.order-item { display: flex; justify-content: space-between; padding: 10px 0; border-bottom: 1px solid var(--color-line); }
.order-item:last-child { border-bottom: none; }
.item-name { font-size: 14px; font-weight: 600; }
.item-opts { font-size: 12px; color: var(--color-text-muted); margin-top: 4px; }
.item-subtotal { font-size: 14px; font-weight: 600; color: var(--color-primary); }
.order-total { display: flex; justify-content: flex-end; align-items: center; gap: 12px; padding-top: 12px; border-top: 2px solid var(--color-line); margin-top: 8px; font-size: 16px; }
.total-amount { font-size: 20px; font-weight: 800; color: var(--color-primary); }
</style>
