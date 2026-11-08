import Phaser from 'phaser'
import { drawMilitaryShip } from './navalMilitaryArt.js'
import { militaryComponents, militaryCoverage, militaryJamming, militaryPlacement } from './navalMilitary.js'
import { playNavalSound } from './navalAudio'
import { NAVAL_AIRCRAFT_DURATION, navalTorpedoDuration } from './navalPresentation'
import { canAnimateFleetPlacement, fleetPlacementMotion, sampleFleetPlacement, shortestFleetTurn } from './navalFleetMotion.js'

export const BOARD_SIZE = 624
const GAP = 24, CELL = 48, SEA = 576, ROOT = '/naval/fleet/'
const SPRITES = ['patrol', 'destroyer', 'cruiser', 'battleship', 'fighter', 'bomber', 'torpedo', 'torpedo-projectile', 'mine', 'scrap-mine', 'antiAir', 'radar', 'submarine', 'stealth']
const SHIPS = { 1: 'patrol', 2: 'destroyer', 3: 'cruiser', 4: 'battleship' }
const AIRCRAFT = ['fighter', 'bomber', 'torpedo']
const COMBAT = ['shot', ...AIRCRAFT, 'radar', 'submarine', 'mine-rebound', 'military-sortie', 'mine','depthCharge','repairSupport']
const GLYPHS = {
  A: '01110/10001/10001/11111/10001/10001/10001', B: '11110/10001/10001/11110/10001/10001/11110',
  C: '01111/10000/10000/10000/10000/10000/01111', D: '11110/10001/10001/10001/10001/10001/11110',
  E: '11111/10000/10000/11110/10000/10000/11111', F: '11111/10000/10000/11110/10000/10000/10000',
  G: '01111/10000/10000/10111/10001/10001/01110', H: '10001/10001/10001/11111/10001/10001/10001',
  I: '11111/00100/00100/00100/00100/00100/11111', J: '00111/00010/00010/00010/10010/10010/01100',
  K: '10001/10010/10100/11000/10100/10010/10001', L: '10000/10000/10000/10000/10000/10000/11111',
  0: '01110/10001/10011/10101/11001/10001/01110', 1: '00100/01100/00100/00100/00100/00100/01110',
  2: '01110/10001/00001/00010/00100/01000/11111', 3: '11110/00001/00001/01110/00001/00001/11110',
  4: '00010/00110/01010/10010/11111/00010/00010', 5: '11111/10000/10000/11110/00001/00001/11110',
  6: '01110/10000/10000/11110/10001/10001/01110', 7: '11111/00001/00010/00100/01000/01000/01000',
  8: '01110/10001/10001/01110/10001/10001/01110', 9: '01110/10001/10001/01111/00001/00001/01110'
}
const center = value => GAP + (value + .5) * CELL
const sunk = ship => ship.hits?.length >= ship.w * ship.h

// Vue owns commands and authoritative state; Phaser owns textures, scenes and combat presentation.
export function createNavalScene(callbacks, initialState) {
  return class NavalScene extends Phaser.Scene {
    constructor() {
      super('naval-board')
      this.snapshot = initialState; this.ships = new Map(); this.pendingSinks = new Map()
      this.seenEvents = new Set(this.eventsFrom(initialState).filter(event => !event.replayOnMount).map(event => String(event.id)))
      this.eventQueue = []; this.animating = false
      this.motionPreference = typeof window !== 'undefined' ? window.matchMedia?.('(prefers-reduced-motion: reduce)') : null
      this.activeAnimations = new Set(); this.blockingAnimations = new Set()
      this.cellFocus = null; this.failures = []; this.entering = true; this.unrevealed = new Set()
    }
    preload() {
      callbacks.loading?.(0)
      this.load.on('progress', value => callbacks.loading?.(value))
      this.load.on('loaderror', file => this.failures.push(file.key))
      for (const key of SPRITES) this.load.image(key, `${ROOT}${key}.png`)
      if(this.snapshot.military){this.load.image('depthChargeHull','/naval/military/depth-charge-destroyer.png');this.load.image('depthChargeBomb','/naval/military/depth-charge.png');this.load.image('surfaceWake','/naval/military/surface-wake.png')}
      if(this.snapshot.military)this.load.spritesheet('repair-sparks','/naval/military/repair-sparks.png',{frameWidth:128,frameHeight:128})
      this.load.spritesheet('explosion', `${ROOT}explosion.png`, { frameWidth: 128, frameHeight: 128 })
      this.load.spritesheet('splash', `${ROOT}splash.png`, { frameWidth: 128, frameHeight: 128 })
    }
    create() {
      if (this.failures.length) { callbacks.error?.(`游戏素材加载失败：${this.failures.join('、')}`); return }
      for (const key of SPRITES) this.trimTexture(key)
      if(this.snapshot.military){this.trimTexture('depthChargeHull');this.trimTexture('depthChargeBomb');this.trimTexture('surfaceWake')}
      if(this.snapshot.military)this.anims.create({key:'repair-work',frames:this.anims.generateFrameNumbers('repair-sparks',{start:0,end:3}),frameRate:10,repeat:2,hideOnComplete:true})
      this.anims.create({ key: 'burst', frames: this.anims.generateFrameNumbers('explosion', { start: 0, end: 15 }), frameRate: 20, hideOnComplete: true })
      this.anims.create({ key: 'water-impact', frames: this.anims.generateFrameNumbers('splash', { start: 0, end: 15 }), frameRate: 20, hideOnComplete: true })
      this.anims.create({ key: 'smoke-drift', frames: this.anims.generateFrameNumbers('explosion', { start: 11, end: 15 }), frameRate: 7, hideOnComplete: true })
      this.sea = this.add.graphics().setDepth(0); this.defenses = this.add.graphics().setDepth(2)
      this.damage = this.add.graphics().setDepth(6); this.target = this.add.graphics().setDepth(7); this.radar = this.add.graphics().setDepth(7)
      this.decorations = this.add.container().setDepth(3); this.effects = this.add.container().setDepth(10)
      this.drawSea(); this.drawAxes(); this.renderState(true)
      this.input.on('pointerdown', pointer => { this.dragOrigin = { x: pointer.x, y: pointer.y, cell: this.pointAt(pointer.x, pointer.y) }; this.dragging = false })
      this.input.on('pointermove', pointer => {
        if (pointer.isDown && this.dragOrigin?.cell && !this.dragging && Math.hypot(pointer.x-this.dragOrigin.x,pointer.y-this.dragOrigin.y)>12) { this.dragging = true; callbacks.dragStart?.(this.dragOrigin.cell) }
        this.dragPointer = { x: this.inputX(pointer.x), y: pointer.y }
        this.focus(this.placementPoint(pointer))
      })
      this.input.on('pointerout', () => this.focus(null))
      this.input.on('pointerup', pointer => {
        const point = this.placementPoint(pointer), dragging = this.dragging
        this.dragging = false; this.dragOrigin = null
        if (point && this.snapshot.interactive) callbacks.cell?.({ ...point, dragging })
        this.focus(null); this.renderState(false)
      })
      this.cameras.main.fadeIn(350, 222, 239, 240)
      this.time.delayedCall(640, () => { this.entering = false })
      this.time.addEvent({ delay: 720, callback: () => this.damageSmoke(), loop: true })
      this.game.canvas.dataset.renderer = 'phaser-3.90'
      this.setAnimating(false)
      this.game.canvas.setAttribute('tabindex', '0'); this.game.canvas.setAttribute('role', 'grid')
      const motionChanged = () => { if (this.motionPreference?.matches) for (const record of this.ships.values()) this.stopFleetPlacement(record, true) }
      this.motionPreference?.addEventListener?.('change', motionChanged)
      callbacks.ready?.()
      this.updateSnapshot(this.snapshot)
      this.events.once(Phaser.Scenes.Events.SHUTDOWN, () => { this.motionPreference?.removeEventListener?.('change', motionChanged); this.tweens.killAll(); this.time.removeAllEvents(); this.eventQueue = []; this.pendingSinks.clear(); this.ships.clear(); this.setAnimating(false) })
    }
    trimTexture(key) {
      const texture = this.textures.get(key), image = texture.getSourceImage(), canvas = document.createElement('canvas')
      canvas.width = image.width; canvas.height = image.height
      const ctx = canvas.getContext('2d', { willReadFrequently: true }); ctx.drawImage(image, 0, 0)
      const pixels = ctx.getImageData(0, 0, image.width, image.height).data
      let minX = image.width, minY = image.height, maxX = -1, maxY = -1
      for (let y = 0; y < image.height; y++) for (let x = 0; x < image.width; x++) if (pixels[(y * image.width + x) * 4 + 3] > 40) { minX = Math.min(minX, x); minY = Math.min(minY, y); maxX = Math.max(maxX, x); maxY = Math.max(maxY, y) }
      if (maxX < minX || maxY < minY) { callbacks.error?.(`游戏素材为空：${key}`); return }
      texture.add('trimmed', 0, minX, minY, maxX - minX + 1, maxY - minY + 1)
      // Canvas tint is not supported by Phaser; derive the wreck palette from the
      // finished raster instead of substituting a newly drawn ship silhouette.
      if (Object.values(SHIPS).includes(key) || AIRCRAFT.includes(key)) {
        const data = ctx.getImageData(0, 0, image.width, image.height)
        for (let i = 0; i < data.data.length; i += 4) {
          const luminance = data.data[i] * .2126 + data.data[i + 1] * .7152 + data.data[i + 2] * .0722
          data.data[i] = luminance * .37; data.data[i + 1] = luminance * .46; data.data[i + 2] = luminance * .5
        }
        ctx.putImageData(data, 0, 0)
        this.textures.addCanvas(`${key}-wreck`, canvas).add('trimmed', 0, minX, minY, maxX - minX + 1, maxY - minY + 1)
      }
    }
    drawSea() {
      const g = this.sea.clear().fillStyle(0xe5eff1).fillRect(0, 0, BOARD_SIZE, BOARD_SIZE)
      g.fillStyle(this.snapshot.hidden ? 0x5b98ac : 0x579caf).fillRect(GAP, GAP, SEA, SEA)
      for (let y = 0; y < 12; y++) for (let x = 0; x < 12; x++) {
        g.fillStyle((x + y) % 2 ? 0x71adbd : 0x79b4c2, this.snapshot.hidden ? .47 : .68).fillRect(GAP + x * CELL + 1, GAP + y * CELL + 1, CELL - 2, CELL - 2)
        if ((x * 7 + y * 3) % 4 === 0) g.lineStyle(2, 0xc0e0e3, .32).beginPath().moveTo(GAP + x * CELL + 11, GAP + y * CELL + 29).lineTo(GAP + x * CELL + 19, GAP + y * CELL + 29).lineTo(GAP + x * CELL + 22, GAP + y * CELL + 27).strokePath()
      }
      g.lineStyle(1, 0xc0dbe0, .75).strokeRect(GAP, GAP, SEA, SEA)
    }
    drawAxes() {
      const g = this.add.graphics().setDepth(1).fillStyle(0x607d87)
      const label = (text, cx, cy) => {
        const left = Math.round(cx - (text.length * 12 - 2) / 2), top = Math.round(cy - 7)
        const mirrored = this.snapshot.defenseSide === 'left', width = text.length * 12 - 2
        for (const [index, char] of [...text].entries()) for (const [row, pixels] of GLYPHS[char].split('/').entries()) for (let col = 0; col < 5; col++) if (pixels[col] === '1') { const dx = index * 12 + col * 2; g.fillRect(left + (mirrored ? width - dx - 2 : dx), top + row * 2, 2, 2) }
      }
      for (let i = 0; i < 12; i++) {
        label(String.fromCharCode(65 + i), center(i), 12)
        label(String(i + 1), 12, center(i))
      }
    }
    eventsFrom(snapshot) { return Array.isArray(snapshot.events) ? snapshot.events : snapshot.event ? [snapshot.event] : [] }
    setAnimating(value) {
      this.animating = value
      if (this.game?.canvas) this.game.canvas.dataset.animating = String(value)
      callbacks.animation?.(value)
    }
    markPhase(phase) { this.game.canvas.dataset.effectPhase = phase }
    // The left canvas mirrors hulls, impacts and attack paths together.
    direction() { return 1 }
    inputX(x) { return this.snapshot.defenseSide === 'left' ? BOARD_SIZE - x : x }
    visualDirection() { return this.snapshot.defenseSide === 'left' ? 'right-to-left' : 'left-to-right' }
    updateSnapshot(snapshot) {
      if (!this.sys.isActive() || !this.sea) { this.snapshot = snapshot; return }
      const previousHidden = this.snapshot.hidden
      const footprint = state => JSON.stringify((state.player?.ships || []).map(ship => [ship.id,ship.x,ship.y,ship.w,ship.h]))
      if (snapshot.selected !== this.snapshot.selected || footprint(snapshot) !== footprint(this.snapshot)) this.cellFocus = null
      const knownShots=new Set((this.snapshot.player?.shots||[]).map(c=>`${c.x},${c.y}`))
      this.snapshot = snapshot
      for (const event of this.eventsFrom(snapshot)) {
        const id = String(event.id)
        if (this.seenEvents.has(id)) continue
        this.seenEvents.add(id)
        if (COMBAT.includes(event.weapon)) { this.eventQueue.push(event); if (this.playingEvent) this.setAnimating(true); for (const cell of event.cells || []) if(!knownShots.has(`${cell.x},${cell.y}`))this.unrevealed.add(`${cell.x},${cell.y}`) }
      }
      if (previousHidden !== snapshot.hidden) this.drawSea()
      this.renderState(false)
      this.playNextEvent()
    }
    playNextEvent() {
      // A hit can be followed immediately; effects overlap rather than building
      // a long queue that could outlive the server's presentation safety timeout.
      while (this.eventQueue.length) {
        const event = this.eventQueue.shift(), id = String(event.id)
        this.activeAnimations.add(id); this.blockingAnimations.add(id)
        this.playingEvent = true
        this.setAnimating(true)
        this.game.canvas.dataset.effectWeapon = event.weapon
        this.game.canvas.dataset.playedEventIds=JSON.stringify([...(JSON.parse(this.game.canvas.dataset.playedEventIds||'[]')),id].slice(-100))
        this.game.canvas.dataset.effectEvent = id
        this.game.canvas.dataset.effectRow = String(event.row ?? event.y ?? '')
        const duration = this.playAttack(event)
        this.time.delayedCall(Math.max(0, duration - 100), () => {
          this.blockingAnimations.delete(id)
          if (!this.blockingAnimations.size) this.setAnimating(false)
        })
        this.time.delayedCall(duration, () => {
          this.activeAnimations.delete(id)
          this.playingEvent = this.activeAnimations.size > 0
          if (!this.playingEvent) this.markPhase('idle')
        })
      }
    }
    fittedSprite(x, y, key, width, height, rotation = 0) {
      const sprite = this.add.sprite(x, y, key, 'trimmed'), frame = sprite.frame
      const swapped = Math.abs(Math.sin(rotation)) > .5
      return sprite.setScale(Math.min((swapped ? height : width) / frame.width, (swapped ? width : height) / frame.height)).setRotation(rotation)
    }
    militaryTexture(ship) {
      // Hits use board coordinates; towing can make the same coordinates describe a different part.
      const flying=this.flyingShip===ship.id?this.flyingPlane:undefined,key=`mil-${ship.id}-${ship.x},${ship.y}-${ship.rotation}-${ship.kind}-${ship.minesUsed||0}-${ship.hits.join(';')}-${(ship.lostPlanes||[]).join(',')}-${flying}`
      if(!this.textures.exists(key)){const canvas=document.createElement('canvas');canvas.width=ship.w*CELL;canvas.height=ship.h*CELL;drawMilitaryShip(canvas.getContext('2d'),ship,CELL,{time:this.time.now,aircraftInFlight:flying});this.textures.addCanvas(key,canvas).add('trimmed',0,0,0,canvas.width,canvas.height)}return key
    }
    militarySortie(event){
      const ship=this.snapshot.player?.ships.find(s=>s.id===event.shipId);if(!ship)return 0
      const part=militaryComponents(ship).find(p=>p.index===(event.plane||0)),x=center(part?.x??ship.x),y=center(part?.y??ship.y),plane=this.fittedSprite(x,y,event.aircraft,48,48,Math.PI/2);this.effects.add(plane);this.flyingShip=ship.id;this.flyingPlane=event.plane||0;this.renderState(false)
      playNavalSound('deploy');this.tweens.add({targets:plane,x:690,duration:600,onComplete:()=>{plane.setVisible(false);if(event.destroyed){plane.destroy();return}this.time.delayedCall(3000,()=>{plane.setPosition(690,y).setRotation(-Math.PI/2).setVisible(true);this.tweens.add({targets:plane,x,y,duration:600,onComplete:()=>plane.destroy()})})}})
      this.time.delayedCall(4200,()=>{this.flyingShip=null;this.flyingPlane=null;this.renderState(false)});return 4200
    }
    militaryAirAttack(event){
      const route=event.route||[],last=route.at(-1);if(!last)return 0
      const plane=this.fittedSprite(-55,center(event.y),event.weapon,88,88,Math.PI/2);this.effects.add(plane);this.game.canvas.dataset.incomingDirection=this.visualDirection();playNavalSound(event.weapon)
      const outbound=event.destroyed?route:route.slice(0,Math.ceil(route.length/2)),end=outbound.at(-1),flight=1850*(center(end.x)+55)/745
      const impact=c=>{this.reveal(c);if(c.result==='mine')this.mineImpact(c,event.weapon);else{this.effect(c.result==='hit'?'burst':'water-impact',center(c.x),center(c.y),c.result==='hit'?100:90);playNavalSound(c.result==='hit'?'explosion':'splash');if(c.result==='hit')this.cameras.main.shake(240,.0018)}}
      for(const c of event.cells||[]){const at=600+1850*(center(c.x)+55)/745; if(event.weapon==='torpedo'){const drop=600+1850*(center(event.x)+55)/745,travel=navalTorpedoDuration(center(event.x),center(c.x));if(c===event.cells.at(-1))this.time.delayedCall(drop,()=>this.torpedoWake(c.x,c.y,event.x,travel));this.time.delayedCall(drop+travel,()=>impact(c))}else this.time.delayedCall(at,()=>impact(c))}
      this.time.delayedCall(600,()=>this.tweens.add({targets:plane,x:center(end.x),duration:flight,ease:'Linear',onUpdate:()=>{this.game.canvas.dataset.aircraftX=String(this.snapshot.defenseSide==='left'?BOARD_SIZE-plane.x:plane.x)},onComplete:()=>{
        if(event.destroyed){playNavalSound('intercept');this.flakTracer(center(end.x)+25,center(end.y)+40,plane.x,plane.y,150);this.effect('burst',plane.x,plane.y,106);playNavalSound('explosion');plane.setTexture(`${event.weapon}-wreck`,'trimmed');this.tweens.add({targets:plane,y:plane.y+42,angle:plane.angle+65,alpha:0,duration:800,onComplete:()=>{this.effect('water-impact',plane.x,plane.y,108);playNavalSound('splash');plane.destroy()}})}
        else{plane.setRotation(-Math.PI/2);this.tweens.add({targets:plane,x:-55,duration:Math.max(300,3000-flight),onComplete:()=>plane.destroy()})}
      }}))
      for(const[id,pending]of this.pendingSinks)if(pending.eventId===String(event.id)){this.pendingSinks.delete(id);this.time.delayedCall(2100,()=>this.sinkShip(pending.record,pending.ship))}return this.pendingSinks.size?5000:4900
    }
    stopFleetPlacement(record, settle = false) {
      record.motionTween?.stop()
      if (record.motion) this.tweens.killTweensOf(record.motion)
      record.motion = null; record.motionTween = null
      record.sprite.setData('placementMotion', false)
      if (settle && record.targetPose) record.sprite.setPosition(record.targetPose.x, record.targetPose.y).setRotation(record.targetPose.rotation)
    }
    placeFleetSprite(record, ship, key, animate) {
      const sprite = record.sprite, oldRotation = sprite.rotation
      const from = { x:sprite.x, y:sprite.y, rotation:oldRotation }
      this.stopFleetPlacement(record)
      if (record.entryTween) { record.entryTween.stop(); record.entryTween = null; sprite.setAlpha(1) }
      sprite.setTexture(sunk(ship) && !this.snapshot.military ? `${key}-wreck` : key, 'trimmed')
      const rotation = !this.snapshot.military && ship.w > ship.h ? Math.PI / 2 : 0
      const swapped = Math.abs(Math.sin(rotation)) > .5, width = ship.w * CELL - 9, height = ship.h * CELL - 9
      sprite.setScale(Math.min((swapped ? height : width) / sprite.frame.width, (swapped ? width : height) / sprite.frame.height))
      const target = { x:GAP + (ship.x + ship.w / 2) * CELL, y:GAP + (ship.y + ship.h / 2) * CELL, rotation }
      if (animate && this.snapshot.military) {
        // Military textures already contain the new heading; undo that turn until the tween catches up.
        from.rotation -= shortestFleetTurn((record.ship.rotation || 0) * Math.PI / 180, (ship.rotation || 0) * Math.PI / 180)
      } else if (animate) target.rotation = from.rotation + shortestFleetTurn(from.rotation, rotation)
      record.targetPose = target
      if (!animate) { sprite.setPosition(target.x, target.y).setRotation(target.rotation); return }
      sprite.setPosition(from.x, from.y).setRotation(from.rotation).setData('placementMotion', true)
      const motion = fleetPlacementMotion(from, target), state = { progress:0 }
      record.motion = state
      record.motionTween = this.tweens.add({ targets:state, progress:1, duration:motion.duration, ease:'Linear', onUpdate:() => {
        if (record.motion !== state || !sprite.active) return
        const pose = sampleFleetPlacement(motion, state.progress)
        sprite.setPosition(pose.x, pose.y).setRotation(pose.rotation)
      }, onComplete:() => { if (record.motion === state && sprite.active) this.stopFleetPlacement(record, true) } })
    }
    renderState(initial) {
      this.decorations.removeAll(true); this.drawDefenses()
      const visible = (this.snapshot.player?.ships || []).map(ship => ({ ...ship, hits: (ship.hits || []).filter(cell => !this.unrevealed.has(cell)) })).filter(ship => ship.x>=0 && (!this.snapshot.hidden || sunk(ship))), ids = new Set(visible.map(s => s.id))
      for (const [id, record] of this.ships) if (!ids.has(id)) { this.stopFleetPlacement(record); this.tweens.killTweensOf(record.sprite); record.sprite.destroy(); this.ships.delete(id) }
      for (const ship of visible) {
        const key = this.snapshot.military ? this.militaryTexture(ship) : SHIPS[Math.max(ship.w, ship.h)]; if (!key) continue
        let record = this.ships.get(ship.id)
        const signature = `${ship.x},${ship.y},${ship.w},${ship.h}${this.snapshot.military?key:''}`
        if (record && record.signature !== signature) {
          const animate = canAnimateFleetPlacement(record.ship, ship, {
            initial, entering:this.entering, hidden:this.snapshot.hidden, reducedMotion:this.motionPreference?.matches,
            placing:this.snapshot.militaryPreparing, wasPlacing:record.placing, selected:this.snapshot.selected
          })
          this.placeFleetSprite(record, ship, key, animate)
          record.signature = signature
        } else if (record?.motion && (!this.snapshot.militaryPreparing || this.snapshot.hidden)) this.stopFleetPlacement(record, true)
        if (!record) {
          const sprite = this.fittedSprite(GAP + (ship.x + ship.w / 2) * CELL, GAP + (ship.y + ship.h / 2) * CELL, key, ship.w * CELL - 9, ship.h * CELL - 9, !this.snapshot.military && ship.w > ship.h ? Math.PI / 2 : 0).setDepth(4)
          sprite.setData('shipId',ship.id)
          record = { sprite, signature, sunk: sunk(ship), ship, targetPose:{x:sprite.x,y:sprite.y,rotation:sprite.rotation} }; this.ships.set(ship.id, record)
          if (initial && !sunk(ship) && !this.snapshot.hidden) { const y = sprite.y; sprite.y -= 20; sprite.alpha = 0; record.entryTween = this.tweens.add({ targets: sprite, y, alpha: 1, duration: 500, delay: visible.indexOf(ship) * 22, ease: 'Cubic.Out', onComplete:() => { record.entryTween = null } }) }
          if (sunk(ship)) { sprite.setTexture(this.snapshot.military?key:`${key}-wreck`, 'trimmed').setAlpha(.58).setDepth(6.3); if (!initial) this.deferSink(record, ship) }
        } else if (sunk(ship) && !record.sunk) { record.sunk = true; this.deferSink(record, ship) }
        record.ship = ship; record.placing = Boolean(this.snapshot.militaryPreparing)
      }
      this.drawDamage(); this.drawRadar(); this.drawTarget()
      this.game.canvas.dataset.shipCount = String(this.ships.size)
      this.game.canvas.dataset.unrevealedCells = String(this.unrevealed.size)
      this.game.canvas.dataset.liveShipSprites = String(this.children.list.filter(child => child.active && child.getData?.('shipId')).length)
      this.game.canvas.dataset.shipFootprints = JSON.stringify([...this.ships].map(([id,record]) => ({ id, x: record.ship.x, y: record.ship.y, w: record.ship.w, h: record.ship.h })))
    }
    deferSink(record, ship) {
      const event = [...this.eventQueue].reverse().find(attack => attack.cells?.some(point => point.result === 'hit' && point.x >= ship.x && point.x < ship.x + ship.w && point.y >= ship.y && point.y < ship.y + ship.h))
      if (event) this.pendingSinks.set(ship.id, { record, ship, eventId: String(event.id) })
      else this.sinkShip(record, ship)
    }
    drawDefenses() {
      const g = this.defenses.clear()
      this.game.canvas.dataset.defenseSide = this.snapshot.defenseSide === 'right' ? 'right' : 'left'
      if (this.snapshot.hidden) return
      if(this.snapshot.military&&this.snapshot.militaryPreparing){for(const c of militaryCoverage(this.snapshot.player))g.fillStyle(0xf1d58a,.25).fillRect(GAP+c.x*CELL,GAP+c.y*CELL,CELL-1,CELL-1);for(const c of militaryJamming(this.snapshot.player))g.fillStyle(0xb1edbe,.20).fillRect(GAP+c.x*CELL,GAP+c.y*CELL,CELL-1,CELL-1)}
      for (const row of this.snapshot.player?.aa || []) {
        g.fillStyle(0xd3e8b0, .18).fillRect(GAP, GAP + row * CELL + 1, SEA, CELL - 2)
        this.decorations.add(this.fittedSprite(this.direction() < 0 ? GAP + 12 : GAP + SEA - 12, center(row), 'antiAir', 20, 20))
      }
      for (const mine of this.snapshot.player?.mines || []) this.decorations.add(this.fittedSprite(center(mine.x), center(mine.y), 'mine', 28, 28))
      for(const mine of this.snapshot.player?.scrapMines||[]){
        const sprite=this.fittedSprite(center(mine.x),center(mine.y),'scrap-mine',28,28);this.decorations.add(sprite)
        g.lineStyle(2,0x82dbb3,.9).strokeRect(GAP+mine.x*CELL+5,GAP+mine.y*CELL+5,CELL-10,CELL-10)
      }
      for (const ship of this.snapshot.player?.ships || []) if (ship.stealth && !sunk(ship)) {
        g.lineStyle(2, 0xb5f9d3, .8).strokeRoundedRect(GAP + ship.x * CELL + 5, GAP + ship.y * CELL + 5, ship.w * CELL - 10, ship.h * CELL - 10, 6)
        this.decorations.add(this.fittedSprite(GAP + (ship.x + ship.w) * CELL - 9, GAP + ship.y * CELL + 10, 'stealth', 18, 18).setDepth(7))
      }
    }
    drawDamage() {
      const g = this.damage.clear()
      this.game.canvas.dataset.selectedShip=this.snapshot.selected||''
      this.game.canvas.dataset.visibleShots = String((this.snapshot.player?.shots || []).filter(cell=>!this.unrevealed.has(`${cell.x},${cell.y}`)).length)
      this.game.canvas.dataset.uncertainShots=JSON.stringify((this.snapshot.player?.shots||[]).filter(s=>s.uncertain&&!this.unrevealed.has(`${s.x},${s.y}`)).map(({x,y})=>({x,y})))
      for (const shot of this.snapshot.player?.shots || []) {
        if (this.unrevealed.has(`${shot.x},${shot.y}`)) continue
        const x = GAP + shot.x * CELL, y = GAP + shot.y * CELL
        if(!this.snapshot.hidden&&(this.snapshot.player?.scrapMines||[]).some(m=>m.x===shot.x&&m.y===shot.y))continue
        if (shot.uncertain) {
          g.fillStyle(0xacc5c9,.35).fillRect(x+1,y+1,CELL-2,CELL-2)
          const glyph='01110/10001/00010/00100/00100/00000/00100'.split('/')
          g.fillStyle(0x365b69,.95)
          for(let row=0;row<7;row++)for(let col=0;col<5;col++)if(glyph[row][col]==='1')g.fillRect(center(shot.x)-7.5+(this.snapshot.defenseSide==='left'?4-col:col)*3,center(shot.y)-10.5+row*3,3,3)
        } else if (shot.result === 'hit') {
          g.fillStyle(0x767f83, .94).fillRect(x + 1, y + 1, CELL - 2, CELL - 2)
          g.lineStyle(2, 0x404e54, .9).lineBetween(x + 17, y + 17, x + 31, y + 31).lineBetween(x + 31, y + 17, x + 17, y + 31)
          g.lineStyle(1, 0xbec4c3, .85).strokeRect(x + 3, y + 3, CELL - 6, CELL - 6)
        } else if (shot.result === 'mine') {
          g.fillStyle(0x849598, .76).fillRect(x + 1, y + 1, CELL - 2, CELL - 2)
          g.lineStyle(2, 0x4b6068, .85).strokeCircle(center(shot.x), center(shot.y), 8)
        } else { g.fillStyle(0xb4c9ce, .36).fillRect(x + 1, y + 1, CELL - 2, CELL - 2); g.fillStyle(0xd7ecee, .9).fillCircle(center(shot.x), center(shot.y), 3) }
      }
      for (const { ship } of this.ships.values()) {
        if (this.snapshot.hidden && !sunk(ship)) continue
        if (ship.id === this.snapshot.selected && !this.snapshot.hidden) g.lineStyle(3, 0xffd286).strokeRect(GAP + ship.x * CELL + 2, GAP + ship.y * CELL + 2, ship.w * CELL - 4, ship.h * CELL - 4)
        if(!this.snapshot.hidden)for(const hit of ship.hits||[]){
          const [hx,hy]=hit.split(',').map(Number)
          if(!(this.snapshot.player.shots||[]).some(p=>p.x===hx&&p.y===hy)){
            g.fillStyle(0x70787c,.65).fillRect(GAP+hx*CELL+2,GAP+hy*CELL+2,CELL-4,CELL-4)
            g.lineStyle(2,0xd4d9d5,.8).strokeCircle(center(hx),center(hy),6)
          }
        }
        if (sunk(ship)) g.lineStyle(2, 0x354950, .7).strokeRect(GAP + ship.x * CELL + 4, GAP + ship.y * CELL + 4, ship.w * CELL - 8, ship.h * CELL - 8)
      }
    }
    drawRadar() { this.radar.clear() }
    pointAt(x, y) { const col = Math.floor((this.inputX(x) - GAP) / CELL), row = Math.floor((y - GAP) / CELL); return col >= 0 && col < 12 && row >= 0 && row < 12 ? { x: col, y: row } : null }
    placementPoint(pointer) {
      const point = this.pointAt(pointer.x, pointer.y), offset = this.dragging && this.snapshot.preview?.offset
      if(!point)return null
      const preview=this.snapshot.preview
      return preview?{x:Phaser.Math.Clamp(point.x-(offset?.x||0),0,12-preview.w),y:Phaser.Math.Clamp(point.y-(offset?.y||0),0,12-preview.h)}:point
    }
    focus(point) { this.cellFocus = point; callbacks.hover?.(point); this.drawTarget() }
    chooseFocus() { if (this.snapshot.interactive) callbacks.cell?.(this.cellFocus || { x: 0, y: 0 }) }
    moveFocus(dx, dy) { const p = this.cellFocus || { x: 0, y: 0 }; this.focus({ x: Phaser.Math.Clamp(p.x + dx, 0, 11), y: Phaser.Math.Clamp(p.y + dy, 0, 11) }) }
    drawTarget() {
      this.ghost?.destroy(); this.ghost = null
      this.game.canvas.dataset.previewSprite = ''
      for (const { sprite, ship } of this.ships.values()) {
        if (this.dragging && ship.id === this.snapshot.preview?.sourceShipId) { sprite.setAlpha(.25); sprite.setData('dragDim', true) }
        else if (sprite.getData('dragDim')) { sprite.setAlpha(sunk(ship) ? .58 : 1); sprite.setData('dragDim', false) }
      }
      for(const label of this.salvoLabels||[])label.destroy();this.salvoLabels=[]
      const g = this.target?.clear(); if (!g || !this.snapshot.interactive) return
      const source = this.snapshot.preview?.source
      if (source) g.lineStyle(3, 0xd0ffe2).strokeRect(GAP+source.x*CELL+2,GAP+source.y*CELL+2,source.w*CELL-4,source.h*CELL-4)
      for (const cell of this.snapshot.region?.selected || []) {
        g.fillStyle(0xb9f7ce,.4).fillRect(GAP + cell.x * CELL + 2,GAP + cell.y * CELL + 2,CELL - 4,CELL - 4)
        g.lineStyle(3,0xd2ffe2).strokeRect(GAP + cell.x * CELL + 2,GAP + cell.y * CELL + 2,CELL - 4,CELL - 4)
      }
      const focus = this.snapshot.region?.pending || this.cellFocus
      if (!focus || this.snapshot.region?.selected) return
      const { x, y } = focus, row = this.snapshot.region?.row, ray = this.snapshot.region?.ray
      const px = row ? 0 : x, w = row ? 12 : ray ? 12-x : this.snapshot.preview?.w || this.snapshot.region?.w || 1, h = this.snapshot.preview?.h || this.snapshot.region?.h || 1
      const width = Math.min(w, 12 - px) * CELL, height = Math.min(h, 12 - y) * CELL
      const militaryShip=this.snapshot.preview?.militaryShip
      const invalid = x < 0 || y < 0 || !row && (x + w > 12 || y + h > 12) || militaryShip && (!militaryPlacement(this.snapshot.player.ships,{...militaryShip,x,y}) || (this.snapshot.player.mines||[]).some(m=>m.x>=x&&m.x<x+w&&m.y>=y&&m.y<y+h))
      g.fillStyle(invalid ? 0xee7759 : 0xffd18d, .27).fillRect(GAP + px * CELL, GAP + y * CELL, width, height)
      g.lineStyle(3, invalid ? 0xff957c : 0xffe3a6).strokeRect(GAP + px * CELL + 2, GAP + y * CELL + 2, width - 4, height - 4)

      const asset = militaryShip ? this.militaryTexture({...militaryShip,x,y,hits:[]}) : this.snapshot.preview?.sprite || this.snapshot.region?.sprite
      if (asset) {
        const assetX = row ? 0 : px, assetW = row ? 1 : w
        this.ghost = this.fittedSprite(GAP+(assetX+assetW/2)*CELL,GAP+(y+h/2)*CELL,asset,assetW*CELL-8,h*CELL-8,!militaryShip&&assetW>h?Math.PI/2:0).setAlpha(.65).setDepth(8)
        if (this.dragging && this.dragPointer) {
          const offset = this.snapshot.preview?.offset || { x: 0, y: 0 }
          this.ghost.setPosition(row ? center(0) : this.dragPointer.x+(assetW/2-.5-offset.x)*CELL,this.dragPointer.y+(h/2-.5-offset.y)*CELL)
        }
        this.game.canvas.dataset.previewSprite = militaryShip?.kind || asset
        this.game.canvas.dataset.previewInvalid = String(Boolean(invalid))
        this.game.canvas.dataset.previewCell = `${assetX},${y}`
      }
    }
    reveal(cell) { this.unrevealed.delete(`${cell.x},${cell.y}`); this.renderState(false) }
    effect(kind, x, y, size = 86, tint) {
      const sprite = this.add.sprite(x, y, kind === 'water-impact' ? 'splash' : 'explosion').setDisplaySize(size, size)
      if (tint) sprite.setTint(tint)
      this.effects.add(sprite); sprite.play(kind)
      sprite.once(Phaser.Animations.Events.ANIMATION_COMPLETE, () => sprite.destroy())
      return sprite
    }
    damageSmoke() {
      if (this.entering) return
      for (const { ship, sunk: destroyed } of this.ships.values()) {
        if (!ship.hits?.length || (destroyed && Math.random() > .3)) continue
        const [x, y] = ship.hits[Math.floor(Math.random() * ship.hits.length)].split(',').map(Number)
        const smoke = this.add.particles(center(x), center(y) - 2, 'explosion', {
          frame: [11, 12, 13], lifespan: 1100, emitting: false,
          speedX: { min: -5, max: 5 }, speedY: { min: -27, max: -17 },
          alpha: { start: .42, end: 0 }, scale: { start: destroyed ? .22 : .14, end: destroyed ? .38 : .26 },
          rotate: { min: -8, max: 8 }, quantity: 2
        }).setDepth(9)
        smoke.explode(2)
        this.time.delayedCall(1150, () => smoke.destroy())
      }
    }
    sinkShip(record, ship) {
      const sprite = record.sprite
      if (!sprite.active) return
      this.tweens.killTweensOf(sprite); playNavalSound('sink'); this.cameras.main.shake(350, .0018)
      let i = 0
      for (let y = ship.y; y < ship.y + ship.h; y++) for (let x = ship.x; x < ship.x + ship.w; x++) this.time.delayedCall(i++ * 180, () => this.effect('burst', center(x), center(y), 106))
      const originalY = sprite.y, sx = sprite.scaleX, sy = sprite.scaleY
      if(!this.snapshot.military) sprite.setTexture(`${SHIPS[Math.max(ship.w, ship.h)]}-wreck`, 'trimmed').setDepth(6.3)
      this.tweens.add({ targets: sprite, angle: sprite.angle + 4, duration: 250, yoyo: true, repeat: 2, ease: 'Sine.InOut' })
      this.time.delayedCall(650, () => { if (sprite.active) this.tweens.add({ targets: sprite, y: originalY + 12, scaleX: sx * .82, scaleY: sy * .82, alpha: .28, duration: 2100, ease: 'Sine.In', onComplete: () => { if (sprite.active) sprite.setAlpha(.48) } }) })
    }
    playAttack(event) {
      const cells = event.cells || [], weapon = event.weapon
      if(this.snapshot.military&&weapon==='military-sortie')return this.militarySortie(event)
      if(this.snapshot.military&&AIRCRAFT.includes(weapon))return this.militaryAirAttack(event)
      if(weapon==='repairSupport'){
        const support=event.support||{},pose=support.after||support.before
        playNavalSound(support.mode==='tow'?'tow':'repair')
        const points=support.cells?.length?support.cells:pose?[{x:pose.x+pose.w/2-.5,y:pose.y+pose.h/2-.5}]:[]
        for(const point of points){
          if(this.anims.exists('repair-work')){const sparks=this.add.sprite(center(point.x),center(point.y),'repair-sparks').setDisplaySize(50,50).setDepth(16);this.effects.add(sparks);sparks.play('repair-work');sparks.once('animationcomplete',()=>sparks.destroy())}
          const halo=this.add.circle(center(point.x),center(point.y),10,0x8be4b2,.5).setDepth(15);this.effects.add(halo)
          this.tweens.add({targets:halo,radius:35,alpha:0,duration:1000,onComplete:()=>halo.destroy()})
        }
        this.markPhase('repair-support');return 1800
      }
      if(weapon==='mine'){playNavalSound('deploy');for(const c of event.targets?.length?event.targets:[{x:event.x,y:event.y}])this.effect('water-impact',center(c.x),center(c.y),45);return 1000}
      if (!COMBAT.includes(weapon)) return 0
      if (weapon === 'mine-rebound') {
        let duration = 0
        for (const cell of cells) {
          const delay = cell.delay || 650; duration = Math.max(duration, delay + 1200)
          this.time.delayedCall(delay, () => { this.reveal(cell); this.markPhase('mine-rebound'); this.effect('burst',center(cell.x),center(cell.y),54); this.effect('water-impact',center(cell.x),center(cell.y),57); playNavalSound('mine') })
        }
        for (const [id,pending] of this.pendingSinks) if (pending.eventId === String(event.id)) { this.pendingSinks.delete(id); this.time.delayedCall(cells.find(cell=>cell.x>=pending.ship.x&&cell.x<pending.ship.x+pending.ship.w&&cell.y>=pending.ship.y&&cell.y<pending.ship.y+pending.ship.h)?.delay || 650,()=>this.sinkShip(pending.record,pending.ship)); duration=Math.max(duration,3500) }
        return duration
      }
      const mx = cells.length ? cells.reduce((s, p) => s + p.x, 0) / cells.length : event.x ?? 5.5
      const my = event.row ?? (cells.length ? cells.reduce((s, p) => s + p.y, 0) / cells.length : event.y ?? 5.5)
      const intercepted = !cells.length && /intercept|拦截|击落/i.test(event.text || '')
      const direction = this.direction()
      const dropAt = event.x == null ? 300 : 1850 * (direction > 0 ? center(event.x)+55 : 690-center(event.x))/745
      const stop = [...cells].reverse().find(point => point.result === 'hit' || point.result === 'mine')
      const torpedoEnd = stop?.x ?? Math.max(event.x ?? 0, ...cells.map(cell => cell.x))
      const torpedoTravel = navalTorpedoDuration(event.x == null ? GAP-25 : center(event.x),center(torpedoEnd))
      playNavalSound(weapon)
      this.markPhase('approach')
      if(weapon==='depthCharge')return this.depthChargeSail(event)
      if (weapon === 'submarine') return this.submarineSail(event)
      if (weapon === 'radar') {
        const recent = (this.snapshot.radar || []).slice(-9)
        this.radarSweep(event.x ?? (recent.length ? Math.min(...recent.map(p => p.x)) : 4), event.y ?? (recent.length ? Math.min(...recent.map(p => p.y)) : 4))
        return 1450
      }
      if (AIRCRAFT.includes(weapon)) {
        if (intercepted) { this.airDefense(event); return 2550 }
        const direction = this.direction(), plane = this.fittedSprite(direction > 0 ? -55 : 690, center(my), weapon, 88, 88, direction * Math.PI / 2); this.effects.add(plane)
        this.game.canvas.dataset.incomingDirection = this.visualDirection()
        this.tweens.add({ targets: plane, x: direction > 0 ? 690 : -55, duration: NAVAL_AIRCRAFT_DURATION, ease: 'Linear', onUpdate: () => { this.game.canvas.dataset.aircraftX=String(this.snapshot.defenseSide==='left' ? BOARD_SIZE-plane.x : plane.x) }, onComplete: () => plane.destroy() })
        if (weapon === 'torpedo') {
          this.time.delayedCall(dropAt, () => this.torpedoWake(torpedoEnd, my, event.x, torpedoTravel))
        }
      }
      const damagedIndex = cells.findIndex(point => point.result === 'hit' || point.result === 'mine')
      const impactIndex = damagedIndex >= 0 ? damagedIndex : 0
      const delayAt = index => {
        if (weapon === 'shot') return 350
        const x = cells[index].x, direction = this.direction()
        if (weapon === 'torpedo') {
          const start = event.x == null ? direction > 0 ? GAP-25 : GAP+SEA+25 : center(event.x)
          const end = event.x == null && direction < 0 ? Math.min(...cells.map(cell => cell.x)) : Math.max(...cells.map(cell => cell.x))
          return dropAt + (event.x === end ? torpedoTravel : torpedoTravel * Math.abs(center(x)-start)/Math.max(1,Math.abs(center(end)-start)))
        }
        return 1850 * (direction > 0 ? center(x)+55 : 690-center(x))/745
      }
      let duration = AIRCRAFT.includes(weapon) ? 1900 : 900
      cells.forEach((point, index) => {
        const delay = delayAt(index)
        duration = Math.max(duration, delay + 1200)
        if (weapon === 'shot') this.projectile(point.x, point.y, delay)
        if (point.result === 'mine') duration = Math.max(duration, delay + 1300)
        this.time.delayedCall(delay, () => {
          this.reveal(point)
          if (point.result === 'mine') { this.mineImpact(point,event.weapon); return }
          const damaged = point.result === 'hit'
          this.markPhase('impact')
          this.effect(damaged ? 'burst' : 'water-impact', center(point.x), center(point.y), damaged ? 100 : 90)
          if (index === impactIndex) playNavalSound(damaged ? 'explosion' : 'splash')
          if (damaged && index === impactIndex) this.cameras.main.shake(240, .0018)
        })
      })
      for (const [id, pending] of this.pendingSinks) {
        if (pending.eventId !== String(event.id)) continue
        this.pendingSinks.delete(id)
        const index = Math.max(0, cells.findIndex(point => point.x >= pending.ship.x && point.x < pending.ship.x + pending.ship.w && point.y >= pending.ship.y && point.y < pending.ship.y + pending.ship.h))
        const delay = delayAt(index)
        this.time.delayedCall(delay, () => this.sinkShip(pending.record, pending.ship))
        duration = Math.max(duration, delay + 2800)
      }
      return duration
    }
    depthChargeSail(event) {
      const route=event.route||[],paths=event.torpedoes||[]
      if(!route.length)return 0
      const last=route.at(-1),cx=GAP+(last.x+1)*CELL,cy=center(last.y),canvas=this.game.canvas
      const missing=!event.destroyed&&paths.length===1&&last.y>0&&last.y<11?(paths[0].at(-1).y<last.y?1:0):null
      const texture=missing==null?'depthChargeHull':this.militaryTexture({id:`depth-voyage-${missing}`,kind:'depthCharge',x:0,y:0,w:1,h:2,rotation:0,hits:[`0,${missing}`],lostPlanes:[]})
      const boat=this.fittedSprite(-90,cy,texture,CELL*1.85,CELL*.85,Math.PI/2).setDepth(11)
      this.effects.add(boat);boat.setData('effect','depth-charge-vessel')
      const wake=this.fittedSprite(boat.x,cy,'surfaceWake',96,64).setOrigin(1,.5).setDisplaySize(96,64).setDepth(10).setAlpha(.65)
      // Container children render in list order, not by their individual depth.
      this.effects.addAt(wake,this.effects.getIndex(boat))
      const wakeScaleX=wake.scaleX,wakeScaleY=wake.scaleY,syncWake=returning=>{wake.setPosition(boat.x+(returning?-1:1)*CELL*.84,cy).setRotation(returning?Math.PI:0).setScale(wakeScaleX,wakeScaleY*(.97+.03*Math.sin(this.time.now/100)));canvas.dataset.depthChargeWake=returning?'return':'outbound';canvas.dataset.depthChargeWakeLayer=this.effects.getIndex(wake)<this.effects.getIndex(boat)?'below-hull':'above-hull';canvas.dataset.depthChargeWakeOrigin='bow'}
      canvas.dataset.depthChargePhase='sailing';canvas.dataset.depthChargeLaunches='0'
      const settleSinks=()=>{for(const[id,pending]of this.pendingSinks)if(pending.eventId===String(event.id)){this.pendingSinks.delete(id);this.sinkShip(pending.record,pending.ship)}}
      const impact=cell=>{this.reveal(cell);if(cell.result==='mine')this.mineImpact(cell,event.weapon);else{this.effect(cell.result==='hit'?'burst':'water-impact',center(cell.x),center(cell.y),cell.result==='hit'?84:52);playNavalSound(cell.result==='hit'?'explosion':'splash')}}
      this.tweens.add({targets:boat,x:cx,duration:1250,ease:'Sine.InOut',onUpdate:()=>{canvas.dataset.depthChargeX=String(this.snapshot.defenseSide==='left'?BOARD_SIZE-boat.x:boat.x);syncWake(false)},onComplete:()=>{
        wake.setVisible(false);canvas.dataset.depthChargePhase=event.destroyed?'collision':'arrived';this.markPhase(event.destroyed?'depth-charge-collision':'depth-charge-arrived')
        if(event.destroyed){wake.destroy();for(const cell of event.cells||[])impact(cell);settleSinks();this.tweens.add({targets:boat,alpha:0,y:cy+20,angle:boat.angle+8,duration:950,onComplete:()=>boat.destroy()});return}
        this.time.delayedCall(140,()=>{
          canvas.dataset.depthChargePhase='throwing';canvas.dataset.depthChargeLaunches=String(paths.length)
          for(const path of paths){if(path.length<2)continue;const start=path[0],end=path.at(-1),up=end.y<start.y,sx=cx+(up?CELL*.45:-CELL*.45),sy=cy+(up?-8:8),bomb=this.fittedSprite(sx,sy,'depthChargeBomb',18,21).setDepth(13),progress={t:0};this.effects.add(bomb);playNavalSound('deploy')
            this.tweens.add({targets:progress,t:1,duration:650,ease:'Sine.InOut',onUpdate:()=>{const t=progress.t;bomb.setPosition(sx+(center(end.x)-sx)*t,sy+(center(end.y)-sy)*t-Math.sin(Math.PI*t)*14);bomb.setAngle(t*260)},onComplete:()=>{bomb.destroy();const cell=(event.cells||[]).find(c=>c.x===end.x&&c.y===end.y);if(cell)impact(cell)}})
          }
          this.time.delayedCall(700,settleSinks)
        })
        this.time.delayedCall(1100,()=>{canvas.dataset.depthChargePhase='returning';boat.setRotation(Math.PI*1.5);wake.setVisible(true);syncWake(true);this.tweens.add({targets:boat,x:-90,duration:950,ease:'Sine.In',onUpdate:()=>{syncWake(true);canvas.dataset.depthChargeX=String(this.snapshot.defenseSide==='left'?BOARD_SIZE-boat.x:boat.x)},onComplete:()=>{wake.destroy();boat.destroy();canvas.dataset.depthChargePhase='complete'}})})
      }})
      return this.pendingSinks.size?5200:3500
    }
    submarineSail(event) {
      const targets = event.targets || [], route = event.route || []
      if (targets.length !== (event.weapon==='depthCharge'?2:3) || !route.length) return 0
      const sinking = [...this.pendingSinks.values()].some(pending => pending.eventId === String(event.id))
      const w = Math.max(...targets.map(cell => cell.x)) - targets[0].x + 1
      const h = Math.max(...targets.map(cell => cell.y)) - targets[0].y + 1
      const last = route.at(-1), cx = GAP + (last.x + w / 2) * CELL, cy = GAP + (last.y + h / 2) * CELL
      const sub = this.fittedSprite(-75,cy,event.weapon==='depthCharge'?'depthChargeHull':'submarine',w * CELL - 8,h * CELL - 8,w > h ? Math.PI / 2 : 0).setAlpha(event.weapon==='depthCharge'?1:.35).setTint(event.weapon==='depthCharge'?0xffffff:0x408b9b)
      this.effects.add(sub); sub.setData('effect','submarine-hull'); this.game.canvas.dataset.submarineDestroyed = String(Boolean(event.destroyed))
      this.game.canvas.dataset.submarineDirection = this.visualDirection()
      const wake = this.add.graphics().setDepth(9)
      this.tweens.add({ targets: sub, x: cx, duration: 1250, ease: 'Sine.InOut', onUpdate: () => {
        this.game.canvas.dataset.submarineX = String(this.snapshot.defenseSide === 'left' ? BOARD_SIZE-sub.x : sub.x)
        wake.clear().lineStyle(2,0x244f70,.25).strokeEllipse(sub.x,cy,w*CELL*.85,h*CELL*.65)
      }, onComplete: () => {
        wake.destroy(); this.markPhase(event.destroyed ? 'submarine-mine' : 'submarine-launch')
        if (event.destroyed) {
          sub.clearTint(); this.tweens.add({ targets: sub, alpha: 1, y: cy-6, duration: 260 })
          for (const cell of event.cells || []) { this.reveal(cell); if (cell.result === 'mine') this.mineImpact(cell,event.weapon); else this.effect('burst',center(cell.x),center(cell.y),48) }
          for (const [id,pending] of this.pendingSinks) if (pending.eventId === String(event.id)) { this.pendingSinks.delete(id); this.sinkShip(pending.record,pending.ship) }
          this.tweens.add({ targets: sub, alpha: 0, duration: 600, delay: 250, onComplete: () => sub.destroy() })
          return
        }
        playNavalSound('torpedo')
        for (const path of event.torpedoes || []) {
          if (path.length < 2) continue
          const start = path[0], end = path.at(-1), direction = end.y < start.y ? -1 : 1
          const projectile = this.fittedSprite(center(start.x),center(start.y),'torpedo-projectile',14,43,direction * Math.PI / 2).setDepth(11)
          this.effects.add(projectile)
          this.tweens.add({ targets: projectile, y: center(end.y), duration: 850, ease: 'Linear', onComplete: () => projectile.destroy() })
        }
        this.tweens.add({ targets: sub, alpha: 0, duration: 800, delay: 650, onComplete: () => sub.destroy() })
        for (const cell of event.cells || []) {
          const path = (event.torpedoes || []).find(path=>path.slice(1).some(p=>p.x===cell.x&&p.y===cell.y)) || []
          const index = path.findIndex(p=>p.x===cell.x&&p.y===cell.y)
          this.time.delayedCall(850 * Math.max(0,index)/Math.max(1,path.length-1), () => {
            this.reveal(cell)
            if (cell.result === 'mine') this.mineImpact(cell,event.weapon)
            else this.effect(cell.result === 'hit' ? 'burst' : 'water-impact',center(cell.x),center(cell.y),cell.result === 'hit' ? 92 : 48)
          })
        }
        this.time.delayedCall(850, () => {
          if (event.cells?.some(cell => cell.result === 'hit')) playNavalSound('explosion')
          for (const [id,pending] of this.pendingSinks) if (pending.eventId === String(event.id)) { this.pendingSinks.delete(id); this.sinkShip(pending.record,pending.ship) }
        })
      } })
      return sinking ? 5000 : event.destroyed ? 2650 : 3500
    }
    airDefense(event) {
      const row = Phaser.Math.Clamp(event.row ?? event.y ?? 5, 0, 11), y = center(row), planeY = center(event.y ?? row)
      const interceptX = center(Phaser.Math.Clamp((event.x ?? 2) + (event.weapon === 'fighter' ? 3 : 1), 2, 9))
      const direction = this.direction(), gunX = direction < 0 ? GAP + 20 : GAP + SEA - 20, offscreen = direction < 0 ? -35 : 659
      this.game.canvas.dataset.effectRow = String(row)
      this.game.canvas.dataset.effectGunX = String(this.snapshot.defenseSide === 'left' ? BOARD_SIZE-gunX : gunX)
      this.game.canvas.dataset.incomingDirection = this.visualDirection()
      const plane = this.fittedSprite(direction > 0 ? -70 : 694, planeY, event.weapon, 96, 96, direction * Math.PI / 2)
      const gun = this.fittedSprite(offscreen, y, 'antiAir', 58, 58), scale = gun.scaleX
      plane.setData('effect', 'intercept-aircraft'); gun.setData('effect', 'anti-air-popup')
      this.effects.add([plane, gun]); gun.setAlpha(0).setScale(scale * .3)
      const band = this.add.graphics().fillStyle(0xffd18d, .22).fillRect(GAP, GAP + row * CELL + 1, SEA, CELL - 2)
      this.effects.add(band); this.markPhase('aa-popup')
      this.tweens.add({ targets: gun, x: gunX, alpha: 1, scaleX: scale, scaleY: scale, duration: 250, delay: 80, ease: 'Back.Out' })
      this.time.delayedCall(360, () => this.markPhase('aa-aim'))
      this.tweens.add({ targets: plane, x: interceptX, duration: 1280, ease: 'Linear', onUpdate: () => { gun.rotation = Phaser.Math.Angle.Between(gun.x, gun.y, plane.x, plane.y) + Math.PI / 2 }, onComplete: () => {
        this.markPhase('plane-crash'); playNavalSound('explosion')
        this.effect('burst', plane.x, plane.y, 106); this.cameras.main.shake(200, .0015)
        plane.setTexture(`${event.weapon}-wreck`, 'trimmed')
        this.tweens.add({ targets: plane, x: plane.x + direction * 56, y: plane.y + 42, angle: plane.angle + direction * 65, alpha: .15, scaleX: plane.scaleX * .58, scaleY: plane.scaleY * .58, duration: 800, ease: 'Sine.In', onComplete: () => {
          this.effect('water-impact', plane.x, plane.y, 108); playNavalSound('splash'); plane.destroy()
        } })
        this.time.addEvent({ delay: 190, repeat: 2, callback: () => { if (plane.active) this.effect('burst', plane.x, plane.y, 42) } })
      } })
      for (const delay of [640, 840, 1040]) this.time.delayedCall(delay, () => {
        this.markPhase('aa-tracer')
        if (delay === 640) playNavalSound('intercept')
        this.flakTracer(gun.x, gun.y, interceptX, planeY, 240)
        const muzzle = this.effect('burst', gun.x - direction * 17, gun.y, 30)
        muzzle.setAlpha(.8)
      })
      this.time.delayedCall(1900, () => {
        this.tweens.add({ targets: gun, x: offscreen, alpha: 0, duration: 350, ease: 'Sine.In', onComplete: () => gun.destroy() })
        this.tweens.add({ targets: band, alpha: 0, duration: 350, onComplete: () => band.destroy() })
      })
    }
    flakTracer(x, y, targetX, targetY, duration) {
      const g = this.add.graphics(), state = { p: 0 }; this.effects.add(g)
      this.tweens.add({ targets: state, p: 1, duration, ease: 'Linear', onUpdate: () => {
        const p = state.p, tail = Math.max(0, p - .14)
        g.clear().lineStyle(3, 0xffefb6, 1).lineBetween(Phaser.Math.Linear(x, targetX, tail), Phaser.Math.Linear(y, targetY, tail), Phaser.Math.Linear(x, targetX, p), Phaser.Math.Linear(y, targetY, p))
        g.fillStyle(0xffffff).fillRect(Phaser.Math.Linear(x, targetX, p) - 2, Phaser.Math.Linear(y, targetY, p) - 2, 4, 4)
      }, onComplete: () => g.destroy() })
    }
    mineImpact(point, weapon) {
      if(point.mineKind!=='scrap'){this.mineDetonation(point);return}
      const x=center(point.x),y=center(point.y),torpedo=weapon==='torpedo'||weapon==='submarine'
      this.game.canvas.dataset.mineKind='scrap'
      this.markPhase(torpedo?'scrap-torpedo-impact':'scrap-destroyed')
      this.effect('water-impact',x,y,52)
      if(torpedo){this.effect('burst',x,y,64);playNavalSound('explosion')}
      else playNavalSound('splash')
    }
    mineDetonation(point) {
      this.game.canvas.dataset.mineKind='live'
      const x = center(point.x), y = center(point.y)
      const mine = this.fittedSprite(x, y + 5, 'mine', 28, 28)
      this.game.canvas.dataset.mineSize = '28'
      this.effects.add(mine); mine.setAlpha(.4)
      this.markPhase('mine-rise')
      this.tweens.add({ targets: mine, y, alpha: 1, duration: 180, ease: 'Sine.Out' })
      const flash = this.add.graphics(); this.effects.add(flash)
      const warning = { p: 0 }
      this.tweens.add({ targets: warning, p: 1, duration: 300, onUpdate: () => { const a = .25 + Math.abs(Math.sin(warning.p * Math.PI * 2)) * .65; flash.clear().lineStyle(2, 0xffc27a, a).strokeCircle(x, y, 13) } })
      this.time.delayedCall(300, () => {
        this.markPhase('mine-detonate'); playNavalSound('mine'); flash.destroy()
        this.tweens.add({ targets: mine, alpha: 0, duration: 100, onComplete: () => mine.destroy() })
        this.effect('burst', x, y, 54); this.effect('water-impact', x, y + 2, 57)
        const droplets = this.add.particles(x, y, 'splash', { frame: [4, 6, 8], lifespan: 650, emitting: false, speed: { min: 8, max: 18 }, angle: { min: 0, max: 360 }, gravityY: 10, alpha: { start: .5, end: 0 }, scale: { start: .06, end: .02 }, quantity: 3 }).setDepth(11)
        droplets.explode(3); this.time.delayedCall(700, () => droplets.destroy())
        const rings = this.add.graphics(), wave = { p: 0 }; this.effects.add(rings)
        this.tweens.add({ targets: wave, p: 1, duration: 850, onUpdate: () => {
          rings.clear().lineStyle(2, 0xe6ffff, (1 - wave.p) * .65).strokeCircle(x, y, 7 + wave.p * 17)
          rings.lineStyle(1, 0x96e6eb, (1 - wave.p) * .6).strokeCircle(x, y, 5 + wave.p * 12)
          if (wave.p > .6) this.markPhase('mine-ripple')
        }, onComplete: () => rings.destroy() })
      })
    }
    projectile(x, y, duration) {
      const g = this.add.graphics().setDepth(11), state = { p: 0 }, direction = this.direction()
      this.tweens.add({ targets: state, p: 1, duration, onUpdate: () => { const px = center(x) - (1 - state.p) * 140 * direction, py = center(y) + (1 - state.p) * 36; g.clear().lineStyle(3, 0xffe2a1, .85).lineBetween(px - 17 * direction, py + 4, px, py); g.fillStyle(0xfff7d1).fillRect(px - 2, py - 3, 4, 6) }, onComplete: () => g.destroy() })
    }
    torpedoWake(x, y, dropX, duration) {
      const direction = dropX == null ? this.direction() : 1, startX = dropX == null ? direction > 0 ? GAP - 25 : GAP + SEA + 25 : center(dropX)
      duration ??= navalTorpedoDuration(startX,center(x))
      const g = this.add.graphics().setDepth(9), torpedo = this.fittedSprite(startX, center(y), 'torpedo-projectile', 45, 15, direction > 0 ? 0 : Math.PI).setDepth(10)
      this.effects.add(torpedo); torpedo.setData('effect', 'torpedo-projectile')
      this.game.canvas.dataset.torpedoAsset = 'torpedo-projectile'
      this.game.canvas.dataset.torpedoDirection = this.visualDirection()
      this.tweens.add({ targets: torpedo, x: center(x), duration, ease: 'Linear', onUpdate: () => {
        this.game.canvas.dataset.torpedoX = String(this.snapshot.defenseSide === 'left' ? BOARD_SIZE-torpedo.x : torpedo.x)
        const px = torpedo.x, py = torpedo.y
        g.clear().lineStyle(2, 0xe0f2ef, .7).lineBetween(px - 33 * direction, py - 5, px - 18 * direction, py).lineBetween(px - 33 * direction, py + 5, px - 18 * direction, py)
      }, onComplete: () => { g.destroy(); torpedo.destroy() } })
    }
    radarSweep(x, y) {
      const n=this.snapshot.military?2:3
      const g = this.add.graphics().setDepth(11), state = { p: 0 }, cx = GAP + (x + n/2) * CELL, cy = GAP + (y + n/2) * CELL
      const probes = Array.isArray(this.snapshot.radar) && this.snapshot.radar.length
        ? this.snapshot.radar.filter(point => point.x >= x && point.x < x + n && point.y >= y && point.y < y + n).slice(-n*n)
        : this.snapshot.hidden ? [] : Array.from({ length: n*n }, (_, index) => {
          const col = x + index % n, row = y + Math.floor(index / n)
          return { x: col, y: row, occupied: this.snapshot.player?.ships?.some(ship => !ship.stealth && col >= ship.x && col < ship.x + ship.w && row >= ship.y && row < ship.y + ship.h) }
        })
      this.tweens.add({ targets: state, p: 1, duration: 1400, onUpdate: () => {
        const p = state.p, alpha = Math.min(1, p * 4) * (1 - p)
        g.clear().lineStyle(3, 0xc2f3ab, 1 - p).strokeCircle(cx, cy, 14 + p * 72)
        g.lineStyle(2, 0xc2f3ab, .75 * (1 - p)).strokeRect(GAP + x * CELL + 3, GAP + y * CELL + 3, CELL * n - 6, CELL * n - 6)
        g.lineStyle(3, 0xe1ffd3, .8 * (1 - p)).lineBetween(cx, cy, cx + Math.cos(p * Math.PI * 3) * 67, cy + Math.sin(p * Math.PI * 3) * 67)
        for (const point of probes) {
          const positive = point.ship ?? point.hit ?? point.occupied ?? false
          g.lineStyle(2, positive ? 0xffd18d : 0xc2f3ab, alpha).strokeRect(GAP + point.x * CELL + 7, GAP + point.y * CELL + 7, CELL - 14, CELL - 14)
          if (positive) g.fillStyle(0xffd18d, alpha * .45).fillRect(GAP + point.x * CELL + 8, GAP + point.y * CELL + 8, CELL - 16, CELL - 16)
        }
      }, onComplete: () => g.destroy() })
    }
  }
}

export function mountNavalGame(parent, snapshot, callbacks) {
  const Scene = createNavalScene(callbacks, snapshot)
  const game = new Phaser.Game({ type: Phaser.CANVAS, parent, width: BOARD_SIZE, height: BOARD_SIZE, backgroundColor: '#e5eff1', pixelArt: true, antialias: false, banner: false, audio: { noAudio: true }, fps: { target: 40 }, render: { antialias: false, roundPixels: true }, scale: { mode: Phaser.Scale.FIT, autoCenter: Phaser.Scale.CENTER_BOTH, width: BOARD_SIZE, height: BOARD_SIZE }, scene: Scene })
  return { game, update(value) { game.scene.getScene('naval-board')?.updateSnapshot(value) }, move(dx, dy) { game.scene.getScene('naval-board')?.moveFocus(dx, dy) }, choose() { game.scene.getScene('naval-board')?.chooseFocus() }, destroy() { game.destroy(true) } }
}
