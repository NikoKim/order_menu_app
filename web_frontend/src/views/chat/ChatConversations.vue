<template>
  <div>
    <!-- Search users -->
    <div class="conv-search">
      <div class="search-input-wrap">
        <Search class="search-icon" :size="18" aria-hidden="true" />
        <input v-model="searchText" class="search-input" placeholder="搜索用户开始聊天..." @input="onSearchInput" />
        <button v-if="searchText" class="search-clear" title="清空搜索" @click="searchText = ''; searchResults = []">
          <X :size="17" aria-hidden="true" />
        </button>
      </div>
      <div v-if="searchResults.length" class="search-dropdown">
        <div v-for="u in searchResults" :key="u.id" class="search-item" @click="startChat(u)">
          <div class="search-avatar">
            <img v-if="u.avatarUrl" :src="imageUrl(u.avatarUrl)" alt="" />
            <UserRound v-else :size="18" aria-hidden="true" />
          </div>
          <span class="search-name">{{ u.nickname }}</span>
          <span v-if="u.role" class="search-role">{{ u.role === 'SHOP' ? '商家' : '顾客' }}</span>
        </div>
      </div>
    </div>

    <!-- Pinned -->
    <div v-if="pinned.length" class="conv-section">
      <div class="conv-section-title">置顶聊天</div>
      <ConversationTile v-for="c in pinned" :key="c.id" :conversation="c" :variant="variant"
        @click="goDetail" @pin="handlePin" @delete="handleDelete" />
    </div>

    <!-- Loading -->
    <LoadingSpinner v-if="loading" text="加载中..." />

    <!-- Error -->
    <div v-else-if="loadError" class="conv-error">
      <TriangleAlert class="conv-error-icon" :size="38" aria-hidden="true" />
      <p>{{ loadError }}</p>
      <button class="btn btn-ghost btn-sm" @click="loadConversations">重试</button>
    </div>

    <!-- All (only show when loaded and no error) -->
    <template v-else>
      <div class="conv-section">
        <div v-if="pinned.length" class="conv-section-title">全部聊天</div>
        <ConversationTile v-for="c in normal" :key="c.id" :conversation="c" :variant="variant"
          @click="goDetail" @pin="handlePin" @delete="handleDelete" />
        <div v-if="!conversations.length && !searchText" class="conv-empty">
          <MessageCircle class="conv-empty-icon" :size="44" :stroke-width="1.5" aria-hidden="true" />
          <p>暂无聊天</p>
          <p class="conv-empty-hint">搜索用户名称，开始聊天</p>
        </div>
      </div>
    </template>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { MessageCircle, Search, TriangleAlert, UserRound, X } from 'lucide-vue-next'
import { getConversations, searchChatUsers, startDirectChatWithUser, startDirectChatWithShop, updateConversationPreferences, hideConversation } from '../../api/messages'
import { imageUrl } from '../../composables/useImage'
import ConversationTile from '../../components/ConversationTile.vue'
import LoadingSpinner from '../../components/LoadingSpinner.vue'

const props = defineProps({
  variant: { type: String, default: 'shop' }
})

const router = useRouter()
const conversations = ref([])
const loading = ref(true)
const loadError = ref('')
const searchText = ref('')
const searchResults = ref([])
let searchTimer = null

const isActive = (c) => c.status !== 'DISSOLVED'
const pinned = computed(() => conversations.value.filter(c => c.pinned && isActive(c) && !c.hidden))
const normal = computed(() => conversations.value.filter(c => !c.pinned && isActive(c) && !c.hidden))

async function loadConversations() {
  loadError.value = ''
  try { conversations.value = await getConversations() } catch (e) {
    loadError.value = e.message || '加载失败'
  }
  finally { loading.value = false }
}

function goDetail(conv) {
  const role = JSON.parse(localStorage.getItem('user') || '{}')?.role
  const prefix = role === 'SHOP' ? '/shop' : '/customer'
  router.push(`${prefix}/chat/${conv.id}`)
}

function onSearchInput() {
  clearTimeout(searchTimer)
  const q = searchText.value.trim()
  if (!q) { searchResults.value = []; return }
  searchTimer = setTimeout(async () => {
    try { searchResults.value = await searchChatUsers(q) } catch { searchResults.value = [] }
  }, 300)
}

async function startChat(user) {
  searchText.value = ''
  searchResults.value = []
  try {
    const role = JSON.parse(localStorage.getItem('user') || '{}')?.role
    let conv
    if (role === 'SHOP') {
      conv = await startDirectChatWithUser(user.id)
    } else {
      if (user.shopId) {
        conv = await startDirectChatWithShop(user.shopId)
      } else {
        conv = await startDirectChatWithUser(user.id)
      }
    }
    await loadConversations()
    goDetail(conv)
  } catch (e) { alert(e.message) }
}

async function handlePin(conv) {
  try {
    await updateConversationPreferences(conv.id, { pinned: !conv.pinned })
    const c = conversations.value.find(x => x.id === conv.id)
    if (c) c.pinned = !c.pinned
  } catch (e) { alert(e.message) }
}

async function handleDelete(conv) {
  if (!confirm('确认删除此会话？')) return
  try {
    await hideConversation(conv.id, { guarded: true, expectedMessageVersion: conv.messageVersion })
    conversations.value = conversations.value.filter(c => c.id !== conv.id)
  } catch (e) { alert(e.message) }
}

let pollTimer = null
onMounted(() => {
  loadConversations()
  pollTimer = setInterval(loadConversations, 5000)
})
onUnmounted(() => clearInterval(pollTimer))
</script>

<style scoped>
.conv-search { position: relative; margin-bottom: 14px; }
.search-input-wrap {
  display: flex; align-items: center; gap: 8px;
  padding: 10px 14px; background: #fff; border-radius: var(--radius-lg);
  border: 1px solid var(--color-line);
}
.search-icon { color: var(--color-text-muted); flex-shrink: 0; }
.search-input {
  flex: 1; border: none; outline: none; font-size: 14px;
  color: var(--color-text); background: transparent;
}
.search-input::placeholder { color: var(--color-text-light); }
.search-clear {
  border: none; background: none; color: var(--color-text-muted);
  width: 30px; height: 30px; display: inline-flex; align-items: center; justify-content: center;
  cursor: pointer; padding: 0; border-radius: 50%;
}
.search-clear:hover { background: var(--color-bg); }

.search-dropdown {
  position: absolute; top: 100%; left: 0; right: 0; z-index: 20;
  background: #fff; border-radius: var(--radius); box-shadow: var(--shadow-lg);
  max-height: 240px; overflow-y: auto; margin-top: 4px;
}
.search-item {
  display: flex; align-items: center; gap: 10px; padding: 10px 14px; cursor: pointer;
}
.search-item:hover { background: var(--color-bg); }
.search-avatar {
  width: 36px; height: 36px; border-radius: 50%; flex-shrink: 0;
  background: var(--color-bg); display: flex; align-items: center; justify-content: center;
  font-size: 18px; overflow: hidden;
}
.search-avatar img { width: 100%; height: 100%; object-fit: cover; }
.search-name { font-size: 14px; font-weight: 600; flex: 1; }
.search-role { font-size: 12px; color: var(--color-text-muted); }

.conv-section { margin-bottom: 8px; }
.conv-section-title {
  font-size: 13px; color: var(--color-text-muted); font-weight: 600;
  padding: 8px 16px 4px;
}

.conv-empty { text-align: center; padding: 40px 20px; color: var(--color-text-muted); }
.conv-empty-icon { display: block; margin: 0 auto 12px; color: var(--color-text-muted); }
.conv-empty p { font-size: 14px; margin: 0; }
.conv-empty-hint { font-size: 13px; color: var(--color-text-light); margin-top: 4px !important; }

.conv-error { text-align: center; padding: 40px 20px; color: var(--color-text-muted); }
.conv-error-icon { display: block; margin: 0 auto 10px; color: var(--color-warning); }
.conv-error p { font-size: 14px; margin: 0 0 12px 0; }

@media (max-width: 480px) {
  .conv-search { margin: 0 12px 10px; }
  .search-input-wrap { min-height: 44px; padding: 6px 10px; border-radius: var(--radius); }
  .search-input { font-size: 16px; min-width: 0; }
  .conv-section-title { padding-inline: 12px; }
}
</style>
