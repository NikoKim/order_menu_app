<template>
  <div class="moment-feed">
    <!-- Tab bar -->
    <div class="feed-tabs">
      <button class="feed-tab" :class="{ active: tab === 'RECOMMENDED' }" @click="switchTab('RECOMMENDED')">
        推荐
      </button>
      <button class="feed-tab" :class="{ active: tab === 'HOT' }" @click="switchTab('HOT')">
        热门
      </button>
      <button class="feed-tab" :class="{ active: tab === 'RELATED' }" @click="switchTab('RELATED')">
        <span>相关</span>
        <i v-if="relatedUnread" class="feed-tab-badge">{{ badge(relatedUnread) }}</i>
      </button>
      <div class="feed-actions">
        <button class="feed-icon-btn" title="搜索动态" aria-label="搜索动态" @click="router.push('/customer/moments/search')"><Search :size="20" /></button>
        <button class="feed-icon-btn" title="草稿箱" aria-label="草稿箱" @click="router.push('/customer/moments/drafts')"><FileText :size="20" /></button>
        <button class="feed-icon-btn primary" title="发布动态" aria-label="发布动态" @click="goNew"><SquarePen :size="20" /></button>
      </div>
    </div>

    <!-- New moments banner (dark pill) -->
    <div v-if="newCount > 0" class="new-banner" @click="refreshFeed">
      <span>有 {{ newCount }} 条新动态</span>
    </div>

    <div v-if="loading && feed.length" class="feed-refresh-status" role="status">
      <LoaderCircle :size="16" />
      <span>正在刷新动态</span>
    </div>

    <LoadingSpinner v-if="loading && !feed.length" variant="moment-feed" />
    <div v-else-if="error && !feed.length" class="error-wrap">
      <EmptyState icon="warning" :title="error" action-text="重试" @action="loadFeed" />
    </div>
    <template v-else>
      <div
        v-if="feed.length"
        class="feed-list"
        ref="feedContainer"
        @scroll="handleScroll"
      >
        <div v-if="error" class="feed-inline-error" role="alert">
          <AlertCircle :size="18" />
          <span>{{ error }}</span>
          <button type="button" @click.stop="refreshFeed">
            <RotateCcw :size="15" />
            重试
          </button>
        </div>
        <MomentCard v-for="m in feed" :key="m.id" :moment="m" :data-moment-id="m.id"
          @click="goDetail(m)" @like="handleLike(m)" @comment="goDetail(m)" @share="showShareSheet(m)"
          @reaction="showReactionPicker(m)" @topic="goTopic" @author="goAuthor" @image="(idx) => viewImages(m, idx)" @reference="goReference(m)"
          @quote="quoteMoment(m)" @open-quote="openQuoted" @edit="editMoment(m)" @delete="removeMoment(m)" />
        <div v-if="loadingMore" class="feed-more-status" role="status">
          <LoaderCircle :size="18" />
          <span>正在加载更多动态</span>
        </div>
        <div v-else-if="loadMoreError" class="feed-more-error" role="alert">
          <span>{{ loadMoreError }}</span>
          <button type="button" @click.stop="loadMore(true)">
            <RotateCcw :size="15" />
            重新加载
          </button>
        </div>
        <div v-if="!hasMore && feed.length" class="feed-end">— 没有更多了 —</div>
      </div>
      <EmptyState
        v-else
        icon="document"
        :title="tab === 'HOT' ? '暂无热门动态' : '暂无动态'"
        :description="tab === 'HOT' ? '暂时没有符合条件的热门内容' : '发布你的第一条动态'"
      />
    </template>

    <!-- Reaction picker -->
    <MomentReactionPicker
      v-if="reactionMoment"
      :reactions="reactionMoment.reactions || []"
      @select="handleReaction"
      @close="reactionMoment = null"
    />

    <!-- Image viewer -->
    <ImageViewer :visible="imageViewer.visible" :images="imageViewer.images" :initial-index="imageViewer.index" @close="imageViewer.visible = false" />

    <!-- Share sheet -->
    <div v-if="shareMoment" class="modal-overlay" @click.self="shareMoment = null">
      <div class="share-sheet">
        <h3 class="share-title">{{ shareMode === 'chat' ? '选择聊天' : (shareMode === 'external' ? '分享到其他应用' : '分享动态') }}</h3>
        <div class="share-preview">
          <img v-if="shareMoment.imageUrls?.[0]" :src="imageUrl(shareMoment.imageUrls[0])" alt="" />
          <span><small>{{ shareMoment.author?.nickname || '动态用户' }}</small><strong>{{ shareMoment.title || '分享了一条动态' }}</strong><p>{{ shareMoment.content }}</p></span>
        </div>
        <div v-if="shareMode === 'options'" class="share-methods">
          <button type="button" @click="openChatShare"><MessageCircle :size="22" /><span><strong>分享到聊天</strong><small>发送动态卡片给好友或群聊</small></span></button>
          <button type="button" @click="shareMode = 'external'"><Share2 :size="22" /><span><strong>分享到其他应用</strong><small>选择分享后的浏览范围</small></span></button>
        </div>
        <ExternalShareOptions v-else-if="shareMode === 'external'" @select="shareExternal" />
        <LoadingSpinner v-else-if="shareLoading" />
        <template v-else-if="shareMode === 'chat'">
          <ConversationTile v-for="c in shareConversations" :key="c.id" :conversation="c" :show-menu="false"
            @click="doShare(c)" />
          <p v-if="!shareConversations.length" class="share-empty">暂无可用会话</p>
        </template>
        <button class="share-cancel" @click="shareMode === 'chat' || shareMode === 'external' ? (shareMode = 'options') : (shareMoment = null)">{{ shareMode === 'chat' || shareMode === 'external' ? '返回' : '取消' }}</button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { nextTick, ref, onMounted, onUnmounted, watch } from 'vue'
import { useRouter } from 'vue-router'
import { AlertCircle, FileText, LoaderCircle, MessageCircle, RotateCcw, Search, Share2, SquarePen } from 'lucide-vue-next'
import { deleteMoment, getHotMoments, getMomentDetail, getMomentUnreadCount, getMoments, markMomentsRead, setMomentLike, setMomentReaction } from '../../api/moments'
import { getConversations, shareMomentToChat } from '../../api/messages'
import { imageUrl } from '../../composables/useImage'
import MomentCard from '../../components/MomentCard.vue'
import ImageViewer from '../../components/ImageViewer.vue'
import ConversationTile from '../../components/ConversationTile.vue'
import LoadingSpinner from '../../components/LoadingSpinner.vue'
import EmptyState from '../../components/EmptyState.vue'
import ExternalShareOptions from '../../components/ExternalShareOptions.vue'
import MomentReactionPicker from '../../components/MomentReactionPicker.vue'
import { shareMomentExternally } from '../../services/shareCard'
import { getMomentFeedCache } from '../../services/momentFeedCache'
import { confirmNotice, notifyError, notifySuccess } from '../../services/notice'
import { connectCustomerWebSocket, subscribeCustomerWebSocket } from '../../services/customerWebSocket'

const router = useRouter()
const props = defineProps({ active: { type: Boolean, default: true } })
const feedTabs = new Set(['RECOMMENDED', 'HOT', 'RELATED'])
const storedTab = sessionStorage.getItem('moment-feed-tab')
const tab = ref(feedTabs.has(storedTab) ? storedTab : 'RECOMMENDED')
const feed = ref([])
const loading = ref(true)
const error = ref('')
const page = ref(1)
const hasMore = ref(true)
const loadingMore = ref(false)
const loadMoreError = ref('')
const newCount = ref(0)
const relatedUnread = ref(0)
const reportedRead = new Set()

const imageViewer = ref({ visible: false, images: [], index: 0 })
const reactionMoment = ref(null)
const shareMoment = ref(null)
const shareConversations = ref([])
const shareLoading = ref(false)
const shareMode = ref('options')

const feedContainer = ref(null)
const emit = defineEmits(['header-visibility', 'related-unread'])
let lastScrollTop = 0
let scrollDirection = 0
let scrollDistance = 0
const FEED_PAGE_SIZE = 12

function cacheScope() {
  try {
    const userId = JSON.parse(localStorage.getItem('user') || 'null')?.id
    return userId ? `user-${userId}` : (localStorage.getItem('token') ? 'customer' : 'guest')
  } catch {
    return localStorage.getItem('token') ? 'customer' : 'guest'
  }
}

const feedCacheScope = cacheScope()

const tabStates = getMomentFeedCache(feedCacheScope)
const momentRefreshRequests = new Map()

function removeCachedMoment(momentId) {
  let activeChanged = false
  for (const [type, state] of Object.entries(tabStates)) {
    const next = state.records.filter(item => Number(item.id) !== Number(momentId))
    if (next.length === state.records.length) continue
    state.records = next
    if (type === tab.value) activeChanged = true
  }
  if (activeChanged) feed.value = [...tabStates[tab.value].records]
}

function replaceCachedMoment(latest) {
  let activeChanged = false
  for (const [type, state] of Object.entries(tabStates)) {
    const index = state.records.findIndex(item => Number(item.id) === Number(latest.id))
    if (index < 0) continue
    state.records = state.records.map((item, itemIndex) => itemIndex === index ? latest : item)
    if (type === tab.value) activeChanged = true
  }
  if (activeChanged) feed.value = [...tabStates[tab.value].records]
}

async function refreshMoment(momentId) {
  const known = Object.values(tabStates).some(state =>
    state.records.some(item => Number(item.id) === Number(momentId)))
  if (!known) return
  let request = momentRefreshRequests.get(Number(momentId))
  if (!request) {
    request = getMomentDetail(momentId)
    momentRefreshRequests.set(Number(momentId), request)
  }
  try {
    replaceCachedMoment(await request)
  } catch (e) {
    if (Number(e?.code) === 403 || Number(e?.code) === 404) removeCachedMoment(momentId)
  } finally {
    if (momentRefreshRequests.get(Number(momentId)) === request) {
      momentRefreshRequests.delete(Number(momentId))
    }
  }
}

function captureAnchor(state, momentId = null) {
  const container = feedContainer.value
  if (!container) return
  const element = momentId
    ? container.querySelector(`[data-moment-id="${momentId}"]`)
    : [...container.querySelectorAll('[data-moment-id]')]
        .find(node => node.getBoundingClientRect().bottom > container.getBoundingClientRect().top)
  if (!element) return
  state.anchorMomentId = Number(element.dataset.momentId)
  state.anchorViewportOffset = element.getBoundingClientRect().top - container.getBoundingClientRect().top
}

function saveCurrentTabState(captureScroll = true, captureCurrentAnchor = true) {
  const state = tabStates[tab.value]
  if (!state) return
  state.records = feed.value
  state.page = page.value
  state.hasMore = hasMore.value
  state.newCount = newCount.value
  state.loadMoreError = loadMoreError.value
  state.loaded = state.loaded || (!loading.value && !error.value)
  if (captureScroll && feedContainer.value) {
    state.scrollTop = feedContainer.value.scrollTop
    if (captureCurrentAnchor) captureAnchor(state)
  }
}

async function restoreScrollPosition(state) {
  await nextTick()
  const container = feedContainer.value
  if (!container) return
  container.scrollTop = state.scrollTop
  await nextTick()
  if (state.anchorMomentId == null || state.anchorViewportOffset == null) return
  const element = container.querySelector(`[data-moment-id="${state.anchorMomentId}"]`)
  if (!element) return
  const currentOffset = element.getBoundingClientRect().top - container.getBoundingClientRect().top
  container.scrollTop += currentOffset - state.anchorViewportOffset
  state.scrollTop = container.scrollTop
}

async function restoreTabState(type) {
  const state = tabStates[type]
  error.value = ''
  loadingMore.value = Boolean(state.loadMoreRequest)
  loadMoreError.value = state.loadMoreError
  if (state.loaded) {
    feed.value = state.records
    page.value = state.page
    hasMore.value = state.hasMore
    newCount.value = state.newCount
    loading.value = false
  } else {
    feed.value = []
    page.value = 1
    hasMore.value = true
    newCount.value = 0
    loading.value = true
    await loadFeed()
  }
  await restoreScrollPosition(state)
  lastScrollTop = state.scrollTop
  scrollDirection = 0
  scrollDistance = 0
  scheduleVisibleReadCheck()
  const returnMomentId = state.returnMomentId
  if (returnMomentId != null) {
    await refreshMoment(returnMomentId)
    state.returnMomentId = null
    await restoreScrollPosition(state)
  }
}

async function loadFeed() {
  const requestedTab = tab.value
  const requestedPage = page.value
  const state = tabStates[requestedTab]
  error.value = ''
  loadMoreError.value = ''
  state.loadMoreError = ''
  loading.value = true
  let request
  try {
    const role = JSON.parse(localStorage.getItem('user') || '{}')?.role
    request = state.request || (state.request = requestedTab === 'HOT'
      ? getHotMoments(10)
      : getMoments({
          feed: requestedTab,
          page: requestedPage,
          pageSize: FEED_PAGE_SIZE,
          consumeExposure: role === 'CUSTOMER'
        }))
    const res = await request
    const records = Array.isArray(res) ? res : (res.records || [])
    state.records = records
    state.hasMore = requestedTab !== 'HOT' && res.hasMore !== false
    state.page = requestedPage
    state.newCount = 0
    state.loaded = true
    if (tab.value !== requestedTab) return
    feed.value = records
    hasMore.value = state.hasMore
    newCount.value = 0
    scheduleVisibleReadCheck()
  } catch (e) {
    if (tab.value === requestedTab) error.value = e?.message || '动态加载失败，请稍后重试'
  }
  finally {
    if (state.request === request) state.request = null
    if (tab.value === requestedTab) loading.value = false
  }
}

async function refreshFeed() {
  const state = tabStates[tab.value]
  page.value = 1
  state.page = 1
  await loadFeed()
  if (error.value) return
  state.scrollTop = 0
  await nextTick()
  if (feedContainer.value) feedContainer.value.scrollTop = 0
  state.anchorMomentId = null
  state.anchorViewportOffset = null
}

async function loadMore(force = false) {
  if (tab.value === 'HOT' || !hasMore.value || loadingMore.value || error.value || (!force && loadMoreError.value)) return
  const requestedTab = tab.value
  const state = tabStates[requestedTab]
  if (state.loadMoreRequest) return
  const nextPage = page.value + 1
  loadMoreError.value = ''
  state.loadMoreError = ''
  loadingMore.value = true
  let request
  try {
    request = getMoments({ feed: requestedTab, page: nextPage, pageSize: FEED_PAGE_SIZE })
    state.loadMoreRequest = request
    const res = await request
    const records = res.records || []
    const existingIds = new Set(state.records.map(item => item.id))
    state.records = [...state.records, ...records.filter(item => !existingIds.has(item.id))]
    state.hasMore = res.hasMore !== false
    state.page = nextPage
    if (tab.value !== requestedTab) return
    feed.value = state.records
    hasMore.value = state.hasMore
    page.value = nextPage
    scheduleVisibleReadCheck()
  } catch (e) {
    state.loadMoreError = e?.message || '加载更多动态失败'
    if (tab.value === requestedTab) loadMoreError.value = state.loadMoreError
  }
  finally {
    if (state.loadMoreRequest === request) state.loadMoreRequest = null
    if (tab.value === requestedTab) loadingMore.value = false
  }
}

async function switchTab(t) {
  if (tab.value === t) return
  saveCurrentTabState()
  tab.value = t
  sessionStorage.setItem('moment-feed-tab', t)
  await restoreTabState(t)
}

function handleScroll(e) {
  const el = e.target
  tabStates[tab.value].scrollTop = el.scrollTop
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
  scheduleVisibleReadCheck()
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
  try { await shareMomentToChat({ conversationId: conv.id, momentId: moment.id }); moment.shareCount = Number(moment.shareCount || 0) + 1; moment.appShareCount = Number(moment.appShareCount || 0) + 1; shareMoment.value = null; notifySuccess('已分享到聊天') }
  catch (e) { notifyError(e.message) }
}

function shareExternal(mode) {
  const moment = shareMoment.value
  shareMoment.value = null
  if (moment) shareMomentExternally(moment, null, { mode })
}

function goNew() {
  if (requireLogin()) router.push('/customer/moments/new')
}
function editMoment(moment) { router.push(`/customer/moments/new?editId=${moment.id}`) }
function quoteMoment(moment) {
  if (!requireLogin()) return
  router.push({ path: '/customer/moments/new', query: { quotedMomentId: moment.id } })
}
function openQuoted(quote) {
  if (!quote?.momentId) return
  router.push({ path: `/customer/moments/${quote.momentId}`, query: { ...(quote.commentId ? { commentId: quote.commentId } : {}), fromQuote: '1' } })
}
async function removeMoment(moment) {
  if (!await confirmNotice('确认删除这条动态？')) return
  try {
    await deleteMoment(moment.id)
    feed.value = feed.value.filter(item => item.id !== moment.id)
    saveCurrentTabState(false)
  } catch (e) { notifyError(e.message) }
}
function goDetail(m) {
  const state = tabStates[tab.value]
  if (feedContainer.value) state.scrollTop = feedContainer.value.scrollTop
  captureAnchor(state, m.id)
  state.returnMomentId = Number(m.id)
  openingDetail = true
  router.push(`/customer/moments/${m.id}`)
}
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
  imageViewer.value = { visible: true, images: urls.map(imageUrl), index: idx }
}
function goReference(ref) {
  if (ref?.shopId) router.push(`/customer/shop/${ref.shopId}/menu`)
}

let pollTimer = null
let unsubscribeWs
let visibilityFrame = 0
let openingDetail = false
const badge = value => value > 99 ? '99+' : value

async function loadRelatedUnread() {
  if (!localStorage.getItem('token')) return
  try {
    const data = await getMomentUnreadCount()
    setRelatedUnread(Number(data?.relatedUnreadCount) || 0)
  } catch { }
}

function setRelatedUnread(value) {
  relatedUnread.value = Math.max(0, Number(value) || 0)
  emit('related-unread', relatedUnread.value)
}

function scheduleVisibleReadCheck() {
  window.cancelAnimationFrame(visibilityFrame)
  visibilityFrame = window.requestAnimationFrame(markVisibleRelatedMomentsRead)
}

async function markVisibleRelatedMomentsRead() {
  if (!props.active || tab.value !== 'RELATED' || !feedContainer.value || !localStorage.getItem('token')) return
  const viewport = feedContainer.value.getBoundingClientRect()
  const unreadIds = feed.value
    .filter(moment => moment.readByMe === false && !reportedRead.has(Number(moment.id)))
    .filter(moment => {
      const element = feedContainer.value.querySelector(`[data-moment-id="${moment.id}"]`)
      if (!element) return false
      const rect = element.getBoundingClientRect()
      return rect.bottom > viewport.top && rect.top < viewport.bottom
    })
    .map(moment => Number(moment.id))
  if (!unreadIds.length) return

  unreadIds.forEach(id => reportedRead.add(id))
  feed.value.forEach(moment => {
    if (unreadIds.includes(Number(moment.id))) moment.readByMe = true
  })
  setRelatedUnread(Math.max(0, relatedUnread.value - unreadIds.length))
  try {
    const result = await markMomentsRead(unreadIds)
    setRelatedUnread(Number(result?.relatedUnreadCount) || 0)
  } catch {
    unreadIds.forEach(id => reportedRead.delete(id))
    feed.value.forEach(moment => {
      if (unreadIds.includes(Number(moment.id))) moment.readByMe = false
    })
    loadRelatedUnread()
  }
}

async function checkForNewMoments() {
  const requestedTab = tab.value
  if ((requestedTab !== 'RECOMMENDED' && requestedTab !== 'RELATED') || !feed.value.length) {
    newCount.value = 0
    return
  }
  try {
    const res = await getMoments({ feed: requestedTab, page: 1, pageSize: FEED_PAGE_SIZE, consumeExposure: false })
    if (tab.value !== requestedTab) return
    const visibleIds = new Set(feed.value.map(item => item.id))
    newCount.value = (res.records || []).filter(item => !visibleIds.has(item.id)).length
    tabStates[requestedTab].newCount = newCount.value
  } catch { }
}

async function handleMomentWs(message) {
  const count = Number(message?.data?.relatedUnreadCount)
  if (Number.isFinite(count)) setRelatedUnread(count)
  else loadRelatedUnread()
  const momentId = Number(message?.data?.momentId)
  if (Number.isFinite(momentId)) await refreshMoment(momentId)
  if (message?.action === 'PUBLISHED') checkForNewMoments()
}

onMounted(async () => {
  await restoreTabState(tab.value)
  if (!localStorage.getItem('token')) return
  await loadRelatedUnread()
  connectCustomerWebSocket()
  unsubscribeWs = subscribeCustomerWebSocket('MOMENT', handleMomentWs)
  pollTimer = setInterval(() => { loadRelatedUnread(); checkForNewMoments() }, 60000)
  scheduleVisibleReadCheck()
})
watch(() => props.active, active => {
  if (!active) {
    saveCurrentTabState()
    return
  }
  nextTick(() => {
    const state = tabStates[tab.value]
    if (feedContainer.value) feedContainer.value.scrollTop = state.scrollTop
    lastScrollTop = state.scrollTop
    scheduleVisibleReadCheck()
  })
})
onUnmounted(() => {
  saveCurrentTabState(true, !openingDetail)
  clearInterval(pollTimer)
  window.cancelAnimationFrame(visibilityFrame)
  unsubscribeWs?.()
})
</script>

<style scoped>
.moment-feed { height: 100%; min-height: 0; position: relative; display: flex; flex-direction: column; }
.feed-tabs { height: 58px; display: flex; gap: 4px; align-items: center; padding: 4px 8px 4px 10px; background: #fff; border-bottom: 1px solid #eee; }
.feed-tab {
  min-width: 70px; height: 42px; display: inline-flex; align-items: center; justify-content: center; gap: 5px;
  padding: 8px 12px; border: none; background: none;
  font-size: 16px; font-weight: 500; color: var(--color-text-muted);
  cursor: pointer; border-radius: var(--radius); transition: all 0.2s;
  position: relative;
}
.feed-tab-badge { min-width: 18px; height: 18px; display: grid; place-items: center; padding: 0 5px; border-radius: 10px; background: #e85d4a; color: #fff; font-size: 10px; font-style: normal; font-weight: 700; }
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

.feed-refresh-status {
  position: absolute; top: 66px; left: 50%; z-index: 9; transform: translateX(-50%);
  min-height: 34px; display: inline-flex; align-items: center; gap: 7px;
  padding: 7px 12px; border: 1px solid #e4e6e9; border-radius: 6px;
  background: rgba(255, 255, 255, .96); box-shadow: 0 3px 12px rgba(32, 33, 36, .12);
  color: #4c5158; font-size: 13px; font-weight: 600;
}
.feed-refresh-status svg,
.feed-more-status svg { color: var(--color-primary); animation: feed-spin .85s linear infinite; }
.feed-inline-error {
  min-height: 48px; display: flex; align-items: center; gap: 8px;
  margin: 10px 12px; padding: 9px 11px; border: 1px solid #f0c5bd; border-radius: 6px;
  background: #fff7f5; color: #9a3e31; font-size: 13px;
}
.feed-inline-error span { min-width: 0; flex: 1; }
.feed-inline-error button,
.feed-more-error button {
  min-height: 30px; display: inline-flex; flex: 0 0 auto; align-items: center; justify-content: center; gap: 5px;
  padding: 5px 9px; border: 1px solid currentColor; border-radius: 5px; background: #fff;
  color: inherit; font-size: 12px; font-weight: 700; cursor: pointer;
}
.feed-more-status,
.feed-more-error {
  min-height: 58px; display: flex; align-items: center; justify-content: center; gap: 8px;
  padding: 12px 20px; font-size: 13px;
}
.feed-more-status { color: #555b63; font-weight: 600; }
.feed-more-error { color: #a04436; }
.feed-end { text-align: center; padding: 20px; color: var(--color-text-light); font-size: 13px; }
@keyframes feed-spin { to { transform: rotate(360deg); } }
@media (prefers-reduced-motion: reduce) {
  .feed-refresh-status svg,
  .feed-more-status svg { animation-duration: 1.8s; }
}

@media (max-width: 480px) {
  .feed-tabs { gap: 2px; padding-inline: 6px; }
  .feed-tab { min-width: 54px; padding-inline: 8px; }
  .feed-actions { gap: 0; }
  .feed-icon-btn { width: 36px; }
}

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
