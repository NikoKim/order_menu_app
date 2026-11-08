<template>
  <section class="game-reviews" aria-labelledby="game-reviews-title" :aria-busy="loading">
    <header class="reviews-heading"><MessageSquare :size="19" /><h2 id="game-reviews-title">玩家评价</h2><span>{{ data.total }} 条</span></header>
    <AppSkeleton v-if="loading" :rows="4" label="正在加载评价" />
    <template v-else>
      <div class="rating-summary">
        <strong>{{ data.total ? Number(data.averageRating).toFixed(1) : '--' }}</strong>
        <div><div class="rating-stars" :aria-label="`平均 ${data.averageRating || 0} 星`"><Star v-for="star in 5" :key="star" :size="17" :class="{ filled: star <= Math.round(Number(data.averageRating)) }" /></div><small>{{ data.total ? `${data.total} 位玩家评分` : '暂无评分' }}</small></div>
      </div>
      <div v-if="error" class="reviews-error" role="alert"><span>{{ error }}</span><button type="button" title="重新加载评价" @click="load(page)"><RotateCcw :size="16" /></button></div>
      <form v-if="data.canReview" class="review-form" @submit.prevent="save">
        <div class="form-heading"><strong>{{ data.myReview ? '我的评价' : '写评价' }}</strong><small v-if="data.myReview">已评价</small></div>
        <fieldset :disabled="saving || deleting"><legend class="sr-only">游戏评分</legend><div class="rating-input">
          <label v-for="star in 5" :key="star" :title="`${star} 星`"><input v-model.number="rating" type="radio" name="game-rating" :value="star" :aria-label="`${star} 星`" /><Star :size="23" :class="{ filled: star <= rating }" /></label>
          <span>{{ rating ? `${rating} 星` : '未评分' }}</span>
        </div></fieldset>
        <textarea v-model="content" :disabled="saving || deleting" rows="3" maxlength="1000" aria-label="游戏评论" placeholder="这局玩得怎么样？" required></textarea>
        <div class="review-form-footer"><small>{{ content.length }}/1000</small><button type="submit" :disabled="saving || deleting || !canSave" :aria-busy="saving"><LoaderCircle v-if="saving" class="spinning" :size="15" /><Send v-else :size="15" />{{ saving ? '提交中' : data.myReview ? '更新评价' : '发表评价' }}</button></div>
      </form>
      <div v-else-if="loaded" class="review-gate"><LockKeyhole :size="16" /><span>{{ loggedIn ? '玩过这个游戏后即可评论和评分' : '登录并玩过这个游戏后即可评价' }}</span><router-link v-if="!loggedIn" :to="{ path: '/login', query: { redirect: route.fullPath } }">登录</router-link></div>
      <div v-if="data.records.length" class="review-list">
        <article v-for="review in data.records" :key="review.id" class="player-review">
          <div class="review-author"><NavalAvatar :person="{...review.author,name:review.author?.nickname,registered:true}" :size="18" /><strong>{{ review.author?.nickname || '用户' }}</strong><small v-if="review.mine">我</small><button v-if="review.mine" type="button" :disabled="saving || deleting" title="删除我的评价" aria-label="删除我的评价" @click="remove"><Trash2 :size="15" /></button></div>
          <div class="review-meta"><div class="rating-stars" :aria-label="`${review.rating} 星`"><Star v-for="star in 5" :key="star" :size="12" :class="{ filled: star <= review.rating }" /></div><time>{{ formatDate(review.updatedAt) }}</time></div>
          <p>{{ review.content }}</p>
        </article>
      </div>
      <p v-else-if="loaded && !error" class="reviews-status">还没有玩家评价</p>
    </template>
    <nav v-if="data.total > pageSize" class="reviews-pagination" aria-label="评价分页"><button type="button" title="上一页评价" :disabled="loading || page <= 1" @click="load(page - 1)"><ChevronLeft :size="17" /></button><span>{{ page }} / {{ Math.ceil(data.total / pageSize) }}</span><button type="button" title="下一页评价" :disabled="loading || page * pageSize >= data.total" @click="load(page + 1)"><ChevronRight :size="17" /></button></nav>
  </section>
</template>

<script setup>
import NavalAvatar from './NavalAvatar.vue'
import AppSkeleton from '../../components/AppSkeleton.vue'
import { computed, ref, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { ChevronLeft, ChevronRight, LoaderCircle, LockKeyhole, MessageSquare, RotateCcw, Send, Star, Trash2 } from 'lucide-vue-next'
import { deleteNavalReview, getNavalReviews, saveNavalReview } from '../../api/naval'
import { confirmNotice, notifyError, notifySuccess } from '../../services/notice'

const route = useRoute()
const loggedIn = computed(() => Boolean(localStorage.getItem('navy_token')))
const emptyData = () => ({ records: [], total: 0, averageRating: 0, canReview: false, myReview: null })
const data = ref(emptyData())
const loading = ref(true)
const loaded = ref(false)
const saving = ref(false)
const deleting = ref(false)
const error = ref('')
const rating = ref(0)
const content = ref('')
const page = ref(1)
const pageSize = 5
let generation = 0
const canSave = computed(() => rating.value >= 1 && rating.value <= 5 && content.value.trim()
  && (!data.value.myReview || rating.value !== data.value.myReview.rating || content.value.trim() !== data.value.myReview.content))

async function load(nextPage = 1, syncDraft = false) {
  const request = ++generation
  loading.value = true
  error.value = ''
  try {
    const result = await getNavalReviews({ page: nextPage, pageSize })
    if (request !== generation) return
    data.value = result
    page.value = nextPage
    loaded.value = true
    if (syncDraft) {
      rating.value = result.myReview?.rating || 0
      content.value = result.myReview?.content || ''
    }
  } catch (e) { if (request === generation) error.value = e.message || '评价读取失败' }
  finally { if (request === generation) loading.value = false }
}

async function save() {
  if (saving.value || deleting.value || !canSave.value) return
  saving.value = true
  try {
    const review = await saveNavalReview({ rating: rating.value, content: content.value.trim() })
    const previous = data.value.myReview
    data.value.myReview = review
    if (!previous) data.value.total++
    data.value.records = [review, ...data.value.records.filter(item => item.id !== review.id)].slice(0, pageSize)
    content.value = review.content
    notifySuccess(previous ? '评价已更新' : '评价已发表')
    await load(1)
  } catch (e) { notifyError(e.message) }
  finally { saving.value = false }
}

async function remove() {
  if (saving.value || deleting.value) return
  deleting.value = true
  try {
    if (!await confirmNotice('确认删除你的评价和评分？')) return
    await deleteNavalReview()
    const id = data.value.myReview?.id
    data.value.records = data.value.records.filter(item => item.id !== id)
    data.value.myReview = null
    data.value.total = Math.max(0, data.value.total - 1)
    rating.value = 0
    content.value = ''
    notifySuccess('评价已删除')
    await load(1)
  } catch (e) { notifyError(e.message) }
  finally { deleting.value = false }
}

function formatDate(value) {
  if (!value) return ''
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? '' : date.toLocaleDateString('zh-CN')
}

onMounted(() => load(1, true))
</script>

<style scoped>
.game-reviews { min-width: 0; background: #fff; border-top: 1px solid var(--color-line); }
.reviews-heading { min-height: 54px; display: flex; align-items: center; gap: 7px; padding: 12px 14px; border-bottom: 1px solid var(--color-line); }
.reviews-heading > svg { color: var(--color-primary-strong); }.reviews-heading h2 { font-size: 15px; }.reviews-heading > span { margin-left: auto; font-size: 12px; color: var(--color-text-muted); }
.rating-summary { display: flex; align-items: center; gap: 14px; padding: 16px 14px; }.rating-summary > strong { font-size: 30px; line-height: 1.2; font-weight: 700; color: var(--color-text); }.rating-summary small { display: block; margin-top: 4px; font-size: 11px; color: var(--color-text-muted); }
.rating-stars { display: flex; gap: 3px; color: #d9dde1; }.filled { fill: #efad3d; color: #cf8a1f; }
.review-form { padding: 0 14px 14px; border-bottom: 1px solid var(--color-line); }.form-heading { display: flex; justify-content: space-between; align-items: center; }.form-heading strong { font-size: 12px; }.form-heading small { font-size: 11px; color: var(--color-primary-strong); }
fieldset { min-width: 0; padding: 0; border: 0; }.rating-input { display: flex; align-items: center; gap: 3px; margin: 8px 0; }.rating-input label { position: relative; width: 32px; height: 36px; display: grid; place-items: center; cursor: pointer; color: #d9dde1; border-radius: 4px; }.rating-input input { position: absolute; width: 1px; height: 1px; opacity: 0; }.rating-input label:has(input:focus-visible) { outline: 2px solid var(--color-primary-strong); outline-offset: 2px; }.rating-input span { margin-left: auto; font-size: 11px; color: var(--color-text-muted); }
.review-form textarea { width: 100%; min-height: 86px; padding: 9px 10px; resize: vertical; border: 1px solid #dfe3e6; border-radius: 6px; font: inherit; font-size: 12px; line-height: 1.65; background: #fcfdfd; }.review-form textarea:focus { outline: 2px solid var(--color-primary-line); outline-offset: 1px; }
.review-form-footer { display: flex; align-items: center; justify-content: space-between; gap: 8px; margin-top: 8px; }.review-form-footer small { color: var(--color-text-muted); font-size: 10px; }.review-form-footer button { min-width: 98px; min-height: 34px; padding: 6px 10px; display: flex; justify-content: center; align-items: center; gap: 6px; border-radius: 6px; color: #fff; background: var(--color-primary); font-size: 12px; font-weight: 600; }
button:disabled { cursor: not-allowed; opacity: .5; }.review-gate { display: flex; align-items: flex-start; gap: 7px; padding: 0 14px 16px; color: #6e747c; font-size: 12px; line-height: 1.65; }.review-gate > svg { flex: 0 0 auto; margin-top: 2px; }.review-gate a { margin-left: auto; white-space: nowrap; color: var(--color-primary); }
.player-review { padding: 14px; border-bottom: 1px solid #edf0f2; }.review-author { display: flex; align-items: center; gap: 7px; }.review-author img, .review-avatar { width: 26px; height: 26px; flex: 0 0 26px; border-radius: 50%; object-fit: cover; }.review-avatar { display: grid; place-items: center; background: var(--color-primary-bg); color: var(--color-primary-strong); font-size: 12px; }.review-author strong { min-width: 0; overflow-wrap: anywhere; font-size: 12px; }.review-author small { font-size: 10px; color: var(--color-primary-strong); }.review-author button { margin-left: auto; width: 30px; height: 30px; flex: 0 0 30px; display: grid; place-items: center; color: #8b929a; background: transparent; border-radius: 4px; }.review-author button:hover { color: #bc4545; background: #fff4f3; }
.review-meta { display: flex; justify-content: space-between; align-items: center; gap: 8px; margin: 7px 0; }.review-meta time { color: #949aa2; font-size: 10px; }.player-review p { font-size: 12px; line-height: 1.75; white-space: pre-wrap; overflow-wrap: anywhere; color: #4c545b; }
.reviews-status { padding: 20px 14px; text-align: center; font-size: 12px; color: var(--color-text-muted); }.reviews-error { display: flex; align-items: center; gap: 8px; margin: 0 14px 14px; padding: 9px; border-radius: 5px; background: #fff5f3; color: #ab4d44; font-size: 12px; }.reviews-error span { flex: 1; }.reviews-error button { width: 30px; height: 30px; display: grid; place-items: center; background: transparent; color: inherit; }
.reviews-pagination { display: flex; justify-content: center; align-items: center; gap: 16px; padding: 10px; }.reviews-pagination span { font-size: 11px; color: var(--color-text-muted); }.reviews-pagination button { width: 32px; height: 32px; display: grid; place-items: center; background: #f4f7f8; color: #667f89; border-radius: 4px; }
.sr-only { position: absolute; width: 1px; height: 1px; padding: 0; overflow: hidden; clip: rect(0,0,0,0); white-space: nowrap; }.spinning { animation: review-spin .9s linear infinite; }@keyframes review-spin { to { transform: rotate(360deg); } }
</style>
