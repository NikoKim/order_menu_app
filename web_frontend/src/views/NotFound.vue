<template>
  <div class="not-found">
    <FileQuestion :size="64" stroke-width="1" />
    <h1>404</h1>
    <p>页面不存在</p>
    <router-link to="/" class="btn btn-primary">返回首页</router-link>
  </div>
</template>

<script setup>
import { FileQuestion } from 'lucide-vue-next'
</script>

<style scoped>
.not-found {
  display: flex; flex-direction: column; align-items: center; justify-content: center;
  min-height: 60vh; gap: 16px;
}
h1 { font-size: 64px; color: var(--color-primary); font-weight: 800; }
p { font-size: 16px; color: var(--color-text-muted); }
</style>
