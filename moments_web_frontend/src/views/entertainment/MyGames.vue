<template>
  <div class="manage-page">
    <header><div><h1>我的股票游戏</h1><p>草稿、已发布版本与下架状态</p></div><router-link class="btn btn-primary" to="/customer/entertainment/create"><Plus :size="17" />创建股票</router-link></header>
    <LoadingSpinner v-if="loading" />
    <EmptyState v-else-if="error" icon="warning" :title="error" action-text="重试" @action="load" />
    <div v-else-if="games.length" class="manage-list">
      <article v-for="game in games" :key="game.id">
        <div class="ticker">{{ game.stockCode }}</div>
        <div class="game-info"><div><h2>{{ game.title }}</h2><span class="status" :data-status="game.status">{{ statusName(game.status) }}</span></div><p>{{ game.stockType }} · {{ game.roundCount }} 回合 · {{ game.aiEnabled ? `${game.aiCount} 个 AI` : '无 AI' }}</p></div>
        <div class="stats"><span><strong>{{ game.playCount }}</strong>游玩</span><span><strong>{{ game.completedCount }}</strong>完成</span><span><strong>{{ game.versionLabel }}</strong>版本</span></div>
        <div class="commands">
          <router-link class="icon-btn" :to="`/customer/entertainment/games/${game.id}`" title="查看"><Eye :size="18" /></router-link>
          <router-link class="icon-btn" :to="`/customer/entertainment/games/${game.id}/edit`" title="编辑"><Pencil :size="18" /></router-link>
          <button v-if="game.status === 'PUBLISHED'" class="icon-btn" title="下架" @click="changeStatus(game, 'OFFLINE')"><CircleOff :size="18" /></button>
          <button v-else-if="game.currentVersionNo" class="icon-btn" title="重新上架" @click="changeStatus(game, 'PUBLISHED')"><CirclePlay :size="18" /></button>
          <button v-if="game.status === 'OFFLINE'" class="icon-btn danger" title="删除已下架游戏" @click="deletingGame = game"><Trash2 :size="18" /></button>
        </div>
      </article>
    </div>
    <EmptyState v-else title="还没有创建股票" action-text="创建股票" @action="$router.push('/customer/entertainment/create')" />
    <Transition name="confirm-modal"><div v-if="deletingGame" class="confirm-mask" @click.self="deletingGame = null"><div class="confirm-dialog"><Trash2 :size="28" /><h2>删除“{{ deletingGame.title }}”？</h2><p>游戏将从娱乐广场和“我的游戏”中移除。已经产生的玩家结算数据不会被篡改，此操作无法撤销。</p><button class="danger-command" :disabled="deleting" @click="confirmDelete">确认删除</button><button class="cancel-command" :disabled="deleting" @click="deletingGame = null">取消</button></div></div></Transition>
  </div>
</template>
<script setup>
import { onMounted, ref } from 'vue'
import { CircleOff, CirclePlay, Eye, Pencil, Plus, Trash2 } from 'lucide-vue-next'
import { deleteGame, getMyGames, setGameStatus } from '../../api/entertainment'
import LoadingSpinner from '../../components/LoadingSpinner.vue'
import EmptyState from '../../components/EmptyState.vue'
import { notifyError, notifySuccess } from '../../services/notice'
const games = ref([]); const loading = ref(true); const error = ref(''); const deletingGame = ref(null); const deleting = ref(false)
const statusName = value => ({ DRAFT: '草稿', PUBLISHED: '已发布', OFFLINE: '已下架' }[value] || value)
async function load() { loading.value = true; error.value = ''; try { const data = await getMyGames({ page: 1, pageSize: 50 }); games.value = data.records || [] } catch (e) { error.value = e.message } finally { loading.value = false } }
async function changeStatus(game, status) { try { await setGameStatus(game.id, status); await load(); notifySuccess(status === 'PUBLISHED' ? '游戏已上架' : '游戏已下架') } catch (e) { notifyError(e.message) } }
async function confirmDelete() { deleting.value = true; try { await deleteGame(deletingGame.value.id); deletingGame.value = null; await load(); notifySuccess('游戏已删除') } catch (e) { notifyError(e.message) } finally { deleting.value = false } }
onMounted(load)
</script>
<style scoped>
.manage-page { max-width: 1080px; margin: 0 auto; padding: 28px 24px 60px; }.manage-page > header { display: flex; align-items: flex-end; justify-content: space-between; gap: 16px; margin-bottom: 20px; }.manage-page h1 { font-size: 25px; }.manage-page header p { margin-top: 4px; color: var(--color-text-muted); font-size: 13px; }.manage-list { border: 1px solid var(--color-line); border-radius: 8px; overflow:hidden;background:#fff}.manage-list article{min-height:94px;display:grid;grid-template-columns:72px minmax(0,1fr) 230px auto;align-items:center;gap:16px;padding:14px 16px;border-bottom:1px solid var(--color-line)}.manage-list article:last-child{border-bottom:0}.ticker{height:58px;display:grid;place-items:center;border-radius:7px;color:#216345;background:#e7f3eb;font:800 12px ui-monospace,monospace;overflow:hidden}.game-info>div{display:flex;align-items:center;gap:8px}.game-info h2{min-width:0;font-size:15px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}.game-info p{margin-top:6px;color:var(--color-text-muted);font-size:12px}.status{padding:2px 6px;border-radius:4px;color:#6d737b;background:#eef0f2;font-size:10px}.status[data-status=PUBLISHED]{color:#24734a;background:#e8f5ed}.status[data-status=OFFLINE]{color:#a24a43;background:#faecea}.stats{display:grid;grid-template-columns:repeat(3,1fr);gap:10px}.stats span{display:flex;flex-direction:column;color:var(--color-text-muted);font-size:10px}.stats strong{color:var(--color-text);font-size:14px}.commands{display:flex;gap:5px}.icon-btn{width:36px;height:36px;display:grid;place-items:center;border:1px solid var(--color-line);border-radius:6px;color:var(--color-text-secondary);background:#fff}.icon-btn:hover{color:var(--color-primary);background:var(--color-primary-bg)}.icon-btn.danger{color:#a8433d}.confirm-mask{position:fixed;z-index:1200;inset:0;display:grid;place-items:center;padding:18px;background:rgba(28,38,32,.6)}.confirm-dialog{width:min(390px,100%);display:flex;flex-direction:column;align-items:center;padding:24px;border-radius:8px;background:#fff;text-align:center;box-shadow:0 24px 65px rgba(20,29,24,.32)}.confirm-dialog>svg{color:#ad443e}.confirm-dialog h2{margin-top:10px;font-size:18px}.confirm-dialog p{margin:8px 0 14px;color:#68736d;font-size:11px;line-height:1.7}.danger-command,.cancel-command{width:100%;min-height:40px;border-radius:6px}.danger-command{color:#fff;background:#ae433d;font-weight:700}.cancel-command{margin-top:6px;color:#68736d;background:transparent}.confirm-modal-enter-active,.confirm-modal-leave-active{transition:opacity .23s}.confirm-modal-enter-active .confirm-dialog,.confirm-modal-leave-active .confirm-dialog{transition:transform .27s,opacity .22s}.confirm-modal-enter-from,.confirm-modal-leave-to{opacity:0}.confirm-modal-enter-from .confirm-dialog{opacity:0;transform:translateY(16px) scale(.98)}.confirm-modal-leave-to .confirm-dialog{opacity:0;transform:translateY(8px) scale(.985)}
@media(max-width:800px){.manage-page{padding:16px 12px 90px}.manage-list article{grid-template-columns:58px 1fr auto}.ticker{height:50px}.stats{grid-column:2/-1;grid-row:2}.commands{grid-column:3;grid-row:1}.manage-page>header{align-items:flex-start}}
</style>
