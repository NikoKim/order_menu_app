<template>
  <div class="page-container">
    <div class="page-header">
      <h1 class="page-title">菜品管理</h1>
      <router-link to="/shop/dishes/new" class="btn btn-primary"><Plus :size="18" />新增菜品</router-link>
    </div>

    <div class="filters">
      <button :class="{ active: !filterCategory && !filterStatus }" @click="setFilter('', '')">全部</button>
      <button :class="{ active: filterStatus === 'OFF_SALE' }" @click="setFilter('', 'OFF_SALE')">未上架</button>
      <button v-for="c in categories" :key="c.id" :class="{ active: filterCategory === c.id }" @click="setFilter(c.id, '')">{{ c.name }}</button>
      <input v-model="filterKeyword" class="form-input" style="width:200px" placeholder="搜索菜品..." @input="onSearch" />
    </div>

    <LoadingSpinner v-if="loading" />
    <div v-else-if="error" class="error-wrap">
      <EmptyState icon="warning" :title="error" action-text="重试" @action="loadDishes" />
    </div>
    <template v-else>
      <div class="table-wrapper card" v-if="dishes.length">
        <table>
          <thead>
            <tr>
              <th>菜品</th>
              <th>分类</th>
              <th>价格</th>
              <th>销量</th>
              <th>状态</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="dish in dishes" :key="dish.id">
              <td>
                <div class="dish-cell">
                  <img v-if="dish.imageUrl" :src="imageUrl(dish.imageUrl)" class="dish-thumb" />
                  <div v-else class="dish-thumb-placeholder"><Utensils :size="20" /></div>
                  <span class="dish-name">{{ dish.name }}</span>
                </div>
              </td>
              <td>{{ getCategoryName(dish.categoryId) }}</td>
              <td class="price-cell">{{ dish.price }}</td>
              <td>{{ dish.salesCount || 0 }}</td>
              <td><StatusBadge :status="dish.status" type="dish" /></td>
              <td>
                <div class="action-btns">
                  <button class="btn btn-outline btn-sm" @click="toggleStatus(dish)">
                    {{ dish.status === 'ON_SALE' ? '下架' : '上架' }}
                  </button>
                  <button class="btn btn-ghost btn-sm" @click="router.push(`/shop/dishes/${dish.id}/edit`)">编辑</button>
                  <button class="btn btn-ghost btn-sm" style="color:var(--color-error)" @click="handleDelete(dish)">删除</button>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
      <EmptyState v-else icon="dish" title="暂无菜品" description="点击上方按钮新增菜品" />

      <Pagination :page="page" :page-size="pageSize" :total="total" @change="p => { page = p; loadDishes() }" />
    </template>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { Plus, Utensils } from 'lucide-vue-next'
import { getDishes, getCategories, updateDishStatus, deleteDish } from '../../api/shop'
import { imageUrl } from '../../composables/useImage'
import StatusBadge from '../../components/StatusBadge.vue'
import Pagination from '../../components/Pagination.vue'
import EmptyState from '../../components/EmptyState.vue'
import LoadingSpinner from '../../components/LoadingSpinner.vue'

const router = useRouter()
const dishes = ref([])
const categories = ref([])
const loading = ref(true)
const error = ref('')
const page = ref(1)
const pageSize = ref(20)
const total = ref(0)
const filterCategory = ref('')
const filterStatus = ref('')
const filterKeyword = ref('')
let searchTimer = null

function getCategoryName(id) {
  const c = categories.value.find(c => c.id === id)
  return c ? c.name : '--'
}

async function loadDishes() {
  loading.value = true
  error.value = ''
  try {
    const res = await getDishes({
      page: page.value, pageSize: pageSize.value,
      categoryId: filterCategory.value || undefined,
      status: filterStatus.value || undefined,
      keyword: filterKeyword.value || undefined
    })
    dishes.value = res.records || []
    total.value = res.total || 0
  } catch (e) {
    error.value = e.message
  } finally {
    loading.value = false
  }
}

async function loadCategories() {
  try {
    categories.value = await getCategories()
  } catch { }
}

function onSearch() {
  clearTimeout(searchTimer)
  searchTimer = setTimeout(loadDishes, 300)
}

function setFilter(category, status) {
  filterCategory.value = category
  filterStatus.value = status
  page.value = 1
  loadDishes()
}

async function toggleStatus(dish) {
  try {
    const newStatus = dish.status === 'ON_SALE' ? 'OFF_SALE' : 'ON_SALE'
    await updateDishStatus(dish.id, newStatus)
    dish.status = newStatus
  } catch (e) {
    alert(e.message)
  }
}

async function handleDelete(dish) {
  if (!confirm(`确认删除菜品「${dish.name}」？`)) return
  try {
    await deleteDish(dish.id)
    loadDishes()
  } catch (e) {
    alert(e.message)
  }
}

onMounted(() => {
  loadCategories()
  loadDishes()
})
</script>

<style scoped>
.filters { display: flex; align-items: center; gap: 8px; overflow-x: auto; margin-bottom: 16px; scrollbar-width: none; }
.filters::-webkit-scrollbar { display: none; }
.filters button { flex: 0 0 auto; min-height: 38px; padding: 0 14px; background: #fff; color: var(--color-text-muted); border: 1px solid var(--color-line); border-radius: var(--radius); font-size: 13px; }
.filters button.active { color: #fff; background: var(--color-primary); border-color: var(--color-primary); font-weight: 700; }
.dish-cell { display: flex; align-items: center; gap: 10px; }
.dish-thumb, .dish-thumb-placeholder {
  width: 44px; height: 44px; border-radius: var(--radius-sm);
  object-fit: cover; background: var(--color-bg);
  display: flex; align-items: center; justify-content: center; font-size: 20px;
}
.dish-name { font-weight: 600; }
.price-cell { color: var(--color-primary); font-weight: 600; }
.action-btns { display: flex; gap: 4px; }
.error-wrap { min-height: 30vh; display: flex; align-items: center; justify-content: center; }

@media (max-width: 768px) {
  .page-container { padding-top: max(14px, env(safe-area-inset-top)); }
  .filters { flex-wrap: nowrap; overflow-x: auto; padding-bottom: 2px; }
  .filters .form-input { min-width: 170px; }
  .table-wrapper { padding: 0; overflow: visible; background: transparent; border: 0; }
  table, tbody { display: block; }
  thead { display: none; }
  tr { display: grid; grid-template-columns: minmax(0, 1fr) auto; gap: 8px; padding: 12px; background: #fff; border: 1px solid var(--color-line-light); border-radius: var(--radius); }
  tr + tr { margin-top: 10px; }
  tr:hover td { background: transparent; }
  td { display: flex; align-items: center; border: 0; padding: 0; font-size: 13px; }
  td:first-child { grid-row: span 2; }
  td:nth-child(2), td:nth-child(4) { display: none; }
  td:nth-child(3) { justify-content: flex-end; font-size: 16px; }
  td:nth-child(5) { justify-content: flex-end; }
  td:nth-child(6) { grid-column: 1 / -1; padding-top: 10px; margin-top: 2px; border-top: 1px solid var(--color-line); }
  .dish-thumb, .dish-thumb-placeholder { width: 54px; height: 54px; }
  .dish-name { max-width: 34vw; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
  .action-btns { width: 100%; justify-content: flex-end; }
}
</style>
