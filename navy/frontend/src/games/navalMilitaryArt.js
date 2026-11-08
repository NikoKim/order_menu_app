import { MILITARY_SHIPS, militaryCell, militaryComponents, militarySunk } from './navalMilitary.js'

const ROOT = '/naval/'
const SOURCES = {
  repairTugHull: `${ROOT}military/repair-tug.png`, repair: `${ROOT}military/repair-tool.png`, tow: `${ROOT}military/tow-tool.png`,
  depthCharge: `${ROOT}military/depth-charge.png`,depthChargeHull:`${ROOT}military/depth-charge-destroyer.png`,
  carrier: `${ROOT}military/carrier.png`, hull: `${ROOT}military/utility-hull.png`,
  fighter: `${ROOT}fleet/fighter.png`, bomber: `${ROOT}fleet/bomber.png`, torpedo: `${ROOT}fleet/torpedo.png`,
  gun: `${ROOT}fleet/cannon.png`, antiAir: `${ROOT}fleet/antiAir.png`, radar: `${ROOT}fleet/radar.png`,
  submarine: `${ROOT}fleet/submarine.png`, mine: `${ROOT}fleet/mine.png`, stealth: `${ROOT}fleet/stealth.png`
}
let assets = {}, loading

// Trim transparent padding once; every footprint then has stable, cell-aligned geometry.
function trimmed(image) {
  const canvas = document.createElement('canvas')
  canvas.width = image.naturalWidth; canvas.height = image.naturalHeight
  const ctx = canvas.getContext('2d', { willReadFrequently: true })
  ctx.drawImage(image, 0, 0)
  const pixels = ctx.getImageData(0, 0, canvas.width, canvas.height).data
  let x0 = canvas.width, y0 = canvas.height, x1 = -1, y1 = -1
  for (let y = 0; y < canvas.height; y++) for (let x = 0; x < canvas.width; x++) {
    if (pixels[(y * canvas.width + x) * 4 + 3] < 32) continue
    x0 = Math.min(x0, x); y0 = Math.min(y0, y); x1 = Math.max(x1, x); y1 = Math.max(y1, y)
  }
  if (x1 < x0) throw new Error('Empty military sprite')
  return { image, x: x0, y: y0, w: x1 - x0 + 1, h: y1 - y0 + 1 }
}

export function loadMilitaryArt() {
  if (!loading) loading = Promise.all(Object.entries(SOURCES).map(([name, url]) => new Promise((resolve, reject) => {
    const image = new Image()
    image.onload = () => { try { resolve([name, trimmed(image)]) } catch (error) { reject(error) } }
    image.onerror = () => reject(new Error(`Military sprite failed to load: ${url}`))
    image.src = url
  }))).then(entries => { assets = Object.fromEntries(entries); return assets }).catch(error => { loading = null; throw error })
  return loading
}

export function militaryShipThumbnail(kind) {
  const model = MILITARY_SHIPS[kind], cell = 40
  const canvas = document.createElement('canvas')
  canvas.width = model.w * cell; canvas.height = model.h * cell
  drawMilitaryShip(canvas.getContext('2d'), { ...model, kind, id: `catalog-${kind}`, x: 0, y: 0, rotation: 0, hits: [], lostPlanes: [], minesUsed: 0 }, cell)
  return canvas.toDataURL('image/png')
}

function sprite(ctx, name, x, y, w, h, fit = false) {
  const asset = assets[name]
  if (!asset) return
  if (fit) {
    const scale = Math.min(w / asset.w, h / asset.h), sw = asset.w * scale, sh = asset.h * scale
    x += (w - sw) / 2; y += (h - sh) / 2; w = sw; h = sh
  }
  ctx.drawImage(asset.image, asset.x, asset.y, asset.w, asset.h, x, y, w, h)
}

function canonicalTransform(ctx, model, rotation, cell) {
  if (rotation === 90) ctx.translate(model.h * cell, 0)
  else if (rotation === 180) ctx.translate(model.w * cell, model.h * cell)
  else if (rotation === 270) ctx.translate(0, model.w * cell)
  ctx.rotate(rotation * Math.PI / 180)
}

function partLocal(ship, part) {
  const model = MILITARY_SHIPS[ship.kind]
  for (let y = 0; y < model.h; y++) for (let x = 0; x < model.w; x++) {
    const cell = militaryCell(ship, x, y)
    if (cell.x === part.x && cell.y === part.y) return { x, y }
  }
  return { x: 0, y: 0 }
}

function damage(ctx, x, y, cell, time) {
  const pulse = .55 + Math.sin(time / 150) * .15
  ctx.fillStyle = '#293c43bb'; ctx.fillRect(x + cell * .13, y + cell * .13, cell * .74, cell * .74)
  ctx.strokeStyle = '#eb785e'; ctx.lineWidth = Math.max(1, cell * .035)
  ctx.beginPath(); ctx.moveTo(x + cell * .25, y + cell * .25); ctx.lineTo(x + cell * .75, y + cell * .75)
  ctx.moveTo(x + cell * .75, y + cell * .25); ctx.lineTo(x + cell * .25, y + cell * .75); ctx.stroke()
  ctx.fillStyle = `rgba(255,181,76,${pulse})`
  ctx.fillRect(x + cell * .42, y + cell * .29, cell * .12, cell * .23)
  ctx.fillStyle = '#e4673d'; ctx.fillRect(x + cell * .35, y + cell * .43, cell * .26, cell * .15)
  for (let i = 0; i < 3; i++) {
    const progress = ((time / 1400 + i / 3) % 1 + 1) % 1
    ctx.fillStyle = `rgba(48,60,66,${(1 - progress) * .5})`
    const size = cell * (.11 + progress * .13)
    ctx.fillRect(x + cell * (.45 + Math.sin(i + progress * 3) * .08), y + cell * (.3 - progress * .48), size, size)
  }
}

function inFlight(ship, index, option) {
  if (typeof option === 'number') return option === index
  if (Array.isArray(option)) return option.includes(index)
  return option?.shipId === ship.id && (option.plane ?? 0) === index
}

function hullScorch(ctx, x, y, cell) {
  ctx.fillStyle = '#293c4399'
  ctx.fillRect(x + cell * .24, y + cell * .22, cell * .54, cell * .5)
  ctx.strokeStyle = '#aab5ac'; ctx.lineWidth = Math.max(1, cell * .025)
  ctx.beginPath(); ctx.moveTo(x + cell * .33, y + cell * .28)
  ctx.lineTo(x + cell * .5, y + cell * .45); ctx.lineTo(x + cell * .43, y + cell * .64); ctx.stroke()
}

function jammerModule(ctx, x, y, cell) {
  sprite(ctx, 'radar', x + cell * .12, y + cell * .08, cell * .76, cell * .8, true)
  ctx.strokeStyle = '#b8e7db'; ctx.lineWidth = Math.max(1, cell * .035)
  ctx.beginPath(); ctx.moveTo(x + cell * .17, y + cell * .18); ctx.lineTo(x + cell * .17, y + cell * .63)
  ctx.moveTo(x + cell * .83, y + cell * .18); ctx.lineTo(x + cell * .83, y + cell * .63); ctx.stroke()
  ctx.fillStyle = '#b6a9ed'
  ctx.fillRect(x + cell * .12, y + cell * .14, cell * .1, cell * .08)
  ctx.fillRect(x + cell * .78, y + cell * .14, cell * .1, cell * .08)
}

/** Draw at local (0,0); caller translates to ship.x/y and controls board clipping. */
export function drawMilitaryShip(ctx, ship, cellSize, options = {}) {
  const model = MILITARY_SHIPS[ship.kind]
  if (!model || !assets.hull) return false
  const c = cellSize, time = options.time || 0, wreck = militarySunk(ship)
  ctx.save(); ctx.imageSmoothingEnabled = false
  canonicalTransform(ctx, model, ship.rotation || 0, c)
  if (wreck) { ctx.globalAlpha *= .58; ctx.filter = 'grayscale(1) brightness(.55)' }
  const carrier = ['carrier','bomberCarrier','torpedoCarrier'].includes(ship.kind)
  sprite(ctx, carrier ? 'carrier' : ship.kind==='repairTug'?'repairTugHull':ship.kind==='depthCharge'?'depthChargeHull':ship.kind === 'submarine' ? 'submarine' : 'hull', c * .09, c * .07, model.w * c - c * .18, model.h * c - c * .14)
  if (ship.kind === 'jammer') sprite(ctx, 'stealth', c * .27, c * 1.25, c * .46, c * .52, true)
  if (ship.kind === 'mineLayer') {
    for (let i = 0; i < 10; i++) {
      ctx.fillStyle = i < (ship.minesUsed || 0) ? '#485b60' : '#edcb77'
      ctx.fillRect(c * (.2 + (i % 5) * .13), c * (1.57 + Math.floor(i / 5) * .16), c * .085, c * .12)
    }
    sprite(ctx, 'mine', c * .3, c * 1.19, c * .4, c * .4, true)
  }
  for (const part of militaryComponents(ship)) {
    if (ship.kind === 'submarine') continue
    const local = partLocal(ship, part), x = local.x * c, y = local.y * c
    const aircraft = ['fighter', 'bomber', 'torpedo'].includes(part.role)
    const bomber = ['bomberCarrier','torpedoCarrier'].includes(ship.kind), span = bomber ? 2 : 1
    const lost = (ship.lostPlanes || []).includes(part.index) && aircraft
    const damagedCells = Array.from({ length: span }, (_, i) => ({ ...militaryCell(ship, local.x, local.y + i), localY: local.y + i }))
      .filter(cell => (ship.hits || []).includes(`${cell.x},${cell.y}`))
    const disabled = !part.active && damagedCells.length > 0
    ctx.save()
    ctx.strokeStyle = disabled ? '#e98066' : lost ? '#84979d' : part.role === 'antiAir' ? '#bfe48f' : '#f6d68a'
    ctx.lineWidth = Math.max(1, c * .025)
    ctx.setLineDash(carrier ? [c * .08, c * .05] : [])
    ctx.strokeRect(x + c * .1, y + c * .1, c * .8, c * (span - .2))
    ctx.setLineDash([])
    if (!part.active) ctx.filter = 'grayscale(1) brightness(.5)'
    const flying = aircraft && inFlight(ship, part.index, options.aircraftInFlight)
    if (!lost && !flying) {
      if (ship.kind === 'battleship' && part.index === 1) {
        ctx.translate(x + c / 2, y + c / 2); ctx.rotate(Math.PI)
        sprite(ctx, part.role, -c * .38, -c * .42, c * .76, c * .84, true)
      } else if (part.role === 'jammer') jammerModule(ctx, x, y, c)
      else if (bomber) sprite(ctx, part.role, x + c * .1, y + c * .2, c * .8, c * 1.6)
      else sprite(ctx, part.role, x + c * .12, y + c * .08, c * .76, c * .84, true)
    }
    ctx.restore()
    if (damagedCells.length) for (const cell of damagedCells) {
      if (disabled) damage(ctx, x, cell.localY * c, c, time)
      else hullScorch(ctx, x, cell.localY * c, c)
    }
    else if (lost) {
      ctx.strokeStyle = '#dd7866'; ctx.lineWidth = Math.max(1, c * .035)
      ctx.beginPath(); ctx.moveTo(x + c * .3, y + c * .3); ctx.lineTo(x + c * .7, y + c * .7)
      ctx.moveTo(x + c * .7, y + c * .3); ctx.lineTo(x + c * .3, y + c * .7); ctx.stroke()
    }
  }
  if (ship.kind === 'submarine') for (let y = 0; y < model.h; y++) {
    const cell = militaryCell(ship, 0, y)
    if ((ship.hits || []).includes(`${cell.x},${cell.y}`)) hullScorch(ctx, 0, y * c, c)
  }
  if (ship.kind === 'decoy') {
    ctx.fillStyle = '#dae5d9'; ctx.fillRect(c * .35, c * .27, c * .3, c * .22)
    ctx.fillStyle = '#577979'; ctx.fillRect(c * .42, c * .31, c * .16, c * .04)
  }
  if (ship.kind === 'repairTug') for (const [x,y] of [[1,0],[0,1]]) {
    const cell=militaryCell(ship,x,y)
    if((ship.hits||[]).includes(`${cell.x},${cell.y}`))hullScorch(ctx,x*c,y*c,c)
  }
  ctx.restore()
  return true
}

/** Aircraft centered at x/y; angle in radians, matching the board's flight route. */
export function drawMilitaryAircraft(ctx, type, x, y, cellSize, angle = 0, options = {}) {
  ctx.save(); ctx.translate(x, y); ctx.rotate(angle); ctx.imageSmoothingEnabled = false
  ctx.globalAlpha *= options.alpha ?? 1
  if (options.destroyed) ctx.filter = 'grayscale(1) brightness(.5)'
  sprite(ctx, ['torpedo', 'bomber'].includes(type) ? type : 'fighter', -cellSize * .43, -cellSize * .43, cellSize * .86, cellSize * .86, true)
  ctx.restore()
}

export function drawMilitaryImpact(ctx, x, y, cellSize, progress) {
  const p = Math.max(0, Math.min(1, progress)), size = cellSize * (.2 + p * .9)
  ctx.save(); ctx.globalAlpha *= 1 - p; ctx.translate(x, y)
  ctx.fillStyle = p < .4 ? '#ffe7a0' : '#e17749'; ctx.fillRect(-size / 2, -size / 2, size, size)
  ctx.strokeStyle = '#fff2c5'; ctx.lineWidth = 2; ctx.strokeRect(-size * .7, -size * .7, size * 1.4, size * 1.4)
  ctx.restore()
}
