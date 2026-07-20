<template>
  <div class="chat-detail">
    <!-- Header -->
    <div class="chat-header">
      <button class="chat-back" title="返回" @click="$router.back()"><ArrowLeft :size="22" /></button>
      <div class="chat-header-info" @click="conversationInfoRoute && $router.push(conversationInfoRoute)">
        <div class="chat-header-avatar">
          <img v-if="convAvatar" :src="convAvatar" alt="" />
          <span v-else>{{ isGroup ? '群' : '户' }}</span>
        </div>
        <div>
          <div class="chat-header-name">{{ convName }}</div>
          <div v-if="isGroup" class="chat-header-sub">家庭群聊</div>
        </div>
      </div>
      <button class="chat-settings-btn" title="聊天设置" @click="showSettings = !showSettings"><SlidersHorizontal :size="20" /></button>
    </div>

    <!-- Settings panel -->
    <div v-if="showSettings" class="chat-settings-panel">
      <template v-if="isGroup">
        <router-link :to="`${basePath}/chat/${conversationId}/essences`" class="settings-link"><Sparkles :size="18" />群精华</router-link>
        <router-link :to="`${basePath}/chat/${conversationId}/group-info`" class="settings-link"><UsersRound :size="18" />群聊信息</router-link>
        <router-link v-if="role === 'SHOP'" :to="`${basePath}/chat/${conversationId}/group-manage`" class="settings-link"><Settings2 :size="18" />群聊管理</router-link>
      </template>
      <label class="settings-row">
        <span>免打扰</span>
        <input type="checkbox" :checked="isMuted" @change="toggleMute" />
      </label>
      <button class="settings-block-btn" @click="handleBlockToggle">
        {{ blocked ? '取消拉黑' : '拉黑用户' }}
      </button>
    </div>
    <button v-if="latestEssence" class="essence-banner" @click="openEssences">
      <Sparkles :size="18" /><span><strong>有新的群精华</strong><small>{{ latestEssence.content || '点击查看精华消息' }}</small></span><ChevronRight :size="18" />
    </button>

    <!-- Messages area -->
    <div class="chat-messages" ref="msgContainer">
      <LoadingSpinner v-if="loading" />
      <div v-else-if="error" class="error-wrap">
        <EmptyState icon="warning" :title="error" action-text="重试" @action="loadMessages" />
      </div>
      <template v-else>
        <div v-for="msg in messages" :key="msg.id" class="chat-msg-wrapper" :class="{ mine: msg.senderUserId === myUserId, system: msg.type === 'SYSTEM' }">
          <!-- System / recalled -->
          <div v-if="msg.recalledAt" class="msg-recalled">
            {{ msg.senderUserId === myUserId ? '你' : msg.senderNickname }}撤回了一条消息
          </div>

          <template v-else>
            <!-- Avatar (other people) -->
            <div v-if="msg.senderUserId !== myUserId" class="msg-avatar">
              <img v-if="msg.senderAvatarUrl" :src="imageUrl(msg.senderAvatarUrl)" alt="" />
              <UserRound v-else :size="18" />
            </div>

            <div class="msg-body" :class="{ mine: msg.senderUserId === myUserId }">
              <span v-if="msg.isEssence || msg.essence" class="essence-tag"><Star :size="12" fill="currentColor" />群精华</span>
              <!-- Quoted -->
              <div v-if="msg.quotedMessage" class="msg-quote" @click="scrollToMessage(msg.quotedMessageId)">
                <span class="quote-sender">{{ msg.quotedMessage.senderNickname || msg.quotedSenderName }}</span>
                <span class="quote-text">{{ msg.quotedContent }}</span>
              </div>

              <!-- Text -->
              <div v-if="msg.type === 'TEXT' || !msg.type" class="msg-bubble" :class="{ mine: msg.senderUserId === myUserId }">
                {{ msg.content }}
              </div>

              <!-- Image -->
              <div v-else-if="msg.type === 'IMAGE_SHARE'" class="msg-image" @click="viewImage(msg)">
                <img v-if="msg.imageUrl && !msg.imageExpired" :src="msg.imageBlobUrl || '#'" alt="" />
                <span v-else class="msg-image-expired">[图片已过期]</span>
              </div>

              <!-- Share cards -->
              <div v-else-if="msg.type === 'DISH_SHARE'" class="msg-share-card" @click="openShare(msg)">
                <img v-if="msg.dishImageUrl" :src="imageUrl(msg.dishImageUrl)" alt="" class="share-card-img" />
                <div class="share-card-info">
                  <span class="share-card-title">{{ msg.dishName }}</span>
                  <span class="share-card-sub">{{ msg.shopName }}</span>
                  <span v-if="msg.dishPrice" class="share-card-price">{{ msg.dishPrice }}</span>
                </div>
              </div>

              <div v-else-if="msg.type === 'ORDER_SHARE'" class="msg-share-card" @click="openShare(msg)">
                <div class="share-card-info">
                  <span class="share-card-title">订单 {{ msg.orderNo }}</span>
                  <span class="share-card-sub">{{ msg.orderShopName }}</span>
                  <span v-if="msg.orderTotalAmount" class="share-card-price">{{ msg.orderTotalAmount }}</span>
                </div>
              </div>

              <div v-else-if="msg.type === 'SHOP_SHARE'" class="msg-share-card" @click="openShare(msg)">
                <img v-if="msg.sharedShopLogoUrl" :src="imageUrl(msg.sharedShopLogoUrl)" alt="" class="share-card-img" />
                <div class="share-card-info">
                  <span class="share-card-title">{{ msg.sharedShopName }}</span>
                  <span class="share-card-sub">{{ msg.sharedShopDescription }}</span>
                </div>
              </div>

              <div v-else-if="msg.type === 'MOMENT_SHARE' || msg.type === 'MOMENT_COMMENT_SHARE'" class="msg-share-card" @click="openShare(msg)">
                <div class="share-card-info">
                  <span class="share-card-title">{{ msg.sharedMomentTitle || '动态分享' }}</span>
                  <span class="share-card-sub">{{ msg.sharedMomentContent }}</span>
                </div>
              </div>

              <!-- Menu button -->
              <button class="msg-menu-btn" title="消息操作" @click.stop="msgMenuId = (msgMenuId === msg.id ? null : msg.id)">
                <MoreVertical :size="16" />
              </button>
              <div v-if="msgMenuId === msg.id" class="msg-menu">
                <button @click="copyText(msg); msgMenuId = null">复制</button>
                <button @click="startReply(msg); msgMenuId = null">回复</button>
                <button v-if="role === 'SHOP' && isGroup" @click="toggleEssence(msg)">{{ msg.isEssence || msg.essence ? '取消精华' : '设为精华' }}</button>
                <button v-if="msg.senderUserId === myUserId" @click="handleRecall(msg)">撤回</button>
                <button @click="handleDeleteMsg(msg)">删除</button>
              </div>
            </div>
          </template>
        </div>
        <div ref="msgEnd"></div>
      </template>
    </div>

    <!-- Input bar -->
    <div class="chat-input-bar">
      <div v-if="replyingTo" class="reply-indicator">
        <span>回复 {{ replyingTo.senderNickname }}: {{ replyingTo.content?.slice(0, 30) }}</span>
        <button class="btn-close" title="取消回复" @click="replyingTo = null"><X :size="16" /></button>
      </div>
      <div class="chat-input-row">
        <label class="chat-img-btn">
          <ImagePlus :size="23" aria-label="发送图片" />
          <input type="file" accept="image/*" hidden @change="handleImageSelect" />
        </label>
        <input v-model="textInput" class="chat-text-input" placeholder="输入消息..." @keyup.enter="handleSend" />
        <button class="chat-send-btn" :disabled="!textInput.trim()" @click="handleSend">发送</button>
      </div>
    </div>

    <div v-if="msgMenuId" class="menu-backdrop" @click="msgMenuId = null"></div>
    <ImageViewer :visible="imageViewer.visible" :src="imageViewer.src" @close="imageViewer.visible = false" />
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted, nextTick } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowLeft, ChevronRight, ImagePlus, MoreVertical, Settings2, SlidersHorizontal, Sparkles, Star, UserRound, UsersRound, X } from 'lucide-vue-next'
import { addEssence, blockUser, deleteMessage, getBlockStatus, getChatImageUrl, getConversationPreferences, getLatestUnreadEssence, getMessages, markEssenceRead, recallMessage, removeEssence, sendMessage as apiSendMessage, setConversationMute, unblockUser } from '../../api/messages'
import { imageUrl } from '../../composables/useImage'
import LoadingSpinner from '../../components/LoadingSpinner.vue'
import EmptyState from '../../components/EmptyState.vue'
import ImageViewer from '../../components/ImageViewer.vue'

const route = useRoute()
const router = useRouter()
const conversationId = computed(() => Number(route.params.id))
const myUserId = ref(0)
const role = JSON.parse(localStorage.getItem('user') || '{}')?.role
const basePath = role === 'SHOP' ? '/shop' : '/customer'

const conversation = ref({})
const messages = ref([])
const loading = ref(true)
const error = ref('')
const textInput = ref('')
const replyingTo = ref(null)
const msgMenuId = ref(null)
const isMuted = ref(false)
const blocked = ref(false)
const showSettings = ref(false)
const latestEssence = ref(null)

const msgContainer = ref(null)
const msgEnd = ref(null)

const isGroup = computed(() => conversation.value?.type === 'FAMILY_GROUP')
const convName = computed(() => isGroup.value ? (conversation.value?.name || '家庭群聊') : (conversation.value?.directUserNickname || ''))
const convAvatar = computed(() => {
  const url = isGroup.value ? conversation.value?.coverImageUrl : conversation.value?.directUserAvatarUrl
  return url ? imageUrl(url) : null
})
const profileRoute = computed(() => {
  const userId = conversation.value?.directUserId
  return userId ? `${basePath}/chat/user/${userId}?conversationId=${conversationId.value}` : ''
})
const conversationInfoRoute = computed(() => isGroup.value ? `${basePath}/chat/${conversationId.value}/group-info` : profileRoute.value)

async function loadMessages() {
  loading.value = true; error.value = ''
  try {
    const data = await getMessages(conversationId.value)
    conversation.value = data.conversation || {}
    messages.value = data.messages || []
    messages.value.forEach(m => {
      if (m.type === 'IMAGE_SHARE' && m.imageUrl && !m.imageExpired) loadChatImage(m)
    })
    scrollToBottom()
  } catch (e) { error.value = e.message }
  finally { loading.value = false }
}

async function loadChatImage(msg) {
  try {
    const filename = msg.imageUrl.split('/').pop()
    msg.imageBlobUrl = await getChatImageUrl(conversationId.value, filename)
  } catch { }
}

function scrollToBottom() {
  nextTick(() => msgEnd.value?.scrollIntoView({ behavior: 'smooth' }))
}

function scrollToMessage(msgId) {
  const el = document.getElementById('msg-' + msgId)
  if (el) el.scrollIntoView({ behavior: 'smooth', block: 'center' })
}

async function handleSend() {
  if (!textInput.value.trim()) return
  const content = textInput.value.trim()
  textInput.value = ''
  try {
    await apiSendMessage(conversationId.value, { content, quotedMessageId: replyingTo.value?.id || null })
    replyingTo.value = null
    loadMessages()
  } catch (e) { alert(e.message) }
}

async function handleImageSelect(e) {
  const file = e.target.files?.[0]
  if (!file) return
  try {
    const form = new FormData()
    form.append('image', file)
    const token = localStorage.getItem('token')
    const res = await fetch(`/api/messages/conversations/${conversationId.value}/images`, {
      method: 'POST',
      headers: { Authorization: `Bearer ${token}` },
      body: form
    })
    const json = await res.json()
    if (json.code !== 0) throw new Error(json.message)
    loadMessages()
  } catch (e) { alert(e.message) }
  e.target.value = ''
}

async function handleRecall(msg) {
  if (!confirm('确认撤回此消息？')) return
  try { await recallMessage(conversationId.value, msg.id); loadMessages() } catch (e) { alert(e.message) }
  msgMenuId.value = null
}

async function handleDeleteMsg(msg) {
  try { await deleteMessage(conversationId.value, msg.id); messages.value = messages.value.filter(m => m.id !== msg.id) } catch (e) { alert(e.message) }
  msgMenuId.value = null
}

async function toggleMute() {
  isMuted.value = !isMuted.value
  try { await setConversationMute(conversationId.value, isMuted.value) } catch { isMuted.value = !isMuted.value }
}

async function handleBlockToggle() {
  const targetUserId = conversation.value?.directUserId
  if (!targetUserId) return
  try {
    if (blocked.value) { await unblockUser(targetUserId); blocked.value = false }
    else { await blockUser(targetUserId); blocked.value = true }
  } catch (e) { alert(e.message) }
}

let pollTimer = null
onMounted(async () => {
  try { myUserId.value = JSON.parse(localStorage.getItem('user') || '{}')?.id || 0 } catch { }
  await loadMessages()
  if (isGroup.value) { try { latestEssence.value = await getLatestUnreadEssence(conversationId.value) } catch { } }
  pollTimer = setInterval(loadMessages, 3000)
  try { const prefs = await getConversationPreferences(conversationId.value); isMuted.value = prefs.muted || false } catch { }
  try {
    const conv = conversation.value
    if (conv?.directUserId) { const b = await getBlockStatus(conv.directUserId); blocked.value = b.blockedByMe || false }
  } catch { }
})
onUnmounted(() => clearInterval(pollTimer))

const imageViewer = ref({ visible: false, src: '' })
function viewImage(msg) {
  const url = msg.imageBlobUrl || (msg.imageUrl ? imageUrl(msg.imageUrl) : '')
  if (url) { imageViewer.value = { visible: true, src: url } }
}
function copyText(msg) {
  navigator.clipboard?.writeText(msg.content || '').catch(() => {})
}
function startReply(msg) {
  replyingTo.value = msg
}
async function toggleEssence(msg) {
  const active = Boolean(msg.isEssence || msg.essence)
  try {
    if (active) await removeEssence(conversationId.value, msg.id)
    else await addEssence(conversationId.value, msg.id)
    msg.isEssence = !active
  } catch (e) { alert(e.message) }
  msgMenuId.value = null
}
async function openEssences() {
  try { await markEssenceRead(conversationId.value, { throughMessageId: latestEssence.value?.id }) } catch { }
  latestEssence.value = null
  router.push(`${basePath}/chat/${conversationId.value}/essences`)
}
function openShare(msg) {
  if (msg.type === 'DISH_SHARE' && msg.dishId && msg.shopId) router.push(`/customer/shops/${msg.shopId}/dishes/${msg.dishId}`)
  else if (msg.type === 'SHOP_SHARE' && (msg.sharedShopId || msg.shopId)) router.push(`/customer/shop/${msg.sharedShopId || msg.shopId}/menu`)
  else if (msg.type === 'ORDER_SHARE' && msg.orderId) router.push(`${basePath}/orders/${msg.orderId}`)
  else if ((msg.type === 'MOMENT_SHARE' || msg.type === 'MOMENT_COMMENT_SHARE') && msg.sharedMomentId) router.push(`${basePath}/moments/${msg.sharedMomentId}`)
}
</script>

<style scoped>
.chat-detail { display: flex; flex-direction: column; height: calc(100dvh - 56px); background: var(--color-bg); }

.chat-header {
  display: flex; align-items: center; gap: 10px; padding: 10px 16px;
  background: #fff; border-bottom: 1px solid var(--color-line); flex-shrink: 0;
}
.chat-back {
  border: none; background: none; color: var(--color-text);
  cursor: pointer; width: 40px; height: 40px; display: inline-flex; align-items: center; justify-content: center; border-radius: var(--radius);
}
.chat-back:hover { background: var(--color-bg); }
.chat-header-info { display: flex; align-items: center; gap: 10px; flex: 1; cursor: pointer; min-width: 0; }
.chat-header-avatar {
  width: 36px; height: 36px; border-radius: 50%; flex-shrink: 0;
  background: var(--color-bg); display: flex; align-items: center; justify-content: center;
  font-size: 16px; font-weight: 700; color: var(--color-text-muted); overflow: hidden;
}
.chat-header-avatar img { width: 100%; height: 100%; object-fit: cover; }
.chat-header-name { font-size: 16px; font-weight: 700; }
.chat-header-sub { font-size: 12px; color: var(--color-text-muted); }
.chat-settings-btn {
  border: none; background: none; cursor: pointer; width: 40px; height: 40px;
  display: inline-flex; align-items: center; justify-content: center; border-radius: var(--radius); color: var(--color-text-muted);
}
.chat-settings-btn:hover { background: var(--color-bg); }

.chat-settings-panel {
  padding: 12px 16px; background: #fff; border-bottom: 1px solid var(--color-line);
  display: flex; align-items: center; gap: 16px; justify-content: space-between;
}
.settings-row { display: flex; align-items: center; gap: 8px; font-size: 14px; color: var(--color-text); cursor: pointer; }
.settings-link { min-height: 38px; padding: 6px 10px; display: inline-flex; align-items: center; gap: 6px; color: var(--color-text); background: var(--color-bg); border-radius: var(--radius); font-size: 13px; }
.settings-block-btn {
  border: 1px solid var(--color-error); background: none; color: var(--color-error);
  padding: 6px 14px; border-radius: var(--radius); font-size: 13px; cursor: pointer;
}
.essence-banner { width: 100%; min-height: 58px; padding: 9px 16px; display: flex; align-items: center; gap: 10px; color: var(--color-text); background: var(--color-primary-bg); border-bottom: 1px solid var(--color-primary-border); text-align: left; }.essence-banner > svg { flex: 0 0 auto; color: var(--color-primary); }.essence-banner span { flex: 1; min-width: 0; display: flex; flex-direction: column; }.essence-banner small { overflow: hidden; color: var(--color-text-muted); text-overflow: ellipsis; white-space: nowrap; }

/* Messages */
.chat-messages {
  flex: 1; overflow-y: auto; padding: 12px 16px;
  display: flex; flex-direction: column; gap: 12px;
}
.error-wrap { min-height: 20vh; display: flex; align-items: center; justify-content: center; }

.chat-msg-wrapper { display: flex; align-items: flex-end; gap: 8px; }
.chat-msg-wrapper.mine { flex-direction: row-reverse; }
.chat-msg-wrapper.system { justify-content: center; }

.msg-avatar {
  width: 36px; height: 36px; border-radius: 50%; flex-shrink: 0;
  background: var(--color-bg); display: flex; align-items: center; justify-content: center;
  font-size: 16px; overflow: hidden;
}
.msg-avatar img { width: 100%; height: 100%; object-fit: cover; }

.msg-body { max-width: 280px; position: relative; display: flex; flex-direction: column; gap: 4px; }
.msg-body.mine { align-items: flex-end; }
.essence-tag { width: fit-content; padding: 2px 6px; display: inline-flex; align-items: center; gap: 3px; color: #a85c10; background: #ffe7cd; border-radius: 4px; font-size: 10px; font-weight: 700; }

.msg-bubble {
  padding: 10px 14px; border-radius: 8px; font-size: 14px; line-height: 1.5;
  background: var(--color-other-bubble); color: var(--color-text);
  word-break: break-word;
}
.msg-bubble.mine { background: var(--color-primary); color: #fff; }

.msg-recalled { font-size: 12px; color: var(--color-text-light); padding: 4px 16px; }

.msg-quote {
  padding: 8px 10px; border-radius: 6px; background: rgba(0,0,0,0.04);
  font-size: 12px; cursor: pointer; border-left: 3px solid var(--color-primary);
  max-width: 260px;
}
.quote-sender { font-weight: 600; display: block; color: var(--color-text); }
.quote-text { color: var(--color-text-muted); display: block; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }

.msg-image { max-width: 240px; border-radius: var(--radius); overflow: hidden; cursor: pointer; }
.msg-image img { width: 100%; display: block; }
.msg-image-expired { font-size: 12px; color: var(--color-text-light); padding: 20px; background: var(--color-bg); display: block; text-align: center; }

.msg-share-card {
  display: flex; gap: 10px; padding: 10px; background: #fff; border-radius: var(--radius);
  max-width: 260px; border: 1px solid var(--color-line); cursor: pointer;
}
.share-card-img { width: 48px; height: 48px; border-radius: 8px; object-fit: cover; flex-shrink: 0; }
.share-card-info { min-width: 0; }
.share-card-title { font-size: 14px; font-weight: 700; display: block; }
.share-card-sub { font-size: 12px; color: var(--color-text-muted); display: block; margin-top: 2px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.share-card-price { font-size: 15px; font-weight: 800; color: var(--color-primary); margin-top: 4px; display: block; }

.msg-menu-btn {
  border: none; background: none; font-size: 16px; color: var(--color-text-muted);
  cursor: pointer; padding: 2px; opacity: 0; position: absolute; top: 2px; right: -20px;
}
.chat-msg-wrapper:hover .msg-menu-btn { opacity: 1; }

.msg-menu {
  position: absolute; bottom: 100%; right: -20px; z-index: 20;
  background: #fff; border-radius: var(--radius); box-shadow: var(--shadow-lg);
  min-width: 100px; padding: 4px;
}
.msg-menu button {
  display: block; width: 100%; padding: 8px 14px; border: none; background: none;
  font-size: 13px; cursor: pointer; text-align: left; border-radius: 4px; color: var(--color-text);
}
.msg-menu button:hover { background: var(--color-bg); }

/* Input bar */
.chat-input-bar { flex-shrink: 0; background: #fff; border-top: 1px solid var(--color-line); padding: 8px 12px; }
.reply-indicator {
  display: flex; justify-content: space-between; align-items: center; font-size: 12px;
  color: var(--color-text-muted); padding: 6px 10px; background: var(--color-bg);
  border-radius: var(--radius); margin-bottom: 8px;
}
.btn-close {
  border: none; background: none; color: var(--color-text-muted);
  cursor: pointer; width: 30px; height: 30px; display: inline-flex; align-items: center; justify-content: center; border-radius: 50%;
}
.chat-input-row { display: flex; gap: 8px; align-items: center; }
.chat-img-btn {
  width: 40px; height: 40px; cursor: pointer; display: flex; align-items: center; justify-content: center;
  color: var(--color-text-muted); padding: 0; border-radius: var(--radius); flex-shrink: 0;
}
.chat-img-btn:hover { color: var(--color-primary); background: var(--color-bg); }
.chat-text-input {
  flex: 1; border: none; outline: none; font-size: 15px; padding: 8px 12px;
  background: var(--color-bg); border-radius: 20px; color: var(--color-text);
}
.chat-text-input::placeholder { color: var(--color-text-light); }
.chat-send-btn {
  border: none; padding: 8px 18px; border-radius: 20px;
  background: var(--color-primary); color: #fff; font-size: 14px; font-weight: 600;
  cursor: pointer; transition: opacity 0.2s; flex-shrink: 0;
}
.chat-send-btn:disabled { opacity: 0.4; cursor: not-allowed; }
.chat-send-btn:not(:disabled):hover { background: var(--color-primary-light); }

.menu-backdrop { position: fixed; inset: 0; z-index: 19; }

@media (max-width: 768px) {
  .chat-detail { height: 100dvh; }
  .chat-header { min-height: calc(56px + env(safe-area-inset-top)); padding: env(safe-area-inset-top) 8px 0; }
  .chat-messages { padding: 12px 10px; overscroll-behavior: contain; }
  .chat-input-bar { padding: 8px 8px max(8px, env(safe-area-inset-bottom)); }
  .chat-text-input { min-width: 0; min-height: 40px; font-size: 16px; }
  .chat-send-btn { min-height: 40px; padding-inline: 14px; }
  .msg-body { max-width: min(76vw, 320px); }
  .msg-menu-btn { opacity: 0.7; right: -22px; width: 24px; height: 28px; display: inline-flex; align-items: center; justify-content: center; }
  .chat-msg-wrapper.mine .msg-menu-btn { right: auto; left: -22px; }
}
</style>
