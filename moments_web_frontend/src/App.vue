<template>
  <router-view />
  <AppNotice />
</template>

<script setup>
import { onMounted, onUnmounted, watch } from 'vue'
import { useRoute } from 'vue-router'
import AppNotice from './components/AppNotice.vue'
import { sendPresenceHeartbeat } from './api/messages'

const route = useRoute()
let heartbeatTimer = null

async function heartbeat() {
  if (!localStorage.getItem('token')) return
  try { await sendPresenceHeartbeat() } catch { }
}

watch(() => route.fullPath, heartbeat)

onMounted(() => {
  heartbeat()
  heartbeatTimer = window.setInterval(heartbeat, 30_000)
})

onUnmounted(() => window.clearInterval(heartbeatTimer))
</script>
