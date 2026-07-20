<template>
  <div class="page-container feedback-page">
    <div class="page-header"><h1 class="page-title">反馈记录</h1></div>
    <section class="intro-card"><span><History :size="22" /></span><div><h2>我的反馈记录</h2><p>商家或平台回复后会在卡片中展示。</p></div><button class="refresh" title="刷新" @click="load"><RefreshCw :size="18" /></button></section>
    <LoadingSpinner v-if="loading" />
    <EmptyState v-else-if="error" icon="warning" :title="error" action-text="重试" @action="load" />
    <EmptyState v-else-if="!records.length" icon="message" title="暂无反馈记录" description="提交过的建议、问题和表扬会显示在这里。" />
    <section v-for="item in records" :key="item.id" class="feedback-card">
      <header><span><Clock3 :size="14" />{{ formatTime(item.createdAt) }}</span><span v-if="feedbackType(item.content)" class="type">{{ feedbackType(item.content) }}</span><span class="status" :class="{ replied: item.reply }">{{ item.reply ? '已回复' : '待处理' }}</span></header>
      <p class="content">{{ feedbackBody(item.content) }}</p>
      <div v-if="item.reply" class="reply"><strong><Store :size="15" />商家回复</strong><p>{{ item.reply }}</p></div>
    </section>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { Clock3, History, RefreshCw, Store } from 'lucide-vue-next'
import { getMyFeedback } from '../../api/customer'
import LoadingSpinner from '../../components/LoadingSpinner.vue'
import EmptyState from '../../components/EmptyState.vue'
const records = ref([]), loading = ref(true), error = ref('')
async function load() { loading.value = true; error.value = ''; try { const data = await getMyFeedback({ page: 1, pageSize: 100 }); records.value = data.records || data.items || data || [] } catch (e) { error.value = e.message } finally { loading.value = false } }
function feedbackType(value = '') { const match = value.match(/^\[([^\]]+)\]/); return match?.[1] || '' }
function feedbackBody(value = '') { return value.replace(/^\[[^\]]+\]\s*/, '') }
function formatTime(value = '') { return value.replace('T', ' ').slice(0, 16) }
onMounted(load)
</script>

<style scoped>
.feedback-page { max-width: 720px; }.intro-card,.feedback-card { margin-bottom: 12px; padding: 16px; background: #fff; border: 1px solid var(--color-line-light); border-radius: var(--radius-lg); }.intro-card { display: flex; align-items: center; gap: 12px; }.intro-card > span { width: 42px; height: 42px; flex: 0 0 auto; display: inline-flex; align-items: center; justify-content: center; color: var(--color-primary); background: var(--color-primary-bg); border-radius: var(--radius); }.intro-card div { flex: 1; }.intro-card h2 { font-size: 18px; }.intro-card p { color: var(--color-text-muted); font-size: 13px; }.refresh { width: 38px; height: 38px; display: inline-flex; align-items: center; justify-content: center; color: var(--color-text-muted); background: transparent; border-radius: 50%; }.feedback-card header { display: flex; align-items: center; gap: 7px; }.feedback-card header > span:first-child { flex: 1; display: inline-flex; align-items: center; gap: 4px; color: var(--color-text-muted); font-size: 12px; }.type,.status { padding: 3px 8px; border-radius: 7px; color: var(--color-primary); background: var(--color-primary-bg); font-size: 11px; font-weight: 700; }.status { color: var(--color-text-muted); background: var(--color-bg); }.status.replied { color: var(--color-success); background: #e8f5e9; }.content { margin-top: 12px; line-height: 1.55; font-size: 15px; font-weight: 600; }.reply { margin-top: 12px; padding: 12px; background: var(--color-primary-bg); border-radius: var(--radius); }.reply strong { display: flex; align-items: center; gap: 5px; color: var(--color-primary); font-size: 13px; }.reply p { margin-top: 5px; font-size: 14px; line-height: 1.5; }
</style>
