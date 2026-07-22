<template>
  <section class="quote-picker">
    <div class="quote-heading">
      <span><Quote :size="17" />引用动态</span>
      <button v-if="selected" type="button" title="移除引用" aria-label="移除引用" @click="$emit('update:selected', null)"><X :size="17" /></button>
    </div>
    <MomentQuoteCard v-if="selected" :quote="selected" />
    <template v-else>
      <label class="quote-search">
        <Search :size="17" />
        <input v-model="query" placeholder="搜索当前可见的动态" autocomplete="off" @input="runSearch" />
      </label>
      <div v-if="query.trim()" class="quote-results">
        <LoadingSpinner v-if="loading" variant="quote-list" text="正在搜索可引用动态" />
        <template v-else>
          <button v-for="moment in results" :key="moment.id" type="button" @click="selectMoment(moment)">
            <strong>{{ moment.title }}</strong>
            <span>{{ moment.author?.nickname }} · {{ formatTime(moment.publishedAt || moment.createdAt) }}</span>
            <p>{{ moment.content }}</p>
          </button>
          <p v-if="!results.length" class="quote-empty">没有找到可引用的动态</p>
        </template>
      </div>
      <p v-else class="quote-hint">一条动态只能引用一个动态或一条评论</p>
    </template>
  </section>
</template>

<script setup>
import { ref } from 'vue'
import { Quote, Search, X } from 'lucide-vue-next'
import { getMoments } from '../api/moments'
import { formatMomentTime } from '../composables/useMomentTime'
import LoadingSpinner from './LoadingSpinner.vue'
import MomentQuoteCard from './MomentQuoteCard.vue'

const props = defineProps({ selected: { type: Object, default: null }, excludeMomentId: { type: Number, default: null } })
const emit = defineEmits(['update:selected'])
const query = ref('')
const results = ref([])
const loading = ref(false)
let timer
let sequence = 0

function runSearch() {
  window.clearTimeout(timer)
  const keyword = query.value.trim()
  const current = ++sequence
  if (!keyword) {
    results.value = []
    loading.value = false
    return
  }
  loading.value = true
  timer = window.setTimeout(async () => {
    try {
      const page = await getMoments({ keyword, page: 1, pageSize: 10, consumeExposure: false })
      if (current === sequence) results.value = (page.records || []).filter(moment => Number(moment.id) !== Number(props.excludeMomentId))
    } catch {
      if (current === sequence) results.value = []
    } finally {
      if (current === sequence) loading.value = false
    }
  }, 280)
}

function selectMoment(moment) {
  emit('update:selected', {
    type: 'MOMENT', momentId: Number(moment.id), commentId: null,
    author: moment.author, momentTitle: moment.title, content: moment.content,
    createdAt: moment.publishedAt || moment.createdAt, available: true
  })
  query.value = ''
  results.value = []
}

const formatTime = formatMomentTime
</script>

<style scoped>
.quote-picker { position: relative; padding-top: 8px; }
.quote-heading { min-height: 30px; display: flex; align-items: center; justify-content: space-between; }
.quote-heading > span { display: inline-flex; align-items: center; gap: 7px; color: #303338; font-size: 14px; font-weight: 700; }
.quote-heading > span svg { color: #d96f0a; }
.quote-heading > button { width: 32px; height: 32px; display: grid; place-items: center; border-radius: 50%; background: transparent; color: #858a91; }
.quote-heading > button:hover { background: #f2f3f5; color: #33363a; }
.quote-search { min-height: 42px; display: flex; align-items: center; gap: 9px; margin-top: 8px; padding: 0 12px; border: 1px solid #e0e3e7; border-radius: 7px; color: #969ba1; background: #f7f8f9; }
.quote-search:focus-within { border-color: #e58a32; background: #fff; box-shadow: 0 0 0 3px rgba(229, 138, 50, .1); }
.quote-search input { min-width: 0; flex: 1; border: 0; outline: 0; background: transparent; color: #303338; font-size: 14px; }
.quote-results { max-height: 310px; margin-top: 6px; overflow-y: auto; border: 1px solid #e5e7ea; border-radius: 7px; background: #fff; box-shadow: 0 10px 28px rgba(34, 37, 41, .11); }
.quote-results > button { width: 100%; min-height: 76px; display: flex; flex-direction: column; align-items: stretch; gap: 3px; padding: 10px 12px; border-bottom: 1px solid #eceef0; background: transparent; color: #34373b; text-align: left; }
.quote-results > button:hover { background: #f7f8f9; }
.quote-results strong, .quote-results p { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.quote-results strong { font-size: 14px; }
.quote-results span { color: #8b9097; font-size: 11px; }
.quote-results p { color: #666b72; font-size: 12px; }
.quote-empty, .quote-hint { padding: 15px 4px 2px; color: #969ba1; font-size: 12px; text-align: center; }
</style>
