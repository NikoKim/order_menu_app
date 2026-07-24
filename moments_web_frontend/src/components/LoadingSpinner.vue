<template>
  <div class="skeleton-loader" :class="[variant, { fullscreen }]" role="status" aria-label="正在加载">
    <span v-if="text" class="loading-caption">{{ text }}</span>
    <span v-else class="sr-only">加载中</span>
    <template v-if="variant === 'chat-list'">
      <div v-for="i in 6" :key="i" class="sk-chat-row">
        <i class="sk-circle"></i><span><i class="sk-line wide"></i><i class="sk-line short"></i></span>
      </div>
    </template>
    <template v-else-if="variant === 'chat-detail'">
      <div v-for="i in 7" :key="i" class="sk-bubble-row" :class="{ mine: i % 3 === 0 }">
        <i class="sk-circle small"></i><i class="sk-bubble" :style="{ width: `${92 + (i % 4) * 24}px` }"></i>
      </div>
    </template>
    <template v-else-if="variant === 'moment-feed' || variant === 'moment-detail'">
      <div v-for="i in (variant === 'moment-detail' ? 1 : 3)" :key="i" class="sk-moment">
        <div class="sk-author"><i class="sk-circle"></i><span><i class="sk-line medium"></i><i class="sk-line tiny"></i></span></div>
        <i class="sk-line title"></i><i class="sk-line wide"></i><i class="sk-line medium"></i>
        <i class="sk-media"></i>
      </div>
    </template>
    <template v-else-if="variant === 'compact'">
      <i class="sk-line compact-line"></i>
    </template>
    <template v-else-if="variant === 'user-list'">
      <div v-for="i in 5" :key="i" class="sk-user-row">
        <i class="sk-circle user"></i><span><i class="sk-line medium"></i><i class="sk-line tiny"></i></span>
      </div>
    </template>
    <template v-else-if="variant === 'quote-list'">
      <div v-for="i in 3" :key="i" class="sk-quote-row">
        <i class="sk-line title"></i><i class="sk-line short"></i><i class="sk-line wide"></i>
      </div>
    </template>
    <template v-else-if="variant === 'comment-list'">
      <div v-for="i in 2" :key="i" class="sk-comment-row">
        <i class="sk-circle comment"></i>
        <span><i class="sk-line short"></i><i class="sk-line wide"></i></span>
      </div>
    </template>
    <template v-else-if="variant === 'topic-chips'">
      <div class="sk-topic-list">
        <i v-for="i in 6" :key="i" class="sk-topic" :class="{ wide: i % 3 === 0 }"></i>
      </div>
    </template>
    <template v-else>
      <div class="sk-generic"><i class="sk-line title"></i><i class="sk-line wide"></i><i class="sk-line medium"></i></div>
    </template>
  </div>
</template>

<script setup>
defineProps({
  fullscreen: Boolean,
  text: { type: String, default: '' },
  variant: { type: String, default: 'generic' }
})
</script>

<style scoped>
.skeleton-loader { width: 100%; padding: 12px; }
.skeleton-loader.fullscreen { position: fixed; inset: 0; z-index: 1000; overflow: hidden; background: rgba(255,255,255,.94); }
.sk-line, .sk-circle, .sk-bubble, .sk-media, .sk-topic { display: block; overflow: hidden; position: relative; background: #eef0f2; }
.sk-line::after, .sk-circle::after, .sk-bubble::after, .sk-media::after, .sk-topic::after { content: ''; position: absolute; inset: 0; transform: translateX(-100%); background: linear-gradient(90deg, transparent, rgba(255,255,255,.65), transparent); animation: shimmer 1.1s infinite; }
.sk-line { width: 100%; height: 11px; border-radius: 4px; }
.sk-line + .sk-line { margin-top: 9px; }
.sk-line.title { width: 68%; height: 17px; }
.sk-line.wide { width: 86%; }
.sk-line.medium { width: 55%; }
.sk-line.short { width: 34%; }
.sk-line.tiny { width: 25%; height: 8px; }
.sk-circle { width: 46px; height: 46px; flex: 0 0 auto; border-radius: 50%; }
.sk-circle.small { width: 34px; height: 34px; }
.sk-chat-row { min-height: 72px; display: flex; align-items: center; gap: 12px; padding: 10px 8px; background: #fff; }
.sk-chat-row > span, .sk-author > span { min-width: 0; flex: 1; }
.sk-bubble-row { display: flex; align-items: flex-end; gap: 8px; margin: 11px 0; }
.sk-bubble-row.mine { flex-direction: row-reverse; }
.sk-bubble { height: 42px; border-radius: 8px; }
.sk-moment { margin-bottom: 10px; padding: 16px; background: #fff; border-radius: 8px; }
.sk-author { display: flex; align-items: center; gap: 10px; margin-bottom: 16px; }
.sk-media { width: 100%; aspect-ratio: 16 / 7; margin-top: 14px; border-radius: 6px; }
.sk-generic { padding: 20px; background: #fff; border-radius: 8px; }
.compact { padding: 16px 24px; }
.compact-line { width: 92px; height: 8px; margin: 0 auto; }
.user-list { padding: 0 8px 10px; }
.sk-user-row { min-height: 50px; display: flex; align-items: center; gap: 11px; padding: 6px 10px; }
.sk-user-row > span { min-width: 0; flex: 1; }
.sk-circle.user { width: 36px; height: 36px; flex-basis: 36px; }
.quote-list { padding: 0; }
.sk-quote-row { min-height: 76px; padding: 11px 12px; border-bottom: 1px solid #eceef0; }
.sk-quote-row .sk-line + .sk-line { margin-top: 7px; }
.comment-list { padding: 4px 10px; }
.sk-comment-row { min-height: 56px; display: flex; align-items: flex-start; gap: 8px; padding: 7px 0; }
.sk-comment-row > span { min-width: 0; flex: 1; padding-top: 3px; }
.sk-circle.comment { width: 28px; height: 28px; flex-basis: 28px; }
.topic-chips { padding: 1px; }
.sk-topic-list { display: flex; flex-wrap: wrap; gap: 7px; }
.sk-topic { width: 92px; height: 30px; border-radius: 16px; }
.sk-topic.wide { width: 114px; }
.loading-caption {
  min-height: 32px; display: flex; align-items: center; justify-content: center;
  margin-bottom: 4px; color: #555b63; font-size: 13px; font-weight: 600;
}
.sr-only { position: absolute; width: 1px; height: 1px; overflow: hidden; clip: rect(0, 0, 0, 0); }
@keyframes shimmer { to { transform: translateX(100%); } }
@media (prefers-reduced-motion: reduce) {
  .sk-line::after, .sk-circle::after, .sk-bubble::after, .sk-media::after, .sk-topic::after { animation-duration: 2.4s; }
}
@media (max-width: 480px) { .skeleton-loader { padding: 8px 0; } .sk-moment { border-radius: 0; } }
</style>
