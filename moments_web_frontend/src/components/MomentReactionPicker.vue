<template>
  <div class="reaction-overlay" @click.self="$emit('close')">
    <section class="reaction-sheet" role="dialog" aria-modal="true" aria-labelledby="reaction-title">
      <div class="sheet-handle"></div>
      <header class="reaction-header">
        <h2 id="reaction-title">选择回应</h2>
        <button title="关闭" aria-label="关闭" @click="$emit('close')"><X :size="20" /></button>
      </header>
      <div class="reaction-grid">
        <button
          v-for="emoji in momentReactionEmojis"
          :key="emoji"
          :class="{ active: reaction(emoji)?.reactedByMe }"
          @click="$emit('select', emoji)"
        >
          <span>{{ emoji }}</span>
          <small>{{ reaction(emoji)?.count || 0 }}</small>
        </button>
      </div>
    </section>
  </div>
</template>

<script setup>
import { X } from 'lucide-vue-next'
import { momentReactionEmojis } from '../constants/momentReactions'

const props = defineProps({ reactions: { type: Array, default: () => [] } })
defineEmits(['select', 'close'])

function reaction(emoji) {
  return props.reactions.find(item => item.emoji === emoji)
}
</script>

<style scoped>
.reaction-overlay { position: fixed; inset: 0; z-index: 220; display: flex; align-items: center; justify-content: center; padding: 24px; background: rgba(0, 0, 0, 0.4); }
.reaction-sheet { width: min(430px, 100%); max-height: min(620px, calc(100dvh - 48px)); display: flex; flex-direction: column; overflow: hidden; border-radius: 8px; background: #fff; box-shadow: var(--shadow-lg); animation: reaction-in .24s cubic-bezier(.18,.89,.32,1.16) both; }
.sheet-handle { display: none; }
.reaction-header { min-height: 56px; display: flex; align-items: center; gap: 12px; padding: 0 14px 0 18px; border-bottom: 1px solid #eceef0; }
.reaction-header h2 { flex: 1; font-size: 18px; font-weight: 800; }
.reaction-header button { width: 38px; height: 38px; display: grid; place-items: center; border-radius: 50%; background: transparent; color: #747981; }
.reaction-header button:hover { background: #f2f3f5; }
.reaction-grid { display: grid; grid-template-columns: repeat(6, minmax(0, 1fr)); gap: 8px; padding: 14px; overflow-y: auto; }
.reaction-grid button { min-width: 0; aspect-ratio: 0.88; display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 2px; border: 1px solid transparent; border-radius: 8px; background: #f5f6f7; }
.reaction-grid button:hover { border-color: #ffd1a1; background: #fff5e9; }
.reaction-grid button.active { border-color: #f4a24f; background: #ffe2b8; }
.reaction-grid span { font-size: 25px; line-height: 1.15; }
.reaction-grid small { color: #73777d; font-size: 11px; font-weight: 700; }
.reaction-grid button.active small { color: #b75e0a; }
@keyframes reaction-in { from { opacity: 0; transform: translateY(12px) scale(.96); } to { opacity: 1; transform: translateY(0) scale(1); } }

@media (max-width: 600px) {
  .reaction-overlay { align-items: flex-end; padding: 0; }
  .reaction-sheet { width: 100%; height: min(390px, calc(100dvh - env(safe-area-inset-top) - 24px)); max-height: none; border-radius: 8px 8px 0 0; padding-bottom: env(safe-area-inset-bottom); }
  .sheet-handle { width: 36px; height: 4px; display: block; flex: 0 0 auto; margin: 8px auto 0; border-radius: 2px; background: #d7d9dc; }
  .reaction-header { min-height: 48px; border-bottom: 0; }
  .reaction-header button { width: 36px; height: 36px; }
  .reaction-grid { padding-top: 0; }
}

@media (max-width: 360px) {
  .reaction-grid { gap: 6px; padding-inline: 10px; }
  .reaction-grid span { font-size: 23px; }
}
</style>
