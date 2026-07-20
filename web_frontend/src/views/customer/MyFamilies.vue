<template>
  <div class="page-container sub-page">
    <div class="page-header">
      <button class="back-btn" @click="$router.back()"><ArrowLeft :size="20" /></button>
      <h1 class="page-title">我的家庭</h1>
    </div>

    <div class="join-section card">
      <div class="form-group">
        <label class="form-label">加入家庭（输入6位家庭号）</label>
        <div class="join-row">
          <input v-model="familyCode" class="form-input" placeholder="6位家庭号" maxlength="6" style="flex:1" />
          <button class="btn btn-primary" :disabled="!familyCode || joining" @click="handleJoin">
            {{ joining ? '加入中...' : '加入' }}
          </button>
        </div>
        <p v-if="joinError" class="form-error">{{ joinError }}</p>
        <p v-if="joinOk" style="color:var(--color-success);font-weight:600">申请已提交！</p>
      </div>
    </div>

    <LoadingSpinner v-if="loading" />
    <div v-else-if="error" class="error-wrap"><EmptyState icon="warning" :title="error" action-text="重试" @action="loadFamilies" /></div>
    <template v-else>
      <div v-if="families.length" class="family-list">
        <div v-for="f in families" :key="f.shopId" class="card family-card">
          <div class="family-info">
            <div class="family-avatar">
              <img v-if="f.familyImageUrl" :src="imageUrl(f.familyImageUrl)" alt="" />
              <UsersRound v-else :size="22" />
            </div>
            <div>
              <div class="family-name">{{ f.familyName || f.shopName }}</div>
              <div class="family-code">家庭号：{{ f.familyCode }}</div>
              <StatusBadge :status="f.memberStatus" type="family" style="margin-top:4px" />
            </div>
          </div>
          <button v-if="f.memberStatus === 'APPROVED'" class="btn btn-ghost btn-sm" style="color:var(--color-error)"
            @click="handleLeave(f)">退出</button>
        </div>
      </div>
      <EmptyState v-else title="暂无家庭" description="输入家庭号加入一个家庭" />
    </template>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ArrowLeft, UsersRound } from 'lucide-vue-next'
import { getMyFamilies, joinFamily, leaveFamily } from '../../api/customer'
import { imageUrl } from '../../composables/useImage'
import StatusBadge from '../../components/StatusBadge.vue'
import EmptyState from '../../components/EmptyState.vue'
import LoadingSpinner from '../../components/LoadingSpinner.vue'

const families = ref([])
const loading = ref(true)
const error = ref('')
const familyCode = ref('')
const joining = ref(false)
const joinError = ref('')
const joinOk = ref(false)

async function loadFamilies() {
  loading.value = true
  error.value = ''
  try { families.value = (await getMyFamilies()).families || [] }
  catch (e) { error.value = e.message }
  finally { loading.value = false }
}

async function handleJoin() {
  joinError.value = ''
  joinOk.value = false
  joining.value = true
  try {
    await joinFamily(familyCode.value.trim().toUpperCase())
    joinOk.value = true
    familyCode.value = ''
    loadFamilies()
  } catch (e) { joinError.value = e.message }
  finally { joining.value = false }
}

async function handleLeave(f) {
  if (!confirm('确认退出该家庭？')) return
  try { await leaveFamily(f.shopId); loadFamilies() } catch (e) { alert(e.message) }
}

onMounted(loadFamilies)
</script>

<style scoped>
.sub-page { max-width: 720px; }
.page-header { display: flex; align-items: center; gap: 10px; margin-bottom: 16px; }
.back-btn { width: 36px; height: 36px; display: inline-flex; align-items: center; justify-content: center; border-radius: 50%; background: transparent; color: var(--color-text); }
.join-section { margin-bottom: 16px; }
.join-row { display: flex; gap: 8px; }
.error-wrap { min-height: 30vh; display: flex; align-items: center; justify-content: center; }
.family-list { display: flex; flex-direction: column; gap: 10px; }
.family-card { display: flex; justify-content: space-between; align-items: center; }
.family-info { display: flex; align-items: center; gap: 12px; }
.family-avatar {
  width: 48px; height: 48px; border-radius: 50%;
  background: var(--color-bg); display: flex; align-items: center; justify-content: center;
  font-size: 24px; overflow: hidden;
}
.family-avatar img { width: 100%; height: 100%; object-fit: cover; }
.family-name { font-weight: 700; font-size: 15px; }
.family-code { font-size: 13px; color: var(--color-text-muted); }

@media (max-width: 768px) {
  .sub-page { padding-top: max(12px, env(safe-area-inset-top)); }
}
</style>
