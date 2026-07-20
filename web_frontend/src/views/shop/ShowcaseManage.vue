<template>
  <div class="page-container showcase-page">
    <div class="page-header"><h1 class="page-title">首页展示设置</h1><button class="btn btn-primary" :disabled="saving" @click="save">{{ saving ? '保存中...' : '保存' }}</button></div>
    <div class="tip"><Info :size="18" /><span>请选择 8 个在售菜品在顾客端首页展示。已选 {{ selected.length }}/8</span></div>
    <LoadingSpinner v-if="loading" />
    <EmptyState v-else-if="!selected.length" icon="dish" title="暂未选择展示菜品" />
    <section v-else class="selected-list">
      <article v-for="(dish, index) in selected" :key="dish.id" class="selected-row">
        <span class="drag"><GripVertical :size="19" /></span>
        <span class="thumb"><img v-if="dish.imageUrl" :src="imageUrl(dish.imageUrl)" alt="" /><Utensils v-else :size="19" /></span>
        <span class="copy"><strong>{{ dish.name }}</strong><small>第 {{ index + 1 }} 位展示</small></span>
        <span class="sort-buttons"><button title="上移" :disabled="index === 0" @click="move(index, -1)"><ChevronUp :size="17" /></button><button title="下移" :disabled="index === selected.length - 1" @click="move(index, 1)"><ChevronDown :size="17" /></button></span>
        <button class="remove" title="移除" @click="selected.splice(index, 1)"><MinusCircle :size="20" /></button>
      </article>
    </section>
    <button v-if="!loading && selected.length < 8" class="add-fab" @click="pickerOpen = true"><Plus :size="19" />添加展示菜品</button>

    <Teleport to="body"><div v-if="pickerOpen" class="picker-backdrop" @click.self="pickerOpen = false"><section class="picker-sheet"><header><h2>选择在售菜品</h2><button title="关闭" @click="pickerOpen = false"><X :size="20" /></button></header><div class="picker-list"><button v-for="dish in available" :key="dish.id" @click="add(dish)"><span class="thumb"><img v-if="dish.imageUrl" :src="imageUrl(dish.imageUrl)" alt="" /><Utensils v-else :size="19" /></span><span class="copy"><strong>{{ dish.name }}</strong><small>¥{{ Number(dish.price).toFixed(2) }}</small></span><PlusCircle :size="20" /></button><EmptyState v-if="!available.length" icon="dish" title="没有可添加的在售菜品" /></div></section></div></Teleport>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { ChevronDown, ChevronUp, GripVertical, Info, MinusCircle, Plus, PlusCircle, Utensils, X } from 'lucide-vue-next'
import { getDishes, updateShowcaseDishes } from '../../api/shop'
import { imageUrl } from '../../composables/useImage'
import LoadingSpinner from '../../components/LoadingSpinner.vue'
import EmptyState from '../../components/EmptyState.vue'
const dishes = ref([]), selected = ref([]), loading = ref(true), saving = ref(false), pickerOpen = ref(false)
const available = computed(() => dishes.value.filter(dish => !selected.value.some(item => item.id === dish.id)))
async function load() { try { const data = await getDishes({ status: 'ON_SALE', pageSize: 500 }); dishes.value = data.records || data.items || data || []; selected.value = dishes.value.filter(d => Number(d.recommendOrder) > 0).sort((a,b) => a.recommendOrder - b.recommendOrder) } finally { loading.value = false } }
function add(dish) { if (selected.value.length >= 8) return; selected.value.push(dish); pickerOpen.value = false }
function move(index, offset) { const target = index + offset; if (target < 0 || target >= selected.value.length) return; const item = selected.value.splice(index, 1)[0]; selected.value.splice(target, 0, item) }
async function save() { if (selected.value.length !== 8) return alert('必须选满 8 个菜品才能保存生效'); saving.value = true; try { await updateShowcaseDishes(selected.value.map(d => d.id)); alert('首页展示设置已保存'); history.back() } catch (e) { alert(e.message) } finally { saving.value = false } }
onMounted(load)
</script>

<style scoped>
.showcase-page { max-width: 720px; padding-bottom: 90px; }.tip { margin-bottom: 14px; padding: 12px 14px; display: flex; align-items: flex-start; gap: 8px; background: var(--color-primary-bg); border-radius: var(--radius); font-size: 13px; }.tip svg { flex: 0 0 auto; color: var(--color-primary); }.selected-list { display: flex; flex-direction: column; gap: 10px; }.selected-row { min-height: 70px; padding: 10px; display: flex; align-items: center; gap: 9px; background: #fff; border: 1px solid var(--color-line-light); border-radius: var(--radius); }.drag { color: var(--color-text-light); }.thumb { width: 46px; height: 46px; overflow: hidden; flex: 0 0 auto; display: inline-flex; align-items: center; justify-content: center; color: var(--color-primary); background: var(--color-primary-bg); border-radius: var(--radius-sm); }.thumb img { width: 100%; height: 100%; object-fit: cover; }.copy { flex: 1; min-width: 0; display: flex; flex-direction: column; }.copy strong { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }.copy small { color: var(--color-text-muted); }.sort-buttons { display: flex; flex-direction: column; }.sort-buttons button,.remove { width: 30px; height: 28px; display: inline-flex; align-items: center; justify-content: center; color: var(--color-text-muted); background: transparent; }.sort-buttons button:disabled { opacity: .25; }.remove { color: var(--color-error); }.add-fab { position: fixed; z-index: 100; right: max(18px, calc((100vw - 720px)/2 + 18px)); bottom: calc(78px + env(safe-area-inset-bottom)); min-height: 46px; padding: 10px 16px; display: inline-flex; align-items: center; gap: 7px; color: #fff; background: var(--color-primary); border-radius: 24px; box-shadow: var(--shadow-lg); font-weight: 700; }.picker-backdrop { position: fixed; z-index: 300; inset: 0; display: flex; align-items: flex-end; justify-content: center; background: rgba(0,0,0,.4); }.picker-sheet { width: 100%; max-width: 600px; max-height: 78dvh; overflow: hidden; background: #fff; border-radius: var(--radius-xl) var(--radius-xl) 0 0; }.picker-sheet header { padding: 15px 16px; display: flex; align-items: center; border-bottom: 1px solid var(--color-line); }.picker-sheet h2 { flex: 1; font-size: 18px; }.picker-sheet header button { width: 38px; height: 38px; display: inline-flex; align-items: center; justify-content: center; background: transparent; }.picker-list { max-height: calc(78dvh - 70px); overflow-y: auto; padding-bottom: env(safe-area-inset-bottom); }.picker-list > button { width: 100%; min-height: 68px; padding: 10px 16px; display: flex; align-items: center; gap: 12px; color: var(--color-text); background: #fff; text-align: left; border-bottom: 1px solid var(--color-line); }.picker-list > button > svg { color: var(--color-primary); }
</style>
