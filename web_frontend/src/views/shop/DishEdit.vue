<template>
  <div class="page-container">
    <div class="page-header">
      <button class="back-btn" @click="$router.back()"><ArrowLeft :size="20" /></button>
      <h1 class="page-title">{{ isEdit ? '编辑菜品' : '新增菜品' }}</h1>
    </div>

    <LoadingSpinner v-if="loadingDish" />
    <form v-else class="card" @submit.prevent="handleSubmit">
      <div class="form-grid">
        <div class="form-group">
          <label class="form-label">菜品名称 *</label>
          <input v-model="form.name" class="form-input" maxlength="80" required placeholder="输入菜品名称" />
        </div>
        <div class="form-group">
          <label class="form-label">所属分类</label>
          <select v-model="form.categoryId" class="form-select">
            <option :value="null">无分类</option>
            <option v-for="c in categories" :key="c.id" :value="c.id">{{ c.name }}</option>
          </select>
        </div>
        <div class="form-group">
          <label class="form-label">价格 *</label>
          <input v-model.number="form.price" type="number" class="form-input" min="0" step="0.01" required placeholder="0.00" />
        </div>
        <div class="form-group">
          <label class="form-label">库存</label>
          <input v-model.number="form.stock" type="number" class="form-input" min="0" placeholder="0" />
        </div>
        <div class="form-group">
          <label class="form-label">状态</label>
          <select v-model="form.status" class="form-select">
            <option value="ON_SALE">在售</option>
            <option value="OFF_SALE">下架</option>
          </select>
        </div>
      </div>
      <div class="form-group" style="margin-top:16px">
        <label class="form-label">描述</label>
        <textarea v-model="form.description" class="form-textarea" maxlength="500" placeholder="菜品描述..."></textarea>
      </div>
      <div class="form-group" style="margin-top:16px">
        <label class="form-label">菜品图片</label>
        <ImageUploader v-model="form.imageUrl" placeholder="上传图片" />
      </div>

      <!-- Option Groups -->
      <div style="margin-top: 20px">
        <div class="section-title">规格组</div>
        <div v-for="(group, gi) in form.optionGroups" :key="gi" class="card option-group-card">
          <div class="og-header">
            <div class="form-group" style="flex:1">
              <input v-model="group.name" class="form-input" placeholder="规格组名称" maxlength="50" />
            </div>
            <label class="og-checkbox">
              <input type="checkbox" v-model="group.required" /> 必选
            </label>
            <select v-model="group.selectionType" class="form-select" style="width:auto">
              <option value="SINGLE">单选</option>
              <option value="MULTIPLE">多选</option>
            </select>
            <button type="button" class="btn btn-ghost btn-sm" style="color:var(--color-error)"
              @click="form.optionGroups.splice(gi, 1)">删除</button>
          </div>
          <div v-for="(opt, oi) in group.options" :key="oi" class="og-option">
            <input v-model="opt.name" class="form-input" placeholder="选项名" style="flex:1" maxlength="50" />
            <span class="og-price-label">加价</span>
            <input v-model.number="opt.priceDelta" type="number" class="form-input" style="width:80px" min="0" step="0.01" />
            <button type="button" class="btn btn-ghost btn-sm" style="color:var(--color-error)"
              @click="group.options.splice(oi, 1)">&times;</button>
          </div>
          <button type="button" class="btn btn-ghost btn-sm" style="margin-top:8px"
            @click="group.options.push({ id: null, name: '', priceDelta: 0, sortOrder: group.options.length })">+ 添加选项</button>
        </div>
        <button type="button" class="btn btn-outline btn-sm" style="margin-top:8px"
          @click="form.optionGroups.push({ id: null, name: '', required: false, selectionType: 'SINGLE', sortOrder: form.optionGroups.length, options: [] })">+ 添加规格组</button>
      </div>

      <p v-if="submitError" class="form-error" style="margin-top:12px">{{ submitError }}</p>

      <div style="margin-top:24px; display:flex; gap:12px">
        <button type="submit" class="btn btn-primary btn-lg" :disabled="submitting">
          {{ submitting ? '保存中...' : '保存' }}
        </button>
        <router-link to="/shop/dishes" class="btn btn-ghost btn-lg">取消</router-link>
      </div>
    </form>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowLeft } from 'lucide-vue-next'
import { getDishes, getCategories, createDish, updateDish } from '../../api/shop'
import { getMyShop } from '../../api/shop'
import { uploadMenuImage } from '../../api/upload'
import LoadingSpinner from '../../components/LoadingSpinner.vue'
import ImageUploader from '../../components/ImageUploader.vue'

const route = useRoute()
const router = useRouter()
const isEdit = !!route.params.id
const dishId = route.params.id

const categories = ref([])
const loadingDish = ref(false)
const submitting = ref(false)
const submitError = ref('')
const shopId = ref(null)

const form = ref({
  name: '', categoryId: null, price: 0, stock: null, status: 'ON_SALE',
  description: '', imageUrl: '', optionGroups: []
})

async function loadData() {
  try {
    const [cats, shop] = await Promise.all([getCategories(), getMyShop()])
    categories.value = cats
    shopId.value = shop.id
  } catch { }

  if (isEdit) {
    loadingDish.value = true
    try {
      const res = await getDishes({ page: 1, pageSize: 100 })
      const dish = (res.records || []).find(d => d.id === Number(dishId))
      if (dish) {
        form.value = {
          name: dish.name,
          categoryId: dish.categoryId,
          price: dish.price,
          stock: dish.stock,
          status: dish.status,
          description: dish.description || '',
          imageUrl: dish.imageUrl || '',
          optionGroups: (dish.optionGroups || []).map(g => ({
            id: g.id,
            name: g.name,
            required: g.required,
            selectionType: g.selectionType || 'SINGLE',
            sortOrder: g.sortOrder,
            options: (g.options || []).map(o => ({
              id: o.id, name: o.name, priceDelta: o.priceDelta, sortOrder: o.sortOrder
            }))
          }))
        }
      }
    } catch (e) {
      submitError.value = e.message
    } finally {
      loadingDish.value = false
    }
  }
}

async function handleSubmit() {
  submitError.value = ''
  submitting.value = true
  try {
    const payload = {
      categoryId: form.value.categoryId,
      name: form.value.name.trim(),
      price: form.value.price,
      stock: form.value.stock,
      status: form.value.status,
      description: form.value.description || null,
      imageUrl: form.value.imageUrl || null,
      optionGroups: form.value.optionGroups.map(g => ({
        id: g.id,
        name: g.name,
        required: g.required,
        selectionType: g.selectionType,
        sortOrder: g.sortOrder,
        options: g.options.map(o => ({
          id: o.id,
          name: o.name,
          priceDelta: o.priceDelta,
          sortOrder: o.sortOrder
        }))
      }))
    }
    if (isEdit) {
      await updateDish(dishId, payload)
    } else {
      await createDish(payload)
    }
    router.push('/shop/dishes')
  } catch (e) {
    submitError.value = e.message
  } finally {
    submitting.value = false
  }
}

onMounted(loadData)
</script>

<style scoped>
.page-header { display: flex; align-items: center; gap: 10px; margin-bottom: 16px; }
.back-btn { width: 36px; height: 36px; display: inline-flex; align-items: center; justify-content: center; border-radius: 50%; background: transparent; color: var(--color-text); }
.page-container { max-width: 720px; }
.form-grid {
  display: grid; grid-template-columns: repeat(auto-fill, minmax(200px, 1fr)); gap: 14px;
}
.section-title { font-size: 16px; font-weight: 700; margin-bottom: 8px; }
.option-group-card { margin-bottom: 12px; }
.og-header { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; margin-bottom: 8px; }
.og-checkbox { display: flex; align-items: center; gap: 4px; font-size: 13px; white-space: nowrap; }
.og-option { display: flex; align-items: center; gap: 8px; margin-top: 6px; }
.og-price-label { font-size: 13px; color: var(--color-text-muted); white-space: nowrap; }
</style>
