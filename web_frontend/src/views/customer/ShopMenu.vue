<template>
  <div class="page-container">
    <LoadingSpinner v-if="loading" />
    <div v-else-if="error" class="error-wrap">
      <EmptyState icon="warning" :title="error" action-text="重试" @action="loadMenu" />
    </div>
    <template v-else-if="menu">
      <div class="shop-header card">
        <div class="shop-hd-top">
          <div class="shop-hd-avatar">
            <img v-if="menu.shop?.coverImageUrl" :src="imageUrl(menu.shop.coverImageUrl)" alt="" />
            <Store v-else :size="28" />
          </div>
          <div class="shop-hd-info">
            <h1>{{ menu.shop?.name }}</h1>
            <p>{{ menu.shop?.description || '欢迎下单' }}</p>
            <span class="badge" :class="menu.shop?.businessStatus === 'OPEN' ? 'badge-green' : 'badge-gray'">
              {{ menu.shop?.businessStatus === 'OPEN' ? '营业中' : '休息中' }}
            </span>
          </div>
          <button v-if="isLoggedIn" class="shop-share-btn" title="分享店铺" @click="router.push(`/customer/shops/${shopId}/share`)"><Share2 :size="20" /></button>
        </div>
      </div>

      <div class="menu-layout">
        <aside class="category-sidebar">
          <button v-for="cat in menu.categories" :key="cat.id"
            class="cat-btn" :class="{ active: activeCat === cat.id }"
            @click="activeCat = cat.id">
            {{ cat.name }}
          </button>
        </aside>

        <div class="dish-area">
          <div v-for="cat in menu.categories" :key="cat.id" v-show="activeCat === cat.id" class="dish-group">
            <h3 class="cat-title">{{ cat.name }}</h3>
            <div class="dish-grid">
              <div v-for="dish in cat.dishes" :key="dish.id" class="card dish-card" @click="openDishDetail(dish)">
                <div class="dish-img">
                  <img v-if="dish.imageUrl" :src="imageUrl(dish.imageUrl)" alt="" />
                  <Utensils v-else :size="26" />
                </div>
                <div class="dish-info">
                  <div class="dish-name">{{ dish.name }}</div>
                  <div class="dish-desc" v-if="dish.description">{{ dish.description }}</div>
                  <div class="dish-bottom">
                    <span class="dish-price">¥{{ Number(dish.price).toFixed(2) }}</span>
                    <button class="add-btn" title="加入购物车" @click.stop="addToCart(dish)"><Plus :size="19" /></button>
                  </div>
                </div>
              </div>
            </div>
          </div>
          <EmptyState v-if="!menu.categories?.length" icon="dish" title="暂无菜品" />
        </div>
      </div>

      <!-- Dish Detail Modal -->
      <Teleport to="body">
        <div v-if="detailDish" class="modal-overlay" @click.self="detailDish = null">
          <div class="modal-content card">
            <div class="modal-img" v-if="detailDish.imageUrl">
              <img :src="imageUrl(detailDish.imageUrl)" alt="" />
            </div>
            <h3>{{ detailDish.name }}</h3>
            <p class="modal-desc" v-if="detailDish.description">{{ detailDish.description }}</p>
            <div class="modal-price">¥{{ Number(detailDish.price).toFixed(2) }}</div>
            <div v-if="detailDish.optionGroups?.length" class="modal-options">
              <div v-for="group in detailDish.optionGroups" :key="group.id" class="option-group">
                <div class="option-group-label">
                  {{ group.name }}
                  <span v-if="group.required" style="color:var(--color-error)">*必选</span>
                </div>
                <div class="option-items">
                  <label v-for="opt in group.options" :key="opt.id" class="option-item"
                    :class="{ selected: selectedOptions[group.id]?.id === opt.id }">
                    <input type="radio" v-if="group.selectionType === 'SINGLE'"
                      :name="'group-' + group.id"
                      :checked="selectedOptions[group.id]?.id === opt.id"
                      @change="selectedOptions[group.id] = opt" />
                    <input type="checkbox" v-else
                      :checked="selectedOptions[group.id]?.id === opt.id"
                      @change="selectedOptions[group.id] = selectedOptions[group.id]?.id === opt.id ? null : opt" />
                    <span>{{ opt.name }}</span>
                    <span v-if="opt.priceDelta > 0" class="opt-price">+{{ opt.priceDelta }}</span>
                  </label>
                </div>
              </div>
            </div>
            <div class="modal-actions">
              <button class="btn btn-ghost btn-lg" @click="detailDish = null">取消</button>
              <button class="btn btn-primary btn-lg" @click="addToCartFromDetail">加入购物车</button>
            </div>
          </div>
        </div>
      </Teleport>

      <!-- Cart bar -->
      <div class="cart-bar" v-if="cart.count > 0">
        <div class="cart-info" @click="showCart = !showCart">
          <span class="cart-count"><ShoppingCart :size="20" /><b>{{ cart.count }}</b></span>
          <span class="cart-total">¥{{ cart.totalAmount.toFixed(2) }}</span>
        </div>
        <button class="btn btn-primary" @click="router.push('/customer/checkout')">去结算</button>
      </div>

      <!-- Cart detail popup -->
      <Teleport to="body">
        <div v-if="showCart" class="modal-overlay" @click.self="showCart = false">
          <div class="cart-popup card">
            <h3>购物车</h3>
            <div v-for="item in cart.items" :key="item._key" class="cart-item">
              <div class="cart-item-info">
                <div>{{ item.name }}</div>
                <div class="cart-item-opts" v-if="item.selectedOptions?.length">
                  {{ item.selectedOptions.map(o => o.name).join('、') }}
                </div>
              </div>
              <div class="cart-item-qty">
                <button class="qty-btn" title="减少" @click="cart.updateQuantity(item._key, item.quantity - 1)"><Minus :size="16" /></button>
                <span>{{ item.quantity }}</span>
                <button class="qty-btn" title="增加" @click="cart.updateQuantity(item._key, item.quantity + 1)"><Plus :size="16" /></button>
              </div>
              <div class="cart-item-price">{{ ((item.price + (item.selectedOptions?.reduce((s, o) => s + o.priceDelta, 0) || 0)) * item.quantity).toFixed(2) }}</div>
            </div>
            <button class="btn btn-ghost btn-sm" @click="cart.clearCart()" style="margin-top:8px"><Trash2 :size="16" />清空</button>
          </div>
        </div>
      </Teleport>
    </template>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Minus, Plus, Share2, ShoppingCart, Store, Trash2, Utensils } from 'lucide-vue-next'
import { getShopMenu } from '../../api/customer'
import { useCartStore } from '../../stores/cart'
import { imageUrl } from '../../composables/useImage'
import LoadingSpinner from '../../components/LoadingSpinner.vue'
import EmptyState from '../../components/EmptyState.vue'

const route = useRoute()
const router = useRouter()
const cart = useCartStore()
const shopId = Number(route.params.id)
const isLoggedIn = Boolean(localStorage.getItem('token'))

const menu = ref(null)
const loading = ref(true)
const error = ref('')
const activeCat = ref(null)
const detailDish = ref(null)
const showCart = ref(false)
let selectedOptions = reactive({});


async function loadMenu() {
  loading.value = true
  error.value = ''
  try {
    menu.value = await getShopMenu(shopId)
    cart.initShop(shopId)
    if (menu.value.categories?.length) {
      activeCat.value = menu.value.categories[0].id
    }
  } catch (e) {
    error.value = e.message
  } finally { loading.value = false }
}

function openDishDetail(dish) {
  router.push(`/customer/shops/${shopId}/dishes/${dish.id}`)
}

function addToCart(dish) {
  if (dish.optionGroups?.length) {
    openDishDetail(dish)
    return
  }
  cart.addItem(dish)
}

function addToCartFromDetail() {
  cart.addItem(detailDish.value, 1, Object.values(selectedOptions).filter(Boolean))
  detailDish.value = null
}

onMounted(loadMenu)
</script>

<style scoped>
.error-wrap { min-height: 30vh; display: flex; align-items: center; justify-content: center; }
.shop-header { margin-bottom: 16px; }
.shop-hd-top { display: flex; gap: 12px; }
.shop-hd-avatar {
  width: 64px; height: 64px; border-radius: var(--radius);
  background: var(--color-bg); display: flex; align-items: center; justify-content: center;
  font-size: 28px; overflow: hidden; flex-shrink: 0;
}
.shop-hd-avatar img { width: 100%; height: 100%; object-fit: cover; }
.shop-hd-info h1 { font-size: 18px; font-weight: 800; }
.shop-hd-info p { font-size: 13px; color: var(--color-text-muted); margin: 2px 0 6px; }
.shop-share-btn { width: 40px; height: 40px; margin-left: auto; flex: 0 0 auto; display: inline-flex; align-items: center; justify-content: center; color: var(--color-text-muted); background: transparent; border-radius: 50%; }

.menu-layout { display: flex; gap: 12px; }
.category-sidebar {
  width: 90px; flex-shrink: 0;
  display: flex; flex-direction: column; gap: 4px;
  position: sticky; top: 64px; align-self: flex-start;
}
.cat-btn {
  padding: 10px 6px; border-radius: var(--radius);
  background: transparent; font-size: 13px; font-weight: 500;
  color: var(--color-text-muted); text-align: left;
  transition: all 0.2s;
}
.cat-btn:hover, .cat-btn.active {
  background: var(--color-primary-bg); color: var(--color-primary); font-weight: 700;
}

.dish-area { flex: 1; min-width: 0; }
.cat-title { font-size: 16px; font-weight: 700; margin-bottom: 10px; }
.dish-grid { display: flex; flex-direction: column; gap: 10px; }
.dish-card { display: flex; gap: 12px; cursor: pointer; padding: 12px; }
.dish-img {
  width: 80px; height: 80px; border-radius: var(--radius-sm);
  background: var(--color-bg); display: flex; align-items: center; justify-content: center;
  font-size: 28px; overflow: hidden; flex-shrink: 0;
}
.dish-img img { width: 100%; height: 100%; object-fit: cover; }
.dish-info { flex: 1; display: flex; flex-direction: column; }
.dish-name { font-size: 15px; font-weight: 600; }
.dish-desc { font-size: 12px; color: var(--color-text-light); margin-top: 2px; flex: 1; }
.dish-bottom { display: flex; align-items: center; justify-content: space-between; margin-top: 6px; }
.dish-price { font-size: 16px; font-weight: 800; color: var(--color-primary); }
.add-btn { width: 32px; height: 32px; display: inline-flex; align-items: center; justify-content: center; color: #fff; background: var(--color-primary); border-radius: 50%; }

/* Modal */
.modal-overlay {
  position: fixed; inset: 0; background: rgba(0,0,0,0.4); z-index: 200;
  display: flex; align-items: flex-end; justify-content: center;
}
.modal-content {
  width: 100%; max-width: 500px; max-height: 70vh; overflow-y: auto;
  margin-bottom: 0; border-radius: var(--radius-xl) var(--radius-xl) 0 0;
  padding: 24px 20px;
}
.modal-img { width: 100%; height: 200px; border-radius: var(--radius); overflow: hidden; margin-bottom: 12px; background: var(--color-bg); }
.modal-img img { width: 100%; height: 100%; object-fit: cover; }
.modal-desc { font-size: 13px; color: var(--color-text-muted); margin: 6px 0; }
.modal-price { font-size: 20px; font-weight: 800; color: var(--color-primary); margin-bottom: 12px; }
.option-group { margin-bottom: 12px; }
.option-group-label { font-size: 13px; font-weight: 600; margin-bottom: 6px; }
.option-items { display: flex; flex-wrap: wrap; gap: 6px; }
.option-item {
  display: flex; align-items: center; gap: 6px;
  padding: 6px 12px; border: 1px solid var(--color-line);
  border-radius: var(--radius); font-size: 13px; cursor: pointer;
}
.option-item.selected { border-color: var(--color-primary); background: var(--color-primary-bg); }
.opt-price { color: var(--color-text-light); font-size: 12px; }
.modal-actions { display: flex; gap: 12px; margin-top: 16px; }
.modal-actions .btn { flex: 1; }

/* Cart */
.cart-bar {
  position: fixed; bottom: 0; left: 0; right: 0;
  background: var(--color-bg-white); padding: 12px 16px;
  display: flex; align-items: center; justify-content: space-between;
  box-shadow: 0 -2px 10px rgba(0,0,0,0.08); z-index: 150;
  max-width: 500px; margin: 0 auto; border-radius: var(--radius-lg) var(--radius-lg) 0 0;
}
.cart-info { display: flex; align-items: center; gap: 10px; cursor: pointer; }
.cart-count {
  position: relative; width: 42px; height: 42px; border-radius: 50%;
  background: var(--color-primary); color: #fff;
  display: flex; align-items: center; justify-content: center;
  font-size: 14px; font-weight: 700;
}
.cart-count b { position: absolute; top: -5px; right: -6px; min-width: 18px; height: 18px; padding: 0 4px; display: inline-flex; align-items: center; justify-content: center; background: var(--color-error); border: 2px solid #fff; border-radius: 10px; font-size: 10px; }
.cart-total { font-size: 18px; font-weight: 800; color: var(--color-primary); }
.cart-popup {
  position: fixed; bottom: 0; left: 50%; transform: translateX(-50%);
  width: 100%; max-width: 500px; max-height: 50vh; overflow-y: auto;
  z-index: 201; border-radius: var(--radius-xl) var(--radius-xl) 0 0;
  padding: 20px;
}
.cart-item { display: flex; align-items: center; gap: 10px; padding: 10px 0; border-bottom: 1px solid var(--color-line); }
.cart-item-info { flex: 1; font-size: 14px; }
.cart-item-opts { font-size: 12px; color: var(--color-text-light); }
.cart-item-qty { display: flex; align-items: center; gap: 6px; }
.cart-item-qty span { min-width: 20px; text-align: center; font-weight: 600; }
.qty-btn { width: 32px; height: 32px; display: inline-flex; align-items: center; justify-content: center; color: var(--color-primary); background: var(--color-primary-bg); border-radius: 50%; }
.cart-item-price { font-weight: 700; color: var(--color-primary); font-size: 14px; }

@media (max-width: 768px) {
  .category-sidebar { width: 72px; }
  .cat-btn { font-size: 12px; padding: 8px 4px; }
}
</style>
