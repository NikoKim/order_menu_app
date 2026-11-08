import { onMounted, onUnmounted, shallowRef } from 'vue'

export function useFullscreenTarget() {
  const current = () => document.fullscreenElement || document.webkitFullscreenElement || 'body'
  const target = shallowRef(current())
  const update = () => { target.value = current() }
  onMounted(() => {
    update()
    document.addEventListener('fullscreenchange', update)
    document.addEventListener('webkitfullscreenchange', update)
  })
  onUnmounted(() => {
    document.removeEventListener('fullscreenchange', update)
    document.removeEventListener('webkitfullscreenchange', update)
  })
  return target
}
