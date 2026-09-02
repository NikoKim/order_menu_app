<template>
  <section class="chat-moment-page">
    <header class="switch-header" :class="{ 'header-hidden': headerHidden && tab === 'moments' }">
      <button class="header-tab" :class="{ active: tab === 'chat' }" @click="switchTab('chat')">
        <span>聊天</span>
        <i v-if="chatUnread" class="tab-badge">{{ badge(chatUnread) }}</i>
      </button>
      <button class="header-tab" :class="{ active: tab === 'moments' }" @click="switchTab('moments')">
        <span>动态</span>
        <i v-if="momentUnread" class="tab-badge">{{ badge(momentUnread) }}</i>
      </button>
      <router-link class="header-tab entertainment-tab" to="/customer/entertainment"><span>娱乐</span></router-link>
      <router-link v-if="isLoggedIn" class="mobile-message-link" to="/customer/message-center" title="消息中心" aria-label="消息中心">
        <Bell :size="21" aria-hidden="true" />
        <i v-if="notificationUnread" class="message-badge">{{ badge(notificationUnread) }}</i>
      </router-link>
      <button v-if="isLoggedIn" class="mobile-menu-btn" title="账户菜单" aria-label="账户菜单" @click="mobileMenuOpen = !mobileMenuOpen">
        <img v-if="currentUser.avatarUrl" :src="imageUrl(currentUser.avatarUrl)" alt="" />
        <UserRound v-else :size="21" aria-hidden="true" />
      </button>
      <router-link v-else class="mobile-login-link" :to="{ path: '/customer/login', query: { redirect: route.fullPath } }">登录</router-link>
    </header>

    <div v-if="mobileMenuOpen" class="mobile-menu-backdrop" @click="mobileMenuOpen = false"></div>
    <nav v-if="mobileMenuOpen" class="mobile-nav-menu" aria-label="更多页面">
      <template v-if="isLoggedIn">
        <router-link to="/customer/message-center" @click="mobileMenuOpen = false"><Bell :size="19" /><span>消息中心</span><i v-if="notificationUnread" class="menu-badge">{{ badge(notificationUnread) }}</i></router-link>
        <router-link to="/customer/my-moments" @click="mobileMenuOpen = false"><Album :size="19" /><span>我的动态</span></router-link>
        <router-link to="/customer/moments/drafts" @click="mobileMenuOpen = false"><FileText :size="19" /><span>动态草稿</span></router-link>
        <router-link to="/customer/privacy" @click="mobileMenuOpen = false"><Settings :size="19" /><span>聊天设置</span></router-link>
        <router-link to="/customer/moment-privacy" @click="mobileMenuOpen = false"><ShieldCheck :size="19" /><span>动态隐私</span></router-link>
        <button class="mobile-logout" @click="logout"><LogOut :size="19" /><span>退出登录</span></button>
      </template>
      <router-link v-else to="/customer/login" @click="mobileMenuOpen = false"><LogIn :size="19" /><span>登录</span></router-link>
    </nav>

    <div v-show="tab === 'chat'" class="tab-panel">
      <ChatConversations v-if="isLoggedIn" variant="customer" />
    </div>
    <div v-show="tab === 'moments'" class="tab-panel moments-panel">
      <MomentFeed :active="tab === 'moments'" @header-visibility="setHeaderVisibility" @related-unread="momentUnread = $event" />
    </div>
  </section>
</template>

<script setup>
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Album, Bell, FileText, LogIn, LogOut, Settings, ShieldCheck, UserRound } from 'lucide-vue-next'
import { getConversations, getNotificationUnreadCount } from '../../api/messages'
import { getMomentUnreadCount } from '../../api/moments'
import ChatConversations from '../chat/ChatConversations.vue'
import MomentFeed from '../moments/MomentFeed.vue'
import { imageUrl } from '../../composables/useImage'
import { connectCustomerWebSocket, disconnectCustomerWebSocket, subscribeCustomerWebSocket } from '../../services/customerWebSocket'

const route = useRoute()
const router = useRouter()
const tab = ref(route.path === '/customer/moments' ? 'moments' : 'chat')
const chatUnread = ref(0)
const momentUnread = ref(0)
const notificationUnread = ref(0)
const mobileMenuOpen = ref(false)
const headerHidden = ref(false)
const isLoggedIn = computed(() => Boolean(localStorage.getItem('token')))
const currentUser = computed(() => JSON.parse(localStorage.getItem('user') || '{}'))
let pollTimer
const wsUnsubscribers = []

const badge = value => value > 99 ? '99+' : value

watch(() => route.path, path => {
  tab.value = path === '/customer/moments' ? 'moments' : 'chat'
})

function switchTab(next) {
  headerHidden.value = false
  if (next === 'chat') {
    router.push('/customer/chat')
    return
  }
  tab.value = 'moments'
  if (route.path !== '/customer/moments') router.push('/customer/moments')
}

function setHeaderVisibility(state) {
  if (!window.matchMedia('(max-width: 999px)').matches) return
  const hidden = state === 'hide'
  if (headerHidden.value === hidden) return
  headerHidden.value = hidden
}

function logout() {
  localStorage.removeItem('token')
  localStorage.removeItem('user')
  disconnectCustomerWebSocket()
  mobileMenuOpen.value = false
  router.replace('/customer/login')
}

async function loadBadges() {
  if (!localStorage.getItem('token')) return
  try {
    const rows = await getConversations()
    chatUnread.value = rows.filter(item => !item.muted).reduce((sum, item) => sum + (item.unreadCount || 0), 0)
  } catch { }
  try {
    const data = await getMomentUnreadCount()
    momentUnread.value = data.relatedUnreadCount || 0
  } catch { }
  try { notificationUnread.value = Number(await getNotificationUnreadCount()) || 0 } catch { }
}

async function openSharedMoment() {
  const momentId = String(route.query.sharedMoment || '')
  if (!momentId || !/^\d+$/.test(momentId)) return
  const query = { shareMode: 'browse' }
  if (route.query.commentId) query.commentId = route.query.commentId
  await router.replace('/customer/moments')
  await router.push({ path: `/customer/moments/${momentId}`, query })
}

onMounted(() => {
  openSharedMoment()
  loadBadges()
  connectCustomerWebSocket()
  wsUnsubscribers.push(
    subscribeCustomerWebSocket('CHAT', loadBadges),
    subscribeCustomerWebSocket('MOMENT', message => {
      const count = Number(message?.data?.relatedUnreadCount)
      if (Number.isFinite(count)) momentUnread.value = count
      else loadBadges()
    }),
    subscribeCustomerWebSocket('NOTIFICATION', message => {
      const count = Number(message?.data?.unreadCount)
      if (Number.isFinite(count)) notificationUnread.value = count
      else loadBadges()
    }),
    subscribeCustomerWebSocket('CONNECTION', message => {
      if (message.action === 'CONNECTED') loadBadges()
    })
  )
  pollTimer = window.setInterval(loadBadges, 60000)
})
onUnmounted(() => {
  window.clearInterval(pollTimer)
  wsUnsubscribers.splice(0).forEach(unsubscribe => unsubscribe())
})
</script>

<style scoped>
.chat-moment-page { height: 100dvh; display: flex; flex-direction: column; overflow: hidden; background: #f6f7f8; }
.switch-header { height: 60px; flex: 0 0 60px; display: flex; align-items: center; gap: 10px; padding: 8px 14px 7px 18px; overflow: hidden; background: #fff; border-bottom: 1px solid #eaecf0; transition: height .24s ease, flex-basis .24s ease, padding .24s ease, border-color .2s ease, transform .24s ease, opacity .2s ease; }
.header-tab { height: 45px; position: relative; display: flex; align-items: center; gap: 6px; padding: 0 7px; background: transparent; color: #8a8f98; font-size: 20px; font-weight: 500; }
.header-tab.active { color: #202124; font-weight: 800; }
.header-tab.active::after { content: ''; position: absolute; left: 50%; bottom: 1px; width: 22px; height: 3px; transform: translateX(-50%); border-radius: 2px; background: #f28c28; }
.tab-badge { min-width: 18px; height: 18px; display: grid; place-items: center; padding: 0 5px; border-radius: 10px; background: #e85d4a; color: #fff; font-size: 10px; font-style: normal; font-weight: 700; }
.mobile-message-link { width: 40px; height: 40px; position: relative; margin-left: auto; display: none; place-items: center; border-radius: 50%; color: #54585f; }
.message-badge { position: absolute; top: 1px; right: -3px; min-width: 17px; height: 17px; display: grid; place-items: center; padding: 0 4px; border: 2px solid #fff; border-radius: 10px; background: #e85d4a; color: #fff; font-size: 9px; font-style: normal; font-weight: 750; }
.menu-badge { min-width: 19px; height: 19px; margin-left: auto; display: grid; place-items: center; padding: 0 5px; border-radius: 10px; background: #e85d4a; color: #fff; font-size: 10px; font-style: normal; }
.mobile-message-link:active { background: #f2f3f5; }
.mobile-menu-btn { width: 40px; height: 40px; display: none; place-items: center; flex: 0 0 auto; border-radius: 50%; background: transparent; color: #54585f; }
.mobile-message-link + .mobile-menu-btn { margin-left: 0; }
.mobile-menu-btn:active { background: #f2f3f5; }
.mobile-menu-btn img { width: 34px; height: 34px; border-radius: 50%; object-fit: cover; }
.mobile-login-link { display: none; margin-left: auto; padding: 8px 4px; color: var(--color-primary); font-size: 14px; font-weight: 700; }
.mobile-menu-backdrop { display: none; }
.mobile-nav-menu { display: none; }
.tab-panel { min-height: 0; flex: 1; overflow-y: auto; padding: 12px 12px 24px; }
.moments-panel { padding: 0; overflow: hidden; }

@media (max-width: 999px) {
  .switch-header { padding-top: max(8px, env(safe-area-inset-top)); height: calc(60px + env(safe-area-inset-top)); flex-basis: calc(60px + env(safe-area-inset-top)); }
  .mobile-message-link { display: grid; }
  .mobile-menu-btn { display: grid; margin-left: auto; }
  .mobile-login-link { display: inline-flex; align-items: center; }
  .switch-header.header-hidden { height: 0; flex-basis: 0; padding-top: 0; padding-bottom: 0; border-bottom-color: transparent; transform: translateY(-100%); opacity: 0; pointer-events: none; }
  .mobile-message-link + .mobile-menu-btn { margin-left: 0; }
  .mobile-menu-backdrop { position: fixed; inset: 0; z-index: 79; display: block; background: rgba(0, 0, 0, 0.16); }
  .mobile-nav-menu { position: fixed; top: calc(58px + env(safe-area-inset-top)); right: 12px; z-index: 80; width: min(280px, calc(100vw - 24px)); display: flex; flex-direction: column; padding: 6px; overflow: hidden; border: 1px solid #e8eaed; border-radius: 8px; background: #fff; box-shadow: 0 12px 32px rgba(28, 30, 33, 0.18); }
  .mobile-nav-menu a, .mobile-nav-menu button { min-height: 46px; display: flex; align-items: center; gap: 12px; padding: 0 12px; border-radius: 6px; background: transparent; color: #34373b; font-size: 14px; font-weight: 600; text-align: left; }
  .mobile-nav-menu a:active, .mobile-nav-menu button:active { background: #f3f4f6; }
  .mobile-nav-menu svg { flex: 0 0 auto; color: #737880; }
  .mobile-nav-menu .mobile-logout { margin-top: 4px; border-top: 1px solid #eceef1; border-radius: 0 0 6px 6px; color: #c94e42; }
  .mobile-nav-menu .mobile-logout svg { color: currentColor; }
}

@media (max-width: 480px) {
  .tab-panel { padding: 10px 0 max(16px, env(safe-area-inset-bottom)); }
  .moments-panel { padding: 0; }
}
</style>
