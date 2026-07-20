<template>
  <div class="page-container group-page">
    <div class="page-header"><h1 class="page-title">群聊信息</h1></div>
    <LoadingSpinner v-if="loading" />
    <EmptyState v-else-if="error" icon="warning" :title="error" action-text="重试" @action="load" />
    <template v-else-if="group">
      <section class="group-card summary">
        <span class="cover"><img v-if="group.coverImageUrl" :src="imageUrl(group.coverImageUrl)" alt="" /><UsersRound v-else :size="27" /></span>
        <span><strong>{{ group.name }}</strong><small>{{ group.isActive === false || group.status === 'DISSOLVED' ? '已解散' : '家庭群聊' }}</small></span>
      </section>
      <section class="group-card actions">
        <router-link :to="base + '/chat/' + group.id + '/essences'"><Sparkles :size="20" /><span>群精华</span><ChevronRight :size="18" /></router-link>
        <div v-if="group.announcement" class="row"><Megaphone :size="20" /><span><b>群公告</b><small>{{ group.announcement }}</small></span></div>
        <label v-if="ownMember" class="row"><BellOff :size="20" /><span>消息免打扰</span><input type="checkbox" :checked="ownMember.muted" :disabled="working" @change="setMuted($event.target.checked)" /></label>
        <button v-if="!activeMember && groupActive" class="row" :disabled="working" @click="join"><UserPlus :size="20" /><span>加入家庭群聊</span><ChevronRight :size="18" /></button>
        <button v-if="activeMember" class="row danger" :disabled="working" @click="leave"><LogOut :size="20" /><span>退出群聊</span></button>
      </section>
      <h2>群成员（{{ group.members?.length || 0 }}）</h2>
      <section class="group-card member-list">
        <router-link v-for="member in group.members" :key="member.userId" :to="member.userId === myId ? '' : base + '/chat/user/' + member.userId + '?conversationId=' + group.id" class="member-row" :class="{ disabled: member.userId === myId }">
          <span class="avatar"><img v-if="member.avatarUrl" :src="imageUrl(member.avatarUrl)" alt="" /><UserRound v-else :size="18" /></span>
          <span><b>{{ member.nickname }}</b><small>{{ member.role === 'OWNER' ? '群主' : memberLabel(member.memberStatus) }}</small></span>
          <ChevronRight v-if="member.userId !== myId" :size="18" />
        </router-link>
      </section>
    </template>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { BellOff, ChevronRight, LogOut, Megaphone, Sparkles, UserPlus, UserRound, UsersRound } from 'lucide-vue-next'
import { getFamilyGroup, joinFamilyGroup, leaveFamilyGroup, muteFamilyGroup } from '../../api/messages'
import { imageUrl } from '../../composables/useImage'
import LoadingSpinner from '../../components/LoadingSpinner.vue'
import EmptyState from '../../components/EmptyState.vue'

const route = useRoute()
const group = ref(null)
const loading = ref(true)
const working = ref(false)
const error = ref('')
const me = JSON.parse(localStorage.getItem('user') || '{}')
const myId = me.id
const base = me.role === 'SHOP' ? '/shop' : '/customer'
const ownMember = computed(() => group.value?.members?.find(item => item.userId === myId))
const activeMember = computed(() => ownMember.value?.memberStatus === 'ACTIVE')
const groupActive = computed(() => group.value?.isActive !== false && group.value?.status !== 'DISSOLVED')

function memberLabel(status) { return ({ ACTIVE: '群成员', PENDING: '待审核', REJECTED: '已拒绝' })[status] || status || '群成员' }
async function load() { loading.value = true; error.value = ''; try { group.value = await getFamilyGroup(route.params.id) } catch (e) { error.value = e.message } finally { loading.value = false } }
async function perform(action, confirmation) {
  if (confirmation && !confirm(confirmation)) return
  working.value = true
  try { await action(); await load() } catch (e) { alert(e.message) } finally { working.value = false }
}
function setMuted(value) { perform(() => muteFamilyGroup(route.params.id, value)) }
function join() { perform(() => joinFamilyGroup(route.params.id)) }
function leave() { perform(() => leaveFamilyGroup(route.params.id), '确认退出该家庭群聊？') }
onMounted(load)
</script>

<style scoped>
.group-page { max-width: 720px; }.group-card { overflow: hidden; background: #fff; border: 1px solid var(--color-line-light); border-radius: var(--radius-lg); }.summary { padding: 16px; display: flex; align-items: center; gap: 12px; }.summary span:last-child,.member-row span:nth-child(2) { min-width: 0; display: flex; flex: 1; flex-direction: column; }.summary small,.member-row small { margin-top: 2px; color: var(--color-text-muted); }.cover { width: 56px; height: 56px; overflow: hidden; flex: 0 0 auto; display: inline-flex; align-items: center; justify-content: center; border-radius: 50%; background: var(--color-primary-bg); color: var(--color-primary); }.cover img,.avatar img { width: 100%; height: 100%; object-fit: cover; }.actions { margin-top: 12px; }.row,.actions a { width: 100%; min-height: 58px; padding: 10px 16px; display: flex; align-items: center; gap: 12px; color: var(--color-text); background: #fff; text-align: left; }.row + .row,.actions a + .row { border-top: 1px solid var(--color-line); }.row > span,.actions a > span { flex: 1; }.row span:has(small) { display: flex; flex-direction: column; }.row small { color: var(--color-text-muted); }.row svg,.actions a > svg:first-child { color: var(--color-primary); }.row.danger,.row.danger svg { color: var(--color-error); }input { width: 44px; height: 24px; accent-color: var(--color-primary); }h2 { margin: 20px 4px 8px; font-size: 15px; }.member-row { min-height: 64px; padding: 9px 16px; display: flex; align-items: center; gap: 11px; color: var(--color-text); }.member-row + .member-row { border-top: 1px solid var(--color-line); }.member-row.disabled { pointer-events: none; }.avatar { width: 40px; height: 40px; overflow: hidden; flex: 0 0 auto; display: inline-flex; align-items: center; justify-content: center; border-radius: 50%; background: var(--color-bg); color: var(--color-text-muted); }
</style>
