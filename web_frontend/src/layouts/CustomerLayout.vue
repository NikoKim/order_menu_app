<template>
  <div class="customer-layout" :class="{ 'has-mobile-nav': showMobileNav, 'has-mobile-subheader': showMobileSubheader }">
    <header class="cust-header">
      <div class="cust-header-inner">
        <router-link to="/customer/home" class="cust-logo" aria-label="香狗点餐首页">
          <img src="/app_icon.png" alt="" />
          <span>香狗点餐</span>
        </router-link>
        <nav class="cust-nav" aria-label="客户主导航">
          <router-link to="/customer/home" :class="{ active: isActive('/customer/home') }">首页</router-link>
          <router-link v-if="isLoggedIn" to="/customer/chat" :class="{ active: isActive('/customer/chat') || isActive('/customer/moments') }">聊天</router-link>
          <router-link v-if="isLoggedIn" to="/customer/orders" :class="{ active: isActive('/customer/orders') }">订单</router-link>
          <router-link v-if="isLoggedIn" to="/customer/feedback" :class="{ active: isActive('/customer/feedback') }">留言</router-link>
          <router-link v-if="isLoggedIn" to="/customer/families" :class="{ active: isActive('/customer/families') }">家庭</router-link>
        </nav>
        <div class="cust-actions">
          <router-link v-if="!isLoggedIn" to="/customer/login" class="login-btn">登录</router-link>
          <router-link v-else to="/customer/profile" class="profile-btn">
            <UserRound :size="16" aria-hidden="true" />
            {{ auth.userName || '我的' }}
          </router-link>
        </div>
      </div>
    </header>

    <header v-if="showMobileSubheader" class="mobile-subheader">
      <button title="返回" @click="$router.back()"><ArrowLeft :size="22" /></button>
      <h1>{{ mobileTitle }}</h1>
    </header>

    <main class="cust-main">
      <router-view />
    </main>

    <nav v-if="showMobileNav" class="mobile-tabbar" aria-label="客户主导航">
      <router-link to="/customer/home" :class="{ active: isActive('/customer/home') }">
        <Store :size="23" :stroke-width="isActive('/customer/home') ? 2.5 : 2" />
        <span>首页</span>
      </router-link>
      <router-link v-if="isLoggedIn" to="/customer/chat" :class="{ active: isActive('/customer/chat') }">
        <MessageCircle :size="23" :stroke-width="isActive('/customer/chat') ? 2.5 : 2" />
        <span>聊天</span>
      </router-link>
      <router-link :to="isLoggedIn ? '/customer/profile' : '/customer/login'" :class="{ active: isLoggedIn ? isActive('/customer/profile') : (isActive('/customer/login') || isActive('/customer/register')) }">
        <UserRound v-if="isLoggedIn" :size="23" :stroke-width="isActive('/customer/profile') ? 2.5 : 2" />
        <LogIn v-else :size="23" :stroke-width="isActive('/customer/login') ? 2.5 : 2" />
        <span>{{ isLoggedIn ? '我的' : '登录' }}</span>
      </router-link>
    </nav>
  </div>
</template>

<script setup>
import { computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { storeToRefs } from 'pinia'
import { ArrowLeft, LogIn, MessageCircle, Store, UserRound } from 'lucide-vue-next'
import { useAuthStore } from '../stores/auth'

const route = useRoute()
const auth = useAuthStore()
const { isLoggedIn } = storeToRefs(auth)

const showMobileNav = computed(() => {
  const roots = isLoggedIn.value
    ? ['/customer/home', '/customer/chat', '/customer/profile']
    : ['/customer/home', '/customer/login', '/customer/register']
  return roots.includes(route.path)
})
const mobileTitles = {
  'shop-menu': '商家点餐', checkout: '确认订单', 'customer-orders': '我的订单',
  'customer-order-detail': '订单详情', 'customer-feedback': '留言建议', 'customer-families': '我的家庭',
  'customer-notifications': '消息通知', 'customer-message-center': '消息通知', 'customer-chat-settings': '隐私设置', 'customer-privacy': '动态隐私',
  'customer-my-moments': '我的动态', 'customer-profile-edit': '修改个人信息',
  'customer-my-feedback': '反馈记录', 'customer-dish-detail': '菜品详情',
  'customer-dish-share': '分享菜品', 'customer-shop-share': '分享店铺', 'customer-order-share': '分享订单',
  'customer-development-log': '开发日志',
  'customer-chat-detail': '聊天详情', 'customer-chat-user': '用户资料', 'customer-chat-essences': '群精华', 'customer-family-group-info': '群聊信息',
  'customer-moment-new': '发布动态', 'customer-moment-detail': '动态详情'
}
const showMobileSubheader = computed(() => Boolean(mobileTitles[route.name]))
const mobileTitle = computed(() => mobileTitles[route.name] || '')

onMounted(() => auth.restoreSession())

function isActive(path) {
  return route.path.startsWith(path)
}
</script>

<style scoped>
.customer-layout { min-height: 100dvh; display: flex; flex-direction: column; background: var(--color-bg-light); }

.cust-header {
  background: #fff; border-bottom: 1px solid var(--color-line);
  position: sticky; top: 0; z-index: 100;
  box-shadow: 0 1px 4px rgba(0,0,0,0.04);
}
.cust-header-inner {
  max-width: 1200px; margin: 0 auto; padding: 0 16px;
  display: flex; align-items: center; min-height: 56px; gap: 16px;
}
.cust-logo { display: inline-flex; align-items: center; gap: 8px; font-size: 17px; font-weight: 800; color: var(--color-primary); white-space: nowrap; }
.cust-logo img { width: 28px; height: 28px; border-radius: 7px; }
.cust-nav { display: flex; gap: 2px; flex: 1; }
.cust-nav a {
  padding: 8px 14px; border-radius: var(--radius);
  font-size: 14px; font-weight: 500; color: var(--color-text-muted);
  white-space: nowrap; transition: all 0.2s;
}
.cust-nav a:hover { color: var(--color-primary); background: rgba(242,140,40,0.06); }
.cust-nav a.active { color: var(--color-primary); background: var(--color-primary-bg); font-weight: 700; }

.cust-actions { display: flex; gap: 8px; }
.login-btn, .profile-btn { min-height: 38px; }
.login-btn {
  display: inline-flex; align-items: center; padding: 7px 18px;
  background: var(--color-primary); color: #fff; border-radius: var(--radius-pill);
  font-size: 14px; font-weight: 600; transition: all 0.2s;
}
.login-btn:hover { background: var(--color-primary-light); }
.profile-btn {
  display: inline-flex; align-items: center; gap: 6px; padding: 6px 14px;
  background: var(--color-bg); color: var(--color-text); border-radius: var(--radius-pill);
  font-size: 14px; font-weight: 600; transition: all 0.2s;
}
.profile-btn:hover { background: var(--color-line); }
.cust-main { flex: 1; min-width: 0; }
.mobile-tabbar { display: none; }
.mobile-subheader { display: none; }

@media (max-width: 768px) {
  .cust-header { display: none; }
  .mobile-subheader {
    position: sticky; top: 0; z-index: 120; min-height: calc(52px + env(safe-area-inset-top));
    padding: env(safe-area-inset-top) 12px 0; display: flex; align-items: center; gap: 4px;
    background: rgba(255,255,255,0.97); border-bottom: 1px solid var(--color-line);
    backdrop-filter: blur(12px);
  }
  .mobile-subheader button { width: 40px; height: 40px; display: inline-flex; align-items: center; justify-content: center; background: transparent; color: var(--color-text); border-radius: var(--radius); flex-shrink: 0; }
  .mobile-subheader h1 { font-size: 18px; font-weight: 700; text-align: left; }
  .customer-layout.has-mobile-subheader :deep(.page-header) { display: none; }
  .customer-layout.has-mobile-nav .cust-main { padding-bottom: calc(64px + env(safe-area-inset-bottom)); }
  .mobile-tabbar {
    position: fixed; z-index: 190; right: 0; bottom: 0; left: 0;
    display: grid; grid-auto-flow: column; grid-auto-columns: 1fr;
    min-height: 64px; padding: 5px 6px max(5px, env(safe-area-inset-bottom));
    background: rgba(255,255,255,0.97); border-top: 1px solid var(--color-line);
    box-shadow: 0 -2px 12px rgba(0,0,0,0.06); backdrop-filter: blur(12px);
  }
  .mobile-tabbar a {
    min-width: 0; min-height: 52px; display: flex; flex-direction: column;
    align-items: center; justify-content: center; gap: 2px;
    color: var(--color-text-muted); font-size: 11px; font-weight: 600;
    -webkit-tap-highlight-color: transparent;
  }
  .mobile-tabbar a.active { color: var(--color-primary); }
  .mobile-tabbar a span { line-height: 1.2; }
}
</style>
