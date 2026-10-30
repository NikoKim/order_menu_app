import test from 'node:test'
import assert from 'node:assert/strict'
import { AI_PROVIDERS, applyAiProvider, detectAiProvider } from '../src/constants/aiProviders.js'

test('DeepSeek preset follows its documented base URL and model', () => {
  const credential = { apiKey: 'unchanged-key' }
  applyAiProvider('deepseek', credential)
  assert.deepEqual(credential, { apiKey: 'unchanged-key', baseUrl: 'https://api.deepseek.com', model: 'deepseek-flash' })
  assert.equal(AI_PROVIDERS.find(provider => provider.id === 'deepseek').model, 'deepseek-flash')
})

test('DeepSeek base URL aliases and manually entered models are recognized without rewriting them', () => {
  for (const baseUrl of ['https://api.deepseek.com', 'https://api.deepseek.com/v1', ' HTTPS://API.DEEPSEEK.COM/v1/ ']) {
    assert.equal(detectAiProvider(baseUrl, 'deepseek-flash'), 'deepseek')
    assert.equal(detectAiProvider(baseUrl, 'deepseek-v4-pro'), 'deepseek')
  }
  assert.equal(detectAiProvider('https://api.deepseek.com.example.com/v1', 'deepseek-flash'), 'custom')
  assert.equal(detectAiProvider('https://example.com/v1', 'deepseek-flash'), 'custom')
})
