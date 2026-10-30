import { reactive } from 'vue'

const pendingLikes = new Map()

export function preservePendingLike(target, key = `moment:${target.id}`) {
  target = reactive(target)
  const pending = pendingLikes.get(key)
  if (pending) {
    Object.assign(target, pending.optimistic, { likePending: true })
    pending.targets.add(target)
  }
  return target
}

export async function optimisticLike(target, send, key = `moment:${target.id}`) {
  if (pendingLikes.has(key)) return
  const previous = { likedByMe: Boolean(target.likedByMe), likeCount: Number(target.likeCount) || 0 }
  const optimistic = {
    likedByMe: !previous.likedByMe,
    likeCount: Math.max(0, previous.likeCount + (previous.likedByMe ? -1 : 1))
  }
  const pending = { targets: new Set([target]), optimistic }
  pendingLikes.set(key, pending)
  Object.assign(target, optimistic, { likePending: true })
  try {
    const result = await send(optimistic.likedByMe)
    const confirmed = {
      likedByMe: result?.likedByMe ?? optimistic.likedByMe,
      likeCount: result?.likeCount ?? optimistic.likeCount
    }
    pending.targets.forEach(item => Object.assign(item, confirmed))
  } catch (error) {
    pending.targets.forEach(item => Object.assign(item, previous))
    throw error
  } finally {
    pending.targets.forEach(item => { item.likePending = false })
    pendingLikes.delete(key)
  }
}
