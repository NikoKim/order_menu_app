<template>
  <component :is="tag" class="naval-chat-bubble" :class="[theme ? `bubble-${theme}` : '', { decorated: theme, mine, rich, compact }]" :data-bubble-style="theme || undefined">
    <span v-if="theme" class="bubble-skin" :style="skin" aria-hidden="true" />
    <span class="bubble-content"><slot /></span>
  </component>
</template>

<script setup>
import { computed, onUnmounted, ref, watch } from 'vue'
const props = defineProps({ bubbleStyle: String, expiresAt: [Number, String], mine: Boolean, rich: Boolean, compact: Boolean, tag: { type: String, default: 'div' } })
const now = ref(Date.now())
let expiryTimer
watch(() => props.expiresAt, value => {
  clearTimeout(expiryTimer)
  now.value = Date.now()
  if (Number(value) > now.value) expiryTimer = setTimeout(() => { now.value = Date.now() }, Math.min(2147483647, Number(value) - now.value + 5))
}, { immediate: true })
onUnmounted(() => clearTimeout(expiryTimer))
const theme = computed(() => ['captain', 'pirate', 'tabby'].includes(props.bubbleStyle) && (!props.expiresAt || Number(props.expiresAt) > now.value) ? props.bubbleStyle : '')
const skin = computed(() => ({ borderImageSource: `url(/naval/shop/bubble-${theme.value}-skin.png)` }))
</script>

<style scoped>
.naval-chat-bubble{--bubble-fill:#f1f7fb;--bubble-line:#a5bdcb;--bubble-ink:#455d6a;position:relative;display:block;width:fit-content;max-width:100%;min-width:0;padding:9px 12px;border:1px solid #e1e9eb;border-radius:2px 7px 7px 7px;background:#fff;color:#405f69;white-space:pre-wrap;overflow-wrap:anywhere;font-size:13px;font-weight:400;line-height:1.65;box-sizing:border-box}
.naval-chat-bubble.mine{background:var(--color-primary-bg,#fff1e5);border-color:var(--color-primary-line,#f2caa5);border-radius:7px 2px 7px 7px;color:#7e512e}
.naval-chat-bubble.rich{background:transparent;padding:0;border:0;white-space:normal}
.bubble-content{position:relative;z-index:1;display:block;min-width:0;max-width:100%;overflow-wrap:anywhere}
.naval-chat-bubble.decorated{isolation:isolate;border-color:transparent;background:transparent;color:var(--bubble-ink);overflow:visible;box-shadow:none}
.decorated:not(.rich){min-width:38px}
.bubble-skin{--skin-scale:.045;position:absolute;inset:-1px;z-index:0;border:1px solid transparent;border-image-slice:260 400 fill;border-image-width:11.7px 18px;border-image-outset:calc(var(--skin-top) * var(--skin-scale)) calc(var(--skin-right) * var(--skin-scale)) calc(var(--skin-bottom) * var(--skin-scale)) calc(var(--skin-left) * var(--skin-scale));border-image-repeat:repeat;pointer-events:none}
/* Compensate source transparency without changing the normal message box. */
.bubble-captain{--skin-top:70px;--skin-right:35px;--skin-bottom:86px;--skin-left:35px}
.naval-chat-bubble.bubble-pirate{--skin-top:59px;--skin-right:35px;--skin-bottom:66px;--skin-left:35px;--bubble-fill:#fbf4ed;--bubble-line:#c8aa7d;--bubble-ink:#6d594a}
.naval-chat-bubble.bubble-tabby{--skin-top:60px;--skin-right:80px;--skin-bottom:74px;--skin-left:44px;--bubble-fill:#fff0de;--bubble-line:#eab17b;--bubble-ink:#8b602f}
.naval-chat-bubble.compact{font-size:11px;line-height:1.6}
.compact .bubble-skin{--skin-scale:.035;border-image-width:9.1px 14px}
.compact .bubble-content{display:-webkit-box;-webkit-line-clamp:3;-webkit-box-orient:vertical;max-height:4.8em;overflow:hidden}
.compact.rich .bubble-content{display:block;max-height:none;overflow:visible}
.compact.rich .bubble-content :deep(.naval-message-sticker){width:90px;height:90px;object-fit:contain}
.rich .bubble-content{white-space:normal}.rich .bubble-content :deep(.naval-message-sticker){max-width:100%}
@media(prefers-reduced-motion:reduce){.naval-chat-bubble{transition:none}}
</style>
