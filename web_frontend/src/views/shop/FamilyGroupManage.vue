<template>
  <div class="page-container manage-page">
    <div class="page-header"><h1 class="page-title">家庭群聊管理</h1></div>
    <LoadingSpinner v-if="loading" />
    <EmptyState v-else-if="error" icon="warning" :title="error" action-text="重试" @action="load" />
    <template v-else-if="group">
      <section class="manage-card">
        <h2><Settings2 :size="19" />群资料</h2>
        <label>群聊名称<input v-model.trim="form.name" class="form-input" /></label>
        <label>群公告<textarea v-model.trim="form.announcement" class="form-textarea" placeholder="填写群公告"></textarea></label>
        <button class="btn btn-primary btn-block" :disabled="saving" @click="saveInfo">保存群资料</button>
      </section>
      <section v-if="group.status === 'DISSOLVED' || group.isActive === false" class="manage-card restore">
        <RotateCcw :size="21" /><div><strong>群聊已解散</strong><small>恢复后家庭成员可以继续使用群聊。</small></div><button class="btn btn-outline" :disabled="saving" @click="perform(restoreFamilyGroup)">恢复</button>
      </section>
      <template v-else>
        <section class="manage-card"><h2><UserRoundCheck :size="19" />待审核成员（{{ pending.length }}）</h2><EmptyState v-if="!pending.length" icon="users" title="暂无待审核成员" /><article v-for="member in pending" :key="member.userId" class="member"><span class="avatar"><img v-if="member.avatarUrl" :src="imageUrl(member.avatarUrl)" alt="" /><UserRound v-else :size="18" /></span><span class="copy"><strong>{{ member.nickname }}</strong><small>申请加入</small></span><button title="拒绝" class="reject" @click="perform(() => rejectMember(group.id, member.userId))"><X :size="18" /></button><button title="同意" class="approve" @click="perform(() => approveMember(group.id, member.userId))"><Check :size="18" /></button></article></section>
        <section class="manage-card"><h2><UsersRound :size="19" />群成员（{{ active.length }}）</h2><article v-for="member in active" :key="member.userId" class="member"><span class="avatar"><img v-if="member.avatarUrl" :src="imageUrl(member.avatarUrl)" alt="" /><UserRound v-else :size="18" /></span><router-link class="copy" :to="`/shop/chat/user/${member.userId}?conversationId=${group.id}`"><strong>{{ member.nickname }}</strong><small>{{ member.role === 'OWNER' ? '群主' : '群成员' }}</small></router-link><button v-if="member.role !== 'OWNER'" title="移出群聊" class="remove" @click="remove(member)"><UserMinus :size="18" /></button></article></section>
        <button class="dissolve" :disabled="saving" @click="dissolve"><UsersRound :size="20" /><span><strong>解散家庭群聊</strong><small>历史消息会继续保留</small></span><ChevronRight :size="18" /></button>
      </template>
    </template>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute } from 'vue-router'
import { Check, ChevronRight, RotateCcw, Settings2, UserMinus, UserRound, UserRoundCheck, UsersRound, X } from 'lucide-vue-next'
import { approveMember, dissolveFamilyGroup, getFamilyGroup, rejectMember, removeFamilyMember, restoreFamilyGroup, updateFamilyGroup } from '../../api/messages'
import { imageUrl } from '../../composables/useImage'
import LoadingSpinner from '../../components/LoadingSpinner.vue'
import EmptyState from '../../components/EmptyState.vue'
const route = useRoute(), group = ref(null), loading = ref(true), saving = ref(false), error = ref('')
const form = reactive({ name: '', announcement: '' })
const pending = computed(() => group.value?.members?.filter(m => m.memberStatus === 'PENDING') || [])
const active = computed(() => group.value?.members?.filter(m => m.memberStatus === 'ACTIVE') || [])
async function load() { loading.value = true; error.value = ''; try { group.value = await getFamilyGroup(route.params.id); form.name = group.value.name || ''; form.announcement = group.value.announcement || '' } catch (e) { error.value = e.message } finally { loading.value = false } }
async function perform(fn) { saving.value = true; try { await fn(group.value.id); await load() } catch (e) { alert(e.message) } finally { saving.value = false } }
function saveInfo() { perform(() => updateFamilyGroup(group.value.id, { ...form })) }
function remove(member) { if (confirm(`确认将 ${member.nickname} 移出群聊？`)) perform(() => removeFamilyMember(group.value.id, member.userId)) }
function dissolve() { if (confirm('确认解散家庭群聊？历史消息会继续保留。')) perform(() => dissolveFamilyGroup(group.value.id)) }
onMounted(load)
</script>

<style scoped>
.manage-page { max-width: 720px; }.manage-card,.dissolve { width: 100%; margin-bottom: 12px; padding: 16px; background: #fff; border: 1px solid var(--color-line-light); border-radius: var(--radius); }.manage-card h2 { margin-bottom: 14px; display: flex; align-items: center; gap: 8px; font-size: 16px; }.manage-card h2 svg { color: var(--color-primary); }.manage-card > label { margin-bottom: 12px; display: flex; flex-direction: column; gap: 6px; font-size: 13px; font-weight: 600; }.restore { display: flex; align-items: center; gap: 11px; }.restore > svg { color: var(--color-primary); }.restore div { flex: 1; display: flex; flex-direction: column; }.restore small,.member small,.dissolve small { color: var(--color-text-muted); }.member { min-height: 62px; display: flex; align-items: center; gap: 10px; }.member + .member { border-top: 1px solid var(--color-line); }.avatar { width: 40px; height: 40px; overflow: hidden; flex: 0 0 auto; display: inline-flex; align-items: center; justify-content: center; border-radius: 50%; color: var(--color-primary); background: var(--color-primary-bg); }.avatar img { width: 100%; height: 100%; object-fit: cover; }.copy { flex: 1; min-width: 0; display: flex; flex-direction: column; color: var(--color-text); }.member button { width: 36px; height: 36px; display: inline-flex; align-items: center; justify-content: center; background: transparent; border-radius: 50%; }.reject,.remove { color: var(--color-error); }.approve { color: var(--color-success); }.dissolve { min-height: 68px; display: flex; align-items: center; gap: 11px; color: var(--color-error); text-align: left; }.dissolve span { flex: 1; display: flex; flex-direction: column; }.dissolve small { margin-top: 2px; }
</style>
