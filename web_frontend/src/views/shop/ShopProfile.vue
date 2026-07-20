<template>
  <div class="page-container">
    <div class="page-header"><h1 class="page-title">店铺</h1></div>

    <LoadingSpinner v-if="loading" />
    <div v-else-if="error" class="error-wrap"><EmptyState icon="warning" :title="error" action-text="重试" @action="loadShop" /></div>
    <template v-else>
    <nav class="shop-menu" aria-label="店铺功能">
      <router-link to="/shop/chat" class="shop-menu-row"><MessageCircle :size="20" /><span><strong>聊天</strong><small>查看顾客和家庭会话</small></span><ChevronRight :size="18" /></router-link>
      <router-link to="/shop/notifications" class="shop-menu-row"><Bell :size="20" /><span><strong>消息通知</strong><small>查看订单、家庭和留言提醒</small></span><ChevronRight :size="18" /></router-link>
      <router-link to="/shop/privacy" class="shop-menu-row"><ShieldCheck :size="20" /><span><strong>隐私设置</strong><small>管理聊天和个人信息隐私</small></span><ChevronRight :size="18" /></router-link>
      <router-link to="/shop/family" class="shop-menu-row"><House :size="20" /><span><strong>家庭管理</strong><small>管理家庭成员与加入申请</small></span><ChevronRight :size="18" /></router-link>
      <router-link to="/shop/feedback" class="shop-menu-row"><MessageSquareText :size="20" /><span><strong>顾客反馈</strong><small>查看并回复顾客留言</small></span><ChevronRight :size="18" /></router-link>
    </nav>

    <form class="card settings-form" @submit.prevent="handleSave">
      <div class="section-title">基本信息</div>
      <div class="form-grid">
        <div class="form-group">
          <label class="form-label">店铺名称 *</label>
          <input v-model="form.name" class="form-input" maxlength="80" required />
        </div>
        <div class="form-group">
          <label class="form-label">营业状态</label>
          <select v-model="form.businessStatus" class="form-select">
            <option value="OPEN">营业中</option>
            <option value="CLOSED">休息中</option>
          </select>
        </div>
        <div class="form-group">
          <label class="form-label">可见范围</label>
          <select v-model="form.visibilityType" class="form-select">
            <option value="PUBLIC">公开</option>
            <option value="FAMILY">仅家庭</option>
          </select>
        </div>
        <div class="form-group">
          <label class="form-label">电话</label>
          <input v-model="form.phone" class="form-input" maxlength="30" />
        </div>
        <div class="form-group">
          <label class="form-label">地址</label>
          <input v-model="form.address" class="form-input" maxlength="255" />
        </div>
      </div>
      <div class="form-group" style="margin-top:16px">
        <label class="form-label">描述</label>
        <textarea v-model="form.description" class="form-textarea" maxlength="500" placeholder="店铺描述..."></textarea>
      </div>
      <div class="form-group" style="margin-top:16px">
        <label class="form-label">店铺封面</label>
        <ImageUploader v-model="form.coverImageUrl" placeholder="上传封面" />
      </div>

      <div class="section-title" style="margin-top:24px">家庭设置</div>
      <div class="form-grid">
        <div class="form-group">
          <label class="form-label">家庭名称</label>
          <input v-model="form.familyName" class="form-input" maxlength="80" />
        </div>
        <div class="form-group">
          <label class="form-label">家庭加入模式</label>
          <input v-model="form.familyJoinMode" class="form-input" placeholder="例如：APPROVAL" />
        </div>
        <div class="form-group">
          <label class="form-label">家庭图片</label>
          <ImageUploader v-model="form.familyImageUrl" placeholder="上传图片" />
        </div>
      </div>

      <p v-if="saveError" class="form-error" style="margin-top:12px">{{ saveError }}</p>
      <p v-if="saveSuccess" style="color:var(--color-success);margin-top:12px;font-weight:600">保存成功</p>

      <button type="submit" class="btn btn-primary btn-lg" :disabled="saving" style="margin-top:24px">
        {{ saving ? '保存中...' : '保存设置' }}
      </button>
    </form>
    <button class="btn btn-outline btn-block logout-action" @click="handleLogout"><LogOut :size="18" />退出登录</button>
    <AppFooter />
    </template>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { Bell, ChevronRight, House, LogOut, MessageCircle, MessageSquareText, ShieldCheck } from 'lucide-vue-next'
import { useAuthStore } from '../../stores/auth'
import { getMyShop, updateMyShop } from '../../api/shop'
import LoadingSpinner from '../../components/LoadingSpinner.vue'
import EmptyState from '../../components/EmptyState.vue'
import ImageUploader from '../../components/ImageUploader.vue'
import AppFooter from '../../components/AppFooter.vue'

const form = ref({
  name: '', businessStatus: 'OPEN', visibilityType: 'PUBLIC',
  phone: '', address: '', description: '', coverImageUrl: '',
  familyName: '', familyJoinMode: '', familyImageUrl: ''
})
const router = useRouter()
const auth = useAuthStore()
const loading = ref(true)
const saving = ref(false)
const error = ref('')
const saveError = ref('')
const saveSuccess = ref(false)

async function loadShop() {
  loading.value = true
  error.value = ''
  try {
    const s = await getMyShop()
    form.value = {
      name: s.name || '',
      businessStatus: s.businessStatus || 'OPEN',
      visibilityType: s.visibilityType || 'PUBLIC',
      phone: s.phone || '',
      address: s.address || '',
      description: s.description || '',
      coverImageUrl: s.coverImageUrl || '',
      familyName: s.familyName || '',
      familyJoinMode: s.familyJoinMode || '',
      familyImageUrl: s.familyImageUrl || ''
    }
  } catch (e) { error.value = e.message }
  finally { loading.value = false }
}

async function handleSave() {
  saveError.value = ''
  saveSuccess.value = false
  saving.value = true
  try {
    await updateMyShop(form.value)
    saveSuccess.value = true
  } catch (e) { saveError.value = e.message }
  finally { saving.value = false }
}

function handleLogout() {
  auth.logout()
  router.push('/shop/login')
}

onMounted(loadShop)
</script>

<style scoped>
.error-wrap { min-height: 30vh; display: flex; align-items: center; justify-content: center; }
.form-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(200px, 1fr)); gap: 14px; }
.section-title { font-size: 16px; font-weight: 700; color: var(--color-text); padding-bottom: 8px; border-bottom: 1px solid var(--color-line); }
.shop-menu { display: grid; grid-template-columns: 1fr 1fr; gap: 10px; margin-bottom: 16px; }
.shop-menu-row { min-height: 72px; padding: 12px 14px; display: flex; align-items: center; gap: 12px; background: #fff; color: var(--color-primary); border: 1px solid var(--color-line-light); border-radius: var(--radius); }
.shop-menu-row > span { flex: 1; display: flex; flex-direction: column; color: var(--color-text); }
.shop-menu-row strong { font-size: 14px; }
.shop-menu-row small { margin-top: 2px; color: var(--color-text-muted); }
.shop-menu-row > svg:last-child { color: var(--color-text-light); }
.settings-form { margin-top: 0; }
.settings-form > .section-title:first-child { margin-bottom: 16px; }
.logout-action { margin-top: 16px; color: var(--color-error); border-color: var(--color-error); gap: 7px; }

@media (max-width: 768px) {
  .page-container { padding-top: max(16px, env(safe-area-inset-top)); }
  .shop-menu { display: block; overflow: hidden; border: 1px solid var(--color-line-light); border-radius: var(--radius-lg); }
  .shop-menu-row { border: 0; border-radius: 0; }
  .shop-menu-row + .shop-menu-row { border-top: 1px solid var(--color-line); }
  .settings-form { margin-top: 14px; }
  .form-grid { grid-template-columns: 1fr; }
}
</style>
