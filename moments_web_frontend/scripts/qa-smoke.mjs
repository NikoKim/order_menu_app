import { chromium } from 'playwright-core'

const base = process.env.QA_BASE_URL || 'http://127.0.0.1:5174'
const browser = await chromium.launch({
  executablePath: 'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe',
  headless: true
})

const sampleMoments = Array.from({ length: 12 }, (_, index) => ({
  id: index + 1,
  author: { id: 9, nickname: '夏日食记', avatarUrl: null, role: 'CUSTOMER', merchant: false },
  title: `今天的动态标题 ${index + 1}`,
  content: '记录一段刚刚发生的小事，也分享此刻的心情。移动端和电脑端都应该自然、清晰地呈现。',
  visibility: 'PUBLIC', status: 'PUBLISHED', imageUrls: index === 0 ? ['/images/sample.svg'] : [],
  topics: [{ id: 1, name: '日常记录' }], likeCount: 8 + index, commentCount: 3, rootCommentCount: index === 1 ? 1 : 3,
  hotComment: { id: 90 + index, momentId: index + 1, author: { id: 8, nickname: '评论用户' }, content: '动态卡片评论预览', imageUrls: [], likeCount: 1, replyCount: 0, replies: [], createdAt: '2026-07-20T12:01:00.000+08:00' },
  reactions: [{ emoji: '😊', count: 2, reactedByMe: false }], editable: false,
  publishedAt: '2026-07-20T12:00:00.000+08:00'
}))

const conversations = [
  { id: 1, type: 'DIRECT', isDirect: true, directUserId: 8, directUserNickname: '小林', directUserAvatarUrl: null, lastMessageContent: '晚点聊', lastMessageAt: '2026-07-20T12:00:00.000+08:00', unreadCount: 2, pinned: false, status: 'ACTIVE' },
  { id: 2, type: 'FAMILY_GROUP', name: '周末家庭群', lastMessageContent: '周六见', lastMessageAt: '2026-07-20T11:00:00.000+08:00', unreadCount: 0, pinned: true, status: 'ACTIVE' }
]

const topics = [
  { id: 1, name: '日常记录', usageCount: 128, userCount: 42, momentCount: 128 },
  { id: 2, name: '美食探店', usageCount: 96, userCount: 31, momentCount: 96 },
  { id: 3, name: '今日心情', usageCount: 81, userCount: 27, momentCount: 81 },
  { id: 4, name: '周末去哪儿', usageCount: 64, userCount: 22, momentCount: 64 },
  { id: 5, name: '随手拍', usageCount: 49, userCount: 19, momentCount: 49 }
]
let lastCommentPayload = null

async function mockApi(page) {
  await page.route('**/images/sample.svg', route => route.fulfill({
    status: 200,
    contentType: 'image/svg+xml',
    body: '<svg xmlns="http://www.w3.org/2000/svg" width="900" height="560"><rect width="900" height="560" fill="#f39a3c"/><circle cx="450" cy="280" r="150" fill="#fff1df"/></svg>'
  }))
  await page.route('**/images/moment_mes/7/legacy-comment.png', route => route.fulfill({
    status: 200,
    contentType: 'image/png',
    body: Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=', 'base64')
  }))
  await page.route('**/api/**', async route => {
    const url = new URL(route.request().url())
    if (!url.pathname.startsWith('/api/')) return route.continue()
    let data = {}
    if (/\/messages\/conversations\/1\/messages$/.test(url.pathname)) {
      const at = (daysAgo, hour, minute, second = 0) => {
        const date = new Date()
        date.setDate(date.getDate() - daysAgo)
        date.setHours(hour, minute, second, 0)
        return date.toISOString()
      }
      data = {
        conversation: conversations[0],
        messages: [
          { id: 11, senderUserId: 8, senderNickname: '小林', type: 'TEXT', content: '这条消息用来检查长按操作栏的位置。', createdAt: at(2, 10, 0) },
          { id: 12, senderUserId: 7, senderNickname: '我', type: 'MOMENT_SHARE', sharedMomentId: 1, sharedMomentTitle: '夏天第一顿冰粉', sharedMomentPreview: '加了很多水果，清爽又好吃。', sharedMomentAuthorName: '夏日食记', sharedMomentCoverUrl: '', content: '动态分享', createdAt: at(2, 10, 0, 30) },
          { id: 13, senderUserId: 0, senderNickname: '', type: 'SYSTEM', content: '你们已经成为好友，可以开始聊天了', createdAt: at(1, 11, 0) },
          { id: 14, senderUserId: 8, senderNickname: '小林', type: 'TEXT', content: '已经撤回的内容', recalledAt: at(1, 11, 0, 20), createdAt: at(1, 11, 0, 20) },
          { id: 15, senderUserId: 7, senderNickname: '我', type: 'TEXT', content: '今天的正常消息', quotedMessageId: 11, quotedContent: '这条消息用来检查长按操作栏的位置。', quotedMessage: { id: 11, senderNickname: '小林' }, createdAt: new Date(Date.now() - 90_000).toISOString() },
          { id: 16, senderUserId: 7, senderNickname: '我', type: 'TEXT', content: '发送失败的消息', deliveryFailed: true, failureReason: '对方已将您拉黑，无法发送消息', createdAt: new Date(Date.now() - 60_000).toISOString() }
        ]
      }
    } else if (url.pathname.endsWith('/messages/conversations')) {
      if (!route.request().headers().authorization) {
        await route.fulfill({ status: 401, contentType: 'application/json', body: JSON.stringify({ code: 401, message: '未登录' }) })
        return
      }
      data = conversations
    }
    else if (url.pathname.endsWith('/moments/topics') && route.request().method() === 'POST') {
      const body = route.request().postDataJSON()
      data = { id: 99, name: body.name, usageCount: 0, userCount: 0, momentCount: 0 }
    }
    else if (url.pathname.endsWith('/moments/topics')) {
      const query = (url.searchParams.get('query') || '').toLocaleLowerCase()
      const records = topics.filter(topic => topic.name.toLocaleLowerCase().includes(query))
      data = { records, page: 1, pageSize: 10, total: query ? records.length : 15, hasMore: !query }
    }
    else if (url.pathname.endsWith('/moments') && route.request().method() === 'POST') {
      data = { ...sampleMoments[0], ...route.request().postDataJSON(), id: 77 }
    }
    else if (url.pathname.endsWith('/moments/77') && route.request().method() === 'PUT') {
      data = { ...sampleMoments[0], ...route.request().postDataJSON(), id: 77 }
    }
    else if (url.pathname.endsWith('/moments/unread-count')) data = { relatedUnreadCount: 0 }
    else if (url.pathname.endsWith('/moments/comment-images') && route.request().method() === 'POST') {
      data = { url: '/images/comment-upload.png' }
    }
    else if (/\/moments\/\d+\/comments$/.test(url.pathname) && route.request().method() === 'POST') {
      lastCommentPayload = route.request().postDataJSON()
      data = { id: 92, momentId: 1, content: lastCommentPayload.content, imageUrls: lastCommentPayload.imageUrls || [] }
    }
    else if (/\/moments\/\d+\/comments$/.test(url.pathname)) data = {
      records: [
        { id: 91, momentId: 1, author: { id: 7, nickname: '测试用户' }, content: '这是一条自己的评论', imageUrls: [], mine: Boolean(route.request().headers().authorization), likeCount: 0, replyCount: 0, replies: [], createdAt: '2026-07-20T12:00:00.000+08:00' },
        { id: 93, momentId: 1, author: { id: 8, nickname: '旧版用户' }, content: '/images/moment_mes/7/legacy-comment.png', imageUrls: [], mine: false, likeCount: 0, replyCount: 0, replies: [], createdAt: '2026-07-20T12:01:00.000+08:00' }
      ],
      total: 4,
      hasMore: url.searchParams.get('page') !== '2'
    }
    else if (/\/moments\/\d+$/.test(url.pathname)) data = { ...sampleMoments[0], content: sampleMoments[0].content.repeat(28) }
    else if (url.pathname.endsWith('/moments')) data = { records: sampleMoments, hasMore: false, total: sampleMoments.length }
    else data = {}
    await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ code: 0, data }) })
  })
}

function assert(condition, message) {
  if (!condition) throw new Error(message)
}

async function assertNoHorizontalOverflow(page, label) {
  const overflow = await page.evaluate(() => [...document.querySelectorAll('body *')]
    .filter(el => {
      const style = getComputedStyle(el)
      const rect = el.getBoundingClientRect()
      return style.position !== 'fixed' && rect.width > 0 && (rect.right > innerWidth + 1 || rect.left < -1)
    })
    .slice(0, 5)
    .map(el => `${el.tagName}.${el.className}`))
  assert(overflow.length === 0, `${label} 出现横向溢出: ${overflow.join(', ')}`)
}

const desktop = await browser.newPage({ viewport: { width: 1440, height: 900 } })
desktop.on('pageerror', error => console.error('desktop page error:', error.message))
desktop.on('console', message => { if (message.type() === 'error') console.error('desktop console:', message.text()) })
await mockApi(desktop)
let response = await desktop.goto(`${base}/customer/moments`, { waitUntil: 'networkidle' })
assert(response?.status() === 200, '动态深层路由未返回 200')
assert(new URL(desktop.url()).pathname === '/customer/moments', '游客访问动态时被隐藏的聊天请求带到了登录页')
assert(await desktop.locator('.conv-search').count() === 0, '游客访问动态时仍然挂载了聊天组件')
await desktop.evaluate(async () => {
  const { default: client } = await import('/src/api/client.js')
  try { await client.get('/messages/conversations') } catch { }
})
await desktop.waitForTimeout(100)
assert(new URL(desktop.url()).pathname === '/customer/moments', '游客在公开页面遇到 401 时被全局拦截器强制跳转登录')
const firstFeedCard = desktop.locator('.moment-card').first()
assert(await firstFeedCard.locator('.mc-comment-preview').count() === 1, '动态卡片默认没有只展示一条评论')
assert(await desktop.locator('.moment-card').nth(1).locator('.mc-comment-controls button').count() === 0, '只有一条直接评论时错误显示了展开评论')
await firstFeedCard.locator('.mc-comment-controls button').first().click()
assert(await firstFeedCard.locator('.mc-comment-preview').first().isVisible(), '首次展开时原有评论发生闪烁或消失')
await firstFeedCard.locator('.mc-comment-preview').nth(1).waitFor({ state: 'visible' })
assert(await firstFeedCard.locator('.mc-comment-preview').count() >= 2, '展开更多评论后没有显示更多评论')
assert(await firstFeedCard.locator('.mc-comment-controls .collapse').isVisible(), '展开评论后没有提供收起评论按钮')
const [moreButtonBox, collapseButtonBox] = await Promise.all([
  firstFeedCard.locator('.mc-comment-controls button').first().boundingBox(),
  firstFeedCard.locator('.mc-comment-controls .collapse').boundingBox()
])
assert(Math.abs(moreButtonBox.x - collapseButtonBox.x) < 2, '展开更多评论和收起评论没有纵向对齐')
await firstFeedCard.locator('.mc-comment-controls .collapse').click()
await firstFeedCard.locator('.mc-comment-preview').nth(1).waitFor({ state: 'hidden' })
assert(await firstFeedCard.locator('.mc-comment-preview').count() === 1, '收起评论后没有恢复成一条评论')
await assertNoHorizontalOverflow(desktop, '桌面动态页')
await desktop.screenshot({ path: 'qa-desktop.png', fullPage: true })
await desktop.addInitScript(() => {
  localStorage.setItem('token', 'qa-token')
  localStorage.setItem('user', JSON.stringify({ id: 7, role: 'CUSTOMER', nickname: '测试用户' }))
})
await desktop.goto(`${base}/customer/moments`, { waitUntil: 'networkidle' })
await desktop.getByRole('button', { name: '相关' }).click()
await desktop.locator('.feed-list').evaluate(el => { el.scrollTop = 360; el.dispatchEvent(new Event('scroll')) })
await desktop.locator('.header-tab').filter({ hasText: '聊天' }).click()
await desktop.waitForURL('**/customer/chat')
await desktop.locator('.header-tab').filter({ hasText: '动态' }).click()
await desktop.waitForURL('**/customer/moments')
assert((await desktop.locator('.feed-tab.active').textContent()).trim() === '相关', '从聊天切回动态后推荐/相关状态丢失')
assert(Math.abs(await desktop.locator('.feed-list').evaluate(el => el.scrollTop) - 360) < 2, '从聊天切回动态后浏览位置丢失')
await desktop.locator('.header-tab').filter({ hasText: '聊天' }).click()
await desktop.waitForURL('**/customer/chat')
await desktop.goto(`${base}/customer/chat`, { waitUntil: 'networkidle' })
await desktop.locator('.conv-menu-trigger').first().click()
const desktopConversationMenu = desktop.locator('.conv-menu')
const desktopConversationMenuBox = await desktopConversationMenu.boundingBox()
assert(desktopConversationMenuBox && desktopConversationMenuBox.height > 0, 'PC 会话操作菜单被 overflow 裁切')
assert(await desktopConversationMenu.evaluate(el => {
  const rect = el.getBoundingClientRect()
  const target = document.elementFromPoint(rect.left + rect.width / 2, rect.bottom - 8)
  return target === el || el.contains(target)
}), 'PC 会话操作菜单被下一条会话遮挡')
await desktop.locator('.conv-search').click({ position: { x: 8, y: 8 } })
assert(new URL(desktop.url()).pathname === '/customer/chat', '关闭会话菜单时误进入了会话')
await desktop.waitForTimeout(180)
assert(await desktop.locator('.conv-menu').count() === 0, '点击外部后会话菜单没有消失')
await desktop.locator('.conv-menu-trigger').nth(1).click()
await desktop.locator('.conv-menu').getByRole('button', { name: '删除' }).click()
assert(await desktop.locator('.notice-dialog').isVisible(), '删除会话没有使用自定义确认组件')
await desktop.waitForTimeout(260)
await desktop.screenshot({ path: 'qa-notice-desktop.png', fullPage: false })
await desktop.locator('.notice-cancel').click()
await desktop.goto(`${base}/customer/chat/1`, { waitUntil: 'networkidle' })
const ownBubble = desktop.locator('.chat-msg-wrapper.mine .msg-body').first()
const ownMenuButton = ownBubble.locator('.msg-menu-btn')
const otherBubble = desktop.locator('.chat-msg-wrapper:not(.mine) .msg-body').first()
const otherMenuButton = otherBubble.locator('.msg-menu-btn')
const [ownBubbleBox, ownButtonBox, otherBubbleBox, otherButtonBox] = await Promise.all([
  ownBubble.boundingBox(), ownMenuButton.boundingBox(), otherBubble.boundingBox(), otherMenuButton.boundingBox()
])
assert(ownButtonBox.x < ownBubbleBox.x, 'PC 端自己的消息操作按钮不在气泡左侧')
assert(otherButtonBox.x > otherBubbleBox.x, 'PC 端对方的消息操作按钮不在气泡右侧')

const mobile = await browser.newPage({ viewport: { width: 390, height: 844 }, isMobile: true, hasTouch: true })
mobile.on('pageerror', error => console.error('mobile page error:', error.message))
mobile.on('console', message => { if (message.type() === 'error') console.error('mobile console:', message.text()) })
await mobile.addInitScript(() => {
  Object.defineProperty(navigator, 'share', { configurable: true, value: async data => { window.__sharedLink = data } })
})
await mockApi(mobile)
await mobile.goto(`${base}/customer/moments`, { waitUntil: 'networkidle' })
await assertNoHorizontalOverflow(mobile, '移动动态页')
await mobile.screenshot({ path: 'qa-mobile.png', fullPage: false })
assert(await mobile.getByRole('link', { name: '登录' }).isVisible(), '未登录状态右上角未显示登录入口')
const feedImage = mobile.locator('.mc-img-wrap img').first()
await feedImage.waitFor({ state: 'visible' })
assert(await feedImage.evaluate(el => el.classList.contains('app-image-loaded')), '动态图片没有完成加载过渡')
await feedImage.click()
const viewerImage = mobile.locator('.iv-image')
await viewerImage.waitFor({ state: 'visible' })
await viewerImage.dblclick({ position: { x: 80, y: 80 } })
assert(await viewerImage.evaluate(el => el.classList.contains('zoomed')), '图片双击后没有放大')
await viewerImage.dispatchEvent('dblclick', { clientX: 150, clientY: 180 })
assert(!await viewerImage.evaluate(el => el.classList.contains('zoomed')), '图片再次双击后没有缩小')
await viewerImage.click()
await mobile.waitForTimeout(260)
assert(await mobile.locator('.iv-backdrop').count() === 0, '单击图片后没有关闭预览')
const feedPanelHeight = await mobile.locator('.moments-panel').evaluate(el => el.clientHeight)
await mobile.locator('.feed-list').evaluate(el => { el.scrollTop = 80; el.dispatchEvent(new Event('scroll')) })
await mobile.locator('.feed-list').evaluate(el => { el.scrollTop = 100; el.dispatchEvent(new Event('scroll')) })
await mobile.waitForTimeout(20)
assert(await mobile.locator('.switch-header').evaluate(el => el.classList.contains('header-hidden')), '动态页没有在滑动过程中立即隐藏顶部操作栏')
await mobile.locator('.feed-list').evaluate(el => { el.scrollTop = 120; el.dispatchEvent(new Event('scroll')) })
await mobile.waitForTimeout(280)
assert(await mobile.locator('.switch-header').evaluate(el => el.classList.contains('header-hidden')), '动态页下滑后顶部操作栏没有隐藏')
assert(await mobile.locator('.switch-header').evaluate(el => el.offsetHeight) <= 1, '顶部操作栏消失后仍占用页面空间')
assert(await mobile.locator('.moments-panel').evaluate(el => el.clientHeight) >= feedPanelHeight + 55, '动态内容没有扩展占据顶部操作栏位置')
assert(Math.abs(await mobile.locator('.feed-list').evaluate(el => el.scrollTop) - 120) < 2, '顶部操作栏动画修改 scrollTop 并中断了滑动')
await mobile.locator('.feed-list').evaluate(el => { el.scrollTop = Math.max(0, el.scrollTop - 30); el.dispatchEvent(new Event('scroll')) })
await mobile.waitForTimeout(20)
assert(!await mobile.locator('.switch-header').evaluate(el => el.classList.contains('header-hidden')), '动态页没有在上滑过程中立即显示顶部操作栏')
await mobile.waitForTimeout(260)
assert(!await mobile.locator('.switch-header').evaluate(el => el.classList.contains('header-hidden')), '动态页上滑后顶部操作栏没有恢复')
await mobile.goto(`${base}/customer/moments/1`, { waitUntil: 'networkidle' })
const legacyComment = mobile.locator('#comment-93')
assert((await legacyComment.locator('.comment-text').textContent()).trim() === '', '旧版评论图片 URL 仍显示为评论文字')
assert(await legacyComment.locator('.comment-img').isVisible(), '旧版评论图片 URL 没有兼容渲染为图片')
const [detailArticleBox, detailImagesBox] = await Promise.all([
  mobile.locator('.detail-article').boundingBox(),
  mobile.locator('.moment-images').boundingBox()
])
assert(detailArticleBox && detailImagesBox && detailImagesBox.x >= detailArticleBox.x + 3 && detailImagesBox.x + detailImagesBox.width <= detailArticleBox.x + detailArticleBox.width - 3, '动态详情图片没有保留左右边距')
assert(parseFloat(await mobile.locator('.moment-images').evaluate(el => getComputedStyle(el).borderRadius)) >= 8, '动态详情图片缺少圆角')
await mobile.evaluate(() => window.scrollTo(0, 520))
await mobile.waitForTimeout(280)
assert(await mobile.locator('.page-header').evaluate(el => el.classList.contains('hidden')), '动态详情下滑后顶部栏没有隐藏')
await mobile.evaluate(() => window.scrollTo(0, 260))
await mobile.waitForTimeout(280)
assert(!await mobile.locator('.page-header').evaluate(el => el.classList.contains('hidden')), '动态详情上滑后顶部栏没有恢复')

await mobile.addInitScript(() => {
  localStorage.setItem('token', 'qa-token')
  localStorage.setItem('user', JSON.stringify({ id: 7, role: 'CUSTOMER', nickname: '测试用户' }))
})
await mobile.goto(`${base}/customer/moments/new`, { waitUntil: 'networkidle' })
assert(await mobile.getByText('热门话题', { exact: true }).isVisible(), '发布动态页没有推荐热门话题')
assert(await mobile.locator('.topic-sug-item').filter({ hasText: '日常记录' }).isVisible(), '热门话题没有正确渲染')
const topicInput = mobile.getByPlaceholder('搜索或创建话题')
await topicInput.fill('美食')
await topicInput.dispatchEvent('input')
await mobile.waitForTimeout(380)
assert(await mobile.getByText('搜索结果', { exact: true }).isVisible(), '输入话题后没有进入搜索结果状态')
const matchedTopic = mobile.locator('.topic-sug-item').filter({ hasText: '美食探店' })
assert(await matchedTopic.isVisible(), '没有显示与输入内容匹配的话题')
await matchedTopic.click()
assert((await mobile.locator('.topic-tag').textContent()).includes('#美食探店'), '选择匹配话题后没有添加到动态')
await mobile.locator('.title-input').fill('测试发布动态标题')
await mobile.locator('.content-area').fill('用于验证发布之后再统一上传图片。')
const imageInput = mobile.locator('.editor-img-add input[type="file"]')
await imageInput.setInputFiles({
  name: 'upload-failure.png',
  mimeType: 'image/png',
  buffer: Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=', 'base64')
})
assert(await mobile.locator('.editor-img-status.queued').isVisible(), '选择图片后提前开始上传，没有等待发布动态')
await mobile.locator('.btn-publish').click()
await mobile.locator('.editor-img-status.failed').waitFor({ state: 'visible' })
assert(await mobile.locator('.editor-img-wrap.is-failed img').isVisible(), '图片上传失败后预览消失')
await mobile.locator('.editor-img-status.failed button').click()
await mobile.locator('.editor-img-status.failed').waitFor({ state: 'visible' })
assert(await mobile.locator('.editor-img-wrap.is-failed').count() === 1, '图片重试失败后没有保留')
await mobile.screenshot({ path: 'qa-editor-topics-mobile.png', fullPage: false })
await mobile.goto(`${base}/customer/moments/1`, { waitUntil: 'networkidle' })
await mobile.locator('.comment-img-btn input[type="file"]').setInputFiles({
  name: 'comment.png',
  mimeType: 'image/png',
  buffer: Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=', 'base64')
})
await mobile.locator('.comment-image-preview.is-ready').waitFor({ state: 'visible' })
assert(await mobile.locator('.comment-text-input').inputValue() === '', '评论图片上传后仍把 URL 嵌入了输入框')
assert(await mobile.locator('.comment-image-preview img').isVisible(), '评论图片上传后没有直接显示图片预览')
assert(await mobile.locator('.comment-send-btn').isEnabled(), '只有图片的评论无法发送')
await mobile.screenshot({ path: 'qa-comment-image-mobile.png', fullPage: false })
await mobile.locator('.comment-send-btn').click()
await mobile.waitForTimeout(100)
assert(lastCommentPayload?.content === '' && lastCommentPayload?.imageUrls?.[0] === '/images/comment-upload.png', '评论图片没有通过 imageUrls 字段发送')
  await mobile.locator('.comment-more').first().click()
  const visibleCommentMenu = mobile.locator('.anchored-menu-surface:visible .comment-menu')
  await visibleCommentMenu.waitFor({ state: 'visible' })
  assert(await visibleCommentMenu.isVisible(), '自己的评论没有三点删除菜单')
  assert(await visibleCommentMenu.getByText('分享到聊天').isVisible(), '评论菜单缺少分享到聊天')
  assert(await visibleCommentMenu.getByText('分享到其他应用').isVisible(), '评论菜单缺少分享到其他应用')
  await visibleCommentMenu.getByText('分享到聊天').click()
assert((await mobile.locator('.share-preview').textContent()).includes('这是一条自己的评论'), '评论分享到聊天没有携带评论卡片内容')
await mobile.locator('.share-cancel').click()
await mobile.locator('.share-cancel').click()
  await mobile.evaluate(() => { window.__sharedLink = null })
  await mobile.locator('.comment-more').first().click()
  await visibleCommentMenu.waitFor({ state: 'visible' })
  await visibleCommentMenu.getByText('分享到其他应用').click()
await mobile.getByRole('button', { name: /^分享评论/ }).click()
await mobile.waitForFunction(() => window.__sharedLink?.url)
const sharedComment = await mobile.evaluate(() => window.__sharedLink)
const sharedCommentUrl = new URL(sharedComment.url)
assert(sharedComment.title === '今天的动态标题 1', '评论外部分享标题没有沿用动态标题')
assert(sharedComment.text.startsWith('今天的动态标题 1\n这是一条自己的评论'), '评论外部分享内容不是评论内容')
assert(sharedCommentUrl.pathname === '/api/moments/1/share-card' && sharedCommentUrl.searchParams.get('commentId') === '91', '评论外部分享链接没有携带评论标识')
await mobile.evaluate(() => { window.__sharedLink = null })
await mobile.locator('.moment-actions .act-btn').filter({ hasText: '分享' }).click()
assert(await mobile.locator('.share-methods').isVisible(), '动态分享未显示分享方式选择')
assert((await mobile.locator('.share-title').textContent()).trim() === '分享动态', '动态分享被错误识别为评论分享')
await mobile.waitForTimeout(260)
await mobile.screenshot({ path: 'qa-share-mobile.png', fullPage: false })
await mobile.locator('.share-methods').getByText('分享到其他应用').click()
assert(await mobile.getByRole('checkbox', { name: /只分享此动态/ }).isVisible(), '外部分享没有显示只分享此动态复选框')
assert(!await mobile.getByRole('checkbox', { name: /只分享此动态/ }).isChecked(), '只分享此动态不应默认勾选')
await mobile.locator('.info-button').click()
await mobile.locator('.info-tooltip').waitFor({ state: 'visible' })
await mobile.screenshot({ path: 'qa-share-options-mobile.png', fullPage: false })
await mobile.getByRole('button', { name: /^分享动态/ }).click()
await mobile.waitForFunction(() => window.__sharedLink?.url)
assert((await mobile.evaluate(() => window.__sharedLink.files?.length || 0)) === 0, '外部分享仍然生成了图片文件')
const sharedMomentUrl = new URL(await mobile.evaluate(() => window.__sharedLink.url))
assert(sharedMomentUrl.origin === 'https://order-menu-backend.zsj-unknown.top', '外部分享没有使用动态元数据接口')
assert(sharedMomentUrl.pathname === '/api/moments/1/share-card', '外部分享没有使用带动态摘要的分享链接')
assert((await mobile.evaluate(() => window.__sharedLink.title)) === '今天的动态标题 1', '外部分享标题不是动态标题')
assert((await mobile.evaluate(() => window.__sharedLink.text)).startsWith('今天的动态标题 1\n记录一段刚刚发生的小事'), 'QQ 分享文本没有按“标题换行内容”组织')
assert(new URL(await mobile.evaluate(() => window.__sharedLink.url)).searchParams.has('v'), '外部分享链接缺少缓存版本，可能继续命中旧卡片')
assert(new URL(await mobile.evaluate(() => window.__sharedLink.url)).searchParams.get('v').startsWith('q9-'), '微信分享链接没有切换到新的卡片版本')
assert(new URL(await mobile.evaluate(() => window.__sharedLink.url)).searchParams.get('mode') === 'browse', '默认外部分享没有使用可浏览动态模式')
await mobile.locator('.moment-actions .act-btn').filter({ hasText: '分享' }).click()
await mobile.locator('.share-methods').getByText('分享到其他应用').click()
await mobile.getByRole('checkbox', { name: /只分享此动态/ }).check()
await mobile.getByRole('button', { name: /^分享动态/ }).click()
await mobile.waitForFunction(() => new URL(window.__sharedLink?.url || location.href).searchParams.get('mode') === 'isolated')
assert(new URL(await mobile.evaluate(() => window.__sharedLink.url)).searchParams.get('mode') === 'isolated', '只分享此动态没有使用隔离模式')
await mobile.goto(`${base}/customer/moments?sharedMoment=1`, { waitUntil: 'networkidle' })
await mobile.waitForURL(url => url.pathname === '/customer/moments/1' && url.searchParams.get('shareMode') === 'browse')
await mobile.locator('.back-btn').click()
await mobile.waitForURL(url => url.pathname === '/customer/moments' && !url.search)
await mobile.goto(`${base}/customer/moments/1?shareMode=isolated`, { waitUntil: 'networkidle' })
assert(await mobile.locator('.desktop-rail').count() === 0, '只分享此动态仍然显示了动态浏览入口')
assert(await mobile.locator('.back-btn').count() === 0, '只分享此动态仍然显示了返回按钮')
await mobile.screenshot({ path: 'qa-isolated-detail-mobile.png', fullPage: false })
await mobile.goto(`${base}/customer/chat`, { waitUntil: 'networkidle' })
const firstConversation = mobile.locator('.conv-tile').first()
const conversationBox = await firstConversation.boundingBox()
await firstConversation.dispatchEvent('pointerdown', { pointerType: 'touch', pointerId: 1, clientX: conversationBox.x + conversationBox.width - 20, clientY: conversationBox.y + 30 })
await firstConversation.dispatchEvent('pointermove', { pointerType: 'touch', pointerId: 1, clientX: conversationBox.x + 80, clientY: conversationBox.y + 30 })
await firstConversation.dispatchEvent('pointerup', { pointerType: 'touch', pointerId: 1, clientX: conversationBox.x + 80, clientY: conversationBox.y + 30 })
assert(await mobile.locator('.conv-swipe-shell').first().evaluate(el => el.classList.contains('open')), '移动端会话左滑操作未打开')
await mobile.screenshot({ path: 'qa-chat-list-mobile.png', fullPage: false })
await mobile.goto(`${base}/customer/chat/1`, { waitUntil: 'networkidle' })
const timestamps = mobile.locator('.msg-timestamp')
assert(await timestamps.count() === 3, '聊天时间未按一分钟间隔合并显示')
assert(/\d+月\d+日 \d{2}:\d{2}/.test((await timestamps.nth(0).textContent()).trim()), '两天前的聊天时间格式不正确')
assert(/^昨天 \d{2}:\d{2}$/.test((await timestamps.nth(1).textContent()).trim()), '昨天的聊天时间格式不正确')
assert(/^\d{2}:\d{2}$/.test((await timestamps.nth(2).textContent()).trim()), '当天的聊天时间格式不正确')
assert(await mobile.locator('.msg-system').isVisible(), '系统提示消息未居中显示')
assert(await mobile.locator('.msg-recalled').isVisible(), '撤回提示消息未居中显示')
const failedNotice = mobile.locator('.msg-delivery-failed')
assert(await failedNotice.isVisible(), '拉黑发送失败提示未显示')
assert((await failedNotice.textContent()).includes('对方已将您拉黑，无法发送消息'), '拉黑发送失败文案不正确')
assert(await failedNotice.evaluate(el => {
  const [red, green, blue] = getComputedStyle(el).color.match(/\d+/g).map(Number)
  return red > green * 1.8 && red > blue * 1.5
}), '发送失败提示不是红色')
await mobile.locator('.msg-quote').click()
assert(await mobile.locator('#msg-11').evaluate(el => el.classList.contains('quote-highlight')), '点击引用后原消息没有高亮强调')
const firstBody = mobile.locator('.msg-body').first()
const firstBodyBox = await firstBody.boundingBox()
await firstBody.dispatchEvent('pointerdown', { pointerType: 'touch', clientX: firstBodyBox.x + 20, clientY: firstBodyBox.y + 20 })
await mobile.waitForTimeout(540)
await firstBody.dispatchEvent('pointerup', { pointerType: 'touch', clientX: firstBodyBox.x + 20, clientY: firstBodyBox.y + 20 })
const menu = mobile.locator('.msg-menu')
await menu.waitFor({ state: 'visible' })
const menuBox = await menu.boundingBox()
const headerBox = await mobile.locator('.chat-header').boundingBox()
assert(menuBox && headerBox && menuBox.y >= headerBox.y + headerBox.height, '消息操作栏被顶部信息栏遮挡')
assert(menuBox.x >= 0 && menuBox.x + menuBox.width <= 390, '消息操作栏溢出移动端屏幕')
await mobile.locator('.menu-backdrop').click({ position: { x: 4, y: 4 } })
await mobile.locator('.chat-emoji-btn').click()
assert(await mobile.locator('.chat-emoji-panel').isVisible(), '聊天 emoji 面板未打开')
await mobile.locator('.chat-emoji-panel button').first().click()
assert((await mobile.locator('.chat-text-input').inputValue()).length > 0, '聊天 emoji 未插入输入框')
await assertNoHorizontalOverflow(mobile, '移动聊天详情页')
await mobile.screenshot({ path: 'qa-chat-mobile.png', fullPage: false })

await desktop.goto(`${base}/404.html`, { waitUntil: 'domcontentloaded' })
const reloadLink = desktop.getByRole('link', { name: /重新加载/ })
assert(await reloadLink.getAttribute('href') === 'https://moment.zsj-unknown.top/', '404 页面重新加载链接不正确')

await browser.close()
console.log('UI smoke checks passed')
