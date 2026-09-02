<template>
  <div class="detail-page">
    <LoadingSpinner v-if="loading" text="正在读取游戏" />
    <EmptyState v-else-if="error" icon="warning" :title="error" action-text="返回娱乐" @action="$router.push('/customer/entertainment')" />
    <template v-else-if="detail">
      <header class="detail-header">
        <div class="detail-toolbar">
          <button class="back-button" title="返回" @click="$router.push('/customer/entertainment')"><ArrowLeft :size="20" /></button>
          <div class="detail-tags"><span>{{ detail.config.stockType }}</span><span>{{ detail.game.versionLabel }}</span><span>{{ detail.game.visibility === 'PUBLIC' ? '公开游戏' : '私密游戏' }}</span></div>
        </div>
        <div class="stock-overview">
          <div class="stock-mark"><span>{{ detail.config.stockCode }}</span><LineChart :size="30" /></div>
          <div class="stock-title">
            <h1>{{ detail.game.title }}</h1>
            <p>{{ detail.game.description || '暂无游戏介绍' }}</p>
            <div class="creator"><UserRound :size="15" /><span>创建者</span><strong>{{ detail.game.creator.nickname }}</strong></div>
          </div>
          <div class="primary-action">
            <button v-if="detail.hasResumableSession" class="btn btn-primary btn-lg main-play" :disabled="starting" @click="continueGame"><Play :size="19" />继续游戏</button>
            <button v-else class="btn btn-primary btn-lg main-play" :disabled="starting" @click="prepareSolo"><Play :size="19" />开始游戏</button>
            <button v-if="detail.hasResumableSession" class="btn btn-outline restart-saved" :disabled="starting" @click="showRestartSaved = true"><RotateCcw :size="18" />重新开始</button>
            <button v-if="detail.config.dailyChallengeEnabled" class="btn btn-outline" :disabled="starting" @click="startChallenge"><Trophy :size="18" />今日挑战</button>
            <button v-if="detail.config.dailyChallengeEnabled" class="btn btn-ghost" :disabled="starting" @click="openChallengeLeaderboard"><Medal :size="18" />挑战榜</button>
            <button v-if="detail.activeRoomId" class="btn btn-outline" :disabled="starting" @click="returnToRoom"><Users :size="18" />{{ detail.activeRoomStatus === 'PLAYING' ? '返回多人游戏' : '返回多人房间' }}</button>
            <button v-else-if="detail.config.multiplayerEnabled" class="btn btn-outline" :disabled="starting" @click="prepareRoom"><Users :size="18" />多人房间</button>
<!--            <button class="btn btn-ghost" @click="showManual = true"><BookOpen :size="18" />玩法说明</button>-->
            <small>{{ hostedAi?.configured ? '创建者已提供 AI，无需 API Key' : '虚拟行情与虚拟资产' }}</small>
          </div>
        </div>
        <section class="rule-band">
          <div><LineChart :size="19" /><strong>{{ price(detail.config.initialPrice) }}</strong><span>初始价格</span></div>
          <div><Timer :size="19" /><strong>{{ detail.config.roundCount }}</strong><span>回合</span></div>
          <div><Clock3 :size="19" /><strong>{{ detail.config.progressionMode === 'MANUAL' ? '手动' : detail.config.tradingSeconds }}</strong><span>{{ detail.config.progressionMode === 'MANUAL' ? '无倒计时' : '秒交易' }}</span></div>
          <div><WalletCards :size="19" /><strong>{{ money(detail.config.initialCash) }}</strong><span>初始资金</span></div>
          <div><Bot :size="19" /><strong>{{ detail.config.aiEnabled ? detail.config.aiCount : 0 }}</strong><span>AI 对手</span></div>
        </section>
      </header>

      <div class="detail-layout">
        <main>
          <section class="market-section">
            <div class="section-title"><div><span>GAME RULES</span><h2>市场参数</h2></div><small>{{ pricingEngineName }}</small></div>
            <dl class="market-grid">
              <div><dt>价格引擎</dt><dd>{{ pricingEngineName }}</dd></div>
              <div><dt>基础波动率</dt><dd>±{{ detail.config.baseVolatilityPercent }}%</dd></div>
              <div><dt>订单影响上限</dt><dd>±{{ detail.config.maxOrderImpactPercent }}%</dd></div>
              <div><dt>交易撮合</dt><dd>{{ detail.config.tradingMode === 'TARGET_AUCTION' ? '目标仓位竞价' : '经典分阶段交易' }}</dd></div>
              <div v-if="detail.config.tradingMode === 'TARGET_AUCTION'"><dt>手续费 / 滑点</dt><dd>{{ detail.config.feePercent }}% / {{ detail.config.slippagePercent }}%</dd></div>
              <div><dt>单次最大买入</dt><dd>{{ detail.config.maxBuyQuantity }} 股</dd></div>
              <div><dt>市场流动性</dt><dd>{{ liquidityName(detail.config.liquidity) }}</dd></div>
              <div><dt>AI 难度</dt><dd>{{ detail.config.aiEnabled ? difficultyName(detail.config.aiDifficulty) : '未启用' }}</dd></div>
              <div v-if="detail.config.aiEnabled"><dt>AI 资金范围</dt><dd>{{ aiCashRange }}</dd></div>
              <div><dt>随机事件</dt><dd>{{ enabledEvents.length }} 个</dd></div>
              <div><dt>回合推进</dt><dd>{{ detail.config.progressionMode === 'MANUAL' ? '全员手动确认' : '计时自动推进' }}</dd></div>
              <div><dt>多人房间</dt><dd>{{ detail.config.multiplayerEnabled ? `最多 ${detail.config.maxHumanPlayers} 人` : '未开启' }}</dd></div>
              <div><dt>银行融资</dt><dd>{{ creditModeName }}</dd></div>
              <div v-if="detail.config.loanEnabled"><dt>融资时间</dt><dd>{{ creditTimingName }}</dd></div>
              <div v-if="detail.config.pricingEngineVersion === 'V3'"><dt>公司财报</dt><dd>每 {{ detail.config.reportIntervalRounds }} 回合</dd></div>
            </dl>
          </section>

          <section v-if="detail.game.owner && detail.config.aiEnabled" class="hosted-ai-panel">
            <div><Bot :size="20" /><span><strong>为玩家提供固定 AI</strong><small>{{ hostedAi?.configured ? `${hostedAi.model} · 已启用` : '玩家开始游戏时无需填写自己的 API Key' }}</small></span></div>
            <div><button v-if="hostedAi?.configured" class="btn btn-ghost" @click="removeHostedAi">停用</button><button class="btn btn-outline" @click="openHostedSetup">{{ hostedAi?.configured ? '修改' : '配置' }}</button></div>
          </section>

          <section class="event-section">
            <div class="section-title"><div><span>MARKET EVENTS</span><h2>事件池</h2></div><small>每回合最多一个</small></div>
            <div v-if="enabledEvents.length" class="event-table">
              <div class="event-row event-head"><span>事件</span><span>发生</span><span>影响</span><span>方向</span><span>原始幅度</span></div>
              <div v-for="event in pagedEnabledEvents" :key="event.name" class="event-row">
                <span><strong>{{ event.name }}</strong><small>{{ event.description }}</small><small>{{ eventMeta(event) }}</small></span>
                <span>{{ event.occurrenceProbability }}%</span><span>{{ event.effectProbability }}%</span>
                <span>{{ directionName(event.direction) }}</span><span>{{ event.minImpactPercent }}%～{{ event.maxImpactPercent }}%</span>
              </div>
            </div>
            <PaginationBar v-if="enabledEvents.length" v-model="eventPage" :total-pages="eventPages" :total-items="enabledEvents.length" />
            <div v-else class="empty-events">没有配置随机事件</div>
          </section>
        </main>

        <aside class="leaderboard-panel">
          <header><div><Trophy :size="19" /><h2>收益率榜</h2></div><span>{{ leaderboard?.versionLabel || detail.game.versionLabel }}</span></header>
          <div v-if="leaderboardLoading" class="leaderboard-loading"><LoadingSpinner /></div>
          <ol v-else-if="leaderboard?.entries?.length">
            <li v-for="entry in leaderboard.entries.slice(0, 20)" :key="entry.sessionId" :class="{ mine: entry.currentPlayer }">
              <span class="rank">{{ entry.rank }}</span>
              <span class="player-name">{{ entry.displayName }}</span>
              <span class="rank-value"><strong :class="Number(entry.returnPercent) >= 0 ? 'positive' : 'negative'">{{ signed(entry.returnPercent) }}%</strong><small>{{ money(entry.finalAsset) }}</small></span>
            </li>
          </ol>
          <div v-else class="empty-ranking"><Medal :size="30" /><span>等待首位完成者</span></div>
        </aside>
      </div>
      <div class="risk-note"><ShieldAlert :size="16" />虚拟行情、虚拟资产，仅供娱乐，不构成投资建议。</div>

      <div v-if="showAiSetup" class="modal-mask" @click.self="showAiSetup = false">
        <form class="ai-dialog" @submit.prevent="startGame">
          <header><div><Bot :size="20" /><h2>连接本局 AI</h2></div><button type="button" title="关闭" @click="showAiSetup = false"><X :size="19" /></button></header>
          <p>本局可以不加入 AI。开启后可使用创建者配置、账号默认配置，或仅为本局填写模型配置。</p>
          <label class="use-ai-toggle"><input v-model="useAi" type="checkbox" /><span>加入 {{ detail.config.aiCount }} 个 AI 对手</span></label>
          <template v-if="useAi">
          <div v-if="hostedAi?.configured" class="default-ai-card" :class="{ active: aiSource === 'HOSTED' }">
            <button type="button" @click="aiSource = 'HOSTED'"><CircleCheck :size="17" /><span><strong>使用创建者配置</strong><small>{{ hostedAi.model }} · 无需填写 Key</small></span></button>
          </div>
          <div v-if="defaultAi?.configured" class="default-ai-card" :class="{ active: aiSource === 'DEFAULT' }">
            <button type="button" @click="aiSource = 'DEFAULT'"><KeyRound :size="17" /><span><strong>使用我的默认配置</strong><small>{{ defaultAi.model }} · {{ defaultAi.baseUrl }}</small></span></button>
            <button type="button" class="edit-default" @click="editDefault">修改</button>
          </div>
          <button type="button" class="own-ai-choice" :class="{ active: aiSource === 'OWN' }" @click="aiSource = 'OWN'">使用本局自己的 AI Key</button>
          <template v-if="aiSource === 'OWN'">
            <label><span>模型服务</span><CustomSelect v-model="aiProvider" :options="providerOptions" @change="selectAiProvider" /></label>
            <label v-if="aiProvider === 'custom'"><span>接口地址</span><input v-model.trim="aiCredential.baseUrl" placeholder="https://example.com/v1" autocomplete="off" required /></label>
            <label><span>模型</span><input v-model.trim="aiCredential.model" placeholder="gpt-5-mini" autocomplete="off" required /></label>
            <label><span>API Key</span><input v-model="aiCredential.apiKey" type="password" autocomplete="new-password" required /></label>
            <label v-if="loggedIn" class="save-default"><input v-model="saveAsDefault" type="checkbox" /><span>{{ defaultAi?.configured ? '更新账号默认 AI 配置' : '保存为账号默认 AI 配置' }}</span></label>
          </template>
          </template>
          <button class="btn btn-primary btn-lg" :disabled="starting"><Play :size="18" />{{ useAi ? '与 AI 开始本局' : '不使用 AI，开始本局' }}</button>
        </form>
      </div>
      <div v-if="showHostedSetup" class="modal-mask" @click.self="showHostedSetup = false">
        <form class="ai-dialog" @submit.prevent="saveHostedAi">
          <header><div><Bot :size="20" /><h2>创建者固定 AI</h2></div><button type="button" title="关闭" @click="showHostedSetup = false"><X :size="19" /></button></header>
          <p>此 Key 将加密保存在服务端，并承担其他玩家游玩本游戏时的模型调用。</p>
          <label><span>模型服务</span><CustomSelect v-model="hostedProvider" :options="providerOptions" @change="applyAiProvider(hostedProvider, hostedCredential)" /></label>
          <label v-if="hostedProvider === 'custom'"><span>接口地址</span><input v-model.trim="hostedCredential.baseUrl" required /></label>
          <label><span>模型</span><input v-model.trim="hostedCredential.model" required /></label>
          <label><span>API Key</span><input v-model="hostedCredential.apiKey" type="password" autocomplete="new-password" required /></label>
          <button class="btn btn-primary btn-lg" :disabled="starting"><KeyRound :size="18" />保存并提供给玩家</button>
        </form>
      </div>
      <div v-if="showPlayKey" class="modal-mask" @click.self="showPlayKey = false">
        <form class="ai-dialog" @submit.prevent="confirmPlayKey">
          <header><div><KeyRound :size="20" /><h2>输入游玩密钥</h2></div><button type="button" title="关闭" @click="showPlayKey = false"><X :size="19" /></button></header>
          <p>创建者为本游戏开启了访问密钥。密钥会在成功创建{{ pendingMode === 'MULTI' ? '多人房间' : '单人场次' }}时计次。</p>
          <label><span>游玩密钥</span><input v-model.trim="playKey" type="password" autocomplete="off" required /></label>
          <button class="btn btn-primary btn-lg" :disabled="starting"><KeyRound :size="18" />验证并继续</button>
        </form>
      </div>
      <div v-if="showRestartSaved" class="modal-mask" @click.self="showRestartSaved = false">
        <div class="ai-dialog restart-dialog">
          <header><div><RotateCcw :size="20" /><h2>重新开始这局游戏？</h2></div><button type="button" title="关闭" @click="showRestartSaved = false"><X :size="19" /></button></header>
          <p>当前可恢复的存档将结束，之后从第 1 回合创建新场次。该操作不会删除历史记录。</p>
          <button class="btn btn-danger btn-lg" :disabled="starting" @click="restartSavedGame">结束存档并重新开始</button>
          <button class="btn btn-ghost" :disabled="starting" @click="showRestartSaved = false">保留存档</button>
        </div>
      </div>
      <GameManualDialog :open="showManual" :config="detail.config" @close="showManual = false" />
    </template>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowLeft, BookOpen, Bot, CircleCheck, Clock3, KeyRound, LineChart, Medal, Play, RotateCcw, ShieldAlert, Timer, Trophy, UserRound, Users, WalletCards, X } from 'lucide-vue-next'
import { abandonGameSession, createGameRoom, deleteGameAiCredential, ensureEntertainmentGuest, getDailyChallenge, getDefaultAiCredential, getGame, getGameAiCredential, getLeaderboard, saveDefaultAiCredential, saveGameAiCredential, startDailyChallenge, startGameSession } from '../../api/entertainment'
import LoadingSpinner from '../../components/LoadingSpinner.vue'
import EmptyState from '../../components/EmptyState.vue'
import { AI_PROVIDERS, applyAiProvider, detectAiProvider } from '../../constants/aiProviders'
import GameManualDialog from './GameManualDialog.vue'
import CustomSelect from '../../components/CustomSelect.vue'
import PaginationBar from '../../components/PaginationBar.vue'
import { confirmNotice, notifyError, notifySuccess } from '../../services/notice'

const route = useRoute()
const router = useRouter()
const detail = ref(null)
const leaderboard = ref(null)
const loading = ref(true)
const leaderboardLoading = ref(true)
const starting = ref(false)
const error = ref('')
const showAiSetup = ref(false)
const showHostedSetup = ref(false)
const showManual = ref(false)
const showPlayKey = ref(false); const playKey = ref(''); const pendingMode = ref('SOLO')
const showRestartSaved = ref(false)
const eventPage = ref(1)
const aiCredential = ref({ baseUrl: 'https://api.openai.com/v1', model: 'gpt-5-mini', apiKey: '' })
const aiProvider = ref('openai')
const defaultAi = ref(null); const useDefaultAi = ref(false); const saveAsDefault = ref(false)
const aiSource = ref('OWN')
const useAi = ref(true)
const hostedAi = ref(null); const hostedProvider = ref('openai'); const hostedCredential = ref({ baseUrl: 'https://api.openai.com/v1', model: 'gpt-5-mini', apiKey: '' })
const gameId = computed(() => Number(route.params.gameId))
const loggedIn = computed(() => Boolean(localStorage.getItem('token')))
const providerOptions = AI_PROVIDERS.map(item => ({ value: item.id, label: item.name }))
const enabledEvents = computed(() => (detail.value?.config.events || []).filter(event => event.enabled !== false))
const eventPages = computed(() => Math.max(1, Math.ceil(enabledEvents.value.length / 8)))
const pagedEnabledEvents = computed(() => enabledEvents.value.slice((eventPage.value - 1) * 8, eventPage.value * 8))
const aiCashRange = computed(() => { const values = (detail.value?.config.aiPlayers || []).map(ai => Number(ai.initialCash)); if (!values.length) return money(detail.value?.config.initialCash); return `${money(Math.min(...values))}～${money(Math.max(...values))}` })
const pricingEngineName = computed(() => ({ V1: '经典价格 V1', V2: '市场趋势 V2', V3: '基本面与财报 V3' }[detail.value?.config.pricingEngineVersion] || '市场趋势 V2'))
const creditModeName = computed(() => {
  if (!detail.value?.config.loanEnabled) return '未开启'
  return detail.value.config.creditInteractionMode === 'INLINE' ? '开盘内融资' : '独立银行阶段'
})
const creditTimingName = computed(() => {
  const config = detail.value?.config
  if (!config?.loanEnabled) return '未开启'
  if (config.creditInteractionMode === 'INLINE') return config.progressionMode === 'MANUAL' ? '跟随手动交易' : `与交易共用 ${config.tradingSeconds} 秒`
  return config.creditPhaseTimed ? `${config.creditPhaseSeconds} 秒` : '全员确认后推进'
})

const money = value => Number(value || 0).toLocaleString('zh-CN', { maximumFractionDigits: 2 })
const price = value => `¥${Number(value || 0).toFixed(2)}`
const signed = value => `${Number(value) >= 0 ? '+' : ''}${Number(value).toFixed(2)}`
const liquidityName = value => ({ LOW: '低', MEDIUM: '中', HIGH: '高' }[value] || value)
const difficultyName = value => ({ EASY: '简单', NORMAL: '普通', HARD: '困难' }[value] || value)
const directionName = value => ({ UP: '上涨', DOWN: '下跌', RANDOM: '随机' }[value] || value)
const eventMeta = event => {
  const type = ({ DEMAND: '需求', SUPPLY: '供给', POLICY: '政策', FINANCIAL: '财务', ACCIDENT: '突发', INDUSTRY: '行业', SENTIMENT: '情绪' }[event.eventType] || '行业')
  const relevance = ({ LOW: '低相关', MEDIUM: '中相关', HIGH: '高相关' }[event.relevance] || '中相关')
  const credibility = ({ RUMOR: '传闻', PRELIMINARY: '初步确认', CONFIRMED: '已确认' }[event.credibility] || '已确认')
  const surprise = ({ EXPECTED: '已有预期', NORMAL: '一般意外', MAJOR: '显著超预期' }[event.surprise] || '一般意外')
  return `${type} · ${relevance} · ${credibility} · ${surprise}`
}

async function load() {
  loading.value = true
  error.value = ''
  try {
    await ensureEntertainmentGuest()
    detail.value = await getGame(gameId.value)
    if (detail.value.config.aiEnabled) hostedAi.value = await getGameAiCredential(gameId.value)
    if (loggedIn.value) { try { defaultAi.value = await getDefaultAiCredential() } catch { defaultAi.value = null } }
    loadLeaderboard()
  } catch (e) { error.value = e.message } finally { loading.value = false }
}

async function loadLeaderboard() {
  leaderboardLoading.value = true
  try { leaderboard.value = await getLeaderboard(gameId.value) } catch { leaderboard.value = null }
  finally { leaderboardLoading.value = false }
}

function prepareSolo() { if (detail.value.game.playKeyRequired) { pendingMode.value = 'SOLO'; showPlayKey.value = true; return } openSoloSetup() }
function openSoloSetup() { if (detail.value.config.aiEnabled) { useAi.value = true; aiSource.value = hostedAi.value?.configured ? 'HOSTED' : defaultAi.value?.configured ? 'DEFAULT' : 'OWN'; showAiSetup.value = true } else startGame() }
function selectAiProvider() { applyAiProvider(aiProvider.value, aiCredential.value) }
function editDefault() { aiSource.value = 'OWN'; saveAsDefault.value = true; aiCredential.value.baseUrl = defaultAi.value.baseUrl; aiCredential.value.model = defaultAi.value.model; aiCredential.value.apiKey = ''; aiProvider.value = detectAiProvider(defaultAi.value.baseUrl, defaultAi.value.model) }
async function startGame() {
  starting.value = true
  try {
    await ensureEntertainmentGuest()
    if (detail.value.config.aiEnabled && useAi.value && aiSource.value === 'OWN' && saveAsDefault.value) {
      defaultAi.value = await saveDefaultAiCredential(aiCredential.value)
    }
    const savedSpeed = Number(localStorage.getItem('entertainment_auto_advance_speed'))
    const playback = { playbackSpeed: [1, 1.5, 2, 3, 5].includes(savedSpeed) ? savedSpeed : 1,
      autoAdvance: localStorage.getItem('entertainment_auto_advance') === 'true' }
    const body = detail.value.config.aiEnabled ? { useAi: useAi.value,
      useDefaultAi: useAi.value && aiSource.value === 'DEFAULT',
      aiCredential: useAi.value && aiSource.value === 'OWN' ? aiCredential.value : null,
      playKey: playKey.value || null, ...playback } : { ...playback, playKey: playKey.value || null }
    const session = await startGameSession(gameId.value, body)
    router.push(`/customer/entertainment/sessions/${session.id}/play`)
  } catch (e) { notifyError(e.message) } finally { starting.value = false }
}

async function startChallenge() {
  starting.value = true
  try {
    await ensureEntertainmentGuest()
    const challenge = await getDailyChallenge(gameId.value)
    const savedSpeed = Number(localStorage.getItem('entertainment_auto_advance_speed'))
    const session = await startDailyChallenge(challenge.id, { useAi: false, playKey: playKey.value || null,
      playbackSpeed: [1, 1.5, 2, 3, 5].includes(savedSpeed) ? savedSpeed : 1,
      autoAdvance: localStorage.getItem('entertainment_auto_advance') === 'true' })
    localStorage.setItem(`guess_stock_challenge_${session.id}`, String(challenge.id))
    router.push(`/customer/entertainment/sessions/${session.id}/play`)
  } catch (e) { notifyError(e.message) } finally { starting.value = false }
}

async function openChallengeLeaderboard() {
  starting.value = true
  try {
    await ensureEntertainmentGuest()
    const challenge = await getDailyChallenge(gameId.value)
    router.push(`/customer/entertainment/challenges/${challenge.id}/leaderboard`)
  } catch (e) { notifyError(e.message) } finally { starting.value = false }
}

function openHostedSetup() { hostedCredential.value = { baseUrl: hostedAi.value?.baseUrl || 'https://api.openai.com/v1', model: hostedAi.value?.model || 'gpt-5-mini', apiKey: '' }; hostedProvider.value = detectAiProvider(hostedCredential.value.baseUrl, hostedCredential.value.model); showHostedSetup.value = true }
async function saveHostedAi() { starting.value = true; try { hostedAi.value = await saveGameAiCredential(gameId.value, hostedCredential.value); hostedCredential.value.apiKey = ''; showHostedSetup.value = false; notifySuccess('固定 AI 配置已保存') } catch (e) { notifyError(e.message) } finally { starting.value = false } }
async function removeHostedAi() { if (!await confirmNotice('停用后，玩家需要使用自己的 AI 配置。确认停用？', { title: '停用固定 AI', confirmText: '确认停用' })) return; try { await deleteGameAiCredential(gameId.value); hostedAi.value = { configured: false }; notifySuccess('固定 AI 已停用') } catch (e) { notifyError(e.message) } }

async function createRoom() {
  starting.value = true
  try { await ensureEntertainmentGuest(); const room = await createGameRoom(gameId.value, { playKey: playKey.value || null }); router.push(`/customer/entertainment/rooms/${room.id}`) }
  catch (e) { notifyError(e.message) }
  finally { starting.value = false }
}
function prepareRoom() { if (detail.value.game.playKeyRequired) { pendingMode.value = 'MULTI'; showPlayKey.value = true } else createRoom() }
function confirmPlayKey() { showPlayKey.value = false; if (pendingMode.value === 'MULTI') createRoom(); else openSoloSetup() }

function continueGame() { router.push(`/customer/entertainment/sessions/${detail.value.resumableSessionId}/play`) }
async function restartSavedGame() {
  let restart = false
  starting.value = true
  try {
    await abandonGameSession(detail.value.resumableSessionId)
    detail.value.hasResumableSession = false
    detail.value.resumableSessionId = null
    showRestartSaved.value = false
    restart = true
  } catch (e) { notifyError(e.message) } finally { starting.value = false }
  if (restart) prepareSolo()
}
function returnToRoom() { router.push(`/customer/entertainment/rooms/${detail.value.activeRoomId}`) }
onMounted(load)
</script>

<style scoped>
.detail-page { max-width: 1260px; margin: 0 auto; padding: 20px 28px 64px; }
.detail-header { border-bottom: 1px solid var(--color-line); }
.detail-toolbar { min-height: 48px; display: flex; align-items: center; gap: 12px; }
.back-button { width: 40px; height: 40px; display: grid; place-items: center; border: 1px solid var(--color-line); border-radius: 6px; background: #fff; }
.detail-tags { display: flex; flex-wrap: wrap; gap: 6px; }
.detail-tags span { padding: 4px 7px; border-radius: 4px; color: #5e6b64; background: #edf1ef; font-size: 10px; }
.stock-overview { display: grid; grid-template-columns: 96px minmax(0, 1fr) 320px; gap: 20px; align-items: center; padding: 18px 0 24px; }
.stock-mark { width: 96px; height: 96px; display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 7px; border-radius: 7px; color: #235f40; background: #e6f3ea; }
.stock-mark span { max-width: 90px; font: 800 15px ui-monospace, monospace; overflow: hidden; text-overflow: ellipsis; }
.stock-title { min-width: 0; }
.stock-title h1 { min-width: 0; font-size: 27px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.stock-title > p { max-width: 650px; margin-top: 8px; color: var(--color-text-muted); font-size: 13px; line-height: 1.65; }
.creator { display: flex; align-items: center; gap: 6px; margin-top: 12px; color: #68717b; font-size: 11px; }.creator span { color: #8a938f; }.creator strong { color: #536159; }
.primary-action { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 6px; align-items: stretch; }.primary-action .main-play,.primary-action small { grid-column: 1 / -1; }.primary-action small { text-align: center; color: var(--color-text-muted); font-size: 10px; }
.restart-saved{color:#9c433e;border-color:#dfbbb7}.restart-dialog>.btn{width:100%;margin-top:8px}.restart-dialog>.btn-danger{color:#fff;background:#aa433d}
.detail-layout { display: grid; grid-template-columns: minmax(0, 1fr) 320px; gap: 24px; margin-top: 26px; align-items: start; }
.detail-layout > main { min-width: 0; }
.rule-band { display: grid; grid-template-columns: repeat(5, 1fr); border-top: 1px solid var(--color-line); border-left: 1px solid var(--color-line); background: #fff; }
.rule-band > div { min-height: 78px; display: grid; grid-template-columns: 22px auto; grid-template-rows: auto auto; align-content: center; column-gap: 8px; padding: 12px 14px; border-right: 1px solid var(--color-line); border-bottom: 1px solid var(--color-line); }
.rule-band svg { grid-row: 1 / 3; align-self: center; color: #a65d1c; }.rule-band strong { font-size: 17px; }.rule-band span { color: var(--color-text-muted); font-size: 11px; }
.market-section { margin-top: 0; }.event-section { margin-top: 28px; }.section-title { display: flex; justify-content: space-between; align-items: flex-end; margin-bottom: 11px; }.section-title > div { display: flex; flex-direction: column; gap: 2px; }.section-title h2 { font-size: 16px; }.section-title span { color: #92704e; font-size: 8px; font-weight: 700; }.section-title small { color: var(--color-text-muted); font-size: 11px; }
.market-grid { display: grid; grid-template-columns: repeat(3, 1fr); border-top: 1px solid var(--color-line); border-left: 1px solid var(--color-line); }
.market-grid > div { min-height: 74px; padding: 13px; border-right: 1px solid var(--color-line); border-bottom: 1px solid var(--color-line); background: #fff; }.market-grid dt { color: var(--color-text-muted); font-size: 10px; }.market-grid dd { margin-top: 5px; overflow-wrap: anywhere; font-weight: 700; font-size: 13px; }
.event-table { border: 1px solid var(--color-line); border-radius: 7px; overflow: hidden; background: #fff; }.event-row { display: grid; grid-template-columns: minmax(180px, 1fr) 65px 65px 65px 120px; align-items: center; min-height: 52px; padding: 7px 12px; border-bottom: 1px solid var(--color-line); font-size: 12px; }.event-row:last-child { border-bottom: 0; }.event-head { min-height: 38px; color: var(--color-text-muted); background: #f7f8f9; font-weight: 600; }.event-row > span:first-child { display: flex; flex-direction: column; }.event-row small { color: var(--color-text-muted); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.empty-events { padding: 30px; text-align: center; color: var(--color-text-muted); border: 1px solid var(--color-line); background: #fff; }
.leaderboard-panel { position: sticky; top: 76px; overflow: hidden; border: 1px solid var(--color-line); border-radius: 8px; background: #fff; }.leaderboard-panel > header { min-height: 54px; display: flex; align-items: center; justify-content: space-between; padding: 12px 14px; border-bottom: 1px solid var(--color-line); }.leaderboard-panel header div { display: flex; align-items: center; gap: 7px; }.leaderboard-panel h2 { font-size: 15px; }.leaderboard-panel header svg { color: #a36a16; }.leaderboard-panel header > span { color: var(--color-text-muted); font-size: 11px; }
.leaderboard-panel ol { list-style: none; padding: 5px 0; }.leaderboard-panel li { min-height: 48px; display: grid; grid-template-columns: 30px minmax(0, 1fr) auto; align-items: center; padding: 6px 14px; font-size: 12px; }.leaderboard-panel li.mine { background: #fff5e8; }.rank { color: var(--color-text-muted); font-weight: 700; }.player-name { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }.rank-value { display: flex; flex-direction: column; align-items: flex-end; gap: 2px; }.rank-value strong { font-size: 13px; }.rank-value small { color: var(--color-text-muted); font-size: 10px; }.positive { color: #1c8050; }.negative { color: #c04f47; }.empty-ranking { min-height: 180px; display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 8px; color: var(--color-text-muted); font-size: 12px; }
.risk-note { display: flex; align-items: center; justify-content: center; gap: 6px; margin-top: 32px; color: #7c664d; font-size: 11px; }
.modal-mask{position:fixed;inset:0;z-index:500;display:grid;place-items:center;padding:18px;background:rgba(31,37,34,.5);backdrop-filter:blur(3px)}.ai-dialog{width:min(460px,100%);padding:20px;border-radius:8px;background:#fff}.ai-dialog header{display:flex;align-items:center;justify-content:space-between}.ai-dialog header div{display:flex;align-items:center;gap:8px}.ai-dialog h2{font-size:18px}.ai-dialog header button{width:36px;height:36px;display:grid;place-items:center;background:transparent}.ai-dialog>p{margin:10px 0 16px;color:var(--color-text-muted);font-size:12px;line-height:1.7}.ai-dialog label{display:flex;flex-direction:column;gap:5px;margin-top:10px}.ai-dialog label span{font-size:12px;font-weight:600}.ai-dialog input,.ai-dialog select{height:42px;padding:0 11px;border:1px solid var(--color-line);border-radius:6px;background:#fff}.ai-dialog>.btn{width:100%;margin-top:18px}
.default-ai-card{display:grid;grid-template-columns:1fr auto;gap:6px;padding:7px;border:1px solid var(--color-line);border-radius:7px;background:#f7f9f8}.default-ai-card.active{border-color:#78a98e;background:#edf7f1}.default-ai-card>button:first-child{min-width:0;display:flex;align-items:center;gap:9px;padding:5px;color:#326047;background:transparent;text-align:left}.default-ai-card span{min-width:0;display:flex;flex-direction:column}.default-ai-card small{overflow:hidden;text-overflow:ellipsis;white-space:nowrap;color:var(--color-text-muted);font-size:10px}.edit-default{padding:0 9px;color:#53645b;background:transparent;font-size:11px}.ai-dialog .save-default{display:flex;flex-direction:row;align-items:center;gap:7px}.ai-dialog .save-default input{width:17px;height:17px;padding:0}.ai-dialog .save-default span{font-weight:500}
.ai-dialog .use-ai-toggle{display:flex;flex-direction:row;align-items:center;gap:9px;margin:0 0 13px;padding:11px;border:1px solid #bfd4c7;border-radius:6px;background:#f0f7f3}.ai-dialog .use-ai-toggle input{width:18px;height:18px;padding:0}.hosted-ready{display:flex;align-items:center;gap:8px;padding:11px;color:#276b49;background:#edf7f1;font-size:12px}
.own-ai-choice{width:100%;margin-top:8px;padding:9px;border:1px solid var(--color-line);border-radius:6px;color:#53645b;background:#fff}.own-ai-choice.active{color:#286847;border-color:#70a486;background:#edf7f1}
@media (max-width: 900px) { .detail-layout { grid-template-columns: 1fr; }.leaderboard-panel { position: static; }.stock-overview { grid-template-columns: 82px minmax(0,1fr); }.stock-mark { width: 82px; height: 82px; }.primary-action { grid-column: 1 / -1; grid-template-columns:repeat(3,minmax(0,1fr)); }.primary-action .main-play{grid-column:auto}.primary-action small { grid-column: 1 / -1; }.rule-band{grid-template-columns:repeat(3,1fr)} }
@media (max-width: 768px) { .detail-page { padding: 12px 12px 90px; }.detail-toolbar{min-height:44px}.stock-overview { grid-template-columns:64px minmax(0,1fr);gap:12px;padding:14px 0 18px}.stock-mark { width: 64px; height: 64px; }.stock-mark span{max-width:58px;font-size:11px}.stock-title h1 { font-size: 21px; }.stock-title>p{font-size:12px}.creator{margin-top:8px}.primary-action{grid-template-columns:repeat(2,minmax(0,1fr))}.primary-action .main-play{grid-column:1/-1}.rule-band { grid-template-columns: repeat(2, 1fr); }.rule-band>div{min-height:68px;padding:10px}.rule-band>div:last-child{grid-column:1/-1}.market-grid { grid-template-columns: repeat(2, 1fr); }.event-table { overflow-x: auto; }.event-row { min-width: 620px; } }
.hosted-ai-panel{display:flex;align-items:center;justify-content:space-between;gap:12px;margin-top:18px;padding:14px 16px;border:1px solid #b9d2c3;border-radius:7px;background:#f0f7f3}.hosted-ai-panel>div{display:flex;align-items:center;gap:9px}.hosted-ai-panel>div:first-child{color:#286d4a}.hosted-ai-panel span{display:flex;flex-direction:column}.hosted-ai-panel small{color:var(--color-text-muted);font-size:10px}.hosted-ai-panel>div:last-child{gap:6px}.modal-mask,.ai-dialog{animation:modal-fade .22s ease-out}.ai-dialog{animation-name:dialog-rise}@keyframes modal-fade{from{opacity:0}}@keyframes dialog-rise{from{opacity:0;transform:translateY(18px) scale(.98)}}
@media(max-width:600px){.hosted-ai-panel{align-items:flex-start;flex-direction:column}.hosted-ai-panel>div:last-child{width:100%;justify-content:flex-end}}
</style>
