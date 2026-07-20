<template>
  <div class="page-container">
    <div class="page-header">
      <button class="back-btn" @click="$router.back()"><ArrowLeft :size="20" /></button>
      <h1 class="page-title">用户管理</h1>
      <button class="btn btn-primary" @click="openCreate"><Plus :size="16" />添加用户</button>
    </div>

    <div class="filters">
      <select v-model="filterRole" class="form-select" style="width:auto" @change="loadUsers">
        <option value="">全部角色</option>
        <option value="CUSTOMER">顾客</option>
        <option value="SHOP">商家</option>
        <option value="ADMIN">管理员</option>
      </select>
    </div>

    <LoadingSpinner v-if="loading" />
    <div v-else-if="error" class="error-wrap"><EmptyState icon="warning" :title="error" action-text="重试" @action="loadUsers" /></div>
    <template v-else>
      <div class="table-wrapper card" v-if="users.length">
        <table>
          <thead>
            <tr>
              <th>用户</th>
              <th>角色</th>
              <th class="hide-mobile">电话</th>
              <th>状态</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="u in users" :key="u.id">
              <td>
                <div class="user-cell">
                  <div class="user-avatar">
                    <img v-if="u.avatarUrl" :src="imageUrl(u.avatarUrl)" alt="" />
                    <span v-else>{{ u.nickname?.charAt(0) || '?' }}</span>
                  </div>
                  <span class="user-name">{{ u.nickname }}</span>
                </div>
              </td>
              <td><span class="badge" :class="roleBadgeClass(u.role)">{{ roleLabel(u.role) }}</span></td>
              <td class="hide-mobile">{{ u.phone || '-' }}</td>
              <td><StatusBadge :status="u.enabled ? 'ENABLED' : 'DISABLED'" type="user" /></td>
              <td>
                <button class="btn btn-ghost btn-sm" :style="{ color: u.enabled ? 'var(--color-error)' : 'var(--color-success)' }"
                  @click="handleToggle(u)">
                  {{ u.enabled ? '禁用' : '启用' }}
                </button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
      <EmptyState v-else title="暂无用户" />
      <Pagination :page="page" :page-size="pageSize" :total="total" @change="p => { page = p; loadUsers() }" />
    </template>

    <Teleport to="body">
      <div v-if="showCreate" class="modal-overlay" @click.self="showCreate = false">
        <div class="card modal-card">
          <h2 style="margin-bottom:20px;font-size:18px;font-weight:700">添加用户</h2>
          <form @submit.prevent="handleCreate">
            <div class="form-group" style="margin-bottom:12px">
              <label class="form-label">昵称</label>
              <input v-model="form.nickname" class="form-input" required maxlength="50" />
            </div>
            <div class="form-group" style="margin-bottom:12px">
              <label class="form-label">密码</label>
              <input v-model="form.password" type="password" class="form-input" required minlength="6" />
            </div>
            <div class="form-group" style="margin-bottom:12px">
              <label class="form-label">角色</label>
              <select v-model="form.role" class="form-select">
                <option value="CUSTOMER">顾客</option>
                <option value="SHOP">商家</option>
                <option value="ADMIN">管理员</option>
              </select>
            </div>
            <div class="form-group" style="margin-bottom:12px">
              <label class="form-label">电话</label>
              <input v-model="form.phone" class="form-input" maxlength="30" />
            </div>
            <p v-if="createError" class="form-error">{{ createError }}</p>
            <div style="display:flex;gap:8px;justify-content:flex-end;margin-top:16px">
              <button type="button" class="btn btn-ghost" @click="showCreate = false">取消</button>
              <button type="submit" class="btn btn-primary" :disabled="creating">{{ creating ? '创建中...' : '创建' }}</button>
            </div>
          </form>
        </div>
      </div>
    </Teleport>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ArrowLeft, Plus } from 'lucide-vue-next'
import { getUsers, createUser, updateUserStatus } from '../../api/admin'
import { imageUrl } from '../../composables/useImage'
import StatusBadge from '../../components/StatusBadge.vue'
import Pagination from '../../components/Pagination.vue'
import EmptyState from '../../components/EmptyState.vue'
import LoadingSpinner from '../../components/LoadingSpinner.vue'

const users = ref([])
const loading = ref(true)
const error = ref('')
const page = ref(1)
const pageSize = ref(20)
const total = ref(0)
const filterRole = ref('')

const showCreate = ref(false)
const creating = ref(false)
const createError = ref('')
const form = ref({ nickname: '', password: '', role: 'CUSTOMER', phone: '' })

function roleLabel(role) {
  const map = { CUSTOMER: '顾客', SHOP: '商家', ADMIN: '管理员' }
  return map[role] || role
}

function roleBadgeClass(role) {
  const map = { CUSTOMER: 'badge-green', SHOP: 'badge-orange', ADMIN: 'badge-red' }
  return map[role] || ''
}

async function loadUsers() {
  loading.value = true
  error.value = ''
  try {
    const res = await getUsers({ page: page.value, pageSize: pageSize.value, role: filterRole.value || undefined })
    users.value = res.records || []
    total.value = res.total || 0
  } catch (e) { error.value = e.message }
  finally { loading.value = false }
}

function openCreate() {
  form.value = { nickname: '', password: '', role: 'CUSTOMER', phone: '' }
  createError.value = ''
  showCreate.value = true
}

async function handleCreate() {
  createError.value = ''
  creating.value = true
  try {
    await createUser({ ...form.value })
    showCreate.value = false
    loadUsers()
  } catch (e) { createError.value = e.message }
  finally { creating.value = false }
}

async function handleToggle(u) {
  const newStatus = u.enabled ? 'DISABLED' : 'ENABLED'
  if (!confirm(`确认${u.enabled ? '禁用' : '启用'}该用户？`)) return
  try { await updateUserStatus(u.id, newStatus); loadUsers() } catch (e) { alert(e.message) }
}

onMounted(loadUsers)
</script>

<style scoped>
.page-header { display: flex; align-items: center; gap: 10px; margin-bottom: 16px; }
.page-header .page-title { flex: 1; }
.back-btn { width: 36px; height: 36px; display: inline-flex; align-items: center; justify-content: center; border-radius: 50%; background: transparent; color: var(--color-text); }
.filters { margin-bottom: 16px; }
.error-wrap { min-height: 30vh; display: flex; align-items: center; justify-content: center; }
.user-cell { display: flex; align-items: center; gap: 10px; }
.user-avatar {
  width: 36px; height: 36px; border-radius: 50%;
  background: var(--color-primary-bg); color: var(--color-primary);
  display: flex; align-items: center; justify-content: center;
  font-size: 14px; font-weight: 700; overflow: hidden; flex-shrink: 0;
}
.user-avatar img { width: 100%; height: 100%; object-fit: cover; }
.user-name { font-weight: 600; }

.modal-overlay {
  position: fixed; inset: 0; background: rgba(0,0,0,0.4);
  display: flex; align-items: center; justify-content: center; z-index: 200; padding: 24px;
}
.modal-card { width: 100%; max-width: 420px; padding: 24px; }

@media (max-width: 768px) {
  table, tbody { display: block; }
  thead { display: none; }
  tr { display: grid; grid-template-columns: minmax(0, 1fr) auto; gap: 8px; padding: 12px; background: #fff; border: 1px solid var(--color-line-light); border-radius: var(--radius); }
  tr + tr { margin-top: 10px; }
  tr:hover td { background: transparent; }
  td { display: flex; align-items: center; border: 0; padding: 0; font-size: 13px; }
  td:first-child { grid-row: span 2; }
  td:nth-child(4) { justify-content: flex-end; }
  td:nth-child(5) { grid-column: 1 / -1; padding-top: 10px; margin-top: 2px; border-top: 1px solid var(--color-line); justify-content: flex-end; }
}
</style>
