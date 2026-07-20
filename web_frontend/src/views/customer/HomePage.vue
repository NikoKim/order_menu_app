<template>
  <div class="page-container">
    <div class="welcome-card card">
      <h1>今天想吃点什么？</h1>
      <p class="welcome-sub"><MapPin :size="16" />本地商家 · 即刻点餐</p>
    </div>

    <div class="family-filters" v-if="families.length">
      <button class="btn" :class="!selectedFamily ? 'btn-primary' : 'btn-ghost'" @click="selectedFamily = ''; loadShops()">
        全部商家
      </button>
      <button v-for="f in families" :key="f.shopId" class="btn"
        :class="selectedFamily === f.familyCode ? 'btn-primary' : 'btn-ghost'"
        @click="selectedFamily = f.familyCode; loadShops()">
        {{ f.familyName }}
      </button>
    </div>

    <LoadingSpinner v-if="loading" />
    <div v-else-if="error" class="error-wrap">
      <EmptyState icon="warning" :title="error" action-text="重试" @action="loadShops" />
    </div>
    <template v-else>
      <div class="shop-grid" v-if="shops.length">
        <div v-for="shop in shops" :key="shop.id" class="card shop-card" @click="router.push(`/customer/shop/${shop.id}/menu`)">
          <div class="shop-card-top">
            <div class="shop-avatar" :style="{ background: shopColors[shop.id % shopColors.length] }">
              <img v-if="shop.coverImageUrl" :src="imageUrl(shop.coverImageUrl)" alt="" />
              <Store v-else :size="30" aria-hidden="true" />
            </div>
            <div class="shop-info">
              <div class="shop-name-row">
                <span class="shop-name">{{ shop.name }}</span>
                <span class="badge" :class="shop.businessStatus === 'OPEN' ? 'badge-green' : 'badge-gray'">
                  {{ shop.businessStatus === 'OPEN' ? '营业中' : '休息中' }}
                </span>
              </div>
              <span v-if="shop.familyName && !selectedFamily" class="badge badge-orange family-badge"><House :size="12" />{{ shop.familyName }}</span>
              <p class="shop-desc">{{ shop.description || '欢迎下单，商家正在准备好味道' }}</p>
            </div>
          </div>
          <div class="shop-preview" v-if="shop.previewDishes?.length">
            <div v-for="dish in shop.previewDishes.slice(0, 5)" :key="dish.id" class="preview-dish">
              <div class="preview-thumb">
                <img v-if="dish.imageUrl" :src="imageUrl(dish.imageUrl)" alt="" />
                <Utensils v-else :size="23" aria-hidden="true" />
              </div>
              <div class="preview-name">{{ dish.name }}</div>
              <div class="preview-price">{{ dish.price }}</div>
            </div>
          </div>
        </div>
      </div>
      <EmptyState v-else icon="store" title="暂无商家" description="稍后刷新试试" action-text="刷新" @action="loadShops" />
    </template>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { House, MapPin, Store, Utensils } from 'lucide-vue-next'
import { getShops, getMyFamilies } from '../../api/customer'
import { imageUrl } from '../../composables/useImage'
import LoadingSpinner from '../../components/LoadingSpinner.vue'
import EmptyState from '../../components/EmptyState.vue'

const router = useRouter()
const shops = ref([])
const families = ref([])
const selectedFamily = ref('')
const loading = ref(true)
const error = ref('')

const shopColors = ['rgba(255,217,168,0.3)', 'rgba(255,183,161,0.3)', 'rgba(207,232,201,0.3)', 'rgba(255,198,110,0.3)']

async function loadShops() {
  loading.value = true
  error.value = ''
  try {
    const res = await getShops({ page: 1, pageSize: 50, familyCode: selectedFamily.value || undefined })
    shops.value = res.records || []
  } catch (e) {
    error.value = e.message
  } finally { loading.value = false }
}

async function loadFamilies() {
  if (!localStorage.getItem('token')) {
    families.value = []
    return
  }
  try {
    const data = await getMyFamilies()
    families.value = (data.families || []).filter(f => f.memberStatus === 'APPROVED')
  } catch { }
}

onMounted(() => {
  loadFamilies()
  loadShops()
})
</script>

<style scoped>
.welcome-card {
  margin-bottom: 12px; padding: 20px;
  background: #fff; border-radius: var(--radius-lg); color: var(--color-text); text-align: left;
}
.welcome-card h1 { font-size: 21px; font-weight: 800; color: var(--color-text); }
.welcome-sub { display: flex; align-items: center; gap: 4px; font-size: 13px; color: var(--color-text-muted); margin-top: 7px; }
.welcome-sub svg { color: var(--color-primary); }

.family-filters { display: flex; flex-wrap: wrap; gap: 6px; margin-bottom: 12px; }

.error-wrap { min-height: 30vh; display: flex; align-items: center; justify-content: center; }

.shop-grid { display: flex; flex-direction: column; gap: 12px; }
.shop-card {
  cursor: pointer; transition: box-shadow 0.2s; border-radius: var(--radius-lg);
  overflow: hidden;
}
.shop-card:hover { box-shadow: var(--shadow); }
.shop-card-top { display: flex; gap: 14px; }
.shop-avatar {
  width: 72px; height: 72px; border-radius: var(--radius-lg);
  display: flex; align-items: center; justify-content: center;
  font-size: 28px; flex-shrink: 0; overflow: hidden; background: var(--color-bg);
}
.shop-avatar img { width: 100%; height: 100%; object-fit: cover; }
.shop-info { flex: 1; min-width: 0; }
.shop-name-row { display: flex; align-items: center; gap: 8px; margin-bottom: 4px; }
.shop-name { font-size: 16px; font-weight: 700; color: var(--color-text); }
.family-badge { width: fit-content; margin: 2px 0 5px; border-radius: 4px; }
.shop-desc { font-size: 12px; color: var(--color-text-muted); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.shop-preview { display: flex; gap: 8px; margin-top: 14px; overflow-x: auto; padding-bottom: 4px; }
.preview-dish { min-width: 72px; text-align: center; }
.preview-thumb {
  width: 72px; height: 72px; border-radius: var(--radius);
  background: var(--color-bg); display: flex; align-items: center; justify-content: center;
  font-size: 24px; overflow: hidden;
}
.preview-thumb img { width: 100%; height: 100%; object-fit: cover; }
.preview-name { font-size: 12px; margin-top: 4px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; color: var(--color-text); }
.preview-price { font-size: 12px; font-weight: 700; color: var(--color-primary); }

@media (max-width: 768px) {
  .page-container { padding-top: max(12px, env(safe-area-inset-top)); }
  .welcome-card { padding: 16px; }
  .welcome-card h1 { font-size: 20px; }
  .family-filters {
    position: sticky; top: 0; z-index: 20; flex-wrap: nowrap; overflow-x: auto;
    margin-inline: -12px; padding: 5px 12px 11px; background: var(--color-bg-light);
    scrollbar-width: none;
  }
  .family-filters::-webkit-scrollbar { display: none; }
  .family-filters .btn { flex: 0 0 auto; min-height: 38px; }
  .shop-card { padding: 12px; }
  .shop-avatar { width: 64px; height: 64px; }
  .shop-preview { margin-top: 12px; }
}
</style>
