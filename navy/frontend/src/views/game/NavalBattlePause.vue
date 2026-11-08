<template>
  <div class="battle-pause-status" role="status" aria-live="polite"><Pause :size="17" /><strong>{{ label }}</strong><span>{{ seconds }}s</span><small v-for="attempt in attempts" :key="attempt.seat" class="pause-attempt" :data-reconnect-count="attempt.count">{{ attempt.label }}</small></div>
</template>
<script setup>
import { computed } from 'vue'
import { Pause } from 'lucide-vue-next'
import { navalPauseSeconds, NAVAL_RECONNECT_LIMIT } from '../../games/navalBattlePause'
const props = defineProps({room:{type:Object,required:true}, now:Number})
const seconds = computed(() => navalPauseSeconds(props.room, props.now))
const attempts = computed(() => {
  const players=props.room.pause?.players || [], limit=Number(props.room.pause?.maxReconnects) || NAVAL_RECONNECT_LIMIT
  return players.map(player=>{
    const count=Math.max(1,Number(player.reconnectCount ?? props.room.players?.[player.seat]?.reconnectCount) || 1)
    const side=players.length<2?'':props.room.role==='SPECTATOR'?(player.seat===0?'左方':'右方'):(player.seat===props.room.you?'己方':'对方')
    return {seat:player.seat,count,label:`${side}${count}/${limit}次`}
  })
})
const label = computed(() => {
  const players = props.room.pause?.players || []
  if (players.length > 1) return '双方暂离，战局暂停'
  const own = props.room.role !== 'SPECTATOR' && players.some(player => player.seat === props.room.you)
  return own ? '你已暂离，战局暂停' : props.room.role === 'SPECTATOR' ? '玩家暂离，战局暂停' : '对方暂离，战局暂停'
})
</script>
<style scoped>
.battle-pause-status{position:fixed;z-index:265;top:calc(var(--game-top,0px) + 72px);left:50%;transform:translateX(-50%);display:flex;align-items:center;flex-wrap:wrap;justify-content:center;gap:8px;max-width:calc(var(--game-width,100vw) - 24px);padding:9px 13px;border:1px solid #d2e0e5;border-radius:5px;background:#f9fcfdf5;box-shadow:0 3px 16px #29465317;color:#547783;pointer-events:none;white-space:nowrap}.battle-pause-status strong{font-size:12px;font-weight:600}.battle-pause-status>svg{color:#b98b4d;flex-shrink:0}.battle-pause-status>span{min-width:3ch;text-align:center;font-size:13px;font-variant-numeric:tabular-nums;color:#ac7f43}.battle-pause-status small{font-size:10px;color:#8aa0a8}
@media(orientation:landscape) and (max-height:550px){.battle-pause-status{top:calc(var(--game-top,0px) + 36px);padding:6px 10px;gap:6px}.battle-pause-status strong{font-size:11px}}
</style>
