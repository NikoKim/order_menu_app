# Navy

Independent naval battle application. Requires Node.js 20+, Java 17+, Maven 3.9+ and MySQL 8+.

## Database

Run `backend/database/create-database.sql` once as a MySQL administrator. The application user is restricted to the `navy` database. Flyway creates only game and account tables on first backend startup. Existing application data is not imported.

Connection defaults: `127.0.0.1:3307/navy`, user `navy_admin`. Override with `NAVY_DB_HOST`, `NAVY_DB_PORT`, `NAVY_DB_NAME`, `NAVY_DB_USER`, `NAVY_DB_PASSWORD`.

## Run

Backend, from `backend`: `mvn spring-boot:run` (port 8082).

Frontend, from `frontend`: `npm ci`, then `npm run dev` (http://localhost:5175).

Build: `mvn package` and `npm run build`. Run the backend with `java -jar target/navy-backend-1.0.0.jar`. Serve frontend `dist` with SPA fallback and proxy `/api` to the backend, or set `VITE_BACKEND_ORIGIN` at build time. Set `NAVY_FRONTEND_ORIGIN` to the deployed frontend origin and `NAVY_PORT` to change the API port.

The `ai` directory contains optional tests, migration helpers and validation output only. It can be removed without affecting installation, builds or runtime. No sibling project, Redis, order system or external account service is required.

## Accounts And Mail

Login accepts a commander nickname or a verified email. Registration requires matching passwords, a single-use four-character image captcha and a six-digit email code. Login sessions last 90 days. Supported email domains are qq.com, 163.com, 126.com and gmail.com; the normalized email has a database unique index. Image challenges expire after three minutes; email codes expire after five minutes and allow five attempts. Sending mail requires a valid image challenge and is limited per IP and email address.

SMTP uses TLS on port 465. Set `NAVY_MAIL_USER` and `NAVY_MAIL_PASSWORD`, or provide them in the ignored `backend/.env.smtp` file. Never commit the authorization code. Verification mail uses the display name `海战棋` by default; override it with `NAVY_MAIL_SENDER_NAME`. This changes only the sender's display name, not the authenticated mailbox address. Registration and email-binding messages contain matching UTF-8 HTML and plain-text alternatives, with a selectable six-digit code and five-minute expiry. The normal tests capture MIME messages locally and never send mail; the opt-in SMTP connection test only authenticates, without sending.

The personal profile entry opens login for guests and the current account's information and medal wall for signed-in players. Legacy accounts without email can bind an unused supported address using an image captcha and an email code. Binding codes are single-use, scoped to the account and separate from registration codes; conditional updates and the existing unique email index protect concurrent binding. Existing bound addresses cannot be replaced through this flow.

Avatar uploads accept PNG/JPEG up to 2 MB and four million pixels, center-crop to a square and re-encode as a maximum 512px PNG. The profile previews both square and circular crops before explicit confirmation. Uploaded images have generated immutable URLs. After a successful replacement commits, the previous local avatar file is deleted; failed or rolled-back replacements retain the previous avatar. Account-row locks serialize concurrent replacements. Historical records referring to a deleted avatar use the default avatar. Configure storage with `NAVY_AVATAR_DIRECTORY`: local development defaults to `backend/uploads/avatars` when starting from `backend`, while the BaoTa production configuration uses `/www/wwwroot/navy/assets`. This runtime directory is separate from the frontend build's `frontend/assets`. Persist and back it up across deployments. See `deploy/README.md` for permissions and moving existing images without changing their URLs.

Flyway V16 adds the private account preference `defaultAllowSpectators`, initially true for compatibility. The own-profile switch reads and saves it through `GET/PATCH /api/account/settings`; it is not exposed on another commander's public profile. New AI games use the human player's preference. Multiplayer defaults permit spectating only if both players permit it; guests default to true. Joining, seating or merging existing waiting rooms can only tighten their current permission, never automatically reopen a room already closed by a player. A fresh next round recalculates defaults; changing the account preference does not modify a battle already underway.

Players may override spectating inside the room. Successful enable/disable operations share one server-enforced 60-second room cooldown, retained across rounds; duplicate-state actions are rejected without extending it. Room views include `spectatorSettings` with the manual revision, actor, round and cooldown deadline. The opponent receives a toast on a new manual enable, not on initial loads, reconnect snapshots, default reconciliation or duplicate socket updates. The UI disables the switch and shows remaining cooldown. Guest-local AI mirrors retain equivalent client-side cooldown behavior.

## Account Progression And Daily Tasks

Flyway V6 adds account progression and reward receipts, V8 adds AI daily tasks, and V9 adds account coin fractions and wager escrow. V11 changes new account balances to 100 coins, adds manual task claims and sets the existing account 小鲨鱼 to 100 coins once. Other existing balances are preserved. Historical games are not rewarded retroactively. `GET /api/naval/progression` returns only the authenticated account's score, wallet and six tasks. Account coins are separate from the 300-coin combat procurement budget and persist between rounds.

Clicking a commander's avatar opens their public player profile through the existing random medal UUID. It includes rank artwork, level, score progress, total played/won counts, the latest ten finished battle summaries and the medal wall. Statistics combine server-hosted AI and multiplayer games in both rule sets; preparation cancellations, local mirrors and tutorials are excluded. Guests have battle statistics and medals but no fabricated account rank. Recent summaries contain only finish time, mode, rule set, outcome and opponent name: no room/game identifiers, board layouts, actions, wagers, wallets, tasks, email or account preferences are exposed. First-time and AI-only players receive a public UUID through the existing medal backfill path, without marking their historical medals as already migrated. Full profile data loads only when opened, not once per avatar in a list. Medal overlays do not change avatar or row height.

Rank ranges (inclusive):

| Level | Rank | Score |
| --- | --- | --- |
| 1 | 下士 | 0-300 |
| 2 | 中士 | 301-600 |
| 3 | 上士 | 601-1000 |
| 4 | 少尉 | 1001-1500 |
| 5 | 中尉 | 1501-2000 |
| 6 | 上尉 | 2001-2800 |
| 7 | 少校 | 2801-3800 |
| 8 | 中校 | 3801-4800 |
| 9 | 上校 | 4801-6000 |
| 10 | 少将 | 6001-7500 |
| 11 | 中将 | 7501-9000 |
| 12 | 上将 | 9001-12000 |

At 1000/2800/6000 points, advancement requires 5/10/16 consecutive natural multiplayer victories. Classic and military wins both count toward the same promotion streak. The major-general gate uses 6000, resolving the repeated 2800 in the original request against its stated rank ranges. A game that reaches a gate does not also count as its first promotion win. During the gate all match/task points are frozen, but task coins still accrue. Completing the streak unlocks 1001/2801/6001 points without additionally awarding that game's frozen points. Scores cap at 12000. A natural loss resets the streak; quitting/disconnecting resets the quitter's streak and never advances the opponent's streak.

Natural multiplayer victories award the winner 5 points. A classic loser earns 1 point if the winner has two unsunk ships, or 2 if only one remains. A military loser earns 1 point if the winner has exactly one ship with an active offensive capability. Server-hosted AI games can complete their selected daily tasks, but never grant multiplayer match points or change promotion streaks. Tutorials, local mirrors, pre-battle cancellations and quit/disconnect results grant no tasks or points. Guests can match, join and continue multiplayer games without a wager, but do not receive account rewards.

Six tasks refresh at midnight UTC+8, using the server's battle-finish timestamp. Tasks 3 and 4 are always selected; two are sampled without replacement from 5/6/7/8 and two from 1/2/9/10/11/12. Both selections and individual restrictions persist per account/day, including reconnects and page refreshes. Each selected task pays at most once per assignment cycle:

| No. | Task | Account Coins | Points |
| --- | --- | --- | --- |
| 1 | Win a multiplayer classic game | 10 | 10 |
| 2 | Win a multiplayer military game | 10 | 10 |
| 3 | Win multiplayer classic without using one randomly selected anti-air/fighter/bomber/torpedo item | 20 | 20 |
| 4 | Win multiplayer military without deploying one randomly selected carrier/destroyer/repair-tug/submarine category | 30 | 30 |
| 5 | Win a server AI classic game | 5 | 5 |
| 6 | Win three consecutive server AI classic games | 30 | 30 |
| 7 | Win AI classic without using one randomly selected fighter/torpedo/anti-air item | 10 | 10 |
| 8 | Win AI classic with at least 3 surviving patrol boats / 2 destroyers / 1 cruiser (one daily choice) | 20 / 30 / 40 | 20 / 30 / 40 |
| 9 | Win multiplayer classic spending at least 30 fewer combat coins than the opponent | 20 | 20 |
| 10 | Win multiplayer military spending at least 30 fewer combat coins than the opponent | 20 | 20 |
| 11 | Win AI military spending at least 30 fewer combat coins than the opponent | 10 | 10 |
| 12 | Win AI classic spending at least 30 fewer combat coins than the opponent | 10 | 10 |

All tasks require spectating to remain disabled throughout battle. It can be disabled before battle starts; enabling it at any point during battle disqualifies that round even if disabled again. Merely having no spectators is insufficient. This restriction does not affect match points, promotion streaks or wager payouts. Old rounds without a verifiable spectating-history version cannot complete new tasks. Classic AI procurement now retains its actual unspent balance (10 coins for the initial 290-coin loadout), including older preparation snapshots, so economy thresholds compare actual costs.

Classic anti-air counts as used only if it actually intercepts an enemy aircraft; purchasing or positioning it alone does not fail that task. A launched aircraft counts even when intercepted. The military restriction excludes the entire selected ship category, not merely its attack button. Tasks complete automatically on qualifying natural wins, but coins and task points are awarded only by an explicit claim. A dot on the profile icon signals pending rewards, including unclaimed tasks from earlier days. `POST /api/naval/progression/tasks/{taskId}/claim` accepts an optional `day` and is idempotent. Tasks already paid before V11 are marked claimed during migration.

The AI classic streak belongs to that UTC+8 day. A finished AI classic loss, explicit quit or spectating-ineligible round resets it; other modes neither increment nor reset it. Task points freeze if claimed during a promotion gate, but task coins still pay.

Flyway V15 adds persisted assignment selections and AI task variants. Existing current-day assignments are upgraded once under the wallet lock: retained task progress and claim bits survive, and displaced unclaimed rewards are archived for later claim. Previous dates retain their original task definitions. Task-reset cards archive pending rewards, reroll the six tasks, and advance the cycle so stale requests cannot claim a different assignment.

Settlement lists only the current round receipt's newly completed tasks, never earlier completed or pending tasks from the profile. Task rows appear after the initial wager, match-point and rank-promotion presentations finish, and keep their positions during claims. The pot is centered and the wallet is aligned with the player's seat. Match coins fly from the pot; task coins fly from the claimed task's own coin icon to the wallet. Wallet counters change one coin per update, with the signed delta shown after the animation. Historical rewards remain claimable from the personal task page.

## Fixed Wagers

Each registered player pays their own rank-defined stake. Level 1-12 amounts are 10, 20, 30, 50, 70, 100, 150, 200, 250, 320, 400 and 500 coins. Level 1 can instead choose zero; guests always stake zero. Each player's next-round rank determines their own next fee; both players confirm again before another round starts.

Escrow is reserved transactionally on joining a seat. Natural combat victory pays the winner their own stake plus the smaller of the two stakes; unused opponent escrow is returned to the loser. For 10 versus 20, a 10-stake winner receives 20 and the loser receives 10; a 20-stake winner receives 30. A pre-battle cancellation refunds both players fully. A battle-ending quit/disconnect refunds the quitter 50% of their stake; the opponent receives their own stake back plus 10% of the quitter's stake, capped at the opponent's own stake. The unreturned portion is removed from circulation (40% of the quitter's stake when the compensation cap does not apply). Zero-stake guests never receive coin compensation. Half coins and other fractions are retained exactly in the shared wallet. Quit results still award no grade/task rewards.

Invites quote the joining player's own stake before charging, and stale quotes are rejected. Automatic matching preserves each player's already reserved escrow without adjusting it to the host's rank. Zero-stake games are supported throughout joining, seating and rematches.

## Administration And Notifications

V12 seeds the reserved administrator account `NikoKim` using a BCrypt hash and a database role, extends existing unexpired sessions to the new lifetime, and creates notification and send-receipt tables. `/admin` is accessible from the administrator's top-right shield icon. Every admin API independently checks the stored role.

Administrators can search accounts by nickname, email or ID, choose up to 100 recipients, and send a message with an optional per-recipient coin gift. A stable UUID `requestId` makes retries idempotent. The notification bell displays an unread dot and opens the shared community side panel on desktop and mobile; merely opening a message does not credit its gift. Reading and claiming are explicit per-user operations, and gift claims lock the notification and wallet in one transaction to prevent duplicate awards. Notification summaries share one refresh every 20 seconds only while visible; full message lists are fetched on demand and support pagination. Logout is a text action on the personal profile page.

V14 also permits administrators to attach up to five shop item types, with at most 100 individual copies per recipient. Each type has an administrator-selected validity of 1-8760 hours starting at the successful claim timestamp. Coin/item gift mail must be claimed within 24 hours of sending; ordinary messages do not expire. Existing coin gift deadlines are derived from their original send time. Claimed mail remains safely replayable after its deadline without awarding anything twice. Gift items are marked `GIFT`, retain the source notification, and do not count toward daily purchase limits. The item catalogue endpoint is administrator-only.

## Shop And Inventory

Flyway V13 adds shop inventory, equipment, idempotent operation receipts, and archived task-reset rewards. The top-right shop entry uses the existing community panel. The personal profile's inventory tab supports the same item actions. Purchases, card use and equipment changes require an authenticated account, a UUID request ID, and server-side ownership checks; wallet and item updates commit in one transaction.

| Item | Price | Validity From Purchase | Daily Limit (UTC+8) |
| --- | --- | --- | --- |
| Task reset card | 50 | 24 hours, single use | 1 |
| 100 rank-point card | 50 | 24 hours, single use | 1 |
| Captain chat bubble | 50 | 7 days | None |
| Pirate chat bubble | 50 | 7 days | None |
| Orange tabby chat bubble | 100 | 7 days | None |

Task reset clears today's task progress, AI streak and claimed bits, selects different restrictions, and starts a new task cycle. Completed unclaimed rewards are archived and remain claimable with opaque IDs through the existing task endpoint; stale pre-reset task IDs cannot claim a new cycle's rewards. A point card grants exactly 100 points or remains unused if promotion gates or the score cap prevent the complete award. Unused expired cards cannot be used. Only one active owned bubble can be equipped; expired decorations cannot be equipped and stop decorating messages. The catalog directs players with an active copy to their inventory instead of offering a duplicate purchase.

Lobby, battle and spectator chat receive the equipped style and expiry from the server, never from client-supplied styling. Public opponent rank badges appear on the matching-found presentation. Bubble skins use fixed, proportionally scaled corners and repeated middle edges without changing the ordinary bubble's content metrics or height. There are no separate corner overlays. Current artwork uses canonical unversioned filenames in `frontend/public/naval/shop`; prompts are recorded in `ai/shop-assets-prompts.md`.

Mobile chat sizes its panels to the visible `visualViewport`, including keyboard resize and pan offsets, instead of assuming that `100dvh` remains unobscured. Physical orientation is retained while the keyboard is open. Shared dialogs and toast portals track native fullscreen changes and the same visible viewport. Action feedback uses the shared notice system rather than inline page messages.

Lobby and battle chat share the same message-row and composer components, including avatars, grouped timestamps, bubble alignment, emoji/sticker tools and the send control. Battle sender avatars are resolved by the message's public sender ID, never by a potentially reassigned seat; unresolved historical senders keep a neutral avatar. Business sending and spectator permissions remain separate, and sticker trays stay within their panel/fullscreen root.

Settlement groups the wallet and rank/experience together on the player's side, with tasks in the opposite column. A separate central pot row exists only for the initial wager animation, then collapses; wallet-only games never show that row, and later task claims do not reopen it. This round's completed tasks appear first, followed by today's assigned incomplete tasks matching both the AI/PvP type and classic/military rules, with their real progress and target. Earlier completed tasks are not presented as new completions. Static rank and wallet summaries are independent of animated reward receipts, so returning from the board retains them without replaying rewards. Earned experience still shows its increment, animation and sound. The existing cash, experience and task-reveal order is unchanged, and claimed task coins still fly from their own task row.

Natural wager settlement animates only the matched portion of both stakes. A loss starts the visible pot at the smaller stake, then transfers that same amount from the displayed wallet into the pot one coin at a time. A win transfers twice the smaller stake into the wallet. Unmatched escrow refunds are already reflected in the display baseline; animations never mutate the authoritative balance or receipt.

Matched-player stakes animate independently from the two avatar positions into the central pot, using exact own-stake totals. The dedicated `match-coins.wav` follows existing audio unlock/mute behavior. Reduced-motion settings skip the travel animation and immediately show the final pot; zero-stake seats contribute neither coins nor sound.

## Repair Tug And Placement

Military cruisers cost 25 coins and share the battleship type selector. The repair tug costs 35 coins, occupies 2x2 cells and has its own two-ship cap. Its repair and tow modules rotate with the hull; damaged modules disable their corresponding operation. Support affects complete non-submarine hulls inside a tug-centered 12x12 region clipped at sea edges. Towing preserves historical shot coordinates and remaps hull-local damage; repairs restore one damaged non-aircraft function cell, never a sunk hull or a lost aircraft. Spent mines can become disposable torpedo-blocking scrap mines, up to two per turn; when only one legal mine remains, repairing it automatically ends the turn, otherwise an explicit skip is available.

Desktop placement commits on click/drop with toolbar rotation; mobile placement retains confirm/cancel/rotate previews. Desktop combat actions still require confirmation and highlight their supplying military ship. Matchmaking and loading use mutually exclusive out-in presentation. Pre-battle exits in every mode are cancelled without recording a result. Uncertain question-mark cells are valid tow destinations; confirmed attacked cells remain forbidden. Ghosts use the actual selected military model and orientation. Historical hit cells show a question mark after repair or towing (optional Shot.uncertain=true). Only another real attack resolves the marker to its authoritative hit/miss result, replacing the coordinate entry without duplication. Legacy saves and replay follow the same event order; AI treats uncertain evidence as unknown. The partial mine-repair turn can end beside the mobile countdown or in the desktop turn action bar, never over the board. Scrap mines have an independent fleet/scrap-mine.png sprite: gunfire destroys them without a mine detonation, while colliding torpedoes stop and explode themselves; result=mine plus optional mineKind=scrap distinguishes the effect without changing historical shot counts. Edge rotations clamp inside the board; invalid positions remain red previews. Tutorial v7 and replay include support actions. Support events are permission-filtered before live delivery so opposing clients cannot learn new hull coordinates. No SQL migration is required for these JSON fields.

Military hull textures include the ship position in their cache key because damage coordinates are absolute. Moving a ship can map the same coordinate to a different module; repairing it must therefore refresh the corresponding red damage overlay instead of reusing an old hull texture. Other damaged modules remain visibly damaged.

The first approved pixel-art repair-tug assets are retained, with separate repair/tow modules, a four-frame repair effect and independent repair.wav/tow.wav. Auxiliary generation/QA files remain under ai. AI handles the new hull dimensions and does not treat support tools as attacks or read unexposed relocated damage; choosing active repair/tow tactics is not yet part of its strategy.

## Live Synchronization

Automatic matching is event-driven, not a periodic room scan. Flyway V5 adds an indexed generated rule key. Creation/re-entry, committed changes to an automatic waiting room, socket re-subscription and one startup recovery wake a bounded coalescing worker. It considers a bounded batch of up to 32 same-rule waiting rooms for a pair, retires the duplicate room with a durable authorized handoff, and pushes both changes through existing sockets. Each player's escrow moves transactionally without changing its amount. The retired room ID resolves to the destination for reconnects, actions and invitations. Private invitation rooms and different rule sets never enter the same queue. No changes means no matching work.

Only failed database work receives exponential-backoff recovery (one pending retry per rule, capped at 30 seconds). Successful empty queues stop completely; this is not a periodic matching poll.

Matching shares the existing database matchmaking mutex with seat-changing operations for atomicity, and processes at most 16 pairs before yielding. This prevents cancellation/join races but is still a potential throughput limit under very high arrival rates; it is not an unlimited-capacity design. Ordinary server-side attacks/deployment and steady socket heartbeats do not acquire this matching mutex. Process-local notifications still require an inter-instance event bus before multi-JVM deployment.

Room subscriptions are indexed by room ID. A bounded two-worker/64-job loader reads each committed room once and reuses player/spectator permission-group projections. Each connection has one coalescing pending state, served by a four-worker/128-job sender pool; stalled sends are closed for reconnect/resynchronization. Reconnect baselines are bounded by room, TTL, entry count and serialized size. Authentication is rechecked periodically; actions and win/loss remain server-authoritative HTTP operations.

While a live stream is healthy, normal mutations request compact HTTP acknowledgements (`accepted`, room ID and version). The browser waits for that authoritative stream version rather than downloading a duplicate complete room in each action response. A disconnected/timed-out stream falls back to a full authorized room read. Leave/seat-change compatibility responses remain unchanged. Reconnect baselines are scoped to the identity and permission under which they were actually delivered.

Each page shares one authenticated connection at `/api/naval/live`, including in the lobby. The first frame sets `multiplex: true`, credentials, `subscriptions: { lobby, directory, presence }`, an optional game `roomId`/`scope`/`version`, and the lobby `afterId`/`cardVersion`. Later subscription frames replace these interests. Directory subscriptions exist only while that panel is open; unread notifications reuse the lobby feed. Public feeds send initial history/snapshots, then incremental messages and public directory deltas. Reconnect uses message IDs and room versions; a missing baseline or version gap requests a snapshot. A ten-second socket heartbeat maintains presence; the server aggregates and pushes online counts.

During `BATTLE`, explicit leave or a confirmed page departure starts a 60-second reconnect grace instead of settling immediately. Each human player has three grace periods per round; their fourth new departure immediately loses. Repeated notifications for the same absence do not consume another attempt, and a new round resets the counts. Both players' battle actions, turn clock, pending presentation and AI clock pause; seats remain reserved. Explicit reconnect with a fresh page token resumes the original remaining time only after all absent players return. A missing player at the exact deadline loses through the existing `DISCONNECTED` settlement path, retaining its zero rank/task rewards and partial-exit wager rules. Chat and spectator settings remain available while paused. Ordinary reads/heartbeats cannot undo a departure; retired page tokens and cancelled pending reconnect intents cannot take control back. Silent network loss is detected after 15 seconds without a heartbeat, then receives a full 60 seconds from detection while battle clocks freeze at the stale threshold. Waiting, placement and supply departures still cancel rather than starting this grace. Local guest AI uses the same boundary and timer preservation, and lobby expiry checks are bounded read-only confirmations, never implicit reconnects.

Battle countdowns expand and collapse a reserved animated slot, moving nearby status elements aside in the appropriate direction. The last visible value remains during exit, and reduced-motion preferences skip transitions. Unspent combat procurement coins remain visible during preparation only, not during battle; account-wallet balances are unaffected.

Active players can opt into transient connection-quality frames with `networkQuality: true` on their page subscription. Native WebSocket Ping/Pong measures actual round-trip time on the same connection without extra HTTP requests or database writes. Only opted-in current players receive their room's seat-bound measurements; old clients continue using the existing protocol, and unmeasured peers remain unknown rather than being labelled weak. Network metadata is not archived or used for game timers, rewards or disconnect penalties. The Wi-Fi control appears only for a poor/unavailable connection during `BATTLE`, never during placement or supply, and opens the same panel presentation as the audience control with measured latency. Its button matches adjacent action controls, before the player's icons and after the opponent's icons. Recovery hides it with a layout transition. Client-side silence/disconnection clears opponent measurements rather than blaming the peer, and background/resume handling gives new samples a grace period. Normal room heartbeat traffic also prevents false warnings when connected to an older backend without quality frames.

Lobby medal lookups are batched and cached; private sender identity is never returned and `mine` is projected separately for each viewer. `/lobby/sync` remains as an incremental compatibility endpoint, not a polling loop. Caches coordinate loading per key, outside global locks. No-op acknowledgements and heartbeat-only differences do not rewrite the JSON snapshot or advance its version.

Game messages contain versioned changes to the viewer-filtered state, not private enemy boards. Read-only HTTP snapshots remain available for explicit recovery and action timeouts, but there is no fixed room polling loop. Reads first check the small SQL version and reuse a bounded committed-state cache. Writes and cache publication respect transaction commit; rollbacks never populate that cache.

Guest AI mirrors publish only changed state while spectators are allowed. Unchanged games use the shared heartbeat, and audience changes are pushed as metadata. Closing spectators sends only the room ID and stops board uploads; re-enabling publishes the current snapshot. Local mirrors never produce server rankings.

Flyway V4 backfills historical rankings once. Settlement updates indexed `naval_ranking_totals`, guarded by unique `naval_ranking_rounds.game_id`; leaderboard requests sort/page these totals instead of parsing history. `/leaderboard/page` returns records and total count. Action requests carry a UUID `requestId`; the server stores its payload hash and acknowledgement atomically with the action, returning the original version on retries and rejecting ID reuse with a different payload. Receipts cascade when their room is deleted. Older clients without IDs remain compatible but cannot receive this retry guarantee.

An indexed due-time scheduler processes deadlines and AI actions instead of rewriting all live rooms. Public data uses short bounded caches. `frontend/nginx.conf` includes WebSocket forwarding, HTTP compression and asset caching; adjust its deployment path before use.

This configuration targets a single backend instance. Presence, heartbeats, captcha/email challenges, caches and reconnect history are in process memory; restarting can require a fresh challenge/full snapshot. Multi-instance deployment needs shared presence/challenge storage and cross-instance room notifications before load balancing.
