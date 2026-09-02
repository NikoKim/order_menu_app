<template>
  <main class="challenge-page">
    <header class="challenge-header">
      <button class="back" title="返回游戏" @click="goBack"><ArrowLeft :size="20" /></button>
      <div><span>{{ challenge?.challengeDate || '今日' }}</span><h1>每日固定种子挑战榜</h1><p>同一版本、同一行情种子，按收益率排序，回撤用于同收益率下的比较。</p></div>
      <button class="refresh" :disabled="loading" title="刷新排行榜" @click="load(true)"><RefreshCw :size="18" /></button>
    </header>

    <LoadingSpinner v-if="loading && !challenge" text="正在读取挑战榜" />
    <section v-else-if="error && !challenge" class="load-failure"><TriangleAlert :size="30" /><h2>暂时无法读取挑战榜</h2><p>{{ error }}</p><button class="refresh-action" @click="load(false)"><RefreshCw :size="16" />重新读取</button></section>
    <section v-else class="leaderboard">
      <div class="table-head"><span>排名 / 玩家</span><span>收益率</span><span>最大回撤</span><span>研究准确率</span><span>风险成本</span><span>徽章</span></div>
      <article v-for="entry in entries" :key="entry.id" :class="{ mine: entry.currentPlayer }">
        <div class="identity"><b>{{ entry.rank }}</b><span><strong>{{ entry.displayName }}</strong><small v-if="entry.currentPlayer">我的成绩</small></span></div>
        <strong :class="Number(entry.returnPercent) >= 0 ? 'positive' : 'negative'">{{ signed(entry.returnPercent) }}%</strong>
        <span>{{ fixed(entry.maxDrawdownPercent, 2) }}%</span>
        <span>{{ entry.accurateResearchCount }}/{{ entry.researchAttemptCount }} · {{ fixed(entry.researchAccuracyPercent, 0) }}%</span>
        <span>利息 {{ money(entry.interestCost) }} · 强平 {{ entry.liquidationCount }}</span>
        <div class="badges"><small v-if="entry.lowDrawdownBadge">低回撤</small><small v-if="entry.noLiquidationBadge">无强平</small><small v-if="entry.noDebtCostBadge">零利息</small><small v-if="!entry.lowDrawdownBadge && !entry.noLiquidationBadge && !entry.noDebtCostBadge">-</small></div>
      </article>
      <div v-if="!entries.length" class="empty"><Trophy :size="34" /><strong>今天还没有完赛成绩</strong><span>完成挑战后，成绩会自动提交到这里。</span></div>
    </section>
  </main>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowLeft, RefreshCw, TriangleAlert, Trophy } from 'lucide-vue-next'
import { ensureEntertainmentGuest, getDailyChallengeById, getDailyChallengeLeaderboard } from '../../api/entertainment'
import LoadingSpinner from '../../components/LoadingSpinner.vue'
import { notifyError } from '../../services/notice'

const route = useRoute()
const router = useRouter()
const challenge = ref(null)
const entries = ref([])
const loading = ref(true)
const error = ref('')
const challengeId = Number(route.params.challengeId)

async function load(fromAction = false) {
  loading.value = true
  if (!challenge.value) error.value = ''
  try {
    await ensureEntertainmentGuest()
    const [detail, leaderboard] = await Promise.all([
      getDailyChallengeById(challengeId), getDailyChallengeLeaderboard(challengeId)
    ])
    challenge.value = detail
    entries.value = leaderboard
    error.value = ''
  } catch (e) {
    if (fromAction && challenge.value) notifyError(e.message)
    else error.value = e.message
  } finally { loading.value = false }
}
function goBack() { challenge.value?.gameId ? router.push(`/customer/entertainment/games/${challenge.value.gameId}`) : router.back() }
function fixed(value, digits = 2) { return Number(value || 0).toFixed(digits) }
function signed(value) { const number = Number(value || 0); return `${number >= 0 ? '+' : ''}${number.toFixed(2)}` }
function money(value) { return `¥${Number(value || 0).toFixed(2)}` }
onMounted(load)
</script>

<style scoped>
.challenge-page{min-height:calc(100vh - 57px);padding:22px 24px 52px;background:#f1f3f2;color:#28342e}.challenge-header,.leaderboard,.load-failure{width:min(1180px,100%);margin-left:auto;margin-right:auto}.challenge-header{display:grid;grid-template-columns:42px 1fr 42px;align-items:start;gap:14px;margin-bottom:16px}.back,.refresh{width:42px;height:42px;display:grid;place-items:center;border:1px solid #d7ddda;border-radius:6px;background:#fff;color:#516057}.refresh:disabled{opacity:.5}.challenge-header span{color:#8a642b;font-size:10px;font-weight:800}.challenge-header h1{margin-top:3px;font-size:24px}.challenge-header p{margin-top:5px;color:#6f7a74;font-size:11px}.load-failure{min-height:320px;display:flex;flex-direction:column;align-items:center;justify-content:center;text-align:center;color:#747f79}.load-failure>svg{color:#b84942}.load-failure h2{margin-top:9px;color:#34423a;font-size:18px}.load-failure p{margin-top:6px;font-size:12px}.refresh-action{min-height:38px;display:flex;align-items:center;gap:6px;margin-top:14px;padding:0 14px;border-radius:6px;background:#287650;color:#fff;font-weight:700}.leaderboard{overflow:hidden;border:1px solid #d7ddda;border-radius:7px;background:#fff}.table-head,.leaderboard article{display:grid;grid-template-columns:minmax(190px,1.3fr) 110px 110px 145px minmax(180px,1fr) 180px;align-items:center;gap:12px;padding:0 16px}.table-head{height:42px;color:#737e78;background:#f7f9f8;border-bottom:1px solid #dfe4e1;font-size:9px;font-weight:800}.leaderboard article{min-height:62px;border-bottom:1px solid #edf0ee;font-size:10px}.leaderboard article:last-of-type{border-bottom:0}.leaderboard article.mine{background:#eef8f3}.identity{display:grid;grid-template-columns:30px 1fr;align-items:center}.identity>b{color:#8a642b;font-size:15px}.identity span{display:flex;min-width:0;flex-direction:column}.identity strong{overflow:hidden;text-overflow:ellipsis;white-space:nowrap;font-size:11px}.identity small{margin-top:2px;color:#27704b;font-size:8px}.positive{color:#178052}.negative{color:#c34843}.badges{display:flex;flex-wrap:wrap;gap:4px}.badges small{padding:4px 6px;border-radius:4px;color:#37644e;background:#e5f1ea;font-size:8px}.empty{min-height:260px;display:flex;align-items:center;justify-content:center;flex-direction:column;color:#758079}.empty strong{margin-top:8px;font-size:13px}.empty span{margin-top:4px;font-size:10px}@media(max-width:800px){.challenge-page{padding:14px 10px 40px}.challenge-header h1{font-size:19px}.leaderboard{overflow-x:auto}.table-head,.leaderboard article{width:920px}.challenge-header p{line-height:1.5}}
</style>
