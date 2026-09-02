export const AI_PROVIDERS = [
  { id: 'openai', name: 'OpenAI', baseUrl: 'https://api.openai.com/v1', model: 'gpt-5-mini' },
  { id: 'deepseek', name: 'DeepSeek', baseUrl: 'https://api.deepseek.com', model: 'deepseek-chat' },
  { id: 'qwen', name: '阿里云百炼', baseUrl: 'https://dashscope.aliyuncs.com/compatible-mode/v1', model: 'qwen-plus' },
  { id: 'moonshot', name: 'Moonshot', baseUrl: 'https://api.moonshot.cn/v1', model: 'moonshot-v1-8k' },
  { id: 'zhipu', name: '智谱 AI', baseUrl: 'https://open.bigmodel.cn/api/paas/v4', model: 'glm-4-flash' },
  { id: 'siliconflow', name: '硅基流动', baseUrl: 'https://api.siliconflow.cn/v1', model: 'deepseek-ai/DeepSeek-V3' },
  { id: 'custom', name: '自定义兼容接口', baseUrl: '', model: '' }
]

export function applyAiProvider(providerId, credential) {
  const provider = AI_PROVIDERS.find(item => item.id === providerId)
  if (!provider || provider.id === 'custom') return
  credential.baseUrl = provider.baseUrl
  credential.model = provider.model
}

export function detectAiProvider(baseUrl, model = '') {
  const normalize = value => String(value || '').trim().replace(/\/+$/, '').toLowerCase()
  const matched = AI_PROVIDERS.find(item => item.id !== 'custom' && normalize(item.baseUrl) === normalize(baseUrl)
    && (!model || !item.model || item.model.toLowerCase() === String(model).trim().toLowerCase()))
    || AI_PROVIDERS.find(item => item.id !== 'custom' && normalize(item.baseUrl) === normalize(baseUrl))
  return matched?.id || 'custom'
}
