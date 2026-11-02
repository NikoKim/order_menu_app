export function navalViewportState(win = window, doc = document) {
  const ios = /iPhone|iPad|iPod/.test(win.navigator.userAgent) || win.navigator.platform === 'MacIntel' && win.navigator.maxTouchPoints > 1
  const native = Boolean(doc.fullscreenElement || doc.webkitFullscreenElement)
  const standalone = Boolean(win.navigator.standalone || win.matchMedia('(display-mode: standalone)').matches || win.matchMedia('(display-mode: fullscreen)').matches)
  const supported = Boolean((doc.fullscreenEnabled !== false && doc.documentElement.requestFullscreen) || doc.webkitFullscreenEnabled && doc.documentElement.webkitRequestFullscreen)
  const width = native ? win.innerWidth : win.visualViewport?.width || win.innerWidth, height = native ? win.innerHeight : win.visualViewport?.height || win.innerHeight
  return { ios, native, standalone, iosFallback: ios && !supported && !standalone, landscape: width > height, width, height, top: native ? 0 : win.visualViewport?.offsetTop || 0, left: native ? 0 : win.visualViewport?.offsetLeft || 0 }
}
