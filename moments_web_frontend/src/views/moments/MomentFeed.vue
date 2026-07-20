<template>
  <div class="moment-feed">
    <!-- Tab bar -->
    <div class="feed-tabs">
      <button class="feed-tab" :class="{ active: tab === 'RECOMMENDED' }" @click="switchTab('RECOMMENDED')">
        推荐
      </button>
      <button class="feed-tab" :class="{ active: tab === 'RELATED' }" @click="switchTab('RELATED')">
        相关
      </button>
      <div class="feed-actions">
        <button class="feed-icon-btn" title="搜索动态" aria-label="搜索动态" @click="router.push('/customer/moments/search')"><Search :size="20" /></button>
        <button class="feed-icon-btn" title="草稿箱" aria-label="草稿箱" @click="router.push('/customer/moments/drafts')"><FileText :size="20" /></button>
        <button class="feed-icon-btn primary" title="发布动态" aria-label="发布动态" @click="goNew"><SquarePen :size="20" /></button>
      </div>
    </div>

    <!-- New moments banner (dark pill) -->
    <div v-if="newCount > 0" class="new-banner" @click="loadFeed">
      <span>有 {{ newCount }} 条新动态</span>
    </div>

    <LoadingSpinner v-if="loading && !feed.length" variant="moment-feed" />
    <div v-else-if="error" class="error-wrap">
      <EmptyState icon="warning" :title="error" action-text="重试" @action="loadFeed" />
    </div>
    <template v-else>
      <div
        v-if="feed.length"
        class="feed-list"
        ref="feedContainer"
        @scroll="handleScroll"
      >
        <MomentCard v-for="m in feed" :key="m.id" :moment="m"
          @click="goDetail(m)" @like="handleLike(m)" @comment="goDetail(m)" @share="showShareSheet(m)"
          @reaction="showReactionPicker(m)" @topic="goTopic" @author="goAuthor" @image="(idx) => viewImages(m, idx)" @reference="goReference(m)"
          @edit="editMoment(m)" @delete="removeMoment(m)" />
        <LoadingSpinner v-if="loadingMore" variant="compact" />
        <div v-if="!hasMore && feed.length" class="feed-end">— 没有更多了 —</div>
      </div>
      <EmptyState v-else icon="document" title="暂无动态" description="发布你的第一条动态" />
    </template>

    <!-- Reaction picker -->
    <MomentReactionPicker
      v-if="reactionMoment"
      :reactions="reactionMoment.reactions || []"
      @select="handleReaction"
      @close="reactionMoment = null"
    />

    <!-- Image viewer -->
    <ImageViewer :visible="imageViewer.visible" :src="imageViewer.src" @close="imageViewer.visible = false" />

    <!-- Share sheet -->
    <div v-if="shareMoment" class="modal-overlay" @click.self="shareMoment = null">
      <div class="share-sheet">
        <h3 class="share-title">{{ shareMode === 'chat' ? '选择聊天' : '分享动态' }}</h3>
        <div class="share-preview">
          <img v-if="shareMoment.imageUrls?.[0]" :src="imageUrl(shareMoment.imageUrls[0])" alt="" />
          <span><small>{{ shareMoment.author?.nickname || '动态用户' }}</small><strong>{{ shareMoment.title || '分享了一条动态' }}</strong><p>{{ shareMoment.content }}</p></span>
        </div>
        <div v-if="shareMode === 'options'" class="share-methods">
          <button type="button" @click="openChatShare"><MessageCircle :size="22" /><span><strong>分享到聊天</strong><small>发送动态卡片给好友或群聊</small></span></button>
          <button type="button" @click="shareMomentExternally(shareMoment)"><Share2 :size="22" /><span><strong>分享到其他应用</strong><small>分享可点击进入动态详情的网页卡片</small></span></button>
        </div>
        <LoadingSpinner v-else-if="shareLoading" />
        <template v-else-if="shareMode === 'chat'">
          <ConversationTile v-for="c in shareConversations" :key="c.id" :conversation="c" :show-menu="false"
            @click="doShare(c)" />
          <p v-if="!shareConversations.length" class="share-empty">暂无可用会话</p>
        </template>
        <button class="share-cancel" @click="shareMode === 'chat' ? (shareMode = 'options') : (shareMoment = null)">{{ shareMode === 'chat' ? '返回' : '取消' }}</button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { nextTick, ref, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { FileText, MessageCircle, Search, Share2, SquarePen } from 'lucide-vue-next'
import { deleteMoment, getMoments, setMomentLike, setMomentReaction } from '../../api/moments'
import { getConversations, shareMomentToChat } from '../../api/messages'
import { imageUrl } from '../../composables/useImage'
import MomentCard from '../../components/MomentCard.vue'
import ImageViewer from '../../components/ImageViewer.vue'
import ConversationTile from '../../components/ConversationTile.vue'
import LoadingSpinner from '../../components/LoadingSpinner.vue'
import EmptyState from '../../components/EmptyState.vue'
import MomentReactionPicker from '../../components/MomentReactionPicker.vue'
import { shareMomentExternally } from '../../services/shareCard'
import { confirmNotice, notifyError, notifySuccess } from '../../services/notice'

const router = useRouter()
const tab = ref(sessionStorage.getItem('moment-feed-tab') || 'RECOMMENDED')
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
const shareMode = ref('options')

const feedContainer = ref(null)
const emit = defineEmits(['header-visibility'])
let lastScrollTop = 0
let scrollDirection = 0
let scrollDistance = 0

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
  sessionStorage.setItem('moment-feed-tab', t)
  sessionStorage.setItem('moment-feed-scroll', '0')
  page.value = 1
  loading.value = true
  loadFeed()
}

function handleScroll(e) {
  const el = e.target
  sessionStorage.setItem('moment-feed-scroll', String(el.scrollTop))
  if (el.scrollHeight - el.scrollTop - el.clientHeight < 300) loadMore()
  const delta = el.scrollTop - lastScrollTop
  const direction = Math.sign(delta)
  if (direction && direction !== scrollDirection) {
    scrollDirection = direction
    scrollDistance = 0
  }
  scrollDistance += Math.abs(delta)
  if (el.scrollTop < 28) emitHeaderVisibility('show')
  else if (direction > 0 && el.scrollTop > 88 && scrollDistance > 16) emitHeaderVisibility('hide')
  else if (direction < 0 && scrollDistance > 12) emitHeaderVisibility('show')
  lastScrollTop = el.scrollTop
}

function emitHeaderVisibility(state) {
  emit('header-visibility', state)
  scrollDistance = 0
}

function requireLogin() {
  if (localStorage.getItem('token')) return true
  router.push({ path: '/customer/login', query: { redirect: router.currentRoute.value.fullPath } })
  return false
}

async function handleLike(moment) {
  if (!requireLogin()) return
  try {
    const liked = !moment.likedByMe
    await setMomentLike(moment.id, liked)
    moment.likedByMe = liked
    moment.likeCount = (moment.likeCount || 0) + (liked ? 1 : -1)
  } catch (e) { notifyError(e.message) }
}

function showReactionPicker(moment) { if (requireLogin()) reactionMoment.value = moment }

async function handleReaction(emoji) {
  const moment = reactionMoment.value
  if (!moment) return
  try {
    const existing = moment.reactions?.find(r => r.emoji === emoji)
    const updated = await setMomentReaction(moment.id, emoji, !existing?.reactedByMe)
    Object.assign(moment, updated)
    reactionMoment.value = null
  } catch (e) { notifyError(e.message) }
}

function showShareSheet(moment) {
  shareMoment.value = moment
  shareMode.value = 'options'
}

async function openChatShare() {
  if (!requireLogin()) return
  shareMode.value = 'chat'
  shareLoading.value = true
  try { shareConversations.value = await getConversations() } catch { shareConversations.value = [] }
  finally { shareLoading.value = false }
}

async function doShare(conv) {
  const moment = shareMoment.value
  if (!moment) return
  try { await shareMomentToChat({ conversationId: conv.id, momentId: moment.id }); shareMoment.value = null; notifySuccess('已分享到聊天') }
  catch (e) { notifyError(e.message) }
}

function goNew() {
  if (requireLogin()) router.push('/customer/moments/new')
}
function editMoment(moment) { router.push(`/customer/moments/new?editId=${moment.id}`) }
async function removeMoment(moment) {
  if (!await confirmNotice('确认删除这条动态？')) return
  try {
    await deleteMoment(moment.id)
    feed.value = feed.value.filter(item => item.id !== moment.id)
  } catch (e) { notifyError(e.message) }
}
function goDetail(m) { router.push(`/customer/moments/${m.id}`) }
function goTopic(t) {
  if (!t?.id) return
  router.push({
    path: `/customer/moments/topics/${t.id}`,
    query: { name: t.name || undefined, userCount: t.userCount ?? undefined, momentCount: t.momentCount ?? undefined }
  })
}
function goAuthor(a) {
  if (!a?.id) return
  const prefix = router.currentRoute.value.path.startsWith('/shop/') ? '/shop' : '/customer'
  router.push({
    path: `${prefix}/chat/user/${a.id}`,
    query: { nickname: a.nickname || undefined, avatar: a.avatarUrl || undefined, role: a.role || undefined }
  })
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
async function checkForNewRelatedMoments() {
  if (tab.value !== 'RELATED' || !feed.value.length) {
    newCount.value = 0
    return
  }
  try {
    const res = await getMoments({ feed: 'RELATED', page: 1, pageSize: 20, consumeExposure: false })
    const visibleIds = new Set(feed.value.map(item => item.id))
    newCount.value = (res.records || []).filter(item => !visibleIds.has(item.id)).length
  } catch { }
}

onMounted(async () => {
  await loadFeed()
  await nextTick()
  const savedScroll = Number(sessionStorage.getItem('moment-feed-scroll') || 0)
  if (feedContainer.value && savedScroll > 0) {
    feedContainer.value.scrollTop = savedScroll
    lastScrollTop = savedScroll
  }
  if (!localStorage.getItem('token')) return
  pollTimer = setInterval(checkForNewRelatedMoments, 12000)
})
onUnmounted(() => {
  clearInterval(pollTimer)
})
</script>

<style scoped>
.moment-feed { height: 100%; min-height: 0; position: relative; display: flex; flex-direction: column; }
.feed-tabs { height: 58px; display: flex; gap: 4px; align-items: center; padding: 4px 8px 4px 10px; background: #fff; border-bottom: 1px solid #eee; }
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
.feed-actions { margin-left: auto; display: flex; align-items: center; gap: 2px; }
.feed-icon-btn { width: 40px; height: 40px; display: grid; place-items: center; border-radius: 8px; background: transparent; color: #686d74; }
.feed-icon-btn:hover { background: #f3f4f5; color: #202124; }
.feed-icon-btn.primary { color: #e47710; }

.new-banner {
  text-align: center; padding: 10px 24px;
  background: var(--color-dark-banner); color: #fff;
  font-weight: 600; font-size: 14px; border-radius: var(--radius-pill);
  cursor: pointer; margin: 0; display: inline-block;
  position: absolute; top: 66px; z-index: 10; left: 50%; transform: translateX(-50%);
}

.feed-list { min-height: 0; flex: 1; overflow-y: auto; overscroll-behavior: contain; }
.error-wrap { min-height: 20vh; display: flex; align-items: center; justify-content: center; }

.feed-loading { text-align: center; padding: 16px; color: var(--color-text-muted); font-size: 13px; }
.feed-end { text-align: center; padding: 20px; color: var(--color-text-light); font-size: 13px; }

/* Modals */
.modal-overlay {
  position: fixed; inset: 0; background: rgba(0,0,0,0.4);
  display: flex; align-items: center; justify-content: center; z-index: 200; padding: 24px;
}
.share-sheet {
  width: 100%; max-width: 400px; max-height: 70vh; overflow-y: auto;
  background: #fff; border-radius: var(--radius-xl); padding: 20px;
  box-shadow: var(--shadow-lg);
}
.share-title { font-size: 16px; font-weight: 700; margin-bottom: 14px; text-align: center; }
.share-preview { min-height: 76px; display: flex; gap: 11px; margin-bottom: 12px; padding: 10px; border: 1px solid #eceef1; border-radius: 8px; background: #f8f9fa; }
.share-preview > img { width: 72px; height: 72px; flex: 0 0 auto; border-radius: 6px; object-fit: cover; }
.share-preview > span { min-width: 0; display: flex; flex: 1; flex-direction: column; }
.share-preview small { color: var(--color-text-muted); font-size: 11px; }
.share-preview strong { margin-top: 2px; overflow: hidden; color: var(--color-text); font-size: 14px; text-overflow: ellipsis; white-space: nowrap; }
.share-preview p { display: -webkit-box; overflow: hidden; margin-top: 3px; color: var(--color-text-muted); font-size: 12px; line-height: 1.45; -webkit-box-orient: vertical; -webkit-line-clamp: 2; }
.share-methods { display: grid; gap: 8px; }
.share-methods button { min-height: 64px; display: flex; align-items: center; gap: 12px; padding: 10px 12px; border: 1px solid #eceef1; border-radius: 8px; background: #fff; color: var(--color-primary); text-align: left; }
.share-methods button:hover { border-color: var(--color-primary-border); background: var(--color-primary-bg); }
.share-methods button > span { min-width: 0; display: flex; flex-direction: column; }
.share-methods strong { color: var(--color-text); font-size: 14px; }
.share-methods small { margin-top: 2px; color: var(--color-text-muted); font-size: 11px; }
.share-empty { text-align: center; color: var(--color-text-muted); padding: 20px; font-size: 14px; }
.share-cancel {
  display: block; width: 100%; margin-top: 12px; padding: 12px;
  border: none; background: var(--color-bg); border-radius: var(--radius);
  font-size: 15px; font-weight: 600; color: var(--color-text-muted); cursor: pointer;
}
.share-cancel:hover { background: var(--color-line); }
</style>
