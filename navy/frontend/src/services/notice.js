const listeners = new Set()

function emit(event) {
  listeners.forEach(listener => listener(event))
}

export function subscribeNotice(listener) {
  listeners.add(listener)
  return () => listeners.delete(listener)
}

export function notify(message, type = 'error', duration = 3000) {
  if (!message) return
  emit({ kind: 'toast', message: String(message), type, duration })
}

export const notifyError = message => notify(message, 'error')
export const notifySuccess = message => notify(message, 'success')

export function confirmNotice(message, options = {}) {
  return new Promise(resolve => {
    emit({
      kind: 'confirm',
      message: String(message),
      title: options.title || '请确认',
      confirmText: options.confirmText || '确认',
      cancelText: options.cancelText || '取消',
      danger: options.danger !== false,
      resolve
    })
  })
}
