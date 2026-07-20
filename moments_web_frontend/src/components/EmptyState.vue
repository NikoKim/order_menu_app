<template>
  <div class="empty-state">
    <div class="empty-icon" aria-hidden="true">
      <component :is="iconComponent" :size="42" :stroke-width="1.6" />
    </div>
    <p class="empty-title">{{ title }}</p>
    <p class="empty-desc" v-if="description">{{ description }}</p>
    <button v-if="actionText" class="btn btn-primary btn-sm" @click="$emit('action')">{{ actionText }}</button>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { FileText, Inbox, MessageCircle, Store, TriangleAlert, Utensils } from 'lucide-vue-next'

const props = defineProps({
  icon: { type: String, default: 'inbox' },
  title: { type: String, default: '暂无数据' },
  description: { type: String, default: '' },
  actionText: { type: String, default: '' }
})

defineEmits(['action'])

const iconComponent = computed(() => {
  const icons = {
    warning: TriangleAlert,
    message: MessageCircle,
    document: FileText,
    store: Store,
    dish: Utensils,
    inbox: Inbox
  }
  return icons[props.icon] || Inbox
})
</script>

<style scoped>
.empty-state {
  display: flex; flex-direction: column; align-items: center; justify-content: center;
  padding: 48px 24px; text-align: center;
}
.empty-icon {
  width: 72px; height: 72px; margin-bottom: 14px; border-radius: 50%;
  display: flex; align-items: center; justify-content: center;
  color: var(--color-text-muted); background: var(--color-bg);
}
.empty-title { font-size: 16px; font-weight: 600; color: var(--color-text); margin-bottom: 4px; }
.empty-desc { font-size: 13px; color: var(--color-text-muted); margin-bottom: 16px; }

@media (max-width: 768px) {
  .empty-state { padding: 40px 20px; }
  .empty-icon { width: 64px; height: 64px; }
}
</style>
