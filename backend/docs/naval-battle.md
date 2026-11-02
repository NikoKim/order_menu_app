# Naval Battle

## 勋章与深弹舰修订（2026-11-01，当前规则）

### 价格与深水炸弹驱逐舰

- 军事模式巡洋舰 30、潜艇 50、雷达驱逐舰 20、干扰驱逐舰 20、深水炸弹驱逐舰 25、佯攻舰 15 金币。战列舰 35、防空/布雷驱逐舰 30、战斗/轰炸航母 50、鱼雷航母 60 不变。共享额度仍为 2/4/6，佯攻舰另限 3。
- depthCharge 为 1×2 舰体，首尾两格分别是上侧、下侧深弹发射器。每门只受自己格子的损伤影响；旋转仍准确对应原模块。剩一门仍可航行和撞击，两格全毁后不可使用。
- 出击固定沿所选行从左侧进入，与部署朝向无关；选定终点为横向两格。安全抵达后，分别在上方一排两格、下方一排两格中随机攻击一格；只投未损模块对应的一侧，越界侧不投。成功出击可重复使用。
- 航行路径不写入 shots，不视为炮击或扫雷证据。碰到舰体（包括残骸）即停止：突击舰与目标整艘均沉没，另对碰撞时两格水域结算命中/落空/水雷。触雷则自身沉没并结算当前两格冲击，不在原定终点爆炸，边界外部分不计入海域。
- 碰撞后自身原部署位置自动全部标为击毁；目标整舰补齐 hits/shots。深弹/触雷冲击不触发水雷的炮击反伤。事件使用 route、targets、torpedoes（两条短投掷路径）、cells、backfire、destroyed，前后端、回放、分享及 AI 同步。
- 教程升级 v6，增加采购、抵达投弹、撞舰、触雷、单侧发射器损毁的完整实操。采购课总花费 285，剩余 15。新版进度键与 v5 分开。

### 六枚连胜勋章

- 经典、军事各有人人连胜 3/5/10 勋章。只接受服务端正式开战的 MULTIPLAYER 结算，两种规则独立累计；相应规则输棋重置当前连胜，人机/教程/本地镜像不增减。历史最大连胜达标后永久解锁。
- V59__naval_medals.sql 新增 naval_medal_profiles 和 naval_medal_rounds；按 game_id/player_key 幂等入账，重复轮询、掉线及再来一局不重复发奖。首次访问从可验证历史回填，清理旧战绩不回收勋章。部署必须先应用 V59；本地 3307/order_menu 已显式安装并通过真实 MySQL 回归。
- GET /api/entertainment/naval/medals 返回本人；player 参数接受随机公开 medalId 查看他人墙，不暴露内部 player_key。PUT 同路径仅允许本人佩戴已解锁勋章，空字符串卸下；不能给他人佩戴或伪造解锁。
- 2026-11-02：大厅右上角保留勋章入口，复用 NavalCommunity 操作窗口；电脑对局点击真人头像同样展开右侧社区位置，手机对局点击真人头像复用 mobile-utility-panel 右侧弹窗。取消独立勋章抽屉和遮罩，游玩时不显示勋章 icon，AI 不提供勋章墙。佩戴勋章仍在共用 NavalAvatar 下方显示，对局、房间目录、排行榜、评论、回放/详情、匹配均携带或读取资料。
- 每局开场显示双方头像及佩戴勋章，包括人机和再来一局；重新连接战备界面也会展示。原八个表情保持不变，用户已取消新增表情，所有本次新表情 GIF/PNG 与解锁入口已删除。
- 素材：medals/{classic,military}-{3,5,10}.png，military/depth-charge.png，military/depth-charge-destroyer.png。均为新增像素 PNG，不含新表情。
- 主要验证：naval 单元测试、NavalDepthChargeTest、NavalMedalsTest、NavalMedalsMysqlTest、naval-medals-smoke.mjs、naval-depth-charge-smoke.mjs、完整军事教程 smoke。测试覆盖双发射器各方向损坏、航路不留弹坑、完整撞沉与两格冲击、到达后投掷和延迟揭示。


## 舰船共享上限修订（2026-11-01，当前规则）

- 1×4 航母跨型号合计最多 2 艘；1×3 战列舰、巡洋舰、潜艇合计最多 4 艘；1×2 驱逐舰跨功能合计最多 6 艘。
- 佯攻舰不占驱逐舰额度，仍独立最多 3 艘；300 金币预算及其他规则不变。
- 前后端型号数据、共享校验、采购按钮、AI 配舰/剩余舰长判断、历史模板校验和新手教程同步。下方旧版 2/3/5 等额度属于历史描述，以本节为准。


## AI 有效收益与单次风险修订（2026-11-01）

- 决策顺序为硬过滤、预计收益、扣一次风险成本。已受击格不贡献新增伤害；沉舰整舰及隔离带排除；已唯一定位的活舰仅排除舰体外周一格，保留未损舰体并优先有效功能部件。位置不唯一时，仅排除所有候选共同确认不可能容纳其他舰艇的外周格，不按猜测位置整片排除。
- 雷达空信号在普通搜索中为零伤害价值；普通进攻候选耗尽后单独进行一次允许回查的候选计算。正雷达信号仍可直接攻击；侦察与布雷不会阻止伤害候选的回查。
- 战列舰计算加权、不放回抽样的单格入选概率，包含抽中旧格浪费的概率；战斗机/轰炸机按攻击数量及旧命中校正计算入选收益。鱼雷、潜艇射线按已知阻挡及未知舰体的估计到达概率计收益，不仅比较区域格数。
- 功能价值按所有公开合法布局中的功能部件概率分摊；已定位活舰功能优先，不能因为大片区域叠了候选防空炮位就获得虚高收益。
- 防空风险分已确认、击落记录的条件覆盖风险、仅未知、已验证安全。仅未知允许冒险；安全证据与失效防空会降低/移除成本。空袭仅扣一次预计损失：拦截概率 ×（飞机资产价值 + 本次可能损失的收益），剩余飞机少时资产价值更高。移除所有极小系数及“有炮再乘 0.02”。
- 潜艇按实际入场到目标末端的完整航路计算未探明比例；已受击格不能重新布水雷，连续安全航路降低成本，整条已探明时成本为零，不能只检查终点三格。
- 排查只奖励尚未受击、尚未扫描的相关防空候选，已验证无效的重复探查不给收益；可换低风险航路，不要求击落后无条件只能开炮。
- 历史模板在硬过滤后仅增加最多 0.15 且不超过该动作毛收益 3% 的加分，随后统一扣风险；不再以模板固定加 10 分覆盖实时判断。回查和模板均不能复活硬排除目标。
- 回归场景覆盖活舰隔离带、功能部件保留、雷达耗尽回查、旧格炮弹浪费、低风险空袭、防空解除、未知风险不锁炮击及完整潜航安全路径。用户界面、实际伤害引擎和经典模式不变。


## Military AI And Combat Revision (2026-11-01)

Military shared hull caps are two carriers, four medium ships and six destroyers, with three decoys independently. Six complementary strategy cores use role/redundancy weights and a 250/270/285/300-coin budget target; unspent coins are retained. This is a heuristic strategy, not a model trained on player archives. Deployment normally prioritizes distinct AA rows and early interception; a 15% alternative weighting permits other defensive tradeoffs, including jammer protection. Both engines use public wreck halos, shot history, radar memory, aligned hit pursuit and revealed hit-ship type for targeting, with discounted radar revisits and safer submarine routes. Aircraft interceptions create a local AA-gun search; successful flight paths clear AA hypotheses, while negative radar cannot prove safety under jamming. Sustainable gunfire probes promising flight corridors before risking aircraft. Submarines also favor previously attacked mine-free routes. Verified-safe area weapons take priority; without safe probes, the AI may take the lowest available risk rather than stall.

Public hit evidence can establish a unique disabled hull: battleship gun hits at C3/C5 identify C3-C5, retiring C4 and the surrounding halo without changing actual damage. Hidden live coordinates and rotation are never used. Defensive mine scoring responds to observed torpedo/submarine rows and stops once all publicly identified enemy ships establish that no such threat remains; unknown ships remain potential threats. Pending two-mine turns finish first.

Military deployment lasts eight minutes (classic remains five). Carriers share a two-ship cap: fighter and bomber variants cost 50 coins; the new torpedo-plane variant costs 60 coins and replaces the torpedo destroyer in new purchases. Each uses a 1-by-4 hull. The torpedo carrier has one aircraft sharing both middle deck cells; damage to either cell disables launch.

Minelayers carry ten mines. New UI actions place one mine at a time through `ATTACK.targets`: the first retains the current turn with `mineActions=1`, and the second passes the turn. No other weapon may interrupt the pair; timeout or exhausted mine ammunition also ends the turn. Legacy two-cell submissions remain compatible. A submarine's middle hull cell is fatal; end-cell damage alone is not.

Battleship first-shell probabilities are 35% for each remaining cell when one of three cells is already hit, and 45% for the remaining cell when two are hit; later shells sample the remaining weights without replacement. Each existing hit in a fighter's six-cell or bomber's nine-cell region raises each other cell's inclusion probability by two percentage points, subject to the fixed four-to-five or five-to-six distinct attack-cell count. Old shot cells produce splash effects without duplicate damage. No per-cell probabilities are displayed. Mobile placement previews expose rotate, confirm and cancel together. Salvo labels show 1x3/3x1, using smaller wrapping text on phones. Replay and tutorial v5 follow these rules.

PC audience uses the same right-hand expanding layout as the other community icons in both rule sets, without a floating modal or overlay.

## Current Item Balance

| Item | Coins | Purchase Cap |
| --- | ---: | ---: |
| Anti-air gun | 30 | 3 |
| Fighter | 30 | 3 |
| Bomber | 25 | 3 |
| Torpedo plane | 25 | 3 |
| Radar | 40 | 2 |
| Submarine | 30 | 3 |
| Stealth coating | 10 | 5 |
| Mine | 5 | 12 |

Fighter preview remains 2 rows by 8 columns. The engine randomly chooses one
row before resolving interception. A gun on either candidate row shoots it down;
if both rows are defended, the chosen row's gun fires and the other remains.
Without interception, exactly that row is strafed. The selected row is event `y`,
while event `row` identifies a spent anti-air gun. AI coverage uses the expected
single-row result, not a guaranteed 16-cell hit. Both tutorial paths demonstrate
interception and a separate successful random-row strafe.

A submarine hitting a mine while its hull is still entering attacks only the
in-bounds hull cells at that instant. At the first or second entry cell, only one
or two cells explode. A fully entered hull still affects three cells. The tutorial
demonstrates the second-cell case. No position clamp may move an unseen hull part
into the sea just to enlarge the explosion, and it never launches after touching
a mine.

Dropped plane torpedoes use distance-based travel at approximately 85% of aircraft
speed (18% longer over the same distance). Impact reveal follows that exact tween,
including mirrored defense and replay; a zero-distance impact has a 120ms minimum.
These balance changes require both engines/frontend to be deployed together, but
no additional SQL after V58. Archived costs and actual attack cells are not
recalculated under the new prices or random-row rules.

## Mirrored Defense, Mine Backfire And Shared Results

Deploy `V58__naval_shared_results.sql` before the new backend and frontend.
It adds only `naval_lobby_messages.battle_json`; rollback removes that column.
Existing installations still apply migrations explicitly, not automatically.

Left-hand seas mirror the entire canvas, including hulls, mine positions,
aircraft, submarine routes and torpedo travel. Axis glyphs, pointer/drag input,
keyboard movement, confirmation controls and tutorial spotlights compensate for
the mirror, retaining authoritative logical coordinates. Incoming torpedoes and
submarines travel right to left, and left defenses remain on the left edge.

Mines hit by cannon, fighter or bomber also strike the attacker's same logical
coordinate. Torpedo-plane and submarine hits do not cause this backfire. Backfire
can damage or sink own ships and end a round, but never recursively rebounds.
Reattacking an already damaged coordinate cannot add duplicate hull damage.
The original event records `backfire` cells; live boards and replay animate both
seas, and replay applies this damage without inventing an extra attack.

History details can share one's own battle to the lobby. Registered archives are
loaded and ownership-checked on the server, never accepted as client results.
Guest AI shares explicitly upload a bounded local snapshot, labeled local and
never added to rankings or official history. Shared cards expose public battle
details and replay, without account/guest keys or another share-as-owner control.

Replay requests fullscreen directly from the play gesture, plus landscape on phones.
Unsupported iPhone Safari uses the existing landscape immersive fallback; browsers
that deny orientation lock require physical rotation. A gate blocks playback until
the screen is ready. Closing restores fullscreen only if replay entered it.
Both tutorial paths explain mirrored defense and demonstrate mine backfire.

`naval-mirror-results-smoke.mjs` covers local sharing, public replay cards, moving
mirrored assets, mine backfire, fullscreen/orientation requests and replay chat.

## Database

Apply `src/main/resources/db/migration/V53__naval_battle.sql` before starting
this version. This repository applies SQL migrations explicitly; the naval
module does not silently alter a production database at startup. The rollback
script is `docs/db/migration/rollback/V53__naval_battle_rollback.sql` and deletes
naval room and profile data.

Apply `V54__naval_page_presence.sql` for the page-scoped online counter.
Its rollback drops only the temporary presence table.

Apply `V55__naval_round_results.sql` for archived rounds and
`V56__naval_community.sql` for lobby messages and naval player reviews.
The community migration is additive and repeatable. Deploy it before the new
backend. Leaderboards include registered players only, separated by AI/PvP.
Wins and win rate aggregate all archived rounds; economy ranks individual rounds
by opponent purchase cost minus player purchase cost, for victories only. Lost
rounds remain in history but never enter economy standings. Older archives without
cost information remain visible in history but do not enter economy standings.
New archives retain the complete action log, item consumption and final fleets.
Guest AI history remains local and does not enter server leaderboards.

Apply `V57__naval_lobby_cards.sql` after V56 and deploy the backend together
with the frontend. Lobby posts accept exactly one of text, `stickerId` or
`roomId`. Stickers and room shares do not require accompanying text. Older
backends only accept text and will reject these new posts as empty messages.
Room cards resolve live room status on each lobby refresh; ended/closed rounds
are disabled, while active games offer spectating. A reused room cannot revive
an invitation for an older round. Lobby chat is available while matching and
watching archived replays, but not during placement, supply or battle.

History details stay inside the community panel. Replays reconstruct both
fleets from archived final geometry and ordered attack events, with automatic
playback, pause, restart and manual next attack. Old summary-only records cannot
be replayed. Phaser is loaded only when a live or replay board is mounted, so
entering the naval tab does not wait for the game engine or combat assets.

Battle presentation uses a persisted acknowledgement gate. The initial banner
and each attack must finish on both player clients (only the human in AI mode)
before a fresh 30-second clock and AI delay start. A 15-second fallback releases
the gate if a client disconnects; stale acknowledgements cannot release a later
attack. Spectators never participate in this gate.

### Deployment Matchmaking Errors

The generic `{code:500,message:"Server error",data:null}` response does not
identify its cause. Check the backend's `Unhandled exception` or `Naval database
request failed` stack trace for the failed request. In particular, data-only
restores can leave `naval_matchmaking_lock` empty even when its table exists.
The old `SELECT ... WHERE id=1 FOR UPDATE` then throws
`EmptyResultDataAccessException` and matching cannot start.

Check the datasource points to the intended database, then run:

```sql
SELECT DATABASE();
SHOW TABLES LIKE 'naval_%';
SELECT id FROM naval_matchmaking_lock WHERE id=1;
```

If all five naval tables exist but the last query returns no row, this
idempotent repair is sufficient and does not modify any room or game history:

```sql
INSERT IGNORE INTO naval_matchmaking_lock (id) VALUES (1);
```

The updated service also repairs this seed transactionally on every room
creation, invite join, seat claim and rematch. Its duplicate-key upsert takes an
exclusive row lock before matching; it does not use `INSERT IGNORE` followed by
shared-lock upgrades, which could deadlock concurrent first requests.

If tables are missing, deploy migrations V53, V54 and V55 before serving traffic.
For a partially initialized database, the additive, repeatable repair script is
`docs/db/migration/repair/naval_battle_bootstrap.sql`. Back up the configured
database and verify `SELECT DATABASE()` before executing it. It creates only
missing tables and the mutex seed, never drops or replaces existing game data.
It does not repair arbitrary incompatible columns; inspect `SHOW CREATE TABLE`
and the deployment migration history in that case. The application does not
perform production DDL automatically.

Missing-table/column database failures now return HTTP 503 with a safe migration
message, and permission failures return safe connection/permission guidance.
Full SQL errors stay in server logs rather than leaking connection details to
players. This code must be redeployed to change the running server's behavior;
an SQL repair of a missing mutex row can restore old deployments immediately.

The database stores authoritative multiplayer rooms and signed-in AI sessions.
Guest AI sessions and guest saved formations/history live only in the browser.
Multiplayer guests use the existing signed `X-Guest-Token`; temporary shared
room state is necessarily held on the server. Completed guest-only rooms are
removed after one day. Signed-in players retain their database history.

Rules version 2 uses 10 ships and 20 occupied cells: four 1x1, three 1x2,
two 1x3 and one 1x4. The two 2x3 carriers are removed. Persisted version-1
rooms and formations are upgraded when read by removing those carriers while
preserving the other ships' positions. No additional SQL schema migration is
needed for version 2. Browser-local saves use the same migration policy.

## API

All routes are under `/api/entertainment/naval` and use the normal `ApiResponse`
envelope. Authentication is the existing bearer token or entertainment guest
token; the browser cannot choose its player identity or turn index.

- `POST /rooms`: `{mode:"AI"|"MULTIPLAYER",autoMatch:boolean}`.
- `POST /rooms/join`: `{inviteCode:"ABC234"}`.
- `GET /rooms/{id}`: member-only redacted room snapshot.
- `POST /rooms/{id}/actions`: action described below.
- `GET /profile`: signed-in formations and up to 50 recent completed games.
- `PUT /profile`: `{formations:[{id,name,ships}]}`, at most 20 named formations.

Actions:

- `DEPLOY`: `ships:[{id,x,y,w,h}]`, `inventory`, `mines:[{x,y}]`, `aa:[row]`,
  optional `ready:true`. In placement only ships may change and inventory must
  remain zero. In supply ships are locked; inventory is absolute purchases.
- `LAYOUT_READY`: optional `ships`, locks the player's fleet and opens supply.
- `READY`: confirm supply and wait for battle. All purchased defenses must be
  placed. Calling this during placement is rejected.
- `ATTACK`: `{type:"ATTACK",weapon:"shot"|"fighter"|"bomber"|"torpedo"|"radar",x,y}`.
- `CHAT`: `{type:"CHAT",text}`, 1 to 300 characters, rate limited.
- `LEAVE`: forfeit or close a waiting room.

Placement `DEPLOY` accepts an empty or partial legal fleet (0 through 10
ships), allowing a player to clear the board and place ships one by one.
`LAYOUT_READY` and saved formations still require all 10 ships. Placement
timeout fills missing ships around the already placed ones whenever possible;
if bounded completion cannot fit them, it regenerates a legal fleet and adds
an explanatory battle event.

The AI excludes the one-cell border (including diagonals) around publicly
sunk ships from both single shots and aircraft attack areas. Adjacent cells
around an unsunk hit remain eligible because they may be part of the same
ship. AI presentation delay is 4.2 seconds without changing the 30-second
attack clock.

Presence endpoints:

- `POST /presence`: `{pageToken: UUID}` renews this tab for 25 seconds.
- `DELETE /presence/{pageToken}` removes only the caller's own tab.

Both return `{onlineCount}`. Only live tabs of the naval page are counted;
multiple tabs for one authenticated or guest identity count once. Presence is
shared through MySQL across server instances, and stale tabs expire even if
the browser closes without sending the leave request. Page tokens cannot be
claimed or deleted by another identity.

Coordinates are zero-based. Area attacks use the top-left cell; fighter selects
8 columns by 2 rows, then randomly strafes just one row (8 cells). Either candidate
row's AA intercepts it; when both have AA, only the chosen attack row's gun is spent.
Bomber/radar use 3 by 3. Torpedoes travel left-to-right across
row `y` and stop at the first ship or live mine. Aircraft crossing a defended
row are destroyed before attacking; one AA charge and one aircraft are spent.
Radar scans are private, do not damage ships, and consume the turn. A newly
damaged ship cell grants another 30-second attack, including an area attack.
Repeated damage never grants another hit. First turn is selected randomly.

Both members receive a server-generated legal initial fleet. Placement lasts
five minutes and is separate from purchasing. A player can submit their own
layout and start supply before the opponent finishes arranging. Once neither
player is arranging, the room enters `SUPPLY` with a fresh three-minute timer.
The room remains `PLACEMENT` while any player is still arranging; the
individual `players[i].stage` is `PLACEMENT`, `SUPPLY` or `READY`.

Placement expiry locks the existing formations and opens supply, never skips
shopping. Supply expiry deploys any unplaced purchased mines/AA automatically
and starts battle. Both players confirming supply starts early. AI opponents
have their own legal fleet and supplies ready in advance. Each battle turn has
30 seconds; expiry passes to the opponent. A scheduled sweep and every room
read/action enforce deadlines even if no browser is open.

Automatic matching considers only waiting rooms polled within the last 20
seconds. Polling renews the host's queue presence, so closing or leaving a
waiting browser does not pair a new player with an offline opponent.

Snapshots never include an opponent's unsunk ships, mines, AA, inventory,
identity keys, or radar results. Sunk ships are public. Fleets are revealed at
the end. `players[i].shots` are incoming attacks on that player's own grid.

## Verification

The Vue shell manages room, formation and supply controls. Combat presentation
uses Phaser 3.90 scenes: loaded PNG textures, 16-frame explosion/splash sheets,
sprite tweens, smoke and wreck effects. Hit cells become grey; sunk ships retain
a charred wreck after the destruction sequence. Coordinate labels use a small
bitmap UI font, not unreliable browser canvas text metrics.

Raster art is under `moments_web_frontend/public/entertainment/naval/v2`, with
the built-in image-generation prompts and provenance in `ART.md`. Fifteen
authored stereo WAV files are under `v2/audio`; their offline authoring script
is `moments_web_frontend/scripts/build-naval-audio.mjs`. The runtime samples
those files through a capped mixer/limiter; it does not synthesize oscillator
bleeps. Phaser schedules combat audio with the corresponding launch/impact.

Combat animations are deliberately paced: aircraft flights and interception
take roughly four seconds, and mines have a separate rise, warning, underwater
blast, splash and ripple sequence. An intercepted AA gun emerges on its actual
defended row before firing tracers; the aircraft burns and falls into the sea.
Each board queues unseen combat events without blocking legal follow-up attacks
on the presentation timeline. The server AI responds after 1.2 seconds.
Historical events are not replayed when a game is restored.

The permanent phase strip is removed. Phase changes use a short full-width
banner and scene transition; chat is collapsed by default so the boards remain
the primary surface. Successful multiplayer entry always presents a bounded
searching/connecting phase and then an opponent-found phase, including when the
server immediately matches or returns an existing active room.

The separate eighteen-step naval tutorial uses a movable spotlight with a dark
surround, real game actions and its own `naval_tutorial_v1` browser save. AI and
timers are paused for teaching; training never writes normal sessions, saved
formations, database game history or win/loss records. Exit, resume and restart
are supported, including leaving the mobile fullscreen gate during a lesson.

Formation selection uses the shared `CustomSelect`; its menu is teleported
inside the fullscreen game root and flips upward when space is limited.
Image/audio loading is progress-tracked and retryable. Leaving during preload
cancels entry before any room-creation or invitation-join request is sent.

```powershell
mvn '-Dtest=NavalEngineTest,NavalMysqlTest' '-Dnaval.mysql=true' test
```

The optional MySQL test uses the local development datasource and uniquely
prefixed fixture tables, then removes only its own fixtures. It exercises
concurrent matchmaking under the database mutex, invites, two-member limits,
membership privacy, persistent state, and user/guest profile isolation.

## Spectators, Seats And Rounds

Apply `V55__naval_round_results.sql` before deploying this version. It preserves
immutable per-round results independently of the mutable room snapshot. Room ID
and invite code survive rematches; `round` increments and `gameId` is
`roomId:round`. Registered history queries the result table, retaining original
participants even after a spectator replaces an empty player seat. Guest-only
server result records expire after one day; browser guest history stays local.

`POST /api/entertainment/naval/rooms/spectate` accepts `inviteCode` and optional
`viewMode` (`BOTH`, `PLAYER_0`, `PLAYER_1`). Full player joins still return 409;
the client presents its three-second full-room notice before spectating.
Single-player rooms are private. Spectators may only issue `SPECTATE_VIEW`,
`TAKE_SEAT {seat:0|1}`, or `LEAVE`; player actions including `CHAT` return 403.
Spectator polling renews a 45-second membership lease. Single-side views use the
same board, inventory, AA, mine and radar redaction as that actual player.

Room views always contain two player slots with `occupied`, `left`,
`continueRequested`, and public opaque `id`. Registered users alone expose
their stored avatar URL. Spectator IDs are also opaque; authentication identity
keys never occur in client snapshots or share metadata. Leaving immediately
settles an active two-player game and frees that seat, without invalidating the
room code. Departed players retain read-only access to their un-replaced seat.

After `FINISHED`, `CONTINUE {value:true}` records a player's consent. Both
occupied seats must consent before a fresh placement/supply/battle round starts.
An incoming spectator's successful seat claim counts as that new player's
consent; the remaining player still confirms. Seat claims and votes lock the
room transactionally; concurrent claims cannot occupy the same seat.
Continuing also takes the matchmaking mutex and rechecks every participant for
another active room, including previously accepted votes when an empty seat is
claimed. An old settlement can never restart a second concurrent player room.

`lastResult` (also `settlement`) is an immutable public result with original
participant names, avatars and combat statistics. Live `players` remains the
current seats and votes. `resultYou` is the original participant index or -1,
preventing a replacement player from inheriting the previous occupant's result.
Chat messages snapshot `name` and `senderId` (the same opaque participant UUID),
so old messages cannot be relabeled by a later occupant of that seat. Legacy
four-field messages remain readable and are backfilled before seat changes;
pre-settlement messages can recover their sender from the immutable result.

Frontend checks (from `moments_web_frontend`):

```powershell
npm run test:naval
npm run test:naval-tutorial
npm run qa:naval
npm run qa:naval-online
npm run qa:naval-media
npm run qa:naval-tutorial
npm run qa:naval-mobile-tutorial
npm run qa:naval-controls
npm run build
```

Browser checks use local Chrome with Playwright and the Vite dev server on
5174. The online check also needs the backend on 8080. Screenshots default to
the ignored `.agents/naval-qa` folder. Mobile checks exercise real Chromium
fullscreen plus emulated portrait/landscape viewports; physical phones still
need device testing. Native fullscreen remains required on capable mobile browsers.
iPhone Safari without element fullscreen uses an explicit immersive fallback,
still requiring landscape; it never reports that browser chrome is hidden.
The standalone manifest and Apple web-app metadata allow launching from the Home
Screen without the Safari address bar. VisualViewport dimensions track toolbars
and safe-area insets, with no vertically centered blank header band.

## Additional Items And Controls

The submarine UI uses a single anchor with a translucent three-cell hull, not
three taps; the unchanged authoritative `targets` payload contains its footprint.
Mobile ship movement also accepts touch dragging followed by confirmation.
During supply, clicking an existing mine, AA gun or coating selects it instead
of removing it. A subsequent destination (or drag and drop) relocates the same
purchased item; mobile previews require confirmation and cancel preserves the
source. Phaser shows the actual bitmap following the pointer. Board controls
measure the full asset footprint and choose a bounded position above or below
it. Exit confirmations are centered in both phone orientations. Enemy attack
tutorial demonstrations keep their instructions on the friendly side.
Notifications expire after three seconds with a draining progress line. Landscape
fullscreen uses the layout viewport and no top safe-area padding; portrait exit
dialogs use full-width constraints rather than the landscape half-screen width.
Combat presentation masks newly returned results until their projectile reaches
each cell. A presentation acknowledgement gate defers the next clock and AI
until both clients finish their animations. AI extends adjacent hits
along their confirmed axis, and varies probing bomber/torpedo attacks with fighter
follow-ups using public attack history, never unrevealed AA positions.

- `submarine`: 30 coins, cap 3. `ATTACK.targets` is exactly three distinct,
  consecutive cells in one row or column. The three-cell hull travels from the
  left sea edge to that footprint, checking every occupied transit cell for mines.
  A mine destroys it before launch and attacks only hull cells actually inside the
  sea at impact. One or two cells inside means one or two explosion cells; the
  footprint is never shifted forward to force a three-cell explosion. Entry route
  anchors may have x=-2 or x=-1; damage cells must stay within the board.
  Transit is underwater; a destroyed submarine resurfaces during the explosion.
  On arrival, upward and downward torpedoes
  independently choose one of the three origin cells, then stop at the first
  ship or mine. AA does not intercept a submarine. Event `targets`, `route`,
  `torpedoes` and `destroyed` drive authoritative animations on both clients.
- `stealth`: 10 coins, cap 5. Supply `DEPLOY.coatedShips` contains distinct own
  ship IDs, at most the bought count. One coating covers the whole ship and
  suppresses its radar presence, not attack damage. Ready requires all coatings
  applied; supply timeout completes them. Both fields persist in room JSON,
  so no additional relational migration is needed for these two items.
- Mobile placements and item attacks are previews until the anchored checkmark
  is pressed; cross cancels without consuming coins, inventory or a turn.
  Successful item attacks reset selection to cannon, even with remaining stock.
- Both rosters provide rotation buttons; mobile also shows a model rotation
  control, desktop accepts R outside text inputs. Geometry changes clear stale
  hover previews and replace the keyed Phaser sprite instead of accumulating it.
- Desktop/mobile tutorials are independent, cover every item and demonstrate
  submarine success, transit mine destruction and radar evasion. Version 1
  tutorial checkpoints restart the expanded version 2 training; real games and
  history are not modified.

The explicit WeChat action copies the invitation card URL for pasting in chat;
it does not claim to send a message or depend on an OS text/URL handoff being
accepted by WeChat. The separate system sharing action remains available. Public
card metadata and direct invitation entry are unchanged. Actual WeChat delivery
and platform URL restrictions still require verification on a physical device.

## Resume And Statistics

Starting a new game or joining an invitation first offers to resume an existing
saved game or explicitly leave it. Dismissing the resume row also asks for
confirmation instead of deleting its save silently.

`POST /rooms/{id}/connection` accepts a UUID `pageToken` and `connected` flag.
Route unmount, pagehide and visibility loss send a keepalive disconnection notice.
A waiting room is cancelled immediately with no result. After multiplayer
matching reaches placement, absence allows 30 seconds to reconnect; expiration
ends the game with `DISCONNECTED` and awards the opponent the win. Missing
disconnect notices fall back to room polling heartbeats. A stale tab's
disconnection cannot replace a newer tab's registered connection token.
Connection fields persist in existing `state_json`; no new SQL migration is
needed for reconnection. Deploy frontend and backend together.

AI placement and supply cancellations have no winner, defeat or archived result.
Offline preparation pauses instead of automatically starting an unattended game.
Only AI rounds that reached battle count in history and rankings; multiplayer
rounds count from placement. The economy ranking includes winning results only.
Historical AI results explicitly marked `battleStarted=false` are excluded.

History details stay inline and offer deterministic Phaser replays with automatic
play, pause and one-attack stepping. Lobby chat remains available during replay.
`naval-resume-smoke.mjs` covers resume decisions, waiting-room cancellation, real
30-second reconnection expiry, button alignment and mirrored torpedo movement.

## Combat Presentation

Turn handoffs acknowledge the final queued animation 100 ms before its end,
without an additional cooldown. Human hits grant an immediate follow-up, while
queued effects still finish before a subsequent miss can hand control to AI.
Turn highlighting and countdowns wait for presentation acknowledgement; battle
clocks are clamped to 30 seconds. These changes require both frontend and backend.

Torpedo planes drop at the selected cell and travel rightward along the remaining
row, stopping at the first ship or mine. Recorded origins are retained in replays.
Mobile replay boards fit the actual available height; event captions occupy a
separate row above the boards and fade out. `naval-turn-replay-smoke.mjs` covers
hit chaining, turn indicators, drop origins, replay layout and early unlocking.

## Guest Identity Recovery

Naval API requests renew a rejected anonymous identity once on HTTP/business 401.
Concurrent initialization and renewal share one creation request; a late failure
cannot discard a newer token. Logged-in requests are never downgraded to guests.
Renewal removes only the old guest token/name, preserving local games, history and
formations. Presence allocates a fresh page token if its prior row belonged to
the replaced identity. No backend or SQL change is required for this recovery.
`naval-guest-recovery-smoke.mjs` verifies expired identities, concurrent renewal,
online counts, real matching between two guests, and bounded retries.

## Lobby Notifications

While the lobby is available but closed, its icon shows an unread dot and plays
an original short notification sample for new messages. Opening it clears the
dot. Initial history, own messages and duplicate poll results are silent. The
existing sound mute applies, and active gameplay pauses lobby polling. Replay
and waiting-room icons share the same state. The API returns a viewer-specific
`mine` flag without publishing private sender keys; deploy frontend and backend
together. No SQL migration is needed. `naval-lobby-notifications-smoke.mjs` covers
desktop/mobile indicators, sound, deduplication, reading and replay behavior.

## Room Directory And Spectator Privacy

The top-right room directory lists live AI and multiplayer rooms, with mode filters,
player avatars, phase, viewer count and an invitation badge. Public responses never
include invite codes, boards or private player keys. Entering a private room by ID
requires its correct invite code, whether joining a seat or spectating. Players can
disable spectators in the audience panel or mobile menu; existing spectators lose
access on their next poll. Lobby cards reflect this setting and require an invitation
for private rooms. A room can post one lobby invitation per round, enforced in the
same database transaction under the room row lock.

Guest AI continues locally and works offline. Its disposable live mirror is stored
with SQL phase `LOCAL`, so it is never advanced as a server game or included in
results, history or leaderboards. The owner publishes a bounded snapshot after
changes and every four seconds, and receives the audience list. Mirrors close after
30 seconds without publication; completed mirrors are purged after an hour. Guest
formations and history remain in the browser. Deploy both frontend and backend;
all new settings live in existing JSON state, with no new SQL migration.

## AI Targeting And Economy Rankings

Local and server AI use only public hits and sunk hulls. Confirmed hit axes take
priority. Hunting searches the largest remaining hull's legal straight segments,
usually choosing a middle cell, with occasional edge sweeps or alternate probes.
Each weapon maximizes unvisited coverage outside sunk-ship halos; torpedoes also
respect publicly known blocking hull cells. Aircraft bait/follow-up tactics remain
probabilistic. Mines mix forward screens and positions one to five cells ahead of
own hulls. `naval-ai-strategy.test.mjs` and `NavalAiTest` cover these decisions.

Economy leaderboards include winners only and keep one best result per registered
player per mode, ranked by opponent spending minus player spending. Equal results
retain the most recent battle for the history link. Guest copy includes a ranking
button and explains login eligibility. Toolbar controls share stable equal spacing.

`naval-rooms-smoke.mjs` verifies real room entry/privacy, one-time invitations,
guest AI mirrors, equal desktop/mobile toolbar spacing, ranking entry, and inline
sticker trays below the input. Small landscape screens show a compact sticker row
in place of the message log while choosing, rather than a separate floating dialog.

## Military Rules

Room creation accepts `ruleSet: CLASSIC | MILITARY` (default `CLASSIC`). Matching
queues and leaderboard queries use this discriminator independently of AI / PvP.
Archives persist it in `result_json`; older archives remain classic. No new SQL
migration is needed. Restart the backend when deploying these rules.

Military games reuse NavalBattle, NavalMobilePreparation, NavalTutorialGuide,
fullscreen gating, community panels, settlement and replay controls. Only fleet
procurement, ship-bound actions and military tutorial lessons vary. Guest AI
mirrors also preserve the rule set, functional damage and aircraft losses.

Military fleets start empty with 300 coins. Purchases follow the catalog in
`NavalMilitary.MODELS` / `MILITARY_SHIPS`. Rotation is clockwise in 90-degree
steps. Hulls retain the classic one-cell separation rule. All purchased ships
must be deployed and at least one offensive capability must exist before ready.
Every action, including radar and successful hits, passes the turn.

Fighters target six vertical cells; torpedo aircraft target one. Routes enter
at row 1, turn one cell beyond the last target (possibly row 13), and return.
Active AA covers its destroyer's hull and surrounding cells; interception occurs
before attacking the covered cell and permanently loses the aircraft. Guns,
decks, radar and AA stop functioning when their own cells are hit. A player loses
when every non-decoy hull sinks or all guns and attacking aircraft are disabled;
radar and decoy survival alone cannot prevent defeat.

Verification: `npm run test:naval`, `npm run qa:naval-military`,
`node scripts/naval-military-online-smoke.mjs`, and the military / community MySQL
tests. Browser scripts default to `http://127.0.0.1:5173`, an existing allowed dev
origin, and accept `QA_BASE_URL` overrides.
