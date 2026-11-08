import { FLEET, ITEMS, createLocalGame, legalPlacement, shipCells, validateFleet } from './navalEngine.js'

export const NAVAL_TUTORIAL_STORAGE_KEY = 'naval_tutorial_v1'
export const NAVAL_MOBILE_TUTORIAL_STORAGE_KEY = 'naval_tutorial_mobile_v1'
const VERSION = 4
const HOUR = 60 * 60 * 1000
const clone = value => JSON.parse(JSON.stringify(value))
const key = cell => `${cell.x},${cell.y}`
const coordinate = cell => `${String.fromCharCode(65 + cell.x)}${cell.y + 1}`
const LESSONS = [
  { id: 'welcome', title: '先布阵，再补给，最后交战', target: 'naval-own-board', mode: 'read', chapter: '游戏流程', description: '第一步在己方 12 × 12 海域布置 10 艘舰船；第二步用 300 金币购买道具、部署防御；双方准备好才进入第三步交战。击毁敌方全部舰船获胜。舰船之间至少空一格，斜角也不能相邻。训练不限时，不计战绩。', continueLabel: '开始布阵练习' },
  { id: 'move', title: '选择并移动舰船', target: 'naval-fleet-roster', mode: 'action', chapter: '舰队布局' },
  { id: 'clear', title: '清空当前排布', target: 'naval-clear-layout', mode: 'action', chapter: '舰队布局', description: '点击清空排布。舰船会回到编成列表，不会删除你保存的阵型。' },
  { id: 'randomize', title: '生成完整阵型', target: 'naval-random-layout', mode: 'action', chapter: '舰队布局', description: '点击随机阵型，重新布置全部舰船。正式游戏中也可以逐艘摆放，或载入你保存的阵型。' },
  { id: 'rotate', title: '旋转舰船', target: 'naval-rotate-ship', mode: 'action', chapter: '舰队布局', description: '电脑部署选中舰船后点击或拖放直接生效，用工具栏旋转图标或 R 转向；手机先预览，用勾确认、叉取消。靠近边缘会自动移入棋盘；红色半透明预览表示当前不能摆放，换个位置即可。' },
  { id: 'layout', title: '第二阶段：战备补给', target: 'naval-layout-next', mode: 'action', chapter: '舰队布局', description: '布阵限时 5 分钟，双方完成后进入补给，提前完成需等待对方。点击下一步锁定舰队；进入补给后只能购买道具和调整防御，不能再移动舰船。' },
  { id: 'buy-fighter', title: '购买两架战斗机', target: 'naval-buy-fighter', mode: 'action', chapter: '战备补给', description: '每架 30 金币。选中 2 行 × 8 列后，随机选其中一行扫射 8 格；任一候选行有防空炮都会拦截。买两架，分别演示拦截与扫射。' },
  { id: 'buy-mine', title: '购买一枚水雷', target: 'naval-buy-mine', mode: 'action', chapter: '战备补给', description: '一枚水雷花费 5 金币。购买后还要将它部署在自己的空海域中，不能与舰船重叠。' },
  { id: 'place-mine', title: '将水雷放入海域', target: 'naval-own-board', mode: 'action', chapter: '战备补给' },
  { id: 'buy-aa', title: '购买一座防空炮', target: 'naval-buy-antiAir', mode: 'action', chapter: '战备补给', description: '防空炮花费 30 金币，保护整行。敌方飞机袭击这一行时，它会现身开火，击落飞机后消耗。' },
  { id: 'place-aa', title: '部署一行防空', target: 'naval-own-board', mode: 'action', chapter: '战备补给', description: '点击亮起的第 8 行，部署防空炮。它不会占用舰船的位置，同一行只需部署一座。' },
  { id: 'buy-radar', title: '再购买一次雷达', target: 'naval-buy-radar', mode: 'action', chapter: '战备补给', description: '雷达花费 40 金币，能够探测 3 × 3 区域中的舰船，但不造成伤害，也会消耗一次行动。' },
  { id: 'buy-bomber', title: '购买轰炸机', target: 'naval-buy-bomber', mode: 'action', chapter: '战备补给', description: '25 金币，最多 3 架。在 3 × 3 区域随机轰炸 5 至 7 格；防空炮也能击落它。' },
  { id: 'buy-torpedo', title: '购买鱼雷机', target: 'naval-buy-torpedo', mode: 'action', chapter: '战备补给', description: '25 金币，最多 3 架。从选中的格子向右攻击，遇舰船或水雷停止，也会被防空炮拦截。投放后鱼雷移动比飞机略慢。' },
  { id: 'buy-submarine', title: '购买两艘潜艇', target: 'naval-buy-submarine', mode: 'action', chapter: '战备补给', description: '每艘 30 金币，最多 3 艘。连续三格组成艇身，驶入敌方海域后随机向上、向下各发一颗鱼雷。买两艘，分别演示发射和触雷。' },
  { id: 'buy-stealth', title: '购买隐身图层', target: 'naval-buy-stealth', mode: 'action', chapter: '战备补给', description: '10 金币，最多 5 份。每份涂一艘舰船，使整艘舰船不被雷达发现；实际攻击仍会命中。' },
  { id: 'coat', title: '给舰船涂装', target: 'naval-own-board', mode: 'action', chapter: '战备补给' },
  { id: 'ready', title: '第三阶段：双方轮流交战', target: 'naval-supply-ready', mode: 'action', chapter: '战备补给', description: '补给限时 3 分钟，水雷、防空炮和涂层需全部部署。双方完成战备后，左边是己方，右边是敌方；每次进攻 30 秒，命中继续，落空换手。战斗中用完道具自动切回舰炮。' },
  { id: 'stealth-demo', title: '观察隐身舰船躲过雷达', target: 'naval-own-board', mode: 'read', chapter: '海域交战', description: '教官正在扫描你刚涂装的舰船。扫描经过时，涂装舰船不会出现舰船信号；周围未涂装舰船仍会被发现。扫描结束后不留下痕迹。' },
  { id: 'hit', title: '开炮命中舰船', target: 'naval-enemy-board', mode: 'action', chapter: '海域交战', weapon: 'shot' },
  { id: 'hit-bonus', title: '命中后可以继续进攻', target: 'naval-enemy-board', mode: 'read', chapter: '海域交战', description: '命中格会置灰。刚才击沉了一艘巡逻艇，你获得一次追加攻击；正式对局的 30 秒计时也会重置。已击沉舰船周边一格不可能再放舰船，包括斜角。' },
  { id: 'mine', title: '观察水雷引爆', target: 'naval-enemy-board', mode: 'action', chapter: '海域交战', weapon: 'shot' },
  { id: 'mine-backfire', title: '水雷会反击同坐标', target: 'naval-own-board', mode: 'read', chapter: '海域交战', description: '刚才敌方水雷爆炸时，左侧己方海域的同坐标也爆炸了，可能损伤自己的舰船。舰炮、战斗机和轰炸机会触发反击；鱼雷机和潜艇引爆水雷不会反击。连带引爆己方水雷也不会无限连锁。两边棋盘互为镜像，同坐标不是同一屏幕横向位置。' },
  { id: 'radar', title: '用雷达探测海域', target: 'naval-weapon-radar', mode: 'action', chapter: '海域交战', weapon: 'radar' },
  { id: 'fighter', title: '体验飞机与防空拦截', target: 'naval-weapon-fighter', mode: 'action', chapter: '海域交战', weapon: 'fighter' },
  { id: 'fighter-strafe', title: '随机一行扫射', target: 'naval-weapon-fighter', mode: 'action', chapter: '海域交战', weapon: 'fighter' },
  { id: 'bomber', title: '演示区域轰炸', target: 'naval-weapon-bomber', mode: 'action', chapter: '海域交战', weapon: 'bomber' },
  { id: 'torpedo', title: '演示鱼雷沿行前进', target: 'naval-weapon-torpedo', mode: 'action', chapter: '海域交战', weapon: 'torpedo' },
  { id: 'submarine', title: '演示潜艇双向鱼雷', target: 'naval-weapon-submarine', mode: 'action', chapter: '海域交战', weapon: 'submarine' },
  { id: 'submarine-mine', title: '演示潜艇触雷', target: 'naval-weapon-submarine', mode: 'action', chapter: '海域交战', weapon: 'submarine' },
  { id: 'complete', title: '舰队训练完成', target: 'naval-enemy-board', mode: 'read', chapter: '训练完成', description: '全部八种道具已经完成演练：防空炮、战斗机、轰炸机、鱼雷机、雷达、水雷、潜艇与隐身图层。道具用后切回舰炮。击毁对方全部舰船获胜。', continueLabel: '完成教程' }
]

export const NAVAL_TUTORIAL_LESSONS = LESSONS.map(lesson => ({ ...lesson }))
const MOBILE_OPENERS = {
  move: { id: 'fleet-open', title: '右侧选择舰船', target: 'naval-fleet-roster', subTarget: 'button', mode: 'read', chapter: '手机布阵', description: '右侧常驻舰队列表。选中舰船后，直接点左侧棋盘移动，不需要收起列表。' },
  clear: { id: 'tools-open', title: '切换布阵工具', target: 'naval-mobile-tools-toggle', panel: 'tools', mode: 'action', chapter: '手机布阵', description: '点击右侧工具标签。清空、随机、旋转和保存阵型都在这里；左侧棋盘始终可见。' },
  'buy-fighter': { id: 'supply-open', title: '右侧购买战备', target: 'naval-buy-fighter', mode: 'read', chapter: '手机补给', description: '右侧已经切换为道具商店。购买水雷或防空炮后，直接点击左侧海域部署，商店保持可见。' },
  hit: { id: 'weapons-open', title: '找到攻击方式', target: 'naval-mobile-weapons-toggle', panel: 'weapons', mode: 'action', chapter: '手机交战', description: '手机会收起攻击栏，给双方海域留出空间。点击亮起的准星按钮，展开攻击方式。' }
}
const MOBILE_LESSONS = LESSONS.flatMap(lesson => {
  const sequence = MOBILE_OPENERS[lesson.id] ? [{ ...MOBILE_OPENERS[lesson.id] }] : []
  if (lesson.id === 'hit') sequence.push({ id: 'select-shot', title: '选择舰炮后瞄准', target: 'naval-weapon-shot', mode: 'action', chapter: '手机交战', description: '点选舰炮。攻击栏会自动收起，随后直接点右侧海域发射；切换其他道具也是同样的操作。' })
  sequence.push({ ...lesson })
  return sequence
})
export const NAVAL_MOBILE_TUTORIAL_LESSONS = MOBILE_LESSONS.map(lesson => ({ ...lesson }))
const deviceOf = value => value === 'mobile' ? 'mobile' : 'desktop'
const lessonsFor = session => session?.device === 'mobile' ? MOBILE_LESSONS : LESSONS

export function navalTutorialStorageKey(device = 'desktop') {
  return deviceOf(device) === 'mobile' ? NAVAL_MOBILE_TUTORIAL_STORAGE_KEY : NAVAL_TUTORIAL_STORAGE_KEY
}

function trainingFleet() {
  const positions = [[2, 2, 1, 1], [5, 1, 1, 1], [9, 1, 1, 1], [11, 6, 1, 1],
    [0, 5, 1, 2], [3, 6, 1, 2], [6, 5, 1, 2], [0, 9, 3, 1], [5, 9, 3, 1], [11, 8, 1, 4]]
  return FLEET.map((ship, index) => ({ ...ship, x: positions[index][0], y: positions[index][1], w: positions[index][2], h: positions[index][3], hits: [] }))
}

function waterCell(ships) {
  const occupied = new Set(ships.flatMap(shipCells).map(key))
  for (let y = 0; y < 12; y++) for (let x = 0; x < 12; x++) if (!occupied.has(`${x},${y}`)) return { x, y }
  return { x: 0, y: 0 }
}

function moveCell(session) {
  const ships = session.room.players[0].ships
  const ship = ships.find(value => value.id === session.flags.selectedShip) || ships[0]
  if (!ship) return { x: 0, y: 0, w: 1, h: 1 }
  for (let y = 0; y < 12; y++) for (let x = 0; x < 12; x++) {
    if ((x !== ship.x || y !== ship.y) && legalPlacement(ships, { ...ship, x, y })) return { x, y, w: ship.w, h: ship.h }
  }
  return { x: ship.x, y: ship.y, w: ship.w, h: ship.h }
}

export function createNavalTutorial(now = Date.now(), { device = 'desktop' } = {}) {
  const room = createLocalGame(now)
  room.id = 'naval-tutorial'
  room.tutorial = true
  room.deadline = now + HOUR
  room.players[0].name = '训练舰队'
  room.players[0].ships = trainingFleet()
  room.players[1].name = '教官舰队'
  room.players[1].ships = trainingFleet()
  room.players[1].mines = [{ x: 4, y: 3 }, { x: 1, y: 11 }]
  room.players[1].aa = [4]
  room.players[1].inventory = Object.fromEntries(Object.keys(ITEMS).map(name => [name, name === 'antiAir' || name === 'mine' ? 1 : 0]))
  room.players[1].coins = 265
  return {
    version: VERSION, device: deviceOf(device), step: 0, completed: false, room,
    flags: { shipSelected: false, selectedShip: '', lastAction: null, openPanel: device === 'mobile' ? 'fleet' : null }, selectedWeapon: 'shot',
    targets: { hit: { x: 2, y: 2 }, mine: { x: 4, y: 3 }, radar: { x: 4, y: 4, w: 3, h: 3 }, fighter: { x: 0, y: 4, w: 8, h: 2 }, 'fighter-strafe': { x: 0, y: 6, w: 8, h: 2 }, antiAir: { x: 0, y: 7, w: 12, h: 1 }, bomber: { x: 8, y: 0, w: 3, h: 3 }, torpedo: { x: 0, y: 9, w: 12, h: 1 }, submarine: [{ x: 7, y: 4 }, { x: 8, y: 4 }, { x: 9, y: 4 }], 'submarine-mine': [{ x: 3, y: 11 }, { x: 4, y: 11 }, { x: 5, y: 11 }] }
  }
}

function isSession(value) {
  return value?.version === VERSION && [undefined, 'desktop', 'mobile'].includes(value.device) && Number.isInteger(value.step) && value.step >= 0 && value.step <= lessonsFor(value).length &&
    value.room?.id === 'naval-tutorial' && value.room.mode === 'AI' && value.room.players?.length === 2 &&
    value.room.players.every(player => Array.isArray(player.ships) && Array.isArray(player.shots)) &&
    value.flags && value.targets && typeof value.completed === 'boolean'
}

export function loadNavalTutorial(storage, { device = 'desktop' } = {}) {
  const requested = deviceOf(device)
  try {
    const source = (storage || globalThis.localStorage)?.getItem(navalTutorialStorageKey(requested))
    if (!source || source.length > 2_000_000) return createNavalTutorial(Date.now(), { device: requested })
    let session = JSON.parse(source)
    if (session?.version === 1) session = createNavalTutorial(Date.now(), { device: requested })
    return isSession(session) && deviceOf(session.device) === requested ? session : createNavalTutorial(Date.now(), { device: requested })
  } catch { return createNavalTutorial(Date.now(), { device: requested }) }
}

export function saveNavalTutorial(session, storage) {
  try {
    if (!isSession(session)) return false
    const destination = storage || globalThis.localStorage
    if (!destination) return false
    destination.setItem(navalTutorialStorageKey(session.device), JSON.stringify(session))
    return true
  } catch { return false }
}

export function getNavalTutorialStep(session) {
  const lessons = lessonsFor(session)
  const index = Math.min(lessons.length - 1, Math.max(0, session?.step || 0))
  const step = { ...lessons[index], index, total: lessons.length, device: deviceOf(session?.device), canContinue: true }
  if (step.id === 'move') {
    if (!session.flags.shipSelected) step.description = '先在亮起的舰队列表中，选择一艘你要移动的舰船。'
    else { step.target = 'naval-own-board'; step.cell = moveCell(session); step.description = `点击亮起的 ${coordinate(step.cell)}，将选中的舰船移动到这里。舰船不能重叠、越界或彼此紧邻。` }
  }
  if (step.id === 'place-mine') { step.cell = waterCell(session.room.players[0].ships); step.description = `点击亮起的 ${coordinate(step.cell)}，部署刚购买的水雷。被舰炮或飞机炸中会反击攻击方同坐标；被鱼雷或潜艇引爆不会反击，并能阻挡它们。` }
  if (step.id === 'mine-backfire') step.cell = { ...session.targets.mine }
  if (step.id === 'place-aa') step.cell = { ...session.targets.antiAir }
  if (step.id === 'coat') { const ship = session.room.players[0].ships[0]; step.cell = { x: ship.x, y: ship.y, w: ship.w, h: ship.h }; step.description = '点击亮起的己方舰船涂装，绿色描边表示它不会被雷达发现。补给期间，再点已部署的涂层、水雷或防空炮会选中它，然后点新位置或拖动来调整，不会直接删除。' }
  if (step.id === 'stealth-demo') { const ship = session.room.players[0].ships.find(ship => ship.stealth); step.cell = { x: Math.min(9,ship?.x || 0), y: Math.min(9,ship?.y || 0), w: 3, h: 3 } }
  const target = session.targets[step.id]
  const actionTarget = step.weapon ? Array.isArray(target) ? target[0] : target : null
  if (actionTarget) {
    if (session.selectedWeapon !== step.weapon) {
      step.target = `naval-weapon-${step.weapon}`
      step.description = `先选择${step.weapon === 'shot' ? '舰炮' : ITEMS[step.weapon].name}，再对亮起的海域执行这次行动。`
    } else {
      step.target = 'naval-enemy-board'; step.cell = { ...actionTarget }
      step.description = {
        hit: `教官舰队的一艘巡逻艇位于 ${coordinate(actionTarget)}。点击它，观察命中与击毁效果。`,
        mine: `点击 ${coordinate(actionTarget)}，这里有一枚训练水雷。观察右侧水雷爆炸，以及左侧己方同坐标的反击爆炸；引爆水雷不奖励追加攻击。`,
        radar: `点击亮起区域左上角 ${coordinate(actionTarget)}，扫描这片 3 × 3 海域。探测结果只对你可见，不会造成伤害。`,
        fighter: `点击 ${coordinate(actionTarget)} 选两行。第 5 行有防空炮，无论随机选哪一行都被击落；两行都有炮时，只消耗实际进攻行的炮。`
        , 'fighter-strafe': `点击 ${coordinate(actionTarget)} 选择 2 行 × 8 列。这两行没有防空炮，观察战斗机随机选一行，只扫射该行的 8 格，另一行不受攻击。`
        , bomber: `点击 ${coordinate(actionTarget)}，观察 3 × 3 区域内随机 5 至 7 格轰炸。结束后自动切回舰炮。`
        , torpedo: `点击目标投放格，观察鱼雷在敌方海域向右前进，在首个舰船或水雷处停止；敌方进攻己方时，完整路径镜像为从右向左。鱼雷引爆水雷不触发同坐标反击。`
        , submarine: `点 ${coordinate(actionTarget)} 定位三格潜艇虚影，无需逐格选择。确认后从左侧水下驶入，并随机向上、向下各发一颗鱼雷。敌方潜艇在己方棋盘从右侧镜像驶入，触雷不反击。`
        , 'submarine-mine': `点 ${coordinate(actionTarget)} 定位三格潜艇。它进入第二格就触雷，只有已进入海域的两格爆炸，第三格还没进入，不受影响，也不会发射鱼雷。`
      }[step.id]
    }
  }
  if (step.device === 'mobile') adaptMobileStep(step, session)
  return step
}

function adaptMobileStep(step, session) {
  const preparing = ['PLACEMENT', 'SUPPLY'].includes(session.room.phase)
  const panel = preparing ? session.room.players[0].stage === 'SUPPLY' ? 'supply' : session.flags.openPanel === 'tools' ? 'tools' : 'fleet' : session.flags.openPanel || null
  if (step.id === 'move' && !session.flags.shipSelected) {
    step.subTarget = 'button'
    step.description = '在右侧点选亮起的舰船，再直接点击左侧海域移动它。右侧操作区始终保留，不会盖住棋盘。'
  }
  if (step.id === 'welcome') {
    step.title = '手机舰队训练'
    step.description = '左侧是完整的 12 × 12 海域，右侧是操作区。10 艘舰船之间至少空一格，斜角也不能相邻。交战时玩家信息和倒计时显示在各自棋盘上方。'
    step.cell = { x: 0, y: 0, w: 5, h: 5 }
  }
  if (step.id === 'rotate') step.description = '在海域预览中点击旋转图标转向；朝向会保留，选择位置后用绿色勾确认。红色半透明表示此处不能放置，叉号可取消。'
  if (step.id === 'hit-bonus') {
    step.cell = { ...session.targets.hit }
    step.description = '命中格置灰，击沉后可继续进攻并重置 30 秒计时。已击沉舰船周边一格，包括斜角，都不会再有舰船。'
  }
  if (step.id === 'complete') {
    step.cell = { ...session.targets.hit }
    step.description = '八种道具全部演练完成。准星展开攻击方式，选择后点敌方海域预览，再点勾确认、叉取消。用完道具自动回到舰炮。左右双方的进攻提示与倒计时在各自棋盘上方。'
  }
  const requiredPanel = ['naval-fleet-roster', 'naval-rotate-ship'].includes(step.target) ? 'fleet'
    : ['naval-clear-layout', 'naval-random-layout'].includes(step.target) ? 'tools'
      : step.target.startsWith('naval-buy-') ? 'supply'
        : step.target.startsWith('naval-weapon-') ? 'weapons' : null
  if (requiredPanel && panel !== requiredPanel) {
    step.target = `naval-mobile-${requiredPanel}-toggle`
    step.subTarget = undefined
    step.cell = undefined
    step.description = ({ fleet: '先点击右侧舰队标签，切回舰船列表，再选择舰船。', tools: '先点击右侧工具标签，切换布阵工具。', supply: '右侧是战备商店，先选择本次需要的道具。', weapons: '先点击亮起的准星按钮，展开攻击方式，再选择本次道具。' })[requiredPanel]
    step.pendingPanel = requiredPanel
    step.mode = 'action'
  } else if (!preparing && !requiredPanel && !step.panel && panel && (step.target.endsWith('-board') || ['naval-layout-next', 'naval-supply-ready'].includes(step.target))) {
    step.target = `naval-mobile-${panel}-toggle`
    step.subTarget = undefined
    step.cell = undefined
    step.description = '点击亮起的按钮收起面板，将海域完整露出，再继续当前操作。'
    step.pendingPanel = null
    step.mode = 'action'
  }
}

function expectedPurchase(id) { return ({ 'buy-fighter': 'fighter', 'buy-mine': 'mine', 'buy-aa': 'antiAir', 'buy-radar': 'radar', 'buy-bomber': 'bomber', 'buy-torpedo': 'torpedo', 'buy-submarine': 'submarine', 'buy-stealth': 'stealth' })[id] }

export function advanceNavalTutorial(current, event = {}) {
  if (!isSession(current) || current.completed) return current
  const session = clone(current), value = typeof event === 'string' ? { type: event } : event
  const step = getNavalTutorialStep(session)
  if (value.type === 'SUBMARINE_TARGET') { session.flags.submarineTargets = clone(value.targets); return session }
  if (value.type === 'PANEL') {
    if (!['fleet', 'tools', 'supply', 'weapons', null].includes(value.panel)) return current
    session.flags.openPanel = value.panel
    if (step.panel !== value.panel) return session
    session.flags.lastAction = value.type; session.step++; session.completed = session.step >= lessonsFor(session).length
    return session
  }
  if (value.type === 'WEAPON') {
    session.selectedWeapon = value.weapon || 'shot'
    if (step.id !== 'select-shot' || value.weapon !== 'shot') return session
    session.flags.lastAction = value.type; session.step++
    return session
  }
  if (value.type === 'SELECT_SHIP' && step.id === 'move') {
    const id = value.shipId || value.ship?.id || value.id
    if (!session.room.players[0].ships.some(ship => ship.id === id)) return current
    session.flags.shipSelected = true; session.flags.selectedShip = id; return session
  }
  let done = false
  if (step.mode === 'read') done = value.type === 'CONTINUE'
  if (step.id === 'move') done = value.type === 'MOVE_SHIP' && session.flags.shipSelected
  if (step.id === 'clear') done = value.type === 'CLEAR_FLEET'
  if (step.id === 'randomize') done = value.type === 'RANDOMIZE' && !validateFleet(session.room.players[0].ships)
  if (step.id === 'rotate') done = value.type === 'ROTATE_SHIP'
  if (step.id === 'layout') done = value.type === 'ACTION' && value.action?.type === 'LAYOUT_READY' && session.room.players[0].stage === 'SUPPLY'
  if (expectedPurchase(step.id)) done = value.type === 'PURCHASE' && value.key === expectedPurchase(step.id) && session.room.players[0].inventory[value.key] >= (['buy-submarine','buy-fighter'].includes(step.id) ? 2 : 1)
  if (step.id === 'coat') done = value.type === 'COAT_SHIP' && session.room.players[0].ships[0].stealth
  if (step.id === 'place-mine') done = value.type === 'PLACE_MINE' && session.room.players[0].mines.length > 0
  if (step.id === 'place-aa') done = value.type === 'PLACE_AA' && session.room.players[0].aa.includes(session.targets.antiAir.y)
  if (step.id === 'ready') done = value.type === 'ACTION' && (value.action?.type === 'READY' || value.action?.ready) && session.room.phase === 'BATTLE'
  if (step.weapon) {
    const action = value.action, target = session.targets[step.id]
    done = value.type === 'ACTION' && action?.type === 'ATTACK' && action.weapon === step.weapon && (Array.isArray(target) ? JSON.stringify(action.targets) === JSON.stringify(target) : action.x === target.x && action.y === target.y)
    if (done) { session.room.turn = 0; session.room._aiNextAt = null; session.flags.submarineTargets = [] }
  }
  if (!done) return current
  session.flags.lastAction = value.type
  session.step++
  if (step.id === 'ready') {
    const ship = session.room.players[0].ships.find(ship => ship.stealth), x = Math.min(9,ship?.x || 0), y = Math.min(9,ship?.y || 0)
    session.room.players[1].radar = Array.from({ length: 9 }, (_, i) => { const cell = { x: x + i % 3, y: y + Math.floor(i / 3) }; return { ...cell, occupied: session.room.players[0].ships.some(ship => !ship.stealth && shipCells(ship).some(point => key(point) === key(cell))) } })
    session.room.events.push({ id: `tutorial-stealth-${++session.room._sequence}`, replayOnMount: true, player: 1, weapon: 'radar', cells: [], x, y, text: '隐身涂层避开了教官的雷达扫描' })
  }
  session.completed = session.step >= lessonsFor(session).length
  return session
}

export function prepareTutorialAction(current, action, now = Date.now()) {
  const session = clone(current), room = session.room, step = getNavalTutorialStep(session)
  if (session.completed) throw new Error('训练已完成，请返回港口进入正式游戏')
  room.deadline = now + HOUR
  room._aiNextAt = null
  if (action.type === 'ATTACK') {
    const target = session.targets[step.id]
    if (!target || action.weapon !== step.weapon || (Array.isArray(target) ? JSON.stringify(action.targets) !== JSON.stringify(target) : action.x !== target.x || action.y !== target.y)) throw new Error('请先按教程提示操作亮起的目标')
    room.turn = 0
  }
  return { room, action: clone(action) }
}
