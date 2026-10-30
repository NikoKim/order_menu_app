import assert from 'node:assert/strict'
import { mkdir } from 'node:fs/promises'
import path from 'node:path'
import { chromium } from 'playwright-core'

const base = process.env.QA_BASE_URL || 'http://127.0.0.1:5175'
const output = path.resolve(process.env.QA_OUTPUT_DIR || 'test-results/social-feedback')
await mkdir(output, { recursive: true })
const browser = await chromium.launch({ executablePath: 'C:/Program Files/Google/Chrome/Application/chrome.exe', headless: true })
const user = { id: 7, nickname: '测试玩家', role: 'CUSTOMER', avatarUrl: null }
const moment = { id: 1, title: '周末的一点小记录', content: '分享生活里的美好时刻，愿今天也有值得记录的小事。', author: { id: 8, nickname: '小鲨鱼', role: 'CUSTOMER' }, publishedAt: '2026-10-22T10:00:00', status: 'PUBLISHED', visibility: 'PUBLIC', imageUrls: [], topics: [], reactions: [], likedByMe: false, likeCount: 9, commentCount: 0, hotComment: null }
const game = { game: { id: 12, title: '市场交易挑战', description: '在虚拟市场中完成交易，体验不同的市场变化。', versionLabel: 'v18', visibility: 'PUBLIC', creator: { id: 8, nickname: '小鲨鱼' } }, config: { stockCode: 'TEST', stockType: '科技', initialPrice: 100, initialCash: 100000, roundCount: 20, tradingSeconds: 30, aiEnabled: false, aiCount: 0, baseVolatilityPercent: 2, maxOrderImpactPercent: 3, maxBuyQuantity: 1000, events: [], pricingEngineVersion: 'V2', progressionMode: 'MANUAL' } }
const state = { likes: 0, comments: 0, commentReads: 0, messages: 0, reviews: 0, canReview: false, review: null, failLike: false, failComment: false }
const delay = ms => new Promise(resolve => setTimeout(resolve, ms))
const pageErrors = []

async function mock(page, authenticated = true) {
  page.on('pageerror', error => pageErrors.push(error.message))
  if (authenticated) await page.addInitScript(value => {
    localStorage.setItem('token', 'qa-token')
    localStorage.setItem('user', JSON.stringify(value))
  }, user)
  await page.route('**/api/**', async route => {
    const pathname = new URL(route.request().url()).pathname
    if (!pathname.startsWith('/api/')) return route.continue()
    const method = route.request().method()
    let data = {}
    let code = 0
    if (pathname === '/api/moments') data = { records: Array.from({ length: 3 }, (_, index) => ({ ...moment, id: index + 1 })), total: 3, hasMore: false }
    else if (pathname === '/api/moments/1/like') {
      state.likes++
      await delay(1200)
      code = state.failLike ? 400 : 0
      if (!code) { moment.likedByMe = route.request().postDataJSON().active; moment.likeCount += moment.likedByMe ? 1 : -1 }
      data = moment
    } else if (pathname === '/api/moments/1/comments' && method === 'POST') {
      state.comments++
      const body = route.request().postDataJSON()
      await delay(1200)
      code = state.failComment ? 400 : 0
      data = { id: 101, momentId: 1, author: user, ...body, createdAt: '2026-10-22T11:00:00', replies: [], likeCount: 0, replyCount: 0 }
    } else if (pathname === '/api/moments/1/comments') { state.commentReads++; data = { records: [], total: 0, hasMore: false } }
    else if (pathname === '/api/moments/1') data = moment
    else if (pathname.endsWith('/moments/unread-count')) data = { relatedUnreadCount: 0 }
    else if (pathname === '/api/messages/conversations/1/messages') {
      if (method === 'POST') {
        state.messages++
        const body = route.request().postDataJSON()
        await delay(1200)
        data = { id: 20, type: 'TEXT', senderUserId: 7, senderNickname: user.nickname, content: body.content, createdAt: '2026-10-22T11:00:00' }
      } else data = { conversation: { id: 1, isDirect: true, type: 'DIRECT', directUserId: 8, directUserNickname: '好友' }, messages: [{ id: 10, type: 'TEXT', senderUserId: 8, senderNickname: '好友', content: '你好', createdAt: '2026-10-22T10:00:00' }], hasMore: false }
    } else if (pathname === '/api/messages/conversations') data = []
    else if (pathname.endsWith('/typing')) data = { userIds: [] }
    else if (pathname === '/api/entertainment/games/12') data = game
    else if (pathname.endsWith('/leaderboard')) data = { versionLabel: 'v18', entries: [{ rank: 1, sessionId: 1, displayName: '游客-121D', returnPercent: -7.01, finalAsset: 92989.39 }] }
    else if (pathname.endsWith('/reviews/me')) {
      state.reviews++
      await delay(800)
      if (method === 'DELETE') { state.review = null; data = null }
      else {
        state.review = { id: 51, gameId: 12, author: user, ...route.request().postDataJSON(), mine: true, createdAt: '2026-10-22T11:00:00', updatedAt: '2026-10-22T11:00:00' }
        data = state.review
      }
    } else if (pathname.endsWith('/reviews')) {
      const authenticated = Boolean(route.request().headers().authorization)
      data = { records: state.review ? [state.review] : [], total: state.review ? 1 : 0, averageRating: state.review?.rating || 0, canReview: state.canReview && authenticated, myReview: authenticated ? state.review : null }
    } else if (pathname.endsWith('/ai-default')) data = { configured: false }
    else if (pathname.endsWith('/guests')) data = { token: 'qa-guest', displayName: '游客' }
    else if (pathname.includes('unread')) data = { unreadCount: 0, totalUnreadCount: 0 }
    await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ code, message: code ? '测试请求失败' : 'ok', data }) })
  })
}

async function noOverflow(page) {
  assert.equal(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth + 1), true, 'horizontal overflow')
}

try {
  const desktop = await browser.newPage({ viewport: { width: 1520, height: 1000 } })
  await mock(desktop)
  await desktop.goto(`${base}/customer/moments`)
  const card = desktop.locator('.feed-list .moment-card').first()
  await card.waitFor()
  const gutters = await card.evaluate(el => {
    const parent = el.parentElement.getBoundingClientRect()
    const rect = el.getBoundingClientRect()
    return { left: rect.left - parent.left, right: parent.right - rect.right }
  })
  assert(gutters.left >= 24 && gutters.right >= 24, JSON.stringify(gutters))
  await card.getByRole('button', { name: '点赞', exact: true }).click()
  await desktop.waitForFunction(() => document.querySelector('.mc-act-btn.active .mc-act-num')?.textContent === '10', null, { timeout: 700 })
  assert.equal(await card.locator('.mc-act-btn.active .mc-act-num').textContent(), '10')
  assert.equal(await card.getByRole('button', { name: '取消点赞', exact: true }).isDisabled(), true)
  await card.locator('.mc-act-btn').first().evaluate(el => { for (let i = 0; i < 5; i++) el.click() })
  await desktop.waitForFunction(() => !document.querySelector('.mc-act-btn[aria-busy="true"]'))
  assert.equal(state.likes, 1)
  await noOverflow(desktop)
  await desktop.screenshot({ path: path.join(output, 'feed-desktop.png') })
  state.failLike = true
  await card.getByRole('button', { name: '取消点赞', exact: true }).click()
  assert.equal(await card.getByRole('button', { name: '点赞', exact: true }).isDisabled(), true)
  await card.getByRole('button', { name: '取消点赞', exact: true }).waitFor()
  await desktop.waitForFunction(() => document.querySelector('.mc-act-btn.active .mc-act-num')?.textContent === '10')
  assert.equal(await card.locator('.mc-act-btn.active .mc-act-num').textContent(), '10')
  state.failLike = false

  await desktop.goto(`${base}/customer/moments/1`)
  const comment = desktop.locator('.comment-text-input')
  await comment.fill('延迟请求只发送一条评论')
  await desktop.locator('.comment-send-btn').click()
  await comment.evaluate(el => { for (let i = 0; i < 5; i++) el.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter', bubbles: true })) })
  assert.equal(await desktop.locator('.comment-send-btn').textContent(), '发送中')
  const reads = state.commentReads
  await desktop.getByText('延迟请求只发送一条评论', { exact: true }).waitFor()
  assert.equal(state.comments, 1)
  assert.equal(state.commentReads, reads, 'successful comment should not require another list fetch')
  assert.equal(await comment.inputValue(), '')
  state.failComment = true
  await comment.fill('失败后保留草稿')
  await desktop.locator('.comment-send-btn').click()
  await desktop.locator('.comment-send-btn:not([aria-busy="true"])').waitFor()
  assert.equal(await comment.inputValue(), '失败后保留草稿')

  await desktop.goto(`${base}/customer/chat/1`)
  const input = desktop.locator('.chat-text-input')
  await input.fill('只发送一次的消息')
  await desktop.locator('.chat-send-btn').click()
  await input.evaluate(el => { for (let i = 0; i < 5; i++) el.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter', bubbles: true })) })
  assert.equal(await desktop.locator('.chat-send-btn').getAttribute('aria-busy'), 'true')
  await input.fill('等待期间编辑的新草稿')
  await desktop.getByText('只发送一次的消息', { exact: true }).waitFor()
  assert.equal(state.messages, 1)
  assert.equal(await input.inputValue(), '等待期间编辑的新草稿')

  await desktop.goto(`${base}/customer/entertainment/games/12`)
  await desktop.getByText('玩过这个游戏后即可评论和评分', { exact: true }).waitFor()
  assert.equal(await desktop.locator('.review-form').count(), 0)
  state.canReview = true
  await desktop.reload()
  const reviewInput = desktop.getByRole('textbox', { name: '游戏评论' })
  await reviewInput.fill('交易节奏很舒服，市场事件也很有趣。')
  await desktop.locator('.rating-input label').nth(3).click()
  const submit = desktop.getByRole('button', { name: '发表评价', exact: true })
  await submit.click()
  assert.equal(await desktop.getByRole('button', { name: '提交中', exact: true }).isDisabled(), true)
  await desktop.getByRole('button', { name: '更新评价', exact: true }).waitFor()
  assert.equal(state.reviews, 1)
  await desktop.locator('.rating-input label').nth(4).click()
  await desktop.getByRole('button', { name: '更新评价', exact: true }).click()
  await desktop.locator('.rating-summary > strong').filter({ hasText: '5.0' }).waitFor()
  assert.equal(state.reviews, 2)
  await noOverflow(desktop)
  await desktop.screenshot({ path: path.join(output, 'game-desktop.png'), fullPage: true })

  const mobile = await browser.newPage({ viewport: { width: 390, height: 844 }, isMobile: true, hasTouch: true })
  await mock(mobile)
  await mobile.goto(`${base}/customer/moments`)
  await mobile.locator('.moment-card').first().waitFor()
  const mobileCard = await mobile.locator('.feed-list .moment-card').first().evaluate(el => {
    const rect = el.getBoundingClientRect()
    const style = getComputedStyle(el)
    return { left: rect.left, right: rect.right, radius: style.borderRadius, gap: style.marginBottom }
  })
  assert.deepEqual(mobileCard, { left: 0, right: 390, radius: '0px', gap: '8px' })
  await noOverflow(mobile)
  await mobile.screenshot({ path: path.join(output, 'feed-mobile.png') })
  await mobile.goto(`${base}/customer/entertainment/games/12`)
  await mobile.locator('.player-review').waitFor()
  await mobile.locator('.game-community').scrollIntoViewIfNeeded()
  await noOverflow(mobile)
  await mobile.screenshot({ path: path.join(output, 'game-mobile.png') })
  await mobile.getByRole('button', { name: '删除我的评价', exact: true }).click()
  await mobile.locator('.notice-confirm').click()
  await mobile.getByRole('button', { name: '发表评价', exact: true }).waitFor()
  assert.equal(state.review, null)

  const guest = await browser.newPage({ viewport: { width: 375, height: 812 } })
  await mock(guest, false)
  await guest.goto(`${base}/customer/entertainment/games/12`)
  await guest.getByText('登录并玩过这个游戏后即可评价', { exact: true }).waitFor()
  assert.equal(await guest.locator('.review-form').count(), 0)
  for (const width of [320, 540, 768]) {
    await mobile.setViewportSize({ width, height: 844 })
    await mobile.goto(`${base}/customer/moments`)
    await mobile.locator('.feed-list .moment-card').first().waitFor()
    const bounds = await mobile.locator('.feed-list .moment-card').first().evaluate(el => {
      const rect = el.getBoundingClientRect()
      return { left: rect.left, right: rect.right, radius: getComputedStyle(el).borderRadius }
    })
    assert.deepEqual(bounds, { left: 0, right: width, radius: '0px' })
    await noOverflow(mobile)
  }
  assert.deepEqual(pageErrors, [])
  console.log(JSON.stringify({ ok: true, gutters, requests: state, screenshots: output }, null, 2))
} catch (error) {
  for (const [index, page] of browser.contexts().flatMap(context => context.pages()).entries()) {
    await page.screenshot({ path: path.join(output, `failure-${index}.png`) }).catch(() => {})
    console.error(page.url(), await page.locator('body').innerText(), pageErrors)
  }
  throw error
} finally { await browser.close() }
