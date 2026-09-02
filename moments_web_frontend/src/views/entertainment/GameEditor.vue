<template>
  <div class="editor-page">
    <header class="editor-header">
      <button class="icon-command" title="返回" @click="$router.back()"><ArrowLeft :size="20" /></button>
      <div class="editor-heading"><span>{{ gameId ? '游戏配置' : '新建游戏' }}</span><h1>{{ gameId ? '编辑股票游戏' : '创建股票游戏' }}</h1><small v-if="savedStatus">{{ savedStatus }}</small></div>
      <div class="header-actions">
        <button class="btn btn-outline" :disabled="saving" @click="save(false)"><Save :size="17" />保存草稿</button>
        <button class="btn btn-primary" :disabled="saving" @click="save(true)"><Send :size="17" />{{ gameId ? '保存并发布新版' : '发布' }}</button>
      </div>
    </header>

    <div v-if="loading" class="editor-loading"><LoadingSpinner /></div>
    <section v-else-if="error" class="editor-load-failure">
      <TriangleAlert :size="32" />
      <h2>暂时无法读取游戏配置</h2>
      <p>{{ error }}</p>
      <div><button type="button" class="btn btn-primary" @click="load">重新读取</button><button type="button" class="btn btn-ghost" @click="$router.back()">返回</button></div>
    </section>
    <form v-else class="editor-grid" @submit.prevent>
      <main class="editor-main">
        <nav class="editor-tabs">
          <button v-for="tab in editorTabs" :key="tab.value" type="button" :class="{ active: activeTab === tab.value }" @click="activeTab = tab.value">{{ tab.label }}</button>
        </nav>
        <section v-show="activeTab === 'BASIC'" class="form-section identity-section">
          <div class="section-heading"><span>01</span><div><h2>股票信息</h2><p>定义玩家在列表和游戏详情中首先看到的内容。</p></div></div>
          <div class="fields two-cols">
            <label class="form-group"><span class="form-label">股票名称</span><input v-model.trim="form.title" class="form-input" maxlength="50" required /></label>
            <label class="form-group"><span class="form-label">股票代码</span><input v-model.trim="form.config.stockCode" class="form-input code-input" maxlength="10" @input="uppercaseCode" /></label>
            <label class="form-group"><span class="form-label">股票类型</span>
              <input v-model.trim="form.config.stockType" class="form-input" list="stock-types" maxlength="30" />
              <datalist id="stock-types"><option value="贵金属"/><option value="工业"/><option value="航天"/><option value="新能源"/><option value="消费"/><option value="科技"/></datalist>
            </label>
            <label class="form-group"><span class="form-label">可见范围</span><CustomSelect v-model="form.visibility" :options="visibilityOptions" /></label>
            <label class="form-group full"><span class="form-label">游戏介绍</span><textarea v-model.trim="form.description" class="form-textarea" maxlength="500" /></label>
          </div>
        </section>

        <section v-show="activeTab === 'BASIC'" class="form-section market-rules-section">
          <div class="section-heading"><span>02</span><div><h2>市场规则</h2><p>价格、节奏和参与方式共同决定一局游戏的操作密度。</p></div></div>

          <div class="setting-group">
            <div class="setting-group-title"><strong>资金与价格</strong><span>决定市场的起始规模和波动空间</span></div>
            <div class="fields four-cols">
              <NumberField v-model="form.config.initialPrice" label="初始价格" :min="0.01" :step="0.01" />
              <NumberField v-model="form.config.initialCash" label="玩家初始金额" :min="100" :step="100" />
              <NumberField v-model="form.config.baseVolatilityPercent" label="基础波动率 %" :min="0" :max="20" :step="0.1" />
              <NumberField v-model="form.config.maxOrderImpactPercent" label="订单影响上限 %" :min="0" :max="20" :step="0.1" />
              <NumberField v-model="form.config.maxBuyQuantity" label="单次最大买入股数" :min="1" :max="10000000" :step="1" />
              <NumberField v-model="form.config.marketLiquidityShares" label="市场流动性（股/参与者）" :min="100" :max="10000000" :step="100" />
              <label class="form-group"><span class="form-label">市场流动性</span><CustomSelect v-model="form.config.liquidity" :options="liquidityOptions" /></label>
            </div>
          </div>

          <div class="setting-group">
            <div class="setting-group-title"><strong>回合与引擎</strong><span>配置推进节奏、定价模型和成交方式</span></div>
            <div class="fields four-cols">
              <NumberField v-model="form.config.roundCount" label="回合数" :min="3" :max="200" :step="1" />
              <NumberField v-model="form.config.tradingSeconds" :label="form.config.progressionMode === 'MANUAL' ? '自动模式交易秒数' : '交易秒数'" :min="3" :max="300" :step="1" />
              <label class="form-group"><span class="form-label">回合推进</span><CustomSelect v-model="form.config.progressionMode" :options="progressionOptions" /></label>
              <label class="form-group"><span class="form-label">场景预设</span><CustomSelect v-model="form.config.scenarioPreset" :options="scenarioOptions" @change="applyScenarioPreset" /></label>
              <label class="form-group"><span class="form-label">价格引擎</span><CustomSelect v-model="form.config.pricingEngineVersion" :options="pricingOptions" /></label>
              <label class="form-group"><span class="form-label">交易撮合</span><CustomSelect v-model="form.config.tradingMode" :options="tradingModeOptions" /></label>
              <NumberField v-if="form.config.tradingMode === 'TARGET_AUCTION'" v-model="form.config.feePercent" label="成交手续费 %" :min="0" :max="5" :step="0.01" />
              <NumberField v-if="form.config.tradingMode === 'TARGET_AUCTION'" v-model="form.config.slippagePercent" label="竞价滑点 %" :min="0" :max="5" :step="0.01" />
            </div>
          </div>

          <div class="setting-group participation-group">
            <div class="setting-group-title"><strong>参与方式</strong><span>开放多人房间或每日固定种子挑战</span></div>
            <div class="fields four-cols">
              <label class="form-group switch-field"><span class="form-label">多人模式</span><label class="switch"><input v-model="form.config.multiplayerEnabled" type="checkbox" /><span></span></label></label>
              <NumberField v-if="form.config.multiplayerEnabled" v-model="form.config.maxHumanPlayers" label="最多真人玩家" :min="2" :max="8" :step="1" />
              <label class="form-group switch-field"><span class="form-label">每日固定种子挑战</span><label class="switch"><input v-model="form.config.dailyChallengeEnabled" type="checkbox" /><span></span></label></label>
            </div>
          </div>

          <div v-if="form.config.pricingEngineVersion === 'V3'" class="setting-group fundamentals-group">
            <div class="setting-group-title"><strong>公司基本面与财报</strong><span>V3 专属，财报预期差会参与价格计算</span></div>
            <div class="fields four-cols">
              <NumberField v-model="form.config.reportIntervalRounds" label="财报间隔回合" :min="2" :max="50" :step="1" />
              <NumberField v-model="form.config.fundamentalSensitivity" label="基本面价格敏感度" :min="0" :max="2" :step="0.05" />
              <NumberField v-model="form.config.researchPoints" label="每局研究点" :min="0" :max="20" :step="1" />
              <NumberField v-model="form.config.initialFundamentals.demandIndex" label="初始需求指数" :min="0" :max="200" :step="1" />
              <NumberField v-model="form.config.initialFundamentals.orderIndex" label="初始订单指数" :min="0" :max="200" :step="1" />
              <NumberField v-model="form.config.initialFundamentals.inventoryIndex" label="初始库存指数" :min="0" :max="200" :step="1" />
              <NumberField v-model="form.config.initialFundamentals.marginIndex" label="初始利润率指数" :min="0" :max="200" :step="1" />
              <NumberField v-model="form.config.initialFundamentals.cashFlowIndex" label="初始现金流指数" :min="0" :max="200" :step="1" />
            </div>
          </div>
        </section>

        <section v-show="activeTab === 'AI'" class="form-section">
          <div class="section-heading"><span>03</span><h2>AI 对手</h2><label class="switch"><input v-model="form.config.aiEnabled" type="checkbox" @change="toggleAi" /><span></span></label></div>
          <div v-if="form.config.aiEnabled" class="ai-config">
            <div class="ai-toolbar">
            <label class="form-group"><span class="form-label">AI 难度</span><CustomSelect v-model="form.config.aiDifficulty" :options="difficultyOptions" /></label>
              <button type="button" class="add-ai" @click="addAi"><Plus :size="16" />添加 AI</button>
            </div>
            <div class="ai-player-list">
              <article v-for="entry in pagedAiPlayers" :key="entry.ai.localId" class="ai-player-card">
                <header><div><span>AI {{ entry.index + 1 }}</span><strong>{{ entry.ai.name || '未命名对手' }}</strong></div><button type="button" title="删除 AI" :disabled="form.config.aiPlayers.length === 1" @click="removeAi(entry.index)"><Trash2 :size="17" /></button></header>
                <div class="fields ai-fields">
                  <label class="form-group"><span class="form-label">显示名称</span><input v-model.trim="entry.ai.name" class="form-input" maxlength="30" /></label>
                  <label class="form-group"><span class="form-label">交易风格</span><CustomSelect v-model="entry.ai.style" :options="aiStyleSelectOptions" @change="applyAiStyle(entry.ai)" /><small class="style-summary">{{ aiStyle(entry.ai.style).description }}</small></label>
                  <NumberField v-model="entry.ai.initialCash" label="初始资金" :min="100" :max="10000000" :step="100" />
                  <NumberField v-model="entry.ai.maxOrderQuantity" label="单次下单上限" :min="1" :max="10000000" :step="1" />
                  <NumberField v-model="entry.ai.activityPercent" label="交易活跃倾向 %" :min="0" :max="100" :step="1" />
                  <NumberField v-model="entry.ai.riskPercent" label="最大股票敞口 %" :min="0" :max="300" :step="5" />
                  <NumberField v-model="entry.ai.rebalanceBandPctPoints" label="调仓缓冲（百分点）" :min="0.5" :max="20" :step="0.5" />
                  <NumberField v-model="entry.ai.minHoldRounds" label="最短持有回合" :min="0" :max="20" :step="1" />
                  <NumberField v-model="entry.ai.minTradeAssetPct" label="最小交易资产占比 %" :min="0" :max="20" :step="0.5" />
                  <NumberField v-model="entry.ai.maxRoundTurnoverPct" label="单回合换手上限 %" :min="1" :max="100" :step="1" />
                  <NumberField v-model="entry.ai.maxEstimatedImpactPct" label="最大预估价格冲击 %" :min="0.1" :max="20" :step="0.1" />
                  <NumberField v-model="entry.ai.maxPositionStepPctPoints" label="普通敞口步长（百分点）" :min="1" :max="100" :step="1" />
                  <NumberField v-model="entry.ai.highUrgencyPositionStepPctPoints" label="强信号敞口步长（百分点）" :min="1" :max="100" :step="1" />
                  <NumberField v-model="entry.ai.reverseCooldownRounds" label="反向交易冷却回合" :min="0" :max="20" :step="1" />
                  <label class="form-group strategy-field"><span class="form-label">指定策略</span><textarea v-model.trim="entry.ai.strategyPrompt" class="form-textarea" maxlength="300" placeholder="例如：趋势连续两轮转弱时降低目标股票敞口" /></label>
                </div>
              </article>
            </div>
            <PaginationBar v-model="aiPage" :total-pages="aiPages" :total-items="form.config.aiPlayers.length" />
            <p class="ai-note"><Info :size="16" />AI 维护投资观点并决定目标股票敞口；100% 以上由后端自动选择授信与借款金额，模型不能绕过 LTV、换手和价格冲击边界。</p>
          </div>
          <div v-else class="disabled-row"><BotOff :size="18" />本游戏不加入 AI 对手</div>
        </section>

        <section v-show="activeTab === 'BANK'" class="form-section">
          <div class="section-heading"><span>04</span><h2>银行融资</h2><label class="switch"><input v-model="form.config.loanEnabled" type="checkbox" /><span></span></label></div>
          <template v-if="form.config.loanEnabled">
            <div class="phase-time-settings">
              <div class="setting-group-title"><strong>阶段与计时</strong><span>贷款交互方式决定融资是否占用独立阶段</span></div>
              <div class="fields four-cols">
                <label class="form-group"><span class="form-label">贷款交互</span><CustomSelect v-model="form.config.creditInteractionMode" :options="creditInteractionOptions" /></label>
                <div v-if="form.config.creditInteractionMode === 'INLINE'" class="inline-credit-time">
                  <Clock3 :size="18" />
                  <span><strong>与开盘交易共用倒计时</strong><small>{{ form.config.progressionMode === 'MANUAL' ? '当前为手动推进，不限制融资时间' : `融资窗口随开盘交易阶段，共 ${form.config.tradingSeconds} 秒` }}</small></span>
                </div>
                <template v-else>
                  <label class="form-group switch-field"><span class="form-label">贷款阶段倒计时</span><label class="switch"><input v-model="form.config.creditPhaseTimed" type="checkbox" /><span></span></label><small>关闭时由全部真人玩家点击完成后推进</small></label>
                  <NumberField v-if="form.config.creditPhaseTimed" v-model="form.config.creditPhaseSeconds" label="贷款阶段秒数" :min="3" :max="600" :step="1" />
                </template>
                <label class="form-group switch-field"><span class="form-label">还贷阶段倒计时</span><label class="switch"><input v-model="form.config.repaymentPhaseTimed" type="checkbox" /><span></span></label><small>关闭时由全部真人玩家点击完成后推进</small></label>
                <NumberField v-if="form.config.repaymentPhaseTimed" v-model="form.config.repaymentPhaseSeconds" label="还贷阶段秒数" :min="3" :max="600" :step="1" />
              </div>
            </div>
          <div class="credit-products">
            <article v-for="product in form.config.creditProducts" :key="product.code" :class="['credit-product', `risk-${product.riskLevel.toLowerCase()}`]">
              <header><div><span>{{ product.riskLevel }}</span><strong>{{ product.name }}</strong></div><label class="switch"><input v-model="product.enabled" type="checkbox" /><span></span></label></header>
              <p v-if="product.riskLevel === 'HIGH' || product.riskLevel === 'EXTREME'">高收益空间伴随更短期限、更高利息和更近的强平线。</p>
              <div class="fields credit-fields">
                <NumberField v-model="product.maxInitialCapitalRatio" label="初始资金额度 %" :min="0" :max="200" :step="5" />
                <NumberField v-model="product.maxDebtToEquity" label="债务/净资产上限 %" :min="0" :max="200" :step="5" />
                <NumberField v-model="product.maxExposureRatio" label="最大股票敞口 %" :min="100" :max="300" :step="10" />
                <NumberField v-model="product.interestRatePerRound" label="每回合利率 %" :min="0" :max="10" :step="0.1" />
                <NumberField v-model="product.maturityRounds" label="期限（回合）" :min="1" :max="50" :step="1" />
                <NumberField v-model="product.minBorrowRatio" label="最低借款/初始资金 %" :min="0" :max="100" :step="1" />
                <NumberField v-model="product.borrowCooldownRounds" label="借款冷却（回合）" :min="0" :max="20" :step="1" />
                <NumberField v-model="product.collateralHaircut" label="股票抵押折扣 %" :min="0" :max="50" :step="1" />
                <NumberField v-model="product.originationLtv" label="放款 LTV %" :min="0" :max="95" :step="1" />
                <NumberField v-model="product.warningLtv" label="警戒 LTV %" :min="0" :max="95" :step="1" />
                <NumberField v-model="product.blockLtv" label="禁借 LTV %" :min="0" :max="95" :step="1" />
                <NumberField v-model="product.liquidationLtv" label="强平 LTV %" :min="0" :max="95" :step="1" />
                <NumberField v-model="product.recoveryLtv" label="强平恢复 LTV %" :min="0" :max="95" :step="1" />
              </div>
            </article>
          </div>
          </template>
          <div v-else class="disabled-row"><Landmark :size="18" />本游戏不开放银行融资</div>
        </section>

        <section v-show="activeTab === 'EVENT'" class="form-section events-section">
          <div class="section-heading"><span>05</span><h2>随机事件</h2><div class="event-tools"><button type="button" title="下载 CSV 模板" @click="downloadTemplate"><Download :size="16" />模板</button><button type="button" title="导入 CSV" @click="csvInput.click()"><Upload :size="16" />导入</button><button type="button" class="add-event" @click="addEvent"><Plus :size="16" />添加</button></div></div>
          <input ref="csvInput" class="hidden-input" type="file" accept=".csv,text/csv" @change="readCsv" />
          <div class="probability-note"><Info :size="17" /><p>每回合先从尚未出现的事件中随机选一个候选，再按该事件自己的“发生概率”判断是否出现。事件数量不会叠加提高触发频率；CSV 模板内附字段说明和理论建议值，说明行导入时会自动忽略。</p></div>
          <div v-if="csvPreview.length || csvErrors.length" class="csv-preview">
            <header><strong>CSV 预览：{{ csvPreview.length }} 条可导入</strong><button type="button" title="关闭预览" @click="clearCsv"><X :size="16" /></button></header>
            <p v-if="csvErrors.length">{{ csvErrors.slice(0, 3).join('；') }}</p>
            <div v-if="csvPreview.length"><span v-for="item in csvPreview.slice(0, 4)" :key="item.localId">{{ item.name }} · {{ item.occurrenceProbability }}%</span></div>
            <footer v-if="csvPreview.length"><button type="button" class="btn btn-outline" @click="applyCsv(false)">追加导入</button><button type="button" class="btn btn-primary" @click="applyCsv(true)">替换现有事件</button></footer>
          </div>
          <div v-if="form.config.events.length" class="event-list">
            <article v-for="entry in pagedEvents" :key="entry.event.localId" class="event-item">
              <div class="event-summary"><span>事件 {{ entry.index + 1 }}</span><strong>{{ entry.event.name }}</strong><small>{{ entry.event.description }}</small><small class="event-meta">{{ eventMeta(entry.event) }}</small></div>
              <div class="event-stats"><span>候选权重 {{ entry.event.candidateWeight || 1 }}</span><span>发生 {{ entry.event.occurrenceProbability }}%</span><span>影响 {{ entry.event.effectProbability }}%</span><span>{{ directionLabel(entry.event.direction) }} {{ entry.event.minImpactPercent }}%～{{ entry.event.maxImpactPercent }}%</span></div>
              <label class="event-enabled"><input v-model="entry.event.enabled" type="checkbox" />启用</label><button type="button" title="编辑事件" @click="editEvent(entry.index)"><Pencil :size="16" /></button><button type="button" title="删除事件" class="delete-event" @click="removeEvent(entry.index)"><Trash2 :size="17" /></button>
            </article>
          </div>
          <PaginationBar v-if="form.config.events.length" v-model="eventPage" :total-pages="eventPages" :total-items="form.config.events.length" />
          <div v-if="!form.config.events.length" class="no-events">当前没有随机事件</div>
        </section>

        <section v-show="activeTab === 'ACCESS'" class="form-section access-section">
          <div class="section-heading"><span>06</span><h2>版本与访问</h2></div>
          <div class="fields two-cols">
            <label class="form-group"><span class="form-label">版本标识</span><input v-model.trim="form.versionLabel" class="form-input" maxlength="40" placeholder="例如 1.0、赛季一" /><small>标识不变时，即使发布了新配置，成绩仍归入同一个排行榜版本。</small></label>
            <label class="form-group switch-field"><span class="form-label">游玩需要密钥</span><label class="switch"><input v-model="form.playKeyRequired" type="checkbox" /><span></span></label></label>
          </div>
          <div v-if="form.playKeyRequired" class="key-manager">
            <div class="key-create">
              <input v-model.trim="keyDraft.keyValue" class="form-input" maxlength="80" placeholder="输入新密钥" />
              <CustomSelect v-model="keyDraft.playScope" :options="keyScopeOptions" />
              <CustomSelect v-model="keyDraft.usageMode" :options="keyUsageOptions" />
              <input v-if="keyDraft.usageMode === 'LIMITED'" v-model.number="keyDraft.maxUses" class="form-input" type="number" min="1" max="100000" placeholder="次数" />
              <button type="button" class="btn btn-primary" :disabled="!gameId || !keyDraft.keyValue" @click="addPlayKey"><Plus :size="16" />添加密钥</button>
            </div>
            <p v-if="!gameId" class="key-help">先保存草稿，再添加和管理密钥。</p>
            <div v-else-if="playKeys.length" class="key-list">
              <article v-for="key in playKeys" :key="key.id">
                <div><strong>{{ key.keyHint }}</strong><small>{{ keyScopeLabel(key.playScope) }} · {{ keyUsageLabel(key) }} · 已使用 {{ key.usedCount }} 次 · {{ keyLastUsed(key) }}</small></div>
                <span :class="key.status.toLowerCase()">{{ key.status === 'ACTIVE' ? '启用中' : '已禁用' }}</span>
                <button type="button" class="btn btn-ghost" @click="togglePlayKey(key)">{{ key.status === 'ACTIVE' ? '禁用' : '启用' }}</button>
                <button type="button" class="icon-danger" title="删除密钥" @click="removePlayKey(key)"><Trash2 :size="16" /></button>
              </article>
            </div>
            <div v-else-if="gameId" class="no-events">尚未添加游玩密钥</div>
          </div>
        </section>
      </main>

      <aside class="editor-summary">
        <header><div><span>配置概览</span><h2>发布检查</h2></div><strong>{{ gameId ? '编辑中' : '草稿' }}</strong></header>
        <div class="stock-preview">
          <span>{{ form.config.stockCode || '--' }}</span>
          <strong>{{ form.title || '未命名股票' }}</strong>
          <small>{{ form.config.stockType || '未设置类型' }}</small>
        </div>
        <dl class="summary-metrics">
          <div><dt>局长</dt><dd>{{ form.config.roundCount }} 回合</dd></div>
          <div><dt>价格引擎</dt><dd>{{ form.config.pricingEngineVersion }}</dd></div>
          <div><dt>参与方式</dt><dd>{{ form.config.multiplayerEnabled ? `最多 ${form.config.maxHumanPlayers} 人` : '单人' }}</dd></div>
          <div><dt>银行融资</dt><dd>{{ form.config.loanEnabled ? (form.config.creditInteractionMode === 'INLINE' ? '开盘内' : '独立阶段') : '关闭' }}</dd></div>
        </dl>
        <h3>发布条件</h3>
        <ul>
          <li :class="{ ok: form.title.length >= 2 }"><CircleCheck :size="16" />股票名称</li>
          <li :class="{ ok: /^[A-Z0-9]{2,10}$/.test(form.config.stockCode) }"><CircleCheck :size="16" />股票代码</li>
          <li :class="{ ok: Boolean(form.config.stockType) }"><CircleCheck :size="16" />股票类型</li>
          <li :class="{ ok: form.config.events.every(event => event.occurrenceProbability >= 0 && event.occurrenceProbability <= 100) }"><CircleCheck :size="16" />事件概率</li>
          <li :class="{ ok: !form.config.aiEnabled || form.config.aiPlayers.length > 0 }"><CircleCheck :size="16" />AI 阵容</li>
        </ul>
      </aside>
    </form>
    <div v-if="showEventEditor" class="modal-mask" @click.self="closeEventEditor">
      <form class="event-dialog" @submit.prevent="saveEvent">
        <header><div><span>{{ editingEventIndex == null ? '新增事件' : `编辑事件 ${editingEventIndex + 1}` }}</span><h2>随机事件设置</h2></div><button type="button" title="关闭" @click="closeEventEditor"><X :size="19" /></button></header>
        <div class="fields event-dialog-fields">
          <label class="form-group full"><span class="form-label">事件名称</span><input v-model.trim="eventDraft.name" class="form-input" maxlength="50" required /></label>
          <label class="form-group full"><span class="form-label">事件介绍</span><textarea v-model.trim="eventDraft.description" class="form-textarea" maxlength="500" required /></label>
          <NumberField v-model="eventDraft.occurrenceProbability" label="发生概率 %" :min="0" :max="100" :step="0.1" />
          <NumberField v-model="eventDraft.candidateWeight" label="候选权重" :min="0.01" :max="1000" :step="0.1" />
          <NumberField v-model="eventDraft.effectProbability" label="影响概率 %" :min="0" :max="100" :step="0.1" />
          <label class="form-group"><span class="form-label">影响方向</span><CustomSelect v-model="eventDraft.direction" :options="directionOptions" /></label>
          <label class="form-group"><span class="form-label">事件类型</span><CustomSelect v-model="eventDraft.eventType" :options="eventTypeOptions" /></label>
          <label class="form-group"><span class="form-label">股票相关度</span><CustomSelect v-model="eventDraft.relevance" :options="relevanceOptions" /></label>
          <label class="form-group"><span class="form-label">信息可信度</span><CustomSelect v-model="eventDraft.credibility" :options="credibilityOptions" /></label>
          <label class="form-group"><span class="form-label">意外程度</span><CustomSelect v-model="eventDraft.surprise" :options="surpriseOptions" /></label>
          <label class="form-group"><span class="form-label">事件强度</span><CustomSelect v-model="eventDraft.strength" :options="strengthOptions" /></label>
          <NumberField v-model="eventDraft.minImpactPercent" label="原始冲击下限 %" :min="0" :max="50" :step="0.1" />
          <NumberField v-model="eventDraft.maxImpactPercent" label="原始冲击上限 %" :min="0" :max="50" :step="0.1" />
          <label class="form-group"><span class="form-label">事件链标识</span><input v-model.trim="eventDraft.chainKey" class="form-input" maxlength="40" placeholder="例如 DEMAND_SLOWDOWN" /></label>
          <NumberField v-model="eventDraft.chainStep" label="事件链步骤" :min="0" :max="20" :step="1" />
          <label class="form-group full"><span class="form-label">前置标签</span><input :value="tagsText(eventDraft.parentTags)" class="form-input" placeholder="多个标签用逗号分隔" @input="eventDraft.parentTags = parseTags($event.target.value)" /></label>
          <label class="form-group full"><span class="form-label">后续标签</span><input :value="tagsText(eventDraft.followUpTags)" class="form-input" placeholder="例如 ORDERS, INVENTORY" @input="eventDraft.followUpTags = parseTags($event.target.value)" /></label>
          <NumberField v-model="eventDraft.followUpProbabilityBoost" label="后续概率加成 %" :min="0" :max="100" :step="0.1" />
          <NumberField v-model="eventDraft.maxChainDepth" label="事件链最大深度" :min="1" :max="20" :step="1" />
          <label class="event-enabled dialog-enabled"><input v-model="eventDraft.enabled" type="checkbox" />启用该事件</label>
        </div>
        <footer><button type="button" class="btn btn-outline" @click="closeEventEditor">取消</button><button type="submit" class="btn btn-primary"><Save :size="16" />保存事件</button></footer>
      </form>
    </div>
    <div v-if="showAiEditor" class="modal-mask" @click.self="closeAiEditor">
      <form class="event-dialog ai-add-dialog" @submit.prevent="saveAi">
        <header><div><span>新增对手</span><h2>添加 AI</h2></div><button type="button" title="关闭" @click="closeAiEditor"><X :size="19" /></button></header>
        <div class="ai-style-picker" role="radiogroup" aria-label="交易风格">
          <button v-for="style in aiStyleOptions" :key="style.value" type="button" :class="{ active: aiDraft.style === style.value }" role="radio" :aria-checked="aiDraft.style === style.value" @click="selectAiDraftStyle(style.value)">
            <strong>{{ style.label }}</strong><small>{{ style.description }}</small>
          </button>
        </div>
        <div class="fields event-dialog-fields ai-dialog-fields">
          <label class="form-group full"><span class="form-label">显示名称</span><input v-model.trim="aiDraft.name" class="form-input" maxlength="30" required /></label>
          <NumberField v-model="aiDraft.initialCash" label="初始资金" :min="100" :max="10000000" :step="100" />
          <NumberField v-model="aiDraft.maxOrderQuantity" label="单次下单上限" :min="1" :max="10000000" :step="1" />
          <NumberField v-model="aiDraft.activityPercent" label="交易活跃倾向 %" :min="0" :max="100" :step="1" />
          <NumberField v-model="aiDraft.riskPercent" label="最大股票敞口 %" :min="0" :max="300" :step="5" />
          <NumberField v-model="aiDraft.rebalanceBandPctPoints" label="调仓缓冲（百分点）" :min="0.5" :max="20" :step="0.5" />
          <NumberField v-model="aiDraft.minHoldRounds" label="最短持有回合" :min="0" :max="20" :step="1" />
          <NumberField v-model="aiDraft.minTradeAssetPct" label="最小交易资产占比 %" :min="0" :max="20" :step="0.5" />
          <NumberField v-model="aiDraft.maxRoundTurnoverPct" label="单回合换手上限 %" :min="1" :max="100" :step="1" />
          <NumberField v-model="aiDraft.maxEstimatedImpactPct" label="最大预估价格冲击 %" :min="0.1" :max="20" :step="0.1" />
          <NumberField v-model="aiDraft.maxPositionStepPctPoints" label="普通敞口步长（百分点）" :min="1" :max="100" :step="1" />
          <NumberField v-model="aiDraft.highUrgencyPositionStepPctPoints" label="强信号敞口步长（百分点）" :min="1" :max="100" :step="1" />
          <NumberField v-model="aiDraft.reverseCooldownRounds" label="反向交易冷却回合" :min="0" :max="20" :step="1" />
          <label class="form-group full"><span class="form-label">指定策略</span><textarea v-model.trim="aiDraft.strategyPrompt" class="form-textarea" maxlength="300" required /></label>
        </div>
        <footer><button type="button" class="btn btn-outline" @click="closeAiEditor">取消</button><button type="submit" class="btn btn-primary"><Plus :size="16" />添加 AI</button></footer>
      </form>
    </div>
  </div>
</template>

<script setup>
import { computed, defineComponent, h, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowLeft, BotOff, CircleCheck, Clock3, Download, Info, Landmark, Pencil, Plus, Save, Send, Trash2, TriangleAlert, Upload, X } from 'lucide-vue-next'
import Papa from 'papaparse'
import { createGame, createGamePlayKey, deleteGamePlayKey, getGame, getGamePlayKeys, publishGame, setGamePlayKeyStatus, updateGame } from '../../api/entertainment'
import { confirmNotice, notifyError, notifySuccess } from '../../services/notice'
import LoadingSpinner from '../../components/LoadingSpinner.vue'
import CustomSelect from '../../components/CustomSelect.vue'
import PaginationBar from '../../components/PaginationBar.vue'

const NumberField = defineComponent({
  props: { modelValue: [Number, String], label: String, min: Number, max: Number, step: Number },
  emits: ['update:modelValue'],
  setup(props, { emit }) {
    return () => h('label', { class: 'form-group' }, [
      h('span', { class: 'form-label' }, props.label),
      h('input', { class: 'form-input', type: 'number', value: props.modelValue, min: props.min, max: props.max, step: props.step,
        onInput: event => emit('update:modelValue', Number(event.target.value)) })
    ])
  }
})
const route = useRoute()
const router = useRouter()
const gameId = computed(() => route.params.id ? Number(route.params.id) : null)
const loading = ref(Boolean(gameId.value))
const saving = ref(false)
const error = ref('')
const savedStatus = ref('')
const csvInput = ref(null)
const csvPreview = ref([])
const csvErrors = ref([])
const showEventEditor = ref(false)
const showAiEditor = ref(false)
const activeTab = ref('BASIC'); const aiPage = ref(1); const eventPage = ref(1)
const playKeys = ref([])
const keyDraft = reactive({ keyValue: '', playScope: 'BOTH', usageMode: 'ONCE', maxUses: 5 })
const editingEventIndex = ref(null)
let eventSequence = 0
let aiSequence = 0
const aiStyleOptions = [
  { value: 'CONSERVATIVE', label: '稳健', description: '重视本金与现金余量，低位试仓、分批止盈。', name: '稳健投资者', initialCash: 10000, maxOrderQuantity: 180, activityPercent: 48, riskPercent: 110, rebalanceBandPctPoints: 7, minHoldRounds: 2, minTradeAssetPct: 2, maxRoundTurnoverPct: 20, maxEstimatedImpactPct: 2, strategyPrompt: '先比较当前价、持仓成本与多周期客观指标。证据充分时逐步建立观点；普通波动保持目标敞口，风险收益比下降时分批调整，结构化失效条件触发时退出或替换观点。' },
  { value: 'TREND', label: '趋势', description: '等待方向确认后顺势交易，趋势破坏即减仓。', name: '趋势交易者', initialCash: 18000, maxOrderQuantity: 600, activityPercent: 72, riskPercent: 200, rebalanceBandPctPoints: 5, minHoldRounds: 1, minTradeAssetPct: 2, maxRoundTurnoverPct: 30, maxEstimatedImpactPct: 2, strategyPrompt: '结合3、8、20回合收益和斜率确认趋势。趋势形成后维护同一投资观点；单回合急涨不追高，趋势结构破坏或结构化失效条件触发时降低目标敞口。' },
  { value: 'CONTRARIAN', label: '逆势', description: '在恐慌低估时布局，在情绪过热时退出。', name: '逆向投资者', initialCash: 60000, maxOrderQuantity: 2200, activityPercent: 46, riskPercent: 150, rebalanceBandPctPoints: 5, minHoldRounds: 1, minTradeAssetPct: 2, maxRoundTurnoverPct: 25, maxEstimatedImpactPct: 2, strategyPrompt: '对照近期区间、波动和卖压寻找过度反应。低价本身不是买入理由，跌势放缓且证据改善时才建立观点；过热时可以在观点仍有效的情况下分批止盈。' },
  { value: 'AGGRESSIVE', label: '激进', description: '高参与、高敞口捕捉机会，但不无条件追涨。', name: '激进交易者', initialCash: 30000, maxOrderQuantity: 1400, activityPercent: 88, riskPercent: 250, rebalanceBandPctPoints: 3, minHoldRounds: 0, minTradeAssetPct: 2, maxRoundTurnoverPct: 40, maxEstimatedImpactPct: 2, strategyPrompt: '利用强趋势、反转和高质量事件建立高置信观点，允许较大目标敞口；仍需考虑价格冲击，普通波动不反复交易，核心判断失效时以高紧急度调整。' },
  { value: 'RANDOM', label: '灵活', description: '在人气、趋势与估值信号间切换，保留人的犹豫。', name: '灵活投资者', initialCash: 8000, maxOrderQuantity: 260, activityPercent: 64, riskPercent: 150, rebalanceBandPctPoints: 5, minHoldRounds: 1, minTradeAssetPct: 2, maxRoundTurnoverPct: 30, maxEstimatedImpactPct: 2, strategyPrompt: '根据市场结构在趋势、逆势和防守思路之间选择，但一个活动观点内保持逻辑连续。只有重要新证据出现才显著调整目标敞口或替换观点。' }
]
const editorTabs = [{ value: 'BASIC', label: '基础与市场' }, { value: 'AI', label: 'AI 对手' }, { value: 'BANK', label: '银行融资' }, { value: 'EVENT', label: '事件池' }, { value: 'ACCESS', label: '版本与密钥' }]
const visibilityOptions = [{ value: 'PUBLIC', label: '公开' }, { value: 'UNLISTED', label: '仅链接可见' }]
const liquidityOptions = [{ value: 'LOW', label: '低' }, { value: 'MEDIUM', label: '中' }, { value: 'HIGH', label: '高' }]
const progressionOptions = [{ value: 'AUTO', label: '到时自动推进' }, { value: 'MANUAL', label: '无计时，全员确认后结算' }]
const pricingOptions = [{ value: 'V2', label: '市场趋势 V2' }, { value: 'V3', label: '基本面与财报 V3' }]
const tradingModeOptions = [{ value: 'CLASSIC', label: '经典分阶段交易' }, { value: 'TARGET_AUCTION', label: '密封目标仓位竞价' }]
const scenarioOptions = [{ value: 'CUSTOM', label: '自定义' }, { value: 'QUICK', label: '快速局' }, { value: 'STANDARD', label: '标准局' }, { value: 'LONG', label: '长线局' }, { value: 'HIGH_VOLATILITY', label: '高波动挑战' }, { value: 'CREDIT_CRISIS', label: '信用危机' }]
const creditInteractionOptions = [{ value: 'INLINE', label: '开盘内融资' }, { value: 'PHASED', label: '独立银行阶段' }]
const difficultyOptions = [{ value: 'EASY', label: '简单' }, { value: 'NORMAL', label: '普通' }, { value: 'HARD', label: '困难' }]
const directionOptions = [{ value: 'UP', label: '上涨' }, { value: 'DOWN', label: '下跌' }, { value: 'RANDOM', label: '随机' }]
const eventTypeOptions = ['DEMAND:需求','SUPPLY:供给','POLICY:政策','FINANCIAL:财务','ACCIDENT:突发','INDUSTRY:行业','SENTIMENT:情绪'].map(item => { const [value, label] = item.split(':'); return { value, label } })
const relevanceOptions = [{ value: 'LOW', label: '低' }, { value: 'MEDIUM', label: '中' }, { value: 'HIGH', label: '高' }]
const credibilityOptions = [{ value: 'RUMOR', label: '传闻' }, { value: 'PRELIMINARY', label: '初步确认' }, { value: 'CONFIRMED', label: '已确认' }]
const surpriseOptions = [{ value: 'EXPECTED', label: '已有预期' }, { value: 'NORMAL', label: '一般意外' }, { value: 'MAJOR', label: '显著超预期' }]
const strengthOptions = [{ value: 'WEAK', label: '弱' }, { value: 'MEDIUM', label: '中' }, { value: 'STRONG', label: '强' }]
const keyScopeOptions = [{ value: 'BOTH', label: '单人和多人' }, { value: 'SOLO', label: '仅单人' }, { value: 'MULTI', label: '仅多人' }]
const keyUsageOptions = [{ value: 'ONCE', label: '仅一次' }, { value: 'LIMITED', label: '有限次' }, { value: 'UNLIMITED', label: '不限次数' }]
const aiStyleSelectOptions = computed(() => aiStyleOptions.map(item => ({ value: item.value, label: item.label })))
const aiPages = computed(() => Math.max(1, Math.ceil(form.config.aiPlayers.length / 2)))
const pagedAiPlayers = computed(() => form.config.aiPlayers.map((ai, index) => ({ ai, index })).slice((aiPage.value - 1) * 2, aiPage.value * 2))
const eventPages = computed(() => Math.max(1, Math.ceil(form.config.events.length / 8)))
const pagedEvents = computed(() => form.config.events.map((event, index) => ({ event, index })).slice((eventPage.value - 1) * 8, eventPage.value * 8))
const aiDefaults = aiStyleOptions.slice(0, 3).map(({ value, label, description, ...preset }) => ({
  ...preset, style: value, maxPositionStepPctPoints: 15,
  highUrgencyPositionStepPctPoints: 30, reverseCooldownRounds: 2
}))
const defaultCreditProducts = () => [
  { code: 'CONSERVATIVE_BANK', name: '稳健抵押银行', riskLevel: 'LOW', enabled: true, maxInitialCapitalRatio: 50, maxDebtToEquity: 50, maxExposureRatio: 150, interestRatePerRound: 0.4, maturityRounds: 3, minBorrowRatio: 5, borrowCooldownRounds: 1, collateralHaircut: 5, originationLtv: 36, warningLtv: 42, blockLtv: 47, liquidationLtv: 52, recoveryLtv: 38 },
  { code: 'GROWTH_BANK', name: '进取杠杆银行', riskLevel: 'MEDIUM', enabled: true, maxInitialCapitalRatio: 100, maxDebtToEquity: 100, maxExposureRatio: 200, interestRatePerRound: 0.8, maturityRounds: 3, minBorrowRatio: 5, borrowCooldownRounds: 1, collateralHaircut: 7, originationLtv: 56, warningLtv: 60, blockLtv: 64, liquidationLtv: 68, recoveryLtv: 56 },
  { code: 'HIGH_RISK_BANK', name: '高风险杠杆银行', riskLevel: 'HIGH', enabled: true, maxInitialCapitalRatio: 150, maxDebtToEquity: 150, maxExposureRatio: 250, interestRatePerRound: 1.2, maturityRounds: 2, minBorrowRatio: 10, borrowCooldownRounds: 1, collateralHaircut: 8, originationLtv: 66, warningLtv: 70, blockLtv: 73, liquidationLtv: 76, recoveryLtv: 66 },
  { code: 'EXTREME_BANK', name: '极端杠杆银行', riskLevel: 'EXTREME', enabled: false, maxInitialCapitalRatio: 200, maxDebtToEquity: 200, maxExposureRatio: 300, interestRatePerRound: 1.8, maturityRounds: 2, minBorrowRatio: 10, borrowCooldownRounds: 1, collateralHaircut: 10, originationLtv: 75, warningLtv: 78, blockLtv: 81, liquidationLtv: 84, recoveryLtv: 74 }
]
const emptyEvent = () => ({ name: '', description: '', candidateWeight: 1, occurrenceProbability: 10, effectProbability: 100, direction: 'RANDOM', minImpactPercent: 2, maxImpactPercent: 6, eventType: 'INDUSTRY', relevance: 'MEDIUM', credibility: 'CONFIRMED', surprise: 'NORMAL', strength: 'MEDIUM', chainKey: '', chainStep: 0, parentTags: [], followUpTags: [], followUpProbabilityBoost: 0, maxChainDepth: 4, enabled: true })
const eventDraft = reactive(emptyEvent())
const aiDraft = reactive({ ...aiDefaults[0] })
const newAiProfile = (preset = aiDefaults[aiSequence % aiDefaults.length]) => ({
  maxPositionStepPctPoints: 15, highUrgencyPositionStepPctPoints: 30,
  reverseCooldownRounds: 2, localId: ++aiSequence, ...preset
})

const form = reactive({
  title: '', description: '', coverUrl: '', visibility: 'PUBLIC', versionLabel: '1.0', playKeyRequired: false,
  config: {
    stockCode: '', stockType: '科技', roundCount: 10, revealSeconds: 1, tradingSeconds: 5,
    initialCash: 10000, initialPrice: 100, baseVolatilityPercent: 3, liquidity: 'MEDIUM', marketLiquidityShares: 1000,
    pricingEngineVersion: 'V2', scenarioPreset: 'STANDARD', tradingMode: 'CLASSIC',
    maxOrderImpactPercent: 10, maxBuyQuantity: 1000, progressionMode: 'AUTO', multiplayerEnabled: true, maxHumanPlayers: 8,
    aiEnabled: true, aiCount: 3, aiDifficulty: 'NORMAL',
    aiStyles: ['CONSERVATIVE', 'TREND', 'CONTRARIAN'], aiPlayers: aiDefaults.map(item => newAiProfile(item)), events: [],
    loanEnabled: false, creditRulesVersion: 1, creditProducts: defaultCreditProducts(),
    creditPhaseTimed: false, creditPhaseSeconds: 30, repaymentPhaseTimed: false, repaymentPhaseSeconds: 30,
    creditInteractionMode: 'INLINE', fundamentalEnabled: false, reportIntervalRounds: 5,
    initialFundamentals: { demandIndex: 100, orderIndex: 100, inventoryIndex: 100, marginIndex: 100, cashFlowIndex: 100 },
    fundamentalSensitivity: 0.35, researchPoints: 3, feePercent: 0.05, slippagePercent: 0.05,
    dailyChallengeEnabled: false
  }
})

watch(() => form.config.pricingEngineVersion, version => {
  form.config.fundamentalEnabled = version === 'V3'
})

watch(() => form.config.creditInteractionMode, mode => {
  if (mode === 'INLINE') form.config.creditPhaseTimed = false
})

function uppercaseCode() { form.config.stockCode = form.config.stockCode.toUpperCase().replace(/[^A-Z0-9]/g, '') }
function applyScenarioPreset() {
  const presets = {
    QUICK: { roundCount: 12, baseVolatilityPercent: 3, loanEnabled: false },
    STANDARD: { roundCount: 30, baseVolatilityPercent: 3, loanEnabled: true },
    LONG: { roundCount: 80, baseVolatilityPercent: 2.4, loanEnabled: true, fundamentalEnabled: true, pricingEngineVersion: 'V3' },
    HIGH_VOLATILITY: { roundCount: 20, baseVolatilityPercent: 5, loanEnabled: false },
    CREDIT_CRISIS: { roundCount: 24, baseVolatilityPercent: 4.5, loanEnabled: true }
  }
  Object.assign(form.config, presets[form.config.scenarioPreset] || {})
}
function toggleAi() {
  if (!form.config.aiEnabled) return
  if (!form.config.aiPlayers.length) form.config.aiPlayers = aiDefaults.map(item => newAiProfile(item))
}
function addEvent() {
  editingEventIndex.value = null
  Object.assign(eventDraft, emptyEvent())
  showEventEditor.value = true
}
function editEvent(index) { editingEventIndex.value = index; Object.assign(eventDraft, emptyEvent(), form.config.events[index]); showEventEditor.value = true }
function closeEventEditor() { showEventEditor.value = false; editingEventIndex.value = null }
function saveEvent() {
  if (eventDraft.minImpactPercent > eventDraft.maxImpactPercent) { notifyError('事件最小幅度不能大于最大幅度'); return }
  const saved = { ...eventDraft, localId: editingEventIndex.value == null ? ++eventSequence : form.config.events[editingEventIndex.value].localId }
  if (editingEventIndex.value == null) form.config.events.push(saved); else form.config.events.splice(editingEventIndex.value, 1, saved)
  error.value = ''; closeEventEditor()
}
function removeEvent(index) { form.config.events.splice(index, 1) }
function parseTags(value) { return [...new Set(String(value || '').split(/[,;|，]/).map(item => item.trim().toUpperCase()).filter(Boolean))].slice(0, 12) }
function tagsText(value) { return Array.isArray(value) ? value.join(', ') : '' }
function aiStyle(style) { return aiStyleOptions.find(item => item.value === style) || aiStyleOptions[0] }
function stylePreset(style) { const { value, label, description, ...preset } = aiStyle(style); return { maxPositionStepPctPoints: 15, highUrgencyPositionStepPctPoints: 30, reverseCooldownRounds: 2, ...preset, style: value } }
function applyAiStyle(ai) { const localId = ai.localId; Object.assign(ai, stylePreset(ai.style), { localId }) }
function addAi() {
  Object.assign(aiDraft, stylePreset(aiStyleOptions[form.config.aiPlayers.length % aiStyleOptions.length].value))
  showAiEditor.value = true
}
function selectAiDraftStyle(style) { Object.assign(aiDraft, stylePreset(style)) }
function closeAiEditor() { showAiEditor.value = false }
function saveAi() {
  if (!aiDraft.name || !aiDraft.strategyPrompt) return
  form.config.aiPlayers.push(newAiProfile({ ...aiDraft }))
  closeAiEditor()
}
function removeAi(index) { if (form.config.aiPlayers.length > 1) form.config.aiPlayers.splice(index, 1) }
function keyScopeLabel(value) { return ({ BOTH: '单人和多人', SOLO: '仅单人', MULTI: '仅多人' }[value] || value) }
function keyUsageLabel(key) { return key.usageMode === 'UNLIMITED' ? '不限次数' : key.usageMode === 'ONCE' ? '仅一次' : `最多 ${key.maxUses} 次` }
function keyLastUsed(key) { return key.lastUsedAt ? `最近 ${new Date(key.lastUsedAt).toLocaleString('zh-CN')}` : '尚未使用' }
async function loadPlayKeys() { if (gameId.value) playKeys.value = await getGamePlayKeys(gameId.value) }
async function addPlayKey() { try { await createGamePlayKey(gameId.value, { ...keyDraft, maxUses: keyDraft.usageMode === 'LIMITED' ? keyDraft.maxUses : null }); keyDraft.keyValue = ''; await loadPlayKeys(); notifySuccess('游玩密钥已添加') } catch (e) { notifyError(e.message) } }
async function togglePlayKey(key) { const nextStatus = key.status === 'ACTIVE' ? 'DISABLED' : 'ACTIVE'; try { await setGamePlayKeyStatus(gameId.value, key.id, nextStatus); await loadPlayKeys(); notifySuccess(nextStatus === 'ACTIVE' ? '密钥已启用' : '密钥已禁用') } catch (e) { notifyError(e.message) } }
async function removePlayKey(key) { if (!await confirmNotice(`删除密钥 ${key.keyHint}？删除后无法恢复或使用。`, { title: '删除游玩密钥', confirmText: '确认删除' })) return; try { await deleteGamePlayKey(gameId.value, key.id); await loadPlayKeys(); notifySuccess('游玩密钥已删除') } catch (e) { notifyError(e.message) } }
function directionLabel(direction) { return ({ UP: '上涨', DOWN: '下跌', RANDOM: '随机' }[direction] || direction) }
function eventMeta(event) {
  const type = ({ DEMAND: '需求', SUPPLY: '供给', POLICY: '政策', FINANCIAL: '财务', ACCIDENT: '突发', INDUSTRY: '行业', SENTIMENT: '情绪' }[event.eventType] || '行业')
  const relevance = ({ LOW: '低相关', MEDIUM: '中相关', HIGH: '高相关' }[event.relevance] || '中相关')
  const credibility = ({ RUMOR: '传闻', PRELIMINARY: '初步确认', CONFIRMED: '已确认' }[event.credibility] || '已确认')
  const surprise = ({ EXPECTED: '已有预期', NORMAL: '一般意外', MAJOR: '显著超预期' }[event.surprise] || '一般意外')
  const strength = ({ WEAK: '弱', MEDIUM: '中', STRONG: '强' }[event.strength] || '中')
  return `${type} · ${relevance} · ${credibility} · ${surprise} · ${strength}`
}

const CSV_FIELDS = ['rowType','guideField','guideDescription','theoreticalValue','name','description','candidateWeight','occurrenceProbability','effectProbability','direction','minImpactPercent','maxImpactPercent','eventType','relevance','credibility','surprise','strength','chainKey','chainStep','parentTags','followUpTags','followUpProbabilityBoost','maxChainDepth','enabled']
const eventTemplateRows = [
  { rowType: 'EVENT', name: '行业订单超预期', description: '核心客户追加订单，未来两个季度需求预期上升', candidateWeight: 1, occurrenceProbability: 15, effectProbability: 80, direction: 'UP', minImpactPercent: 1.5, maxImpactPercent: 4, eventType: 'DEMAND', relevance: 'HIGH', credibility: 'CONFIRMED', surprise: 'MAJOR', strength: 'STRONG', chainKey: 'DEMAND_RECOVERY', chainStep: 1, parentTags: '', followUpTags: 'ORDERS,INVENTORY', followUpProbabilityBoost: 20, maxChainDepth: 4, enabled: true },
  { rowType: 'GUIDE', guideField: 'rowType', guideDescription: 'EVENT 表示可导入事件；GUIDE 表示说明行，导入时自动忽略。旧 CSV 没有本列也可以导入。', theoreticalValue: 'EVENT 或 GUIDE' },
  { rowType: 'GUIDE', guideField: 'name', guideDescription: '玩家看到的事件标题，应直接表达发生了什么，避免只写“利好”或“利空”。', theoreticalValue: '必填，2-50 字' },
  { rowType: 'GUIDE', guideField: 'description', guideDescription: '补充事件对象、原因、规模和预期传导路径，不应提前写出最终涨跌结果。', theoreticalValue: '建议 20-80 字，最多 500 字' },
  { rowType: 'GUIDE', guideField: 'occurrenceProbability', guideDescription: '事件被选为本阶段候选后真正出现的概率。事件数量不会把概率相加。', theoreticalValue: '一般 5%-25%；罕见重大事件 1%-8%；有效范围 0%-100%' },
  { rowType: 'GUIDE', guideField: 'candidateWeight', guideDescription: '候选池中的相对抽取权重，不是百分比。权重 2 约为权重 1 的两倍候选机会。', theoreticalValue: '默认 1；常用 0.2-5；有效范围 0.01-1000' },
  { rowType: 'GUIDE', guideField: 'effectProbability', guideDescription: '事件出现后形成可观察价格影响的概率。形成影响后再分为轻微 50%、常规 35%、强烈 15%。', theoreticalValue: '一般 60%-90%；传闻 30%-60%；确定性事件才建议 100%' },
  { rowType: 'GUIDE', guideField: 'direction', guideDescription: '事件的经济方向。因果明确时使用 UP 或 DOWN；只有结果确实双向不确定时才使用 RANDOM。', theoreticalValue: 'UP / DOWN / RANDOM' },
  { rowType: 'GUIDE', guideField: 'minImpactPercent', guideDescription: '原始冲击区间下限，不是最终影响保底值。市场吸收后，最终影响可以低于该值。', theoreticalValue: '日常事件 0.5%-1.5%；重要事件 1.5%-3%；有效范围 0%-50%' },
  { rowType: 'GUIDE', guideField: 'maxImpactPercent', guideDescription: '原始冲击区间上限，同时也是事件自身的最终硬上限；必须大于或等于下限。', theoreticalValue: '日常事件 2%-4%；重大事件 4%-8%；极端玩法不建议超过 10%' },
  { rowType: 'GUIDE', guideField: 'eventType', guideDescription: '事件传导类型：需求、供给、政策、财务、突发、行业或情绪。', theoreticalValue: 'DEMAND / SUPPLY / POLICY / FINANCIAL / ACCIDENT / INDUSTRY / SENTIMENT' },
  { rowType: 'GUIDE', guideField: 'relevance', guideDescription: '事件与本股票收入、成本、供需或估值的直接关联程度。', theoreticalValue: 'LOW=间接；MEDIUM=行业相关；HIGH=直接影响' },
  { rowType: 'GUIDE', guideField: 'credibility', guideDescription: '信息被市场相信的程度，会决定原始冲击能传导多少。', theoreticalValue: 'RUMOR=传闻；PRELIMINARY=初步确认；CONFIRMED=已确认' },
  { rowType: 'GUIDE', guideField: 'surprise', guideDescription: '信息相对市场原有预期的新鲜程度；已有预期代表部分影响可能已计入价格。', theoreticalValue: 'EXPECTED=已有预期；NORMAL=一般意外；MAJOR=显著超预期' },
  { rowType: 'GUIDE', guideField: 'strength', guideDescription: '事件自身的基本冲击等级，不等于最终影响层级，仍会受到市场状态适配。', theoreticalValue: 'WEAK / MEDIUM / STRONG；日常事件通常 MEDIUM' },
  { rowType: 'GUIDE', guideField: 'chainKey', guideDescription: '同一经营故事使用相同事件链标识；留空表示独立事件。', theoreticalValue: '建议大写英文和下划线' },
  { rowType: 'GUIDE', guideField: 'parentTags / followUpTags', guideDescription: '前置和后续标签决定哪些事件可能承接，多个标签用逗号分隔。', theoreticalValue: '后续只提高候选概率，不保证发生' },
  { rowType: 'GUIDE', guideField: 'enabled', guideDescription: '是否把该事件放入候选池。关闭后保留配置但不会在游戏中出现。', theoreticalValue: 'true 或 false' },
  { rowType: 'GUIDE', guideField: '最终影响（计算说明）', guideDescription: '截断正态原始幅度 × 影响层级 × 相关度 × 可信度 × 意外程度 × 强度 × 市场状态 × 基础走势协同，并受基础波动率和最大幅度限制。', theoreticalValue: '可能低于 minImpactPercent，不会高于 maxImpactPercent' }
]
function downloadTemplate() {
  const csv = Papa.unparse(eventTemplateRows, { columns: CSV_FIELDS })
  const url = URL.createObjectURL(new Blob([`\uFEFF${csv}`], { type: 'text/csv;charset=utf-8' })); const anchor = document.createElement('a'); anchor.href = url; anchor.download = 'guess-stock-events-template.csv'; anchor.click(); URL.revokeObjectURL(url)
}
function readCsv(event) {
  const file = event.target.files?.[0]; if (!file) return
  Papa.parse(file, { header: true, skipEmptyLines: true, transformHeader: value => value.trim(), complete(result) {
    csvErrors.value = result.errors.map(item => `第 ${item.row + 2} 行：${item.message}`)
    csvPreview.value = result.data.map((row, index) => normalizeCsvRow(row, index)).filter(Boolean)
    event.target.value = ''
  } })
}
function normalizeCsvRow(row, index) {
  const rowType = String(row.rowType || '').trim().toUpperCase()
  if (rowType === 'GUIDE' || rowType === '说明') return null
  if (rowType && !['EVENT', '事件', 'SAMPLE', '示例'].includes(rowType)) { csvErrors.value.push(`第 ${index + 2} 行 rowType 无效`); return null }
  const number = (key, fallback) => row[key] === '' || row[key] == null ? fallback : Number(row[key])
  const item = { localId: ++eventSequence, name: String(row.name || '').trim(), description: String(row.description || '').trim(), candidateWeight: number('candidateWeight', 1), occurrenceProbability: number('occurrenceProbability', 0), effectProbability: number('effectProbability', 100), direction: String(row.direction || 'RANDOM').trim().toUpperCase(), minImpactPercent: number('minImpactPercent', 0), maxImpactPercent: number('maxImpactPercent', 0), eventType: String(row.eventType || 'INDUSTRY').trim().toUpperCase(), relevance: String(row.relevance || 'MEDIUM').trim().toUpperCase(), credibility: String(row.credibility || 'CONFIRMED').trim().toUpperCase(), surprise: String(row.surprise || 'NORMAL').trim().toUpperCase(), strength: String(row.strength || 'MEDIUM').trim().toUpperCase(), chainKey: String(row.chainKey || '').trim().toUpperCase(), chainStep: number('chainStep', 0), parentTags: parseTags(row.parentTags), followUpTags: parseTags(row.followUpTags), followUpProbabilityBoost: number('followUpProbabilityBoost', 0), maxChainDepth: number('maxChainDepth', 4), enabled: !['false','0','no'].includes(String(row.enabled).toLowerCase()) }
  if (!item.name || !['UP','DOWN','RANDOM'].includes(item.direction) || !['DEMAND','SUPPLY','POLICY','FINANCIAL','ACCIDENT','INDUSTRY','SENTIMENT'].includes(item.eventType) || !['LOW','MEDIUM','HIGH'].includes(item.relevance) || !['RUMOR','PRELIMINARY','CONFIRMED'].includes(item.credibility) || !['EXPECTED','NORMAL','MAJOR'].includes(item.surprise) || !['WEAK','MEDIUM','STRONG'].includes(item.strength) || [item.occurrenceProbability,item.effectProbability,item.minImpactPercent,item.maxImpactPercent].some(value => !Number.isFinite(value))) { csvErrors.value.push(`第 ${index + 2} 行字段无效`); return null }
  return item
}
function applyCsv(replace) { form.config.events = replace ? [...csvPreview.value] : [...form.config.events, ...csvPreview.value]; clearCsv() }
function clearCsv() { csvPreview.value = []; csvErrors.value = [] }

function payload() {
  const aiPlayers = form.config.aiEnabled ? form.config.aiPlayers.map(({ localId, ...ai }) => ai) : []
  return {
    title: form.title, description: form.description || null, coverUrl: form.coverUrl || null, visibility: form.visibility,
    versionLabel: form.versionLabel, playKeyRequired: form.playKeyRequired,
    config: { ...form.config, aiCount: aiPlayers.length, aiStyles: [...new Set(aiPlayers.map(ai => ai.style))], aiPlayers,
      events: form.config.events.map(({ localId, ...event }) => event) }
  }
}

async function save(shouldPublish) {
  saving.value = true
  error.value = ''
  savedStatus.value = ''
  try {
    let detail
    if (gameId.value) detail = await updateGame(gameId.value, payload())
    else detail = await createGame(payload())
    const id = detail.game.id
    if (shouldPublish) {
      await publishGame(id)
      savedStatus.value = '已发布'
      notifySuccess('游戏新版本已发布')
      router.replace(`/customer/entertainment/games/${id}`)
    } else {
      savedStatus.value = '草稿已保存'
      notifySuccess('草稿已保存')
      if (!gameId.value) router.replace(`/customer/entertainment/games/${id}/edit`)
    }
  } catch (e) { notifyError(e.message) } finally { saving.value = false }
}

async function load() {
  if (!gameId.value) { loading.value = false; error.value = ''; return }
  loading.value = true
  error.value = ''
  try {
    const detail = await getGame(gameId.value)
    Object.assign(form, { title: detail.game.title, description: detail.game.description || '', coverUrl: detail.game.coverUrl || '', visibility: detail.game.visibility, versionLabel: detail.game.versionLabel || '1.0', playKeyRequired: Boolean(detail.game.playKeyRequired) })
    Object.assign(form.config, detail.config)
    form.config.events = (detail.config.events || []).map(event => ({ ...emptyEvent(), ...event, localId: ++eventSequence }))
    form.config.aiPlayers = (detail.config.aiPlayers || []).map(ai => newAiProfile(ai))
    form.config.loanEnabled = Boolean(detail.config.loanEnabled)
    form.config.creditRulesVersion = detail.config.creditRulesVersion || 1
    form.config.creditProducts = detail.config.creditProducts?.length ? detail.config.creditProducts : defaultCreditProducts()
    await loadPlayKeys()
  } catch (e) { error.value = e.message } finally { loading.value = false }
}

onMounted(load)
</script>

<style scoped>
.editor-page { max-width: 1360px; margin: 0 auto; padding: 20px 28px 64px; }
.editor-header { min-height: 66px; display: flex; align-items: center; gap: 14px; margin-bottom: 16px; padding-bottom: 16px; border-bottom: 1px solid var(--color-line); }
.editor-heading { min-width: 0; display: grid; grid-template-columns: auto 1fr; align-items: baseline; gap: 2px 9px; }
.editor-heading > span { grid-column: 1 / -1; color: #6e7b74; font-size: 10px; font-weight: 700; }
.editor-heading h1 { font-size: 22px; }
.editor-heading small { color: var(--color-success); font-size: 11px; }
.icon-command { width: 40px; height: 40px; display: grid; place-items: center; border: 1px solid var(--color-line); border-radius: 6px; background: #fff; }
.header-actions { display: flex; gap: 8px; margin-left: auto; }
.editor-grid { display: grid; grid-template-columns: minmax(0, 1fr) 286px; gap: 20px; align-items: start; }
.editor-main { min-width: 0; background: #fff; border: 1px solid var(--color-line); border-radius: 8px; }
.editor-tabs{position:sticky;top:0;z-index:20;display:flex;gap:3px;overflow-x:auto;padding:8px 10px;border-bottom:1px solid var(--color-line);background:rgba(255,255,255,.97)}.editor-tabs button{flex:0 0 auto;min-height:38px;padding:0 14px;border-radius:5px;color:#65716a;background:transparent;font-size:12px}.editor-tabs button.active{color:#245f43;background:#eaf4ee;font-weight:700}
.form-section { padding: 26px 28px 30px; border-bottom: 1px solid var(--color-line); }
.form-section:last-child { border-bottom: 0; }
.credit-products{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:10px}.credit-product{overflow:hidden;border:1px solid var(--color-line);border-radius:7px}.credit-product>header{min-height:48px;display:flex;align-items:center;padding:9px 12px;background:#f7f9f8;border-bottom:1px solid var(--color-line)}.credit-product>header>div{display:flex;flex-direction:column}.credit-product>header span{color:#748078;font-size:9px}.credit-product>header strong{font-size:13px}.credit-product>header .switch{margin-left:auto}.credit-product>p{padding:8px 12px;color:#8c4d43;background:#fff2ef;font-size:9px;line-height:1.55}.credit-fields{grid-template-columns:repeat(2,minmax(0,1fr));padding:12px}.credit-product.risk-high{border-color:#e2b29f}.credit-product.risk-extreme{border-color:#d88f89}
.section-heading { display: flex; align-items: flex-start; gap: 10px; margin-bottom: 21px; }
.section-heading > span { min-width: 26px; height: 26px; display: grid; place-items: center; border-radius: 4px; color: #8b5725; background: #f7efe7; font-size: 10px; font-weight: 800; }
.section-heading h2 { font-size: 16px; }
.section-heading p { margin-top: 3px; color: var(--color-text-muted); font-size: 11px; line-height: 1.5; }
.setting-group { display: grid; grid-template-columns: 160px minmax(0, 1fr); gap: 22px; padding: 20px 0; border-top: 1px solid #e7ebe8; }
.setting-group-title { display: flex; flex-direction: column; gap: 4px; }
.setting-group-title strong { color: #35443c; font-size: 13px; }
.setting-group-title span { color: #818b86; font-size: 10px; line-height: 1.55; }
.setting-group .four-cols { grid-template-columns: repeat(3, minmax(0, 1fr)); }
.fundamentals-group { border-top-color: #cadfd2; }
.fields { display: grid; gap: 14px; }
.two-cols { grid-template-columns: repeat(2, minmax(0, 1fr)); }
.three-cols { grid-template-columns: repeat(3, minmax(0, 1fr)); }
.four-cols { grid-template-columns: repeat(4, minmax(0, 1fr)); }
.full { grid-column: 1 / -1; }
.code-input { font-family: ui-monospace, SFMono-Regular, Consolas, monospace; font-weight: 700; text-transform: uppercase; }
.switch { margin-left: auto; }
.switch input { position: absolute; opacity: 0; }
.switch span { width: 40px; height: 22px; display: block; border-radius: 11px; background: #c9ced4; padding: 3px; transition: .2s; }
.switch span::after { content: ''; display: block; width: 16px; height: 16px; border-radius: 50%; background: #fff; transition: .2s; }
.switch input:checked + span { background: var(--color-primary); }
.switch input:checked + span::after { transform: translateX(18px); }
.check-row { display: flex; flex-wrap: wrap; gap: 8px; }
.check-row label { display: inline-flex; align-items: center; gap: 6px; padding: 8px 10px; border: 1px solid var(--color-line); border-radius: 6px; font-size: 13px; }
.check-row input { accent-color: var(--color-primary); }
.ai-note{display:flex;align-items:flex-start;gap:7px;padding:10px 11px;color:#665b4f;background:#f8f5f1;border-radius:6px;font-size:11px;line-height:1.6}.ai-note svg{flex:0 0 auto}
.disabled-row, .no-events { display: flex; align-items: center; justify-content: center; gap: 7px; min-height: 72px; color: var(--color-text-muted); background: #f7f8f9; border-radius: 6px; font-size: 13px; }
.add-event { margin-left: auto; display: inline-flex; align-items: center; gap: 5px; padding: 7px 10px; color: #a65310; background: #fff2e4; border-radius: 6px; font-weight: 700; font-size: 12px; }
.event-tools{margin-left:auto;display:flex;gap:6px}.event-tools button{display:inline-flex;align-items:center;gap:5px;padding:7px 9px;border-radius:6px;color:#7a562f;background:#f7f2ed;font-size:12px;font-weight:700}.event-tools .add-event{margin:0;color:#a65310;background:#fff2e4}.hidden-input{display:none}.probability-note{display:flex;gap:9px;margin-bottom:14px;padding:11px 12px;border-left:3px solid #c77a2c;background:#fff8ef;color:#6c5d4c}.probability-note svg{flex:0 0 auto}.probability-note p{font-size:12px;line-height:1.6}.csv-preview{margin-bottom:14px;padding:12px;border:1px solid #cbded1;border-radius:7px;background:#f4faf6}.csv-preview header{display:flex;justify-content:space-between}.csv-preview header button{background:transparent}.csv-preview>p{margin-top:7px;color:var(--color-error);font-size:11px}.csv-preview>div{display:flex;gap:6px;flex-wrap:wrap;margin-top:8px}.csv-preview>div span{padding:4px 6px;background:#fff;border:1px solid #dae4dd;border-radius:4px;font-size:10px}.csv-preview footer{display:flex;justify-content:flex-end;gap:7px;margin-top:10px}.switch-field{display:flex;flex-direction:row;align-items:center;justify-content:space-between}.switch-field .switch{margin:0}.page-bar{display:flex;justify-content:center;gap:5px;margin-top:14px}.page-bar button{width:32px;height:30px;border:1px solid var(--color-line);border-radius:4px;background:#fff}.page-bar button.active{color:#fff;border-color:#347b57;background:#347b57}.key-manager{margin-top:18px;padding-top:18px;border-top:1px solid var(--color-line)}.key-create{display:grid;grid-template-columns:1.4fr 1fr 1fr .6fr auto;gap:8px;align-items:end}.key-help{margin-top:9px;color:var(--color-text-muted);font-size:11px}.key-list{display:grid;gap:7px;margin-top:14px}.key-list article{display:grid;grid-template-columns:minmax(0,1fr) auto auto 36px;align-items:center;gap:8px;padding:10px;border:1px solid var(--color-line);border-radius:6px}.key-list article>div{display:flex;flex-direction:column}.key-list small{color:var(--color-text-muted);font-size:10px}.key-list article>span{padding:3px 6px;border-radius:4px;font-size:10px}.key-list article>span.active{color:#267049;background:#eaf5ee}.key-list article>span.disabled{color:#8c5955;background:#f7eceb}.icon-danger{width:34px;height:34px;display:grid;place-items:center;color:#aa443e;background:transparent}
.probability-meter { margin-bottom: 14px; padding: 12px; background: #f5f7f6; border-radius: 6px; }
.probability-meter > div:first-child { display: flex; justify-content: space-between; font-size: 12px; }
.probability-meter strong { color: #26754b; }
.probability-meter.invalid strong { color: var(--color-error); }
.meter-track { height: 5px; margin: 7px 0 4px; overflow: hidden; background: #dfe4e1; border-radius: 3px; }
.meter-track span { display: block; height: 100%; background: #3b8f63; }
.probability-meter.invalid .meter-track span { background: var(--color-error); }
.probability-meter small { color: var(--color-text-muted); }
.event-list { display: flex; flex-direction: column; gap: 10px; }
.event-item { border: 1px solid var(--color-line); border-radius: 7px; }
.event-item > header { min-height: 42px; display: flex; align-items: center; gap: 12px; padding: 7px 12px; background: #f8f9fa; border-bottom: 1px solid var(--color-line); font-size: 13px; font-weight: 700; }
.event-item > header button { margin-left: auto; color: var(--color-error); background: transparent; }
.event-enabled { display: flex; align-items: center; gap: 5px; margin-left: auto; color: var(--color-text-muted); font-weight: 500; }
.event-item > header .event-enabled + button { margin-left: 0; }
.event-fields { grid-template-columns: repeat(5, minmax(0, 1fr)); padding: 14px; }
.name-field { grid-column: span 2; }.description-field { grid-column: span 3; }
.editor-summary { position: sticky; top: 76px; overflow: hidden; background: #fff; border: 1px solid var(--color-line); border-radius: 8px; }
.editor-summary > header { min-height: 58px; display: flex; align-items: center; justify-content: space-between; padding: 12px 16px; border-bottom: 1px solid var(--color-line); background: #f8faf9; }
.editor-summary > header div { display: flex; flex-direction: column; gap: 2px; }
.editor-summary > header span { color: #7c8781; font-size: 9px; }
.editor-summary > header strong { padding: 4px 7px; border-radius: 4px; color: #58675f; background: #e9eeeb; font-size: 9px; }
.editor-summary h2 { font-size: 15px; }
.stock-preview { display: flex; flex-direction: column; gap: 2px; margin: 14px 16px 12px; padding: 15px; color: #fff; background: #33463d; border-radius: 6px; }
.stock-preview span { font-family: ui-monospace, monospace; color: #a9e3bf; font-size: 12px; font-weight: 700; }
.stock-preview strong { margin-top: 7px; overflow-wrap: anywhere; font-size: 16px; }
.stock-preview small { color: #cad7d0; }
.summary-metrics { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); margin: 0 16px 15px; border-top: 1px solid var(--color-line); border-left: 1px solid var(--color-line); }
.summary-metrics > div { min-width: 0; padding: 9px; border-right: 1px solid var(--color-line); border-bottom: 1px solid var(--color-line); }
.summary-metrics dt { color: #87918c; font-size: 9px; }
.summary-metrics dd { margin-top: 3px; overflow: hidden; color: #33423a; font-size: 11px; font-weight: 700; text-overflow: ellipsis; white-space: nowrap; }
.editor-summary h3 { margin: 0 16px 9px; color: #58665f; font-size: 11px; }
.editor-summary ul { list-style: none; display: flex; flex-direction: column; gap: 8px; padding: 0 16px 16px; }
.editor-summary li { display: flex; align-items: center; gap: 7px; color: #9ba1a9; font-size: 12px; }
.editor-summary li.ok { color: #2e7950; }
.editor-load-failure{min-height:420px;display:flex;flex-direction:column;align-items:center;justify-content:center;gap:10px;padding:30px;text-align:center;color:var(--color-text-muted)}.editor-load-failure>svg{color:var(--color-error)}.editor-load-failure h2{color:var(--color-text);font-size:19px}.editor-load-failure p{max-width:520px;font-size:13px;line-height:1.6}.editor-load-failure>div{display:flex;gap:8px;margin-top:8px}
.phase-time-settings { margin-bottom: 16px; padding: 18px; border: 1px solid #dfe6e2; border-radius: 7px; background: #f8faf9; }
.phase-time-settings > .setting-group-title { margin-bottom: 14px; }
.inline-credit-time { min-height: 64px; display: flex; align-items: center; gap: 10px; grid-column: span 2; padding: 10px 12px; border: 1px solid #c9ddd1; border-radius: 6px; color: #2d6e4c; background: #edf7f1; }
.inline-credit-time > span { min-width: 0; display: flex; flex-direction: column; gap: 3px; }
.inline-credit-time strong { font-size: 12px; }
.inline-credit-time small { color: #61766a; font-size: 10px; line-height: 1.45; }
.ai-config{display:flex;flex-direction:column;gap:13px}.ai-toolbar{display:flex;align-items:end;justify-content:space-between;gap:12px}.ai-toolbar .form-group{width:min(260px,100%)}.add-ai{height:39px;display:inline-flex;align-items:center;gap:6px;padding:0 12px;border:1px solid #b8d4c3;border-radius:6px;color:#216f49;background:#eff8f3;font-size:12px;font-weight:700}.ai-player-list{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:10px}.ai-player-card{overflow:hidden;border:1px solid var(--color-line);border-radius:7px}.ai-player-card>header{min-height:48px;display:flex;align-items:center;padding:8px 12px;background:#f7f9f8;border-bottom:1px solid var(--color-line)}.ai-player-card>header div{display:flex;flex-direction:column}.ai-player-card>header span{color:#728078;font-size:10px}.ai-player-card>header strong{font-size:13px}.ai-player-card>header button{margin-left:auto;color:#a64640;background:transparent}.ai-player-card>header button:disabled{opacity:.3}.ai-fields{grid-template-columns:repeat(2,minmax(0,1fr));padding:13px}.strategy-field{grid-column:1/-1}.strategy-field textarea{min-height:74px}.event-item{min-height:70px;display:grid;grid-template-columns:minmax(0,1.5fr) minmax(210px,1fr) auto 34px 34px;align-items:center;gap:10px;padding:10px 12px}.event-summary{min-width:0;display:flex;flex-direction:column;gap:2px}.event-summary>span{color:#8a7560;font-size:9px}.event-summary strong{font-size:13px}.event-summary small{overflow:hidden;color:#747f79;font-size:10px;text-overflow:ellipsis;white-space:nowrap}.event-stats{display:flex;flex-wrap:wrap;gap:5px}.event-stats span{padding:4px 6px;border-radius:4px;color:#5e6c64;background:#f1f4f2;font-size:9px}.event-item>button{display:grid;place-items:center;background:transparent;color:#5e7066}.event-item>.delete-event{color:var(--color-error)}.modal-mask{position:fixed;inset:0;z-index:900;display:grid;place-items:center;padding:18px;background:rgba(26,36,31,.55);backdrop-filter:blur(3px)}.event-dialog{width:min(650px,100%);max-height:90vh;overflow:auto;border-radius:8px;background:#fff;box-shadow:0 22px 65px rgba(20,31,25,.28)}.event-dialog>header{display:flex;align-items:center;padding:18px 20px;border-bottom:1px solid var(--color-line)}.event-dialog>header span{color:#a66325;font-size:10px;font-weight:700}.event-dialog>header h2{font-size:18px}.event-dialog>header button{width:34px;height:34px;display:grid;place-items:center;margin-left:auto;background:transparent}.event-dialog-fields{grid-template-columns:repeat(2,minmax(0,1fr));padding:20px}.dialog-enabled{align-self:end;min-height:39px}.event-dialog>footer{display:flex;justify-content:flex-end;gap:8px;padding:14px 20px;border-top:1px solid var(--color-line);background:#fafbfa}
.style-summary{color:#718079;font-size:10px;line-height:1.45}.ai-add-dialog{width:min(720px,100%)}.ai-style-picker{display:grid;grid-template-columns:repeat(5,minmax(0,1fr));gap:7px;padding:16px 20px 0}.ai-style-picker button{min-height:78px;display:flex;flex-direction:column;align-items:flex-start;gap:4px;padding:10px;border:1px solid var(--color-line);border-radius:6px;color:#58665e;background:#fff;text-align:left}.ai-style-picker button.active{color:#1f6845;border-color:#72aa8a;background:#edf7f1;box-shadow:inset 0 0 0 1px #72aa8a}.ai-style-picker small{font-size:9px;line-height:1.45}.ai-dialog-fields textarea{min-height:92px}
@media (max-width: 960px) { .editor-grid { grid-template-columns: 1fr; }.editor-summary { position: static; }.four-cols,.setting-group .four-cols { grid-template-columns: repeat(2, minmax(0, 1fr)); }.event-fields { grid-template-columns: repeat(3, minmax(0, 1fr)); } }
@media (max-width: 768px) { .editor-page { padding: 12px 12px 90px; }.editor-header { align-items: flex-start; flex-wrap: wrap; }.editor-heading{grid-template-columns:1fr}.editor-heading small{grid-column:1}.header-actions { width: 100%; }.header-actions .btn { flex: 1; }.form-section { padding: 18px 14px; }.setting-group{grid-template-columns:1fr;gap:12px;padding:17px 0}.two-cols,.three-cols,.four-cols,.setting-group .four-cols,.event-fields,.ai-player-list,.ai-fields,.event-dialog-fields,.credit-products,.credit-fields { grid-template-columns: 1fr; }.inline-credit-time{grid-column:auto}.phase-time-settings{padding:14px}.name-field,.description-field,.strategy-field { grid-column: auto; }.ai-toolbar{align-items:stretch;flex-direction:column}.ai-toolbar .form-group{width:100%}.add-ai{justify-content:center}.event-item{grid-template-columns:minmax(0,1fr) 34px 34px}.event-stats{grid-column:1/-1;grid-row:2}.event-enabled{grid-column:1}.event-dialog{max-height:94vh}.event-dialog-fields{padding:15px}.event-dialog>header,.event-dialog>footer{padding:13px 15px} }
@media (max-width: 768px) { .ai-style-picker{grid-template-columns:repeat(2,minmax(0,1fr));padding:12px 15px 0}.ai-style-picker button:last-child{grid-column:1/-1;min-height:64px} }
@media (max-width: 768px) { .key-create{grid-template-columns:1fr}.key-list article{grid-template-columns:1fr auto 36px}.key-list article>.btn{grid-column:1/-1}.editor-main{overflow:visible} }
.event-dialog>header{position:sticky;z-index:2;top:0;background:#fff}.event-dialog>footer{position:sticky;z-index:2;bottom:0}
</style>
