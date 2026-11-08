<template>
  <section ref="panelElement" class="naval-community" :class="{ mobile, 'keyboard-open': keyboardOpen, 'shop-panel': tab === 'shop', 'chat-panel': tab === 'chat' }" :style="keyboardStyle" :aria-label="title">
    <header class="community-heading"><h2>{{ title }}<span v-if="detail || detailLoading"> / 战斗详情</span></h2><button class="small-icon" title="关闭面板" aria-label="关闭社区面板" @click="$emit('close')"><X :size="19" /></button></header>
    <NavalProfile v-if="tab === 'profile'" @updated="$emit('profile-update',$event)" @logout="$emit('logout')" />
    <NavalNotifications v-else-if="tab === 'notifications'" />
    <NavalShop v-else-if="tab === 'shop'" />
    <NavalMedalWall v-else-if="tab === 'medals'" :person="medalPerson" />
    <AppScrollArea tag="section" v-else-if="detail || detailLoading" class="history-detail community-body" aria-label="战斗详情" :aria-busy="detailLoading">
      <button class="detail-back" @click="closeDetail"><ArrowLeft :size="16" />返回{{ tab === 'history' ? '战绩列表' : '排行榜' }}</button>
      <AppSkeleton v-if="detailLoading" variant="detail" label="正在加载战斗详情" />
      <template v-else>
        <p class="detail-date">{{ ruleLabel(detail) }} · {{ detail.mode === 'AI' ? '人机对战' : '双人对战' }} · {{ date(detail.finishedAt) }}</p>
        <p v-if="detail.legacy" class="community-empty">这场旧战绩仅保存了胜负，新对局会保留道具和攻击记录。</p>
        <button v-if="!detail.legacy" class="replay-entry" :disabled="!detail.players?.every(person => person.ships?.length) || Boolean(room && room.phase !== 'FINISHED')" @click="$emit('replay', detail)"><Film :size="17" />对局回放</button>
        <button v-if="!detail.legacy && !detail.shared && (detail.you >= 0 || detail.local)" class="replay-entry" :disabled="sending || detail.lobbyShared" @click="shareResult"><Share2 :size="17" />{{ detail.lobbyShared ? '已分享到大厅' : '分享战绩到大厅' }}</button>
        <div class="detail-players"><section v-for="(person, index) in detail.players || []" :key="index"><h3><Trophy v-if="detail.winner === index" :size="17" />{{ person.name }}</h3><dl><dt>金币花费</dt><dd>{{ person.coinsSpent ?? '--' }}</dd><dt>攻击次数</dt><dd>{{ person.attacks ?? '--' }}</dd><dt>命中 / 攻击格数</dt><dd>{{ person.hits ?? '--' }} / {{ person.shots ?? '--' }}</dd></dl><template v-if="detail.ruleSet==='MILITARY'"><h4>采购舰队</h4><ul><li v-for="(count,kind) in fleetCounts(person)" :key="kind">{{ MILITARY_SHIPS[kind]?.name || kind }} × {{ count }}</li></ul></template><template v-for="group in detail.ruleSet==='MILITARY' ? [] : itemGroups" :key="group.key"><h4>{{ group.label }}</h4><ul><li v-for="(count, key) in nonzero(person[group.key])" :key="key"><img :src="`/naval/fleet/${key}.png`" alt="" />{{ ITEMS[key]?.name || key }} × {{ count }}</li><li v-if="!Object.keys(nonzero(person[group.key])).length" class="muted">无</li></ul></template></section></div>
        <details v-if="detail.events?.length"><summary>战斗行动记录 · {{ detail.events.length }}</summary><ol><li v-for="event in detail.events" :key="event.id"><strong>{{ detail.players?.[event.player]?.name }}</strong> · {{ ITEMS[event.weapon]?.name || (event.weapon === 'shot' ? '舰炮' : '战局') }}<span>{{ event.text }}</span><small v-if="event.cells?.length">{{ event.cells.map(cell => `${String.fromCharCode(65 + cell.x)}${cell.y + 1}`).join('、') }}</small></li></ol></details>
      </template>
    </AppScrollArea>
    <NavalRooms v-else-if="tab === 'rooms'" :current-room="room" :busy="sending" @enter="$emit('join', $event)" />
    <AppScrollArea tag="div" v-else-if="tab === 'reviews'" class="community-body"><ReviewPanel /></AppScrollArea>
    <template v-else-if="tab === 'ranking'">
      <nav class="ranking-subtabs" role="tablist" aria-label="排行榜对战类型"><button v-for="item in modes" :key="item.value" role="tab" :aria-selected="mode === item.value" :class="{ active: mode === item.value }" @click="mode = item.value">{{ item.label }}</button></nav>
      <nav class="ranking-subtabs metrics-tabs" role="tablist" aria-label="排行榜指标"><button v-for="item in metrics" :key="item.value" role="tab" :aria-selected="metric === item.value" :class="{ active: metric === item.value }" @click="metric = item.value">{{ item.label }}</button></nav>
      <p class="ranking-note">注册玩家榜 · {{ metric === 'economy' ? '每位玩家最佳获胜记录 · 对手花费 − 自己花费' : metric === 'rate' ? '按胜率排序，展示对局样本数' : '按累计获胜场数排序' }}</p>
      <AppScrollArea tag="div" class="community-body ranking-table" :aria-busy="loading"><AppSkeleton v-if="loading" variant="ranking" :rows="pageSize" label="正在加载排行榜" /><template v-else><div class="ranking-row table-heading"><span>排名</span><span>指挥官</span><span>{{ metric === 'economy' ? '节省金币' : metric === 'rate' ? '胜率' : '胜场' }}</span><span>{{ metric === 'economy' ? '战绩' : '对局' }}</span></div><div v-for="(row, index) in pageRanking" :key="`${row.name}-${row.gameId || index}`" class="ranking-row"><span>{{ page * pageSize + index + 1 }}</span><div class="ranking-person"><NavalAvatar :person="{ ...row, registered: true }" :size="22" /><strong>{{ row.name }}</strong></div><strong>{{ metric === 'economy' ? `${row.difference > 0 ? '+' : ''}${row.difference}` : metric === 'rate' ? `${Number(row.rate).toFixed(1)}%` : row.wins }}</strong><button v-if="row.gameId" class="detail-link" @click="openDetail(row)">查看</button><span v-else>{{ row.games }} 场</span></div><p v-if="error" class="community-error"><span>{{ error }}</span><button class="small-icon" aria-label="重新读取排行榜" @click="load()"><RotateCcw :size="15" /></button></p><p v-else-if="!ranking.length" class="community-empty">暂无上榜战绩</p></template></AppScrollArea>
      <nav v-if="rankingTotal > pageSize" class="pagination" aria-label="排行榜分页"><button :disabled="loading || !page" aria-label="上一页" @click="page--"><ChevronLeft :size="18" /></button><span>{{ page + 1 }} / {{ Math.ceil(rankingTotal / pageSize) }}</span><button :disabled="loading || (page + 1) * pageSize >= rankingTotal" aria-label="下一页" @click="page++"><ChevronRight :size="18" /></button></nav>
    </template>
    <template v-else-if="tab === 'history'"><AppScrollArea tag="div" class="community-body" :aria-busy="historyLoading || historyPageLoading"><AppSkeleton v-if="historyLoading || historyPageLoading" :rows="pageSize" label="正在加载战绩" /><template v-else><p v-if="!history.length" class="community-empty">暂无战斗记录</p><button v-for="item in pageHistory" :key="item.gameId || item.id" class="history-entry" @click="openDetail(item)"><strong :class="item.won ? 'won' : 'lost'">{{ item.won ? '胜利' : '战败' }}</strong><span>{{ ruleLabel(item) }} · {{ item.mode === 'AI' ? '人机对战' : '双人对战' }}<small v-if="item.opponent">{{ item.opponent }}</small></span><time>{{ date(item.at || item.finishedAt) }}</time><ChevronRight :size="16" /></button></template></AppScrollArea><nav v-if="history.length > pageSize" class="pagination" aria-label="战绩分页"><button :disabled="historyLoading || historyPageLoading || !page" aria-label="上一页" @click="page--"><ChevronLeft :size="18" /></button><span>{{ page + 1 }} / {{ Math.ceil(history.length / pageSize) }}</span><button :disabled="historyLoading || historyPageLoading || (page + 1) * pageSize >= history.length" aria-label="下一页" @click="page++"><ChevronRight :size="18" /></button></nav></template>
    <AppScrollArea tag="section" v-else class="global-chat community-body">
      <AppScrollArea tag="div" ref="chatLog" class="lobby-chat-log" aria-live="polite" :aria-busy="chatLoading">
        <AppSkeleton v-if="chatLoading" :rows="4" label="正在加载聊天记录" />
        <p v-else-if="!messages.length" class="community-empty">还没有消息</p>
        <NavalChatMessage v-for="(message, index) in messages" :key="message.id" class="lobby-message" :message="message" :mine="message.mine" :show-time="navalChatTimeVisible(messages, index)" :rich="Boolean(message.room || message.battle || message.stickerId)">
          <button v-if="message.room" class="lobby-room-card" :class="{ inactive: !joinable(message.room) }" :disabled="!joinable(message.room) || Boolean(room)" @click="selectRoom(message.room)"><Anchor :size="24" /><span><strong>{{ ruleLabel(message.room) }}邀请 · {{ message.room.names?.join(' / ') || message.name }}</strong><small>{{ roomLabel(message.room) }}</small></span><Eye v-if="message.room.status === 'STARTED'" :size="20" /><ChevronRight v-else :size="20" /></button>
          <button v-else-if="message.battle" class="lobby-room-card battle-result-card" aria-label="查看分享战绩" @click="openDetail({ detail: message.battle })"><Trophy :size="24" /><span><strong>{{ message.battle.you === message.battle.winner ? '胜利战报' : '对战战报' }} · {{ message.battle.players?.map(person=>person.name).join(' / ') }}</strong><small>{{ ruleLabel(message.battle) }} · {{ message.battle.local ? '本地人机战绩 · 不计入排行榜' : message.battle.mode === 'AI' ? '人机对战' : '人人对战' }} · {{ message.battle.players?.[message.battle.winner]?.name }} 获胜</small><small>查看战绩与回放</small></span><Film :size="20" /></button>
          <NavalMessage v-else :text="message.stickerId ? stickerMessage(message.stickerId) : message.text" />
        </NavalChatMessage>
      </AppScrollArea>
      <form v-if="invitedRoom" class="invite-room-form" @submit.prevent="$emit('join', { ...invitedRoom, inviteCode: inviteText }); invitedRoom = null"><input v-model="inviteText" aria-label="分享房间邀请码" placeholder="输入此房间的 6 位邀请码" maxlength="6" /><button class="send-button" :disabled="inviteText.trim().length !== 6" aria-label="确认邀请码"><Check :size="18" /></button><button type="button" class="small-icon" aria-label="取消加入" @click="invitedRoom = null"><X :size="17" /></button></form>
      <button v-if="room?.phase === 'WAITING'" class="share-lobby" :disabled="sending || room.lobbyShared" @click="$emit('share')"><Share2 :size="15" />{{ room.lobbyShared ? '本局已分享到大厅' : '分享当前房间到大厅' }}</button>
      <NavalChatComposer v-model="text" :disabled="sending" :mobile="mobile" :keyboard-open="keyboardOpen" label="大厅消息" placeholder="和指挥官们聊聊" @send="send" @sticker="sendSticker" />
    </AppScrollArea>
  </section>
</template>
<script setup>
import AppScrollArea from "../../components/AppScrollArea.vue"
import AppSkeleton from '../../components/AppSkeleton.vue'
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import { MILITARY_SHIPS } from '../../games/navalMilitary'
import { Anchor, ArrowLeft, Check, ChevronLeft, ChevronRight, Eye, Film, RotateCcw, Share2, Trophy, X } from 'lucide-vue-next'
import ReviewPanel from './ReviewPanel.vue'
import NavalAvatar from './NavalAvatar.vue'
import NavalChatMessage from './NavalChatMessage.vue'
import NavalChatComposer from './NavalChatComposer.vue'
import NavalMessage from './NavalMessage.vue'
import NavalRooms from './NavalRooms.vue'
import NavalMedalWall from './NavalMedalWall.vue'
import NavalProfile from './NavalProfile.vue'
import NavalNotifications from './NavalNotifications.vue'
import NavalShop from './NavalShop.vue'
import { navalChatTimeVisible } from '../../games/navalChatPresentation'
import { stickerMessage } from '../../games/navalStickers'
import { ITEMS } from '../../games/navalEngine'
import { getNavalHistory, getNavalRankingPage, sendNavalLobbyMessage } from '../../api/naval'
import { subscribeLobby, refreshStreams } from '../../services/roomSync'
import { notifyError, notifySuccess } from '../../services/notice'
const props = defineProps({ ruleSet: { type: String, default: 'CLASSIC' }, panel: String, medalPerson: Object, mobile: Boolean, room: Object, history: { type: Array, default: () => [] }, historyLoading:Boolean })
const emit = defineEmits(['close', 'join', 'share', 'replay', 'messages', 'profile-update', 'logout'])
const panelElement = ref(null), keyboardOpen = ref(false), keyboardStyle = ref(null)
let viewportFrame, focusTimer, viewportBaseline = 0, viewportOrientation
function syncChatViewport() {
  viewportFrame = null
  if (!props.mobile || tab.value !== 'chat') { keyboardOpen.value = false; keyboardStyle.value = null; return }
  const viewport = window.visualViewport
  const height = viewport?.height || innerHeight, top = viewport?.offsetTop || 0
  const orientation = screen.orientation?.type || (innerWidth > innerHeight ? 'landscape' : 'portrait')
  const focused = panelElement.value?.contains(document.activeElement) && document.activeElement?.matches('input,textarea,[contenteditable=true]')
  if (!focused || orientation !== viewportOrientation) viewportBaseline = Math.max(innerHeight, height)
  viewportOrientation = orientation
  const keyboard = navigator.virtualKeyboard?.boundingRect
  const bottom = Math.min(top + height, keyboard?.height > 0 ? keyboard.top : Infinity)
  keyboardOpen.value = Boolean(focused && (Math.max(viewportBaseline, innerHeight) - height > 70 || keyboard?.height > 0))
  keyboardStyle.value = keyboardOpen.value ? {
    '--chat-visible-top': `${top}px`, '--chat-visible-left': `${viewport?.offsetLeft || 0}px`,
    '--chat-visible-width': `${viewport?.width || innerWidth}px`, '--chat-visible-height': `${Math.max(0, bottom - top)}px`
  } : null
}
function scheduleChatViewport() { if (viewportFrame == null) viewportFrame = requestAnimationFrame(syncChatViewport) }
function focusChatViewport() { scheduleChatViewport(); clearTimeout(focusTimer); focusTimer = setTimeout(scheduleChatViewport, 280) }
const tab = computed(() => props.panel)
const title = computed(() => props.panel === 'medals' ? '玩家主页' : ({ profile: '个人主页', notifications: '消息通知', shop: '商城', rooms: '游戏房间', ranking: `${props.ruleSet === 'MILITARY' ? '军事模式' : '经典模式'}排行榜`, history: '历史战绩', chat: '聊天大厅', reviews: '玩家点评' })[props.panel])
const fleetCounts = person => (person.ships || []).reduce((out,ship) => {out[ship.kind]=(out[ship.kind]||0)+1;return out}, {})
const ruleLabel = value => (value?.ruleSet || value?.detail?.ruleSet) === 'MILITARY' ? '军事模式' : '经典模式'
const modes = [{ value: 'AI', label: '人机对战' }, { value: 'MULTIPLAYER', label: '人人对战' }]
const metrics = [{ value: 'wins', label: '获胜场数' }, { value: 'rate', label: '胜率' }, { value: 'economy', label: '金币使用' }]
const itemGroups = [{ key: 'itemsPurchased', label: '购买道具' }, { key: 'itemsUsed', label: '道具消耗' }, { key: 'inventoryRemaining', label: '剩余道具' }]
const roomStatus = { WAITING: '等待加入 · 点击参加', STARTED: '此房间游戏已开始 · 点击观战', CLOSED: '此房间已关闭', FINISHED: '此房间已结束' }
const joinable = card => card.status === 'WAITING' || card.status === 'STARTED' && card.allowSpectators !== false
const roomLabel = card => `${card.status === 'STARTED' && card.allowSpectators === false ? '游戏已开始 · 无法观战' : roomStatus[card.status]}${card.requiresInvite ? ' · 需要邀请码' : ''}`
const invitedRoom = ref(null), inviteText = ref('')
function selectRoom(card) { if (card.requiresInvite) { invitedRoom.value = card; inviteText.value = '' } else emit('join', card) }
const mode = ref('AI'), metric = ref('wins'), ranking = ref([]), rankingTotal = ref(0), loading = ref(false), error = ref(''), page = ref(0)
const pageSize = computed(() => props.mobile ? 5 : 8)
const pageRanking = computed(() => ranking.value)
const pageHistory = computed(() => props.history.slice(page.value * pageSize.value, (page.value + 1) * pageSize.value))
const historyPageLoading = ref(false), chatLoading = ref(props.panel === 'chat')
let historyFrame, historyEndFrame
function historyTransition() {
  cancelAnimationFrame(historyFrame); cancelAnimationFrame(historyEndFrame)
  historyPageLoading.value = true
  historyFrame = requestAnimationFrame(() => { historyEndFrame = requestAnimationFrame(() => { historyPageLoading.value = false }) })
}
watch(page, () => { if (tab.value === 'history') historyTransition() }, {flush:'sync'})
const messages = ref([]), text = ref(''), sending = ref(false), chatLog = ref(null), detail = ref(null), detailLoading = ref(false)
let generation = 0, detailGeneration = 0, disposed = false
watch(error, value => { if(value)notifyError(value) })
const date = value => value ? new Date(value).toLocaleString('zh-CN', { month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' }) : ''
const nonzero = value => Object.fromEntries(Object.entries(value || {}).filter(([, count]) => count > 0))
async function load(silent = false) {
  const request = ++generation
  if (!silent) { loading.value = true; error.value = ''; if (tab.value === 'ranking') ranking.value = [] }
  try {
    if (tab.value === 'ranking') { const result = await getNavalRankingPage(mode.value, metric.value, props.ruleSet, page.value + 1, pageSize.value); if (!disposed && request === generation) { ranking.value = result.records; rankingTotal.value = result.total } }
    if (tab.value === 'chat') refreshStreams()
  } catch (e) { if (!disposed && request === generation) error.value = e.message || '读取失败' }
  finally { if (!disposed && request === generation) loading.value = false }
}
async function post(value) { if (sending.value) return; sending.value = true; error.value = ''; try { const result = await sendNavalLobbyMessage(value); if (disposed) return; messages.value = result; emit('messages', result); await nextTick(); chatLog.value?.scrollTo(0, chatLog.value.scrollHeight); return true } catch (e) { if (!disposed) error.value = e.message } finally { sending.value = false } }
async function send() { if (text.value.trim() && await post(text.value.trim())) text.value = '' }
const sendSticker = id => post({ stickerId: id })
function closeDetail() { detailGeneration++; detail.value = null; detailLoading.value = false }
async function openDetail(item) { const request = ++detailGeneration; detailLoading.value = true; error.value = ''; try { const result = item.detail || ((item.local || !item.gameId) ? { legacy: true, mode: item.mode, ruleSet: item.ruleSet, finishedAt: item.at } : await getNavalHistory(item.gameId || item.id)); if (request === detailGeneration && !disposed) detail.value = { ...result, ...(item.local ? { local: true, you: 0 } : {}) } } catch (e) { error.value = e.message } finally { if (request === detailGeneration) detailLoading.value = false } }
async function shareResult() {
  const result=detail.value
  if (!result || result.shared || result.lobbyShared) return
  if (await post(result.local ? { localResult: result } : { gameId: result.gameId })) { result.lobbyShared=true; notifySuccess('战绩已分享到大厅') }
}
watch([tab, mode, metric, () => props.ruleSet], () => { page.value = 0; closeDetail() }, { flush: 'sync' })
watch([tab, mode, metric, () => props.ruleSet, page, pageSize], () => load(), { immediate: true })
let stopLobby
watch(tab, value => {
  stopLobby?.();stopLobby=null
  if(value==='chat')chatLoading.value=!messages.value.length
  if(value==='chat')stopLobby=subscribeLobby(async result=>{
    if(disposed)return
    chatLoading.value=false
    const atBottom=!chatLog.value||chatLog.value.scrollHeight-chatLog.value.scrollTop-chatLog.value.clientHeight<60
    messages.value=result;emit('messages',result)
    if(atBottom){await nextTick();chatLog.value?.scrollTo(0,chatLog.value.scrollHeight)}
  })
}, {immediate:true})
watch([tab, () => props.mobile], () => nextTick(scheduleChatViewport))
onMounted(() => {
  syncChatViewport()
  window.visualViewport?.addEventListener('resize', scheduleChatViewport)
  window.visualViewport?.addEventListener('scroll', scheduleChatViewport)
  window.addEventListener('resize', scheduleChatViewport)
  navigator.virtualKeyboard?.addEventListener('geometrychange', scheduleChatViewport)
  document.addEventListener('focusin', focusChatViewport)
  document.addEventListener('focusout', focusChatViewport)
})
onUnmounted(() => {
  disposed = true; generation++; detailGeneration++; stopLobby?.()
  cancelAnimationFrame(historyFrame); cancelAnimationFrame(historyEndFrame)
  if (viewportFrame != null) cancelAnimationFrame(viewportFrame)
  clearTimeout(focusTimer)
  window.visualViewport?.removeEventListener('resize', scheduleChatViewport)
  window.visualViewport?.removeEventListener('scroll', scheduleChatViewport)
  window.removeEventListener('resize', scheduleChatViewport)
  navigator.virtualKeyboard?.removeEventListener('geometrychange', scheduleChatViewport)
  document.removeEventListener('focusin', focusChatViewport)
  document.removeEventListener('focusout', focusChatViewport)
})
</script>
<style scoped>
.sticker-tray{min-height:0}.sticker-tray:not(:empty){height:min(220px,38dvh);min-height:70px;flex:0 1 auto;overflow:auto;overscroll-behavior:contain}.sticker-tray:empty{display:none}.global-chat{gap:8px}.lobby-chat-log article{padding-bottom:12px}.lobby-chat-log header{margin-bottom:8px}.invite-room-form{align-items:center}.invite-room-form .small-icon{flex-shrink:0}
@media(max-height:450px){.global-chat:has(.sticker-picker) .lobby-chat-log,.global-chat:has(.sticker-picker) .chat-tools,.global-chat:has(.sticker-picker) .share-lobby{display:none}.global-chat:has(.sticker-picker){gap:6px}.sticker-tray:not(:empty){height:145px;min-height:110px;flex:1}}
</style>
<style scoped>
.naval-community.mobile.naval-community{inset:auto;top:var(--community-top,52px);left:var(--game-left,0px);width:var(--game-width,100%);height:calc(var(--game-top,0px) + var(--game-height,100dvh) - var(--community-top,52px));max-height:var(--game-height,100dvh)}.naval-community.mobile .global-chat input{font-size:16px;min-height:38px;scroll-margin-block:8px}.naval-community.mobile .global-chat .lobby-chat-log{min-height:0}.naval-community.mobile .global-chat form{min-height:38px;flex-shrink:0}
:global(.keyboard-visible .naval-community.mobile .chat-tools),:global(.keyboard-visible .naval-community.mobile .share-lobby),:global(.keyboard-visible .naval-community.mobile .sticker-tray){display:none}:global(.keyboard-visible .naval-community.mobile .global-chat){gap:5px}:global(.keyboard-visible .naval-community.mobile .community-heading){min-height:28px;margin-bottom:4px}:global(.keyboard-visible .naval-community.mobile .global-chat form){margin-top:auto}
</style>
<style scoped>
.replay-entry{display:flex;align-items:center;gap:6px;background:var(--color-primary-bg);color:var(--color-primary-strong);padding:8px 12px;border-radius:4px;font-size:12px;margin-top:12px}.replay-entry:disabled{opacity:.4}
.naval-community{position:relative;align-self:stretch;display:flex;flex-direction:column;width:100%;height:100%;min-height:0;min-width:0;padding:12px;background:#f9fbfc;box-sizing:border-box;border-radius:6px;color:#456a77;overflow:hidden;z-index:450;box-shadow:0 3px 20px #29404a12}.community-heading{display:flex;align-items:center;justify-content:space-between;gap:12px;flex-shrink:0;min-height:32px;margin-bottom:8px}.community-heading h2{font-size:15px}.community-heading h2 span{font-size:12px;font-weight:400}.community-body{min-height:0;flex:1;overflow:auto;overscroll-behavior:contain}.ranking-subtabs{display:flex;flex-shrink:0;gap:4px;margin-bottom:6px;background:#eff3f4;border-radius:4px;padding:3px}.ranking-subtabs button{flex:1;min-width:0;height:30px;padding:0 6px;border-radius:3px;background:transparent;font-size:12px;color:#7a8d96}.ranking-subtabs button.active{background:white;color:var(--color-primary-strong)}.metrics-tabs{background:transparent;padding:0}.metrics-tabs button.active{background:var(--color-primary-bg)}.ranking-note,.community-empty{font-size:11px;color:#80949b;padding:8px 0;line-height:1.6}.community-error{display:flex;align-items:center;gap:10px;color:#b45547;font-size:12px;flex-shrink:0}.ranking-row{display:grid;grid-template-columns:30px minmax(0,1fr) 68px 46px;gap:6px;align-items:center;min-height:42px;font-size:12px}.ranking-row:nth-child(even){background:#f0f4f5}.table-heading{font-size:11px;color:#82959a;min-height:26px}.ranking-row>span:first-child{text-align:center}.ranking-person{display:flex;gap:8px;align-items:center;min-width:0}.ranking-person strong{overflow-wrap:anywhere}.detail-link{padding:6px;background:transparent;color:#b57e3d;text-align:left}.history-entry{display:flex;align-items:center;width:100%;gap:12px;background:transparent;padding:12px 3px;color:#67838d;text-align:left;font-size:12px}.history-entry:hover{background:#f0f4f5}.history-entry small{display:block;font-size:11px;margin-top:3px}.history-entry time{margin-left:auto;font-size:11px}.won{color:#42876c}.lost{color:#bb786b}.pagination{display:flex;align-items:center;justify-content:center;gap:15px;flex-shrink:0;padding-top:8px;font-size:11px}.pagination button,.small-icon{width:32px;height:32px;display:grid;place-items:center;background:transparent;color:inherit;flex-shrink:0}.pagination button:disabled{opacity:.3}.global-chat{display:flex;flex-direction:column;overflow:hidden;gap:4px}.lobby-chat-log{flex:1;min-height:30px;overflow:auto;background:#f0f4f5;padding:6px 10px;border-radius:4px;overscroll-behavior:contain}.lobby-chat-log article{padding:6px 0;font-size:13px;line-height:1.6;white-space:pre-wrap;overflow-wrap:anywhere}.lobby-chat-log header{display:flex;align-items:center;justify-content:space-between;gap:12px;font-size:11px;margin-bottom:3px}.lobby-chat-log time,.muted{color:#91a2a7}.global-chat form{display:flex;gap:8px;flex-shrink:0}.global-chat input{flex:1;min-width:0;padding:9px;border:1px solid #dce6e9;border-radius:4px;background:#fff;font:inherit;font-size:13px}.send-button{display:grid;place-items:center;width:38px;background:var(--color-primary);color:#fff;border-radius:4px}.send-button:disabled{opacity:.4}.history-detail{font-size:12px}.detail-back{display:flex;align-items:center;gap:6px;background:transparent;color:var(--color-primary-strong);padding:4px 0 10px}.detail-date{color:#80949b}.detail-players{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:18px;margin-top:14px}.detail-players h3{font-size:13px;display:flex;align-items:center;gap:6px;overflow-wrap:anywhere}.detail-players h3 svg{color:#c1934a;flex-shrink:0}.detail-players h4{font-size:11px;margin:12px 0 6px}.detail-players dl{display:grid;grid-template-columns:1fr auto;gap:6px;font-size:11px;margin-top:10px}.detail-players dd{margin:0}.detail-players ul{list-style:none;padding:0;display:flex;flex-wrap:wrap;gap:6px;font-size:11px}.detail-players li{display:flex;align-items:center;gap:3px}.detail-players img{width:22px;height:22px;object-fit:contain;image-rendering:pixelated}.history-detail details{margin-top:20px}.history-detail summary{cursor:pointer}.history-detail ol{padding-left:20px}.history-detail ol li{padding:8px 0}.history-detail ol span,.history-detail ol small{display:block;line-height:1.6;overflow-wrap:anywhere}.history-detail ol small{color:#90a5aa}.lobby-room-card{display:flex;align-items:center;gap:10px;width:100%;padding:10px;background:var(--color-primary-bg);border-radius:5px;color:var(--color-primary-strong);text-align:left}.lobby-room-card>span{flex:1;min-width:0}.lobby-room-card strong{font-size:12px;display:block;overflow-wrap:anywhere}.lobby-room-card small{display:block;margin-top:4px;font-size:11px}.lobby-room-card>svg{flex-shrink:0}.lobby-room-card.inactive{filter:grayscale(1);opacity:.55}.share-lobby{display:flex;justify-content:center;align-items:center;gap:6px;background:var(--color-primary-bg);color:var(--color-primary-strong);font-size:11px;padding:5px;border-radius:3px;flex-shrink:0}.naval-community :deep(.game-reviews){border:0;margin:0;padding:0}
.naval-community.mobile{position:fixed;inset:var(--community-top,52px) 0 0;width:100%;height:calc(100dvh - var(--community-top,52px));max-height:none;border-radius:0;z-index:450;padding:10px max(10px,env(safe-area-inset-right)) max(8px,env(safe-area-inset-bottom)) max(10px,env(safe-area-inset-left));box-shadow:none}.naval-community.mobile .ranking-row{min-height:32px}.naval-community.mobile .history-entry{padding:9px 3px}@media(max-height:450px){.naval-community.mobile .global-chat{display:grid;grid-template-columns:minmax(0,1fr) minmax(170px,36%);grid-template-rows:minmax(0,1fr) auto auto;gap:4px 10px}.global-chat .lobby-chat-log{grid-column:1;grid-row:1/4}.global-chat :deep(.naval-stickers){grid-column:2;grid-row:1;grid-template-columns:repeat(4,minmax(0,1fr));align-content:center}.global-chat :deep(.naval-stickers button){max-height:62px}.global-chat :deep(.naval-emoji){grid-column:2;grid-row:2}.global-chat form{grid-column:2;grid-row:3}.global-chat .share-lobby{position:absolute;right:48px;top:10px;padding:5px}.ranking-note{padding:2px 0}.community-heading{margin-bottom:4px}}
</style>
<style scoped>
.chat-tools{display:flex;align-items:center;gap:6px;flex-shrink:0}.chat-tools :deep(.naval-emoji){padding:0;gap:2px}.chat-tools :deep(.naval-emoji button){width:26px;height:28px;font-size:18px}@media(max-height:450px){.naval-community.mobile .global-chat{display:flex}.global-chat .lobby-chat-log{min-height:30px}.global-chat form{min-height:34px}.global-chat input{padding:6px 8px}.global-chat .share-lobby{position:static}.chat-tools{justify-content:flex-start}}
.lobby-chat-log{background:#f0f4f5;padding:10px 12px;border-radius:4px}
.lobby-chat-log article.lobby-message{padding:8px 0;white-space:normal}
.message-time{display:block;text-align:center;font-size:10px;line-height:18px;margin:5px 0 16px;color:#92a2a6}
.message-row{display:flex;align-items:flex-start;gap:9px;min-width:0}
.message-row :deep(.naval-avatar){width:32px;height:32px;margin-top:2px}
.message-body{display:flex;align-items:flex-start;flex-direction:column;min-width:0;max-width:calc(100% - 48px)}
.message-name{font-size:10px;line-height:16px;font-weight:400;color:#85999f;overflow-wrap:anywhere;margin:0 0 5px}
.message-bubble{max-width:100%}
.mine .message-row{flex-direction:row-reverse}
.mine .message-body{align-items:flex-end}
.lobby-message :deep(.message-bubble) .lobby-room-card{text-align:left;border:1px solid var(--color-primary-line);background:#fff8f1}
.lobby-message :deep(.message-bubble.decorated) .lobby-room-card{background:transparent;border-color:transparent}
.chat-compose{display:flex;flex-direction:column;gap:4px;flex-shrink:0;min-width:0}
.chat-compose .message-compose{min-width:0;margin:0}
.naval-community.mobile.naval-community{position:fixed;inset:auto;top:max(var(--community-top,52px),var(--navy-visual-top,0px));left:var(--navy-visual-left,0px);width:var(--navy-visual-width,100%);height:max(0px,calc(var(--navy-visual-bottom,100dvh) - max(var(--community-top,52px),var(--navy-visual-top,0px))));max-height:var(--navy-visual-height,100dvh);margin:0;border:0;border-radius:0;box-shadow:none}
.naval-community.mobile .global-chat{min-height:0;overflow:hidden}
.naval-community.mobile .global-chat>.lobby-chat-log{min-height:0;flex:1;overflow:auto}
.naval-community.mobile.keyboard-open{top:var(--chat-visible-top);left:var(--chat-visible-left);width:var(--chat-visible-width);height:var(--chat-visible-height);max-height:var(--chat-visible-height);padding-top:5px;padding-bottom:5px}
.naval-community.mobile.keyboard-open .community-heading{min-height:28px;height:28px;margin-bottom:4px}
.naval-community.mobile.keyboard-open .chat-tools,.naval-community.mobile.keyboard-open .share-lobby,.naval-community.mobile.keyboard-open .sticker-tray{display:none}
.naval-community.mobile.keyboard-open .global-chat{gap:5px}
.naval-community.mobile.keyboard-open .chat-compose{margin-top:auto}
.naval-community.mobile.keyboard-open .message-compose{min-height:38px}
@media(orientation:landscape) and (max-height:550px){
  .naval-community.mobile{padding-top:5px;padding-bottom:max(6px,env(safe-area-inset-bottom))}
  .naval-community.mobile .community-heading{min-height:28px;height:28px;margin-bottom:3px}
  .naval-community.mobile .community-heading h2{font-size:13px}
  .naval-community.mobile .community-heading .small-icon{width:30px;height:28px}
  .naval-community.mobile .chat-compose{flex-direction:row;align-items:center;gap:12px}
  .naval-community.mobile .chat-compose .message-compose{flex:1;min-height:36px}
  .naval-community.mobile .chat-tools{flex-shrink:0;gap:3px}
  .naval-community.mobile .chat-tools :deep(.naval-emoji button){width:25px;height:28px;font-size:17px}
  .naval-community.mobile .global-chat .lobby-chat-log{padding:6px 10px}
  .naval-community.mobile .message-row :deep(.naval-avatar){width:28px;height:28px}
  .naval-community.mobile.keyboard-open .chat-compose{display:block}
}
</style>
