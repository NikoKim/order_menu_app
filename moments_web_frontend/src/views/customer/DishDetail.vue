<template>
  <div class="dish-page">
    <LoadingSpinner v-if="loading" />
    <EmptyState v-else-if="error" icon="warning" :title="error" action-text="重试" @action="load" />
    <template v-else-if="dish">
      <div class="dish-hero"><img v-if="dish.imageUrl" :src="imageUrl(dish.imageUrl)" alt="" /><Utensils v-else :size="48" /></div>
      <main class="dish-content">
        <div class="dish-heading">
          <div><h1>{{ dish.name }}</h1><p>{{ shop?.name }}</p></div>
          <button v-if="isLoggedIn" class="icon-btn" title="分享菜品" @click="router.push(`/customer/dishes/${dish.id}/share`)"><Share2 :size="21" /></button>
        </div>
        <div class="price">¥{{ Number(dish.price).toFixed(2) }}</div>
        <p v-if="dish.description" class="description">{{ dish.description }}</p>
        <div v-if="dish.stock != null" class="stock">库存 {{ dish.stock }}</div>

        <section v-for="group in dish.optionGroups" :key="group.id" class="option-group">
          <h2>{{ group.name }} <small v-if="group.required">必选</small></h2>
          <div class="options">
            <button v-for="option in group.options" :key="option.id" :class="{ selected: isSelected(group, option) }" @click="toggleOption(group, option)">
              {{ option.name }}<span v-if="Number(option.priceDelta)">+¥{{ Number(option.priceDelta).toFixed(2) }}</span>
            </button>
          </div>
        </section>
      </main>
      <footer class="add-bar">
        <span class="total">¥{{ totalPrice.toFixed(2) }}</span>
        <div class="stepper" v-if="quantity"><button title="减少" @click="quantity--"><Minus :size="17" /></button><span>{{ quantity }}</span><button title="增加" @click="quantity++"><Plus :size="17" /></button></div>
        <button class="btn btn-primary" :disabled="!canAdd" @click="add">{{ quantity ? '加入购物车' : '添加菜品' }}</button>
      </footer>
    </template>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Minus, Plus, Share2, Utensils } from 'lucide-vue-next'
import { getDishDetail, getShopDetail } from '../../api/customer'
import { useCartStore } from '../../stores/cart'
import { imageUrl } from '../../composables/useImage'
import LoadingSpinner from '../../components/LoadingSpinner.vue'
import EmptyState from '../../components/EmptyState.vue'

const route = useRoute(), router = useRouter(), cart = useCartStore()
const dish = ref(null), shop = ref(null), loading = ref(true), error = ref(''), quantity = ref(1)
const selected = reactive({})
const isLoggedIn = Boolean(localStorage.getItem('token'))
const selectedList = computed(() => Object.values(selected).flat().filter(Boolean))
const totalPrice = computed(() => (Number(dish.value?.price || 0) + selectedList.value.reduce((sum, item) => sum + Number(item.priceDelta || 0), 0)) * quantity.value)
const canAdd = computed(() => quantity.value > 0 && (!dish.value?.stock || quantity.value <= dish.value.stock) && (dish.value?.optionGroups || []).every(g => !g.required || (selected[g.id]?.length || 0)))

async function load() {
  loading.value = true; error.value = ''
  try {
    dish.value = await getDishDetail(route.params.dishId)
    const shopId = route.params.shopId || dish.value.shopId
    shop.value = await getShopDetail(shopId)
    cart.initShop(Number(shopId))
    dish.value.optionGroups?.forEach(group => { selected[group.id] = group.options?.length ? [group.options[0]] : [] })
  } catch (e) { error.value = e.message } finally { loading.value = false }
}
function isSelected(group, option) { return selected[group.id]?.some(item => item.id === option.id) }
function toggleOption(group, option) {
  if (group.selectionType === 'SINGLE') selected[group.id] = [option]
  else if (isSelected(group, option)) selected[group.id] = selected[group.id].filter(item => item.id !== option.id)
  else selected[group.id] = [...(selected[group.id] || []), option]
}
function add() { cart.addItem(dish.value, quantity.value, selectedList.value); router.back() }
onMounted(load)
</script>

<style scoped>
.dish-page { min-height: 100%; padding-bottom: 84px; }.dish-hero { width: 100%; max-width: 720px; aspect-ratio: 16/9; margin: 0 auto; overflow: hidden; display: flex; align-items: center; justify-content: center; background: #eee; color: var(--color-text-muted); }.dish-hero img { width: 100%; height: 100%; object-fit: cover; }.dish-content { max-width: 720px; margin: 0 auto; padding: 18px 16px; }.dish-heading { display: flex; justify-content: space-between; gap: 12px; }.dish-heading h1 { font-size: 23px; }.dish-heading p,.description,.stock { color: var(--color-text-muted); font-size: 13px; }.icon-btn { width: 42px; height: 42px; display: inline-flex; align-items: center; justify-content: center; background: #fff; border: 1px solid var(--color-line); border-radius: 50%; color: var(--color-text-secondary); }.price { margin-top: 10px; color: var(--color-primary); font-size: 22px; font-weight: 800; }.description { margin-top: 8px; line-height: 1.6; }.stock { margin-top: 6px; }.option-group { margin-top: 22px; }.option-group h2 { font-size: 15px; }.option-group small { margin-left: 5px; color: var(--color-error); }.options { margin-top: 9px; display: flex; flex-wrap: wrap; gap: 8px; }.options button { min-height: 38px; padding: 7px 12px; background: #fff; border: 1px solid var(--color-line); border-radius: var(--radius); color: var(--color-text); }.options button.selected { color: var(--color-primary); border-color: var(--color-primary); background: var(--color-primary-bg); }.options span { margin-left: 5px; font-size: 11px; color: var(--color-text-muted); }.add-bar { position: fixed; z-index: 150; right: 0; bottom: 0; left: 0; max-width: 720px; min-height: 68px; margin: 0 auto; padding: 10px 16px max(10px, env(safe-area-inset-bottom)); display: flex; align-items: center; gap: 12px; background: #fff; border-top: 1px solid var(--color-line); }.total { flex: 1; color: var(--color-primary); font-size: 20px; font-weight: 800; }.stepper { display: flex; align-items: center; gap: 8px; }.stepper button { width: 32px; height: 32px; display: inline-flex; align-items: center; justify-content: center; border-radius: 50%; color: var(--color-primary); background: var(--color-primary-bg); }
</style>
