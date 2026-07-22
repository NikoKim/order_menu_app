<template>
  <div v-if="route.meta.bare" class="bare-layout">
    <router-view />
  </div>
  <div v-else class="moments-shell" :class="{ immersive: route.meta.immersive, 'isolated-share': isolatedShare }">
    <aside v-if="!isolatedShare" class="desktop-rail" aria-label="主导航">
      <router-link :to="landingPath" class="brand" aria-label="香狗点餐聊天与动态">
        <img src="/app_icon.png" alt="" />
        <span>香狗</span>
      </router-link>

      <nav class="rail-nav">
        <router-link :to="landingPath" class="rail-link" :class="{ active: isChatHome }">
          <MessageCircle :size="22" aria-hidden="true" />
          <span>聊天与动态</span>
          <i v-if="chatUnread" class="unread-dot">{{ chatUnread > 99 ? '99+' : chatUnread }}</i>
        </router-link>
        <router-link to="/customer/message-center" class="rail-link">
          <Bell :size="22" aria-hidden="true" /><span>消息中心</span>
          <i v-if="notificationUnread" class="unread-dot">{{ notificationUnread > 99 ? '99+' : notificationUnread }}</i>
        </router-link>
        <router-link to="/customer/my-moments" class="rail-link">
          <Album :size="22" aria-hidden="true" /><span>我的动态</span>
        </router-link>
        <router-link to="/customer/privacy" class="rail-link">
          <Settings :size="22" aria-hidden="true" /><span>聊天设置</span>
        </router-link>
        <router-link to="/customer/moment-privacy" class="rail-link">
          <ShieldCheck :size="22" aria-hidden="true" /><span>动态隐私</span>
        </router-link>
      </nav>

      <div v-if="user" class="rail-account">
        <div class="account-avatar">
          <img v-if="user?.avatarUrl" :src="imageUrl(user.avatarUrl)" alt="" />
          <UserRound v-else :size="20" aria-hidden="true" />
        </div>
        <span>{{ user?.nickname || '我的账号' }}</span>
        <button class="icon-btn" title="退出登录" aria-label="退出登录" @click="logout">
          <LogOut :size="18" aria-hidden="true" />
        </button>
      </div>
      <router-link v-else class="rail-login" to="/customer/login"><LogIn :size="18" />登录</router-link>
    </aside>

    <main class="app-surface">
      <router-view />
    </main>
  </div>
</template>

<script setup>
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Album, Bell, LogIn, LogOut, MessageCircle, Settings, ShieldCheck, UserRound } from 'lucide-vue-next'
import { getConversations, getNotificationUnreadCount } from '../api/messages'
import { imageUrl } from '../composables/useImage'
import { connectCustomerWebSocket, disconnectCustomerWebSocket, subscribeCustomerWebSocket } from '../services/customerWebSocket'

const route = useRoute()
const router = useRouter()
const chatUnread = ref(0)
const notificationUnread = ref(0)
let timer
const wsUnsubscribers = []

const user = computed(() => {
  try { return JSON.parse(localStorage.getItem('user') || 'null') } catch { return null }
})
const isChatHome = computed(() => route.path === '/customer/chat')
const landingPath = computed(() => localStorage.getItem('token') ? '/customer/chat' : '/customer/moments')
const isolatedShare = computed(() => route.query.shareMode === 'isolated')

async function refreshUnread() {
  if (!localStorage.getItem('token')) return
  try {
    const rows = await getConversations()
    chatUnread.value = rows.filter(item => !item.muted).reduce((sum, item) => sum + (item.unreadCount || 0), 0)
  } catch { }
  try { notificationUnread.value = Number(await getNotificationUnreadCount()) || 0 } catch { }
}
function logout() {
  localStorage.removeItem('token')
  localStorage.removeItem('user')
  disconnectCustomerWebSocket()
  router.replace('/customer/login')
}

onMounted(() => {
  refreshUnread()
  connectCustomerWebSocket()
  wsUnsubscribers.push(
    subscribeCustomerWebSocket('CHAT', refreshUnread),
    subscribeCustomerWebSocket('NOTIFICATION', message => {
      const count = Number(message?.data?.unreadCount)
      if (Number.isFinite(count)) notificationUnread.value = count
      else refreshUnread()
    }),
    subscribeCustomerWebSocket('CONNECTION', message => {
      if (message.action === 'CONNECTED') refreshUnread()
    })
  )
  timer = window.setInterval(refreshUnread, 60000)
})
onUnmounted(() => {
  window.clearInterval(timer)
  wsUnsubscribers.splice(0).forEach(unsubscribe => unsubscribe())
})
</script>

<style scoped>
.bare-layout { min-height: 100dvh; }
.moments-shell { min-height: 100dvh; display: grid; grid-template-columns: 248px minmax(0, 760px); justify-content: center; background: #f6f7f8; }
.moments-shell.isolated-share { grid-template-columns: minmax(0, 760px); }
.desktop-rail { height: 100dvh; position: sticky; top: 0; display: flex; flex-direction: column; padding: 24px 18px 18px; background: #fff; border-right: 1px solid #eaecf0; }
.brand { display: flex; align-items: center; gap: 11px; padding: 0 10px 26px; color: #202124; font-size: 20px; font-weight: 800; }
.brand img { width: 38px; height: 38px; border-radius: 8px; object-fit: cover; }
.rail-nav { display: flex; flex-direction: column; gap: 6px; }
.rail-link { min-height: 48px; display: flex; align-items: center; gap: 13px; padding: 0 13px; border-radius: 8px; color: #60646c; font-size: 15px; font-weight: 600; }
.rail-link:hover { background: #f6f7f8; color: #202124; }
.rail-link.active, .rail-link.router-link-active { color: #d96f0a; background: #fff4e8; }
.unread-dot { min-width: 19px; height: 19px; margin-left: auto; padding: 0 5px; display: grid; place-items: center; border-radius: 10px; background: #e85d4a; color: #fff; font-size: 10px; font-style: normal; }
.rail-account { margin-top: auto; min-width: 0; display: flex; align-items: center; gap: 10px; padding: 12px 6px 0; border-top: 1px solid #eee; font-size: 14px; font-weight: 600; }
.rail-account > span { flex: 1; min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.account-avatar { width: 36px; height: 36px; flex: 0 0 auto; display: grid; place-items: center; overflow: hidden; border-radius: 50%; background: #f0f1f3; color: #8a8f98; }
.account-avatar img { width: 100%; height: 100%; object-fit: cover; }
.icon-btn { width: 36px; height: 36px; display: grid; place-items: center; border-radius: 8px; background: transparent; color: #8a8f98; }
.icon-btn:hover { background: #f6f7f8; color: #202124; }
.rail-login { min-height: 44px; margin-top: auto; display: flex; align-items: center; justify-content: center; gap: 8px; border: 1px solid #f28c28; border-radius: 8px; color: #d96f0a; font-size: 14px; font-weight: 700; }
.rail-login:hover { background: #fff4e8; }
.app-surface { min-width: 0; min-height: 100dvh; background: #f6f7f8; border-right: 1px solid #eaecf0; }

@media (max-width: 999px) {
  .moments-shell { display: block; }
  .desktop-rail { display: none; }
  .app-surface { width: 100%; border: 0; }
}
</style>
