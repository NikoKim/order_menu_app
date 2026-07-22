<template>
  <div class="external-share-options">
    <div class="scope-setting" :class="{ 'info-open': infoOpen }">
      <label class="scope-check">
        <input v-model="isolated" type="checkbox" />
        <span>
          <strong>只分享此动态</strong>
          <small>勾选后，对方不能进入动态浏览页</small>
        </span>
      </label>
      <button
        class="info-button"
        type="button"
        aria-label="了解只分享此动态"
        :aria-expanded="infoOpen"
        @click.stop="infoOpen = !infoOpen"
      >
        <CircleHelp :size="17" />
      </button>
      <div class="info-tooltip" role="tooltip">
        对方打开后只能查看这条动态，无法通过页面入口继续浏览其他动态。
      </div>
    </div>

    <button class="share-submit" type="button" @click="$emit('select', isolated ? 'isolated' : 'browse')">
      <Share2 :size="19" />
      <span>分享{{ itemLabel }}</span>
    </button>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { CircleHelp, Share2 } from 'lucide-vue-next'

defineProps({ itemLabel: { type: String, default: '动态' } })
defineEmits(['select'])
const isolated = ref(false)
const infoOpen = ref(false)
</script>

<style scoped>
.external-share-options { display: grid; gap: 12px; }
.scope-setting {
  position: relative; min-height: 52px; display: flex; align-items: center; flex-wrap: wrap;
  padding: 7px 42px 7px 10px; border-radius: 7px; background: #f7f8fa;
}
.scope-check { min-width: 0; display: flex; align-items: center; gap: 9px; cursor: pointer; }
.scope-check input { width: 17px; height: 17px; flex: 0 0 auto; margin: 0; accent-color: var(--color-primary); }
.scope-check > span { min-width: 0; display: flex; flex-direction: column; }
.scope-check strong { color: var(--color-text); font-size: 13px; }
.scope-check small { margin-top: 1px; color: var(--color-text-muted); font-size: 11px; }
.info-button {
  position: absolute; top: 8px; right: 7px; z-index: 2; width: 36px; height: 36px;
  display: grid; place-items: center; border-radius: 50%; background: transparent;
  color: var(--color-text-muted);
}
.info-button:hover, .info-button:focus-visible { background: #e9ebee; color: var(--color-text); }
.info-tooltip {
  position: absolute; right: 7px; bottom: calc(100% + 5px); z-index: 5; width: min(270px, calc(100vw - 64px));
  padding: 10px 12px; border: 1px solid #e2e4e8; border-radius: 7px; background: #202124;
  color: #fff; font-size: 12px; line-height: 1.55; box-shadow: var(--shadow-lg);
  opacity: 0; visibility: hidden; transform: translateY(-4px); pointer-events: none;
  transition: opacity .16s ease, visibility .16s ease, transform .16s ease;
}
.info-button:hover + .info-tooltip,
.info-button:focus-visible + .info-tooltip,
.scope-setting.info-open .info-tooltip { opacity: 1; visibility: visible; transform: translateY(0); }
.scope-setting.info-open .info-tooltip {
  position: static; width: 100%; flex: 0 0 100%; margin: 7px 0 2px;
}
.share-submit {
  min-height: 46px; display: flex; align-items: center; justify-content: center; gap: 8px;
  border-radius: 7px; background: var(--color-primary); color: #fff; font-size: 14px; font-weight: 700;
}
.share-submit:hover { filter: brightness(.96); }
</style>
