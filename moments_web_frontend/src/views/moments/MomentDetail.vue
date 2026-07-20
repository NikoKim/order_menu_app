<template>
  <div class="detail-page">
    <header class="page-header" :class="{ hidden: headerHidden }">
      <button class="back-btn" @click="$router.back()"><ArrowLeft :size="20" /></button>
      <h1 class="page-title">动态详情</h1>
      <div class="header-actions">
        <button v-if="moment?.editable" class="more-btn" title="动态操作" aria-label="动态操作" @click.stop="ownerMenuOpen = !ownerMenuOpen"><MoreVertical :size="21" /></button>
        <span v-else class="header-spacer"></span>
        <div v-if="ownerMenuOpen" class="owner-menu-backdrop" @click.stop="ownerMenuOpen = false"></div>
        <Transition name="owner-menu">
          <div v-if="ownerMenuOpen" class="owner-menu" @click.stop>
            <button @click="editMoment"><Pencil :size="16" />编辑</button>
            <button class="danger" @click="removeMoment"><Trash2 :size="16" />删除</button>
          </div>
        </Transition>
      </div>
    </header>

    <LoadingSpinner v-if="loading" variant="moment-detail" />
    <div v-else-if="error" class="error-wrap">
      <EmptyState icon="warning" :title="error" action-text="重试" @action="loadDetail" />
    </div>
    <template v-else-if="moment">
      <article class="detail-article">
        <h2 v-if="moment.title" class="moment-title">{{ moment.title }}</h2>

        <!-- Author header -->
        <div class="moment-author">
          <div class="author-avatar" @click="goAuthor(moment.author)">
            <img v-if="moment.author?.avatarUrl" :src="imageUrl(moment.author.avatarUrl)" alt="" />
            <span v-else>{{ moment.author?.nickname?.charAt(0) || '?' }}</span>
          </div>
          <div class="author-info">
            <div class="author-row">
              <span class="author-name">{{ moment.author?.nickname }}</span>
              <span v-if="moment.author?.merchant" class="author-badge">商家</span>
            </div>
            <span class="author-time">{{ fmtTime(moment.publishedAt || moment.createdAt) }}</span>
          </div>
          <span v-if="moment.visibility" class="vis-badge" :class="'vis-' + moment.visibility.toLowerCase()">
            {{ visLabel(moment.visibility) }}
          </span>
        </div>

        <div v-if="moment.content" class="moment-text">
          <p>{{ moment.content }}</p>
        </div>

        <!-- Images grid -->
        <div v-if="moment.imageUrls?.length" class="moment-images" :class="'img-n-' + Math.min(moment.imageUrls.length, 9)">
          <div v-for="(img, i) in moment.imageUrls.slice(0, 9)" :key="i" class="moment-img-wrap" @click="viewImage(i)">
            <img :src="imageUrl(img)" alt="" loading="lazy" />
          </div>
        </div>

        <!-- Reference card -->
        <div v-if="moment.reference?.shopId" class="moment-ref" @click="goReference(moment.reference)">
          <img v-if="moment.reference.shopImageUrl" :src="imageUrl(moment.reference.shopImageUrl)" class="ref-img" alt="" />
          <div class="ref-info">
            <span class="ref-name">{{ moment.reference.shopName }}</span>
            <span v-if="moment.reference.dishName" class="ref-desc">{{ moment.reference.dishName }}</span>
          </div>
          <span v-if="moment.reference.dishPrice" class="ref-price">{{ moment.reference.dishPrice }}</span>
        </div>

        <!-- Topics -->
        <div v-if="moment.topics?.length" class="moment-topics">
          <span v-for="t in moment.topics" :key="t.id" class="moment-topic" @click="goTopic(t)">#{{ t.name }}</span>
        </div>

        <!-- Reaction chips -->
        <div v-if="moment.reactions?.length" class="moment-reactions">
          <span v-for="r in moment.reactions" :key="r.emoji" class="reaction-chip" :class="{ active: r.reactedByMe }"
            @click="showReactionPicker(moment)">
            {{ r.emoji }} {{ r.count }}
          </span>
        </div>

        <!-- Action bar -->
        <div class="moment-actions">
          <button class="act-btn" :class="{ active: moment.likedByMe }" @click="handleLike">
            <Heart class="act-icon" :size="19" :fill="moment.likedByMe ? 'currentColor' : 'none'" />
            <span>{{ moment.likedByMe ? '已赞' : '点赞' }}</span>
            <span class="act-num">{{ moment.likeCount || 0 }}</span>
          </button>
          <span class="act-div"></span>
          <button class="act-btn" @click="showReactionPicker(moment)">
            <SmilePlus class="act-icon" :size="19" />
            <span>表情</span>
            <span class="act-num">{{ reactionCount }}</span>
          </button>
          <span class="act-div"></span>
          <button class="act-btn" @click="focusComment">
            <MessageCircle class="act-icon" :size="19" />
            <span>评论</span>
            <span class="act-num">{{ moment.commentCount || 0 }}</span>
          </button>
          <span class="act-div"></span>
          <button class="act-btn" @click="openShareSheet()">
            <Share2 class="act-icon" :size="19" />
            <span>分享</span>
          </button>
        </div>
      </article>

      <!-- Comments section -->
      <section class="comments-card">
        <div class="comments-header">
          <span>全部评论</span>
          <i>{{ moment.commentCount || 0 }}</i>
        </div>

        <div v-if="comments.length">
          <div v-for="c in comments" :id="`comment-${c.id}`" :key="c.id" class="comment-item">
            <div class="comment-avatar">
              <img v-if="c.author?.avatarUrl" :src="imageUrl(c.author.avatarUrl)" alt="" />
              <span v-else>{{ c.author?.nickname?.charAt(0) || '?' }}</span>
            </div>
            <div class="comment-body">
              <div class="comment-top">
                <span class="comment-name">{{ c.author?.nickname }}</span>
                <span v-if="c.authorBadge" class="comment-badge">作者</span>
                <span v-if="c.author?.merchant" class="comment-badge merchant">商家</span>
                <span class="comment-time">{{ fmtTime(c.createdAt) }}</span>
                <div class="comment-more-wrap">
                  <button class="comment-more" title="评论操作" aria-label="评论操作" @click.stop="toggleCommentMenu(`comment-${c.id}`)"><MoreVertical :size="17" /></button>
                  <Transition name="owner-menu">
                    <div v-if="commentMenuId === `comment-${c.id}`" class="comment-menu" @click.stop>
                      <button @click="openShareSheet(c, 'chat')"><MessageCircle :size="15" />分享到聊天</button>
                      <button @click="shareCommentExternally(c)"><Share2 :size="15" />分享到其他应用</button>
                      <button v-if="c.mine" class="danger" @click="handleDeleteComment(c)"><Trash2 :size="15" />删除</button>
                    </div>
                  </Transition>
                </div>
              </div>
              <p v-if="c.replyToNickname" class="comment-reply-label">回复 <b>{{ c.replyToNickname }}</b></p>
              <p class="comment-text">{{ c.content }}</p>
              <div v-if="c.imageUrls?.length" class="comment-images">
                <img v-for="(img, i) in c.imageUrls" :key="i" :src="imageUrl(img)" class="comment-img" @click="viewCommentImage(c.imageUrls, i)" />
              </div>
              <div class="comment-actions">
                <button class="comment-like-btn" :class="{ liked: c.likedByMe }" @click="handleCommentLike(c)">
                  <Heart :size="17" :fill="c.likedByMe ? 'currentColor' : 'none'" />
                  <span v-if="c.likeCount">{{ c.likeCount }}</span>
                </button>
                <button class="comment-reply-btn" @click="startReply(c)">回复</button>
              </div>

              <!-- Replies -->
              <div v-if="c.replyCount > 0 && c.replies?.length" class="comment-replies">
                <div v-for="r in c.replies" :id="`comment-${r.id}`" :key="r.id" class="reply-item">
                  <span class="reply-name">{{ r.author?.nickname }}</span>
                  <span v-if="r.replyToNickname" class="reply-target"> 回复 <b>{{ r.replyToNickname }}</b></span>
                  <span class="reply-colon">：</span>
                  <span class="reply-text">{{ r.content }}</span>
                  <span class="reply-more-wrap">
                    <button class="reply-more" title="回复操作" aria-label="回复操作" @click.stop="toggleCommentMenu(`reply-${r.id}`)"><MoreVertical :size="15" /></button>
                    <Transition name="owner-menu">
                      <span v-if="commentMenuId === `reply-${r.id}`" class="comment-menu reply-menu" @click.stop>
                        <button @click="openShareSheet(r, 'chat')"><MessageCircle :size="14" />分享到聊天</button>
                        <button @click="shareCommentExternally(r)"><Share2 :size="14" />分享到其他应用</button>
                        <button v-if="r.mine" class="danger" @click="handleDeleteReply(c, r)"><Trash2 :size="14" />删除</button>
                      </span>
                    </Transition>
                  </span>
                </div>
                <button v-if="c.replyCount > (c.replies?.length || 0)" class="load-replies-btn" @click="loadReplies(c)">
                  展开更多回复 ({{ c.replyCount - (c.replies?.length || 0) }})
                </button>
              </div>
            </div>
          </div>
        </div>
        <EmptyState v-else icon="message" title="暂无评论，快来抢沙发" />

        <div v-if="hasMoreComments" class="load-more-wrap">
          <button class="load-more-btn" :disabled="loadingComments" @click="loadMoreComments">
            {{ loadingComments ? '加载中...' : '加载更多评论' }}
          </button>
        </div>
      </section>

      <!-- Comment input -->
      <div v-if="!isLoggedIn" class="guest-action-bar">
        <button @click="goLogin">登录后参与评论</button>
      </div>
      <div v-else class="comment-input-bar">
        <div v-if="replyTarget" class="reply-indicator">
          <span>回复 {{ replyTarget.author?.nickname }}</span>
          <button class="reply-cancel" title="取消回复" @click="replyTarget = null"><X :size="16" /></button>
        </div>
        <div class="comment-input-row">
          <label class="comment-img-btn">
            <ImagePlus :size="22" aria-label="添加图片" />
            <input type="file" accept="image/*" hidden @change="handleCommentImage" />
          </label>
          <input ref="commentInputEl" v-model="commentText" class="comment-text-input" :placeholder="replyTarget ? '写回复...' : '写评论...'" @keyup.enter="handleCommentSend" />
          <button class="comment-send-btn" :disabled="!commentText.trim()" @click="handleCommentSend">发送</button>
        </div>
      </div>
    </template>

    <div v-if="commentMenuId" class="comment-menu-backdrop" @click.stop="commentMenuId = null"></div>

    <!-- Image viewer -->
    <ImageViewer :visible="imageViewer.visible" :src="imageViewer.src" @close="imageViewer.visible = false" />

    <!-- Reaction picker -->
    <MomentReactionPicker
      v-if="reactionMoment"
      :reactions="reactionMoment.reactions || []"
      @select="handleReaction"
      @close="reactionMoment = null"
    />

    <!-- Share sheet -->
    <div v-if="shareSheet.visible" class="modal-overlay" @click.self="shareSheet.visible = false">
      <div class="share-panel">
        <h3 class="share-title">{{ shareSheet.mode === 'chat' ? '选择聊天' : (shareSheet.comment ? '分享评论' : '分享动态') }}</h3>
        <div class="share-preview">
          <img v-if="moment.imageUrls?.[0]" :src="imageUrl(moment.imageUrls[0])" alt="" />
          <span><small>{{ shareSheet.comment?.author?.nickname || moment.author?.nickname || '动态用户' }}</small><strong>{{ moment.title || '分享了一条动态' }}</strong><p>{{ shareSheet.comment?.content || moment.content }}</p></span>
        </div>
        <div v-if="shareSheet.mode === 'options'" class="share-methods">
          <button type="button" @click="openChatShare"><MessageCircle :size="22" /><span><strong>分享到聊天</strong><small>发送{{ shareSheet.comment ? '评论' : '动态' }}卡片给好友或群聊</small></span></button>
          <button type="button" @click="shareCurrentExternally"><Share2 :size="22" /><span><strong>分享到其他应用</strong><small>分享可点击进入{{ shareSheet.comment ? '评论' : '动态' }}详情的网页卡片</small></span></button>
        </div>
        <LoadingSpinner v-else-if="shareSheet.loading" />
        <div v-else-if="shareSheet.conversations.length === 0" class="share-empty">暂无可用会话</div>
        <div v-else-if="shareSheet.mode === 'chat'" class="share-list">
          <ConversationTile
            v-for="conv in shareSheet.conversations"
            :key="conv.id"
            :conversation="conv"
            :show-menu="false"
            variant="customer"
            :class="{ 'share-sending': shareSheet.sending }"
            @click="!shareSheet.sending && doShare(conv)"
          />
        </div>
        <button class="share-cancel" @click="shareSheet.mode === 'chat' ? (shareSheet.mode = 'options') : (shareSheet.visible = false)">{{ shareSheet.mode === 'chat' ? '返回' : '取消' }}</button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, nextTick, ref, onMounted, onUnmounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowLeft, Heart, ImagePlus, MessageCircle, MoreVertical, Pencil, Share2, SmilePlus, Trash2, X } from 'lucide-vue-next'
import { deleteMoment, getMomentDetail, setMomentLike, setMomentReaction, getMomentComments, addMomentComment, deleteMomentComment, setCommentLike, getCommentReplies, uploadMomentCommentImage } from '../../api/moments'
import { getConversations, shareMomentToChat } from '../../api/messages'
import { imageUrl } from '../../composables/useImage'
import ImageViewer from '../../components/ImageViewer.vue'
import LoadingSpinner from '../../components/LoadingSpinner.vue'
import EmptyState from '../../components/EmptyState.vue'
import ConversationTile from '../../components/ConversationTile.vue'
import MomentReactionPicker from '../../components/MomentReactionPicker.vue'
import { shareMomentExternally } from '../../services/shareCard'
import { confirmNotice, notifyError, notifySuccess } from '../../services/notice'

const route = useRoute()
const router = useRouter()
const moment = ref(null)
const loading = ref(true)
const error = ref('')
const comments = ref([])
const commentPage = ref(1)
const hasMoreComments = ref(false)
const loadingComments = ref(false)
const isLoggedIn = Boolean(localStorage.getItem('token'))

const commentText = ref('')
const commentInputEl = ref(null)
const replyTarget = ref(null)
const reactionMoment = ref(null)
const imageViewer = ref({ visible: false, src: '' })
const ownerMenuOpen = ref(false)
const commentMenuId = ref(null)
const headerHidden = ref(false)
const reactionCount = computed(() => (moment.value?.reactions || []).reduce((sum, item) => sum + Number(item.count || 0), 0))
let lastScrollY = 0

const shareSheet = ref({ visible: false, mode: 'options', comment: null, conversations: [], loading: false, sending: false })

const portalPrefix = route.path.startsWith('/shop/') ? '/shop' : '/customer'

function goLogin() {
  router.push({ path: '/customer/login', query: { redirect: route.fullPath } })
}

function requireLogin() {
  if (isLoggedIn) return true
  goLogin()
  return false
}

async function loadDetail() {
  loading.value = true; error.value = ''
  try {
    moment.value = await getMomentDetail(route.params.id)
  } catch (e) { error.value = e.message }
  finally { loading.value = false }
  loadComments()
}

async function loadComments() {
  try {
    const res = await getMomentComments(Number(route.params.id), { page: 1, pageSize: 20 })
    comments.value = res.records || []
    hasMoreComments.value = res.hasMore !== false
    commentPage.value = 1
    const sharedCommentId = Number(route.query.commentId)
    if (sharedCommentId) {
      await nextTick()
      const target = document.getElementById(`comment-${sharedCommentId}`)
      target?.scrollIntoView({ behavior: 'smooth', block: 'center' })
      target?.classList.add('shared-comment-highlight')
      window.setTimeout(() => target?.classList.remove('shared-comment-highlight'), 1800)
    }
  } catch { }
}

async function loadMoreComments() {
  loadingComments.value = true
  try {
    const res = await getMomentComments(Number(route.params.id), { page: commentPage.value + 1, pageSize: 20 })
    comments.value.push(...(res.records || []))
    hasMoreComments.value = res.hasMore !== false
    commentPage.value++
  } catch { }
  finally { loadingComments.value = false }
}

async function loadReplies(c) {
  try {
    const res = await getCommentReplies(Number(route.params.id), c.id, { page: 1, pageSize: 10 })
    c.replies = res.records || []
  } catch { }
}

async function handleLike() {
  if (!requireLogin()) return
  const m = moment.value
  if (!m) return
  try {
    const liked = !m.likedByMe
    await setMomentLike(m.id, liked)
    m.likedByMe = liked
    m.likeCount = (m.likeCount || 0) + (liked ? 1 : -1)
  } catch (e) { notifyError(e.message) }
}

async function handleCommentLike(c) {
  if (!requireLogin()) return
  try {
    const liked = !c.likedByMe
    await setCommentLike(Number(route.params.id), c.id, liked)
    c.likedByMe = liked
    c.likeCount = (c.likeCount || 0) + (liked ? 1 : -1)
  } catch (e) { notifyError(e.message) }
}

async function handleCommentSend() {
  if (!requireLogin()) return
  if (!commentText.value.trim()) return
  try {
    await addMomentComment(Number(route.params.id), {
      content: commentText.value.trim(),
      parentCommentId: replyTarget.value?.id || null
    })
    commentText.value = ''
    replyTarget.value = null
    loadComments()
    if (moment.value) moment.value.commentCount = (moment.value.commentCount || 0) + 1
  } catch (e) { notifyError(e.message) }
}

function startReply(c) {
  if (!requireLogin()) return
  replyTarget.value = c; commentText.value = ''; commentInputEl.value?.focus()
}

async function handleDeleteComment(c) {
  commentMenuId.value = null
  if (!await confirmNotice('确认删除评论？')) return
  try {
    await deleteMomentComment(Number(route.params.id), c.id)
    comments.value = comments.value.filter(x => x.id !== c.id)
    if (moment.value) moment.value.commentCount = Math.max(0, (moment.value.commentCount || 1) - 1)
  } catch (e) { notifyError(e.message) }
}

async function handleDeleteReply(parent, reply) {
  commentMenuId.value = null
  if (!await confirmNotice('确认删除回复？')) return
  try {
    await deleteMomentComment(Number(route.params.id), reply.id)
    parent.replies = (parent.replies || []).filter(item => item.id !== reply.id)
    parent.replyCount = Math.max(0, (parent.replyCount || 1) - 1)
    if (moment.value) moment.value.commentCount = Math.max(0, (moment.value.commentCount || 1) - 1)
  } catch (e) { notifyError(e.message) }
}

function toggleCommentMenu(id) {
  commentMenuId.value = commentMenuId.value === id ? null : id
}

async function handleCommentImage(e) {
  if (!requireLogin()) return
  const file = e.target.files?.[0]
  if (!file) return
  try {
    const res = await uploadMomentCommentImage(file)
    commentText.value += (commentText.value ? ' ' : '') + res.url
  } catch (err) { notifyError(err.message) }
  e.target.value = ''
}

function showReactionPicker(m) { if (requireLogin()) reactionMoment.value = m }

async function handleReaction(emoji) {
  const m = reactionMoment.value
  if (!m) return
  try {
    const existing = m.reactions?.find(r => r.emoji === emoji)
    const updated = await setMomentReaction(m.id, emoji, !existing?.reactedByMe)
    Object.assign(m, updated)
    reactionMoment.value = null
  } catch (e) { notifyError(e.message) }
}

// --- Implemented stub functions ---

function viewImage(idx) {
  const urls = moment.value?.imageUrls
  if (!urls?.[idx]) return
  imageViewer.value = { visible: true, src: imageUrl(urls[idx]) }
}

function viewCommentImage(urls, idx) {
  if (!urls?.[idx]) return
  imageViewer.value = { visible: true, src: imageUrl(urls[idx]) }
}

function focusComment() {
  commentInputEl.value?.focus()
  commentInputEl.value?.scrollIntoView({ behavior: 'smooth', block: 'center' })
}

function openShareSheet(comment = null, mode = 'options') {
  commentMenuId.value = null
  shareSheet.value = { visible: true, mode: 'options', comment, conversations: [], loading: false, sending: false }
  if (mode === 'chat') openChatShare()
}

async function openChatShare() {
  if (!requireLogin()) return
  shareSheet.value.mode = 'chat'
  shareSheet.value.loading = true
  try {
    const convs = await getConversations()
    shareSheet.value.conversations = convs || []
  } catch { shareSheet.value.conversations = [] }
  finally { shareSheet.value.loading = false }
}

async function doShare(conv) {
  shareSheet.value.sending = true
  try {
    if (shareSheet.value.comment) {
      await shareMomentToChat({ momentId: moment.value.id, commentId: shareSheet.value.comment.id, conversationId: conv.id })
    } else {
      await shareMomentToChat({ momentId: moment.value.id, conversationId: conv.id })
    }
    shareSheet.value.visible = false
    notifySuccess('已分享到聊天')
  } catch (e) { notifyError(e.message) }
  finally { shareSheet.value.sending = false }
}

function shareCurrentExternally() {
  const comment = shareSheet.value.comment
  shareSheet.value.visible = false
  shareMomentExternally(moment.value, comment)
}

function shareCommentExternally(comment) {
  commentMenuId.value = null
  shareMomentExternally(moment.value, comment)
}

function goReference(ref) {
  if (ref.shopId) {
    router.push(`/customer/shop/${ref.shopId}/menu`)
  }
}

function goTopic(t) {
  if (!t?.id) return
  router.push({
    path: `${portalPrefix}/moments/topics/${t.id}`,
    query: { name: t.name || undefined, userCount: t.userCount ?? undefined, momentCount: t.momentCount ?? undefined }
  })
}

function goAuthor(a) {
  if (a?.id) {
    router.push({
      path: `${portalPrefix}/chat/user/${a.id}`,
      query: { nickname: a.nickname || undefined, avatar: a.avatarUrl || undefined, role: a.role || undefined }
    })
  }
}

function editMoment() {
  ownerMenuOpen.value = false
  router.push(`${portalPrefix}/moments/new?editId=${moment.value.id}`)
}

async function removeMoment() {
  ownerMenuOpen.value = false
  if (!await confirmNotice('确认删除这条动态？')) return
  try {
    await deleteMoment(moment.value.id)
    router.replace(`${portalPrefix}/moments`)
  } catch (e) { notifyError(e.message) }
}

function handlePageScroll() {
  if (!window.matchMedia('(max-width: 480px)').matches) return
  const y = window.scrollY
  const delta = y - lastScrollY
  if (y < 28 || delta < -7) headerHidden.value = false
  else if (delta > 7 && y > 88) { headerHidden.value = true; ownerMenuOpen.value = false }
  lastScrollY = y
}

function visLabel(v) {
  const map = { PUBLIC: '公开', RELATED: '相关', PRIVATE: '私密' }
  return map[v] || v
}

function fmtTime(ts) {
  if (!ts) return ''
  const d = new Date(ts)
  const now = new Date()
  const diff = now - d
  if (diff < 60000) return '刚刚'
  if (diff < 3600000) return Math.floor(diff / 60000) + '分钟前'
  if (diff < 86400000) return Math.floor(diff / 3600000) + '小时前'
  if (diff < 172800000) return '昨天'
  if (d.getFullYear() === now.getFullYear()) {
    return `${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
  }
  return d.toLocaleDateString()
}

onMounted(() => { loadDetail(); window.addEventListener('scroll', handlePageScroll, { passive: true }) })
onUnmounted(() => window.removeEventListener('scroll', handlePageScroll))
</script>

<style scoped>
.detail-page { min-height: 100dvh; background: #f6f7f8; padding-bottom: 20px; }
.page-header { min-height: 60px; position: sticky; top: 0; z-index: 30; display: grid; grid-template-columns: 40px 1fr 40px; align-items: center; padding: 9px 12px; background: rgba(255,255,255,.96); border-bottom: 1px solid #eaecf0; transition: transform .24s ease, opacity .2s ease; }
.page-header.hidden { transform: translateY(-100%); opacity: 0; pointer-events: none; }
.back-btn {
  width: 36px; height: 36px; display: inline-flex; align-items: center; justify-content: center;
  border-radius: 50%; background: transparent; color: var(--color-text);
}
.page-title { text-align: center; font-size: 18px; font-weight: 700; }
.header-spacer { width: 40px; }
.header-actions { position: relative; width: 40px; height: 40px; display: grid; place-items: center; }
.more-btn { width: 38px; height: 38px; display: inline-flex; align-items: center; justify-content: center; border-radius: 50%; background: transparent; color: var(--color-text); }
.more-btn:hover { background: var(--color-bg); }
.owner-menu-backdrop { position: fixed; inset: 0; z-index: 31; }
.owner-menu { position: absolute; top: 42px; right: 0; z-index: 32; width: 128px; padding: 5px; background: #fff; border: 1px solid var(--color-line); border-radius: 8px; box-shadow: var(--shadow-lg); transform-origin: top right; }
.owner-menu button { width: 100%; min-height: 39px; display: flex; align-items: center; gap: 9px; padding: 0 10px; background: transparent; border-radius: 6px; color: var(--color-text); font-size: 14px; }
.owner-menu button:hover { background: var(--color-bg); }
.owner-menu button.danger { color: var(--color-error); }
.owner-menu-enter-active, .owner-menu-leave-active { transition: opacity .16s ease, transform .16s ease; }
.owner-menu-enter-from, .owner-menu-leave-to { opacity: 0; transform: translateY(-4px) scale(.94); }

.error-wrap { min-height: 30vh; display: flex; align-items: center; justify-content: center; }

/* Dedicated article layout */
.detail-article { max-width: 720px; margin: 0 auto; padding: 28px 28px 20px; background: #fff; border-bottom: 1px solid #eaecf0; }

/* Author */
.moment-author { display: flex; align-items: center; gap: 10px; margin-bottom: 22px; }
.author-avatar {
  width: 42px; height: 42px; border-radius: 50%; flex-shrink: 0;
  background: var(--color-primary-bg); color: var(--color-primary);
  display: flex; align-items: center; justify-content: center;
  font-size: 18px; font-weight: 700; overflow: hidden; cursor: pointer;
}
.author-avatar img { width: 100%; height: 100%; object-fit: cover; }
.author-info { flex: 1; min-width: 0; }
.author-row { display: flex; align-items: center; gap: 6px; }
.author-name { font-size: 15px; font-weight: 700; color: var(--color-text); }
.author-badge {
  font-size: 11px; padding: 1px 6px; border-radius: 4px;
  background: var(--color-primary-bg); color: var(--color-primary); font-weight: 600;
}
.author-time { font-size: 12px; color: var(--color-text-muted); display: block; margin-top: 2px; }

.vis-badge {
  font-size: 11px; padding: 2px 8px; border-radius: 10px; font-weight: 600; flex-shrink: 0;
}
.vis-public { background: #E8F5E9; color: #388E3C; }
.vis-related { background: #FFF3E0; color: #EF6C00; }
.vis-private { background: #FCE4EC; color: #C62828; }

/* Title & Content */
.moment-title { margin-bottom: 18px; color: #202124; font-size: 26px; font-weight: 800; line-height: 1.35; }
.moment-text { color: #34373b; font-size: 16px; line-height: 1.85; white-space: pre-wrap; }

/* Images */
.moment-images { display: grid; gap: 4px; margin: 22px 8px 0; overflow: hidden; border-radius: 10px; }
.moment-images.img-n-1 { grid-template-columns: 1fr; }
.moment-images.img-n-1 .moment-img-wrap { aspect-ratio: auto; }
.moment-images.img-n-1 .moment-img-wrap img { height: auto; max-height: 720px; object-fit: contain; background: #f3f4f5; }
.moment-images.img-n-2 { grid-template-columns: 1fr 1fr; }
.moment-images.img-n-2 .moment-img-wrap { aspect-ratio: 1; }
.moment-images.img-n-3 { grid-template-columns: 1fr 1fr 1fr; }
.moment-images.img-n-4 { grid-template-columns: 1fr 1fr; }
.moment-images:not(.img-n-1):not(.img-n-2):not(.img-n-3):not(.img-n-4) { grid-template-columns: 1fr 1fr 1fr; }
.moment-img-wrap { overflow: hidden; border-radius: 7px; background: var(--color-bg); cursor: pointer; }
.moment-images:not(.img-n-1) .moment-img-wrap { aspect-ratio: 1; }
.moment-img-wrap img { width: 100%; height: 100%; object-fit: cover; transition: transform 0.2s; }
.moment-img-wrap:hover img { transform: scale(1.03); }

/* Reference */
.moment-ref {
  display: flex; align-items: center; gap: 10px; margin-top: 10px;
  padding: 10px; background: var(--color-bg); border-radius: var(--radius); cursor: pointer;
}
.ref-img { width: 44px; height: 44px; border-radius: 6px; object-fit: cover; flex-shrink: 0; }
.ref-info { flex: 1; min-width: 0; }
.ref-name { display: block; font-size: 14px; font-weight: 600; color: var(--color-text); }
.ref-desc { display: block; font-size: 12px; color: var(--color-text-muted); margin-top: 2px; }
.ref-price { font-size: 16px; font-weight: 800; color: var(--color-primary); flex-shrink: 0; }

/* Topics */
.moment-topics { display: flex; flex-wrap: wrap; gap: 8px; margin-top: 10px; }
.moment-topic {
  font-size: 13px; color: var(--color-primary); font-weight: 500;
  cursor: pointer; padding: 2px 0;
}
.moment-topic:hover { text-decoration: underline; }

/* Reaction chips */
.moment-reactions { display: flex; flex-wrap: wrap; gap: 6px; margin-top: 10px; }
.reaction-chip {
  display: inline-flex; align-items: center; gap: 3px;
  height: 32px; padding: 0 10px; border-radius: 9px;
  background: var(--color-bg); font-size: 13px; color: var(--color-text-muted);
  cursor: pointer; border: 1px solid transparent; transition: all 0.15s;
}
.reaction-chip.active {
  background: var(--color-primary-bg); border-color: var(--color-primary-border); color: var(--color-primary);
}

/* Action bar */
.moment-actions {
  display: flex; align-items: center; height: 48px;
  margin-top: 4px; padding-top: 8px;
  border-top: 1px solid var(--color-line);
}
.act-btn {
  flex: 1; display: inline-flex; align-items: center; justify-content: center; gap: 4px;
  padding: 8px 0; border: none; background: none; font-size: 14px;
  color: var(--color-text-muted); cursor: pointer; transition: color 0.15s;
}
.act-btn:hover { color: var(--color-primary); }
.act-btn.active { color: var(--color-like); }
.act-icon { line-height: 1; }
.act-num { font-size: 13px; font-weight: 500; }
.act-div { width: 1px; height: 20px; background: var(--color-line); flex-shrink: 0; }

/* Comments */
.comments-card {
  max-width: 720px; margin: 10px auto 0; padding: 20px 28px; background: #fff;
}
.comments-header { display: flex; align-items: center; gap: 7px; margin-bottom: 14px; color: var(--color-text); font-size: 16px; font-weight: 700; }
.comments-header i { min-width: 22px; height: 22px; display: grid; place-items: center; padding: 0 6px; border-radius: 11px; background: #f1f2f4; color: var(--color-text-muted); font-size: 11px; font-style: normal; font-weight: 700; }

.comment-item { display: flex; gap: 10px; padding: 14px 0; border-bottom: 1px solid var(--color-line); transition: background-color .28s ease, box-shadow .28s ease; }
.comment-item.shared-comment-highlight, .reply-item.shared-comment-highlight { background: #fff0df; box-shadow: 0 0 0 6px #fff0df; }
.comment-item:last-child { border-bottom: none; }

.comment-avatar {
  width: 36px; height: 36px; border-radius: 50%; flex-shrink: 0;
  background: var(--color-primary-bg); color: var(--color-primary);
  display: flex; align-items: center; justify-content: center;
  font-size: 15px; font-weight: 700; overflow: hidden;
}
.comment-avatar img { width: 100%; height: 100%; object-fit: cover; }

.comment-body { flex: 1; min-width: 0; }
.comment-top { display: flex; align-items: center; gap: 6px; flex-wrap: wrap; margin-bottom: 4px; }
.comment-name { font-size: 14px; font-weight: 700; color: var(--color-text); }
.comment-badge {
  font-size: 11px; padding: 1px 6px; border-radius: 4px;
  background: var(--color-primary-bg); color: var(--color-primary); font-weight: 600;
}
.comment-badge.merchant { background: #FFF3E0; color: #EF6C00; }
.comment-time { font-size: 12px; color: var(--color-text-light); }
.comment-more-wrap, .reply-more-wrap { position: relative; z-index: 42; margin-left: auto; }
.comment-more, .reply-more { width: 28px; height: 28px; display: inline-flex; align-items: center; justify-content: center; background: transparent; color: var(--color-text-muted); border-radius: 50%; }
.comment-more:hover, .reply-more:hover { background: var(--color-bg); }
.comment-menu { position: absolute; top: 30px; right: 0; z-index: 44; width: 174px; padding: 4px; background: #fff; border: 1px solid var(--color-line); border-radius: 8px; box-shadow: var(--shadow-lg); transform-origin: top right; }
.comment-menu button { width: 100%; min-height: 38px; display: flex; align-items: center; gap: 8px; padding: 0 9px; background: transparent; border-radius: 6px; color: var(--color-text); font-size: 13px; }
.comment-menu button:hover { background: var(--color-bg); }
.comment-menu button.danger { color: var(--color-error); }
.comment-menu button.danger:hover { background: var(--color-error-light); }
.comment-menu-backdrop { position: fixed; inset: 0; z-index: 41; }

.comment-reply-label { font-size: 12px; color: var(--color-text-muted); margin-bottom: 2px; }
.comment-text { font-size: 14px; line-height: 1.6; color: var(--color-text-secondary); word-break: break-word; }

.comment-images { display: flex; gap: 4px; margin-top: 8px; flex-wrap: wrap; }
.comment-img { width: 80px; height: 80px; object-fit: cover; border-radius: 6px; cursor: pointer; }

.comment-actions { display: flex; gap: 16px; margin-top: 8px; }
.comment-like-btn {
  border: none; background: none; font-size: 13px; color: var(--color-text-muted);
  cursor: pointer; padding: 2px 4px; border-radius: 4px; display: flex; align-items: center; gap: 3px;
}
.comment-like-btn.liked { color: var(--color-like); }
.comment-like-btn:hover { background: var(--color-bg); }
.comment-reply-btn {
  border: none; background: none; font-size: 13px; color: var(--color-text-muted);
  cursor: pointer; padding: 2px 4px; border-radius: 4px;
}
.comment-reply-btn:hover { background: var(--color-bg); }

.comment-replies {
  margin-top: 8px; padding: 10px; background: var(--color-bg);
  border-radius: var(--radius);
}
.reply-item { font-size: 13px; line-height: 1.6; padding: 4px 0; }
.reply-name { font-weight: 600; color: var(--color-text); }
.reply-target { color: var(--color-text-muted); }
.reply-colon { color: var(--color-text-muted); }
.reply-text { color: var(--color-text-secondary); }
.reply-more-wrap { display: inline-block; margin-left: 4px; vertical-align: middle; }
.reply-more { width: 24px; height: 24px; }
.reply-menu { top: 25px; display: block; }
.load-replies-btn {
  border: none; background: none; font-size: 13px; color: var(--color-primary);
  cursor: pointer; padding: 4px 0; margin-top: 4px; font-weight: 500;
}

.load-more-wrap { text-align: center; padding: 12px 0 4px; }
.load-more-btn {
  border: none; background: var(--color-bg); padding: 8px 24px;
  border-radius: var(--radius-pill); font-size: 14px; color: var(--color-text-muted);
  cursor: pointer; font-weight: 500;
}
.load-more-btn:hover { background: var(--color-line); }
.load-more-btn:disabled { opacity: 0.5; cursor: not-allowed; }

/* Input bar */
.comment-input-bar {
  max-width: 720px; position: sticky; bottom: 12px; margin: 12px auto 0;
  background: #fff; border-radius: var(--radius-lg); padding: 10px 14px;
  box-shadow: var(--shadow);
}
.guest-action-bar { max-width: 720px; position: sticky; bottom: 12px; margin: 12px auto 0; padding: 10px 14px; background: #fff; box-shadow: var(--shadow); }
.guest-action-bar button { width: 100%; min-height: 42px; border-radius: 8px; background: #f28c28; color: #fff; font-size: 14px; font-weight: 700; }
.reply-indicator {
  display: flex; justify-content: space-between; align-items: center;
  font-size: 12px; color: var(--color-text-muted); padding: 4px 8px;
  background: var(--color-bg); border-radius: var(--radius); margin-bottom: 8px;
}
.reply-cancel { border: none; background: none; font-size: 14px; color: var(--color-text-muted); cursor: pointer; }
.comment-input-row { display: flex; gap: 8px; align-items: center; }
.comment-img-btn { font-size: 22px; cursor: pointer; color: var(--color-text-muted); padding: 4px; border-radius: var(--radius); }
.comment-img-btn:hover { color: var(--color-primary); }
.comment-text-input {
  flex: 1; border: none; outline: none; font-size: 14px;
  padding: 8px 12px; background: var(--color-bg); border-radius: 20px; color: var(--color-text);
}
.comment-text-input::placeholder { color: var(--color-text-light); }
.comment-send-btn {
  border: none; padding: 8px 18px; border-radius: 20px;
  background: var(--color-primary); color: #fff; font-size: 14px; font-weight: 600;
  cursor: pointer; flex-shrink: 0;
}
.comment-send-btn:disabled { opacity: 0.4; cursor: not-allowed; }

/* Modal overlays */
.modal-overlay { position: fixed; inset: 0; background: rgba(0,0,0,0.4); display: flex; align-items: center; justify-content: center; z-index: 200; padding: 24px; }
/* Share sheet */
.share-panel {
  background: #fff; border-radius: var(--radius-xl); padding: 20px;
  width: min(400px, 90vw); max-height: 60vh; display: flex; flex-direction: column;
}
.share-title { font-size: 17px; font-weight: 700; text-align: center; margin-bottom: 14px; }
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
.share-list { flex: 1; overflow-y: auto; display: flex; flex-direction: column; gap: 2px; margin-bottom: 12px; }
.share-list :deep(.conv-tile) { border-radius: 7px; }
.share-list :deep(.conv-badge) { display: none; }
.share-sending { opacity: 0.55; pointer-events: none; }
.share-cancel {
  width: 100%; padding: 12px; border: none; background: var(--color-bg);
  border-radius: var(--radius-lg); font-size: 15px; font-weight: 600;
  color: var(--color-text-muted); cursor: pointer;
}
.share-cancel:hover { background: var(--color-line); }

@media (max-width: 480px) {
  .page-header { padding-top: max(9px, env(safe-area-inset-top)); }
  .detail-article { padding: 22px 16px 16px; }
  .moment-title { font-size: 23px; }
  .moment-author { margin-bottom: 18px; }
  .moment-text { font-size: 16px; line-height: 1.75; }
  .moment-images, .moment-images.img-n-1 { margin-inline: 4px; border-radius: 9px; }
  .comments-card { margin-top: 8px; padding: 18px 16px; }
  .comment-input-bar { bottom: max(8px, env(safe-area-inset-bottom)); margin: 8px; padding: 8px 10px; }
  .guest-action-bar { bottom: max(8px, env(safe-area-inset-bottom)); margin: 8px; padding: 8px; }
}
</style>
