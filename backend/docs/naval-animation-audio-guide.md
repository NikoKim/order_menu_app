# 海战棋动画与音效实现及新模式接入

## 深弹舰与勋章表现（2026-11-01）

- 新舰体 military/depth-charge-destroyer.png 与独立模块 depth-charge.png：首尾两套发射器，drawMilitaryShip 沿用格级损坏、灰化、火花与烟雾；一门受损仅禁用一侧。航行时也显示完整新舰体，单侧损坏的中间行出击显示对应受损模块。
- 专用移动水花资产 military/surface-wake.png（2026-11-02 修订）尖端锚定船头，白沫向船体两侧向后展开；返航复用同一贴图并旋转 180°、切换船头锚点。停船投弹时隐藏，碰撞时停止。Phaser Container 按子对象顺序绘制，必须 addAt 到舰体之前，不能只设低 depth，否则水花仍可能盖住舰体。
- navalScene.depthChargeSail：驶入 1250ms → 到位停顿 140ms → 两侧深弹抛物线投掷 650ms → 对目标播放经典爆炸/落水 → 等待后驶离。只创建未损模块的投掷路径。碰撞/触雷在 route 最后锚点立即中断航行，不继续走到终点；双方沉没与当时两格冲击由引擎事件驱动。
- 沿途没有受击标记，cells 在投掷落点/碰撞时才逐格 reveal；正常阻塞窗口 3500ms，有沉舰时 5200ms。复用 submarine/deploy/explosion/splash/mine/sink 独立 WAV，不引入新的音频系统。
- 新武器必须贯通页面 combatWeapons、Phaser COMBAT、引擎 action/targets、回放 weapons、分享与统计白名单。此次修复了页面漏掉 depthCharge 导致引擎已结算但动画事件未送达的问题。
- naval-depth-charge-smoke.mjs 检测真实 canvas 航行位置变化、到达前 visibleShots=0、抵达后的 1/2 个投掷、延迟伤害揭示、碰撞中断；保存 PC/手机截图，不以“事件存在”代替动画验证。
- 六枚勋章使用透明 128×128 像素 PNG：经典蓝绿锚形、军事绿色双炮盾牌，铜/银/金区分 3/5/10 连胜。佩戴图放在共用头像下方并预留空间，不裁切头像。
- 匹配/每局再开场面带双方头像与勋章；勋章墙内容嵌入现有窗口，不再有独立容器、遮罩和关闭按钮。大厅及电脑端复用 NavalCommunity；手机对局点击真人头像复用 mobile-utility-panel 右侧弹窗。对局中无勋章操作 icon，AI 头像无勋章墙。原八个表情保留，本次新增表情资产及入口按用户要求删除。


## 舰船共享上限修订（2026-11-01，当前规则）

- 1×4 航母跨型号合计最多 2 艘；1×3 战列舰、巡洋舰、潜艇合计最多 4 艘；1×2 驱逐舰跨功能合计最多 6 艘。
- 佯攻舰不占驱逐舰额度，仍独立最多 3 艘；300 金币预算及其他规则不变。
- 前后端型号数据、共享校验、采购按钮、AI 配舰/剩余舰长判断、历史模板校验和新手教程同步。下方旧版 2/3/5 等额度属于历史描述，以本节为准。


## 胜局模板与行列标注（2026-11-01）

- 攻击区域采用行×列：横向三格标注 1×3，竖向三格标注 3×1；内部 w/h 仍是宽/高，不交换攻击几何。手机航母、战列舰攻击信息均单行小字，按按钮容器及文字长度缩小，不换行或省略。
- AI 从最近 500 份军事战绩中筛出可复用胜局：人人取胜者，人机只取玩家获胜；必须实际开战、有攻击流程、舰队符合当前预算与额度。随机挑一份，脱敏成 template-N 舰船编号，只返回舰队及攻击参数，不返回用户、房间、战绩编号或姓名。
- 服务端人机开局/续局直接安装模板；游客人机通过 military-template 接口读取同样模板，失败或没有候选时尝试本机胜局，再回退策略配舰。老版已取消舰种的记录不复用。
- 大约 40% 作大范围合法重新布阵，其他作局部移动/旋转；攻击流程有概率镜像或邻格扰动。保留舰船型号和预算，清除全部旧伤、损失及消耗。
- 按 AI 已执行行动数向前推进模板出手流程，给对应舰船/武器/目标附近加权；目标无效、部件失效时自然改选。防空与潜航安全过滤、当前命中追击和失能推断仍优先，不直接使用旧对手的舰艇位置，也不修改真实伤害。
- 模板存于私有 aiTemplate 状态，不在对局视图中泄露。它是战绩驱动的启发式借鉴，并非模型训练。


### 情报与攻击标签修订（2026-11-01）

- 战列舰攻击按钮按实际舰体朝向标注 1×3/3×1，手机较小字号并允许换行，不绘制逐格概率。
- AI 读取公开飞机事件的 route 最后格定位防空排查区域：destroyed 时末格代表拦截点，末格之前是已通过的安全路径；安全返航事件的完整航路可以排除防空假设。不要用动画临时坐标替代引擎 route。
- AI 战术失能推断仅改变选点与周边排除，不把未受击艇身改成残骸，不额外触发击沉动画或音效。
- 教程 v5 新增防空排查、失能舰体推断说明，并同步 2/4/6 的共享购买额度；经典表现和复用音效不变。


### 后续修订：航母型号与预览（2026-11-01）

- 不再绘制战列舰逐格概率文本，仅显示攻击区域边框。
- torpedoCarrier 复用航母舰体与现有鱼雷机素材，中间两格是共用甲板；起降、飞行、鱼雷与音效仍复用 Phaser 实现。
- 水雷通常逐颗形成事件；第一颗表现完成后同一方继续，第二颗后换手，不要在动画回调中自行切换 turn。以引擎 mineActions/turn 为准。
- 手机布局确认组件支持 rotatable，同时展示旋转、确认、取消，且宽度由 78 增为 114，保持边缘避让。

## 最新规则表现修正（2026-11-01）

- 双水雷部署从事件 targets 逐格播放部署水花，回放按 targets 恢复一至两颗，不能只读 event.x/y。
- 战列舰目标预览仅显示区域，不显示逐格概率；引擎首发权重为一个旧命中时其余各 35%（旧格 30%），两个旧命中时剩余 45%（旧格各 27.5%）。炮弹落入旧受击格仅播放水花，不修改原始 shots/hits。
- 潜艇中部命中时事件补齐其他艇身格，复用原受击/击沉表现。

- 军事战斗机的 `targets` 仍为横向六格，但 `cells` 只包含随机选择的 4–5 格及防空前已到达的伤害，动画不能按目标框补齐未开火格。
- 经典和军事鱼雷现在被已毁舰体阻挡；事件包含终点的旧命中记录以定位爆炸和鱼雷停止，但不能再次加入 `player.shots` 或累计 `ship.hits`。
- 军事潜艇触雷后，原停泊艇身的全部新命中通过 `backfire` 投影到己方，复用现有爆炸、烟火和击沉。回放及旧续局都补齐自己的受击格。
- 手机末五秒播放共享 `tick.wav`，音量从 .27 调至 .65。是否提示取决于是否获得计时行动权，不再取决于菜单等 UI 是否遮住攻击操作；仍尊重全局静音、浏览器手势解锁和后台暂停。
- 采购缩略图直接调用 `drawMilitaryShip`，与棋盘模型相同；布雷舰的剩余弹药标记扩为两行，共十颗。

更新时间：2026-11-01。本文面向开发新模式的前端、游戏表现和素材开发人员，说明现有实现可以复用什么、如何调用、时序如何同步，以及哪些地方尚未形成通用能力。

本文不是玩法或部署交接。综合交接见 [naval-handoff-2026-11-01.md](naval-handoff-2026-11-01.md)。以下路径均相对于仓库根目录 `E:\order_menu_app`；前端工程为 `moments_web_frontend`。

## 1 先理解当前实现边界

| 层 | 现有实现 | 接新模式时的职责 |
| --- | --- | --- |
| 玩法与裁决 | 本地 JS 引擎、服务器 Java 引擎 | 计算最终攻击格、路径、拦截、击沉等，不在渲染器随机决定结果 |
| 经典棋盘 | `NavalBoard.vue` → `mountNavalGame()` → Phaser Scene | 接收状态快照及事件，播放素材、粒子、Tween、音效 |
| 军事棋盘 | `NavalBoard.vue` → `NavalMilitaryBoard.vue` | 原生 Canvas 2D + `requestAnimationFrame`，不是 Phaser |
| 页面及转场 | `NavalBattle.vue`、Vue Transition、CSS | 入场、匹配、阶段横幅、弹层、全屏、计时解锁 |
| 音频 | `navalAudio.js` + 离线生成的 WAV | Web Audio 解码、缓存、静音、限流、播放和停止 |

**现有工程不是 Godot 工程，也没有可直接注册任意新模式的统一特效 SDK。** 可以复用素材、音频模块、经典 Scene 方法及页面壳，但仍需编写模式适配代码。不要把军事模式误认为已经自动继承了经典的逐格揭示、音效和结束确认。

### 关键源文件

| 文件 | 重点阅读内容 |
| --- | --- |
| `moments_web_frontend/src/games/navalScene.js` | `createNavalScene`、`mountNavalGame`、`playAttack`、各效果方法 |
| `moments_web_frontend/src/views/entertainment/NavalBoard.vue` | 快照组装、异步加载、事件与生命周期、镜像样式 |
| `moments_web_frontend/src/games/navalPresentation.js` | 鱼雷时长、反击事件分配 |
| `moments_web_frontend/src/games/navalAudio.js` | 全部运行时音频 API |
| `moments_web_frontend/scripts/build-naval-audio.mjs` | 离线声音设计、PCM WAV 编码 |
| `moments_web_frontend/src/views/entertainment/NavalMilitaryBoard.vue` | Canvas 渲染循环和军事动画时长 |
| `moments_web_frontend/src/games/navalMilitaryArt.js` | 军事舰体、功能部件、飞机、损毁绘制 |
| `moments_web_frontend/src/views/entertainment/NavalBattle.vue` | 音频解锁、横幅、双方忙碌状态、`PRESENTED`、倒计时音 |
| `moments_web_frontend/src/games/navalReplay.js`、`NavalReplay.vue` | 从事件还原状态，以及回放动画等待 |

## 2 素材规格和加载

### 2.1 当前资源目录与实际尺寸

经典资源根 URL 是 `/entertainment/naval/v2/`，对应 `moments_web_frontend/public/entertainment/naval/v2/`。

| 资源 | 文件尺寸 | 加载及用途 |
| --- | --- | --- |
| `patrol/destroyer/cruiser/battleship.png` | 各 256×256 | 按透明内容包围盒裁切，再按舰船格数等比适配 |
| `fighter/bomber/torpedo.png` | 各 256×256 | 飞机精灵；`torpedo.png` 是鱼雷机，不是水中鱼雷 |
| `antiAir/radar/mine/stealth/cannon.png` | 各 256×256 | 防御、雷达、道具或军事部件；并非全部都被经典 Scene 加载 |
| `submarine.png` | 256×384 | 三格潜艇，靠旋转和适配占格 |
| `torpedo-projectile.png` | 512×171 | 独立水中鱼雷素材，不能用飞机图替代 |
| `explosion.png`、`splash.png` | 各 512×512 | 4×4 帧表，每帧 128×128，共 16 帧 |
| `harbor.png` | 2172×724 | 港口背景 |
| `contact-sheet.png` | 1024×768 | 素材总览、背景回退，不是运行时精灵图集 |
| `supply.png` | 256×256 | 补给素材 |
| `military/carrier.png`、`military/utility-hull.png` | 各 887×1774 | 位于 `/entertainment/naval/` 下，军事舰体底图 |

专属聊天表情位于 `/entertainment/naval/stickers/{id}.png`，白名单在 `navalStickers.js`。不要把任意聊天文本当作资源 URL 加载。

### 2.2 Phaser 中的资源处理

`preload()` 对 `SPRITES` 数组逐个执行 `load.image()`，对两张效果帧表执行：

```js
this.load.spritesheet('explosion', '/entertainment/naval/v2/explosion.png', {
  frameWidth: 128, frameHeight: 128
})
```

`trimTexture(key)` 扫描 alpha > 40 的像素，建立 `trimmed` frame。`fittedSprite(x,y,key,width,height,rotation)` 使用该 frame，考虑 90° 旋转交换宽高，再等比缩放。新图应提供透明背景和完整轮廓，不要靠 PNG 外侧的大空白对齐模型。

残骸不是额外 PNG：加载时通过亮度 `0.2126R + 0.7152G + 0.0722B` 生成暗色 Canvas 纹理 `{key}-wreck`，再添加相同裁切 frame。经典当前只为舰船和飞机生成残骸。

当前 Phaser 后端是 `Phaser.CANVAS`。不能依赖 WebGL 专用特效或 `setTint()` 获得稳定的 Canvas 染色；潜艇代码虽然调用了 tint，复用时仍应以透明度或预生成贴图保证外观，残骸做法更可靠。

### 2.3 新素材接入检查

1. 保存透明 PNG；帧表固定单帧尺寸、间距和帧顺序。
2. 添加到 `SPRITES` 或单独的 `preload()` 分支，不能仅放文件而不注册纹理。
3. 注册帧动画，核对 `start/end/frameRate`，不要把静态图当帧表。
4. 根据是否需要残骸，扩展残骸生成条件，而不是引用不存在的 `{key}-wreck.png`。
5. 在实际大小的手机棋盘查看，不只检查原图。部件、鱼雷、旋转标识必须仍可辨认。
6. 发布时一起上传静态文件；缺图走 `callbacks.error`，不应只留下空白画布。

## 3 棋盘坐标与图层

### 3.1 逻辑画布

经典与军事均采用 624×624 的逻辑画布：边距 24、格子 48、海域 576、12×12。

```js
const cellLeft = x => 24 + x * 48
const center = x => 24 + (x + 0.5) * 48
const hullCenterX = ship => 24 + (ship.x + ship.w / 2) * 48
```

Phaser 配置：Canvas 渲染、`pixelArt:true`、`antialias:false`、`roundPixels:true`，目标 40 FPS，缩放为 `Phaser.Scale.FIT`。DOM 容器维持正方形，Canvas CSS 为 100% 宽高并使用 `image-rendering:pixelated`。

游戏时长使用毫秒，不以“经过多少帧”作为规则依据；低帧率时仍应按同一时间轴移动。

### 3.2 经典 Scene 图层

| 深度 | 内容 |
| ---: | --- |
| 0 | 海面 |
| 1 | 坐标轴 |
| 2 | 防御行底色 |
| 3 | 常驻水雷、防空图标、涂层装饰容器 |
| 4 | 普通舰船 |
| 6 | 命中灰格、伤害标记 |
| 6.3 | 沉船残骸 |
| 7 | 选区、雷达 |
| 8 | 拖动或部署虚影 |
| 9 至 11 | 尾迹、粒子、飞机、鱼雷、爆炸等 |

Phaser 容器的内部对象还受父容器整体深度影响；不要认为给容器内某个精灵 `setDepth(100)` 就能跨过任意外部 DOM。

### 3.3 左右镜像

经典左棋盘通过 Canvas CSS `scaleX(-1)` 整体镜像，逻辑动画仍正向计算。`inputX()` 把指针 x 转为 `624-x`，坐标字形预反转，键盘左右、确认框和教程聚光框分别补偿。

新模式复用左侧组件时，**不要再次把服务器 x 改成 `11-x`**，否则会二次镜像。水中鱼雷、潜艇、灰格及舰体必须一起翻转，不能只改变飞机起飞方向。

把触控屏幕坐标转为逻辑格子的公式是：

```js
const u = (event.clientX - bounds.left) / bounds.width
const logicalX = mirrored ? (1 - u) * 624 : u * 624
const x = Math.floor((logicalX - 24) / 48)
```

边框处可能得到 -1 或 12，必须再检查范围。

## 4 事件协议与逐格揭示

### 4.1 渲染输入

`NavalBoard` 给经典 Scene 的快照包含：

`player, hidden, interactive, label, preview, region, event, events, selected, radar, defenseSide`。

`events` 存在时优先于单个 `event`。游戏引擎先算出实际结果，渲染器不重新抽随机数决定命中。典型事件如下：

```js
{
  id: 72,                 // 当前对局内唯一；本地也允许字符串
  player: 0,              // 攻击者座位，不是目标棋盘索引
  weapon: 'torpedo',
  x: 2, y: 5,             // 投放或攻击起点
  cells: [
    { x: 2, y: 5, result: 'miss' },
    { x: 3, y: 5, result: 'hit' }
  ],
  text: '鱼雷命中'
}
```

附加字段：

| 字段 | 当前含义 |
| --- | --- |
| `row` | 被消耗的防空炮行；不等于飞机必然位于该行 |
| `targets` | 潜艇原始三格目标 |
| `route` | 进入路径锚点，潜艇允许边界外 x=-2/-1 |
| `torpedoes` | 潜艇两条已裁决的鱼雷路径，含发射起点 |
| `destroyed` | 潜艇或军事飞机是否被摧毁 |
| `backfire` | 反击到攻击者海域的已裁决格子 |
| `replayOnMount` | 前端表现提示，允许初次挂载时播放该事件；不是通用服务器玩法字段 |

军事事件还有 `shipId`、`plane`、`origin` 等舰载来源信息，应以 `NavalState.Event` 和军事引擎当前输出为准。

### 4.2 去重和初始挂载

- 构造 Scene 时，把已有且没有 `replayOnMount` 的事件加入 `seenEvents`，防止刷新页面重播全部历史。
- `updateSnapshot()` 只收集尚未见过的 ID，武器必须在 `COMBAT` 白名单中。
- 新事件的 `cells` 先进入 `unrevealed`。虽然状态已包含最终命中结果，伤害绘制和舰船命中显示会暂时隐藏这些格子。
- 到达轨迹对应时间后，调用 `reveal(cell)` 删除遮蔽并重绘，随后播放该格的爆炸或水花。
- 舰船沉没通过 `pendingSinks` 关联事件 ID，等该次攻击抵达才播放沉船，不能在服务器结果刚到时立即露出残骸。

不要为了修复一次漏播清空 `seenEvents`，这会让轮询消息反复爆炸。重开局或回放重置应明确销毁、重建 Scene，或设计独立的对局代次。

### 4.3 反击投影

`navalBoardEvents(events,board)` 把普通攻击送往防守方，把 `backfire` 投影成攻击方棋盘上的合成 `mine-rebound` 事件，ID 是 `{原ID}-backfire`。

舰炮反击延迟 650ms；飞机反击使用飞机到达对应 x 的时刻再加 300ms。该合成事件只用于表现，不代表额外一次行动或扣一次道具。

## 5 经典效果的具体实现与时序

以下时间以 `playAttack(event)` 被调用为 0；数值是现有代码常量，不是新模式必须照搬的规格。

### 5.1 帧动画和常驻损伤

| 动画 | 实现 |
| --- | --- |
| 爆炸 `burst` | `explosion` 的 0 至 15 帧，20 FPS，约 800ms |
| 水花 `water-impact` | `splash` 的 0 至 15 帧，20 FPS，约 800ms |
| 烟雾帧动画 `smoke-drift` | 爆炸图 11 至 15 帧，7 FPS；当前伤船主要使用粒子方法 |
| 伤船持续冒烟 | 每 720ms 尝试发射一次；取受损格，使用爆炸 11/12/13 帧，寿命 1100ms，向上漂移后销毁粒子发射器 |
| 入场 | Camera 350ms 淡入；初始舰船上移 20px、500ms 淡入滑回，按舰船索引每艘错开 22ms |

`effect(kind,x,y,size)` 会创建精灵、播放对应帧表，并在动画完成时销毁，不需要业务层逐帧更新图片地址。

### 5.2 舰炮

`projectile()` 用 Graphics 画弹道亮线和弹头，Tween 一个 `p:0→1`；350ms 到达目标格。

到达时揭示结果。命中播 100px 爆炸，落空播 90px 水花；首个主要命中可触发 240ms、强度 .0018 的 Camera shake。开炮开始调用 `playNavalSound('shot')`，落点再调用 `explosion/splash`。

普通攻击表现窗口不是仅弹道的 350ms，而是至少覆盖落点后 1200ms；新模式不能只报告飞行结束而忽略尾部效果。

### 5.3 战斗机和轰炸机

- 飞机精灵按 88×88 适配，逻辑 x 从 -55 飞到 690，线性 1850ms。
- `delayAt()` 按目标格 x 在飞行路线中的位置算抵达时间：`1850 * (center(x)+55) / 745`。
- 战斗机事件现在只包含被随机选中那一行的攻击格。两行预览不表示两行都播命中。
- 轰炸机的随机落点由引擎给出；Scene 仍按这些格子的 x 算时序，而不是将数组顺序当作投弹顺序。
- 起飞时分别调用 `fighter/bomber` 音效；格子到达时播放帧动画，主要落点才触发命中或水花音，避免每格同时叠加巨响。

### 5.4 鱼雷机与水中鱼雷

鱼雷机沿同一飞机轨迹飞行，到 `event.x` 时投放独立的 `torpedo-projectile`。投放时间仍由飞机抵达该格的时间决定。

`navalPresentation.js` 的真实公式：

```js
export const NAVAL_AIRCRAFT_DURATION = 1850
export function navalTorpedoDuration(startX, endX) {
  return Math.max(120,
    Math.abs(endX - startX) * NAVAL_AIRCRAFT_DURATION / 745 * 1.18)
}
```

鱼雷按 45×15 适配，水下尾迹由两条 Graphics 线构成；精灵与尾迹共用 Tween 更新，完成后一起销毁。伤害揭示使用同一个总时长按各格距离插值。

这里的“慢一点”是相同距离的速度比，不是所有航程固定等待某个毫秒数。不要重新写成固定 950ms，也不要飞机刚投放就一次性显示整行攻击。

### 5.5 潜艇

`submarineSail()` 使用事件最后一个路径锚点求停靠位置，不重新模拟触雷结果。

| 阶段 | 当前实现 |
| --- | --- |
| 水下进入 | 从逻辑 x=-75 到最终艇身中心，1250ms、`Sine.InOut`、alpha .35；椭圆尾迹每帧跟随 |
| 成功抵达 | 从两个已记录的起点向上下发射独立鱼雷；每条路径 850ms，并按路径索引逐格揭示 |
| 舰体离场 | 成功发射后延迟 650ms，再用 800ms 淡出 |
| 触雷 | 进入完成时仅对 `event.cells` 播效果；260ms 浮起 6px、alpha 到 1，再延迟 250ms 用 600ms 淡出 |
| 表现窗口 | 通常 3500ms，触雷 2650ms，若有待沉船则 5000ms |

三格潜艇在第二个入口格触雷时，路径末尾锚点可以仍在边界外；只炸引擎给出的两格。不要钳制锚点使三格强行进入画面。

潜艇两条纵向鱼雷当前固定 850ms，与鱼雷机的距离速度公式是不同实现。新模式需要统一速度时应显式调整，而非假设现成一致。

### 5.6 防空拦截

经典通过“没有伤害格且文本匹配 `intercept|拦截|击落`”识别拦截，然后调用 `airDefense()`。新模式宜提供结构化拦截字段，当前文本判断不要被误认为可靠的通用事件类型。

| 相对时间 | 当前效果 |
| ---: | --- |
| 80ms 起 | 防空炮从场外滑出，250ms，`Back.Out`，alpha 和 scale 一起变化 |
| 360ms | 表现阶段标记 `aa-aim` |
| 640/840/1040ms | 三发曳光，单发 240ms；第一发播放 `intercept`，炮口附小爆炸 |
| 0 至 1280ms | 飞机飞向拦截点，炮管每帧转向飞机 |
| 1280ms 起 | 爆炸、换飞机残骸纹理；800ms 翻转坠落、缩小和淡出，落水再播水花音 |
| 1900ms 起 | 防空炮和覆盖行色带用 350ms 收回 |
| 2550ms | `playAttack()` 返回的完整表现窗口 |

炮位行来自 `event.row`，飞机行来自 `event.y`，可以不同。曳光要追踪飞机行，不能为了方便把飞机强行画到炮位行。

### 5.7 水雷与反击

- 水雷素材保持 28×28，180ms 上浮约 5px，周围闪烁 300ms。
- 300ms 时播放 `mine` 音效，水雷 100ms 淡出，附加 54px 爆炸、57px 水花。
- 水滴粒子寿命 650ms，两个扩散波纹 850ms；不放大整枚水雷或整个棋盘。
- `mine-rebound` 在攻击方同逻辑坐标播放小爆炸和水花，按合成事件的 `cell.delay` 调度。

水雷 WAV 长 2.9 秒，不等于必须锁操作 2.9 秒。视觉窗口、操作阻塞和音频尾响是三种不同的生命周期。

### 5.8 击沉和雷达

击沉由 `sinkShip()` 执行：各艇身格错开 180ms 播爆炸；更换生成的残骸纹理；250ms 左右摇摆并往返重复；650ms 后开始 2100ms 下沉、缩到 .82、降低 alpha，最后保留暗色残骸。击沉立即播放 `sink`，并有轻微震动。

雷达 `radarSweep()` 在 1400ms 内画扫描圆、旋转线和格子信号，alpha 随进度衰减，结束销毁 Graphics；返回表现窗口 1450ms。`drawRadar()` 不常驻画扫描结果，不能留下永久框线。

## 6 动画结束如何与规则同步

### 6.1 经典 Scene 的结束模型

`playAttack()` 返回该事件的完整视觉时长。`playNextEvent()` 同时维护：

- `activeAnimations`：仍有视觉尾部的事件。
- `blockingAnimations`：仍影响动画确认的事件。
- 在 `duration-100ms` 移除阻塞 ID，阻塞集合为空才 `callbacks.animation(false)`。
- 在 `duration` 移除视觉活动 ID；全部完成才标记 `idle`。

队列实际使用 `while` 启动新事件，可以重叠，不是强制串行播放。经典命中后的即时追加攻击依赖这一行为；简单改成“上一段全部结束才播放下一段”会重新制造长等待。

一个新模式也必须分清“能否再次输入”和“能否把回合交给对方”：人类命中允许追加，不代表 AI 可以在己方落空动画未结束时提前攻击。

### 6.2 页面的聚合确认

`NavalBattle.vue` 合并己方、敌方 `animation` 状态。没有加载遮罩、横幅、设备限制和视觉忙碌后，发送：

```js
{ type: 'PRESENTED', presentationId: room.presentationId }
```

服务器 `waitForPresentation()` 记录事件序号、清空确认集合，并设 15 秒兜底；AI 对手预先占一个确认名额。`presented()` 只接受相同 ID，双方确认后重置正式 30 秒截止时间和 AI 行动时间。15 秒是异常兜底，**不是正常动画等待时长**。

本地引擎也有对应机制。新模式不能只实现按钮禁用，而让后台截止时间和 AI 计时照常跑。

### 6.3 页面转场

页面层不属于 Phaser：

- `beginLaunch()` 解锁音频并显示入场；`finishLaunch()` 在资源完成后保持至少约 950ms 总入场时间，并留至少 180ms 收尾。
- 阶段横幅 `showAnnouncement()` 调用 `transition` 音效，2400ms 后清除；实际控制还需考虑 Vue leave 动画状态。
- 匹配至少展示约 1400ms 搜索，匹配成功展示 1500ms，调用 `ready` 音效。
- 弹窗、聊天侧栏、提示倒计时线等由 `NavalBattle.vue` 的 Transition 和 CSS 实现。

新模式复用页面壳时应沿用这些状态，不要同时再加一套覆盖层和第二套倒计时。

## 7 音效系统的实际 API

### 7.1 运行时调用

从 `src/games/navalAudio.js` 导入：

```js
import {
  preloadNavalAudio, unlockNavalAudio, playNavalSound,
  setNavalMuted, getNavalMuted, stopNavalSounds
} from '@/games/navalAudio'

// 放在真实的点击/触摸处理开始处，不能等网络请求完成后才解锁。
async function onEnterMode() {
  await unlockNavalAudio()
  await preloadNavalAudio()
  playNavalSound('ready')
}
```

`preloadNavalAudio(onProgress)` 只预取编码数据，返回 Promise；可传 0 至 1 的进度回调。首次播放才懒解码并缓存 `AudioBuffer`。

`playNavalSound(kind)` 是非等待式调用，内部吞掉设备阻塞等错误，不能把它当作返回音效完成 Promise 的函数。声音失败不能改变游戏行动结果。

### 7.2 音效清单

资源根 URL：`/entertainment/naval/v2/audio/`，都是 `.wav`。

| 名称 | 文件时长秒 | 单音增益 | 设计及触发用途 |
| --- | ---: | ---: | --- |
| cannon | 1.10 | .52 | 炮口瞬态、低频炮身、金属尾部；舰炮发射 |
| explosion | 2.10 | .62 | 冲击、低频、碎片；命中和飞机爆炸 |
| splash | 1.30 | .42 | 入水、气泡和滴落；落空或坠水 |
| fighter | 3.20 | .36 | 螺旋桨扫过与短促扫射声 |
| bomber | 3.40 | .40 | 低速重型发动机和空气纹理 |
| torpedo | 3.10 | .36 | 推进、水流、入水层 |
| submarine | 3.30 | 默认 .45 | 低频发动机、水下共振、双次水声；当前未在 `VOLUMES` 单独指定 |
| sonar | 1.70 | .55 | 双谐波声呐脉冲 |
| intercept | 1.50 | .42 | 三次高射炮短连发 |
| sink | 3.30 | .62 | 金属形变、低频与水体 |
| deploy | .50 | .30 | 布置、购买和工具操作 |
| ready | .80 | .40 | 准备完成、匹配成功 |
| transition | 1.15 | .40 | 入场或阶段切换 |
| win | 2.40 | .55 | 上行胜利和弦 |
| lose | 2.20 | .50 | 下行失败音型 |
| mine | 2.90 | .40 | 水下低频爆炸与水花 |
| tick | .17 | .27 | 最后五秒短滴答 |
| notification | .65 | .38 | 大厅新消息双音提示 |

这些增益在共享 master 之前应用，并非最终声压或分贝。

### 7.3 别名、混音和停止

别名：`shot→cannon`、`hit→explosion`、`miss/water→splash`、`radar→sonar`、`antiAir→intercept`、`join→transition`、`purchase/ui→deploy`、`countdown→tick`。未知名字回退到 `deploy`，**不会自动加载一个同名 WAV**。

播放链路：

```text
AudioBufferSource → 单音 Gain → master Gain → DynamicsCompressor → destination
```

master 默认 .8；静音用 `setTargetAtTime(...,.035)` 平滑变化，存储键为 `naval_sound_muted`。压缩器 threshold=-12、knee=18、ratio=5、attack=.003、release=.2。

最多 8 个活跃音源，超限停止最旧的。每次播放创建新的 `BufferSource`，不能重用已启动音源；`onended` 移除并断开节点。

`stopNavalSounds()` 停止当前音源，并递增 `playbackGeneration`，让异步解码完成的旧任务不再播放。缓存和 AudioContext 保留，不是每局重新创建。

该模块是页面共享的单例。不要在单个棋盘动画结束时调用 `stopNavalSounds()`，会截掉另一侧效果；退出模式、退出教程或页面卸载时由统一 owner 停止。

当前没有背景音乐、按棋盘左右声像定位或逐事件可取消的音轨。双声道 WAV 的空间感来自离线反射，不代表运行时 3D/位置音频。

### 7.4 避免重复响和时间错位

- 选择一个音效 owner：经典渲染器已负责战斗效果音，父页面不要每次收到同一攻击再播放一次。
- 页面只负责 UI、转场、结算、通知等音。结算音通过定时器延后；离开时要清理该定时器。
- 当前飞机 WAV 自带扫射/发动机的固定音型，音频长度与各格可变落点不是逐采样锁定。若新模式要求严格同步，应拆分起飞、飞行、投弹、命中片段，而不是拖长一整段 WAV。
- 经典潜艇抵达时再次调用 `torpedo`，不能假设只有开头一次声音。
- `tick` 只在可攻击、非教程、页面可见且剩余 1 至 5 秒时播放，以对局、截止时间、回合和秒值去重。
- 大厅通知要过滤首次历史、己方消息和重复轮询，复用 `useNavalLobbyNotifications.js`，不要遍历整份消息列表逐条响。

## 8 如何生成和修改音效

在 `moments_web_frontend` 执行：

```powershell
node scripts/build-naval-audio.mjs
```

这会覆盖 `public/entertainment/naval/v2/audio/` 中脚本定义的 WAV 及 README，**不是播放命令**。修改设计后再执行，检查实际 WAV 差异与听感，不要在文档任务或启动应用时自动重生成。

### 生成器组成

| 函数 | 作用 |
| --- | --- |
| `random()` | 固定种子的 LCG，生成可复现噪声；种子在整次生成中共享 |
| `env(t,length,attack,decay)` | 快起音、指数衰减包络 |
| `noiseLayer()` | 白噪、低通/高通和少量棕噪纹理，用于爆裂、水流、风声 |
| `resonant()` | 指数变频基波，加 2.013 和 3.92 倍非整数谐波，形成金属或声呐感 |
| `engine()` | 随时间变频的发动机、叶片脉冲、噪声和起伏包络 |
| `cannon/explosion/water()` | 组合瞬态、低频、金属、随机碎片和滴水层 |
| `encode()` | 添加错开双声道反射、控制峰值和软限幅，编码 WAV |

WAV 为 44.1kHz、双声道、16-bit PCM，小端数据，44 字节头。反射延时为 61/113/197/331ms，右声道再错开 9ms；混音后以 `tanh` 软限幅。开头 80 个采样、结尾 220 个采样淡变，避免咔哒声。

新增音效需同时修改 `designs`、运行时 `NAMES`、可选 `VOLUMES/ALIASES`，然后生成文件。只在 `designs` 加名字，运行时未知名字仍会播放 `deploy`。

共享随机种子意味着在 `designs` 中间新增需要噪声的项目，可能改变后续 WAV 的噪声细节。若只想局部改一个音效，后续可改为每音效独立种子；这属于待实现改进。

## 9 军事模式当前接入（2026-11-01 修订）

军事模式现复用 NavalBoard → mountNavalGame → navalScene.js 的 Phaser 播放器。旧 NavalMilitaryBoard 独立 Canvas 已移除。navalMilitaryArt.js 只负责生成分层舰体/功能部件纹理，不再负责战斗时间轴。

- NavalBoard 在挂载 Phaser 前等待 loadMilitaryArt；Phaser 自身加载完成后才报告 ready。
- 炮击、爆炸、落水、水雷、击沉、潜艇、鱼雷和雷达直接调用经典实现，共用 explosion/splash 精灵表及 navalAudio.js 独立 WAV。两种规则共用一套事件去重、unrevealed、忙碌和销毁机制。
- 飞机固定沿目标行水平飞行，与舰体旋转无关；右海域从左进入，左海域由整个棋盘镜像为从右进入。militarySortie 处理甲板起飞/降落；militaryAirAttack 处理入场、逐格命中、返航或击落。
- 0–600ms 起飞，600ms 后进入目标海域；伤害按实际横坐标抵达时间揭示，鱼雷投放另加行进时间。安全飞机在 3600–4200ms 降落，防守方保留爆炸/击沉尾部至 4900ms。
- 发射播放 cannon，飞机播放对应 fighter/bomber/torpedo，命中播放 explosion，落空播放 splash，击落追加 intercept 与坠水，潜艇继续复用 submarineSail 的双鱼雷音效。静音、解锁、8 音源上限与退出停止均沿用共享 owner。
- 军事也使用 PRESENTED 确认；不能根据固定 UI 延时自行启动下一回合。联机和本地 AI 均等待动画完成确认。
- data-played-event-ids 提供最近已播放事件 ID；同帧发起的重叠事件不能用瞬时 effectEvent 必须停留某一值来测试，应该断言 ID 集合、忙碌状态及像素变化。

验收入口为 npm run qa:naval-military。它检查真实 Phaser canvas、WAV BufferSource 启动时长、水平轨迹采样、采购切型和手机全屏。

## 10 新模式接入步骤与示例

### 10.1 复用现有经典效果

如果新模式仍使用标准舰体及相同视觉语言，先由模式适配器输出现有快照和已裁决事件，再使用 `NavalBoard`。下面是父组件接线示例，`player/events/side/canChoose` 来自新模式自身状态：

```vue
<NavalBoard
  :player="player"
  :events="events"
  :defense-side="side"
  :interactive="canChoose"
  @cell="handleLogicalCell"
  @loading="updateAssetProgress"
  @ready="markBoardReady"
  @error="showAssetError"
  @animation="setBoardBusy"
/>
```

`military` 属性现在同样调用 Phaser，只增加军事舰体纹理、起降和水平空袭分支。自定义 `weapon` 仍需注册 `COMBAT` 及对应播放逻辑，不能只传入一个新名字便期待自动播放。

### 10.2 添加一种新效果

以未来的 `depthCharge` 为例，以下是开发步骤，不表示仓库已经有该武器或对应 API：

1. 本地和服务器引擎产生唯一 ID、真实结果 `cells` 及必要路径，不让渲染器再随机判伤。
2. 扩展 `NavalState.Event`、本地镜像序列化和分享的字段白名单；不要只修改 Vue。
3. 准备并注册图片、帧表；在 `COMBAT` 及 `playAttack()` 添加分支。
4. 为每个落点调度 `reveal(cell)` 和视觉/音效触发，返回最大落点时间加尾部的完整时长。
5. 在新模式的动作校验、事件筛选、`navalBoardEvents` 适配、回放武器白名单和状态重建中接通。
6. 若是自定义音效，在生成器及运行时两处注册；若复用爆炸音，不在父页面再播放第二遍。
7. 左右两侧、观战、回放和手机尺寸都复测，最后验证计时和 AI 不提前。

### 10.3 建议抽出的通用接口

以下仅是后续重构建议，**目前没有 `PresentationPlayer` 类或 `playCue()` 方法可直接导入**：

```ts
type EffectPlan = {
  eventId: string
  durationMs: number
  cues: Array<{
    atMs: number
    type: 'reveal' | 'sprite' | 'particles' | 'sound' | 'shake'
    payload: unknown
  }>
}
```

由模式负责生成确定性计划，由统一播放器负责镜像、调度、取消和清理；页面只汇总忙碌状态，服务器只接收规则层确认。这样其他模式可以改变武器与路线，而不复制整份 `navalScene.js` 或 `NavalBattle.vue`。

## 11 销毁 回放和验收

### 生命周期

- Phaser Scene `SHUTDOWN` 清 Tween、定时事件、队列、舰船和待击沉集合；Vue 卸载执行 `game.destroy(true)`。
- Canvas 模式卸载执行 `cancelAnimationFrame` 和 `clearTimeout`，并回报 `animation(false)`。
- 粒子、Graphics、尾迹和精灵都需要完成后销毁；不要只设 `alpha=0` 便永久留在场景。
- 页面退出调用 `stopNavalSounds()`，并清理延迟结算音等任务。
- 新模式最好以 `eventId + 对局代次` 关联取消，防止旧局加载、旧 Tween 或旧 Promise 在新局完成。

### 回放

复用 `navalReplay.js` 的“由归档事件逐步还原状态”思路，不重新调用攻击引擎。新武器需加入 `replayAttacks()` 白名单并实现伤害、部件损毁和消耗恢复；同一次攻击的 `backfire` 仍属于同一步。

`NavalReplay.vue` 根据双方 `animation` 状态决定下一次播放，另有 650ms stepping 保护和 850ms 自动推进间隔。该间隔属于回放节奏，不是联机回合交接延迟。重放从头开始会重建棋盘，防止 `seenEvents` 抑制再次播放。

### 验收脚本

在前端工程，配置实际服务地址后执行：

```powershell
$env:QA_BASE_URL = 'http://127.0.0.1:5175'
$env:QA_API_URL = 'http://127.0.0.1:8082'
node scripts/naval-balance-smoke.mjs
node scripts/naval-mirror-results-smoke.mjs
node scripts/naval-media-smoke.mjs
node scripts/naval-defense-movement-smoke.mjs
npm run qa:naval-mobile-tutorial
npm run qa:naval-military
```

脚本各自的默认端口、Chrome 路径与输出目录不同，运行前检查；部分会操作测试房间或保存截图。本文编写时未重新执行这些浏览器测试。

新模式验收至少检查：画布像素非空、素材真加载、相邻采样帧位置变化、左右镜像一致、命中按路线揭示、同事件不重复播放、跨行防空、边界潜艇不补格、静音和自动播放解锁、8 声源上限、卸载后无迟到声音、动画结束才交接回合，以及回放和观战不泄漏隐藏阵型。

`naval-media-smoke.mjs` 已包含离线解码 WAV、RMS 非静音、峰值不削顶、双声道和 tick 时长等检查。新增声音应同时听测；波形指标通过不等于听感或与画面节奏一定合适。
