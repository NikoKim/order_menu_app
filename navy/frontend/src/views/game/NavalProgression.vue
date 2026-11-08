<template>
  <section class="profile-progression" aria-label="军衔与每日任务">
    <AppSkeleton v-if="loading && !profile" variant="profile" :rows="3" label="正在读取军衔与任务" />
    <template v-if="profile">
      <section class="profile-rank-section" aria-label="军衔进度"><header class="rank-heading">
        <div class="rank-title"><NavalRankBadge :level="profile.level" :size="58" /><div><h3>{{ profile.rank }} <small>Lv.{{ profile.level }}</small></h3><span>{{ profile.score }} 等级分</span></div></div>
        <div class="account-wallet" aria-label="账户金币"><Coins :size="17" /><strong>{{ profile.coins }}</strong><span>账户金币</span></div>
      </header>
      <div class="rank-meter" role="progressbar" aria-label="军衔等级进度" :aria-valuenow="profile.score" :aria-valuemin="profile.minScore" :aria-valuemax="profile.maxScore"><span :style="{ width: `${Math.max(0,Math.min(1,profile.progress || 0))*100}%` }" /></div>
      <div class="rank-range"><span>{{ profile.minScore }} 分</span><span>{{ profile.level === 12 && profile.score >= profile.maxScore ? '已达最高等级' : `${profile.maxScore} 分` }}</span></div>
      <div v-if="profile.promotion" class="promotion-status" role="status"><Flag :size="17" /><div><strong>{{ profile.promotion.targetRank }}晋升考核</strong><span>人人对战连胜 {{ profile.promotion.currentWins }} / {{ profile.promotion.requiredWins }} · 等级分暂停</span></div></div>
      </section>
      <section class="profile-task-section" aria-label="每日任务"><header class="tasks-heading"><h3><ClipboardCheck :size="17" />每日任务</h3><small v-if="profile.claimableCount">{{ profile.claimableCount }} 项待领取</small></header>
      <p class="task-spectator-requirement"><EyeOff :size="12" /><span>仅战斗全程<strong>关闭观战</strong>的对局计入任务</span></p>
      <ul class="daily-tasks">
        <li v-for="task in profile.tasks" :key="task.id" :class="{ completed: task.completed }">
          <CheckCircle2 v-if="task.completed" :size="18" class="task-state" aria-label="已完成" /><Circle v-else :size="18" class="task-state" aria-label="未完成" />
          <div class="task-content"><h4>{{ task.title }}<small v-if="task.target > 1" class="task-progress">{{ task.progress }} / {{ task.target }}</small></h4><div class="task-rewards"><span data-task-coins><Coins :size="12" />{{ task.coins }}</span><span><TrendingUp :size="12" />{{ task.score }} 等级分</span><small v-if="task.claimed">已领取</small></div></div>
          <button v-if="task.canClaim" class="claim-task" :disabled="Boolean(claiming)" @click="claim(task,$event)"><LoaderCircle v-if="claiming === task.id + task.day" :size="13" /><Gift v-else :size="13" />领取</button>
        </li>
      </ul>
      <template v-if="profile.pendingTasks?.length"><div class="tasks-heading"><h3><Gift :size="17" />待领补给</h3></div><ul class="daily-tasks"><li v-for="task in profile.pendingTasks" :key="task.day + task.id" class="completed"><CheckCircle2 :size="18" class="task-state" /><div class="task-content"><h4>{{ task.title }}</h4><div class="task-rewards"><span data-task-coins><Coins :size="12" />{{ task.coins }}</span><span><TrendingUp :size="12" />{{ task.score }} 等级分</span><time>{{ task.day }}</time></div></div><button class="claim-task" :disabled="Boolean(claiming)" @click="claim(task,$event)"><Gift :size="13" />领取</button></li></ul></template>
      </section>
    </template>
    <button v-if="!profile && !loading" type="button" class="progression-retry" aria-label="重新读取军衔与任务" @click="refreshNavalProgression"><RefreshCw :size="15" />重试</button>
  </section>
</template>

<script setup>
import { onMounted, onUnmounted, ref, watch } from 'vue'
import { CheckCircle2, Circle, ClipboardCheck, Coins, EyeOff, Flag, Gift, LoaderCircle, RefreshCw, TrendingUp } from 'lucide-vue-next'
import { claimNavalTaskReward } from '../../games/navalTaskRewards'
import { notifyError } from '../../services/notice'
import AppSkeleton from '../../components/AppSkeleton.vue'
import NavalRankBadge from './NavalRankBadge.vue'
import { ownNavalProgression as profile, navalProgressionLoading as loading, navalProgressionError as error, refreshNavalProgression, syncNavalProgressionAccount } from '../../games/navalProgression'

syncNavalProgressionAccount()
const claiming = ref(''), claimError = ref('')
watch(error,value=>{if(value)notifyError(value)},{immediate:true})
watch(claimError,value=>{if(value)notifyError(value)},{flush:'sync'})
async function claim(task,event) {
  if (claiming.value) return
  claiming.value = task.id + task.day
  claimError.value = ''
  const sourceElement = event?.currentTarget?.closest('li')?.querySelector('[data-task-coins]')
  try { await claimNavalTaskReward(task,{sourceElement}) }
  catch (e) { claimError.value = e.message }
  finally { claiming.value = '' }
}
let resetTimer
function scheduleReset() {
  clearTimeout(resetTimer)
  const at = Number(profile.value?.resetsAt)
  if (!Number.isFinite(at) || at <= Date.now()) return
  resetTimer = setTimeout(refreshNavalProgression, Math.min(2147483647, at - Date.now() + 100))
}
function onVisible() {
  if (document.visibilityState === 'visible' && Number(profile.value?.resetsAt) <= Date.now()) refreshNavalProgression()
}
watch(() => profile.value?.resetsAt, scheduleReset)
onMounted(() => { refreshNavalProgression(); scheduleReset(); document.addEventListener('visibilitychange', onVisible) })
onUnmounted(() => { clearTimeout(resetTimer); document.removeEventListener('visibilitychange', onVisible) })
</script>

<style scoped>
.profile-progression{padding:0;color:#456a77;container-type:inline-size}.profile-rank-section,.profile-task-section{padding:18px 0;border-bottom:1px solid #dce6e9}
.rank-heading{display:flex;align-items:center;justify-content:space-between;gap:10px;min-height:66px}
.rank-title{display:flex;align-items:center;gap:9px;min-width:0;color:var(--color-primary-strong)}
.rank-title h3{font-size:22px;line-height:1.4;margin:0;overflow-wrap:anywhere}
.rank-title h3 small{font-size:10px;font-weight:500;color:#7a969e;margin-left:3px;white-space:nowrap}
.rank-title span{display:block;color:#829ba4;font-size:11px;margin-top:3px;font-variant-numeric:tabular-nums}
.account-wallet{display:grid;grid-template-columns:17px minmax(0,1fr);align-items:center;gap:3px 5px;max-width:40%;color:#b57b37;font-size:16px}
.account-wallet strong{font-variant-numeric:tabular-nums;overflow-wrap:anywhere}
.account-wallet>span{grid-column:1/3;font-size:10px;color:#8d9da1;text-align:right}
.rank-meter{height:5px;background:#f4e7db;border-radius:3px;overflow:hidden;margin-top:12px}
.rank-meter>span{height:100%;display:block;background:var(--color-primary);transition:width .35s}
.rank-range{display:flex;justify-content:space-between;gap:12px;font-size:10px;color:#8ba1a8;margin-top:6px;font-variant-numeric:tabular-nums}
.promotion-status{display:flex;align-items:center;gap:9px;margin-top:9px;padding:8px 0;color:#ad813d}
.promotion-status>svg{flex-shrink:0}
.promotion-status strong{display:block;font-size:12px;font-weight:600}
.promotion-status span{display:block;margin-top:4px;font-size:10px;line-height:1.6;color:#8e9290}
.tasks-heading{display:flex;align-items:center;justify-content:space-between;gap:10px;min-height:24px;margin:0}.daily-tasks+.tasks-heading{margin-top:20px}
.tasks-heading h3{display:flex;align-items:center;gap:7px;margin:0;color:#456a77;font-size:13px;font-weight:600;line-height:1.6}.tasks-heading h3>svg{color:var(--color-primary);flex-shrink:0}
.tasks-heading>small{font-size:10px;color:#91a5ad;white-space:nowrap}
.task-spectator-requirement{display:flex;align-items:center;gap:5px;color:#a97d3f;font-size:11px;line-height:1.6;margin-top:6px;overflow-wrap:anywhere}.task-spectator-requirement>svg{flex-shrink:0}.task-spectator-requirement strong{font-weight:650;color:#95662d}
.daily-tasks{padding:0;margin:7px 0 0;list-style:none;display:grid;grid-template-columns:minmax(0,1fr)}
.daily-tasks li{display:flex;align-items:flex-start;gap:9px;border-bottom:1px solid #e4ecee;padding:12px 0;min-width:0}
.daily-tasks li:last-child{border-bottom:0;padding-bottom:0}
.task-state{flex-shrink:0;color:#b4c7cd;margin-top:1px;width:16px;height:16px}
.completed .task-state{color:#5d9c80}
.task-content{flex:1;min-width:0}
.task-content h4{margin:0;font-weight:500;font-size:11px;line-height:1.6;overflow-wrap:anywhere}
.task-rewards{display:flex;align-items:center;flex-wrap:wrap;gap:4px 13px;margin-top:4px;font-size:10px}
.task-rewards>span{display:flex;align-items:center;gap:4px;color:#8298a0}
.task-rewards>span:first-child{color:#b1854d}
.task-rewards>small{color:#5d9c80;font-size:9px;margin-left:auto}
.task-progress{display:inline-block;margin-left:8px;color:#8298a0;font-size:10px;font-weight:400;font-variant-numeric:tabular-nums;white-space:nowrap}
.claim-task{display:flex;align-items:center;justify-content:center;gap:4px;flex-shrink:0;min-height:30px;min-width:62px;padding:5px 8px;border-radius:4px;background:var(--color-primary);color:#fff;font-size:11px}.task-rewards time{font-size:9px;color:#91a5ad}
.progression-status{display:flex;align-items:center;gap:7px;font-size:11px;line-height:1.7;overflow-wrap:anywhere}
.progression-status.error{color:#b45f50;margin-top:10px}
.progression-status>button{display:grid;place-items:center;flex-shrink:0;width:28px;height:28px;border:1px solid #e0c6bc;border-radius:4px;color:inherit;background:transparent}
.progression-status>button:disabled{opacity:.5}
@container(min-width:520px){.daily-tasks{grid-template-columns:repeat(2,minmax(0,1fr));column-gap:22px}.daily-tasks li:nth-last-child(-n+2){border-bottom:0;padding-bottom:0}}
@media(max-width:370px){.tasks-heading{align-items:flex-start;flex-wrap:wrap;gap:5px}.rank-title h3{font-size:19px}.account-wallet{font-size:14px}}
@media(prefers-reduced-motion:reduce){.rank-meter>span{transition:none}}
</style>
