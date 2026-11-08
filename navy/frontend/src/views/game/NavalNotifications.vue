<template>
  <section class="notification-panel community-body" aria-label="通知列表">
    <div class="notification-toolbar"><span v-if="unreadCount" class="unread-summary"><span />{{ unreadCount }} 条未读</span><span v-else-if="!loaded" class="all-read">消息</span><span v-else class="all-read"><CheckCheck :size="14" />全部已读</span><button class="notification-icon" type="button" title="刷新通知" aria-label="刷新通知" :disabled="loading || Boolean(acting)" @click="load()"><RefreshCw :size="16" :class="{ spinning: loading }" /></button></div>
    <AppScrollArea tag="div" class="notification-scroll" :aria-busy="loading">
      <AppSkeleton v-if="loading && !loadingMore" :rows="4" label="正在加载通知" />
      <p v-else-if="!items.length" class="notification-empty"><MailOpen :size="38" />暂无消息</p>
      <ol v-else class="notification-list">
        <li v-for="item in items" :key="item.id" class="notification-entry" :class="{ unread: !item.read }">
          <header class="notification-meta"><span class="notice-sender"><Gift v-if="hasGift(item)" :size="15" /><Mail v-else :size="15" />指挥部</span><time :datetime="new Date(item.createdAt).toISOString()">{{ date(item.createdAt) }}</time><span v-if="!item.read" class="unread-indicator" aria-label="未读" /></header>
          <h3>{{ item.title }}</h3><p class="notification-content" :class="{ collapsed: item.content?.length > 140 && !expanded.has(item.id) }">{{ item.content }}</p>
          <button v-if="item.content?.length > 140" class="expand-message" type="button" :aria-expanded="expanded.has(item.id)" @click="toggleExpanded(item)">{{ expanded.has(item.id) ? '收起' : '展开全文' }}<ChevronUp v-if="expanded.has(item.id)" :size="13" /><ChevronDown v-else :size="13" /></button>
          <section v-if="hasGift(item)" class="notification-gift" :class="{claimed:item.claimed,expired:expired(item)}" aria-label="邮件附件">
            <div v-if="Number(item.coins)>0" class="gift-value"><span class="coin-seal"><Coins :size="21" /></span><strong>{{ item.coins }}</strong><span>金币</span></div>
            <ul v-if="item.items?.length" class="gift-items"><li v-for="attachment in item.items" :key="attachment.itemId"><span class="attachment-art" :class="{'bubble-art':isBubbleItem(attachment.itemId)}" :style="shopArtwork(attachment.itemId)" /><div><strong>{{ attachment.name }}</strong><small>领取后 {{ duration(attachment.durationHours) }}</small></div><span class="attachment-count">× {{ attachment.quantity }}</span></li></ul>
            <footer class="gift-claim-bar"><span v-if="item.claimed" class="gift-claimed"><Check :size="14" />已领取</span><span v-else-if="expired(item)" class="gift-expired"><Clock3 :size="14" />已失效</span><template v-else><span v-if="Number(item.claimExpiresAt)>0" class="gift-countdown"><Clock3 :size="11" />{{ remaining(item.claimExpiresAt) }}</span><button class="claim-gift" type="button" :disabled="Boolean(acting) || item.canClaim===false" @click="claim(item)"><LoaderCircle v-if="acting===item.id" :size="14" class="spinning" /><Gift v-else :size="14" />{{ acting===item.id ? '领取中...' : item.items?.length ? '领取附件' : '领取金币' }}</button></template></footer>
          </section>
          <footer v-if="!item.read" class="notification-footer"><button class="mark-read" type="button" :disabled="Boolean(acting)" @click="markRead(item)"><CheckCheck :size="14" />标为已读</button></footer>
        </li>
      </ol>
      <AppSkeleton v-if="loading && loadingMore" :rows="2" label="正在加载更早的通知" />
      <button v-if="hasMore" class="load-more" type="button" :disabled="loading || Boolean(acting)" @click="load({more:true})">更早的消息<ChevronDown :size="14" /></button>
    </AppScrollArea>
  </section>
</template>

<script setup>
import { onMounted, onUnmounted, ref } from 'vue'
import { Check, CheckCheck, ChevronDown, ChevronUp, Clock3, Coins, Gift, LoaderCircle, Mail, MailOpen, RefreshCw } from 'lucide-vue-next'
import AppScrollArea from '../../components/AppScrollArea.vue'
import AppSkeleton from '../../components/AppSkeleton.vue'
import { claimNotification, getNotifications, readNotification } from '../../api/account'
import { refreshNavalProgression } from '../../games/navalProgression'
import { refreshNavalShop } from '../../games/navalShop'
import { isBubbleItem, shopArtwork } from '../../games/shopArtwork'
import { notifyError, notifySuccess } from '../../services/notice'
import { notificationUnreadCount as unreadCount, refreshNotificationSummary, updateNotificationUnread } from '../../services/accountNotifications'

const items=ref([]),hasMore=ref(false),loading=ref(true),loadingMore=ref(false),loaded=ref(false),acting=ref(null),expanded=ref(new Set()),now=ref(Date.now())
let disposed=false,generation=0,timer
const hasGift=item=>Number(item.coins)>0 || Boolean(item.items?.length)
const expired=item=>!item.claimed && (item.expired || Number(item.claimExpiresAt)>0 && Number(item.claimExpiresAt)<=now.value)
const duration=hours=>Number(hours)>24 && Number(hours)%24===0?`${Number(hours)/24} 天有效`:`${hours} 小时有效`
const remaining=at=>{const minutes=Math.max(0,Math.ceil((Number(at)-now.value)/60000));return minutes>=60?`剩余 ${Math.floor(minutes/60)} 小时 ${minutes%60} 分钟`:`剩余 ${minutes} 分钟`}
const date=value=>new Date(value).toLocaleString('zh-CN',{...(new Date(value).getFullYear()===new Date().getFullYear()?{}:{year:'numeric'}),month:'2-digit',day:'2-digit',hour:'2-digit',minute:'2-digit',hour12:false})
function toggleExpanded(item){if(expanded.value.has(item.id))expanded.value.delete(item.id);else expanded.value.add(item.id)}
async function load({more=false,force=false}={}){
  if(disposed || loading.value && generation > 0 && !force)return
  const request=++generation;loading.value=true;loadingMore.value=more
  try{const value=await getNotifications(more?items.value.at(-1)?.id:undefined);if(!disposed&&request===generation){items.value=more?[...items.value,...(value.items||[])]:value.items||[];hasMore.value=Boolean(value.hasMore);loaded.value=true;now.value=Date.now();updateNotificationUnread(value.unreadCount)}}
  catch(e){if(!disposed&&request===generation)notifyError(e.message)}finally{if(request===generation)loading.value=false}
}
async function markRead(item){
  if(acting.value)return
  generation++;loading.value=false;acting.value=item.id
  try{await readNotification(item.id);item.read=true;await refreshNotificationSummary({force:true})}catch(e){if(!disposed)notifyError(e.message)}finally{acting.value=null}
}
async function claim(item){
  if(acting.value || expired(item))return
  generation++;loading.value=false;acting.value=item.id
  try{await claimNotification(item.id);if(!disposed){item.claimed=true;item.read=true;notifySuccess(item.items?.length?'附件已领取，物品已放入我的物品':'金币已领取')}await refreshNotificationSummary({force:true});await refreshNavalProgression({force:true});if(item.items?.length)await refreshNavalShop({force:true})}
  catch(e){if(!disposed){notifyError(e.message);if(Number(e.code)===409)await load({force:true})}}finally{acting.value=null}
}
function visible(){if(document.visibilityState==='visible'&&!acting.value){now.value=Date.now();load()}}
onMounted(()=>{load();timer=setInterval(()=>{now.value=Date.now()},30000);document.addEventListener('visibilitychange',visible)})
onUnmounted(()=>{disposed=true;generation++;clearInterval(timer);document.removeEventListener('visibilitychange',visible)})
</script>

<style scoped>
.notification-panel{display:flex;flex-direction:column;min-height:0;flex:1;color:#3e4b52}.notification-toolbar{display:flex;align-items:center;justify-content:space-between;gap:10px;flex-shrink:0;padding:0 2px 10px;border-bottom:1px solid #e3e8eb;min-height:35px}.unread-summary,.all-read{display:flex;align-items:center;gap:6px;font-size:11px;color:#87959b}.unread-summary{color:var(--color-primary-strong)}.unread-summary>span{width:5px;height:5px;border-radius:50%;background:currentColor}.notification-icon{display:grid;place-items:center;width:28px;height:28px;border-radius:4px;background:transparent;color:#829198}.notification-icon:hover{background:var(--color-primary-bg);color:var(--color-primary)}.notification-scroll{flex:1;min-height:0}.notification-list{list-style:none;margin:0;padding:0 2px}.notification-entry{padding:20px 2px;border-bottom:1px solid #e3e8eb;min-width:0}.notification-entry:last-child{border-bottom:0}.notification-meta{display:flex;align-items:center;gap:8px;color:#96a2a7;font-size:10px;line-height:1.5;min-height:18px;margin-bottom:9px}.notice-sender{display:inline-flex;align-items:center;gap:6px;flex-shrink:0}.notice-sender>svg{color:#acb5b8}.notification-meta time{margin-left:auto;white-space:nowrap;font-variant-numeric:tabular-nums}.unread-indicator{width:6px;height:6px;flex-shrink:0;border-radius:50%;background:var(--color-primary)}.notification-entry h3{font-size:14px;line-height:1.6;font-weight:600;overflow-wrap:anywhere;color:#58656c;margin:0}.notification-entry.unread h3{color:#303f47}.unread .notice-sender>svg{color:var(--color-primary)}.notification-content{white-space:pre-wrap;overflow-wrap:anywhere;font-size:12px;line-height:1.85;color:#7a888f;margin:8px 0 0}.notification-content.collapsed{display:-webkit-box;-webkit-box-orient:vertical;-webkit-line-clamp:3;overflow:hidden}.expand-message{display:flex;align-items:center;gap:3px;min-height:26px;background:transparent;font-size:11px;color:var(--color-primary-strong);padding:2px 0;margin-top:3px}.notification-gift{margin-top:15px;padding:13px;background:#fff4e8;border:1px solid #f4dfc7;border-radius:6px;min-width:0}.gift-value{display:flex;align-items:center;gap:7px;color:#b67532}.coin-seal{display:grid;place-items:center;width:34px;height:34px;border-radius:50%;background:#ffe6c8;color:#d78a32;flex-shrink:0}.gift-value strong{font-size:23px;line-height:1.2;font-weight:650;font-variant-numeric:tabular-nums;overflow-wrap:anywhere}.gift-value>span:last-child{font-size:10px;color:#ae957c}.gift-items{list-style:none;padding:0;margin:0}.gift-value+.gift-items{margin-top:12px;padding-top:10px;border-top:1px solid #f0dcc6}.gift-items>li{display:flex;align-items:center;gap:9px;padding:6px 0;min-width:0}.attachment-art{display:block;width:38px;height:38px;flex-shrink:0;background-size:contain;background-position:center;background-repeat:no-repeat}.attachment-art.bubble-art{background-size:200% 100%;background-position:left center}.gift-items>li>div{min-width:0;flex:1}.gift-items strong{display:block;font-size:11px;font-weight:500;line-height:1.6;overflow-wrap:anywhere;color:#805f3d}.gift-items small{display:block;font-size:9px;color:#ad9780;margin-top:2px}.attachment-count{font-size:12px;color:#b78b59;flex-shrink:0;font-variant-numeric:tabular-nums}.gift-claim-bar{display:flex;align-items:center;gap:8px;justify-content:flex-end;margin-top:10px;min-height:30px;flex-wrap:wrap}.gift-countdown{display:flex;align-items:center;gap:3px;margin-right:auto;color:#b49d85;font-size:9px}.claim-gift{display:inline-flex;align-items:center;justify-content:center;gap:6px;min-height:32px;min-width:91px;padding:6px 10px;border-radius:4px;background:var(--color-primary);color:#fff;font-size:11px;flex-shrink:0}.claim-gift:hover:not(:disabled){background:var(--color-primary-hover)}.notification-gift.claimed,.notification-gift.expired{background:#f0f3f4;border-color:#e1e7e9}.claimed .gift-value,.expired .gift-value{color:#929da2}.claimed .coin-seal,.expired .coin-seal{background:#e6ecef;color:#9aa7ad}.gift-claimed,.gift-expired{display:inline-flex;align-items:center;gap:4px;font-size:11px;color:#99a4a9}.notification-footer{display:flex;justify-content:flex-end;margin-top:9px}.mark-read{display:inline-flex;align-items:center;gap:4px;min-height:28px;padding:3px 0;background:transparent;color:#99a4a9;font-size:10px}.mark-read:hover{color:var(--color-primary-strong)}.notification-empty{min-height:220px;display:flex;flex-direction:column;align-items:center;justify-content:center;gap:16px;color:#a2adb2;font-size:12px}.notification-empty>svg{color:#d2dade}.load-more{display:flex;align-items:center;justify-content:center;gap:5px;width:100%;min-height:40px;padding:8px;margin:8px 0;background:transparent;border-top:1px solid #e3e8eb;color:#88999f;font-size:11px}.load-more:hover{color:var(--color-primary-strong)}.spinning{animation:notice-spin 1s linear infinite}@keyframes notice-spin{to{transform:rotate(360deg)}}@media(prefers-reduced-motion:reduce){.spinning{animation:none}}
</style>
