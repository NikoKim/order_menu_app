export function formatMomentTime(value) {
  if (!value) return ''
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return ''

  const now = new Date()
  const diff = Math.max(0, now.getTime() - date.getTime())
  if (diff < 60000) return '刚刚'
  if (diff < 3600000) return `${Math.floor(diff / 60000)}分钟前`
  if (diff < 86400000) return `${Math.floor(diff / 3600000)}小时前`

  const today = new Date(now.getFullYear(), now.getMonth(), now.getDate())
  const yesterday = new Date(today)
  yesterday.setDate(today.getDate() - 1)
  if (date >= yesterday && date < today) {
    return `昨天 ${two(date.getHours())}:${two(date.getMinutes())}`
  }
  if (date.getFullYear() === now.getFullYear()) {
    return `${two(date.getMonth() + 1)}-${two(date.getDate())} ${two(date.getHours())}:${two(date.getMinutes())}`
  }
  return `${date.getFullYear()}-${two(date.getMonth() + 1)}-${two(date.getDate())} ${two(date.getHours())}:${two(date.getMinutes())}`
}

export function hasMeaningfulMomentEdit(moment) {
  if (!moment?.editedAt || moment.status !== 'PUBLISHED') return false
  const editedAt = new Date(moment.editedAt)
  const publishedAt = new Date(moment.publishedAt || moment.createdAt)
  if (Number.isNaN(editedAt.getTime())) return false
  if (Number.isNaN(publishedAt.getTime())) return true

  // Legacy clients uploaded images through an immediate follow-up update.
  return editedAt.getTime() - publishedAt.getTime() > 30000
}

function two(value) {
  return String(value).padStart(2, '0')
}
