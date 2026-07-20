<template>
  <div class="user-moments-page">
    <header class="page-header">
      <button class="header-btn" title="返回" aria-label="返回" @click="router.back()"><ArrowLeft :size="22" /></button>
      <h1>{{ nickname }}的动态</h1>
      <span class="header-btn"></span>
    </header>

    <main class="moments-content">
      <LoadingSpinner v-if="loading" />
      <EmptyState v-else-if="error" icon="warning" :title="error" action-text="重试" @action="loadMoments" />
      <template v-else-if="moments.length">
        <MomentCard
          v-for="moment in moments"
          :key="moment.id"
          :moment="moment"
          @click="openMoment(moment)"
          @comment="openMoment(moment)"
          @like="toggleLike(moment)"
          @reaction="openMoment(moment)"
          @share="shareMoment = moment"
          @topic="openTopic"
          @image="index => viewImage(moment, index)"
        />
        <button v-if="hasMore" class="load-more" :disabled="loadingMore" @click="loadMore">
          {{ loadingMore ? '加载中...' : '加载更多' }}
        </button>
      </template>
      <EmptyState v-else icon="document" title="暂无可见动态" />
    </main>

    <ImageViewer :visible="viewer.visible" :src="viewer.src" @close="viewer.visible = false" />
    <MomentShareDialog v-if="shareMoment" :moment="shareMoment" @close="shareMoment = null" />
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowLeft } from 'lucide-vue-next'
import { getMoments, setMomentLike } from '../../api/moments'
import { imageUrl } from '../../composables/useImage'
import MomentCard from '../../components/MomentCard.vue'
import LoadingSpinner from '../../components/LoadingSpinner.vue'
import EmptyState from '../../components/EmptyState.vue'
import ImageViewer from '../../components/ImageViewer.vue'
import MomentShareDialog from '../../components/MomentShareDialog.vue'
import { notifyError } from '../../services/notice'

const route = useRoute()
const router = useRouter()
const moments = ref([])
const page = ref(1)
const hasMore = ref(false)
const loading = ref(true)
const loadingMore = ref(false)
const error = ref('')
const viewer = ref({ visible: false, src: '' })
const shareMoment = ref(null)
const nickname = computed(() => route.query.nickname || 'Ta')

async function loadMoments() {
  loading.value = true
  error.value = ''
  page.value = 1
  try {
    const data = await getMoments({ authorId: Number(route.params.userId), page: 1, pageSize: 20, consumeExposure: false })
    moments.value = data.records || []
    hasMore.value = data.hasMore === true
  } catch (e) { error.value = e.message }
  finally { loading.value = false }
}

async function loadMore() {
  loadingMore.value = true
  try {
    const nextPage = page.value + 1
    const data = await getMoments({ authorId: Number(route.params.userId), page: nextPage, pageSize: 20, consumeExposure: false })
    moments.value.push(...(data.records || []))
    page.value = nextPage
    hasMore.value = data.hasMore === true
  } catch (e) { notifyError(e.message) }
  finally { loadingMore.value = false }
}

async function toggleLike(moment) {
  try {
    const updated = await setMomentLike(moment.id, !moment.likedByMe)
    Object.assign(moment, updated)
  } catch (e) { notifyError(e.message) }
}

function openMoment(moment) { router.push(`/customer/moments/${moment.id}`) }
function openTopic(topic) {
  if (!topic?.id) return
  router.push({ path: `/customer/moments/topics/${topic.id}`, query: { name: topic.name || undefined, userCount: topic.userCount ?? undefined, momentCount: topic.momentCount ?? undefined } })
}
function viewImage(moment, index) {
  const url = moment.imageUrls?.[index]
  if (url) viewer.value = { visible: true, src: imageUrl(url) }
}

onMounted(loadMoments)
</script>

<style scoped>
.user-moments-page { min-height: 100dvh; background: #f5f5f5; }
.page-header { height: 60px; position: sticky; top: 0; z-index: 20; display: grid; grid-template-columns: 44px 1fr 44px; align-items: center; padding: 0 14px; background: rgba(245, 245, 245, 0.96); border-bottom: 1px solid #e8e8e8; }
.page-header h1 { min-width: 0; overflow: hidden; text-align: center; font-size: 18px; font-weight: 700; text-overflow: ellipsis; white-space: nowrap; }
.header-btn { width: 40px; height: 40px; display: grid; place-items: center; border-radius: 50%; background: transparent; color: var(--color-text); }
.header-btn:hover { background: #ebebeb; }
.moments-content { max-width: 680px; margin: 0 auto; padding: 14px 12px 32px; }
.load-more { width: 100%; min-height: 44px; border-radius: 7px; background: #fff; color: var(--color-primary); font-size: 14px; font-weight: 700; }
.load-more:disabled { opacity: 0.55; }
@media (max-width: 480px) {
  .page-header { height: calc(56px + env(safe-area-inset-top)); padding-top: env(safe-area-inset-top); }
  .moments-content { padding: 8px 0 max(24px, env(safe-area-inset-bottom)); }
}
</style>
