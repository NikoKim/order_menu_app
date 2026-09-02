<template>
  <div class="page-container sub-page">
    <header class="page-header back-header">
      <button class="icon-btn" title="返回" @click="router.back()"><ArrowLeft :size="22" /></button>
      <h1 class="page-title">隐私设置</h1>
    </header>
    <LoadingSpinner v-if="loading" />
    <EmptyState v-else-if="error" icon="warning" :title="error" action-text="重试" @action="load" />
    <section v-else class="settings-card">
      <div class="settings-copy">
        <strong>个人动态页可见范围</strong>
        <p>控制其他用户查看你个人动态页的范围</p>
      </div>
      <label v-for="option in options" :key="option.value" class="option-row" :class="{ selected: visibility === option.value }">
        <span>
          <strong>{{ option.label }}</strong>
          <small>{{ option.desc }}</small>
        </span>
        <input v-model="visibility" type="radio" :value="option.value" @change="save" />
      </label>
      <p v-if="saved" class="save-ok">设置已保存</p>
    </section>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ArrowLeft } from 'lucide-vue-next'
import { getMyProfileVisibility, updateMyProfileVisibility } from '../../api/moments'
import LoadingSpinner from '../../components/LoadingSpinner.vue'
import EmptyState from '../../components/EmptyState.vue'
import { notifyError } from '../../services/notice'

const router = useRouter()
const visibility = ref('PUBLIC')
const loading = ref(true)
const error = ref('')
const saved = ref(false)
const options = [
  { value: 'PUBLIC', label: '公开', desc: '所有用户可查看' },
  { value: 'CHAT_FRIENDS', label: '互聊好友', desc: '仅有双向聊天记录的用户可查看' },
  { value: 'FAMILY', label: '家庭成员', desc: '仅同一家庭的成员可查看' },
  { value: 'PRIVATE', label: '仅自己', desc: '其他用户不可查看' }
]

async function load() {
  loading.value = true; error.value = ''
  try {
    const data = await getMyProfileVisibility()
    visibility.value = data.profileVisibility || data.visibility || 'PUBLIC'
  } catch (e) { error.value = e.message }
  finally { loading.value = false }
}

async function save() {
  saved.value = false
  try { await updateMyProfileVisibility(visibility.value); saved.value = true }
  catch (e) { notifyError(e.message) }
}

onMounted(load)
</script>

<style scoped>
.sub-page { max-width: 720px; }
.back-header { flex-wrap: nowrap; justify-content: flex-start; }
.icon-btn { width: 40px; height: 40px; flex: 0 0 auto; display: inline-flex; align-items: center; justify-content: center; background: transparent; border-radius: var(--radius); }
.settings-card { overflow: hidden; background: #fff; border: 1px solid var(--color-line-light); border-radius: var(--radius-lg); }
.settings-copy { padding: 16px; border-bottom: 1px solid var(--color-line); }
.settings-copy strong { font-size: 15px; }
.settings-copy p { margin-top: 3px; font-size: 13px; color: var(--color-text-muted); }
.option-row { min-height: 66px; display: flex; align-items: center; justify-content: space-between; padding: 10px 16px; cursor: pointer; }
.option-row + .option-row { border-top: 1px solid var(--color-line); }
.option-row.selected { background: #fffaf4; }
.option-row span { display: flex; flex-direction: column; }
.option-row strong { font-size: 14px; }
.option-row small { margin-top: 2px; color: var(--color-text-muted); }
.option-row input { width: 20px; height: 20px; accent-color: var(--color-primary); }
.save-ok { padding: 10px 16px; color: var(--color-success); font-size: 13px; font-weight: 600; border-top: 1px solid var(--color-line); }
@media (max-width: 768px) { .sub-page { padding-top: max(10px, env(safe-area-inset-top)); } }
</style>
