<template>
  <div class="page-container">
    <div class="page-header">
      <button class="back-btn" @click="$router.back()"><ArrowLeft :size="20" /></button>
      <h1 class="page-title">确认订单</h1>
    </div>

    <div v-if="cart.count === 0" class="empty-wrap">
      <EmptyState title="购物车为空" action-text="去逛逛" @action="$router.back()" />
    </div>

    <template v-else>
      <div class="card" style="margin-bottom:12px">
        <div class="section-title">商品列表</div>
        <div v-for="item in cart.items" :key="item._key" class="checkout-item">
          <div class="checkout-item-info">
            <div class="ci-name">{{ item.name }}</div>
            <div class="ci-opts" v-if="item.selectedOptions?.length">
              {{ item.selectedOptions.map(o => o.name).join('、') }}
            </div>
          </div>
          <div class="ci-qty">x{{ item.quantity }}</div>
          <div class="ci-price">{{ ((item.price + (item.selectedOptions?.reduce((s, o) => s + o.priceDelta, 0) || 0)) * item.quantity).toFixed(2) }}</div>
        </div>
        <div class="checkout-total">
          <span>合计</span>
          <span class="total-amount">{{ cart.totalAmount.toFixed(2) }}</span>
        </div>
      </div>

      <div class="card" style="margin-bottom:12px">
        <div class="form-group">
          <label class="form-label">备注</label>
          <textarea v-model="remark" class="form-textarea" maxlength="500" placeholder="如有特殊需求请备注..."></textarea>
        </div>
      </div>

      <p v-if="error" class="form-error" style="text-align:center;margin-bottom:12px">{{ error }}</p>

      <button class="btn btn-primary btn-block btn-lg" :disabled="submitting" @click="handleSubmit">
        {{ submitting ? '提交中...' : '提交订单' }}
      </button>
    </template>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { ArrowLeft } from 'lucide-vue-next'
import { createOrder } from '../../api/customer'
import { useCartStore } from '../../stores/cart'
import EmptyState from '../../components/EmptyState.vue'

const router = useRouter()
const cart = useCartStore()
const remark = ref('')
const submitting = ref(false)
const error = ref('')
const pendingOrderKey = 'order-menu:pending-order-request'
const pendingOrderTtlMs = 30 * 60 * 1000

function newClientRequestId() {
  return globalThis.crypto?.randomUUID?.()
    ?? `${Date.now()}-${Math.random().toString(16).slice(2)}`
}

function idempotencyFor(payload) {
  const fingerprint = JSON.stringify(payload)
  try {
    const pending = JSON.parse(localStorage.getItem(pendingOrderKey) || 'null')
    if (pending?.fingerprint === fingerprint
      && Date.now() - Number(pending.createdAt) < pendingOrderTtlMs) {
      return { clientRequestId: pending.clientRequestId, fingerprint }
    }
  } catch {
    // Ignore corrupt browser state and replace it with a fresh request.
  }
  const clientRequestId = newClientRequestId()
  try {
    localStorage.setItem(pendingOrderKey, JSON.stringify({ clientRequestId, fingerprint, createdAt: Date.now() }))
  } catch {
    // Browser storage can be unavailable; the in-flight request still remains idempotent.
  }
  return { clientRequestId, fingerprint }
}

async function handleSubmit() {
  error.value = ''
  submitting.value = true
  try {
    const payload = {
      shopId: cart.shopId,
      remark: remark.value.trim() || undefined,
      items: cart.items.map(i => ({
        dishId: i.dishId,
        quantity: i.quantity,
        optionItemIds: i.optionItemIds || []
      }))
    }
    const pending = idempotencyFor(payload)
    await createOrder({ ...payload, clientRequestId: pending.clientRequestId })
    try {
      const stored = JSON.parse(localStorage.getItem(pendingOrderKey) || 'null')
      if (stored?.clientRequestId === pending.clientRequestId) {
        localStorage.removeItem(pendingOrderKey)
      }
    } catch {
      // Order succeeded; stale browser state can expire naturally.
    }
    cart.clearCart()
    router.push('/customer/orders')
  } catch (e) {
    error.value = e.message
  } finally {
    submitting.value = false
  }
}
</script>

<style scoped>
.page-header { display: flex; align-items: center; gap: 10px; margin-bottom: 16px; }
.back-btn { width: 36px; height: 36px; display: inline-flex; align-items: center; justify-content: center; border-radius: 50%; background: transparent; color: var(--color-text); }
.page-container { max-width: 720px; }
.empty-wrap { min-height: 40vh; display: flex; align-items: center; justify-content: center; }
.section-title { font-size: 15px; font-weight: 700; margin-bottom: 10px; }
.checkout-item { display: flex; align-items: center; gap: 10px; padding: 12px 0; border-bottom: 1px solid var(--color-line); }
.checkout-item:last-child { border-bottom: none; }
.checkout-item-info { flex: 1; }
.ci-name { font-size: 14px; font-weight: 600; }
.ci-opts { font-size: 12px; color: var(--color-text-light); margin-top: 2px; }
.ci-qty { font-size: 14px; color: var(--color-text-muted); }
.ci-price { font-weight: 700; color: var(--color-primary); font-size: 15px; }
.checkout-total { display: flex; justify-content: flex-end; align-items: center; gap: 12px; padding-top: 12px; border-top: 2px solid var(--color-line); font-size: 16px; font-weight: 700; }
.total-amount { font-size: 22px; font-weight: 800; color: var(--color-primary); }
</style>
