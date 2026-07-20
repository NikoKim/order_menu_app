<template>
  <div class="page-container essence-page">
    <div class="page-header"><h1 class="page-title">群精华</h1></div>
    <LoadingSpinner v-if="loading" />
    <EmptyState v-else-if="error" icon="warning" :title="error" action-text="重试" @action="load" />
    <EmptyState v-else-if="!items.length" icon="message" title="暂无精华消息" />
    <section v-for="message in items" :key="message.id" class="essence-card">
      <header>
        <span class="avatar">
          <img v-if="message.senderAvatarUrl" :src="imageUrl(message.senderAvatarUrl)" alt="" />
          <UserRound v-else :size="17" />
        </span>
        <strong>{{ message.senderNickname || '用户' }}</strong>
        <span v-if="message.essencePinned" class="pin-tag"><Pin :size="12" />置顶</span>
      </header>
      <div class="content">
        <img v-if="message.type === 'IMAGE_SHARE' && message.imageUrl" class="shared-image" :src="imageUrl(message.imageUrl)" alt="" />
        <div v-else-if="message.type === 'DISH_SHARE'" class="share-block"><Utensils :size="19" /><span><b>{{ message.dishName }}</b><small>{{ message.shopName }}</small></span></div>
        <div v-else-if="message.type === 'ORDER_SHARE'" class="share-block"><ReceiptText :size="19" /><span><b>订单 {{ message.orderNo }}</b><small>{{ message.orderShopName }}</small></span></div>
        <p v-else>{{ message.content }}</p>
      </div>
      <footer class="card-actions">
        <button :class="{ liked: message.essenceLikedByMe }" @click="toggleLike(message)"><Heart :size="18" :fill="message.essenceLikedByMe ? 'currentColor' : 'none'" />{{ message.essenceLikeCount || message.essenceLikes || 0 }}</button>
        <button v-if="role === 'SHOP'" :title="message.essencePinned ? '取消置顶' : '置顶'" @click="togglePin(message)"><Pin :size="18" :fill="message.essencePinned ? 'currentColor' : 'none'" /></button>
        <button v-if="role === 'SHOP'" title="取消精华" class="danger" @click="remove(message)"><Trash2 :size="18" /></button>
      </footer>
    </section>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { Heart, Pin, ReceiptText, Trash2, UserRound, Utensils } from 'lucide-vue-next'
import { getEssences, likeEssence, markEssenceRead, pinEssence, removeEssence } from '../../api/messages'
import { imageUrl } from '../../composables/useImage'
import LoadingSpinner from '../../components/LoadingSpinner.vue'
import EmptyState from '../../components/EmptyState.vue'

const route = useRoute()
const items = ref([])
const loading = ref(true)
const error = ref('')
const role = JSON.parse(localStorage.getItem('user') || '{}')?.role

async function load() {
  loading.value = true
  error.value = ''
  try {
    const data = await getEssences(route.params.id)
    items.value = data.items || data.records || data || []
    if (items.value.length) await markEssenceRead(route.params.id)
  } catch (e) { error.value = e.message }
  finally { loading.value = false }
}
async function togglePin(message) { try { const value = !message.essencePinned; await pinEssence(route.params.id, message.id, value); message.essencePinned = value } catch (e) { alert(e.message) } }
async function remove(message) { if (!confirm('确认取消这条群精华？')) return; try { await removeEssence(route.params.id, message.id); items.value = items.value.filter(item => item.id !== message.id) } catch (e) { alert(e.message) } }

async function toggleLike(message) {
  const liked = !message.essenceLikedByMe
  try {
    await likeEssence(route.params.id, message.id, liked)
    message.essenceLikedByMe = liked
    message.essenceLikeCount = Math.max(0, (message.essenceLikeCount || 0) + (liked ? 1 : -1))
  } catch (e) { alert(e.message) }
}

onMounted(load)
</script>

<style scoped>
.essence-page { max-width: 720px; }
.essence-card { position: relative; margin-bottom: 10px; padding: 14px; background: #fff; border: 1px solid var(--color-line-light); border-radius: var(--radius-lg); }
header { display: flex; align-items: center; gap: 8px; }
.avatar { width: 30px; height: 30px; overflow: hidden; flex: 0 0 auto; display: inline-flex; align-items: center; justify-content: center; border-radius: 50%; background: var(--color-bg); color: var(--color-text-muted); }
.avatar img { width: 100%; height: 100%; object-fit: cover; }
.pin-tag { margin-left: auto; padding: 3px 7px; display: inline-flex; align-items: center; gap: 3px; background: #ffe7cd; border-radius: 4px; color: #a85c10; font-size: 11px; }
.content { margin-top: 10px; font-size: 14px; word-break: break-word; }
.shared-image { max-width: 240px; max-height: 220px; object-fit: cover; border-radius: var(--radius); }
.share-block { display: flex; align-items: center; gap: 10px; padding: 10px; background: var(--color-bg); border-radius: var(--radius); }
.share-block span { display: flex; flex-direction: column; }.share-block small { color: var(--color-text-muted); }
.card-actions { margin-top: 7px; display: flex; justify-content: flex-end; gap: 4px; }.card-actions button { min-width: 38px; height: 36px; padding: 0 8px; display: inline-flex; align-items: center; justify-content: center; gap: 4px; color: var(--color-text-muted); background: transparent; border-radius: var(--radius); }.card-actions button.liked { color: var(--color-like); }.card-actions button.danger { color: var(--color-error); }
</style>
