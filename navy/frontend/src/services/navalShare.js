import { BACKEND_ORIGIN } from '../config/endpoints'

const COVER = '/naval/fleet/harbor.png'

function roomCode(room) {
  const code = String(room?.inviteCode || '').trim().toUpperCase()
  if (!/^[A-Z2-9]{6}$/.test(code)) throw new Error('当前房间没有有效的邀请码')
  return code
}

function pageOrigin() {
  return new URL(window.location.origin).origin
}

export function buildNavalRoomUrl(room) {
  const url = new URL('/', pageOrigin())
  url.searchParams.set('invite', roomCode(room))
  return url.toString()
}

export function buildNavalShareUrl(room) {
  const url = new URL(`/api/naval/share/${roomCode(room)}`, BACKEND_ORIGIN || pageOrigin())
  url.searchParams.set('frontendOrigin', pageOrigin())
  return url.toString()
}

export function navalShareDetails(room) {
  const players = (room?.players || []).filter(player => player && !player.left)
  const names = players.map(player => String(player.name || '指挥官')).slice(0, 2)
  const host = names[0] || '指挥官'
  const rules = room?.ruleSet === 'MILITARY' ? '军事模式' : '经典模式'
  const title = `海战棋 · ${rules} · ${host}的房间`
  const occupied = names.length
  const phaseText = room?.phase === 'BATTLE' ? '对局进行中，满员后可进入观众席'
    : room?.phase === 'FINISHED' ? '本局已结束，可观战或等待下一局'
      : occupied < 2 ? '双人海战，等待另一位指挥官加入' : '舰队集结中，可进入观众席'
  return {
    title, description: `${names.join(' 对阵 ') || '舰队指挥部'} · ${phaseText}`,
    image: new URL(room?.ruleSet === 'MILITARY' ? '/naval/military/harbor.png' : COVER, pageOrigin()).toString(),
    inviteCode: roomCode(room), roomUrl: buildNavalRoomUrl(room), url: buildNavalShareUrl(room)
  }
}

export async function copyNavalShareLink(room) {
  const url = buildNavalShareUrl(room)
  if (navigator.clipboard?.writeText) {
    try { await navigator.clipboard.writeText(url); return url } catch { /* Insecure LAN previews may need the selection fallback. */ }
  }
  const input = document.createElement('textarea')
  input.value = url; input.readOnly = true
  input.style.position = 'fixed'; input.style.opacity = '0'; input.style.pointerEvents = 'none'
  const parent = document.fullscreenElement || document.webkitFullscreenElement || document.body
  const previous = document.activeElement
  parent.appendChild(input); input.focus(); input.select(); input.setSelectionRange(0, input.value.length)
  try {
    if (!document.execCommand('copy')) throw new Error('未能复制，请长按房间链接复制')
  } finally { input.remove(); previous?.focus?.({ preventScroll: true }) }
  return url
}

export function buildNavalQqShareUrl(room) {
  const details = navalShareDetails(room)
  const url = new URL('https://connect.qq.com/widget/shareqq/index.html')
  url.searchParams.set('url', details.url)
  url.searchParams.set('title', details.title)
  url.searchParams.set('summary', details.description)
  url.searchParams.set('pics', details.image)
  return url.toString()
}

export async function shareNavalRoom(room, { platform = 'system' } = {}) {
  const details = navalShareDetails(room)
  // Web Share hands text/URL MIME payloads to an OS target; it cannot select or
  // verify WeChat delivery. Use a link paste for the explicit WeChat command.
  if (platform === 'copy' || platform === 'wechat') {
    await copyNavalShareLink(room)
    return { status: 'copied', message: platform === 'wechat' ? '邀请链接已复制，请在微信聊天中粘贴发送；尚未自动发送' : '房间链接已复制，好友打开后可直接进入房间' }
  }
  if (navigator.share) {
    try {
      await navigator.share({ title: details.title, text: `${details.title}\n${details.description}`, url: details.url })
      return { status: 'shared', message: '已交给系统分享，请在分享面板中选择接收人' }
    } catch (error) {
      if (error?.name === 'AbortError') return { status: 'cancelled', message: '' }
    }
  }
  if (platform === 'qq') {
    const popup = window.open(buildNavalQqShareUrl(room), '_blank', 'noopener,noreferrer')
    if (popup) return { status: 'opened', message: '已打开 QQ 分享页，请选择好友或群聊' }
    // Some browsers return null with noopener even after successfully opening the window.
  }
  await copyNavalShareLink(room)
  return { status: 'copied', message: platform === 'wechat' ? '链接已复制，请粘贴到微信发送；好友点开即可进入房间' : platform === 'qq' ? '房间链接已复制，也可直接粘贴到 QQ 发送' : '房间链接已复制，可发送到微信或 QQ' }
}
