<template>
  <div class="conv-swipe-shell" :class="{ open: swipeOpen, swiping: swipeOffset < 0, 'menu-open': menuOpen }">
    <div class="conv-swipe-actions" aria-label="会话操作">
      <button class="pin-action" type="button" @click.stop="runSwipeAction('pin')"><Pin :size="19" />{{ conversation.pinned ? '取消置顶' : '置顶' }}</button>
      <button v-if="conversation.isDirect" class="delete-action" type="button" @click.stop="runSwipeAction('delete')"><Trash2 :size="19" />删除</button>
    </div>
  <div class="conv-tile" :class="{ 'conv-customer': variant === 'customer' }" :style="swipeStyle"
    @click="handleTileClick" @pointerdown="startSwipe" @pointermove="moveSwipe" @pointerup="endSwipe" @pointercancel="cancelSwipe">
    <div class="conv-avatar">
      <img v-if="avatar" :src="avatar" alt="" />
      <span v-else class="conv-avatar-placeholder">{{ isGroup ? '群' : '户' }}</span>
      <span v-if="unreadBadge" class="conv-badge" :class="{ muted: conversation.muted }">{{ unreadBadge }}</span>
    </div>
    <div class="conv-body">
      <div class="conv-top">
        <span class="conv-title">{{ title }}</span>
        <span v-if="isGroup" class="conv-type-label">群聊</span>
        <Pin v-if="conversation.pinned" class="conv-pin" :size="13" aria-label="已置顶" />
        <span class="conv-time">{{ formatTime(conversation.lastMessageAt) }}</span>
      </div>
      <div class="conv-subtitle">
        <span v-if="conversation.status === 'DISSOLVED'" class="conv-inactive">已解散</span>
        <span v-else class="conv-last-msg">{{ conversation.lastMessageContent || (isGroup ? '家庭群聊' : '开始聊天') }}</span>
      </div>
    </div>
    <button v-if="showMenu" ref="menuAnchor" type="button" class="conv-menu-trigger" title="更多操作" aria-label="更多操作"
      @click.stop="menuOpen = !menuOpen" @keydown.enter.stop="menuOpen = !menuOpen">
      <MoreVertical :size="20" aria-hidden="true" />
    </button>
    <AnchoredMenu :open="menuOpen" :anchor="menuAnchor" @close="menuOpen = false">
      <div class="conv-menu">
        <button @click.stop="$emit('pin', conversation); menuOpen = false">
          {{ conversation.pinned ? '取消置顶' : '置顶' }}
        </button>
        <button v-if="conversation.isDirect" class="danger" @click.stop="$emit('delete', conversation); menuOpen = false">删除</button>
      </div>
    </AnchoredMenu>
  </div>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'
import { MoreVertical, Pin, Trash2 } from 'lucide-vue-next'
import { imageUrl } from '../composables/useImage'
import { formatMomentTime } from '../composables/useMomentTime'
import AnchoredMenu from './AnchoredMenu.vue'

const props = defineProps({
  conversation: { type: Object, required: true },
  showMenu: { type: Boolean, default: true },
  variant: { type: String, default: 'shop' }
})

const emit = defineEmits(['click', 'pin', 'delete'])

const menuOpen = ref(false)
const menuAnchor = ref(null)
const swipeOpen = ref(false)
const swipeOffset = ref(0)
let pointerStart = null
let moved = false

const isGroup = computed(() => props.conversation.type === 'FAMILY_GROUP')

const title = computed(() => {
  if (isGroup.value) return props.conversation.name || '家庭群聊'
  return props.conversation.directUserNickname || '用户'
})

const avatar = computed(() => {
  const url = isGroup.value ? props.conversation.coverImageUrl : props.conversation.directUserAvatarUrl
  return url ? imageUrl(url) : null
})

const unreadBadge = computed(() => {
  if (!props.conversation.unreadCount || props.conversation.unreadCount <= 0) return null
  return props.conversation.unreadCount > 99 ? '99+' : String(props.conversation.unreadCount)
})
const swipeWidth = computed(() => props.conversation.isDirect ? 144 : 72)
const swipeStyle = computed(() => ({ transform: `translateX(${swipeOffset.value}px)` }))

function startSwipe(event) {
  if (event.pointerType !== 'touch' || !window.matchMedia('(max-width: 480px)').matches) return
  pointerStart = { x: event.clientX, y: event.clientY, initial: swipeOpen.value ? -swipeWidth.value : 0 }
  moved = false
  event.currentTarget.setPointerCapture?.(event.pointerId)
}

function moveSwipe(event) {
  if (!pointerStart) return
  const dx = event.clientX - pointerStart.x
  const dy = event.clientY - pointerStart.y
  if (Math.abs(dy) > Math.abs(dx) && Math.abs(dy) > 8) { cancelSwipe(); return }
  if (Math.abs(dx) > 5) moved = true
  swipeOffset.value = Math.max(-swipeWidth.value, Math.min(0, pointerStart.initial + dx))
}

function endSwipe() {
  if (!pointerStart) return
  swipeOpen.value = swipeOffset.value < -swipeWidth.value * .35
  swipeOffset.value = swipeOpen.value ? -swipeWidth.value : 0
  pointerStart = null
}

function cancelSwipe() {
  pointerStart = null
  swipeOffset.value = swipeOpen.value ? -swipeWidth.value : 0
}

function handleTileClick() {
  if (moved) { moved = false; return }
  if (swipeOpen.value) { swipeOpen.value = false; swipeOffset.value = 0; return }
  emit('click', props.conversation)
}

function runSwipeAction(action) {
  swipeOpen.value = false
  swipeOffset.value = 0
  emit(action, props.conversation)
}

const formatTime = formatMomentTime
</script>

<style scoped>
.conv-swipe-shell { position: relative; overflow: hidden; background: #fff; border-radius: var(--radius-lg); }
.conv-swipe-shell + .conv-swipe-shell { margin-top: 1px; }
.conv-swipe-actions { position: absolute; top: 1px; right: 1px; bottom: 1px; display: none; opacity: 0; visibility: hidden; overflow: hidden; border-radius: 0 var(--radius-lg) var(--radius-lg) 0; transition: opacity .08s ease; }
.conv-swipe-shell.open .conv-swipe-actions, .conv-swipe-shell.swiping .conv-swipe-actions { opacity: 1; visibility: visible; }
.conv-swipe-actions button { width: 72px; height: 100%; display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 5px; color: #fff; font-size: 12px; }
.conv-swipe-actions .pin-action { background: #ee942f; }
.conv-swipe-actions .delete-action { background: #d9544f; }
.conv-tile {
  display: flex; align-items: center; gap: 12px; padding: 14px 16px;
  background: #fff; border-radius: var(--radius);
  cursor: pointer; position: relative; transition: background 0.15s, transform .22s cubic-bezier(.22,.8,.32,1); touch-action: pan-y;
}
.conv-tile.conv-customer { border-radius: var(--radius-lg); }
.conv-tile:hover { background: #FAFAFA; }

.conv-avatar {
  width: 48px; height: 48px; border-radius: 50%; flex-shrink: 0;
  background: var(--color-bg); display: flex; align-items: center; justify-content: center;
  position: relative;
}
.conv-avatar img { width: 100%; height: 100%; object-fit: cover; }
.conv-avatar-placeholder {
  font-size: 16px; font-weight: 700; color: var(--color-text-muted);
}

.conv-badge {
  position: absolute; top: -4px; right: -4px;
  min-width: 18px; height: 18px; border-radius: 9px;
  background: var(--color-error); color: #fff;
  font-size: 11px; font-weight: 700;
  display: flex; align-items: center; justify-content: center;
  padding: 0 5px; line-height: 1;
}
.conv-badge.muted { background: #BDBDBD; }

.conv-body { flex: 1; min-width: 0; }
.conv-top { display: flex; align-items: center; gap: 6px; margin-bottom: 4px; }
.conv-title {
  font-size: 15px; font-weight: 700; color: var(--color-text);
  overflow: hidden; text-overflow: ellipsis; white-space: nowrap;
  max-width: 160px;
}
.conv-type-label {
  font-size: 11px; padding: 1px 6px; border-radius: 4px;
  background: var(--color-group-badge-bg); color: var(--color-group-badge-text);
  font-weight: 600; white-space: nowrap; flex-shrink: 0;
}
.conv-pin { color: var(--color-text-muted); flex-shrink: 0; }
.conv-time { font-size: 12px; color: var(--color-text-light); margin-left: auto; flex-shrink: 0; }
.conv-subtitle { overflow: hidden; }
.conv-last-msg {
  font-size: 13px; color: var(--color-text-muted);
  overflow: hidden; text-overflow: ellipsis; white-space: nowrap; display: block;
}
.conv-inactive {
  font-size: 12px; color: var(--color-error);
}

.conv-menu-trigger {
  width: 36px; height: 36px; color: var(--color-text-muted); padding: 0;
  display: inline-flex; align-items: center; justify-content: center;
  background: transparent; cursor: pointer; position: relative; line-height: 1; flex-shrink: 0;
  border-radius: 4px;
}
.conv-menu-trigger:hover { background: var(--color-bg); }
.conv-menu {
  background: #fff; border-radius: var(--radius); box-shadow: var(--shadow-lg);
  min-width: 120px; padding: 4px; overflow: hidden;
}
.conv-menu button {
  display: block; width: 100%; padding: 10px 14px; border: none; background: none;
  font-size: 14px; text-align: left; cursor: pointer; border-radius: 4px;
  color: var(--color-text);
}
.conv-menu button:hover { background: var(--color-bg); }
.conv-menu button.danger { color: var(--color-error); }

@media (min-width: 481px) {
  .conv-swipe-shell { overflow: visible; }
}

@media (max-width: 480px) {
  .conv-swipe-shell { border-radius: var(--radius-lg); }
  .conv-swipe-actions { display: flex; }
  .conv-tile, .conv-tile.conv-customer { padding: 12px; gap: 10px; border-radius: var(--radius-lg); }
  .conv-menu-trigger { display: none; }
  .conv-title { max-width: min(42vw, 180px); }
}
</style>
