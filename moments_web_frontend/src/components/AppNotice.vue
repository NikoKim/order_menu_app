<template>
  <Teleport to="body">
    <div class="notice-stack" aria-live="polite" aria-atomic="true">
      <TransitionGroup name="notice-toast">
        <div v-for="item in toasts" :key="item.id" class="notice-toast" :class="item.type" role="status">
          <CircleCheck v-if="item.type === 'success'" :size="19" />
          <TriangleAlert v-else :size="19" />
          <span>{{ item.message }}</span>
          <button type="button" title="关闭" aria-label="关闭提示" @click="removeToast(item.id)"><X :size="16" /></button>
        </div>
      </TransitionGroup>
    </div>

    <Transition name="notice-dialog">
      <div v-if="confirmation" class="notice-dialog-overlay" role="presentation" @click.self="finishConfirm(false)">
        <section class="notice-dialog" role="alertdialog" aria-modal="true" :aria-labelledby="dialogTitleId" :aria-describedby="dialogMessageId">
          <div class="notice-dialog-icon" :class="{ danger: confirmation.danger }">
            <TriangleAlert v-if="confirmation.danger" :size="23" />
            <CircleHelp v-else :size="23" />
          </div>
          <h2 :id="dialogTitleId">{{ confirmation.title }}</h2>
          <p :id="dialogMessageId">{{ confirmation.message }}</p>
          <footer>
            <button type="button" class="notice-cancel" @click="finishConfirm(false)">{{ confirmation.cancelText }}</button>
            <button ref="confirmButton" type="button" class="notice-confirm" :class="{ danger: confirmation.danger }" @click="finishConfirm(true)">{{ confirmation.confirmText }}</button>
          </footer>
        </section>
      </div>
    </Transition>
  </Teleport>
</template>

<script setup>
import { nextTick, onMounted, onUnmounted, ref } from 'vue'
import { CircleCheck, CircleHelp, TriangleAlert, X } from 'lucide-vue-next'
import { subscribeNotice } from '../services/notice'

const toasts = ref([])
const confirmation = ref(null)
const confirmButton = ref(null)
const dialogTitleId = 'app-notice-title'
const dialogMessageId = 'app-notice-message'
let nextId = 1
let unsubscribe

function removeToast(id) {
  toasts.value = toasts.value.filter(item => item.id !== id)
}

function onNotice(event) {
  if (event.kind === 'confirm') {
    if (confirmation.value) confirmation.value.resolve(false)
    confirmation.value = event
    nextTick(() => confirmButton.value?.focus())
    return
  }
  const item = { ...event, id: nextId++ }
  toasts.value.push(item)
  window.setTimeout(() => removeToast(item.id), item.duration)
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
  window.removeEventListener('keydown', handleKeydown)
  if (confirmation.value) confirmation.value.resolve(false)
})
</script>

<style scoped>
.notice-stack { width: min(420px, calc(100vw - 24px)); position: fixed; top: max(18px, env(safe-area-inset-top)); left: 50%; z-index: 1200; display: flex; flex-direction: column; gap: 8px; transform: translateX(-50%); pointer-events: none; }
.notice-toast { min-height: 48px; display: grid; grid-template-columns: 22px minmax(0, 1fr) 30px; align-items: center; gap: 9px; padding: 8px 8px 8px 13px; border: 1px solid #f1c5bf; border-radius: 8px; background: #fff; box-shadow: 0 10px 28px rgba(30, 32, 35, .16); color: #b54135; font-size: 14px; line-height: 1.45; pointer-events: auto; }
.notice-toast.success { border-color: #b9ddc2; color: #28743b; }
.notice-toast button { width: 30px; height: 30px; display: grid; place-items: center; border-radius: 6px; background: transparent; color: currentColor; opacity: .7; }
.notice-toast button:hover { background: rgba(0, 0, 0, .05); opacity: 1; }
.notice-dialog-overlay { position: fixed; inset: 0; z-index: 1190; display: grid; place-items: center; padding: 20px; background: rgba(18, 20, 22, .4); backdrop-filter: blur(2px); }
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
.notice-dialog-enter-active, .notice-dialog-leave-active { transition: opacity .18s ease; }
.notice-dialog-enter-active .notice-dialog, .notice-dialog-leave-active .notice-dialog { transition: transform .22s cubic-bezier(.2,.8,.3,1), opacity .18s ease; }
.notice-dialog-enter-from, .notice-dialog-leave-to { opacity: 0; }
.notice-dialog-enter-from .notice-dialog, .notice-dialog-leave-to .notice-dialog { opacity: 0; transform: translateY(10px) scale(.96); }
</style>
