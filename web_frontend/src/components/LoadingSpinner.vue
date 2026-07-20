<template>
  <div class="loading-spinner" :class="{ fullscreen: fullscreen }">
    <div class="spinner"></div>
    <p class="loading-text" v-if="text">{{ text }}</p>
  </div>
</template>

<script setup>
defineProps({ fullscreen: Boolean, text: { type: String, default: '' } })
</script>

<style scoped>
.loading-spinner {
  display: flex; flex-direction: column; align-items: center; justify-content: center;
  padding: 32px; gap: 12px;
}
.loading-spinner.fullscreen {
  position: fixed; inset: 0; background: rgba(255,255,255,0.8); z-index: 1000;
}
.spinner {
  width: 32px; height: 32px;
  border: 3px solid var(--color-line);
  border-top-color: var(--color-primary);
  border-radius: 50%;
  animation: spin 0.7s linear infinite;
}
@keyframes spin { to { transform: rotate(360deg); } }
.loading-text { font-size: 13px; color: var(--color-text-muted); }
</style>
