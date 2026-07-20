<template>
  <div class="conv-tile" :class="{ 'conv-customer': variant === 'customer' }" @click="$emit('click', conversation)">
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
    <div v-if="showMenu" class="conv-menu-trigger" role="button" tabindex="0" title="更多操作"
      @click.stop="menuOpen = !menuOpen" @keydown.enter.stop="menuOpen = !menuOpen">
      <MoreVertical :size="20" aria-hidden="true" />
      <div v-if="menuOpen" class="conv-menu" @click.stop>
        <button @click.stop="$emit('pin', conversation); menuOpen = false">
          {{ conversation.pinned ? '取消置顶' : '置顶' }}
        </button>
        <button v-if="conversation.isDirect" class="danger" @click.stop="$emit('delete', conversation); menuOpen = false">删除</button>
      </div>
    </div>
    <div v-if="menuOpen" class="menu-backdrop" @click="menuOpen = false"></div>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'
import { MoreVertical, Pin } from 'lucide-vue-next'
import { imageUrl } from '../composables/useImage'

const props = defineProps({
  conversation: { type: Object, required: true },
  showMenu: { type: Boolean, default: true },
  variant: { type: String, default: 'shop' }
})

defineEmits(['click', 'pin', 'delete'])

const menuOpen = ref(false)

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

function formatTime(ts) {
  if (!ts) return ''
  const d = new Date(ts)
  const now = new Date()
  const diff = now - d
  if (diff < 60000) return '刚刚'
  if (diff < 3600000) return Math.floor(diff / 60000) + '分钟前'
  if (diff < 86400000) return Math.floor(diff / 3600000) + '小时前'
  if (diff < 172800000) return '昨天'
  if (d.getFullYear() === now.getFullYear()) {
    return `${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
  }
  return d.toLocaleDateString()
}
</script>

<style scoped>
.conv-tile {
  display: flex; align-items: center; gap: 12px; padding: 14px 16px;
  background: #fff; border-radius: var(--radius);
  cursor: pointer; position: relative; transition: background 0.15s;
}
.conv-tile.conv-customer { border-radius: var(--radius-lg); }
.conv-tile:hover { background: #FAFAFA; }
.conv-tile + .conv-tile { margin-top: 1px; }

.conv-avatar {
  width: 48px; height: 48px; border-radius: 50%; flex-shrink: 0;
  background: var(--color-bg); display: flex; align-items: center; justify-content: center;
  overflow: hidden; position: relative;
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
  position: absolute; right: 0; top: 100%; z-index: 50;
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
.menu-backdrop { position: fixed; inset: 0; z-index: 49; }

@media (max-width: 480px) {
  .conv-tile { padding: 12px; gap: 10px; border-radius: 0; }
  .conv-title { max-width: min(42vw, 180px); }
}
</style>
