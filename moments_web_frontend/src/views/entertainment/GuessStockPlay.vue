<template>
  <div v-if="session" class="play-page">
    <header class="play-header">
      <button class="icon-btn" title="返回游戏" @click="leaveToDetail"><ArrowLeft :size="19" /></button>
      <div class="symbol"><span>{{ session.game.stockCode }}</span><strong>{{ session.game.title }}</strong></div>
      <div class="round-meter"><span>第 {{ session.currentRound }} / {{ session.config.roundCount }} 回合</span><i><b :style="{ width: `${roundProgress}%` }"></b></i></div>
      <div class="stage-pill" :data-stage="stage"><span>{{ phaseName }}</span><strong>{{ countdown }}</strong></div>
      <div v-if="session.status === 'ACTIVE' && !session.room && !isManual" class="auto-controls">
        <button type="button" :class="['auto-toggle', { active: autoAdvanceEnabled }]" :disabled="submitting" :aria-pressed="autoAdvanceEnabled" :title="autoAdvanceEnabled ? '关闭自动进入下一阶段' : '自动进入下一阶段'" @click="toggleAutoAdvance"><FastForward :size="16" /><span>{{ autoAdvanceEnabled && autoAdvanceCountdown != null ? `${autoAdvanceCountdown}s` : '自动' }}</span></button>
        <div class="speed-selector" role="group" aria-label="每个阶段的游戏速度"><button v-for="speed in speedOptions" :key="speed" type="button" :disabled="submitting" :class="{ active: autoAdvanceSpeed === speed }" @click="setAutoAdvanceSpeed(speed)">x{{ speed }}</button></div>
      </div>
      <button class="icon-btn" title="规则" @click="showRules = true"><BookOpen :size="18" /></button>
      <button v-if="session.credit?.enabled" class="icon-btn credit-trigger" :data-risk="session.credit.riskState" :disabled="session.phase === 'ROUND_START'" title="银行融资" @click="openCredit"><Landmark :size="18" /></button>
      <button v-if="session.config.fundamentalEnabled" class="icon-btn" title="市场研究" @click="showResearch = true"><FlaskConical :size="18" /></button>
      <button v-if="session.status === 'ACTIVE'" class="icon-btn" title="重新开始" @click="showRestart = true"><RotateCcw :size="18" /></button>
      <button v-if="session.status === 'ACTIVE' && session.room?.host" class="icon-btn room-close-trigger" title="关闭多人房间" @click="showCloseRoom = true"><DoorClosed :size="18" /></button>
      <button v-if="session.status === 'ACTIVE' && !session.room" class="icon-btn desktop-only" title="暂停" @click="pause"><Pause :size="18" /></button>
    </header>

    <Transition name="stage"><div v-if="stageNotice" class="stage-notice"><Zap :size="18" /><div><strong>{{ stageNotice }}</strong><span>{{ stageNoticeDetail }}</span></div></div></Transition>
    <Transition name="curtain"><div v-if="session.phase === 'ROUND_START'" class="round-curtain"><div><strong>第 {{ session.currentRound }} 回合</strong><small>新一轮市场即将开始</small></div></div></Transition>

    <section class="market-strip">
      <div class="main-price"><span>{{ priceLabel }}</span><strong :class="priceTone">{{ price(currentPrice) }}</strong><small :class="priceTone">{{ currentChangeLabel }}</small></div>
      <div><span>当前收益率</span><strong :class="tone(currentReturn)">{{ signed(currentReturn) }}%</strong></div>
      <div><span>现金</span><strong :class="tone(player.cash)">{{ money(player.cash) }}</strong></div>
      <div><span>持仓</span><strong>{{ player.quantity }} 股</strong></div>
      <div><span>总资产</span><strong>{{ money(player.asset) }}</strong></div>
    </section>

    <section v-if="session.credit?.enabled && Number(session.credit.effectiveDebt) > 0" :class="['credit-strip', `risk-${String(session.credit.riskState).toLowerCase()}`]" @click="openCredit">
      <span>借贷银行 <strong>{{ indebtedBankCount }} 家</strong></span><span>总有效债务 <strong>{{ money(session.credit.effectiveDebt) }}</strong></span><span>股票敞口 <strong>{{ fixed(session.credit.exposurePercent, 1) }}%</strong></span><span>LTV <strong>{{ fixed(session.credit.ltvPercent, 1) }}%</strong></span><b>{{ creditRiskLabel(session.credit.riskState) }}</b>
    </section>

    <section v-if="session.status === 'COMPLETED'" class="final-result">
      <div><span>最终净资产</span><strong>{{ money(session.finalAsset) }}</strong></div>
      <div><span>最终收益率</span><strong :class="tone(session.returnPercent)">{{ signed(session.returnPercent) }}%</strong></div>
      <div><span>净收益</span><strong :class="tone(session.profitAmount)">{{ signedMoney(session.profitAmount) }}</strong></div>
      <div><span>收益率排名</span><strong>{{ currentPlayerRank }} / {{ session.participants.length }}</strong></div>
      <router-link class="primary-command" :to="`/customer/entertainment/games/${session.gameId}`"><Trophy :size="17" />查看排行榜</router-link>
    </section>
    <section v-if="challengeResult" class="challenge-result">
      <strong>今日挑战第 {{ challengeResult.rank }} 名</strong><span>研究 {{ challengeResult.accurateResearchCount }}/{{ challengeResult.researchAttemptCount }} · {{ fixed(challengeResult.researchAccuracyPercent, 0) }}%</span><span>回撤 {{ fixed(challengeResult.maxDrawdownPercent, 2) }}%</span><span v-if="challengeResult.lowDrawdownBadge">低回撤</span><span v-if="challengeResult.noLiquidationBadge">无强平</span><span v-if="challengeResult.noDebtCostBadge">零利息</span><router-link :to="`/customer/entertainment/challenges/${challengeResult.challengeId || challengeId}/leaderboard`">挑战榜</router-link>
    </section>
    <section v-if="session.status === 'COMPLETED' && session.credit?.enabled" class="final-credit-summary">
      <div><span>累计贷款利息</span><strong class="sell">-{{ money(session.credit.totalInterestCharged) }}</strong></div><div><span>强制卖出</span><strong>{{ session.credit.totalForcedLiquidationShares || 0 }} 股</strong></div><div><span>剩余债务</span><strong :class="{ sell: Number(session.credit.effectiveDebt) > 0 }">{{ money(session.credit.effectiveDebt) }}</strong></div><div><span>最终净资产</span><strong>{{ money(session.finalAsset) }}</strong></div>
    </section>
    <section v-if="session.status === 'COMPLETED' && session.aiReviews?.length" class="ai-review">
      <header><div><Bot :size="18" /><h2>AI 决策复盘</h2></div><span>以决策后一个可观察周期的方向收益评价</span></header>
      <div class="ai-review-table">
        <article v-for="review in session.aiReviews" :key="review.participantId">
          <div class="ai-review-name"><strong>{{ review.participantName }}</strong><small>#{{ review.finalRank }} · {{ signed(review.returnPercent) }}%</small></div>
          <div><span>最佳决策</span><strong v-if="review.bestDecision" class="positive">第 {{ review.bestDecision.roundNo }} 回合 {{ tradeDirection(review.bestDecision.direction) }} · {{ signed(review.bestDecision.outcomePercent) }}%</strong><strong v-else>暂无可评价成交</strong><small v-if="review.bestDecision">{{ review.bestDecision.shares }} 股 · {{ review.bestDecision.reason || '按目标仓位执行' }}</small></div>
          <div><span>最大错误</span><strong v-if="review.biggestMistake" class="negative">第 {{ review.biggestMistake.roundNo }} 回合 {{ tradeDirection(review.biggestMistake.direction) }} · {{ signed(review.biggestMistake.outcomePercent) }}%</strong><strong v-else>未出现负向决策</strong><small v-if="review.biggestMistake">{{ review.biggestMistake.shares }} 股 · {{ review.biggestMistake.reason || '方向与后续价格相反' }}</small></div>
          <div class="ai-review-stats"><span>换观点 {{ review.thesisReplacementCount }}</span><span>反向交易 {{ review.reverseTradeCount }}</span><span>模型 {{ review.modelCallCount }}</span><span>降级 {{ review.fallbackCount }}</span><span>利息 {{ money(review.totalInterestCost) }}</span><span>强平 {{ review.forcedLiquidationShares }} 股</span></div>
        </article>
      </div>
    </section>

    <div class="play-grid">
      <main class="market-main">
        <section class="chart-panel">
          <header>
            <div><Activity :size="18" /><h2>价格走势</h2></div>
            <div class="chart-actions">
              <div v-if="session.config.roundCount > 1" class="chart-range" aria-label="走势图合并粒度">
                <button v-for="size in chartGroupOptions" :key="size" type="button" :class="{ active: chartGroupSize === size }" :title="size === 1 ? '逐回合展示' : `每 ${size} 个回合合并为一柱`" @click="setChartGroupSize(size)">{{ size }}回合</button>
              </div>
              <button class="event-button" :class="{ active: stageEventOccurred }" @click="showEvent = true"><Newspaper :size="15" />事件</button>
            </div>
          </header>
          <div class="chart-wrap" @pointermove="moveHover" @pointerleave="hovered = null">
            <svg viewBox="0 0 820 300" class="price-chart" role="img" aria-label="可按回合数量合并的开盘价与收盘价走势图">
              <line v-for="tick in chartPriceTicks" :key="`grid-${tick.y}`" x1="58" :y1="tick.y" x2="792" :y2="tick.y" class="grid-line" />
              <text v-for="tick in chartPriceTicks" :key="`price-${tick.y}`" x="54" :y="tick.y + 3" text-anchor="end" class="axis-label price-axis">{{ price(tick.price) }}</text>
              <g v-for="tick in chartTicks" :key="tick.key">
                <line :x1="tick.x" y1="26" :x2="tick.x" y2="258" class="vertical-grid" />
                <text :x="tick.x" y="286" text-anchor="middle" class="axis-label">{{ tick.label }}</text>
              </g>
              <line x1="58" :y1="initialY" x2="792" :y2="initialY" class="initial-line" />
              <text x="61" :y="Math.max(16, initialY - 5)" class="initial-label">{{ chartBaselineLabel }} {{ price(chartBaselinePrice) }}</text>
              <line v-for="connector in barConnectors" :key="connector.key" pathLength="1" :x1="connector.x1" :y1="connector.y1" :x2="connector.x2" :y2="connector.y2" :class="['kline-connector', connector.tone]" />
              <g v-for="bar in roundBars" :key="bar.key" :class="['round-bar', bar.tone, { hovered: hovered?.key === bar.key }]">
                <line v-if="bar.mineBuy" :x1="bar.x" y1="34" :x2="bar.x" y2="258" :class="['trade-marker', 'buy', { paired: bar.mineSell }]" />
                <line v-if="bar.mineSell" :x1="bar.x" y1="34" :x2="bar.x" y2="258" :class="['trade-marker', 'sell', { paired: bar.mineBuy }]" />
                <line v-if="bar.forcedSell" :x1="bar.x" y1="26" :x2="bar.x" y2="258" class="forced-marker" />
                <text v-if="bar.forcedSell" :x="bar.x" y="22" text-anchor="middle" class="forced-label">强平</text>
                <rect :x="bar.x - bar.width / 2" :y="bar.top" :width="bar.width" :height="bar.height" class="round-candle" />
                <template v-if="hovered?.key === bar.key"><text :x="bar.x - bar.width / 2 - 5" :y="bar.yOpen + 3" text-anchor="end" class="price-label">开 {{ price(bar.openPrice) }}</text><text :x="bar.x + bar.width / 2 + 5" :y="bar.yClose + 3" class="price-label">收 {{ price(bar.closePrice) }}</text></template>
              </g>
              <g v-if="hovered" class="crosshair"><line :x1="hovered.x" y1="26" :x2="hovered.x" y2="258" /><line x1="58" :y1="hovered.yClose" x2="792" :y2="hovered.yClose" /></g>
            </svg>
            <div v-if="hovered" :class="['chart-tooltip', { left: hovered.x > 560 }]" :style="tooltipStyle">
              <strong>{{ hovered.label }}</strong>
              <span>开盘 {{ price(hovered.openPrice) }} · 收盘 {{ price(hovered.closePrice) }}</span>
              <span :class="tone(hovered.change)">区间变化 {{ signed(hovered.change) }}%</span>
              <span v-if="hovered.eventName">事件：{{ hovered.eventName }}</span>
              <span>买盘 {{ signed(hovered.openingFlow) }}% · 卖盘 {{ signed(hovered.closingFlow) }}%</span>
              <span v-if="hovered.forcedSell" class="sell">强制卖出 {{ hovered.forcedSell }} 股，已计入本柱卖盘</span>
              <span v-if="hovered.mineBuy" class="buy">我的买入 {{ hovered.mineBuy.quantity }} 股 @ {{ price(hovered.mineBuy.executionPrice) }}<template v-if="hovered.mineBuy.tradeCount > 1">（{{ hovered.mineBuy.tradeCount }} 笔）</template></span>
              <span v-if="hovered.mineSell" class="sell">我的卖出 {{ hovered.mineSell.quantity }} 股 @ {{ price(hovered.mineSell.executionPrice) }}<template v-if="hovered.mineSell.tradeCount > 1">（{{ hovered.mineSell.tradeCount }} 笔）</template></span>
            </div>
          </div>
          <footer class="chart-legend"><span><i class="line-up"></i>收高于开</span><span><i class="line-down"></i>收低于开</span><span><i class="buy-key"></i>该柱内买入</span><span><i class="sell-key"></i>该柱内卖出</span><span><i class="forced-key"></i>强平</span></footer>
        </section>

        <section class="market-details desktop-details">
          <div class="ranking">
            <header><h2>收益率排名</h2><span>实时估值</span></header>
            <div v-for="(item, index) in rankedParticipants" :key="item.id" :class="['rank-row', { mine: item.currentPlayer }]">
              <b>{{ index + 1 }}</b><span class="rank-player">{{ item.displayName }}<small>{{ item.quantity }} 股</small></span><span class="rank-cash">{{ money(availableFunds(item)) }}</span><small :class="tone(item.returnPercent)">{{ signed(item.returnPercent) }}%</small><strong>{{ money(item.asset) }}</strong>
            </div>
          </div>
          <div class="trades">
            <header><h2>最近成交</h2><span>{{ session.recentTrades.length }} 笔</span></header>
            <div v-if="session.recentTrades.length" class="trade-list"><div v-for="trade in session.recentTrades" :key="trade.id" :title="Number(trade.feeAmount) || Number(trade.slippagePercent) ? `手续费 ${money(trade.feeAmount)} · 滑点 ${fixed(trade.slippagePercent, 2)}%` : ''"><span>第 {{ trade.roundNo }} 回合</span><span class="trader-name" :title="trade.participantName">{{ trade.participantName }}</span><b :class="trade.side === 'BUY' ? 'buy' : 'sell'">{{ trade.side === 'BUY' ? '买' : '卖' }}</b><span>{{ trade.quantity }} 股</span><strong>{{ price(trade.executionPrice) }}</strong></div></div>
            <p v-else>暂无成交</p>
          </div>
          <div class="trades timeline" style="display: none">
            <header><h2>市场时间线</h2><span>已结算</span></header>
            <div v-if="marketTimeline.length" class="trade-list"><div v-for="item in marketTimeline" :key="item.key"><span>第 {{ item.roundNo }} 回合</span><b>{{ item.kind }}</b><strong>{{ item.text }}</strong></div></div>
            <p v-else>等待首轮结算</p>
          </div>
        </section>
      </main>

      <aside :class="['order-panel', isTargetAuction ? 'auction-mode' : isOpening ? 'buy-mode' : 'sell-mode', { locked: !canTrade }]">
        <header><div><component :is="isTargetAuction ? Scale : isOpening ? TrendingUp : TrendingDown" :size="21" /><span><small>{{ isOpening ? '开盘前' : '收盘前' }}</small><h2>{{ isTargetAuction ? '目标仓位竞价' : `当前操作：${isOpening ? '买入' : '卖出'}` }}</h2></span></div><strong>{{ canTrade ? (isTargetAuction ? 'SEALED 竞价' : isOpening ? 'BUY 买入' : 'SELL 卖出') : (tradingExpired ? '本阶段已截止' : '等待阶段') }}</strong></header>
        <div class="stage-price"><span>{{ isOpening ? '价格基准' : '当前开盘价' }}</span><strong>{{ price(orderReferencePrice) }}</strong><small>{{ isOpening ? '实际按开盘价成交' : '实际按收盘价成交' }}</small></div>
        <div class="order-account"><span>可用现金 <b :class="tone(player.cash)">{{ money(player.cash) }}</b></span><span>可卖持仓 <b>{{ player.quantity }} 股</b></span></div>
        <template v-if="isTargetAuction">
          <label class="quantity-field"><span>目标股票敞口 %</span><input v-model.number="targetExposure" type="number" min="0" :max="targetExposureMax" step="1" :disabled="!canTrade" /></label>
          <input v-model.number="targetExposure" class="quantity-slider" type="range" min="0" :max="targetExposureMax" step="1" :disabled="!canTrade" />
          <div class="quick-row target-quick"><button v-for="ratio in [0,25,50,75,100]" :key="ratio" :disabled="!canTrade || submitting" :class="{ active: Number(targetExposure) === ratio }" @click="targetExposure = ratio">{{ ratio }}%</button></div>
          <div class="order-preview"><span>当前敞口 <strong>{{ fixed(player.exposurePercent, 1) }}%</strong></span><span>预计方向 <strong :class="targetDirectionClass">{{ targetDirectionLabel }}</strong></span><span>手续费 <strong>{{ fixed(session.config.feePercent, 2) }}%</strong></span><span>滑点 <strong>{{ fixed(session.config.slippagePercent, 2) }}%</strong></span></div>
          <button class="primary-command auction-submit" :disabled="!canTrade || submitting" @click="submitTargetOrder"><Scale :size="16" />提交目标仓位</button>
          <button v-if="session.currentOrder" class="cancel-order" :disabled="submitting || !canTrade" @click="cancelOrder"><X :size="15" />撤销竞价订单</button>
          <div v-if="session.currentOrder" class="pending-order"><Clock3 :size="15" /><span>已密封提交 {{ fixed(session.currentOrder.targetExposurePercent, 1) }}% 目标敞口</span></div>
        </template>
        <template v-else>
        <label class="quantity-field"><span>{{ isOpening ? '买入' : '卖出' }}股数</span><input v-model.number="quantity" type="number" min="0" :max="orderMax" step="1" :disabled="!canTrade" @input="onQuantityInput" @blur="scheduleOrderCommit(0)" /></label>
        <input v-model.number="quantity" class="quantity-slider" :class="{ sell: !isOpening }" type="range" min="0" :max="Math.max(1, orderMax)" step="1" :disabled="!canTrade || orderMax === 0" @input="onQuantityInput" />
        <div class="quick-row"><button v-for="ratio in [.25,.5,.75,1]" :key="ratio" :disabled="!canTrade || submitting" :class="{ active: !allIn && ratio === selectedRatio }" @click="selectRatio(ratio)">{{ ratio * 100 }}%</button><button :class="{ active: isOpening ? allIn : Number(quantity) === orderMax && orderMax > 0 }" :disabled="!canTrade || submitting || orderMax === 0" @click="selectAllIn">全部</button></div>
        <div class="order-preview">
          <span>{{ allIn ? '成交口径' : '参考金额' }} <strong>{{ allIn ? '按实际开盘价全仓' : money(orderReferencePrice * quantity) }}</strong></span>
          <span v-if="isOpening">预计现金 <strong :class="tone(estimatedCash)">{{ allIn ? '尽可能接近 ¥0.00' : money(estimatedCash) }}</strong></span>
          <span v-else>预计回笼 <strong>{{ money(orderReferencePrice * quantity) }}</strong></span>
        </div>
        <button v-if="session.currentOrder" class="cancel-order" :disabled="submitting || !canTrade" @click="cancelOrder"><X :size="15" />撤销订单</button>
        <div v-if="session.currentOrder" class="pending-order"><Clock3 :size="15" /><span>已保存 {{ session.currentOrder.allIn ? '全部资金买入' : `${session.currentOrder.side === 'SELL' ? '卖出' : '买入'} ${session.currentOrder.quantity} 股` }}</span></div>
        </template>

        <section v-if="showReadyGate" class="ready-gate">
          <header><Flag :size="16" /><strong>{{ gateTitle }}</strong></header>
          <div class="ready-list"><span v-for="item in readiness" :key="item.key" :class="{ done: item.ready }"><CircleCheck :size="14" />{{ item.name }}</span></div>
          <button class="primary-command" :disabled="myReady || submitting" @click="readyNext"><ChevronRight :size="18" />{{ myReady ? '已完成，等待其他参与者' : gateButton }}</button>
        </section>
      </aside>
    </div>

    <Transition name="modal"><div v-if="session.status === 'PAUSED' && !showRestart" class="overlay"><div class="dialog compact"><PauseCircle :size="34" /><h2>存档可以恢复</h2><p>游戏数据完整，恢复后会从第 {{ session.currentRound }} 回合的“{{ phaseName }}”继续，不会重新结算已经完成的阶段。</p><button class="primary-command" :disabled="submitting" @click="resume"><Play :size="17" />恢复游戏</button><button class="restart-save-command" :disabled="submitting" @click="showRestart = true"><RotateCcw :size="16" />重新开始本局</button><button class="text-command" @click="leaveToDetail">稍后再玩</button></div></div></Transition>

    <Transition name="phase-modal"><div v-if="isEventAck" class="overlay"><div class="dialog event-dialog"><Newspaper :size="34" /><span>{{ stage === 'OPENING' ? '开盘前事件' : '收盘前事件' }}</span><h2>{{ currentRound.eventName }}</h2><p>{{ currentRound.eventDescription }}</p><small>计时已暂停，事件对价格的实际影响将在本阶段价格公布时说明。</small><div v-if="session.room" class="ack-count">{{ acknowledgedCount }} / {{ session.room.members.length }} 位玩家已知晓</div><button class="primary-command" :disabled="myEventAck || submitting" @click="ackEvent"><CircleCheck :size="17" />{{ myEventAck ? '等待其他玩家' : '我已知晓' }}</button></div></div></Transition>

    <Transition name="phase-modal"><div v-if="session.phase === 'OPENING_RESULT_ACK'" class="overlay"><div class="dialog result-dialog"><div class="result-icon"><BellRing :size="25" /></div><span>第 {{ session.currentRound }} 回合已开盘</span><h2>{{ price(currentRound.openPrice) }}</h2><div class="result-grid"><div><span>上一收盘</span><strong>{{ price(currentRound.previousClosePrice) }}</strong></div><div><span>全场买入</span><strong>{{ currentRound.openingBuyQuantity || 0 }} 股</strong></div><div><span>开盘基础变化</span><strong :class="tone(currentRound.openingBaseChangePercent)">{{ signed(currentRound.openingBaseChangePercent) }}%</strong></div><div><span>买盘影响</span><strong class="buy">{{ signed(currentRound.openingOrderFlowChangePercent) }}%</strong></div><div><span>事件影响</span><strong :class="tone(currentRound.openingEventChangePercent)">{{ signed(currentRound.openingEventChangePercent) }}%</strong></div><div><span>我的成交</span><strong>{{ myRoundBuy ? `${myRoundBuy.quantity} 股` : '0 股' }}</strong></div></div><div :class="['event-result', { empty: !currentRound.openingEventOccurred }]" role="status"><span>事件结果</span><strong>{{ currentRound.openingEventOccurred ? eventOutcome('OPENING') : '开盘前没有发生随机事件' }}</strong><small v-if="currentRound.openingEventOccurred">{{ eventReason('OPENING') }}</small></div><button class="primary-command" :disabled="myReady || submitting" @click="readyNext"><ChevronRight :size="18" />{{ myReady ? '等待其他参与者' : '进入收盘前' }}</button></div></div></Transition>

    <Transition name="phase-modal">
      <div v-if="session.phase === 'ROUND_RESULT_ACK'" class="overlay">
        <div class="dialog result-dialog">
          <div class="result-icon close"><CandlestickChart :size="25" /></div>
          <span>第 {{ session.currentRound }} 回合已收盘</span>
          <h2>{{ price(currentRound.closePrice) }}</h2>
          <div class="result-grid">
            <div><span>开盘价</span><strong>{{ price(currentRound.openPrice) }}</strong></div>
            <div><span>普通卖出</span><strong>{{ currentRound.closingSellQuantity || 0 }} 股</strong></div>
            <template v-if="['V2', 'V3'].includes(currentRound.pricingEngineVersion)">
              <div><span>趋势动量</span><strong :class="tone(currentRound.momentumChangePercent)">{{ signed(currentRound.momentumChangePercent) }}%</strong></div>
              <div><span>事件传导</span><strong :class="tone(currentRound.scheduledEventChangePercent)">{{ signed(currentRound.scheduledEventChangePercent) }}%</strong></div>
              <div><span>中枢回归</span><strong :class="tone(currentRound.mediumMeanReversionPercent)">{{ signed(currentRound.mediumMeanReversionPercent) }}%</strong></div>
              <div><span>动态噪声</span><strong :class="tone(currentRound.noiseChangePercent)">{{ signed(currentRound.noiseChangePercent) }}%</strong></div>
              <div v-if="currentRound.pricingEngineVersion === 'V3'"><span>基本面锚定</span><strong :class="tone(currentRound.fundamentalChangePercent)">{{ signed(currentRound.fundamentalChangePercent) }}%</strong></div>
              <div v-if="currentRound.pricingEngineVersion === 'V3'"><span>财报预期差</span><strong :class="tone(currentRound.reportSurprisePercent)">{{ signed(currentRound.reportSurprisePercent) }}%</strong></div>
              <template v-if="currentRound.financialReportPublished"><div><span>需求 / 订单</span><strong>{{ fixed(currentRound.demandIndex, 0) }} / {{ fixed(currentRound.orderIndex, 0) }}</strong></div><div><span>库存 / 利润率</span><strong>{{ fixed(currentRound.inventoryIndex, 0) }} / {{ fixed(currentRound.marginIndex, 0) }}</strong></div><div><span>现金流</span><strong>{{ fixed(currentRound.cashFlowIndex, 0) }}</strong></div><div><span>基本面评分</span><strong>{{ fixed(currentRound.fundamentalScore, 1) }}</strong></div></template>
              <div v-if="Number(currentRound.clampAdjustmentPercent)"><span>波动保护修正</span><strong :class="tone(currentRound.clampAdjustmentPercent)">{{ signed(currentRound.clampAdjustmentPercent) }}%</strong></div>
              <div><span>本回合总变化</span><strong :class="tone(currentRound.totalChangePercent)">{{ signed(currentRound.totalChangePercent) }}%</strong></div>
            </template>
            <template v-else>
              <div><span>收盘基础变化</span><strong :class="tone(currentRound.baseChangePercent)">{{ signed(currentRound.baseChangePercent) }}%</strong></div>
              <div><span>事件影响</span><strong :class="tone(currentRound.closingEventChangePercent)">{{ signed(currentRound.closingEventChangePercent) }}%</strong></div>
              <div><span>均值回归</span><strong :class="tone(currentRound.meanReversionChangePercent)">{{ signed(currentRound.meanReversionChangePercent) }}%</strong></div>
            </template>
            <div><span>强制卖出</span><strong :class="{ sell: Number(currentRound.forcedSellQuantity) > 0 }">{{ currentRound.forcedSellQuantity || 0 }} 股</strong></div>
            <div><span>卖盘影响</span><strong class="sell">{{ signed(currentRound.orderFlowChangePercent) }}%</strong></div>
          </div>
          <div :class="['event-result', { empty: !currentRound.closingEventOccurred }]" role="status"><span>事件结果</span><strong>{{ currentRound.closingEventOccurred ? eventOutcome('CLOSING') : '收盘前没有发生随机事件' }}</strong><small v-if="currentRound.closingEventOccurred">{{ eventReason('CLOSING') }}</small></div>
          <button class="primary-command" :disabled="myReady || submitting" @click="readyNext"><ChevronRight :size="18" />{{ myReady ? '等待其他参与者' : session.config.loanEnabled ? '进入银行还贷期' : session.currentRound >= session.config.roundCount ? '查看最终收益' : '进入下一回合' }}</button>
        </div>
      </div>
    </Transition>

    <GameManualDialog :open="showRules" :config="session.config" @close="showRules = false" />

    <Transition name="modal"><div v-if="showResearch" class="overlay" @click.self="showResearch = false"><div class="dialog compact"><button class="dialog-close" title="关闭" @click="showResearch = false"><X :size="18" /></button><FlaskConical :size="31" /><h2>市场研究</h2><p v-if="researchResult">{{ researchDirectionLabel(researchResult.direction) }} · 估计区间 {{ researchResult.rangeLow }}～{{ researchResult.rangeHigh }}</p><div class="credit-kind-tabs"><button v-for="action in researchActions" :key="action.value" :disabled="submitting" @click="runResearch(action.value)">{{ action.label }}</button></div><small v-if="researchResult">剩余研究点 {{ researchResult.remainingPoints }}</small></div></div></Transition>

    <Transition name="modal"><div v-if="showCredit" class="overlay credit-overlay" @click.self="showCredit = false"><div class="dialog credit-dialog"><button class="dialog-close" title="关闭" @click="showCredit = false"><X :size="18" /></button><Landmark :size="31" /><span>{{ creditStageActive ? `第 ${session.currentRound} 回合 · 银行贷款阶段` : repaymentStageActive ? `第 ${session.currentRound} 回合 · 银行还贷阶段` : '银行融资' }}</span>
      <label class="bank-select"><span>选择银行</span><CustomSelect v-model="selectedCreditProduct" :options="bankSelectOptions" @change="selectCreditProductCode" /><small>本行债务 {{ money(selectedBankDebt) }} · 可借 {{ money(selectedCreditAvailable) }}</small></label>
      <div class="credit-metrics"><div><span>净资产</span><strong>{{ money(session.credit.netAsset) }}</strong></div><div><span>本行可用额度</span><strong>{{ money(selectedCreditAvailable) }}</strong></div><div><span>本行贷款本金</span><strong>{{ money(selectedCreditBank?.principal || 0) }}</strong></div><div><span>本行未付利息</span><strong>{{ money(selectedCreditBank?.accruedInterest || 0) }}</strong></div><div><span>总股票敞口</span><strong>{{ fixed(session.credit.exposurePercent, 1) }}%</strong></div><div><span>总债务 LTV</span><strong>{{ fixed(session.credit.ltvPercent, 1) }}%</strong></div><div v-if="selectedCreditBank?.nearestMaturityRound"><span>本行最近到期</span><strong>第 {{ selectedCreditBank.nearestMaturityRound }} 回合</strong></div></div>
      <div class="ltv-track"><i :style="{ width: `${Math.min(100, Number(session.credit.ltvPercent || 0))}%` }"></i><span>{{ creditRiskLabel(session.credit.riskState) }}</span><b v-if="selectedCreditBank?.warningLtv" :style="{ left: `${selectedCreditBank.warningLtv}%` }" title="警戒线"></b><b v-if="selectedCreditBank?.blockLtv" :style="{ left: `${selectedCreditBank.blockLtv}%` }" title="禁借线"></b><b v-if="selectedCreditBank?.liquidationLtv" :style="{ left: `${selectedCreditBank.liquidationLtv}%` }" title="强平线"></b></div>
      <div class="credit-kind-tabs"><button :class="{ active: creditMode === 'BORROW' }" :disabled="repaymentStageActive" @click="creditMode = 'BORROW'">借款</button><button :class="{ active: creditMode === 'REPAY' }" :disabled="selectedBankDebt <= 0" @click="creditMode = 'REPAY'">还款</button><button :class="{ active: creditMode === 'LOANS' }" @click="creditMode = 'LOANS'">本行贷款</button></div>
      <template v-if="creditMode !== 'LOANS'">
        <label v-if="creditMode === 'BORROW'" class="credit-amount"><span>贷款期限</span><CustomSelect v-model="creditMaturityRounds" :options="maturityOptions" /></label>
        <label class="credit-amount"><span>{{ creditMode === 'BORROW' ? '借款' : '还款' }}金额</span><input v-model.number="creditAmount" type="number" min="0" :step="creditMode === 'BORROW' ? 100 : .0001" /></label>
        <div class="credit-quick"><button v-for="ratio in [.25,.5,1]" :key="ratio" type="button" @click="setCreditRatio(ratio)">{{ ratio * 100 }}%</button></div>
        <div class="credit-actions"><button v-if="creditMode === 'BORROW'" class="primary-command" :disabled="submitting || !canBorrowCredit || selectedCreditAvailable <= 0 || Number(creditAmount) <= 0 || Number(creditAmount) > selectedCreditAvailable" @click="borrowCredit">确认借款</button><button v-else class="danger-command" :disabled="submitting || !canRepayCredit || selectedBankDebt <= 0 || repayableCash <= 0 || Number(creditAmount) <= 0" @click="repayCredit">确认还款</button></div>
        <small>每家银行独立维护贷款、利息和还款记录；可以切换银行继续借款。总额度与风险仍按全部银行债务共同计算。</small>
      </template>
      <template v-else-if="selectedBankLoans.length"><div class="loan-list"><article v-for="loan in pagedLoans" :key="loan.id" :class="{ closed: loan.status === 'CLOSED' }"><header><strong>贷款 #{{ loan.id }}</strong><span>{{ loan.status === 'CLOSED' ? '已结清' : `第 ${loan.maturityRound} 回合到期` }}</span></header><div v-if="loan.status === 'CLOSED'" class="loan-settlement"><span>借款 <strong>{{ money(loan.principalOriginal) }}</strong></span><span>还款（本金） <strong>{{ money(Number(loan.principalOriginal) - Number(loan.principalOutstanding)) }}</strong></span></div><div v-else><span>剩余本金 {{ money(loan.principalOutstanding) }}</span><span>未付利息 {{ money(loan.accruedInterest) }}</span><span>利率 {{ fixed(loan.interestRate, 2) }}%/回合</span><span>剩余 {{ loan.remainingRounds }} 回合</span></div><small>原到期第 {{ loan.originalMaturityRound }} 回合 · 已延期 {{ loan.extensionCount }} 次 / {{ loan.totalExtendedRounds }} 回合</small><div v-if="loan.canExtend" class="loan-extension"><CustomSelect v-model="extensionRounds[loan.id]" :options="extensionOptions" /><button class="btn btn-outline" :disabled="submitting" @click="extendLoan(loan)">延期</button></div></article></div><PaginationBar v-model="loanPage" :total-pages="loanPages" :total-items="selectedBankLoans.length" /></template>
      <div v-else-if="creditMode === 'LOANS'" class="no-loans">该银行暂无贷款合同</div>
      <div v-if="selectedBankLedger.length" class="credit-ledger"><div v-for="row in selectedBankLedger" :key="row.id"><span>第{{ row.roundNo }}回合 · {{ ledgerLabel(row.type) }}</span><strong :class="ledgerTone(row)">{{ ledgerAmount(row) }}</strong></div></div>
      <div v-if="creditFlowActive" class="credit-stage-footer"><div v-if="session.room" class="credit-readiness"><span v-for="member in creditReadiness" :key="member.id" :class="{ done: member.ready }"><i>{{ member.initial }}</i><b>{{ member.name }}</b><small>{{ member.ready ? '已完成' : '未完成' }}</small></span></div><span>{{ myReady ? '已完成操作，等待其他玩家' : repaymentStageActive ? '可按需还款，完成后进入下一回合' : '借款或还款完成后进入开盘前交易' }}</span><button class="primary-command" :disabled="submitting || myReady" @click="readyNext"><ChevronRight :size="18" />{{ myReady ? '等待其他玩家' : repaymentStageActive ? '完成还贷' : '完成融资，进入开盘前' }}</button></div>
    </div></div></Transition>

    <Transition name="modal"><div v-if="showEvent" class="overlay" @click.self="showEvent = false"><div class="dialog event-dialog"><button class="dialog-close" title="关闭" @click="showEvent = false"><X :size="18" /></button><Newspaper :size="31" /><span>第 {{ session.currentRound }} 回合{{ stage === 'OPENING' ? '开盘前' : '收盘前' }}事件</span><h2>{{ stageEventOccurred ? currentRound.eventName : '本阶段暂未发生事件' }}</h2><p>{{ stageEventOccurred ? currentRound.eventDescription : '本阶段没有抽到随机事件。' }}</p><div v-if="stageEventOccurred" class="event-tags"><span>{{ eventTypeLabel(currentRound.eventType) }}</span><span>{{ relevanceLabel(currentRound.eventRelevance) }}</span><span>{{ credibilityLabel(currentRound.eventCredibility) }}</span><span>{{ surpriseLabel(currentRound.eventSurprise) }}</span><span>{{ strengthLabel(currentRound.eventStrength) }}</span></div><div v-if="eventResultVisible" :class="['event-result', { empty: !stageEventOccurred }]" role="status"><span>事件结果</span><strong>{{ stageEventOccurred ? eventOutcome(stage) : '本阶段没有发生随机事件' }}</strong><small v-if="stageEventOccurred">{{ eventReason(stage) }}</small></div><div v-else-if="stageEventOccurred" class="event-pending" role="status"><Clock3 :size="16" /><span>结果将在本阶段价格公布后揭晓</span></div></div></div></Transition>

    <Transition name="modal"><div v-if="showRestart" class="overlay" @click.self="showRestart = false"><div class="dialog compact"><RotateCcw :size="32" /><h2>确认重新开始？</h2><p>{{ session.room ? '提交后需所有真人玩家确认；全员确认后本局结束并返回游戏主界面。' : '当前进度将结束，并返回游戏主界面重新创建一局。' }}</p><div v-if="session.room" class="ack-count">{{ restartCount }} / {{ session.room.members.length }} 位玩家已确认</div><button class="danger-command" :disabled="submitting || myRestartReady" @click="confirmRestart"><RotateCcw :size="17" />{{ myRestartReady ? '已确认，等待其他玩家' : '确认重新开始' }}</button><button class="text-command" @click="showRestart = false">取消</button></div></div></Transition>
    <Transition name="modal"><div v-if="showCloseRoom" class="room-close-confirm" @click.self="showCloseRoom = false"><div><DoorClosed :size="28" /><h2>关闭多人房间？</h2><p>多人场次会立即结束，所有玩家返回游戏主界面；各自的单人进度不会改变。</p><button class="danger-command" :disabled="submitting" @click="confirmCloseRoom">确认关闭房间</button><button class="text-command" @click="showCloseRoom = false">取消</button></div></div></Transition>
  </div>
  <div v-else class="loading">
    <LoadingSpinner v-if="!error" text="正在读取游戏存档" />
    <div v-else class="load-failure"><TriangleAlert :size="30" /><h2>暂时无法读取存档</h2><p>{{ error }}</p><button class="primary-command" @click="load">重新读取存档</button><router-link class="text-command" to="/customer/entertainment/history">查看游玩记录</router-link></div>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { onBeforeRouteLeave, useRoute, useRouter } from 'vue-router'
import { Activity, ArrowLeft, BellRing, BookOpen, Bot, CandlestickChart, ChevronRight, CircleCheck, Clock3, DoorClosed, FastForward, Flag, FlaskConical, Landmark, Newspaper, Pause, PauseCircle, Play, RotateCcw, Scale, TrendingDown, TrendingUp, TriangleAlert, Trophy, X, Zap } from 'lucide-vue-next'
import { abandonGameSession, borrowGameCredit, closeGameRoom, ensureEntertainmentGuest, extendGameLoan, getGameSession, pauseGameSession, performGameResearch, repayGameCredit, resumeGameSession, sendGameAction, submitDailyChallenge, syncGameSession } from '../../api/entertainment'
import LoadingSpinner from '../../components/LoadingSpinner.vue'
import GameManualDialog from './GameManualDialog.vue'
import CustomSelect from '../../components/CustomSelect.vue'
import PaginationBar from '../../components/PaginationBar.vue'
import { notifyError } from '../../services/notice'

const route = useRoute(); const router = useRouter(); const session = ref(null); const error = ref(''); const submitting = ref(false); const challengeResult = ref(null); let challengeSubmitted = false
const now = ref(Date.now()); const quantity = ref(0); const allIn = ref(false); const targetExposure = ref(0); const hovered = ref(null); const chartGroupSize = ref(1); const showRules = ref(false); const showEvent = ref(false); const showRestart = ref(false); const showCloseRoom = ref(false); const showCredit = ref(false); const showResearch = ref(false); const researchResult = ref(null); const creditAmount = ref(0); const creditMode = ref('BORROW'); const selectedCreditProduct = ref('')
const researchActions = [{ value: 'VERIFY_EVENT', label: '核验事件' }, { value: 'SURVEY_DEMAND', label: '需求调查' }, { value: 'PREVIEW_REPORT', label: '财报预览' }, { value: 'CHECK_LIQUIDITY', label: '流动性观察' }]
const creditMaturityRounds = ref(1); const extensionRounds = ref({}); const loanPagesByBank = ref({})
const speedOptions = [1, 1.5, 2, 3, 5]
const preferredSpeed = Number(localStorage.getItem('entertainment_auto_advance_speed'))
const preferredAutoAdvance = localStorage.getItem('entertainment_auto_advance') === 'true'
const stageNotice = ref(''); const stageNoticeDetail = ref(''); let timer; let poller; let noticeTimer; let roundIntroTimer; let orderTimer; let syncing = false; let leaving = false; let orderRevision = 0; let orderCommitChain = Promise.resolve()
const sessionId = computed(() => Number(route.params.sessionId)); const currentRound = computed(() => session.value.currentRoundState)
const challengeId = computed(() => Number(localStorage.getItem(`guess_stock_challenge_${sessionId.value}`)) || 0)
const player = computed(() => session.value.participants.find(item => item.currentPlayer && item.type === 'PLAYER') || { cash: 0, quantity: 0, asset: 0 })
const stage = computed(() => ['CLOSING_EVENT_ACK', 'CLOSING_TRADING', 'ROUND_RESULT_ACK', 'CREDIT_REPAYMENT'].includes(session.value.phase) ? 'CLOSING' : 'OPENING')
const isOpening = computed(() => stage.value === 'OPENING'); const isEventAck = computed(() => session.value.phase?.endsWith('EVENT_ACK'))
const isManual = computed(() => session.value.config.progressionMode === 'MANUAL')
const currentPrice = computed(() => Number(currentRound.value.closePrice ?? currentRound.value.openPrice ?? currentRound.value.previousClosePrice ?? session.value.config.initialPrice))
const priceLabel = computed(() => currentRound.value.closePrice != null ? '最新收盘价' : currentRound.value.openPrice != null ? '本回合开盘价' : '上一收盘价')
const currentReturn = computed(() => { const initial = Number(player.value.initialCash || session.value.config.initialCash); const asset = session.value.status === 'COMPLETED' ? Number(session.value.finalAsset ?? player.value.asset) : Number(player.value.asset); return (asset - initial) / initial * 100 })
const rankedParticipants = computed(() => session.value.participants.map((item, originalIndex) => {
  const initialCash = Number(item.initialCash || session.value.config.initialCash)
  const asset = Number(item.asset || 0)
  const returnPercent = item.returnPercent == null ? (initialCash > 0 ? (asset - initialCash) / initialCash * 100 : 0) : Number(item.returnPercent)
  return { ...item, originalIndex, returnPercent }
}).sort((left, right) => right.returnPercent - left.returnPercent || Number(right.asset) - Number(left.asset) || left.originalIndex - right.originalIndex))
const marketTimeline = computed(() => {
  const rounds = session.value.rounds.filter(round => round.clearedAt).map(round => ({ key: `R-${round.roundNo}`, roundNo: round.roundNo, kind: round.eventName ? '事件与收盘' : '市场收盘', text: `${round.eventName || round.trendState || '市场'} · ${price(round.closePrice)}` }))
  const ai = session.value.aiDecisions.filter(item => item.status === 'EXECUTED').map((item, index) => ({ key: `AI-${item.participantId}-${item.roundNo}-${item.stage}-${index}`, roundNo: item.roundNo, kind: item.participantName, text: `${item.decisionType || 'KEEP'} · 目标 ${fixed(item.targetPositionPercent || 0, 0)}%` }))
  const trades = session.value.recentTrades.filter(item => Number(item.participantId) === Number(player.value.id)).map(item => ({ key: `T-${item.id}`, roundNo: item.roundNo, kind: '我的成交', text: `${item.side === 'BUY' ? '买入' : '卖出'} ${item.quantity} 股 · ${price(item.executionPrice)}` }))
  const credit = (session.value.credit?.banks || []).flatMap(bank => (bank.recentLedger || []).map(item => ({ key: `C-${bank.productCode}-${item.id}`, roundNo: item.roundNo, kind: bank.productName || '银行', text: `${ledgerLabel(item.type)} · ${ledgerAmount(item)}` })))
  return [...rounds, ...ai, ...trades, ...credit].sort((a, b) => b.roundNo - a.roundNo || b.key.localeCompare(a.key)).slice(0, 16)
})
const currentPlayerRank = computed(() => Math.max(1, rankedParticipants.value.findIndex(item => item.currentPlayer) + 1))
const enabledCreditProducts = computed(() => (session.value.config.creditProducts || []).filter(item => item.enabled))
const bankSelectOptions = computed(() => enabledCreditProducts.value.map(item => ({ value: item.code, label: item.name })))
const selectedCreditConfig = computed(() => enabledCreditProducts.value.find(item => item.code === selectedCreditProduct.value))
const selectedCreditBank = computed(() => (session.value.credit.banks || []).find(item => item.productCode === selectedCreditProduct.value))
const selectedBankDebt = computed(() => Number(selectedCreditBank.value?.effectiveDebt || 0))
const selectedBankLoans = computed(() => selectedCreditBank.value?.contracts || [])
const selectedBankLedger = computed(() => selectedCreditBank.value?.recentLedger || [])
const indebtedBankCount = computed(() => (session.value.credit.banks || []).filter(item => Number(item.effectiveDebt) > 0).length)
const maturityOptions = computed(() => Array.from({ length: Math.max(1, Number(selectedCreditConfig.value?.maturityRounds || 1)) }, (_, index) => ({ value: index + 1, label: `${index + 1} 回合` })))
const extensionOptions = computed(() => Array.from({ length: Math.max(1, Number(selectedCreditConfig.value?.maturityRounds || 1)) }, (_, index) => ({ value: index + 1, label: `延期 ${index + 1} 回合` })))
const selectedCreditAvailable = computed(() => Number(selectedCreditBank.value?.availableCredit ?? session.value.credit.productAvailableCredit?.[selectedCreditProduct.value]
  ?? session.value.credit.availableCredit ?? 0))
const roundProgress = computed(() => Math.max(0, ((session.value.currentRound - 1) / session.value.config.roundCount) * 100))
const serverOffset = computed(() => session.value ? new Date(session.value.serverTime).getTime() - Date.now() : 0)
const configuredUnlimitedCreditPhase = computed(() => session.value.phase === 'CREDIT_TRADING'
  ? !session.value.config.creditPhaseTimed
  : session.value.phase === 'CREDIT_REPAYMENT' ? !session.value.config.repaymentPhaseTimed : false)
const remainingMs = computed(() => configuredUnlimitedCreditPhase.value ? null
  : session.value.phaseEndsAt ? Math.max(0, new Date(session.value.phaseEndsAt).getTime() - now.value - serverOffset.value) : null)
const tradingExpired = computed(() => !isManual.value && session.value.phase === `${stage.value}_TRADING` && remainingMs.value === 0)
const canTrade = computed(() => session.value.status === 'ACTIVE' && session.value.phase === `${stage.value}_TRADING` && !tradingExpired.value)
const creditStageActive = computed(() => session.value.phase === 'CREDIT_TRADING')
const repaymentStageActive = computed(() => session.value.phase === 'CREDIT_REPAYMENT')
const creditFlowActive = computed(() => creditStageActive.value || repaymentStageActive.value)
const creditStageExpired = computed(() => creditStageActive.value && session.value.phaseEndsAt && remainingMs.value === 0)
const canBorrowCredit = computed(() => session.value.status === 'ACTIVE'
  && (creditStageActive.value ? !creditStageExpired.value : session.value.phase === 'OPENING_TRADING' && !tradingExpired.value))
const repayableCash = computed(() => Math.max(0, Number(player.value.cash || 0) - Number(player.value.reservedCash || 0)))
const canRepayCredit = computed(() => session.value.status === 'ACTIVE' && !['ROUND_START', 'COMPLETED'].includes(session.value.phase))
const countdown = computed(() => tradingExpired.value ? '已截止' : remainingMs.value == null ? (creditFlowActive.value ? '不限时' : isManual.value ? '手动' : '暂停') : `${Math.ceil(remainingMs.value / 1000)}s`)
const autoAdvanceEnabled = computed(() => session.value?.autoAdvance ?? preferredAutoAdvance)
const autoAdvanceSpeed = computed(() => Number(session.value?.playbackSpeed || (speedOptions.includes(preferredSpeed) ? preferredSpeed : 1)))
const autoAdvanceCountdown = computed(() => autoAdvanceEnabled.value && (isEventAck.value || ['OPENING_RESULT_ACK', 'ROUND_RESULT_ACK'].includes(session.value.phase)) && remainingMs.value != null ? Math.max(0, Math.ceil(remainingMs.value / 1000)) : null)
const phaseName = computed(() => ({ ROUND_START: '新回合', OPENING_EVENT_ACK: '开盘事件', CREDIT_TRADING: '银行贷款', OPENING_TRADING: '开盘前', OPENING_RESULT_ACK: '开盘结果', CLOSING_EVENT_ACK: '收盘事件', CLOSING_TRADING: '收盘前', ROUND_RESULT_ACK: '收盘结果', CREDIT_REPAYMENT: '银行还贷', COMPLETED: '已结束' }[session.value.phase] || session.value.phase))
const orderReferencePrice = computed(() => Number(isOpening.value ? currentRound.value.previousClosePrice : currentRound.value.openPrice || currentPrice.value))
const isTargetAuction = computed(() => session.value.config.tradingMode === 'TARGET_AUCTION')
const targetExposureMax = computed(() => session.value.config.loanEnabled
  ? Math.max(100, ...(session.value.config.creditProducts || []).filter(item => item.enabled).map(item => Number(item.maxExposureRatio || 100))) : 100)
const targetDirectionLabel = computed(() => Number(targetExposure.value) > Number(player.value.exposurePercent || 0) + .05 ? '增持'
  : Number(targetExposure.value) < Number(player.value.exposurePercent || 0) - .05 ? '减持' : '保持')
const targetDirectionClass = computed(() => targetDirectionLabel.value === '增持' ? 'positive' : targetDirectionLabel.value === '减持' ? 'negative' : '')
const editableBuyReservation = computed(() => session.value.currentOrder?.stage === 'OPENING'
  && session.value.currentOrder?.status === 'PENDING' ? Number(session.value.currentOrder.reservedAmount || 0) : 0)
const availableCash = computed(() => Number(player.value.cash || 0) - Number(player.value.reservedCash || 0) + editableBuyReservation.value)
const orderMax = computed(() => isOpening.value ? Math.max(0, Math.min(Number(session.value.config.maxBuyQuantity || 1000), Math.floor(availableCash.value / Math.max(.01, Number(currentRound.value.previousClosePrice))))) : Number(player.value.quantity || 0))
const estimatedCash = computed(() => Number(player.value.cash) - orderReferencePrice.value * Number(quantity.value || 0))
const selectedRatio = computed(() => orderMax.value > 0 ? Number(quantity.value) / orderMax.value : 0)
const chartGroupOptions = computed(() => [1, 2, 3, 5, 10].filter(size => size === 1 || size <= Number(session.value.config.roundCount)))
const myMember = computed(() => session.value.room?.members.find(item => item.currentPlayer)); const myReady = computed(() => session.value.room ? myMember.value?.readyStage === session.value.phase : false)
const creditReadiness = computed(() => (session.value.room?.members || []).map(member => ({ id: member.id, name: member.displayName, initial: (member.displayName || '?').trim().slice(0, 1).toUpperCase(), ready: member.readyStage === session.value.phase })))
const loanPages = computed(() => Math.max(1, Math.ceil(selectedBankLoans.value.length / 3)))
const loanPage = computed({ get: () => Number(loanPagesByBank.value[selectedCreditProduct.value] || 1), set: value => { loanPagesByBank.value[selectedCreditProduct.value] = Number(value) } })
const pagedLoans = computed(() => selectedBankLoans.value.slice((loanPage.value - 1) * 3, loanPage.value * 3))
const myEventAck = computed(() => session.value.room ? myMember.value?.eventAckStage === session.value.phase : false)
const restartCount = computed(() => session.value.room?.members.filter(item => item.restartReady).length || 0); const myRestartReady = computed(() => Boolean(myMember.value?.restartReady))
const acknowledgedCount = computed(() => session.value.room?.members.filter(item => item.eventAckStage === session.value.phase).length || 0)
const myTrades = computed(() => (session.value.playerTrades || session.value.recentTrades)
  .filter(item => Number(item.participantId) === Number(player.value.id)))
const myTradesByRound = computed(() => {
  const result = new Map()
  for (const trade of myTrades.value) {
    const roundNo = Number(trade.roundNo)
    if (!Number.isInteger(roundNo)) continue
    const roundTrades = result.get(roundNo) || {}
    roundTrades[trade.side] = trade
    result.set(roundNo, roundTrades)
  }
  return result
})
const myRoundBuy = computed(() => myTrades.value.find(item => Number(item.roundNo) === Number(session.value.currentRound) && item.side === 'BUY'))
const stageEventOccurred = computed(() => stage.value === 'OPENING'
  ? Boolean(currentRound.value.openingEventOccurred ?? currentRound.value.eventStage === 'OPENING')
  : Boolean(currentRound.value.closingEventOccurred ?? currentRound.value.eventStage === 'CLOSING'))
const eventResultVisible = computed(() => {
  if (stage.value === 'OPENING') return !['OPENING_EVENT_ACK', 'CREDIT_TRADING', 'OPENING_TRADING'].includes(session.value.phase)
  return session.value.phase === 'ROUND_RESULT_ACK' || session.value.status === 'COMPLETED'
})
function eventOutcome(eventStage) {
  if (!currentRound.value.eventName) return ''
  const opening = eventStage === 'OPENING'
  const effective = opening ? currentRound.value.openingEventEffective : currentRound.value.closingEventEffective
  const change = Number(opening ? currentRound.value.openingEventChangePercent : currentRound.value.closingEventChangePercent)
  const tier = opening ? currentRound.value.openingEventTier : currentRound.value.closingEventTier
  const tierText = ({ WEAK: '轻微', NORMAL: '常规', STRONG: '强烈' }[tier] || '')
  return effective ? `${currentRound.value.eventName}形成${tierText}影响，令本阶段价格${change >= 0 ? '上升' : '下降'} ${Math.abs(change).toFixed(2)}%` : `${currentRound.value.eventName}发生，但未造成可观察的价格变化`
}
function eventReason(eventStage) { return eventStage === 'OPENING' ? currentRound.value.openingEventReason : currentRound.value.closingEventReason }
function eventTypeLabel(value) { return ({ DEMAND: '需求', SUPPLY: '供给', POLICY: '政策', FINANCIAL: '财务', ACCIDENT: '突发', INDUSTRY: '行业', SENTIMENT: '情绪' }[value] || '行业') }
function relevanceLabel(value) { return ({ LOW: '低相关', MEDIUM: '中相关', HIGH: '高相关' }[value] || '中相关') }
function credibilityLabel(value) { return ({ RUMOR: '传闻', PRELIMINARY: '初步确认', CONFIRMED: '已确认' }[value] || '已确认') }
function surpriseLabel(value) { return ({ EXPECTED: '已有预期', NORMAL: '一般意外', MAJOR: '显著超预期' }[value] || '一般意外') }
function strengthLabel(value) { return ({ WEAK: '弱强度', MEDIUM: '中强度', STRONG: '强强度' }[value] || '中强度') }
const showReadyGate = computed(() => session.value.status === 'ACTIVE' && isManual.value && canTrade.value)
const gateTitle = computed(() => isOpening.value ? '确认买入订单，进入开盘结算' : '确认卖出订单，进入收盘结算')
const gateButton = computed(() => isOpening.value ? '完成开盘前操作' : '完成收盘前操作')
const readiness = computed(() => {
  const humans = session.value.room ? session.value.room.members.map(item => ({ key: `H${item.id}`, name: item.displayName, ready: item.readyStage === session.value.phase })) : [{ key: 'ME', name: player.value.displayName || '我', ready: false }]
  const aiStage = session.value.aiDecisions.filter(item => item.roundNo === session.value.currentRound && item.stage === stage.value)
  const ais = session.value.participants.filter(item => item.type === 'AI').map(item => { const decision = aiStage.find(task => task.participantId === item.id); return { key: `A${item.id}`, name: item.displayName, ready: decision && decision.status !== 'PENDING' } })
  return [...humans, ...ais]
})

const chartModel = computed(() => {
  const left = 58; const right = 792; const top = 34; const bottom = 258
  const count = Number(session.value.config.roundCount)
  const groupSize = chartGroupSize.value
  const groupCount = Math.ceil(count / groupSize)
  const completed = session.value.rounds
    .filter(round => round.openPrice != null && round.closePrice != null)
    .sort((leftRound, rightRound) => Number(leftRound.roundNo) - Number(rightRound.roundNo))
  const grouped = new Map()
  for (const round of completed) {
    const roundNo = Number(round.roundNo)
    const groupIndex = Math.floor((roundNo - 1) / groupSize)
    const rounds = grouped.get(groupIndex) || []
    rounds.push(round)
    grouped.set(groupIndex, rounds)
  }
  const initialPrice = Number(session.value.config.initialPrice)
  const baselinePrice = initialPrice
  const values = [baselinePrice, ...completed.flatMap(round => [Number(round.openPrice), Number(round.closePrice)])]
  const rawMin = Math.min(...values); const rawMax = Math.max(...values)
  const padding = Math.max((rawMax - rawMin) * .22, rawMax * .025, .01)
  const min = Math.max(.01, rawMin - padding); const max = rawMax + padding
  const y = value => bottom - (Number(value) - min) / (max - min) * (bottom - top)
  const x = groupIndex => left + (right - left) * (groupIndex + 1) / groupCount
  const candleWidth = Math.max(3, Math.min(16, 240 / groupCount))
  const aggregateTrade = (rounds, side) => {
    const trades = rounds.map(round => myTradesByRound.value.get(Number(round.roundNo))?.[side]).filter(Boolean)
    if (!trades.length) return null
    const quantity = trades.reduce((total, trade) => total + Number(trade.quantity || 0), 0)
    const amount = trades.reduce((total, trade) => total + Number(trade.amount ?? Number(trade.executionPrice || 0) * Number(trade.quantity || 0)), 0)
    return { quantity, executionPrice: quantity > 0 ? amount / quantity : 0, tradeCount: trades.length }
  }
  const bars = [...grouped.entries()].map(([groupIndex, rounds]) => {
    const firstRound = rounds[0]; const lastRound = rounds.at(-1)
    const startRound = groupIndex * groupSize + 1; const endRound = Number(lastRound.roundNo)
    const openPrice = Number(firstRound.openPrice); const closePrice = Number(lastRound.closePrice)
    const actualOpenY = y(openPrice); const actualCloseY = y(closePrice)
    const toneName = closePrice > openPrice ? 'up' : closePrice < openPrice ? 'down' : 'flat'
    const actualHeight = Math.abs(actualCloseY - actualOpenY)
    const height = Math.max(3, actualHeight)
    const middle = (actualOpenY + actualCloseY) / 2
    const bodyTop = actualHeight < 3 ? middle - height / 2 : Math.min(actualOpenY, actualCloseY)
    const bodyBottom = bodyTop + height
    const visualOpenY = toneName === 'up' ? bodyBottom : toneName === 'down' ? bodyTop : middle
    const visualCloseY = toneName === 'up' ? bodyTop : toneName === 'down' ? bodyBottom : middle
    const eventNames = [...new Set(rounds.filter(round => round.eventName).map(round => round.eventName))]
    return { key: `G${groupIndex}`, round: endRound, label: startRound === endRound ? `第 ${startRound} 回合` : `第 ${startRound}-${endRound} 回合`, x: x(groupIndex), width: candleWidth,
      yOpen: visualOpenY, yClose: visualCloseY, centerY: middle, top: bodyTop, height, openPrice, closePrice,
      change: (closePrice - openPrice) / openPrice * 100, tone: toneName, eventName: eventNames.join('、'),
      openingFlow: rounds.reduce((total, round) => total + Number(round.openingOrderFlowChangePercent || 0), 0),
      closingFlow: rounds.reduce((total, round) => total + Number(round.orderFlowChangePercent || 0), 0),
      forcedSell: rounds.reduce((total, round) => total + Number(round.forcedSellQuantity || 0), 0),
      mineBuy: aggregateTrade(rounds, 'BUY'), mineSell: aggregateTrade(rounds, 'SELL') }
  })
  const initialYValue = y(baselinePrice)
  const connectors = bars.map((bar, index) => {
    const previous = bars[index - 1]
    const previousPrice = previous ? previous.closePrice : baselinePrice
    return { key: `connector-${bar.round}`, x1: previous ? previous.x : left,
      y1: previous ? previous.centerY : initialYValue, x2: bar.x, y2: bar.centerY,
      tone: bar.openPrice > previousPrice ? 'up' : bar.openPrice < previousPrice ? 'down' : 'flat' }
  })
  const roundStep = groupCount <= 10 ? 1 : Math.ceil(groupCount / 8)
  const roundTicks = [{ key: 'start', label: '起点', x: left }, ...Array.from({ length: groupCount }, (_, groupIndex) => groupIndex)
    .filter(groupIndex => groupIndex === 0 || groupIndex === groupCount - 1 || (groupIndex + 1) % roundStep === 0)
    .map(groupIndex => {
      const startRound = groupIndex * groupSize + 1
      const endRound = Math.min(count, startRound + groupSize - 1)
      return { key: `tick-${groupIndex}`, label: groupSize === 1 ? `第${startRound}回合` : `${startRound}-${endRound}`, x: x(groupIndex) }
    })]
  const priceTicks = [top, top + 56, top + 112, top + 168, bottom]
    .map(tickY => ({ y: tickY, price: max - (tickY - top) / (bottom - top) * (max - min) }))
  return { bars, connectors, roundTicks, priceTicks, initialY: initialYValue, baselinePrice, baselineLabel: '初始' }
})
const chartTicks = computed(() => chartModel.value.roundTicks)
const chartPriceTicks = computed(() => chartModel.value.priceTicks)
const initialY = computed(() => chartModel.value.initialY)
const chartBaselinePrice = computed(() => chartModel.value.baselinePrice)
const chartBaselineLabel = computed(() => chartModel.value.baselineLabel)
const roundBars = computed(() => chartModel.value.bars)
const barConnectors = computed(() => chartModel.value.connectors)
const tooltipStyle = computed(() => ({ left: `${hovered.value.x / 8.2}%`, top: `calc(${Math.min(68, Math.max(5, hovered.value.top / 3))}% - 18px)` }))
const latestCompletedRound = computed(() => session.value.rounds.filter(round => round.openPrice != null && round.closePrice != null).at(-1))
const latestRoundChange = computed(() => latestCompletedRound.value ? (Number(latestCompletedRound.value.closePrice) - Number(latestCompletedRound.value.openPrice)) / Number(latestCompletedRound.value.openPrice) * 100 : 0)
const priceTone = computed(() => tone(latestRoundChange.value))
const currentChangeLabel = computed(() => `${signed(latestRoundChange.value)}%`)

function moveHover(event) { if (!roundBars.value.length) { hovered.value = null; return } const rect = event.currentTarget.getBoundingClientRect(); const x = (event.clientX - rect.left) / rect.width * 820; hovered.value = roundBars.value.reduce((best, bar) => Math.abs(bar.x - x) < Math.abs(best.x - x) ? bar : best, roundBars.value[0]) }
function setChartGroupSize(size) { chartGroupSize.value = size; hovered.value = null }
function action(actionType, extra = {}) { return sendGameAction(sessionId.value, { clientRequestId: `${actionType}-${Date.now()}-${Math.random().toString(16).slice(2)}`, actionType, ...extra }) }
async function run(task) { submitting.value = true; try { session.value = await task(); return true } catch (e) { notifyError(e.message); return false } finally { submitting.value = false } }
function openCredit() { const indebted = (session.value.credit.banks || []).find(item => Number(item.effectiveDebt) > 0); selectedCreditProduct.value = selectedCreditProduct.value || indebted?.productCode || enabledCreditProducts.value[0]?.code || ''; creditMaturityRounds.value = Number(selectedCreditConfig.value?.maturityRounds || 1); creditMode.value = repaymentStageActive.value ? (selectedBankDebt.value > 0 ? 'REPAY' : 'LOANS') : 'BORROW'; creditAmount.value = 0; loanPage.value = Math.min(loanPage.value, loanPages.value); for (const loan of selectedBankLoans.value) if (!extensionRounds.value[loan.id]) extensionRounds.value[loan.id] = 1; showCredit.value = true }
function setCreditRatio(ratio) { const base = creditMode.value === 'BORROW' ? selectedCreditAvailable.value : Math.min(selectedBankDebt.value, repayableCash.value); creditAmount.value = creditMode.value === 'REPAY' ? Number((base * ratio).toFixed(4)) : Math.floor(base * ratio) }
function selectCreditProductCode(code) { const product = enabledCreditProducts.value.find(item => item.code === code); if (product) selectCreditProduct(product) }
function selectCreditProduct(product) { selectedCreditProduct.value = product.code; creditMaturityRounds.value = Number(product.maturityRounds || 1); creditAmount.value = 0; if (creditMode.value === 'REPAY' && selectedBankDebt.value <= 0) creditMode.value = repaymentStageActive.value ? 'LOANS' : 'BORROW'; loanPage.value = Math.min(loanPage.value, loanPages.value); for (const loan of selectedBankLoans.value) if (!extensionRounds.value[loan.id]) extensionRounds.value[loan.id] = 1 }
async function borrowCredit() { const ok = await run(() => borrowGameCredit(sessionId.value, { requestId: `BORROW-${Date.now()}-${Math.random().toString(16).slice(2)}`, productCode: selectedCreditProduct.value, amount: Number(creditAmount.value), maturityRounds: Number(creditMaturityRounds.value) })); if (ok) creditAmount.value = 0 }
async function repayCredit() { const ok = await run(() => repayGameCredit(sessionId.value, { requestId: `REPAY-${Date.now()}-${Math.random().toString(16).slice(2)}`, amount: Number(creditAmount.value), productCode: selectedCreditProduct.value })); if (ok) creditAmount.value = 0 }
async function runResearch(actionType) {
  submitting.value = true
  try { researchResult.value = await performGameResearch(sessionId.value, { requestId: `RESEARCH-${Date.now()}-${Math.random().toString(16).slice(2)}`, actionType }) }
  catch (e) { notifyError(e.message) }
  finally { submitting.value = false }
}
function researchDirectionLabel(direction) { return ({ UP: '信号偏强', DOWN: '信号偏弱', FLAT: '信号中性' }[direction] || '暂无结论') }
async function extendLoan(loan) { await run(() => extendGameLoan(sessionId.value, { requestId: `EXTEND-${loan.id}-${Date.now()}`, contractId: loan.id, extensionRounds: Number(extensionRounds.value[loan.id] || 1) })) }
function creditRiskLabel(value) { return ({ NORMAL: '正常', WARNING: '警戒', BLOCKED: '禁止加杠杆', LIQUIDATION: '强制平仓', DEFAULTED: '违约' }[value] || '正常') }
function ledgerLabel(value) { return ({ BORROW: '借款', INTEREST_ACCRUAL: '计息', EXTENSION_FEE: '延期', MANUAL_REPAY: '主动还款', AI_AUTO_REPAY: 'AI 自动还款', MATURITY_REPAY: '到期还款', LIQUIDATION_REPAY: '风险还款', MATURITY_LIQUIDATION: '到期强平', RISK_LIQUIDATION: '风险强平', DEFAULT: '违约' }[value] || value) }
function ledgerAmount(row) { return row.type === 'INTEREST_ACCRUAL' ? signedMoney(row.interestDelta) : signedMoney(row.cashDelta) }
function ledgerTone(row) { return row.type === 'INTEREST_ACCRUAL' ? 'sell' : tone(row.cashDelta) }
function onQuantityInput() { if (!canTrade.value) return; allIn.value = false; scheduleOrderCommit(360) }
function selectRatio(ratio) { if (!canTrade.value) return; allIn.value = false; quantity.value = Math.floor(orderMax.value * ratio); scheduleOrderCommit(0) }
function selectAllIn() {
  if (!canTrade.value) return
  if (isOpening.value) {
    allIn.value = true
    quantity.value = Math.max(1, Math.floor(availableCash.value / Math.max(.01, orderReferencePrice.value)))
  } else {
    allIn.value = false
    quantity.value = orderMax.value
  }
  scheduleOrderCommit(0)
}
async function submitTargetOrder() {
  if (!canTrade.value || !isTargetAuction.value) return
  const exposure = Math.max(0, Math.min(targetExposureMax.value, Number(targetExposure.value) || 0))
  await run(() => action('PLACE_ORDER', { targetExposurePercent: exposure }))
  targetExposure.value = Number(session.value.currentOrder?.targetExposurePercent ?? exposure)
}
function scheduleOrderCommit(delay = 360) {
  clearTimeout(orderTimer)
  const revision = ++orderRevision
  const desired = { side: isOpening.value ? 'BUY' : 'SELL', quantity: Math.max(0, Math.floor(Number(quantity.value) || 0)), allIn: Boolean(allIn.value && isOpening.value), stage: stage.value }
  orderTimer = setTimeout(() => {
    orderCommitChain = orderCommitChain.then(() => commitOrder(desired, revision))
  }, delay)
}
async function commitOrder(desired, revision) {
  if (!canTrade.value || desired.stage !== stage.value || revision !== orderRevision) return
  submitting.value = true
  try {
    let next
    if (!desired.allIn && desired.quantity === 0) {
      if (!session.value.currentOrder) return
      next = await action('CANCEL_ORDER')
    } else {
      next = await action('PLACE_ORDER', { side: desired.side, quantity: desired.quantity, allIn: desired.allIn })
    }
    session.value = next
    if (revision === orderRevision) {
      quantity.value = next.currentOrder?.quantity || 0
      allIn.value = Boolean(next.currentOrder?.allIn)
    }
  } catch (e) { notifyError(e.message) }
  finally { submitting.value = false }
}
async function cancelOrder() { if (!canTrade.value) return; clearTimeout(orderTimer); orderRevision++; await run(() => action('CANCEL_ORDER')); quantity.value = 0; allIn.value = false; targetExposure.value = Number(player.value.exposurePercent || 0) }
async function readyNext() { await run(() => action('READY_NEXT_ROUND')) }
function scheduleRoundIntro() {
  clearTimeout(roundIntroTimer)
  if (session.value?.phase !== 'ROUND_START' || session.value?.status !== 'ACTIVE') return
  const delay = Math.max(650, Math.round(1500 / Math.max(1, autoAdvanceSpeed.value)))
  roundIntroTimer = setTimeout(async () => {
    if (session.value?.phase !== 'ROUND_START' || session.value?.status !== 'ACTIVE') return
    if (submitting.value) return scheduleRoundIntro()
    await readyNext()
  }, delay)
}
async function ackEvent() { await run(() => action('ACK_EVENT')) }
async function confirmRestart() { if (session.value.room) { await run(() => action('REQUEST_RESTART')) } else { submitting.value = true; try { await abandonGameSession(sessionId.value); leaving = true; await router.replace(`/customer/entertainment/games/${session.value.gameId}`) } catch (e) { notifyError(e.message); submitting.value = false } } }
async function confirmCloseRoom() { submitting.value = true; try { await closeGameRoom(session.value.room.id); showCloseRoom.value = false; leaving = true; await router.replace(`/customer/entertainment/games/${session.value.gameId}`) } catch (e) { notifyError(e.message) } finally { submitting.value = false } }
async function pause() { await run(() => pauseGameSession(sessionId.value)) }
async function resume() { await run(() => resumeGameSession(sessionId.value)) }
async function sync() { if (syncing) return; syncing = true; try { const next = await syncGameSession(sessionId.value); if (next.status === 'ABANDONED') { leaving = true; await router.replace(`/customer/entertainment/games/${next.gameId}`); return } session.value = next } catch { } finally { syncing = false } }
async function load() { error.value = ''; try { await ensureEntertainmentGuest(); session.value = await getGameSession(sessionId.value); chartGroupSize.value = 1; targetExposure.value = Number(session.value.currentOrder?.targetExposurePercent ?? player.value.exposurePercent ?? 0); if (session.value.phase === 'ROUND_START') scheduleRoundIntro(); else if (['CREDIT_TRADING', 'CREDIT_REPAYMENT'].includes(session.value.phase)) openCredit(); if (session.value.status === 'COMPLETED') submitChallengeScore(); startLoops() } catch (e) { error.value = e.message } }
async function submitChallengeScore() {
  if (challengeSubmitted || session.value?.status !== 'COMPLETED') return
  if (!challengeId.value) return
  challengeSubmitted = true
  try { challengeResult.value = { ...(await submitDailyChallenge(challengeId.value, sessionId.value)), challengeId: challengeId.value } }
  catch (e) { challengeSubmitted = false; notifyError(e.message) }
}
watch(() => session.value?.status, status => { if (status === 'COMPLETED') submitChallengeScore() })
async function leaveToDetail() { leaving = true; if (session.value.status === 'ACTIVE' && !session.value.room) await pauseGameSession(sessionId.value).catch(() => {}); router.push(`/customer/entertainment/games/${session.value.gameId}`) }
function startLoops() { timer = window.setInterval(() => { now.value = Date.now() }, 200); poller = window.setInterval(sync, 900) }
function stopLoops() { clearInterval(timer); clearInterval(poller) }
async function toggleAutoAdvance() {
  const enabled = !autoAdvanceEnabled.value
  if (await updatePlaybackSettings(enabled, autoAdvanceSpeed.value)) {
    localStorage.setItem('entertainment_auto_advance', String(enabled))
  }
}
async function setAutoAdvanceSpeed(speed) {
  if (speed === autoAdvanceSpeed.value) return
  if (await updatePlaybackSettings(autoAdvanceEnabled.value, speed)) {
    localStorage.setItem('entertainment_auto_advance_speed', String(speed))
  }
}
function updatePlaybackSettings(enabled, speed) {
  return run(() => action('SET_PLAYBACK_SETTINGS', { autoAdvance: enabled, playbackSpeed: speed }))
}
function showStageNotice(phase, round) { const map = { CREDIT_TRADING: [`第 ${round} 回合 · 银行贷款`, '选择贷款或归还欠款，完成后进入开盘前交易'], OPENING_TRADING: [`第 ${round} 回合 · 开盘前`, '买入股数自动保存，阶段结束后公布开盘价'], CLOSING_TRADING: [`第 ${round} 回合 · 收盘前`, '卖出股数自动保存，阶段结束后公布收盘价'], CREDIT_REPAYMENT: [`第 ${round} 回合 · 银行还贷`, '查看贷款并按需还款，完成后进入下一回合'] }; if (!map[phase]) return; [stageNotice.value, stageNoticeDetail.value] = map[phase]; clearTimeout(noticeTimer); noticeTimer = setTimeout(() => { stageNotice.value = '' }, 1700) }
function price(value) { return Number(value || 0).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 4 }) }
function money(value) { return `¥${Number(value || 0).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}` }
function fixed(value, digits = 2) { return Number(value || 0).toFixed(digits) }
function signed(value) { const n = Number(value || 0); return `${n >= 0 ? '+' : ''}${n.toFixed(2)}` }
function signedMoney(value) { const n = Number(value || 0); return `${n >= 0 ? '+' : '-'}${money(Math.abs(n))}` }
function tradeDirection(value) { return value === 'BUY' ? '买入' : value === 'SELL' ? '卖出' : value }
function tone(value) { const n = Number(value || 0); return n > 0 ? 'positive' : n < 0 ? 'negative' : '' }
function availableFunds(item) { return Math.max(0, Number(item.cash || 0) - Number(item.reservedCash || 0)) }
function handleKeydown(event) {
  if (event.key !== 'Enter' || event.repeat || event.ctrlKey || event.metaKey || event.altKey || submitting.value) return
  if (showRules.value || showEvent.value || showRestart.value || showCloseRoom.value || showCredit.value) return
  if (!['OPENING_RESULT_ACK', 'ROUND_RESULT_ACK'].includes(session.value?.phase) || myReady.value) return
  const target = event.target
  if (target instanceof HTMLInputElement || target instanceof HTMLTextAreaElement || target instanceof HTMLSelectElement || target?.isContentEditable) return
  event.preventDefault()
  readyNext()
}

watch(() => session.value ? `${session.value.currentRound}-${session.value.phase}-${session.value.status}` : '', (next, old) => { if (old && next !== old) { showStageNotice(session.value.phase, session.value.currentRound); if (session.value.phase === 'ROUND_START') scheduleRoundIntro(); else { clearTimeout(roundIntroTimer); if (['CREDIT_TRADING', 'CREDIT_REPAYMENT'].includes(session.value.phase)) openCredit() } if ((old.includes('-CREDIT_TRADING-') || old.includes('-CREDIT_REPAYMENT-')) && !['CREDIT_TRADING', 'CREDIT_REPAYMENT'].includes(session.value.phase)) showCredit.value = false; quantity.value = session.value.currentOrder?.quantity || 0; allIn.value = Boolean(session.value.currentOrder?.allIn); targetExposure.value = Number(session.value.currentOrder?.targetExposurePercent ?? player.value.exposurePercent ?? 0); hovered.value = null } })
onMounted(() => { load(); window.addEventListener('keydown', handleKeydown) }); onBeforeUnmount(() => { stopLoops(); clearTimeout(noticeTimer); clearTimeout(roundIntroTimer); clearTimeout(orderTimer); window.removeEventListener('keydown', handleKeydown) }); onBeforeRouteLeave(async () => { if (!leaving && session.value?.status === 'ACTIVE' && !session.value.room) await pauseGameSession(sessionId.value).catch(() => {}); return true })
</script>

<style scoped>
.play-page{min-height:calc(100vh - 57px);padding:12px 18px 42px;background:#eef1ef;color:#26332c}.play-header,.error-bar,.market-strip,.play-grid,.final-result{width:min(1450px,100%);margin-left:auto;margin-right:auto}.play-header{height:54px;display:flex;align-items:center;gap:10px}.icon-btn{width:38px;height:38px;flex:0 0 38px;display:grid;place-items:center;border:1px solid #d8dedb;border-radius:6px;background:#fff;color:#526158}.symbol{min-width:190px;display:flex;align-items:baseline;gap:8px}.symbol span{color:#197348;font:800 12px ui-monospace,monospace}.symbol strong{max-width:230px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;font-size:14px}.round-meter{width:min(330px,28vw);margin-left:auto}.round-meter>span{display:block;margin-bottom:4px;color:#6d7872;text-align:right;font-size:10px}.round-meter i{height:5px;display:block;overflow:hidden;border-radius:3px;background:#d6ddda}.round-meter b{height:100%;display:block;background:#247b4e;transition:width .5s ease}.stage-pill{min-width:112px;height:38px;display:flex;align-items:center;justify-content:space-between;gap:9px;padding:0 10px;border:1px solid #d8dedb;border-radius:6px;background:#fff}.stage-pill span{font-size:10px;color:#68756e}.stage-pill strong{font:800 14px ui-monospace,monospace;color:#a35a1e}.stage-pill[data-stage=CLOSING] strong{color:#a3433d}.auto-controls{height:38px;display:flex;align-items:center;gap:5px}.auto-toggle{height:38px;display:flex;align-items:center;gap:5px;padding:0 9px;border:1px solid #d8dedb;border-radius:6px;color:#69766f;background:#fff;font-size:10px}.auto-toggle.active{color:#176e46;border-color:#77aa8e;background:#eaf5ef}.speed-selector{height:30px;display:flex;overflow:hidden;border:1px solid #d8dedb;border-radius:5px;background:#fff}.speed-selector button{min-width:31px;padding:0 5px;border-right:1px solid #e2e6e4;color:#717c76;background:#fff;font-size:9px}.speed-selector button:last-child{border-right:0}.speed-selector button.active{color:#fff;background:#3d6e56}.error-bar{display:flex;align-items:center;gap:7px;margin-bottom:9px;padding:8px 11px;color:#9e4139;background:#fff0ee;border-left:3px solid #ba4b43;font-size:11px}.error-bar button{margin-left:auto;color:inherit;background:transparent;font-weight:700}.stage-notice{position:fixed;z-index:650;top:74px;left:50%;transform:translateX(-50%);min-width:min(430px,calc(100vw - 20px));display:flex;align-items:center;gap:10px;padding:12px 16px;color:#fff;background:#293b32;border-radius:7px;box-shadow:0 12px 36px rgba(29,42,35,.25)}.stage-notice div{display:flex;flex-direction:column}.stage-notice strong{font-size:13px}.stage-notice span{margin-top:2px;color:#cdd8d2;font-size:10px}.stage-enter-active,.stage-leave-active{transition:.25s}.stage-enter-from,.stage-leave-to{opacity:0;transform:translate(-50%,-10px)}
.restart-save-command{width:100%;min-height:40px;display:flex;align-items:center;justify-content:center;gap:6px;margin-top:7px;border:1px solid #e1b4af;border-radius:6px;color:#a6443e;background:#fff;font-size:11px;font-weight:700}.loading{min-height:calc(100vh - 57px);display:grid;place-items:center}.load-failure{width:min(390px,calc(100% - 28px));display:flex;flex-direction:column;align-items:center;padding:24px;border:1px solid #ead5d2;border-radius:8px;background:#fff;text-align:center}.load-failure>svg{color:#ad443e}.load-failure h2{margin-top:9px;font-size:18px}.load-failure p{margin:7px 0 14px;color:#6d7772;font-size:11px;line-height:1.6}.load-failure .primary-command,.load-failure .text-command{width:100%}
.market-strip{display:grid;grid-template-columns:1.4fr repeat(4,1fr);margin-bottom:11px;border:1px solid #d9dfdc;border-radius:7px;overflow:hidden;background:#fff}.market-strip>div{min-height:70px;display:flex;flex-direction:column;justify-content:center;padding:10px 15px;border-right:1px solid #e4e8e6}.market-strip>div:last-child{border:0}.market-strip span{color:#77827c;font-size:9px}.market-strip strong{margin-top:4px;font-size:15px}.market-strip .main-price{display:grid;grid-template-columns:1fr auto}.main-price strong{grid-row:2;font-size:23px}.main-price small{grid-row:2;align-self:end;margin-bottom:3px;font-size:10px}.positive,.buy{color:#168052!important}.negative,.sell{color:#c04a44!important}
.credit-trigger{position:relative;z-index:1400}.credit-trigger[data-risk=WARNING]{color:#a26520;background:#fff6e8}.credit-trigger[data-risk=BLOCKED],.credit-trigger[data-risk=LIQUIDATION],.credit-trigger[data-risk=DEFAULTED]{color:#a33f39;background:#fff0ee}.credit-strip{min-height:42px;display:grid;grid-template-columns:repeat(4,minmax(0,1fr)) auto;align-items:center;gap:12px;margin:-3px 0 11px;padding:7px 12px;border:1px solid #d9dfdc;border-left:4px solid #4d755f;border-radius:6px;background:#fff;cursor:pointer}.credit-strip span{display:flex;flex-direction:column;color:#77817b;font-size:8px}.credit-strip strong{margin-top:2px;color:#303b35;font-size:10px}.credit-strip>b{padding:5px 7px;border-radius:4px;color:#37604c;background:#e8f1ec;font-size:9px}.credit-strip.risk-warning{border-left-color:#b3782c}.credit-strip.risk-warning>b{color:#86591f;background:#fff0d8}.credit-strip.risk-blocked,.credit-strip.risk-liquidation,.credit-strip.risk-defaulted{border-left-color:#b84943}.credit-strip.risk-blocked>b,.credit-strip.risk-liquidation>b,.credit-strip.risk-defaulted>b{color:#963c37;background:#fde9e7}
.play-grid{display:grid;grid-template-columns:minmax(0,1fr) 380px;gap:11px;align-items:start}.market-main{min-width:0}.chart-panel,.order-panel,.ranking,.trades,.final-result{border:1px solid #d9dfdc;border-radius:7px;background:#fff}.chart-panel{overflow:hidden}.chart-panel>header,.ranking>header,.trades>header,.order-panel>header{height:46px;display:flex;align-items:center;justify-content:space-between;padding:0 13px;border-bottom:1px solid #e5e9e7}.chart-panel>header>div,.order-panel>header>div{display:flex;align-items:center;gap:7px}.chart-panel h2,.order-panel h2,.market-details h2{font-size:13px}.chart-actions{display:flex;align-items:center;gap:7px}.chart-range{height:29px;display:flex;overflow:hidden;border:1px solid #dbe1de;border-radius:5px;background:#fff}.chart-range button{min-width:48px;padding:0 7px;border-right:1px solid #e2e6e4;color:#6d7872;background:#fff;font-size:9px}.chart-range button:last-child{border-right:0}.chart-range button.active{color:#fff;background:#426d58;font-weight:700}.event-button{height:29px;display:flex;align-items:center;gap:5px;padding:0 8px;border:1px solid #dbe1de;border-radius:5px;color:#6f7b74;background:#f8faf9;font-size:10px}.event-button.active{color:#87561f;border-color:#e7c89d;background:#fff6e9}.chart-wrap{position:relative;padding:4px 10px 0;overflow:hidden;touch-action:pan-y}.price-chart{width:100%;height:auto;aspect-ratio:820/300;display:block}.grid-line,.vertical-grid{stroke:#edf0ee;stroke-width:1;vector-effect:non-scaling-stroke}.vertical-grid{stroke:#f3f4f3}.axis-label{fill:#87908b;font-size:9px}.trade-marker{stroke-width:1;stroke-dasharray:4 4;vector-effect:non-scaling-stroke;opacity:.72}.trade-marker.buy{stroke:#168052}.trade-marker.sell{stroke:#c34843}.trade-marker.paired{stroke-dasharray:4 8}.trade-marker.sell.paired{stroke-dashoffset:6}.crosshair line{stroke:#6f7e76;stroke-dasharray:4 4;vector-effect:non-scaling-stroke}.chart-tooltip{position:absolute;z-index:8;min-width:175px;max-width:230px;display:flex;flex-direction:column;gap:3px;padding:9px 10px;border:1px solid #d9dfdc;border-radius:6px;background:rgba(255,255,255,.97);box-shadow:0 8px 23px rgba(35,47,40,.16);pointer-events:none;font-size:9px}.chart-tooltip strong{font-size:11px}.chart-tooltip span{color:#66736c}.chart-legend{height:34px;display:flex;align-items:center;justify-content:flex-end;gap:15px;padding:0 14px;color:#748079;font-size:9px}.chart-legend span{display:flex;align-items:center;gap:5px}.chart-legend i{display:block}.line-up,.line-down{width:14px;height:3px}.line-up{background:#168052}.line-down{background:#c34843}.buy-key,.sell-key{width:2px;height:14px;border-left:1px dashed}.buy-key{border-color:#168052}.sell-key{border-color:#c34843}
.market-details{display:grid;grid-template-columns:1.15fr .85fr;gap:11px;margin-top:11px}.ranking,.trades{overflow:hidden}.market-details header span{color:#7d8782;font-size:9px}.rank-row{min-height:44px;display:grid;grid-template-columns:24px minmax(78px,1fr) 88px 62px 92px;align-items:center;gap:5px;padding:0 10px;border-bottom:1px solid #edf0ee;font-size:9px}.rank-row:last-child{border:0}.rank-row.mine{background:#eff8f3}.rank-row>b{color:#7a8580}.rank-player{min-width:0;display:flex;flex-direction:column;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;font-size:10px}.rank-player small{margin-top:2px;color:#7a8580;font-size:8px}.rank-cash{color:#4f5c55;text-align:right}.rank-row>small{color:#78827d;text-align:right}.rank-row>strong{text-align:right}.trade-list{max-height:220px;overflow-y:auto;overscroll-behavior:contain;scrollbar-width:thin}.trade-list>div{height:39px;display:grid;grid-template-columns:34px 30px 1fr 80px;align-items:center;padding:0 12px;border-bottom:1px solid #edf0ee;font-size:10px}.trade-list strong{text-align:right}.trades>p{height:100px;display:grid;place-items:center;color:#8a938e;font-size:10px}
.order-panel{position:sticky;top:68px;overflow:hidden}.order-panel>header{height:55px}.order-panel.buy-mode>header{background:#eef8f3}.order-panel.sell-mode>header{background:#fff1f0}.order-panel>header>div>span{display:flex;flex-direction:column}.order-panel>header small{color:#738078;font-size:8px}.order-panel>header h2{margin-top:1px;font-size:15px}.order-panel.buy-mode>header svg,.order-panel.buy-mode>header h2{color:#126f45}.order-panel.sell-mode>header svg,.order-panel.sell-mode>header h2{color:#ad3d38}.order-panel>header>strong{padding:5px 7px;border-radius:4px;color:#69756e;background:#e3e8e5;font-size:9px}.order-panel.buy-mode>header>strong{color:#fff;background:#178052}.order-panel.sell-mode>header>strong{color:#fff;background:#c34843}.order-panel.locked>header>strong{color:#666f6a;background:#dce1de}.order-panel.buy-mode .stage-price{background:#f4faf7}.order-panel.sell-mode .stage-price{background:#fff7f6}.stage-price{display:grid;grid-template-columns:1fr auto;padding:13px 14px;background:#f7f9f8}.stage-price span{color:#75817a;font-size:9px}.stage-price strong{grid-row:2;font-size:20px}.stage-price small{grid-column:2;grid-row:2;align-self:end;color:#777f7b;font-size:9px}.order-account{display:grid;grid-template-columns:1fr 1fr;gap:1px;margin:11px 13px;background:#e1e6e3;border:1px solid #e1e6e3}.order-account span{display:flex;flex-direction:column;gap:3px;padding:8px 9px;background:#fff;color:#7b8580;font-size:9px}.order-account b{color:#2d3933;font-size:11px}.quantity-field{display:flex;flex-direction:column;gap:5px;margin:0 13px}.quantity-field span{color:#707b75;font-size:10px}.quantity-field input{height:43px;padding:0 10px;border:1px solid #d9dfdc;border-radius:6px;font-size:17px;font-weight:800}.buy-mode .quantity-field input:focus{border-color:#178052;box-shadow:0 0 0 2px rgba(23,128,82,.12)}.sell-mode .quantity-field input:focus{border-color:#c34843;box-shadow:0 0 0 2px rgba(195,72,67,.12)}.quantity-field input:disabled,.quantity-slider:disabled,.quick-row button:disabled{cursor:not-allowed;opacity:.52}.quantity-slider{width:calc(100% - 26px);margin:13px 13px 4px;accent-color:#168052}.quantity-slider.sell{accent-color:#c34843}.quick-row{display:grid;grid-template-columns:repeat(4,1fr);gap:5px;margin:5px 13px 11px}.quick-row button{height:28px;border:1px solid #dfe4e1;border-radius:4px;color:#68736d;background:#fff;font-size:9px}.order-preview{display:grid;grid-template-columns:1fr 1fr;margin:0 13px 11px;padding:9px;background:#f7f8f8}.order-preview span{display:flex;flex-direction:column;gap:2px;color:#76817b;font-size:9px}.order-preview strong{color:#2d3933;font-size:11px}.primary-command,.danger-command{min-height:41px;display:flex;align-items:center;justify-content:center;gap:6px;border-radius:6px;color:#fff;background:#19774b;font-weight:700;font-size:11px}.primary-command:disabled,.danger-command:disabled{opacity:.48;cursor:not-allowed}.cancel-order{width:calc(100% - 26px);height:31px;display:flex;align-items:center;justify-content:center;gap:5px;margin:5px 13px 0;color:#6d7772;background:transparent;font-size:9px}.cancel-order:disabled{opacity:.45;cursor:not-allowed}.pending-order{display:flex;align-items:center;gap:6px;margin:9px 13px 0;padding:8px;color:#805922;background:#fff3e2;font-size:9px}.ready-gate{margin-top:12px;padding:11px 13px 13px;border-top:1px solid #e2e7e4;background:#f6f9f7}.ready-gate>header{display:flex;align-items:center;gap:6px;font-size:10px}.ready-list{display:flex;flex-wrap:wrap;gap:5px;margin:8px 0}.ready-list span{display:flex;align-items:center;gap:4px;padding:4px 6px;border-radius:4px;color:#8b6653;background:#faece5;font-size:8px}.ready-list span.done{color:#236c49;background:#e2f0e8}.ready-gate .primary-command{width:100%}
.final-result{display:grid;grid-template-columns:repeat(3,1fr) 180px;gap:1px;margin-bottom:11px;overflow:hidden;background:#d9dfdc}.final-result>div{padding:13px;background:#fff}.final-result span{display:block;color:#77817c;font-size:9px}.final-result strong{display:block;margin-top:4px;font-size:17px}.final-result .primary-command{margin:10px}.overlay{position:fixed;inset:0;z-index:800;display:grid;place-items:center;padding:18px;background:rgba(30,40,35,.56);backdrop-filter:blur(3px)}.dialog{position:relative;width:min(440px,100%);display:flex;flex-direction:column;align-items:center;padding:25px;border-radius:8px;background:#fff;text-align:center}.dialog>svg{color:#9a6428}.dialog>span{margin-top:8px;color:#7b746b;font-size:10px}.dialog h2{margin-top:5px;font-size:20px}.dialog>p{margin:7px 0 14px;color:#68736d;font-size:11px;line-height:1.65}.dialog>small{margin-bottom:13px;color:#776b5d;font-size:9px;line-height:1.55}.dialog .primary-command,.dialog .danger-command{width:100%;margin-top:8px}.text-command{margin-top:8px;padding:7px;color:#6e7872;background:transparent;font-size:10px}.danger-command{background:#ac433d}.dialog-close{position:absolute;top:8px;right:8px;width:30px;height:30px;display:grid;place-items:center;color:#737c77;background:transparent}.ack-count{margin:4px 0 3px;padding:6px 9px;border-radius:4px;color:#70583f;background:#f6eee5;font-size:9px}.result-icon{width:46px;height:46px;display:grid;place-items:center;border-radius:50%;color:#19774b;background:#e4f2e9}.result-icon.close{color:#a74b3f;background:#f7e8e6}.result-dialog>h2{font-size:27px}.result-grid{width:100%;display:grid;grid-template-columns:1fr 1fr;border:1px solid #e0e5e2}.result-grid>div{padding:9px;border-right:1px solid #e6e9e7;border-bottom:1px solid #e6e9e7;text-align:left}.result-grid>div:nth-child(2n){border-right:0}.result-grid>div:nth-last-child(-n+2){border-bottom:0}.result-grid span{display:block;color:#7c8580;font-size:8px}.result-grid strong{display:block;margin-top:2px;font-size:11px}.rules-dialog{align-items:stretch;text-align:left;max-height:min(720px,90vh);overflow:auto}.rules-dialog>svg{align-self:center}.rules-dialog>h2{text-align:center}.rules-dialog section{margin-top:15px}.rules-dialog h3{font-size:12px}.rules-dialog section p{margin-top:4px;color:#606c65;font-size:10px;line-height:1.7}.rule-values{display:flex;flex-wrap:wrap;gap:5px;margin-top:16px}.rule-values span{padding:5px 7px;border-radius:4px;color:#496056;background:#edf3f0;font-size:9px}.loading{min-height:70vh;display:grid;place-items:center;align-content:center}.loading p{color:#a7443d}.desktop-only{display:grid}
.challenge-result{width:min(1450px,100%);min-height:42px;display:flex;align-items:center;gap:8px;margin:0 auto 11px;padding:8px 12px;border:1px solid #cbded2;border-radius:7px;background:#f4faf6}.challenge-result strong{margin-right:auto;font-size:11px}.challenge-result span{padding:4px 7px;border-radius:4px;color:#35644c;background:#e1f0e7;font-size:9px}
.credit-overlay{z-index:1450!important;display:grid!important}.credit-dialog{width:min(620px,100%);max-height:92vh;overflow:auto;align-items:stretch;text-align:left}.credit-dialog>svg{align-self:center}.credit-dialog>span,.credit-dialog>h2{text-align:center}.credit-metrics{display:grid;grid-template-columns:repeat(3,1fr);margin:14px 0 10px;border:1px solid #e0e5e2}.credit-metrics>div{padding:8px 9px;border-right:1px solid #e7ebe8;border-bottom:1px solid #e7ebe8}.credit-metrics>div:nth-child(3n){border-right:0}.credit-metrics>div:nth-last-child(-n+3){border-bottom:0}.credit-metrics span{display:block;color:#78827d;font-size:8px}.credit-metrics strong{display:block;margin-top:2px;font-size:11px}.ltv-track{position:relative;height:22px;overflow:hidden;border-radius:4px;background:#e8ecea}.ltv-track i{position:absolute;inset:0 auto 0 0;background:#bd7a2c;transition:width .3s}.ltv-track span{position:relative;z-index:1;display:grid;place-items:center;height:100%;color:#26342d;font-size:9px;font-weight:700}.bank-options{display:flex;gap:6px;margin:10px 0 4px;padding:2px 1px 6px;overflow-x:auto}.bank-options button{min-width:175px;flex:1 0 175px;display:flex;flex-direction:column;align-items:flex-start;padding:8px 10px;border:1px solid #dce2df;border-radius:5px;background:#fff;text-align:left}.bank-options button.active{border-color:#4f896c;background:#edf7f1;box-shadow:inset 0 -2px #4f896c}.bank-options strong{font-size:10px}.bank-options small{margin-top:2px;color:#758079;font-size:8px;white-space:nowrap}.credit-amount{display:flex;flex-direction:column;gap:4px;margin-top:10px}.credit-amount span{color:#69756e;font-size:9px}.credit-amount input{height:39px;padding:0 10px;border:1px solid #d8dfdb;border-radius:5px;font-size:15px;font-weight:700}.credit-quick{display:grid;grid-template-columns:repeat(3,1fr);gap:5px;margin-top:5px}.credit-quick button{height:28px;border:1px solid #dce2df;border-radius:4px;background:#fff;font-size:9px}.credit-actions{display:grid;grid-template-columns:1fr 1fr;gap:7px}.credit-ledger{margin-top:4px;border-top:1px solid #e1e5e3}.credit-ledger>div{display:flex;justify-content:space-between;padding:6px 2px;border-bottom:1px solid #edf0ee;font-size:9px}.credit-stage-footer{margin-top:10px;padding-top:10px;border-top:1px solid #dfe5e2}.credit-stage-footer>span{display:block;color:#68746d;font-size:9px;text-align:center}.credit-stage-footer .primary-command{width:100%;margin-top:7px}.credit-readiness{display:flex;gap:7px;margin-bottom:10px;padding-bottom:9px;overflow-x:auto;border-bottom:1px solid #edf0ee}.credit-readiness>span{min-width:68px;display:grid;grid-template-columns:26px 1fr;grid-template-rows:auto auto;align-items:center;gap:0 6px;padding:6px;border:1px solid #ead8cf;border-radius:6px;background:#fff8f4}.credit-readiness i{grid-row:1/3;width:26px;height:26px;display:grid;place-items:center;border-radius:50%;color:#86503a;background:#f3ddd2;font-size:10px;font-style:normal;font-weight:800}.credit-readiness b{max-width:70px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;font-size:9px}.credit-readiness small{color:#a1624a;font-size:8px}.credit-readiness>span.done{border-color:#c9dfd2;background:#f2f8f5}.credit-readiness>span.done i{color:#276b49;background:#dbeee3}.credit-readiness>span.done small{color:#27704b}
.bank-select{display:grid;grid-template-columns:96px minmax(0,1fr);align-items:center;gap:5px 10px;margin:12px 0 4px}.bank-select>span{color:#69756e;font-size:9px}.bank-select>small{grid-column:2;color:#758079;font-size:8px}
@keyframes draw-line{to{stroke-dashoffset:0}}@keyframes area-in{to{opacity:1}}
@media(max-width:1000px){.play-grid{grid-template-columns:1fr}.order-panel{position:static}.market-details{display:none}.round-meter{width:220px}.desktop-only{display:none}}
@media(max-width:768px){.play-page{padding:7px 8px 84px}.play-header{height:auto;min-height:44px;gap:6px;flex-wrap:wrap}.icon-btn{width:34px;height:34px;flex-basis:34px}.symbol{min-width:0;flex:1}.symbol strong{max-width:150px}.auto-controls{order:6;width:100%;justify-content:space-between}.round-meter{order:7;width:100%;margin:0}.round-meter>span{margin-bottom:3px;text-align:left}.stage-pill{min-width:100px;height:34px}.market-strip{grid-template-columns:1.25fr repeat(2,1fr);margin-top:7px}.market-strip>div{min-height:58px;padding:7px 9px}.market-strip>div:nth-child(3){border-right:0}.market-strip>div:nth-child(n+4){border-top:1px solid #e4e8e6}.market-strip>div:nth-child(4){grid-column:1/2}.market-strip>div:nth-child(5){grid-column:2/4;border-right:0}.market-strip .main-price strong{font-size:20px}.play-grid{gap:8px}.chart-panel>header{height:40px;padding:0 8px}.chart-actions{gap:4px}.chart-range button{min-width:39px;padding:0 4px}.event-button{padding:0 6px}.chart-wrap{padding:2px 4px 0;overflow-x:auto;overscroll-behavior-x:contain}.price-chart{width:760px;max-width:none;min-height:0;aspect-ratio:820/300}.chart-legend{height:31px;justify-content:flex-start;gap:11px;overflow-x:auto;padding:0 9px}.order-panel>header{height:52px}.order-panel>header h2{font-size:14px}.stage-price{padding:9px 11px}.stage-price strong{font-size:18px}.order-account{margin:8px 10px}.quantity-field{margin:0 10px}.quantity-field input{height:39px}.quantity-slider{width:calc(100% - 20px);margin:10px 10px 2px}.quick-row{margin:4px 10px 8px}.order-preview{margin:0 10px 8px}.pending-order{margin:7px 10px 0}.ready-gate{padding:9px 10px 10px}.final-result{grid-template-columns:repeat(2,1fr)}.final-result .primary-command{grid-column:1/-1;margin:8px}.stage-notice{top:9px}.chart-tooltip{min-width:155px}.dialog{padding:21px 18px}.result-dialog>h2{font-size:24px}}
.initial-line{stroke:#9aa59f;stroke-width:1;stroke-dasharray:4 4;vector-effect:non-scaling-stroke}.initial-label,.price-label{fill:#68746d;font-size:8px}.round-candle{stroke-width:1;vector-effect:non-scaling-stroke;transform-box:fill-box;transform-origin:center;animation:candle-grow .62s cubic-bezier(.2,.75,.25,1);transition:filter .2s ease,transform .2s ease}.round-bar.up .round-candle{fill:rgba(26,134,87,.76);stroke:#147349}.round-bar.down .round-candle{fill:rgba(204,62,56,.78);stroke:#b93732}.round-bar.flat .round-candle{fill:#85908a;stroke:#6c7771}.round-bar.hovered .round-candle{filter:drop-shadow(0 4px 5px rgba(26,39,32,.24));transform:scaleX(1.3)}.line-up{background:#198657!important}.line-down{background:#c93f39!important}.overlay~.overlay{display:none}.modal-enter-active,.modal-leave-active,.phase-modal-enter-active,.phase-modal-leave-active{transition:opacity .24s ease}.modal-enter-active .dialog,.modal-leave-active .dialog,.modal-enter-active.room-close-confirm>div,.modal-leave-active.room-close-confirm>div{transition:transform .28s cubic-bezier(.2,.75,.25,1),opacity .22s}.modal-enter-from,.modal-leave-to,.phase-modal-enter-from,.phase-modal-leave-to{opacity:0}.modal-enter-from .dialog,.modal-enter-from.room-close-confirm>div{opacity:0;transform:translateY(18px) scale(.98)}.modal-leave-to .dialog,.modal-leave-to.room-close-confirm>div{opacity:0;transform:translateY(10px) scale(.985)}.phase-modal-enter-active .dialog,.phase-modal-leave-active .dialog{transition:transform .34s cubic-bezier(.2,.8,.25,1),opacity .24s}.phase-modal-enter-from .dialog{opacity:0;transform:translateY(10px) scaleX(.92)}.phase-modal-leave-to .dialog{opacity:0;transform:translateY(-6px) scaleX(.96)}.round-curtain{position:fixed;inset:0;z-index:900;display:grid;place-items:center;background:rgba(20,31,25,.45);pointer-events:none}.round-curtain>div{width:100%;display:flex;flex-direction:column;align-items:center;padding:32px 18px;color:#fff;background:#22382d;border-top:1px solid rgba(255,255,255,.22);border-bottom:1px solid rgba(255,255,255,.22);box-shadow:0 22px 60px rgba(13,24,18,.35)}.round-curtain span{font-size:9px;letter-spacing:3px;color:#a9c7b6}.round-curtain strong{margin-top:5px;font-size:36px}.round-curtain small{margin-top:5px;color:#d3e2da;font-size:11px}.curtain-enter-active,.curtain-leave-active{transition:opacity .32s}.curtain-enter-from,.curtain-leave-to{opacity:0}.curtain-enter-from>div{transform:scaleX(.2)}.curtain-enter-active>div{transition:transform .45s cubic-bezier(.2,.8,.25,1)}
@keyframes candle-grow{from{opacity:.12;filter:blur(1px)}to{opacity:1;filter:blur(0)}}@keyframes overlay-in{from{opacity:0}}@keyframes dialog-rise{from{opacity:0;transform:translateY(22px) scale(.97)}}
@media(max-width:768px){.price-label{font-size:7px}.round-curtain strong{font-size:30px}}
.chart-tooltip{transform:translateX(14px);transition:left .12s ease,top .12s ease}.chart-tooltip.left{transform:translateX(calc(-100% - 14px))}.price-axis{font-size:7px;fill:#78847d}.trade-list>div{grid-template-columns:58px minmax(62px,1fr) 25px 48px 72px!important;gap:5px}.trader-name{overflow:hidden;text-overflow:ellipsis;white-space:nowrap;color:#4f5d55}.trade-list>div>strong{text-align:right}
.price-chart{background:#fbfcfb}.grid-line{stroke:#e2e8e4}.vertical-grid{stroke:#eef2ef}.kline-connector{fill:none;stroke-width:1.6;stroke-linecap:butt;vector-effect:non-scaling-stroke;animation:connector-in .28s ease-out both}.kline-connector.up{stroke:#198657}.kline-connector.down{stroke:#c93f39}.kline-connector.flat{stroke:#738078}.round-bar.up .round-candle{fill:#21875d;stroke:#146f49}.round-bar.down .round-candle{fill:#d74b45;stroke:#b93732}.round-bar.flat .round-candle{fill:#7e8983;stroke:#66716b}.price-axis{font-size:8px}.axis-label{fill:#75817a}@keyframes connector-in{from{opacity:0}to{opacity:1}}
.event-result{width:100%;display:flex;flex-direction:column;gap:5px;margin-top:12px;padding:13px 14px;border-left:4px solid #bf7a2e;background:#fff5e7;text-align:left}.event-result span{color:#8b6d46;font-size:9px}.event-result strong{color:#493b2c;font-size:13px;line-height:1.55}.event-result small{color:#716353;font-size:10px;line-height:1.55}.event-result.empty{border-left-color:#89938e;background:#f2f5f3}.event-result.empty span,.event-result.empty strong{color:#65716a}.event-tags{display:flex;flex-wrap:wrap;justify-content:center;gap:5px;margin-top:10px}.event-tags span{padding:4px 7px;border-radius:4px;color:#496457;background:#edf3ef;font-size:9px}
.event-pending{width:100%;display:flex;align-items:center;justify-content:center;gap:7px;margin-top:12px;padding:12px;color:#776b5d;background:#f3f5f4;font-size:10px}
.quick-row{grid-template-columns:repeat(5,1fr)}.quick-row button.active{color:#126a42;border-color:#65a986;background:#eaf5ef}
.final-result{grid-template-columns:repeat(4,1fr) 180px}
.room-close-trigger{position:relative;z-index:1400;color:#a23f39;border-color:#e1aaa5;background:#fff}.room-close-confirm{position:fixed;z-index:1500;inset:0;display:grid;place-items:center;padding:18px;background:rgba(27,36,31,.62)}.room-close-confirm>div{width:min(390px,100%);display:flex;flex-direction:column;align-items:center;padding:24px;border-radius:8px;background:#fff;text-align:center;box-shadow:0 22px 65px rgba(18,28,22,.35)}.room-close-confirm svg{color:#a23f39}.room-close-confirm h2{margin-top:9px;font-size:19px}.room-close-confirm p{margin:8px 0 12px;color:#65716a;font-size:11px;line-height:1.65}.room-close-confirm .danger-command{width:100%;margin-top:6px}
.forced-marker{stroke:#9b3e37;stroke-width:2;stroke-dasharray:2 3;vector-effect:non-scaling-stroke}.forced-label{fill:#9b3e37;font-size:7px;font-weight:800}.forced-key{width:2px;height:14px;border-left:2px dashed #9b3e37}.ltv-track>b{position:absolute;z-index:1;top:0;width:1px;height:100%;background:#743a36;opacity:.72}.ltv-track span{z-index:2}.credit-kind-tabs{display:grid;grid-template-columns:repeat(3,1fr);gap:4px;margin-top:10px;padding:3px;border-radius:5px;background:#eef2f0}.credit-kind-tabs button{height:30px;border-radius:4px;color:#68746d;background:transparent;font-size:10px}.credit-kind-tabs button.active{color:#285f45;background:#fff;box-shadow:0 1px 4px rgba(30,47,38,.12);font-weight:700}.credit-kind-tabs button:disabled{opacity:.45}.credit-actions{grid-template-columns:1fr}.loan-list{display:grid;gap:7px;margin-top:10px}.loan-list article{padding:10px;border:1px solid #dce2df;border-radius:6px;background:#fafcfb}.loan-list header{display:flex;align-items:center;justify-content:space-between}.loan-list header strong{font-size:11px}.loan-list header span,.loan-list small{color:#6e7a73;font-size:8px}.loan-list article>div:not(.loan-extension){display:grid;grid-template-columns:repeat(2,1fr);gap:4px;margin:7px 0;font-size:9px}.loan-extension{display:grid;grid-template-columns:1fr auto;gap:6px;margin-top:8px}.loan-extension .custom-select-trigger{height:34px}.no-loans{padding:18px;color:#77817c;text-align:center;font-size:10px}.credit-ledger{max-height:180px;overflow:auto}.final-credit-summary{display:grid;grid-template-columns:repeat(4,1fr);gap:1px;margin:-5px 0 11px;overflow:hidden;border:1px solid #d9dfdc;border-radius:7px;background:#d9dfdc}.final-credit-summary>div{padding:10px 13px;background:#fff}.final-credit-summary span{display:block;color:#77817c;font-size:9px}.final-credit-summary strong{display:block;margin-top:3px;font-size:12px}
.loan-list article.closed{border-color:#cbded3;background:#f3f8f5}.loan-settlement span{display:flex;flex-direction:column;gap:2px;color:#6e7973}.loan-settlement strong{color:#245f43;font-size:11px}
@media(max-width:768px){.credit-strip{grid-template-columns:repeat(2,minmax(0,1fr));gap:7px}.credit-strip>b{grid-column:1/-1;text-align:center}.final-credit-summary{grid-template-columns:repeat(2,1fr)}}
.final-credit-summary,.ai-review{width:min(1450px,100%);margin-left:auto;margin-right:auto}.order-panel.auction-mode>header{background:#edf5f7}.order-panel.auction-mode>header svg,.order-panel.auction-mode>header h2{color:#246579}.order-panel.auction-mode>header>strong{color:#fff;background:#2d7183}.order-panel.auction-mode .stage-price{background:#f2f7f8}.target-quick{grid-template-columns:repeat(5,1fr)}.auction-submit{width:calc(100% - 26px);margin:0 13px}.auction-mode .quick-row button.active{color:#fff;border-color:#2d7183;background:#2d7183}.challenge-result a{color:#286248;font-size:9px;font-weight:800}.ai-review{margin-bottom:11px;border:1px solid #d9dfdc;border-radius:7px;background:#fff}.ai-review>header{min-height:46px;display:flex;align-items:center;justify-content:space-between;padding:9px 13px;border-bottom:1px solid #e5e9e7}.ai-review>header>div{display:flex;align-items:center;gap:7px}.ai-review h2{font-size:13px}.ai-review>header span{color:#77817c;font-size:9px}.ai-review-table article{display:grid;grid-template-columns:145px minmax(190px,1fr) minmax(190px,1fr) minmax(260px,1.25fr);gap:12px;align-items:center;min-height:72px;padding:10px 13px;border-bottom:1px solid #edf0ee}.ai-review-table article:last-child{border-bottom:0}.ai-review-table article>div{min-width:0;display:flex;flex-direction:column;gap:3px}.ai-review-table span,.ai-review-table small{color:#758079;font-size:8px}.ai-review-table strong{overflow:hidden;text-overflow:ellipsis;font-size:10px;white-space:nowrap}.ai-review-name strong{font-size:12px}.ai-review-stats{display:grid!important;grid-template-columns:repeat(3,1fr);gap:5px!important}.ai-review-stats span{padding:5px;background:#f4f6f5;color:#46554d;text-align:center}@media(max-width:1000px){.ai-review-table article{grid-template-columns:120px 1fr 1fr}.ai-review-stats{grid-column:1/-1}}@media(max-width:768px){.challenge-result{align-items:flex-start;flex-wrap:wrap}.challenge-result strong{width:100%}.ai-review>header{align-items:flex-start;flex-direction:column}.ai-review-table article{grid-template-columns:1fr}.ai-review-stats{grid-column:auto}.ai-review-table strong{white-space:normal}}
</style>
