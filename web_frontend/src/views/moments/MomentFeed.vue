<template>
  <div>
    <!-- Tab bar -->
    <div class="feed-tabs">
      <button class="feed-tab" :class="{ active: tab === 'RECOMMENDED' }" @click="switchTab('RECOMMENDED')">
        推荐
      </button>
      <button class="feed-tab" :class="{ active: tab === 'RELATED' }" @click="switchTab('RELATED')">
        相关
      </button>
      <div style="flex:1"></div>
      <button class="feed-compose-btn" @click="goNew"><SquarePen :size="17" />发布</button>
    </div>

    <!-- New moments banner (dark pill) -->
    <div v-if="newCount > 0" class="new-banner" @click="loadFeed">
      <span>有 {{ newCount }} 条新动态</span>
    </div>

    <LoadingSpinner v-if="loading && !feed.length" />
    <div v-else-if="error" class="error-wrap">
      <EmptyState icon="warning" :title="error" action-text="重试" @action="loadFeed" />
    </div>
    <template v-else>
      <div v-if="feed.length" class="feed-list" ref="feedContainer" @scroll="handleScroll">
        <MomentCard v-for="m in feed" :key="m.id" :moment="m"
          @click="goDetail(m)" @like="handleLike(m)" @comment="goDetail(m)" @share="showShareSheet(m)"
          @reaction="showReactionPicker(m)" @topic="goTopic" @author="goAuthor" @image="(idx) => viewImages(m, idx)" @reference="goReference(m)" />
        <div v-if="loadingMore" class="feed-loading">加载中...</div>
        <div v-if="!hasMore && feed.length" class="feed-end">— 没有更多了 —</div>
      </div>
      <EmptyState v-else icon="document" title="暂无动态" description="发布你的第一条动态" />
    </template>

    <!-- Reaction picker -->
    <div v-if="reactionMoment" class="modal-overlay" @click.self="reactionMoment = null">
      <div class="reaction-panel">
        <button v-for="e in emojis" :key="e" class="reaction-btn"
          :class="{ active: reactionMoment.reactions?.find(r => r.emoji === e)?.reactedByMe }"
          @click="handleReaction(e)">{{ e }}</button>
      </div>
    </div>

    <!-- Image viewer -->
    <ImageViewer :visible="imageViewer.visible" :src="imageViewer.src" @close="imageViewer.visible = false" />

    <!-- Share sheet -->
    <div v-if="shareMoment" class="modal-overlay" @click.self="shareMoment = null">
      <div class="share-sheet">
        <h3 class="share-title">分享到聊天</h3>
        <LoadingSpinner v-if="shareLoading" />
        <template v-else>
          <ConversationTile v-for="c in shareConversations" :key="c.id" :conversation="c" :show-menu="false"
            @click="doShare(c)" />
          <p v-if="!shareConversations.length" class="share-empty">暂无可用会话</p>
        </template>
        <button class="share-cancel" @click="shareMoment = null">取消</button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { SquarePen } from 'lucide-vue-next'
import { getMoments, setMomentLike, addMomentReaction, getMomentUnreadCount } from '../../api/moments'
import { getConversations, shareMomentToChat } from '../../api/messages'
import { imageUrl } from '../../composables/useImage'
import MomentCard from '../../components/MomentCard.vue'
import ImageViewer from '../../components/ImageViewer.vue'
import ConversationTile from '../../components/ConversationTile.vue'
import LoadingSpinner from '../../components/LoadingSpinner.vue'
import EmptyState from '../../components/EmptyState.vue'

const router = useRouter()
const tab = ref('RECOMMENDED')
const feed = ref([])
const loading = ref(true)
const error = ref('')
const page = ref(1)
const hasMore = ref(true)
const loadingMore = ref(false)
const newCount = ref(0)

const imageViewer = ref({ visible: false, src: '' })
const reactionMoment = ref(null)
const shareMoment = ref(null)
const shareConversations = ref([])
const shareLoading = ref(false)

const emojis = ['👍', '❤️', '😄', '😢', '😮', '🔥', '👏', '🎉']

const feedContainer = ref(null)

async function loadFeed() {
  error.value = ''
  try {
    const role = JSON.parse(localStorage.getItem('user') || '{}')?.role
    const res = await getMoments({
      feed: tab.value,
      page: page.value,
      pageSize: 20,
      consumeExposure: role === 'CUSTOMER'
    })
    feed.value = res.records || []
    hasMore.value = res.hasMore !== false
    newCount.value = 0
  } catch (e) { error.value = e.message }
  finally { loading.value = false }
}

async function loadMore() {
  if (!hasMore.value || loadingMore.value) return
  loadingMore.value = true
  try {
    const res = await getMoments({ feed: tab.value, page: page.value + 1, pageSize: 20 })
    const records = res.records || []
    feed.value.push(...records)
    hasMore.value = res.hasMore !== false
    page.value++
  } catch { }
  finally { loadingMore.value = false }
}

function switchTab(t) {
  if (tab.value === t) return
  tab.value = t
  page.value = 1
  loading.value = true
  loadFeed()
}

function handleScroll(e) {
  const el = e.target
  if (el.scrollHeight - el.scrollTop - el.clientHeight < 300) loadMore()
}

async function handleLike(moment) {
  try {
    const liked = !moment.likedByMe
    await setMomentLike(moment.id, liked)
    moment.likedByMe = liked
    moment.likeCount = (moment.likeCount || 0) + (liked ? 1 : -1)
  } catch (e) { alert(e.message) }
}

function showReactionPicker(moment) { reactionMoment.value = moment }

async function handleReaction(emoji) {
  const moment = reactionMoment.value
  if (!moment) return
  try {
    const existing = moment.reactions?.find(r => r.emoji === emoji)
    await addMomentReaction(moment.id, emoji)
    if (existing?.reactedByMe) {
      existing.reactedByMe = false
      existing.count = Math.max(0, (existing.count || 1) - 1)
    } else if (existing) {
      existing.reactedByMe = true
      existing.count = (existing.count || 0) + 1
    } else {
      if (!moment.reactions) moment.reactions = []
      moment.reactions.push({ emoji, count: 1, reactedByMe: true })
    }
    reactionMoment.value = null
  } catch (e) { alert(e.message) }
}

async function showShareSheet(moment) {
  shareMoment.value = moment
  shareLoading.value = true
  try { shareConversations.value = await getConversations() } catch { shareConversations.value = [] }
  finally { shareLoading.value = false }
}

async function doShare(conv) {
  const moment = shareMoment.value
  if (!moment) return
  try { await shareMomentToChat({ conversationId: conv.id, momentId: moment.id }); shareMoment.value = null }
  catch (e) { alert(e.message) }
}

function goNew() { router.push(router.currentRoute.value.path.replace(/\/chat$/, '/moments/new')) }
function goDetail(m) { router.push(router.currentRoute.value.path.replace(/\/chat$/, '/moments/') + m.id) }
function goTopic(t) { }
function goAuthor(a) {
  if (!a?.id) return
  const prefix = router.currentRoute.value.path.startsWith('/shop/') ? '/shop' : '/customer'
  router.push(`${prefix}/chat/user/${a.id}`)
}
function viewImages(moment, idx) {
  const urls = moment?.imageUrls
  if (!urls?.[idx]) return
  imageViewer.value = { visible: true, src: imageUrl(urls[idx]) }
}
function goReference(ref) {
  if (ref?.shopId) router.push(`/customer/shop/${ref.shopId}/menu`)
}

let pollTimer = null
onMounted(() => {
  loadFeed()
  pollTimer = setInterval(async () => {
    try { const res = await getMomentUnreadCount(); newCount.value = res.relatedUnreadCount || 0 } catch { }
  }, 5000)
})
onUnmounted(() => clearInterval(pollTimer))
</script>

<style scoped>
.feed-tabs { display: flex; gap: 4px; align-items: center; margin-bottom: 14px; }
.feed-tab {
  padding: 8px 16px; border: none; background: none;
  font-size: 16px; font-weight: 500; color: var(--color-text-muted);
  cursor: pointer; border-radius: var(--radius); transition: all 0.2s;
  position: relative;
}
.feed-tab.active { color: var(--color-text); font-weight: 800; }
.feed-tab.active::after {
  content: ''; position: absolute; bottom: 0; left: 50%; transform: translateX(-50%);
  width: 20px; height: 3px; background: var(--color-primary); border-radius: 2px;
}
.feed-compose-btn {
  padding: 8px 16px; border: 1px solid var(--color-primary); background: none;
  color: var(--color-primary); font-size: 13px; font-weight: 600;
  border-radius: var(--radius-pill); cursor: pointer; transition: all 0.2s;
}
.feed-compose-btn:hover { background: var(--color-primary-bg); }

.new-banner {
  text-align: center; padding: 10px 24px;
  background: var(--color-dark-banner); color: #fff;
  font-weight: 600; font-size: 14px; border-radius: var(--radius-pill);
  cursor: pointer; margin-bottom: 12px; display: inline-block;
  position: sticky; top: 8px; z-index: 10; left: 50%; transform: translateX(-50%);
}

.feed-list { max-height: calc(100vh - 220px); overflow-y: auto; }
.error-wrap { min-height: 20vh; display: flex; align-items: center; justify-content: center; }

.feed-loading { text-align: center; padding: 16px; color: var(--color-text-muted); font-size: 13px; }
.feed-end { text-align: center; padding: 20px; color: var(--color-text-light); font-size: 13px; }

/* Modals */
.modal-overlay {
  position: fixed; inset: 0; background: rgba(0,0,0,0.4);
  display: flex; align-items: center; justify-content: center; z-index: 200; padding: 24px;
}
.reaction-panel {
  display: grid; grid-template-columns: repeat(4, 1fr); gap: 10px;
  background: #fff; border-radius: var(--radius-xl); padding: 20px;
  box-shadow: var(--shadow-lg);
}
.reaction-btn {
  font-size: 32px; padding: 12px; border: 2px solid transparent;
  background: var(--color-bg); border-radius: var(--radius-lg); cursor: pointer;
  transition: all 0.15s;
}
.reaction-btn:hover { transform: scale(1.1); }
.reaction-btn.active { border-color: var(--color-primary); background: var(--color-primary-bg); }

.share-sheet {
  width: 100%; max-width: 400px; max-height: 70vh; overflow-y: auto;
  background: #fff; border-radius: var(--radius-xl); padding: 20px;
  box-shadow: var(--shadow-lg);
}
.share-title { font-size: 16px; font-weight: 700; margin-bottom: 14px; text-align: center; }
.share-empty { text-align: center; color: var(--color-text-muted); padding: 20px; font-size: 14px; }
.share-cancel {
  display: block; width: 100%; margin-top: 12px; padding: 12px;
  border: none; background: var(--color-bg); border-radius: var(--radius);
  font-size: 15px; font-weight: 600; color: var(--color-text-muted); cursor: pointer;
}
.share-cancel:hover { background: var(--color-line); }
</style>
