<template>
  <Teleport :to="target">
    <div class="notice-stack" aria-live="polite" aria-atomic="true">
      <TransitionGroup name="notice-toast">
        <div v-for="item in toasts" :key="item.id" class="notice-toast" :class="item.type" :role="item.type === 'error' ? 'alert' : 'status'">
          <CircleCheck v-if="item.type === 'success'" :size="15" class="notice-symbol" />
          <TriangleAlert v-else :size="15" class="notice-symbol" />
          <span class="notice-message">{{ item.message }}</span>
          <button type="button" title="关闭" aria-label="关闭提示" @click="removeToast(item.id)"><X :size="13" /></button>
          <i :key="`${item.id}-${progressVersion}`" class="notice-progress" :style="countdownStyle(item)" aria-hidden="true" />
        </div>
      </TransitionGroup>
    </div>

    <AppDialog :open="Boolean(confirmation)" :title="displayConfirmation?.title" @closed="displayConfirmation=null" role="alertdialog" :dismissible="true" @close="finishConfirm(false)">
      <div class="notice-content">
        <div class="notice-dialog-icon" :class="{danger:displayConfirmation?.danger}"><TriangleAlert v-if="displayConfirmation?.danger" :size="23" /><CircleHelp v-else :size="23" /></div>
        <h2 :id="dialogTitleId">{{ displayConfirmation?.title }}</h2><p :id="dialogMessageId">{{ displayConfirmation?.message }}</p>
        <footer><button class="notice-cancel" @click="finishConfirm(false)">{{ displayConfirmation?.cancelText }}</button><button ref="confirmButton" class="notice-confirm" :class="{danger:displayConfirmation?.danger}" @click="finishConfirm(true)">{{ displayConfirmation?.confirmText }}</button></footer>
      </div>
    </AppDialog>
  </Teleport>
</template>

<script setup>
import { nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import { CircleCheck, CircleHelp, TriangleAlert, X } from 'lucide-vue-next'
import AppDialog from './AppDialog.vue'
import { subscribeNotice } from '../services/notice'
import { useFullscreenTarget } from '../composables/useFullscreenTarget'

const toasts = ref([])
const target = useFullscreenTarget()
const confirmation = ref(null)
const displayConfirmation = ref(null)
const confirmButton = ref(null)
const dialogTitleId = 'app-notice-title'
const dialogMessageId = 'app-notice-message'
const progressVersion = ref(0)
let nextId = 1
let unsubscribe
const timers = new Map(), progressStyles = new Map()

watch(target, () => { progressVersion.value++; progressStyles.clear() }, {flush:'sync'})

function removeToast(id) {
  clearTimeout(timers.get(id))
  timers.delete(id)
  progressStyles.delete(id)
  toasts.value = toasts.value.filter(item => item.id !== id)
}

function countdownStyle(item) {
  if (!progressStyles.has(item.id)) progressStyles.set(item.id, { '--toast-duration': `${item.duration}ms`, '--toast-elapsed': `${-Math.max(0, performance.now() - item.startedAt)}ms` })
  return progressStyles.get(item.id)
}

function onNotice(event) {
  if (event.kind === 'confirm') {
    if (confirmation.value) confirmation.value.resolve(false)
    confirmation.value = event
    displayConfirmation.value = event
    nextTick(() => confirmButton.value?.focus())
    return
  }
  if (toasts.value.some(item => item.message === event.message && item.type === event.type)) return
  const requested = Number(event.duration)
  const duration = Number.isFinite(requested) && requested > 0 ? requested : 3000
  const item = { ...event, duration, id: nextId++, startedAt: performance.now() }
  toasts.value.push(item)
  timers.set(item.id, window.setTimeout(() => removeToast(item.id), duration))
}

function finishConfirm(result) {
  const current = confirmation.value
  if (!current) return
  confirmation.value = null
  current.resolve(result)
}

function handleKeydown(event) {
  if (event.key === 'Escape' && confirmation.value) finishConfirm(false)
}

onMounted(() => {
  unsubscribe = subscribeNotice(onNotice)
  window.addEventListener('keydown', handleKeydown)
})
onUnmounted(() => {
  unsubscribe?.()
  for (const timer of timers.values()) clearTimeout(timer)
  timers.clear()
  progressStyles.clear()
  window.removeEventListener('keydown', handleKeydown)
  if (confirmation.value) confirmation.value.resolve(false)
})
</script>

<style scoped>
.notice-stack { width: max-content; max-width: min(360px, calc(var(--navy-visual-width,100vw) - 20px)); position: fixed; top: calc(var(--navy-visual-top,0px) + max(10px, env(safe-area-inset-top))); left: calc(var(--navy-visual-left,0px) + var(--navy-visual-width,100vw)/2); z-index: 2147483647; display: flex; flex-direction: column; align-items:center; gap: 6px; transform: translateX(-50%); pointer-events: none; }
.notice-toast { --toast-accent:#b77b68; position:relative; width:fit-content; max-width:100%; min-height:32px; display:grid; grid-template-columns:16px minmax(0,1fr) 22px; align-items:center; gap:7px; padding:4px 6px 4px 8px; border:1px solid #d6e3e7; border-radius:5px; background:#f9fcfd; box-shadow:0 5px 16px #29404a19; color:#48616b; font-size:12px; line-height:18px; pointer-events:auto; overflow:hidden; }
.notice-toast.success { --toast-accent:#bd8d48; }
.notice-symbol { color:var(--toast-accent); align-self:start; margin-top:3px; flex-shrink:0; }
.notice-message { min-width:0; overflow-wrap:anywhere; white-space:pre-wrap; }
.notice-progress { position:absolute; left:0; right:0; bottom:0; height:2px; background:var(--toast-accent); opacity:.7; transform-origin:left center; animation:notice-countdown var(--toast-duration) linear var(--toast-elapsed) both; }
.notice-toast button { width:22px; height:22px; display:grid; place-items:center; border-radius:3px; background:transparent; color:#78909a; opacity:.8; }
.notice-toast button:hover { background: rgba(0, 0, 0, .05); opacity: 1; }
.notice-dialog-overlay { position: fixed; inset: 0; z-index: 2147483646; display: grid; place-items: center; padding: 20px; background: rgba(18, 20, 22, .4); backdrop-filter: blur(2px); }
.notice-dialog { width: min(360px, 100%); padding: 22px; border-radius: 8px; background: #fff; box-shadow: 0 22px 60px rgba(20, 22, 25, .24); text-align: center; }
.notice-dialog-icon { width: 46px; height: 46px; margin: 0 auto 12px; display: grid; place-items: center; border-radius: 50%; background: #edf4ff; color: #4777b8; }
.notice-dialog-icon.danger { background: #fff0ed; color: #d55245; }
.notice-dialog h2 { font-size: 18px; }
.notice-dialog p { margin-top: 8px; color: var(--color-text-muted); font-size: 14px; line-height: 1.6; word-break: break-word; }
.notice-dialog footer { margin-top: 20px; display: grid; grid-template-columns: 1fr 1fr; gap: 9px; }
.notice-dialog footer button { min-height: 42px; border-radius: 7px; font-size: 14px; font-weight: 700; }
.notice-cancel { background: #f2f3f5; color: #555a61; }
.notice-confirm { background: var(--color-primary); color: #fff; }
.notice-confirm.danger { background: #d9544f; }
.notice-toast-enter-active, .notice-toast-leave-active { transition: opacity .18s ease, transform .22s cubic-bezier(.2,.8,.3,1); }
.notice-toast-enter-from, .notice-toast-leave-to { opacity: 0; transform: translateY(-10px) scale(.96); }
@keyframes notice-countdown { from { transform:scaleX(1); } to { transform:scaleX(0); } }
@media(prefers-reduced-motion:reduce){.notice-toast-enter-active,.notice-toast-leave-active{transition:opacity .01s}.notice-toast-enter-from,.notice-toast-leave-to{transform:none}}
.notice-dialog-enter-active, .notice-dialog-leave-active { transition: opacity .18s ease; }
.notice-dialog-enter-active .notice-dialog, .notice-dialog-leave-active .notice-dialog { transition: transform .22s cubic-bezier(.2,.8,.3,1), opacity .18s ease; }
.notice-dialog-enter-from, .notice-dialog-leave-to { opacity: 0; }
.notice-dialog-enter-from .notice-dialog, .notice-dialog-leave-to .notice-dialog { opacity: 0; transform: translateY(10px) scale(.96); }
.notice-content{text-align:center}.notice-content h2{font-size:18px}.notice-content p{margin-top:10px;font-size:13px;line-height:1.8;color:#7b959f;overflow-wrap:anywhere}.notice-content footer{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:10px;margin-top:22px}.notice-content footer button{min-height:42px;border-radius:5px;font-size:13px}.notice-confirm{background:#ee8d32}
</style>
