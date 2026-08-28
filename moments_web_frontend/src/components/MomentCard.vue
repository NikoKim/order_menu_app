<template>
  <div class="moment-card" :class="{ 'no-navigate': noNavigate }" @click="!noNavigate && $emit('click', moment)">
    <!-- Author header -->
    <div class="mc-header">
      <div class="mc-avatar" @click.stop="$emit('author', moment.author)">
        <img v-if="moment.author?.avatarUrl" :src="imageUrl(moment.author.avatarUrl)" alt="" decoding="async" />
        <span v-else>{{ moment.author?.nickname?.charAt(0) || '?' }}</span>
      </div>
      <div class="mc-author-info">
        <div class="mc-author-row">
          <span class="mc-author-name">{{ moment.author?.nickname }}</span>
          <span v-if="moment.author?.merchant" class="mc-badge">商家</span>
        </div>
        <span class="mc-time">
          {{ showEdited ? `编辑于 ${formatTime(moment.editedAt)}` : formatTime(moment.publishedAt || moment.createdAt) }}
        </span>
      </div>
      <div class="mc-header-actions">
        <span v-if="moment.visibility" class="mc-vis-badge" :class="'vis-' + moment.visibility.toLowerCase()">
          {{ visLabel(moment.visibility) }}
        </span>
        <button ref="menuAnchor" class="mc-more-btn" type="button" title="动态操作" aria-label="动态操作" @click.stop="menuOpen = !menuOpen">
          <MoreVertical :size="20" />
        </button>
        <AnchoredMenu :open="menuOpen" :anchor="menuAnchor" @close="menuOpen = false">
          <div class="mc-owner-menu">
            <button type="button" @click="runOwnerAction('quote')"><Quote :size="16" />引用动态</button>
            <button v-if="moment.editable" type="button" @click="runOwnerAction('edit')"><Pencil :size="16" />编辑</button>
            <button v-if="moment.editable" type="button" class="danger" @click="runOwnerAction('delete')"><Trash2 :size="16" />删除</button>
          </div>
        </AnchoredMenu>
      </div>
    </div>

    <!-- Title & Content -->
    <h3 v-if="moment.title" class="mc-title">{{ moment.title }}</h3>
    <div class="mc-content" :class="{ clamped: contentClamped }" v-if="moment.content">
      <p ref="contentEl"><MentionText :content="moment.content" :mentions="moment.mentions" /></p>
      <button v-if="showExpand" class="mc-expand" @click.stop="contentClamped = !contentClamped">
        {{ contentClamped ? '展开全文' : '收起' }}
      </button>
    </div>

    <MomentQuoteCard v-if="moment.quote" :quote="moment.quote" @open="openQuote" />

    <!-- Images grid -->
    <div v-if="moment.imageUrls?.length" class="mc-images" :class="'img-n-' + Math.min(moment.imageUrls.length, 9)">
      <div v-for="(img, i) in moment.imageUrls.slice(0, 9)" :key="i" class="mc-img-wrap" @click.stop="$emit('image', i)">
        <img :src="imageUrl(img)" alt="" loading="lazy" decoding="async" />
      </div>
    </div>

    <!-- Reference card -->
    <div v-if="moment.reference?.shopId" class="mc-ref" @click.stop="$emit('reference', moment.reference)">
      <img v-if="moment.reference.shopImageUrl" :src="imageUrl(moment.reference.shopImageUrl)" class="mc-ref-img" alt="" loading="lazy" decoding="async" />
      <div class="mc-ref-info">
        <span class="mc-ref-name">{{ moment.reference.shopName }}</span>
        <span v-if="moment.reference.dishName" class="mc-ref-desc">{{ moment.reference.dishName }}</span>
      </div>
      <span v-if="moment.reference.dishPrice" class="mc-ref-price">{{ moment.reference.dishPrice }}</span>
    </div>

    <!-- Topics -->
    <div v-if="moment.topics?.length" class="mc-topics">
      <span v-for="t in moment.topics" :key="t.id" class="mc-topic" @click.stop="$emit('topic', t)">#{{ t.name }}</span>
    </div>

    <!-- Reaction chips -->
    <div v-if="moment.reactions?.length" class="mc-reactions">
      <span v-for="r in moment.reactions" :key="r.emoji" class="mc-reaction-chip" :class="{ active: r.reactedByMe }"
        @click.stop="$emit('reaction', moment)">
        {{ r.emoji }} {{ r.count }}
      </span>
    </div>

    <!-- Comment preview -->
    <div v-if="previewComment || commentsExpanded" class="mc-comment-list">
      <TransitionGroup name="mc-comment" tag="div" class="mc-comment-items">
      <div v-for="comment in visibleComments" :key="comment.id" class="mc-comment-preview" @click.stop="$emit('comment', moment)">
        <div class="mc-comment-main">
          <div class="mc-comment-avatar">
            <img v-if="comment.author?.avatarUrl" :src="imageUrl(comment.author.avatarUrl)" alt="" loading="lazy" decoding="async" />
            <span v-else>{{ comment.author?.nickname?.charAt(0) || '?' }}</span>
          </div>
          <div class="mc-comment-copy">
            <div class="mc-comment-author">
              <strong>{{ comment.author?.nickname }}</strong>
              <span v-if="comment.authorBadge" class="mc-comment-badge">作者</span>
              <time>{{ formatTime(comment.createdAt) }}</time>
            </div>
            <p><MentionText :content="comment.content || (comment.imageUrls?.length ? '[图片]' : '')" :mentions="comment.mentions" /></p>
            <div v-if="comment.imageUrls?.length" class="mc-comment-images">
              <span v-for="(img, i) in comment.imageUrls.slice(0, 3)" :key="i" class="mc-comment-image">
                <img :src="imageUrl(img)" alt="" loading="lazy" decoding="async" />
                <i v-if="i === 2 && comment.imageUrls.length > 3">
                  +{{ comment.imageUrls.length - 3 }}
                </i>
              </span>
            </div>
            <div v-if="comment.likeCount || comment.replyCount" class="mc-comment-meta">
              <span v-if="comment.likeCount"><Heart :size="13" />{{ comment.likeCount }}</span>
              <span v-if="comment.replyCount"><MessageCircle :size="13" />{{ comment.replyCount }}</span>
            </div>
          </div>
        </div>
        <div v-if="comment.replies?.length" class="mc-reply-preview">
          <p v-for="reply in comment.replies.slice(0, 2)" :key="reply.id">
            <strong>{{ reply.author?.nickname }}</strong><span v-if="reply.replyToNickname"> 回复 {{ reply.replyToNickname }}</span>：<MentionText :content="reply.content || '[图片]'" :mentions="reply.mentions" />
          </p>
        </div>
      </div>
      </TransitionGroup>
      <LoadingSpinner v-if="commentsLoading" :variant="loadedComments.length <= 1 ? 'comment-list' : 'compact'" />
      <div class="mc-comment-controls" @click.stop>
        <button v-if="!commentsExpanded && canExpandComments" type="button" @click="expandComments">展开更多评论</button>
        <button v-else-if="commentsExpanded && commentsHasMore" type="button" :disabled="commentsLoading" @click="loadMoreComments">
          {{ commentsLoading ? '正在加载' : (commentLoadError ? '加载失败，点击重试' : '展开更多评论') }}
        </button>
        <button v-if="commentsExpanded" type="button" class="collapse" @click="collapseComments">收起评论</button>
      </div>
    </div>

    <!-- Action bar -->
    <div class="mc-actions">
      <button class="mc-act-btn" :class="{ active: moment.likedByMe }" @click.stop="$emit('like', moment)">
        <Heart class="mc-act-icon" :size="19" :fill="moment.likedByMe ? 'currentColor' : 'none'" />
        <Transition name="count" mode="out-in"><span :key="moment.likeCount || 0" class="mc-act-num">{{ moment.likeCount || 0 }}</span></Transition>
      </button>
      <span class="mc-act-div"></span>
      <button class="mc-act-btn" @click.stop="$emit('reaction', moment)">
        <SmilePlus class="mc-act-icon" :size="19" />
        <Transition name="count" mode="out-in"><span :key="reactionCount" class="mc-act-num">{{ reactionCount }}</span></Transition>
      </button>
      <span class="mc-act-div"></span>
      <button class="mc-act-btn" @click.stop="$emit('comment', moment)">
        <MessageCircle class="mc-act-icon" :size="19" />
        <Transition name="count" mode="out-in"><span :key="moment.commentCount || 0" class="mc-act-num">{{ moment.commentCount || 0 }}</span></Transition>
      </button>
      <span class="mc-act-div"></span>
      <button class="mc-act-btn" @click.stop="$emit('share', moment)">
        <Share2 class="mc-act-icon" :size="19" />
        <Transition name="count" mode="out-in"><span :key="moment.shareCount || 0" class="mc-act-num">{{ moment.shareCount || 0 }}</span></Transition>
      </button>
    </div>
  </div>
</template>

<script setup>
import { computed, ref, nextTick, watch } from 'vue'
import { useRouter } from 'vue-router'
import { Heart, MessageCircle, MoreVertical, Pencil, Quote, Share2, SmilePlus, Trash2 } from 'lucide-vue-next'
import { getMomentComments } from '../api/moments'
import { imageUrl } from '../composables/useImage'
import { formatMomentTime, hasMeaningfulMomentEdit } from '../composables/useMomentTime'
import AnchoredMenu from './AnchoredMenu.vue'
import LoadingSpinner from './LoadingSpinner.vue'
import MomentQuoteCard from './MomentQuoteCard.vue'
import MentionText from './MentionText.vue'

const props = defineProps({
  moment: { type: Object, required: true },
  noNavigate: { type: Boolean, default: false }
})
const router = useRouter()

const emit = defineEmits(['click', 'author', 'image', 'reference', 'topic', 'comment', 'like', 'reaction', 'share', 'quote', 'open-quote', 'edit', 'delete'])

const contentClamped = ref(true)
const contentEl = ref(null)
const showExpand = ref(false)
const menuOpen = ref(false)
const menuAnchor = ref(null)
const commentsExpanded = ref(false)
const commentsLoading = ref(false)
const commentLoadError = ref(false)
const loadedComments = ref([])
const fallbackPreview = ref(null)
const previewLoading = ref(false)
const commentsPage = ref(1)
const commentsTotal = ref(0)
const commentsHasMore = ref(false)
const commentPageSize = 3
const reactionCount = computed(() => (props.moment.reactions || []).reduce((sum, item) => sum + Number(item.count || 0), 0))
const previewComment = computed(() => props.moment.hotComment || fallbackPreview.value)
const visibleComments = computed(() => commentsExpanded.value ? loadedComments.value : (previewComment.value ? [previewComment.value] : []))
const canExpandComments = computed(() => Number(props.moment.rootCommentCount || 0) >= 2)

async function loadCommentPreview() {
  if (previewLoading.value || previewComment.value || Number(props.moment.commentCount || 0) <= 0) return
  previewLoading.value = true
  const requestedId = Number(props.moment.id)
  try {
    const result = await getMomentComments(requestedId, { page: 1, pageSize: 1 })
    if (Number(props.moment.id) === requestedId) fallbackPreview.value = result.records?.[0] || null
  } catch { }
  finally { previewLoading.value = false }
}

async function loadMoreComments() {
  if (commentsLoading.value) return
  commentsLoading.value = true
  commentLoadError.value = false
  try {
    const result = await getMomentComments(props.moment.id, { page: commentsPage.value, pageSize: commentPageSize })
    const knownIds = new Set(loadedComments.value.map(comment => comment.id))
    const nextComments = (result.records || []).filter(comment => !knownIds.has(comment.id))
    loadedComments.value.push(...nextComments)
    commentsTotal.value = Number(result.total || loadedComments.value.length)
    commentsHasMore.value = Boolean(result.hasMore)
    commentsPage.value += 1
  } catch {
    commentLoadError.value = true
    commentsHasMore.value = true
    commentsTotal.value = Math.max(commentsTotal.value, loadedComments.value.length + 1)
  } finally {
    commentsLoading.value = false
  }
}

async function expandComments() {
  if (!loadedComments.value.length && props.moment.hotComment) loadedComments.value = [props.moment.hotComment]
  commentsExpanded.value = true
  if (commentsPage.value === 1) await loadMoreComments()
}

function collapseComments() {
  commentsExpanded.value = false
}

watch(() => props.moment.id, () => {
  commentsExpanded.value = false
  commentsLoading.value = false
  commentLoadError.value = false
  loadedComments.value = []
  commentsPage.value = 1
  commentsTotal.value = 0
  commentsHasMore.value = false
  fallbackPreview.value = null
  loadCommentPreview()
})

watch(() => [props.moment.commentCount, props.moment.hotComment], ([count, hotComment]) => {
  if (hotComment || Number(count || 0) <= 0) fallbackPreview.value = null
  loadCommentPreview()
})

function runOwnerAction(action) {
  menuOpen.value = false
  if (action === 'quote') {
    if (!localStorage.getItem('token')) {
      router.push({ path: '/customer/login', query: { redirect: router.currentRoute.value.fullPath } })
      return
    }
    router.push({ path: '/customer/moments/new', query: { quotedMomentId: props.moment.id } })
    return
  }
  emit(action, props.moment)
}

function openQuote(quote) {
  if (!quote?.momentId) return
  router.push({ path: `/customer/moments/${quote.momentId}`, query: { ...(quote.commentId ? { commentId: quote.commentId } : {}), fromQuote: '1' } })
}

nextTick(() => {
  if (contentEl.value) {
    if (contentEl.value.scrollHeight > 76) showExpand.value = true
  }
  loadCommentPreview()
})

function visLabel(v) {
  const map = { PUBLIC: '公开', RELATED: '相关', PRIVATE: '私密' }
  return map[v] || v
}

const formatTime = formatMomentTime
const showEdited = computed(() => hasMeaningfulMomentEdit(props.moment))
</script>

<style scoped>
.moment-card {
  background: #fff;
  border-radius: var(--radius-xl);
  padding: 16px;
  margin-bottom: 12px;
  cursor: pointer;
}
.moment-card.no-navigate { cursor: default; }

.mc-header {
  display: flex; align-items: center; gap: 10px; margin-bottom: 12px;
}
.mc-avatar {
  width: 42px; height: 42px; border-radius: 50%; flex-shrink: 0;
  background: var(--color-primary-bg); color: var(--color-primary);
  display: flex; align-items: center; justify-content: center;
  font-size: 18px; font-weight: 700; overflow: hidden;
}
.mc-avatar img { width: 100%; height: 100%; object-fit: cover; }
.mc-author-info { flex: 1; min-width: 0; }
.mc-header-actions { position: relative; display: flex; align-items: center; gap: 4px; }
.mc-more-btn { width: 34px; height: 34px; display: inline-flex; align-items: center; justify-content: center; border-radius: 7px; background: transparent; color: var(--color-text-muted); }
.mc-more-btn:hover { background: var(--color-bg); color: var(--color-text); }
.mc-owner-menu { width: 142px; overflow: hidden; padding: 5px; background: #fff; border: 1px solid var(--color-line); border-radius: 8px; box-shadow: var(--shadow-lg); }
.mc-owner-menu button { width: 100%; min-height: 38px; display: flex; align-items: center; gap: 9px; padding: 0 10px; background: transparent; border-radius: 6px; color: var(--color-text); font-size: 14px; text-align: left; }
.mc-owner-menu button:hover { background: var(--color-bg); }
.mc-owner-menu button.danger { color: var(--color-error); }
.mc-author-row { display: flex; align-items: center; gap: 6px; }
.mc-author-name { font-size: 15px; font-weight: 700; color: var(--color-text); }
.mc-badge {
  font-size: 11px; padding: 1px 6px; border-radius: 4px;
  background: var(--color-primary-bg); color: var(--color-primary); font-weight: 600;
}
.mc-time { font-size: 12px; color: var(--color-text-muted); display: block; margin-top: 2px; }

.mc-vis-badge {
  font-size: 11px; padding: 2px 8px; border-radius: 10px; font-weight: 600; flex-shrink: 0;
}
.vis-public { background: #E8F5E9; color: #388E3C; }
.vis-related { background: #FFF3E0; color: #EF6C00; }
.vis-private { background: #FCE4EC; color: #C62828; }

.mc-title {
  font-size: 17px; font-weight: 700; color: var(--color-text);
  margin-bottom: 8px; line-height: 1.4;
}

.mc-content { font-size: 15px; line-height: 1.58; color: var(--color-text-secondary); position: relative; }
.mc-content.clamped p {
  display: -webkit-box; -webkit-line-clamp: 3; -webkit-box-orient: vertical; overflow: hidden;
}
.mc-expand {
  border: none; background: none; color: var(--color-primary);
  font-weight: 600; cursor: pointer; font-size: 14px; padding: 4px 0;
}

/* Image grid */
.mc-images { display: grid; gap: 3px; margin-top: 10px; border-radius: 6px; overflow: hidden; }
.mc-images.img-n-1 { grid-template-columns: 1fr; }
.mc-images.img-n-1 .mc-img-wrap { aspect-ratio: 16 / 10; }
.mc-images.img-n-2 { grid-template-columns: 1fr 1fr; }
.mc-images.img-n-2 .mc-img-wrap { aspect-ratio: 1; }
.mc-images.img-n-3 { grid-template-columns: 1fr 1fr 1fr; }
.mc-images.img-n-4 { grid-template-columns: 1fr 1fr; }
.mc-images:not(.img-n-1):not(.img-n-2):not(.img-n-3):not(.img-n-4) { grid-template-columns: 1fr 1fr 1fr; }
.mc-img-wrap { overflow: hidden; background: var(--color-bg); cursor: pointer; }
.mc-images:not(.img-n-1) .mc-img-wrap { aspect-ratio: 1; }
.mc-img-wrap img { width: 100%; height: 100%; object-fit: cover; transition: transform 0.2s; }
.mc-img-wrap:hover img { transform: scale(1.03); }

/* Reference */
.mc-ref {
  display: flex; align-items: center; gap: 10px; margin-top: 10px;
  padding: 10px; background: var(--color-bg); border-radius: var(--radius);
}
.mc-ref-img { width: 44px; height: 44px; border-radius: 6px; object-fit: cover; flex-shrink: 0; }
.mc-ref-info { flex: 1; min-width: 0; }
.mc-ref-name { display: block; font-size: 14px; font-weight: 600; color: var(--color-text); }
.mc-ref-desc { display: block; font-size: 12px; color: var(--color-text-muted); margin-top: 2px; }
.mc-ref-price { font-size: 16px; font-weight: 800; color: var(--color-primary); flex-shrink: 0; }

/* Topics */
.mc-topics { display: flex; flex-wrap: wrap; gap: 8px; margin-top: 10px; }
.mc-topic {
  font-size: 13px; color: var(--color-primary); font-weight: 500;
  cursor: pointer; padding: 2px 0;
}
.mc-topic:hover { text-decoration: underline; }

/* Reaction chips */
.mc-reactions { display: flex; flex-wrap: wrap; gap: 6px; margin-top: 10px; }
.mc-reaction-chip {
  display: inline-flex; align-items: center; gap: 3px;
  height: 32px; padding: 0 10px; border-radius: 9px;
  background: var(--color-bg); font-size: 13px; color: var(--color-text-muted);
  cursor: pointer; border: 1px solid transparent; transition: all 0.15s;
}
.mc-reaction-chip.active {
  background: var(--color-primary-bg); border-color: var(--color-primary-border); color: var(--color-primary);
}

/* Comment preview */
.mc-comment-list { margin-top: 8px; overflow: hidden; border-radius: 7px; background: #f6f7f8; }
.mc-comment-preview { max-height: 640px; overflow: hidden; padding: 8px 10px; cursor: pointer; }
.mc-comment-preview + .mc-comment-preview { border-top: 1px solid #e7e9ec; }
.mc-comment-enter-active, .mc-comment-leave-active { transition: opacity .22s ease, transform .22s ease, max-height .22s ease, padding .22s ease; }
.mc-comment-enter-from, .mc-comment-leave-to { max-height: 0; padding-top: 0; padding-bottom: 0; opacity: 0; transform: translateY(-5px); }
.mc-comment-main { display: flex; align-items: flex-start; gap: 8px; }
.mc-comment-avatar { width: 28px; height: 28px; flex: 0 0 auto; display: grid; place-items: center; overflow: hidden; border-radius: 50%; background: #fff0df; color: #dd750f; font-size: 12px; font-weight: 700; }
.mc-comment-avatar img { width: 100%; height: 100%; object-fit: cover; }
.mc-comment-copy { min-width: 0; flex: 1; }
.mc-comment-author { display: flex; align-items: center; gap: 5px; margin-bottom: 1px; color: #34373b; font-size: 13px; }
.mc-comment-author time { margin-left: auto; color: #969ba1; font-size: 11px; font-weight: 400; }
.mc-comment-badge { padding: 0 5px; border-radius: 3px; background: #ffe5c8; color: #c76509; font-size: 10px; }
.mc-comment-copy > p { display: -webkit-box; overflow: hidden; -webkit-box-orient: vertical; -webkit-line-clamp: 2; color: #50545a; font-size: 14px; line-height: 1.5; word-break: break-word; }
.mc-comment-images { display: flex; gap: 5px; margin-top: 7px; }
.mc-comment-image { width: 58px; height: 58px; position: relative; overflow: hidden; border-radius: 6px; background: #e9eaec; }
.mc-comment-image img { width: 100%; height: 100%; display: block; object-fit: cover; }
.mc-comment-image i { position: absolute; inset: 0; display: grid; place-items: center; background: rgba(0, 0, 0, 0.45); color: #fff; font-size: 13px; font-style: normal; font-weight: 700; }
.mc-comment-meta { display: flex; align-items: center; gap: 12px; margin-top: 3px; color: #969ba1; font-size: 11px; }
.mc-comment-meta span { display: inline-flex; align-items: center; gap: 3px; }
.mc-reply-preview { margin: 6px 0 0 36px; padding: 6px 8px; border-radius: 6px; background: #fff; }
.mc-reply-preview p { overflow: hidden; color: #6d7278; font-size: 13px; line-height: 1.55; text-overflow: ellipsis; white-space: nowrap; }
.mc-reply-preview strong { color: #45494f; }
.mc-comment-controls { display: flex; flex-direction: column; align-items: flex-start; gap: 1px; padding: 3px 8px 7px 42px; }
.mc-comment-controls button { min-height: 30px; padding: 0 7px; background: transparent; color: #767b82; font-size: 12px; }
.mc-comment-controls button:hover { color: var(--color-primary); }
.mc-comment-controls button:disabled { cursor: wait; opacity: .65; }

/* Action bar */
.mc-actions {
  display: flex; align-items: center; height: 48px;
  margin-top: 4px; padding-top: 8px;
  border-top: 1px solid var(--color-line);
}
.mc-act-btn {
  flex: 1; display: inline-flex; align-items: center; justify-content: center; gap: 4px;
  padding: 8px 0; border: none; background: none; font-size: 14px;
  color: var(--color-text-muted); cursor: pointer; transition: color 0.15s;
}
.mc-act-btn:hover { color: var(--color-primary); }
.mc-act-btn.active { color: var(--color-like); }
.mc-act-icon { line-height: 1; }
.mc-act-num { font-size: 13px; font-weight: 500; }
.count-enter-active, .count-leave-active { transition: opacity .14s ease, transform .14s ease; }
.count-enter-from { opacity: 0; transform: translateY(5px) scale(.8); }
.count-leave-to { opacity: 0; transform: translateY(-5px) scale(.8); }
.mc-act-div { width: 1px; height: 20px; background: var(--color-line); flex-shrink: 0; }

@media (max-width: 480px) {
  .moment-card { padding: 14px 12px; border-radius: 0; margin-bottom: 8px; }
  .mc-actions { height: 46px; }
}
</style>
