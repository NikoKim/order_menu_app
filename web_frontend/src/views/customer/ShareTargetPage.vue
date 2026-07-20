<template>
  <div class="page-container share-page">
    <div class="page-header"><h1 class="page-title">{{ title }}</h1></div>
    <LoadingSpinner v-if="loading" />
    <EmptyState v-else-if="error" icon="warning" :title="error" action-text="重试" @action="load" />
    <EmptyState v-else-if="!conversations.length" icon="message" title="暂无可分享的会话" />
    <section v-else class="target-list">
      <button v-for="item in conversations" :key="item.id || `user:${item.userId}`" class="target-row" :disabled="sendingId" @click="share(item)">
        <span class="avatar"><img v-if="item.imageUrl" :src="imageUrl(item.imageUrl)" alt="" /><UserRound v-else-if="item.type === 'DIRECT' || item.isDirect" :size="20" /><UsersRound v-else :size="20" /></span>
        <span class="copy"><strong>{{ item.title || item.name || item.directUserNickname }}</strong><small>{{ item.type === 'DIRECT' || item.isDirect ? '私聊' : '家庭群聊' }}</small></span>
        <span v-if="sendingId === (item.id || item.userId)" class="mini-loader"></span><Send v-else :size="19" />
      </button>
    </section>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Send, UserRound, UsersRound } from 'lucide-vue-next'
import { getConversations, getFamilyGroupByShop, shareDishToChat, shareOrderToChat, shareShopToChat, startDirectChatWithUser } from '../../api/messages'
import { getShopDetail } from '../../api/customer'
import { imageUrl } from '../../composables/useImage'
import LoadingSpinner from '../../components/LoadingSpinner.vue'
import EmptyState from '../../components/EmptyState.vue'

const route = useRoute(), router = useRouter()
const conversations = ref([]), loading = ref(true), error = ref(''), sendingId = ref(null)
const kind = computed(() => route.meta.shareKind)
const title = computed(() => ({ dish: '分享菜品', order: '分享订单', shop: '分享店铺' })[kind.value])

async function load() {
  loading.value = true; error.value = ''
  try {
    const data = await getConversations()
    const active = (data.items || data.records || data || []).filter(item => item.isActive !== false)
    if (kind.value !== 'shop') conversations.value = active
    else {
      const shop = await getShopDetail(route.params.id)
      if (shop.visibilityType !== 'FAMILY') conversations.value = active.filter(isDirect)
      else {
        const group = await getFamilyGroupByShop(route.params.id)
        const directByUser = new Map(active.filter(isDirect).map(item => [item.directUserId, item]))
        const groupConversation = active.find(item => !isDirect(item) && item.id === group.id)
        conversations.value = [
          ...(groupConversation ? [groupConversation] : []),
          ...(group.members || []).filter(member => member.memberStatus === 'ACTIVE' && member.userId !== currentUserId).map(member => directByUser.get(member.userId) || { userId: member.userId, title: member.nickname, imageUrl: member.avatarUrl, isDirect: true })
        ]
      }
    }
  }
  catch (e) { error.value = e.message } finally { loading.value = false }
}
async function share(item) {
  sendingId.value = item.id || item.userId
  try {
    const id = Number(route.params.id)
    const conversation = item.id ? item : await startDirectChatWithUser(item.userId)
    if (kind.value === 'dish') await shareDishToChat({ conversationId: conversation.id, dishId: id })
    if (kind.value === 'order') await shareOrderToChat({ conversationId: conversation.id, orderId: id })
    if (kind.value === 'shop') await shareShopToChat({ conversationId: conversation.id, shopId: id })
    alert('分享成功')
    router.back()
  } catch (e) { alert(e.message) } finally { sendingId.value = null }
}
onMounted(load)
const currentUserId = JSON.parse(localStorage.getItem('user') || '{}')?.id
function isDirect(item) { return item.type === 'DIRECT' || item.isDirect }
</script>

<style scoped>
.share-page { max-width: 720px; }.target-list { overflow: hidden; background: #fff; border: 1px solid var(--color-line-light); border-radius: var(--radius-lg); }.target-row { width: 100%; min-height: 70px; padding: 10px 16px; display: flex; align-items: center; gap: 12px; color: var(--color-text); background: #fff; text-align: left; }.target-row + .target-row { border-top: 1px solid var(--color-line); }.avatar { width: 44px; height: 44px; overflow: hidden; flex: 0 0 auto; display: inline-flex; align-items: center; justify-content: center; border-radius: 50%; color: var(--color-primary); background: var(--color-primary-bg); }.avatar img { width: 100%; height: 100%; object-fit: cover; }.copy { flex: 1; min-width: 0; display: flex; flex-direction: column; }.copy small { color: var(--color-text-muted); }.target-row > svg { color: var(--color-primary); }.mini-loader { width: 19px; height: 19px; border: 2px solid var(--color-primary-bg); border-top-color: var(--color-primary); border-radius: 50%; animation: spin .7s linear infinite; }@keyframes spin { to { transform: rotate(360deg); } }
</style>
