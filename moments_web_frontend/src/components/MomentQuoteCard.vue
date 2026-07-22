<template>
  <button type="button" class="quote-card" :class="{ unavailable: quote.available === false }" @click.stop="$emit('open', quote)">
    <span v-if="quote.type === 'MOMENT'" class="quote-title">{{ quote.momentTitle }}</span>
    <span class="quote-author">
      <span class="quote-avatar">
        <img v-if="quote.author?.avatarUrl" :src="imageUrl(quote.author.avatarUrl)" alt="" />
        <span v-else>{{ quote.author?.nickname?.charAt(0) || '?' }}</span>
      </span>
      <span>{{ quote.author?.nickname || '用户' }}</span>
      <time>{{ formatTime(quote.createdAt) }}</time>
    </span>
    <span class="quote-content" :class="quote.type === 'COMMENT' ? 'comment-lines' : 'moment-lines'">
      {{ quote.content || '[图片]' }}
    </span>
    <span v-if="quote.type === 'COMMENT'" class="quote-source">动态：{{ quote.momentTitle }}</span>
    <span v-if="quote.available === false" class="quote-status"><LockKeyhole :size="13" />详情权限已变更</span>
  </button>
</template>

<script setup>
import { LockKeyhole } from 'lucide-vue-next'
import { imageUrl } from '../composables/useImage'
import { formatMomentTime } from '../composables/useMomentTime'

defineProps({ quote: { type: Object, required: true } })
defineEmits(['open'])

const formatTime = formatMomentTime
</script>

<style scoped>
.quote-card { width: 100%; display: flex; flex-direction: column; align-items: stretch; gap: 6px; margin-top: 10px; padding: 9px 4px 9px 12px; border: 0; border-left: 2px solid #eceef0; border-radius: 0; background: #fff; color: #303338; text-align: left; }
.quote-card:hover { border-left-color: #d6d9dd; background: #fcfcfc; }
.quote-card.unavailable { border-left-color: #e3e5e8; }
.quote-title { overflow: hidden; font-size: 14px; font-weight: 750; text-overflow: ellipsis; white-space: nowrap; }
.quote-author { min-width: 0; display: flex; align-items: center; gap: 7px; color: #54585f; font-size: 12px; }
.quote-author time { margin-left: auto; color: #989da4; }
.quote-avatar { width: 24px; height: 24px; flex: 0 0 24px; display: grid; place-items: center; overflow: hidden; border-radius: 50%; background: #ffe7cd; color: #bd620b; font-size: 10px; font-weight: 700; }
.quote-avatar img { width: 100%; height: 100%; object-fit: cover; }
.quote-content { display: -webkit-box; overflow: hidden; -webkit-box-orient: vertical; color: #555a61; font-size: 13px; line-height: 1.55; white-space: normal; word-break: break-word; }
.quote-content.moment-lines { -webkit-line-clamp: 3; }
.quote-content.comment-lines { -webkit-line-clamp: 2; }
.quote-source { overflow: hidden; color: #8a8f97; font-size: 12px; text-overflow: ellipsis; white-space: nowrap; }
.quote-status { display: inline-flex; align-items: center; gap: 5px; color: #8a6060; font-size: 11px; }
</style>
