<template>
  <div class="shop-layout" :class="{ 'has-mobile-nav': showMobileNav, 'has-mobile-subheader': showMobileSubheader }">
    <header class="shop-header">
      <div class="shop-header-inner">
        <router-link to="/shop/dashboard" class="shop-logo" aria-label="商家工作台">
          <img src="/app_icon.png" alt="" />
          <span>商家端</span>
        </router-link>
        <nav class="shop-nav" v-if="isLoggedIn" aria-label="商家主导航">
          <router-link to="/shop/dashboard" :class="{ active: isActive('/shop/dashboard') }">工作台</router-link>
          <router-link to="/shop/dishes" :class="{ active: isActive('/shop/dishes') }">菜品管理</router-link>
          <router-link to="/shop/chat" :class="{ active: isActive('/shop/chat') || isActive('/shop/moments') }">聊天动态</router-link>
          <router-link to="/shop/orders" :class="{ active: isActive('/shop/orders') }">
            订单管理
            <span v-if="pendingOrders" class="nav-badge">{{ badgeText(pendingOrders) }}</span>
          </router-link>
          <router-link to="/shop/feedback" :class="{ active: isActive('/shop/feedback') }">
            留言管理
            <span v-if="pendingFeedback" class="nav-badge">{{ badgeText(pendingFeedback) }}</span>
          </router-link>
          <router-link to="/shop/family" :class="{ active: isActive('/shop/family') }">
            家庭管理
            <span v-if="pendingFamily" class="nav-badge">{{ badgeText(pendingFamily) }}</span>
          </router-link>
          <router-link to="/shop/profile" :class="{ active: isActive('/shop/profile') }">店铺设置</router-link>
        </nav>
        <button v-if="isLoggedIn" class="logout-btn" title="退出登录" @click="handleLogout">
          <LogOut :size="16" aria-hidden="true" />
          <span>退出</span>
        </button>
      </div>
    </header>

    <header v-if="showMobileSubheader" class="mobile-subheader">
      <button title="返回" @click="$router.back()"><ArrowLeft :size="22" /></button>
      <h1>{{ mobileTitle }}</h1>
    </header>

    <main class="shop-main">
      <router-view />
    </main>

    <nav v-if="showMobileNav" class="mobile-tabbar" aria-label="商家主导航">
      <router-link to="/shop/dashboard" :class="{ active: isActive('/shop/dashboard') }">
        <LayoutDashboard :size="22" :stroke-width="isActive('/shop/dashboard') ? 2.5 : 2" />
        <span>工作台</span>
      </router-link>
      <router-link to="/shop/dishes" :class="{ active: isActive('/shop/dishes') }">
        <Utensils :size="22" :stroke-width="isActive('/shop/dishes') ? 2.5 : 2" />
        <span>菜品</span>
      </router-link>
      <router-link to="/shop/chat" :class="{ active: isActive('/shop/chat') }">
        <MessageCircle :size="22" :stroke-width="isActive('/shop/chat') ? 2.5 : 2" />
        <span>聊天</span>
      </router-link>
      <router-link to="/shop/orders" :class="{ active: isActive('/shop/orders') }">
        <span class="tab-icon-wrap">
          <ClipboardList :size="22" :stroke-width="isActive('/shop/orders') ? 2.5 : 2" />
          <span v-if="pendingOrders" class="tab-badge">{{ badgeText(pendingOrders) }}</span>
        </span>
        <span>订单</span>
      </router-link>
      <router-link to="/shop/profile" :class="{ active: isActive('/shop/profile') }">
        <span class="tab-icon-wrap">
          <Store :size="22" :stroke-width="isActive('/shop/profile') ? 2.5 : 2" />
          <span v-if="shopBadgeCount" class="tab-badge">{{ badgeText(shopBadgeCount) }}</span>
        </span>
        <span>店铺</span>
      </router-link>
    </nav>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { storeToRefs } from 'pinia'
import { ArrowLeft, ClipboardList, LayoutDashboard, LogOut, MessageCircle, Store, Utensils } from 'lucide-vue-next'
import { useAuthStore } from '../stores/auth'
import { getShopNotifications } from '../api/shop'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const { isLoggedIn } = storeToRefs(auth)

const pendingOrders = ref(0)
const pendingFamily = ref(0)
const pendingFeedback = ref(0)

const shopBadgeCount = computed(() => pendingFamily.value + pendingFeedback.value)
const showMobileNav = computed(() => isLoggedIn.value && [
  '/shop/dashboard', '/shop/dishes', '/shop/chat', '/shop/orders', '/shop/profile'
].includes(route.path))
const mobileTitles = {
  'shop-dish-new': '新增菜品', 'shop-dish-edit': '编辑菜品', 'shop-order-detail': '订单详情',
  'shop-feedback': '留言管理', 'shop-family': '家庭管理',
  'shop-notifications': '消息通知', 'shop-message-center': '消息通知', 'shop-chat-settings': '隐私设置',
  'shop-development-log': '开发日志', 'shop-showcase': '首页展示设置',
  'shop-chat-detail': '聊天详情', 'shop-chat-user': '用户资料', 'shop-chat-essences': '群精华',
  'shop-family-group-info': '群聊信息', 'shop-family-group-manage': '家庭群聊管理',
  'shop-moment-new': '发布动态', 'shop-moment-detail': '动态详情'
}
const showMobileSubheader = computed(() => Boolean(mobileTitles[route.name]))
const mobileTitle = computed(() => mobileTitles[route.name] || '')

auth.restoreSession()

function isActive(path) {
  return route.path.startsWith(path)
}

function badgeText(count) {
  return count > 99 ? '99+' : count
}

async function loadBadges() {
  try {
    const data = await getShopNotifications()
    pendingOrders.value = data.pendingOrders || 0
    pendingFamily.value = data.pendingFamilyMembers || 0
    pendingFeedback.value = data.pendingFeedback || 0
  } catch { }
}

function handleLogout() {
  auth.logout()
  router.push('/shop/login')
}

onMounted(() => {
  if (isLoggedIn.value) loadBadges()
})
</script>

<style scoped>
.shop-layout { min-height: 100dvh; display: flex; flex-direction: column; background: var(--color-bg); }
.shop-header {
  background: #fff; border-bottom: 1px solid var(--color-line);
  position: sticky; top: 0; z-index: 100;
  box-shadow: 0 1px 4px rgba(0,0,0,0.04);
}
.shop-header-inner {
  max-width: 1200px; margin: 0 auto; padding: 0 16px;
  display: flex; align-items: center; min-height: 56px; gap: 20px;
}
.shop-logo { display: inline-flex; align-items: center; gap: 8px; font-size: 17px; font-weight: 800; color: var(--color-primary); white-space: nowrap; }
.shop-logo img { width: 28px; height: 28px; border-radius: 7px; }
.shop-nav { display: flex; gap: 2px; flex: 1; min-width: 0; }
.shop-nav a {
  position: relative; padding: 8px 12px; border-radius: var(--radius);
  font-size: 14px; font-weight: 500; color: var(--color-text-muted);
  white-space: nowrap; transition: all 0.2s;
}
.shop-nav a:hover { color: var(--color-primary); background: rgba(242,140,40,0.06); }
.shop-nav a.active { color: var(--color-primary); background: var(--color-primary-bg); font-weight: 700; }
.nav-badge, .tab-badge {
  min-width: 18px; height: 18px; border-radius: 9px; padding: 0 4px;
  background: var(--color-error); color: #fff; font-size: 10px; font-weight: 700;
  display: flex; align-items: center; justify-content: center; line-height: 1;
}
.nav-badge { position: absolute; top: 0; right: 0; transform: translate(25%, -25%); }
.logout-btn {
  margin-left: auto; flex-shrink: 0; min-height: 38px; padding: 7px 12px;
  display: inline-flex; align-items: center; gap: 6px;
  border: 1px solid var(--color-line); background: #fff;
  border-radius: var(--radius); font-size: 13px; color: var(--color-text-muted);
  transition: all 0.2s;
}
.logout-btn:hover { background: var(--color-bg); color: var(--color-error); border-color: var(--color-error); }
.shop-main { flex: 1; min-width: 0; }
.mobile-tabbar { display: none; }
.mobile-subheader { display: none; }

@media (max-width: 900px) and (min-width: 769px) {
  .shop-header-inner { gap: 8px; }
  .shop-nav a { padding-inline: 8px; font-size: 13px; }
  .logout-btn span { display: none; }
}

@media (max-width: 768px) {
  .shop-header { display: none; }
  .mobile-subheader {
    position: sticky; top: 0; z-index: 120; min-height: calc(52px + env(safe-area-inset-top));
    padding: env(safe-area-inset-top) 12px 0; display: flex; align-items: center; gap: 4px;
    background: rgba(255,255,255,0.97); border-bottom: 1px solid var(--color-line);
    backdrop-filter: blur(12px);
  }
  .mobile-subheader button { width: 40px; height: 40px; display: inline-flex; align-items: center; justify-content: center; background: transparent; color: var(--color-text); border-radius: var(--radius); flex-shrink: 0; }
  .mobile-subheader h1 { font-size: 18px; font-weight: 700; text-align: left; }
  .shop-layout.has-mobile-subheader :deep(.page-header) { display: none; }
  .shop-layout.has-mobile-nav .shop-main { padding-bottom: calc(64px + env(safe-area-inset-bottom)); }
  .mobile-tabbar {
    position: fixed; z-index: 190; right: 0; bottom: 0; left: 0;
    display: grid; grid-template-columns: repeat(5, minmax(0, 1fr));
    min-height: 64px; padding: 5px 3px max(5px, env(safe-area-inset-bottom));
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
  .mobile-tabbar a > span:last-child { line-height: 1.2; }
  .tab-icon-wrap { position: relative; display: inline-flex; }
  .tab-badge { position: absolute; top: -7px; right: -12px; }
}
</style>
