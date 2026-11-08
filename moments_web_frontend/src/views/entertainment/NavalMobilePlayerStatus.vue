<template>
  <section class="mobile-player-status" :class="[`is-${side}`, { 'is-active': active, 'is-urgent': urgent, 'has-turn-end': canEndTurn }]" :data-side="side" :aria-label="`${name}的舰队信息`">
    <div class="status-identity">
      <NavalAvatar class="status-avatar" :person="person" :size="13" />
      <strong class="status-name" :title="name">{{ name }}</strong>
    </div>
    <div class="status-fleet" :title="`剩余舰船 ${remaining} / ${total}`"><Ship :size="10" /><span>{{ remaining }}/{{ total }}</span></div>
    <span v-if="side==='left' && Number.isFinite(coins)" class="status-coins" aria-label="剩余金币"><Coins :size="10" />{{ coins }}</span>
    <div v-if="active || preparing || canEndTurn" class="status-turn" :title="preparing ? stageLabel : '正在进攻'" aria-live="polite">
      <ShieldCheck v-if="preparing" :size="14" /><Crosshair v-else :size="14" />
      <strong v-if="!tutorial && clock" class="rail-clock">{{ clock }}</strong><span v-else-if="tutorial" class="status-training">演习</span>
      <button v-if="canEndTurn" class="turn-end-action" title="结束本次回合" aria-label="结束本次回合" :disabled="endTurnDisabled" @click="$emit('end-turn')"><SkipForward :size="12" /><span>结束回合</span></button>
    </div>
    <nav v-if="$slots.actions" class="status-actions" :aria-label="`${name}的对局操作`"><slot name="actions" /></nav>
    <Transition name="status-chat"><span v-if="message?.text" :key="message.id" class="status-chat-bubble" :class="{ 'sticker-pop': messageSticker(message.text) }" :title="messageSticker(message.text)?.name || message.text" role="status"><NavalMessage :text="message.text" :class="{ 'status-chat-text': !messageSticker(message.text) }" /></span></Transition>
  </section>
</template>

<script setup>
import { computed } from 'vue'
import { Coins, Crosshair, ShieldCheck, Ship, SkipForward } from 'lucide-vue-next'
import NavalAvatar from './NavalAvatar.vue'
import NavalMessage from './NavalMessage.vue'
import { messageSticker } from '../../games/navalStickers'

defineEmits(['end-turn'])
const props = defineProps({
  canEndTurn: Boolean,
  endTurnDisabled: Boolean,
  person: Object,
  coins: Number,
  side: { type: String, default: 'left' },
  active: Boolean,
  seconds: { type: Number, default: null },
  clock: { type: String, default: '' },
  total: { type: Number, default: 10 }, remaining: { type: Number, default: 10 },
  preparing: Boolean,
  stage: { type: String, default: '' },
  message: Object,
  tutorial: Boolean
})

const name = computed(() => props.person?.name || (props.side === 'left' ? '己方舰队' : '对方舰队'))
const urgent = computed(() => props.active && Number.isFinite(props.seconds) && props.seconds <= 5)
const stageLabel = computed(() => ({ PLACEMENT: '布阵', SUPPLY: '补给', READY: '就绪', FINISHED: '结束' })[props.stage] || '战备')
</script>

<style scoped>
.status-chat-bubble.sticker-pop{top:100%;bottom:auto;left:0;right:auto;width:90px;max-width:none;max-height:none;padding:0;background:transparent;border:0;box-shadow:none;overflow:visible;pointer-events:none}.is-right .status-chat-bubble.sticker-pop{left:auto;right:0}.status-chat-bubble.sticker-pop:after{display:none}.status-chat-bubble.sticker-pop :deep(img){width:90px;height:90px}
.mobile-player-status{container:naval-status / inline-size;--status-accent:#356d5b;--status-active:#e3f1eb;position:relative;z-index:275;display:flex;align-items:center;gap:4px;width:100%;height:26px;min-height:26px;min-width:0;padding:0 3px;box-sizing:border-box;border-bottom:2px solid #d1e0e5;background:#edf3f5;color:#64828e;letter-spacing:0;transition:background .25s,border-color .25s,color .25s}
.mobile-player-status.is-right{--status-accent:#925b36;--status-active:#fbede3;flex-direction:row-reverse}
.mobile-player-status.is-active{background:var(--status-active);border-bottom-color:var(--status-accent);color:var(--status-accent)}
.status-identity{position:relative;display:flex;align-items:center;flex:1 1 0;gap:4px;min-width:32px;height:100%}
.status-identity :deep(.naval-avatar){width:18px;height:18px;flex:0 0 18px;background:#f8fbfc;border-color:#cddde3}
.status-name{display:block;flex:1 1 0;min-width:0;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;font-size:11px;line-height:16px;font-weight:650;color:#496573}
.is-active .status-name{color:var(--status-accent)}
.is-right .status-identity{flex-direction:row-reverse}
.is-right .status-name{text-align:right}
.status-fleet{display:flex;flex:0 0 auto;align-items:center;justify-content:center;gap:2px;min-width:38px;white-space:nowrap;font-size:10px;line-height:14px;font-variant-numeric:tabular-nums}
.status-fleet>svg{flex:0 0 10px}
.status-coins{display:flex;align-items:center;gap:2px;flex-shrink:0;font-size:10px;color:#ac7c31;white-space:nowrap}
.status-turn{display:flex;flex:0 0 auto;align-items:center;justify-content:center;gap:2px;min-width:42px;height:24px;white-space:nowrap;color:var(--status-accent)}
.status-turn>svg{flex:0 0 14px}
.rail-clock{display:block;min-width:26px;font-size:11px;line-height:14px;font-weight:650;font-variant-numeric:tabular-nums;white-space:nowrap;text-align:center}
.status-training{font-size:10px;line-height:14px}
.turn-end-action{display:inline-flex;align-items:center;justify-content:center;gap:2px;height:22px;margin-left:3px;padding:0 4px;border:1px solid #9bbcaf;border-radius:3px;background:#f6fbf8;color:#3f755e;font-size:10px;white-space:nowrap;flex-shrink:0;transition:background .18s,opacity .18s}.turn-end-action:hover{background:#d6eade}.turn-end-action:disabled{opacity:.45}.has-turn-end .status-coins{display:none}
.is-active .status-turn>svg{animation:status-turn-pulse 1.8s ease-in-out infinite}
.is-urgent .rail-clock{color:#b44a3e}
.status-actions{display:flex;flex:0 0 auto;align-items:center;gap:4px;height:24px;margin-left:1px}
.is-right .status-actions{margin-left:0;margin-right:1px}
.status-chat-bubble{position:absolute;left:4px;top:calc(100% + 6px);z-index:340;display:block;width:max-content;max-width:180px;padding:5px 8px;border:1px solid #cfdee3;border-radius:5px;background:#fffffff5;color:#4f7481;box-shadow:0 3px 12px #224b5518;font-size:10px;line-height:15px;font-weight:400;text-align:left;pointer-events:none}
.status-chat-text{display:-webkit-box;-webkit-line-clamp:2;-webkit-box-orient:vertical;overflow:hidden;max-height:30px;white-space:pre-wrap;overflow-wrap:anywhere}
.is-right .status-chat-bubble{left:auto;right:4px}
.status-chat-enter-active,.status-chat-leave-active{transition:opacity .2s,transform .2s}
.status-chat-enter-from,.status-chat-leave-to{opacity:0;transform:translateY(-4px)}
@keyframes status-turn-pulse{50%{opacity:.4}}
@container naval-status (max-width:270px){.has-turn-end .status-fleet{display:none}.status-identity{min-width:18px}}
@media(prefers-reduced-motion:reduce){.mobile-player-status,.status-chat-enter-active,.status-chat-leave-active{transition-duration:.01s}.is-active .status-turn>svg{animation:none}}
</style>
