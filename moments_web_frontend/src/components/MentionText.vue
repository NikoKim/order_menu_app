<template>
  <span class="mention-text">
    <template v-for="(token, index) in tokens" :key="`${index}-${token.text}`">
      <span v-if="!token.user">{{ token.text }}</span>
      <button
        v-else
        type="button"
        class="mention-user"
        @mouseenter="openCard(token.user, $event.currentTarget)"
        @mouseleave="scheduleClose"
        @focus="openCard(token.user, $event.currentTarget)"
        @blur="scheduleClose"
        @click.stop="goToUser(token.user)"
      >{{ token.text }}</button>
    </template>
  </span>

  <Teleport to="body">
    <aside
      v-if="activeUser"
      class="mention-profile-card"
      :style="cardStyle"
      @mouseenter="cancelClose"
      @mouseleave="scheduleClose"
      @click.stop
    >
      <div class="profile-head">
        <span class="profile-avatar">
          <img v-if="profile?.avatarUrl || activeUser.avatarUrl" :src="imageUrl(profile?.avatarUrl || activeUser.avatarUrl)" alt="" />
          <span v-else>{{ (profile?.nickname || activeUser.nickname || '?').charAt(0) }}</span>
        </span>
        <span class="profile-copy">
          <span class="profile-name-row">
            <strong>{{ profile?.nickname || activeUser.nickname }}</strong>
            <i v-if="profile?.self || isSelf(activeUser)" class="self-label">自己</i>
          </span>
          <small>{{ roleLabel(profile?.role || activeUser.role) }}</small>
        </span>
      </div>
      <p v-if="loading" class="profile-muted">资料加载中...</p>
      <template v-else-if="profile">
        <p v-if="profile.phone"><Phone :size="14" />{{ profile.phone }}</p>
        <p v-else class="profile-muted"><PhoneOff :size="14" />电话未公开</p>
      </template>
      <p v-else class="profile-muted">暂时无法获取更多资料</p>
    </aside>
  </Teleport>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { Phone, PhoneOff } from 'lucide-vue-next'
import { useRouter } from 'vue-router'
import { getMomentUserCard } from '../api/moments'
import { imageUrl } from '../composables/useImage'

const props = defineProps({
  content: { type: String, default: '' },
  mentions: { type: Array, default: () => [] }
})
const router = useRouter()

const profileCache = new Map()
const activeUser = ref(null)
const profile = ref(null)
const loading = ref(false)
const cardPosition = ref({ left: 12, top: 12 })
let closeTimer
let requestVersion = 0

const tokens = computed(() => {
  const users = (props.mentions || []).filter(user => user?.nickname)
  if (!users.length || !props.content) return [{ text: props.content, user: null }]
  const byName = new Map(users.map(user => [user.nickname, user]))
  const alternatives = [...byName.keys()].sort((a, b) => b.length - a.length).map(escapeRegExp).join('|')
  const matcher = new RegExp(`(@(?:${alternatives}))(?![\\p{L}\\p{N}_])`, 'gu')
  const result = []
  let cursor = 0
  for (const match of props.content.matchAll(matcher)) {
    if (match.index > cursor) result.push({ text: props.content.slice(cursor, match.index), user: null })
    result.push({ text: match[0], user: byName.get(match[0].slice(1)) || null })
    cursor = match.index + match[0].length
  }
  if (cursor < props.content.length) result.push({ text: props.content.slice(cursor), user: null })
  return result.length ? result : [{ text: props.content, user: null }]
})

const cardStyle = computed(() => ({ left: `${cardPosition.value.left}px`, top: `${cardPosition.value.top}px` }))

async function openCard(user, target) {
  cancelClose()
  activeUser.value = user
  positionCard(target)
  const cached = profileCache.get(Number(user.id))
  if (cached) {
    profile.value = cached
    loading.value = false
    return
  }
  profile.value = null
  loading.value = true
  const version = ++requestVersion
  try {
    const result = await getMomentUserCard(user.id)
    profileCache.set(Number(user.id), result)
    if (version === requestVersion) profile.value = result
  } catch {
    if (version === requestVersion) profile.value = null
  } finally {
    if (version === requestVersion) loading.value = false
  }
}

function goToUser(user) {
  closeCard()
  const destination = isSelf(user) ? '/customer/my-moments' : `/customer/chat/user/${user.id}/moments`
  if (!localStorage.getItem('token')) {
    router.push({ path: '/customer/login', query: { redirect: destination } })
    return
  }
  router.push(destination)
}

function isSelf(user) {
  const currentUser = JSON.parse(localStorage.getItem('user') || '{}')
  return Number(currentUser?.id) === Number(user?.id)
}

function positionCard(target) {
  const rect = target.getBoundingClientRect()
  const width = 260
  const left = Math.min(Math.max(12, rect.left), Math.max(12, window.innerWidth - width - 12))
  const preferredTop = rect.bottom + 8
  const top = preferredTop + 150 > window.innerHeight ? Math.max(12, rect.top - 158) : preferredTop
  cardPosition.value = { left, top }
}

function scheduleClose() {
  cancelClose()
  closeTimer = window.setTimeout(closeCard, 140)
}

function cancelClose() {
  window.clearTimeout(closeTimer)
}

function closeCard() {
  cancelClose()
  activeUser.value = null
  profile.value = null
  loading.value = false
  requestVersion += 1
}

function roleLabel(role) {
  return role === 'SHOP' ? '商家用户' : '用户'
}

function escapeRegExp(value) {
  return value.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
}

function handleOutside(event) {
  if (!event.target.closest?.('.mention-user, .mention-profile-card')) closeCard()
}

onMounted(() => {
  document.addEventListener('pointerdown', handleOutside)
  window.addEventListener('scroll', closeCard, true)
})
onBeforeUnmount(() => {
  closeCard()
  document.removeEventListener('pointerdown', handleOutside)
  window.removeEventListener('scroll', closeCard, true)
})
</script>

<style scoped>
.mention-text { white-space: inherit; }
.mention-user { display: inline; padding: 0; border: 0; border-radius: 2px; background: transparent; color: #d96f0a; font: inherit; font-weight: 750; line-height: inherit; vertical-align: baseline; cursor: pointer; }
.mention-user:hover, .mention-user:focus-visible { color: #b95800; background: #fff1e2; outline: none; }
.mention-profile-card { width: 260px; position: fixed; z-index: 520; padding: 13px; border: 1px solid #e2e4e7; border-radius: 8px; background: #fff; color: #34373b; box-shadow: 0 12px 30px rgba(32, 33, 36, .16); }
.profile-head { display: flex; align-items: center; gap: 10px; }
.profile-avatar { width: 42px; height: 42px; flex: 0 0 42px; display: grid; place-items: center; overflow: hidden; border-radius: 50%; background: #fff0df; color: #c76509; font-weight: 750; }
.profile-avatar img { width: 100%; height: 100%; object-fit: cover; }
.profile-copy { min-width: 0; display: flex; flex-direction: column; }
.profile-name-row { min-width: 0; display: flex; align-items: center; gap: 6px; }
.profile-copy strong { min-width: 0; overflow: hidden; font-size: 14px; text-overflow: ellipsis; white-space: nowrap; }
.self-label { flex: 0 0 auto; padding: 1px 5px; border-radius: 3px; background: #fff0df; color: #c76509; font-size: 10px; font-style: normal; font-weight: 700; }
.profile-copy small { margin-top: 2px; color: #8a8f97; font-size: 11px; }
.mention-profile-card p { display: flex; align-items: center; gap: 7px; margin-top: 11px; color: #555a61; font-size: 12px; }
.mention-profile-card .profile-muted { color: #92979e; }
</style>
