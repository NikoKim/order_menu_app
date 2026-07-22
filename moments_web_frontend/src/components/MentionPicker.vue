<template>
  <Teleport to="body">
    <div v-if="visible" class="mention-overlay" @click.self="$emit('close')">
      <section class="mention-panel" role="dialog" aria-modal="true" aria-label="选择要提及的用户">
        <header>
          <h3>提及用户</h3>
          <button type="button" title="关闭" aria-label="关闭" @click="$emit('close')"><X :size="19" /></button>
        </header>
        <label class="mention-search">
          <Search :size="17" />
          <input ref="searchEl" v-model="query" placeholder="搜索用户昵称" autocomplete="off" @input="search" />
        </label>
        <p class="mention-subtitle">{{ query.trim() ? '搜索结果' : '最近聊天用户' }}</p>
        <div class="mention-results">
          <LoadingSpinner v-if="loading" variant="user-list" text="正在加载用户" />
          <button v-else v-for="user in results" :key="user.id" type="button" class="mention-user" @click="choose(user)">
            <span class="mention-avatar">
              <img v-if="user.avatarUrl" :src="imageUrl(user.avatarUrl)" alt="" />
              <UserRound v-else :size="18" />
            </span>
            <span>{{ user.nickname }}</span>
            <Check v-if="selectedIds.includes(Number(user.id))" :size="17" />
          </button>
          <p v-if="!loading && !results.length" class="mention-empty">{{ query.trim() ? '没有找到用户' : '暂无最近聊天用户，可搜索昵称' }}</p>
        </div>
      </section>
    </div>
  </Teleport>
</template>

<script setup>
import { nextTick, ref, watch } from 'vue'
import { Check, Search, UserRound, X } from 'lucide-vue-next'
import { getConversations } from '../api/messages'
import { searchMomentMentionUsers } from '../api/moments'
import { imageUrl } from '../composables/useImage'
import LoadingSpinner from './LoadingSpinner.vue'

const props = defineProps({
  visible: { type: Boolean, default: false },
  selectedIds: { type: Array, default: () => [] }
})
const emit = defineEmits(['close', 'select'])
const query = ref('')
const recent = ref([])
const results = ref([])
const loading = ref(false)
const searchEl = ref(null)
let timer
let sequence = 0

watch(() => props.visible, async visible => {
  if (!visible) return
  query.value = ''
  await loadRecent()
  await nextTick()
  searchEl.value?.focus()
})

async function loadRecent() {
  loading.value = true
  try {
    const conversations = await getConversations()
    const seen = new Set()
    recent.value = (conversations || []).filter(item => item.directUserId).map(item => ({
      id: Number(item.directUserId),
      nickname: item.directUserNickname || item.name || '聊天用户',
      avatarUrl: item.directUserAvatarUrl || item.coverImageUrl || ''
    })).filter(user => !seen.has(user.id) && seen.add(user.id))
    results.value = recent.value
  } catch {
    recent.value = []
    results.value = []
  } finally { loading.value = false }
}

function search() {
  window.clearTimeout(timer)
  const keyword = query.value.trim()
  const current = ++sequence
  if (!keyword) {
    results.value = recent.value
    loading.value = false
    return
  }
  loading.value = true
  timer = window.setTimeout(async () => {
    try {
      const users = await searchMomentMentionUsers(keyword)
      if (current === sequence) results.value = users || []
    } catch {
      if (current === sequence) results.value = []
    } finally {
      if (current === sequence) loading.value = false
    }
  }, 250)
}

function choose(user) {
  emit('select', { id: Number(user.id), nickname: user.nickname, avatarUrl: user.avatarUrl || '' })
}
</script>

<style scoped>
.mention-overlay { position: fixed; inset: 0; z-index: 140; display: grid; place-items: center; padding: 18px; background: rgba(28, 30, 33, .38); }
.mention-panel { width: min(440px, 100%); max-height: min(600px, 82dvh); display: flex; flex-direction: column; overflow: hidden; border: 1px solid #e4e6e9; border-radius: 8px; background: #fff; box-shadow: 0 18px 46px rgba(20, 22, 25, .2); }
.mention-panel header { min-height: 54px; display: flex; align-items: center; padding: 0 14px 0 18px; border-bottom: 1px solid #eceef0; }
.mention-panel h3 { flex: 1; font-size: 17px; }
.mention-panel header button { width: 36px; height: 36px; display: grid; place-items: center; border-radius: 50%; background: transparent; color: #777c83; }
.mention-search { min-height: 42px; display: flex; align-items: center; gap: 9px; margin: 14px 16px 8px; padding: 0 12px; border: 1px solid #dfe2e6; border-radius: 7px; color: #90959c; background: #f7f8f9; }
.mention-search:focus-within { border-color: #e58a32; background: #fff; box-shadow: 0 0 0 3px rgba(229, 138, 50, .11); }
.mention-search input { min-width: 0; flex: 1; border: 0; outline: 0; background: transparent; color: #2e3135; font-size: 14px; }
.mention-subtitle { padding: 4px 18px 7px; color: #8c9198; font-size: 12px; font-weight: 650; }
.mention-results { min-height: 130px; overflow-y: auto; padding: 0 8px 10px; }
.mention-user { width: 100%; min-height: 50px; display: flex; align-items: center; gap: 11px; padding: 6px 10px; border-radius: 6px; background: transparent; color: #34373b; text-align: left; }
.mention-user:hover { background: #f5f6f7; }
.mention-user > span:nth-child(2) { flex: 1; min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.mention-user > svg { color: #de760f; }
.mention-avatar { width: 36px; height: 36px; flex: 0 0 36px; display: grid; place-items: center; overflow: hidden; border-radius: 50%; background: #f0f1f3; color: #8a8f96; }
.mention-avatar img { width: 100%; height: 100%; object-fit: cover; }
.mention-empty { padding: 30px 16px; color: #9499a0; font-size: 13px; text-align: center; }
</style>
