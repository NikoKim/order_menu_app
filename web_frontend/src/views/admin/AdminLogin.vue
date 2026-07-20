<template>
  <div class="login-page">
    <div class="card login-card">
      <div class="login-header">
        <img class="login-icon" src="/app_icon.png" alt="" />
        <h1>管理员登录</h1>
        <p>使用管理员账号登录系统后台</p>
      </div>
      <form @submit.prevent="handleLogin" class="login-form">
        <div class="form-group">
          <label class="form-label">管理员账号</label>
          <input v-model="nickname" class="form-input" placeholder="请输入账号" required />
        </div>
        <div class="form-group">
          <label class="form-label">密码</label>
          <input v-model="password" type="password" class="form-input" placeholder="请输入密码" required />
        </div>
        <p v-if="error" class="form-error" style="text-align: center;">{{ error }}</p>
        <button type="submit" class="btn btn-primary btn-block btn-lg" :disabled="loading">
          {{ loading ? '登录中...' : '登录' }}
        </button>
      </form>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '../../stores/auth'

const router = useRouter()
const auth = useAuthStore()

const nickname = ref('')
const password = ref('')
const loading = ref(false)
const error = ref('')

async function handleLogin() {
  error.value = ''
  loading.value = true
  try {
    await auth.loginAction(nickname.value.trim(), password.value, 'ADMIN')
    router.push('/admin/users')
  } catch (e) {
    error.value = e.message
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-page { min-height: 100dvh; display: flex; align-items: center; justify-content: center; background: #fff; padding: 24px; }
.login-card {
  width: 100%; max-width: 420px; padding: 36px 28px;
  border-radius: var(--radius-lg); box-shadow: none; border: 1px solid var(--color-line-light);
}
.login-header { text-align: left; margin-bottom: 28px; }
.login-icon { width: 56px; height: 56px; border-radius: var(--radius); margin-bottom: 20px; box-shadow: 0 8px 18px rgba(242,140,40,.18); }
.login-header h1 { font-size: 24px; font-weight: 800; color: var(--color-text); }
.login-header p { font-size: 14px; color: var(--color-text-muted); margin-top: 6px; }
.login-form { display: flex; flex-direction: column; gap: 16px; }
@media (max-width: 768px) { .login-page { align-items: flex-start; padding: max(24px, env(safe-area-inset-top)) 24px; } .login-card { padding: 12px 0 28px; border: 0; } .login-header h1 { font-size: 28px; } }
</style>
