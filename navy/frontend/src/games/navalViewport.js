export function navalViewportState(win = window, doc = document) {
  const ios = /iPhone|iPad|iPod/.test(win.navigator.userAgent) || win.navigator.platform === 'MacIntel' && win.navigator.maxTouchPoints > 1
  const native = Boolean(doc.fullscreenElement || doc.webkitFullscreenElement)
  const standalone = Boolean(win.navigator.standalone || win.matchMedia('(display-mode: standalone)').matches || win.matchMedia('(display-mode: fullscreen)').matches)
  const supported = Boolean((doc.fullscreenEnabled !== false && doc.documentElement.requestFullscreen) || doc.webkitFullscreenEnabled && doc.documentElement.webkitRequestFullscreen)
  const width = win.visualViewport?.width || win.innerWidth, height = win.visualViewport?.height || win.innerHeight
  const orientation = win.screen?.orientation?.type
  const landscape = orientation ? orientation.startsWith('landscape') : win.innerWidth > win.innerHeight
  return { ios, native, standalone, iosFallback: ios && !supported && !standalone, landscape, width, height, top: win.visualViewport?.offsetTop || 0, left: win.visualViewport?.offsetLeft || 0 }
}

export function installNavyVisualViewport(win = window, doc = document) {
  const names = ['top', 'left', 'width', 'height', 'bottom']
  const style = doc.documentElement.style
  const previous = Object.fromEntries(names.map(name => [name, style.getPropertyValue(`--navy-visual-${name}`)]))
  let frame, disposed = false
  function update() {
    frame = null
    if (disposed) return
    const viewport = navalViewportState(win, doc)
    for (const name of names) style.setProperty(`--navy-visual-${name}`, `${name === 'bottom' ? viewport.top + viewport.height : viewport[name]}px`)
  }
  function schedule() { if (frame == null) frame = win.requestAnimationFrame(update) }
  update()
  win.visualViewport?.addEventListener('resize', schedule)
  win.visualViewport?.addEventListener('scroll', schedule)
  win.addEventListener('resize', schedule)
  win.addEventListener('orientationchange', schedule)
  return () => {
    disposed = true
    if (frame != null) win.cancelAnimationFrame(frame)
    win.visualViewport?.removeEventListener('resize', schedule)
    win.visualViewport?.removeEventListener('scroll', schedule)
    win.removeEventListener('resize', schedule)
    win.removeEventListener('orientationchange', schedule)
    for (const name of names) {
      const key = `--navy-visual-${name}`
      if (previous[name]) style.setProperty(key, previous[name]); else style.removeProperty(key)
    }
  }
}
