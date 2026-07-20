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
      <button class="chat-settings-btn" :title="isGroup ? '群聊信息' : '用户资料'" @click="conversationInfoRoute && $router.push(conversationInfoRoute)">
        <UsersRound v-if="isGroup" :size="20" /><UserRound v-else :size="20" />
      </button>
    </div>
    <button v-if="latestEssence" class="essence-banner" @click="openEssences">
      <Sparkles :size="18" /><span><strong>有新的群精华</strong><small>{{ latestEssence.content || '点击查看精华消息' }}</small></span><ChevronRight :size="18" />
    </button>

    <!-- Messages area -->
    <div class="chat-messages" ref="msgContainer">
      <LoadingSpinner v-if="loading" variant="chat-detail" />
      <div v-else-if="error" class="error-wrap">
        <EmptyState icon="warning" :title="error" action-text="重试" @action="loadMessages" />
      </div>
      <template v-else>
        <div v-for="(msg, index) in messages" :key="msg.id" class="chat-message-block">
          <div v-if="shouldShowTimestamp(index)" class="msg-center-notice msg-timestamp">{{ formatMessageTime(msg.createdAt) }}</div>
        <div :id="'msg-' + msg.id" class="chat-msg-wrapper" :class="{ mine: msg.senderUserId === myUserId, system: msg.type === 'SYSTEM', 'menu-open': msgMenuId === msg.id, 'quote-highlight': highlightedMessageId === msg.id }">
          <!-- System / recalled -->
          <div v-if="msg.type === 'SYSTEM'" class="msg-center-notice msg-system">{{ msg.content }}</div>
          <div v-else-if="msg.recalledAt" class="msg-center-notice msg-recalled">
            {{ msg.senderUserId === myUserId ? '你' : msg.senderNickname }}撤回了一条消息
          </div>

          <template v-else>
            <!-- Both sides keep the App-style sender avatar beside the bubble. -->
            <div class="msg-avatar" :class="{ clickable: msg.senderUserId !== myUserId }" @click="openSenderProfile(msg)">
              <img v-if="messageAvatarUrl(msg)" :src="messageAvatarUrl(msg)" alt="" />
              <span v-else>{{ messageAvatarInitial(msg) }}</span>
            </div>

            <div class="msg-body" :class="{ mine: msg.senderUserId === myUserId }"
              @pointerdown="beginLongPress($event, msg)" @pointermove="moveLongPress" @pointerup="endLongPress" @pointercancel="endLongPress"
              @click.capture="suppressLongPressClick"
              @contextmenu.prevent="openMessageMenu($event, msg)">
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

              <div v-else-if="msg.type === 'MOMENT_SHARE' || msg.type === 'MOMENT_COMMENT_SHARE'" class="moment-share-card" @click="openShare(msg)">
                <div class="moment-share-kicker"><Sparkles :size="14" />动态分享</div>
                <div v-if="msg.sharedMomentAuthorName" class="moment-share-author">
                  <img v-if="msg.sharedMomentAuthorAvatar" :src="imageUrl(msg.sharedMomentAuthorAvatar)" alt="" />
                  <span>{{ msg.sharedMomentAuthorName }}</span>
                </div>
                <strong>{{ msg.sharedMomentTitle || '分享了一条动态' }}</strong>
                <p>{{ msg.sharedMomentPreview || msg.sharedMomentContent }}</p>
                <img v-if="msg.sharedMomentCoverUrl" :src="imageUrl(msg.sharedMomentCoverUrl)" class="moment-share-cover" alt="" />
                <blockquote v-if="msg.sharedCommentPreview">评论：{{ msg.sharedCommentPreview }}</blockquote>
                <span class="moment-share-footer">查看动态 <ChevronRight :size="15" /></span>
              </div>

              <!-- Menu button -->
              <button class="msg-menu-btn" title="消息操作" @click.stop="msgMenuId = (msgMenuId === msg.id ? null : msg.id)">
                <MoreVertical :size="16" />
              </button>
              <Transition name="action-pop">
              <div v-if="msgMenuId === msg.id" class="msg-menu" :style="mobileMenuStyle">
                <button @click="copyText(msg); msgMenuId = null"><Copy :size="15" /><span>复制</span></button>
                <button @click="startReply(msg); msgMenuId = null"><Reply :size="15" /><span>回复</span></button>
                <button v-if="role === 'SHOP' && isGroup" @click="toggleEssence(msg)"><Sparkles :size="15" /><span>{{ msg.isEssence || msg.essence ? '取消精华' : '精华' }}</span></button>
                <button v-if="msg.senderUserId === myUserId" @click="handleRecall(msg)"><RotateCcw :size="15" /><span>撤回</span></button>
                <button @click="handleDeleteMsg(msg)"><Trash2 :size="15" /><span>删除</span></button>
              </div>
              </Transition>
            </div>
          </template>
        </div>
          <div v-if="msg.deliveryFailed" class="msg-center-notice msg-delivery-failed">
            <strong>{{ msg.failureReason || '对方已将您拉黑，无法发送消息' }}</strong>
            <span>还请您注意您的言行举止，互联网不是法外之地</span>
          </div>
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
        <textarea ref="textInputEl" v-model="textInput" class="chat-text-input" rows="1" placeholder="输入消息"
          @input="resizeInput" @focus="emojiOpen = false" @keydown.enter.exact.prevent="handleSend"></textarea>
        <button class="chat-emoji-btn" type="button" :title="emojiOpen ? '收起表情' : '表情'" :aria-label="emojiOpen ? '收起表情' : '表情'" @click="toggleEmojiPanel">
          <Keyboard v-if="emojiOpen" :size="22" /><Smile v-else :size="22" />
        </button>
        <button class="chat-send-btn" :disabled="!textInput.trim()" title="发送" @click="handleSend"><Send :size="19" /><span>发送</span></button>
      </div>
      <Transition name="emoji-panel">
        <div v-if="emojiOpen" class="chat-emoji-panel" aria-label="聊天表情">
          <button v-for="emoji in customerChatEmojis" :key="emoji" type="button" @click="insertEmoji(emoji)">{{ emoji }}</button>
        </div>
      </Transition>
    </div>

    <div v-if="msgMenuId" class="menu-backdrop" @click="msgMenuId = null"></div>
    <ImageViewer :visible="imageViewer.visible" :src="imageViewer.src" @close="imageViewer.visible = false" />
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted, nextTick } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowLeft, ChevronRight, Copy, ImagePlus, Keyboard, MoreVertical, Reply, RotateCcw, Send, Smile, Sparkles, Star, Trash2, UserRound, UsersRound, X } from 'lucide-vue-next'
import { addEssence, deleteMessage, getChatImageUrl, getLatestUnreadEssence, getMessages, markEssenceRead, recallMessage, removeEssence, sendMessage as apiSendMessage } from '../../api/messages'
import { imageUrl } from '../../composables/useImage'
import { apiUrl } from '../../config/endpoints'
import LoadingSpinner from '../../components/LoadingSpinner.vue'
import EmptyState from '../../components/EmptyState.vue'
import ImageViewer from '../../components/ImageViewer.vue'
import { customerChatEmojis } from '../../constants/chatEmojis'
import { confirmNotice, notifyError } from '../../services/notice'

const route = useRoute()
const router = useRouter()
const conversationId = computed(() => Number(route.params.id))
const myUserId = ref(0)
const currentUser = JSON.parse(localStorage.getItem('user') || '{}')
const role = currentUser?.role
const basePath = role === 'SHOP' ? '/shop' : '/customer'

const conversation = ref({})
const messages = ref([])
const loading = ref(true)
const error = ref('')
const textInput = ref('')
const textInputEl = ref(null)
const emojiOpen = ref(false)
const replyingTo = ref(null)
const msgMenuId = ref(null)
const latestEssence = ref(null)
const highlightedMessageId = ref(null)
const mobileMenuStyle = ref({})
let longPressTimer = null
let highlightTimer = null
let pressStart = null
let longPressTriggered = false

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

function messageAvatarUrl(msg) {
  const fallback = msg.senderUserId === myUserId.value
    ? currentUser?.avatarUrl
    : (!isGroup.value ? conversation.value?.directUserAvatarUrl : null)
  const url = msg.senderAvatarUrl || msg.senderAvatar || fallback
  return url ? imageUrl(url) : ''
}

function messageAvatarInitial(msg) {
  const name = msg.senderUserId === myUserId.value ? currentUser?.nickname : msg.senderNickname
  return name?.charAt(0) || '用'
}

function shouldShowTimestamp(index) {
  const current = new Date(messages.value[index]?.createdAt)
  if (Number.isNaN(current.getTime())) return false
  if (index === 0) return true
  const previous = new Date(messages.value[index - 1]?.createdAt)
  if (Number.isNaN(previous.getTime())) return true
  return current.getTime() - previous.getTime() >= 60_000
}

function formatMessageTime(value) {
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return ''
  const now = new Date()
  const two = number => String(number).padStart(2, '0')
  const time = `${two(date.getHours())}:${two(date.getMinutes())}`
  const day = new Date(date.getFullYear(), date.getMonth(), date.getDate())
  const today = new Date(now.getFullYear(), now.getMonth(), now.getDate())
  const dayDifference = Math.round((today - day) / 86_400_000)
  if (dayDifference === 0) return time
  if (dayDifference === 1) return `昨天 ${time}`
  return `${date.getMonth() + 1}月${date.getDate()}日 ${time}`
}

function openSenderProfile(msg) {
  if (!msg.senderUserId || msg.senderUserId === myUserId.value) return
  router.push({
    path: `${basePath}/chat/user/${msg.senderUserId}`,
    query: { conversationId: conversationId.value, nickname: msg.senderNickname || undefined, avatar: msg.senderAvatarUrl || undefined }
  })
}

async function loadMessages({ silent = false } = {}) {
  const wasEmpty = messages.value.length === 0
  const previousLastId = messages.value.at(-1)?.id
  const nearBottom = !msgContainer.value || msgContainer.value.scrollHeight - msgContainer.value.scrollTop - msgContainer.value.clientHeight < 140
  if (!silent && wasEmpty) loading.value = true
  if (!silent) error.value = ''
  try {
    const data = await getMessages(conversationId.value)
    conversation.value = data.conversation || {}
    messages.value = data.messages || []
    messages.value.forEach(m => {
      if (m.type === 'IMAGE_SHARE' && m.imageUrl && !m.imageExpired) loadChatImage(m)
    })
    const hasNewMessage = messages.value.at(-1)?.id !== previousLastId
    if (wasEmpty || (hasNewMessage && nearBottom)) scrollToBottom(wasEmpty ? 'auto' : 'smooth')
  } catch (e) { if (!silent) error.value = e.message }
  finally { if (!silent) loading.value = false }
}

async function loadChatImage(msg) {
  try {
    const filename = msg.imageUrl.split('/').pop()
    msg.imageBlobUrl = await getChatImageUrl(conversationId.value, filename)
  } catch { }
}

function scrollToBottom(behavior = 'smooth') {
  nextTick(() => msgEnd.value?.scrollIntoView({ behavior }))
}

function openMessageMenu(event, msg) {
  if (!window.matchMedia('(max-width: 768px)').matches) return
  const rect = event.currentTarget.getBoundingClientRect()
  showMessageMenu(rect, msg)
}

function showMessageMenu(rect, msg) {
  const headerBottom = document.querySelector('.chat-header')?.getBoundingClientRect().bottom || 0
  const estimatedHeight = 54
  const actionCount = 3 + (msg.senderUserId === myUserId.value ? 1 : 0) + (role === 'SHOP' && isGroup.value ? 1 : 0)
  const estimatedWidth = Math.min(window.innerWidth - 16, actionCount * 64)
  let top = rect.top - estimatedHeight - 8
  if (top < headerBottom + 8) top = Math.min(window.innerHeight - estimatedHeight - 8, rect.bottom + 8)
  const left = Math.max(8, Math.min(window.innerWidth - estimatedWidth - 8, rect.left + rect.width / 2 - estimatedWidth / 2))
  mobileMenuStyle.value = { top: `${top}px`, left: `${left}px`, width: `${estimatedWidth}px` }
  msgMenuId.value = msg.id
}

function beginLongPress(event, msg) {
  if (!window.matchMedia('(max-width: 768px)').matches) return
  pressStart = { x: event.clientX, y: event.clientY }
  longPressTriggered = false
  const rect = event.currentTarget.getBoundingClientRect()
  clearTimeout(longPressTimer)
  longPressTimer = window.setTimeout(() => {
    longPressTriggered = true
    navigator.vibrate?.(12)
    showMessageMenu(rect, msg)
  }, 480)
}

function moveLongPress(event) {
  if (!pressStart) return
  if (Math.hypot(event.clientX - pressStart.x, event.clientY - pressStart.y) > 10) endLongPress()
}

function endLongPress() {
  clearTimeout(longPressTimer)
  longPressTimer = null
  pressStart = null
}

function suppressLongPressClick(event) {
  if (!longPressTriggered) return
  event.preventDefault()
  event.stopPropagation()
  longPressTriggered = false
}

function scrollToMessage(msgId) {
  const el = document.getElementById('msg-' + msgId)
  if (!el) return
  el.scrollIntoView({ behavior: 'smooth', block: 'center' })
  highlightedMessageId.value = msgId
  clearTimeout(highlightTimer)
  highlightTimer = window.setTimeout(() => { highlightedMessageId.value = null }, 1700)
}

async function handleSend() {
  if (!textInput.value.trim()) return
  const content = textInput.value.trim()
  try {
    const sent = await apiSendMessage(conversationId.value, { content, quotedMessageId: replyingTo.value?.id || null })
    textInput.value = ''
    resizeInput()
    replyingTo.value = null
    if (sent?.id) {
      messages.value = [...messages.value.filter(item => item.id !== sent.id), sent]
        .sort((a, b) => new Date(a.createdAt) - new Date(b.createdAt))
      scrollToBottom()
    } else {
      loadMessages({ silent: true })
    }
  } catch (e) { notifyError(e.message) }
}

function resizeInput() {
  nextTick(() => {
    const input = textInputEl.value
    if (!input) return
    input.style.height = 'auto'
    input.style.height = `${Math.min(input.scrollHeight, 96)}px`
  })
}

function toggleEmojiPanel() {
  emojiOpen.value = !emojiOpen.value
  if (emojiOpen.value) textInputEl.value?.blur()
  nextTick(() => scrollToBottom('auto'))
}

function insertEmoji(emoji) {
  const input = textInputEl.value
  const start = input?.selectionStart ?? textInput.value.length
  const end = input?.selectionEnd ?? textInput.value.length
  textInput.value = textInput.value.slice(0, start) + emoji + textInput.value.slice(end)
  nextTick(() => {
    if (input) input.setSelectionRange(start + emoji.length, start + emoji.length)
    resizeInput()
  })
}

async function handleImageSelect(e) {
  const file = e.target.files?.[0]
  if (!file) return
  try {
    const form = new FormData()
    form.append('image', file)
    const token = localStorage.getItem('token')
    const res = await fetch(apiUrl(`/messages/conversations/${conversationId.value}/images`), {
      method: 'POST',
      headers: { Authorization: `Bearer ${token}` },
      body: form
    })
    const json = await res.json()
    if (json.code !== 0) throw new Error(json.message)
    loadMessages({ silent: true })
  } catch (e) { notifyError(e.message) }
  e.target.value = ''
}

async function handleRecall(msg) {
  if (!await confirmNotice('确认撤回此消息？')) return
  try { await recallMessage(conversationId.value, msg.id); loadMessages({ silent: true }) } catch (e) { notifyError(e.message) }
  msgMenuId.value = null
}

async function handleDeleteMsg(msg) {
  try { await deleteMessage(conversationId.value, msg.id); messages.value = messages.value.filter(m => m.id !== msg.id) } catch (e) { notifyError(e.message) }
  msgMenuId.value = null
}

let pollTimer = null
onMounted(async () => {
  try { myUserId.value = JSON.parse(localStorage.getItem('user') || '{}')?.id || 0 } catch { }
  await loadMessages()
  if (isGroup.value) { try { latestEssence.value = await getLatestUnreadEssence(conversationId.value) } catch { } }
  pollTimer = setInterval(() => loadMessages({ silent: true }), 3000)
})
onUnmounted(() => { clearInterval(pollTimer); clearTimeout(longPressTimer); clearTimeout(highlightTimer) })

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
  } catch (e) { notifyError(e.message) }
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
.chat-detail { display: flex; flex-direction: column; height: 100dvh; background: var(--color-bg); }

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
  display: flex; flex-direction: column; gap: 0;
}
.error-wrap { min-height: 20vh; display: flex; align-items: center; justify-content: center; }

.chat-message-block { width: 100%; margin-bottom: 12px; }
.msg-center-notice { width: 100%; display: flex; align-items: center; justify-content: center; text-align: center; }
.msg-timestamp { padding: 4px 12px 12px; color: #8a8a8a; font-size: 12px; }
.msg-system, .msg-recalled { min-height: 30px; padding: 4px 16px; color: var(--color-text-light); font-size: 12px; line-height: 1.5; }
.msg-delivery-failed { flex-direction: column; gap: 2px; padding: 7px 20px 0; color: #ef5350; font-size: 11px; line-height: 1.45; }
.msg-delivery-failed strong { font-weight: 600; }
.chat-msg-wrapper { display: flex; align-items: flex-end; gap: 8px; animation: bubble-in .32s cubic-bezier(.18,.89,.32,1.28) backwards; transform-origin: left bottom; }
.chat-msg-wrapper.mine { flex-direction: row-reverse; }
.chat-msg-wrapper.mine { transform-origin: right bottom; }
.chat-msg-wrapper.system { justify-content: center; }
.chat-msg-wrapper.menu-open { position: relative; z-index: 121; }
.chat-msg-wrapper { padding: 5px 7px; margin: -5px -7px; border-radius: 8px; transition: background-color .35s ease, box-shadow .35s ease; }
.chat-msg-wrapper.quote-highlight { background: #fff0dd; box-shadow: inset 3px 0 0 #f28c28; animation: quote-emphasis 1.7s ease both; }

.msg-avatar {
  width: 36px; height: 36px; border-radius: 50%; flex-shrink: 0;
  background: var(--color-bg); display: flex; align-items: center; justify-content: center;
  color: var(--color-text-muted); font-size: 13px; font-weight: 700; overflow: hidden;
}
.msg-avatar img { width: 100%; height: 100%; object-fit: cover; }
.msg-avatar.clickable { cursor: pointer; }

.msg-body { max-width: 280px; position: relative; display: flex; flex-direction: column; gap: 4px; }
.msg-body.mine { align-items: flex-end; }
.essence-tag { width: fit-content; padding: 2px 6px; display: inline-flex; align-items: center; gap: 3px; color: #a85c10; background: #ffe7cd; border-radius: 4px; font-size: 10px; font-weight: 700; }

.msg-bubble {
  padding: 10px 14px; border-radius: 8px; font-size: 14px; line-height: 1.5;
  background: var(--color-other-bubble); color: var(--color-text);
  word-break: break-word;
}
.msg-bubble.mine { background: var(--color-primary); color: #fff; }

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
.moment-share-card { width: min(292px, 72vw); overflow: hidden; padding: 11px; background: #fff; border: 1px solid var(--color-line); border-radius: 8px; cursor: pointer; box-shadow: 0 2px 8px rgba(28, 30, 33, .05); }
.moment-share-kicker { display: flex; align-items: center; gap: 5px; margin-bottom: 8px; color: var(--color-primary); font-size: 11px; font-weight: 700; }
.moment-share-author { display: flex; align-items: center; gap: 6px; margin-bottom: 7px; color: var(--color-text-muted); font-size: 12px; }
.moment-share-author img { width: 22px; height: 22px; border-radius: 50%; object-fit: cover; }
.moment-share-card > strong { display: block; color: var(--color-text); font-size: 14px; line-height: 1.4; }
.moment-share-card > p { display: -webkit-box; overflow: hidden; margin-top: 4px; color: var(--color-text-muted); font-size: 12px; line-height: 1.5; -webkit-box-orient: vertical; -webkit-line-clamp: 2; }
.moment-share-cover { width: 100%; aspect-ratio: 16 / 9; display: block; margin-top: 9px; border-radius: 6px; object-fit: cover; }
.moment-share-card blockquote { margin: 8px 0 0; padding: 7px 8px; overflow: hidden; background: #f5f6f7; border-left: 3px solid #f2a354; border-radius: 4px; color: #686d74; font-size: 11px; line-height: 1.45; text-overflow: ellipsis; white-space: nowrap; }
.moment-share-footer { display: flex; align-items: center; justify-content: flex-end; margin-top: 8px; padding-top: 7px; border-top: 1px solid #f0f1f2; color: var(--color-text-light); font-size: 11px; }
.share-card-img { width: 48px; height: 48px; border-radius: 8px; object-fit: cover; flex-shrink: 0; }
.share-card-info { min-width: 0; }
.share-card-title { font-size: 14px; font-weight: 700; display: block; }
.share-card-sub { font-size: 12px; color: var(--color-text-muted); display: block; margin-top: 2px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.share-card-price { font-size: 15px; font-weight: 800; color: var(--color-primary); margin-top: 4px; display: block; }

.msg-menu-btn {
  border: none; background: none; font-size: 16px; color: var(--color-text-muted);
  cursor: pointer; padding: 2px; opacity: 0; position: absolute; top: 2px; right: -20px;
}
.chat-msg-wrapper.mine .msg-menu-btn { right: auto; left: -20px; }
.chat-msg-wrapper:hover .msg-menu-btn { opacity: 1; }

.msg-menu {
  position: absolute; bottom: calc(100% + 6px); right: 0; z-index: 20;
  background: #fff; border-radius: var(--radius); box-shadow: var(--shadow-lg);
  min-width: 112px; max-width: calc(100vw - 24px); padding: 4px;
}
.chat-msg-wrapper:not(.mine) .msg-menu { right: auto; left: 0; }
.chat-msg-wrapper.mine .msg-menu { right: 0; left: auto; }
.msg-menu button {
  display: flex; width: 100%; align-items: center; gap: 7px; padding: 8px 14px; border: none; background: none;
  font-size: 13px; cursor: pointer; text-align: left; border-radius: 4px; color: var(--color-text);
}
.msg-menu button:hover { background: var(--color-bg); }

/* Input bar */
.chat-input-bar { flex-shrink: 0; background: #fff; border-top: 1px solid var(--color-line); padding: 9px 12px; }
.reply-indicator {
  display: flex; justify-content: space-between; align-items: center; font-size: 12px;
  color: var(--color-text-muted); padding: 6px 10px; background: var(--color-bg);
  border-radius: var(--radius); margin-bottom: 8px;
}
.btn-close {
  border: none; background: none; color: var(--color-text-muted);
  cursor: pointer; width: 30px; height: 30px; display: inline-flex; align-items: center; justify-content: center; border-radius: 50%;
}
.chat-input-row { display: flex; gap: 7px; align-items: flex-end; }
.chat-img-btn {
  width: 40px; height: 40px; cursor: pointer; display: flex; align-items: center; justify-content: center;
  color: var(--color-text-muted); padding: 0; border-radius: 9px; flex-shrink: 0;
}
.chat-img-btn:hover { color: var(--color-primary); background: var(--color-bg); }
.chat-text-input {
  min-width: 0; min-height: 40px; max-height: 96px; flex: 1; resize: none; overflow-y: auto;
  border: 1px solid #e2e5e9; outline: none; font-family: inherit; font-size: 15px; line-height: 22px; padding: 8px 13px;
  background: #f6f7f8; border-radius: 10px; color: var(--color-text); transition: border-color .16s ease, background .16s ease, box-shadow .16s ease;
}
.chat-text-input::placeholder { color: var(--color-text-light); }
.chat-text-input:focus { border-color: #f1a45d; background: #fff; box-shadow: 0 0 0 3px rgba(242, 140, 40, .12); }
.chat-emoji-btn { width: 40px; height: 40px; flex: 0 0 auto; display: inline-flex; align-items: center; justify-content: center; border-radius: 9px; background: transparent; color: var(--color-text-muted); }
.chat-emoji-btn:hover, .chat-emoji-btn:active { background: var(--color-bg); color: var(--color-primary); }
.chat-send-btn {
  min-height: 40px; border: none; padding: 0 14px; border-radius: 9px;
  display: inline-flex; align-items: center; justify-content: center; gap: 5px;
  background: var(--color-primary); color: #fff; font-size: 14px; font-weight: 600;
  cursor: pointer; transition: opacity 0.2s, transform .14s ease; flex-shrink: 0;
}
.chat-send-btn:disabled { opacity: 0.4; cursor: not-allowed; }
.chat-send-btn:not(:disabled):hover { background: var(--color-primary-light); }
.chat-send-btn:not(:disabled):active { transform: scale(.96); }
.chat-emoji-panel { height: 210px; display: grid; grid-template-columns: repeat(10, minmax(0, 1fr)); gap: 5px; margin: 9px -2px -1px; padding: 9px 4px 4px; overflow-y: auto; border-top: 1px solid #eceef1; }
.chat-emoji-panel button { min-width: 0; aspect-ratio: 1; display: grid; place-items: center; border-radius: 7px; background: transparent; font-size: 23px; line-height: 1; }
.chat-emoji-panel button:hover, .chat-emoji-panel button:active { background: #fff1df; }
.emoji-panel-enter-active, .emoji-panel-leave-active { transition: height .2s ease, opacity .16s ease, transform .2s ease; overflow: hidden; }
.emoji-panel-enter-from, .emoji-panel-leave-to { height: 0; opacity: 0; transform: translateY(8px); }

.menu-backdrop { position: fixed; inset: 0; z-index: 120; background: rgba(0, 0, 0, 0.02); }
.action-pop-enter-active, .action-pop-leave-active { transition: opacity .16s ease, transform .2s cubic-bezier(.18,.89,.32,1.2); }
.action-pop-enter-from, .action-pop-leave-to { opacity: 0; transform: translateY(5px) scale(.84); }
@keyframes bubble-in { from { opacity: 0; transform: scale(.82) translateY(5px); } to { opacity: 1; transform: scale(1) translateY(0); } }
@keyframes quote-emphasis { 0%, 100% { background: transparent; box-shadow: inset 0 0 0 transparent; } 18%, 72% { background: #fff0dd; box-shadow: inset 3px 0 0 #f28c28; } }

@media (max-width: 768px) {
  .chat-detail { height: 100dvh; }
  .chat-header { min-height: calc(56px + env(safe-area-inset-top)); padding: env(safe-area-inset-top) 8px 0; }
  .chat-messages { padding: 12px 10px; overscroll-behavior: contain; }
  .chat-input-bar { padding: 8px 8px max(8px, env(safe-area-inset-bottom)); }
  .chat-text-input { font-size: 16px; }
  .chat-send-btn { min-height: 40px; padding-inline: 14px; }
  .chat-send-btn span { display: none; }
  .chat-send-btn { width: 40px; padding: 0; }
  .chat-emoji-panel { height: 210px; grid-template-columns: repeat(8, minmax(0, 1fr)); gap: 6px; }
  .msg-body { max-width: min(76vw, 320px); }
  .msg-body { -webkit-touch-callout: none; user-select: none; }
  .msg-menu-btn { display: none; }
  .msg-menu {
    position: fixed; right: auto !important; bottom: auto;
    max-width: calc(100vw - 16px); min-width: 0; margin: 0; padding: 4px;
    display: flex; align-items: stretch;
    z-index: 122; border: 0; border-radius: 8px; background: #303236; color: #fff;
    box-shadow: 0 8px 24px rgba(0, 0, 0, .24); transform-origin: center bottom;
  }
  .msg-menu button { min-width: 0; min-height: 48px; flex: 1; flex-direction: column; justify-content: center; gap: 3px; padding: 5px 3px; color: #fff; font-size: 11px; text-align: center; white-space: nowrap; }
  .msg-menu button:hover, .msg-menu button:active { background: rgba(255, 255, 255, .1); }
  .msg-menu button svg { width: 17px; height: 17px; }
}
</style>
