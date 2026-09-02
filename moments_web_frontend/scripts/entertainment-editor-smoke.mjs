import { chromium } from 'playwright-core'
import { mkdir, readFile } from 'node:fs/promises'
import path from 'node:path'

const token = process.env.QA_CUSTOMER_TOKEN
const user = JSON.parse(process.env.QA_CUSTOMER_USER || 'null')
if (!token || !user) throw new Error('QA_CUSTOMER_TOKEN and QA_CUSTOMER_USER are required')

const baseUrl = process.env.QA_BASE_URL || 'http://127.0.0.1:5174'
const outputDir = process.env.QA_OUTPUT_DIR || path.resolve('test-results/entertainment')
await mkdir(outputDir, { recursive: true })
const browser = await chromium.launch({ executablePath: 'C:/Program Files/Google/Chrome/Application/chrome.exe', headless: true })

try {
  const page = await browser.newPage({ viewport: { width: 1440, height: 1000 } })
  await page.addInitScript(({ token, user }) => {
    localStorage.setItem('token', token)
    localStorage.setItem('user', JSON.stringify(user))
  }, { token, user })
  await page.goto(`${baseUrl}/customer/entertainment/create`, { waitUntil: 'networkidle' })
  await page.getByRole('heading', { name: '创建股票游戏' }).waitFor()
  const [templateDownload] = await Promise.all([
    page.waitForEvent('download'),
    page.getByTitle('下载 CSV 模板').click()
  ])
  const templatePath = await templateDownload.path()
  const templateCsv = await readFile(templatePath, 'utf8')
  if (!templateCsv.includes('guideDescription') || !templateCsv.includes('最终影响（计算说明）') || !templateCsv.includes('可能低于 minImpactPercent')) {
    throw new Error('CSV template is missing field explanations or theoretical values')
  }
  await page.locator('input[type=file][accept*=".csv"]').setInputFiles(templatePath)
  await page.getByText('CSV 预览：1 条可导入').waitFor()
  await page.locator('.csv-preview header button').click()
  if (await page.locator('.ai-player-card').count() !== 3) throw new Error('Default AI profiles were not rendered')
  await page.getByRole('button', { name: '添加 AI' }).click()
  const aiDialog = page.locator('.ai-add-dialog')
  await aiDialog.getByRole('heading', { name: '添加 AI' }).waitFor()
  await aiDialog.getByRole('radio', { name: /激进/ }).click()
  if (await aiDialog.getByLabel('交易活跃倾向 %').inputValue() !== '88') throw new Error('AI style did not update its numeric profile')
  if (await aiDialog.getByLabel('调仓缓冲（百分点）').inputValue() !== '3') throw new Error('AI style did not update its rebalance guard')
  if (await aiDialog.getByLabel('单回合换手上限 %').inputValue() !== '40') throw new Error('AI style did not update its turnover guard')
  if (!await aiDialog.getByLabel('指定策略').inputValue().then(value => value.includes('核心判断失效'))) throw new Error('AI style did not update its strategy text')
  await page.screenshot({ path: path.join(outputDir, 'editor-ai-modal.png') })
  await aiDialog.getByRole('button', { name: '添加 AI' }).click()
  if (await page.locator('.ai-player-card').count() !== 4) throw new Error('AI profile could not be added')

  await page.getByRole('button', { name: '添加', exact: true }).click()
  await page.getByRole('heading', { name: '随机事件设置' }).waitFor()
  await page.getByLabel('事件名称').fill('QA 行业订单')
  await page.getByLabel('事件介绍').fill('验证事件弹窗保存和服务端配置归一化')
  await page.getByLabel('事件类型').selectOption('DEMAND')
  await page.getByLabel('股票相关度').selectOption('HIGH')
  await page.getByLabel('信息可信度').selectOption('PRELIMINARY')
  await page.getByLabel('意外程度').selectOption('MAJOR')
  await page.getByLabel('事件强度').selectOption('STRONG')
  await page.screenshot({ path: path.join(outputDir, 'editor-event-modal.png') })
  await page.getByRole('button', { name: '保存事件' }).click()
  await page.getByText('QA 行业订单').waitFor()

  await page.getByLabel('股票名称').fill(`QA 股票 ${Date.now()}`)
  await page.getByLabel('股票代码').fill('QA26')
  await page.getByRole('button', { name: '保存草稿' }).click()
  await page.waitForURL(/\/customer\/entertainment\/games\/\d+\/edit$/)
  await page.getByText('草稿已保存').waitFor()
  const gameId = new URL(page.url()).pathname.split('/')[4]
  await page.screenshot({ path: path.join(outputDir, 'editor-desktop.png'), fullPage: true })

  const persisted = await page.evaluate(async () => {
    const response = await fetch(`/api/entertainment/games/${location.pathname.split('/')[4]}`, {
      headers: { Authorization: `Bearer ${localStorage.getItem('token')}` }
    }).then(item => item.json())
    return response.data.config
  })
  if (persisted.aiPlayers.length !== 4 || persisted.events.length !== 1) throw new Error('AI or event config was not persisted')
  const savedAi = persisted.aiPlayers[3]
  if (Number(savedAi.rebalanceBandPctPoints) !== 3 || Number(savedAi.maxRoundTurnoverPct) !== 40 || Number(savedAi.maxEstimatedImpactPct) !== 2) {
    throw new Error(`AI execution guards were not persisted: ${JSON.stringify(savedAi)}`)
  }
  const savedEvent = persisted.events[0]
  if (savedEvent.eventType !== 'DEMAND' || savedEvent.relevance !== 'HIGH' || savedEvent.credibility !== 'PRELIMINARY' || savedEvent.surprise !== 'MAJOR' || savedEvent.strength !== 'STRONG') {
    throw new Error(`Structured event metadata was not persisted: ${JSON.stringify(savedEvent)}`)
  }

  await page.setViewportSize({ width: 390, height: 844 })
  await page.reload({ waitUntil: 'networkidle' })
  if (await page.evaluate(() => document.documentElement.scrollWidth > innerWidth)) throw new Error('Mobile editor overflows horizontally')
  await page.getByRole('button', { name: '添加', exact: true }).click()
  await page.getByRole('heading', { name: '随机事件设置' }).waitFor()
  const dialog = await page.locator('.event-dialog').boundingBox()
  if (!dialog || dialog.x < 0 || dialog.x + dialog.width > 390) throw new Error('Mobile event dialog does not fit viewport')
  await page.screenshot({ path: path.join(outputDir, 'editor-mobile-event-modal.png') })
  console.log(JSON.stringify({ gameId, aiProfiles: persisted.aiPlayers.length, events: persisted.events.length, outputDir }))
} finally {
  await browser.close()
}
