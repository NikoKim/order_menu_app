<template>
  <nav v-if="totalPages > 1" class="pagination" aria-label="分页">
    <span class="pagination-summary">第 {{ modelValue }} / {{ totalPages }} 页<span v-if="totalItems != null"> · 共 {{ totalItems }} 条</span></span>
    <div class="pagination-actions">
      <button type="button" title="上一页" :disabled="modelValue <= 1" @click="select(modelValue - 1)"><ChevronLeft :size="16" /></button>
      <template v-for="item in pageItems" :key="item.key">
        <span v-if="item.ellipsis" class="pagination-ellipsis">...</span>
        <button v-else type="button" :class="{ active: modelValue === item.page }" :aria-current="modelValue === item.page ? 'page' : undefined" @click="select(item.page)">{{ item.page }}</button>
      </template>
      <button type="button" title="下一页" :disabled="modelValue >= totalPages" @click="select(modelValue + 1)"><ChevronRight :size="16" /></button>
    </div>
  </nav>
</template>

<script setup>
import { computed } from 'vue'
import { ChevronLeft, ChevronRight } from 'lucide-vue-next'

const props = defineProps({ modelValue: { type: Number, default: 1 }, totalPages: { type: Number, default: 1 }, totalItems: { type: Number, default: null } })
const emit = defineEmits(['update:modelValue'])

const pageItems = computed(() => {
  const pages = props.totalPages
  if (pages <= 7) return Array.from({ length: pages }, (_, index) => ({ key: `p-${index + 1}`, page: index + 1 }))
  const selected = new Set([1, pages, props.modelValue - 1, props.modelValue, props.modelValue + 1].filter(page => page >= 1 && page <= pages))
  const values = [...selected].sort((a, b) => a - b)
  const result = []
  values.forEach((page, index) => {
    if (index && page - values[index - 1] > 1) result.push({ key: `e-${page}`, ellipsis: true })
    result.push({ key: `p-${page}`, page })
  })
  return result
})

function select(page) {
  emit('update:modelValue', Math.min(props.totalPages, Math.max(1, page)))
}
</script>

<style scoped>
.pagination{display:flex;align-items:center;justify-content:space-between;gap:12px;margin-top:14px;padding:9px 10px;border:1px solid #dfe5e1;border-radius:7px;background:#f8faf9}.pagination-summary{color:#68756e;font-size:11px}.pagination-actions{display:flex;align-items:center;gap:4px}.pagination-actions button{width:32px;height:32px;display:grid;place-items:center;border:1px solid transparent;border-radius:5px;color:#506059;background:transparent;font-size:11px;font-weight:700}.pagination-actions button:hover:not(:disabled):not(.active){color:#285f45;border-color:#bdd5c7;background:#fff}.pagination-actions button.active,.pagination-actions button.active:hover{color:#fff;border-color:#236b49;background:#236b49;box-shadow:0 2px 6px rgba(35,107,73,.18)}.pagination-actions button:disabled{opacity:.32;cursor:not-allowed}.pagination-ellipsis{width:20px;color:#8a948f;text-align:center;font-size:11px}@media(max-width:520px){.pagination{align-items:flex-start;flex-direction:column}.pagination-actions{width:100%;justify-content:flex-end;overflow-x:auto}}
</style>
