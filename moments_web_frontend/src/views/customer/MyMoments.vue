<template>
  <div class="page-container sub-page">
    <header class="page-header back-header">
      <button class="icon-btn" title="返回" @click="router.back()"><ArrowLeft :size="22" /></button>
      <h1 class="page-title">我的动态</h1>
      <router-link to="/customer/moments/new" class="publish-btn"><SquarePen :size="18" />发布</router-link>
    </header>
    <div class="segment" role="tablist">
      <button :class="{ active: tab === 'published' }" @click="switchTab('published')">已发布</button>
      <button :class="{ active: tab === 'drafts' }" @click="switchTab('drafts')">草稿</button>
    </div>
    <LoadingSpinner v-if="loading" />
    <EmptyState v-else-if="error" icon="warning" :title="error" action-text="重试" @action="load" />
    <div v-else-if="moments.length" class="moment-list">
      <MomentCard v-for="moment in moments" :key="moment.id" :moment="moment"
        @click="openMoment(moment)" @like="toggleLike(moment)" @comment="openMoment(moment)"
        @share="shareMoment = moment" @edit="editMoment(moment)" @delete="removeMoment(moment)" />
    </div>
    <EmptyState v-else icon="document" :title="tab === 'drafts' ? '暂无草稿' : '暂无已发布动态'" />
    <MomentShareDialog v-if="shareMoment" :moment="shareMoment" @close="shareMoment = null" />
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ArrowLeft, SquarePen } from 'lucide-vue-next'
import { deleteMoment, getMomentDrafts, getMoments, setMomentLike } from '../../api/moments'
import MomentCard from '../../components/MomentCard.vue'
import LoadingSpinner from '../../components/LoadingSpinner.vue'
import EmptyState from '../../components/EmptyState.vue'
import MomentShareDialog from '../../components/MomentShareDialog.vue'
import { confirmNotice } from '../../services/notice'

const router = useRouter()
const tab = ref('published')
const moments = ref([])
const loading = ref(true)
const error = ref('')
const shareMoment = ref(null)

async function load() {
  loading.value = true; error.value = ''
  try {
    const currentUser = JSON.parse(localStorage.getItem('user') || '{}')
    const data = tab.value === 'drafts'
      ? await getMomentDrafts({ page: 1, pageSize: 30 })
      : await getMoments({ authorId: currentUser.id, page: 1, pageSize: 30, consumeExposure: false })
    moments.value = data.records || []
  } catch (e) { error.value = e.message }
  finally { loading.value = false }
}

function switchTab(value) { tab.value = value; load() }
function openMoment(moment) { if (tab.value !== 'drafts') router.push(`/customer/moments/${moment.id}`) }
function editMoment(moment) { router.push(`/customer/moments/new?editId=${moment.id}`) }

async function toggleLike(moment) {
  const updated = await setMomentLike(moment.id, !moment.likedByMe)
  Object.assign(moment, updated)
}

async function removeMoment(moment) {
  if (!await confirmNotice(`确认删除${tab.value === 'drafts' ? '草稿' : '动态'}？`)) return
  await deleteMoment(moment.id)
  moments.value = moments.value.filter(item => item.id !== moment.id)
}

onMounted(load)
</script>

<style scoped>
.sub-page { max-width: 720px; }
.back-header { flex-wrap: nowrap; justify-content: flex-start; }
.icon-btn { width: 40px; height: 40px; flex: 0 0 auto; display: inline-flex; align-items: center; justify-content: center; background: transparent; border-radius: var(--radius); }
.publish-btn { margin-left: auto; min-height: 40px; display: inline-flex; align-items: center; gap: 5px; color: var(--color-primary); font-size: 14px; font-weight: 700; }
.segment { display: grid; grid-template-columns: 1fr 1fr; padding: 4px; margin-bottom: 12px; background: var(--color-line); border-radius: var(--radius); }
.segment button { min-height: 38px; background: transparent; color: var(--color-text-muted); border-radius: 6px; font-weight: 600; }
.segment button.active { background: #fff; color: var(--color-text); box-shadow: var(--shadow-sm); }
@media (max-width: 768px) { .sub-page { padding: max(10px, env(safe-area-inset-top)) 0 20px; } .back-header, .segment { margin-inline: 12px; } }
</style>
