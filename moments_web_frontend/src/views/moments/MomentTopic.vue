<template>
  <div class="topic-page">
    <header class="topic-header">
      <button class="header-btn" title="返回" aria-label="返回" @click="router.back()"><ArrowLeft :size="22" /></button>
      <h1>#{{ topic.name }}</h1>
      <span class="header-btn"></span>
    </header>

    <main class="topic-content">
      <section class="topic-summary">
        <span class="topic-mark">#</span>
        <span class="topic-copy">
          <strong>{{ topic.name }}</strong>
          <small>{{ topic.userCount || 0 }} 人参与 · {{ topic.momentCount || 0 }} 条动态</small>
        </span>
      </section>

      <LoadingSpinner v-if="loading" />
      <EmptyState v-else-if="error" icon="warning" :title="error" action-text="重试" @action="load" />
      <template v-else-if="moments.length">
        <MomentCard
          v-for="moment in moments"
          :key="moment.id"
          :moment="moment"
          @click="openMoment(moment)"
          @comment="openMoment(moment)"
          @like="toggleLike(moment)"
          @reaction="openReaction(moment)"
          @share="openShare(moment)"
          @topic="openTopic"
          @author="openAuthor"
          @image="index => viewImage(moment, index)"
          @reference="openReference"
        />
        <button v-if="hasMore" class="load-more" :disabled="loadingMore" @click="loadMore">
          {{ loadingMore ? '加载中...' : '加载更多' }}
        </button>
      </template>
      <EmptyState v-else icon="document" title="该话题暂无动态" />
    </main>

    <MomentReactionPicker
      v-if="reactionMoment"
      :reactions="reactionMoment.reactions || []"
      @select="handleReaction"
      @close="reactionMoment = null"
    />

    <MomentShareDialog v-if="shareMoment" :moment="shareMoment" @close="shareMoment = null" />

    <ImageViewer :visible="viewer.visible" :images="viewer.images" :initial-index="viewer.index" @close="viewer.visible = false" />
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowLeft } from 'lucide-vue-next'
import { getTopicDetail, getTopicMoments, setMomentLike, setMomentReaction } from '../../api/moments'
import { imageUrl } from '../../composables/useImage'
import MomentCard from '../../components/MomentCard.vue'
import MomentReactionPicker from '../../components/MomentReactionPicker.vue'
import MomentShareDialog from '../../components/MomentShareDialog.vue'
import LoadingSpinner from '../../components/LoadingSpinner.vue'
import EmptyState from '../../components/EmptyState.vue'
import ImageViewer from '../../components/ImageViewer.vue'
import { notifyError } from '../../services/notice'
import { optimisticLike } from '../../services/optimisticLike'

const route = useRoute()
const router = useRouter()
const topic = ref({
  id: Number(route.params.id),
  name: route.query.name || '话题',
  userCount: Number(route.query.userCount) || 0,
  momentCount: Number(route.query.momentCount) || 0
})
const moments = ref([])
const page = ref(1)
const hasMore = ref(false)
const loading = ref(true)
const loadingMore = ref(false)
const error = ref('')
const reactionMoment = ref(null)
const shareMoment = ref(null)
const viewer = ref({ visible: false, images: [], index: 0 })

function requireLogin() {
  if (localStorage.getItem('token')) return true
  router.push({ path: '/customer/login', query: { redirect: route.fullPath } })
  return false
}

async function load() {
  loading.value = true
  error.value = ''
  page.value = 1
  const [detailResult, momentsResult] = await Promise.allSettled([
    getTopicDetail(Number(route.params.id)),
    getTopicMoments(Number(route.params.id), { page: 1, pageSize: 20 })
  ])
  if (detailResult.status === 'fulfilled') topic.value = detailResult.value
  if (momentsResult.status === 'fulfilled') {
    moments.value = momentsResult.value.records || []
    hasMore.value = momentsResult.value.hasMore === true
    if (!topic.value.momentCount) topic.value.momentCount = momentsResult.value.total || moments.value.length
  } else {
    error.value = momentsResult.reason?.message || '话题动态加载失败'
  }
  loading.value = false
}

async function loadMore() {
  loadingMore.value = true
  try {
    const nextPage = page.value + 1
    const data = await getTopicMoments(Number(route.params.id), { page: nextPage, pageSize: 20 })
    moments.value.push(...(data.records || []))
    page.value = nextPage
    hasMore.value = data.hasMore === true
  } catch (e) { notifyError(e.message) }
  finally { loadingMore.value = false }
}

async function toggleLike(moment) {
  if (!requireLogin()) return
  try {
    await optimisticLike(moment, active => setMomentLike(moment.id, active))
  } catch (e) { notifyError(e.message) }
}

function openReaction(moment) {
  if (requireLogin()) reactionMoment.value = moment
}
async function handleReaction(emoji) {
  const moment = reactionMoment.value
  if (!moment) return
  const current = moment.reactions?.find(item => item.emoji === emoji)
  try {
    const updated = await setMomentReaction(moment.id, emoji, !current?.reactedByMe)
    Object.assign(moment, updated)
    reactionMoment.value = null
  } catch (e) { notifyError(e.message) }
}

function openShare(moment) { shareMoment.value = moment }

function openMoment(moment) { router.push(`/customer/moments/${moment.id}`) }
function openTopic(nextTopic) {
  if (!nextTopic?.id || nextTopic.id === topic.value.id) return
  router.push({ path: `/customer/moments/topics/${nextTopic.id}`, query: { name: nextTopic.name || undefined, userCount: nextTopic.userCount ?? undefined, momentCount: nextTopic.momentCount ?? undefined } })
}
function openAuthor(author) {
  if (!author?.id) return
  router.push({ path: `/customer/chat/user/${author.id}`, query: { nickname: author.nickname || undefined, avatar: author.avatarUrl || undefined, role: author.role || undefined } })
}
function openReference(reference) { if (reference?.shopId) router.push(`/customer/shop/${reference.shopId}/menu`) }
function viewImage(moment, index) {
  const url = moment.imageUrls?.[index]
  if (url) viewer.value = { visible: true, images: moment.imageUrls.map(imageUrl), index }
}

onMounted(load)
</script>

<style scoped>
.topic-page { min-height: 100dvh; background: #f6f7f8; }
.topic-header { height: 60px; position: sticky; top: 0; z-index: 20; display: grid; grid-template-columns: 44px 1fr 44px; align-items: center; padding: 0 14px; border-bottom: 1px solid #eaecf0; background: rgba(255, 255, 255, 0.96); }
.topic-header h1 { min-width: 0; overflow: hidden; text-align: center; font-size: 18px; font-weight: 700; text-overflow: ellipsis; white-space: nowrap; }
.header-btn { width: 40px; height: 40px; display: grid; place-items: center; border-radius: 50%; background: transparent; color: #25272a; }
.header-btn:hover { background: #f1f2f4; }
.topic-content { max-width: 680px; margin: 0 auto; padding: 12px 12px 32px; }
.topic-summary { min-height: 80px; display: flex; align-items: center; gap: 13px; margin-bottom: 12px; padding: 16px; border: 1px solid #ffe2c5; border-radius: 8px; background: linear-gradient(110deg, #fff6ed, #fffbf7); }
.topic-mark { width: 48px; height: 48px; flex: 0 0 auto; display: grid; place-items: center; border-radius: 8px; background: #fff; color: #e77910; font-size: 25px; font-weight: 800; }
.topic-copy { min-width: 0; display: flex; flex-direction: column; gap: 4px; }
.topic-copy strong { overflow: hidden; color: #202124; font-size: 18px; font-weight: 800; text-overflow: ellipsis; white-space: nowrap; }
.topic-copy small { color: #7a7f85; font-size: 13px; }
.load-more { width: 100%; min-height: 44px; border-radius: 7px; background: #fff; color: var(--color-primary); font-size: 14px; font-weight: 700; }
@media (max-width: 480px) {
  .topic-header { height: calc(56px + env(safe-area-inset-top)); padding-top: env(safe-area-inset-top); }
  .topic-content { padding: 10px 0 max(24px, env(safe-area-inset-bottom)); }
  .topic-summary { margin: 0 12px 10px; }
}
</style>
