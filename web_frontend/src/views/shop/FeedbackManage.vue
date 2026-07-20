<template>
  <div class="page-container">
    <div class="page-header">
      <button class="back-btn" @click="$router.back()"><ArrowLeft :size="20" /></button>
      <h1 class="page-title">留言管理</h1>
    </div>

    <div class="filters">
      <select v-model="filterStatus" class="form-select" style="width:auto" @change="loadFeedback">
        <option value="">全部状态</option>
        <option value="PENDING">待回复</option>
        <option value="REPLIED">已回复</option>
        <option value="CLOSED">已关闭</option>
      </select>
    </div>

    <LoadingSpinner v-if="loading" />
    <div v-else-if="error" class="error-wrap"><EmptyState icon="warning" :title="error" action-text="重试" @action="loadFeedback" /></div>
    <template v-else>
      <div v-if="feedbacks.length" class="feedback-list">
        <div v-for="fb in feedbacks" :key="fb.id" class="card feedback-card">
          <div class="fb-top">
            <div class="fb-user">
              <span class="fb-avatar">{{ fb.userNickname?.charAt(0) || '?' }}</span>
              <span class="fb-nickname">{{ fb.userNickname }}</span>
            </div>
            <StatusBadge :status="fb.status" type="feedback" />
          </div>
          <p class="fb-content">{{ fb.content }}</p>
          <div class="fb-time">{{ fb.createdAt?.slice(0, 16) }}</div>
          <div v-if="fb.reply" class="fb-reply">
            <span class="fb-reply-label">我的回复：</span>{{ fb.reply }}
          </div>
          <div v-if="fb.status === 'PENDING'" class="fb-actions">
            <input v-model="replies[fb.id]" class="form-input" placeholder="输入回复..." style="flex:1" maxlength="1000" />
            <button class="btn btn-primary btn-sm" :disabled="!replies[fb.id]" @click="handleReply(fb)">回复</button>
          </div>
        </div>
      </div>
      <EmptyState v-else icon="message" title="暂无留言" />
      <Pagination :page="page" :page-size="pageSize" :total="total" @change="p => { page = p; loadFeedback() }" />
    </template>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ArrowLeft } from 'lucide-vue-next'
import { getShopFeedback, replyFeedback } from '../../api/shop'
import StatusBadge from '../../components/StatusBadge.vue'
import Pagination from '../../components/Pagination.vue'
import EmptyState from '../../components/EmptyState.vue'
import LoadingSpinner from '../../components/LoadingSpinner.vue'

const feedbacks = ref([])
const loading = ref(true)
const error = ref('')
const page = ref(1)
const pageSize = ref(10)
const total = ref(0)
const filterStatus = ref('')
const replies = reactive({})

async function loadFeedback() {
  loading.value = true
  error.value = ''
  try {
    const res = await getShopFeedback({ page: page.value, pageSize: pageSize.value, status: filterStatus.value || undefined })
    feedbacks.value = res.records || []
    total.value = res.total || 0
    feedbacks.value.forEach(fb => { if (!replies[fb.id]) replies[fb.id] = '' })
  } catch (e) { error.value = e.message }
  finally { loading.value = false }
}

async function handleReply(fb) {
  try {
    await replyFeedback(fb.id, replies[fb.id])
    replies[fb.id] = ''
    loadFeedback()
  } catch (e) { alert(e.message) }
}

onMounted(loadFeedback)
</script>

<style scoped>
.page-header { display: flex; align-items: center; gap: 10px; margin-bottom: 16px; }
.back-btn { width: 36px; height: 36px; display: inline-flex; align-items: center; justify-content: center; border-radius: 50%; background: transparent; color: var(--color-text); }
.page-container { max-width: 720px; }
.filters { margin-bottom: 16px; }
.error-wrap { min-height: 30vh; display: flex; align-items: center; justify-content: center; }
.feedback-list { display: flex; flex-direction: column; gap: 12px; }
.fb-top { display: flex; justify-content: space-between; align-items: center; margin-bottom: 8px; }
.fb-user { display: flex; align-items: center; gap: 8px; }
.fb-avatar {
  width: 32px; height: 32px; border-radius: 50%;
  background: var(--color-primary-bg); color: var(--color-primary);
  display: flex; align-items: center; justify-content: center;
  font-weight: 700; font-size: 14px;
}
.fb-nickname { font-weight: 600; font-size: 14px; }
.fb-content { font-size: 14px; margin-bottom: 8px; line-height: 1.6; }
.fb-time { font-size: 12px; color: var(--color-text-light); }
.fb-reply { margin-top: 10px; padding: 10px; background: var(--color-bg); border-radius: var(--radius); font-size: 13px; }
.fb-reply-label { font-weight: 600; color: var(--color-primary); }
.fb-actions { display: flex; gap: 8px; margin-top: 10px; align-items: center; }
</style>
