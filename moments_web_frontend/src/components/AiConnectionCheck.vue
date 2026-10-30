<template>
  <div class="connection-check">
    <button type="button" class="check-command" :disabled="disabled || busy || !ready" title="发送少量测试请求，可能产生少量模型费用" @click="verify"><LoaderCircle v-if="busy" class="spinning" :size="16" /><PlugZap v-else :size="16" />{{ busy ? '正在验证连接' : label }}</button>
    <div v-if="result" class="check-result" :class="{ connected: result.connected }" role="status"><CircleCheck v-if="result.connected" :size="16" /><CircleAlert v-else :size="16" /><span><span>{{ result.message }}</span><small v-if="result.connected">{{ result.model }} · {{ result.latencyMs }} ms</small></span></div>
    <small v-else class="check-cost">仅在点击时发送少量模型测试请求，可能产生少量费用。</small>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { CircleAlert, CircleCheck, LoaderCircle, PlugZap } from 'lucide-vue-next'
import { ensureEntertainmentGuest, testAiConnection } from '../api/entertainment'

const props = defineProps({ source: { type: String, default: 'OWN' }, gameId: Number, roomId: Number, credential: Object, disabled: Boolean, label: { type: String, default: '验证 API 连接' } })
const busy = ref(false)
const result = ref(null)
let controller
let revision = 0
const ready = computed(() => props.source !== 'OWN' || (props.credential?.baseUrl?.trim() && props.credential?.model?.trim() && props.credential?.apiKey?.trim().length >= 8))
watch(() => [props.source, props.gameId, props.roomId, props.credential?.baseUrl, props.credential?.model, props.credential?.apiKey], () => {
  revision++
  controller?.abort()
  busy.value = false
  result.value = null
})
async function verify() {
  if (!ready.value || busy.value) return
  const current = ++revision
  controller?.abort()
  controller = new AbortController()
  busy.value = true
  result.value = null
  const payload = { source: props.source, gameId: props.gameId, roomId: props.roomId,
    aiCredential: props.source === 'OWN' ? { ...props.credential } : undefined }
  try {
    await ensureEntertainmentGuest()
    if (current !== revision) return
    const response = await testAiConnection(payload, controller.signal)
    if (current === revision) result.value = response
  } catch (error) {
    if (current === revision && !controller.signal.aborted) result.value = { connected: false,
      message: error.code === 404 && error.message === '请求的接口或资源不存在'
        ? '当前后端尚未提供 API 验证接口，请更新并重启后端后再试' : error.message }
  } finally { if (current === revision) busy.value = false }
}
onBeforeUnmount(() => { revision++; controller?.abort() })
</script>

<style scoped>
.connection-check{display:grid;gap:7px;margin-top:14px;min-width:0}.check-command{min-height:38px;display:flex;align-items:center;justify-content:center;gap:7px;padding:8px 12px;border:1px solid #adc8ba;border-radius:5px;color:#286b50;background:#f3f8f5;font-size:12px}.check-command:disabled{opacity:.5;cursor:not-allowed}.check-result{display:flex;align-items:flex-start;gap:6px;padding:8px 10px;color:#a23f3b;background:#fff1ef;border-radius:4px;font-size:12px;overflow-wrap:anywhere}.check-result.connected{color:#246b49;background:#edf7f1}.check-result>svg{flex-shrink:0;margin-top:1px}.check-result span{min-width:0}.check-result small{display:block;margin-top:3px;color:#5b7c6b;font-size:11px}.check-cost{color:#818a85;font-size:10px;line-height:1.6}.spinning{animation:connection-spin .8s linear infinite}@keyframes connection-spin{to{transform:rotate(360deg)}}@media(prefers-reduced-motion:reduce){.spinning{animation:none}}
</style>
