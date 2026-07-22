import { BACKEND_ORIGIN } from '../config/endpoints'

const listeners = new Map()
let socket = null
let reconnectTimer = null
let heartbeatTimer = null
let retryCount = 0
let manuallyClosed = false

export function connectCustomerWebSocket() {
  const token = localStorage.getItem('token')
  if (!token || socket?.readyState === WebSocket.OPEN || socket?.readyState === WebSocket.CONNECTING) return
  manuallyClosed = false
  const origin = BACKEND_ORIGIN || window.location.origin
  const url = new URL('/ws/customer', origin)
  url.protocol = url.protocol === 'https:' ? 'wss:' : 'ws:'
  url.searchParams.set('token', token)

  socket = new WebSocket(url)
  socket.addEventListener('open', () => {
    retryCount = 0
    emit('CONNECTION', { module: 'CONNECTION', action: 'CONNECTED', data: {} })
    window.clearInterval(heartbeatTimer)
    heartbeatTimer = window.setInterval(() => send('SYSTEM', 'PING'), 25000)
  })
  socket.addEventListener('message', event => {
    try {
      const message = JSON.parse(event.data)
      if (message.module === 'SYSTEM' && message.action === 'PONG') return
      if (message.module === 'SYSTEM' && message.action === 'AUTH_EXPIRED') {
        disconnectCustomerWebSocket()
        return
      }
      emit(message.module, message)
    } catch { }
  })
  socket.addEventListener('close', handleDisconnect)
  socket.addEventListener('error', () => socket?.close())
}

export function disconnectCustomerWebSocket() {
  manuallyClosed = true
  window.clearTimeout(reconnectTimer)
  window.clearInterval(heartbeatTimer)
  const current = socket
  socket = null
  current?.close()
}

export function subscribeCustomerWebSocket(module, listener) {
  if (!listeners.has(module)) listeners.set(module, new Set())
  listeners.get(module).add(listener)
  return () => listeners.get(module)?.delete(listener)
}

function send(module, action, data = {}) {
  if (socket?.readyState !== WebSocket.OPEN) return
  socket.send(JSON.stringify({ module, action, data }))
}

function handleDisconnect() {
  window.clearInterval(heartbeatTimer)
  socket = null
  emit('CONNECTION', { module: 'CONNECTION', action: 'DISCONNECTED', data: {} })
  if (manuallyClosed || !localStorage.getItem('token')) return
  window.clearTimeout(reconnectTimer)
  const delay = Math.min(15000, 3000 + retryCount * 2000)
  retryCount += 1
  reconnectTimer = window.setTimeout(connectCustomerWebSocket, delay)
}

function emit(module, message) {
  listeners.get(module)?.forEach(listener => listener(message))
}

window.addEventListener('online', connectCustomerWebSocket)
