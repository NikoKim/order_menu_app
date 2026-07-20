<template>
  <Teleport to="body">
    <Transition name="notice">
      <div v-if="visible" class="notice-overlay" @click.self="visible = false">
        <div class="notice-box" :class="noticeClass">
          <component :is="noticeIcon" v-if="noticeIcon" class="notice-icon" :size="18" aria-hidden="true" />
          <p class="notice-text">{{ message }}</p>
        </div>
      </div>
    </Transition>
  </Teleport>
</template>

<script setup>
import { computed, ref } from 'vue'
import { CircleCheck, CircleX, Info } from 'lucide-vue-next'

const visible = ref(false)
const message = ref('')
const type = ref('info')

const noticeClass = ref('')
const noticeIcon = computed(() => ({ error: CircleX, success: CircleCheck, info: Info }[type.value]))

function show(msg, opts = {}) {
  message.value = msg
  type.value = opts.type || 'info'
  noticeClass.value = `notice-${type.value}`
  visible.value = true
  setTimeout(() => { visible.value = false }, opts.duration || 2500)
}

defineExpose({ show })
</script>

<script>
export default {
  methods: {
    staticShow(message, opts) {
      const el = document.createElement('div')
      el.id = 'app-notice-global'
      document.body.appendChild(el)
      const { createApp, h } = require('vue')
      const app = createApp({
        render() { return h(AppNotice, { ref: 'notice' }) },
        mounted() {
          this.$refs.notice.show(message, opts)
          setTimeout(() => app.unmount(), 3000)
        }
      })
      app.mount(el)
    }
  }
}
</script>

<style scoped>
.notice-overlay {
  position: fixed; inset: 0; z-index: 9999;
  display: flex; align-items: flex-start; justify-content: center;
  padding-top: 10vh;
  background: transparent;
  pointer-events: none;
}
.notice-box {
  display: flex; align-items: center; gap: 8px;
  padding: 12px 24px; border-radius: var(--radius);
  font-size: 14px; font-weight: 600;
  box-shadow: var(--shadow-lg);
  pointer-events: auto;
  max-width: 90vw;
}
.notice-info { background: #242424; color: #fff; }
.notice-success { background: var(--color-success); color: #fff; }
.notice-error { background: var(--color-error); color: #fff; }
.notice-icon { flex: 0 0 auto; }
.notice-text { margin: 0; }
.notice-enter-active, .notice-leave-active { transition: all 0.3s ease; }
.notice-enter-from, .notice-leave-to { opacity: 0; transform: translateY(-12px); }
</style>
