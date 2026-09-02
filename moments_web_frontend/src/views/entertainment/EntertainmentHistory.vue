<template>
  <div class="history-page">
    <header><div><h1>游玩记录</h1><p>进行中、已暂停与已完成场次</p></div><router-link class="btn btn-outline" to="/customer/entertainment"><Gamepad2 :size="17" />娱乐广场</router-link></header>
    <LoadingSpinner v-if="loading" />
    <EmptyState v-else-if="error" icon="warning" :title="error" action-text="重试" @action="load" />
    <div v-else-if="sessions.length" class="history-list">
      <article v-for="session in sessions" :key="session.id">
        <div class="round-mark"><span>{{ session.currentRound }}/{{ session.config.roundCount }}</span><small>回合</small></div>
        <div class="history-copy"><div><h2>{{ session.game.title }}</h2><span :data-status="session.status">{{ statusName(session.status) }}</span></div><p>{{ session.game.stockCode }} · v{{ session.gameVersionNo }} · {{ formatTime(session.startedAt) }}</p></div>
        <div class="result" v-if="session.status === 'COMPLETED'"><strong :class="session.returnPercent >= 0 ? 'up' : 'down'">{{ signed(session.returnPercent) }}%</strong><span>收益率</span></div>
        <div class="record-actions"><router-link class="btn btn-sm" :class="session.status === 'COMPLETED' ? 'btn-outline' : 'btn-primary'" :to="`/customer/entertainment/sessions/${session.id}/play`">{{ session.status === 'COMPLETED' ? '查看结算' : session.status === 'PAUSED' ? '恢复游戏' : '继续游戏' }}</router-link><button class="delete-record" title="删除游玩记录" @click="deletingSession = session"><Trash2 :size="16" /></button></div>
      </article>
    </div>
    <EmptyState v-else title="暂无游玩记录" action-text="去娱乐广场" @action="$router.push('/customer/entertainment')" />
    <Transition name="confirm-modal"><div v-if="deletingSession" class="confirm-mask" @click.self="deletingSession = null"><div class="confirm-dialog"><Trash2 :size="28" /><h2>删除这条游玩记录？</h2><p>“{{ deletingSession.game.title }}”的本地历史入口将被移除。已完成的公共排行榜成绩不会被删除，此操作无法撤销。</p><button class="danger-command" :disabled="deleting" @click="confirmDelete">确认删除</button><button class="cancel-command" :disabled="deleting" @click="deletingSession = null">取消</button></div></div></Transition>
  </div>
</template>
<script setup>
import { onMounted, ref } from 'vue'
import { Gamepad2, Trash2 } from 'lucide-vue-next'
import { deleteGameSessionRecord, ensureEntertainmentGuest, getGameHistory } from '../../api/entertainment'
import LoadingSpinner from '../../components/LoadingSpinner.vue'; import EmptyState from '../../components/EmptyState.vue'
const sessions=ref([]);const loading=ref(true);const error=ref('');const deletingSession=ref(null);const deleting=ref(false)
const statusName=v=>({ACTIVE:'进行中',PAUSED:'已暂停',COMPLETED:'已完成',ABANDONED:'已结束'}[v]||v)
const signed=v=>`${Number(v)>=0?'+':''}${Number(v||0).toFixed(2)}`
const formatTime=v=>v?new Date(v).toLocaleString('zh-CN',{month:'numeric',day:'numeric',hour:'2-digit',minute:'2-digit'}):''
async function load(){loading.value=true;error.value='';try{await ensureEntertainmentGuest();const data=await getGameHistory({page:1,pageSize:20});sessions.value=data.records||[]}catch(e){error.value=e.message}finally{loading.value=false}}
async function confirmDelete(){deleting.value=true;error.value='';try{await deleteGameSessionRecord(deletingSession.value.id);deletingSession.value=null;await load()}catch(e){error.value=e.message}finally{deleting.value=false}}
onMounted(load)
</script>
<style scoped>
.history-page{max-width:940px;margin:0 auto;padding:28px 24px 60px}.history-page>header{display:flex;align-items:flex-end;justify-content:space-between;margin-bottom:20px}.history-page h1{font-size:25px}.history-page header p{margin-top:4px;color:var(--color-text-muted);font-size:13px}.history-list{border:1px solid var(--color-line);border-radius:8px;overflow:hidden;background:#fff}.history-list article{min-height:86px;display:grid;grid-template-columns:62px minmax(0,1fr) 100px auto;align-items:center;gap:15px;padding:12px 15px;border-bottom:1px solid var(--color-line)}.history-list article:last-child{border:0}.round-mark{height:56px;display:flex;flex-direction:column;align-items:center;justify-content:center;border-radius:7px;color:#5e4e3d;background:#f3eee8}.round-mark span{font-weight:800}.round-mark small{font-size:10px}.history-copy>div{display:flex;align-items:center;gap:8px}.history-copy h2{font-size:15px}.history-copy span{padding:2px 6px;border-radius:4px;background:#f0f2f4;color:#65707a;font-size:10px}.history-copy span[data-status=ACTIVE],.history-copy span[data-status=PAUSED]{color:#a05716;background:#fff1df}.history-copy p{margin-top:5px;color:var(--color-text-muted);font-size:11px}.result{display:flex;flex-direction:column}.result strong{font-size:16px}.result span{color:var(--color-text-muted);font-size:10px}.up{color:#20794d}.down{color:#bd4e47}.record-actions{display:flex;align-items:center;gap:5px}.delete-record{width:34px;height:34px;display:grid;place-items:center;border:1px solid #ead0cd;border-radius:6px;color:#aa4942;background:#fff}.confirm-mask{position:fixed;z-index:1200;inset:0;display:grid;place-items:center;padding:18px;background:rgba(28,38,32,.6)}.confirm-dialog{width:min(390px,100%);display:flex;flex-direction:column;align-items:center;padding:24px;border-radius:8px;background:#fff;text-align:center;box-shadow:0 24px 65px rgba(20,29,24,.32)}.confirm-dialog>svg{color:#ad443e}.confirm-dialog h2{margin-top:10px;font-size:18px}.confirm-dialog p{margin:8px 0 14px;color:#68736d;font-size:11px;line-height:1.7}.danger-command,.cancel-command{width:100%;min-height:40px;border-radius:6px}.danger-command{color:#fff;background:#ae433d;font-weight:700}.cancel-command{margin-top:6px;color:#68736d;background:transparent}.confirm-modal-enter-active,.confirm-modal-leave-active{transition:opacity .23s}.confirm-modal-enter-active .confirm-dialog,.confirm-modal-leave-active .confirm-dialog{transition:transform .27s,opacity .22s}.confirm-modal-enter-from,.confirm-modal-leave-to{opacity:0}.confirm-modal-enter-from .confirm-dialog{opacity:0;transform:translateY(16px) scale(.98)}.confirm-modal-leave-to .confirm-dialog{opacity:0;transform:translateY(8px) scale(.985)}@media(max-width:768px){.history-page{padding:16px 12px 90px}.history-list article{grid-template-columns:54px 1fr auto}.result{grid-column:2}.record-actions{grid-column:3;grid-row:1/3;flex-direction:column}}
</style>
