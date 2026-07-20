<template>
  <section class="subpage">
    <header class="subpage-header">
      <button class="icon-button" aria-label="返回" @click="router.back()"><ArrowLeft :size="22" /></button>
      <div class="search-box">
        <Search :size="19" aria-hidden="true" />
        <input ref="input" v-model="keyword" type="search" placeholder="搜索标题、正文或话题" @input="queueSearch" />
        <button v-if="keyword" aria-label="清空" @click="clear"><X :size="18" /></button>
      </div>
    </header>

    <div class="subpage-body">
      <LoadingSpinner v-if="loading" text="搜索中..." />
      <template v-else-if="results.length">
        <MomentCard v-for="item in results" :key="item.id" :moment="item" @click="open(item)" @comment="open(item)" @author="openAuthor" @topic="openTopic" />
      </template>
      <EmptyState v-else icon="document" :title="keyword ? '没有找到相关动态' : '搜索动态'" :description="keyword ? '换一个关键词试试' : '输入标题、正文或话题关键词开始查找'" />
    </div>
  </section>
</template>

<script setup>
import { nextTick, onMounted, onUnmounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ArrowLeft, Search, X } from 'lucide-vue-next'
import { getMoments } from '../../api/moments'
import EmptyState from '../../components/EmptyState.vue'
import LoadingSpinner from '../../components/LoadingSpinner.vue'
import MomentCard from '../../components/MomentCard.vue'

const router = useRouter()
const input = ref(null)
const keyword = ref('')
const results = ref([])
const loading = ref(false)
let timer

function queueSearch() {
  window.clearTimeout(timer)
  if (!keyword.value.trim()) { results.value = []; loading.value = false; return }
  loading.value = true
  timer = window.setTimeout(search, 350)
}
async function search() {
  const value = keyword.value.trim()
  if (!value) return
  try {
    const data = await getMoments({ feed: 'SEARCH', keyword: value, page: 1, pageSize: 30 })
    results.value = data.records || []
  } catch { results.value = [] }
  finally { loading.value = false }
}
function clear() { keyword.value = ''; results.value = []; nextTick(() => input.value?.focus()) }
function open(item) { router.push(`/customer/moments/${item.id}`) }
function openAuthor(author) { if (author?.id) router.push(`/customer/chat/user/${author.id}`) }
function openTopic(topic) {
  if (!topic?.id) return
  router.push({ path: `/customer/moments/topics/${topic.id}`, query: { name: topic.name || undefined, userCount: topic.userCount ?? undefined, momentCount: topic.momentCount ?? undefined } })
}

onMounted(() => input.value?.focus())
onUnmounted(() => window.clearTimeout(timer))
</script>

<style scoped>
.subpage { min-height: 100dvh; background: #f6f7f8; }
.subpage-header { min-height: 60px; position: sticky; top: 0; z-index: 20; display: flex; align-items: center; gap: 8px; padding: 9px 12px; background: #fff; border-bottom: 1px solid #eaecf0; }
.icon-button { width: 40px; height: 40px; flex: 0 0 auto; display: grid; place-items: center; border-radius: 8px; background: transparent; color: #202124; }
.search-box { height: 42px; flex: 1; display: flex; align-items: center; gap: 8px; padding: 0 12px; border: 1px solid transparent; border-radius: 8px; background: #f2f3f5; color: #858a91; transition: border-color 0.2s, background-color 0.2s, box-shadow 0.2s; }
.search-box:focus-within { border-color: var(--color-primary); background: #fff; box-shadow: 0 0 0 3px rgba(240, 126, 35, 0.12); }
.search-box input { min-width: 0; flex: 1; border: 0; outline: none; background: transparent; color: #202124; font-size: 15px; }
.search-box button { width: 30px; height: 30px; display: grid; place-items: center; border-radius: 50%; background: transparent; color: #858a91; }
.subpage-body { padding: 12px; }
.subpage-body :deep(.moment-card) { margin-bottom: 10px; }
@media (max-width: 480px) { .subpage-header { padding-top: max(9px, env(safe-area-inset-top)); } .subpage-body { padding: 10px 0; } }
</style>
