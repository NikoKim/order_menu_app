<template>
 <section class="naval-medal-wall community-body" :class="{embedded}" :aria-label="embedded ? '勋章墙' : '玩家主页'" :aria-busy="loading">
  <AppSkeleton v-if="loading" variant="profile" :rows="4" :label="embedded ? '正在加载勋章' : '正在加载玩家主页'" />
  <div v-else-if="error" class="wall-error" role="alert"><p>{{ error }}</p><button class="medal-retry" @click="load"><RefreshCw :size="14" />重试</button></div>
  <template v-else>
   <header v-if="!embedded" class="wall-profile" role="region" aria-label="指挥官资料">
    <div class="wall-overview">
     <div class="wall-identity"><span class="wall-avatar"><img v-if="publicAvatar && !avatarFailed" :src="backendAssetUrl(publicAvatar)" alt="指挥官头像" @error="avatarFailed=true" /><UserRound v-else :size="25" /></span><div><h3 :title="wall.name || person?.name">{{ wall.name || person?.name || '指挥官' }}</h3><small v-if="wall.registered === false">游客</small></div></div>
     <div v-if="hasProgression" class="wall-rank"><NavalRankBadge :level="wall.progression.level" :size="40" /><div><h3>{{ wall.progression.rank }} <small>Lv.{{ wall.progression.level }}</small></h3><span>{{ wall.progression.score }} 等级分</span></div></div>
    </div>
    <div v-if="hasProgression" class="wall-rank-meter" role="progressbar" aria-label="指挥官等级进度" :aria-valuenow="wall.progression.score" :aria-valuemin="wall.progression.minScore" :aria-valuemax="wall.progression.maxScore"><span :style="{width:`${Math.max(0,Math.min(1,wall.progression.progress || 0))*100}%`}" /></div>
    <p v-if="hasProgression && wall.progression.promotion" class="wall-promotion">{{ wall.progression.promotion.targetRank }}晋升考核 · {{ wall.progression.promotion.currentWins }} / {{ wall.progression.promotion.requiredWins }}</p>
    <dl class="wall-stats"><div><dt><Swords :size="13" />对战数</dt><dd>{{ statValue('games') }}</dd></div><div><dt><Trophy :size="13" />获胜数</dt><dd>{{ statValue('wins') }}</dd></div></dl>
   </header>
   <nav v-if="!embedded" ref="tabs" class="wall-tabs" role="tablist" aria-label="玩家主页视图" @keydown="onTabKeydown">
    <button :id="tabId+'-recent'" role="tab" :aria-selected="activeTab==='recent'" :tabindex="activeTab==='recent'?0:-1" :aria-controls="tabId+'-panel'" @click="selectTab('recent')"><History :size="14" />最近对战</button>
    <button :id="tabId+'-medals'" role="tab" :aria-selected="activeTab==='medals'" :tabindex="activeTab==='medals'?0:-1" :aria-controls="tabId+'-panel'" @click="selectTab('medals')"><Medal :size="14" />勋章</button>
   </nav>
   <AppScrollArea ref="content" tag="div" class="wall-tab-content" :id="embedded ? undefined : tabId+'-panel'" :role="embedded ? undefined : 'tabpanel'" :aria-labelledby="embedded ? undefined : tabId+'-'+activeTab" :tabindex="embedded ? undefined : 0">
    <template v-if="!embedded && activeTab==='recent'">
     <ul v-if="recentMatches.length" class="wall-recent-matches" aria-label="最近对战记录">
      <li v-for="(match,index) in recentMatches" :key="index"><strong class="match-outcome" :class="{won:match.won}">{{ match.won ? '胜利' : '战败' }}</strong><div><h4>{{ match.ruleSet==='MILITARY' ? '军事模式' : '经典模式' }}</h4><p :title="match.opponentName">{{ match.mode==='AI' ? '人机对战' : '人人对战' }} · {{ match.opponentName || '指挥官' }}</p></div><time :datetime="matchDateTime(match.finishedAt)">{{ matchDate(match.finishedAt) }}</time></li>
     </ul>
     <p v-else class="wall-empty">暂无对战记录</p>
    </template>
    <template v-else>
     <div class="medal-progress"><span>经典连胜<strong>{{ wall.classicCurrent || 0 }}</strong></span><span>军事连胜<strong>{{ wall.militaryCurrent || 0 }}</strong></span></div>
     <section v-for="mode in ['classic','military']" :key="mode" class="medal-group"><h3>{{ mode==='classic'?'经典模式':'军事模式' }} · 人人对战</h3>
      <div v-for="m in NAVAL_MEDALS.filter(m=>m.mode===mode)" :key="m.id" class="medal-row" :class="{locked:!wall.unlocked?.includes(m.id)}">
       <img :src="m.src" :alt="m.name" /><div><strong>{{ m.name }}</strong><small>{{ wall.unlocked?.includes(m.id)?'已解锁':`需连续获胜 ${m.wins} 局` }}</small></div>
       <button v-if="canManage" :disabled="busy||!wall.unlocked?.includes(m.id)" :aria-pressed="wall.equipped===m.id" @click="wear(wall.equipped===m.id?'':m.id)"><Check v-if="wall.equipped===m.id" :size="15" />{{ wall.equipped===m.id?'卸下':'佩戴' }}</button>
      </div>
     </section>
    </template>
   </AppScrollArea>
  </template>
 </section>
</template>
<script setup>
import {computed,nextTick,onMounted,onUnmounted,ref,watch} from 'vue'
import AppScrollArea from '../../components/AppScrollArea.vue'
import AppSkeleton from '../../components/AppSkeleton.vue'
import {Check,History,Medal,RefreshCw,Swords,Trophy,UserRound} from 'lucide-vue-next'
import NavalRankBadge from './NavalRankBadge.vue'
import {backendAssetUrl} from '../../config/endpoints'
import {getNavalMedals} from '../../api/naval'
import {ensureGuest} from '../../api/guest'
import {NAVAL_MEDALS,loadOwnNavalMedals,navalMedalIdentity,ownNavalMedals,wearNavalMedal} from '../../games/navalMedals'
import {navalPublicPlayerId} from '../../games/navalPublicPlayers'
import {notifyError,notifySuccess} from '../../services/notice'

const props=defineProps({person:Object,embedded:Boolean})
const wall=ref({unlocked:[]}),loading=ref(false),busy=ref(false),error=ref(''),activeTab=ref('recent'),tabs=ref(null),content=ref(null)
const tabId='player-wall-'+Math.random().toString(36).slice(2,10)
let request=0,wearRequest=0,disposed=false,wallAccount=''
const avatarFailed=ref(false),publicAvatar=computed(()=>wall.value.avatarUrl || props.person?.avatarUrl)
const hasProgression=computed(()=>!props.person?.ai && wall.value.registered!==false && Number.isFinite(wall.value.progression?.score) && wall.value.progression.level>=1 && wall.value.progression.level<=12)
const recentMatches=computed(()=>Array.isArray(wall.value.recentMatches)?wall.value.recentMatches.slice(0,10):[])
const canManage=computed(()=>Boolean(wall.value.mine && wallAccount===navalMedalIdentity() && (props.person?.self || navalPublicPlayerId(props.person)===ownNavalMedals.value.medalId)))
const statValue=key=>{const value=wall.value.stats?.[key];return typeof value==='number'&&Number.isFinite(value)&&value>=0?value.toLocaleString('zh-CN'):'--'}
const matchDateTime=value=>Number.isFinite(Number(value))&&Number(value)>0?new Date(Number(value)).toISOString():undefined
const matchDate=value=>matchDateTime(value)?new Date(Number(value)).toLocaleString('zh-CN',{month:'2-digit',day:'2-digit',hour:'2-digit',minute:'2-digit',hour12:false}):'--'
async function load(){
 const seq=++request
 wearRequest++;wall.value={unlocked:[]};wallAccount='';loading.value=true;busy.value=false;error.value='';activeTab.value='recent';avatarFailed.value=false
 try{
  if(props.person?.ai || !props.person?.self && !navalPublicPlayerId(props.person))throw new Error('暂时无法查看该玩家主页')
  await ensureGuest()
  const owner=navalMedalIdentity()
  let value
  if(props.person?.self){
   const own=await loadOwnNavalMedals()
   if(disposed||seq!==request)return
   if(owner!==navalMedalIdentity()){load();return}
   const publicId=navalPublicPlayerId({medals:own})
   value=!props.embedded && publicId?{...await getNavalMedals(publicId),...own}:own
  }else value=await getNavalMedals(navalPublicPlayerId(props.person))
  if(disposed||seq!==request)return
  if(owner!==navalMedalIdentity()){load();return}
  if(props.person?.self && value.medalId && value.medalId===ownNavalMedals.value.medalId)value={...value,...ownNavalMedals.value}
  wallAccount=owner;wall.value=value
 }catch(e){if(!disposed&&seq===request){error.value=e.message||'读取玩家主页失败';notifyError(error.value)}}finally{if(!disposed&&seq===request)loading.value=false}
}
async function wear(id){
 if(!canManage.value||busy.value)return
 const seq=request,attempt=++wearRequest,owner=wallAccount
 busy.value=true
 try{
  const value=await wearNavalMedal(id)
  if(!value||disposed||seq!==request||attempt!==wearRequest||owner!==navalMedalIdentity())return
  wall.value={...wall.value,...value,mine:true}
  window.dispatchEvent(new CustomEvent('naval-medal-equipped'));notifySuccess(id?'勋章已佩戴':'勋章已卸下')
 }catch(e){if(!disposed&&seq===request&&attempt===wearRequest&&owner===navalMedalIdentity())notifyError(e.message)}finally{if(!disposed&&seq===request&&attempt===wearRequest)busy.value=false}
}
function selectTab(value){activeTab.value=value;nextTick(()=>content.value?.scrollTo({top:0}))}
function onTabKeydown(event){if(!['ArrowLeft','ArrowRight','Home','End'].includes(event.key))return;event.preventDefault();selectTab(event.key==='Home'?'recent':event.key==='End'?'medals':activeTab.value==='recent'?'medals':'recent');nextTick(()=>tabs.value?.querySelector('[aria-selected=true]')?.focus())}
function accountChanged(event){if(['navy_token','navy_guest_token'].includes(event.key))load()}
watch(()=>[props.person,props.embedded],load,{immediate:true})
watch(ownNavalMedals,value=>{if(wallAccount===navalMedalIdentity() && (props.person?.self || canManage.value))wall.value={...wall.value,...value}})
watch(publicAvatar,()=>{avatarFailed.value=false})
onMounted(()=>window.addEventListener('storage',accountChanged))
onUnmounted(()=>{disposed=true;request++;wearRequest++;window.removeEventListener('storage',accountChanged)})
</script>
<style scoped>
.naval-medal-wall{display:flex;flex-direction:column;flex:1;min-height:0;min-width:0;overflow:hidden;color:inherit}.naval-medal-wall.embedded{display:block;flex:none;overflow:visible}
.wall-profile{flex-shrink:0;padding:5px 0 10px}.wall-overview{display:grid;grid-template-columns:minmax(0,1fr) auto;align-items:center;gap:10px}.wall-identity{display:flex;align-items:center;gap:9px;min-width:0}.wall-identity>div{min-width:0}.wall-avatar{display:grid;place-items:center;width:42px;height:42px;border:1px solid #d1e0e4;border-radius:50%;background:#e7eff1;color:#7c98a3;flex-shrink:0;overflow:hidden}.wall-avatar img{width:100%;height:100%;object-fit:cover}.wall-identity h3{margin:0;font-size:15px;line-height:1.5;white-space:nowrap;overflow:hidden;text-overflow:ellipsis;color:#345e6a}.wall-identity small{display:block;font-size:10px;color:#8c9fa7}
.wall-rank{display:flex;align-items:center;gap:7px;color:var(--color-primary-strong);min-width:0}.wall-rank h3{font-size:15px;margin:0;line-height:1.5;white-space:nowrap}.wall-rank h3 small{font-size:9px;color:#7a969e;font-weight:400;margin-left:3px}.wall-rank>div>span{display:block;margin-top:2px;font-size:10px;color:#829ba4;font-variant-numeric:tabular-nums;white-space:nowrap}.wall-rank-meter{height:4px;border-radius:2px;background:#f4e7db;overflow:hidden;margin-top:9px}.wall-rank-meter>span{display:block;height:100%;background:var(--color-primary)}.wall-promotion{font-size:10px;color:#ad813d;line-height:1.6;margin:5px 0 0}
.wall-stats{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:15px;margin:9px 0 0}.wall-stats>div{display:flex;align-items:baseline;justify-content:space-between;gap:8px;min-width:0}.wall-stats dt{display:flex;align-items:center;gap:5px;color:#81969e;font-size:10px;white-space:nowrap}.wall-stats dt>svg{align-self:center}.wall-stats dd{margin:0;color:#496c78;font-size:18px;font-weight:600;line-height:1.4;font-variant-numeric:tabular-nums;overflow-wrap:anywhere}
.wall-tabs{display:flex;align-items:center;gap:21px;flex-shrink:0;border-bottom:1px solid #dce6e9;min-height:34px}.wall-tabs>button{display:flex;align-items:center;gap:6px;align-self:stretch;padding:7px 0;border-bottom:2px solid transparent;background:transparent;color:#8599a2;font-size:11px;white-space:nowrap}.wall-tabs>button[aria-selected=true]{border-color:var(--color-primary);color:var(--color-primary-strong);font-weight:600}.wall-tab-content{flex:1;min-height:0;outline-offset:-2px}.embedded .wall-tab-content{flex:none;overflow:visible}
.wall-recent-matches{list-style:none;margin:0;padding:0}.wall-recent-matches>li{display:grid;grid-template-columns:29px minmax(0,1fr) auto;align-items:center;gap:10px;min-height:53px;padding:9px 0;border-bottom:1px solid #e5ecef}.wall-recent-matches>li>div{min-width:0}.match-outcome{font-size:11px;font-weight:500;color:#b47769}.match-outcome.won{color:#57917b}.wall-recent-matches h4{margin:0;font-size:11px;font-weight:500;color:#5a7c88}.wall-recent-matches p{margin-top:3px;font-size:10px;color:#8b9fa7;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}.wall-recent-matches time{font-size:9px;color:#9baab0;white-space:nowrap}.wall-empty{padding:24px 0;text-align:center;color:#91a4ad;font-size:11px}
.medal-progress{display:flex;align-items:center;gap:22px;font-size:10px;color:#8298a0;margin:12px 0 15px}.medal-progress>span{display:flex;align-items:center;gap:9px}.medal-progress strong{font-size:15px;font-weight:600;color:var(--color-primary-strong);font-variant-numeric:tabular-nums}.medal-group{padding:8px 0;border-top:1px solid #dce6e9}.medal-group h3{font-size:11px;margin:4px 0;color:#8298a0;font-weight:500;line-height:1.6}.medal-row{display:flex;align-items:center;gap:11px;min-height:75px;padding:7px 0;border-bottom:1px solid #e5ecef}.medal-row:last-child{border-bottom:0}.medal-row>img{width:49px;height:57px;object-fit:contain;image-rendering:pixelated;flex-shrink:0}.medal-row>div{flex:1;min-width:0}.medal-row strong{font-size:12px;line-height:1.5;overflow-wrap:anywhere}.medal-row small{display:block;font-size:10px;color:#8b9da5;margin:3px 0;line-height:1.5}.medal-row.locked>img{filter:grayscale(1);opacity:.4}.medal-row>button{display:flex;align-items:center;gap:3px;background:var(--color-primary-bg);color:var(--color-primary-strong);padding:7px 9px;border:1px solid var(--color-primary-line);border-radius:4px;font-size:11px;flex-shrink:0}.medal-row>button:disabled{opacity:.4}
.wall-error{display:flex;align-items:center;justify-content:space-between;gap:10px;padding:15px 0}.wall-error p{font-size:11px;line-height:1.6;color:#b56b5e;overflow-wrap:anywhere}.medal-retry{display:flex;align-items:center;gap:5px;padding:6px 9px;min-height:30px;background:#eef3f4;color:#6d8a95;border-radius:4px;font-size:11px;flex-shrink:0}
@media(max-height:450px) and (orientation:landscape){.wall-profile{padding:0 0 5px}.wall-avatar{width:34px;height:34px}.wall-overview{gap:10px}.wall-identity h3,.wall-rank h3{font-size:13px}.wall-rank:deep(.naval-rank-badge){width:32px!important;height:32px!important}.wall-rank-meter{margin-top:5px;height:3px}.wall-stats{margin-top:5px}.wall-stats dd{font-size:16px}.wall-tabs{min-height:30px}.wall-tabs>button{padding:5px 0}.wall-recent-matches>li{min-height:45px;padding:6px 0}.medal-row{min-height:65px}.medal-row>img{height:48px;width:42px}}
@media(max-width:370px){.wall-overview{gap:7px}.wall-avatar{width:34px;height:34px}.wall-identity{gap:6px}.wall-rank{gap:4px}.wall-rank h3,.wall-identity h3{font-size:13px}.wall-rank:deep(.naval-rank-badge){width:32px!important;height:32px!important}.wall-recent-matches>li{column-gap:7px}.wall-recent-matches time{font-size:8px}}
</style>
