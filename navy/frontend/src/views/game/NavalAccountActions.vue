<template>
  <div class="account-actions">
    <router-link v-if="account?.isAdmin" to="/admin" class="navy-toolbar-button account-icon" title="管理中心" aria-label="管理中心"><ShieldCheck :size="19" /></router-link>
    <button class="navy-toolbar-button account-icon" :class="{ active: active === 'shop' }" type="button" title="商城" aria-label="商城" :aria-pressed="active === 'shop'" @click="openShop"><ShoppingBag :size="19" /></button>
    <button class="navy-toolbar-button account-icon" :class="{ active: active === 'notifications' }" type="button" title="消息通知" :aria-label="unreadCount ? '消息通知，有未读消息' : '消息通知'" :aria-pressed="active === 'notifications'" @click="openInbox"><Bell :size="19" /><span v-if="unreadCount" class="activity-dot" /></button>
    <button class="navy-toolbar-button account-icon" :class="{ active: active === 'profile' }" type="button" title="个人主页" :aria-label="profile?.claimableCount ? '个人主页，有任务奖励待领取' : '个人主页'" :aria-pressed="active === 'profile'" @click="$emit('profile')"><UserRound :size="19" /><span v-if="profile?.claimableCount" class="activity-dot" /></button>
  </div>
</template>

<script setup>
import { onMounted, onUnmounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { Bell, ShieldCheck, ShoppingBag, UserRound } from 'lucide-vue-next'
import client from '../../api/client'
import { notificationUnreadCount as unreadCount, subscribeNotificationSummary } from '../../services/accountNotifications'
import { ownNavalProgression as profile, refreshNavalProgression } from '../../games/navalProgression'

defineProps({ active: String })
const emit = defineEmits(['profile', 'notifications', 'shop'])
const router = useRouter(), loggedIn = Boolean(localStorage.getItem('navy_token'))
const account = ref(null)
let disposed = false, unsubscribe
function openInbox() {
  if (!loggedIn) { router.push('/login?redirect=/'); return }
  emit('notifications')
}
function openShop() {
  if (!loggedIn) { router.push('/login?redirect=/'); return }
  emit('shop')
}
function visible() { if (document.visibilityState === 'visible') refreshNavalProgression() }
onMounted(async () => {
  if (!loggedIn) return
  unsubscribe = subscribeNotificationSummary()
  refreshNavalProgression()
  document.addEventListener('visibilitychange', visible)
  window.addEventListener('focus', visible)
  try {
    const value = await client.get('/account')
    if (!disposed) account.value = value
  } catch { /* The profile provides its own retry state. */ }
})
onUnmounted(() => { disposed = true; unsubscribe?.(); document.removeEventListener('visibilitychange', visible); window.removeEventListener('focus', visible) })
</script>

<style scoped>
.account-actions{display:flex;align-items:center;gap:var(--navy-toolbar-gap,6px);flex-shrink:0}.account-icon{position:relative}.activity-dot{position:absolute;right:4px;top:3px;width:7px;height:7px;border-radius:50%;background:#db6250;box-shadow:0 0 0 2px #f6f8f9;pointer-events:none}
@media(max-width:700px){.account-actions{gap:2px}}
</style>
