import assert from 'node:assert/strict'
import test from 'node:test'
import { replayAttacks, replaySnapshot } from '../src/games/navalReplay.js'
import { messageSticker, NAVAL_STICKERS, stickerMessage } from '../src/games/navalStickers.js'
import { createMilitaryGame, militaryAttack, defaultMilitaryFleet } from '../src/games/navalMilitary.js'
import { localBattleResult } from '../src/games/navalEngine.js'
import { navalBoardEvents, navalTorpedoDuration, NAVAL_AIRCRAFT_DURATION } from '../src/games/navalPresentation.js'

const detail = { players: [{ name: 'A', ships: [{ id: 'a', x: 0, y: 0, w: 1, h: 1, hits: ['0,0'] }] }, { name: 'B', ships: [{ id: 'b', x: 2, y: 2, w: 2, h: 1, hits: ['2,2'] }] }], events: [{ id: 0, player: -1, weapon: 'start' }, { id: 1, player: 0, weapon: 'shot', cells: [{ x: 2, y: 2, result: 'hit' }] }, { id: 2, player: 1, weapon: 'bomber', row: 3, cells: [] }, { id: 3, player: 0, weapon: 'torpedo', cells: [{ x: 4, y: 4, result: 'mine' }] }, { id: 4, player: 1, weapon: 'radar', cells: [{ x: 0, y: 0, result: 'detected' }] }] }
test('replay resets final damage then reconstructs deterministic attack order without mutating archive', () => {
  const original = JSON.stringify(detail)
  assert.equal(replayAttacks(detail).length, 4)
  assert.deepEqual(replaySnapshot(detail, 0).players[1].ships[0].hits, [])
  assert.deepEqual(replaySnapshot(detail, 1).players[1].ships[0].hits, ['2,2'])
  assert.equal(replaySnapshot(detail, 1).players[1].shots.length, 1)
  assert.equal(replaySnapshot(detail, 4).players[0].shots.length, 0, 'radar is not damage')
  assert.equal(JSON.stringify(detail), original)
})

test('military replay preserves mine-lost submarine shots without duplicate hull damage',()=>{
  const room=createMilitaryGame();room.phase='BATTLE';room.battleStarted=true;room.players[0].ships=defaultMilitaryFleet()
  room.players[1].mines=[{x:0,y:11}]
  const source=room.players[0].ships.find(s=>s.kind==='submarine') || (room.players[0].ships=[{kind:'submarine',id:'test-submarine',x:0,y:0,w:1,h:3,hits:[],lostPlanes:[]},{kind:'cruiser',id:'test-gun',x:4,y:4,w:1,h:3,hits:[],lostPlanes:[]}],room.players[0].ships[0])
  militaryAttack(room,0,{shipId:source.id,weapon:'submarine',x:4,y:11})
  const detail=localBattleResult(room),snapshot=replaySnapshot(detail,1)
  const lost=snapshot.players[0].ships.find(s=>s.id===source.id)
  assert.equal(detail.ruleSet,'MILITARY');assert.equal(lost.hits.length,3)
  assert.equal(snapshot.players[0].shots.filter(c=>c.result==='hit').length,3)
  assert.deepEqual(detail.players[0].itemsPurchased,{})
})
test('replay restores consumed mines and anti-air rows until the corresponding attack', () => {
  assert.deepEqual(replaySnapshot(detail, 0).players[1].mines, [{ x: 4, y: 4 }])
  assert.deepEqual(replaySnapshot(detail, 0).players[0].aa, [3])
  assert.deepEqual(replaySnapshot(detail, 3).players[1].mines, [])
  assert.deepEqual(replaySnapshot(detail, 2).players[0].aa, [])
})
test('eight dedicated stickers round trip; arbitrary message text is never treated as an image URL', () => {
  assert.equal(NAVAL_STICKERS.length, 8)
  for (const sticker of NAVAL_STICKERS) assert.equal(messageSticker(stickerMessage(sticker.id))?.id, sticker.id)
  assert.equal(messageSticker('::naval-sticker:https://example.com::'), null)
  assert.equal(messageSticker('hello'), null)
})

test('replay and board presentation include mine backfire on the attacking board without adding an attack', () => {
  const result=structuredClone(detail)
  result.events=[{id:1,player:0,weapon:'shot',cells:[{x:0,y:0,result:'mine'}],backfire:[{x:0,y:0,result:'hit'}]}]
  assert.equal(replayAttacks(result).length,1)
  assert.deepEqual(replaySnapshot(result,0).players[0].ships[0].hits,[])
  assert.deepEqual(replaySnapshot(result,1).players[0].ships[0].hits,['0,0'])
  assert.equal(navalBoardEvents(result.events,0)[0].weapon,'mine-rebound')
  assert.equal(navalBoardEvents(result.events,0)[0].cells[0].delay,650)
  assert.equal(navalBoardEvents(result.events,1)[0].weapon,'shot')
})

test('dropped torpedoes travel slightly slower than aircraft over the same distance', () => {
  for(const distance of [48,192,528]) {
    const travel=navalTorpedoDuration(48,48+distance), flight=distance*NAVAL_AIRCRAFT_DURATION/745
    assert(travel>flight); assert(travel<flight*1.25)
  }
  assert.equal(navalTorpedoDuration(100,100),120)
})
