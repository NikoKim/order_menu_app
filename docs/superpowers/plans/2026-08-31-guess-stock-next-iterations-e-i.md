# 猜股票下一阶段 E-I 详细执行计划

> 编写日期：2026-08-31  
> 适用范围：猜股票市场 V2、事件系统、AI Agent、贷款系统、Web 游玩体验和批量校准  
> 执行原则：先修公平性和校准可信度，再优化节奏与可解释性，最后增加基本面、事件故事线和高级竞价玩法

## 1. 当前基线

现有系统已经具备：

- V1/V2 价格版本隔离；新游戏默认 V2，旧版本保持原规则。
- 统一 `TrendState + VolatilityState`、趋势年龄、状态年龄和滞后切换。
- 趋势衰减、趋势疲劳、EMA20/EMA50 中枢回归、动态噪声和订单流影响。
- 事件有效性评估、跨阶段切片、同类饱和、冷却和反趋势重大意外修正。
- AI Thesis、Evidence Gate、FAST/STRONG 路由、结构化失效条件、目标敞口、仓位步长和反向交易冷却。
- 多银行贷款、独立贷款合同、利息、到期、延期、LTV、强平、违约和净资产排名。
- 固定种子市场模拟、AI 本地行为模拟、多人房间、复盘和排行榜。

当前自动检查基线：猜股票模块 74 个测试通过，Web 生产构建通过。

## 2. 必须先解决的问题

### 2.1 AI 公开信息边界不公平

当前模型决策包可以在价格揭晓前取得：

- 尚未应用的精确事件冲击 `currentImpact` 和 `remainingImpact`。
- 真人玩家当前阶段尚未成交的买卖数量。

这些信息真人只能在结算后看到。模型 AI 因此可能提前知道下一阶段价格方向、幅度和真人意图。所有后续 AI 难度、排行榜和对抗测试在修复前都不具备可信公平性。

### 2.2 波动状态阈值与 V2 实际分布不匹配

V2 全市场当前实现波动约为 0.89%，但 `HIGH/EXTREME` 使用接近 4%～6.6% 的阈值。结果可能是大部分回合停留在 `LOW/NORMAL`，使高波动事件适配、AI 证据和失效条件长期不触发。

### 2.3 AI 校准没有覆盖生产决策链

当前 AI 批量模拟使用独立高斯价格和本地 `AiTradingPolicy`，没有实际经过：

```text
MarketPriceEngine
-> EventImpactSchedule
-> AiMarketSnapshot
-> Evidence Gate
-> Thesis
-> TargetPositionExecutor
-> CreditService
```

因此现有交易次数可以作为行为烟雾测试，但不能证明生产 AI 的收益、风险和贷款策略合理。

### 2.4 Hard AI 没有形成稳定的能力优势

当前固定种子报告中，Hard 的交易次数和换手率更高，但平均收益和 Sharpe-like 没有稳定高于 Normal。难度差异需要从“活跃程度”转向“公开信息利用能力和风险管理质量”。

### 2.5 贷款功能增加了过多必经阶段

开启贷款后每回合都会进入贷款阶段；存在债务时还会进入还贷阶段。长局和多人局可能把大量时间花在无操作的银行确认上，削弱市场决策节奏。

## 3. 总体实施顺序

| 迭代 | 目标 | 优先级 | 预计工作量（单人） |
| --- | --- | --- | ---: |
| E | 修复信息公平性并重新校准市场状态 | 必须 | 4～6 个开发日 |
| F | 建立生产级 AI/贷款对抗与真正的难度分层 | 必须 | 7～10 个开发日 |
| G | 压缩贷款阶段，增加 AI 与市场可解释复盘 | 高 | 5～8 个开发日 |
| H | 公司基本面、事件链、财报和研究玩法 | 核心新玩法 | 10～15 个开发日 |
| I | 双向批量竞价、每日挑战和赛季玩法 | 扩展 | 10～14 个开发日 |

总计约 36～53 个开发日，不包含真实模型付费批测、产品体验观察和原生客户端开发。

每个迭代必须单独完成迁移、测试、模拟报告和人工体验验收。上一阶段未通过时，不进入下一阶段。

---

## 4. 迭代 E：公平信息与市场状态校准

### 4.1 目标

1. 模型 AI 与真人在同一时点只能看到相同的公开市场信息。
2. 四档波动状态在常规 V2 市场中都具有可观察意义。
3. 价格分解字段的语义准确，不把限幅修正伪装成 regime 贡献。
4. 事件发生概率在编辑器中拥有可理解、可复算的语义。

### 4.2 E1：建立公开信息契约

新增两个明确分离的数据结构：

```text
InternalPricingContext
  仅价格、事件和结算服务使用
  可包含精确事件切片、随机种子、未结算订单

PublicDecisionSnapshot
  真人响应和 AI 决策共同使用
  只包含当前时点已经公开的信息
```

公开信息矩阵：

| 阶段 | 可公开 | 必须隐藏 |
| --- | --- | --- |
| 开盘交易前 | 事件名称和公开元数据、历史价格、历史订单流 | 本阶段事件是否有效、精确冲击、真人待成交订单、AI 待成交订单 |
| 开盘结算后 | 已实现开盘事件影响、开盘订单流、开盘价 | 尚未释放的精确未来事件切片 |
| 收盘交易前 | 已实现开盘信息、收盘事件公开元数据 | 收盘事件精确影响、当前真人/AI 待成交卖单 |
| 收盘结算后 | 本回合全部价格分解、事件结果和订单流 | 无 |

AI 可以取得事件残余信息，但只能是由玩家也能看到的信息推导的粗粒度摘要：

```json
{
  "residualSignal": "NONE|LOW|MEDIUM|HIGH",
  "remainingStages": 0,
  "exactImpact": null
}
```

价格结算后如产品决定公开事件切片公式，真人和 AI 才能共同取得可推导的残余区间；仍不直接返回服务器内部精确随机值。

后端任务：

- 新增 `PublicDecisionSnapshot` 和 `PublicEventDigest`。
- `GuessStockSessionService.createFrozenAiMarketSnapshot` 不再查询真人当前 pending orders。
- 订单流字段改为上一已结算阶段的数值或玩家界面同时可见的粗粒度档位。
- 模型 Prompt 删除 `currentImpact/remainingImpact` 精确值。
- `AiThesisConditionEvaluator` 的 `EVENT_RESIDUAL_BELOW` 改为使用公开残余档位，或在旧 Thesis 中继续兼容但不为新观点生成该精确条件。
- 提升 `AI_PROMPT_VERSION`，避免旧缓存复用。

测试：

- `PublicDecisionSnapshotTest`：每个阶段逐字段验证公开/隐藏边界。
- `ModelAiDecisionServiceTest`：Prompt 不包含未揭晓事件精确值。
- `GuessStockSessionServiceInformationBoundaryTest`：真人修改 pending order 不改变同阶段 AI 的公共快照。
- API 响应与 AI 快照进行同源字段对照测试。

### 4.3 E2：重新校准波动状态

把配置的“最大自然波动范围”和统计意义上的“实现波动”分开。新增：

```text
expectedRealizedVolatility
normalizedVolatility = realizedVolatility / expectedRealizedVolatility
```

V2 的初始 `expectedRealizedVolatility` 从开盘/收盘噪声预算推导，而不是直接等于 `baseVolatilityPercent`。历史达到 12 回合后，可与 EWMA 实际波动进行有限混合，但不得完全使用当前样本分位数造成状态自适应漂移。

初始校准目标，仅用于固定测试场景：

- LOW：15%～35% 回合。
- NORMAL：40%～70% 回合。
- HIGH：8%～25% 回合。
- EXTREME：0.5%～6% 回合。
- 单次冲击后波动状态逐步恢复，不允许下一回合立即从 EXTREME 降为 LOW。

扩展 `MarketSimulationRunner.Report`：

- 四档波动状态占比。
- 波动状态切换次数。
- 冲击后维持 HIGH/EXTREME 的平均阶段数。
- 状态一回合来回翻转比例。

测试：

- `MarketStateCalculatorTest` 增加波动阈值、持续性和恢复路径。
- `CalibrationSuiteTest` 固化波动状态分布区间。
- 不对单一路径断言状态比例，只对固定种子批量分布断言。

### 4.4 E3：修正价格分解审计

当前 `regimeImpactPct` 实际为总收益限幅后的残差，而价格公式中的 regime 值为 0。改为：

```text
rawTotalImpactPct
clampAdjustmentPct
totalImpactPct
```

处理方式：

- 保留旧 `regime_change_percent` 读取兼容，不再写入限幅残差。
- 新增 `clamp_adjustment_percent`。
- 如果本期仍不启用独立 regime bias，则 `regime_change_percent=0`。
- 前端只在发生限幅时显示“波动保护修正”，避免正常结算信息过载。

数据库建议：

- `V41__guess_stock_public_snapshot_and_price_audit.sql`
- 对应 rollback。

### 4.5 E4：明确事件候选概率

为 `EventConfig` 增加可选 `candidateWeight`：

- 旧配置缺失时默认为 1，行为兼容。
- 候选按权重确定，不再全部等权。
- `occurrenceProbability` 继续表示候选选中后真正发生的概率。
- 增加游戏级 `baseEventFrequency` 作为可选总频率控制；第一阶段可只实现权重，避免同时改变过多参数。

编辑器和 CSV 必须明确展示：候选权重不是百分比，发生概率也不会随事件数量相加。

### 4.6 迭代 E 验收门

- 价格揭晓前，AI Prompt 中不存在精确事件冲击和真人 pending order。
- AI 和真人公共快照字段通过自动对照测试。
- 常规全市场 10,000 局中四档波动状态均符合固定基线。
- 价格组件之和满足 `raw + clampAdjustment = total`。
- V1、已发布 V2 和进行中场次可继续读取。
- 猜股票模块测试、前端构建、六场景完整校准全部通过。

---

## 5. 迭代 F：生产级 AI/贷款对抗与难度重构

### 5.1 目标

1. 用与生产一致的纯函数/内存状态机验证 AI，而不是独立高斯简化模型。
2. Easy、Normal、Hard 的差异来自公开信息利用和风险管理，不来自隐藏信息或单纯高频。
3. 贷款产品和 AI 自动融资经过批量风险校准。
4. 能统计真实模型调用成本、耗时、缓存和降级。

### 5.2 F1：抽象生产级对抗内核

新增：

```text
simulation/GuessStockBattleSimulator
simulation/SimulationPortfolio
simulation/SimulationCreditBook
simulation/SimulationEventBook
simulation/StrategyAgent
simulation/SimulationReportWriter
```

模拟器直接复用：

- `MarketStateCalculator`
- `MarketPriceEngine`
- `GuessStockEventSelector`
- `GuessStockEventImpactEngine`
- `EventImpactSchedule`
- `AiEvidenceDetector`
- `AiThesisConditionEvaluator`
- `AiDecisionValidator`
- `AiTargetPositionExecutor`
- 四个信用纯计算引擎

服务层数据库操作与纯业务计算之间如有耦合，先抽出纯函数，不在测试中模拟整套 Mapper。

### 5.3 F2：增加基准策略

至少提供：

- `CASH_ONLY`：始终持有现金。
- `BUY_AND_HOLD`：第一回合买入后持有。
- `DCA`：固定间隔定投。
- `EVENT_FOLLOW`：只响应已确认高相关事件。
- `MEAN_REVERSION`：只在偏离和跌势减速同时成立时交易。
- `TREND_FOLLOW`：多周期同向才参与。
- `THESIS_AGENT`：生产 Thesis/Evidence/Executor 链。

同一场对战共享相同市场种子，不允许每个 Agent 各自生成行情。

### 5.4 F3：难度能力模型

新增 `AiDifficultyPolicy`，集中定义差异：

| 能力 | Easy | Normal | Hard |
| --- | --- | --- | --- |
| 公开历史长度 | 4～8 回合 | 20 回合 | 50 回合压缩特征 |
| 状态信息 | 粗粒度 | 完整公开状态 | 完整状态与跨周期一致性 |
| 复核节奏 | 较慢 | 标准 | 证据驱动，不固定高频 |
| 模型路由 | FAST 为主 | FAST+必要升级 | 复杂状态优先 STRONG |
| 贷款决策 | 默认不用或仅稳健银行 | 标准额度 | 比较利息、期限和强平概率 |
| 执行误差 | 确定性轻微目标偏差 | 无额外误差 | 无额外误差 |

Easy 的弱化必须是可复算且对玩家可说明的，不使用完全随机乱买。Hard 不获得任何真人不可见数据。

建议验收指标：

- 使用至少 5,000 个配对市场种子。
- Hard 对 Normal 的风险调整得分胜率至少 55%。
- Normal 对 Easy 至少 55%。
- Easy/Normal/Hard 每 100 回合交易次数仍约为 5～15、8～20、10～25。
- Hard 的优势不能主要来自更高平均杠杆或更多交易。
- 任一难度都不得稳定击败“知道未来事件”的作弊基准；作弊基准仅用于检测信息泄漏，不参与正式玩法。

### 5.5 F4：贷款专项校准

场景矩阵：

- 无事件横盘。
- 缓慢上涨。
- 缓慢下跌。
- 单次重大下跌。
- 连续高波动。
- 接近到期时发生冲击。
- 多银行连续借款。

统计：

- 借款参与率、平均敞口、利息拖累。
- WARNING/BLOCKED/LIQUIDATION/DEFAULTED 占比。
- 平均强平股数、强平后的恢复 LTV。
- 各银行选择率和实际收益贡献。
- 最大杠杆策略相对无杠杆基准的收益与尾部损失。

产品目标：高风险银行应提高收益方差，而不是无条件提高平均收益；横盘市场中利息应让无意义杠杆明显劣于不借款。

### 5.6 F5：真实模型遥测与预算

扩展 AI 任务审计：

- `provider`
- `model`
- `latency_ms`
- `prompt_tokens`
- `completion_tokens`
- `cache_hit`
- `fallback_reason`
- `http_status`
- `attempt_count`

新增单局限制：

- 最大模型调用次数。
- 最大 Token。
- 最大累计失败次数。
- 超限后沿用未失效观点或本地策略，不阻塞场次。

数据库建议：

- `V42__guess_stock_ai_telemetry_and_budget.sql`
- 对应 rollback。

真实供应商测试分两层：

1. 默认 CI 使用录制且脱敏的模型 JSON 进行 trace replay。
2. 手工 profile 使用真实凭据运行小规模 50～200 局，只用于质量、Token 和耗时评估，不进入普通 CI。

### 5.7 迭代 F 验收门

- 生产级模拟器复用真实价格、事件、AI 执行和信用纯引擎。
- 三档 AI 在配对种子中形成稳定而非绝对的能力梯度。
- 贷款四档风险状态和强平恢复均有批量回归基线。
- AI 调用耗时、Token、缓存和降级可查询。
- 场次达到预算后仍可继续，不因模型服务阻塞。

---

## 6. 迭代 G：节奏压缩与可解释复盘

### 6.1 目标

1. 贷款不再让每回合增加两个必经等待节点。
2. 玩家能理解事件、市场状态、AI 观点和贷款风险如何共同影响结果。
3. 复盘信息在结算后公开，不泄漏当前阶段订单。

### 6.2 G1：贷款交互模式版本化

新增配置：

```text
creditInteractionMode = PHASED | INLINE
```

- 旧已发布游戏保持 `PHASED`。
- 新游戏默认 `INLINE`。
- INLINE 模式中，银行入口在开盘交易阶段始终可用，借款/还款是侧边操作。
- 只有贷款到期、风险达到 WARNING 以上、发生强平或最终回合时才进入专门银行检查节点。
- 多人局没有人需要处理银行事项时自动跳过，不创建 ready gate。

需要处理的竞态：借款、改单、阶段结束必须使用同一参与者锁顺序，防止借款到账与订单冻结交叉造成负现金。

### 6.3 G2：统一市场时间线

新增回合时间线条目：

- 公开事件。
- 开盘/收盘价格变化。
- 趋势与波动状态变化。
- AI Thesis 创建、维持、调整、替换、关闭。
- 借款、计息、到期、延期、强平。
- 玩家自己的成交。

时间线默认只展示一句结论，展开后显示详细价格分解。不要把每个指标都长期铺在主盘面。

### 6.4 G3：AI 对手卡和结算后解释

交易阶段只显示 AI 是否完成，不显示具体方向和订单。阶段结算后可展示：

- 当前观点方向、置信度和时间周期。
- requested target、step target、实际敞口。
- 触发观点变化的最多 3 条证据。
- 执行被拦截的原因。
- 使用的银行、债务和风险状态。

局末增加 AI 复盘：最佳决策、最大错误、观点替换次数、反向交易次数、强平和利息成本。

### 6.5 G4：游戏长度与场景预设

提供经过校准的预设，而不是让创建者面对几十个参数：

- 快速局：12 回合、无贷款或单一稳健银行。
- 标准局：30 回合、完整事件和三档银行。
- 长线局：80 回合、较低事件密度、财报和事件链预留。
- 高波动挑战：20 回合、较高波动和重大事件。
- 信用危机：20～30 回合、贷款可用且尾部风险更高。

高级参数仍保留，但默认收起。

### 6.6 迭代 G 验收门

- INLINE 贷款局在无到期/风险时不增加额外阶段。
- 30 回合多人贷款局的平均确认节点显著低于 PHASED 模式。
- 当前阶段 AI 订单和观点调整在结算前不可见。
- 时间线能复原每回合事件、价格、成交、AI 和信用变化。
- 手机端银行、时间线和订单面板不重叠。

---

## 7. 迭代 H：公司基本面、事件链、财报与研究

### 7.1 目标

1. 市场形成持续的经营故事，而不是只围绕价格 EMA 运动。
2. 玩家可以根据公开基本面和新闻因果做中期判断。
3. 加入有限、可选择的研究行动，提高每回合参与感。

### 7.2 H1：基本面状态

首版只保留 5 个易理解指标：

```text
demandIndex          需求
orderIndex           订单
inventoryIndex       库存
marginIndex          利润率
cashFlowIndex        现金流
```

可派生：

```text
fundamentalScore
fairValueIndex
financialRisk
```

指标采用 0～200 的相对指数，100 为开局基准。事件改变指标，指标以有限速度传导到财报和价格，不直接等同于价格涨跌。

新增配置：

```text
fundamentalEnabled
reportIntervalRounds
initialFundamentals
fundamentalSensitivity
```

新增表建议：

- `guess_stock_fundamental_snapshots`
- `guess_stock_financial_reports`
- `V43__guess_stock_fundamentals.sql`

### 7.3 H2：价格引擎 V3

不要改变已发布 V2。新增 `pricingEngineVersion=V3`：

```text
V2 components
+ boundedFundamentalGap
+ disclosedReportSurprise
```

基本面影响必须满足：

- 只提供缓慢、有限的价格锚，不生成永久固定漂移。
- 市场已有预期时，财报公布只反映预期差。
- 强趋势仍有疲劳和均值回归。
- 基本面恶化不保证每回合下跌，改善也不保证上涨。

新增价格分解字段：

- `fundamental_change_percent`
- `report_surprise_percent`

### 7.4 H3：事件链

扩展事件配置：

```text
chainKey
chainStep
parentTags
followUpTags
followUpProbabilityBoost
maxChainDepth
fundamentalEffects
```

示例：

```text
需求下滑
-> 订单减少概率提高
-> 库存积压概率提高
-> 利润率下滑概率提高
```

规则：

- 后续事件只提高概率，不保证发生。
- 每条链有最大深度和冷却。
- 互相矛盾的链可被重大反向事件中断。
- 链状态必须落库，暂停恢复可复算。
- 新闻时间线显示“可能相关”，不提前告诉玩家后续事件。

新增表建议：

- `guess_stock_event_chain_states`
- `V44__guess_stock_event_chains.sql`

### 7.5 H4：定期财报

每 `reportIntervalRounds` 回合生成公开财报：

- 当前五项指标。
- 相对上次财报的变化。
- 市场预期区间。
- 实际与预期差。

财报生成前，玩家只能通过已公开事件和研究结果估计；AI 也只能读取相同公开信息。

### 7.6 H5：有限研究点

每名参与者每局拥有固定研究点，例如 3 点。可选择：

- 核验传闻：提高可信度判断精度。
- 需求调查：查看需求指标区间。
- 财报预览：查看某一指标相对预期的方向。
- 流动性观察：查看上一阶段订单流档位。

研究结果必须是带误差的区间或方向，不返回服务器内部精确未来值。

AI 必须使用同一研究预算。模型输出可增加可选字段：

```json
{"research_action":"NONE|VERIFY_EVENT|SURVEY_DEMAND|PREVIEW_REPORT|CHECK_LIQUIDITY"}
```

后端决定行动是否合法并扣除点数；研究和交易分开审计。

新增表建议：

- `guess_stock_research_accounts`
- `guess_stock_research_actions`
- `V45__guess_stock_research.sql`

### 7.7 迭代 H 验收门

- 同种子基本面、财报和事件链可复算。
- 无事件时基本面不会自行单向漂移。
- 只有利好/利空基本面场景的方向符合预期，但不存在必涨必跌。
- 暂停恢复不会重复生成财报、链事件或研究结果。
- AI 与真人研究预算、结果精度和公开信息一致。
- V1/V2 历史不受 V3 影响。

---

## 8. 迭代 I：专业竞价、每日挑战与长期目标

### 8.1 目标

1. 为熟练玩家提供比“开盘只买、收盘只卖”更自由的交易模式。
2. 用固定种子挑战和赛季目标提高重复游玩价值。
3. 保留经典模式，避免新玩家被复杂订单系统劝退。

### 8.2 I1：交易模式版本化

新增：

```text
tradingMode = CLASSIC | TARGET_AUCTION
```

- CLASSIC：保持现有开盘买、收盘卖。
- TARGET_AUCTION：两个阶段都提交目标敞口，后端根据当前资产和目标推导 BUY/SELL。

TARGET_AUCTION 使用统一批量成交：

- 所有人先提交，阶段结束后统一结算。
- 价格由外部市场收益和净订单不平衡共同形成。
- 流动性不足时按规则比例部分成交。
- 同阶段使用统一成交价，避免提交顺序优势。
- 强平单单独标记但进入同一卖盘影响。

### 8.3 I2：交易摩擦

只在 TARGET_AUCTION 或新规则版本启用：

- 手续费默认 0.05%。
- 流动性滑点或价差默认 0.02%～0.15%。
- AI 决策包公开同样的成本规则。
- 执行器在目标差不足以覆盖成本时 HOLD。

交易成本必须进入净资产、排行榜和复盘，不允许只显示不扣除。

数据库建议：

- 扩展 trade 的 `fee_amount`、`slippage_percent`。
- `V46__guess_stock_auction_and_costs.sql`

### 8.4 I3：每日固定种子挑战

每日挑战包含不可变：

- 游戏版本。
- 场景种子。
- 回合数和时间限制。
- 是否允许 AI、贷款和研究。
- 计分规则。

排行榜同时展示：

- 净收益率。
- 最大回撤。
- 强平次数。
- 利息成本。
- 预测/研究准确率。

主排名建议仍以净收益率为主；风险控制、无强平、低换手作为独立徽章，避免复杂综合分掩盖结果。

新增表建议：

- `guess_stock_challenges`
- `guess_stock_challenge_entries`
- `V47__guess_stock_daily_challenges.sql`

### 8.5 I4：赛季和成就

首批只做可验证目标：

- 无贷款完成正收益。
- 最大回撤低于指定值。
- 高波动状态下保持盈利。
- 使用贷款但从未进入 WARNING。
- 正确识别完整事件链。
- 连续若干日完成每日挑战。

不要首批加入付费增益、随机道具或改变市场规则的永久属性。

### 8.6 迭代 I 验收门

- CLASSIC 历史和进行中场次完全不变。
- TARGET_AUCTION 与订单提交顺序无关。
- 所有成交、费用、滑点和订单流贡献可对账。
- 每日挑战同版本同种子产生相同行情。
- 重复提交不能重复计分。
- 桌面和手机端均可在一个交易面板内清晰表达目标敞口与预估成本。

---

## 9. 灰度和兼容策略

### 9.1 版本隔离

- 已发布游戏版本不可变。
- V1、V2 价格规则不得追溯升级。
- V3 基本面和 TARGET_AUCTION 只对新发布版本生效。
- 贷款 `PHASED` 旧版本不自动改为 `INLINE`。
- JSON 配置所有新字段必须提供安全默认值。

### 9.2 功能开关

建议开关：

```text
guessStock.publicSnapshotV2
guessStock.volatilityCalibrationV2
guessStock.aiDifficultyV2
guessStock.inlineCredit
guessStock.fundamentals
guessStock.research
guessStock.targetAuction
guessStock.dailyChallenge
```

先对内部测试游戏开启，再对新建游戏默认开放；旧游戏只在重新发布新版本时选择升级。

### 9.3 线上观测

每个规则版本至少观察：

- 收益、回撤、单阶段 P99、趋势/波动状态占比。
- 事件类型、方向、链长度和组件贡献。
- 玩家交易次数、持有时间、订单取消率。
- 借款率、平均敞口、利息、强平和违约。
- AI 调用数、Token、延迟、缓存、失败和降级。
- 每阶段实际停留时间和多人等待时间。

## 10. 每阶段通用自检流程

1. 运行新增单元测试。
2. 运行全部猜股票模块测试。
3. 运行前端生产构建。
4. 运行固定种子小规模校准，确认开发期间快速反馈。
5. 运行完整 10,000 局市场分布和 AI/信用对抗。
6. 人工完成：单人、单人 AI、多人、多人 AI、贷款开关、暂停恢复和最后一回合结算。
7. 检查旧 V1/V2 已发布配置和旧数据库记录可读取。
8. 输出本阶段报告；未达到验收门则停止进入下一迭代。

建议命令：

```powershell
cd E:\order_menu_app\backend
mvn -q "-Dtest=com.order.menu.module.entertainment.mode.guessstock.**" test
mvn -q "-Dcalibration.full=true" "-Dtest=com.order.menu.module.entertainment.mode.guessstock.simulation.CalibrationSuiteTest" test

cd E:\order_menu_app\moments_web_frontend
npm run build
npm run qa:entertainment
npm run qa:entertainment-room
```

## 11. 暂不进入近期开发的功能

- 做空、期权、期货和复杂衍生品。
- 更多银行数量或银行间随机促销。
- 真实证券行情接入。
- 付费能力、永久数值天赋和影响公平性的随机道具。
- 使用未来信息训练或强化 AI。
- 为了制造戏剧性而加入“连续跌 N 回合必涨”等硬反转。

原因：现有市场、AI、贷款和多人规则已经具有较高复杂度。先让信息公平、节奏顺畅、故事可理解，带来的游戏体验收益会显著高于继续扩展金融工具。

## 12. 推荐首个实施范围

下一次实际开发只启动迭代 E，不同时实施基本面或竞价：

1. `PublicDecisionSnapshot` 与信息可见性矩阵。
2. 移除 AI 的精确未揭晓事件冲击和真人 pending orders。
3. 波动状态归一化与分布报告。
4. `clampAdjustmentPct` 审计。
5. 事件候选权重及编辑器说明。
6. V41、单元测试、完整校准和兼容性回归。

这是后续所有 AI 难度、排行榜和新玩法可信的前置条件。
