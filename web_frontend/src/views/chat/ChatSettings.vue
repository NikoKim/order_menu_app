<template>
  <div class="page-container settings-page">
    <div class="page-header"><h1 class="page-title">{{ role === 'SHOP' ? '聊天设置' : '隐私设置' }}</h1></div>
    <LoadingSpinner v-if="loading" />
    <template v-else>
      <h2>聊天隐私设置</h2>
      <section class="settings-card">
        <label class="setting-row">
          <span class="row-icon"><MessageCircle :size="21" /></span>
          <span class="row-copy"><strong>允许私聊</strong><small>{{ role === 'SHOP' ? '开启后顾客可向店铺发起私聊。' : '关闭后，其他用户无法向你发起新的私聊。' }}</small></span>
          <input type="checkbox" :checked="form.allowPrivateChat" :disabled="saving" @change="save('allowPrivateChat', $event.target.checked)" />
        </label>
        <label v-if="role === 'CUSTOMER'" class="setting-row">
          <span class="row-icon"><UsersRound :size="21" /></span>
          <span class="row-copy"><strong>自动加入家庭群聊</strong><small>关闭后，需要确认家庭群聊邀请。</small></span>
          <input type="checkbox" :checked="form.autoJoinFamilyChat" :disabled="saving" @change="save('autoJoinFamilyChat', $event.target.checked)" />
        </label>
      </section>

      <h2>个人信息隐私设置</h2>
      <section class="settings-card">
        <label class="setting-row">
          <span class="row-icon"><House :size="21" /></span>
          <span class="row-copy"><strong>展示已加入家庭</strong><small>控制他人在资料页能否看到你加入的家庭。</small></span>
          <input type="checkbox" :checked="form.showJoinedFamilies" :disabled="saving" @change="save('showJoinedFamilies', $event.target.checked)" />
        </label>
        <label class="setting-row">
          <span class="row-icon"><Phone :size="21" /></span>
          <span class="row-copy"><strong>展示手机号</strong><small>控制私聊和家庭成员资料中是否显示手机号。</small></span>
          <input type="checkbox" :checked="form.showPhone" :disabled="saving" @change="save('showPhone', $event.target.checked)" />
        </label>
      </section>

      <h2>动态隐私设置</h2>
      <router-link v-if="role === 'CUSTOMER'" to="/customer/moment-privacy" class="settings-card link-row">
        <span class="row-icon"><Newspaper :size="21" /></span>
        <span class="row-copy"><strong>动态页可见人群</strong><small>设置互聊好友、家庭成员、公开或私密。</small></span>
        <ChevronRight :size="19" />
      </router-link>
      <section v-else class="settings-card visibility-row">
        <span class="row-icon"><Newspaper :size="21" /></span>
        <label class="row-copy"><strong>动态页可见人群</strong><select v-model="profileVisibility" class="form-select" :disabled="saving" @change="saveVisibility"><option value="CHAT_FRIENDS">仅互聊好友可见</option><option value="FAMILY">仅家庭成员可见</option><option value="PUBLIC">公开</option><option value="PRIVATE">私密</option></select></label>
      </section>
      <p v-if="notice" class="save-notice">{{ notice }}</p>
    </template>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ChevronRight, House, MessageCircle, Newspaper, Phone, UsersRound } from 'lucide-vue-next'
import { getCurrentUser, updateCurrentUser } from '../../api/auth'
import { getMyProfileVisibility, updateMyProfileVisibility } from '../../api/moments'
import LoadingSpinner from '../../components/LoadingSpinner.vue'

const loading = ref(true)
const saving = ref(false)
const notice = ref('')
const role = JSON.parse(localStorage.getItem('user') || '{}')?.role
const profileVisibility = ref('CHAT_FRIENDS')
const form = reactive({ allowPrivateChat: true, autoJoinFamilyChat: true, showJoinedFamilies: true, showPhone: false })

async function load() {
  try {
    const user = await getCurrentUser()
    Object.keys(form).forEach(key => { if (typeof user[key] === 'boolean') form[key] = user[key] })
    if (role === 'SHOP') {
      const value = await getMyProfileVisibility()
      profileVisibility.value = value.visibility || value || 'CHAT_FRIENDS'
    }
  } finally { loading.value = false }
}

async function saveVisibility() {
  saving.value = true
  try { await updateMyProfileVisibility(profileVisibility.value); notice.value = '动态页可见人群已保存' }
  catch (error) { notice.value = error.message || '保存失败' }
  finally { saving.value = false }
}

async function save(key, value) {
  const previous = form[key]
  form[key] = value
  saving.value = true
  notice.value = ''
  try {
    const updated = await updateCurrentUser({ ...form })
    const savedUser = JSON.parse(localStorage.getItem('user') || '{}')
    localStorage.setItem('user', JSON.stringify({ ...savedUser, ...updated, ...form }))
    notice.value = '设置已保存'
  } catch (error) {
    form[key] = previous
    notice.value = error.message || '保存失败'
  } finally { saving.value = false }
}

onMounted(load)
</script>

<style scoped>
.settings-page { max-width: 720px; }
h2 { margin: 14px 4px 8px; color: var(--color-text-muted); font-size: 14px; }
.settings-card { display: block; overflow: hidden; color: var(--color-text); background: #fff; border: 1px solid var(--color-line-light); border-radius: var(--radius-lg); }
.setting-row, .link-row { min-height: 78px; padding: 12px 16px; display: flex; align-items: center; gap: 12px; }
.setting-row + .setting-row { border-top: 1px solid var(--color-line); }
.row-icon { width: 36px; height: 36px; flex: 0 0 auto; display: inline-flex; align-items: center; justify-content: center; color: var(--color-primary); background: var(--color-primary-bg); border-radius: var(--radius); }
.row-copy { flex: 1; min-width: 0; display: flex; flex-direction: column; }
.row-copy strong { font-size: 15px; }
.row-copy small { margin-top: 3px; color: var(--color-text-muted); line-height: 1.35; }
input[type='checkbox'] { width: 44px; height: 24px; accent-color: var(--color-primary); }
.save-notice { margin-top: 12px; text-align: center; color: var(--color-text-muted); font-size: 13px; }
.visibility-row { padding: 14px 16px; display: flex; align-items: center; gap: 12px; }.visibility-row select { margin-top: 7px; }
</style>
