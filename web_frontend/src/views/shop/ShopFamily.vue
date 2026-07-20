<template>
  <div class="page-container">
    <div class="page-header">
      <button class="back-btn" @click="$router.back()"><ArrowLeft :size="20" /></button>
      <h1 class="page-title">家庭管理</h1>
    </div>

    <div class="filters">
      <select v-model="filterStatus" class="form-select" style="width:auto" @change="loadMembers">
        <option value="">全部状态</option>
        <option value="PENDING">待审核</option>
        <option value="APPROVED">已通过</option>
      </select>
    </div>

    <LoadingSpinner v-if="loading" />
    <div v-else-if="error" class="error-wrap"><EmptyState icon="warning" :title="error" action-text="重试" @action="loadMembers" /></div>
    <template v-else>
      <div v-if="members.length" class="member-list">
        <div v-for="m in members" :key="m.id" class="card member-card">
          <div class="member-info">
            <span class="member-avatar">{{ m.nickname?.charAt(0) || '?' }}</span>
            <div>
              <div class="member-name">{{ m.nickname }}</div>
              <div class="member-phone" v-if="m.phone">{{ m.phone }}</div>
            </div>
          </div>
          <div class="member-right">
            <StatusBadge :status="m.status" type="family" />
            <div class="member-actions" v-if="m.status === 'PENDING'">
              <button class="btn btn-primary btn-sm" @click="handleApprove(m)">通过</button>
              <button class="btn btn-ghost btn-sm" style="color:var(--color-error)" @click="handleReject(m)">拒绝</button>
            </div>
          </div>
        </div>
      </div>
      <EmptyState v-else title="暂无家庭成员" />
    </template>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ArrowLeft } from 'lucide-vue-next'
import { getFamilyMembers, approveFamilyMember, removeFamilyMember } from '../../api/shop'
import StatusBadge from '../../components/StatusBadge.vue'
import EmptyState from '../../components/EmptyState.vue'
import LoadingSpinner from '../../components/LoadingSpinner.vue'

const members = ref([])
const loading = ref(true)
const error = ref('')
const filterStatus = ref('')

async function loadMembers() {
  loading.value = true
  error.value = ''
  try {
    members.value = await getFamilyMembers({ status: filterStatus.value || undefined })
  } catch (e) { error.value = e.message }
  finally { loading.value = false }
}

async function handleApprove(m) {
  try { await approveFamilyMember(m.id); loadMembers() } catch (e) { alert(e.message) }
}

async function handleReject(m) {
  if (!confirm('确认拒绝该成员？')) return
  try { await removeFamilyMember(m.id); loadMembers() } catch (e) { alert(e.message) }
}

onMounted(loadMembers)
</script>

<style scoped>
.page-header { display: flex; align-items: center; gap: 10px; margin-bottom: 16px; }
.back-btn { width: 36px; height: 36px; display: inline-flex; align-items: center; justify-content: center; border-radius: 50%; background: transparent; color: var(--color-text); }
.page-container { max-width: 720px; }
.filters { margin-bottom: 16px; }
.error-wrap { min-height: 30vh; display: flex; align-items: center; justify-content: center; }
.member-list { display: flex; flex-direction: column; gap: 10px; }
.member-card { display: flex; justify-content: space-between; align-items: center; }
.member-info { display: flex; align-items: center; gap: 10px; }
.member-avatar {
  width: 40px; height: 40px; border-radius: 50%;
  background: var(--color-primary-bg); color: var(--color-primary);
  display: flex; align-items: center; justify-content: center;
  font-weight: 700; font-size: 16px;
}
.member-name { font-weight: 600; font-size: 14px; }
.member-phone { font-size: 12px; color: var(--color-text-light); }
.member-right { display: flex; align-items: center; gap: 10px; }
.member-actions { display: flex; gap: 6px; }
</style>
