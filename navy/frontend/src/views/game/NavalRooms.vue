<template>
  <section class="room-directory" aria-label="正在游玩的房间">
    <nav class="room-filters" aria-label="房间类型"><button v-for="item in filters" :key="item.id" :class="{ active: filter === item.id }" @click="filter = item.id">{{ item.label }}</button><button class="refresh-rooms" aria-label="刷新房间" title="刷新房间" :disabled="loading" @click="load"><RefreshCw :size="16" :class="{ spinning: loading }" /></button></nav>
    <p v-if="error" class="room-error" role="alert">{{ error }}</p>
    <div class="room-results" :aria-busy="loading || filtering"><AppSkeleton v-if="loading || filtering" :rows="4" label="正在加载房间" /><template v-else><p v-if="!filtered.length" class="room-empty">当前没有{{ filter === 'AI' ? '人机' : filter === 'MULTIPLAYER' ? '人人' : '' }}对战房间</p>
      <article v-for="item in filtered" :key="item.id" class="directory-room"><header><span><Bot v-if="item.mode === 'AI'" :size="17" /><Users v-else :size="17" />{{ item.ruleSet === 'MILITARY' ? '军事模式' : '经典模式' }} · {{ item.mode === 'AI' ? '人机对战' : '人人对战' }}</span><small :class="{ live: item.phase === 'BATTLE' }">{{ phases[item.phase] }}</small></header>
        <div class="room-commanders"><span v-for="(person, index) in item.players" :key="index"><NavalAvatar :person="person" :size="24" /><strong>{{ person.name }}</strong></span></div>
        <div v-if="item.wager > 0 || item.wagerPot > 0" class="room-wager"><Coins :size="14" /><strong>房主 {{ item.wager }} 金币</strong><small v-if="item.wagerPot > 0">已投入 {{ item.wagerPot }} 金币</small></div>
        <footer><span v-if="item.requiresInvite" class="invite-badge"><LockKeyhole :size="13" />需要邀请码</span><span v-else class="public-badge">公开房间</span><span class="room-viewers"><Eye :size="13" />{{ item.spectators }}</span></footer>
        <form class="room-enter" @submit.prevent="enter(item)"><input v-if="item.requiresInvite && canEnter(item)" v-model="codes[item.id]" maxlength="6" placeholder="输入 6 位邀请码" :aria-label="`${item.players[0]?.name}的房间邀请码`" autocomplete="off" /><button :disabled="busy || Boolean(currentRoom) || !canEnter(item) || item.requiresInvite && codes[item.id]?.trim().length !== 6"><LogIn v-if="item.phase === 'WAITING'" :size="15" /><Eye v-else :size="15" />{{ item.phase === 'WAITING' ? '加入房间' : item.allowSpectators ? '观战' : '已关闭观战' }}</button></form>
      </article>
      </template>
    </div>
  </section>
</template>
<script setup>
import { computed, onUnmounted, ref, watch } from 'vue'
import { Bot, Coins, Eye, LockKeyhole, LogIn, RefreshCw, Users } from 'lucide-vue-next'
import { subscribeDirectory, refreshStreams } from '../../services/roomSync'
import NavalAvatar from './NavalAvatar.vue'
import AppSkeleton from '../../components/AppSkeleton.vue'
defineProps({ currentRoom: Object, busy: Boolean })
const emit = defineEmits(['enter'])
const rooms = ref([]), codes = ref({}), filter = ref('ALL'), loading = ref(false), error = ref('')
const filtering=ref(false)
let filterFrame,filterEndFrame
watch(filter,()=>{cancelAnimationFrame(filterFrame);cancelAnimationFrame(filterEndFrame);filtering.value=true;filterFrame=requestAnimationFrame(()=>{filterEndFrame=requestAnimationFrame(()=>{filtering.value=false})})},{flush:'sync'})
const filters = [{ id: 'ALL', label: '全部' }, { id: 'AI', label: '人机对战' }, { id: 'MULTIPLAYER', label: '人人对战' }]
const phases = { WAITING: '等待加入', PLACEMENT: '舰队布局', SUPPLY: '战备补给', BATTLE: '交战中' }
const filtered = computed(() => rooms.value.filter(r => filter.value === 'ALL' || r.mode === filter.value))
const canEnter = room => room.phase === 'WAITING' || room.allowSpectators
function enter(room) { emit('enter', { roomId: room.id, requiresInvite: room.requiresInvite, inviteCode: codes.value[room.id]?.trim() || '', phase: room.phase, wager: Number(room.wager || 0), wagerPot: Number(room.wagerPot || 0) }) }
function load() { loading.value=true; refreshStreams() }
loading.value=true
const stopDirectory=subscribeDirectory(value=>{rooms.value=value;loading.value=false;error.value=''})
onUnmounted(()=>{stopDirectory();cancelAnimationFrame(filterFrame);cancelAnimationFrame(filterEndFrame)})
</script>
<style scoped>
.room-directory{display:flex;flex-direction:column;min-height:0;flex:1;gap:12px}.room-filters{display:flex;align-items:center;gap:4px;flex-shrink:0}.room-filters button{height:30px;padding:0 10px;border-radius:4px;background:transparent;color:#748d93;font-size:12px}.room-filters button.active{background:var(--color-primary-bg);color:var(--color-primary-strong)}.room-filters .refresh-rooms{display:grid;place-items:center;margin-left:auto;width:30px;padding:0}.room-results{overflow:auto;min-height:0;flex:1;overscroll-behavior:contain}.directory-room{padding:12px 0;border-bottom:1px solid #e5ece8}.directory-room:first-child{padding-top:0}.directory-room header,.directory-room footer{display:flex;align-items:center;gap:8px;font-size:12px}.directory-room header>span,.room-commanders>span,.invite-badge,.room-viewers{display:flex;align-items:center;gap:5px}.directory-room header small{margin-left:auto;font-size:11px;color:#82979a}.directory-room header small.live{color:#c97f36}.room-commanders{display:flex;flex-wrap:wrap;gap:8px 14px;padding:12px 0}.room-commanders strong{font-size:12px;overflow-wrap:anywhere}.invite-badge{color:#af7c3f;font-size:11px}.public-badge{color:#7b9199;font-size:11px}.room-viewers{margin-left:auto;color:#91a2a3}.room-enter{display:flex;gap:8px;margin-top:10px}.room-enter input{min-width:0;flex:1;width:0;height:32px;border:1px solid #d7e4de;background:#fff;border-radius:4px;padding:0 8px;font:inherit;font-size:12px}.room-enter button{display:flex;align-items:center;justify-content:center;gap:6px;height:32px;min-width:82px;padding:0 10px;border-radius:4px;background:var(--color-primary-bg);color:var(--color-primary-strong);margin-left:auto;font-size:12px}.room-enter button:disabled{opacity:.5;cursor:default}.room-empty,.room-error{font-size:12px;padding:12px 0;line-height:1.6;color:#91a2a3}.room-error{color:#bb7265}.spinning{animation:room-spin 1s linear infinite}@keyframes room-spin{to{transform:rotate(360deg)}}
</style>
<style scoped>
.room-wager{display:flex;align-items:center;gap:6px 10px;flex-wrap:wrap;padding:0 0 10px;color:#ae7c3e;font-size:11px}.room-wager>svg{flex-shrink:0}.room-wager strong{font-size:11px;font-weight:500}.room-wager small{font-size:10px;color:#8a9f9c;margin-left:auto}
</style>
