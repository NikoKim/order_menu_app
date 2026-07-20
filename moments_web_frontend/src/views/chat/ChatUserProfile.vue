<template>
  <div class="profile-page">
    <header class="profile-header">
      <button class="header-btn" title="返回" aria-label="返回" @click="router.back()"><ArrowLeft :size="22" /></button>
      <h1>用户资料</h1>
      <span class="header-btn"></span>
    </header>

    <LoadingSpinner v-if="loading" />
    <div v-else-if="error" class="error-wrap">
      <EmptyState icon="warning" :title="error" action-text="重试" @action="loadProfile" />
    </div>
    <main v-else-if="profile" class="profile-content">
      <section class="identity-panel">
        <div class="profile-avatar">
          <img v-if="profile.avatarUrl" :src="imageUrl(profile.avatarUrl)" alt="" />
          <UserRound v-else :size="42" aria-hidden="true" />
        </div>
        <div class="name-row">
          <h2>{{ profile.nickname || '用户' }}</h2>
          <span v-if="isMerchant" class="merchant-badge">商家</span>
        </div>
        <p v-if="profile.phone" class="profile-line">手机号 {{ profile.phone }}</p>
        <p v-if="joinedFamilyNames.length" class="profile-line">已加入家庭：{{ joinedFamilyNames.join('、') }}</p>
        <p v-if="blockStatus.blockedMe" class="blocked-note">对方已将您拉黑</p>
      </section>

      <button v-if="shopProfile?.shopId" class="shop-panel" @click="openShop">
        <span class="shop-logo">
          <img v-if="shopProfile.shopLogoUrl" :src="imageUrl(shopProfile.shopLogoUrl)" alt="" />
          <Store v-else :size="22" aria-hidden="true" />
        </span>
        <span class="shop-copy">
          <strong>{{ shopProfile.shopName }}</strong>
          <small v-if="shopProfile.shopDescription">{{ shopProfile.shopDescription }}</small>
        </span>
        <ChevronRight :size="20" aria-hidden="true" />
      </button>

      <button class="moments-panel" @click="openUserMoments">
        <span class="moment-preview-stack">
          <span v-for="(item, index) in momentPreview.slice(0, 3)" :key="item.id || index" class="preview-avatar" :style="{ left: `${index * 15}px` }">
            <img v-if="item.imageUrls?.length" :src="imageUrl(item.imageUrls[0])" alt="" />
            <Newspaper v-else :size="16" aria-hidden="true" />
          </span>
          <span v-if="!momentPreview.length" class="preview-avatar"><Newspaper :size="18" aria-hidden="true" /></span>
        </span>
        <span class="moments-copy">
          <strong>Ta发布的动态</strong>
          <small v-if="momentPreview.length">{{ momentPreview.slice(0, 2).map(item => item.title || item.content).filter(Boolean).join(' · ') }}</small>
          <small v-else>{{ momentSummary?.momentCount || 0 }}条动态</small>
        </span>
        <ChevronRight :size="20" aria-hidden="true" />
      </button>

      <section class="action-panel">
        <button class="action-row" :disabled="working" @click="startChat">
          <MessageCircle :size="21" aria-hidden="true" />
          <span class="row-copy"><strong>发送消息</strong></span>
          <ChevronRight :size="20" aria-hidden="true" />
        </button>

        <label v-if="preferences" class="action-row" :class="{ disabled: working }">
          <BellOff :size="21" aria-hidden="true" />
          <span class="row-copy"><strong>消息免打扰</strong></span>
          <input class="switch-input" type="checkbox" :checked="preferences.muted" :disabled="working" @change="toggleMute" />
          <span class="switch-track" aria-hidden="true"><i></i></span>
        </label>

        <label class="action-row" :class="{ disabled: working }">
          <EyeOff :size="21" aria-hidden="true" />
          <span class="row-copy"><strong>不看Ta动态</strong></span>
          <input class="switch-input" type="checkbox" :checked="momentPrivacy.hideTargetMoments" :disabled="working" @change="event => updatePrivacy('hideTargetMoments', event.target.checked)" />
          <span class="switch-track" aria-hidden="true"><i></i></span>
        </label>

        <label class="action-row" :class="{ disabled: working }">
          <UserRoundX :size="21" aria-hidden="true" />
          <span class="row-copy"><strong>不让Ta看我动态</strong></span>
          <input class="switch-input" type="checkbox" :checked="momentPrivacy.hideMyMoments" :disabled="working" @change="event => updatePrivacy('hideMyMoments', event.target.checked)" />
          <span class="switch-track" aria-hidden="true"><i></i></span>
        </label>

        <button class="action-row" :class="{ danger: !blockStatus.blockedByMe }" :disabled="working" @click="toggleBlock">
          <UserRoundCheck v-if="blockStatus.blockedByMe" :size="21" aria-hidden="true" />
          <Ban v-else :size="21" aria-hidden="true" />
          <span class="row-copy"><strong>{{ blockStatus.blockedByMe ? '取消拉黑' : '拉黑用户' }}</strong></span>
        </button>
      </section>
    </main>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowLeft, Ban, BellOff, ChevronRight, EyeOff, MessageCircle, Newspaper, Store, UserRound, UserRoundCheck, UserRoundX } from 'lucide-vue-next'
import { blockUser, getBlockStatus, getChatUserProfile, getConversationPreferences, setConversationMute, startDirectChatWithShop, startDirectChatWithUser, unblockUser } from '../../api/messages'
import { getMomentPrivacy, getMomentProfileSummary, updateMomentPrivacy } from '../../api/moments'
import { imageUrl } from '../../composables/useImage'
import LoadingSpinner from '../../components/LoadingSpinner.vue'
import EmptyState from '../../components/EmptyState.vue'
import { notifyError } from '../../services/notice'

const route = useRoute()
const router = useRouter()
const profile = ref(null)
const blockStatus = ref({ blockedByMe: false, blockedMe: false })
const preferences = ref(null)
const momentPrivacy = ref({ hideTargetMoments: false, hideMyMoments: false })
const momentSummary = ref(null)
const loading = ref(true)
const working = ref(false)
const error = ref('')

const userId = computed(() => Number(route.params.userId))
const conversationId = computed(() => {
  const value = Number(route.query.conversationId)
  return Number.isFinite(value) && value > 0 ? value : null
})
const shopProfile = computed(() => profile.value?.shopProfile || (profile.value?.shopId ? {
  shopId: profile.value.shopId,
  shopName: profile.value.shopName,
  shopLogoUrl: profile.value.shopLogoUrl,
  shopDescription: profile.value.shopDescription
} : null))
const isMerchant = computed(() => ['SHOP', 'MERCHANT'].includes(profile.value?.role) || Boolean(shopProfile.value?.shopId))
const joinedFamilyNames = computed(() => (profile.value?.joinedFamilies || []).map(item => {
  if (typeof item === 'string') return item
  return item?.familyName || item?.shopName || ''
}).filter(Boolean))
const momentPreview = computed(() => momentSummary.value?.preview || [])

function normalizedPrivacy(value = {}) {
  return {
    hideTargetMoments: Boolean(value.hideTargetMoments ?? value.hideTheirMoments ?? value.notSeeTheirMoments),
    hideMyMoments: Boolean(value.hideMyMoments ?? value.hideMineFromThem ?? value.notAllowThemSeeMine)
  }
}

async function loadProfile() {
  loading.value = true
  error.value = ''
  const profileRequest = getChatUserProfile(userId.value, conversationId.value || undefined)
  const requests = [profileRequest, getBlockStatus(userId.value), getMomentPrivacy(userId.value), getMomentProfileSummary(userId.value)]
  if (conversationId.value) requests.push(getConversationPreferences(conversationId.value))
  const results = await Promise.allSettled(requests)

  if (results[0].status === 'fulfilled') {
    profile.value = results[0].value
  } else {
    profile.value = {
      userId: userId.value,
      nickname: route.query.nickname || '用户',
      avatarUrl: route.query.avatar || null,
      role: route.query.role || 'CUSTOMER'
    }
  }
  if (results[1].status === 'fulfilled') blockStatus.value = results[1].value
  if (results[2].status === 'fulfilled') momentPrivacy.value = normalizedPrivacy(results[2].value)
  if (results[3].status === 'fulfilled') momentSummary.value = results[3].value
  if (conversationId.value && results[4]?.status === 'fulfilled') preferences.value = results[4].value
  if (!profile.value) error.value = results[0].reason?.message || '用户资料加载失败'
  loading.value = false
}

async function startChat() {
  if (working.value) return
  working.value = true
  try {
    const conversation = shopProfile.value?.shopId
      ? await startDirectChatWithShop(shopProfile.value.shopId)
      : await startDirectChatWithUser(userId.value)
    router.push(`/customer/chat/${conversation.id}`)
  } catch (e) { notifyError(e.message) }
  finally { working.value = false }
}

async function toggleMute(event) {
  const checked = event.target.checked
  working.value = true
  try {
    await setConversationMute(conversationId.value, checked)
    preferences.value.muted = checked
  } catch (e) {
    event.target.checked = !checked
    notifyError(e.message)
  } finally { working.value = false }
}

async function updatePrivacy(field, checked) {
  working.value = true
  const previous = momentPrivacy.value[field]
  momentPrivacy.value[field] = checked
  try {
    const updated = await updateMomentPrivacy(userId.value, { [field]: checked })
    momentPrivacy.value = normalizedPrivacy(updated)
  } catch (e) {
    momentPrivacy.value[field] = previous
    notifyError(e.message)
  } finally { working.value = false }
}

async function toggleBlock() {
  if (working.value) return
  working.value = true
  try {
    if (blockStatus.value.blockedByMe) await unblockUser(userId.value)
    else await blockUser(userId.value)
    blockStatus.value.blockedByMe = !blockStatus.value.blockedByMe
  } catch (e) { notifyError(e.message) }
  finally { working.value = false }
}

function openUserMoments() {
  router.push({ path: `/customer/chat/user/${userId.value}/moments`, query: { nickname: profile.value.nickname || '用户' } })
}
function openShop() { router.push(`/customer/shop/${shopProfile.value.shopId}/menu`) }

onMounted(loadProfile)
</script>

<style scoped>
.profile-page { min-height: 100dvh; background: #f5f5f5; }
.profile-header { height: 60px; position: sticky; top: 0; z-index: 20; display: grid; grid-template-columns: 44px 1fr 44px; align-items: center; padding: 0 14px; background: rgba(245, 245, 245, 0.96); border-bottom: 1px solid #e8e8e8; }
.profile-header h1 { text-align: center; font-size: 18px; font-weight: 700; }
.header-btn { width: 40px; height: 40px; display: grid; place-items: center; border-radius: 50%; background: transparent; color: var(--color-text); }
.header-btn:hover { background: #ebebeb; }
.profile-content { max-width: 680px; margin: 0 auto; padding: 16px 16px 32px; }
.error-wrap { min-height: 50vh; display: grid; place-items: center; }

.identity-panel { padding: 28px 20px; display: flex; flex-direction: column; align-items: center; border-radius: 8px; background: #fff; text-align: center; }
.profile-avatar { width: 84px; height: 84px; display: grid; place-items: center; overflow: hidden; border-radius: 50%; background: #f0f1f2; color: #8b9097; }
.profile-avatar img { width: 100%; height: 100%; object-fit: cover; }
.name-row { max-width: 100%; display: flex; align-items: center; justify-content: center; gap: 8px; margin-top: 14px; }
.name-row h2 { min-width: 0; overflow: hidden; color: #242629; font-size: 21px; font-weight: 650; text-overflow: ellipsis; white-space: nowrap; }
.merchant-badge { padding: 3px 7px; border-radius: 5px; background: #ffe7cd; color: #b75e0a; font-size: 11px; font-weight: 700; }
.profile-line { max-width: 100%; margin-top: 8px; color: #62666d; font-size: 14px; line-height: 1.5; }
.blocked-note { margin-top: 8px; color: #969ba2; font-size: 13px; }

.shop-panel, .moments-panel { width: 100%; min-height: 72px; margin-top: 12px; display: flex; align-items: center; gap: 12px; padding: 12px 16px; border-radius: 8px; background: #fff; color: #33363a; text-align: left; }
.shop-panel:hover, .moments-panel:hover { background: #fcfcfc; }
.shop-logo { width: 46px; height: 46px; flex: 0 0 auto; display: grid; place-items: center; overflow: hidden; border-radius: 50%; background: #f1f2f3; color: #777c84; }
.shop-logo img { width: 100%; height: 100%; object-fit: cover; }
.shop-copy, .moments-copy { min-width: 0; flex: 1; display: flex; flex-direction: column; gap: 4px; }
.shop-copy strong, .moments-copy strong { font-size: 15px; font-weight: 650; }
.shop-copy small, .moments-copy small { overflow: hidden; color: #82878e; font-size: 12px; line-height: 1.35; text-overflow: ellipsis; white-space: nowrap; }
.moment-preview-stack { width: 62px; height: 38px; position: relative; flex: 0 0 62px; }
.preview-avatar { width: 36px; height: 36px; position: absolute; top: 1px; display: grid; place-items: center; overflow: hidden; border: 2px solid #fff; border-radius: 50%; background: #f0f1f2; color: #888d94; }
.preview-avatar img { width: 100%; height: 100%; object-fit: cover; }

.action-panel { margin-top: 12px; overflow: hidden; border-radius: 8px; background: #fff; }
.action-row { width: 100%; min-height: 56px; position: relative; display: flex; align-items: center; gap: 14px; padding: 0 16px; background: #fff; color: #34373b; text-align: left; }
.action-row + .action-row::before { content: ''; position: absolute; top: 0; right: 0; left: 51px; height: 1px; background: #eceeef; }
.action-row:hover { background: #fafafa; }
.action-row > svg { flex: 0 0 auto; color: #747981; }
.action-row .row-copy { min-width: 0; flex: 1; }
.row-copy strong { font-size: 15px; font-weight: 500; }
.action-row.danger, .action-row.danger > svg { color: #d5483d; }
.action-row.disabled, .action-row:disabled { opacity: 0.6; }
.switch-input { width: 1px; height: 1px; position: absolute; opacity: 0; pointer-events: none; }
.switch-track { width: 44px; height: 24px; position: relative; flex: 0 0 auto; border-radius: 12px; background: #c8cbd0; transition: background 0.18s; }
.switch-track i { width: 20px; height: 20px; position: absolute; top: 2px; left: 2px; border-radius: 50%; background: #fff; box-shadow: 0 1px 3px rgba(0, 0, 0, 0.25); transition: transform 0.18s; }
.switch-input:checked + .switch-track { background: #f28c28; }
.switch-input:checked + .switch-track i { transform: translateX(20px); }
.switch-input:focus-visible + .switch-track { outline: 2px solid #202124; outline-offset: 2px; }

@media (max-width: 480px) {
  .profile-header { height: calc(56px + env(safe-area-inset-top)); padding-top: env(safe-area-inset-top); }
  .profile-content { padding: 12px 12px max(24px, env(safe-area-inset-bottom)); }
  .identity-panel { padding-block: 24px; }
}
</style>
