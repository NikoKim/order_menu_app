<template>
  <div ref="layer" class="match-wager-layer" :class="{ complete, 'reduce-motion': reduced }" aria-label="本局奖池">
    <div class="match-pot"><Coins :size="20" /><output :aria-label="`奖池 ${displayed}`">{{ displayed }}</output></div>
    <Teleport :to="flightTarget"><div class="naval-coin-flight-layer" :class="{ complete, 'reduce-motion':reduced }" data-flight-kind="match" aria-hidden="true"><span v-for="coin in plan.coins" :key="`${matchKey}:${coin.id}`" class="match-flying-coin" :class="{ 'from-right': coin.side === 1 }" :style="coinStyle(coin)"><span><Coins :size="13" /></span></span></div></Teleport>
    <span v-for="(amount, side) in plan.amounts" v-show="amount" :key="`stake-${side}`" class="match-stake" :class="{ right: side===1 }"><Coins :size="11" />{{ amount }}</span>
  </div>
</template>
<script setup>
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import { Coins } from 'lucide-vue-next'
import { matchWagerPlan } from '../../games/navalMatchWagers'
import { playNavalSound } from '../../games/navalAudio'
import { createNavalUnitCounter } from '../../games/navalUnitCounter'
import { useFullscreenTarget } from '../../composables/useFullscreenTarget'

const props=defineProps({people:{type:Array,default:()=>[]},matchKey:String})
const emit=defineEmits(['complete'])
const flightTarget=useFullscreenTarget()
const plan=computed(()=>matchWagerPlan(props.people)),layer=ref(null),displayed=ref(0),complete=ref(false),reduced=ref(false)
const points=ref([{x:0,y:0},{x:0,y:0}])
const viewportOffset=ref({x:0,y:0})
const center=ref({x:0,y:24})
let timers=[],observer,counter,disposed=false,lastKey=''
function clear(){timers.forEach(clearTimeout);timers=[];counter?.stop();counter=undefined}
function measure(){
  if(!layer.value)return
  const box=layer.value.getBoundingClientRect(),avatars=layer.value.parentElement.querySelectorAll('.matched-commander .naval-avatar')
  viewportOffset.value={x:box.left,y:box.top}
  center.value={x:box.width/2,y:innerHeight<=500&&innerWidth>innerHeight?18:24}
  points.value=[0,1].map(side=>{const avatar=avatars[side]?.getBoundingClientRect();return avatar?{x:avatar.left+avatar.width/2-box.left,y:avatar.top+avatar.height/2-box.top}:{x:box.width*(side?0.75:0.25),y:24}})
}
function coinStyle(coin){const point=points.value[coin.side];return{'--coin-x':`${viewportOffset.value.x+point.x}px`,'--coin-y':`${viewportOffset.value.y+point.y}px`,'--coin-end-x':`${center.value.x-point.x}px`,'--coin-end-y':`${center.value.y-point.y}px`,'--coin-delay':`${coin.startsAt}ms`,'--coin-arc':`${-22-(coin.index%3)*7}px`}}
async function start(){
  const key=props.matchKey||props.people.map(person=>`${person.id}:${person.wager}`).join('|')
  if(key===lastKey)return
  lastKey=key;clear();displayed.value=0;complete.value=false
  await nextTick();if(disposed||key!==lastKey)return;measure()
  if(reduced.value){displayed.value=plan.value.total;complete.value=true;emit('complete',key);return}
  timers.push(setTimeout(()=>{
    counter=createNavalUnitCounter({fromCents:0,toCents:plan.value.total*100,onChange:value=>{displayed.value=value/100},onComplete:()=>{complete.value=true;emit('complete',key)}})
    counter.start()
  },plan.value.counterStart))
  for(const side of [0,1])if(plan.value.amounts[side])timers.push(setTimeout(()=>playNavalSound('match-coins'),130+side*260))
}
watch(()=>props.matchKey,start)
onMounted(()=>{reduced.value=matchMedia('(prefers-reduced-motion: reduce)').matches;observer=new ResizeObserver(measure);observer.observe(layer.value);start()})
onUnmounted(()=>{disposed=true;clear();observer?.disconnect()})
</script>
<style scoped>
.match-wager-layer{position:absolute;inset:0;pointer-events:none;z-index:3;overflow:visible}.match-pot{position:absolute;left:50%;top:24px;transform:translate(-50%,-50%);display:flex;align-items:center;justify-content:center;gap:6px;min-width:68px;min-height:32px;padding:0;background:transparent;border:0;border-radius:0;box-shadow:none;color:#c47c25;transition:color .25s}.match-pot output{font-size:23px;line-height:1.1;font-weight:700;min-width:3ch;font-variant-numeric:tabular-nums;text-align:left}.complete .match-pot{color:#b77d2d}.match-flying-coin{position:absolute;left:var(--coin-x);top:var(--coin-y);width:0;height:0;animation:coin-travel 430ms cubic-bezier(.25,.65,.55,1) var(--coin-delay) both;opacity:0}.match-flying-coin>span{position:absolute;display:grid;place-items:center;width:21px;height:21px;left:-10px;top:-10px;border-radius:50%;border:2px solid #e09a2d;background:#ffd46c;color:#a36517;box-shadow:inset 0 0 0 2px #ffe8a3,0 2px 4px #a86c2226;animation:coin-turn 150ms ease-in-out infinite alternate}.match-stake{position:absolute;left:25%;top:-18px;transform:translateX(-50%);display:flex;align-items:center;gap:3px;color:#c48b40;font-size:10px;font-variant-numeric:tabular-nums;animation:stake-fade 1.15s ease both}.match-stake.right{left:75%}@keyframes coin-travel{0%{opacity:0;transform:translate(0,0) scale(.65)}12%{opacity:1}48%{opacity:1;transform:translate(calc(var(--coin-end-x)/2),var(--coin-arc)) scale(1)}100%{opacity:0;transform:translate(var(--coin-end-x),var(--coin-end-y)) scale(.55)}}@keyframes coin-turn{to{transform:rotateY(65deg) rotate(25deg)}}@keyframes stake-fade{0%,55%{opacity:1}100%{opacity:0;transform:translate(-50%,-5px)}}.reduce-motion .match-flying-coin,.reduce-motion .match-stake{display:none}@media(max-width:500px){.match-pot{min-width:58px;min-height:30px;gap:4px}.match-pot output{font-size:18px}.match-pot>svg{width:17px;height:17px}.match-flying-coin>span{width:18px;height:18px}.match-stake{font-size:9px}}@media(max-height:500px) and (orientation:landscape){.match-pot{top:18px;min-height:28px}.match-pot output{font-size:18px}.match-stake{top:-14px}}@media(prefers-reduced-motion:reduce){.match-pot{transition:none}.match-flying-coin,.match-stake{display:none}}
</style>
