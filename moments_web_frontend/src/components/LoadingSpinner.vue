<template>
  <div class="skeleton-loader" :class="[variant, { fullscreen }]" role="status" aria-label="正在加载">
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
    <template v-else>
      <div class="sk-generic"><i class="sk-line title"></i><i class="sk-line wide"></i><i class="sk-line medium"></i></div>
    </template>
    <span class="sr-only">{{ text || '加载中' }}</span>
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
.sk-line, .sk-circle, .sk-bubble, .sk-media { display: block; overflow: hidden; position: relative; background: #eceef1; }
.sk-line::after, .sk-circle::after, .sk-bubble::after, .sk-media::after { content: ''; position: absolute; inset: 0; transform: translateX(-100%); background: linear-gradient(90deg, transparent, rgba(255,255,255,.72), transparent); animation: shimmer 1.25s infinite; }
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
.sr-only { position: absolute; width: 1px; height: 1px; overflow: hidden; clip: rect(0, 0, 0, 0); }
@keyframes shimmer { to { transform: translateX(100%); } }
@media (max-width: 480px) { .skeleton-loader { padding: 8px 0; } .sk-moment { border-radius: 0; } }
</style>
