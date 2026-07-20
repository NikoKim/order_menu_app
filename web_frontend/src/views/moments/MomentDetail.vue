<template>
  <div class="page-container">
    <div class="page-header">
      <button class="back-btn" @click="$router.back()"><ArrowLeft :size="20" /></button>
      <h1 class="page-title">动态详情</h1>
    </div>

    <LoadingSpinner v-if="loading" />
    <div v-else-if="error" class="error-wrap">
      <EmptyState icon="warning" :title="error" action-text="重试" @action="loadDetail" />
    </div>
    <template v-else-if="moment">
      <!-- Inline moment content -->
      <div class="detail-content">
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

        <!-- Title & Content -->
        <h3 v-if="moment.title" class="moment-title">{{ moment.title }}</h3>
        <div v-if="moment.content" class="moment-text">
          <p :class="{ clamped: contentClamped }" ref="contentEl">{{ moment.content }}</p>
          <button v-if="showExpand" class="expand-btn" @click="contentClamped = !contentClamped">
            {{ contentClamped ? '展开全文' : '收起' }}
          </button>
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

        <!-- Hot comment -->
        <div v-if="moment.hotComment" class="hot-comment" @click="focusComment">
          <span class="hot-user">{{ moment.hotComment.author?.nickname }}</span>
          <span class="hot-text">{{ moment.hotComment.content }}</span>
        </div>

        <!-- Action bar -->
        <div class="moment-actions">
          <button class="act-btn" :class="{ active: moment.likedByMe }" @click="handleLike">
            <Heart class="act-icon" :size="19" :fill="moment.likedByMe ? 'currentColor' : 'none'" />
            <span v-if="moment.likeCount" class="act-num">{{ moment.likeCount }}</span>
          </button>
          <span class="act-div"></span>
          <button class="act-btn" @click="showReactionPicker(moment)">
            <SmilePlus class="act-icon" :size="19" />
          </button>
          <span class="act-div"></span>
          <button class="act-btn" @click="focusComment">
            <MessageCircle class="act-icon" :size="19" />
            <span v-if="moment.commentCount" class="act-num">{{ moment.commentCount }}</span>
          </button>
          <span class="act-div"></span>
          <button class="act-btn" @click="openShareSheet">
            <Share2 class="act-icon" :size="19" />
          </button>
        </div>
      </div>

      <!-- Comments section -->
      <div class="comments-card">
        <div class="comments-header">评论 {{ moment.commentCount ? `(${moment.commentCount})` : '' }}</div>

        <div v-if="comments.length">
          <div v-for="c in comments" :key="c.id" class="comment-item">
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
                <button v-if="c.mine" class="comment-delete" @click="handleDeleteComment(c)">删除</button>
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
                <div v-for="r in c.replies" :key="r.id" class="reply-item">
                  <span class="reply-name">{{ r.author?.nickname }}</span>
                  <span v-if="r.replyToNickname" class="reply-target"> 回复 <b>{{ r.replyToNickname }}</b></span>
                  <span class="reply-colon">：</span>
                  <span class="reply-text">{{ r.content }}</span>
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
      </div>

      <!-- Comment input -->
      <div class="comment-input-bar">
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

    <!-- Image viewer -->
    <ImageViewer :visible="imageViewer.visible" :src="imageViewer.src" @close="imageViewer.visible = false" />

    <!-- Reaction picker -->
    <div v-if="reactionMoment" class="modal-overlay" @click.self="reactionMoment = null">
      <div class="reaction-panel">
        <button v-for="e in emojis" :key="e" class="reaction-btn"
          :class="{ active: reactionMoment.reactions?.find(r => r.emoji === e)?.reactedByMe }"
          @click="handleReaction(e)">{{ e }}</button>
      </div>
    </div>

    <!-- Share sheet -->
    <div v-if="shareSheet.visible" class="modal-overlay" @click.self="shareSheet.visible = false">
      <div class="share-panel">
        <h3 class="share-title">分享到聊天</h3>
        <LoadingSpinner v-if="shareSheet.loading" />
        <div v-else-if="shareSheet.conversations.length === 0" class="share-empty">暂无可用会话</div>
        <div v-else class="share-list">
          <button v-for="conv in shareSheet.conversations" :key="conv.id" class="share-item"
            :disabled="shareSheet.sending" @click="doShare(conv)">
            <div class="share-avatar">
              <img v-if="conv.otherUser?.avatarUrl" :src="imageUrl(conv.otherUser.avatarUrl)" alt="" />
              <span v-else>{{ conv.title?.charAt(0) || '?' }}</span>
            </div>
            <span class="share-name">{{ conv.title }}</span>
          </button>
        </div>
        <button class="share-cancel" @click="shareSheet.visible = false">取消</button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, nextTick } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowLeft, Heart, ImagePlus, MessageCircle, Share2, SmilePlus, X } from 'lucide-vue-next'
import { getMomentDetail, setMomentLike, addMomentReaction, getMomentComments, addMomentComment, deleteMomentComment, setCommentLike, getCommentReplies, uploadMomentCommentImage } from '../../api/moments'
import { getConversations, shareMomentToChat } from '../../api/messages'
import { imageUrl } from '../../composables/useImage'
import ImageViewer from '../../components/ImageViewer.vue'
import LoadingSpinner from '../../components/LoadingSpinner.vue'
import EmptyState from '../../components/EmptyState.vue'

const route = useRoute()
const router = useRouter()
const moment = ref(null)
const loading = ref(true)
const error = ref('')
const comments = ref([])
const commentPage = ref(1)
const hasMoreComments = ref(false)
const loadingComments = ref(false)

const commentText = ref('')
const commentInputEl = ref(null)
const replyTarget = ref(null)
const reactionMoment = ref(null)
const emojis = ['👍', '❤️', '😄', '😢', '😮', '🔥', '👏', '🎉']

const imageViewer = ref({ visible: false, src: '' })

const shareSheet = ref({ visible: false, conversations: [], loading: false, sending: false })

const contentClamped = ref(true)
const contentEl = ref(null)
const showExpand = ref(false)

// Check content height for expand button
function checkContentExpand() {
  nextTick(() => {
    if (contentEl.value) {
      const p = contentEl.value.querySelector('p')
      if (p && p.scrollHeight > 76) showExpand.value = true
    }
  })
}

const portalPrefix = route.path.startsWith('/shop/') ? '/shop' : '/customer'

async function loadDetail() {
  loading.value = true; error.value = ''
  try {
    moment.value = await getMomentDetail(route.params.id)
    checkContentExpand()
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
  const m = moment.value
  if (!m) return
  try {
    const liked = !m.likedByMe
    await setMomentLike(m.id, liked)
    m.likedByMe = liked
    m.likeCount = (m.likeCount || 0) + (liked ? 1 : -1)
  } catch (e) { alert(e.message) }
}

async function handleCommentLike(c) {
  try {
    const liked = !c.likedByMe
    await setCommentLike(Number(route.params.id), c.id, liked)
    c.likedByMe = liked
    c.likeCount = (c.likeCount || 0) + (liked ? 1 : -1)
  } catch (e) { alert(e.message) }
}

async function handleCommentSend() {
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
  } catch (e) { alert(e.message) }
}

function startReply(c) { replyTarget.value = c; commentText.value = ''; commentInputEl.value?.focus() }

async function handleDeleteComment(c) {
  if (!confirm('确认删除评论？')) return
  try {
    await deleteMomentComment(Number(route.params.id), c.id)
    comments.value = comments.value.filter(x => x.id !== c.id)
    if (moment.value) moment.value.commentCount = Math.max(0, (moment.value.commentCount || 1) - 1)
  } catch (e) { alert(e.message) }
}

async function handleCommentImage(e) {
  const file = e.target.files?.[0]
  if (!file) return
  try {
    const res = await uploadMomentCommentImage(file)
    commentText.value += (commentText.value ? ' ' : '') + res.url
  } catch (err) { alert(err.message) }
  e.target.value = ''
}

function showReactionPicker(m) { reactionMoment.value = m }

async function handleReaction(emoji) {
  const m = reactionMoment.value
  if (!m) return
  try {
    await addMomentReaction(m.id, emoji)
    const existing = m.reactions?.find(r => r.emoji === emoji)
    if (existing?.reactedByMe) { existing.reactedByMe = false; existing.count = Math.max(0, (existing.count || 1) - 1) }
    else if (existing) { existing.reactedByMe = true; existing.count = (existing.count || 0) + 1 }
    else {
      if (!m.reactions) m.reactions = []
      m.reactions.push({ emoji, count: 1, reactedByMe: true })
    }
    reactionMoment.value = null
  } catch (e) { alert(e.message) }
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

async function openShareSheet() {
  shareSheet.value = { visible: true, conversations: [], loading: true, sending: false }
  try {
    const convs = await getConversations()
    shareSheet.value.conversations = convs || []
  } catch { shareSheet.value.conversations = [] }
  finally { shareSheet.value.loading = false }
}

async function doShare(conv) {
  shareSheet.value.sending = true
  try {
    await shareMomentToChat({ momentId: moment.value.id, conversationId: conv.id })
    shareSheet.value.visible = false
    alert('已分享')
  } catch (e) { alert(e.message) }
  finally { shareSheet.value.sending = false }
}

function goReference(ref) {
  if (ref.shopId) {
    router.push(`/customer/shop/${ref.shopId}/menu`)
  }
}

function goTopic(t) {
  // Navigate back to feed — topic-specific feed page not yet implemented
  router.push(`${portalPrefix}/chat`)
}

function goAuthor(a) {
  if (a?.id) {
    router.push(`${portalPrefix}/chat/user/${a.id}`)
  }
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

onMounted(loadDetail)
</script>

<style scoped>
.page-header { display: flex; align-items: center; gap: 10px; margin-bottom: 14px; }
.back-btn {
  width: 36px; height: 36px; display: inline-flex; align-items: center; justify-content: center;
  border-radius: 50%; background: transparent; color: var(--color-text);
}
.page-title { font-size: 20px; font-weight: 700; }

.error-wrap { min-height: 30vh; display: flex; align-items: center; justify-content: center; }

/* Inline moment content */
.detail-content {
  background: #fff; border-radius: var(--radius-xl); padding: 16px; margin-bottom: 12px;
}

/* Author */
.moment-author { display: flex; align-items: center; gap: 10px; margin-bottom: 12px; }
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
.moment-title { font-size: 17px; font-weight: 700; color: var(--color-text); margin-bottom: 8px; line-height: 1.4; }
.moment-text { font-size: 15px; line-height: 1.58; color: var(--color-text-secondary); }
.moment-text p.clamped {
  display: -webkit-box; -webkit-line-clamp: 3; -webkit-box-orient: vertical; overflow: hidden;
}
.expand-btn {
  border: none; background: none; color: var(--color-primary);
  font-weight: 600; cursor: pointer; font-size: 14px; padding: 4px 0;
}

/* Images */
.moment-images { display: grid; gap: 3px; margin-top: 10px; border-radius: 6px; overflow: hidden; }
.moment-images.img-n-1 { grid-template-columns: 1fr; }
.moment-images.img-n-1 .moment-img-wrap { aspect-ratio: 16 / 10; }
.moment-images.img-n-2 { grid-template-columns: 1fr 1fr; }
.moment-images.img-n-2 .moment-img-wrap { aspect-ratio: 1; }
.moment-images.img-n-3 { grid-template-columns: 1fr 1fr 1fr; }
.moment-images.img-n-4 { grid-template-columns: 1fr 1fr; }
.moment-images:not(.img-n-1):not(.img-n-2):not(.img-n-3):not(.img-n-4) { grid-template-columns: 1fr 1fr 1fr; }
.moment-img-wrap { overflow: hidden; background: var(--color-bg); cursor: pointer; }
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

/* Hot comment */
.hot-comment {
  margin-top: 10px; padding: 8px 10px;
  background: #F5F5F5; border-radius: 6px;
  border-left: 3px solid var(--color-primary);
  font-size: 13px; line-height: 1.5; cursor: pointer;
}
.hot-user { font-weight: 600; color: var(--color-text); }
.hot-text { color: var(--color-text-secondary); margin-left: 4px; }

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
  background: #fff; border-radius: var(--radius-xl); padding: 16px; margin-top: 12px;
}
.comments-header { font-size: 15px; font-weight: 700; margin-bottom: 14px; color: var(--color-text); }

.comment-item { display: flex; gap: 10px; padding: 14px 0; border-bottom: 1px solid var(--color-line); }
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
.comment-delete {
  margin-left: auto; border: none; background: none; font-size: 12px;
  color: var(--color-error); cursor: pointer; padding: 2px 6px; border-radius: 4px;
}
.comment-delete:hover { background: var(--color-error-light); }

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
  position: sticky; bottom: 16px; margin-top: 16px;
  background: #fff; border-radius: var(--radius-lg); padding: 10px 14px;
  box-shadow: var(--shadow);
}
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
.reaction-panel {
  display: grid; grid-template-columns: repeat(4, 1fr); gap: 10px;
  background: #fff; border-radius: var(--radius-xl); padding: 20px;
}
.reaction-btn {
  font-size: 32px; padding: 12px; border: 2px solid transparent;
  background: var(--color-bg); border-radius: var(--radius-lg); cursor: pointer;
  transition: all 0.15s;
}
.reaction-btn:hover { transform: scale(1.1); }
.reaction-btn.active { border-color: var(--color-primary); background: var(--color-primary-bg); }

/* Share sheet */
.share-panel {
  background: #fff; border-radius: var(--radius-xl); padding: 20px;
  width: min(400px, 90vw); max-height: 60vh; display: flex; flex-direction: column;
}
.share-title { font-size: 17px; font-weight: 700; text-align: center; margin-bottom: 14px; }
.share-empty { text-align: center; color: var(--color-text-muted); padding: 20px; font-size: 14px; }
.share-list { flex: 1; overflow-y: auto; display: flex; flex-direction: column; gap: 4px; margin-bottom: 12px; }
.share-item {
  display: flex; align-items: center; gap: 10px; padding: 10px 8px;
  border: none; background: none; border-radius: var(--radius);
  cursor: pointer; text-align: left; font-size: 14px; color: var(--color-text);
}
.share-item:hover { background: var(--color-bg); }
.share-item:disabled { opacity: 0.5; cursor: not-allowed; }
.share-avatar {
  width: 40px; height: 40px; border-radius: 50%; flex-shrink: 0;
  background: var(--color-primary-bg); color: var(--color-primary);
  display: flex; align-items: center; justify-content: center;
  font-size: 16px; font-weight: 700; overflow: hidden;
}
.share-avatar img { width: 100%; height: 100%; object-fit: cover; }
.share-name { flex: 1; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.share-cancel {
  width: 100%; padding: 12px; border: none; background: var(--color-bg);
  border-radius: var(--radius-lg); font-size: 15px; font-weight: 600;
  color: var(--color-text-muted); cursor: pointer;
}
.share-cancel:hover { background: var(--color-line); }
</style>
