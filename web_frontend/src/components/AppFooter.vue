<template>
  <footer class="app-footer">
    <p>2026 @NikoKim NJU {{ version }} <router-link :to="logPath">开发日志</router-link></p>
    <p>更多反馈 Email: nju_zsj030702@163.com</p>
  </footer>
</template>
<script setup>
const role = JSON.parse(localStorage.getItem('user') || '{}')?.role
const version = role === 'SHOP' ? 'v3.1.1' : 'v2.1.1'
const logPath = role === 'SHOP' ? '/shop/development-log' : '/customer/development-log'
</script>
<style scoped>
.app-footer { padding: 20px 8px 10px; color: var(--color-text-muted); text-align: center; font-size: 11px; }.app-footer p + p { margin-top: 2px; }.app-footer a { font-weight: 700; }
</style>
