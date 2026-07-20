<template>
  <div class="share-overlay" @click.self="$emit('close')">
    <section class="share-dialog">
      <h2>{{ mode === 'chat' ? '选择聊天' : (comment ? '分享评论' : '分享动态') }}</h2>
      <div class="share-preview">
        <img v-if="moment.imageUrls?.[0]" :src="imageUrl(moment.imageUrls[0])" alt="" />
        <span><small>{{ comment?.author?.nickname || moment.author?.nickname || '动态用户' }}</small><strong>{{ moment.title || '分享了一条动态' }}</strong><p>{{ comment?.content || moment.content }}</p></span>
      </div>
      <div v-if="mode === 'options'" class="share-methods">
        <button type="button" @click="openChat"><MessageCircle :size="22" /><span><strong>分享到聊天</strong><small>发送{{ comment ? '评论' : '动态' }}卡片给好友或群聊</small></span></button>
        <button type="button" @click="shareExternal"><Share2 :size="22" /><span><strong>分享到其他应用</strong><small>分享可点击进入{{ comment ? '评论' : '动态' }}详情的网页卡片</small></span></button>
      </div>
      <LoadingSpinner v-else-if="loading" />
      <div v-else-if="conversations.length" class="share-list">
        <ConversationTile v-for="conversation in conversations" :key="conversation.id" :conversation="conversation" :show-menu="false" variant="customer" @click="sendToChat(conversation)" />
      </div>
      <p v-else class="share-empty">暂无可用会话</p>
      <button class="share-cancel" type="button" @click="mode === 'chat' ? (mode = 'options') : $emit('close')">{{ mode === 'chat' ? '返回' : '取消' }}</button>
    </section>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { MessageCircle, Share2 } from 'lucide-vue-next'
import { useRouter } from 'vue-router'
import { getConversations, shareMomentToChat } from '../api/messages'
import { imageUrl } from '../composables/useImage'
import { shareMomentExternally } from '../services/shareCard'
import { notifyError, notifySuccess } from '../services/notice'
import ConversationTile from './ConversationTile.vue'
import LoadingSpinner from './LoadingSpinner.vue'

const props = defineProps({
  moment: { type: Object, required: true },
  comment: { type: Object, default: null }
})
const emit = defineEmits(['close'])
const router = useRouter()
const mode = ref('options')
const loading = ref(false)
const conversations = ref([])

async function openChat() {
  if (!localStorage.getItem('token')) {
    emit('close')
    router.push({ path: '/customer/login', query: { redirect: router.currentRoute.value.fullPath } })
    return
  }
  mode.value = 'chat'
  loading.value = true
  try { conversations.value = await getConversations() || [] }
  catch { conversations.value = [] }
  finally { loading.value = false }
}

async function sendToChat(conversation) {
  try {
    await shareMomentToChat({ momentId: props.moment.id, commentId: props.comment?.id || null, conversationId: conversation.id })
    notifySuccess('已分享到聊天')
    emit('close')
  } catch (error) { notifyError(error.message) }
}

function shareExternal() {
  emit('close')
  shareMomentExternally(props.moment, props.comment)
}
</script>

<style scoped>
.share-overlay { position: fixed; inset: 0; z-index: 210; display: grid; place-items: center; padding: 24px; background: rgba(0, 0, 0, .42); }
.share-dialog { width: min(400px, 100%); max-height: 72dvh; display: flex; flex-direction: column; padding: 18px; border-radius: 8px; background: #fff; box-shadow: var(--shadow-lg); }
.share-dialog h2 { margin-bottom: 12px; text-align: center; font-size: 17px; }
.share-preview { min-height: 76px; display: flex; gap: 11px; margin-bottom: 12px; padding: 10px; border: 1px solid #eceef1; border-radius: 8px; background: #f8f9fa; }
.share-preview > img { width: 72px; height: 72px; flex: 0 0 auto; border-radius: 6px; object-fit: cover; }
.share-preview > span { min-width: 0; display: flex; flex: 1; flex-direction: column; }
.share-preview small { color: var(--color-text-muted); font-size: 11px; }
.share-preview strong { margin-top: 2px; overflow: hidden; font-size: 14px; text-overflow: ellipsis; white-space: nowrap; }
.share-preview p { display: -webkit-box; overflow: hidden; margin-top: 3px; color: var(--color-text-muted); font-size: 12px; line-height: 1.45; -webkit-box-orient: vertical; -webkit-line-clamp: 2; }
.share-methods { display: grid; gap: 8px; }
.share-methods button { min-height: 64px; display: flex; align-items: center; gap: 12px; padding: 10px 12px; border: 1px solid #eceef1; border-radius: 8px; background: #fff; color: var(--color-primary); text-align: left; }
.share-methods button:hover { border-color: var(--color-primary-border); background: var(--color-primary-bg); }
.share-methods button > span { display: flex; flex-direction: column; }
.share-methods strong { color: var(--color-text); font-size: 14px; }
.share-methods small { margin-top: 2px; color: var(--color-text-muted); font-size: 11px; }
.share-list { min-height: 0; flex: 1; overflow-y: auto; }
.share-list :deep(.conv-badge) { display: none; }
.share-empty { padding: 24px 0; color: var(--color-text-muted); text-align: center; font-size: 14px; }
.share-cancel { min-height: 44px; margin-top: 10px; border-radius: 7px; background: #f2f3f5; color: #686d74; font-weight: 650; }
@media (max-width: 480px) { .share-overlay { align-items: end; padding: 0; } .share-dialog { width: 100%; max-height: 78dvh; padding-bottom: max(18px, env(safe-area-inset-bottom)); border-radius: 8px 8px 0 0; } }
</style>
