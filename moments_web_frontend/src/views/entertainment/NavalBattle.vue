<template>
  <div ref="root" class="naval-page" :style="{ '--naval-harbor': `url(${harborAsset})` }" @pointerdown.capture="unlockNavalAudio().catch(() => {})" :class="{ 'military-rules': isMilitary, 'community-open': communityPanel || !mobile && mobilePanel === 'audience', 'is-mobile': mobile, 'in-game': room, 'is-fullscreen': fullscreen, 'is-preparing': preparing, 'mobile-game': mobile && room, 'mobile-waiting': mobile && room?.phase === 'WAITING', 'mobile-preparing': mobilePrep, [`mobile-panel-${mobilePanel}`]: mobile && mobilePanel }">
    <NavalReplay v-if="replay" :detail="replay" :blocked="blocked" :lobby-unread="lobbyUnread" @close="closeReplay" @chat="openCommunity('chat')" />
    <div v-show="!replay" class="naval-primary-content">
    <header v-if="room?.phase === 'WAITING'" class="naval-header waiting-toolbar">
      <nav class="header-actions" aria-label="房间操作">
        <NavalCommunityActions :medals="!room" :active="communityPanel" :lobby="lobbyChatAvailable" :unread="lobbyUnread" @select="openCommunity" />
        <button class="icon-control chat-toggle" title="战局聊天" aria-label="战局聊天" :aria-pressed="chatOpen" @click="toggleChat"><MessageSquare :size="19" /><i v-if="unreadChat">{{ unreadChat > 9 ? '9+' : unreadChat }}</i></button>
        <button class="icon-control" title="对局菜单" aria-label="对局菜单" :aria-expanded="mobilePanel === 'more'" @click="toggleMobilePanel('more')"><Ellipsis :size="21" /></button>
        <button class="icon-control" title="观众席" aria-label="展开观众席" :aria-expanded="mobilePanel === 'audience'" @click="toggleMobilePanel('audience')"><Users :size="19" /></button>
      </nav>
    </header>
    <header v-else-if="!mobile || !room" class="naval-header">
      <div class="naval-title"><Anchor :size="24" /><div><h1>海战棋</h1><span>{{ isMilitary ? '军事模式' : '经典模式' }}</span></div></div>
      <div v-if="room" class="match-status" aria-live="polite">
        <span v-if="room.phase === 'WAITING'" class="waiting-dot" />
        <strong>{{ statusText }}</strong>
        <span v-if="!tutorialActive && (preparing || mobile && room.phase === 'BATTLE')" class="clock" :class="{ urgent: seconds <= 10 }"><Timer :size="15" />{{ clockText }}</span>
      </div>
      <nav class="header-actions">
        <NavalCommunityActions :medals="!room" :active="communityPanel" :lobby="lobbyChatAvailable" :unread="lobbyUnread" @select="openCommunity" />
        <button v-if="mobile && room" class="icon-control mobile-more-toggle" title="对局菜单" aria-label="对局菜单" :aria-expanded="mobilePanel === 'more'" @click="toggleMobilePanel('more')"><Ellipsis :size="21" /></button>
        <span v-if="connectionError" class="connection-warning" role="status">{{ connectionError }}</span>
        <button v-if="room?.mode === 'MULTIPLAYER' && !tutorialActive" class="icon-control chat-toggle" title="战局聊天" aria-label="战局聊天" :aria-pressed="chatOpen" @click="toggleChat"><MessageSquare :size="19" /><i v-if="unreadChat">{{ unreadChat > 9 ? '9+' : unreadChat }}</i></button>
        <button v-if="room && !tutorialActive" class="icon-control" title="观众席" aria-label="展开观众席" :aria-expanded="mobilePanel === 'audience'" @click="toggleMobilePanel('audience')"><Users :size="19" /></button>
        <button v-if="room?.mode === 'MULTIPLAYER'" class="icon-control" title="分享房间" aria-label="分享房间" @click="showShare = true"><Share2 :size="18" /></button>
        <button v-if="room?.phase === 'FINISHED'" class="icon-control" title="战局结算" aria-label="战局结算" @click="showSettlement = true"><Trophy :size="18" /></button>
        <button class="icon-control" :title="muted ? '开启音效' : '关闭音效'" :aria-label="muted ? '开启音效' : '关闭音效'" @click="toggleSound"><VolumeX v-if="muted" :size="19" /><Volume2 v-else :size="19" /></button>
        <button class="icon-control" title="对局规则" aria-label="对局规则" @click="showRules = true"><CircleHelp :size="19" /></button>
        <button class="icon-control" title="全屏" aria-label="全屏" @click="enterFullscreen"><Maximize :size="19" /></button>
        <button v-if="room" class="icon-control" title="离开对局" aria-label="离开对局" @click="requestLeave"><LogOut :size="19" /></button>
        <router-link v-else-if="!room && !mobile" class="icon-control" title="返回娱乐" aria-label="返回娱乐" to="/customer/entertainment"><ArrowLeft :size="19" /></router-link>
      </nav>
    </header>

    <template v-if="!room">
      <section class="naval-lobby">
        <nav class="naval-rule-switch" aria-label="海战规则模式"><button type="button" :aria-pressed="!isMilitary" @click="ruleSet='CLASSIC'">经典模式</button><button type="button" :aria-pressed="isMilitary" @click="ruleSet='MILITARY'">军事模式</button></nav>
        <div class="lobby-sea"><img :src="harborAsset" alt="像素舰队港口" @error="useFleetBackdrop" /><span class="sea-label">FLEET COMMAND / 12 × 12</span></div>
        <div class="lobby-heading"><div class="lobby-profile"><NavalAvatar v-if="loggedIn" :person="accountPerson" :size="36" /><div><h2>{{ loggedIn ? accountPerson.name : '选择出航方式' }}</h2><p v-if="loggedIn">账号档案 · 每局 300 金币</p><p v-else class="guest-storage">阵型和战绩仅存储在本地，更换设备或浏览器无法同步；当前对局会临时同步用于观战。推荐<router-link :to="authTarget('login')">登录</router-link>或<router-link :to="authTarget('register')">注册</router-link>。登录用户的战绩还会计入<button type="button" class="guest-ranking-button" aria-label="查看排行榜" @click="openCommunity('ranking')">排行榜</button>。</p></div></div><span class="record-summary">{{ history.length }} 场战绩 · {{ history.filter(h => h.won).length }} 场胜利</span></div>
        <div class="naval-tutorial-entry"><GraduationCap :size="24" /><div><h3>{{ mobile ? '海战棋手机教程' : '海战棋电脑教程' }}</h3><span>{{ tutorialSaved?.completed ? '已完成演习' : tutorialSaved ? '演习进度已保留' : '舰队训练场' }}</span></div><button class="naval-secondary" :disabled="busy" @click="startTutorial()"><Play :size="16" />{{ tutorialSaved && !tutorialSaved.completed ? '继续演习' : '开始教程' }}</button></div>
        <div class="lobby-modes">
          <section class="mode-entry"><Bot :size="28" /><h3>单人对战</h3><p>对手：舰队 AI</p><button class="naval-primary" :disabled="busy" @click="start('AI')"><Play :size="17" />开始对战</button></section>
          <section class="mode-entry"><Users :size="28" /><h3>双人海战</h3><div class="online-count" role="status"><span class="online-dot" />{{ onlineCount == null ? '在线人数暂不可用' : `${onlineCount} 人在线` }}</div><label class="match-toggle"><input v-model="autoMatch" type="checkbox" role="switch" /><span>自动匹配</span></label><button class="naval-primary" :disabled="busy" @click="start('MULTIPLAYER')"><Radar :size="17" />{{ autoMatch ? '匹配并创建房间' : '创建邀请房间' }}</button></section>
          <form class="mode-entry" @submit.prevent="join()"><Ticket :size="28" /><h3>邀请码加入</h3><input v-model="inviteCode" class="invite-input" aria-label="海战棋邀请码" placeholder="房间邀请码" maxlength="6" @input="inviteCode = inviteCode.toUpperCase().replace(/[^A-Z0-9]/g, '')" /><button class="naval-secondary" :disabled="busy || inviteCode.length !== 6"><LogIn :size="17" />加入房间</button></form>
        </div>
        <div v-if="resumeId || localResume" class="resume-line"><span><Save :size="17" />有尚未结束的对局</span><button class="naval-secondary" :disabled="busy" @click="resume()"><RotateCcw :size="16" />继续对局</button><button class="icon-control" title="退出之前的对局" aria-label="退出之前的对局" :disabled="busy" @click="requestResumeDecision()"><X :size="17" /></button></div>
      </section>
    </template>

    <template v-else>
      <div v-if="spectating && !mobile" class="spectator-toolbar"><span><Eye :size="16" />观众席</span><CustomSelect :model-value="room.viewMode || 'BOTH'" :options="spectatorOptions" :disabled="busy" :teleport-to="root || 'body'" aria-label="观战视角" @update:model-value="changeSpectatorView" /></div>
      <Transition name="stage-scene" mode="out-in">
      <div :key="sceneKey" class="battle-layout" :class="{ preparation: preparing && !spectating, placement: placing, supply: shopping, 'room-waiting': room.phase === 'WAITING' }">
        <section class="battlefield">
          <div v-if="room.phase === 'WAITING'" class="waiting-dock"><img :src="harborAsset" alt="等待出航的舰队" @error="useFleetBackdrop" /><div class="matching-radar"><Radar :size="40" /><span>{{ room.autoMatch ? '正在搜索海域信号' : '等待舰队集结' }}</span><strong>{{ room.inviteCode }}</strong><small>{{ onlineCount == null ? '在线人数暂不可用' : `${onlineCount} 位指挥官在线` }}</small></div></div>
          <div v-if="room.phase !== 'WAITING'" class="boards" :class="{ 'single-board': preparing && !spectating }">
            <section class="board-panel own-panel"><div v-if="room.phase === 'BATTLE'" class="board-turn" :class="{ active: activeTurn === selfIndex }"><Crosshair :size="20" /><strong>{{ spectating ? `${me?.name || '左方'}进攻` : '己方进攻' }}</strong><span v-if="activeTurn === selfIndex"><Timer :size="13" />{{ clockText }}</span></div><header :class="{ 'mobile-turn-active': room.phase === 'BATTLE' && activeTurn === selfIndex }"><h2><NavalAvatar :person="me" :size="16" /><span class="player-name" :title="me?.name">{{ me?.name || '己方舰队' }}</span><Transition name="chat-pop"><span v-if="chatPops[selfIndex]" :key="chatPops[selfIndex].id" class="player-chat-bubble" :class="{ 'sticker-pop': messageSticker(chatPops[selfIndex]?.text) }" :title="messageSticker(chatPops[selfIndex]?.text)?.name || chatPops[selfIndex]?.text"><NavalMessage :text="chatPops[selfIndex].text" /></span></Transition></h2><span>{{ placing ? `已布置 ${isMilitary ? draft.ships.filter(s=>s.x>=0).length : draft.ships.length}/${isMilitary ? draft.ships.length : 10}` : waitingReady ? '战备完成' : `舰船 ${remainingShips(ownBoard)}/${fleetTotal(ownBoard)}` }}</span></header>
              <NavalMobilePlayerStatus v-if="mobile && !mobilePrep" side="left" :coins="me?.coins" :person="me" :active="room.phase === 'BATTLE' && activeTurn === selfIndex" :clock="clockText" :seconds="seconds" :total="fleetTotal(ownBoard)" :remaining="remainingShips(ownBoard)" :preparing="preparing" :stage="playerStage" :tutorial="tutorialActive" :message="chatPops[selfIndex]">
                          <template #actions><span v-if="connectionError" class="rail-connection-warning" role="status" :title="connectionError"><AlertCircle :size="16" /></span><button v-if="!tutorialActive && room.mode === 'MULTIPLAYER'" class="mobile-rail-button chat-toggle" title="战局聊天" aria-label="战局聊天" :aria-pressed="chatOpen" @click="toggleChat"><MessageSquare :size="19" /><i v-if="unreadChat">{{ unreadChat > 9 ? '9+' : unreadChat }}</i></button><button class="mobile-rail-button" title="对局菜单" aria-label="对局菜单" :aria-expanded="mobilePanel === 'more'" @click="toggleMobilePanel('more')"><Ellipsis :size="21" /></button></template>
                        </NavalMobilePlayerStatus>
              <NavalBoard :military="isMilitary" :military-selected="selectedModel" :military-preparing="placing" :military-event="militaryLast" :military-attacking="militaryLast?.player===selfIndex" data-tutorial="naval-own-board" :player="ownBoard" defense-side="left" :label="spectating ? `${me?.name || '左方'}海域` : '己方海域'" :interactive="canArrange || canDeployDefense || isMilitary && canAttack && militarySelectedAction?.weapon==='mine'" :preview="placementPreview" :region="isMilitary && militarySelectedAction?.weapon==='mine' && !preparing ? weaponRegion : pendingTarget?.kind === 'placement' ? { selected: [] } : shopping && placementTool === 'antiAir' ? { row: true } : null" :selected="placing ? selectedShip : ''" :event="ownEvent" :events="ownEvents" :radar="opponent?.radar || []" @animation="ownAnimating = $event" @cell="ownCell" @loading="onBoardLoading" @ready="finishLaunch" @error="fail">
                <NavalTargetConfirmation v-if="isMilitary && pendingTarget?.kind==='attack' && pendingTarget.weapon==='mine'" :point="pendingTarget.point" :shape="{w:1,h:1}" :busy="busy" @confirm="confirmTarget" @cancel="cancelTarget" /><span v-if="isMilitary && room.phase==='BATTLE' && militarySelectedAction?.weapon==='mine'" class="submarine-count">本回合布雷 {{ room.mineActions || 0 }}/2</span>
                <NavalTargetConfirmation v-else-if="pendingTarget?.kind === 'placement'" :point="pendingTarget.point" :shape="pendingTarget.shape" :rotatable="mobile && placing && pendingTarget.shape.w !== pendingTarget.shape.h" :busy="busy" @rotate="rotatePendingShip" @confirm="confirmTarget" @cancel="cancelTarget" />
                <NavalTargetConfirmation v-else-if="mobile && placing && selectedModel && selectedModel.w !== selectedModel.h" rotate-only :point="selectedModel" :shape="selectedModel" @rotate="rotateShip()" />
              </NavalBoard>
              <NavalSpectatorData v-if="spectating && !mobile" :player="me" />
            </section>
            <section v-if="!preparing || spectating" class="board-panel enemy-panel"><div v-if="room.phase === 'BATTLE'" class="board-turn" :class="{ active: activeTurn === otherIndex }"><span v-if="activeTurn === otherIndex"><Timer :size="13" />{{ clockText }}</span><strong>{{ spectating ? `${opponent?.name || '右方'}进攻` : '对方进攻' }}</strong><Crosshair :size="20" /></div><header :class="{ 'mobile-turn-active': room.phase === 'BATTLE' && activeTurn === otherIndex }"><span>{{ isMilitary ? `已击沉 ${remainingShips(opponent) === 0 ? 0 : (opponent?.ships || []).filter(s=>s.hits?.length===s.w*s.h).length} 艘` : `舰船 ${remainingShips(opponent)}/10` }}</span><h2><Transition name="chat-pop"><span v-if="chatPops[otherIndex]" :key="chatPops[otherIndex].id" class="player-chat-bubble" :class="{ 'sticker-pop': messageSticker(chatPops[otherIndex]?.text) }" :title="messageSticker(chatPops[otherIndex]?.text)?.name || chatPops[otherIndex]?.text"><NavalMessage :text="chatPops[otherIndex].text" /></span></Transition><span class="player-name" :title="opponent?.name">{{ opponent?.name || '等待对手' }}</span><NavalAvatar :person="opponent" :size="16" /></h2></header>
              <NavalMobilePlayerStatus v-if="mobile && !mobilePrep" side="right" :person="opponent" :active="room.phase === 'BATTLE' && activeTurn === otherIndex" :clock="clockText" :seconds="seconds" :total="fleetTotal(opponent)" :remaining="opponent?.occupied === false ? 0 : remainingShips(opponent)" :preparing="preparing" :stage="opponent?.stage" :tutorial="tutorialActive" :message="chatPops[otherIndex]">
                          <template #actions><nav class="mobile-battle-dock" aria-label="海战操作">
                            <button v-if="room.phase === 'BATTLE' && !spectating" data-tutorial="naval-mobile-weapons-toggle" class="mobile-dock-button weapon-dock" :class="{ active: mobilePanel === 'weapons' }" title="攻击方式" aria-label="展开攻击方式" :aria-expanded="mobilePanel === 'weapons'" @click="toggleMobilePanel('weapons')"><Crosshair v-if="weapon === 'shot'" :size="23" /><img v-else :src="weapon==='depthCharge'?'/entertainment/naval/military/depth-charge.png':`/entertainment/naval/v2/${weapon}.png`" alt="" /><small>{{ isMilitary ? '舰载功能' : weapon === 'shot' ? '舰炮' : ITEMS[weapon].name }}</small></button>
                            <button v-if="spectating" class="mobile-dock-button" title="观战视角与战备" aria-label="观战视角与战备" :aria-expanded="mobilePanel === 'detail'" @click="toggleMobilePanel('detail')"><ScanEye :size="21" /></button>
                            <button v-if="!tutorialActive" class="mobile-dock-button audience-dock" title="观众席" aria-label="展开观众席" :aria-expanded="mobilePanel === 'audience'" @click="toggleMobilePanel('audience')"><Users :size="20" /><small>{{ room.spectators?.length || 0 }}</small></button>
                            <button v-if="room.phase === 'FINISHED'" class="mobile-dock-button mobile-dock-primary" title="战局结算" aria-label="战局结算" @click="showSettlement = true"><Trophy :size="22" /></button>
                          </nav></template>
                        </NavalMobilePlayerStatus>
              <NavalBoard :military="isMilitary" :military-action="militarySelectedAction" :military-source="militarySource" :military-event="militaryLast?.player===selfIndex ? militaryLast : null" data-tutorial="naval-enemy-board" :player="opponent" defense-side="right" :hidden="hiddenOpponent" :label="spectating ? `${opponent?.name || '右方'}海域` : '对方海域'" :interactive="canAttack && (!isMilitary || militarySelectedAction?.weapon!=='mine')" :region="isMilitary && militarySelectedAction?.weapon==='mine' ? null : weaponRegion" :event="enemyEvent" :events="enemyEvents" :radar="me?.radar || room.radar || []" @animation="enemyAnimating = $event" @cell="attack" @error="fail"><NavalTargetConfirmation v-if="pendingTarget?.kind === 'attack' && pendingTarget.weapon !== 'mine'" :point="pendingTarget.point" :shape="weaponRegion" :busy="busy" @confirm="confirmTarget" @cancel="cancelTarget" /><span v-if="weapon === 'submarine'" class="submarine-count" role="status">潜艇 {{ submarineTargets.length }}/3</span></NavalBoard>
              <NavalSpectatorData v-if="spectating && !mobile && !hiddenOpponent" :player="opponent" />
            </section>
          </div>
          <div v-if="room.phase === 'WAITING'" class="waiting-line"><Radar :size="19" class="radar-spin" /><span>{{ room.autoMatch ? '寻找正在匹配的指挥官…' : '等待另一位指挥官加入' }}</span><button class="naval-secondary" @click="copyInvite"><Copy :size="15" />复制邀请码</button></div>
          <div v-if="placing && !mobile" class="placement-controls">
            <Transition name="mobile-sheet"><div v-show="!mobile || mobilePanel === 'tools'" class="formation-tools" data-mobile-panel="tools"><div v-if="mobile" class="mobile-panel-heading"><h3>阵型工具</h3><button class="icon-control" aria-label="收起阵型工具" title="收起阵型工具" @click="closeMobilePanel"><X :size="18" /></button></div><button data-tutorial="naval-clear-layout" class="icon-control" title="清空舰队布局" aria-label="清空舰队布局" :disabled="!canArrange || !draft.ships.length" @click="clearLayout"><Eraser :size="18" /></button><button data-tutorial="naval-random-layout" class="icon-control" title="随机阵型" :disabled="!canArrange" @click="randomize"><Shuffle :size="18" /></button><button class="icon-control" title="旋转选中舰船" :disabled="!canArrange || !selectedShip" @click="rotateShip()"><RotateCw :size="18" /></button><button class="icon-control" title="保存当前阵型" :disabled="!canArrange || !fullFleet || isMilitary" @click="showFormation = true"><Save :size="18" /></button>
              <CustomSelect v-model="formationId" class="formation-select" :options="formationOptions" placeholder="已有阵型" :disabled="!canArrange" :teleport-to="root || 'body'" aria-label="已有阵型" @change="loadFormation" /><button v-if="formationId" class="icon-control" title="删除选中阵型" :disabled="!canArrange" @click="deleteFormation"><Trash2 :size="16" /></button>
            </div></Transition>
            <button v-if="!mobile" data-tutorial="naval-layout-next" class="naval-primary ready-button" :disabled="busy || !canArrange || !fullFleet" @click="advanceLayout"><Check :size="18" />{{ isMilitary ? '完成战备' : '下一步：战备补给' }}</button>
          </div>
          <div v-if="shopping" class="supply-controls"><span>水雷 {{ draft.mines.length }}/{{ draft.inventory.mine || 0 }}<i />防空炮 {{ draft.aa.length }}/{{ draft.inventory.antiAir || 0 }}</span><button v-if="!mobile" data-tutorial="naval-supply-ready" class="naval-primary ready-button" :disabled="busy || !canShop" @click="ready"><Check :size="18" />完成战备</button></div>
          <div v-if="waitingReady" class="ready-wait"><ShieldCheck :size="23" /><span>战备就绪，等待对方舰队</span><LoaderCircle :size="17" class="radar-spin" /></div>
          <Transition name="mobile-sheet"><div v-show="!mobile || mobilePanel === 'weapons'" data-mobile-panel="weapons" v-if="room.phase === 'BATTLE' && !spectating" class="weapons" aria-label="攻击方式">
            <div v-if="mobile" class="mobile-panel-heading"><h3>攻击方式</h3><button class="icon-control" title="收起攻击方式" aria-label="收起攻击方式" @click="closeMobilePanel"><X :size="18" /></button></div>
            <template v-if="isMilitary"><button v-for="a in militaryAvailable" :key="militaryKey(a)" :data-tutorial="`naval-weapon-${a.weapon}-${a.kind}`" :class="{ selected: militaryKey(militarySelectedAction||{})===militaryKey(a), 'salvo-weapon': a.kind==='battleship', 'military-weapon': true }" :disabled="!canAttack || room.mineActions > 0 && a.weapon !== 'mine'" :style="{ '--weapon-text-length': [...a.name].reduce((n,c)=>n+(c.charCodeAt(0)>255?1:.6),0) }" :title="a.name" :aria-label="a.name" @click="selectMilitaryAction(a)"><Crosshair v-if="a.weapon==='shot'" :size="21" /><img v-else :src="a.weapon==='depthCharge'?'/entertainment/naval/military/depth-charge.png':`/entertainment/naval/v2/${a.weapon}.png`" alt="" /><span>{{ a.name }}</span></button></template><template v-else>
            <button data-tutorial="naval-weapon-shot" :class="{ selected: weapon === 'shot' }" :disabled="!canAttack" title="舰炮：单格攻击" aria-label="舰炮" @click="selectWeapon('shot')"><Crosshair :size="21" /><span>舰炮</span><small>∞</small></button>
            <button v-for="key in attackItems" :key="key" :data-tutorial="`naval-weapon-${key}`" :class="{ selected: weapon === key }" :disabled="!canAttack || !me?.inventory?.[key]" :aria-label="ITEMS[key].name" :title="`${ITEMS[key].name}：${itemDetails[key]}`" @click="selectWeapon(key)"><img :src="`/entertainment/naval/v2/${key}.png`" alt="" /><span>{{ ITEMS[key].name }}</span><small>{{ me?.inventory?.[key] || 0 }}</small></button>
          </template></div></Transition>
          <div v-if="room.phase === 'FINISHED'" class="result-line"><Trophy :size="22" /><span>本局行动结束</span><button class="naval-secondary" @click="showSettlement = true">查看结算</button></div>

          <div v-if="!tutorialActive && room.phase === 'BATTLE'" class="battle-feed" aria-live="polite"><span v-for="event in (room.events || []).slice(-1)" :key="event.id">{{ event.text }}</span></div>
        </section>
        <NavalMobilePreparation :military="isMilitary" :ship-models="militaryCatalog" :ship-families="militaryFamilies" :can-buy-ship="canBuyMilitary" @cycle-ship="cycleMilitaryType" :ship-counts="militaryCounts" :military-asset="militaryAsset" @buy-ship="purchaseMilitary($event.key,$event.count)" v-if="mobilePrep" :stage="playerStage" :person="me" :clock="clockText" :urgent="seconds <= 10" :tutorial="tutorialActive" :chat-enabled="room.mode === 'MULTIPLAYER'" :tab="prepTab" :busy="busy" :can-arrange="canArrange" :can-shop="canShop" :draft="draft" :fleet="fleetRoster" :selected-ship="selectedShip" :full-fleet="fullFleet" :placement-tool="placementTool" :coins="coins" :items="ITEMS" :details="itemDetails" :ship-name="shipName" :ship-asset="shipAsset" :formation-id="formationId" :formations="formationOptions" :teleport-to="root || 'body'" @tab="toggleMobilePanel" @ship="selectShip" @clear="clearLayout" @random="randomize" @rotate="rotateShip" @save="showFormation = true" @formation="formationId = $event; loadFormation()" @delete="deleteFormation" @purchase="purchase($event.key, $event.count)" @defense="placementTool = $event; cancelTarget()" @advance="advanceLayout" @ready="ready" @chat="toggleChat" @more="toggleMobilePanel('more')" />
        <Transition name="chat-slide"><aside v-if="preparing && !spectating && !mobile || chatOpen" v-show="!mobile || mobilePanel === 'chat'" :data-mobile-panel="mobile ? mobilePanel : null" class="battle-sidebar" :class="{ 'chat-drawer': !preparing || spectating || mobile }">
          <template v-if="placing && (!mobile || mobilePanel === 'fleet')">
            <div v-if="mobile" class="mobile-panel-heading"><h3>舰队编成</h3><button class="icon-control" title="收起舰队编成" aria-label="收起舰队编成" @click="closeMobilePanel"><X :size="18" /></button></div>
            <template v-if="isMilitary"><div class="sidebar-heading"><h3>舰船采购</h3><strong><Coins :size="17" />{{ coins }}</strong></div><div class="supply-list"><div v-for="(item,key) in militaryCatalog" :key="key" class="supply-row military-card"><img class="supply-sprite" :src="militaryAsset(key)" alt="" /><button class="supply-name" :title="item.detail"><strong>{{ item.name }}</strong><small>{{ item.price }} 金币 · 上限 {{ item.max }}</small></button><button class="quantity-control" :aria-label="`减少${item.name}`" :disabled="!canArrange || !militaryCounts[key]" @click="purchaseMilitary(key,-1)"><Minus :size="14" /></button><span class="quantity">{{ militaryCounts[key] }}</span><button :data-tutorial="`naval-buy-${key}`" class="quantity-control" :aria-label="`购买${item.name}`" :disabled="!canArrange || !canBuyMilitary(key)" @click="purchaseMilitary(key,1)"><Plus :size="14" /></button><button v-if="militaryFamilies[item.group]" :data-tutorial="`naval-type-${item.group}`" class="military-type-switch" :title="`切换${item.group==='carrier'?'航母':'驱逐舰'}类型`" :aria-label="`切换${item.group==='carrier'?'航母':'驱逐舰'}类型`" :disabled="!canArrange" @click="cycleMilitaryType(key)"><RotateCw :size="13" /></button></div></div></template>
            <div class="sidebar-heading"><h3>{{ mobile ? '当前阵型' : '舰队编成' }}</h3><span class="fleet-total">{{ isMilitary ? `${draft.ships.length} 艘 · ${coins} 金币` : '10 艘 / 20 格' }}</span></div>
            <div class="fleet-roster" data-tutorial="naval-fleet-roster"><div v-for="ship in fleetRoster" :key="ship.id" class="fleet-command"><button :class="{ selected: selectedShip === ship.id, unplaced: !ship.placed }" :disabled="!canArrange" :title="`${ship.w} × ${ship.h} ${ship.placed ? '已布置' : '待布置'}`" @click="selectShip(ship)"><img :src="shipAsset(ship)" :style="{ transform: ship.w > ship.h ? 'rotate(90deg)' : 'none' }" alt="" /><span><strong>{{ shipName(ship) }}</strong><small>{{ ship.placed ? `${ship.w} × ${ship.h}` : '待布置' }}</small></span><Check v-if="ship.placed" :size="15" /></button><button :data-tutorial="ship.w !== ship.h ? 'naval-rotate-ship' : undefined" class="fleet-rotate" :disabled="!canArrange || ship.w === ship.h" :title="`旋转${shipName(ship)}`" :aria-label="`旋转${shipName(ship)}`" @click="rotateShip(ship)"><RotateCw :size="15" /></button></div></div>
          </template>
          <template v-if="shopping && (!mobile || mobilePanel === 'supply')">
            <div v-if="mobile" class="mobile-panel-heading"><h3>战备商店</h3><button class="icon-control" title="收起战备商店" aria-label="收起战备商店" @click="closeMobilePanel"><X :size="18" /></button></div>
            <div class="sidebar-heading"><h3>{{ mobile ? '剩余金币' : '战备补给' }}</h3><strong><Coins :size="17" />{{ coins }}</strong></div>
            <div class="supply-list"><div v-for="(item, key) in ITEMS" :key="key" class="supply-row"><img class="supply-sprite" :src="`/entertainment/naval/v2/${key}.png`" alt="" /><button class="supply-name" :title="itemDetails[key]" :class="{ active: placementTool === key }" :disabled="!canShop || !['mine', 'antiAir', 'stealth'].includes(key)" @click="placementTool = key"><strong>{{ item.name }}</strong><small>{{ item.price }} 金币 · 上限 {{ item.max }}</small></button><button class="quantity-control" :aria-label="`减少${item.name}`" :disabled="!canShop || !draft.inventory[key]" @click="purchase(key, -1)"><Minus :size="14" /></button><span class="quantity">{{ draft.inventory[key] || 0 }}</span><button :data-tutorial="`naval-buy-${key}`" class="quantity-control" :aria-label="`购买${item.name}`" :disabled="!canShop || coins < item.price || draft.inventory[key] >= item.max" @click="purchase(key, 1)"><Plus :size="14" /></button></div></div>
          </template>
          <Transition name="chat-slide"><section v-if="chatOpen && room.mode === 'MULTIPLAYER'" class="battle-chat"><h3><MessageSquare :size="16" />战局聊天<button class="icon-control" title="收起聊天" aria-label="收起聊天" @click="toggleChat"><X :size="16" /></button></h3><div ref="chatLog" class="chat-log" aria-live="polite"><p v-if="!room.messages?.length" class="chat-empty">暂无消息</p><div v-for="message in room.messages || []" :key="message.id" class="chat-message" :class="{ mine: message.senderId ? message.senderId === room.players[room.you]?.id : message.player === room.you }"><span>{{ message.name || room.players[message.player]?.name || '指挥官' }}</span><p><NavalMessage :text="message.text" /></p></div></div><NavalStickers :panel-target="battleStickerTray" v-if="!spectating && !me?.left" :disabled="busy" @send="sendBattleSticker" /><NavalEmoji v-if="!spectating && !me?.left" @select="chatText = (chatText + $event).slice(0, 300)" /><form v-if="!spectating && !me?.left" @submit.prevent="sendChat"><input v-model="chatText" aria-label="战局消息" placeholder="发送消息" maxlength="300" :disabled="busy" /><button class="icon-control" title="发送" aria-label="发送消息" :disabled="busy || !chatText.trim()"><Send :size="17" /></button></form><div ref="battleStickerTray" class="sticker-tray" /></section></Transition>
        </aside></Transition>
      </div>
      </Transition>
      <template v-if="mobile || room.phase === 'WAITING' || mobilePanel === 'audience'">
        <Transition name="mobile-veil"><button v-if="mobileOverlay && (mobile || mobilePanel !== 'audience')" class="mobile-panel-veil" aria-label="收起面板" @click="closeMobilePanel" /></Transition>

        <Transition name="mobile-sheet"><section v-if="['more', 'audience', 'detail', 'medals'].includes(mobilePanel) && (mobile || mobilePanel !== 'audience')" class="mobile-utility-panel" role="dialog" :aria-label="mobilePanelTitle" :data-mobile-panel="mobilePanel"><header class="mobile-panel-heading"><h3>{{ mobilePanelTitle }} · {{ isMilitary ? '军事模式' : '经典模式' }}</h3><button class="icon-control" title="收起面板" aria-label="收起面板" @click="closeMobilePanel"><X :size="18" /></button></header>
          <div v-if="mobilePanel === 'more'" class="mobile-menu-actions"><button v-if="room.mode === 'MULTIPLAYER'" @click="openMobileDialog('share')"><Share2 :size="19" />分享房间</button><button v-if="room.phase === 'WAITING'" :disabled="room.lobbyShared" @click="shareToLobby"><MessagesSquare :size="19" />{{ room.lobbyShared ? '本局已分享' : '分享到大厅' }}</button><label v-if="!spectating && !tutorialActive" class="spectator-toggle"><Eye :size="19" /><span>允许观战</span><input type="checkbox" role="switch" :checked="room.allowSpectators !== false" :disabled="busy" @change="toggleSpectators($event.target.checked)" /></label><button @click="toggleSound"><VolumeX v-if="muted" :size="19" /><Volume2 v-else :size="19" />{{ muted ? '开启音效' : '关闭音效' }}</button><button @click="openMobileDialog('rules')"><CircleHelp :size="19" />对局规则</button><button @click="enterFullscreen"><Maximize :size="19" />全屏</button><button @click="openMobileDialog('leave')"><LogOut :size="19" />离开对局</button></div>
          <NavalMedalWall v-if="mobilePanel === 'medals'" :person="medalPerson" />
          <NavalAudience v-if="mobilePanel === 'audience'" :room="room" :busy="busy" @claim="claimSeat" @spectators="toggleSpectators" />
          <template v-if="mobilePanel === 'detail'"><CustomSelect :model-value="room.viewMode || 'BOTH'" :options="spectatorOptions" :disabled="busy" :teleport-to="root || 'body'" aria-label="观战视角" @update:model-value="changeSpectatorView" /><h4>{{ me?.name }}</h4><NavalSpectatorData :player="me" /><template v-if="!hiddenOpponent"><h4>{{ opponent?.name }}</h4><NavalSpectatorData :player="opponent" /></template></template>
        </section></Transition>
      </template>
    </template>

    <div v-if="error" :key="noticeSerial" class="naval-notice" role="alert"><AlertCircle :size="17" /><span>{{ error }}</span><button class="icon-control" title="关闭提示" @click="error = ''"><X :size="16" /></button><i class="notice-countdown" /></div>
    <Transition name="launch"><div v-if="launching" class="launch-overlay" role="status" aria-label="舰队加载中"><div class="launch-water"><img src="/entertainment/naval/v2/battleship.png" alt="" /><div class="launch-wake" /></div><span class="launch-kicker">FLEET COMMAND</span><h2>{{ launchLabel }}</h2><div class="load-track"><span :style="{ width: `${launchProgress}%` }" /></div><span class="load-percent">{{ Math.round(launchProgress) }}%</span></div></Transition>
    <Transition name="match-presentation"><div v-if="matchPresentation" class="match-presentation" role="status" aria-label="匹配过程" :data-stage="matchPresentation"><div class="signal-rings"><span /><span /><Radar :size="42" /></div><span class="launch-kicker">{{ matchKind === 'AUTO' ? 'SEARCHING THE SEAS' : 'CONNECTING FLEETS' }}</span><span class="match-rule">{{ isMilitary ? '军事模式' : '经典模式' }}</span><h2>{{ matchPresentation === 'FOUND' ? '对手已就位' : matchKind === 'AUTO' ? '正在匹配指挥官' : '正在连接舰队' }}</h2><div v-if="matchPresentation === 'FOUND'" class="matched-fleets"><span><NavalAvatar :person="matchPeople[0]" :size="25" />{{ matchNames[0] }}</span><Crosshair :size="22" /><span><NavalAvatar :person="matchPeople[1]" :size="25" />{{ matchNames[1] }}</span></div><p v-else>{{ onlineCount == null ? '正在建立海域信号' : `${onlineCount} 位指挥官在线` }}</p><button v-if="lobbyChatAvailable" class="naval-secondary" @click="openCommunity('chat')"><MessageSquare :size="18" />聊天大厅</button></div></Transition>
    <Transition name="announcement" @before-enter="announcementTransition = true" @after-leave="announcementTransition = false"><div v-if="announcement && !tutorialActive" class="phase-announcement" :key="announcement"><span>FLEET COMMAND</span><strong>{{ announcement }}</strong></div></Transition>
    <NavalTutorialGuide v-if="tutorialActive && !launching && !blocked && !confirmation" :step="tutorialStep" :completed="tutorialSession.completed" :busy="busy || visualBusy" @continue="tutorialEvent({ type: 'CONTINUE' })" @restart="restartTutorial" @exit="exitTutorial" />
    <Transition name="modal"><NavalShareDialog v-if="showShare && room" :room="room" @close="showShare = false" /></Transition>
    <Transition name="modal"><div v-if="confirmation" class="naval-modal-mask" role="dialog" aria-modal="true" :aria-label="confirmation.title" @click.self="confirmation = null"><section class="naval-dialog"><h2>{{ confirmation.title }}</h2><p>{{ confirmation.message }}</p><footer><button class="naval-secondary" @click="confirmation = null">取消</button><button class="naval-primary" @click="confirmOperation"><Check :size="16" />确认</button></footer></section></div></Transition>
    <Transition name="modal"><div v-if="resumeDecision" class="naval-modal-mask resume-mask" role="dialog" aria-modal="true" aria-label="已有未结束的对局" @click.self="resumeDecision = null"><section class="naval-dialog resume-dialog"><header><h2>已有未结束的对局</h2><button class="icon-control" aria-label="取消" :disabled="busy" @click="resumeDecision = null"><X :size="18" /></button></header><p>可以继续之前的对局，或先退出再开始新局。在线对局离开页面后保留 30 秒，超过时间未重连将判负。人机局在布阵、补给阶段退出不计战绩。</p><footer><button class="naval-secondary" :disabled="busy" @click="continueSavedGame"><RotateCcw :size="16" />继续旧对局</button><button class="naval-primary" :disabled="busy" @click="exitSavedGame"><LogOut :size="16" />{{ resumeDecision.run ? '退出并开始新局' : '退出对局' }}</button></footer></section></div></Transition>
    <Transition name="settlement"><NavalSettlement v-if="showSettlement && room?.phase === 'FINISHED'" :room="room" :busy="busy" @close="showSettlement = false" @continue="continueGame" @exit="leave" @claim="claimSeat" /></Transition>
    <Transition name="modal"><div v-if="fullRoom" class="naval-modal-mask" role="dialog" aria-modal="true" aria-label="房间已满"><section class="naval-dialog full-room-dialog"><Eye :size="32" /><h2>房间已满</h2><p>{{ fullRoomSeconds }} 秒后自动加入观众席</p><div class="spectate-countdown"><span :style="{ width: `${fullRoomSeconds / 3 * 100}%` }" /></div><button class="naval-secondary" @click="cancelSpectating">取消</button></section></div></Transition>
    <Transition name="modal"><div v-if="showFormation" class="naval-modal-mask" @click.self="showFormation = false"><form class="naval-dialog" @submit.prevent="saveFormation"><header><h2>保存阵型</h2><button type="button" class="icon-control" title="关闭" @click="showFormation = false"><X :size="19" /></button></header><input v-model.trim="formationName" aria-label="阵型名称" placeholder="阵型名称" maxlength="30" required /><button class="naval-primary" :disabled="busy"><Save :size="17" />保存</button></form></div></Transition>
    <Transition name="modal"><div v-if="showLeave" class="naval-modal-mask leave-mask" @click.self="showLeave = false"><section class="naval-dialog"><h2>离开本局？</h2><p>{{ spectating ? '离开观众席，不影响当前对局。' : room?.phase === 'FINISHED' ? '返回港口后将腾出你的玩家席位。' : room?.phase === 'WAITING' ? '匹配将取消，房间自动关闭，不计入战绩。' : room?.mode === 'AI' && preparing ? '战斗尚未开始，退出不算失败，也不计入战绩。' : '确认退出会立即判负。误关页面可在 30 秒内返回继续对局。' }}</p><footer><button class="naval-secondary" @click="showLeave = false">取消</button><button class="naval-primary" :disabled="busy" @click="leave"><LogOut :size="17" />确认离开</button></footer></section></div></Transition>
    <Transition name="modal"><div v-if="showRules" class="naval-modal-mask" @click.self="showRules = false"><section class="naval-dialog rules-dialog"><header><h2>{{ isMilitary ? '军事模式规则' : '海战棋规则' }}</h2><button class="icon-control" title="关闭" @click="showRules = false"><X :size="19" /></button></header><template v-if="isMilitary"><p>12 × 12 海域，初始 300 金币，先购买舰船并部署。1 × 4 共享上限 2 艘、1 × 3 共享 4 艘、1 × 2 驱逐舰共享 6 艘，佯攻舰另限 3 艘。每次旋转 90°，舰船间隔一格，包括斜角。布阵 8 分钟，每回合一次行动，命中也换手。</p><dl><template v-for="(m,k) in MILITARY_SHIPS" :key="k"><dt>{{ m.name }} · {{ m.w }} × {{ m.h }} · {{ m.price }} 金币 · 最多 {{ m.max }}</dt><dd>{{ m.detail }}</dd></template></dl><p>飞机沿所选行从左侧第 1 列进入，飞越目标后再多飞一格掉头。战斗机在横向六格中随机攻击 4–5 格，轰炸机在 3 × 3 内随机命中 5–6 格，鱼雷从投放点沿行前进，遇舰体（包括已击毁的残骸）或水雷停止。进入防空范围先被击落，永久损失；安全返航则降落回甲板。甲板或对应部件被命中后无法使用。</p><p>所有非佯攻舰沉没，或全部主炮、飞机和潜艇失效时战败。防空、雷达、干扰、布雷及佯攻舰不能单独维持战斗。</p></template><template v-else><p>12 × 12 海域，10 艘舰船共 20 格：1×1 四艘、1×2 三艘、1×3 两艘、1×4 一艘。舰船之间至少空一格，斜角也不可接触。</p><p>先布置己方舰队，再购买道具与部署防御，双方战备完成后开始交战。布阵限时 5 分钟；双方布阵完成后，补给限时 3 分钟。每次攻击限时 30 秒，命中可继续攻击并重新计时。</p><p>已锁定的舰队不可在补给阶段移动。水雷和防空炮购买后需部署；布阵或补给超时将自动完成。</p><dl><template v-for="(item, key) in ITEMS" :key="key"><dt>{{ item.name }} · {{ item.price }} 金币 · 最多 {{ item.max }}</dt><dd>{{ itemDetails[key] }}</dd></template></dl><p>每局金币重置为 300。雷达消耗一次行动；空袭被防空炮拦截后消耗道具与行动。舰队全部被击毁即战败。</p></template></section></div></Transition>
    </div>
    <Transition name="modal"><div v-if="mobile && (room || pendingStart || replay) && blocked" class="device-gate" role="dialog" aria-modal="true" aria-label="横屏全屏限制"><Smartphone :size="46" /><h2>{{ !landscape ? '请横屏进入海域' : iosFallback ? '横屏沉浸模式' : '请开启全屏' }}</h2><p>{{ fullscreenError || (iosFallback ? 'iPhone Safari 不允许网页主动全屏。可横屏直接游玩；要隐藏地址栏，请点 Safari 分享，再添加到主屏幕并打开。' : '手机游戏与回放需保持横屏与全屏状态') }}</p><button class="naval-primary" @click="enableMobile"><Maximize :size="18" />{{ iosFallback ? '进入横屏游戏' : '全屏并锁定横屏' }}</button><button class="naval-secondary" @click="cancelMobile">返回港口</button></div></Transition>
    <Transition name="community-sheet"><section v-if="!mobile && room && mobilePanel==='audience'" class="desktop-audience-panel" data-mobile-panel="audience" aria-label="观众席"><header><h2>观众席 · {{ isMilitary ? '军事模式' : '经典模式' }}</h2><button class="icon-control" title="收起面板" aria-label="收起面板" @click="closeMobilePanel"><X :size="19" /></button></header><NavalAudience :room="room" :busy="busy" @claim="claimSeat" @spectators="toggleSpectators" /></section></Transition>
    <Transition name="community-sheet"><NavalCommunity v-if="communityPanel" :rule-set="room?.ruleSet || ruleSet" :panel="communityPanel" :medal-person="medalPerson" :mobile="mobile"  :history="history.filter(h=>(h.ruleSet||'CLASSIC')===(room?.ruleSet||ruleSet))" :room="room" @messages="receiveLobbyMessages" @replay="startReplay" @join="joinLobbyRoom" @share="shareToLobby" @close="communityPanel = ''" /></Transition>
  </div>
</template>

<style scoped>
.naval-page:has(.match-presentation)>.naval-community:not(.mobile){position:fixed;z-index:610;right:16px;top:60px;width:380px;height:calc(100dvh - 76px)}.military-card{position:relative}.military-type-switch{position:static;flex:0 0 24px;width:24px;height:24px;margin-left:5px;display:grid;place-items:center;background:#e5eef1;color:#608590;border-radius:3px}.military-rules .weapons{flex-wrap:wrap}.military-rules .weapons>button{min-width:0}.military-rules .weapons>button span{white-space:normal;overflow-wrap:anywhere;font-size:11px}.military-rules .battle-sidebar .supply-name{min-width:0}
.naval-rule-switch{display:flex;gap:0;margin-bottom:14px;border-bottom:1px solid #bdd0d5}.naval-rule-switch button{padding:10px 20px;border:0;border-bottom:3px solid transparent;background:transparent;color:#63838d;font:inherit;cursor:pointer}.naval-rule-switch button[aria-pressed=true]{color:#365f69;border-bottom-color:#bd9252;font-weight:600}
.naval-page:has(>.naval-replay){height:calc(100dvh - 52px);min-height:0;box-sizing:border-box;overflow:hidden}
.naval-page:has(>.naval-replay)>.naval-replay{flex:1;min-width:0}
.naval-page.is-mobile:not(.mobile-game):has(>.naval-replay){height:calc(100dvh - 52px);min-height:0;padding:4px 8px;overflow:hidden}
.naval-page.mobile-game:has(>.naval-replay){height:var(--game-height,100dvh)}
.naval-page.is-fullscreen:has(>.naval-replay){height:100dvh;min-height:0}
.naval-page.is-mobile:has(>.naval-replay){position:fixed;inset:0;z-index:100;height:var(--game-height,100dvh)!important;min-height:0!important;padding:4px max(8px,env(safe-area-inset-right)) max(4px,env(safe-area-inset-bottom)) max(8px,env(safe-area-inset-left));background:#f4f8f8}
@media(max-width:700px){.naval-page.is-mobile:not(.mobile-game):has(>.naval-replay){height:calc(100dvh - 46px)}}
</style>
<style scoped>
.naval-page .match-presentation{isolation:isolate;background:transparent}.match-presentation:before{content:'';position:absolute;inset:0;z-index:-1;background-image:linear-gradient(180deg,#edf4f6c9,#edf4f6f5),var(--naval-harbor);background-size:cover;background-position:center;image-rendering:pixelated}.match-rule{font-size:12px;color:#9b743c}
@media(max-height:500px) and (orientation:landscape){.naval-page:not(.in-game){isolation:isolate;position:relative}.naval-page:not(.in-game):before{content:'';position:absolute;inset:0;z-index:-1;background-image:linear-gradient(180deg,#f3f7f959,#f3f7f9ed 65%,#f3f7f9),var(--naval-harbor);background-size:cover;background-position:center;image-rendering:pixelated}.naval-page:not(.in-game) .mode-entry{background:#f9fcfce0}}
</style>

<script setup>
import { computed, nextTick, onMounted, onUnmounted, reactive, ref, watch } from 'vue'
import { AlertCircle, Anchor, ArrowLeft, ArrowRight, Bot, Check, ChevronLeft, CircleHelp, Coins, Copy, Crosshair, Ellipsis, Eraser, Eye, GraduationCap, LoaderCircle, LogIn, LogOut, Maximize, MessageSquare, MessagesSquare, Minus, Plus, Radar, RotateCcw, RotateCw, Save, ScanEye, Send, Share2, Shield, ShieldCheck, Ship, ShoppingBag, Shuffle, SlidersHorizontal, Smartphone, Ticket, Timer, Trash2, TrendingUp, Trophy, Users, Volume2, VolumeX, X } from 'lucide-vue-next'
import { useRoute } from 'vue-router'
import { Play } from 'lucide-vue-next'
import { navalViewportState } from '../../games/navalViewport'
import NavalBoard from './NavalBoard.vue'
import NavalCommunity from './NavalCommunity.vue'
import NavalCommunityActions from './NavalCommunityActions.vue'
import NavalEmoji from './NavalEmoji.vue'
import NavalStickers from './NavalStickers.vue'
import NavalMessage from './NavalMessage.vue'
import NavalReplay from './NavalReplay.vue'
import { stickerMessage, messageSticker } from '../../games/navalStickers'
import NavalTargetConfirmation from './NavalTargetConfirmation.vue'
import NavalMobilePreparation from './NavalMobilePreparation.vue'
import NavalMobilePlayerStatus from './NavalMobilePlayerStatus.vue'
import NavalAvatar from './NavalAvatar.vue'
import NavalMedalWall from './NavalMedalWall.vue'
import {ownNavalMedals,refreshNavalMedals} from '../../games/navalMedals'
import NavalAudience from './NavalAudience.vue'
import NavalSpectatorData from './NavalSpectatorData.vue'
import NavalSettlement from './NavalSettlement.vue'
import NavalShareDialog from './NavalShareDialog.vue'
import CustomSelect from '../../components/CustomSelect.vue'
import NavalTutorialGuide from './NavalTutorialGuide.vue'
import { MILITARY_SHIPS, militaryActions, militaryGroupCount, militaryComponents, militaryPlacement, validateMilitaryFleet, arrangeMilitaryFleet, createMilitaryGame, militaryLocalAction, tickMilitaryGame } from '../../games/navalMilitary'
import { loadMilitaryArt, militaryShipThumbnail } from '../../games/navalMilitaryArt'
import { createMilitaryTutorial, militaryTutorialStep, advanceMilitaryTutorial, tickMilitaryTutorial } from '../../games/navalMilitaryTutorial'
import { FLEET, ITEMS, generateFleet, validateFleet, legalPlacement, createLocalGame, localAction, tickLocalGame, migrateLocalGame, normalizeSavedFleet, submarineFootprint, localBattleResult } from '../../games/navalEngine'
import { navalTutorialStorageKey, createNavalTutorial, loadNavalTutorial, saveNavalTutorial, getNavalTutorialStep, advanceNavalTutorial, prepareTutorialAction } from '../../games/navalTutorial'
import { preloadNavalAudio, playNavalSound, setNavalMuted, getNavalMuted, unlockNavalAudio, stopNavalSounds } from '../../games/navalAudio'
import {militaryRecordTemplate,installMilitaryTemplate} from '../../games/navalMilitaryTemplate'
import { createNavalRoom, getMilitaryTemplate, joinNavalRoom, spectateNavalRoom, getNavalRoom, navalAction, getNavalProfile, saveNavalProfile, heartbeatNavalPresence, leaveNavalPresence, notifyNavalConnection, sendNavalLobbyMessage, enterNavalRoom, publishLocalNavalRoom } from '../../api/naval'
import { useNavalLobbyNotifications } from '../../composables/useNavalLobbyNotifications'
import { navalBoardEvents } from '../../games/navalPresentation'

const root = ref(null), chatLog = ref(null), battleStickerTray = ref(null)
const ruleSet = ref('CLASSIC')
const isMilitary = computed(() => (room.value?.ruleSet || ruleSet.value) === 'MILITARY')
const militaryActionId = ref('')
const militaryAvailable = computed(() => militaryActions(me.value))
const militarySelectedAction = computed(() => militaryAvailable.value.find(a => militaryKey(a) === militaryActionId.value && a.weapon === weapon.value) || militaryAvailable.value.find(a=>a.weapon===weapon.value) || militaryAvailable.value[0])
const militarySource = computed(() => me.value?.ships?.find(s => s.id === militarySelectedAction.value?.shipId))
const militaryLast = computed(() => room.value?.events?.at(-1))
const militaryTypes=reactive({carrier:'carrier',destroyer:'antiAir'})
const militaryFamilies={carrier:['carrier','bomberCarrier','torpedoCarrier'],destroyer:['antiAir','radar','mineLayer','jammer','depthCharge']}
const militaryCatalog=computed(()=>Object.fromEntries([militaryTypes.carrier,'battleship','cruiser',militaryTypes.destroyer,'decoy','submarine'].map(k=>[k,MILITARY_SHIPS[k]])))
function cycleMilitaryType(kind){const group=MILITARY_SHIPS[kind].group,types=militaryFamilies[group];if(types){militaryTypes[group]=types[(types.indexOf(kind)+1)%types.length];tutorialEvent({type:'TYPE',kind:militaryTypes[group]})}}
const canBuyMilitary=kind=>coins.value>=MILITARY_SHIPS[kind].price&&militaryGroupCount(draft.ships,kind)<MILITARY_SHIPS[kind].max
function ownCell(point){if(isMilitary.value&&room.value?.phase==='BATTLE'&&militarySelectedAction.value?.weapon==='mine')attack(point);else placeCell(point)}
const militaryCounts = computed(() => Object.fromEntries(Object.keys(MILITARY_SHIPS).map(k => [k, draft.ships.filter(s => s.kind === k).length])))
const militaryKey = a => `${a.shipId}:${a.weapon}:${a.plane}`
function selectMilitaryAction(a) { if(room.value?.mineActions&&a.weapon!=='mine')return; militaryActionId.value = militaryKey(a); weapon.value = a.weapon; cancelTarget(); closeMobilePanel(); tutorialEvent({type:'WEAPON',weapon:a.weapon,kind:a.kind}) }
const militaryThumbnails = ref({})
loadMilitaryArt().then(() => { militaryThumbnails.value = Object.fromEntries(Object.keys(MILITARY_SHIPS).map(kind => [kind, militaryShipThumbnail(kind)])) }).catch(fail)
function militaryAsset(kind) { return militaryThumbnails.value[kind] || '/entertainment/naval/military/utility-hull.png' }
const communityPanel = ref(''),medalPerson=ref(null),matchPeople=ref([])
function openMedals(person={self:true,name:'我的'}){if(person.ai||!person.self&&!person.medals?.medalId)return;medalPerson.value=person;communityPanel.value=mobile.value&&room.value?'':'medals';mobilePanel.value=mobile.value&&room.value?'medals':null;chatOpen.value=false;nextTick(positionCommunity)}
function onMedalWall(e){openMedals(e.detail||{self:true,name:'我的'})}
function medalEquipped(){if(localMode.value&&room.value)room.value.players[room.value.you||0].medals=ownNavalMedals.value}
onMounted(()=>{refreshNavalMedals();window.addEventListener('naval-medal-wall',onMedalWall);window.addEventListener('naval-medal-equipped',medalEquipped)})
onUnmounted(()=>{window.removeEventListener('naval-medal-wall',onMedalWall);window.removeEventListener('naval-medal-equipped',medalEquipped)})
const replay = ref(null)
let replayOwnsFullscreen = false
function startReplay(detail) {
  if (room.value && room.value.phase !== 'FINISHED') { error.value = '请先结束或离开当前对局'; return }
  replay.value = detail; communityPanel.value = ''; window.scrollTo(0, 0)
  replayOwnsFullscreen = !fullscreen.value; enterFullscreen()
}
async function closeReplay() {
  replay.value = null
  if (replayOwnsFullscreen) { screen.orientation?.unlock?.(); try { if (document.fullscreenElement || document.webkitFullscreenElement) await (document.exitFullscreen || document.webkitExitFullscreen)?.call(document) } catch { /* The browser may have already left fullscreen. */ } }
  replayOwnsFullscreen = false; syncViewport()
}
function positionCommunity() {
  if (!root.value || !mobile.value) return
  const header=[...root.value.querySelectorAll('.replay-toolbar,.naval-header,.mobile-player-status,.prep-heading')].find(element => element.getBoundingClientRect().height>0)
  root.value.style.setProperty('--community-top',`${Math.max(0,Math.min(innerHeight-160,(header?.getBoundingClientRect().bottom || 24)+4))}px`)
}
function openCommunity(panel) { if(panel==='medals'){if(communityPanel.value==='medals')communityPanel.value='';else openMedals();return}medalPerson.value=null; if(!mobile.value&&mobilePanel.value==='audience')mobilePanel.value=null; if (panel === 'chat' && !lobbyChatAvailable.value) return; communityPanel.value = communityPanel.value === panel ? '' : panel; if (room.value && mobile.value) closeMobilePanel(); nextTick(positionCommunity) }
const mobilePanel = ref(null)
const prepTab = ref('fleet')
const mobilePanelTitle = computed(() => ({ more: '对局菜单', audience: '观众席', detail: '观战视角与战备', medals: `${medalPerson.value?.name || '我的'}勋章墙` })[mobilePanel.value] || '')
const route = useRoute()
const showShare = ref(false), showSettlement = ref(false), fullRoom = ref(null), fullRoomSeconds = ref(3)
const confirmation = ref(null)
const resumeDecision = ref(null)
const chatPops = reactive({ 0: null, 1: null }), chatPopTimers = {}
const room = ref(null), busy = ref(false), error = ref(''), connectionError = ref(''), autoMatch = ref(true), inviteCode = ref(''), weapon = ref('shot'), chatText = ref('')
const showRules = ref(false), showLeave = ref(false), showFormation = ref(false), formationName = ref(''), formationId = ref('')
const selectedShip = ref(''), rotated = ref(false), placementTool = ref('ship'), muted = ref(getNavalMuted())
const selectedDefense = ref(null), dragAnchor = ref(null)
const pendingTarget = ref(null)
const reserveRotations = reactive({})
const localMode = ref(false), fullscreen = ref(false), landscape = ref(innerWidth > innerHeight), fullscreenError = ref(''), pendingStart = ref(null)
const mobile = ref(/Android|iPhone|iPad|iPod/i.test(navigator.userAgent) || (navigator.platform === 'MacIntel' && navigator.maxTouchPoints > 1) || (matchMedia('(pointer:coarse)').matches && navigator.maxTouchPoints > 0))
const loggedIn = Boolean(localStorage.getItem('token'))
const account = readJson('user', {}) || {}
const accountPerson = { self:true,name: account.nickname || account.username || '指挥官', avatarUrl: account.avatarUrl || account.avatar_url, registered: true }
const authTarget = page => ({ path: `/customer/${page}`, query: { redirect: route.fullPath } })
const lobbyChatAvailable = computed(() => Boolean(replay.value) || !room.value || room.value.phase === 'WAITING')
const { unread: lobbyUnread, receive: receiveLobbyMessages } = useNavalLobbyNotifications({ available: () => lobbyChatAvailable.value, open: () => communityPanel.value === 'chat' })
watch(lobbyChatAvailable, available => { if (!available && communityPanel.value === 'chat') communityPanel.value = '' })
const storagePrefix = loggedIn ? `naval-account-${readJson('user', {})?.id || 'current'}` : 'naval-guest'
const formations = ref(loggedIn ? [] : normalizeFormations(readJson('naval-guest-formations', [])))
const history = ref(loggedIn ? [] : readJson('naval-guest-history', []).filter(item => item.mode !== 'AI' || item.detail?.battleStarted !== false))
const localResume = ref(!loggedIn ? readJson('naval-guest-session', null) : null)
const resumeId = ref(localStorage.getItem(`${storagePrefix}-room`) || '')
const now = ref(Date.now())
const serverOffset = ref(0)
const launching = ref(false), launchProgress = ref(0), launchLabel = ref('舰队正在入场'), announcement = ref('')
const announcementTransition = ref(false)
const bannerBusy = computed(() => Boolean(announcement.value) || announcementTransition.value)
const ownAnimating = ref(false), enemyAnimating = ref(false), chatOpen = ref(false), unreadChat = ref(0)
const onlineCount = ref(null), matchPresentation = ref(''), matchKind = ref('AUTO'), matchNames = ref([])
const tutorialOptions = { device: mobile.value ? 'mobile' : 'desktop' }
const classicTutorialSaved = ref(readJson(navalTutorialStorageKey(tutorialOptions.device), null) ? loadNavalTutorial(undefined, tutorialOptions) : null), militaryTutorialSaved = ref(readJson(`naval-military-tutorial-v6-${tutorialOptions.device}`,null)), tutorialSession = ref(null)
const tutorialSaved = computed({get:()=>isMilitary.value?militaryTutorialSaved.value:classicTutorialSaved.value,set:value=>{if(isMilitary.value)militaryTutorialSaved.value=value;else classicTutorialSaved.value=value}})
function persistTutorial(session) { if(session.ruleSet==='MILITARY')writeJson(`naval-military-tutorial-v6-${session.device}`,session);else saveNavalTutorial(session) }
const tutorialActive = computed(() => Boolean(tutorialSession.value))
const tutorialStep = computed(() => {
  const step = tutorialSession.value ? (isMilitary.value ? militaryTutorialStep(tutorialSession.value) : getNavalTutorialStep(tutorialSession.value)) : null
  if (!step) return null
  const guideSide = room.value?.phase === 'BATTLE' && step.target === 'naval-enemy-board' ? 'left' : undefined
  return pendingTarget.value ? { ...step, guideSide, target: 'naval-confirm-target', cell: undefined, description: '目标已预览，还没有生效。点绿色勾确认，红色叉取消；只有确认后才部署或消耗道具。' } : { ...step, guideSide }
})
const visualBusy = computed(() => ownAnimating.value || enemyAnimating.value)
const harborFailed = ref(false)
const harborAsset = computed(() => harborFailed.value ? '/entertainment/naval/v2/harbor.png' : isMilitary.value ? '/entertainment/naval/military/harbor-v2.png' : '/entertainment/naval/v2/harbor.png')
const draft = reactive({ ships: [], inventory: emptyInventory(), mines: [], aa: [] })
const attackItems = ['fighter', 'bomber', 'torpedo', 'radar', 'submarine']
const submarineTargets = ref([])
const iosFallback = ref(navalViewportState().iosFallback), immersive = ref(false), iosFullscreenRejected = ref(false)
const itemDetails = { antiAir: '部署在一行，拦截覆盖该行的一次飞机袭击，随后消耗。', fighter: '选择 2 行 × 8 列，随机扫射其中一行 8 格。任一行有防空炮即被击落；两行都有炮时只消耗进攻行的炮。', bomber: '在目标起始的 3 × 3 区域随机轰炸 5 至 7 格。', torpedo: '从选中的投放格向敌方海域后方攻击，己方防守时镜像为从右向左；遇舰船或水雷停止，不触发水雷反击。投放后鱼雷比飞机略慢。', radar: '探测目标起始的 3 × 3 区域是否存在舰船，不造成伤害。', mine: '部署在无舰船的格子；舰炮或飞机击中会反击攻击方同坐标。鱼雷与潜艇引爆时不反击。' }
Object.assign(itemDetails, { submarine: '单次定位三格潜艇虚影，从海域边缘水下驶入。触雷只攻击当前已进入海域的艇身格；第二格触雷只爆炸两格，不补齐第三格。抵达后随机向上、向下各发射一颗鱼雷，遇舰船或水雷停止。', stealth: '补给时点击自己的舰船涂装，每份覆盖一艘；被雷达扫描时不显示，不抵挡实际攻击。' })
const spectating = computed(() => room.value?.role === 'SPECTATOR' || room.value?.you < 0)
const selfIndex = computed(() => spectating.value ? (room.value?.viewAs === 1 ? 1 : 0) : room.value?.you ?? 0)
const otherIndex = computed(() => 1 - selfIndex.value)
const me = computed(() => room.value?.players?.[selfIndex.value])
const opponent = computed(() => room.value?.players?.[otherIndex.value])
const hiddenOpponent = computed(() => spectating.value ? room.value?.viewMode !== 'BOTH' && room.value?.phase !== 'FINISHED' : room.value?.phase !== 'FINISHED')
const spectatorOptions = computed(() => [{ value: 'BOTH', label: '双方完整视角' }, ...[0, 1].map(index => ({ value: `PLAYER_${index}`, label: `${room.value?.players?.[index]?.name || '空席'} 的视角` }))])
const preparing = computed(() => ['PLACEMENT', 'SUPPLY'].includes(room.value?.phase))
const mobilePrep = computed(() => mobile.value && preparing.value && !spectating.value)
const mobileOverlay = computed(() => Boolean(mobilePanel.value) && (!mobilePrep.value || !['fleet', 'tools', 'supply'].includes(mobilePanel.value)))
const playerStage = computed(() => me.value?.stage || (me.value?.ready ? 'READY' : room.value?.phase))
const placing = computed(() => !spectating.value && !me.value?.left && preparing.value && playerStage.value === 'PLACEMENT')
const shopping = computed(() => !spectating.value && !me.value?.left && preparing.value && playerStage.value === 'SUPPLY')
const waitingReady = computed(() => !spectating.value && preparing.value && playerStage.value === 'READY')
const visualStage = computed(() => preparing.value ? playerStage.value : room.value?.phase)
const sceneKey = computed(() => `${room.value?.gameId || room.value?.id}:${room.value?.round || 1}:${selfIndex.value}:${room.value?.viewMode || 'PLAYER'}:${['BATTLE', 'FINISHED'].includes(visualStage.value) ? 'COMBAT' : visualStage.value}`)
const blocked = computed(() => mobile.value && ((!fullscreen.value && !(iosFallback.value && immersive.value)) || !landscape.value))
const canArrange = computed(() => placing.value && !blocked.value && !busy.value && !launching.value && !matchPresentation.value)
const canShop = computed(() => shopping.value && !blocked.value && !busy.value && !launching.value && !matchPresentation.value)
const canDeployDefense = computed(() => canShop.value)
const chainAttack = computed(() => {
  if (isMilitary.value) return false
  const state = room.value
  const last = state?.events?.findLast(event => ['shot', ...attackItems].includes(event.weapon))
  return state?.turn === state?.you && !state?.presentationUntil && last?.player === state?.you && last?.cells?.some(cell => cell.result === 'hit')
})
const activeTurn = computed(() => room.value?.phase === 'BATTLE' && !room.value.presentationUntil && (!visualBusy.value || chainAttack.value) && !bannerBusy.value && !launching.value ? room.value.turn : -1)
const canAttack = computed(() => !communityPanel.value && mobilePanel.value !== 'medals' && !spectating.value && !me.value?.left && activeTurn.value === room.value?.you && !blocked.value && !busy.value && !matchPresentation.value && !showRules.value && !showLeave.value && !showShare.value && !confirmation.value)
const ownBoard = computed(() => preparing.value && !spectating.value ? pendingTarget.value?.kind === 'placement' ? { ...me.value, ...pendingTarget.value.preview } : { ...me.value, ...draft } : me.value)
const selectedModel = computed(() => draft.ships.find(ship => ship.id === selectedShip.value))
const formationOptions = computed(() => [{ value: '', label: '已有阵型' }, ...formations.value.map(f => ({ value: f.id, label: f.name }))])
const fullFleet = computed(() => !(isMilitary.value ? validateMilitaryFleet(draft.ships) : validateFleet(draft.ships)))
const fleetRoster = computed(() => {
  if (isMilitary.value) return draft.ships.map(s => ({...s, placed:s.x>=0}))
  const available = [...draft.ships], used = new Set(available.map(ship => ship.id))
  return FLEET.map(template => {
    const index = available.findIndex(ship => ship.w * ship.h === template.w * template.h)
    if (index >= 0) return { ...available.splice(index, 1)[0], placed: true }
    let id = `reserve-${template.id}`
    while (used.has(id)) id += '-new'
    used.add(id)
    return { ...template, ...(reserveRotations[id] ? { w: template.h, h: template.w } : {}), id, placed: false }
  })
})
const coins = computed(() => isMilitary.value ? 300 - draft.ships.reduce((n,s)=>n+MILITARY_SHIPS[s.kind].price,0) : 300 - Object.entries(ITEMS).reduce((sum, [key, item]) => sum + item.price * (draft.inventory[key] || 0), 0))
const selectedShape = computed(() => { const ship = fleetRoster.value.find(s => s.id === selectedShip.value); return ship ? { w: rotated.value ? ship.h : ship.w, h: rotated.value ? ship.w : ship.h } : null })
const placementPreview = computed(() => {
  if (pendingTarget.value) return null
  if (placing.value && placementTool.value === 'ship' && selectedShape.value) {
    const ship = selectedShape.value
    return { ...ship, sprite: ({ 1: 'patrol', 2: 'destroyer', 3: 'cruiser', 4: 'battleship' })[Math.max(ship.w, ship.h)], sourceShipId: selectedShip.value, offset: dragAnchor.value }
  }
  if (shopping.value && ['mine', 'antiAir', 'stealth'].includes(placementTool.value)) {
    const selection = selectedDefense.value
    const source = selection?.tool === 'stealth' ? draft.ships.find(ship => ship.id === selection.shipId) : selection ? { x: selection.x || 0, y: selection.y, w: 1, h: 1 } : null
    return { w: 1, h: 1, sprite: placementTool.value, offset: null, source }
  }
  return null
})
const weaponRegion = computed(() => {
  if (isMilitary.value) { const a=militarySelectedAction.value,s=militarySource.value; const region=a?.weapon==='torpedo'?{ray:true}:a?.weapon==='fighter'?{w:6,h:1}:a?.weapon==='bomber'?{w:3,h:3}:a?.weapon==='depthCharge'?{w:2,h:1}:a?.weapon==='submarine'?{w:3,h:1}:a?.weapon==='radar'?{w:2,h:2}:a?.weapon==='mine'?{w:1,h:1,selected:pendingTarget.value?.targets||[]}:s?.kind==='battleship'?{w:s.w>1?3:1,h:s.w>1?1:3}:null; return region && pendingTarget.value?.kind==='attack' ? {...region,pending:pendingTarget.value.point}:region }
  const region = weapon.value === 'submarine' ? { w: 3, h: 1, sprite: 'submarine' } : weapon.value === 'fighter' ? { w: 8, h: 2 } : ['bomber', 'radar'].includes(weapon.value) ? { w: 3, h: 3 } : weapon.value === 'torpedo' ? { ray: true } : null
  return region && pendingTarget.value?.kind === 'attack' ? { ...region, pending: pendingTarget.value.point } : region
})
const seconds = computed(() => Math.min(room.value?.phase === 'BATTLE' ? 30 : Infinity, Math.max(0, Math.ceil(((room.value?.deadline || now.value) - now.value - serverOffset.value) / 1000))))
const clockText = computed(() => room.value?.phase === 'BATTLE' && activeTurn.value < 0 ? '' : `${Math.floor(seconds.value / 60)}:${String(seconds.value % 60).padStart(2, '0')}`)
const phaseName = computed(() => ({ WAITING: '等待对手', PLACEMENT: '舰队布局', SUPPLY: '战备补给', READY: '战备就绪', BATTLE: '交战海域', FINISHED: '行动结束' })[visualStage.value])
const statusText = computed(() => spectating.value ? '观战中' : room.value?.phase === 'BATTLE' ? room.value.turn === room.value.you ? '轮到你进攻' : '对手正在进攻' : waitingReady.value ? '等待对方完成战备' : shopping.value ? '战备补给' : placing.value ? '布置你的舰队' : phaseName.value)
const combatWeapons = ['shot', ...attackItems, 'depthCharge']
const ownEvents = computed(() => navalBoardEvents((room.value?.events || []).filter(e=>combatWeapons.includes(e.weapon)),selfIndex.value))
const enemyEvents = computed(() => navalBoardEvents((room.value?.events || []).filter(e=>combatWeapons.includes(e.weapon)),otherIndex.value))
const ownEvent = computed(() => ownEvents.value.at(-1))
const enemyEvent = computed(() => enemyEvents.value.at(-1))
let timer, pollTimer, draftTimer, launchTimer, announcementTimer, resultAudioTimer, presenceTimer, matchTimer, matchFoundTimer, disposed = false, polling = false, presencePolling = false, finishedId = '', epoch = 0, actionSequence = 0, inFlight = 0, launchAt = 0, matchAt = 0, queuedAnnouncement = ''
let pageToken = makePageToken()
let actionQueue = Promise.resolve()
let assetsPromise
let fullRoomTimer, settlementTimer, lastTickKey = ''
let scrollLock

function emptyInventory() { return Object.fromEntries(Object.keys(ITEMS).map(key => [key, 0])) }
function makePageToken() {
  if (crypto.randomUUID) return crypto.randomUUID()
  const bytes = crypto.getRandomValues(new Uint8Array(16)); bytes[6] = bytes[6] & 15 | 64; bytes[8] = bytes[8] & 63 | 128
  const hex = Array.from(bytes, value => value.toString(16).padStart(2, '0')).join('')
  return `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20)}`
}
function readJson(key, fallback) { try { return JSON.parse(localStorage.getItem(key)) ?? fallback } catch { return fallback } }
function writeJson(key, value) { try { localStorage.setItem(key, JSON.stringify(value)) } catch { error.value = '本机存储不可用，请检查浏览器存储空间' } }
function clone(value) { return JSON.parse(JSON.stringify(value)) }
function normalizeFormations(values) { return (Array.isArray(values) ? values : []).flatMap(f => { try { return [{ ...f, ships: normalizeSavedFleet(f.ships) }] } catch { return [] } }) }
function formatDate(value) { if (!value) return ''; const date = new Date(value); return Number.isNaN(date.getTime()) ? '' : date.toLocaleString('zh-CN', { month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' }) }
function sunkShips(player) { return (player?.ships || []).filter(s => s.hits?.length >= s.w * s.h).length }
function fleetTotal(player) { return isMilitary.value ? player?.totalShips ?? player?.ships?.length ?? 0 : 10 }
function remainingShips(player) { return Math.max(0,fleetTotal(player) - sunkShips(player)) }
function shipType(ship) { return ({ 1: 'patrol', 2: 'destroyer', 3: 'cruiser', 4: 'battleship' })[ship.w * ship.h] || 'patrol' }
function shipAsset(ship) { return ship.kind ? militaryAsset(ship.kind) : `/entertainment/naval/v2/${shipType(ship)}.png` }
function shipName(ship) { if(ship.kind) return MILITARY_SHIPS[ship.kind].name; return ({ patrol: '巡逻艇', destroyer: '驱逐舰', cruiser: '巡洋舰', battleship: '战列舰' })[shipType(ship)] }
function useFleetBackdrop() { harborFailed.value = true }
function fail(exception) { error.value = typeof exception === 'string' ? exception : exception?.message || '操作失败，请重试'; launching.value = false; clearTimeout(matchTimer); clearTimeout(matchFoundTimer); matchPresentation.value = '' }
function beginLaunch(label = '舰队正在入场') { clearTimeout(launchTimer); launchAt = Date.now(); launchLabel.value = label; launchProgress.value = 4; launching.value = true; unlockNavalAudio().catch(() => {}) }
function onBoardLoading(value) { if (launching.value) launchProgress.value = Math.max(launchProgress.value, 70 + Number(value) * 29) }
function finishLaunch() { if (!launching.value) return; launchProgress.value = 100; clearTimeout(launchTimer); launchTimer = setTimeout(() => { launching.value = false; revealAnnouncement() }, Math.max(180, 950 - (Date.now() - launchAt))) }
function showAnnouncement(label) {
  if (launching.value || matchPresentation.value) { queuedAnnouncement = label; return }
  clearTimeout(announcementTimer); announcement.value = label; playNavalSound('transition')
  announcementTimer = setTimeout(() => { announcement.value = '' }, 2400)
}
function revealAnnouncement() { if (!launching.value && !matchPresentation.value && queuedAnnouncement) { const value = queuedAnnouncement; queuedAnnouncement = ''; showAnnouncement(value) } }
function beginMatching(kind) {
  clearTimeout(matchTimer); clearTimeout(matchFoundTimer)
  matchKind.value = kind; matchAt = performance.now(); matchPresentation.value = 'SEARCHING'; matchNames.value = []
}
function showMatched(value) {
  if (!matchPresentation.value) beginMatching(value.autoMatch ? 'AUTO' : 'INVITE')
  clearTimeout(matchTimer); clearTimeout(matchFoundTimer)
  const revision = epoch
  matchNames.value = value.players.map(player => player.name);matchPeople.value=value.players
  matchTimer = setTimeout(() => {
    if (disposed || revision !== epoch) return
    matchPresentation.value = 'FOUND'; playNavalSound('ready')
    matchFoundTimer = setTimeout(() => { if (!disposed && revision === epoch) { matchPresentation.value = ''; revealAnnouncement() } }, 1500)
  }, Math.max(150, 1400 - (performance.now() - matchAt)))
}
function finishInitialSearch() {
  clearTimeout(matchTimer)
  const revision = epoch
  matchTimer = setTimeout(() => { if (!disposed && revision === epoch && room.value?.phase === 'WAITING') { matchPresentation.value = ''; revealAnnouncement() } }, Math.max(100, 1400 - (performance.now() - matchAt)))
}
async function refreshPresence() {
  if (disposed || presencePolling || document.hidden) return
  presencePolling = true
  notifyConnection(true)
  try {
    let value
    try { value = await heartbeatNavalPresence(pageToken) } catch (error) {
      if (Number(error.code) !== 403 || error.message !== '页面标识不属于当前玩家') throw error
      pageToken = makePageToken()
      value = await heartbeatNavalPresence(pageToken)
    }
    if (!disposed) onlineCount.value = Number.isInteger(value.onlineCount) ? value.onlineCount : null
  } catch { if (!disposed) onlineCount.value = null } finally { presencePolling = false }
}
function notifyConnection(connected) {
  if (!room.value || localMode.value || spectating.value || room.value.phase === 'FINISHED') return
  const id = String(room.value.id)
  notifyNavalConnection(id, pageToken, connected).then(value => {
    if (value?.cancelled && localStorage.getItem(`${storagePrefix}-room`) === id) {
      localStorage.removeItem(`${storagePrefix}-room`); resumeId.value = ''
    }
  })
}
function onPageHide() {
  if (localMode.value && room.value && preparing.value && !tutorialActive.value) { room.value._pausedAt ||= Date.now(); persist() }
  notifyConnection(false); leaveNavalPresence(pageToken)
}
function restoreLocalPreparation(value) {
  if (!value?._pausedAt || !['PLACEMENT', 'SUPPLY'].includes(value.phase)) return value
  const next = clone(value); next.deadline += Math.max(0, Date.now()-next._pausedAt); delete next._pausedAt; return next
}
function onVisibilityChange() { if (document.hidden) onPageHide(); else { if (localMode.value && room.value?._pausedAt) { room.value = restoreLocalPreparation(room.value); persist() }; refreshPresence(); poll() } }
function lockMobileScroll(active) {
  if (active && !scrollLock) {
    scrollLock = { html: document.documentElement.style.overflow, body: document.body.style.overflow, overscroll: document.body.style.overscrollBehavior, x: scrollX, y: scrollY }
    document.documentElement.style.overflow = 'hidden'; document.body.style.overflow = 'hidden'; document.body.style.overscrollBehavior = 'none'; window.scrollTo(0, 0)
  } else if (!active && scrollLock) {
    const previous = scrollLock; scrollLock = null
    document.documentElement.style.overflow = previous.html; document.body.style.overflow = previous.body; document.body.style.overscrollBehavior = previous.overscroll
    requestAnimationFrame(() => { if (!room.value) window.scrollTo(previous.x, previous.y) })
  }
}
function defaultMobilePanel() { return mobilePrep.value ? placing.value ? prepTab.value : shopping.value ? 'supply' : null : null }
function closeMobilePanel() { if (!mobile.value && room.value?.phase !== 'WAITING' && mobilePanel.value!=='audience') return; mobilePanel.value = defaultMobilePanel(); chatOpen.value = false; tutorialEvent({ type: 'PANEL', panel: mobilePanel.value }) }
function toggleMobilePanel(panel) {
  if(panel==='audience'&&!mobile.value)communityPanel.value=''
  if (mobilePrep.value && ['fleet', 'tools', 'supply'].includes(panel)) { if (['fleet', 'tools'].includes(panel) || isMilitary.value && panel==='supply') prepTab.value = panel; mobilePanel.value = panel }
  else mobilePanel.value = mobilePanel.value === panel ? defaultMobilePanel() : panel
  chatOpen.value = mobilePanel.value === 'chat'; if (chatOpen.value) unreadChat.value = 0
  tutorialEvent({ type: 'PANEL', panel: mobilePanel.value })
}
function openMobileDialog(name) { closeMobilePanel(); if (name === 'share') showShare.value = true; else if (name === 'rules') showRules.value = true; else requestLeave() }
function toggleChat() { if (room.value?.mode === 'AI') return; if (mobile.value || room.value?.phase === 'WAITING') { toggleMobilePanel('chat'); return }; chatOpen.value = !chatOpen.value; if (chatOpen.value) unreadChat.value = 0 }
function requestLeave() { if (tutorialActive.value) exitTutorial(); else showLeave.value = true }
function tutorialEvent(event) {
  if (!tutorialSession.value) return
  tutorialSession.value = (isMilitary.value ? advanceMilitaryTutorial : advanceNavalTutorial)({ ...tutorialSession.value, room: clone(room.value) }, event)
  room.value = clone(tutorialSession.value.room)
  weapon.value = tutorialSession.value.selectedWeapon
  persistTutorial(tutorialSession.value); tutorialSaved.value = tutorialSession.value
}
function runLocalAction(action) {
  if (isMilitary.value) return militaryLocalAction(clone(room.value), action)
  const prepared = tutorialActive.value ? prepareTutorialAction({ ...tutorialSession.value, room: clone(room.value) }, action, Date.now()) : { room: clone(room.value), action }
  return localAction(prepared.room, prepared.action, Date.now())
}
async function startTutorial(prepared = false, restart = false) {
  if (busy.value) return
  if (mobile.value && !prepared) { pendingStart.value = restart ? 'TUTORIAL_RESTART' : 'TUTORIAL'; await enableMobile(); return }
  if (blocked.value) return
  busy.value = true; epoch++; beginLaunch('进入舰队训练场')
  const revision = epoch
  try {
    await preloadAssets(); if (disposed || revision !== epoch) return
    tutorialSession.value = isMilitary.value ? (restart || !tutorialSaved.value || tutorialSaved.value.completed ? createMilitaryTutorial(Date.now(),tutorialOptions) : clone(tutorialSaved.value)) : restart || !tutorialSaved.value || tutorialSaved.value.completed ? createNavalTutorial(Date.now(), tutorialOptions) : loadNavalTutorial(undefined, tutorialOptions)
    const savedPanel = tutorialSession.value.flags.openPanel
    if (isMilitary.value) militaryTypes.destroyer = tutorialSession.value.flags.destroyerType || 'antiAir'
    tutorialSession.value.room.deadline = Date.now() + 3_600_000
    localMode.value = true; chatOpen.value = false
    accept(clone(tutorialSession.value.room), true); weapon.value = tutorialSession.value.selectedWeapon
    if (tutorialSession.value.flags.selectedShip && draft.ships.some(ship => ship.id === tutorialSession.value.flags.selectedShip)) selectedShip.value = tutorialSession.value.flags.selectedShip
    const lesson = isMilitary.value ? militaryTutorialStep(tutorialSession.value) : getNavalTutorialStep(tutorialSession.value)
    placementTool.value = lesson.id === 'coat' ? 'stealth' : lesson.id === 'place-mine' ? 'mine' : lesson.id === 'place-aa' ? 'antiAir' : placing.value ? 'ship' : 'inspect'
    prepTab.value = savedPanel === 'tools' ? 'tools' : isMilitary.value && savedPanel === 'supply' ? 'supply' : 'fleet'
    mobilePanel.value = mobile.value ? mobilePrep.value ? defaultMobilePanel() : savedPanel === 'weapons' ? 'weapons' : null : null
    if (mobile.value) tutorialSession.value.flags.openPanel = mobilePanel.value
    persistTutorial(tutorialSession.value)
  } catch (e) { if (!disposed) fail(e) } finally { busy.value = false }
}
function restartTutorial() { confirmation.value = { title: '重新开始教程', message: '当前演习进度会重置。', run: () => { exitTutorial(); startTutorial(false, true) } } }
function confirmOperation() { const operation = confirmation.value?.run; confirmation.value = null; operation?.() }
function exitTutorial() {
  if (!tutorialSession.value) return
  tutorialSession.value.room = clone(room.value); persistTutorial(tutorialSession.value); tutorialSaved.value = tutorialSession.value
  tutorialSession.value = null; stopNavalSounds(); backToLobby()
}
function selectWeapon(value) { pendingTarget.value = null; weapon.value = value; submarineTargets.value = []; tutorialEvent({ type: 'WEAPON', weapon: value }); closeMobilePanel() }
function preloadAssets() {
  if (assetsPromise) return assetsPromise
  const paths = ['patrol', 'destroyer', 'cruiser', 'battleship', 'fighter', 'bomber', 'torpedo', 'torpedo-projectile', 'mine', 'antiAir', 'radar', 'submarine', 'stealth', 'explosion', 'splash']
  let done = 0
  assetsPromise = Promise.all(paths.map(name => new Promise((resolve, reject) => {
    const image = new Image()
    image.onload = () => { launchProgress.value = Math.max(launchProgress.value, 10 + ++done / paths.length * 55); resolve() }
    image.onerror = () => reject(new Error(`游戏素材加载失败：${name}，请重试`))
    image.src = `/entertainment/naval/v2/${name}.png`
  }))).then(() => preloadNavalAudio()).catch(e => { assetsPromise = null; throw e })
  return assetsPromise
}
function toggleSound() { muted.value = !muted.value; setNavalMuted(muted.value); if (!muted.value) playNavalSound('sonar') }
function syncViewport() {
  const viewport = navalViewportState()
  fullscreen.value = viewport.native || viewport.standalone; iosFallback.value = viewport.iosFallback || iosFullscreenRejected.value && viewport.ios && !fullscreen.value; landscape.value = viewport.landscape
  if (root.value) for (const [name, value] of Object.entries({ height: viewport.height, width: viewport.width, top: viewport.top, left: viewport.left })) root.value.style.setProperty(`--game-${name}`, `${value}px`)
  if (communityPanel.value) nextTick(positionCommunity)
}
async function enterFullscreen() {
  fullscreenError.value = ''
  try {
    if (iosFallback.value) immersive.value = true
    else if (!fullscreen.value) {
      const request = root.value.requestFullscreen || root.value.webkitRequestFullscreen
      if (!request) throw new Error('此浏览器不支持全屏，请使用支持全屏的浏览器或电脑')
      await request.call(root.value)
    }
    if (mobile.value && screen.orientation?.lock) { try { await screen.orientation.lock('landscape') } catch { /* Rotation may require a physical device gesture. */ } }
  } catch (e) {
    if (navalViewportState().ios) { iosFullscreenRejected.value = true; iosFallback.value = true; immersive.value = true }
    else fullscreenError.value = e.message || '无法开启全屏，请检查浏览器设置'
  }
  syncViewport()
}
async function completePending() {
  if (blocked.value || !pendingStart.value || busy.value) return
  const action = pendingStart.value; pendingStart.value = null
  if (action === 'JOIN') await join(true)
  else if (action === 'AUDIENCE') { const code=pendingAudienceCode.value; pendingAudienceCode.value=''; await enterAudience(code) }
  else if (action === 'RESUME') await resume(true)
  else if (action === 'DIRECTORY') await enterDirectory(pendingDirectory.value, true)
  else if (action === 'TUTORIAL' || action === 'TUTORIAL_RESTART') await startTutorial(true, action === 'TUTORIAL_RESTART')
  else await start(action, true)
}
async function enableMobile() { await enterFullscreen(); await completePending() }
function cancelMobile() { pendingStart.value = null; if (replay.value) closeReplay(); else if (room.value) requestLeave() }
async function localMilitaryGame(){
  const value=createMilitaryGame()
  let template
  try{template=await getMilitaryTemplate()}catch{}
  if(!template?.ships){const candidates=readJson('naval-guest-history',[]).map(h=>militaryRecordTemplate(h.detail)).filter(Boolean);template=candidates[Math.floor(Math.random()*candidates.length)]}
  installMilitaryTemplate(value,template)
  return value
}
async function start(mode, prepared = false) {
  if (busy.value) return
  if (requestResumeDecision(() => start(mode))) return
  if (mobile.value && !prepared) { pendingStart.value = mode; await enableMobile(); return }
  if (mobile.value && blocked.value) return
  busy.value = true; error.value = ''; epoch++; beginLaunch()
  if (mode === 'MULTIPLAYER' && autoMatch.value) beginMatching('AUTO')
  const requestEpoch = epoch
  try {
    await preloadAssets()
    if (disposed || requestEpoch !== epoch) return
    localMode.value = mode === 'AI' && !loggedIn
    const value = localMode.value ? (isMilitary.value ? await localMilitaryGame() : createLocalGame()) : await createNavalRoom({ mode, autoMatch: mode === 'MULTIPLAYER' && autoMatch.value, ruleSet: ruleSet.value })
    if (localMode.value) {value.presentationSync = true;value.players[value.you||0].medals=ownNavalMedals.value;value.players[value.you||0].self=true}
    if (disposed || requestEpoch !== epoch) return
    accept(value, true)
    if (room.value.phase === 'WAITING') { finishLaunch(); if (matchPresentation.value) finishInitialSearch() }
    else if (matchPresentation.value) showMatched(value)
    playNavalSound('join')
  } catch (e) { fail(e) } finally { busy.value = false }
}
async function join(prepared = false) {
  if (busy.value) return
  if (requestResumeDecision(() => join())) return
  if (mobile.value && !prepared) { pendingStart.value = 'JOIN'; await enableMobile(); return }
  if (mobile.value && blocked.value) return
  busy.value = true; error.value = ''; epoch++; beginLaunch('正在加入舰队'); beginMatching('INVITE')
  const requestEpoch = epoch
  try {
    await preloadAssets(); if (disposed || requestEpoch !== epoch) return
    localMode.value = false
    const value = await joinNavalRoom(inviteCode.value)
    if (disposed || requestEpoch !== epoch) return
    accept(value, true)
    if (value.phase === 'WAITING') { finishLaunch(); finishInitialSearch() }
    else showMatched(value)
    playNavalSound('join')
  } catch (e) { if (!disposed) { if (String(e?.message || '').includes('房间已满')) scheduleSpectating(inviteCode.value); else fail(e) } } finally { busy.value = false }
}
async function resume(prepared = false) {
  if (busy.value) return
  if (mobile.value && !prepared) { pendingStart.value = 'RESUME'; await enableMobile(); return }
  if (mobile.value && blocked.value) return
  busy.value = true; epoch++; beginLaunch('正在恢复战局')
  const requestEpoch = epoch
  try {
    localMode.value = Boolean(localResume.value) && (!resumeId.value || localStorage.getItem(`${storagePrefix}-resume-kind`) === 'LOCAL')
    let value
    try {
      if (localMode.value) {
        value=localResume.value.ruleSet === 'MILITARY' ? restoreLocalPreparation(localResume.value) : migrateLocalGame(restoreLocalPreparation(localResume.value))
        value.presentationSync=value.ruleSet !== 'MILITARY'
        if (value.phase === 'BATTLE') { if(value.ruleSet==='MILITARY'){value.presentationUntil=0;value.deadline=Date.now()+30000}else{value.presentationId=value._sequence;value.presentationUntil=Date.now()+15000;value.deadline=value.presentationUntil+30000} }
      } else value=await getNavalRoom(resumeId.value)
    }
    catch (e) { const previous = readJson(`${storagePrefix}-spectator`, null); if (e.code !== 403 || String(previous?.id) !== resumeId.value) throw e; value = await spectateNavalRoom(previous.inviteCode, previous.viewMode) }
    if (!localMode.value && value.phase !== 'FINISHED') notifyNavalConnection(value.id, pageToken, true)
    await preloadAssets()
    if (disposed || requestEpoch !== epoch) return
    accept(value, true); if (room.value.phase === 'WAITING') finishLaunch()
  } catch (e) { if (!disposed) fail(e) } finally { busy.value = false }
}
function cancelSpectating() { clearInterval(fullRoomTimer); fullRoom.value = null }
function scheduleSpectating(code) {
  launching.value = false; clearTimeout(matchTimer); clearTimeout(matchFoundTimer); matchPresentation.value = ''; queuedAnnouncement = ''
  cancelSpectating(); fullRoom.value = code; fullRoomSeconds.value = 3
  fullRoomTimer = setInterval(() => { fullRoomSeconds.value--; if (fullRoomSeconds.value <= 0) { const target = fullRoom.value; cancelSpectating(); enterAudience(target) } }, 1000)
}
async function enterAudience(code) {
  if (!code || busy.value) return
  busy.value = true; epoch++; beginLaunch('正在进入观众席'); const revision = epoch
  try { await preloadAssets(); const value = await spectateNavalRoom(code); if (!disposed && revision === epoch) { localMode.value = false; accept(value, true); if (value.phase === 'WAITING') finishLaunch(); playNavalSound('join') } } catch (e) { if (!disposed) fail(e) } finally { busy.value = false }
}
async function changeSpectatorView(viewMode) { if (spectating.value && viewMode !== room.value.viewMode) await dispatch({ type: 'SPECTATE_VIEW', viewMode }) }
async function claimSeat(seat) { if (spectating.value && await dispatch({ type: 'TAKE_SEAT', seat })) closeMobilePanel() }
async function continueGame(value = true) {
  if (spectating.value || me.value?.left || busy.value) return
  if (localMode.value) { epoch++; const revision=epoch;showSettlement.value = false; beginLaunch('舰队再次出航');const next=isMilitary.value ? await localMilitaryGame() : createLocalGame();if(disposed||revision!==epoch)return;next.players[next.you||0].medals=ownNavalMedals.value;next.players[next.you||0].self=true;accept(next, true); playNavalSound('join') }
  else await dispatch({ type: 'CONTINUE', value })
}
function onAttackKey(event) {
  if (!event.repeat && !event.altKey && !event.ctrlKey && !event.metaKey && event.key.toLowerCase() === 'r' && canArrange.value && !event.target?.closest?.('input, textarea, select, [contenteditable="true"], [role="textbox"]')) { event.preventDefault(); rotateShip(); return }
  if (event.repeat || event.altKey || event.ctrlKey || event.metaKey || !canAttack.value || showFormation.value || showSettlement.value) return
  if (event.target?.closest?.('input, textarea, select, [contenteditable="true"], [role="textbox"]')) return
  if(isMilitary.value){const index=event.code?.startsWith('Numpad')?Number(event.code.slice(6)):Number(event.key);const action=militaryAvailable.value[index-1];if(!/^[1-9]$/.test(String(index))||!action)return;event.preventDefault();selectMilitaryAction(action);playNavalSound('ui');return}
  const key = combatWeapons[Number(event.key) - 1]
  if (!key || !/^[1-6]$/.test(event.key) || key !== 'shot' && !me.value?.inventory?.[key]) return
  event.preventDefault(); selectWeapon(key); playNavalSound('ui')
}
function joinFromLink() {
  const code = String(route.query.invite || '').toUpperCase()
  if (!/^[A-Z0-9]{6}$/.test(code) || room.value || busy.value || fullRoom.value) return
  inviteCode.value = code
  if (mobile.value) pendingStart.value = 'JOIN'
  else join()
}
function accept(value, initialize = false) {
  if (!value || disposed) return
  ruleSet.value = value.ruleSet || 'CLASSIC'
  if (initialize) communityPanel.value = ''
  const previousPhase = room.value?.phase
  if (previousPhase === 'WAITING' && value.phase === 'FINISHED' && value.winner == null && !initialize) {
    resumeId.value = ''; localStorage.removeItem(`${storagePrefix}-room`); backToLobby(); error.value = '匹配已取消，房间已关闭'; return
  }
  const previousStage = room.value?.players?.[selfIndex.value]?.stage
  const previousVisual = ['PLACEMENT', 'SUPPLY'].includes(previousPhase) ? previousStage : previousPhase
  const changed = room.value?.id !== value.id
  const newRound = changed || (room.value?.round || 1) !== (value.round || 1)
  if (newRound) { mobilePanel.value = null; chatOpen.value = false }
  if (newRound || value.phase !== previousPhase || value.turn !== room.value?.turn) pendingTarget.value = null
  const previousMessages = new Set((room.value?.messages || []).map(message => message.id))
  const previousPlayers = (room.value?.players || []).map(player => player.id)
  const previousRole = room.value?.role
  if (newRound) { clearTimeout(settlementTimer); showSettlement.value = false; showShare.value = false; chatPops[0] = null; chatPops[1] = null; weapon.value = 'shot' }
  serverOffset.value = !localMode.value && value.serverTime ? value.serverTime - Date.now() : 0
  if (value.mode === 'AI' && value.players?.[1]) value.players[1].ai = true
  if (localMode.value && value.players?.[value.you || 0]) { value.players[value.you || 0].self = true; value.players[value.you || 0].medals = ownNavalMedals.value }
  room.value = value
  if (initialize || changed) notifyConnection(true)
  for (const index of [0, 1]) if (previousPlayers[index] && previousPlayers[index] !== value.players[index]?.id) { clearTimeout(chatPopTimers[index]); chatPops[index] = null }
  if (!spectating.value && (initialize || newRound || previousRole !== value.role || (preparing.value && (!['PLACEMENT', 'SUPPLY'].includes(previousPhase) || me.value?.stage !== previousStage)))) {
    draft.ships = clone(Array.isArray(me.value?.ships) ? me.value.ships : generateFleet())
    draft.inventory = { ...emptyInventory(), ...me.value?.inventory }
    draft.mines = clone(me.value?.mines || []); draft.aa = [...(me.value?.aa || [])]
    selectedShip.value = fleetRoster.value[0]?.id || ''; placementTool.value = shopping.value ? 'inspect' : 'ship'; rotated.value = false
  }
  if (!spectating.value && !tutorialActive.value && value.phase === 'PLACEMENT' && (previousPhase === 'WAITING' || initialize || newRound)) showMatched(value)
  if (mobile.value && (initialize || newRound || visualStage.value !== previousVisual)) {
    if (newRound) prepTab.value = isMilitary.value ? 'supply' : 'fleet'
    mobilePanel.value = defaultMobilePanel(); chatOpen.value = false
    tutorialEvent({ type: 'PANEL', panel: mobilePanel.value })
  }
  if ((newRound || visualStage.value !== previousVisual) && ['PLACEMENT', 'SUPPLY', 'BATTLE'].includes(visualStage.value)) {
    ownAnimating.value = false; enemyAnimating.value = false
    showAnnouncement(({ PLACEMENT: spectating.value ? '舰队布局' : '布置你的舰队', SUPPLY: '战备补给', BATTLE: '舰队出击' })[visualStage.value])
  }
  if (!initialize && !newRound) for (const message of value.messages || []) if (!previousMessages.has(message.id) && [0, 1].includes(message.player)) {
    if (message.senderId && value.players[message.player]?.id && message.senderId !== value.players[message.player].id) continue
    const index = message.player; clearTimeout(chatPopTimers[index]); chatPops[index] = message
    if (!chatOpen.value) unreadChat.value++
    chatPopTimers[index] = setTimeout(() => { chatPops[index] = null }, 5500)
  }
  if(!spectating.value&&value.players?.[value.you]?.medals?.medalId){ownNavalMedals.value=value.players[value.you].medals;value.players[value.you].self=true}
  persist()
  const gameId = value.gameId || `${value.id}:${value.round || 1}`
  if (!tutorialActive.value && value.phase === 'FINISHED' && finishedId !== gameId) {
    refreshNavalMedals();finishedId = gameId
    const resultYou = value.resultYou ?? value.you
    const delay = value.players.some(player => player.left) ? 0 : value.events?.at(-1)?.weapon === 'victory' ? 850 : 0
    const revealSettlement = () => {
      if (disposed || room.value?.phase !== 'FINISHED') return
      if (visualBusy.value || announcement.value) { settlementTimer = setTimeout(revealSettlement, 150); return }
      showSettlement.value = true
    }
    clearTimeout(settlementTimer); settlementTimer = setTimeout(revealSettlement, delay)
    if (!spectating.value && resultYou >= 0 && value.winner != null) { clearTimeout(resultAudioTimer); resultAudioTimer = setTimeout(() => { if (!disposed) playNavalSound(value.winner === resultYou ? 'win' : 'lose') }, delay) }
    if (!loggedIn && !spectating.value && resultYou >= 0 && value.winner != null) recordGuestResult(value, localMode.value)
    else if (!spectating.value) refreshProfile()
  }
}
let mirrorTimer, mirrorHeartbeat, mirrorPending = null, mirrorSending = false
function queueLocalMirror() {
  if (!localMode.value || !room.value || tutorialActive.value || disposed) return
  mirrorPending = clone(room.value)
  clearTimeout(mirrorTimer); mirrorTimer = setTimeout(flushLocalMirror, 180)
}
async function flushLocalMirror() {
  if (mirrorSending || !mirrorPending) return
  const snapshot = mirrorPending; mirrorPending = null; mirrorSending = true
  try { const result = await publishLocalNavalRoom(snapshot); if (localMode.value && room.value?.id === snapshot.id && room.value.allowSpectators !== false) room.value.spectators = result.spectators || [] }
  catch { /* Offline local play remains available; the next heartbeat retries publication. */ }
  finally { mirrorSending = false; if (mirrorPending) flushLocalMirror() }
}
onMounted(() => { mirrorHeartbeat = setInterval(() => { if (!document.hidden) queueLocalMirror() }, 4000) })
onUnmounted(() => { clearTimeout(mirrorTimer); clearInterval(mirrorHeartbeat); mirrorPending = null })
function persist() {
  if (!room.value) return
  if (tutorialSession.value) { tutorialSession.value = { ...tutorialSession.value, room: clone(room.value) }; persistTutorial(tutorialSession.value); tutorialSaved.value = tutorialSession.value; return }
  if (localMode.value) { writeJson('naval-guest-session', room.value); localStorage.setItem(`${storagePrefix}-resume-kind`, 'LOCAL'); localResume.value = room.value.phase === 'FINISHED' ? null : room.value; if (!localResume.value) localStorage.removeItem('naval-guest-session'); queueLocalMirror() }
  else { resumeId.value = room.value.phase === 'FINISHED' || !spectating.value && me.value?.left ? '' : String(room.value.id); localStorage.setItem(`${storagePrefix}-resume-kind`, 'ONLINE'); if (resumeId.value) localStorage.setItem(`${storagePrefix}-room`, resumeId.value); else localStorage.removeItem(`${storagePrefix}-room`); if (!loggedIn) writeJson('naval-guest-last-online', room.value) }
  if (!localMode.value && spectating.value) writeJson(`${storagePrefix}-spectator`, { id: room.value.id, inviteCode: room.value.inviteCode, viewMode: room.value.viewMode })
  else localStorage.removeItem(`${storagePrefix}-spectator`)
}
async function dispatch(action, quiet = false) {
  if (!room.value || (busy.value && !quiet)) return false
  const requestEpoch = epoch
  const id = room.value.id
  const isLocal = localMode.value
  actionSequence++; inFlight++
  if (!quiet) busy.value = true
  const operation = actionQueue.then(async () => {
    if (requestEpoch !== epoch) return false
    const value = isLocal ? runLocalAction(action) : await navalAction(id, action)
    if (requestEpoch === epoch) { accept(value); connectionError.value = ''; tutorialEvent({ type: 'ACTION', action, result: value }) }
    return true
  })
  actionQueue = operation.catch(() => {})
  try { return await operation } catch (e) { if (requestEpoch === epoch) fail(e); return false } finally { inFlight--; if (!quiet) busy.value = false }
}
function deployment(ready = false) { return { type: 'DEPLOY', ships: clone(draft.ships), inventory: { ...draft.inventory }, mines: clone(draft.mines), aa: [...draft.aa], coatedShips: draft.ships.filter(ship => ship.stealth).map(ship => ship.id), ready } }
function queueDeployment() {
  clearTimeout(draftTimer)
  if (localMode.value) { try { accept(runLocalAction(deployment())) } catch (e) { fail(e) }; return }
  draftTimer = setTimeout(() => { if (preparing.value && playerStage.value !== 'READY') dispatch(deployment(), true) }, 350)
}
function selectShip(ship) { cancelTarget(); selectedShip.value = ship.id; placementTool.value = 'ship'; rotated.value = false; tutorialEvent({ type: 'SELECT_SHIP', shipId: ship.id }) }
function rotatePendingShip(){
  const pending=pendingTarget.value;if(!pending||pending.kind!=='placement'||!canArrange.value)return
  const ship=pending.preview.ships.find(s=>s.id===selectedShip.value);if(!ship)return
  const next={...ship,w:ship.h,h:ship.w,x:Math.min(ship.x,12-ship.h),y:Math.min(ship.y,12-ship.w),...(isMilitary.value?{rotation:((ship.rotation||0)+90)%360}:{})}
  if(!(isMilitary.value?militaryPlacement(pending.preview.ships,next):legalPlacement(pending.preview.ships.filter(s=>s.id!==ship.id),next))){error.value='旋转后不能越界或与其他舰船相邻';return}
  pending.preview.ships=pending.preview.ships.map(s=>s.id===ship.id?next:s);pending.shape=next;pending.point={x:next.x,y:next.y};playNavalSound('ui')
}
function rotateShip(ship = fleetRoster.value.find(ship => ship.id === selectedShip.value)) {
  if (isMilitary.value) {
    if (!canArrange.value || !ship) return
    const next={...ship, rotation:((ship.rotation||0)+90)%360,w:ship.h,h:ship.w}
    if (ship.x>=0 && !militaryPlacement(draft.ships,next)) { error.value='旋转后不能越界或与其他舰船相邻'; return }
    draft.ships=draft.ships.map(s=>s.id===ship.id?next:s);selectedShip.value=ship.id;rotated.value=false;queueDeployment();tutorialEvent({type:'ROTATE_SHIP'});return
  }
  if (!canArrange.value || !ship || ship.w === ship.h) return
  error.value = ''
  selectedShip.value = ship.id; placementTool.value = 'ship'; pendingTarget.value = null
  if (!ship.placed) { reserveRotations[ship.id] = !reserveRotations[ship.id]; rotated.value = false; playNavalSound('ui'); tutorialEvent({ type: 'ROTATE_SHIP' }); return }
  const next = { ...ship, w: ship.h, h: ship.w, x: Math.min(ship.x, 12 - ship.h), y: Math.min(ship.y, 12 - ship.w) }
  if (!legalPlacement(draft.ships, next) || draft.mines.some(m => m.x >= next.x && m.x < next.x + next.w && m.y >= next.y && m.y < next.y + next.h)) { rotated.value = !rotated.value; error.value = '旋转位置不足，请点选新的空位'; tutorialEvent({ type: 'ROTATE_SHIP' }); return }
  draft.ships = draft.ships.map(value => value.id === ship.id ? { ...next, hits: [] } : value); rotated.value = false
  playNavalSound('ui'); queueDeployment(); tutorialEvent({ type: 'ROTATE_SHIP' })
}
function clearLayout() {
  if (!canArrange.value) return
  if (isMilitary.value) { draft.ships=draft.ships.map(s=>({...s,x:-1,y:-1}));cancelTarget();queueDeployment();tutorialEvent({type:'CLEAR_FLEET'});return }
  if (!canArrange.value) return
  cancelTarget()
  draft.ships = []; draft.mines = []; draft.aa = []; draft.inventory = emptyInventory(); formationId.value = ''
  selectedShip.value = fleetRoster.value[0]?.id || ''; rotated.value = false; placementTool.value = 'ship'
  playNavalSound('ui'); queueDeployment(); tutorialEvent({ type: 'CLEAR_FLEET' })
}
function randomize() { if (!canArrange.value) return; if(isMilitary.value) { try {draft.ships=arrangeMilitaryFleet(draft.ships);cancelTarget();queueDeployment();tutorialEvent({type:'RANDOMIZE'})} catch(e){fail(e)};return }; cancelTarget(); draft.ships = generateFleet(); draft.mines = []; draft.aa = []; rotated.value = false; selectedShip.value = draft.ships[0]?.id || ''; playNavalSound('ui'); queueDeployment(); tutorialEvent({ type: 'RANDOMIZE' }) }
function purchaseMilitary(kind,count) {
  if (!canArrange.value) return
  const model=MILITARY_SHIPS[kind], owned=draft.ships.filter(s=>s.kind===kind)
  if(count>0) {
    if(!canBuyMilitary(kind))return
    const ship={id:`${kind}-${Date.now().toString(36)}`,kind,w:model.w,h:model.h,x:-1,y:-1,rotation:0,hits:[],lostPlanes:[]}
    draft.ships.push(ship);selectedShip.value=ship.id;placementTool.value='ship'
  } else if(owned.length) draft.ships=draft.ships.filter(s=>s.id!==owned.at(-1).id)
  cancelTarget();queueDeployment();playNavalSound('purchase');tutorialEvent({type:'PURCHASE',key:kind})
}
function purchase(key, count) {
  if (!canShop.value) return
  const number = (draft.inventory[key] || 0) + count
  if (number < 0 || number > ITEMS[key].max || (count > 0 && coins.value < ITEMS[key].price)) return
  cancelTarget()
  draft.inventory[key] = number
  if (key === 'mine') draft.mines = draft.mines.slice(0, number)
  if (key === 'antiAir') draft.aa = draft.aa.slice(0, number)
  if (key === 'stealth') { let retained = 0; draft.ships.forEach(ship => { if (ship.stealth) ship.stealth = ++retained <= number }) }
  if (count > 0 && ['mine', 'antiAir', 'stealth'].includes(key)) placementTool.value = key
  playNavalSound('purchase')
  queueDeployment()
  tutorialEvent({ type: 'PURCHASE', key })
}
function selectPlacedDefense(x, y) {
  const ship = draft.ships.find(s => x >= s.x && x < s.x+s.w && y >= s.y && y < s.y+s.h)
  const tool = placementTool.value
  let selected
  if (draft.mines.some(m => m.x === x && m.y === y)) selected = { tool: 'mine', x, y }
  else if ((tool === 'antiAir' || x === 0) && draft.aa.includes(y)) selected = { tool: 'antiAir', y }
  else if (ship?.stealth) selected = { tool: 'stealth', shipId: ship.id }
  if (!selected) return false
  cancelTarget(); placementTool.value = selected.tool; selectedDefense.value = selected
  return true
}
function placeCell({ x, y, dragStart, dragging }, confirmed = false) {
  if (!canArrange.value && !canDeployDefense.value) return
  if (dragStart) {
    cancelTarget()
    if (canArrange.value) {
      const ship = draft.ships.find(s => x >= s.x && x < s.x+s.w && y >= s.y && y < s.y+s.h)
      if (ship) { selectShip(ship); dragAnchor.value = { x: x-ship.x, y: y-ship.y } }
    } else selectPlacedDefense(x, y)
    return
  }
  error.value = ''
  if (!dragging && !selectedDefense.value && canShop.value && selectPlacedDefense(x, y)) return
  if (placementTool.value === 'inspect') return
  const previous = clone(draft)
  let shape = { w: 1, h: 1 }
  if (placementTool.value === 'stealth') {
    const ship = draft.ships.find(s => x >= s.x && x < s.x + s.w && y >= s.y && y < s.y + s.h)
    if (!ship) { error.value = '请选择己方舰船涂装'; return }
    if (selectedDefense.value?.shipId === ship.id) return
    const source = draft.ships.find(s => s.id === selectedDefense.value?.shipId)
    if (ship.stealth) { error.value = '该舰船已经涂装，请选择其他舰船'; return }
    if (!source && draft.ships.filter(s => s.stealth).length >= draft.inventory.stealth) { error.value = '隐身图层已全部涂装'; return }
    if (source) source.stealth = false
    ship.stealth = true
    shape = { x: ship.x, y: ship.y, w: ship.w, h: ship.h }
  } else if (placementTool.value === 'mine') {
    const source = selectedDefense.value
    const index = source ? draft.mines.findIndex(m => m.x === source.x && m.y === source.y) : -1
    if (draft.mines.some(m => m.x === x && m.y === y)) return
    if (draft.ships.some(s => x >= s.x && x < s.x + s.w && y >= s.y && y < s.y + s.h)) error.value = '水雷不能与舰船重叠'
    else if (index >= 0) draft.mines[index] = { x, y }
    else if (draft.mines.length >= draft.inventory.mine) error.value = '可部署水雷不足'
    else draft.mines.push({ x, y })
  } else if (placementTool.value === 'antiAir') {
    const index = selectedDefense.value ? draft.aa.indexOf(selectedDefense.value.y) : -1
    if (draft.aa.includes(y)) return
    if (index >= 0) draft.aa[index] = y
    else if (draft.aa.length >= draft.inventory.antiAir) error.value = '可部署防空炮不足'
    else draft.aa.push(y)
    shape = { x: 0, y, w: 1, h: 1 }
  } else {
    const clicked = draft.ships.find(s => x >= s.x && x < s.x + s.w && y >= s.y && y < s.y + s.h)
    if (clicked && clicked.id !== selectedShip.value) { selectShip(clicked); return }
    const ship = fleetRoster.value.find(s => s.id === selectedShip.value)
    if (!ship) return
    const next = { ...(isMilitary.value ? ship : {}), id: ship.id, ...selectedShape.value, x, y, hits: [] }
    shape = next
    if (!(isMilitary.value ? militaryPlacement(draft.ships, next) : legalPlacement(draft.ships.filter(s => s.id !== ship.id), next))) { error.value = '此位置不可用：舰船间须空一格，且不能超出海域'; return }
    if (draft.mines.some(m => m.x >= x && m.x < x + next.w && m.y >= y && m.y < y + next.h)) { error.value = '此位置已部署水雷'; return }
    draft.ships = ship.placed || isMilitary.value ? draft.ships.map(s => s.id === ship.id ? next : s) : [...draft.ships, next]; rotated.value = false
  }
  if (!error.value && mobile.value && !confirmed) {
    pendingTarget.value = { kind: 'placement', point: { x, y }, shape, preview: clone(draft), tool: placementTool.value }
    Object.assign(draft, previous); return
  }
  if (!error.value) { pendingTarget.value = null; selectedDefense.value = null; dragAnchor.value = null; playNavalSound('ui'); queueDeployment(); tutorialEvent({ type: placementTool.value === 'stealth' ? 'COAT_SHIP' : placementTool.value === 'mine' ? 'PLACE_MINE' : placementTool.value === 'antiAir' ? 'PLACE_AA' : 'MOVE_SHIP' }) }
}
async function confirmTarget() {
  const pending = pendingTarget.value
  if (!pending) return
  if (pending.kind === 'placement') {
    if (!canArrange.value && !canDeployDefense.value) { cancelTarget(); return }
    pendingTarget.value = null; selectedDefense.value = null; dragAnchor.value = null; placementTool.value = pending.tool; Object.assign(draft, clone(pending.preview))
    playNavalSound('ui'); queueDeployment(); tutorialEvent({ type: pending.tool === 'stealth' ? 'COAT_SHIP' : pending.tool === 'mine' ? 'PLACE_MINE' : pending.tool === 'antiAir' ? 'PLACE_AA' : 'MOVE_SHIP' })
  } else { pendingTarget.value = null; await attack({...pending.point,...(pending.targets?{targets:pending.targets}:{})}, true) }
}
function cancelTarget() { pendingTarget.value = null; submarineTargets.value = []; selectedDefense.value = null; dragAnchor.value = null }
async function advanceLayout() {
  if (isMilitary.value) { const invalid=validateMilitaryFleet(draft.ships);if(invalid){error.value=invalid;return};clearTimeout(draftTimer);closeMobilePanel();await dispatch(deployment(true));return }
  const invalid = validateFleet(draft.ships)
  if (invalid) { error.value = invalid; return }
  clearTimeout(draftTimer)
  closeMobilePanel()
  await dispatch({ type: 'LAYOUT_READY', ships: clone(draft.ships) })
}
async function ready() {
  if (isMilitary.value) return advanceLayout()
  if (!canShop.value) return
  const invalid = validateFleet(draft.ships)
  if (invalid) { error.value = typeof invalid === 'string' ? invalid : '阵型不完整'; return }
  if (draft.mines.length !== draft.inventory.mine || draft.aa.length !== draft.inventory.antiAir) { error.value = '请先部署全部已购买的水雷与防空炮'; return }
  if (draft.ships.filter(ship => ship.stealth).length !== (draft.inventory.stealth || 0)) { error.value = '请先涂好已购买的隐身图层'; return }
  clearTimeout(draftTimer)
  closeMobilePanel()
  await dispatch(deployment(true))
}
async function attack(point, confirmed = false) {
  if (point.dragStart) return
  if (!canAttack.value) return
  const region = weaponRegion.value
  if (region?.w && (point.x + region.w > 12 || point.y + region.h > 12)) { error.value = '攻击区域超出海域，请选择更靠左上方的位置'; return }
  if(isMilitary.value&&militarySelectedAction.value?.weapon==='mine'&&!confirmed){
    pendingTarget.value={kind:'attack',point,weapon:'mine',targets:[point]};return
  }
  if (!confirmed) pendingTarget.value = null
  if (weapon.value === 'submarine' && !confirmed) {
    submarineTargets.value = Array.from({ length: 3 }, (_, i) => ({ x: point.x + i, y: point.y }))
    tutorialEvent({ type: 'SUBMARINE_TARGET', targets: clone(submarineTargets.value) })
    try { submarineFootprint(submarineTargets.value) } catch (e) { error.value = e.message; return }
  }
  if (mobile.value && (isMilitary.value ? militarySource.value?.kind !== 'cruiser' || militarySelectedAction.value?.weapon !== 'shot' : weapon.value !== 'shot') && !confirmed) { pendingTarget.value = { kind: 'attack', point, weapon: weapon.value }; return }
  error.value = ''; unlockNavalAudio().catch(() => {})
  const usedWeapon = isMilitary.value ? militarySelectedAction.value?.weapon : weapon.value
  if(!usedWeapon) {error.value='舰队已无可用进攻功能';return}
  const success = await dispatch({ type: 'ATTACK', ...(isMilitary.value ? militarySelectedAction.value : {}), weapon: usedWeapon, ...point, ...(usedWeapon === 'submarine' ? { targets: clone(submarineTargets.value) } : {}) })
  if (success && usedWeapon !== 'shot' && !isMilitary.value) { weapon.value = 'shot'; submarineTargets.value = []; tutorialEvent({ type: 'WEAPON', weapon: 'shot' }) }
}
async function sendBattleSticker(id) { if (spectating.value || me.value?.left || busy.value) return; await dispatch({ type: 'CHAT', text: stickerMessage(id) }) }
async function shareToLobby() { if (!room.value || room.value.phase !== 'WAITING' || busy.value || room.value.lobbyShared) return; try { await sendNavalLobbyMessage({ roomId: room.value.id }); room.value.lobbyShared = true; closeMobilePanel(); communityPanel.value = 'chat'; await nextTick(); positionCommunity(); error.value = '房间已分享到大厅' } catch (e) { fail(e) } }
const pendingDirectory = ref(null)
async function enterDirectory(card, prepared = false) {
  if (!card || room.value || busy.value) return
  if (requestResumeDecision(() => enterDirectory(card))) return
  if (mobile.value && !prepared) { pendingDirectory.value = card; pendingStart.value = 'DIRECTORY'; await enableMobile(); return }
  if (mobile.value && blocked.value) return
  busy.value = true; epoch++; beginLaunch('正在连接海域'); const revision = epoch
  try { await preloadAssets(); const value = await enterNavalRoom(card.roomId, card.inviteCode); if (!disposed && revision === epoch) { localMode.value = false; accept(value, true); if (value.phase === 'WAITING') finishLaunch(); playNavalSound('join') } } catch (e) { fail(e) } finally { busy.value = false; pendingDirectory.value = null }
}
async function joinLobbyRoom(card) { if (card.roomId) return enterDirectory(card); if (room.value || busy.value || !card.inviteCode) return; communityPanel.value = ''; inviteCode.value = card.inviteCode; if (card.status === 'STARTED') { if (mobile.value && blocked.value) { pendingAudienceCode.value = card.inviteCode; pendingStart.value = 'AUDIENCE'; await enableMobile() } else await enterAudience(card.inviteCode) } else await join() }
async function toggleSpectators(value) { if (spectating.value || tutorialActive.value || busy.value) return; if (localMode.value) accept({ ...room.value, allowSpectators: value, spectators: value ? room.value.spectators : [] }); else await dispatch({ type: 'SPECTATORS', value }) }
const pendingAudienceCode = ref('')
async function sendChat() { const text = chatText.value.trim(); if (!text || spectating.value || me.value?.left) return; if (await dispatch({ type: 'CHAT', text })) { chatText.value = ''; await nextTick(); if (chatLog.value) chatLog.value.scrollTop = chatLog.value.scrollHeight } }
async function copyInvite() { try { await navigator.clipboard.writeText(room.value.inviteCode); error.value = '邀请码已复制' } catch { error.value = `邀请码：${room.value.inviteCode}` } }
async function leave() { if (tutorialActive.value) { exitTutorial(); return }; clearTimeout(draftTimer); const observer = spectating.value; if (await dispatch({ type: 'LEAVE' }) || observer) { resumeId.value = ''; localStorage.removeItem(`${storagePrefix}-room`); showLeave.value = false; backToLobby() } }
function backToLobby() { epoch++; mobilePanel.value = null; room.value = null; clearTimeout(draftTimer); clearTimeout(launchTimer); clearTimeout(matchTimer); clearTimeout(matchFoundTimer); clearTimeout(announcementTimer); clearTimeout(settlementTimer); clearTimeout(resultAudioTimer); cancelSpectating(); Object.values(chatPopTimers).forEach(clearTimeout); chatPops[0] = null; chatPops[1] = null; matchPresentation.value = ''; announcement.value = ''; queuedAnnouncement = ''; launching.value = false; pendingStart.value = null; showLeave.value = false; showSettlement.value = false; showShare.value = false; confirmation.value = null; connectionError.value = ''; chatOpen.value = false; unreadChat.value = 0; ownAnimating.value = false; enemyAnimating.value = false; finishedId = '' }
function requestResumeDecision(run = null) {
  if (room.value || !resumeId.value && !localResume.value) return false
  resumeDecision.value = { run }; return true
}
function continueSavedGame() { resumeDecision.value = null; resume() }
function recordGuestResult(value, local) {
  if (loggedIn || value.winner == null || value.mode === 'AI' && !(value.battleStarted || value.settlement?.battleStarted || value.events?.some(event => event.weapon === 'start'))) return
  const gameId = value.gameId || `${value.id}:${value.round || 1}`
  history.value = [{ id: gameId, gameId, local, detail: local ? {...localBattleResult(value),ruleSet:value.ruleSet||'CLASSIC',battleStarted:value.battleStarted||value.events?.length>0} : null, mode: value.mode, ruleSet:value.ruleSet||'CLASSIC', won: value.winner === (value.resultYou ?? value.you ?? 0), at: Date.now() }, ...history.value.filter(item => item.id !== gameId)].slice(0, 100)
  writeJson('naval-guest-history', history.value)
}
async function exitSavedGame() {
  if (busy.value) return
  const run = resumeDecision.value?.run
  busy.value = true
  try {
    if (resumeId.value) {
      try { const value = await navalAction(resumeId.value, { type: 'LEAVE' }); if (value.role !== 'SPECTATOR') recordGuestResult(value, false) }
      catch (e) { if (![403, 404].includes(e.code)) throw e }
    }
    if (localResume.value) recordGuestResult(localResume.value.ruleSet==='MILITARY'?militaryLocalAction(localResume.value,{type:'LEAVE'}):localAction(restoreLocalPreparation(localResume.value), { type: 'LEAVE' }), true)
    resumeId.value = ''; localResume.value = null
    localStorage.removeItem(`${storagePrefix}-room`); localStorage.removeItem(`${storagePrefix}-resume-kind`); localStorage.removeItem(`${storagePrefix}-spectator`)
    if (!loggedIn) localStorage.removeItem('naval-guest-session')
    resumeDecision.value = null
    if (loggedIn) refreshProfile()
  } catch (e) { fail(e); return } finally { busy.value = false }
  run?.()
}
function loadFormation() {
  if (!canArrange.value) return
  const formation = formations.value.find(f => f.id === formationId.value)
  if (!formation) return
  if (validateFleet(formation.ships)) { error.value = '已保存阵型不符合规则'; return }
  cancelTarget(); rotated.value = false; placementTool.value = 'ship'
  draft.ships = clone(formation.ships).map(s => ({ ...s, hits: [] })); draft.mines = []
  selectedShip.value = draft.ships[0]?.id || ''; queueDeployment()
}
async function saveFormation() {
  if(isMilitary.value){error.value='军事舰队按本局采购部署';return}
  if (!formationName.value) return
  if (!fullFleet.value) { error.value = '请先布置完整舰队后再保存阵型'; return }
  if (tutorialActive.value) { error.value = '教程阵型不写入正式档案'; return }
  const value = { id: String(Date.now()), name: formationName.value, ships: clone(draft.ships).map(s => ({ ...s, hits: [] })) }
  const updated = [value, ...formations.value.filter(f => f.name !== value.name)]
  if (updated.length > 20) { error.value = '最多保存 20 个阵型，请先删除一个已有阵型'; return }
  try { if (loggedIn) await saveNavalProfile({ formations: updated }); else writeJson('naval-guest-formations', updated); formations.value = updated; showFormation.value = false; formationName.value = '' } catch (e) { fail(e) }
}
async function deleteFormation() {
  const formation = formations.value.find(f => f.id === formationId.value)
  if (!formation) return
  confirmation.value = { title: '删除阵型', message: `删除阵型“${formation.name}”？`, run: () => removeFormation(formation.id) }
}
async function removeFormation(id) {
  const updated = formations.value.filter(f => f.id !== id)
  try { if (loggedIn) await saveNavalProfile({ formations: updated }); else writeJson('naval-guest-formations', updated); formations.value = updated; formationId.value = '' } catch (e) { fail(e) }
}
async function refreshProfile() { if (!loggedIn) return; try { const value = await getNavalProfile(); formations.value = normalizeFormations(value.formations || []); history.value = value.history || [] } catch (e) { fail(e) } }
async function poll() {
  if (!room.value || localMode.value || busy.value || inFlight || polling || document.hidden) return
  polling = true; const requestEpoch = epoch, sequence = actionSequence
  try {
    let value
    try { value = await getNavalRoom(room.value.id) }
    catch (e) { if (e.code !== 403 || !spectating.value || epoch !== requestEpoch) throw e; value = await spectateNavalRoom(room.value.inviteCode, room.value.viewMode) }
    if (epoch === requestEpoch && sequence === actionSequence) { accept(value); connectionError.value = '' }
  } catch (e) { if (epoch === requestEpoch) { if (spectating.value && (e.code === 404 || String(e.message).includes('关闭观战'))) { resumeId.value = ''; localStorage.removeItem(`${storagePrefix}-room`); backToLobby(); error.value = e.message } else connectionError.value = '连接中断，正在重试' } } finally { polling = false }
}
watch(() => room.value?.messages?.at(-1)?.id, async () => { await nextTick(); if (chatLog.value) chatLog.value.scrollTop = chatLog.value.scrollHeight })
watch([fullscreen, landscape], completePending)
watch(() => mobile.value && Boolean(room.value || replay.value), lockMobileScroll, { flush: 'post' })
watch(() => route.query.invite, joinFromLink)
let noticeTimer
const noticeSerial = ref(0)
watch(error, value => { clearTimeout(noticeTimer); if (value) { noticeSerial.value++; noticeTimer = setTimeout(() => { error.value = '' }, 3000) } })
watch(placementTool, () => cancelTarget(), { flush: 'sync' })
let presentationTimer, presentedKey = ''
watch(() => [room.value?.presentationId, room.value?.presentationUntil, visualBusy.value, launching.value, bannerBusy.value, blocked.value], () => {
  clearTimeout(presentationTimer)
  if (!room.value?.presentationUntil || spectating.value || visualBusy.value || launching.value || bannerBusy.value || blocked.value) return
  const key = `${room.value.id}:${room.value.round}:${room.value.presentationId}`
  if (key === presentedKey) return
  const id = room.value.presentationId, game = room.value.id
  presentationTimer = setTimeout(async () => {
    if (room.value?.id !== game || room.value.presentationId !== id || visualBusy.value || bannerBusy.value) return
    presentedKey = key
    if (!await dispatch({ type: 'PRESENTED', presentationId: id }, true)) presentedKey = ''
  }, 0)
}, { flush: 'post' })
onUnmounted(() => clearTimeout(presentationTimer))
onUnmounted(() => clearTimeout(noticeTimer))
watch([seconds, activeTurn, visualStage], ([value]) => {
  const timed = room.value?.phase === 'BATTLE' ? activeTurn.value === selfIndex.value : preparing.value && !me.value?.ready
  if (value < 1 || value > 5 || !timed || spectating.value || tutorialActive.value || document.hidden) return
  const key = `${room.value.gameId || room.value.id}:${room.value.deadline}:${room.value.turn}:${value}`
  if (lastTickKey !== key) { lastTickKey = key; playNavalSound('tick') }
})
onMounted(() => {
  syncViewport(); document.addEventListener('fullscreenchange', syncViewport); document.addEventListener('webkitfullscreenchange', syncViewport); window.addEventListener('resize', syncViewport)
  window.visualViewport?.addEventListener('resize', syncViewport)
  window.visualViewport?.addEventListener('scroll', syncViewport)
  refreshProfile(); refreshPresence(); joinFromLink(); window.addEventListener('keydown', onAttackKey)
  window.addEventListener('pagehide', onPageHide); document.addEventListener('visibilitychange', onVisibilityChange)
  presenceTimer = setInterval(refreshPresence, 8000)
  timer = setInterval(() => { now.value = Date.now(); if(room.value && tutorialActive.value && isMilitary.value && !visualBusy.value && !busy.value) { const next=tickMilitaryTutorial(clone(room.value),now.value);if(JSON.stringify(next)!==JSON.stringify(room.value))accept(next) }; if (room.value && localMode.value && !tutorialActive.value && !busy.value && !document.hidden) { try { const value = isMilitary.value ? tickMilitaryGame(clone(room.value),now.value) : tickLocalGame(clone(room.value), now.value); if (JSON.stringify(value) !== JSON.stringify(room.value)) accept(value) } catch (e) { fail(e) } } }, 250)
  pollTimer = setInterval(poll, 1200)
})
onUnmounted(() => { window.visualViewport?.removeEventListener('resize', syncViewport); window.visualViewport?.removeEventListener('scroll', syncViewport) })
onUnmounted(() => { disposed = true; lockMobileScroll(false); epoch++; clearInterval(timer); clearInterval(pollTimer); clearInterval(presenceTimer); clearTimeout(draftTimer); clearTimeout(launchTimer); clearTimeout(announcementTimer); clearTimeout(resultAudioTimer); clearTimeout(matchTimer); clearTimeout(matchFoundTimer); clearTimeout(settlementTimer); cancelSpectating(); Object.values(chatPopTimers).forEach(clearTimeout); onPageHide(); stopNavalSounds(); document.removeEventListener('fullscreenchange', syncViewport); document.removeEventListener('webkitfullscreenchange', syncViewport); window.removeEventListener('resize', syncViewport); window.removeEventListener('pagehide', onPageHide); window.removeEventListener('keydown', onAttackKey); document.removeEventListener('visibilitychange', onVisibilityChange); if (document.fullscreenElement === root.value) document.exitFullscreen?.().catch(() => {}) })
</script>

<style scoped>
.sticker-tray{min-height:0}.sticker-tray:not(:empty){height:min(220px,38dvh);min-height:70px;flex:0 1 auto;overflow:auto;overscroll-behavior:contain}.sticker-tray:empty{display:none}.battle-chat>.naval-stickers{margin-top:8px;margin-bottom:6px}.chat-message{padding-bottom:8px}.spectator-toggle{display:flex;align-items:center;gap:8px;padding:10px;background:#edf4f6;border-radius:4px;color:#63818a;font-size:12px;min-height:44px}.spectator-toggle input{margin-left:auto;accent-color:#45836b;width:17px;height:17px}.spectator-toggle span{min-width:0}
@media(max-height:450px){.battle-chat:has(.sticker-picker)>.chat-log,.battle-chat:has(.sticker-picker)>.naval-stickers,.battle-chat:has(.sticker-picker)>.naval-emoji{display:none}.battle-chat:has(.sticker-picker) .sticker-tray:not(:empty){min-height:110px;flex:1}}
</style>

<style scoped>
.is-mobile:not(.in-game) .naval-header{gap:10px;flex-wrap:wrap}.is-mobile:not(.in-game) .naval-title{gap:6px}.is-mobile:not(.in-game) .naval-title h1{font-size:18px}.is-mobile:not(.in-game) .naval-title>svg{width:18px}
.naval-primary-content{min-width:0;min-height:0}.naval-page.community-open:not(.is-mobile){display:grid;grid-template-columns:minmax(0,1fr) minmax(340px,410px);gap:22px;max-width:1840px;align-items:start}.naval-page.community-open:not(.is-mobile) .naval-header{flex-wrap:wrap;gap:10px}.mobile-game>.naval-primary-content{display:flex;flex:1;flex-direction:column;min-height:0}.community-sheet-enter-active,.community-sheet-leave-active{transition:opacity .28s,transform .28s}.community-sheet-enter-from,.community-sheet-leave-to{opacity:0;transform:translateX(35px)}
.fleet-roster{align-content:start;flex:none}.fleet-command{align-items:center}.fleet-command>.fleet-rotate{height:32px;aspect-ratio:1}.formation-tools{align-self:start}
.naval-page{--sea:#e7f1f4;--ink:#263c43;max-width:1440px;min-height:100dvh;margin:auto;padding:24px;color:var(--ink);background:#f6f8f9;letter-spacing:0}.naval-page:fullscreen,.naval-page.is-fullscreen{max-width:none;overflow:auto;background:#f6f8f9}.naval-header{display:flex;align-items:center;gap:20px;padding-bottom:18px;border-bottom:1px solid #dce4e7}.naval-title{display:flex;align-items:center;gap:11px;flex-shrink:0;color:#de7921}.naval-title h1{font-size:23px;color:#263c43;line-height:1.2}.naval-title span{font-size:11px;color:#839397}.header-actions{display:flex;gap:5px;margin-left:auto}.icon-control{display:inline-flex;align-items:center;justify-content:center;width:34px;height:34px;flex-shrink:0;border-radius:5px;background:transparent;color:#63777e}.icon-control:hover{background:#e9eff1;color:#cf701e}button:disabled{opacity:.4;cursor:not-allowed}.match-status{display:flex;align-items:center;justify-content:center;gap:16px;margin-left:auto;font-size:14px}.clock{display:flex;align-items:center;gap:6px;color:#648088;font-family:monospace;font-size:17px;font-weight:700}.urgent{color:#d44f42}.category-tabs{display:flex;gap:25px;border-bottom:1px solid #dce4e7;padding-top:10px}.category-tabs a,.category-tabs>span{display:flex;align-items:center;gap:8px;padding:12px 2px;color:#708087;font-size:14px;border-bottom:2px solid transparent}.category-tabs .active{color:#db7823;border-color:#e68a37;font-weight:700}.naval-lobby{max-width:1050px;margin:24px auto}.lobby-sea{position:relative;height:220px;overflow:hidden;background:#d8edf0;border-radius:6px}.lobby-sea canvas{width:100%;height:100%;object-fit:cover;image-rendering:pixelated}.sea-label{position:absolute;left:22px;top:18px;font:11px monospace;color:#527d88}.lobby-heading{display:flex;justify-content:space-between;align-items:center;gap:16px;margin:27px 0 20px}.lobby-heading h2{font-size:20px}.lobby-heading p{font-size:12px;color:#809095;margin-top:5px}.lobby-heading p span{padding:0 8px}.record-summary{font-size:12px;color:#73878d}.lobby-modes{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:20px}.mode-entry{min-width:0;border:1px solid #dce4e7;border-radius:6px;background:white;padding:22px;display:flex;flex-direction:column;align-items:flex-start;gap:14px}.mode-entry>svg{color:#5d9b9d}.mode-entry:nth-child(2)>svg{color:#df8843}.mode-entry:nth-child(3)>svg{color:#b07a9c}.mode-entry h3{font-size:17px}.mode-entry p,.match-toggle{font-size:13px;color:#78878b;min-height:32px;display:flex;align-items:center;gap:9px}.match-toggle input{accent-color:#ed8b30;width:17px;height:17px}.mode-entry>.naval-primary,.mode-entry>.naval-secondary{align-self:stretch;margin-top:auto}.naval-primary,.naval-secondary{display:inline-flex;align-items:center;justify-content:center;gap:7px;min-height:38px;padding:8px 13px;font-size:13px;font-weight:650;border:1px solid transparent;border-radius:5px;white-space:normal;text-align:center}.naval-primary{background:#ec8b33;border-color:#ec8b33;color:#fff}.naval-primary:hover:not(:disabled){background:#d77a27}.naval-secondary{background:white;border-color:#d2dfe3;color:#587078}.invite-input,.naval-dialog input{background:#f7f9fa;border:1px solid #d9e2e5;border-radius:4px;padding:8px 10px;width:100%;min-width:0;font-size:13px;min-height:34px}.resume-line{display:flex;align-items:center;gap:10px;border-block:1px solid #dce4e7;margin:24px 0;padding:12px 0;font-size:13px}.resume-line>span{display:flex;align-items:center;gap:9px;flex:1}.history-section{margin-top:30px}.history-section h3{font-size:14px;margin-bottom:12px}.history-row{display:flex;gap:24px;padding:11px 0;border-top:1px solid #e3e9eb;font-size:12px;color:#7a8c91}.history-row time{margin-left:auto}.victory{color:#378e70}.defeat{color:#b56b62}.room-strip{display:flex;gap:15px;align-items:center;min-height:46px;font-size:11px;color:#7b9199}.room-strip>span,.invite-code{display:flex;gap:6px;align-items:center}.invite-code{border:1px dashed #e6ba8b;background:#fff8ef;padding:4px 8px;border-radius:4px;color:#be7837;font:12px monospace}.storage-status{margin-left:auto}.storage-status.offline{color:#c6614c}.battle-layout{display:grid;grid-template-columns:minmax(0,1fr) 260px;gap:22px}.battlefield{min-width:0}.boards{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:20px}.board-panel{min-width:0}.board-panel>header{display:flex;justify-content:space-between;align-items:center;gap:5px;margin-bottom:10px}.board-panel h2{display:flex;align-items:center;gap:7px;font-size:14px;min-width:0;overflow-wrap:anywhere}.board-panel>header>span{font-size:11px;color:#7e939a;white-space:nowrap}.enemy-panel h2{color:#ad625a}.battle-sidebar{min-width:0;display:flex;flex-direction:column;border-left:1px solid #dce4e7;padding-left:20px}.sidebar-heading{display:flex;justify-content:space-between;align-items:center;margin-bottom:12px}.sidebar-heading h3{font-size:14px}.sidebar-heading strong{display:flex;align-items:center;gap:5px;font-size:17px;color:#cc832e}.fleet-selector{display:grid;grid-template-columns:repeat(4,1fr);gap:4px;margin-bottom:15px}.fleet-selector button{height:30px;display:flex;align-items:center;justify-content:center;gap:5px;border:1px solid #d9e4e7;border-radius:3px;background:white;font-size:10px;color:#62858d}.fleet-selector button.selected{border-color:#ec8b33;background:#fff1e4;color:#c77226}.supply-list{border-block:1px solid #e0e7ea}.supply-row{display:flex;align-items:center;gap:5px;padding:9px 0;color:#67919a}.supply-name{flex:1;min-width:0;text-align:left;background:transparent;color:#4b626a}.supply-name:disabled{opacity:1;cursor:default}.supply-name strong{font-size:12px;font-weight:600;display:block}.supply-name small{display:block;font-size:9px;color:#87999e;margin-top:2px}.supply-name.active strong{color:#ce7e32}.quantity-control{display:flex;align-items:center;justify-content:center;flex-shrink:0;width:22px;height:24px;border:1px solid #dce5e8;border-radius:3px;background:#fff;color:#647e87}.quantity{font:12px monospace;min-width:14px;text-align:center}.deployment-summary{display:flex;justify-content:space-between;font-size:10px;color:#8a9a9f;padding:10px 0}.placement-controls{display:flex;align-items:center;justify-content:space-between;gap:10px;padding-top:14px}.formation-tools{display:flex;align-items:center;gap:5px}.formation-tools select{font-size:12px;max-width:150px;background:#fff;border:1px solid #d5e1e5;border-radius:4px;padding:7px;color:#5c7680}.ready-button{min-width:120px}.weapons{display:flex;gap:6px;padding:14px 0}.weapons>button{flex:1;min-width:0;display:flex;align-items:center;justify-content:center;gap:7px;min-height:48px;padding:7px 5px;border:1px solid #dbe4e8;border-radius:5px;background:#fff;color:#66818a;font-size:12px}.weapons>button.selected{background:#fff2e6;border-color:#eca561;color:#bf752d}.weapons small{font:11px monospace;background:#edf2f3;min-width:18px;line-height:20px;border-radius:3px}.battle-feed{display:flex;flex-direction:column;gap:5px;min-height:55px;padding:12px 0;font-size:11px;color:#7e949c}.battle-chat{display:flex;flex-direction:column;min-height:170px;flex:1;margin-top:12px}.battle-chat h3{display:flex;align-items:center;gap:7px;font-size:12px;padding-bottom:10px;border-bottom:1px solid #dfe7ea}.chat-log{height:180px;min-height:100px;flex:1;overflow:auto;padding:12px 0;max-height:400px}.chat-empty{font-size:11px;color:#99aaaf}.chat-message{font-size:11px;margin-bottom:12px;overflow-wrap:anywhere}.chat-message>span{font-size:10px;color:#92a1a6}.chat-message>p{margin-top:3px;white-space:pre-wrap;color:#526e78}.chat-message.mine>span{color:#c08c5b}.battle-chat form{display:flex;align-items:center;gap:4px;border:1px solid #d8e3e7;border-radius:4px;background:#fff;padding:2px 4px 2px 9px}.battle-chat input{width:0;flex:1;min-width:0;border:0;font-size:12px;background:transparent;color:#55717c}.result-line{display:flex;align-items:center;gap:15px;padding:20px 0;color:#cc8b35}.result-line h2{font-size:20px}.result-line span{font-size:12px;color:#8b9da2}.result-line button{margin-left:auto}.waiting-line{display:flex;align-items:center;gap:10px;font-size:13px;padding:20px 0;color:#74929b}.waiting-line button{margin-left:auto}.waiting-dot{width:7px;height:7px;border-radius:50%;background:#d99348;animation:pulse 1s alternate infinite}.radar-spin{animation:spin 2.5s linear infinite}.naval-notice{position:fixed;z-index:600;left:50%;bottom:30px;transform:translateX(-50%);display:flex;align-items:center;gap:9px;width:max-content;max-width:calc(100vw - 30px);padding:8px 10px 8px 15px;background:#fff;border:1px solid #edc7a4;border-radius:6px;box-shadow:0 4px 20px #29465315;font-size:13px;color:#9d6837}.connecting{display:flex;gap:8px;align-items:center;justify-content:center;padding:15px;color:#a57446;font-size:13px}.device-gate,.naval-modal-mask{position:fixed;z-index:500;inset:0;background:#f3f7f9;display:flex;flex-direction:column;align-items:center;justify-content:center;padding:24px;gap:16px;text-align:center}.device-gate>svg{color:#e39147;transform:rotate(90deg)}.device-gate h2{font-size:22px}.device-gate p{font-size:13px;color:#7a9099;max-width:350px}.device-gate button{min-width:190px}.naval-modal-mask{background:#233a4866;z-index:550;text-align:left;backdrop-filter:blur(2px)}.naval-dialog{background:#fff;border:1px solid #d7e3e8;border-radius:8px;padding:22px;width:380px;max-width:100%;max-height:88dvh;overflow:auto;display:flex;flex-direction:column;gap:16px;box-shadow:0 14px 50px #1e37432b}.naval-dialog h2{font-size:18px}.naval-dialog header{display:flex;align-items:center;justify-content:space-between;gap:20px}.naval-dialog p{font-size:13px;line-height:1.8;color:#647a83}.naval-dialog footer{display:flex;justify-content:flex-end;gap:8px}.rules-dialog{width:560px;gap:12px}.rules-dialog dt{font-size:12px;font-weight:700;margin-top:12px}.rules-dialog dd{font-size:12px;color:#69838d;line-height:1.8;margin-top:2px}@keyframes spin{to{transform:rotate(360deg)}}@keyframes pulse{to{opacity:.35}}
@media(min-width:1600px){.in-game .battle-layout{grid-template-columns:minmax(0,1fr) 280px}.in-game .boards{gap:28px}}
@media(max-width:1190px){.naval-page{padding:18px}.battle-layout{grid-template-columns:minmax(0,1fr) 220px;gap:16px}.battle-sidebar{padding-left:14px}.boards{gap:14px}.supply-row{gap:3px}.supply-name small{font-size:8px}.weapons>button{gap:4px}.weapons>button span{font-size:11px}.lobby-modes{gap:12px}.mode-entry{padding:17px}}
@media(max-width:700px) and (orientation:portrait){.naval-page{padding:16px}.naval-header{gap:8px}.naval-title h1{font-size:21px}.lobby-sea{height:150px}.lobby-modes{grid-template-columns:1fr;gap:12px}.mode-entry{display:grid;grid-template-columns:28px 1fr;align-items:center;gap:10px 16px}.mode-entry>svg{grid-row:1/3}.mode-entry h3{font-size:16px}.mode-entry p,.mode-entry .match-toggle{min-height:24px}.mode-entry>button{grid-column:1/-1;width:100%;margin-top:5px}.mode-entry .invite-input{grid-column:2;grid-row:2}.lobby-heading{align-items:flex-start}.record-summary{font-size:10px;max-width:100px;text-align:right}.lobby-heading h2{font-size:18px}.match-status{font-size:11px;gap:8px}.header-actions{gap:0}.header-actions .icon-control{width:30px}.in-game .naval-header{flex-wrap:wrap}.in-game .match-status{order:3;width:100%}.boards{gap:10px}.battle-layout{display:block}.battle-sidebar{padding-left:0;border-left:0}.naval-notice{bottom:80px}}
@media(max-height:600px) and (orientation:landscape){.naval-page.in-game{padding:8px 14px;min-height:100dvh}.in-game .naval-header{padding-bottom:6px;gap:14px;height:43px}.in-game .naval-title h1{font-size:18px}.in-game .naval-title span{font-size:9px}.in-game .naval-title>svg{width:20px}.in-game .room-strip{min-height:27px;font-size:10px}.in-game .match-status{font-size:12px}.in-game .header-actions .icon-control{width:29px;height:29px}.battle-layout{grid-template-columns:minmax(0,1fr) 218px;gap:14px}.boards{gap:14px;max-width:calc((100dvh - 146px)*2 + 14px);margin:auto}.board-panel>header{margin-bottom:5px}.board-panel h2{font-size:11px;gap:4px}.board-panel>header>span{font-size:9px}.board-panel h2>svg{width:13px}.battle-sidebar{padding-left:12px;max-height:calc(100dvh - 86px);overflow:auto}.sidebar-heading{margin-bottom:5px}.sidebar-heading h3{font-size:11px}.sidebar-heading strong{font-size:14px}.fleet-selector{margin-bottom:5px;gap:3px}.fleet-selector button{height:23px;font-size:9px}.fleet-selector svg{width:11px}.supply-row{padding:4px 0}.supply-row>svg{width:15px}.supply-name strong{font-size:10px}.supply-name small{font-size:8px;margin:0}.quantity-control{width:20px;height:21px}.deployment-summary{font-size:8px;padding:5px 0}.battle-chat{margin-top:5px;min-height:125px}.battle-chat h3{font-size:10px;padding-bottom:5px}.chat-log{max-height:170px;min-height:45px;height:70px;padding:5px 0}.chat-message{margin-bottom:5px;font-size:10px}.battle-chat input{font-size:10px}.battle-chat .icon-control{height:25px;width:25px}.battle-chat h3>svg{width:12px}.placement-controls{padding-top:7px;gap:6px}.formation-tools{gap:2px}.formation-tools .icon-control{width:27px;height:28px}.formation-tools select{padding:4px;max-width:110px;font-size:10px}.ready-button{min-width:100px;min-height:30px;padding:5px 9px;font-size:11px}.weapons{padding:7px 0;gap:4px}.weapons>button{min-height:32px;padding:4px;gap:4px;font-size:10px}.weapons>button>svg{width:15px}.weapons>button span{font-size:10px}.weapons small{min-width:15px;font-size:9px;line-height:16px}.battle-feed{font-size:9px;padding:4px 0;min-height:20px}.battle-feed>span:not(:first-child){display:none}.waiting-line{padding:7px 0;font-size:11px}.waiting-line .naval-secondary{font-size:10px;min-height:28px;padding:4px 8px}.result-line{padding:7px 0}.result-line h2{font-size:15px}.result-line span{font-size:10px}.result-line .naval-primary{min-height:30px;padding:4px 8px;font-size:11px}.naval-notice{bottom:12px;font-size:11px;padding:5px 8px}.rules-dialog{width:620px}.lobby-sea{height:140px}.naval-lobby{margin:14px auto}}
@media(max-height:380px) and (orientation:landscape){.battle-layout{grid-template-columns:minmax(0,1fr) 198px}.boards{max-width:calc((100dvh - 136px)*2 + 14px)}.supply-row{padding:3px 0}.fleet-selector button{height:21px}.in-game .room-strip{min-height:24px}.in-game .naval-header{height:39px}}
</style>
<style scoped>
.fleet-command{flex:0 0 40px}.fleet-command>button:first-child{height:40px;padding:3px 6px}.fleet-command strong{line-height:16px}.fleet-command small{line-height:14px}.fleet-command img{width:30px;height:30px}.fleet-command>.fleet-rotate>svg{margin:0}
</style>


<style scoped>
.lobby-sea>img{width:100%;height:100%;object-fit:cover;image-rendering:pixelated;object-position:center 55%}.lobby-sea .sea-label{color:#f2f8f4;text-shadow:1px 1px #244957}.stage-steps{display:flex;align-items:center;justify-content:center;gap:38px;min-height:44px;margin:-2px 0 15px;border-block:1px solid #e0e7ea;font-size:12px;color:#9aa8ad}.stage-steps>span{display:flex;align-items:center;gap:8px;min-height:42px;border-bottom:2px solid transparent}.stage-steps i{font:11px monospace;font-style:normal;opacity:.7}.stage-steps .current{color:#c57b33;border-bottom-color:#ed9b4f;font-weight:700}.stage-steps .complete{color:#548779}.battle-layout.preparation{grid-template-columns:minmax(0,580px) 310px;justify-content:center;gap:36px}.boards.single-board{display:block;width:min(100%,560px,calc(100dvh - 280px));margin:0 auto}.preparation .battle-sidebar{padding-left:25px}.fleet-total{font-size:11px;color:#8b9a9e}.fleet-roster{display:flex;flex-direction:column;gap:3px;margin-bottom:10px;max-height:430px;overflow:auto}.fleet-roster>button{display:flex;align-items:center;gap:12px;min-height:39px;padding:4px 10px;border:1px solid transparent;border-radius:4px;background:transparent;text-align:left;color:#637d87}.fleet-roster>button.selected{background:#fff2e6;border-color:#efc399;color:#b27032}.fleet-roster img{width:36px;height:36px;object-fit:contain;image-rendering:pixelated;transform:rotate(90deg)}.fleet-roster strong{display:block;font-size:12px}.fleet-roster small{font:10px monospace;color:#96a5aa}.fleet-roster>button>svg{margin-left:auto}.formation-select{width:150px;max-width:180px}.formation-select :deep(.custom-select-trigger){height:34px;font-size:12px;border-color:#d5e1e5}.supply .supply-row{min-height:61px;gap:10px}.supply-sprite{width:33px;height:33px;object-fit:contain;image-rendering:pixelated}.supply .quantity-control{width:28px;height:30px}.supply .supply-name strong{font-size:13px}.supply .supply-name small{font-size:10px}.supply-controls{display:flex;justify-content:space-between;align-items:center;gap:10px;padding:14px 0}.supply-controls>span{display:flex;align-items:center;gap:10px;font-size:11px;color:#718b95}.supply-controls i{height:10px;border-left:1px solid #c8d5da}.ready-wait{display:flex;align-items:center;justify-content:center;gap:12px;padding:18px;font-size:13px;color:#51877d}.waiting-dock{position:relative;height:420px;overflow:hidden;border-radius:5px}.waiting-dock>img{width:100%;height:100%;object-fit:cover;image-rendering:pixelated;filter:brightness(.75)}.matching-radar{position:absolute;inset:0;display:flex;flex-direction:column;align-items:center;justify-content:center;gap:20px;color:white;text-shadow:0 1px 2px #153848}.matching-radar>svg{animation:spin 6s linear infinite}.matching-radar>span{font-size:15px}.matching-radar>strong{font:28px monospace;letter-spacing:0}.stage-scene-enter-active,.stage-scene-leave-active{transition:opacity .24s ease,transform .24s ease}.stage-scene-enter-from{opacity:0;transform:translateY(16px)}.stage-scene-leave-to{opacity:0;transform:translateY(-12px)}.launch-overlay{position:fixed;inset:0;z-index:400;background:#e9f2f5;display:flex;flex-direction:column;align-items:center;justify-content:center;color:#365c69;pointer-events:all}.launch-water{position:relative;width:180px;height:150px;overflow:hidden;margin-bottom:20px}.launch-water>img{position:absolute;top:10px;left:60px;width:60px;height:130px;object-fit:contain;image-rendering:pixelated;animation:ship-depart 1.8s ease-in-out infinite;z-index:1}.launch-wake{position:absolute;left:80px;bottom:0;width:20px;height:80px;background:repeating-linear-gradient(0deg,#fff9 0 3px,transparent 3px 8px);transform:perspective(80px) rotateX(25deg);animation:wake-flow .45s linear infinite}.launch-kicker{font:10px monospace;color:#7e9aa6;margin-bottom:10px}.launch-overlay h2{font-size:22px;font-weight:650}.load-track{width:220px;height:4px;background:#cbdde3;margin:25px 0 10px;overflow:hidden}.load-track>span{display:block;height:100%;background:#df9850;transition:width .2s ease}.load-percent{font:11px monospace;color:#7b96a1}.launch-enter-active{transition:opacity .12s}.launch-leave-active{transition:opacity .38s ease,transform .38s ease}.launch-enter-from,.launch-leave-to{opacity:0}.launch-leave-to{transform:scale(1.025)}.phase-announcement{position:fixed;z-index:300;left:50%;top:42%;transform:translate(-50%,-50%);display:flex;align-items:center;justify-content:center;gap:13px;min-width:250px;padding:18px 36px;border-block:1px solid #b3d2d7;background:#eff7f8f2;box-shadow:0 6px 30px #3154601a;color:#497985;pointer-events:none}.phase-announcement strong{font-size:21px}.announcement-enter-active,.announcement-leave-active{transition:opacity .3s,top .3s}.announcement-enter-from,.announcement-leave-to{opacity:0;top:46%}.battle-chat{min-height:130px}.preparation .chat-log{max-height:170px;height:120px}.preparation .battle-chat{flex:0 1 auto;margin-top:auto}.naval-notice{z-index:650}.naval-page .device-gate{z-index:500}.naval-page .naval-modal-mask{z-index:550}@keyframes ship-depart{0%,100%{transform:translateY(5px)}50%{transform:translateY(-5px)}}@keyframes wake-flow{to{background-position:0 8px}}
@media(max-width:1100px){.battle-layout.preparation{grid-template-columns:minmax(0,1fr) 280px;gap:24px}.preparation .battle-sidebar{padding-left:18px}}
@media(max-width:700px) and (orientation:portrait){.stage-steps{gap:14px;font-size:10px}.battle-layout.preparation{display:block}.formation-select{width:120px}.stage-steps i{font-size:9px}.waiting-dock{height:250px}}
@media(max-height:600px) and (orientation:landscape){.stage-steps{min-height:24px;margin:0 0 6px;gap:26px;font-size:9px}.stage-steps>span{min-height:23px;gap:5px}.stage-steps i{font-size:8px}.battle-layout.preparation{grid-template-columns:minmax(0,1fr) 310px;gap:22px}.boards{max-width:calc((100dvh - 180px)*2 + 14px)}.boards.single-board{width:min(100%,calc(100dvh - 168px));max-width:none}.preparation .battle-sidebar{max-height:calc(100dvh - 115px);padding-left:18px}.fleet-roster{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:3px;max-height:none;margin-bottom:6px}.fleet-roster>button{min-height:34px;padding:3px 4px;gap:6px}.fleet-roster img{width:28px;height:26px}.fleet-roster strong{font-size:10px}.fleet-roster small{font-size:8px}.fleet-roster>button>svg{width:11px}.formation-select{width:120px}.formation-select :deep(.custom-select-trigger){height:28px;padding:0 7px;font-size:10px}.supply .supply-row{min-height:35px;padding:3px 0;gap:8px}.supply-sprite{width:25px;height:25px}.supply .supply-name strong{font-size:10px}.supply .supply-name small{font-size:8px}.supply .quantity-control{width:23px;height:24px}.supply-controls{padding:7px 0}.supply-controls>span{font-size:9px;gap:6px}.preparation .battle-chat{margin-top:6px;min-height:100px}.preparation .chat-log{height:50px;max-height:80px}.sidebar-heading h3{font-size:11px}.fleet-total{font-size:9px}.ready-wait{font-size:11px;padding:10px}.waiting-dock{height:calc(100dvh - 160px);min-height:160px}.phase-announcement{padding:12px 30px}.phase-announcement strong{font-size:18px}.battle-chat{min-height:100px}.launch-water{height:110px;margin-bottom:10px}.launch-overlay h2{font-size:19px}}
@media(prefers-reduced-motion:reduce){.stage-scene-enter-active,.stage-scene-leave-active,.launch-enter-active,.launch-leave-active,.announcement-enter-active,.announcement-leave-active{transition-duration:.01s}.launch-water>img,.launch-wake{animation:none}}
@media(max-height:600px) and (orientation:landscape){.in-game .stage-steps{display:none}.is-preparing .room-strip{display:none}.in-game .room-strip{min-height:22px}.boards{max-width:calc((100dvh - 150px)*2 + 14px)}.boards.single-board{width:min(100%,calc(100dvh - 124px))}.preparation .battle-sidebar{max-height:calc(100dvh - 66px)}}
.weapons>button>img{width:26px;height:26px;object-fit:contain;image-rendering:pixelated}
@media(max-height:600px) and (orientation:landscape){.weapons>button>img{width:19px;height:19px}}
</style>

<style scoped>
.naval-header{padding-bottom:14px}.battle-layout{margin-top:20px}.battle-layout:not(.preparation){display:block}.battle-layout:not(.preparation) .battlefield{max-width:min(100%,calc((100dvh - 200px)*2 + 24px));margin:0 auto}.battle-layout:not(.preparation) .boards{max-width:none;gap:24px}.boards.single-board{width:min(100%,600px,calc(100dvh - 205px))}.preparation .battle-sidebar{justify-content:flex-start}.battle-chat h3>button{margin-left:auto}.chat-drawer{position:fixed;right:18px;top:84px;bottom:24px;width:300px;z-index:280;padding:16px;border:1px solid #d5e1e5;border-radius:5px;background:#fff;box-shadow:0 8px 34px #1e475126}.chat-drawer .battle-chat{height:100%;margin:0}.chat-drawer .chat-log{height:0;max-height:none;min-height:80px}.chat-toggle{position:relative}.chat-toggle[aria-pressed=true]{color:#d18636;background:#fff0df}.chat-toggle i{position:absolute;right:0;top:0;min-width:13px;height:13px;display:grid;place-items:center;background:#df8f3c;color:white;border-radius:7px;padding:0 3px;font-size:8px;font-style:normal}.connection-warning{align-self:center;max-width:150px;font-size:10px;color:#c3775f}.fleet-roster .unplaced{color:#8ca0a8;background:#edf2f4;border-style:dashed;border-color:#d5e0e5}.fleet-roster .unplaced img{opacity:.5}.fleet-roster .unplaced.selected{border-color:#e9ab68;background:#fff6eb}.battle-feed{min-height:26px;padding:8px 0;color:#80949d}.naval-tutorial-entry{display:flex;align-items:center;gap:13px;border-block:1px solid #dbe6e9;padding:14px 0;margin:0 0 20px;color:#c28a42}.naval-tutorial-entry>div{flex:1;min-width:0}.naval-tutorial-entry h3{font-size:14px;color:#42616d}.naval-tutorial-entry span{display:block;margin-top:4px;font-size:11px;color:#8aa0a9}.online-count{display:flex;align-items:center;gap:7px;font-size:12px;color:#598b7e;min-height:20px}.online-dot{width:6px;height:6px;display:inline-block;border-radius:50%;background:#6ba88a}.mode-entry .match-toggle{margin-top:auto;min-height:26px}.match-presentation{position:fixed;inset:0;z-index:420;display:flex;flex-direction:column;align-items:center;justify-content:center;gap:19px;padding:24px;background:#edf4f6;color:#3c6876}.match-presentation h2{font-size:23px}.match-presentation p{font-size:12px;color:#86a0aa}.signal-rings{width:144px;height:144px;position:relative;display:grid;place-items:center;margin-bottom:15px;color:#6e9eab}.signal-rings>span{position:absolute;inset:0;border:1px solid #a4c4cb;border-radius:50%;animation:signal-wave 2.1s ease-out infinite}.signal-rings>span:nth-child(2){animation-delay:1s}.signal-rings>svg{animation:spin 5s linear infinite}.matched-fleets{display:flex;align-items:center;justify-content:center;gap:22px;font-size:15px;min-width:250px;max-width:100%;overflow-wrap:anywhere}.matched-fleets>span{display:flex;flex-direction:column;align-items:center;gap:14px;min-height:72px;max-width:180px;text-align:center}.matched-fleets>svg{color:#dba15f;flex-shrink:0}.match-presentation[data-stage=FOUND] .signal-rings{color:#d2954d}.match-presentation[data-stage=FOUND] .signal-rings>svg{animation:none}.match-presentation-enter-active,.match-presentation-leave-active{transition:opacity .4s ease,transform .4s ease}.match-presentation-enter-from{opacity:0}.match-presentation-leave-to{opacity:0;transform:scale(1.03)}.phase-announcement{left:0;right:0;top:42%;width:100%;min-width:0;transform:translateY(-50%);padding:25px 20px;background:#eaf4f6f5;border-block:1px solid #b3d2d8;display:flex;flex-direction:column;gap:9px;box-shadow:0 6px 45px #31546015}.phase-announcement>span{font:9px monospace;color:#99aeb7}.phase-announcement strong{font-size:25px;font-weight:650;color:#426f7e}.announcement-enter-active,.announcement-leave-active{transition:opacity .5s ease,transform .5s cubic-bezier(.2,.7,.3,1)}.announcement-enter-from{opacity:0;top:42%;transform:translate(-50px,-50%)}.announcement-leave-to{opacity:0;top:42%;transform:translate(50px,-50%)}.stage-scene-enter-active,.stage-scene-leave-active{transition:opacity .38s ease,transform .38s ease}.matching-radar small{font-size:12px;color:#d1e4e9}.naval-page.in-game:has(.naval-tutorial-guide){overflow-x:hidden}.naval-page.in-game:has(.naval-tutorial-guide) .phase-announcement{display:none}@keyframes signal-wave{0%{opacity:.8;transform:scale(.35)}100%{opacity:0;transform:scale(1.1)}}
@media(max-width:700px) and (orientation:portrait){.naval-tutorial-entry{gap:10px;padding:12px 0}.naval-tutorial-entry h3{font-size:12px}.naval-tutorial-entry .naval-secondary{font-size:11px;padding:7px 10px}.mode-entry .online-count{grid-column:2;grid-row:2}.mode-entry .match-toggle{grid-column:2;grid-row:3}.chat-drawer{right:10px;left:10px;width:auto;top:70px;bottom:15px}}
@media(max-height:600px) and (orientation:landscape){.battle-layout{margin-top:9px}.battle-layout:not(.preparation) .battlefield{max-width:min(100%,calc((100dvh - 126px)*2 + 16px))}.battle-layout:not(.preparation) .boards{gap:16px}.boards.single-board{width:min(100%,calc(100dvh - 113px))}.preparation .battle-sidebar{max-height:calc(100dvh - 64px)}.naval-header{padding-bottom:6px}.phase-announcement{padding:17px}.phase-announcement strong{font-size:21px}.phase-announcement>span{font-size:8px}.chat-drawer{top:53px;right:10px;bottom:10px;width:260px;padding:10px}.chat-drawer .chat-log{height:0;max-height:none}.signal-rings{width:100px;height:100px;margin-bottom:0}.match-presentation{gap:12px}.match-presentation h2{font-size:20px}.match-presentation .launch-kicker{margin:0}.connection-warning{max-width:95px;font-size:9px}}
@media(prefers-reduced-motion:reduce){.signal-rings>span,.signal-rings>svg{animation:none}.match-presentation-enter-active,.match-presentation-leave-active,.announcement-enter-active,.announcement-leave-active,.stage-scene-enter-active,.stage-scene-leave-active{transition-duration:.01s}}
@media(max-width:740px) and (orientation:landscape){.battle-layout.preparation{grid-template-columns:minmax(0,1fr) 240px;gap:14px}.preparation .battle-sidebar{padding-left:12px}.formation-tools .icon-control{width:25px}.formation-select{width:100px}.ready-button{min-width:110px;padding:5px 7px;font-size:10px}.supply .supply-row{gap:6px}.header-actions{gap:1px}.placement-controls{flex-wrap:wrap;gap:7px}.placement-controls>.ready-button{margin-left:auto}}
@media(max-width:620px) and (orientation:landscape){.boards.single-board{width:min(100%,calc(100dvh - 146px))}}
</style>

<style scoped>
.invite-code,.matching-radar>strong{font-family:inherit}.board-turn{display:flex;align-items:center;gap:9px;min-height:40px;margin-bottom:12px;padding:8px 12px;border-bottom:2px solid #dce7eb;color:#9aafb7;background:#edf2f4;transition:background .3s,color .3s,border-color .3s}.board-turn strong{font-size:13px}.board-turn>span{display:flex;align-items:center;gap:5px;margin-left:auto;font-size:14px;font-variant-numeric:tabular-nums}.board-turn.active{background:#e6f2ee;color:#3b8672;border-color:#73ad98}.board-turn.active>svg{animation:turn-beacon 1.8s ease-in-out infinite}.enemy-panel .board-turn{justify-content:flex-end}.enemy-panel .board-turn>span{margin-right:auto;margin-left:0}.enemy-panel .board-turn.active{background:#fcf0e8;color:#c27f4f;border-color:#dfac84}.board-panel>header{min-height:32px}.board-panel h2{position:relative;isolation:isolate}.board-panel h2 :deep(.naval-avatar){width:26px;height:26px;flex-shrink:0}.enemy-panel h2{justify-content:flex-end;text-align:right}.player-chat-bubble{display:-webkit-box;-webkit-line-clamp:3;-webkit-box-orient:vertical;overflow:hidden;max-height:80px;position:absolute;bottom:calc(100% + 8px);left:22px;z-index:25;width:max-content;max-width:260px;padding:9px 12px;border:1px solid #cbdfe3;background:#fff;color:#4d727e;border-radius:6px;box-shadow:0 3px 16px #23495713;font-size:12px;font-weight:400;line-height:1.6;white-space:pre-wrap;overflow-wrap:anywhere;text-align:left;pointer-events:none}.enemy-panel .player-chat-bubble{left:auto;right:22px}.player-chat-bubble:after{content:'';position:absolute;left:12px;bottom:-5px;width:8px;height:8px;background:white;border-right:1px solid #cbdfe3;border-bottom:1px solid #cbdfe3;transform:rotate(45deg)}.enemy-panel .player-chat-bubble:after{left:auto;right:12px}.spectator-toolbar{display:flex;justify-content:flex-end;align-items:center;gap:16px;margin:12px 0 0;font-size:12px;color:#78939c}.spectator-toolbar>span{display:flex;align-items:center;gap:7px}.spectator-toolbar :deep(.custom-select){width:210px}.spectator-toolbar :deep(.custom-select-trigger){font-size:12px;height:33px}.full-room-dialog{align-items:center;text-align:center;max-width:340px}.full-room-dialog>svg{color:#7b9eab}.spectate-countdown{width:180px;height:3px;background:#e8f0f2;overflow:hidden}.spectate-countdown>span{display:block;height:100%;background:#e1a165;transition:width 1s linear}.modal-enter-active,.modal-leave-active{transition:opacity .28s ease}.modal-enter-active :deep(.naval-dialog),.modal-leave-active :deep(.naval-dialog),.modal-enter-active :deep(.naval-share-dialog),.modal-leave-active :deep(.naval-share-dialog){transition:transform .28s cubic-bezier(.2,.8,.3,1),opacity .28s}.modal-enter-from,.modal-leave-to{opacity:0}.modal-enter-from :deep(.naval-dialog),.modal-leave-to :deep(.naval-dialog),.modal-enter-from :deep(.naval-share-dialog),.modal-leave-to :deep(.naval-share-dialog){transform:translateY(18px) scale(.98);opacity:0}.chat-slide-enter-active,.chat-slide-leave-active{transition:transform .32s cubic-bezier(.2,.8,.3,1),opacity .32s}.chat-slide-enter-from,.chat-slide-leave-to{opacity:0;transform:translateX(50px)}.chat-pop-enter-active,.chat-pop-leave-active{transition:opacity .24s,transform .24s}.chat-pop-enter-from,.chat-pop-leave-to{opacity:0;transform:translateY(7px)}.settlement-enter-active,.settlement-leave-active{transition:opacity .36s ease,transform .36s ease}.settlement-enter-from,.settlement-leave-to{opacity:0;transform:translateY(20px)}.waiting-dock .matching-radar>svg{filter:drop-shadow(0 0 10px #93e7d5);animation:spin 3s linear infinite}.waiting-dock .matching-radar>span{animation:waiting-signal 2s ease-in-out infinite}.battle-layout:not(.preparation) .battlefield{max-width:min(100%,calc((100dvh - 258px)*2 + 24px))}@keyframes turn-beacon{50%{opacity:.45;transform:scale(.92)}}@keyframes waiting-signal{50%{opacity:.55}}
@media(max-height:600px) and (orientation:landscape){.board-turn{min-height:25px;padding:4px 8px;margin-bottom:5px;gap:6px}.board-turn strong{font-size:10px}.board-turn>svg{width:14px;height:14px}.board-turn>span{font-size:11px}.board-panel>header{min-height:23px;margin-bottom:4px}.board-panel h2 :deep(.naval-avatar){width:19px;height:19px}.battle-layout:not(.preparation) .battlefield{max-width:min(100%,calc((100dvh - 156px)*2 + 16px))}.player-chat-bubble{max-width:190px;font-size:10px;padding:6px 9px;bottom:calc(100% + 4px)}.spectator-toolbar{margin:5px 0 0;gap:10px;font-size:10px}.spectator-toolbar :deep(.custom-select-trigger){height:26px;font-size:10px}.spectator-toolbar :deep(.custom-select){width:180px}.in-game:has(.spectator-toolbar) .battle-layout{margin-top:5px}.in-game:has(.spectator-toolbar) .battle-layout:not(.preparation) .battlefield{max-width:min(100%,calc((100dvh - 180px)*2 + 16px))}}
@media(prefers-reduced-motion:reduce){.modal-enter-active,.modal-leave-active,.chat-slide-enter-active,.chat-slide-leave-active,.chat-pop-enter-active,.chat-pop-leave-active,.settlement-enter-active,.settlement-leave-active{transition-duration:.01s}.board-turn.active>svg,.waiting-dock .matching-radar>span{animation:none}}
</style>

<style scoped>
.board-panel>header{height:40px;min-height:40px}.board-panel h2{flex:1;min-width:0;height:100%;font-size:13px}.player-chat-bubble{position:relative;inset:auto;flex:0 1 auto;min-width:0;max-width:min(240px,50%);max-height:36px;padding:3px 9px;line-height:1.4;font-size:11px;-webkit-line-clamp:2;transform:none}.enemy-panel .player-chat-bubble{left:auto;right:auto}.player-chat-bubble:after{left:-4px;top:calc(50% - 4px);bottom:auto;width:6px;height:6px;border-right:0;border-left:1px solid #cbdfe3;transform:rotate(45deg)}.enemy-panel .player-chat-bubble:after{left:auto;right:-4px;transform:rotate(225deg)}.battle-layout:not(.preparation) .battlefield{max-width:min(100%,calc((100dvh - 330px)*2 + 24px))}.battle-layout:not(.preparation):has(.naval-audience) .battlefield{max-width:min(100%,calc((100dvh - 410px)*2 + 24px))}
.player-name{min-width:0;max-width:180px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;flex:0 1 auto}
@media(max-width:620px) and (orientation:landscape){.weapons>button>span{display:none}.weapons>button{min-height:32px;gap:6px}.player-name{max-width:80px}.weapons>button>svg,.weapons>button>img{width:18px;height:18px;flex-shrink:0}}
@media(max-height:600px) and (orientation:landscape){.board-panel>header{height:28px;min-height:28px}.board-panel h2{font-size:10px;gap:4px}.player-chat-bubble{max-width:50%;max-height:27px;font-size:9px;padding:2px 5px}.battle-layout:not(.preparation) .battlefield,.battle-layout:not(.preparation):has(.naval-audience) .battlefield{max-width:min(100%,calc((100dvh - 180px)*2 + 16px))}.in-game:has(.spectator-toolbar) .battle-layout:not(.preparation) .battlefield{max-width:min(100%,calc((100dvh - 210px)*2 + 16px))}}
</style>

<style scoped src="./navalMobile.css"></style>
<style scoped>
.fleet-command{display:flex;gap:4px;min-width:0}.fleet-command>button:first-child{display:flex;align-items:center;gap:12px;flex:1;min-width:0;min-height:40px;padding:4px 8px;border:1px solid transparent;border-radius:4px;text-align:left;background:transparent;color:#637d87}.fleet-command>button.selected{background:#fff2e6;border-color:#efc399;color:#b27032}.fleet-command>button>svg{margin-left:auto}.fleet-rotate{display:grid;place-items:center;width:32px;flex-shrink:0;background:#edf4f5;color:#63828c;border-radius:4px}.fleet-rotate:disabled{opacity:.15}.model-rotate{position:absolute;z-index:12;transform:translateX(-50%);display:grid;place-items:center;width:28px;height:26px;border:1px solid #c9b47d;border-radius:4px;color:#b1732b;background:#fff9e8;box-shadow:0 2px 5px #284a5122}.submarine-count{position:absolute;left:8px;bottom:5px;padding:2px 5px;border-radius:3px;background:#e9f8ee;color:#366f58;font-size:10px;pointer-events:none}
.mobile-game :deep(.naval-share-mask){place-items:center end}.mobile-game :deep(.share-dialog){max-width:calc(var(--game-width,100dvw) / 2 - 16px)}
.mobile-game :deep(.naval-share-dialog){width:min(470px,calc(var(--game-width,100dvw) - 24px));margin-left:auto}
.naval-modal-mask.resume-mask{align-items:center;justify-content:center;padding:16px}
.resume-dialog{width:430px;margin:0;max-width:100%}.resume-dialog footer{flex-wrap:wrap}.resume-dialog footer>button{flex:1;min-height:38px}
.naval-page:not(.in-game) .lobby-modes>.mode-entry>button{margin-top:auto}
.naval-page.is-mobile:not(.in-game):has(.resume-line){height:auto;min-height:calc(100dvh - 46px);overflow:visible}
.naval-page.is-mobile:not(.in-game):has(.resume-line)>.naval-primary-content{height:auto}
.naval-page.is-mobile:not(.in-game):has(.resume-line) .lobby-modes{flex:none}
@media(orientation:landscape){.naval-page.is-mobile:not(.in-game) .lobby-modes>.mode-entry{min-height:124px}.naval-page.is-mobile:not(.in-game) .lobby-modes>.mode-entry>button{flex-shrink:0;height:30px;min-height:30px}}
</style>
<style scoped>
.naval-page:not(.in-game){height:calc(100dvh - 52px);min-height:0;box-sizing:border-box;overflow:hidden;padding:16px 24px}.naval-page:not(.in-game)>.naval-primary-content{display:flex;flex-direction:column;height:100%;min-height:0}.naval-page:not(.in-game) .naval-header{flex-shrink:0;padding-bottom:10px;gap:12px;border:0}.naval-page:not(.in-game) .header-actions{align-items:center;gap:1px;min-width:0}.naval-page:not(.in-game) .naval-lobby{display:flex;flex-direction:column;flex:1;min-height:0;width:100%;margin:4px auto 0;gap:12px}.naval-page:not(.in-game) .lobby-sea{flex:1 1 180px;height:auto;min-height:45px;max-height:220px;border-radius:4px}.lobby-profile{display:flex;align-items:center;gap:12px;min-width:0}.lobby-profile>div{min-width:0}.lobby-profile :deep(.naval-avatar){width:40px;height:40px}.naval-page:not(.in-game) .lobby-heading{margin:0;flex-shrink:0;gap:12px}.lobby-heading p.guest-storage{font-size:11px;line-height:1.9;max-width:680px}.guest-storage a{display:inline-flex;justify-content:center;align-items:center;background:#e3eee7;color:#3b795c;border-radius:3px;min-height:26px;padding:0 8px;margin:0 3px;font-weight:600}.naval-page:not(.in-game) .naval-tutorial-entry{margin:0;padding:6px 0;border:0;flex-shrink:0}.naval-page:not(.in-game) .lobby-modes{flex-shrink:0}.naval-page:not(.in-game) .mode-entry{gap:9px;padding:16px}.naval-page:not(.in-game) .mode-entry h3{font-size:15px}.naval-page:not(.in-game) .resume-line{margin:0;padding:4px 0;border:0;flex-shrink:0}.naval-page.community-open:not(.is-mobile){align-items:stretch;height:calc(100dvh - 52px);min-height:0;box-sizing:border-box;overflow:hidden}.naval-page.community-open:not(.is-mobile)>.naval-primary-content{overflow:hidden}.naval-page.community-open:not(.is-mobile):has(.match-presentation) :deep(.naval-community){position:fixed;right:16px;top:70px;width:400px;height:calc(100dvh - 86px)}
.mobile-menu-actions{align-content:start}.mobile-menu-actions>button{min-width:0;white-space:nowrap}.mobile-menu-actions>button>svg{flex-shrink:0}.player-chat-bubble.sticker-pop{display:block;position:absolute;top:100%;bottom:auto;left:0;right:auto;width:112px;max-width:none;max-height:none;padding:0;border:0;background:transparent;box-shadow:none;overflow:visible}.enemy-panel .player-chat-bubble.sticker-pop{left:auto;right:0}.player-chat-bubble.sticker-pop:after{display:none}.player-chat-bubble.sticker-pop :deep(img){width:112px;height:112px}
@media(max-width:700px){.naval-page:not(.in-game){height:calc(100dvh - 46px);padding:10px 12px}.naval-page:not(.in-game) .naval-header{flex-wrap:nowrap;gap:6px}.naval-page:not(.in-game) .naval-title{display:none}.naval-page:not(.in-game) .header-actions{width:100%;justify-content:flex-end}.naval-page:not(.in-game) .lobby-heading h2{font-size:16px}.naval-page:not(.in-game) .record-summary{font-size:9px;white-space:nowrap;max-width:none}.naval-page:not(.in-game) .lobby-heading{align-items:flex-start}.naval-page:not(.in-game) .lobby-modes{gap:8px}.naval-page:not(.in-game) .mode-entry{padding:10px;gap:6px 12px;grid-template-columns:24px minmax(0,1fr) 120px}.naval-page:not(.in-game) .mode-entry>button{grid-column:3;grid-row:1/4;margin:0;height:100%;font-size:11px}.naval-page:not(.in-game) .mode-entry>svg{width:23px;grid-row:1/4}.naval-page:not(.in-game) .mode-entry h3{font-size:13px}.naval-page:not(.in-game) .mode-entry p,.naval-page:not(.in-game) .mode-entry .match-toggle{min-height:20px;font-size:11px}.naval-page:not(.in-game) .lobby-profile{gap:8px}.naval-page:not(.in-game) .lobby-heading{flex-wrap:wrap;gap:3px}.naval-page:not(.in-game) .lobby-sea{min-height:25px}.naval-page:not(.in-game) .naval-lobby{gap:8px}}
@media(max-height:500px) and (orientation:landscape){.naval-page:not(.in-game){padding:6px 12px}.naval-page:not(.in-game) .lobby-sea{display:none}.naval-page:not(.in-game) .lobby-modes{flex:1;min-height:0;grid-template-columns:repeat(3,minmax(0,1fr))}.naval-page:not(.in-game) .mode-entry{display:flex;gap:5px;padding:8px}.naval-page:not(.in-game) .mode-entry>svg{display:none}.naval-page:not(.in-game) .mode-entry h3{font-size:13px}.naval-page:not(.in-game) .mode-entry>button{height:auto;min-height:30px;font-size:10px;padding:5px;align-self:stretch}.naval-page:not(.in-game) .mode-entry p,.naval-page:not(.in-game) .match-toggle{min-height:18px;font-size:10px}.naval-page:not(.in-game) .lobby-heading h2{font-size:14px}.naval-page:not(.in-game) .lobby-profile{gap:6px}.naval-page:not(.in-game) .lobby-heading p.guest-storage{line-height:1.5;font-size:10px}.guest-storage a{min-height:22px}.naval-page:not(.in-game) .naval-tutorial-entry{padding:0}.naval-page:not(.in-game) .naval-tutorial-entry span{display:none}.naval-page:not(.in-game) .naval-tutorial-entry h3{font-size:11px}.naval-page:not(.in-game) .naval-tutorial-entry .naval-secondary{min-height:28px;font-size:10px}.naval-page:not(.in-game) .naval-header{padding:0}.naval-page:not(.in-game) .lobby-heading{flex-wrap:nowrap}.naval-page:not(.in-game) .naval-lobby{gap:6px}}
@media(max-width:360px){.naval-page:not(.in-game) .header-actions>.icon-control{width:25px}.naval-page:not(.in-game) .mode-entry{grid-template-columns:20px minmax(0,1fr) 106px;gap:4px 6px}.naval-page:not(.in-game) .naval-lobby{gap:6px}}
@media(orientation:portrait){.naval-page.is-mobile:not(.in-game){height:auto;min-height:calc(100dvh - 46px);overflow:visible;padding:12px}.naval-page.is-mobile:not(.in-game)>.naval-primary-content{height:auto}.naval-page.is-mobile:not(.in-game) .naval-lobby{display:flex;gap:18px;flex:none}.naval-page.is-mobile:not(.in-game) .lobby-sea{display:block;flex:none;height:150px;max-height:none}.naval-page.is-mobile:not(.in-game) .lobby-modes{display:grid;grid-template-columns:1fr;gap:12px}.naval-page.is-mobile:not(.in-game) .mode-entry{display:flex;position:relative;flex-direction:column;align-items:flex-start;gap:12px;padding:18px 16px}.naval-page.is-mobile:not(.in-game) .mode-entry>svg{display:block;width:26px;height:26px;position:absolute;top:17px;right:16px}.naval-page.is-mobile:not(.in-game) .mode-entry h3{font-size:16px}.naval-page.is-mobile:not(.in-game) .mode-entry p,.naval-page.is-mobile:not(.in-game) .mode-entry .match-toggle{font-size:12px;min-height:22px}.naval-page.is-mobile:not(.in-game) .mode-entry>button{height:40px;min-height:40px;font-size:13px;align-self:stretch;margin-top:2px}.naval-page.is-mobile:not(.in-game) .invite-input{min-height:38px;font-size:13px}.naval-page.is-mobile:not(.in-game) .lobby-heading{gap:7px}.naval-page.is-mobile:not(.in-game) .guest-storage{font-size:12px;line-height:1.9}.naval-page.is-mobile:not(.in-game) .naval-tutorial-entry{padding:8px 0}.naval-page.is-mobile.community-open:not(.in-game){overflow:hidden;height:calc(100dvh - 46px);min-height:0}}
</style>
<style scoped>
.naval-page .naval-header .header-actions{gap:6px}
.naval-header .header-actions :deep(.community-actions){display:contents;margin:0}
.naval-page .naval-header .header-actions :deep(.community-actions button),.naval-page .naval-header .header-actions>.icon-control{box-sizing:border-box;width:34px;height:34px;padding:0;flex:0 0 34px}
.guest-ranking-button{display:inline-flex;align-items:center;justify-content:center;background:#e3eee7;color:#3b795c;border-radius:3px;min-height:26px;padding:0 8px;margin:0 3px;font:inherit;font-weight:600;vertical-align:baseline}
.guest-ranking-button:hover{background:#cfe4d6}
.desktop-audience-panel{position:relative;align-self:stretch;display:flex;flex-direction:column;width:100%;height:100%;min-height:0;min-width:0;padding:12px;background:#f9fcfb;box-sizing:border-box;border-radius:6px;color:#456a77;overflow:auto;box-shadow:0 3px 20px #294a4412}.desktop-audience-panel>header{display:flex;align-items:center;justify-content:space-between;gap:12px;min-height:32px;margin-bottom:8px}.desktop-audience-panel h2{font-size:15px}.desktop-audience-panel :deep(.naval-audience){border:0;margin:0;padding:5px 0}
@media(max-width:700px){.naval-page .naval-header .header-actions{gap:2px}.naval-page .naval-header .header-actions :deep(.community-actions button),.naval-page .naval-header .header-actions>.icon-control{width:28px;height:30px;flex-basis:28px}}
@media(max-height:500px){.guest-ranking-button{min-height:22px}}
@media(max-height:500px) and (orientation:landscape){
  .naval-page:not(.in-game){padding:10px 16px;background:#f6f8f9}
  .naval-page.is-fullscreen:not(.in-game){height:100dvh;min-height:100dvh}
  .naval-page:not(.in-game):before{background-image:linear-gradient(180deg,#f6f8f9fa,#f6f8f9e8 55%,#f6f8f9f5),var(--naval-harbor);background-position:center}
  .naval-page:not(.in-game)>.naval-primary-content{gap:10px}
  .naval-page:not(.in-game) .naval-header{height:32px;min-height:32px;padding:0;background:transparent;gap:12px}
  .naval-page:not(.in-game) .naval-title{display:flex;gap:7px}
  .naval-page:not(.in-game) .naval-title h1{font-size:17px;line-height:24px}
  .naval-page:not(.in-game) .naval-title>svg{width:20px;height:20px}
  .naval-page:not(.in-game) .naval-title span{display:none}
  .naval-page:not(.in-game) .header-actions{width:auto;gap:3px;justify-content:flex-end}
  .naval-page:not(.in-game) .header-actions>.icon-control,.naval-page:not(.in-game) .header-actions :deep(.community-actions button){width:30px;height:30px;flex-basis:30px;color:#48616c}
  .naval-page:not(.in-game) .header-actions :deep(.community-actions button.active){color:#39775b;background:#e3eee7}
  .naval-page:not(.in-game) .header-actions svg,.naval-page:not(.in-game) .header-actions :deep(svg){width:18px;height:18px;stroke-width:1.8}
  .naval-page:not(.in-game) .naval-lobby{display:grid;grid-template-columns:minmax(0,1fr) auto;grid-template-rows:34px auto minmax(124px,1fr);gap:10px 16px;margin:0;max-width:none}
  .naval-page:not(.in-game) .naval-rule-switch{grid-column:1;grid-row:1;align-self:stretch;align-items:stretch;height:34px;margin:0;background:transparent;border:0;gap:20px}
  .naval-page:not(.in-game) .naval-rule-switch button{padding:0 2px;font-size:12px;color:#48616c;border-bottom-width:2px}
  .naval-page:not(.in-game) .naval-rule-switch button[aria-pressed=true]{color:#263c43;border-bottom-color:#e9954b}
  .naval-page:not(.in-game) .naval-tutorial-entry{grid-column:2;grid-row:1;align-self:center;padding:0;margin:0;gap:0}
  .naval-page:not(.in-game) .naval-tutorial-entry>svg,.naval-page:not(.in-game) .naval-tutorial-entry>div{display:none}
  .naval-page:not(.in-game) .naval-tutorial-entry .naval-secondary{height:30px;min-height:30px;padding:4px 10px;font-size:11px;background:#fff}
  .naval-page:not(.in-game) .lobby-heading{grid-column:1/-1;grid-row:2;align-items:flex-start;gap:12px;background:transparent;min-height:22px}
  .naval-page:not(.in-game) .lobby-profile>div{display:block}
  .naval-page:not(.in-game) .lobby-heading h2{font-size:12px;line-height:18px;color:#263c43}
  .naval-page:not(.in-game) .lobby-heading .guest-storage{display:block;max-width:none;font-size:10px;line-height:18px;margin-top:3px;color:#48616c}
  .naval-page:not(.in-game) .lobby-heading p,.naval-page:not(.in-game) .record-summary{color:#48616c;font-size:10px;margin:0;line-height:18px}
  .naval-page:not(.in-game) .record-summary{flex-shrink:0;white-space:nowrap;max-width:none}
  .naval-page:not(.in-game) .lobby-modes{grid-column:1/-1;grid-row:3;display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:10px;min-height:124px}
  .naval-page:not(.in-game) .lobby-modes>.mode-entry{position:relative;display:flex;min-height:124px;gap:7px;padding:12px;background:#ffffffed;border-color:#dce4e7;border-radius:6px}
  .naval-page:not(.in-game) .mode-entry>svg{display:block;position:absolute;top:12px;right:12px;width:19px;height:19px}
  .naval-page:not(.in-game) .mode-entry h3{font-size:14px;line-height:20px;padding-right:22px}
  .naval-page:not(.in-game) .mode-entry p,.naval-page:not(.in-game) .mode-entry .match-toggle{font-size:11px;min-height:18px;color:#607680;margin:0}
  .naval-page:not(.in-game) .online-count{font-size:11px;min-height:18px}
  .naval-page:not(.in-game) .mode-entry .invite-input{height:30px;min-height:30px;padding:5px 8px;font-size:11px}
  .naval-page:not(.in-game) .lobby-modes>.mode-entry>button{height:32px;min-height:32px;padding:5px 8px;font-size:11px;border-radius:4px}
  .naval-page:not(.in-game) .resume-line{grid-column:1/-1;grid-row:4}
  .naval-page:not(.in-game) .naval-lobby:has(.resume-line){grid-template-rows:34px auto minmax(124px,1fr) auto}
}
</style>
