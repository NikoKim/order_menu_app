<template>
  <div class="moment-card" :class="{ 'no-navigate': noNavigate }" @click="!noNavigate && $emit('click', moment)">
    <!-- Author header -->
    <div class="mc-header">
      <div class="mc-avatar" @click.stop="$emit('author', moment.author)">
        <img v-if="moment.author?.avatarUrl" :src="imageUrl(moment.author.avatarUrl)" alt="" />
        <span v-else>{{ moment.author?.nickname?.charAt(0) || '?' }}</span>
      </div>
      <div class="mc-author-info">
        <div class="mc-author-row">
          <span class="mc-author-name">{{ moment.author?.nickname }}</span>
          <span v-if="moment.author?.merchant" class="mc-badge">商家</span>
        </div>
        <span class="mc-time">{{ formatTime(moment.publishedAt || moment.createdAt) }}</span>
      </div>
      <span v-if="moment.visibility" class="mc-vis-badge" :class="'vis-' + moment.visibility.toLowerCase()">
        {{ visLabel(moment.visibility) }}
      </span>
    </div>

    <!-- Title & Content -->
    <h3 v-if="moment.title" class="mc-title">{{ moment.title }}</h3>
    <div class="mc-content" :class="{ clamped: contentClamped }" v-if="moment.content">
      <p ref="contentEl">{{ moment.content }}</p>
      <button v-if="showExpand" class="mc-expand" @click.stop="contentClamped = !contentClamped">
        {{ contentClamped ? '展开全文' : '收起' }}
      </button>
    </div>

    <!-- Images grid -->
    <div v-if="moment.imageUrls?.length" class="mc-images" :class="'img-n-' + Math.min(moment.imageUrls.length, 9)">
      <div v-for="(img, i) in moment.imageUrls.slice(0, 9)" :key="i" class="mc-img-wrap" @click.stop="$emit('image', i)">
        <img :src="imageUrl(img)" alt="" loading="lazy" />
      </div>
    </div>

    <!-- Reference card -->
    <div v-if="moment.reference?.shopId" class="mc-ref" @click.stop="$emit('reference', moment.reference)">
      <img v-if="moment.reference.shopImageUrl" :src="imageUrl(moment.reference.shopImageUrl)" class="mc-ref-img" alt="" />
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

    <!-- Hot comment -->
    <div v-if="moment.hotComment" class="mc-hot-comment" @click.stop="$emit('comment', moment)">
      <span class="mc-hot-user">{{ moment.hotComment.author?.nickname }}</span>
      <span class="mc-hot-text">{{ moment.hotComment.content }}</span>
    </div>

    <!-- Action bar -->
    <div class="mc-actions">
      <button class="mc-act-btn" :class="{ active: moment.likedByMe }" @click.stop="$emit('like', moment)">
        <Heart class="mc-act-icon" :size="19" :fill="moment.likedByMe ? 'currentColor' : 'none'" />
        <span v-if="moment.likeCount" class="mc-act-num">{{ moment.likeCount }}</span>
      </button>
      <span class="mc-act-div"></span>
      <button class="mc-act-btn" @click.stop="$emit('reaction', moment)">
        <SmilePlus class="mc-act-icon" :size="19" />
      </button>
      <span class="mc-act-div"></span>
      <button class="mc-act-btn" @click.stop="$emit('comment', moment)">
        <MessageCircle class="mc-act-icon" :size="19" />
        <span v-if="moment.commentCount" class="mc-act-num">{{ moment.commentCount }}</span>
      </button>
      <span class="mc-act-div"></span>
      <button class="mc-act-btn" @click.stop="$emit('share', moment)">
        <Share2 class="mc-act-icon" :size="19" />
      </button>
    </div>
  </div>
</template>

<script setup>
import { ref, nextTick } from 'vue'
import { Heart, MessageCircle, Share2, SmilePlus } from 'lucide-vue-next'
import { imageUrl } from '../composables/useImage'

defineProps({
  moment: { type: Object, required: true },
  noNavigate: { type: Boolean, default: false }
})

defineEmits(['click', 'author', 'image', 'reference', 'topic', 'comment', 'like', 'reaction', 'share'])

const contentClamped = ref(true)
const contentEl = ref(null)
const showExpand = ref(false)

nextTick(() => {
  if (contentEl.value) {
    const p = contentEl.value.querySelector('p')
    if (p && p.scrollHeight > 76) showExpand.value = true
  }
})

function visLabel(v) {
  const map = { PUBLIC: '公开', RELATED: '相关', PRIVATE: '私密' }
  return map[v] || v
}

function formatTime(ts) {
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

/* Hot comment */
.mc-hot-comment {
  margin-top: 10px; padding: 8px 10px;
  background: #F5F5F5; border-radius: 6px;
  border-left: 3px solid var(--color-primary);
  font-size: 13px; line-height: 1.5;
  cursor: pointer;
}
.mc-hot-user { font-weight: 600; color: var(--color-text); }
.mc-hot-text { color: var(--color-text-secondary); margin-left: 4px; }

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
.mc-act-div { width: 1px; height: 20px; background: var(--color-line); flex-shrink: 0; }

@media (max-width: 480px) {
  .moment-card { padding: 14px 12px; border-radius: 0; margin-bottom: 8px; }
  .mc-actions { height: 46px; }
}
</style>
