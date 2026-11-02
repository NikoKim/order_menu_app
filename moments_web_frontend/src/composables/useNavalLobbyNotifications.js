import { onMounted, onUnmounted, ref, watch } from 'vue'
import { getNavalLobbyMessages } from '../api/naval.js'
import { playNavalSound, unlockNavalAudio } from '../games/navalAudio.js'

export function createNavalLobbyUnread() {
  let latest = null, unread = false
  return {
    get unread() { return unread },
    read() { unread = false },
    receive(messages, reading = false) {
      let newest = latest, incoming = false
      for (const message of messages) {
        if (!/^\d+$/.test(String(message.id))) continue
        const id = BigInt(message.id)
        if (newest === null || id > newest) newest = id
        if (latest !== null && id > latest && !message.mine) incoming = true
      }
      // The initial history is a baseline, not a batch of new notifications.
      latest = newest ?? 0n
      if (reading) unread = false
      else if (incoming) unread = true
      return incoming && !reading
    }
  }
}

export function useNavalLobbyNotifications({ available, open }) {
  const unread = ref(false), tracker = createNavalLobbyUnread()
  let timer, polling = false, disposed = false
  function receive(messages) {
    if (disposed || !available()) return
    const notify = tracker.receive(messages, open() && !document.hidden)
    unread.value = tracker.unread
    if (notify) playNavalSound('notification')
  }
  async function refresh() {
    if (disposed || polling || !available() || open() || document.hidden) return
    polling = true
    try { receive(await getNavalLobbyMessages()) } catch { /* Retry on the next poll without interrupting the game. */ }
    finally { polling = false }
  }
  function unlock() { unlockNavalAudio().catch(() => {}) }
  function visible() { if (!document.hidden) { if (open()) { tracker.read(); unread.value = false } else refresh() } }
  watch(open, value => { if (value && !document.hidden) { tracker.read(); unread.value = false } })
  watch(available, value => { if (value) refresh() })
  onMounted(() => {
    refresh(); timer = setInterval(refresh, 2500)
    document.addEventListener('visibilitychange', visible)
    document.addEventListener('pointerdown', unlock, { once: true })
    document.addEventListener('keydown', unlock, { once: true })
  })
  onUnmounted(() => {
    disposed = true; clearInterval(timer)
    document.removeEventListener('visibilitychange', visible)
    document.removeEventListener('pointerdown', unlock)
    document.removeEventListener('keydown', unlock)
  })
  return { unread, receive }
}
