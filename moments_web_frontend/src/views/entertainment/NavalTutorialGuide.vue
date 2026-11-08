<template>
  <div class="naval-tutorial-mask" aria-hidden="true">
    <div v-for="(panel, index) in maskPanels" :key="index" class="shade-panel" :style="panel" />
  </div>
  <div class="naval-tutorial-highlight" :class="{ visible: highlightVisible }" :style="highlight" :data-target="step.target" :data-moving="travelling" aria-hidden="true" />
  <aside ref="guide" class="naval-tutorial-guide" :class="{ 'mobile-guide': step.device === 'mobile' }" :style="guidePosition" :data-step="step.id" :data-mode="step.mode" :data-device="step.device || 'desktop'" aria-label="海战棋新手教程">
    <header class="guide-meta"><GraduationCap :size="17" /><strong>{{ step.device === 'mobile' ? '手机海战教程' : '海战棋新手教程' }}</strong><span>{{ completed ? '已完成' : `${step.index + 1} / ${step.total}` }}</span></header>
    <Transition name="naval-hint" mode="out-in">
      <div :key="step.id" id="naval-tutorial-instruction" class="guide-copy" role="status" aria-live="polite" aria-atomic="true"><span class="guide-chapter">{{ step.chapter }}</span><h2>{{ step.title }}</h2><p>{{ step.description }}</p></div>
    </Transition>
    <footer class="guide-actions">
      <button v-if="completed" class="guide-primary" @click="$emit('exit')"><ArrowRight :size="15" />返回港口</button>
      <button v-else-if="step.mode !== 'action'" class="guide-primary" :disabled="busy || travelling || step.canContinue === false" @click="$emit('continue')"><ArrowRight :size="15" />{{ step.continueLabel || '看懂了，继续' }}</button>
      <span v-else class="action-hint"><Hand v-if="step.device === 'mobile'" :size="14" /><MousePointer2 v-else :size="14" />{{ busy ? '正在播放战斗动画' : travelling ? '正在聚焦下一处' : step.device === 'mobile' ? '点按亮起的区域' : '操作亮起的区域' }}</span>
      <button class="guide-icon" title="重新定位当前操作" aria-label="重新定位当前操作" @click="locate"><ScanLine :size="16" /></button>
      <button class="guide-icon" title="重新开始海战教程" aria-label="重新开始海战教程" @click="$emit('restart')"><RotateCcw :size="16" /></button>
      <button class="guide-icon" title="退出教程，保留进度" aria-label="退出教程，保留进度" @click="$emit('exit')"><LogOut :size="16" /></button>
    </footer>
    <div class="guide-progress" role="progressbar" aria-label="海战教程完成进度" :aria-valuemin="0" :aria-valuemax="step.total" :aria-valuenow="completed ? step.total : step.index"><i :style="{ width: `${(completed ? step.total : step.index) / step.total * 100}%` }" /></div>
  </aside>
</template>

<script setup>
import { nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { ArrowRight, GraduationCap, Hand, LogOut, MousePointer2, RotateCcw, ScanLine } from 'lucide-vue-next'

const props = defineProps({ step: { type: Object, required: true }, completed: Boolean, busy: Boolean })
const emit = defineEmits(['continue', 'restart', 'exit', 'locate', 'resize'])
const guide = ref(null), highlightVisible = ref(false), travelling = ref(true)
const highlight = ref({ left: '0px', top: '0px', width: '0px', height: '0px' })
const guidePosition = ref({ left: '12px', top: '12px', width: '340px' })
const maskPanels = ref([{ inset: '0' }])
const motionPreference = window.matchMedia('(prefers-reduced-motion: reduce)')
let target, observer, frame, generation = 0, lastGeometry = '', lastMovement = 0
let hole = { left: 0, top: 0, right: 0, bottom: 0 }

function releaseTarget() {
  if (!target) return
  observer?.unobserve(target)
  target.removeAttribute('data-naval-tutorial-active')
  const description = (target.getAttribute('aria-describedby') || '').split(' ').filter(id => id && id !== 'naval-tutorial-instruction')
  if (description.length) target.setAttribute('aria-describedby', description.join(' '))
  else target.removeAttribute('aria-describedby')
  target = null
}

function findTarget() {
  const element = [...document.querySelectorAll('[data-tutorial]')].find(element =>
    element.dataset.tutorial === props.step.target && element.getClientRects().length && getComputedStyle(element).visibility !== 'hidden')
  return props.step.subTarget ? element?.querySelector(props.step.subTarget) : element
}

function attachTarget(element) {
  releaseTarget(); target = element
  if (!target) return
  observer?.observe(target)
  target.setAttribute('data-naval-tutorial-active', 'true')
  target.setAttribute('aria-describedby', `${target.getAttribute('aria-describedby') || ''} naval-tutorial-instruction`.trim())
  if (props.step.device !== 'mobile') target.scrollIntoView({ block: 'nearest', inline: 'nearest', behavior: motionPreference.matches ? 'instant' : 'smooth' })
  else revealInPanel(target)
}

function revealInPanel(element) {
  for (let parent = element.parentElement; parent && parent !== document.body && !parent.classList.contains('naval-page'); parent = parent.parentElement) {
    const style = getComputedStyle(parent), box = parent.getBoundingClientRect(), bounds = element.getBoundingClientRect()
    if (/(auto|scroll)/.test(style.overflowY) && parent.scrollHeight > parent.clientHeight) {
      if (bounds.top < box.top) parent.scrollTop -= box.top - bounds.top
      else if (bounds.bottom > box.bottom) parent.scrollTop += bounds.bottom - box.bottom
    }
    if (/(auto|scroll)/.test(style.overflowX) && parent.scrollWidth > parent.clientWidth) {
      if (bounds.left < box.left) parent.scrollLeft -= box.left - bounds.left
      else if (bounds.right > box.right) parent.scrollLeft += bounds.right - box.right
    }
  }
}

function targetBounds() {
  const bounds = target.getBoundingClientRect(), cell = props.step.cell
  let left = bounds.left, top = bounds.top, right = bounds.right, bottom = bounds.bottom
  if (cell) {
    const board = target.matches('.naval-board') ? target : target.closest('.naval-board') || target.querySelector('.naval-board')
    const x = board?.classList.contains('mirrored') ? 12-cell.x-(cell.w || 1) : cell.x
    left += (24 + x * 48) / 624 * bounds.width
    top += (24 + cell.y * 48) / 624 * bounds.height
    right = left + (cell.w || 1) * 48 / 624 * bounds.width
    bottom = top + (cell.h || 1) * 48 / 624 * bounds.height
  }
  left -= 4; top -= 4; right += 4; bottom += 4
  for (let parent = target.parentElement; parent && parent !== document.body; parent = parent.parentElement) {
    const style = getComputedStyle(parent), clip = parent.getBoundingClientRect()
    if (/(auto|scroll|hidden|clip)/.test(style.overflowY)) { top = Math.max(top, clip.top); bottom = Math.min(bottom, clip.bottom) }
    if (/(auto|scroll|hidden|clip)/.test(style.overflowX)) { left = Math.max(left, clip.left); right = Math.min(right, clip.right) }
  }
  return { left: Math.max(4, left), top: Math.max(4, top), right: Math.min(innerWidth - 4, right), bottom: Math.min(innerHeight - 4, bottom) }
}

function placeGuide(bounds) {
  if (!guide.value) return
  const compact = props.step.device === 'mobile'
  const gap = compact ? 9 : 14, margin = compact ? 8 : 12
  const ownBoard = props.step.guideSide && document.querySelector(`[data-tutorial="${props.step.guideSide==='right'?'naval-enemy-board':'naval-own-board'}"]`)?.getBoundingClientRect()
  if (ownBoard) {
    let left=Math.max(margin,ownBoard.left+margin),right=Math.min(innerWidth-margin,ownBoard.right-margin)
    if(bounds.top<ownBoard.bottom&&bounds.bottom>ownBoard.top){
      if(props.step.guideSide==='left')right=Math.min(right,bounds.left-gap)
      else left=Math.max(left,bounds.right+gap)
    }
    const width = Math.min(compact ? 260 : 350,Math.max(140,right-left))
    const height = guide.value.getBoundingClientRect().height
    guidePosition.value = { left: `${left+Math.max(0,(right-left-width)/2)}px`, top: `${Math.max(margin,Math.min(innerHeight-height-margin,ownBoard.top+(ownBoard.height-height)/2))}px`, width: `${width}px` }
    return
  }
  const sideSpace = Math.floor(Math.max(bounds.left - gap - margin, innerWidth - bounds.right - gap - margin))
  const preferredWidth = Math.min(innerWidth - margin * 2, compact ? 260 : innerHeight < 500 ? 310 : 350)
  const width = sideSpace >= (compact ? 170 : 240) ? Math.min(preferredWidth, sideSpace) : preferredWidth
  const height = Math.min(innerHeight - margin * 2, guide.value.getBoundingClientRect().height || (compact ? 150 : 210))
  let left, top
  if (innerWidth - bounds.right >= width + gap + margin) { left = bounds.right + gap; top = (bounds.top + bounds.bottom - height) / 2 }
  else if (bounds.left >= width + gap + margin) { left = bounds.left - width - gap; top = (bounds.top + bounds.bottom - height) / 2 }
  else if (innerHeight - bounds.bottom >= height + gap + margin) { left = (bounds.left + bounds.right - width) / 2; top = bounds.bottom + gap }
  else if (bounds.top >= height + gap + margin) { left = (bounds.left + bounds.right - width) / 2; top = bounds.top - height - gap }
  else {
    left = Math.max(margin, Math.min(innerWidth - width - margin, bounds.left + (bounds.right - bounds.left - width) / 2))
    top = bounds.top > innerHeight / 2 ? margin : innerHeight - height - margin
  }
  guidePosition.value = { left: `${Math.max(margin, Math.min(innerWidth - width - margin, left))}px`, top: `${Math.max(margin, Math.min(innerHeight - height - margin, top))}px`, width: `${width}px` }
}

function updatePosition() {
  if (!target?.isConnected || !target.getClientRects().length) return false
  const bounds = targetBounds()
  if (bounds.bottom <= bounds.top || bounds.right <= bounds.left) { highlightVisible.value = false; return false }
  const geometry = [bounds.left, bounds.top, bounds.right - bounds.left, bounds.bottom - bounds.top].map(number => Math.round(number * 2) / 2)
  const key = geometry.join(',')
  if (key !== lastGeometry) {
    lastGeometry = key; lastMovement = performance.now()
    highlight.value = { left: `${geometry[0]}px`, top: `${geometry[1]}px`, width: `${geometry[2]}px`, height: `${geometry[3]}px` }
  }
  hole = bounds
  maskPanels.value = [
    { top: '0px', left: '0px', right: '0px', height: `${bounds.top}px` },
    { top: `${bounds.bottom}px`, left: '0px', right: '0px', bottom: '0px' },
    { top: `${bounds.top}px`, left: '0px', width: `${bounds.left}px`, height: `${bounds.bottom - bounds.top}px` },
    { top: `${bounds.top}px`, left: `${bounds.right}px`, right: '0px', height: `${bounds.bottom - bounds.top}px` }
  ]
  highlightVisible.value = true
  placeGuide(bounds)
  return true
}

async function focusTarget(recover = true) {
  const revision = ++generation
  cancelAnimationFrame(frame); travelling.value = true; highlightVisible.value = false
  await nextTick()
  const element = findTarget()
  attachTarget(element)
  if (!element && recover) emit('locate')
  const started = performance.now(); lastMovement = started
  function follow(now) {
    if (revision !== generation) return
    if (!target?.isConnected) { const next = findTarget(); if (next) attachTarget(next) }
    const visible = updatePosition()
    if (visible && (motionPreference.matches || now - started > 320 && now - lastMovement > 200)) travelling.value = false
    frame = requestAnimationFrame(follow)
  }
  frame = requestAnimationFrame(follow)
}

async function locate() { emit('locate'); await focusTarget(false) }

function guardPointer(event) {
  if (guide.value?.contains(event.target)) return
  const inHole = event.clientX >= hole.left && event.clientX <= hole.right && event.clientY >= hole.top && event.clientY <= hole.bottom
  if (!props.busy && !travelling.value && props.step.mode === 'action' && target?.contains(event.target) && (inHole || event.detail === 0)) return
  event.preventDefault(); event.stopImmediatePropagation()
}

function guardKeyboard(event) {
  if (event.key === 'Escape') { event.preventDefault(); emit('exit'); return }
  if (props.step.id === 'rotate' && !props.busy && !travelling.value && event.key.toLowerCase() === 'r' && !event.target?.closest?.('input, textarea, select, [contenteditable="true"]')) return
  const selector = 'button:not(:disabled),input:not(:disabled),a[href],[tabindex]:not([tabindex="-1"])'
  const targetControls = props.step.mode === 'action' && !props.busy ? [target?.matches(selector) ? target : null, ...(target?.querySelectorAll(selector) || [])] : []
  const controls = [...targetControls, ...(guide.value?.querySelectorAll(selector) || [])].filter(element => element?.getClientRects().length)
  if (event.key === 'Tab' && controls.length) {
    const index = controls.indexOf(document.activeElement)
    const next = index < 0 ? event.shiftKey ? controls.length - 1 : 0 : (index + (event.shiftKey ? -1 : 1) + controls.length) % controls.length
    event.preventDefault(); controls[next].focus({ preventScroll: true }); return
  }
  if (guide.value?.contains(event.target)) return
  if (props.step.mode === 'action' && !props.busy && !travelling.value && target?.contains(event.target)) return
  event.preventDefault(); event.stopImmediatePropagation()
}

watch(() => JSON.stringify([props.step.id, props.step.target, props.step.subTarget, props.step.cell,props.step.guideSide]), () => focusTarget(), { flush: 'post' })
onMounted(() => {
  observer = new ResizeObserver(() => { if (guide.value) emit('resize', Math.ceil(guide.value.getBoundingClientRect().height)); updatePosition() })
  observer.observe(guide.value)
  window.addEventListener('resize', updatePosition); window.addEventListener('scroll', updatePosition, true)
  for (const name of ['pointerdown', 'pointerup', 'click']) document.addEventListener(name, guardPointer, true)
  document.addEventListener('keydown', guardKeyboard, true)
  focusTarget()
})
onBeforeUnmount(() => {
  generation++; cancelAnimationFrame(frame); observer?.disconnect(); releaseTarget()
  window.removeEventListener('resize', updatePosition); window.removeEventListener('scroll', updatePosition, true)
  for (const name of ['pointerdown', 'pointerup', 'click']) document.removeEventListener(name, guardPointer, true)
  document.removeEventListener('keydown', guardKeyboard, true)
})
</script>

<style scoped>
.naval-tutorial-mask{position:fixed;inset:0;z-index:1600;pointer-events:none}.shade-panel{position:fixed;background:rgba(10,22,29,.77);pointer-events:auto;transition:top .28s ease,left .28s ease,width .28s ease,height .28s ease}.naval-tutorial-highlight{position:fixed;z-index:1610;pointer-events:none;border:2px solid #f4bc6e;border-radius:4px;box-shadow:0 0 0 1px #fff9,inset 0 0 0 1px #fff8,0 0 18px #f6cf7730;opacity:0;transition:left .28s ease,top .28s ease,width .28s ease,height .28s ease,opacity .2s}.naval-tutorial-highlight.visible{opacity:1}.naval-tutorial-guide{box-sizing:border-box;position:fixed;z-index:1800;max-height:calc(100dvh - 24px);overflow:auto;padding:15px 16px 0;border:1px solid #dbe4e7;border-top:3px solid #e89843;border-radius:6px;background:#fcfdfd;color:#294650;box-shadow:0 10px 40px #07172055;letter-spacing:0}.guide-meta{display:flex;align-items:center;gap:7px;color:#b67936;font-size:11px}.guide-meta strong{font-size:12px;white-space:nowrap}.guide-meta>span{margin-left:auto;font:11px monospace;white-space:nowrap;color:#879aa1}.guide-copy{min-height:117px}.guide-chapter{display:block;font-size:10px;color:#97a9af;margin-top:12px}.guide-copy h2{font-size:17px;line-height:1.45;margin:5px 0 7px}.guide-copy p{font-size:12px;line-height:1.8;color:#617d87;overflow-wrap:anywhere}.guide-actions{display:flex;align-items:center;gap:6px;padding:15px 0}.guide-actions>button{display:inline-flex;align-items:center;justify-content:center;gap:5px;min-height:33px;border-radius:4px;font-size:11px;flex-shrink:0}.guide-primary{background:#d88c3b;color:#fff;padding:0 11px;margin-right:auto}.guide-primary:disabled{opacity:.45;cursor:not-allowed}.guide-icon{width:32px;background:#fff;border:1px solid #dce5e8;color:#78909a}.action-hint{display:flex;align-items:center;gap:5px;font-size:10px;color:#c18441;margin-right:auto;min-width:0}.guide-progress{height:3px;margin:0 -16px;background:#e6edf0}.guide-progress i{display:block;height:100%;background:#e9a655;transition:width .25s}.naval-hint-enter-active,.naval-hint-leave-active{transition:opacity .1s,transform .1s}.naval-hint-enter-from{opacity:0;transform:translateY(4px)}.naval-hint-leave-to{opacity:0;transform:translateY(-4px)}
@media(max-height:500px){.naval-tutorial-guide{padding:10px 12px 0}.guide-meta{font-size:9px}.guide-meta strong{font-size:10px}.guide-meta>svg{width:15px}.guide-copy{min-height:96px}.guide-chapter{margin-top:8px;font-size:9px}.guide-copy h2{font-size:14px;margin:4px 0 5px}.guide-copy p{font-size:11px;line-height:1.7}.guide-actions{padding:10px 0;gap:5px}.guide-actions>button{min-height:30px;font-size:10px}.guide-icon{width:29px}.action-hint{font-size:9px;gap:3px}.guide-primary{padding:0 9px}.guide-progress{margin:0 -12px}}
.mobile-guide{padding:9px 10px 0;max-height:calc(100dvh - 16px);border-top-width:2px}.mobile-guide .guide-meta{gap:5px}.mobile-guide .guide-meta>svg{width:14px;height:14px}.mobile-guide .guide-meta strong{font-size:10px}.mobile-guide .guide-meta>span{font-size:9px}.mobile-guide .guide-copy{min-height:0}.mobile-guide .guide-chapter{font-size:9px;margin-top:6px}.mobile-guide .guide-copy h2{font-size:13px;line-height:1.4;margin:3px 0 5px}.mobile-guide .guide-copy p{font-size:11px;line-height:1.6}.mobile-guide .guide-actions{padding:8px 0;gap:4px;flex-wrap:wrap}.mobile-guide .guide-actions>button{min-height:30px;font-size:10px}.mobile-guide .guide-primary{padding:0 8px}.mobile-guide .guide-icon{width:28px}.mobile-guide .action-hint{font-size:9px;line-height:1.5;flex:1 1 90px;gap:4px}.mobile-guide .guide-progress{margin:0 -10px}.mobile-guide .guide-meta strong{white-space:normal}.mobile-guide .guide-meta>span{flex-shrink:0}
@media(prefers-reduced-motion:reduce){.shade-panel,.naval-tutorial-highlight,.guide-progress i,.naval-hint-enter-active,.naval-hint-leave-active{transition:none}}
</style>
