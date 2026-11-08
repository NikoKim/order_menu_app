export const NAVAL_AIRCRAFT_DURATION = 1850
export function navalTorpedoDuration(startX,endX) {
  return Math.max(120,Math.abs(endX-startX)*NAVAL_AIRCRAFT_DURATION/745*1.18)
}

export function navalBoardEvents(events, board) {
  return (events || []).flatMap(event => {
    if(event.weapon==='repairSupport')return event.player===board?[event]:[]
    if (event.player === 1-board) return [event]
    if (event.player !== board || !event.backfire?.length) return []
    return [{ ...event, id: `${event.id}-backfire`, weapon: 'mine-rebound', cells: event.backfire.map(cell => ({ ...cell, delay: event.weapon==='depthCharge'?1350:event.weapon === 'shot' ? 650 : 300+1850*(24+(cell.x+.5)*48+55)/745 })), text: event.weapon==='depthCharge'?'突击舰损毁':'水雷反击同坐标海域' }]
  })
}
