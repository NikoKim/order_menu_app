<template>
  <div class="profile-page page-container">
    <h1 class="page-title profile-title">我的</h1>

    <LoadingSpinner v-if="loading" />
    <template v-else>
      <section class="profile-card">
        <div class="profile-avatar">
          <img v-if="user?.avatarUrl" :src="imageUrl(user.avatarUrl)" alt="" />
          <span v-else>{{ user?.nickname?.charAt(0) || '?' }}</span>
        </div>
        <div class="profile-info">
          <h2>{{ user?.nickname || '用户' }}</h2>
          <p>已登录</p>
        </div>
        <button class="logout-icon" title="退出登录" @click="handleLogout"><LogOut :size="20" /></button>
      </section>

      <nav class="profile-menu" aria-label="个人中心功能">
        <router-link v-for="item in menuItems" :key="item.title" :to="item.to" class="menu-row">
          <span class="menu-icon"><component :is="item.icon" :size="21" /></span>
          <span class="menu-copy">
            <strong>{{ item.title }}</strong>
            <small>{{ item.subtitle }}</small>
          </span>
          <ChevronRight :size="18" class="menu-chevron" />
        </router-link>
      </nav>
      <AppFooter />
    </template>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import {
  Bell, ChevronRight, FilePenLine, House, LockKeyhole, LogOut,
  MessageSquareText, Newspaper, ReceiptText
} from 'lucide-vue-next'
import { useAuthStore } from '../../stores/auth'
import { getCurrentUser } from '../../api/auth'
import { imageUrl } from '../../composables/useImage'
import LoadingSpinner from '../../components/LoadingSpinner.vue'
import AppFooter from '../../components/AppFooter.vue'

const router = useRouter()
const auth = useAuthStore()
const user = ref(null)
const loading = ref(true)

const menuItems = [
  { icon: Newspaper, title: '我的动态', subtitle: '浏览、编辑已发布动态和草稿', to: '/customer/my-moments' },
  { icon: Bell, title: '消息通知', subtitle: '查看订单、家庭和反馈处理提醒', to: '/customer/notifications' },
  { icon: LockKeyhole, title: '隐私设置', subtitle: '管理动态和个人信息隐私', to: '/customer/privacy' },
  { icon: ReceiptText, title: '我的订单', subtitle: '查看点餐记录与订单状态', to: '/customer/orders' },
  { icon: FilePenLine, title: '修改个人信息', subtitle: '更新昵称、手机号等资料', to: '/customer/profile/edit' },
  { icon: MessageSquareText, title: '留言建议', subtitle: '告诉商家或平台你的用餐体验', to: '/customer/feedback' },
  { icon: House, title: '我的家庭', subtitle: '输入家庭号，加入或退出家庭', to: '/customer/families' }
]

async function loadUser() {
  try { user.value = await getCurrentUser() } catch { }
  finally { loading.value = false }
}

function handleLogout() {
  auth.logout()
  router.push('/customer/home')
}

onMounted(loadUser)
</script>

<style scoped>
.profile-page { max-width: 720px; }
.profile-title { margin: 4px 0 14px; }
.profile-card {
  display: flex; align-items: center; gap: 14px; padding: 18px;
  background: #fff; border-radius: var(--radius-lg); border: 1px solid var(--color-line-light);
}
.profile-avatar {
  width: 64px; height: 64px; border-radius: 50%; flex: 0 0 auto;
  background: var(--color-primary-bg); color: var(--color-primary);
  display: flex; align-items: center; justify-content: center;
  font-size: 26px; font-weight: 700; overflow: hidden;
}
.profile-avatar img { width: 100%; height: 100%; object-fit: cover; }
.profile-info { flex: 1; min-width: 0; }
.profile-info h2 { font-size: 18px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.profile-info p { margin-top: 4px; font-size: 12px; color: var(--color-success); }
.logout-icon {
  width: 40px; height: 40px; display: inline-flex; align-items: center; justify-content: center;
  color: var(--color-text-muted); background: transparent; border-radius: var(--radius);
}
.logout-icon:hover { color: var(--color-error); background: var(--color-error-light); }
.profile-menu { margin-top: 14px; overflow: hidden; background: #fff; border-radius: var(--radius-lg); border: 1px solid var(--color-line-light); }
.menu-row { display: flex; align-items: center; min-height: 70px; padding: 10px 16px; color: var(--color-text); }
.menu-row + .menu-row { border-top: 1px solid var(--color-line); }
.menu-row:hover { background: #fafafa; }
.menu-icon { width: 38px; height: 38px; flex: 0 0 auto; display: inline-flex; align-items: center; justify-content: center; color: var(--color-primary); background: var(--color-primary-bg); border-radius: var(--radius); }
.menu-copy { flex: 1; min-width: 0; margin-left: 12px; display: flex; flex-direction: column; }
.menu-copy strong { font-size: 14px; }
.menu-copy small { margin-top: 2px; color: var(--color-text-muted); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.menu-chevron { color: var(--color-text-light); flex: 0 0 auto; }

@media (max-width: 768px) {
  .profile-page { padding-top: max(16px, env(safe-area-inset-top)); }
  .profile-title { font-size: 20px; }
  .profile-card, .profile-menu { border-radius: var(--radius-lg); }
}
</style>
