<template>
  <div class="login-page">
    <div class="card login-card">
      <nav class="auth-tabs"><router-link to="/customer/login" class="active">登录</router-link><router-link to="/customer/register">注册</router-link></nav>
      <div class="login-header">
        <img class="login-icon" src="/app_icon.png" alt="" />
        <h1>欢迎回来</h1>
        <p>用昵称和密码继续点餐</p>
      </div>
      <form @submit.prevent="handleLogin" class="login-form">
        <div class="form-group">
          <label class="form-label">账号</label>
          <input v-model="nickname" class="form-input" placeholder="请输入账号" required />
        </div>
        <div class="form-group">
          <label class="form-label">密码</label>
          <input v-model="password" type="password" class="form-input" placeholder="请输入密码" required />
        </div>
        <button type="submit" class="btn btn-primary btn-block btn-lg" :disabled="loading">
          {{ loading ? '登录中...' : '登录' }}
        </button>
        <p class="login-hint">
          还没有账号？<router-link to="/customer/register">立即注册</router-link>
        </p>
      </form>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '../../stores/auth'
import { notifyError } from '../../services/notice'

const router = useRouter()
const route = useRoute()
const auth = useAuthStore()

const nickname = ref('')
const password = ref('')
const loading = ref(false)

async function handleLogin() {
  loading.value = true
  try {
    await auth.loginAction(nickname.value.trim(), password.value, 'CUSTOMER')
    router.push(typeof route.query.redirect === 'string' ? route.query.redirect : '/customer/chat')
  } catch (e) {
    notifyError(e.message)
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-page { min-height: 100dvh; display: flex; align-items: center; justify-content: center; padding: 24px; background: #fff; }
.login-card {
  width: 100%; max-width: 420px; padding: 36px 28px;
  border-radius: var(--radius-lg); box-shadow: none; border: 1px solid var(--color-line-light);
}
.auth-tabs { display: grid; grid-template-columns: 1fr 1fr; gap: 2px; height: 44px; padding: 3px; margin-bottom: 28px; background: var(--color-primary-bg); border-radius: var(--radius); }
.auth-tabs a { display: flex; align-items: center; justify-content: center; color: var(--color-text-muted); border-radius: 6px; font-weight: 600; }
.auth-tabs a.active { color: #fff; background: var(--color-primary); }
.login-header { text-align: left; margin-bottom: 28px; }
.login-icon { width: 64px; height: 64px; border-radius: var(--radius); margin-bottom: 22px; box-shadow: 0 8px 18px rgba(242,140,40,.18); }
.login-header h1 { font-size: 24px; font-weight: 800; color: var(--color-text); }
.login-header p { font-size: 14px; color: var(--color-text-muted); margin-top: 6px; }
.login-form { display: flex; flex-direction: column; gap: 16px; }
.login-hint { font-size: 14px; color: var(--color-text-muted); text-align: center; margin-top: 8px; }
.login-hint a { font-weight: 600; }
@media (max-width: 768px) { .login-page { min-height: 100dvh; align-items: flex-start; padding: max(16px, env(safe-area-inset-top)) 20px 24px; } .login-card { padding: 12px 0 28px; border: 0; } }
</style>
