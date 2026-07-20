<template>
  <div class="pagination" v-if="totalPages > 1">
    <button class="btn btn-ghost btn-sm" :disabled="page <= 1" @click="$emit('change', page - 1)">上一页</button>
    <span class="pagination-info">第 {{ page }} / {{ totalPages }} 页</span>
    <button class="btn btn-ghost btn-sm" :disabled="page >= totalPages" @click="$emit('change', page + 1)">下一页</button>
  </div>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  page: { type: Number, required: true },
  pageSize: { type: Number, required: true },
  total: { type: Number, required: true }
})

defineEmits(['change'])

const totalPages = computed(() => Math.max(1, Math.ceil(props.total / props.pageSize)))
</script>

<style scoped>
.pagination {
  display: flex; align-items: center; justify-content: center; gap: 16px;
  padding: 16px 0;
}
.pagination-info {
  font-size: 13px; color: var(--color-text-muted);
}
</style>
