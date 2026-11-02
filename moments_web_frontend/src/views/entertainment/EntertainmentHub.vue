<template>
  <div class="ent-page">
    <header class="ent-toolbar">
      <div>
        <h1>股票</h1>
      </div>
      <div class="toolbar-actions">
        <button class="btn btn-outline multiplayer-command" @click="showJoinRoom = true"><Users :size="17" />多人游戏</button>
        <button class="btn btn-outline" @click="showManual = true"><BookOpen :size="17" />玩法说明</button>
        <router-link v-if="isLoggedIn" class="btn btn-outline" to="/customer/entertainment/history">
          <History :size="17" />游玩记录
        </router-link>
        <router-link v-if="isLoggedIn" class="btn btn-outline" to="/customer/entertainment/my-games">
          <FolderKanban :size="17" />我的游戏
        </router-link>
        <router-link v-if="isLoggedIn" class="btn btn-primary" to="/customer/entertainment/create">
          <Plus :size="17" />创建股票
        </router-link>
      </div>
    </header>

    <section class="tutorial-entry" aria-label="新手教学股">
      <GraduationCap :size="28" />
      <div><h2>启航科技 <span>LEARN · 新手教学股</span></h2><p><span>3 回合 · 不限时</span><span>独立虚拟账户</span></p></div>
      <router-link class="btn btn-outline" to="/customer/entertainment/tutorial"><Play :size="17" />{{ tutorialCompleted ? '查看教程成果' : tutorialStarted ? '继续新手教程' : '从零开始练习' }}</router-link>
    </section>

    <section class="filter-band" aria-label="游戏筛选">
      <label class="search-field">
        <Search :size="18" aria-hidden="true" />
        <input v-model.trim="filters.keyword" placeholder="搜索股票、代码或介绍" @keyup.enter="loadGames(true)" />
      </label>
      <CustomSelect v-model="filters.sort" :options="sortOptions" aria-label="排序" @change="loadGames(true)" />
      <button class="icon-button" title="刷新" :disabled="loading" @click="loadGames(true)">
        <RefreshCw :size="18" :class="{ spinning: loading }" />
      </button>
    </section>

    <div class="mode-strip">
      <button :class="{ active: !filters.stockType }" @click="selectType('')">全部类型</button>
      <button v-for="type in stockTypes" :key="type" :class="{ active: filters.stockType === type }" @click="selectType(type)">
        {{ type }}
      </button>
    </div>

    <LoadingSpinner v-if="loading && !loaded" text="正在加载股票游戏" />
    <EmptyState v-else-if="error && !loaded" icon="warning" :title="error" action-text="重试" @action="loadGames(false)" />
    <div v-else-if="games.length" class="game-grid">
      <article v-for="game in games" :key="game.id" class="game-item">
        <button class="game-main" @click="openGame(game.id)">
          <div class="ticker-block" :data-type="typeTone(game.stockType)">
            <span>{{ game.stockCode }}</span>
            <TrendingUp :size="25" />
          </div>
          <div class="game-copy">
            <div class="game-heading">
              <h2>{{ game.title }}</h2>
              <span class="stock-type">{{ game.stockType }}</span>
            </div>
            <p>{{ game.description || '这位创作者还没有填写游戏介绍。' }}</p>
            <div class="creator-line">
              <UserRound :size="14" />{{ game.creator.nickname }}
              <span>·</span><span>{{ game.versionLabel }}</span>
            </div>
          </div>
        </button>
        <footer class="game-meta">
          <span><Bot :size="15" />{{ game.aiEnabled ? `${game.aiCount} 个 AI` : '无 AI' }}</span>
          <span><Timer :size="15" />{{ game.roundCount }} 回合</span>
          <span><Gamepad2 :size="15" />{{ game.playCount }} 次</span>
          <button class="play-command" @click="openGame(game.id)">查看并开始<ChevronRight :size="17" /></button>
        </footer>
      </article>
    </div>
    <EmptyState v-else icon="inbox" title="还没有股票游戏" description="换个筛选条件，或创建第一只股票。"
      :action-text="isLoggedIn ? '创建股票' : ''" @action="$router.push('/customer/entertainment/create')" />
    <Transition name="modal">
      <div v-if="showJoinRoom" class="modal-mask" @click.self="showJoinRoom = false">
        <form class="join-dialog" @submit.prevent="joinRoom">
          <header><div><span>多人游戏</span><h2>进入股票房间</h2></div><button type="button" title="关闭" @click="showJoinRoom = false"><X :size="19" /></button></header>
          <div class="join-fields">
            <label><span>房间邀请码</span><input v-model.trim="inviteCode" maxlength="6" autofocus placeholder="6 位字母或数字" @input="inviteCode = inviteCode.toUpperCase().replace(/[^A-Z0-9]/g, '')" /></label>
            <label><span>游玩密钥</span><input v-model.trim="roomPlayKey" maxlength="80" type="password" placeholder="作者未设置时可不填" /></label>
          </div>
          <footer><button type="button" class="btn btn-outline" @click="showJoinRoom = false">取消</button><button class="btn btn-primary" :disabled="inviteCode.length !== 6 || joining"><LogIn :size="17" />{{ joining ? '正在进入' : '进入多人房间' }}</button></footer>
        </form>
      </div>
    </Transition>
    <GameManualDialog :open="showManual" @close="showManual = false" />
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { Anchor, BookOpen, Bot, ChevronRight, FolderKanban, Gamepad2, GraduationCap, History, LogIn, Play, Plus, RefreshCw, Search, Timer, TrendingUp, UserRound, Users, X } from 'lucide-vue-next'
import { ensureEntertainmentGuest, getGames, joinGameRoom } from '../../api/entertainment'
import LoadingSpinner from '../../components/LoadingSpinner.vue'
import EmptyState from '../../components/EmptyState.vue'
import CustomSelect from '../../components/CustomSelect.vue'
import GameManualDialog from './GameManualDialog.vue'
import { notifyError } from '../../services/notice'
import { loadTutorialSession } from '../../games/guessStockTutorial'

const router = useRouter()
const tutorialSave = loadTutorialSession()
const tutorialCompleted = tutorialSave.status === 'COMPLETED'
const tutorialStarted = tutorialSave.phase !== 'ROUND_START' || tutorialSave.tutorial.lessons.length > 0
const games = ref([])
const loading = ref(true)
const loaded = ref(false)
const error = ref('')
const inviteCode = ref('')
const roomPlayKey = ref('')
const joining = ref(false)
const showManual = ref(false)
const showJoinRoom = ref(false)
const filters = reactive({ keyword: '', stockType: '', sort: 'LATEST' })
const sortOptions = [{ value: 'LATEST', label: '最新发布' }, { value: 'POPULAR', label: '最多游玩' }]
const isLoggedIn = computed(() => Boolean(localStorage.getItem('token')))
const stockTypes = computed(() => [...new Set(games.value.map(game => game.stockType).filter(Boolean))])

function typeTone(type = '') {
  if (type.includes('贵金属')) return 'gold'
  if (type.includes('航天')) return 'space'
  if (type.includes('工业')) return 'industry'
  return 'market'
}

function selectType(type) {
  filters.stockType = type
  loadGames(true)
}

function openGame(id) {
  router.push(`/customer/entertainment/games/${id}`)
}
async function joinRoom() {
  joining.value = true
  try { await ensureEntertainmentGuest(); const room = await joinGameRoom(inviteCode.value, roomPlayKey.value || null); showJoinRoom.value = false; router.push(`/customer/entertainment/rooms/${room.id}`) }
  catch (e) { notifyError(e.message) }
  finally { joining.value = false }
}

async function loadGames(fromAction = false) {
  loading.value = true
  if (!loaded.value) error.value = ''
  try {
    await ensureEntertainmentGuest()
    const data = await getGames({ page: 1, pageSize: 50, ...filters })
    games.value = data.records || []
    loaded.value = true
    error.value = ''
  } catch (e) {
    if (fromAction && loaded.value) notifyError(e.message)
    else error.value = e.message
  } finally {
    loading.value = false
  }
}

onMounted(loadGames)
</script>

<style scoped>
.ent-page { max-width: 1180px; margin: 0 auto; padding: 28px 24px 56px; }
.ent-category-tabs{display:flex;gap:25px;margin-bottom:20px;border-bottom:1px solid #dce4e7}.ent-category-tabs>a,.ent-category-tabs>span{display:flex;align-items:center;gap:8px;padding:10px 2px;font-size:14px;color:#78868b;border-bottom:2px solid transparent}.ent-category-tabs .active{border-color:#e68a37;color:#db7823;font-weight:700}
.tutorial-entry{display:flex;align-items:center;gap:14px;padding:16px 0;margin-bottom:18px;border-top:1px solid #d5e5dc;border-bottom:1px solid #d5e5dc;color:#1a7658}.tutorial-entry>svg{flex-shrink:0}.tutorial-entry>div{flex:1;min-width:0}.tutorial-entry h2{font-size:16px;color:#263d32}.tutorial-entry h2 span{display:inline-block;margin-left:8px;font-size:11px;color:#657b6f;font-weight:500}.tutorial-entry p{display:flex;flex-wrap:wrap;gap:4px 10px;margin-top:4px;font-size:12px;color:#738279}.tutorial-entry p span{white-space:nowrap}.tutorial-entry .btn{flex-shrink:0;border-color:#a9cbb9;color:#1a7658}@media(max-width:600px){.tutorial-entry{flex-wrap:wrap;gap:10px}.tutorial-entry h2 span{display:block;margin:3px 0 0}.tutorial-entry .btn{margin-left:38px}}
.ent-toolbar { display: flex; justify-content: space-between; align-items: flex-end; gap: 24px; margin-bottom: 22px; }
.ent-toolbar h1 { font-size: 28px; font-weight: 800; }
.ent-toolbar p { margin-top: 5px; color: var(--color-text-muted); font-size: 14px; }
.toolbar-actions { display: flex; gap: 8px; flex-wrap: wrap; }
.multiplayer-command{color:#8c4b14;border-color:#e0c19f;background:#fffaf4}
.filter-band { display: grid; grid-template-columns: minmax(260px, 1fr) 150px 42px; gap: 8px; padding: 12px; background: #fff; border: 1px solid var(--color-line); border-radius: 8px; }
.search-field { display: flex; align-items: center; gap: 9px; min-height: 42px; padding: 0 12px; border: 1px solid var(--color-line); border-radius: 6px; color: var(--color-text-muted); }
.search-field:focus-within { border-color: var(--color-primary); }
.search-field input { width: 100%; border: 0; background: transparent; color: var(--color-text); }
.sort-select { border: 1px solid var(--color-line); border-radius: 6px; padding: 0 10px; background: #fff; color: var(--color-text-secondary); }
.icon-button { width: 42px; height: 42px; display: grid; place-items: center; border: 1px solid var(--color-line); border-radius: 6px; background: #fff; color: var(--color-text-secondary); }
.icon-button:hover { background: var(--color-bg); }
.spinning { animation: spin 0.8s linear infinite; }
.mode-strip { display: flex; gap: 6px; overflow-x: auto; margin: 14px 0 18px; }
.mode-strip button { flex: 0 0 auto; padding: 7px 12px; border-radius: 6px; background: transparent; color: var(--color-text-muted); font-size: 13px; }
.mode-strip button.active, .mode-strip button:hover { color: #9a5412; background: #fff1df; }
.game-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 12px; }
.game-item { overflow: hidden; background: #fff; border: 1px solid var(--color-line); border-radius: 8px; transition: border-color .2s, box-shadow .2s; }
.game-item:hover { border-color: #ddc5a8; box-shadow: 0 7px 20px rgba(45, 38, 28, .07); }
.game-main { width: 100%; min-height: 150px; display: flex; gap: 16px; padding: 18px; text-align: left; background: #fff; }
.ticker-block { width: 88px; height: 88px; flex: 0 0 88px; display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 6px; border-radius: 8px; color: #1d5e3b; background: #eaf5ee; }
.ticker-block span { max-width: 76px; font-size: 14px; font-weight: 800; overflow: hidden; text-overflow: ellipsis; }
.ticker-block[data-type="gold"] { color: #7a5b10; background: #f9f1cf; }
.ticker-block[data-type="space"] { color: #3c4f83; background: #e8ecf7; }
.ticker-block[data-type="industry"] { color: #764231; background: #f4e8e1; }
.game-copy { min-width: 0; flex: 1; }
.game-heading { display: flex; align-items: center; gap: 8px; }
.game-heading h2 { min-width: 0; font-size: 17px; line-height: 1.35; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.stock-type { flex: 0 0 auto; padding: 2px 6px; border-radius: 4px; color: #59616c; background: #f0f2f4; font-size: 11px; }
.game-copy > p { height: 42px; margin-top: 9px; color: var(--color-text-muted); font-size: 13px; line-height: 21px; overflow: hidden; }
.creator-line { display: flex; align-items: center; gap: 5px; margin-top: 12px; color: #69717c; font-size: 12px; }
.game-meta { min-height: 48px; display: flex; align-items: center; gap: 12px; padding: 8px 14px; border-top: 1px solid var(--color-line); color: var(--color-text-muted); font-size: 12px; }
.game-meta > span { display: inline-flex; align-items: center; gap: 4px; white-space: nowrap; }
.play-command { margin-left: auto; display: inline-flex; align-items: center; color: #b45e14; background: transparent; font-weight: 700; font-size: 12px; }
@keyframes spin { to { transform: rotate(360deg); } }
.modal-mask{position:fixed;z-index:1000;inset:0;display:grid;place-items:center;padding:18px;background:rgba(25,34,29,.58);backdrop-filter:blur(3px)}.join-dialog{width:min(460px,100%);overflow:hidden;border-radius:8px;background:#fff;box-shadow:0 24px 70px rgba(17,27,21,.32)}.join-dialog header{display:flex;align-items:center;justify-content:space-between;padding:18px 20px;border-bottom:1px solid var(--color-line)}.join-dialog header span{color:#9a5b20;font-size:10px;font-weight:800}.join-dialog h2{font-size:19px}.join-dialog header button{width:34px;height:34px;display:grid;place-items:center;background:transparent}.join-fields{display:grid;gap:13px;padding:20px}.join-fields label{display:grid;gap:6px}.join-fields label span{color:var(--color-text-secondary);font-size:11px;font-weight:700}.join-fields input{width:100%;height:43px;padding:0 12px;border:1px solid var(--color-line);border-radius:6px;background:#fff}.join-fields label:first-child input{font:800 16px ui-monospace,monospace;letter-spacing:2px;text-transform:uppercase}.join-fields small{color:var(--color-error);font-size:11px}.join-dialog footer{display:flex;justify-content:flex-end;gap:8px;padding:14px 20px;border-top:1px solid var(--color-line);background:#fafbfa}.modal-enter-active,.modal-leave-active{transition:opacity .2s}.modal-enter-active .join-dialog,.modal-leave-active .join-dialog{transition:transform .25s ease,opacity .2s}.modal-enter-from,.modal-leave-to{opacity:0}.modal-enter-from .join-dialog,.modal-leave-to .join-dialog{opacity:0;transform:translateY(14px) scale(.98)}
@media (max-width: 900px) { .game-grid { grid-template-columns: 1fr; } }
@media (max-width: 768px) {
  .ent-page { padding: 16px 12px 90px; }
  .ent-toolbar { align-items: flex-start; flex-direction: column; }
  .ent-toolbar h1 { font-size: 23px; }
  .filter-band { grid-template-columns: 1fr 110px 42px; }
  .search-field { grid-column: 1 / -1; }
  .game-main { padding: 14px; }
  .ticker-block { width: 68px; height: 68px; flex-basis: 68px; }
  .game-meta { gap: 7px; overflow-x: auto; }
}
</style>
