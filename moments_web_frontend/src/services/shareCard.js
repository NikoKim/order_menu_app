import { notifyError, notifySuccess } from './notice'
import { BACKEND_ORIGIN } from '../config/endpoints'
import { recordExternalMomentShare } from '../api/moments'

const SHARE_ORIGIN = BACKEND_ORIGIN || 'https://order-menu-backend.zsj-unknown.top'

export function buildMomentShareUrl(moment, comment = null, options = {}) {
  const url = new URL(`/api/moments/${moment.id}/share-card`, SHARE_ORIGIN)
  if (comment?.id) url.searchParams.set('commentId', comment.id)
  url.searchParams.set('mode', options.mode === 'isolated' ? 'isolated' : 'browse')
  url.searchParams.set('v', shareVersion(moment))
  return url.toString()
}

function shareVersion(moment) {
  const source = [moment.title, moment.content, moment.imageUrls?.[0], moment.editedAt, moment.publishedAt].join('|')
  let hash = 0
  for (let index = 0; index < source.length; index += 1) {
    hash = ((hash << 5) - hash + source.charCodeAt(index)) | 0
  }
  return `q9-${Math.abs(hash).toString(36)}`
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

export async function shareMomentExternally(moment, comment = null, options = {}) {
  const url = buildMomentShareUrl(moment, comment, options)
  const title = moment.title || '动态分享'
  const rawDescription = String(comment?.content || moment.content || '点击查看动态详情').replace(/\s+/g, ' ').trim()
  const description = rawDescription.length > 180 ? `${rawDescription.slice(0, 180)}...` : rawDescription
  try {
    if (navigator.share) {
      // QQ reads the first two lines of Web Share text as card title and summary.
      await navigator.share({ title, text: `${title}\n${description}`, url })
      try {
        const stats = await recordExternalMomentShare(moment.id, comment?.id || null)
        moment.shareCount = Number(stats?.shareCount ?? moment.shareCount ?? 0)
        moment.appShareCount = Number(stats?.appShareCount ?? moment.appShareCount ?? 0)
        moment.externalShareCount = Number(stats?.externalShareCount ?? moment.externalShareCount ?? 0)
      } catch {
        notifyError('已完成分享，但分享数暂未同步')
      }
      return true
    }
    await copyLink(url)
    notifySuccess('动态链接已复制；实际发送无法由浏览器确认，因此暂不计入分享数')
    return false
  } catch (error) {
    if (error?.name !== 'AbortError') notifyError(error?.message || '分享失败，请稍后重试')
    return false
  }
}
