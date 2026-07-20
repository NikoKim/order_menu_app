<template>
  <span class="badge" :class="badgeClass">{{ label }}</span>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  status: { type: String, required: true },
  type: { type: String, default: 'order' }
})

const STATUS_MAP = {
  order: {
    SUBMITTED: { cls: 'badge-gray', label: '待接单' },
    ACCEPTED: { cls: 'badge-orange', label: '已接单' },
    COMPLETED: { cls: 'badge-green', label: '已完成' },
    CANCELLED: { cls: 'badge-red', label: '已取消' },
    REJECTED: { cls: 'badge-red', label: '已拒绝' }
  },
  dish: {
    ON_SALE: { cls: 'badge-green', label: '在售' },
    OFF_SALE: { cls: 'badge-gray', label: '下架' }
  },
  shop: {
    OPEN: { cls: 'badge-green', label: '营业中' },
    CLOSED: { cls: 'badge-gray', label: '休息中' }
  },
  feedback: {
    PENDING: { cls: 'badge-orange', label: '待回复' },
    REPLIED: { cls: 'badge-green', label: '已回复' },
    CLOSED: { cls: 'badge-gray', label: '已关闭' }
  },
  family: {
    APPROVED: { cls: 'badge-green', label: '已通过' },
    PENDING: { cls: 'badge-orange', label: '待审核' },
    LEFT: { cls: 'badge-gray', label: '已退出' },
    REMOVED: { cls: 'badge-gray', label: '已移出' }
  },
  user: {
    1: { cls: 'badge-green', label: '启用' },
    0: { cls: 'badge-red', label: '禁用' }
  }
}

const badgeClass = computed(() => {
  const map = STATUS_MAP[props.type]
  return (map && map[props.status]) ? map[props.status].cls : 'badge-gray'
})

const label = computed(() => {
  const map = STATUS_MAP[props.type]
  if (map && map[props.status]) return map[props.status].label
  return props.status
})
</script>
