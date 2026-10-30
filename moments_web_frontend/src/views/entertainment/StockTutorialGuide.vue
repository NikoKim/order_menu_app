<template>
  <div class="tutorial-highlight" :class="{ visible: highlightVisible }" :style="highlight" :data-moving="travelling" :data-target="step.target" aria-hidden="true"></div>
  <aside ref="guide" class="tutorial-guide" :data-step="step.id" :data-mode="step.mode" aria-label="新手教程引导">
    <div class="guide-inner">
      <div class="guide-content">
        <div class="guide-meta"><GraduationCap :size="18" /><strong>新手教学股</strong><span>{{ completed ? '已完成' : `${step.index + 1} / ${step.total}` }}</span><span>{{ step.chapter }}</span></div>
        <Transition name="hint" mode="out-in">
          <div :key="step.id" id="tutorial-instruction" class="guide-copy" role="status" aria-live="polite" aria-atomic="true">
            <h2>{{ step.title }}</h2><p>{{ step.description }}</p>
          </div>
        </Transition>
      </div>
      <div class="guide-actions">
        <button v-if="completed" class="guide-primary" @click="$emit('exit')"><ArrowRight :size="16" />进入正式游戏</button>
        <button v-else-if="step.id === 'start'" class="guide-primary" :disabled="busy || travelling" @click="$emit('start')"><Play :size="16" />开始练习</button>
        <button v-else-if="step.mode !== 'action'" class="guide-primary" :disabled="busy || travelling || !step.canContinue" @click="$emit('continue')"><ArrowRight :size="16" />{{ step.continueLabel || '看懂了，继续' }}</button>
        <span v-else class="guide-action-hint"><MousePointer2 :size="15" />{{ travelling ? '正在移动到下一处' : '请操作高亮控件' }}</span>
        <button class="guide-icon" title="重新定位当前操作" aria-label="重新定位当前操作" @click="locate"><ScanLine :size="17" /></button>
        <button class="guide-icon" title="重新开始教程" aria-label="重新开始教程" @click="$emit('restart')"><RotateCcw :size="17" /></button>
        <button class="guide-icon" title="退出教程，保留进度" aria-label="退出教程，保留进度" @click="$emit('exit')"><LogOut :size="17" /></button>
      </div>
    </div>
    <div class="guide-progress" role="progressbar" aria-label="教程完成进度" :aria-valuenow="completed ? step.total : step.index" :aria-valuemax="step.total" :aria-valuemin="0"><i :style="{ width: `${(completed ? step.total : step.index) / step.total * 100}%` }"></i></div>
  </aside>
</template>

<script setup>
import { nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { ArrowRight, GraduationCap, LogOut, MousePointer2, Play, RotateCcw, ScanLine } from 'lucide-vue-next'

const props = defineProps({ step: { type: Object, required: true }, completed: Boolean, busy: Boolean })
const emit = defineEmits(['start', 'continue', 'exit', 'restart', 'locate', 'resize'])
const guide = ref(null)
const highlight = ref({ left: '50%', top: '40%', width: '0px', height: '0px' })
const highlightVisible = ref(false)
const travelling = ref(false)
const motionPreference = window.matchMedia('(prefers-reduced-motion: reduce)')
let observer
let target
let frame
let generation = 0
let lastGeometry = ''
let lastMovement = 0

function releaseTarget() {
  if (!target) return
  observer?.unobserve(target)
  target.removeAttribute('data-tutorial-active')
  const description = (target.getAttribute('aria-describedby') || '').split(' ').filter(id => id && id !== 'tutorial-instruction')
  if (description.length) target.setAttribute('aria-describedby', description.join(' '))
  else target.removeAttribute('aria-describedby')
}

function updatePosition() {
  if (!target?.isConnected || !target.getClientRects().length) return
  const rect = target.getBoundingClientRect()
  let top = Math.max(5, rect.top - 5)
  let bottom = Math.min((guide.value?.getBoundingClientRect().top ?? innerHeight) - 10, rect.bottom + 5)
  let left = Math.max(5, rect.left - 5)
  let right = Math.min(innerWidth - 5, rect.right + 5)
  for (let parent = target.parentElement; parent && parent !== document.body; parent = parent.parentElement) {
    const style = getComputedStyle(parent)
    const bounds = parent.getBoundingClientRect()
    if (/(auto|scroll|hidden|clip)/.test(style.overflowY)) { top = Math.max(top, bounds.top); bottom = Math.min(bottom, bounds.bottom) }
    if (/(auto|scroll|hidden|clip)/.test(style.overflowX)) { left = Math.max(left, bounds.left); right = Math.min(right, bounds.right) }
  }
  if (bottom <= top || right <= left) { if (!travelling.value) highlightVisible.value = false; return }
  const geometry = [left, top, right - left, bottom - top].map(value => Math.round(value * 2) / 2)
  const key = geometry.join(',')
  if (key !== lastGeometry) {
    lastGeometry = key
    lastMovement = performance.now()
    highlight.value = { left: `${geometry[0]}px`, top: `${geometry[1]}px`, width: `${geometry[2]}px`, height: `${geometry[3]}px` }
  }
  highlightVisible.value = true
}

function findTarget() {
  return [...document.querySelectorAll(`[data-tutorial="${props.step.target}"]`)]
    .find(element => element.getClientRects().length && getComputedStyle(element).visibility !== 'hidden')
}

async function focusTarget(recover = true) {
  const revision = ++generation
  cancelAnimationFrame(frame)
  travelling.value = !motionPreference.matches
  await nextTick()
  let next = findTarget()
  if (!next && recover) { emit('locate'); await nextTick(); next = findTarget() }
  if (revision !== generation) return
  releaseTarget()
  target = next
  if (!target) { travelling.value = false; highlightVisible.value = false; return }
  observer?.observe(target)
  target.setAttribute('data-tutorial-active', 'true')
  target.setAttribute('aria-describedby', `${target.getAttribute('aria-describedby') || ''} tutorial-instruction`.trim())
  target.scrollIntoView({ block: 'center', inline: 'nearest', behavior: motionPreference.matches ? 'instant' : 'smooth' })
  const started = performance.now()
  lastMovement = started
  let previousTop = target.getBoundingClientRect().top
  let lastTargetMovement = started
  let lastScrollAttempt = started
  function follow(now) {
    if (revision !== generation) return
    const bounds = target.getBoundingClientRect()
    const guideTop = guide.value.getBoundingClientRect().top
    const visible = bounds.top >= 5 && bounds.bottom <= guideTop - 8
    if (Math.abs(bounds.top - previousTop) > .5) { previousTop = bounds.top; lastTargetMovement = now }
    // Router scroll restoration can cancel the initial scroll after a resumed lesson mounts.
    if (!visible && now - lastTargetMovement > 160 && now - lastScrollAttempt > 220) {
      target.scrollIntoView({ block: 'center', inline: 'nearest', behavior: motionPreference.matches ? 'instant' : 'smooth' })
      lastScrollAttempt = now
    }
    updatePosition()
    if ((visible && (motionPreference.matches || (now - lastMovement > 460 && now - started > 460))) || now - started > 2200) {
      travelling.value = false
      return
    }
    frame = requestAnimationFrame(follow)
  }
  frame = requestAnimationFrame(follow)
}

async function locate() { emit('locate'); await focusTarget(false) }

// Keep unrelated controls from advancing a different phase while a lesson is focused.
function guardClick(event) {
  if (guide.value?.contains(event.target)) return
  if (!travelling.value && (props.step.mode !== 'read' || props.completed) && target?.contains(event.target)) return
  event.preventDefault()
  event.stopImmediatePropagation()
}

function guardTab(event) {
  if (event.key !== 'Tab') return
  const selector = 'button:not(:disabled),input:not(:disabled),select:not(:disabled),a[href]'
  const focusedControls = props.step.mode === 'read' && !props.completed ? [] : [target?.matches(selector) ? target : null, ...(target?.querySelectorAll(selector) || [])]
  const controls = [...focusedControls, ...guide.value.querySelectorAll(selector)]
    .filter(element => element?.getClientRects().length)
  if (!controls.length) return
  const index = controls.indexOf(document.activeElement)
  const next = index < 0 ? (event.shiftKey ? controls.length - 1 : 0)
    : event.shiftKey ? (index - 1 + controls.length) % controls.length : (index + 1) % controls.length
  event.preventDefault()
  controls[next].focus({ preventScroll: true })
}

watch(() => props.step.id, () => focusTarget(), { flush: 'post' })
onMounted(() => {
  observer = new ResizeObserver(() => {
    if (guide.value) emit('resize', Math.ceil(guide.value.getBoundingClientRect().height))
    updatePosition()
  })
  observer.observe(guide.value)
  window.addEventListener('resize', updatePosition)
  window.addEventListener('scroll', updatePosition, true)
  document.addEventListener('click', guardClick, true)
  document.addEventListener('keydown', guardTab, true)
  focusTarget()
})
onBeforeUnmount(() => {
  generation++
  releaseTarget()
  observer?.disconnect()
  cancelAnimationFrame(frame)
  window.removeEventListener('resize', updatePosition)
  window.removeEventListener('scroll', updatePosition, true)
  document.removeEventListener('click', guardClick, true)
  document.removeEventListener('keydown', guardTab, true)
})
</script>

<style scoped>
.tutorial-highlight{position:fixed;z-index:1600;pointer-events:none;border:2px solid #19a57b;border-radius:6px;box-shadow:0 0 0 9999px rgba(15,29,23,.55),inset 0 0 0 1px rgba(255,255,255,.8);opacity:0;transition:left .42s cubic-bezier(.22,.7,.25,1),top .42s cubic-bezier(.22,.7,.25,1),width .42s cubic-bezier(.22,.7,.25,1),height .42s cubic-bezier(.22,.7,.25,1),opacity .2s}.tutorial-highlight.visible{opacity:1}
.tutorial-guide{position:fixed;z-index:1800;bottom:0;left:0;right:0;background:#fff;color:#26332c;border-top:2px solid #168366;box-shadow:0 -4px 24px rgba(25,45,35,.12);padding-bottom:env(safe-area-inset-bottom)}
.guide-inner{max-width:1320px;margin:0 auto;display:flex;align-items:center;justify-content:space-between;gap:24px;padding:15px 24px 17px}.guide-content{flex:1;min-width:0;max-width:890px;min-height:86px}
.guide-meta{display:flex;align-items:center;flex-wrap:wrap;gap:8px;color:#69766f;font-size:11px}.guide-meta svg,.guide-meta strong{color:#15745a}.guide-meta strong{font-size:12px}.guide-meta span:last-child{border-left:1px solid #dce4df;padding-left:8px}
.guide-copy h2{font-size:17px;line-height:1.5;margin:5px 0 3px}.guide-copy p{font-size:13px;line-height:1.7;color:#57645d;overflow-wrap:anywhere}
.guide-actions{display:flex;align-items:center;gap:7px;flex-shrink:0}.guide-actions button{min-height:38px;display:inline-flex;align-items:center;justify-content:center;gap:6px;border-radius:5px;font-size:12px;white-space:nowrap}.guide-primary{padding:0 14px;background:#176d54;color:#fff}.guide-primary:disabled{opacity:.45;cursor:not-allowed}.guide-icon{width:38px;border:1px solid #dde4df;background:#fff;color:#65736b}.guide-actions button:focus-visible{outline:2px solid #1b7eab;outline-offset:3px}.guide-action-hint{display:flex;align-items:center;gap:5px;min-width:128px;color:#176d54;font-size:12px}
.guide-progress{height:3px;background:#e6eeea}.guide-progress i{display:block;height:100%;background:#168366;transition:width .35s}.hint-enter-active,.hint-leave-active{transition:opacity .12s,transform .12s}.hint-enter-from{opacity:0;transform:translateY(6px)}.hint-leave-to{opacity:0;transform:translateY(-6px)}
@media(max-width:768px){.guide-inner{padding:11px 14px 12px;display:block}.guide-content{min-height:98px}.guide-meta{gap:6px;font-size:10px}.guide-meta strong{font-size:11px}.guide-copy h2{font-size:15px;margin-top:4px}.guide-copy p{font-size:12px;line-height:1.6}.guide-actions{margin-top:9px}.guide-actions button{min-height:34px}.guide-icon{width:34px}.guide-meta span:last-child{margin-left:auto;border:0;padding:0}.guide-action-hint{min-height:34px;flex:1}.guide-meta strong{white-space:nowrap}}
@media(prefers-reduced-motion:reduce){.tutorial-highlight,.guide-progress i,.hint-enter-active,.hint-leave-active{transition:none}.hint-enter-from,.hint-leave-to{transform:none}}
</style>
