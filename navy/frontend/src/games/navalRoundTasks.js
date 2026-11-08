export const navalRoundTaskKey = (task, day = '') => `${task.day || day}:${task.id}`

export function navalRoundTaskDay(finishedAt) {
  const timestamp = Number(finishedAt)
  return Number.isFinite(timestamp) && timestamp > 0 ? new Date(timestamp + 8 * 60 * 60 * 1000).toISOString().slice(0, 10) : ''
}

export function navalRoundCompletedTasks(receipts = [], profile, day = '') {
  const latest = new Map([...(profile?.tasks || []), ...(profile?.pendingTasks || [])]
    .map(task => [navalRoundTaskKey(task, profile?.day), task]))
  const unique = new Map()
  for (const receipt of receipts) {
    if (!receipt?.id) continue
    const task = {...receipt, day:receipt.day || day}, key = navalRoundTaskKey(task)
    const current = latest.get(key)
    // A completed receipt absent from the fresh active/pending list was already claimed.
    const claimed = current ? Boolean(current.claimed) : Boolean(receipt.claimed || profile)
    unique.set(key, {...task, completed:true, claimed, canClaim:!claimed && Boolean(current?.canClaim)})
  }
  return [...unique.values()]
}

const taskMatches = {
  'classic-win':['MULTIPLAYER','CLASSIC'], 'classic-restriction':['MULTIPLAYER','CLASSIC'], 'classic-economy':['MULTIPLAYER','CLASSIC'],
  'military-win':['MULTIPLAYER','MILITARY'], 'military-restriction':['MULTIPLAYER','MILITARY'], 'military-economy':['MULTIPLAYER','MILITARY'],
  'ai-classic-win':['AI','CLASSIC'], 'ai-classic-streak':['AI','CLASSIC'], 'ai-classic-restriction':['AI','CLASSIC'],
  'ai-classic-survival':['AI','CLASSIC'], 'ai-classic-economy':['AI','CLASSIC'], 'ai-military-economy':['AI','MILITARY']
}

export function navalRoundTasksForMatch(receipts, profile, {day='', mode, ruleSet='CLASSIC'} = {}) {
  const matches = task => {
    if(typeof task?.id!=='string')return false
    const match=taskMatches[task.id.replace(/~\d+$/,'')]
    return Boolean(match && match[0]===mode && match[1]===ruleSet)
  }
  const completed=navalRoundCompletedTasks(Array.isArray(receipts)?receipts:[],profile,day).filter(matches)
  const shown=new Set(completed.map(task=>navalRoundTaskKey(task)))
  const activeDay=profile?.day || day
  const available=[]
  for(const task of profile?.tasks || []) {
    if(!task?.id)continue
    const current={...task,day:task.day || activeDay}
    const key=navalRoundTaskKey(current)
    if(!matches(task)||task.completed||task.claimed||current.day!==activeDay||shown.has(key))continue
    shown.add(key)
    available.push({...current,completed:false,claimed:false,canClaim:false})
  }
  return [...completed,...available]
}

export function navalRoundTaskProgress(task) {
  if(task?.progress==null||task?.target==null)return '未完成'
  const progress=Number(task?.progress), target=Number(task?.target)
  return Number.isFinite(progress)&&Number.isFinite(target)&&target>0
    ? `${Math.max(0,Math.min(target,progress))} / ${target}` : '未完成'
}
