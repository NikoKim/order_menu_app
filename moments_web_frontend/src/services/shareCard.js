import { notifyError, notifySuccess } from './notice'
import { BACKEND_ORIGIN } from '../config/endpoints'

const SHARE_ORIGIN = BACKEND_ORIGIN || 'https://order-menu-backend.zsj-unknown.top'

function shareUrl(moment, comment) {
  const url = new URL(`/api/moments/${moment.id}/share-card`, SHARE_ORIGIN)
  if (comment?.id) url.searchParams.set('commentId', comment.id)
  url.searchParams.set('v', shareVersion(moment))
  return url.toString()
}

function shareVersion(moment) {
  const source = [moment.title, moment.content, moment.imageUrls?.[0], moment.editedAt, moment.publishedAt].join('|')
  let hash = 0
  for (let index = 0; index < source.length; index += 1) {
    hash = ((hash << 5) - hash + source.charCodeAt(index)) | 0
  }
  return `q2-${Math.abs(hash).toString(36)}`
}

async function copyLink(url) {
  if (navigator.clipboard?.writeText) {
    await navigator.clipboard.writeText(url)
    return
  }
  const input = document.createElement('textarea')
  input.value = url
  input.style.position = 'fixed'
  input.style.opacity = '0'
  document.body.appendChild(input)
  input.select()
  document.execCommand('copy')
  input.remove()
}

export async function shareMomentExternally(moment, comment = null) {
  const url = shareUrl(moment, comment)
  const title = moment.title || '动态分享'
  const rawDescription = String(comment?.content || moment.content || '点击查看动态详情').replace(/\s+/g, ' ').trim()
  const description = rawDescription.length > 180 ? `${rawDescription.slice(0, 180)}...` : rawDescription
  try {
    if (navigator.share) {
      // QQ reads the first two lines of Web Share text as card title and summary.
      await navigator.share({ title, text: `${title}\n${description}`, url })
      return
    }
    await copyLink(url)
    notifySuccess('动态链接已复制，发送后可点击卡片进入详情')
  } catch (error) {
    if (error?.name !== 'AbortError') notifyError(error?.message || '分享失败，请稍后重试')
  }
}
