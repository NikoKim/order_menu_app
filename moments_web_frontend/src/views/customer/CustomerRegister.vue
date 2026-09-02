<template>
  <div class="login-page">
    <div class="card login-card">
      <nav class="auth-tabs"><router-link to="/customer/login">登录</router-link><router-link to="/customer/register" class="active">注册</router-link></nav>
      <div class="login-header">
        <img class="login-icon" src="/app_icon.png" alt="" />
        <h1>创建账号</h1>
        <p>注册后即可使用聊天与动态</p>
      </div>
      <form @submit.prevent="handleRegister" class="login-form">
        <div class="form-group">
          <label class="form-label">昵称</label>
          <input v-model="nickname" class="form-input" placeholder="输入昵称" maxlength="50" required />
        </div>
        <div class="form-group">
          <label class="form-label">密码</label>
          <input v-model="password" type="password" class="form-input" placeholder="输入密码（6-72位）" minlength="6" required />
        </div>
        <div class="form-group">
          <label class="form-label">确认密码</label>
          <input v-model="confirmPassword" type="password" class="form-input" placeholder="再次输入密码" minlength="6" required />
        </div>
        <p v-if="successMsg" style="color:var(--color-success);text-align:center;font-weight:600">{{ successMsg }}</p>
        <button type="submit" class="btn btn-primary btn-block btn-lg" :disabled="loading || !!successMsg">
          {{ loading ? '注册中...' : '注册' }}
        </button>
        <p class="login-hint">
          已有账号？<router-link to="/customer/login">去登录</router-link>
        </p>
      </form>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { customerRegister } from '../../api/auth'
import { notifyError } from '../../services/notice'

const router = useRouter()
const nickname = ref('')
const password = ref('')
const confirmPassword = ref('')
const loading = ref(false)
const successMsg = ref('')

async function handleRegister() {
  successMsg.value = ''
  loading.value = true
  if (password.value !== confirmPassword.value) { notifyError('两次密码不一致'); loading.value = false; return }
  try {
    await customerRegister(nickname.value.trim(), password.value)
    successMsg.value = '注册成功！3秒后跳转到登录页面...'
    setTimeout(() => router.push('/customer/login'), 3000)
  } catch (e) {
    notifyError(e.message)
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-page { min-height: calc(100dvh - 56px); display: flex; align-items: center; justify-content: center; padding: 24px; background: #fff; }
.login-card { width: 100%; max-width: 420px; padding: 32px 28px; box-shadow: none; border: 1px solid var(--color-line-light); }
.auth-tabs { display: grid; grid-template-columns: 1fr 1fr; gap: 2px; height: 44px; padding: 3px; margin-bottom: 28px; background: var(--color-primary-bg); border-radius: var(--radius); }
.auth-tabs a { display: flex; align-items: center; justify-content: center; color: var(--color-text-muted); border-radius: 6px; font-weight: 600; }
.auth-tabs a.active { color: #fff; background: var(--color-primary); }
.login-header { text-align: left; margin-bottom: 28px; }
.login-icon { width: 64px; height: 64px; border-radius: var(--radius); margin-bottom: 22px; box-shadow: 0 8px 18px rgba(242,140,40,.18); }
.login-header h1 { font-size: 24px; font-weight: 800; }
.login-header p { font-size: 13px; color: var(--color-text-muted); margin-top: 6px; }
.login-form { display: flex; flex-direction: column; gap: 16px; }
.login-hint { font-size: 13px; color: var(--color-text-muted); text-align: center; margin-top: 4px; }
@media (max-width: 768px) { .login-page { min-height: 100dvh; align-items: flex-start; padding: max(16px, env(safe-area-inset-top)) 20px 24px; } .login-card { padding: 12px 0 28px; border: 0; } }
</style>
