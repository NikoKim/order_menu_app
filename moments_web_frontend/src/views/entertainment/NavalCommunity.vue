<template>
  <section class="naval-community" :class="{ mobile }" :aria-label="title">
    <header class="community-heading"><h2>{{ title }}<span v-if="detail || detailLoading"> / 战斗详情</span></h2><button class="small-icon" title="关闭面板" aria-label="关闭社区面板" @click="$emit('close')"><X :size="19" /></button></header>
    <div v-if="error" class="community-error" role="alert">{{ error }}<button class="small-icon" title="重试" aria-label="重试" @click="load"><RotateCcw :size="16" /></button></div>
    <NavalMedalWall v-if="tab === 'medals'" :person="medalPerson" />
    <section v-else-if="detail || detailLoading" class="history-detail community-body" aria-label="战斗详情">
      <button class="detail-back" @click="closeDetail"><ArrowLeft :size="16" />返回{{ tab === 'history' ? '战绩列表' : '排行榜' }}</button>
      <p v-if="detailLoading" class="community-empty">正在读取战斗记录...</p>
      <template v-else>
        <p class="detail-date">{{ ruleLabel(detail) }} · {{ detail.mode === 'AI' ? '人机对战' : '双人对战' }} · {{ date(detail.finishedAt) }}</p>
        <p v-if="detail.legacy" class="community-empty">这场旧战绩仅保存了胜负，新对局会保留道具和攻击记录。</p>
        <button v-if="!detail.legacy" class="replay-entry" :disabled="!detail.players?.every(person => person.ships?.length) || Boolean(room && room.phase !== 'FINISHED')" @click="$emit('replay', detail)"><Film :size="17" />对局回放</button>
        <button v-if="!detail.legacy && !detail.shared && (detail.you >= 0 || detail.local)" class="replay-entry" :disabled="sending || detail.lobbyShared" @click="shareResult"><Share2 :size="17" />{{ detail.lobbyShared ? '已分享到大厅' : '分享战绩到大厅' }}</button>
        <div class="detail-players"><section v-for="(person, index) in detail.players || []" :key="index"><h3><Trophy v-if="detail.winner === index" :size="17" />{{ person.name }}</h3><dl><dt>金币花费</dt><dd>{{ person.coinsSpent ?? '--' }}</dd><dt>攻击次数</dt><dd>{{ person.attacks ?? '--' }}</dd><dt>命中 / 攻击格数</dt><dd>{{ person.hits ?? '--' }} / {{ person.shots ?? '--' }}</dd></dl><template v-if="detail.ruleSet==='MILITARY'"><h4>采购舰队</h4><ul><li v-for="(count,kind) in fleetCounts(person)" :key="kind">{{ MILITARY_SHIPS[kind]?.name || kind }} × {{ count }}</li></ul></template><template v-for="group in detail.ruleSet==='MILITARY' ? [] : itemGroups" :key="group.key"><h4>{{ group.label }}</h4><ul><li v-for="(count, key) in nonzero(person[group.key])" :key="key"><img :src="`/entertainment/naval/v2/${key}.png`" alt="" />{{ ITEMS[key]?.name || key }} × {{ count }}</li><li v-if="!Object.keys(nonzero(person[group.key])).length" class="muted">无</li></ul></template></section></div>
        <details v-if="detail.events?.length"><summary>战斗行动记录 · {{ detail.events.length }}</summary><ol><li v-for="event in detail.events" :key="event.id"><strong>{{ detail.players?.[event.player]?.name }}</strong> · {{ ITEMS[event.weapon]?.name || (event.weapon === 'shot' ? '舰炮' : '战局') }}<span>{{ event.text }}</span><small v-if="event.cells?.length">{{ event.cells.map(cell => `${String.fromCharCode(65 + cell.x)}${cell.y + 1}`).join('、') }}</small></li></ol></details>
      </template>
    </section>
    <NavalRooms v-else-if="tab === 'rooms'" :current-room="room" :busy="sending" @enter="$emit('join', $event)" />
    <div v-else-if="tab === 'reviews'" class="community-body"><GameReviewPanel naval /></div>
    <template v-else-if="tab === 'ranking'">
      <nav class="ranking-subtabs" role="tablist" aria-label="排行榜对战类型"><button v-for="item in modes" :key="item.value" role="tab" :aria-selected="mode === item.value" :class="{ active: mode === item.value }" @click="mode = item.value">{{ item.label }}</button></nav>
      <nav class="ranking-subtabs metrics-tabs" role="tablist" aria-label="排行榜指标"><button v-for="item in metrics" :key="item.value" role="tab" :aria-selected="metric === item.value" :class="{ active: metric === item.value }" @click="metric = item.value">{{ item.label }}</button></nav>
      <p class="ranking-note">注册玩家榜 · {{ metric === 'economy' ? '每位玩家最佳获胜记录 · 对手花费 − 自己花费' : metric === 'rate' ? '按胜率排序，展示对局样本数' : '按累计获胜场数排序' }}</p>
      <div class="community-body ranking-table"><p v-if="loading" class="community-empty" role="status">正在读取...</p><template v-else><div class="ranking-row table-heading"><span>排名</span><span>指挥官</span><span>{{ metric === 'economy' ? '节省金币' : metric === 'rate' ? '胜率' : '胜场' }}</span><span>{{ metric === 'economy' ? '战绩' : '对局' }}</span></div><div v-for="(row, index) in pageRanking" :key="`${row.name}-${row.gameId || index}`" class="ranking-row"><span>{{ page * pageSize + index + 1 }}</span><div class="ranking-person"><NavalAvatar :person="{ ...row, registered: true }" :size="22" /><strong>{{ row.name }}</strong></div><strong>{{ metric === 'economy' ? `${row.difference > 0 ? '+' : ''}${row.difference}` : metric === 'rate' ? `${Number(row.rate).toFixed(1)}%` : row.wins }}</strong><button v-if="row.gameId" class="detail-link" @click="openDetail(row)">查看</button><span v-else>{{ row.games }} 场</span></div><p v-if="!ranking.length" class="community-empty">暂无上榜战绩</p></template></div>
      <nav v-if="ranking.length > pageSize" class="pagination" aria-label="排行榜分页"><button :disabled="!page" aria-label="上一页" @click="page--"><ChevronLeft :size="18" /></button><span>{{ page + 1 }} / {{ Math.ceil(ranking.length / pageSize) }}</span><button :disabled="(page + 1) * pageSize >= ranking.length" aria-label="下一页" @click="page++"><ChevronRight :size="18" /></button></nav>
    </template>
    <template v-else-if="tab === 'history'"><div class="community-body"><p v-if="!history.length" class="community-empty">暂无战斗记录</p><button v-for="item in pageHistory" :key="item.gameId || item.id" class="history-entry" @click="openDetail(item)"><strong :class="item.won ? 'won' : 'lost'">{{ item.won ? '胜利' : '战败' }}</strong><span>{{ ruleLabel(item) }} · {{ item.mode === 'AI' ? '人机对战' : '双人对战' }}<small v-if="item.opponent">{{ item.opponent }}</small></span><time>{{ date(item.at || item.finishedAt) }}</time><ChevronRight :size="16" /></button></div><nav v-if="history.length > pageSize" class="pagination" aria-label="战绩分页"><button :disabled="!page" aria-label="上一页" @click="page--"><ChevronLeft :size="18" /></button><span>{{ page + 1 }} / {{ Math.ceil(history.length / pageSize) }}</span><button :disabled="(page + 1) * pageSize >= history.length" aria-label="下一页" @click="page++"><ChevronRight :size="18" /></button></nav></template>
    <section v-else class="global-chat community-body">
      <div ref="chatLog" class="lobby-chat-log" aria-live="polite">
        <p v-if="!messages.length" class="community-empty">还没有消息</p>
        <article v-for="message in messages" :key="message.id"><header><strong>{{ message.name }}</strong><time>{{ date(message.at) }}</time></header>
          <button v-if="message.room" class="lobby-room-card" :class="{ inactive: !joinable(message.room) }" :disabled="!joinable(message.room) || Boolean(room)" @click="selectRoom(message.room)"><Anchor :size="24" /><span><strong>{{ ruleLabel(message.room) }}邀请 · {{ message.room.names?.join(' / ') || message.name }}</strong><small>{{ roomLabel(message.room) }}</small></span><Eye v-if="message.room.status === 'STARTED'" :size="20" /><ChevronRight v-else :size="20" /></button>
          <button v-else-if="message.battle" class="lobby-room-card battle-result-card" aria-label="查看分享战绩" @click="openDetail({ detail: message.battle })"><Trophy :size="24" /><span><strong>{{ message.battle.you === message.battle.winner ? '胜利战报' : '对战战报' }} · {{ message.battle.players?.map(person=>person.name).join(' / ') }}</strong><small>{{ ruleLabel(message.battle) }} · {{ message.battle.local ? '本地人机战绩 · 不计入排行榜' : message.battle.mode === 'AI' ? '人机对战' : '人人对战' }} · {{ message.battle.players?.[message.battle.winner]?.name }} 获胜</small><small>查看战绩与回放</small></span><Film :size="20" /></button>
          <NavalMessage v-else :text="message.stickerId ? stickerMessage(message.stickerId) : message.text" />
        </article>
      </div>
      <form v-if="invitedRoom" class="invite-room-form" @submit.prevent="$emit('join', { ...invitedRoom, inviteCode: inviteText }); invitedRoom = null"><input v-model="inviteText" aria-label="分享房间邀请码" placeholder="输入此房间的 6 位邀请码" maxlength="6" /><button class="send-button" :disabled="inviteText.trim().length !== 6" aria-label="确认邀请码"><Check :size="18" /></button><button type="button" class="small-icon" aria-label="取消加入" @click="invitedRoom = null"><X :size="17" /></button></form>
      <button v-if="room?.phase === 'WAITING'" class="share-lobby" :disabled="sending || room.lobbyShared" @click="$emit('share')"><Share2 :size="15" />{{ room.lobbyShared ? '本局已分享到大厅' : '分享当前房间到大厅' }}</button>
      <div class="chat-tools"><NavalStickers :disabled="sending" :panel-target="stickerTray" @send="sendSticker" /><NavalEmoji @select="appendEmoji" /></div><form @submit.prevent="send"><input v-model="text" aria-label="大厅消息" placeholder="和指挥官们聊聊" maxlength="300" /><button :disabled="sending || !text.trim()" class="send-button" aria-label="发送大厅消息" title="发送"><Send :size="18" /></button></form><div ref="stickerTray" class="sticker-tray" />
    </section>
  </section>
</template>
<script setup>
import { computed, nextTick, onUnmounted, ref, watch } from 'vue'
import { MILITARY_SHIPS } from '../../games/navalMilitary'
import { Anchor, ArrowLeft, Check, ChevronLeft, ChevronRight, Eye, Film, RotateCcw, Send, Share2, Trophy, X } from 'lucide-vue-next'
import GameReviewPanel from './GameReviewPanel.vue'
import NavalAvatar from './NavalAvatar.vue'
import NavalEmoji from './NavalEmoji.vue'
import NavalStickers from './NavalStickers.vue'
import NavalMessage from './NavalMessage.vue'
import NavalRooms from './NavalRooms.vue'
import NavalMedalWall from './NavalMedalWall.vue'
import { stickerMessage } from '../../games/navalStickers'
import { ITEMS } from '../../games/navalEngine'
import { getNavalHistory, getNavalLeaderboard, getNavalLobbyMessages, sendNavalLobbyMessage } from '../../api/naval'
const props = defineProps({ ruleSet: { type: String, default: 'CLASSIC' }, panel: String, medalPerson: Object, mobile: Boolean, room: Object, history: { type: Array, default: () => [] } })
const emit = defineEmits(['close', 'join', 'share', 'replay', 'messages'])
const tab = computed(() => props.panel)
const title = computed(() => props.panel === 'medals' ? `${props.medalPerson?.name || '我的'}勋章墙` : ({ rooms: '游戏房间', ranking: `${props.ruleSet === 'MILITARY' ? '军事模式' : '经典模式'}排行榜`, history: '历史战绩', chat: '聊天大厅', reviews: '玩家点评' })[props.panel])
const fleetCounts = person => (person.ships || []).reduce((out,ship) => {out[ship.kind]=(out[ship.kind]||0)+1;return out}, {})
const ruleLabel = value => (value?.ruleSet || value?.detail?.ruleSet) === 'MILITARY' ? '军事模式' : '经典模式'
const modes = [{ value: 'AI', label: '人机对战' }, { value: 'MULTIPLAYER', label: '人人对战' }]
const metrics = [{ value: 'wins', label: '获胜场数' }, { value: 'rate', label: '胜率' }, { value: 'economy', label: '金币使用' }]
const itemGroups = [{ key: 'itemsPurchased', label: '购买道具' }, { key: 'itemsUsed', label: '道具消耗' }, { key: 'inventoryRemaining', label: '剩余道具' }]
const roomStatus = { WAITING: '等待加入 · 点击参加', STARTED: '此房间游戏已开始 · 点击观战', CLOSED: '此房间已关闭', FINISHED: '此房间已结束' }
const joinable = card => card.status === 'WAITING' || card.status === 'STARTED' && card.allowSpectators !== false
const roomLabel = card => `${card.status === 'STARTED' && card.allowSpectators === false ? '游戏已开始 · 无法观战' : roomStatus[card.status]}${card.requiresInvite ? ' · 需要邀请码' : ''}`
const invitedRoom = ref(null), inviteText = ref(''), stickerTray = ref(null)
function selectRoom(card) { if (card.requiresInvite) { invitedRoom.value = card; inviteText.value = '' } else emit('join', card) }
const mode = ref('AI'), metric = ref('wins'), ranking = ref([]), loading = ref(false), error = ref(''), page = ref(0)
const pageSize = computed(() => props.mobile ? 5 : 8)
const pageRanking = computed(() => ranking.value.slice(page.value * pageSize.value, (page.value + 1) * pageSize.value))
const pageHistory = computed(() => props.history.slice(page.value * pageSize.value, (page.value + 1) * pageSize.value))
const messages = ref([]), text = ref(''), sending = ref(false), chatLog = ref(null), detail = ref(null), detailLoading = ref(false)
let generation = 0, detailGeneration = 0, disposed = false
const date = value => value ? new Date(value).toLocaleString('zh-CN', { month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' }) : ''
const nonzero = value => Object.fromEntries(Object.entries(value || {}).filter(([, count]) => count > 0))
async function load(silent = false) {
  const request = ++generation
  if (!silent) { loading.value = true; error.value = '' }
  try {
    if (tab.value === 'ranking') { const result = await getNavalLeaderboard(mode.value, metric.value, props.ruleSet); if (!disposed && request === generation) ranking.value = result }
    if (tab.value === 'chat') { const result = await getNavalLobbyMessages(); if (!disposed && request === generation) { const atBottom = !chatLog.value || chatLog.value.scrollHeight - chatLog.value.scrollTop - chatLog.value.clientHeight < 60; messages.value = result; emit('messages', result); if (atBottom) { await nextTick(); chatLog.value?.scrollTo(0, chatLog.value.scrollHeight) } } }
  } catch (e) { if (!disposed && request === generation) error.value = e.message || '读取失败' }
  finally { if (!disposed && request === generation) loading.value = false }
}
function appendEmoji(emoji) { text.value = (text.value + emoji).slice(0, 300) }
async function post(value) { if (sending.value) return; sending.value = true; try { const result = await sendNavalLobbyMessage(value); if (disposed) return; messages.value = result; emit('messages', result); error.value = ''; await nextTick(); chatLog.value?.scrollTo(0, chatLog.value.scrollHeight); return true } catch (e) { if (!disposed) error.value = e.message } finally { sending.value = false } }
async function send() { if (text.value.trim() && await post(text.value.trim())) text.value = '' }
const sendSticker = id => post({ stickerId: id })
function closeDetail() { detailGeneration++; detail.value = null; detailLoading.value = false }
async function openDetail(item) { const request = ++detailGeneration; detailLoading.value = true; error.value = ''; try { const result = item.detail || ((item.local || !item.gameId) ? { legacy: true, mode: item.mode, ruleSet: item.ruleSet, finishedAt: item.at } : await getNavalHistory(item.gameId || item.id)); if (request === detailGeneration && !disposed) detail.value = { ...result, ...(item.local ? { local: true, you: 0 } : {}) } } catch (e) { error.value = e.message } finally { if (request === detailGeneration) detailLoading.value = false } }
async function shareResult() {
  const result=detail.value
  if (!result || result.shared || result.lobbyShared) return
  if (await post(result.local ? { localResult: result } : { gameId: result.gameId })) result.lobbyShared=true
}
watch([tab, mode, metric, () => props.ruleSet], () => { page.value = 0; closeDetail(); load() }, { immediate: true })
const poll = setInterval(() => { if (tab.value === 'chat' && !document.hidden && !loading.value && !sending.value) load(true) }, 2500)
onUnmounted(() => { disposed = true; generation++; detailGeneration++; clearInterval(poll) })
</script>
<style scoped>
.sticker-tray{min-height:0}.sticker-tray:not(:empty){height:min(220px,38dvh);min-height:70px;flex:0 1 auto;overflow:auto;overscroll-behavior:contain}.sticker-tray:empty{display:none}.global-chat{gap:8px}.lobby-chat-log article{padding-bottom:12px}.lobby-chat-log header{margin-bottom:8px}.invite-room-form{align-items:center}.invite-room-form .small-icon{flex-shrink:0}
@media(max-height:450px){.global-chat:has(.sticker-picker) .lobby-chat-log,.global-chat:has(.sticker-picker) .chat-tools,.global-chat:has(.sticker-picker) .share-lobby{display:none}.global-chat:has(.sticker-picker){gap:6px}.sticker-tray:not(:empty){height:145px;min-height:110px;flex:1}}
</style>
<style scoped>
.replay-entry{display:flex;align-items:center;gap:6px;background:#e1eee6;color:#397b5f;padding:8px 12px;border-radius:4px;font-size:12px;margin-top:12px}.replay-entry:disabled{opacity:.4}
.naval-community{position:relative;align-self:stretch;display:flex;flex-direction:column;width:100%;height:100%;min-height:0;min-width:0;padding:12px;background:#f9fcfb;box-sizing:border-box;border-radius:6px;color:#456a77;overflow:hidden;z-index:450;box-shadow:0 3px 20px #294a4412}.community-heading{display:flex;align-items:center;justify-content:space-between;gap:12px;flex-shrink:0;min-height:32px;margin-bottom:8px}.community-heading h2{font-size:15px}.community-heading h2 span{font-size:12px;font-weight:400}.community-body{min-height:0;flex:1;overflow:auto;overscroll-behavior:contain}.ranking-subtabs{display:flex;flex-shrink:0;gap:4px;margin-bottom:6px;background:#e8efec;border-radius:4px;padding:3px}.ranking-subtabs button{flex:1;min-width:0;height:30px;padding:0 6px;border-radius:3px;background:transparent;font-size:12px;color:#789089}.ranking-subtabs button.active{background:white;color:#307158}.metrics-tabs{background:transparent;padding:0}.metrics-tabs button.active{background:#e2efe8}.ranking-note,.community-empty{font-size:11px;color:#80949b;padding:8px 0;line-height:1.6}.community-error{display:flex;align-items:center;gap:10px;color:#b45547;font-size:12px;flex-shrink:0}.ranking-row{display:grid;grid-template-columns:30px minmax(0,1fr) 68px 46px;gap:6px;align-items:center;min-height:42px;font-size:12px}.ranking-row:nth-child(even){background:#edf3f3}.table-heading{font-size:11px;color:#82959a;min-height:26px}.ranking-row>span:first-child{text-align:center}.ranking-person{display:flex;gap:8px;align-items:center;min-width:0}.ranking-person strong{overflow-wrap:anywhere}.detail-link{padding:6px;background:transparent;color:#b57e3d;text-align:left}.history-entry{display:flex;align-items:center;width:100%;gap:12px;background:transparent;padding:12px 3px;color:#67838d;text-align:left;font-size:12px}.history-entry:hover{background:#edf3f3}.history-entry small{display:block;font-size:11px;margin-top:3px}.history-entry time{margin-left:auto;font-size:11px}.won{color:#42876c}.lost{color:#bb786b}.pagination{display:flex;align-items:center;justify-content:center;gap:15px;flex-shrink:0;padding-top:8px;font-size:11px}.pagination button,.small-icon{width:32px;height:32px;display:grid;place-items:center;background:transparent;color:inherit;flex-shrink:0}.pagination button:disabled{opacity:.3}.global-chat{display:flex;flex-direction:column;overflow:hidden;gap:4px}.lobby-chat-log{flex:1;min-height:30px;overflow:auto;background:#edf4f3;padding:6px 10px;border-radius:4px;overscroll-behavior:contain}.lobby-chat-log article{padding:6px 0;font-size:13px;line-height:1.6;white-space:pre-wrap;overflow-wrap:anywhere}.lobby-chat-log header{display:flex;align-items:center;justify-content:space-between;gap:12px;font-size:11px;margin-bottom:3px}.lobby-chat-log time,.muted{color:#91a2a7}.global-chat form{display:flex;gap:8px;flex-shrink:0}.global-chat input{flex:1;min-width:0;padding:9px;border:1px solid #d4e3df;border-radius:4px;background:#fff;font:inherit;font-size:13px}.send-button{display:grid;place-items:center;width:38px;background:#3e8166;color:#fff;border-radius:4px}.send-button:disabled{opacity:.4}.history-detail{font-size:12px}.detail-back{display:flex;align-items:center;gap:6px;background:transparent;color:#4d856e;padding:4px 0 10px}.detail-date{color:#80949b}.detail-players{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:18px;margin-top:14px}.detail-players h3{font-size:13px;display:flex;align-items:center;gap:6px;overflow-wrap:anywhere}.detail-players h3 svg{color:#c1934a;flex-shrink:0}.detail-players h4{font-size:11px;margin:12px 0 6px}.detail-players dl{display:grid;grid-template-columns:1fr auto;gap:6px;font-size:11px;margin-top:10px}.detail-players dd{margin:0}.detail-players ul{list-style:none;padding:0;display:flex;flex-wrap:wrap;gap:6px;font-size:11px}.detail-players li{display:flex;align-items:center;gap:3px}.detail-players img{width:22px;height:22px;object-fit:contain;image-rendering:pixelated}.history-detail details{margin-top:20px}.history-detail summary{cursor:pointer}.history-detail ol{padding-left:20px}.history-detail ol li{padding:8px 0}.history-detail ol span,.history-detail ol small{display:block;line-height:1.6;overflow-wrap:anywhere}.history-detail ol small{color:#90a5aa}.lobby-room-card{display:flex;align-items:center;gap:10px;width:100%;padding:10px;background:#dcece3;border-radius:5px;color:#427461;text-align:left}.lobby-room-card>span{flex:1;min-width:0}.lobby-room-card strong{font-size:12px;display:block;overflow-wrap:anywhere}.lobby-room-card small{display:block;margin-top:4px;font-size:11px}.lobby-room-card>svg{flex-shrink:0}.lobby-room-card.inactive{filter:grayscale(1);opacity:.55}.share-lobby{display:flex;justify-content:center;align-items:center;gap:6px;background:#e8f0eb;color:#478269;font-size:11px;padding:5px;border-radius:3px;flex-shrink:0}.naval-community :deep(.game-reviews){border:0;margin:0;padding:0}
.naval-community.mobile{position:fixed;inset:var(--community-top,52px) 0 0;width:100%;height:calc(100dvh - var(--community-top,52px));max-height:none;border-radius:0;z-index:450;padding:10px max(10px,env(safe-area-inset-right)) max(8px,env(safe-area-inset-bottom)) max(10px,env(safe-area-inset-left));box-shadow:none}.naval-community.mobile .ranking-row{min-height:32px}.naval-community.mobile .history-entry{padding:9px 3px}@media(max-height:450px){.naval-community.mobile .global-chat{display:grid;grid-template-columns:minmax(0,1fr) minmax(170px,36%);grid-template-rows:minmax(0,1fr) auto auto;gap:4px 10px}.global-chat .lobby-chat-log{grid-column:1;grid-row:1/4}.global-chat :deep(.naval-stickers){grid-column:2;grid-row:1;grid-template-columns:repeat(4,minmax(0,1fr));align-content:center}.global-chat :deep(.naval-stickers button){max-height:62px}.global-chat :deep(.naval-emoji){grid-column:2;grid-row:2}.global-chat form{grid-column:2;grid-row:3}.global-chat .share-lobby{position:absolute;right:48px;top:10px;padding:5px}.ranking-note{padding:2px 0}.community-heading{margin-bottom:4px}}
</style>
<style scoped>
.chat-tools{display:flex;align-items:center;gap:6px;flex-shrink:0}.chat-tools :deep(.naval-emoji){padding:0;gap:2px}.chat-tools :deep(.naval-emoji button){width:26px;height:28px;font-size:18px}@media(max-height:450px){.naval-community.mobile .global-chat{display:flex}.global-chat .lobby-chat-log{min-height:30px}.global-chat form{min-height:34px}.global-chat input{padding:6px 8px}.global-chat .share-lobby{position:static}.chat-tools{justify-content:flex-start}}
</style>
