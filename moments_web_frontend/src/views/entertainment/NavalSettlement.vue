<template>
  <section class="settlement-screen" role="dialog" aria-modal="true" aria-label="战局结算">
    <div class="settlement-inner">
      <header><span><Anchor :size="18" />{{ room.ruleSet === 'MILITARY' ? '军事模式' : '经典模式' }} <small>第 {{ result.round || room.round || 1 }} 局</small></span><button class="settlement-icon" title="查看棋盘" aria-label="查看棋盘" @click="$emit('close')"><X :size="20" /></button></header>
      <div class="settlement-title"><Trophy :size="40" /><span>行动结束</span><h2 :title="title"><template v-if="resultYou < 0 && result.winner != null"><span class="winner-name">{{ participants[result.winner]?.name || '指挥官' }}</span><span class="winner-outcome"> 获胜</span></template><template v-else>{{ title }}</template></h2><p>{{ reason }}</p></div>
      <div class="settlement-players">
        <article v-for="(player, index) in participants" :key="player.id || index" :class="{ winner: result.winner === index }">
          <div class="player-heading"><NavalAvatar :person="player" :size="23" /><strong>{{ player.name || '空席' }}</strong><span>{{ resultLabel(player, index) }}</span></div>
          <dl><div><dt>命中</dt><dd>{{ statsBy(index).hits }}</dd></div><div><dt>攻击格</dt><dd>{{ statsBy(index).shots }}</dd></div><div><dt>剩余舰船</dt><dd>{{ statsBy(index).shipsRemaining }}</dd></div></dl>
        </article>
      </div>
      <section v-if="room.mode === 'MULTIPLAYER'" class="next-round-seats" aria-label="下一局席位">
        <h3>下一局</h3>
        <div v-for="(player, index) in room.players" :key="player.id || index" class="live-seat" :class="{ ready: player.continueRequested && !player.left, vacant: player.left || player.occupied === false }">
          <span class="seat-label">{{ index === 0 ? '一号席位' : '二号席位' }}</span><NavalAvatar :person="player" :size="14" /><strong>{{ player.left || player.occupied === false ? '空席' : player.name }}</strong>
          <span class="continue-status"><Check v-if="player.continueRequested && !player.left" :size="14" />{{ player.left || player.occupied === false ? '等待加入' : player.continueRequested ? '准备就绪' : '等待选择' }}</span>
        </div>
      </section>
      <NavalAudience v-if="room.mode === 'MULTIPLAYER'" :room="room" :busy="busy" @claim="$emit('claim', $event)" />
      <footer><button class="settlement-secondary" :disabled="busy" @click="$emit('exit')"><LogOut :size="16" />返回港口</button><button class="settlement-secondary" @click="$emit('close')"><Eye :size="16" />查看棋盘</button><button v-if="!spectating && self && !self.left && self.occupied !== false" :class="self.continueRequested ? 'settlement-secondary' : 'settlement-primary'" :disabled="busy" @click="$emit('continue', !self.continueRequested)"><X v-if="self.continueRequested" :size="17" /><RotateCcw v-else :size="17" />{{ room.mode === 'AI' ? '再来一局' : self.continueRequested ? '取消准备' : '继续下一局' }}</button></footer>
    </div>
  </section>
</template>
<script setup>
import { computed } from 'vue'
import { Anchor, Check, Eye, LogOut, RotateCcw, Trophy, X } from 'lucide-vue-next'
import NavalAvatar from './NavalAvatar.vue'
import NavalAudience from './NavalAudience.vue'
const props = defineProps({ room: { type: Object, required: true }, busy: Boolean })
defineEmits(['close', 'continue', 'exit', 'claim'])
const spectating = computed(() => props.room.role === 'SPECTATOR' || props.room.you < 0)
const self = computed(() => props.room.players[props.room.you])
const result = computed(() => props.room.lastResult || props.room.settlement || props.room)
const participants = computed(() => result.value.players || [])
const resultYou = computed(() => {
  if (spectating.value) return -1
  if (typeof props.room.resultYou === 'number') return props.room.resultYou
  if (result.value === props.room) return props.room.you
  return self.value?.id ? participants.value.findIndex(player => player.id === self.value.id) : -1
})
const title = computed(() => result.value.winner == null ? '本局已结束' : resultYou.value < 0 ? `${participants.value[result.value.winner]?.name || '指挥官'} 获胜` : result.value.winner === resultYou.value ? '海域大捷' : '舰队失守')
const reason = computed(() => {
  const left = participants.value.find(player => player.left)
  if (result.value.finishReason === 'DISCONNECTED') return `${left?.name || '一位指挥官'} 离线超过 30 秒未重连，判负`
  if (result.value.finishReason === 'CANCELLED') return '战斗尚未开始，已取消，不计入战绩'
  if (result.value.finishReason === 'PLAYER_LEFT' || left) return `${left?.name || '一位指挥官'} 已离开，本局提前结束`
  if (result.value.finishReason === 'EXPIRED') return '房间已到期，舰队返回港口'
  return result.value.winner == null ? '舰队返回港口' : '本次海域行动已完成'
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
.settlement-screen{position:fixed;inset:0;z-index:360;overflow:auto;background:#f4f8faf7;backdrop-filter:blur(5px);padding:24px;color:#345562}.settlement-inner{max-width:900px;margin:auto}.settlement-inner>header{display:flex;justify-content:space-between;align-items:center;border-bottom:1px solid #d8e5e9;padding-bottom:14px}.settlement-inner>header>span{display:flex;align-items:center;gap:9px;font-weight:700;font-size:15px}.settlement-inner small{font-size:11px;font-weight:400;color:#8ca1a9;margin-left:6px}.settlement-icon{width:34px;height:34px;display:grid;place-items:center;background:transparent;color:#74909c;border-radius:4px}.settlement-title{text-align:center;padding:35px 0 28px}.settlement-title>svg{color:#d29b52;margin-bottom:10px}.settlement-title>span{display:block;font-size:11px;color:#9aafb6;margin-bottom:9px}.settlement-title h2{font-size:30px;font-weight:700}.settlement-title p{margin-top:9px;color:#849ca5;font-size:12px}.settlement-players{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:22px}.settlement-players article{border:1px solid #d8e4e9;border-radius:6px;padding:20px;background:#fff}.settlement-players article.winner{border-color:#e6ba83}.player-heading{display:flex;align-items:center;gap:11px;font-size:14px}.player-heading :deep(.naval-avatar){width:42px;height:42px}.player-heading>strong{overflow-wrap:anywhere;min-width:0}.player-heading>span{margin-left:auto;color:#99aab1;font-size:11px;white-space:nowrap}.winner .player-heading>span{color:#c38b46}.settlement-players dl{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));padding-top:20px;gap:12px}.settlement-players dt{font-size:10px;color:#8fa3ab}.settlement-players dd{font-size:23px;margin:5px 0 0}.continue-status{font-size:10px;color:#88a099;display:flex;align-items:center;gap:5px;margin-top:16px;min-height:15px}.settlement-inner>footer{display:flex;gap:9px;justify-content:center;flex-wrap:wrap;margin:27px 0}.settlement-primary,.settlement-secondary{display:flex;align-items:center;justify-content:center;gap:7px;border:1px solid transparent;border-radius:5px;min-height:40px;padding:8px 15px;font-size:13px}.settlement-primary{background:#e39440;color:#fff}.settlement-secondary{background:#fff;border-color:#d3e1e7;color:#688690}button:disabled{opacity:.5;cursor:default}@media(max-height:600px){.settlement-screen{padding:10px 20px}.settlement-inner>header{padding-bottom:4px}.settlement-title{padding:13px 0}.settlement-title>svg{width:25px;height:25px;margin:0}.settlement-title>span{display:none}.settlement-title h2{font-size:22px;margin-top:4px}.settlement-title p{font-size:10px;margin:5px 0}.settlement-players{gap:12px}.settlement-players article{padding:11px 14px}.player-heading{font-size:12px;gap:7px}.player-heading :deep(.naval-avatar){width:30px;height:30px}.settlement-players dl{padding-top:10px;gap:8px}.settlement-players dd{font-size:18px;margin-top:3px}.continue-status{margin-top:7px}.settlement-inner>footer{margin:14px 0}.settlement-primary,.settlement-secondary{min-height:32px;font-size:11px;padding:6px 11px}}@media(max-width:500px) and (orientation:portrait){.settlement-screen{padding:14px}.settlement-players{gap:10px}.settlement-players article{padding:12px}.player-heading{flex-wrap:wrap;font-size:12px}.settlement-players dl{gap:8px}.settlement-title h2{font-size:25px}}
.settlement-title h2{overflow-wrap:anywhere}.next-round-seats{display:grid;grid-template-columns:50px repeat(2,minmax(0,1fr));align-items:center;gap:20px;padding:15px 0;margin-top:20px;border-top:1px solid #dce6ea}.next-round-seats h3{font-size:12px;font-weight:600;color:#718e99}.live-seat{display:flex;align-items:center;gap:8px;min-width:0;font-size:11px;color:#6e8995}.live-seat .seat-label{font-size:10px;color:#97aab2;white-space:nowrap}.live-seat :deep(.naval-avatar){width:25px;height:25px;flex-shrink:0}.live-seat>strong{font-weight:600;min-width:0;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}.live-seat .continue-status{margin:0 0 0 auto;white-space:nowrap;flex-shrink:0;color:#9babb1}.live-seat.ready .continue-status{color:#56988f}.live-seat.vacant{color:#a4b4bb}@media(max-width:700px){.next-round-seats{gap:12px;grid-template-columns:40px repeat(2,minmax(0,1fr))}.live-seat{gap:6px}.live-seat .seat-label{display:none}}@media(max-height:600px){.next-round-seats{padding:10px 0;margin-top:11px;gap:14px}.next-round-seats h3,.live-seat{font-size:10px}.live-seat :deep(.naval-avatar){width:22px;height:22px}.live-seat .continue-status{font-size:9px}}
</style>

<style scoped>
@media(max-height:500px) and (orientation:landscape){
  .settlement-screen{height:100dvh;overflow:hidden;padding:8px max(12px,env(safe-area-inset-right)) 8px max(12px,env(safe-area-inset-left));box-sizing:border-box}
  .settlement-inner{height:100%;max-width:940px;display:grid;grid-template-columns:150px minmax(0,1fr);grid-template-rows:36px minmax(88px,1fr) 22px 66px 44px;gap:6px 14px;min-height:0}
  .settlement-inner>header{grid-column:1/-1;min-height:0;padding:0;height:36px}
  .settlement-inner>header>span{font-size:12px;gap:7px}.settlement-inner small{font-size:10px}.settlement-icon{width:36px;height:36px}
  .settlement-title{grid-column:1;grid-row:2/5;align-self:center;padding:0;min-width:0}.settlement-title>svg{width:30px;height:30px;margin-bottom:8px}.settlement-title h2{font-size:21px;line-height:1.4;margin:0;display:-webkit-box;-webkit-line-clamp:3;-webkit-box-orient:vertical;overflow:hidden}.winner-name{display:block;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;font-size:14px}.winner-outcome{display:block}.settlement-title p{font-size:10px;line-height:1.6;margin:8px 0 0;display:-webkit-box;-webkit-line-clamp:4;-webkit-box-orient:vertical;overflow:hidden}
  .settlement-players{grid-column:2;grid-row:2;gap:10px;min-height:0}.settlement-players article{padding:10px;min-width:0;display:flex;flex-direction:column;justify-content:center}.player-heading{font-size:11px;gap:6px;min-width:0}.player-heading :deep(.naval-avatar){width:26px;height:26px}.player-heading>strong{white-space:nowrap;overflow:hidden;text-overflow:ellipsis}.player-heading>span{font-size:9px}.settlement-players dl{padding-top:9px;gap:6px}.settlement-players dt{font-size:9px;white-space:nowrap}.settlement-players dd{font-size:20px;line-height:1.2;margin:4px 0 0}
  .next-round-seats{grid-column:2;grid-row:3;grid-template-columns:repeat(2,minmax(0,1fr));gap:10px;padding:0;margin:0;border:0;min-height:0}.next-round-seats h3,.live-seat .seat-label{display:none}.live-seat{gap:5px;font-size:10px}.live-seat :deep(.naval-avatar){width:20px;height:20px}.live-seat .continue-status{font-size:9px}
  .settlement-inner>:deep(.naval-audience){grid-column:2;grid-row:4;display:grid;grid-template-columns:minmax(0,1fr) minmax(0,1fr);grid-template-rows:22px 44px;gap:0 8px;min-height:0;padding:0;margin:0;border:0;overflow:hidden}
  .settlement-inner :deep(.naval-audience header){grid-column:1;grid-row:1;font-size:10px;min-width:0}.settlement-inner :deep(.viewer-tag),.settlement-inner :deep(.audience-empty){display:none}.settlement-inner :deep(.audience-list){grid-column:2;grid-row:1;display:flex;flex-wrap:nowrap;align-items:center;justify-content:flex-start;gap:5px;padding:0;min-width:0;height:22px;max-height:22px;overflow-x:auto;overflow-y:hidden;scrollbar-width:none}.settlement-inner :deep(.audience-list::-webkit-scrollbar){display:none}.settlement-inner :deep(.audience-person){flex:0 0 auto;gap:0}.settlement-inner :deep(.audience-person>span:not(.naval-avatar)){display:none}.settlement-inner :deep(.audience-person .naval-avatar){width:22px;height:22px}.settlement-inner :deep(.seat-choices){grid-column:1/-1;grid-row:2;gap:8px;padding:0;margin:0;min-height:0}.settlement-inner :deep(.seat-choice){height:44px;min-height:44px;padding:6px 9px;gap:5px;font-size:10px}.settlement-inner :deep(.seat-choice strong){font-size:10px}
  .settlement-inner>footer{grid-column:1/-1;grid-row:5;margin:0;gap:9px;align-items:stretch;flex-wrap:nowrap;min-height:0}.settlement-primary,.settlement-secondary{height:44px;min-height:44px;padding:6px 15px;font-size:12px}
}
@media(max-width:620px) and (max-height:500px) and (orientation:landscape){.settlement-inner{grid-template-columns:120px minmax(0,1fr);gap:6px 10px}.settlement-title h2{font-size:19px}.settlement-players{gap:8px}.settlement-players article{padding:8px}.player-heading{gap:4px;font-size:10px}.player-heading :deep(.naval-avatar){width:23px;height:23px}.player-heading>span{font-size:8px}.settlement-players dd{font-size:18px}.live-seat>strong{font-size:9px}.live-seat .continue-status{font-size:8px}}
</style>
