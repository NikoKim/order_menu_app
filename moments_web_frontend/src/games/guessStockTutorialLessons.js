const read = (id, round, phase, title, description, target, extra = {}) =>
  ({ id, round, phase, title, description, target, mode: 'read', ...extra })
const action = (id, round, phase, title, description, target, done = () => false, extra = {}) =>
  ({ id, round, phase, title, description, target, mode: 'action', done, ...extra })
const first = 'OPENING_TRADING'
const credit = { open: 'credit' }
const research = { open: 'research' }
const pct = value => `${Number(value) >= 0 ? '+' : ''}${Number(value).toFixed(2)}`
const price = value => Number(value).toFixed(4)
const player = session => session.participants[0]

export const TUTORIAL_LESSONS = [
  read('intro-price', 1, 'ROUND_START', '先看股票价格', '这里是当前价格。最初是 100 元，之后会依次公布开盘价和收盘价。价格变化不等于你的实际收益。', 'market-price'),
  read('intro-cash', 1, 'ROUND_START', '这是可支配的现金', '你的独立教学账户有 10,000 元虚拟资金。买入会花掉现金，卖出后现金回流；尚未成交的买单会预留资金。', 'market-cash'),
  read('intro-position', 1, 'ROUND_START', '持仓代表已经买到的股票', '当前持仓为 0 股。保存订单不等于成交，只有阶段结算后，股票数量才会真正变化。', 'market-position'),
  read('intro-asset', 1, 'ROUND_START', '净资产才是成绩', '净资产 = 现金 + 持仓按当前价格估值 - 债务。借来的钱会同时增加现金和债务，不会凭空增加收益。', 'market-asset'),
  read('intro-chart', 1, 'ROUND_START', '走势图会记录每次开盘与收盘', '结算后会出现价格柱和你的买卖标记。本教程练习经典分阶段交易：开盘前买入、收盘前卖出，不是现实交易所的连续竞价。', 'chart'),
  action('start', 1, 'ROUND_START', '开始三回合实操', '第一回合练习买卖和融资；第二回合比较订单影响并保留部分持仓；第三回合处理利空和亏损。点击引导栏的“开始练习”，整个教程不限时。', 'market-price'),

  read('r1-event', 1, 'OPENING_EVENT_ACK', '消息是价格来源之一', '新增订单是需求改善信号，但“发生事件”与“事件生效”是两回事。正式游戏还会判断可信度、相关度等，不能看到利好就认定必涨。', 'event-description'),
  action('r1-event-ack', 1, 'OPENING_EVENT_ACK', '确认这条开盘消息', '点击聚焦的“我已知晓”。确认消息只是进入下一阶段，不会直接买入股票。', 'event-confirm'),
  read('r1-credit-limit', 1, 'CREDIT_TRADING', '先核对银行额度', '教学银行最多提供 1,000 元。借款期限是 1 回合，收盘按未还本金计 1% 利息；正式游戏还要关注敞口、抵押和强平风险。', 'credit-available', credit),
  read('loan-input', 1, 'CREDIT_TRADING', '输入 1,000 元借款', '只操作当前输入框，填入 1000。填好后点击引导栏的“金额已填好”，再进行确认，不会在输入中途跳走。', 'credit-input',
    { ...credit, mode: 'input', continueLabel: '金额已填好', validate: (_session, ui) => Number(ui.creditAmount) === 1000 }),
  action('r1-borrow-confirm', 1, 'CREDIT_TRADING', '现在确认借款', '点击“确认借款”。这一步才会实际增加练习账户的现金和债务，刚才输入金额并不算借款成功。', 'credit-borrow-confirm', session => session.tutorial.borrowed, credit),
  read('r1-credit-asset', 1, 'CREDIT_TRADING', '为什么借款后净资产没变？', '现金由 10,000 增至 11,000，但也欠银行 1,000，因此净资产仍为 10,000。借款增加了可用资金，也增加了成本和风险。', 'credit-net-asset', credit),
  action('r1-bank-close', 1, 'CREDIT_TRADING', '先关闭银行页面', '点击银行页面右上角的关闭按钮。关闭窗口不会退出融资阶段，也不会撤销已经成功借入的资金。', 'credit-close', session => session.tutorial.bankClosed, credit),
  action('r1-bank-reopen', 1, 'CREDIT_TRADING', '从这里再次打开银行', '顶部的银行图标是银行融资入口。点击它重新打开银行页面；单人和多人游戏都可以通过这个入口查看贷款、借款或还款。', 'bank-trigger', session => session.tutorial.bankReopened),
  action('r1-credit-ready', 1, 'CREDIT_TRADING', '完成融资阶段', '点击“完成融资，进入开盘前”。每个阶段都需要单独确认，接下来才可以准备买单。', 'credit-ready-button', undefined, credit),

  action('research-open', 1, first, '打开市场研究', '点击唯一聚焦的烧瓶按钮。研究提供额外信息，不会代替你下单，也不能保证之后上涨。', 'research-trigger', (session, ui) => ui.researchOpen || session.tutorial.researched),
  action('research-verify', 1, first, '核验事件', '点击“核验事件”，消耗 1 个研究点。其他研究方式这一步先不操作。', 'research-verify', session => session.tutorial.researched, research),
  read('research-result', 1, first, '研究信号不是承诺', '这里显示“信号偏强”和估计区间，只是研究结果。剩余点数有限，正式游戏需要决定何时把研究机会用在最关键的信息上。', 'research-result', research),
  action('research-close', 1, first, '关闭研究窗口', '点击右上角的关闭按钮，回到买入区域。', 'research-close', (session, ui) => session.tutorial.researched && !ui.researchOpen, research),
  read('r1-reference', 1, first, '先区分参考价与成交价', '下单参考价来自上一收盘价 100 元。实际成交价由本次开盘结算决定，可能更高也可能更低，不能把参考金额当作最终扣款。', 'order-reference'),
  action('r1-buy-first', 1, first, '先保存一笔 25% 买单', '点击聚焦的“25%”，小仓位试单。系统按可买上限计算股数并自动保存，目前仍未成交。', 'order-quarter', session => session.tutorial.firstOrderPlaced),
  read('r1-pending', 1, first, '这是挂单，还不是持仓', '这里显示已经保存的买入数量，资金被预留。股票要在开盘结算后才到账，成交前可以修改或撤销。', 'pending-order'),
  action('r1-cancel', 1, first, '亲手撤销这笔订单', '点击“撤销订单”。尚未成交的订单被取消，预留资金会释放，也不会计入这次开盘的买盘。', 'order-cancel', session => session.tutorial.cancelled),
  action('r1-buy-again', 1, first, '重新保存买单', '再点击一次“25%”。这次保留订单，通过正常的开盘结算查看买盘怎样影响价格。', 'order-quarter', session => session.tutorial.reordered),
  read('r1-price-components', 1, first, '价格不是由买入金额单独决定', '经典引擎把基础变化、事件、订单冲击相加，再按指数合成价格。买盘只是一项正向力量，负面事件更强时仍可能下跌。下面的参考金额并不是实际成交金额。', 'order-preview'),
  action('r1-buy-ready', 1, first, '进入开盘结算', '点击“完成开盘前操作”，接下来会在正常结算页面看到买盘、事件和基础变化各自的贡献。', 'trade-ready'),

  read('r1-open-price', 1, 'OPENING_RESULT_ACK', '现在才是实际成交价', session => `本次实际开盘为 ${price(session.currentRoundState.openPrice)} 元，不再是 100 元参考价。现金按实际成交价扣减，持仓已经到账。`, 'opening-price'),
  read('r1-open-flow', 1, 'OPENING_RESULT_ACK', '单独看买盘贡献', session => `全场已确认买单带来 ${pct(session.currentRoundState.openingOrderFlowChangePercent)} 的正向冲击。买得越多、市场越浅，影响越大，但会逐渐接近配置的影响上限。`, 'opening-flow'),
  read('r1-open-formula', 1, 'OPENING_RESULT_ACK', '把开盘价格完整拆开', '上一收盘价是基准；这里的基础变化、事件和买盘共同决定开盘价。分项是对数冲击，价格按指数合成；实际涨跌幅需要用开盘价与上一收盘价比较。', 'opening-breakdown'),
  action('r1-open-next', 1, 'OPENING_RESULT_ACK', '进入收盘前阶段', '点击“进入收盘前”。开盘时买到的股票，现在才可以准备卖出。', 'opening-next-button'),
  action('r1-close-event', 1, 'CLOSING_EVENT_ACK', '确认收盘阶段的信息', '阶段变了，信息也可能变化。先阅读交付进展，再点击“我已知晓”。', 'event-confirm'),
  read('r1-sell-rule', 1, 'CLOSING_TRADING', '卖盘怎样影响收盘价？', '收盘以本回合开盘价为基准。基础变化、事件、均值回归共同作用，再减去卖盘冲击；卖出越多、流动性越低，下压越明显，但不保证最终一定收跌。', 'order-reference'),
  action('r1-sell-all', 1, 'CLOSING_TRADING', '卖出已有的全部持仓', '点击聚焦的“全部”。卖出不能超过已经持有的数量，此刻保存的仍是待成交订单。', 'order-all', session => session.currentOrder?.quantity === player(session).quantity),
  read('r1-sell-preview', 1, 'CLOSING_TRADING', '预计回笼还不是到账金额', '卖出的参考金额按开盘价估算。你的卖盘以及其他变化会一起决定收盘价，最终回款要等结算。', 'order-preview'),
  action('r1-sell-ready', 1, 'CLOSING_TRADING', '确认收盘结算', '点击“完成收盘前操作”，按即将公布的收盘价卖出。', 'trade-ready'),
  read('r1-close-flow', 1, 'ROUND_RESULT_ACK', '卖盘贡献是负数', session => `这一笔卖出形成 ${pct(session.currentRoundState.orderFlowChangePercent)} 的负向冲击。它压低了“其他条件相同、没有卖单”的价格，不等于整天一定下跌。`, 'closing-flow'),
  read('r1-reversion', 1, 'ROUND_RESULT_ACK', '为什么还会出现均值回归？', '经典 V1 引擎会把价格轻轻拉向上一收盘价：开盘偏高时回拉为负，偏低时回拉为正，并设上限。这是游戏规则，不是现实股价的保证。', 'closing-reversion'),
  read('r1-close-formula', 1, 'ROUND_RESULT_ACK', '对照完整收盘公式', '收盘价 = 开盘价 × exp((基础 + 事件 + 均值回归 - 卖盘压力) / 100)。正向事件可能抵消卖盘，所以要比较这些实际结算分项，而不是只看买卖方向。', 'closing-breakdown'),
  action('r1-close-next', 1, 'ROUND_RESULT_ACK', '进入还贷阶段', '交易完成了，还要处理资金成本。点击“进入银行还贷期”。', 'round-next-button'),
  read('r1-debt', 1, 'CREDIT_REPAYMENT', '本金之外还有利息', '借款 1,000 元，本回合利息 10 元，共需归还 1,010。股票赚到的钱，要扣掉这笔成本才是净收益。', 'credit-interest', credit),
  action('r1-repay-full', 1, 'CREDIT_REPAYMENT', '先填入全部应还金额', '还款页已打开。点击聚焦的“100%”，把本金和利息一起填入，不要只归还 1,000 元本金。', 'credit-full', (session, ui) => session.credit.effectiveDebt === 0 || Number(ui.creditAmount) >= session.credit.effectiveDebt, credit),
  action('r1-repay-confirm', 1, 'CREDIT_REPAYMENT', '再确认还款', '点击“确认还款”。填入金额与执行还款是两个独立动作。', 'credit-repay-confirm', session => session.credit.effectiveDebt === 0, credit),
  action('r1-repay-ready', 1, 'CREDIT_REPAYMENT', '第一回合闭环完成', '债务已经结清。点击“完成还贷”，进入更大的订单和部分卖出练习。', 'credit-ready-button', undefined, credit),

  action('r2-event', 2, 'OPENING_EVENT_ACK', '第二回合：练习部分持仓', '市场继续出现温和利好。这回尝试较大买单，再只卖出一半，看看剩余股票怎样影响下一回合资产。', 'event-confirm'),
  action('r2-credit', 2, 'CREDIT_TRADING', '这次不新增借款', '第二回合使用自己的资金。点击“完成融资，进入开盘前”，不借款也可以正常推进。', 'credit-ready-button', undefined, credit),
  action('r2-buy', 2, first, '把买入比例提高到 50%', '点击“50%”保存买单。同样流动性下，更大的买单会带来更大的正向订单冲击，也可能提高自己的买入成本。', 'order-half', session => Boolean(session.currentOrder)),
  action('r2-buy-ready', 2, first, '结算这笔较大的买单', '点击“完成开盘前操作”，把这一轮的买盘冲击与上一轮作比较。', 'trade-ready'),
  read('r2-flow', 2, 'OPENING_RESULT_ACK', '大买单也会抬高自己的成本', session => `本回合买盘冲击 ${pct(session.currentRoundState.openingOrderFlowChangePercent)}，第一回合为 ${pct(session.rounds[0].openingOrderFlowChangePercent)}。不是买得多就赚得多，仓位和成交成本也都变了。`, 'opening-flow'),
  action('r2-open-next', 2, 'OPENING_RESULT_ACK', '进入部分卖出练习', '点击“进入收盘前”。', 'opening-next-button'),
  action('r2-close-event', 2, 'CLOSING_EVENT_ACK', '继续确认阶段消息', '阅读温和的交付消息，再点击“我已知晓”。利好不是忽略成交成本的理由。', 'event-confirm'),
  action('r2-sell-half', 2, 'CLOSING_TRADING', '只卖出一半', '点击“50%”保存部分卖出订单，剩下的股票继续持有。部分卖出通常比全部卖出产生更小的负向压力。', 'order-half', session => Boolean(session.currentOrder)),
  action('r2-sell-ready', 2, 'CLOSING_TRADING', '结算部分卖出', '点击“完成收盘前操作”。不要再点击“全部”，我们要留下一部分持仓进入下一回合。', 'trade-ready'),
  read('r2-hold', 2, 'ROUND_RESULT_ACK', '只结算了卖出的那部分', session => `你仍持有 ${player(session).quantity} 股。剩余股票按当前收盘价计入资产，但还没换成现金；下一回合下跌时，这部分资产会缩水。`, 'closing-price'),
  action('r2-close-next', 2, 'ROUND_RESULT_ACK', '结束第二回合交易', '点击“进入银行还贷期”。', 'round-next-button'),
  action('r2-repay', 2, 'CREDIT_REPAYMENT', '没有借款就没有新增利息', '核对债务为零，点击“完成还贷”。第三回合带着剩余股票面对利空。', 'credit-ready-button', undefined, credit),

  read('r3-event-read', 3, 'OPENING_EVENT_ACK', '第三回合：没有买单也会跌', '客户削减采购形成负面事件。股价不只由玩家买卖决定，即使你这回不再买入，基础变化与事件仍可改变价格。', 'event-description'),
  action('r3-event', 3, 'OPENING_EVENT_ACK', '确认这条利空', '点击“我已知晓”，这回不开新仓，先观察已有持仓。', 'event-confirm'),
  action('r3-credit', 3, 'CREDIT_TRADING', '不借钱补仓', '本次不新增借款，点击“完成融资，进入开盘前”。这是教程的风险练习，不是现实投资建议。', 'credit-ready-button', undefined, credit),
  read('r3-observe', 3, first, '保留现金，不下新买单', '你仍有上一回合留下的股票，但不需要每回合都买入。没有新买单意味着本回合买盘冲击为 0，其他价格因素仍然存在。', 'order-account'),
  action('r3-buy-ready', 3, first, '直接完成开盘前操作', '不设置买入数量，直接点击“完成开盘前操作”。', 'trade-ready'),
  read('r3-open', 3, 'OPENING_RESULT_ACK', '已有持仓会随价格重估', '这里买盘影响为 0，但事件与基础变化使开盘价下降。现金没变，留下的股票却以更低价格估值，净资产也会减少。', 'opening-breakdown'),
  action('r3-open-next', 3, 'OPENING_RESULT_ACK', '准备处理剩余持仓', '点击“进入收盘前”。', 'opening-next-button'),
  action('r3-close-event', 3, 'CLOSING_EVENT_ACK', '确认需求仍然偏弱', '阅读消息并点击“我已知晓”。接下来把剩余持仓全部卖出，完成收益与损失的结算。', 'event-confirm'),
  action('r3-sell', 3, 'CLOSING_TRADING', '卖出剩余股票', '点击“全部”。这回的卖盘会继续形成向下的价格压力，但也会受到事件和均值回归的共同作用。', 'order-all', session => session.currentOrder?.quantity === player(session).quantity),
  action('r3-sell-ready', 3, 'CLOSING_TRADING', '完成最后一次收盘结算', '点击“完成收盘前操作”。卖出只是把现有股票换成现金，不会抹掉之前的价格变化。', 'trade-ready'),
  read('r3-close', 3, 'ROUND_RESULT_ACK', '把损益和价格来源一起复盘', '对照负面事件、卖盘压力与回归项。你已体验小单、大单、部分卖出、跨回合持仓和利空减仓，最终成绩看净资产，而不是某一次股价上涨。', 'closing-breakdown'),
  action('r3-close-next', 3, 'ROUND_RESULT_ACK', '核对最终债务', '点击“进入银行还贷期”。', 'round-next-button'),
  action('r3-repay', 3, 'CREDIT_REPAYMENT', '完成最后一个阶段', '确认债务已经归零，点击“完成还贷”，查看三回合结果。', 'credit-ready-button', undefined, credit),
  read('complete', 3, 'COMPLETED', '新手教程已完成', '你已学会逐阶段操作、订单与成交的区别、买卖压力和流动性、事件与回归、融资成本及持仓损益。正式游戏还可能使用 V2 趋势或 V3 基本面与财报引擎，不能把本教程情景当作必然行情。', 'summary')
].map((lesson, index) => ({ ...lesson, index }))

export function tutorialStep(session, ui = {}) {
  const lessons = session.tutorial.lessons || []
  const candidates = TUTORIAL_LESSONS.filter(lesson => lesson.round === session.currentRound && lesson.phase === session.phase)
  const current = candidates.find(lesson => lesson.mode === 'action'
    ? !lesson.done(session, ui) : !lessons.includes(lesson.id)) || candidates.at(-1) || TUTORIAL_LESSONS[0]
  return { ...current, description: typeof current.description === 'function' ? current.description(session) : current.description,
    total: TUTORIAL_LESSONS.length, canContinue: current.mode === 'read' || (current.mode === 'input' && current.validate(session, ui)),
    chapter: session.phase === 'ROUND_START' ? '认识账户' : `第 ${session.currentRound} 回合 · ${['', '买卖与融资', '订单与持仓', '利空与损益'][session.currentRound]}` }
}
