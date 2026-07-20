const BASE = 'https://order-menu-backend.zsj-unknown.top'

export function imageUrl(path) {
  if (!path) return ''
  if (path.startsWith('http://') || path.startsWith('https://')) return path
  return BASE + (path.startsWith('/') ? path : '/' + path)
}
