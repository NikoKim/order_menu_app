<template>
  <div class="room-page">
    <LoadingSpinner v-if="loading" text="正在进入房间" />
    <EmptyState v-else-if="!room" icon="warning" :title="error || '房间不存在'" action-text="返回娱乐" @action="$router.push('/customer/entertainment')" />
    <template v-else>
      <header class="room-header">
        <button class="icon-button" title="返回游戏，保留房间" @click="backToGame"><ArrowLeft :size="20" /></button>
        <div><span>{{ room.game.stockCode }}</span><h1>{{ room.game.title }}</h1></div>
        <span class="room-status">{{ room.status === 'LOBBY' ? '等待开始' : '游戏中' }}</span>
      </header>

      <main class="room-layout">
        <section class="invite-panel">
          <div class="invite-copy"><span>房间邀请码</span><strong>{{ room.inviteCode }}</strong><button class="btn btn-outline" @click="copyCode"><Copy :size="16" />{{ copied ? '已复制' : '复制' }}</button></div>
          <p>其他玩家在娱乐首页输入邀请码即可加入。开局后不再接受新玩家。</p>
        </section>

        <section class="members-panel">
          <header><div><Users :size="18" /><h2>玩家 {{ room.members.length }} / {{ room.maxPlayers }}</h2></div><span>至少 2 人</span></header>
          <div class="member-list">
            <article v-for="member in room.members" :key="member.id" class="member-row">
              <div class="avatar"><UserRound :size="20" /></div>
              <div><strong>{{ member.displayName }}</strong><span>{{ member.role === 'HOST' ? '房主' : '玩家' }}<template v-if="member.currentPlayer"> · 我</template></span></div>
              <span v-if="room.aiEnabled && member.role === 'HOST'" :class="['credential-state', { ready: member.credentialReady || room.hostedAiConfigured }]">{{ member.credentialReady || room.hostedAiConfigured ? 'AI 可用' : 'AI 可选' }}</span>
              <button v-if="room.host && !member.currentPlayer && canRemove(member)" class="remove-button" title="移除断线玩家" @click="removeMember(member.id)"><UserMinus :size="17" /></button>
            </article>
          </div>
        </section>

        <section v-if="room.aiEnabled" class="ai-panel">
          <header><div><Bot :size="18" /><h2>本局 AI 对手</h2></div><span>{{ room.host ? '可选，仅由房主配置' : '由房主决定是否加入' }}</span></header>
          <div v-if="room.host" class="ai-choice"><label><span><strong>加入 AI 竞争</strong><small>关闭后无需 API Key，仅保留真人玩家</small></span><input v-model="useAi" type="checkbox" /></label></div>
          <button v-if="room.host && useAi && room.hostedAiConfigured" type="button" class="hosted-choice" :class="{ active: aiSource === 'HOSTED' }" @click="aiSource = 'HOSTED'"><KeyRound :size="17" /><span><strong>使用创建者配置</strong><small>无需再填写 API Key</small></span></button>
          <div v-if="room.host && useAi && defaultAi?.configured" class="default-choice" :class="{ active: aiSource === 'DEFAULT' }">
            <button type="button" @click="aiSource = 'DEFAULT'"><KeyRound :size="16" /><span><strong>使用我的默认配置</strong><small>{{ defaultAi.model }} · {{ defaultAi.baseUrl }}</small></span></button>
            <button type="button" @click="editDefault">修改</button>
          </div>
          <button v-if="room.host && useAi" type="button" class="own-ai" :class="{ active: aiSource === 'OWN' }" @click="aiSource = 'OWN'">使用本房间自己的 AI Key</button>
          <div v-if="room.host && useAi && aiSource === 'OWN'" class="ai-fields">
            <label><span>模型服务</span><CustomSelect v-model="aiProvider" :options="providerOptions" @change="selectAiProvider" /></label>
            <label v-if="aiProvider === 'custom'"><span>接口地址</span><input v-model.trim="credential.baseUrl" placeholder="https://example.com/v1" autocomplete="off" /></label>
            <label><span>模型</span><input v-model.trim="credential.model" placeholder="gpt-5-mini" autocomplete="off" /></label>
            <label><span>API Key</span><input v-model="credential.apiKey" type="password" placeholder="仅加密保存到本局" autocomplete="new-password" /></label>
            <div v-if="loggedIn" class="save-default"><span>账号默认</span><label><input v-model="saveAsDefault" type="checkbox" />{{ defaultAi?.configured ? '同时更新' : '同时保存' }}</label></div>
            <button class="btn btn-outline" :disabled="submitting" @click="saveCredential"><KeyRound :size="16" />保存房主配置</button>
          </div>
          <p v-else-if="!room.host" class="ai-wait">{{ hostCredentialReady ? '房主已准备 AI，可在开局前决定是否加入' : '本局可以不配置 AI，等待房主开始' }}</p>
          <div v-if="room.host && useAi" class="room-connection-check"><AiConnectionCheck :source="aiSource === 'OWN' && !credential.apiKey && me?.credentialReady ? 'ROOM' : aiSource" :room-id="roomId" :game-id="room.gameId" :credential="credential" :disabled="submitting" /></div>
        </section>

        <div class="room-actions">
          <button class="btn btn-ghost" @click="leave">离开房间</button>
          <button v-if="room.host" class="btn close-room-command" @click="showCloseRoom = true"><DoorClosed :size="17" />关闭房间</button>
          <button v-if="room.host" class="btn btn-primary btn-lg" :disabled="!canStart || submitting" @click="start"><Play :size="18" />开始游戏</button>
          <span v-else>等待房主开始游戏</span>
        </div>
      </main>
      <div v-if="showCloseRoom" class="close-room-confirm" @click.self="showCloseRoom = false">
        <div><DoorClosed :size="28" /><h2>关闭多人房间？</h2><p>房间和正在进行的多人场次会结束，其他玩家将返回游戏主界面；单人进度不受影响。</p><button class="btn close-room-command" :disabled="submitting" @click="confirmCloseRoom">确认关闭房间</button><button class="btn btn-ghost" @click="showCloseRoom = false">取消</button></div>
      </div>
    </template>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowLeft, Bot, Copy, DoorClosed, KeyRound, Play, UserMinus, UserRound, Users } from 'lucide-vue-next'
import { closeGameRoom, configureRoomAi, ensureEntertainmentGuest, getDefaultAiCredential, getGameRoom, leaveGameRoom, removeGameRoomMember, saveDefaultAiCredential, startGameRoom } from '../../api/entertainment'
import EmptyState from '../../components/EmptyState.vue'
import LoadingSpinner from '../../components/LoadingSpinner.vue'
import CustomSelect from '../../components/CustomSelect.vue'
import AiConnectionCheck from '../../components/AiConnectionCheck.vue'
import { AI_PROVIDERS, applyAiProvider, detectAiProvider } from '../../constants/aiProviders'
import { notifyError, notifySuccess } from '../../services/notice'

const route = useRoute(); const router = useRouter(); const room = ref(null); const loading = ref(true); const submitting = ref(false); const error = ref(''); const copied = ref(false); const showCloseRoom = ref(false)
const credential = reactive({ baseUrl: 'https://api.openai.com/v1', model: 'gpt-5-mini', apiKey: '' })
const aiProvider = ref('openai')
const useAi = ref(false); const useDefaultAi = ref(false); const saveAsDefault = ref(false); const defaultAi = ref(null); let initializedAiChoice = false
const aiSource = ref('OWN'); const providerOptions = AI_PROVIDERS.map(item => ({ value: item.id, label: item.name }))
const roomId = computed(() => Number(route.params.roomId))
const me = computed(() => room.value?.members.find(member => member.currentPlayer))
const loggedIn = computed(() => Boolean(localStorage.getItem('token')))
const hostCredentialReady = computed(() => room.value?.hostedAiConfigured || room.value?.members.find(member => member.role === 'HOST')?.credentialReady || false)
const canStart = computed(() => room.value?.members.length >= 2 && (!useAi.value || (aiSource.value === 'HOSTED' && room.value?.hostedAiConfigured) || (aiSource.value === 'DEFAULT' && defaultAi.value?.configured) || (aiSource.value === 'OWN' && room.value?.members.find(member => member.role === 'HOST')?.credentialReady)))
let poller

function apply(data) { room.value = data; if (data.status === 'CLOSED') { router.replace(`/customer/entertainment/games/${data.gameId}`); return } if (!initializedAiChoice && data.host) { useAi.value = false; aiSource.value = data.hostedAiConfigured ? 'HOSTED' : defaultAi.value?.configured ? 'DEFAULT' : 'OWN'; initializedAiChoice = true } if (data.sessionId) router.replace(`/customer/entertainment/sessions/${data.sessionId}/play`) }
async function load() { try { await ensureEntertainmentGuest(); if (loggedIn.value && defaultAi.value == null) { try { defaultAi.value = await getDefaultAiCredential() } catch {} } apply(await getGameRoom(roomId.value)); error.value = '' } catch (e) { error.value = e.message } finally { loading.value = false } }
async function saveCredential() { submitting.value = true; try { if (saveAsDefault.value && loggedIn.value) defaultAi.value = await saveDefaultAiCredential(credential); apply(await configureRoomAi(roomId.value, { useDefault: false, aiCredential: credential })); credential.apiKey = ''; notifySuccess('房间 AI 配置已保存') } catch (e) { notifyError(e.message) } finally { submitting.value = false } }
function selectAiProvider() { applyAiProvider(aiProvider.value, credential) }
function editDefault() { aiSource.value = 'OWN'; saveAsDefault.value = true; credential.baseUrl = defaultAi.value.baseUrl; credential.model = defaultAi.value.model; credential.apiKey = ''; aiProvider.value = detectAiProvider(defaultAi.value.baseUrl, defaultAi.value.model) }
async function start() { submitting.value = true; try { const session = await startGameRoom(roomId.value, { useAi: useAi.value, useDefaultAi: useAi.value && aiSource.value === 'DEFAULT' }); router.replace(`/customer/entertainment/sessions/${session.id}/play`) } catch (e) { notifyError(e.message) } finally { submitting.value = false } }
async function leave() { submitting.value = true; try { await leaveGameRoom(roomId.value); router.replace(`/customer/entertainment/games/${room.value.gameId}`) } catch (e) { notifyError(e.message) } finally { submitting.value = false } }
function backToGame() { router.push(`/customer/entertainment/games/${room.value.gameId}`) }
async function confirmCloseRoom() { submitting.value = true; try { await closeGameRoom(roomId.value); showCloseRoom.value = false; router.replace(`/customer/entertainment/games/${room.value.gameId}`) } catch (e) { notifyError(e.message) } finally { submitting.value = false } }
async function removeMember(id) { try { apply(await removeGameRoomMember(roomId.value, id)); notifySuccess('玩家已移出房间') } catch (e) { notifyError(e.message) } }
function canRemove(member) { return Date.now() - new Date(member.lastSeenAt).getTime() >= 60000 }
async function copyCode() { try { await navigator.clipboard.writeText(room.value.inviteCode); copied.value = true; window.setTimeout(() => { copied.value = false }, 1200) } catch { notifyError('邀请码复制失败，请检查浏览器剪贴板权限') } }
onMounted(() => { load(); poller = window.setInterval(load, 1500) })
onBeforeUnmount(() => window.clearInterval(poller))
</script>

<style scoped>
.room-page{max-width:900px;margin:0 auto;padding:28px 24px 70px}.room-header{display:flex;align-items:center;gap:14px;margin-bottom:18px}.room-header>div{min-width:0;display:flex;align-items:baseline;gap:9px}.room-header h1{font-size:22px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}.room-header>div>span{color:#26744d;font:800 12px ui-monospace,monospace}.room-status{margin-left:auto;padding:5px 8px;border-radius:4px;background:#fff1df;color:#995213;font-size:12px}.icon-button{width:40px;height:40px;display:grid;place-items:center;border:1px solid var(--color-line);border-radius:6px;background:#fff}.room-layout{display:flex;flex-direction:column;gap:14px}.invite-panel,.members-panel,.ai-panel{border:1px solid var(--color-line);border-radius:8px;background:#fff}.invite-panel{padding:22px}.invite-copy{display:flex;align-items:center;gap:12px}.invite-copy>span{color:var(--color-text-muted);font-size:12px}.invite-copy strong{font:800 28px ui-monospace,monospace;letter-spacing:2px}.invite-copy button{margin-left:auto}.invite-panel p{margin-top:8px;color:var(--color-text-muted);font-size:12px}.members-panel>header,.ai-panel>header{min-height:52px;display:flex;align-items:center;justify-content:space-between;padding:12px 16px;border-bottom:1px solid var(--color-line)}.members-panel header div,.ai-panel header div{display:flex;align-items:center;gap:7px}.members-panel h2,.ai-panel h2{font-size:15px}.members-panel header>span,.ai-panel header>span{color:var(--color-text-muted);font-size:11px}.member-row{min-height:62px;display:flex;align-items:center;gap:11px;padding:9px 16px;border-bottom:1px solid var(--color-line)}.member-row:last-child{border:0}.avatar{width:38px;height:38px;display:grid;place-items:center;border-radius:50%;background:#edf5f0;color:#28734d}.member-row>div:nth-child(2){display:flex;flex-direction:column}.member-row strong{font-size:13px}.member-row span{color:var(--color-text-muted);font-size:10px}.credential-state{margin-left:auto;padding:4px 7px;border-radius:4px;background:#f1f2f3}.credential-state.ready{color:#217148;background:#e8f4ec}.remove-button{width:34px;height:34px;display:grid;place-items:center;color:#b84a43;background:transparent}.ai-fields{display:grid;grid-template-columns:1.1fr .8fr 1fr auto;gap:10px;padding:16px}.ai-fields label{display:flex;flex-direction:column;gap:5px}.ai-fields label span{color:var(--color-text-muted);font-size:11px}.ai-fields input,.ai-fields select{height:40px;padding:0 10px;border:1px solid var(--color-line);border-radius:6px;background:#fff}.ai-fields button{align-self:end;white-space:nowrap}.room-actions{display:flex;align-items:center;justify-content:flex-end;gap:12px}.room-actions>span{color:var(--color-text-muted);font-size:12px}.room-error{padding:10px 12px;color:var(--color-error);background:#fff0ee;border:1px solid #edc4bf;font-size:12px}
.ai-choice{padding:13px 16px;border-bottom:1px solid var(--color-line)}.ai-choice>label{display:flex;align-items:center;justify-content:space-between;gap:16px}.ai-choice span{display:flex;flex-direction:column}.ai-choice small,.ai-wait{color:var(--color-text-muted);font-size:10px}.ai-choice input{width:19px;height:19px;accent-color:#287a50}.default-choice{display:grid;grid-template-columns:1fr auto;gap:6px;margin:12px 16px 0;padding:7px;border:1px solid var(--color-line);border-radius:7px;background:#f7f9f8}.default-choice.active{border-color:#78a98e;background:#edf7f1}.default-choice button{background:transparent}.default-choice>button:first-child{min-width:0;display:flex;align-items:center;gap:8px;text-align:left}.default-choice span{min-width:0;display:flex;flex-direction:column}.default-choice small{overflow:hidden;text-overflow:ellipsis;white-space:nowrap;color:var(--color-text-muted);font-size:9px}.save-default{display:flex;flex-direction:column;gap:5px}.save-default>span{color:var(--color-text-muted);font-size:11px}.save-default label{height:40px;display:flex;align-items:center;gap:6px;font-size:10px}.save-default input{width:16px;height:16px}.ai-wait{padding:16px}
  @media(max-width:768px){.room-page{padding:14px 12px 90px}.room-header{margin-top:2px}.invite-copy{flex-wrap:wrap}.invite-copy strong{width:100%;font-size:32px}.invite-copy button{margin-left:0}.ai-fields{grid-template-columns:1fr}.room-actions{position:sticky;bottom:72px;padding:10px;background:rgba(255,255,255,.96);border:1px solid var(--color-line);border-radius:8px}.room-actions .btn-primary{flex:1}.credential-state{font-size:9px!important}}
.hosted-choice{width:calc(100% - 32px);display:flex;align-items:center;gap:9px;margin:12px 16px;padding:11px;border:1px solid #8bb79b;border-radius:7px;color:#236a47;background:#edf7f1;text-align:left}.hosted-choice.active{box-shadow:0 0 0 2px rgba(56,125,84,.13)}.hosted-choice span{display:flex;flex-direction:column}.hosted-choice small{color:#648072;font-size:10px}.own-ai{margin:10px 16px 0;padding:8px 10px;border:1px solid var(--color-line);border-radius:5px;color:#53645b;background:#fff}.own-ai.active{color:#286847;border-color:#70a486;background:#edf7f1}
.close-room-command{display:inline-flex;align-items:center;justify-content:center;gap:6px;color:#a13f39;border:1px solid #dfaaa5;background:#fff}.close-room-confirm{position:fixed;z-index:1200;inset:0;display:grid;place-items:center;padding:18px;background:rgba(30,39,34,.58)}.close-room-confirm>div{width:min(390px,100%);display:flex;flex-direction:column;align-items:center;padding:24px;border-radius:8px;background:#fff;text-align:center;box-shadow:0 20px 60px rgba(20,30,25,.3)}.close-room-confirm svg{color:#a13f39}.close-room-confirm h2{margin-top:9px;font-size:18px}.close-room-confirm p{margin:8px 0 14px;color:var(--color-text-muted);font-size:11px;line-height:1.65}.close-room-confirm .btn{width:100%;margin-top:7px}
.room-connection-check{padding:0 16px 16px}
</style>
