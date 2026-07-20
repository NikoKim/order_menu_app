<template>
  <div class="page-container">
    <div class="page-header">
      <button class="back-btn" @click="$router.back()"><ArrowLeft :size="20" /></button>
      <h1 class="page-title">用户资料</h1>
    </div>

    <LoadingSpinner v-if="loading" />
    <div v-else-if="error" class="error-wrap">
      <EmptyState icon="warning" :title="error" action-text="重试" @action="loadProfile" />
    </div>
    <template v-else-if="profile">
      <!-- Avatar & name card -->
      <div class="profile-card">
        <div class="profile-avatar">
          <img v-if="profile.avatarUrl" :src="imageUrl(profile.avatarUrl)" alt="" />
          <span v-else>{{ profile.nickname?.charAt(0) || '?' }}</span>
        </div>
        <div class="profile-info">
          <h3 class="profile-name">{{ profile.nickname }}</h3>
          <span class="profile-role" :class="'role-' + (profile.role || '').toLowerCase()">{{ roleLabel(profile.role) }}</span>
        </div>
      </div>

      <!-- Contact -->
      <div class="info-card">
        <div class="info-card-title">联系方式</div>
        <p v-if="profile.phoneVisible" class="info-text">{{ profile.phone || '未设置' }}</p>
        <p v-else class="info-muted">对方未公开联系方式</p>
      </div>

      <!-- Families -->
      <div v-if="profile.joinedFamilies?.length" class="info-card">
        <div class="info-card-title">共同家庭</div>
        <div v-for="f in profile.joinedFamilies" :key="f.shopId" class="family-row">
          <House :size="18" />
          <span>{{ f.shopName }}</span>
        </div>
      </div>

      <!-- Shop info -->
      <div v-if="profile.shopProfile?.shopId" class="info-card">
        <div class="info-card-title">店铺信息</div>
        <div class="shop-row">
          <img v-if="profile.shopProfile.shopLogoUrl" :src="imageUrl(profile.shopProfile.shopLogoUrl)" alt="" class="shop-logo" />
          <div>
            <div class="shop-name">{{ profile.shopProfile.shopName }}</div>
            <div class="shop-desc">{{ profile.shopProfile.shopDescription }}</div>
          </div>
        </div>
      </div>

      <!-- Block button -->
      <button class="block-btn" @click="handleBlockToggle">
        {{ blockStatus?.blockedByMe ? '取消拉黑' : '拉黑用户' }}
      </button>
    </template>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { ArrowLeft, House } from 'lucide-vue-next'
import { getChatUserProfile, getBlockStatus, blockUser, unblockUser } from '../../api/messages'
import { imageUrl } from '../../composables/useImage'
import LoadingSpinner from '../../components/LoadingSpinner.vue'
import EmptyState from '../../components/EmptyState.vue'

const route = useRoute()
const profile = ref(null)
const blockStatus = ref(null)
const loading = ref(true)
const error = ref('')

async function loadProfile() {
  loading.value = true; error.value = ''
  try {
    const userId = Number(route.params.userId)
    profile.value = await getChatUserProfile(userId, route.query.conversationId || undefined)
    blockStatus.value = await getBlockStatus(userId)
  } catch (e) { error.value = e.message }
  finally { loading.value = false }
}

function roleLabel(r) {
  const map = { CUSTOMER: '顾客', SHOP: '商家', ADMIN: '管理员' }
  return map[r] || r
}

async function handleBlockToggle() {
  const userId = Number(route.params.userId)
  try {
    if (blockStatus.value?.blockedByMe) { await unblockUser(userId); blockStatus.value.blockedByMe = false }
    else { await blockUser(userId); blockStatus.value.blockedByMe = true }
  } catch (e) { alert(e.message) }
}

onMounted(loadProfile)
</script>

<style scoped>
.page-header { display: flex; align-items: center; gap: 10px; margin-bottom: 14px; }
.back-btn { width: 36px; height: 36px; display: inline-flex; align-items: center; justify-content: center; border-radius: 50%; background: transparent; color: var(--color-text); }
.page-title { font-size: 20px; font-weight: 700; }
.error-wrap { min-height: 30vh; display: flex; align-items: center; justify-content: center; }

.profile-card {
  display: flex; align-items: center; gap: 16px;
  background: #fff; border-radius: var(--radius-xl); padding: 20px; margin-bottom: 12px;
}
.profile-avatar {
  width: 72px; height: 72px; border-radius: 50%; flex-shrink: 0;
  background: var(--color-primary-bg); color: var(--color-primary);
  display: flex; align-items: center; justify-content: center;
  font-size: 32px; font-weight: 700; overflow: hidden;
}
.profile-avatar img { width: 100%; height: 100%; object-fit: cover; }
.profile-name { font-size: 20px; font-weight: 700; color: var(--color-text); }
.profile-role {
  display: inline-block; font-size: 13px; font-weight: 600; padding: 2px 10px;
  border-radius: 10px; margin-top: 4px;
}
.role-customer { background: #E3F2FD; color: #1565C0; }
.role-shop { background: var(--color-primary-bg); color: var(--color-primary); }
.role-admin { background: #FCE4EC; color: #C62828; }

.info-card {
  background: #fff; border-radius: var(--radius-xl); padding: 16px; margin-bottom: 12px;
}
.info-card-title {
  font-size: 13px; color: var(--color-text-muted); font-weight: 600;
  margin-bottom: 10px; text-transform: uppercase; letter-spacing: 0.5px;
}
.info-text { font-size: 15px; color: var(--color-text); }
.info-muted { font-size: 14px; color: var(--color-text-muted); }

.family-row { display: flex; align-items: center; gap: 8px; padding: 8px 0; font-size: 14px; color: var(--color-text); }
.family-row + .family-row { border-top: 1px solid var(--color-line); }

.shop-row { display: flex; gap: 12px; align-items: center; }
.shop-logo { width: 48px; height: 48px; border-radius: var(--radius); object-fit: cover; }
.shop-name { font-size: 15px; font-weight: 700; color: var(--color-text); }
.shop-desc { font-size: 13px; color: var(--color-text-muted); margin-top: 2px; }

.block-btn {
  display: block; width: 100%; padding: 14px; border: 1px solid var(--color-error);
  background: none; color: var(--color-error); border-radius: var(--radius-lg);
  font-size: 15px; font-weight: 600; cursor: pointer; margin-top: 16px;
  transition: all 0.2s;
}
.block-btn:hover { background: var(--color-error-light); }
</style>
