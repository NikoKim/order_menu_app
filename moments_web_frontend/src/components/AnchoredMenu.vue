<template>
  <Teleport to="body">
    <Transition name="anchored-menu">
      <div
        v-if="open"
        ref="surface"
        class="anchored-menu-surface"
        :style="position"
        role="menu"
        @click.stop
      >
        <slot />
      </div>
    </Transition>
  </Teleport>
</template>

<script setup>
import { nextTick, onBeforeUnmount, ref, watch } from 'vue'

const props = defineProps({
  open: { type: Boolean, default: false },
  anchor: { type: Object, default: null },
  gap: { type: Number, default: 6 }
})
const emit = defineEmits(['close'])

const surface = ref(null)
const position = ref({ visibility: 'hidden', top: '0px', left: '0px' })
let frame = 0

function schedulePosition() {
  cancelAnimationFrame(frame)
  frame = requestAnimationFrame(updatePosition)
}

function handleOutsidePointer(event) {
  if (!props.open) return
  if (surface.value?.contains(event.target) || props.anchor?.contains?.(event.target)) return
  emit('close')
}

function handleScroll() {
  if (props.open) emit('close')
}

async function updatePosition() {
  if (!props.open || !props.anchor?.getBoundingClientRect) return
  await nextTick()
  const menu = surface.value
  if (!menu) return

  const margin = 8
  const anchor = props.anchor.getBoundingClientRect()
  const width = menu.offsetWidth
  const height = menu.offsetHeight

  let left = anchor.right - width
  if (left < margin) left = anchor.left
  if (left + width > window.innerWidth - margin) left = anchor.right - width
  left = Math.max(margin, Math.min(left, window.innerWidth - width - margin))

  let top = anchor.bottom + props.gap
  if (top + height > window.innerHeight - margin) top = anchor.top - height - props.gap
  top = Math.max(margin, Math.min(top, window.innerHeight - height - margin))

  position.value = { visibility: 'visible', top: `${Math.round(top)}px`, left: `${Math.round(left)}px` }
}

watch(() => [props.open, props.anchor], ([open]) => {
  if (!open) return
  position.value = { visibility: 'hidden', top: '0px', left: '0px' }
  schedulePosition()
}, { flush: 'post' })

document.addEventListener('pointerdown', handleOutsidePointer, true)
document.addEventListener('scroll', handleScroll, true)
window.addEventListener('resize', schedulePosition)
onBeforeUnmount(() => {
  cancelAnimationFrame(frame)
  document.removeEventListener('pointerdown', handleOutsidePointer, true)
  document.removeEventListener('scroll', handleScroll, true)
  window.removeEventListener('resize', schedulePosition)
})
</script>

<style scoped>
.anchored-menu-surface { position: fixed; z-index: 499; width: max-content; max-width: calc(100vw - 16px); }
.anchored-menu-enter-active { transition: opacity .14s ease, transform .14s ease; }
.anchored-menu-leave-active { transition: none; }
.anchored-menu-enter-from, .anchored-menu-leave-to { opacity: 0; transform: translateY(-3px) scale(.97); }
</style>
