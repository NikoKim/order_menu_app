<template>
  <section class="settlement-screen" role="dialog" aria-modal="true" aria-label="战局结算">
    <div class="settlement-inner">
      <header class="settlement-header"><div class="settlement-result-heading"><Trophy :size="29" /><div><h2 :title="title"><template v-if="resultYou < 0 && result.winner != null"><span class="winner-name">{{ participants[result.winner]?.name || '指挥官' }}</span><span>获胜</span></template><template v-else>{{ title }}</template></h2><span class="settlement-mode"><Anchor :size="12" />{{ room.ruleSet === 'MILITARY' ? '军事模式' : '经典模式' }}<small>第 {{ result.round || room.round || 1 }} 局</small></span></div></div><p v-if="reason" class="settlement-reason">{{ reason }}</p><button class="settlement-icon" title="查看棋盘" aria-label="查看棋盘" @click="$emit('close')"><X :size="20" /></button></header>
      <div class="settlement-body">
        <div class="settlement-content">
          <div class="settlement-players">
            <article v-for="(player, index) in participants" :key="player.id || index" :class="{ winner: result.winner === index }">
              <div class="player-heading"><NavalAvatar :person="player" :size="23" /><strong :title="player.name">{{ player.name || '空席' }}</strong><span class="player-outcome">{{ resultLabel(player, index) }}</span></div>
              <dl><div><dt>命中</dt><dd>{{ statsBy(index).hits }}</dd></div><div><dt>攻击格</dt><dd>{{ statsBy(index).shots }}</dd></div><div><dt>剩余舰船</dt><dd>{{ statsBy(index).shipsRemaining }}</dd></div></dl>
            </article>
          </div>
          <section v-if="canReadProgression" class="settlement-account" aria-label="本局账户奖励" :data-tasks-state="tasksRevealed ? 'revealed' : 'waiting'">
            <div v-if="hasWager" class="settlement-pot-row" :class="{'is-visible':potOpen}" :data-pot-state="potOpen?'playing':'hidden'" :aria-hidden="!potOpen"><div class="settlement-pot-clip"><div ref="potTarget" class="settlement-pot-stage" /></div></div>
            <div class="settlement-progression" :class="{'has-tasks':showRoundTasks,'own-right':resultYou===1}">
              <div class="settlement-rank" aria-label="军衔与等级分">
                <div class="settlement-account-row">
            <NavalWagerSettlement v-if="cashPresentation" split :pot-target="potTarget" :key="wagerGameKey" :wager="wager" :seat="resultYou" :balance="wagerBalance" :game-key="wagerGameKey" :account-key="wagerAccount" :reward="rewardPresentation" :paused="presentationPaused" @pot-visible="potOpen = $event" @complete="cashFinished = true" @reward-complete="onCashRewardComplete" />
                  <span v-if="wager?.status === 'EXIT_PARTIAL'" class="wager-exit-rule" tabindex="0" title="退出方退还50%，对手获补偿10%，其余40%作废。" aria-label="退出方退还50%，对手获补偿10%，其余40%作废。"><Info :size="13" /></span>
                </div>
                <NavalAccountReward v-if="rankReceipt" :key="rankReceipt.id" :receipt="rankReceipt" :settled="!lastReward?.score" embedded external-wallet :balance="profile?.coins" :paused="presentationPaused" @complete="onExperienceComplete" />
                <AppSkeleton v-else variant="list" :rows="1" label="正在读取军衔" />
            <p v-if="profile?.promotion || ownReward?.promotion" class="reward-promotion"><Flag :size="12" />{{ (profile?.promotion || ownReward.promotion).targetRank }}晋升考核 {{ (profile?.promotion || ownReward.promotion).currentWins }} / {{ (profile?.promotion || ownReward.promotion).requiredWins }}</p>
              </div>
              <div class="settlement-round-tasks">
            <Transition name="round-tasks"><div v-if="showRoundTasks" class="settlement-tasks-heading"><h4><CheckCircle2 :size="13" />本局任务 <small>{{ completedTasks.length }}/{{ roundTasks.length }}</small></h4><span v-if="claimableCount">{{ claimableCount }} 项待领取</span></div></Transition>
            <Transition name="round-tasks"><div v-if="showRoundTasks" class="settlement-task-scroll" tabindex="0" aria-label="本局任务列表">
              <AppSkeleton v-if="loading && !profile" variant="list" :rows="2" label="正在读取任务" />
              <ul v-else class="settlement-tasks"><li v-for="task in roundTasks" :key="taskKey(task)" :class="{ claimed:task.claimed,completed:task.completed }" :data-task-id="task.id" :data-task-state="task.completed?'completed':'available'"><CheckCircle2 v-if="task.completed" :size="15" class="task-state" /><Circle v-else :size="15" class="task-state task-incomplete" /><div><h4>{{ task.title }}</h4><p><span data-task-coins><Coins :size="11" />{{ task.coins }}</span><span><TrendingUp :size="11" />{{ task.score }} 等级分</span><time v-if="task.day && profile?.day && task.day !== profile.day">{{ task.day }}</time></p></div><button v-if="task.canClaim" type="button" class="settlement-claim" :aria-label="`领取${task.title}`" :disabled="Boolean(claiming) || loading || !tasksRevealed || Boolean(cashPresentation && !cashFinished)" @click="claimTask(task, $event)"><LoaderCircle v-if="claiming === taskKey(task)" :size="13" class="claim-spinner" /><Gift v-else :size="13" />领取</button><span v-else-if="task.claimed" class="task-claimed"><Check :size="12" />已领取</span><span v-else-if="!task.completed" class="task-progress" aria-label="任务进度">{{ navalRoundTaskProgress(task) }}</span><LoaderCircle v-else-if="loading" :size="13" class="claim-spinner" /></li></ul>
            </div></Transition>
            <p v-if="taskError" class="task-error" role="alert">{{ taskError }}<button type="button" class="settlement-icon task-retry" aria-label="重新读取任务" title="重新读取任务" :disabled="loading || Boolean(claiming)" @click="refreshTasks"><RefreshCw :size="14" /></button></p>
              </div>
            </div>
          </section>
          <section v-if="room.mode === 'MULTIPLAYER'" class="next-round-seats" aria-label="下一局席位">
            <header><h3>下一局</h3><span class="settlement-audience-count"><Eye :size="12" />{{ room.spectators?.length || 0 }}</span><label v-if="!spectating && self?.registered && self?.canSkipWager" class="next-wager-choice"><input type="checkbox" role="switch" :checked="skipWager" :disabled="busy || self.continueRequested" @change="$emit('wager-choice', $event.target.checked)" />下一局不下注</label></header>
            <div class="next-round-players"><div v-for="(player, index) in room.players" :key="player.id || index" class="live-seat" :class="{ ready: player.continueRequested && !player.left, vacant: player.left || player.occupied === false }"><NavalAvatar :person="player" :size="14" /><strong :title="player.name">{{ player.left || player.occupied === false ? '空席' : player.name }}</strong><button v-if="spectating && (player.left || player.occupied === false)" type="button" class="claim-seat" :disabled="busy" @click="$emit('claim', index)"><Armchair :size="13" />加入席位</button><span v-else class="continue-status"><Check v-if="player.continueRequested && !player.left" :size="12" />{{ player.left || player.occupied === false ? '等待加入' : player.continueRequested ? '准备就绪' : '等待选择' }}</span></div></div>
          </section>
        </div>
      </div>
      <footer><button class="settlement-secondary" :disabled="busy" @click="$emit('exit')"><LogOut :size="16" />返回港口</button><button class="settlement-secondary" @click="$emit('close')"><Eye :size="16" />查看棋盘</button><button v-if="!spectating && self && !self.left && self.occupied !== false" :class="self.continueRequested ? 'settlement-secondary' : 'settlement-primary'" :disabled="busy" @click="$emit('continue', !self.continueRequested)"><X v-if="self.continueRequested" :size="17" /><RotateCcw v-else :size="17" />{{ room.mode === 'AI' ? '再来一局' : self.continueRequested ? '取消准备' : '继续下一局' }}<span v-if="room.mode === 'MULTIPLAYER' && nextWager > 0 && !self.continueRequested" class="next-wager-amount">押注 {{ nextWager }}</span></button></footer>
    </div>
  </section>
</template>

<script setup>
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { Anchor, Armchair, Check, CheckCircle2, Circle, Coins, Eye, Flag, Gift, Info, LoaderCircle, LogOut, RefreshCw, RotateCcw, TrendingUp, Trophy, X } from 'lucide-vue-next'
import { claimNavalTaskReward } from '../../games/navalTaskRewards'
import { ownNavalProgression as profile, navalProgressionLoading as loading, navalProgressionError, refreshNavalProgression, syncNavalProgressionAccount } from '../../games/navalProgression'
import NavalAvatar from './NavalAvatar.vue'
import NavalWagerSettlement from './NavalWagerSettlement.vue'
import NavalAccountReward from './NavalAccountReward.vue'
import AppSkeleton from '../../components/AppSkeleton.vue'
import { navalRankSnapshot } from '../../games/navalRankPromotions'
import { navalRoundTasksForMatch, navalRoundTaskProgress, navalRoundTaskDay, navalRoundTaskKey } from '../../games/navalRoundTasks'
import { NAVAL_RECONNECT_MS, NAVAL_RECONNECT_LIMIT } from '../../games/navalBattlePause'
const props = defineProps({ room: { type: Object, required: true }, accountKey:String, busy: Boolean, skipWager: Boolean, presentationPaused:Boolean, rewardPresentation:Object, rewardsPending:Boolean })
const emit = defineEmits(['close', 'continue', 'exit', 'claim', 'wager-choice', 'progression', 'cash-busy', 'reward-complete'])
const activeAccount=ref(syncNavalProgressionAccount())
const settlementAccount=props.accountKey ?? activeAccount.value
const claiming = ref(''), taskError = ref('')
const wagerBalance = ref(null)
const cashFinished = ref(false), lastReward = ref(null)
const potTarget=ref(null),potOpen=ref(false)
const tasksRevealed = ref(false), roundTaskDay = ref('')
const completedRewardId = ref('')
const experienceDone = new Set(), cashRewardsDone = new Set()
let balanceGeneration = 0, disposed = false, balanceAccount = ''
const spectating = computed(() => props.room.role === 'SPECTATOR' || props.room.you < 0)
const self = computed(() => props.room.players[props.room.you])
const nextWager = computed(() => self.value?.canSkipWager && props.skipWager ? 0 : Number(self.value?.nextWager ?? props.room.nextWager ?? 0))
const result = computed(() => props.room.lastResult || props.room.settlement || props.room)
const participants = computed(() => result.value.players || [])
const resultYou = computed(() => {
  if (spectating.value) return -1
  if (typeof props.room.resultYou === 'number') return props.room.resultYou
  if (result.value === props.room) return props.room.you
  return self.value?.id ? participants.value.findIndex(player => player.id === self.value.id) : -1
})
const ownReward = computed(() => result.value.progression?.players?.find(player => player.seat === resultYou.value))
const rankReceipt = computed(() => {
  if(lastReward.value?.score > 0)return lastReward.value
  const snapshot=navalRankSnapshot(profile.value) || navalRankSnapshot(ownReward.value)
  return snapshot ? {id:`summary:${wagerGameKey.value}:${snapshot.level}:${snapshot.score}`,score:0,coins:0,before:snapshot,after:snapshot} : null
})
const canReadProgression = computed(() => !spectating.value && Boolean(participants.value[resultYou.value]?.registered) && Boolean(activeAccount.value) && activeAccount.value===settlementAccount)
const taskKey = task => navalRoundTaskKey(task, roundTaskDay.value)
const roundTasks = computed(() => navalRoundTasksForMatch(ownReward.value?.completedTasks, profile.value, {day:roundTaskDay.value,mode:result.value.mode || props.room.mode,ruleSet:result.value.ruleSet || props.room.ruleSet || 'CLASSIC'}))
const completedTasks = computed(() => roundTasks.value.filter(task=>task.completed))
const showRoundTasks = computed(() => tasksRevealed.value && roundTasks.value.length > 0)
const claimableCount = computed(() => completedTasks.value.filter(task => task.canClaim).length)
async function refreshTasks() {
  if (!canReadProgression.value) return
  const request = ++balanceGeneration, token = syncNavalProgressionAccount()
  if(token!==settlementAccount){invalidateAccount(token);return}
  balanceAccount = token
  wagerBalance.value = null
  taskError.value = ''
  const latest = await refreshNavalProgression({ force:true })
  if (disposed || request !== balanceGeneration || token !== syncNavalProgressionAccount()) return
  if (latest && Number.isFinite(Number(latest.coins))) wagerBalance.value = Number(latest.coins)
  taskError.value = navalProgressionError.value
}
async function claimTask(task, event) {
  const token = syncNavalProgressionAccount()
  if(!token || token!==settlementAccount || token!==balanceAccount){invalidateAccount(token);return}
  if (claiming.value || loading.value || !tasksRevealed.value || !task.canClaim || cashPresentation.value && !cashFinished.value) return
  claiming.value = taskKey(task)
  taskError.value = ''
  try {
    const sourceElement = event?.currentTarget?.closest('li')?.querySelector('[data-task-coins]')
    const awarded = await claimNavalTaskReward(task, {sourceElement})
    if (syncNavalProgressionAccount() === token && awarded) emit('progression', awarded)
  } catch (error) {
    if (syncNavalProgressionAccount() === token) taskError.value = error.message || '领取失败，请重试'
  } finally { claiming.value = '' }
}
watch(() => `${props.room.id}:${result.value.gameId || ''}:${result.value.round || props.room.round}:${canReadProgression.value}`, refreshTasks, { immediate:true })
const wager = computed(() => result.value.wager)
const hasWager = computed(() => wager.value?.amount > 0 || wager.value?.pot > 0)
const wagerAccount = computed(() => syncNavalProgressionAccount() || '')
const wagerGameKey = computed(() => `${props.room.id}:${result.value.gameId || ''}:${result.value.round || props.room.round}:${resultYou.value}`)
const cashPresentation = computed(() => canReadProgression.value)
watch(wagerGameKey, () => { cashFinished.value = false; potOpen.value = false; lastReward.value = null; completedRewardId.value = ''; tasksRevealed.value = false; roundTaskDay.value = navalRoundTaskDay(result.value.finishedAt || props.room.finishedAt) || ownReward.value?.completedTasks?.find(task => task.day)?.day || profile.value?.day || ''; experienceDone.clear(); cashRewardsDone.clear() }, {immediate:true})
watch(profile, value => { if (!roundTaskDay.value && value?.day) roundTaskDay.value = value.day })
watch([cashPresentation, cashFinished, () => props.rewardsPending, () => props.rewardPresentation, completedRewardId, () => props.presentationPaused, canReadProgression, loading, taskError], () => {
  if (canReadProgression.value && !loading.value && !taskError.value && !props.rewardsPending && !props.presentationPaused && (!props.rewardPresentation || completedRewardId.value === props.rewardPresentation.id) && (!cashPresentation.value || cashFinished.value)) tasksRevealed.value = true
}, {immediate:true, flush:'post'})
watch([cashPresentation, cashFinished, taskError], () => emit('cash-busy', Boolean(cashPresentation.value && !cashFinished.value && !taskError.value)), {immediate:true})
watch(() => props.rewardPresentation, event => { if (event && (!event.account || event.account===settlementAccount) && syncNavalProgressionAccount()===settlementAccount) lastReward.value = event }, {immediate:true})
function finishReward(id) {
  const event = props.rewardPresentation
  if (event?.id !== id || !experienceDone.has(id) && !(cashPresentation.value && !event.score) || cashPresentation.value && event.coins > 0 && !cashRewardsDone.has(id)) return
  completedRewardId.value = id
  emit('reward-complete', id)
}
function onExperienceComplete(id) { experienceDone.add(id); finishReward(id) }
function onCashRewardComplete(id) { cashRewardsDone.add(id); finishReward(id) }
watch([profile, loading, navalProgressionError], ([value, pending, failure]) => {
  if (disposed || pending || !balanceAccount || balanceAccount !== syncNavalProgressionAccount() || !canReadProgression.value) return
  taskError.value = failure
  if (!failure && value && Number.isFinite(Number(value.coins))) wagerBalance.value = Number(value.coins)
})
function invalidateAccount(token) { activeAccount.value=token;balanceGeneration++;tasksRevealed.value=false;wagerBalance.value=null;lastReward.value=null;emit('close') }
function accountChanged(event) { if(event.key==='navy_token' || event.key===null){const token=syncNavalProgressionAccount();if(token!==settlementAccount)invalidateAccount(token)} }
onMounted(()=>{window.addEventListener('storage',accountChanged);if(activeAccount.value!==settlementAccount)emit('close')})
onUnmounted(() => { disposed = true; balanceGeneration++; window.removeEventListener('storage',accountChanged);emit('cash-busy', false) })
const title = computed(() => result.value.winner == null ? '本局已结束' : resultYou.value < 0 ? `${participants.value[result.value.winner]?.name || '指挥官'} 获胜` : result.value.winner === resultYou.value ? '海域大捷' : '舰队失守')
const reason = computed(() => {
  const left = participants.value.find(player => player.left)
  if (result.value.finishReason === 'DISCONNECTED') {
    const loser=left || participants.value[result.value.winner===0?1:0]
    if(Number(loser?.reconnectCount)>NAVAL_RECONNECT_LIMIT)return `${loser?.name || '一位指挥官'} 本局第4次断线，直接判负`
    return loser?.reconnectCount==null ? `${loser?.name || '一位指挥官'} 离线后未及时重连，判负` : `${loser?.name || '一位指挥官'} 离线超过${NAVAL_RECONNECT_MS / 1000}秒未重连，判负`
  }
  if (result.value.finishReason === 'CANCELLED') return '战斗尚未开始，已取消，不计入战绩'
  if (result.value.finishReason === 'PLAYER_LEFT' || left) return `${left?.name || '一位指挥官'} 已离开，本局提前结束`
  if (result.value.finishReason === 'EXPIRED') return '房间已到期，舰队返回港口'
  return ''
})
function statsBy(index) {
  const player = participants.value[index] || {}, shots = participants.value[1 - index]?.shots || []
  return {
    hits: typeof player.hits === 'number' ? player.hits : Array.isArray(shots) ? shots.filter(shot => shot.result === 'hit').length : 0,
    shots: typeof player.shots === 'number' ? player.shots : Array.isArray(shots) ? shots.length : 0,
    shipsRemaining: typeof player.shipsRemaining === 'number' ? player.shipsRemaining : player.occupied === false ? '--' : (props.room.ruleSet === 'MILITARY' ? player.ships?.length || 0 : 10) - sunk(player)
  }
}
function resultLabel(player, index) { return result.value.winner == null ? '行动结束' : result.value.winner === index ? '胜利' : player.left ? '已离席' : '战败' }
function sunk(player) { return (player.ships || []).filter(ship => ship.hits?.length >= ship.w * ship.h).length }
</script>

<style scoped>
.settlement-screen{position:fixed;inset:0;z-index:360;display:flex;align-items:center;justify-content:center;box-sizing:border-box;height:100dvh;max-height:100dvh;overflow:hidden;background:#f4f8faf7;backdrop-filter:blur(5px);padding:24px max(24px,env(safe-area-inset-right)) max(24px,env(safe-area-inset-bottom)) max(24px,env(safe-area-inset-left));color:#345562}
.settlement-inner{width:100%;max-width:1040px;height:100%;max-height:680px;min-height:0;margin:auto;display:grid;grid-template-rows:auto minmax(0,1fr) auto;gap:18px}
.settlement-header{display:flex;align-items:center;gap:16px;min-width:0;min-height:54px;padding-bottom:14px;border-bottom:1px solid #d8e5e9}
.settlement-result-heading{display:flex;align-items:center;gap:12px;min-width:0}.settlement-result-heading>svg{flex-shrink:0;color:#c99446}.settlement-result-heading>div{min-width:0}.settlement-result-heading h2{display:flex;align-items:center;gap:6px;min-width:0;margin:0;font-size:23px;font-weight:700;line-height:1.3;color:#345965}.winner-name{max-width:260px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}.settlement-mode{display:flex;align-items:center;gap:5px;margin-top:5px;font-size:11px;color:#819aa4}.settlement-mode small{font-size:10px;margin-left:7px;color:#97aab2}.settlement-reason{flex:1;min-width:0;margin:0 0 0 12px;max-width:400px;font-size:11px;line-height:1.6;color:#8a9fa7;overflow-wrap:anywhere}.settlement-icon{flex-shrink:0;width:34px;height:34px;margin-left:auto;display:grid;place-items:center;background:transparent;color:#74909c;border-radius:4px}
.settlement-body,.settlement-content{min-width:0;min-height:0;overflow:hidden}.settlement-content{display:flex;flex-direction:column;gap:18px;height:100%}
.settlement-players{flex-shrink:0;display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:24px}.settlement-players article{display:grid;grid-template-columns:minmax(0,1fr) auto;align-items:center;gap:14px;min-width:0;min-height:60px;padding:0 0 12px;border-bottom:1px solid #dce7eb}
.player-heading{display:flex;align-items:center;gap:7px;min-width:0;font-size:12px}.player-heading :deep(.naval-avatar){width:26px;height:26px;flex-shrink:0}.player-heading>strong{overflow:hidden;text-overflow:ellipsis;white-space:nowrap;min-width:0}.player-heading>.player-outcome{margin-left:auto;font-size:10px;white-space:nowrap;flex-shrink:0;color:#9bacb3}.winner .player-heading>.player-outcome{color:#bc873e}
.settlement-players dl{display:grid;grid-template-columns:repeat(3,minmax(44px,1fr));padding:0;margin:0;gap:16px}.settlement-players dt{font-size:9px;line-height:1.5;color:#93a6ae;white-space:nowrap}.settlement-players dd{font-size:20px;line-height:1.35;margin:2px 0 0;color:#63828e;font-variant-numeric:tabular-nums}
.settlement-account{flex:1;display:flex;flex-direction:column;min-width:0;min-height:0}
.settlement-account-row{position:relative;display:flex;align-items:center;flex-shrink:0;height:78px;min-width:0;min-height:0}.settlement-account-row :deep(.wager-transfer){height:100%;min-height:0}
.settlement-pot-row{display:grid;grid-template-rows:0fr;flex-shrink:0;min-height:0;margin-bottom:0;opacity:0;pointer-events:none;transition:grid-template-rows .26s ease,margin-bottom .26s ease,opacity .22s ease}.settlement-pot-row.is-visible{grid-template-rows:1fr;margin-bottom:12px;opacity:1}.settlement-pot-clip{min-height:0;overflow:hidden}.settlement-pot-stage{display:flex;align-items:center;justify-content:center;height:64px}
.settlement-account-row :deep(.split-layout .transfer-wallet>span){font-size:9px;line-height:13px;color:#97aab1}
.settlement-pot-stage :deep(.transfer-pot),.settlement-account-row :deep(.transfer-wallet){grid-template-columns:22px auto;gap:3px 8px}.settlement-pot-stage :deep(.transfer-pot>svg),.settlement-account-row :deep(.transfer-wallet>svg){width:21px;height:21px}.settlement-pot-stage :deep(.transfer-pot>span),.settlement-account-row :deep(.transfer-wallet>span){font-size:10px;line-height:15px}
.settlement-account-row :deep(.transfer-wallet>output){font-size:24px;line-height:29px;font-weight:700;color:#ac7b32}.settlement-pot-stage :deep(.transfer-pot>strong){font-size:24px;line-height:32px;font-weight:650;color:#b58d50}.settlement-account-row :deep(.wallet-change){font-size:14px;margin-left:7px}
.wager-exit-rule{position:absolute;right:0;top:0;display:flex;color:#8da2a9;cursor:help}
.settlement-progression{flex:1;display:grid;grid-template-columns:minmax(0,.85fr) minmax(0,1.15fr);column-gap:32px;min-height:0;min-width:0}.settlement-progression.own-right{grid-template-columns:minmax(0,1.15fr) minmax(0,.85fr)}
.settlement-rank{grid-column:1;grid-row:1;display:flex;flex-direction:column;gap:16px;min-width:0;align-self:start}.own-right>.settlement-rank{grid-column:2}.own-right>.settlement-round-tasks{grid-column:1}
.settlement-rank :deep(.account-reward-presentation){min-height:58px}.settlement-rank :deep(.experience-gain){gap:12px}.settlement-rank :deep(.experience-gain>.naval-rank-badge){width:52px!important;height:52px!important}.settlement-rank :deep(.experience-track){max-width:none}.settlement-rank :deep(.experience-track strong){font-size:14px;margin-bottom:8px}.settlement-rank :deep(.experience-meter){height:6px}.settlement-rank :deep(.experience-add){font-size:13px}
.reward-promotion{display:flex;align-items:center;gap:5px;margin:10px 0 0;font-size:11px;line-height:1.6;color:#ad813d;overflow-wrap:anywhere}.reward-promotion>svg{flex-shrink:0}
.settlement-round-tasks{grid-column:2;grid-row:1;display:flex;flex-direction:column;min-width:0;min-height:0;overflow:hidden}
.settlement-tasks-heading{flex-shrink:0;display:flex;align-items:center;gap:8px;min-height:28px;margin-bottom:6px}.settlement-tasks-heading h4{display:flex;align-items:center;gap:6px;margin:0;font-weight:600;font-size:12px;color:#577b75}.settlement-tasks-heading h4>small{font-size:10px;font-weight:400;color:#8ca59c}.settlement-tasks-heading>span{margin-left:auto;font-size:10px;color:#b37d3d;white-space:nowrap}
.settlement-task-scroll{flex:1;min-height:0;overflow:auto;overscroll-behavior:contain;scrollbar-width:thin;scrollbar-color:#bdced3 transparent;outline-offset:-2px;padding-right:5px}.settlement-tasks{list-style:none;margin:0;padding:0}.settlement-tasks>li{display:flex;align-items:center;gap:9px;min-height:68px;padding:10px 0;box-sizing:border-box}.settlement-tasks>li+li{border-top:1px solid #e0e9ec}.settlement-tasks>li>div{flex:1;min-width:0}.settlement-tasks h4{font-weight:500;font-size:12px;line-height:1.55;margin:0;overflow-wrap:anywhere}.settlement-tasks p{display:flex;align-items:center;flex-wrap:wrap;gap:4px 12px;margin:5px 0 0;font-size:10px;color:#94a7ae}.settlement-tasks p>span{display:flex;align-items:center;gap:4px}.settlement-tasks p>span:first-child{color:#b28a51}.settlement-tasks time{font-size:9px}
.task-state{color:#6b9e87;flex-shrink:0}.task-incomplete{color:#b6c5ca}.task-progress{flex-shrink:0;min-width:44px;text-align:right;font-size:12px;font-variant-numeric:tabular-nums;color:#7e9aa6;white-space:nowrap}.settlement-tasks>li:not(.completed) h4{color:#6c8994}
.settlement-claim{display:flex;align-items:center;justify-content:center;gap:5px;flex-shrink:0;min-height:32px;min-width:64px;padding:6px 9px;border-radius:4px;background:var(--color-primary,#e39440);color:white;font-size:11px}.task-claimed{display:flex;align-items:center;gap:3px;flex-shrink:0;font-size:10px;color:#74a18d}.claim-spinner{animation:settlement-spin .8s linear infinite}
.task-error{display:flex;align-items:center;gap:5px;flex-shrink:0;margin:4px 0;font-size:10px;line-height:1.5;color:#b56450;overflow-wrap:anywhere}.task-retry{width:26px;height:26px}
.next-round-seats{display:grid;grid-template-columns:auto minmax(0,1fr);align-items:center;gap:18px;flex-shrink:0;min-width:0;margin-top:auto}.next-round-seats>header{display:flex;align-items:center;gap:9px;min-height:26px}.next-round-seats h3{font-size:10px;font-weight:500;color:#8199a3;margin:0;white-space:nowrap}.settlement-audience-count{display:flex;align-items:center;gap:3px;font-size:10px;color:#9aacb3}.next-wager-choice{display:flex;align-items:center;gap:5px;font-size:10px;color:#71868d;white-space:nowrap}.next-wager-choice input{width:13px;height:13px;accent-color:var(--color-primary)}
.next-round-players{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:24px}.live-seat{display:flex;align-items:center;gap:6px;min-width:0;min-height:26px;font-size:10px;color:#8aa0a9}.live-seat :deep(.naval-avatar){width:20px;height:20px;flex-shrink:0}.live-seat>strong{font-weight:400;min-width:0;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}.continue-status{display:flex;align-items:center;gap:3px;white-space:nowrap;font-size:10px;color:#9bacb3;margin-left:auto;flex-shrink:0}.live-seat.ready .continue-status{color:#56988f}.live-seat.vacant{color:#a4b4bb}.claim-seat{display:flex;align-items:center;gap:4px;min-height:25px;padding:4px 6px;background:#eef5f3;color:#538575;border-radius:4px;font-size:10px;flex-shrink:0;margin-left:auto}
.settlement-inner>footer{display:flex;gap:10px;justify-content:center;align-items:stretch;min-width:0;padding-top:3px}.settlement-primary,.settlement-secondary{display:flex;align-items:center;justify-content:center;gap:7px;border:1px solid transparent;border-radius:5px;min-height:42px;padding:9px 18px;font-size:12px;line-height:1.4;min-width:0}.settlement-primary{background:#e39440;color:#fff}.settlement-secondary{background:#fff;border-color:#d3e1e7;color:#688690}.settlement-primary>svg,.settlement-secondary>svg{flex-shrink:0}.next-wager-amount{font-size:10px;white-space:nowrap;opacity:.85}button:disabled{opacity:.5;cursor:default}
@media(max-height:600px) and (orientation:landscape){
 .settlement-account-row :deep(.split-layout .transfer-wallet>span){font-size:8px;line-height:10px}
 .settlement-screen{padding:8px max(12px,env(safe-area-inset-right)) max(8px,env(safe-area-inset-bottom)) max(12px,env(safe-area-inset-left))}.settlement-inner{gap:8px;max-height:none}.settlement-header{min-height:44px;padding-bottom:7px;gap:10px}.settlement-result-heading{gap:8px}.settlement-result-heading>svg{width:24px;height:24px}.settlement-result-heading h2{font-size:19px}.settlement-mode{font-size:9px;margin-top:2px}.settlement-mode small{font-size:9px}.settlement-icon{width:28px;height:28px}.settlement-reason{font-size:10px;line-height:1.4;max-width:45%;margin-left:8px}
 .settlement-content{gap:9px}.settlement-players{gap:18px}.settlement-players article{min-height:45px;padding-bottom:7px;gap:10px}.player-heading{font-size:11px;gap:5px}.player-heading :deep(.naval-avatar){width:22px;height:22px}.player-heading>.player-outcome{font-size:9px}.settlement-players dl{grid-template-columns:repeat(3,minmax(34px,1fr));gap:12px}.settlement-players dt{font-size:8px}.settlement-players dd{font-size:16px;margin-top:1px}
 .settlement-account-row{height:52px}.settlement-rank{gap:10px}.settlement-pot-stage{height:48px}.settlement-pot-stage :deep(.transfer-pot),.settlement-account-row :deep(.transfer-wallet){grid-template-columns:17px auto;gap:1px 6px}.settlement-pot-stage :deep(.transfer-pot>svg),.settlement-account-row :deep(.transfer-wallet>svg){width:17px;height:17px}.settlement-pot-stage :deep(.transfer-pot>span),.settlement-account-row :deep(.transfer-wallet>span){font-size:9px;line-height:12px}.settlement-account-row :deep(.transfer-wallet>output){font-size:20px;line-height:24px}.settlement-pot-stage :deep(.transfer-pot>strong){font-size:19px;line-height:25px}.settlement-account-row :deep(.wallet-change){font-size:11px;margin-left:5px}
 .settlement-progression{column-gap:22px}.settlement-rank{padding-top:2px}.settlement-rank :deep(.account-reward-presentation){min-height:42px}.settlement-rank :deep(.experience-gain){gap:8px}.settlement-rank :deep(.experience-gain>.naval-rank-badge){width:36px!important;height:36px!important}.settlement-rank :deep(.experience-track strong){font-size:12px;margin-bottom:6px}.settlement-rank :deep(.experience-meter){height:5px}.settlement-rank :deep(.experience-add){font-size:11px}.reward-promotion{font-size:9px;margin-top:6px}
 .settlement-tasks-heading{min-height:23px;margin-bottom:2px}.settlement-tasks-heading h4{font-size:11px}.settlement-tasks-heading>span{font-size:9px}.settlement-tasks>li{min-height:53px;padding:6px 0;gap:7px}.settlement-tasks h4{font-size:11px}.settlement-tasks p{font-size:9px;margin-top:3px;gap:3px 9px}.settlement-claim{min-height:29px;min-width:57px;padding:5px 7px;font-size:10px}.task-progress{font-size:11px;min-width:40px}.task-state{width:13px}.task-claimed{font-size:9px}.task-error{font-size:9px}
 .next-round-seats{gap:12px}.next-round-seats>header{gap:7px;min-height:22px}.next-round-seats h3,.next-wager-choice{font-size:9px}.next-round-players{gap:18px}.live-seat{font-size:9px;min-height:22px;gap:5px}.live-seat :deep(.naval-avatar){width:18px;height:18px}.continue-status{font-size:9px}.claim-seat{min-height:22px;font-size:9px}.settlement-inner>footer{gap:8px;padding-top:0}.settlement-primary,.settlement-secondary{min-height:35px;padding:7px 14px;font-size:11px}
}
@media(max-height:350px) and (orientation:landscape){
 .settlement-account-row :deep(.split-layout .transfer-wallet>span){font-size:7px;line-height:9px}
 .settlement-inner{gap:6px}.settlement-header{min-height:38px;padding-bottom:5px}.settlement-result-heading h2{font-size:17px}.settlement-result-heading>svg{width:21px;height:21px}.settlement-content{gap:7px}.settlement-players article{min-height:38px;padding-bottom:5px;gap:7px}.player-heading{font-size:10px}.player-heading :deep(.naval-avatar){width:20px;height:20px}.settlement-players dl{gap:8px;grid-template-columns:repeat(3,minmax(28px,1fr))}.settlement-players dt{font-size:7px}.settlement-players dd{font-size:14px}
 .settlement-account-row{height:39px}.settlement-rank{gap:8px}.settlement-pot-stage{height:38px}.settlement-pot-stage :deep(.transfer-pot),.settlement-account-row :deep(.transfer-wallet){display:grid;grid-template-columns:15px auto;gap:0 5px}.settlement-pot-stage :deep(.transfer-pot>svg),.settlement-account-row :deep(.transfer-wallet>svg){width:15px;height:15px}.settlement-pot-stage :deep(.transfer-pot>span),.settlement-account-row :deep(.transfer-wallet>span){font-size:8px;line-height:10px}.settlement-account-row :deep(.transfer-wallet>output){font-size:17px;line-height:21px}.settlement-pot-stage :deep(.transfer-pot>strong){font-size:17px;line-height:22px}
 .settlement-rank :deep(.experience-gain>.naval-rank-badge){width:31px!important;height:31px!important}.settlement-rank :deep(.experience-track strong){font-size:11px;margin-bottom:5px}.settlement-rank :deep(.account-reward-presentation){min-height:35px}.settlement-tasks-heading{min-height:21px}.settlement-tasks>li{min-height:49px;padding:5px 0}.settlement-tasks h4{font-size:10px}.settlement-tasks p{font-size:8px}.settlement-primary,.settlement-secondary{min-height:32px}.next-round-seats>header,.live-seat{min-height:20px}
}
@media(max-width:700px){.settlement-players{gap:14px}.settlement-players article{gap:7px}.settlement-players dl{gap:7px}.player-heading>.player-outcome{display:none}.settlement-progression{column-gap:18px}}
@media(max-width:600px) and (orientation:portrait){
 .settlement-account-row :deep(.split-layout .transfer-wallet>span){font-size:8px;line-height:10px}
 .settlement-screen{padding:12px max(12px,env(safe-area-inset-right)) max(12px,env(safe-area-inset-bottom)) max(12px,env(safe-area-inset-left))}.settlement-inner{gap:12px;max-height:none}.settlement-header{min-height:46px;padding-bottom:10px;gap:8px}.settlement-result-heading{gap:8px}.settlement-result-heading h2{font-size:21px}.settlement-result-heading>svg{width:25px;height:25px}.settlement-reason{font-size:9px;margin-left:0;max-width:35%}
 .settlement-content{gap:12px}.settlement-players article{display:flex;flex-direction:column;align-items:stretch;gap:8px;padding-bottom:10px}.player-heading{font-size:11px}.player-heading :deep(.naval-avatar){width:23px;height:23px}.settlement-players dl{grid-template-columns:repeat(3,minmax(0,1fr));gap:5px}.settlement-players dt{font-size:8px}.settlement-players dd{font-size:18px}
 .settlement-account-row{height:62px}.settlement-rank{gap:12px}.settlement-pot-stage{height:52px}.settlement-account-row :deep(.transfer-wallet>output){font-size:20px;line-height:24px}.settlement-pot-stage :deep(.transfer-pot>strong){font-size:20px;line-height:28px}.settlement-account-row :deep(.wallet-change){font-size:11px;margin-left:3px}.settlement-progression{grid-template-columns:repeat(2,minmax(0,1fr));gap:13px}.settlement-progression.own-right{grid-template-columns:repeat(2,minmax(0,1fr))}
 .settlement-rank :deep(.experience-gain){gap:6px}.settlement-rank :deep(.experience-gain>.naval-rank-badge){width:32px!important;height:32px!important}.settlement-rank :deep(.experience-track strong){font-size:11px;margin-bottom:5px}.settlement-rank :deep(.experience-meter){height:5px}.settlement-rank :deep(.experience-add){font-size:9px}
 .settlement-tasks-heading{flex-wrap:wrap;gap:2px 6px}.settlement-tasks-heading h4{font-size:11px}.settlement-tasks-heading>span{margin-left:0;font-size:9px}.settlement-claim{min-width:42px;padding:5px;gap:2px;font-size:10px}.settlement-tasks>li{gap:5px}.settlement-tasks h4{font-size:10px}.settlement-tasks p{font-size:9px;gap:3px 6px}.task-state{display:none}.task-progress{font-size:10px;min-width:30px}.task-claimed{font-size:9px}
 .next-round-seats{display:block}.next-round-seats>header{min-height:22px;gap:8px}.next-round-players{gap:12px}.live-seat{font-size:9px;gap:4px}.live-seat :deep(.naval-avatar){width:18px;height:18px}.continue-status{font-size:8px}.settlement-inner>footer{gap:6px;padding-top:0}.settlement-primary,.settlement-secondary{flex:1;flex-wrap:wrap;min-height:42px;padding:7px 6px;font-size:10px;gap:4px}.next-wager-amount{font-size:9px}
}
.round-tasks-enter-active{transition:opacity .24s ease,transform .24s ease}.round-tasks-enter-from{opacity:0;transform:translateY(6px)}
@media(prefers-reduced-motion:reduce){.settlement-pot-row{transition:none}.claim-spinner{animation:none}.round-tasks-enter-active{transition:none}}@keyframes settlement-spin{to{transform:rotate(360deg)}}
</style>
