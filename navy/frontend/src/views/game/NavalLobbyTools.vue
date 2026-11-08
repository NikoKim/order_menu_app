<template>
  <div ref="root" class="lobby-tools">
    <button ref="trigger" type="button" class="icon-control navy-toolbar-button lobby-tools-toggle" title="更多操作" aria-label="更多操作" aria-haspopup="menu" :aria-expanded="open" @click="open = !open" @keydown.down.prevent="focusFirst"><Ellipsis :size="20" /></button>
    <Transition name="lobby-menu">
      <nav v-if="open" ref="menu" class="lobby-tools-menu" role="menu" aria-label="更多操作" @keydown="menuKey">
        <button type="button" role="menuitem" class="compact-reviews-entry" @click="select('reviews')"><Star :size="17" />玩家点评</button>
        <button type="button" role="menuitem" @click="select('sound')"><VolumeX v-if="muted" :size="17" /><Volume2 v-else :size="17" />{{ muted ? '开启音效' : '关闭音效' }}</button>
        <button type="button" role="menuitem" @click="select('rules')"><CircleHelp :size="17" />对局规则</button>
        <button type="button" role="menuitem" @click="select('fullscreen')"><Minimize2 v-if="fullscreen" :size="17" /><Maximize v-else :size="17" />{{ fullscreen ? '取消全屏' : '全屏' }}</button>
      </nav>
    </Transition>
  </div>
</template>

<script setup>
import { nextTick, onMounted, onUnmounted, ref } from 'vue'
import { CircleHelp, Ellipsis, Maximize, Minimize2, Star, Volume2, VolumeX } from 'lucide-vue-next'

defineProps({muted:Boolean, fullscreen:Boolean})
const emit = defineEmits(['reviews', 'sound', 'rules', 'fullscreen'])
const root = ref(null), trigger = ref(null), menu = ref(null), open = ref(false)
function select(command) { open.value = false; emit(command) }
function outside(event) { if (open.value && !root.value?.contains(event.target)) open.value = false }
function escape(event) {
  if (event.key !== 'Escape' || !open.value) return
  event.preventDefault(); open.value = false; trigger.value?.focus()
}
const items = () => [...(menu.value?.querySelectorAll('button') || [])].filter(element => element.getClientRects().length)
async function focusFirst() { open.value = true; await nextTick(); items()[0]?.focus() }
function menuKey(event) {
  const choices = items(), index = choices.indexOf(document.activeElement)
  let next
  if (event.key === 'ArrowDown') next = (index + 1) % choices.length
  else if (event.key === 'ArrowUp') next = (index + choices.length - 1) % choices.length
  else if (event.key === 'Home') next = 0
  else if (event.key === 'End') next = choices.length - 1
  else if (event.key === 'Tab') { open.value = false; return }
  if (next != null) { event.preventDefault(); choices[next]?.focus() }
}
onMounted(() => { document.addEventListener('pointerdown', outside, true); document.addEventListener('focusin', outside); document.addEventListener('keydown', escape) })
onUnmounted(() => { document.removeEventListener('pointerdown', outside, true); document.removeEventListener('focusin', outside); document.removeEventListener('keydown', escape) })
</script>

<style scoped>
.lobby-tools{position:relative;display:none}.lobby-tools-toggle{width:28px;height:30px}
.lobby-tools-menu{position:absolute;right:0;top:calc(100% + 5px);z-index:480;width:156px;display:flex;flex-direction:column;gap:2px;padding:5px;border:1px solid #d8e4e7;border-radius:5px;background:#fff;box-shadow:0 8px 24px #254d5719;transform-origin:top right}
.lobby-tools-menu button{display:flex;align-items:center;gap:9px;min-height:34px;padding:7px 9px;background:transparent;color:#64838d;font-size:12px;text-align:left;white-space:nowrap}.lobby-tools-menu button>svg{flex-shrink:0}.lobby-tools-menu button:hover,.lobby-tools-menu button:focus-visible{background:var(--color-primary-bg,#fff1e5);color:var(--color-primary-strong,#b86620)}
.lobby-tools-menu .compact-reviews-entry{display:none}.lobby-menu-enter-active{transition:opacity .16s ease,transform .2s cubic-bezier(.2,.8,.3,1)}.lobby-menu-leave-active{transition:opacity .12s ease,transform .14s ease}.lobby-menu-enter-from,.lobby-menu-leave-to{opacity:0;transform:translateY(-5px) scale(.96)}
@media(max-width:700px){.lobby-tools{display:block}}@media(max-width:360px){.lobby-tools-menu .compact-reviews-entry{display:flex}}
@media(prefers-reduced-motion:reduce){.lobby-menu-enter-active,.lobby-menu-leave-active{transition-duration:.01s}}
</style>
