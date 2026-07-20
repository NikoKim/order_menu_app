<template>
  <div class="page-container edit-page">
    <div class="page-header back-header">
      <button class="icon-btn" title="返回" @click="router.back()"><ArrowLeft :size="22" /></button>
      <h1 class="page-title">修改个人信息</h1>
    </div>
    <LoadingSpinner v-if="loading" />
    <form v-else class="card edit-form" @submit.prevent="handleSave">
      <div class="form-group">
        <label class="form-label">昵称</label>
        <input v-model="form.nickname" class="form-input" maxlength="50" />
      </div>
      <div class="form-group">
        <label class="form-label">电话</label>
        <input v-model="form.phone" class="form-input" maxlength="30" inputmode="tel" />
      </div>
      <div class="form-group">
        <label class="form-label">头像</label>
        <ImageUploader v-model="form.avatarUrl" placeholder="上传头像" />
      </div>
      <p v-if="saveError" class="form-error">{{ saveError }}</p>
      <p v-if="saveOk" class="save-ok">保存成功</p>
      <button type="submit" class="btn btn-primary btn-block" :disabled="saving">{{ saving ? '保存中...' : '保存' }}</button>
    </form>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ArrowLeft } from 'lucide-vue-next'
import { useAuthStore } from '../../stores/auth'
import { getCurrentUser, updateCurrentUser } from '../../api/auth'
import LoadingSpinner from '../../components/LoadingSpinner.vue'
import ImageUploader from '../../components/ImageUploader.vue'

const router = useRouter()
const auth = useAuthStore()
const loading = ref(true)
const saving = ref(false)
const saveError = ref('')
const saveOk = ref(false)
const form = ref({ nickname: '', phone: '', avatarUrl: '' })

async function loadUser() {
  try {
    const user = await getCurrentUser()
    form.value = { nickname: user.nickname || '', phone: user.phone || '', avatarUrl: user.avatarUrl || '' }
  } finally { loading.value = false }
}

async function handleSave() {
  saveError.value = ''; saveOk.value = false; saving.value = true
  try { await updateCurrentUser(form.value); await auth.fetchMe(); saveOk.value = true }
  catch (e) { saveError.value = e.message }
  finally { saving.value = false }
}

onMounted(loadUser)
</script>

<style scoped>
.edit-page { max-width: 720px; }
.back-header { justify-content: flex-start; flex-wrap: nowrap; }
.icon-btn { width: 40px; height: 40px; flex: 0 0 auto; display: inline-flex; align-items: center; justify-content: center; background: transparent; color: var(--color-text); border-radius: var(--radius); }
.icon-btn:hover { background: var(--color-line); }
.edit-form { display: flex; flex-direction: column; gap: 16px; }
.save-ok { color: var(--color-success); font-weight: 600; }
@media (max-width: 768px) { .edit-page { padding-top: max(12px, env(safe-area-inset-top)); } }
</style>
