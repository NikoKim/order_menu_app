<template>
  <section class="naval-shop" :class="{ embedded }" :aria-label="embedded ? '我的物品' : '舰队商城'">
    <div class="shop-toolbar"><header class="shop-wallet"><div><span>账户金币</span><strong><Coins :size="21" />{{ money(shop?.coins ?? progression?.coins ?? 0) }}</strong></div><div class="shop-wallet-actions"><button v-if="embedded" class="embedded-shop-switch" type="button" @click="tab=tab==='inventory'?'catalog':'inventory'"><ShoppingBag v-if="tab==='inventory'" :size="14" /><Backpack v-else :size="14" />{{ tab==='inventory'?'商城':'我的物品' }}</button><button type="button" class="shop-refresh" title="刷新物品" aria-label="刷新商城" :disabled="loading || busy" @click="refreshNavalShop({force:true})"><RefreshCw :size="16" :class="{ spinning: loading }" /></button></div></header>
    <nav v-if="!embedded" class="shop-tabs" aria-label="商城视图"><button :class="{active:tab==='catalog'}" :aria-pressed="tab==='catalog'" @click="tab='catalog'"><ShoppingBag :size="15" />商城</button><button :class="{active:tab==='inventory'}" :aria-pressed="tab==='inventory'" @click="tab='inventory'"><Backpack :size="15" />我的物品<span v-if="activeInventory.length">{{ activeInventory.length }}</span></button></nav></div>
    <nav class="shop-categories" aria-label="物品分类"><button v-for="category in categories" :key="category.id" :class="{active:filter===category.id}" :aria-pressed="filter===category.id" @click="filter=category.id">{{ category.label }}</button></nav>
    <AppScrollArea tag="div" class="shop-results" :aria-busy="loading || viewLoading">
      <AppSkeleton v-if="loading || viewLoading" variant="products" :rows="4" label="正在加载物品" />
      <template v-else-if="tab==='catalog'">
        <article v-for="item in filteredCatalog" :key="item.id" class="shop-product">
          <div class="item-art" :class="{'bubble-art':item.category==='DECORATION'}" :style="artStyle(item.id)" role="img" :aria-label="item.name" />
          <div class="product-info"><header><h3>{{ item.name }}</h3><span class="item-category">{{ item.category==='CARD' ? '道具卡' : '装饰' }}</span></header><p class="product-description">{{ item.description || descriptions[item.id] }}</p><div v-if="isBubbleItem(item.id)" class="shop-bubble-preview"><NavalChatBubble :bubble-style="item.id.slice(7)">舰队已就位</NavalChatBubble></div><div class="product-terms"><span><Clock3 :size="11" />{{ duration(item.durationHours) }}</span><span v-if="item.dailyLimit">每日限购 {{ item.dailyLimit }} 张</span></div><footer><strong class="item-price"><Coins :size="14" />{{ money(item.price) }}</strong><button v-if="hasActiveDecoration(item)" type="button" class="owned-item" @click="tab='inventory';filter='DECORATION'"><Check :size="13" />已拥有</button><button v-else class="purchase-item" type="button" :disabled="busy || loading || !canPurchase(item)" @click="preparePurchase(item)"><ShoppingBag v-if="canPurchase(item)" :size="13" />{{ purchaseLabel(item) }}</button></footer></div>
        </article>
        <p v-if="!filteredCatalog.length && !loading" class="shop-empty">暂无商品</p>
      </template>
      <template v-else>
        <article v-for="entry in filteredInventory" :key="entry.id" class="shop-product inventory-product" :class="{inactive:status(entry)!=='ACTIVE',equipped:entry.equipped && status(entry)==='ACTIVE'}">
          <div class="item-art" :class="{'bubble-art':catalogItem(entry.itemId).category==='DECORATION'}" :style="artStyle(entry.itemId)" role="img" :aria-label="catalogItem(entry.itemId).name" />
          <div class="product-info"><header><h3>{{ catalogItem(entry.itemId).name }}</h3><span v-if="entry.equipped && status(entry)==='ACTIVE'" class="equipped-state"><Check :size="11" />已装备</span><span v-else class="item-category">{{ catalogItem(entry.itemId).category==='CARD' ? '道具卡' : '装饰' }}</span></header><p class="product-description">{{ descriptions[entry.itemId] || catalogItem(entry.itemId).description }}</p><div class="inventory-bottom"><span class="item-expiry" :class="{expiring:status(entry)==='ACTIVE' && Number(entry.expiresAt)-now<3600000}"><Clock3 v-if="status(entry)==='ACTIVE'" :size="11" />{{ status(entry)==='ACTIVE' ? remaining(entry.expiresAt) : status(entry)==='USED' ? '已使用' : '已过期' }}</span><template v-if="status(entry)==='ACTIVE'"><button v-if="catalogItem(entry.itemId).category==='CARD'" type="button" class="use-item" :disabled="busy || loading || entry.canUse===false" :title="entry.unavailableReason || '使用道具卡'" @click="prepareUse(entry)"><Sparkles :size="13" />使用</button><button v-else type="button" :class="entry.equipped ? 'unequip-item' : 'use-item'" :disabled="busy || loading" @click="equip(entry)"><Check v-if="!entry.equipped" :size="13" />{{ entry.equipped ? '卸下' : '装备' }}</button></template></div><p v-if="status(entry)==='ACTIVE' && entry.canUse===false && entry.unavailableReason" class="unavailable-reason">{{ entry.unavailableReason }}</p></div>
        </article>
        <div v-if="!filteredInventory.length && !loading" class="shop-empty"><Backpack :size="35" /><span>暂无{{ filter==='CARD' ? '道具卡' : filter==='DECORATION' ? '装饰' : '物品' }}</span><button type="button" @click="tab='catalog'">去商城看看<ArrowRight :size="13" /></button></div>
      </template>
    </AppScrollArea>
    <AppDialog :open="Boolean(intent)" :title="intent?.title" :dismissible="!busy" @close="intent=null"><h2 class="shop-confirm-heading">{{ intent?.title }}</h2><p class="shop-confirm-copy">{{ intent?.message }}</p><footer class="shop-confirm-actions"><button type="button" class="cancel-action" :disabled="busy" @click="intent=null">取消</button><button type="button" class="confirm-action" :disabled="busy" @click="confirmAction"><LoaderCircle v-if="busy" class="spinning" :size="14" />{{ busy ? '处理中...' : intent?.kind==='purchase' ? '确认购买' : '确认使用' }}</button></footer></AppDialog>
  </section>
</template>

<script setup>
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { ArrowRight, Backpack, Check, Clock3, Coins, LoaderCircle, RefreshCw, ShoppingBag, Sparkles } from 'lucide-vue-next'
import AppDialog from '../../components/AppDialog.vue'
import AppScrollArea from '../../components/AppScrollArea.vue'
import AppSkeleton from '../../components/AppSkeleton.vue'
import NavalChatBubble from './NavalChatBubble.vue'
import { shopArtwork, isBubbleItem } from '../../games/shopArtwork'
import { notifyError, notifySuccess } from '../../services/notice'
import { navalShop as shop, navalShopActing, navalShopError as loadError, navalShopLoading as loading, performShopAction, refreshNavalShop, shopIntentId, syncNavalShopAccount } from '../../games/navalShop'
import { ownNavalProgression as progression } from '../../games/navalProgression'

const props=defineProps({ initialTab:{type:String,default:'catalog'}, embedded:Boolean })
const tab=ref(props.initialTab),filter=ref('ALL'),localBusy=ref(false),intent=ref(null),actionError=ref(''),operationError=ref(''),success=ref(''),tick=ref(Date.now())
const viewLoading=ref(false)
let viewFrame,viewEndFrame
watch([tab,filter],()=>{
  cancelAnimationFrame(viewFrame);cancelAnimationFrame(viewEndFrame);viewLoading.value=true
  viewFrame=requestAnimationFrame(()=>{viewEndFrame=requestAnimationFrame(()=>{viewLoading.value=false})})
},{flush:'sync'})
const busy=computed(()=>localBusy.value || navalShopActing.value)
const error=computed(()=>operationError.value || loadError.value)
watch(error,value=>{if(value)notifyError(value)},{immediate:true,flush:'sync'})
watch(actionError,value=>{if(value)notifyError(value)},{flush:'sync'})
watch(success,value=>{if(value)notifySuccess(value)},{flush:'sync'})
const categories=[{id:'ALL',label:'全部'},{id:'CARD',label:'道具卡'},{id:'DECORATION',label:'装饰'}]
const descriptions={'task-reset':'刷新今日任务，保留已完成待领奖励。','score-100':'增加 100 等级分，晋升考核期间不可使用。','bubble-captain':'船长帽与海上绳结。','bubble-pirate':'海盗三角帽与宝藏印记。','bubble-tabby':'橘猫陪伴，爪印相随。'}
const catalog=computed(()=>shop.value?.items || []),inventory=computed(()=>shop.value?.inventory || [])
const activeInventory=computed(()=>inventory.value.filter(entry=>status(entry)==='ACTIVE'))
const filteredCatalog=computed(()=>catalog.value.filter(item=>filter.value==='ALL'||item.category===filter.value))
const filteredInventory=computed(()=>inventory.value.filter(entry=>filter.value==='ALL'||catalogItem(entry.itemId).category===filter.value))
const serverOffset=ref(0)
let clockTimer,expiryTimer,disposed=false
const now=computed(()=>tick.value+serverOffset.value)
const money=value=>Number(value||0).toLocaleString('zh-CN',{maximumFractionDigits:2})
const catalogItem=id=>catalog.value.find(item=>item.id===id)||{id,name:'物品',category:id?.startsWith('bubble-')?'DECORATION':'CARD'}
const status=entry=>entry.status==='USED'?'USED':entry.status==='EXPIRED'||Number(entry.expiresAt)<=now.value?'EXPIRED':'ACTIVE'
const duration=hours=>Number(hours)>24 ? `${Number(hours)/24} 天有效` : `${hours} 小时有效`
const artStyle=shopArtwork
const hasActiveDecoration=item=>item.category==='DECORATION'&&activeInventory.value.some(entry=>entry.itemId===item.id)
const canPurchase=item=>item.canPurchase!==false&&(!item.dailyLimit||Number(item.purchasedToday||0)<Number(item.dailyLimit))&&Number(shop.value?.coins||0)>=Number(item.price)
function purchaseLabel(item){if(item.dailyLimit&&Number(item.purchasedToday||0)>=Number(item.dailyLimit))return '今日已购';if(Number(shop.value?.coins||0)<Number(item.price))return '金币不足';return '购买'}
function remaining(at){const minutes=Math.max(1,Math.ceil((Number(at)-now.value)/60000));return minutes>=1440?`${Math.floor(minutes/1440)} 天 ${Math.floor(minutes%1440/60)} 小时后过期`:minutes>=60?`${Math.floor(minutes/60)} 小时 ${minutes%60} 分钟后过期`:`${minutes} 分钟后过期`}
function preparePurchase(item){actionError.value='';operationError.value='';success.value='';intent.value={kind:'purchase',id:item.id,requestId:shopIntentId('purchase',item.id),title:`购买${item.name}`,message:`${money(item.price)} 金币 · ${duration(item.durationHours)}，有效期从购买时开始。`}}
function prepareUse(entry){actionError.value='';operationError.value='';success.value='';intent.value={kind:'use',id:entry.id,requestId:shopIntentId('use',entry.id),title:`使用${catalogItem(entry.itemId).name}`,message:entry.itemId==='task-reset'?'重置今日任务进度，保留已完成待领取的奖励。':'增加 100 等级分。'}}
async function confirmAction(){if(busy.value||!intent.value)return;const action={...intent.value};localBusy.value=true;actionError.value='';try{const value=await performShopAction(action.kind,action.id,action.requestId);if(!disposed&&value){success.value=action.kind==='purchase'?'购买成功，已放入我的物品':'道具已使用';intent.value=null;tick.value=Date.now()}}catch(e){if(!disposed)actionError.value=e.message}finally{localBusy.value=false}}
async function equip(entry){if(busy.value)return;localBusy.value=true;operationError.value='';success.value='';const unequipping=entry.equipped;try{await performShopAction('equip',unequipping?null:entry.id);if(!disposed)success.value=unequipping?'已卸下聊天气泡':'已装备聊天气泡'}catch(e){if(!disposed)operationError.value=e.message}finally{localBusy.value=false}}
function scheduleExpiry(){clearTimeout(expiryTimer);serverOffset.value=Number(shop.value?.serverTime||Date.now())-Date.now();tick.value=Date.now();const times=inventory.value.filter(entry=>entry.status==='ACTIVE').map(entry=>Number(entry.expiresAt)).filter(at=>at>now.value);if(times.length)expiryTimer=setTimeout(()=>{tick.value=Date.now();refreshNavalShop({force:true})},Math.min(2147483647,Math.min(...times)-now.value+150))}
function visible(){if(document.visibilityState==='visible'){tick.value=Date.now();refreshNavalShop({force:true})}}
watch(()=>shop.value,scheduleExpiry)
onMounted(()=>{syncNavalShopAccount();refreshNavalShop();scheduleExpiry();clockTimer=setInterval(()=>{tick.value=Date.now()},30000);document.addEventListener('visibilitychange',visible)})
onUnmounted(()=>{disposed=true;cancelAnimationFrame(viewFrame);cancelAnimationFrame(viewEndFrame);clearInterval(clockTimer);clearTimeout(expiryTimer);document.removeEventListener('visibilitychange',visible)})
</script>

<style scoped>
.naval-shop{display:flex;flex-direction:column;min-height:0;flex:1;color:#526d78}.shop-wallet{display:flex;align-items:center;justify-content:space-between;gap:12px;padding:7px 2px 18px}.shop-wallet>div>span{font-size:10px;color:#91a1a8;display:block;margin-bottom:4px}.shop-wallet strong{display:flex;align-items:center;gap:8px;color:var(--color-primary-strong);font-size:25px;font-weight:650;line-height:1.3;font-variant-numeric:tabular-nums;overflow-wrap:anywhere}.shop-wallet strong>svg{flex-shrink:0;color:var(--color-primary)}.shop-refresh{display:grid;place-items:center;width:30px;height:30px;flex-shrink:0;border-radius:4px;background:transparent;color:#899ca4}.shop-refresh:hover{background:var(--color-primary-bg);color:var(--color-primary-strong)}.shop-tabs{display:flex;border-bottom:1px solid #dfe7ea;flex-shrink:0;gap:22px}.shop-tabs button{display:flex;align-items:center;justify-content:center;gap:6px;min-height:37px;padding:0 0 10px;background:transparent;color:#8a9ea6;border-bottom:2px solid transparent;font-size:12px}.shop-tabs button.active{border-bottom-color:var(--color-primary);color:var(--color-primary-strong)}.shop-tabs button>span{font-size:10px;padding:0 3px;min-width:15px;color:#99a7ad;font-variant-numeric:tabular-nums}.shop-categories{display:flex;align-items:center;gap:18px;min-height:43px;flex-shrink:0}.shop-categories button{font-size:11px;padding:5px 0;background:transparent;color:#91a1a8}.shop-categories button.active{font-weight:600;color:#536d78}.shop-results{flex:1;min-height:0;padding:0 2px}.shop-product{display:grid;grid-template-columns:68px minmax(0,1fr);gap:13px;padding:17px 0;border-bottom:1px solid #e3e9eb;min-width:0}.shop-product:first-child{padding-top:8px}.shop-product:last-child{border-bottom:0}.item-art{width:68px;height:68px;align-self:start;background-position:center;background-repeat:no-repeat;background-size:contain;border-radius:4px}.item-art.bubble-art{background-size:200% 100%;background-position:left center}.product-info{min-width:0}.product-info>header{display:flex;align-items:flex-start;gap:8px;justify-content:space-between;min-height:21px}.product-info h3{font-size:13px;font-weight:600;line-height:1.6;color:#3e5964;overflow-wrap:anywhere;margin:0}.item-category{font-size:9px;color:#a5b0b5;white-space:nowrap;margin-top:3px}.product-description{font-size:10px;color:#91a1a8;line-height:1.65;margin-top:4px;overflow-wrap:anywhere}.product-terms{display:flex;align-items:center;gap:4px 10px;flex-wrap:wrap;font-size:9px;color:#a4adb1;margin-top:7px}.product-terms>span{display:inline-flex;align-items:center;gap:3px}.product-info footer,.inventory-bottom{display:flex;align-items:center;justify-content:space-between;gap:8px;margin-top:11px;min-height:29px}.item-price{display:inline-flex;align-items:center;gap:4px;font-size:13px;color:#b47b3b;font-weight:600;line-height:1.3;font-variant-numeric:tabular-nums}.purchase-item,.use-item,.owned-item,.unequip-item{display:inline-flex;align-items:center;justify-content:center;gap:4px;min-height:29px;min-width:61px;padding:5px 9px;border-radius:4px;background:var(--color-primary);color:#fff;font-size:10px;flex-shrink:0}.purchase-item:hover:not(:disabled),.use-item:hover:not(:disabled){background:var(--color-primary-hover)}.purchase-item:disabled{background:#edf1f3;color:#9daab0;opacity:1}.owned-item,.unequip-item{background:var(--color-primary-bg);color:var(--color-primary-strong)}.item-expiry{display:inline-flex;align-items:center;gap:3px;font-size:9px;color:#99a8af;overflow-wrap:anywhere;line-height:1.5}.item-expiry.expiring{color:#c77e39}.equipped-state{display:inline-flex;align-items:center;gap:3px;margin-top:3px;font-size:9px;color:var(--color-primary-strong);white-space:nowrap}.inventory-product.inactive .item-art{opacity:.4;filter:grayscale(.7)}.inventory-product.inactive h3{color:#9eabb1}.inventory-product.equipped .equipped-state{font-weight:600}.unavailable-reason{font-size:9px;line-height:1.5;color:#b4875b;margin-top:5px}.shop-error,.shop-success{display:flex;align-items:center;gap:5px;font-size:11px;line-height:1.6;overflow-wrap:anywhere;padding:7px 0;flex-shrink:0}.shop-error{color:#bc6555}.shop-success{color:var(--color-primary-strong)}.shop-empty{display:flex;flex-direction:column;align-items:center;justify-content:center;gap:13px;min-height:180px;color:#a0adb3;font-size:11px}.shop-empty>svg{color:#c6d0d5}.shop-empty button{display:flex;align-items:center;gap:4px;background:transparent;color:var(--color-primary-strong);font-size:11px;min-height:30px}.naval-shop.embedded{flex:none}.embedded .shop-results{overflow:visible;flex:none}.embedded .shop-wallet{padding-top:15px}.shop-confirm-heading{font-size:17px;line-height:1.6;color:#405c66;padding-right:20px}.shop-confirm-copy{font-size:12px;line-height:1.8;overflow-wrap:anywhere;color:#8598a1;margin-top:14px}.shop-confirm-actions{display:flex;align-items:center;justify-content:flex-end;gap:8px;margin-top:24px}.shop-confirm-actions button{display:inline-flex;align-items:center;justify-content:center;gap:5px;min-width:80px;min-height:36px;padding:7px 11px;border-radius:4px;font-size:12px}.cancel-action{background:#edf2f4;color:#7b949e}.confirm-action{background:var(--color-primary);color:#fff}.spinning{animation:shop-spin 1s linear infinite}@keyframes shop-spin{to{transform:rotate(360deg)}}@media(max-width:360px){.shop-product{grid-template-columns:57px minmax(0,1fr);gap:10px}.item-art{width:57px;height:57px}.product-info h3{font-size:12px}.product-description{font-size:10px}}@media(prefers-reduced-motion:reduce){.spinning{animation:none}}
</style>
<style scoped>.shop-results>.app-skeleton{grid-column:1/-1}.shop-toolbar{flex-shrink:0;min-width:0}.shop-wallet-actions{display:flex;align-items:center;gap:8px;flex-shrink:0}.embedded-shop-switch{display:flex;align-items:center;gap:4px;min-height:30px;padding:3px 0;background:transparent;color:var(--color-primary-strong);font-size:11px}.shop-bubble-preview{padding:13px 3px 5px;min-width:0}.shop-bubble-preview :deep(.naval-chat-bubble){font-size:11px;line-height:1.6}
@media(orientation:landscape) and (max-height:550px){
  .naval-shop:not(.embedded) .shop-toolbar{display:grid;grid-template-columns:minmax(140px,auto) minmax(0,1fr);align-items:center;gap:16px;border-bottom:1px solid #dfe7ea}
  .naval-shop:not(.embedded) .shop-wallet{padding:0;min-height:36px;gap:7px}
  .naval-shop:not(.embedded) .shop-wallet>div:first-child{display:flex;align-items:center;gap:7px;min-width:0}
  .naval-shop:not(.embedded) .shop-wallet>div>span{margin:0;font-size:10px;white-space:nowrap}
  .naval-shop:not(.embedded) .shop-wallet strong{font-size:19px;gap:4px;line-height:1.2}
  .naval-shop:not(.embedded) .shop-wallet strong>svg{width:17px;height:17px}
  .naval-shop:not(.embedded) .shop-refresh{width:30px;height:30px}
  .naval-shop:not(.embedded) .shop-tabs{justify-content:flex-end;gap:20px;border-bottom:0}
  .naval-shop:not(.embedded) .shop-tabs button{min-height:36px;padding:0 2px;font-size:11px;white-space:nowrap}
  .naval-shop:not(.embedded) .shop-categories{min-height:30px;gap:20px}
  .naval-shop:not(.embedded) .shop-categories button{padding:4px 0;font-size:10px}
  .naval-shop:not(.embedded) .shop-results{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));align-content:start;column-gap:22px;padding:0 2px}
  .naval-shop:not(.embedded) .shop-product{grid-template-columns:52px minmax(0,1fr);gap:10px;padding:12px 0;align-self:start}
  .naval-shop:not(.embedded) .shop-product:first-child{padding-top:12px}
  .naval-shop:not(.embedded) .item-art{width:52px;height:52px}
  .naval-shop:not(.embedded) .product-info h3{font-size:12px}
  .naval-shop:not(.embedded) .product-description{margin-top:2px;font-size:10px;line-height:1.5}
  .naval-shop:not(.embedded) .product-terms{margin-top:5px}
  .naval-shop:not(.embedded) .product-info footer,.naval-shop:not(.embedded) .inventory-bottom{margin-top:7px;min-height:30px}
  .naval-shop:not(.embedded) .shop-bubble-preview{padding-top:8px;padding-bottom:1px}
  .naval-shop:not(.embedded) .shop-empty{grid-column:1/-1;min-height:120px}
}
@media(orientation:landscape) and (max-height:550px) and (max-width:620px){
  .naval-shop:not(.embedded) .shop-toolbar{gap:8px;grid-template-columns:minmax(110px,auto) minmax(0,1fr)}
  .naval-shop:not(.embedded) .shop-wallet>div>span{display:none}
  .naval-shop:not(.embedded) .shop-tabs{gap:12px}
  .naval-shop:not(.embedded) .shop-results{column-gap:12px}
  .naval-shop:not(.embedded) .shop-product{grid-template-columns:40px minmax(0,1fr);gap:7px}
  .naval-shop:not(.embedded) .item-art{width:40px;height:40px}
  .naval-shop:not(.embedded) .item-category{display:none}
}
</style>
