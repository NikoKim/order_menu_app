<template>
  <div class="admin-layout">
    <header class="admin-header">
      <div class="admin-header-inner">
        <router-link to="/admin/users" class="admin-logo"><Settings :size="19" />管理后台</router-link>
        <nav class="admin-nav" v-if="isLoggedIn">
          <router-link to="/admin/users" :class="{ active: route.path.startsWith('/admin/users') }">用户管理</router-link>
        </nav>
        <button v-if="isLoggedIn" class="logout-btn" @click="handleLogout">退出</button>
      </div>
    </header>
    <main class="admin-main">
      <router-view />
    </main>
  </div>
</template>

<script setup>
import { useRoute, useRouter } from 'vue-router'
import { Settings } from 'lucide-vue-next'
import { useAuthStore } from '../stores/auth'
import { onMounted } from 'vue'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const isLoggedIn = auth.isLoggedIn

onMounted(() => auth.restoreSession())

function handleLogout() {
  auth.logout()
  router.push('/admin/login')
}
</script>

<style scoped>
.admin-layout { min-height: 100vh; display: flex; flex-direction: column; background: var(--color-bg); }
.admin-header {
  background: #fff; border-bottom: 1px solid var(--color-line);
  position: sticky; top: 0; z-index: 100;
  box-shadow: 0 1px 4px rgba(0,0,0,0.04);
}
.admin-header-inner {
  max-width: 1200px; margin: 0 auto; padding: 0 16px;
  display: flex; align-items: center; height: 56px; gap: 24px;
}
.admin-logo { font-size: 17px; font-weight: 800; color: var(--color-text); white-space: nowrap; }
.admin-nav { display: flex; gap: 2px; flex: 1; }
.admin-nav a {
  padding: 8px 14px; border-radius: var(--radius);
  font-size: 14px; font-weight: 500; color: var(--color-text-muted);
  white-space: nowrap; transition: all 0.2s;
}
.admin-nav a:hover { color: var(--color-primary); background: rgba(242,140,40,0.06); }
.admin-nav a.active { color: var(--color-primary); background: var(--color-primary-bg); font-weight: 700; }
.admin-main { flex: 1; }
.logout-btn {
  margin-left: auto; flex-shrink: 0; padding: 7px 16px;
  border: 1px solid var(--color-line); background: #fff;
  border-radius: var(--radius); font-size: 13px; color: var(--color-text-muted);
  cursor: pointer; transition: all 0.2s;
}
.logout-btn:hover { background: var(--color-bg); color: var(--color-error); border-color: var(--color-error); }
</style>
