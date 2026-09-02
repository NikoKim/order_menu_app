# 猜股票市场趋势与 AI 可执行优化方案

> 编写日期：2026-08-30  
> 适用范围：`backend` 猜股票价格引擎、事件引擎、AI 决策与执行层，必要时同步 `moments_web_frontend` 配置和复盘展示  
> 核心目标：先证明并修正市场分布偏差，再让 AI 在统一、稳定、可解释的市场状态上决策；不通过“强制反弹”或“后端偷偷止损”制造表面合理性

## 1. 现状结论

参考建议的方向基本正确，但不能直接按“趋势项持续拖累价格”下结论。当前实现中，价格公式并没有独立的 `trendImpact`：

```text
开盘 = 前收 × exp(开盘基础扰动 + 开盘事件影响 + 买单影响)
收盘 = 开盘 × exp(收盘基础扰动 + 收盘事件影响 + 前收回归 - 卖单影响)
```

现有长期单边走势更可能来自以下组合，而不是某一个永久趋势项：

1. `RANDOM` 事件方向在牛/熊状态下有 65% 概率顺趋势，且顺趋势事件被放大、逆趋势事件被压低，形成事件层正反馈。
2. 事件只在同一回合的开盘/收盘阶段消化，没有跨回合残余；图形容易出现单回合台阶。
3. 波动率没有记忆，重大冲击后一回合立即回到固定噪声。
4. 当前均值回归只把开盘价拉向“上一收盘价”，不是向中期交易中枢回归，无法修复多回合极端偏离。
5. 市场状态在事件引擎、AI 快照、本地 AI 策略中分别实现了三套分类，名称和阈值不一致，也没有滞后。
6. AI 已有 Thesis、Evidence Gate、目标仓位、调仓缓冲、最短持有期、单回合反向拦截、换手上限、FAST/STRONG 路由、缓存、非事务 HTTP 和并发任务；这些能力不应重复建设。
7. AI 仍缺少跨回合反向冷却和明确的最大仓位步长。当前 `LOW/MEDIUM/HIGH` 会执行目标差的 50%/75%/100%，高紧急度仍可能一次跳仓。

因此实施顺序必须调整为：

```text
基线模拟 -> 统一市场状态 -> 价格形成 v2 -> 事件跨期传导
        -> AI 快照升级 -> AI 执行约束 -> AI 对抗校准
```

## 2. 本期架构

```text
事件选择
  -> 事件有效性与总冲击评估
  -> 事件冲击切片（当前阶段 + 后续阶段）
  -> 统一 MarketState（TrendState + VolatilityState + age）
  -> MarketPriceEngine
       baseDrift
       + regimeBias
       + decayingMomentum
       + scheduledEventImpact
       + mediumMeanReversion
       + volatilityNoise
       + orderFlowImpact
  -> RoundPriceBreakdown 持久化
  -> AiMarketSnapshot v3
  -> Evidence Gate / Thesis
  -> requestedTargetPosition
  -> stepTargetPosition / Rebalance / Cooldown
  -> Order
```

所有价格组件继续使用对数收益百分比，最终统一执行：

```java
double totalLogReturnPct = clamp(
        baseDriftPct
        + regimeBiasPct
        + momentumPct
        + eventPct
        + meanReversionPct
        + noisePct
        + orderFlowPct,
        -maxMovePct,
        maxMovePct);

BigDecimal newPrice = oldPrice.multiply(
        BigDecimal.valueOf(Math.exp(totalLogReturnPct / 100.0)));
```

## 3. P0：先建立可校准基线

### 3.1 抽出纯函数模拟内核

新增：

- `engine/MarketStateCalculator.java`
- `engine/MarketPriceEngine.java`
- `engine/EventImpactSchedule.java`
- `simulation/MarketSimulationRunner.java`（放在测试源码或独立 profile 下，不能依赖数据库和 Spring 容器）

`GuessStockSessionService.createRound`、`clearOpening`、`clearClosing` 只负责编排和落库，不再内联生成基础扰动或拼价格公式。纯函数输入必须包含种子，使线上场次和测试可复算。

### 3.2 先跑当前算法，不先调参数

建立以下场景，每个场景至少 `10,000 局 × 100 回合`：

| 场景 | 事件 | 订单流 | 目的 |
| --- | --- | --- | --- |
| `NO_EVENT_NO_FLOW` | 无 | 无 | 查基础噪声与价格公式偏差 |
| `NO_EVENT_BALANCED_FLOW` | 无 | 对称 | 查开盘买/收盘卖的结构偏差 |
| `BALANCED_EVENTS_NO_FLOW` | 正负 50/50 | 无 | 查事件 regime 正反馈偏差 |
| `ONLY_POSITIVE_EVENTS` | 仅利好 | 无 | 校验冲击上界和饱和效果 |
| `ONLY_NEGATIVE_EVENTS` | 仅利空 | 无 | 校验回撤和破产尾部 |
| `FULL_MARKET` | 正常 | 规则化订单流 | 建立最终游戏基线 |

输出 CSV/JSON，至少包含：最终收益、中位收益、最大回撤、实现波动率、最大单阶段波动、最长涨跌序列、趋势状态占比、状态切换数、事件数、订单流贡献、各价格组件累计贡献。

首轮验收不写死“真实证券市场标准”，使用防失控区间：

- `NO_EVENT_NO_FLOW` 的中位最终收益绝对值不超过 2%。
- 对称正负事件场景的中位收益绝对值不超过 5%，上涨局占比在 40%～60%。
- 普通阶段绝对变化的 P99 不超过配置基础波动率的约 2 倍。
- 非极端事件单阶段绝对变化不超过 7%，极端事件不超过 12%。
- 每 100 回合趋势状态切换约 3～12 次，且单回合来回翻转比例低于 5%。

这些阈值先用于发现系统偏差，最终值通过游戏体验样本再收紧。

## 4. P1：统一趋势、波动与价格形成

### 4.1 只保留一套 MarketState

统一替换以下重复逻辑：

- `GuessStockEventImpactEngine.detectRegime`
- `AiMarketSnapshotService.regime`
- `AiTradingPolicy.features` 中的 regime 分类

统一状态拆成两个正交维度：

```java
enum TrendState { STRONG_UP, UP, SIDEWAYS, DOWN, STRONG_DOWN }
enum VolatilityState { LOW, NORMAL, HIGH, EXTREME }

record MarketState(
    TrendState trend,
    VolatilityState volatility,
    int regimeAge,
    int trendAge,
    int consecutiveDirectionRounds,
    double trendScore,
    double currentVolatilityPct
) {}
```

趋势分数使用 3/8/20 回合收益与斜率的归一化组合，不直接用价格高低判断方向：

```text
trendScore = 0.45 × z(slope3) + 0.35 × z(slope8) + 0.20 × z(return20)
```

状态切换使用滞后阈值。例如进入 `DOWN` 为 `< -0.45`，退出 `DOWN` 为 `> -0.20`；强趋势同理使用更宽阈值。上一状态来自上一回合已落库状态，避免每次重算历史产生不一致。

### 4.2 趋势延续必须衰减和疲劳

新增的是“小幅 momentum”，不是让状态直接决定涨跌：

```text
rawMomentum = direction × min(momentumCap, baseVolatility × momentumStrength)
timeDecay = exp(-trendAge / trendDecayRounds)
exhaustion = clamp(consecutiveRounds / 10 + abs(return20) / 30, 0, 1)
momentumPct = rawMomentum × timeDecay × (1 - 0.65 × exhaustion)
```

首轮建议参数：

- `momentumStrength = 0.10`
- `momentumCap = 0.45% / 阶段`
- `trendDecayRounds = 6`
- `exhaustionMaxReduction = 65%`

参数只作为模拟起点。禁止加入“连续跌 N 次后强制上涨”。

### 4.3 中期均值回归

把当前“开盘拉回上一收盘”的回归改为交易中枢回归：

```text
fairPrice = EMA20 × 0.7 + EMA50 × 0.3
deviation = ln(fairPrice / currentPrice) × 100
meanReversionPct = clamp(deviation × reversionStrength, -cap, cap)
```

历史不足 20 回合时使用现有全部收盘价，EMA50 不足时退化为 EMA20。首轮参数：`reversionStrength=0.04`、普通上限 `0.60%/阶段`，强趋势时乘 `0.50`，趋势疲劳时逐渐恢复到 `1.0`。

### 4.4 波动率聚集和动态噪声

```text
shockPct = abs(lastStageReturnPct - expectedComponentPct)
currentVol = clamp(
    0.85 × previousVol + 0.15 × shockPct,
    baseVol × 0.35,
    baseVol × 2.5)
noisePct = truncatedGaussian(seed) × currentVol
```

开盘和收盘仍可按 35%/65% 分配波动预算，但预算来源改为 `currentVol`，重大冲击后不能下一阶段立即恢复固定方差。

### 4.5 价格分解落库

新增迁移 `V35__guess_stock_market_state.sql`，在 `guess_stock_rounds` 增加：

- `trend_state`、`volatility_state`
- `trend_score`、`trend_age`、`regime_age`
- `current_volatility_percent`
- `momentum_change_percent`
- `regime_change_percent`
- `scheduled_event_change_percent`
- `medium_mean_reversion_percent`
- `noise_change_percent`
- `total_change_percent`
- `pricing_engine_version`

同时在 `GuessStockConfig` 增加 `pricingEngineVersion`，`GuessStockRules.validate` 只接受 `V1/V2`；旧 JSON 缺省为 `V1`，新建游戏的前端默认值为 `V2`。旧字段保留一版兼容复盘，不在同一个迁移中删除。每回合再写入实际使用的 `pricing_engine_version` 作为审计快照。这样旧发布版本即使以后才开局也仍使用 V1，已开始和历史场次同样不会被新公式改写。

## 5. P1：事件从“单点冲击”改为跨期传导

### 5.1 事件先算总冲击，再切片

保留当前已有的 `effectProbability`、tier、relevance、credibility、surprise、strength 和波动率上限，但将结果定义为 `totalImpactPct`，再按事件类型切成阶段权重：

| 类型 | 默认阶段权重 |
| --- | --- |
| `ACCIDENT` | 0.70 / 0.20 / 0.10 |
| `FINANCIAL` | 0.50 / 0.30 / 0.15 / 0.05 |
| `DEMAND`、`SUPPLY` | 0.35 / 0.30 / 0.20 / 0.10 / 0.05 |
| `INDUSTRY` | 0.20 / 0.25 / 0.25 / 0.20 / 0.10 |
| `POLICY` | 0.10 / 0.30 / 0.30 / 0.20 / 0.10 |
| `SENTIMENT` | 0.60 / 0.25 / 0.10 / 0.05 |

“阶段”按 `OPENING -> CLOSING -> 下一回合 OPENING` 顺序推进。只有 `STRONG + CONFIRMED + MAJOR` 的突发事件允许首阶段占比达到 80% 以上。

### 5.2 使用可审计的冲击切片表

新增迁移 `V36__guess_stock_event_impact_slices.sql` 和表 `guess_stock_event_impact_slices`：

```text
session_id, source_round_no, source_stage, event_config_index,
target_round_no, target_stage, direction, event_type,
total_impact_percent, slice_weight, slice_impact_percent,
saturation_factor, contrarian_factor, applied_at
```

事件发生时一次性生成所有切片；阶段定价时按目标回合和阶段聚合未应用切片。这样暂停、恢复、重试和复盘都不会重复释放冲击。

### 5.3 同类饱和、反向修正与冷却

按最近 5 回合相同 `eventType + direction` 计算：

```text
saturationFactor = 1 / (1 + similarCount × 0.35)
```

极端反向事件仅在以下条件同时满足时放大 `1.2～1.5`：

- 事件方向与当前趋势相反；
- `HIGH relevance`；
- `CONFIRMED`；
- `MAJOR surprise`；
- 当前趋势年龄至少 4 回合。

事件选择器不再用“所有事件整局只能出现一次”替代冷却。新增：普通同类型冷却 2～4 回合、强事件 5～10 回合、同主题 3～6 回合。为避免 NLP，引入可选 `topicKey`；未配置时用 `eventType` 作为主题。

事件链放在第二次迭代：新增可选 `chainKey`、`parentTopicKey`、`followUpMultiplier`，只提升后续候选概率，不保证发生。

## 6. P2：AI 输入与观点连续性

### 6.1 DecisionPacket v3

在 `AiMarketSnapshot` 和模型包中增加：

```json
{
  "drawdown": { "fromHighPct": -12.4, "maxPct": -18.7 },
  "state": {
    "trend": "DOWN",
    "volatility": "HIGH",
    "trendAge": 8,
    "regimeAge": 4,
    "consecutiveDirectionRounds": 6
  },
  "eventDigest": {
    "currentImpactPct": -0.8,
    "remainingImpactPct": -1.1,
    "remainingStages": 2
  }
}
```

删除 AI 自己猜测趋势状态的空间：`market_regime` 输出保留用于描述 Thesis，而服务端趋势和波动状态是只读事实。`promptVersion` 升级，缓存键自然隔离。

### 6.2 Evidence Gate 去抖

只有以下变化触发模型：

- 持久化 MarketState 真正切换，而不是无滞后的瞬时分类变化；
- drawdown 跨过预设区间；
- 新事件或残余事件的方向/强度发生重要变化；
- Thesis 复核、到期或结构化失效条件触发；
- 异常波动和订单失衡超过相对当前波动率的阈值。

普通残余事件每阶段释放不应都触发一次模型，否则跨期传导会反而增加 Token 和观点抖动。

### 6.3 Thesis 校验补充

新增可验证条件：

- `TREND_STATE_CHANGED`
- `VOLATILITY_STATE_ABOVE`
- `REGIME_AGE_ABOVE`
- `EVENT_RESIDUAL_BELOW`

继续坚持：后端检测条件并要求模型复核，不把 `PRICE_BELOW` 等条件直接解释为自动卖出。

## 7. P2：AI 目标仓位执行优化

### 7.1 最大仓位步长

在 `AiPlayerConfig` 增加：

- `maxPositionStepPctPoints`：默认 15pp
- `highUrgencyPositionStepPctPoints`：默认 30pp
- `reverseCooldownRounds`：默认 2 回合

执行层同时保留两个目标：

```text
requestedTarget = Thesis 的长期目标
stepTarget = current + clamp(requestedTarget - current, -maxStep, +maxStep)
```

只有 `thesisInvalidated || criticalRisk` 可越过普通步长；即便越过，也仍受现金、持仓、单笔上限、冲击和单回合换手约束。审计表分别记录 requested target、step target 和实际成交后仓位，避免当前“目标仓位未变但只执行部分股数”的解释歧义。

### 7.2 跨回合反向冷却

把现有“同回合反向拦截”扩展为最近成交方向：

```text
若新方向与 lastTradeDirection 相反
且 roundNo - lastTradeRound < reverseCooldownRounds
且不存在 thesisInvalidated / criticalRisk / confirmedRegimeReversal
则 HOLD
```

`minHoldRounds` 继续约束买入后的过早卖出，`reverseCooldownRounds` 负责 BUY/SELL 双向切换，两者职责分开。

### 7.3 交易成本暂不首批上线

原玩法文档把手续费排除在首版范围外，直接加入会改变排行和创作者配置语义。先在 AI 对抗测试中统计换手率：若 Hard AI 100 回合中位交易次数仍超过 25，或成本敏感性模拟显示收益主要来自无成本高频交易，再新增规则版本 V3：手续费默认 0.05%，滑点随订单冲击计算 0.02%～0.15%。旧版本场次不追溯应用。

## 8. P3：测试、监控与校准

### 8.1 单元与性质测试

新增或扩展：

- `MarketStateCalculatorTest`：滞后、状态年龄、多周期一致性。
- `MarketPriceEngineTest`：同种子确定性、价格始终为正、组件和等于总收益、最大波动限制。
- `EventImpactScheduleTest`：权重和为 1、恢复不重复、不同类型消化速度、饱和递减。
- `AiMarketSnapshotServiceTest`：drawdown、双维状态、年龄、残余事件摘要。
- `AiTargetPositionExecutorTest`：15pp/30pp 步长、跨回合反向冷却、硬覆盖条件。
- `MarketSimulationTest`：六组分布场景和固定种子回归基线。

不要对随机单路径断言最终价格；对确定种子断言可复算，对批量结果断言分位数和比例。

### 8.2 AI 对抗测试

市场分布验收后，再对 Easy/Normal/Hard 各运行至少 1,000 局，记录：

- 收益率、中位收益、最大回撤、胜率、Sharpe-like 指标；
- 交易次数、换手率、平均持有回合、反向交易次数；
- 模型调用次数、FAST/STRONG 比例、缓存命中率、失败降级率和 Token；
- requested target、step target、实际仓位的平均偏差。

建议初始目标：Easy 5～15 次、Normal 8～20 次、Hard 10～25 次/100 回合；这不是难度越高交易越多的硬规则，Hard 的主要优势应来自更好的证据判断和风险调整收益。

### 8.3 线上观测

按 `pricing_engine_version` 聚合：

- 100 回合收益分布、最大回撤、最大单阶段波动；
- 各价格组件累计贡献，重点观察事件和订单流是否长期单向；
- MarketState 占比、平均年龄和切换次数；
- AI 观点替换率、反向成交率、执行约束拦截原因；
- 模型耗时和失败率。

## 9. 交付拆分

### 迭代 A：基线与统一状态

1. 纯函数模拟器和当前 V1 六场景报告。
2. `MarketStateCalculator`、双维状态、滞后和状态落库。
3. 三套 regime 逻辑统一接入。

验收：能回答当前 90 回合下跌是随机样本、事件正反馈还是订单流偏差；同一历史在事件、AI 和复盘中得到相同市场状态。

### 迭代 B：价格与事件 V2

1. 衰减 momentum、趋势疲劳、中期均值回归、波动率聚集。
2. 事件冲击切片、同类饱和、反向超预期修正、冷却。
3. 价格分解落库和前端复盘展示。

验收：通过六场景分布阈值；暂停恢复不会重复应用事件；旧场次仍按 V1 正常读取。

### 迭代 C：AI v3

1. DecisionPacket 加 drawdown、双维状态、状态年龄和残余事件。
2. Evidence Gate 去抖和新失效条件。
3. 最大仓位步长、跨回合反向冷却、审计字段拆分。

验收：无重要新证据时延续 Thesis；普通信号单次仓位变化不超过 15pp；非硬覆盖条件下两回合内不反向成交。

### 迭代 D：对抗校准与灰度

1. Easy/Normal/Hard 批量对抗。
2. 参数基线固化为测试资源。
3. 新发布游戏默认 V2，旧发布版本保持 V1；观察一轮线上指标后再决定是否加入交易成本和事件链。

## 10. 明确不做

- 不用“跌够 N 次必涨”或“价格低于阈值强制反弹”。
- 不让后端把 AI 的结构化失效条件直接变成自动止损单。
- 不同时重写价格模型、事件模型和 AI Prompt 后只看一张走势图验收。
- 不删除现有 Thesis、Evidence Gate、路由、缓存、非事务 HTTP 和并发任务实现。
- 不修改进行中的旧版本场次价格规则。

## 11. 首个实施 PR 的建议边界

首个 PR 只做“基线模拟 + 统一 MarketState”，暂不改变线上价格：

- 新增纯函数历史特征与状态计算器。
- 让事件引擎、AI 快照和本地 fallback 读取同一状态结果。
- 新增 V1 批量模拟报告与固定种子回归测试。
- 输出造成负漂移的组件贡献排名。

这个边界最稳妥：它先解决“三套趋势定义”和“没有统计证据”两个根问题，同时不会把多项参数变化混在一起，便于后续准确判断每个优化是否真的改善市场。

首个 PR 预计修改范围：

- `engine/MarketStateCalculator.java`：新增唯一的状态计算入口。
- `engine/GuessStockEventImpactEngine.java`：删除内部状态判断，改为接收 `MarketState`。
- `agent/AiMarketSnapshotService.java`：读取统一状态，不再维护独立阈值。
- `engine/AiTradingPolicy.java`：fallback 只消费统一特征，不再自行分类 regime。
- `service/GuessStockSessionService.java`：查询历史、调用计算器、落库状态；本 PR 不改价格结果。
- `simulation/MarketSimulationRunner.java` 及对应测试：生成六场景报告。
- `V35__guess_stock_market_state.sql` 与 rollback：增加状态和版本审计字段。
