const ROOT = '/entertainment/naval/v2/audio/'
const NAMES = ['cannon', 'explosion', 'splash', 'fighter', 'bomber', 'torpedo', 'submarine', 'sonar', 'intercept', 'sink', 'deploy', 'ready', 'transition', 'win', 'lose', 'mine', 'tick', 'notification', 'repair', 'tow']
const ALIASES = { repairSupport:'repair', depthCharge:'submarine', shot: 'cannon', hit: 'explosion', miss: 'splash', water: 'splash', radar: 'sonar', antiAir: 'intercept', join: 'transition', purchase: 'deploy', ui: 'deploy', countdown: 'tick' }
const VOLUMES = { cannon: .52, explosion: .62, splash: .42, fighter: .36, bomber: .4, torpedo: .36, sonar: .55, intercept: .42, sink: .62, deploy: .3, ready: .4, transition: .4, win: .55, lose: .5, mine: .4, tick: .65, notification: .38, repair: .65, tow: .6 }
let context, master, loading, muted = false, playbackGeneration = 0
const buffers = new Map(), encoded = new Map(), active = new Set()
try { muted = localStorage.getItem('naval_sound_muted') === '1' } catch { }

export function getNavalMuted() { return muted }
export function setNavalMuted(value) {
  muted = Boolean(value)
  if (master && context) master.gain.setTargetAtTime(muted ? 0 : .8, context.currentTime, .035)
  try { localStorage.setItem('naval_sound_muted', muted ? '1' : '0') } catch { }
}

function audioContext() {
  const Type = globalThis.AudioContext || globalThis.webkitAudioContext
  if (!Type) return null
  if (!context || context.state === 'closed') {
    context = new Type()
    master = context.createGain(); master.gain.value = muted ? 0 : .8
    const limiter = context.createDynamicsCompressor()
    limiter.threshold.value = -12; limiter.knee.value = 18; limiter.ratio.value = 5; limiter.attack.value = .003; limiter.release.value = .2
    master.connect(limiter); limiter.connect(context.destination)
  }
  return context
}

export async function preloadNavalAudio(onProgress) {
  if (loading) { await loading; onProgress?.(1); return }
  loading = (async () => {
    let done = 0
    await Promise.all(NAMES.map(async name => {
      const response = await fetch(`${ROOT}${name}.wav`)
      if (!response.ok) throw new Error(`音效载入失败：${name}`)
      encoded.set(name, await response.arrayBuffer())
      onProgress?.(++done / NAMES.length)
    }))
  })()
  try { await loading } catch (error) { loading = null; throw error }
}

export async function unlockNavalAudio() {
  const ctx = audioContext()
  if (!ctx) return false
  if (ctx.state === 'suspended') await ctx.resume()
  return ctx.state === 'running'
}

export function playNavalSound(kind) {
  if (muted) return
  const generation = playbackGeneration
  const name = ALIASES[kind] || (NAMES.includes(kind) ? kind : 'deploy')
  // AudioContext creation/resume occurs synchronously on the initiating gesture.
  const ctx = audioContext()
  if (!ctx) return
  if (ctx.state === 'suspended') ctx.resume().catch(() => {})
  ;(async () => {
    try {
      if (!encoded.has(name)) await preloadNavalAudio()
      if (!buffers.has(name)) buffers.set(name, await ctx.decodeAudioData(encoded.get(name).slice(0)))
      if (muted || ctx.state !== 'running' || generation !== playbackGeneration) return
      while (active.size >= 8) { const oldest = active.values().next().value; oldest.stop(); active.delete(oldest) }
      const source = ctx.createBufferSource(), gain = ctx.createGain()
      source.buffer = buffers.get(name); gain.gain.value = VOLUMES[name] || .45
      source.connect(gain); gain.connect(master)
      source.onended = () => { active.delete(source); source.disconnect(); gain.disconnect() }
      active.add(source); source.start()
    } catch { /* A blocked audio device must not interrupt a game action. */ }
  })()
}

export function stopNavalSounds() {
  playbackGeneration++
  for (const source of active) { try { source.stop() } catch { } }
  active.clear()
}
