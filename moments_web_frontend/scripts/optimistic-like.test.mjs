import assert from 'node:assert/strict'
import test from 'node:test'
import { effect } from 'vue'
import { optimisticLike, preservePendingLike } from '../src/services/optimisticLike.js'

test('likes update immediately and rapid duplicate clicks send only one request', async () => {
  const target = { id: 1, likedByMe: false, likeCount: 2 }
  let resolve
  let calls = 0
  const send = () => { calls++; return new Promise(done => { resolve = done }) }
  const request = optimisticLike(target, send)
  assert.equal(target.likedByMe, true)
  assert.equal(target.likeCount, 3)
  assert.equal(target.likePending, true)
  await optimisticLike(target, send)
  assert.equal(calls, 1)
  resolve({ likedByMe: true, likeCount: 5 })
  await request
  assert.equal(target.likeCount, 5)
  assert.equal(target.likePending, false)
})

test('failed request restores previous like state and unlocks retries', async () => {
  const target = { id: 2, likedByMe: true, likeCount: 3 }
  await assert.rejects(optimisticLike(target, async () => { throw new Error('offline') }))
  assert.equal(target.likedByMe, true)
  assert.equal(target.likeCount, 3)
  assert.equal(target.likePending, false)
  await optimisticLike(target, async () => ({ likedByMe: false, likeCount: 2 }))
  assert.equal(target.likeCount, 2)
})

test('WebSocket refresh preserves pending state and receives final confirmation', async () => {
  const target = { id: 3, likedByMe: false, likeCount: 1, commentCount: 2 }
  let resolve
  const request = optimisticLike(target, () => new Promise(done => { resolve = done }))
  const refreshed = preservePendingLike({ id: 3, likedByMe: false, likeCount: 1, commentCount: 7 })
  const pendingStates = []
  effect(() => pendingStates.push(refreshed.likePending))
  assert.equal(refreshed.likedByMe, true)
  assert.equal(refreshed.likeCount, 2)
  resolve({ likedByMe: true, likeCount: 4, commentCount: 2 })
  await request
  assert.equal(refreshed.likeCount, 4)
  assert.equal(refreshed.commentCount, 7)
  assert.equal(refreshed.likePending, false)
  assert.deepEqual(pendingStates, [true, false])
})

test('different items and comments have independent in-flight locks', async () => {
  const moment = { id: 4, likedByMe: true, likeCount: 0 }
  const comment = { id: 4, likedByMe: false, likeCount: 1 }
  await Promise.all([
    optimisticLike(moment, async () => null),
    optimisticLike(comment, async () => null, 'comment:4')
  ])
  assert.equal(moment.likeCount, 0)
  assert.equal(comment.likeCount, 2)
})
