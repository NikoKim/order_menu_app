import { MILITARY_SHIPS, createMilitaryGame, militaryActions, militaryAttack } from './navalMilitary.js'

const read = (id, title, description, extra = {}) => ({ id, title, description, mode: 'read', target: 'naval-own-board', ...extra })
const buy = (kind, description) => ({ id: `buy-${kind}`, title: `购买${MILITARY_SHIPS[kind].name}`, description, mode: 'action', buy: kind, target: `naval-buy-${kind}` })
const exercise = (id, title, kind, weapon, description, cell, extra = {}) => ({ id, title, kind, weapon, description, cell, mode: 'action', target: `naval-weapon-${weapon}-${kind}`, ...extra })
const lessons = [
  read('welcome', '军事舰队训练', '完整演习分为采购部署、舰载功能、战损与结算。部署限时 8 分钟，每局 300 金币，不购买道具；金币不足时可用减号退购。'),
  { id: 'shop', title: '打开舰船采购', mode: 'action', target: 'naval-mobile-shop-toggle', description: '进入采购标签，右上方显示剩余金币。' },
  buy('carrier', '50 金币，1 × 4。中间两格各停一架战斗机，每架独立起飞；舰体上的飞机不是额外购买的道具。'),
  read('variants', '同舰体切换类型', '加号后的旋转箭头切换舰船型号。航母可选双战斗机、单轰炸机或单鱼雷机，驱逐舰可选防空、雷达、布雷、干扰或深水炸弹；雷达与干扰均 20 金币。型号切换不改变已购舰船。'),
  buy('cruiser', '30 金币，1 × 3。舰首一门主炮，单格炮击；这是手机上唯一不需要勾叉确认的军事攻击。'),
  buy('battleship', '35 金币，1 × 3。首尾各一门主炮。沿舰体朝向选择连续三格，在其中随机攻击 1 或 2 格，取决于剩余主炮数。'),
  buy('submarine', '50 金币，1 × 3。沿行潜航进入敌海，抵达后向上、下各发一颗鱼雷。遭遇水雷或中部被炮击会失去整艘潜艇；两侧受击不会直接击沉。'),
  buy('radar', '20 金币。切换到雷达型号后购买，舰首雷达提供 2 × 2 扫描。'),
  buy('antiAir', '30 金币。切换到防空型号后购买，覆盖舰体及周围一格；部署时覆盖可见。'),
  buy('mineLayer', '30 金币。切换到布雷型号后购买，每艘最多放置 10 颗水雷；每回合可逐颗放置两颗，第一颗后继续，第二颗后换手。'),
  buy('depthCharge','25 金币，1 × 2。两格各一门深弹发射器，前格负责上侧、后格负责下侧；受损一格仅失去对应侧发射，撞舰双方全沉并波及碰撞两格。'),
  buy('decoy', '15 金币，1 × 2。无攻击能力，只能在雷达扫描中伪装成舰艇。'),
  read('caps', '共享购买额度', '当前舰队花费 285，剩余 15。三种航母共享 2 艘额度；战列舰、巡洋舰和潜艇共享 4 艘；五种驱逐舰共享 6 艘；佯攻舰另限 3 艘。'),
  { id: 'fleet', title: '查看已购舰队', mode: 'action', target: 'naval-mobile-fleet-toggle', description: '回到舰队标签，选择舰船模型进行部署。虚线表示尚未部署。' },
  { id: 'rotate', title: '旋转舰船', mode: 'action', target: 'naval-fleet-roster', subTarget: '.fleet-rotate, .prep-rotate', description: '预览时同时显示旋转、勾和叉，可先旋转再确认；每次顺时针 90°。主炮、甲板和干扰方向随舰体转动，飞机进攻方向不会随舰体改变。' },
  { id: 'manual', title: '手动部署一艘', mode: 'action', target: 'naval-own-board', cell: { x: 1, y: 1, w: 4, h: 4 }, description: '在亮起的海域放置选中舰船。手机先预览，再用勾确认或叉取消；舰船间隔至少一格，斜角也不能接触。' },
  { id: 'tools', title: '阵型工具', mode: 'action', target: 'naval-mobile-tools-toggle', description: '打开阵型工具。舰船可以拖动重排、旋转，也能清空或随机部署。' },
  { id: 'clear', title: '清空部署位置', mode: 'action', target: 'naval-clear-layout', description: '清空只把已购舰船收回待部署列表，不退购、不扣金币。' },
  { id: 'layout', title: '随机部署舰队', mode: 'action', target: 'naval-random-layout', description: '自动安排所有已购舰船。观察防空覆盖，之后仍可以手动调整舰首朝向和阵型。' },
  { id: 'ready', title: '完成战备', mode: 'action', target: 'naval-layout-next', description: '所有舰船部署后出击，不再进入经典模式的道具补给阶段。接下来进入固定靶场，逐项实操。' },
  read('turns', '每回合一次行动', '军事模式即使命中也换手，扫描和布雷同样占一回合。每回合 30 秒，最后 5 秒有声音提示。攻击栏按数字 1–9 切换，手机除巡洋舰单格炮击外都需勾叉确认。', { practice: 'standard' }),
  exercise('scan', '雷达侦察', 'radar', 'radar', '扫描亮起的 2 × 2。信号只表示存在舰体，不显示具体型号，佯攻舰也有信号。', { x: 0, y: 0, w: 2, h: 2 }),
  exercise('gun', '巡洋舰单格炮击', 'cruiser', 'shot', '攻击一个格子。巡洋舰舰首主炮被命中后就无法炮击，其他艇身仍可存活。', { x: 0, y: 1 }),
  exercise('broadside', '战列舰齐射', 'battleship', 'shot', '这艘战列舰横向部署，攻击栏标注 1×3（一行三列）；竖向时标注 3×1（三行一列）。因此选择横向连续三格，两门主炮各打不同的一格。三格已有一格命中时，另外两格首发概率各为 35%；已有两格命中时，剩余格为 45%。不显示逐格概率，手机需二次确认。', { x: 3, y: 5, w: 3, h: 1 }),
  exercise('flight', '战斗机起飞与返航', 'carrier', 'fighter', '选择横向 1 × 6，随机攻击其中 4–5 格。飞机固定从左侧飞入，越过目标后掉头；安全返回会降落回甲板，可再次使用。', { x: 2, y: 10, w: 6, h: 1 }),
  read('torpedo-brief','鱼雷航母专项','航母（鱼雷机）为 1 × 4、60 金币，中间甲板共用一架鱼雷机，与其他航母共享两艘额度。',{practice:'torpedo'}),
  exercise('torpedo-flight', '鱼雷机投放', 'torpedoCarrier', 'torpedo', '选投放点后鱼雷沿行前进。空水域的旧弹坑不会挡住鱼雷，但任何舰体，包括已击毁部位和残骸，都会阻挡；水雷也会拦截。', { x: 3, y: 4 }),
  read('depth-free-brief','深弹舰抵达与投弹','横向选择两格终点。突击舰从左入场，安全抵达后向上方一排两格、下方一排两格各随机攻击一格。经过的普通航路不留下受击标记。',{practice:'depth-free'}),
  exercise('depth-free','两侧深水炸弹','depthCharge','depthCharge','选择亮起的横向两格。观察新舰体、两套发射器、上下投弹与命中/落水表现。安全出击后仍可再次使用。',{x:4,y:6,w:2,h:1}),
  read('depth-collision-brief','撞舰与两格冲击','撞到任何舰体，包括残骸，突击舰和撞击目标整艘沉没，同时影响碰撞时所在的两格水域，不只是船体接触的那一格。',{practice:'depth-collision'}),
  exercise('depth-collision','撞舰实操','depthCharge','depthCharge','该航路上有一艘巡洋舰，选择两格终点，观察双方整舰沉没和碰撞水域的两格冲击。',{x:4,y:6,w:2,h:1}),
  read('depth-mine-brief','触雷与两格水域','突击舰触雷会沉没，水雷和当时实际在海域内的两格受影响；边界外部分不凭空进入海域。',{practice:'depth-mine'}),
  exercise('depth-mine','触雷实操','depthCharge','depthCharge','航路前方有水雷。突击舰会在触雷处停止，而不是在原定终点爆炸。',{x:4,y:6,w:2,h:1}),
  read('depth-damage-brief','单侧发射器损毁','前格发射器已经损毁，因此本次只能向下方投弹；尾格损毁则只能向上方投弹。两格都被毁才不能再出击。',{practice:'depth-damage'}),
  exercise('depth-damage','单侧深弹实操','depthCharge','depthCharge','再次选择横向两格，观察只剩一侧发射，未受损的那门仍然有效。',{x:4,y:6,w:2,h:1}),
  exercise('lay-mine', '在己方海域布雷', 'mineLayer', 'mine', '选择己方空格，勾叉只在己方出现。每艘可布 10 颗，每次选择一格确认，第一颗布置后继续放置第二颗；不得与舰体、水雷或已受击格重叠；整舰被击毁才失去布雷能力。', { x: 10, y: 10 }, { own: true }),
  exercise('submarine', '潜航与上下双鱼雷', 'submarine', 'submarine', '选择三格潜航目标。潜艇水下进入敌海后，随机选艇身位置向上、下各发一颗鱼雷，遇舰体或水雷停止。', { x: 4, y: 8, w: 3, h: 1 }),
  read('bomber-brief', '轰炸航母专项', '靶场换装为轰炸航母。中间两格共用一架轰炸机，任意一格甲板被击中都无法起飞。', { practice: 'bomber' }),
  exercise('bomber', '区域轰炸', 'bomberCarrier', 'bomber', '选择 3 × 3，随机攻击 5–6 个不同格子。飞行和返航仍沿所选行，不受航母方向影响。', { x: 3, y: 8, w: 3, h: 3 }),
  read('jammer-brief', '干扰与佯攻专项', '靶场设置了干扰驱逐舰。范围是舰首前方 4 格、横向 3 格，随旋转改变，不包含自己；舰首受损后失效。', { practice: 'jammer' }),
  exercise('jamming', '扫描受干扰区域', 'radar', 'radar', '扫描亮起区域：这里实际有舰船，但在干扰范围内不会显示雷达信号。', { x: 5, y: 5, w: 2, h: 2 }),
  exercise('decoy-scan', '识别雷达诱饵', 'radar', 'radar', '再扫描未受干扰的佯攻舰。雷达显示舰艇信号，不能仅凭信号判断它是否具备攻击能力。', { x: 0, y: 7, w: 2, h: 2 }),
  read('aa-brief', '防空与飞机损失专项', '前方有防空驱逐舰。飞机经过舰体及周围一格会被击落，先结算拦截，再结算同格攻击。防空炮格被毁后失效。', { practice: 'intercept' }),
  exercise('intercept', '穿越防空火力', 'carrier', 'fighter', '派战斗机经过防空区，观察拦截、坠落和甲板空位。飞机永久损失，另一架仍可使用。', { x: 2, y: 5, w: 6, h: 1 }),
  read('air-recon', '先侦察，再出动飞机', 'AI 会记住飞机被击落的位置，用炮击或雷达优先寻找附近的防空驱逐舰；未知航路只是风险，并非禁止出动；AI 比较预计新增伤害、功能摧毁和侦察收益，再扣一次损失成本。可排查防空或换航线，安全证据会降低空袭成本。雷达空信号可能受干扰，普通攻击候选耗尽后再回查。'),
  read('damage-brief', '功能部件损毁专项', '敌方将命中我方巡洋舰舰首。观察损坏标记、烟火和攻击栏中对应主炮的消失。受损的普通艇身不一定禁用功能。', { practice: 'damage' }),
  read('damage', '查看已失效主炮', '巡洋舰仍有两格舰体，但舰首主炮已毁。战列舰首尾主炮分别失效；战斗机甲板各自独立，轰炸机和鱼雷机则共用航母中间两格甲板。'),
  read('disabled-hull', '已定位舰体与隔离带', '公开命中证据若唯一确定舰体，AI 会排除舰体外周一格，不要求先击沉或破坏全部功能。舰体内尚未受损的格子仍可攻击，但优先仍有效的主炮、甲板等部件。布局尚不唯一时只排除所有候选都确认无效的格子。'),
  read('mine-brief', '潜艇触雷专项', '靶场航路布有水雷。潜艇触雷时，只爆炸已进入敌海的艇身部分；自己的整艘潜艇则永久损失。', { practice: 'mine' }),
  exercise('mine-collision', '潜艇触雷', 'submarine', 'submarine', '在亮起位置下潜，观察水雷爆炸。潜艇在己方原停泊位置的三格也会自动标为命中击毁，不能再用。', { x: 4, y: 5, w: 3, h: 1 }),
  read('backfire-brief', '水雷反击专项', '舰炮、战斗机、轰炸机引爆水雷，会反击攻击方同坐标海域一次，不递归连锁；鱼雷和潜艇触雷不反击。', { practice: 'backfire' }),
  exercise('backfire', '引爆水雷并观察反击', 'cruiser', 'shot', '攻击亮起的水雷格。己方同坐标也会爆炸，布雷和攻击时都要考虑自己的阵型。', { x: 0, y: 6 }),
  read('victory-brief', '最后的进攻力量', '对方只剩一门可用主炮及辅助舰。击毁最后主炮即可获胜，不需要清掉辅助舰或残骸。', { practice: 'victory' }),
  exercise('victory', '击毁最后主炮', 'cruiser', 'shot', '击中巡洋舰舰首。无可用主炮、战斗机、轰炸机、鱼雷机或潜艇就算战败，即使还有水雷和辅助舰。', { x: 0, y: 0 }),
  read('ai-tactics', 'AI 与观战', '正式对战中，AI 随机借鉴一份军事胜局：人人对局取胜者，人机仅取玩家获胜局。以其舰队布局和出手顺序为模板，作局部或大范围变动，并按当前情报调整，不机械复盘；无可用记录时才采用攻防策略搭配，允许保留金币；防空舰通常优先覆盖更多行并前置，少数采用其他部署。攻击结合已发现阵型及对手行为调整；确定敌方已无鱼雷机、潜艇后停止布雷，未识别舰艇仍视为潜在威胁。潜艇优先走已探明航路。顶部观众席按钮在电脑与手机均可查看观众、设置允许观战。'),
  read('complete', '训练完成', '你已完成采购部署、全部主动舰载功能、雷达隐蔽与诱饵、防空拦截、部件损毁、水雷和胜负训练。回港后可选人机或联机；房间、战绩、分享和回放标注规则模式，排行榜按当前模式区分。', { continueLabel: '完成教程' })
]
const copy = value => JSON.parse(JSON.stringify(value))
const sequence = s => s.device === 'mobile' ? lessons : lessons.filter(l => !['shop', 'fleet', 'tools'].includes(l.id))
const hull = (kind, x, y, rotation = 0) => ({ ...MILITARY_SHIPS[kind], kind, id: `training-${kind}`, x, y, rotation, w: rotation % 180 ? MILITARY_SHIPS[kind].h : 1, h: rotation % 180 ? 1 : MILITARY_SHIPS[kind].h, hits: [], lostPlanes: [], minesUsed: 0 })

// Fixed firing ranges keep every lesson available, independent of earlier aircraft losses.
function prepareRange(s, type) {
  const room = s.room
  room.phase = 'BATTLE'; room.mineActions=0; room.turn = 0; room.winner = null; room.deadline = Date.now() + 3600000; room.presentationUntil = 0
  room.players[0].ships = [hull(type === 'bomber' ? 'bomberCarrier' : type==='torpedo'?'torpedoCarrier':'carrier', 0, 0), hull('cruiser', 0, 6), hull('battleship', 3, 0, 90), hull('submarine', 3, 3), hull('radar', 6, 0), hull('antiAir', 6, 4), hull('jammer', 8, 0), hull('mineLayer', 10, 0), hull('decoy', 8, 4)]
  room.players[1].ships = [hull('cruiser', 0, 0), hull('cruiser', 8, 3), hull('decoy', 0, 7)]
  for (const p of room.players) { p.shots = []; p.mines = []; p.radar = []; p.inventory = {}; p.ready = true; p.stage = 'READY' }
  if(type.startsWith('depth-')){room.players[0].ships.push(hull('depthCharge',10,4));if(type==='depth-collision')room.players[1].ships.push(hull('cruiser',4,5));else if(type==='depth-mine')room.players[1].mines=[{x:2,y:6}];else if(type==='depth-damage'){room.players[0].ships.at(-1).hits=['10,4'];room.players[0].shots=[{x:10,y:4,result:'hit'}]}}
  if (type === 'jammer') room.players[1].ships.push(hull('jammer', 8, 6, 270), hull('mineLayer', 5, 6))
  if (type === 'intercept') room.players[1].ships.push(hull('antiAir', 2, 4))
  if (type === 'mine') room.players[1].mines = [{ x: 0, y: 5 }]
  if (type === 'backfire') room.players[1].mines = [{ x: 0, y: 6 }]
  if (type === 'victory') room.players[1].ships = [hull('cruiser', 0, 0), hull('radar', 5, 5), hull('mineLayer', 8, 8), hull('decoy', 0, 7)]
  if (type === 'damage') {
    const gun = militaryActions(room.players[1]).find(a => a.weapon === 'shot')
    room.turn = 1; militaryAttack(room, 1, { ...gun, x: 0, y: 6 })
  }
}
export function createMilitaryTutorial(now = Date.now(), options = {}) {
  return { ruleSet: 'MILITARY', room: createMilitaryGame(now), step: 0, device: options.device || 'desktop', flags: {}, version: 6, selectedWeapon: 'shot', completed: false }
}
export function militaryTutorialStep(s) {
  const list = sequence(s), l = list[s.step] || list.at(-1)
  const result = { ...l, ...(l.id==='lay-mine'&&s.room.mineActions?{cell:{x:11,y:10},description:'第一颗已布置，仍是你的回合。再选择一格确认第二颗后换手。'}:{}), index: s.step, total: list.length, device: s.device, chapter: s.room.phase !== 'PLACEMENT' ? '舰载功能实操' : '采购与部署', canContinue: true }
  if (l.buy && MILITARY_SHIPS[l.buy].group === 'destroyer' && s.flags.destroyerType !== l.buy) return { ...result, target: 'naval-type-destroyer', description: `点击加号后的切型按钮，切换至${MILITARY_SHIPS[l.buy].name}，然后购买。` }
  if (l.weapon && s.device === 'mobile' && (s.selectedWeapon !== l.weapon || s.flags.selectedKind !== l.kind) && s.flags.openPanel !== 'weapons') return { ...result, target: 'naval-mobile-weapons-toggle', cell: undefined, description: '展开攻击方式，选择本次演习的舰载功能。' }
  if (l.weapon && s.selectedWeapon === l.weapon && s.flags.selectedKind === l.kind) return { ...result, target: l.own ? 'naval-own-board' : 'naval-enemy-board', description: `${result.description} 点击亮起的海域执行。` }
  if (l.weapon) result.cell = undefined
  return result
}
export function advanceMilitaryTutorial(session, event) {
  const s = copy(session), list = sequence(s), l = list[s.step]
  if (event.type === 'TYPE') s.flags.destroyerType = event.kind
  if (event.type === 'PANEL') s.flags.openPanel = event.panel
  if (event.type === 'WEAPON') { s.selectedWeapon = event.weapon; s.flags.selectedKind = event.kind }
  const done = event.type === 'CONTINUE' && l.mode === 'read'
    || l.buy && event.type === 'PURCHASE' && event.key === l.buy
    || l.id === 'rotate' && event.type === 'ROTATE_SHIP'
    || l.id === 'manual' && event.type === 'MOVE_SHIP'
    || l.id === 'clear' && event.type === 'CLEAR_FLEET'
    || l.id === 'layout' && event.type === 'RANDOMIZE'
    || l.id === 'ready' && event.type === 'ACTION' && s.room.phase === 'BATTLE'
    || l.weapon && event.type === 'ACTION' && event.action.type === 'ATTACK' && event.action.weapon === l.weapon && event.action.kind === l.kind && (l.weapon!=='mine'||!s.room.mineActions)
    || ['shop', 'fleet', 'tools'].includes(l.id) && event.type === 'PANEL' && event.panel === (l.id === 'shop' ? 'supply' : l.id)
  if (done) {
    if (s.step < list.length - 1) {
      s.step++; s.selectedWeapon = ''; s.flags.selectedKind = null
      const next = list[s.step]
      if (next.practice) prepareRange(s, next.practice)
    } else s.completed = true
  }
  return s
}
export function tickMilitaryTutorial(current, now = Date.now()) {
  if (current.phase !== 'BATTLE' || current.turn !== 1 || now < (current.nextAiAt || 0) || current.presentationUntil > now) return current
  const next = copy(current), gun = militaryActions(next.players[1]).find(a => a.weapon === 'shot')
  const target = Array.from({ length: 144 }, (_, i) => ({ x: i % 12, y: Math.floor(i / 12) })).find(c => !next.players[0].ships.some(s => c.x >= s.x && c.x < s.x + s.w && c.y >= s.y && c.y < s.y + s.h) && !next.players[0].shots.some(p => p.x === c.x && p.y === c.y) && !next.players[0].mines.some(p => p.x === c.x && p.y === c.y))
  if (gun && target) militaryAttack(next, 1, { ...gun, ...target }, now)
  return next
}
