<template>
  <div class="naval-share-mask" @click.self="emit('close')">
    <section ref="dialog" class="naval-share-dialog" role="dialog" aria-modal="true" aria-labelledby="naval-share-heading" @keydown="dialogKeydown">
      <header><h2 id="naval-share-heading">分享海战房间</h2><button class="share-icon" type="button" title="关闭分享" aria-label="关闭分享" @click="emit('close')"><X :size="19" /></button></header>
      <article class="room-share-preview">
        <img :src="details.image" alt="像素舰队港口" />
        <div><h3 :title="details.title">{{ details.title }}</h3><p :title="details.description">{{ details.description }}</p></div>
        <footer><span>房间邀请码</span><strong>{{ details.inviteCode }}</strong></footer>
      </article>
      <div class="room-share-actions">
        <button type="button" :disabled="busy" @click="share('wechat')"><MessageCircle :size="20" /><span>微信</span></button>
        <button type="button" :disabled="busy" @click="share('qq')"><Send :size="20" /><span>QQ</span></button>
        <button type="button" :disabled="busy" @click="share('system')"><Share2 :size="20" /><span>系统分享</span></button>
        <button type="button" :disabled="busy" @click="share('copy')"><Copy :size="20" /><span>复制链接</span></button>
      </div>
      <label class="room-share-link"><span>邀请链接</span><input :value="details.url" aria-label="海战棋分享链接" readonly @focus="$event.target.select()" /></label>
      <p v-if="message" class="share-feedback" :class="{ failure }" :title="message" role="status">{{ message }}</p>
    </section>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { Copy, MessageCircle, Send, Share2, X } from 'lucide-vue-next'
import { navalShareDetails, shareNavalRoom } from '../../services/navalShare'

const props = defineProps({ room: { type: Object, required: true } })
const emit = defineEmits(['close'])
const dialog = ref(null), busy = ref(false), message = ref(''), failure = ref(false)
const details = computed(() => navalShareDetails(props.room))
let previousFocus

async function share(platform) {
  if (busy.value) return
  busy.value = true; message.value = ''; failure.value = false
  try { const result = await shareNavalRoom(props.room, { platform }); message.value = result.message }
  catch (error) { failure.value = true; message.value = error?.message || '暂时无法分享，请复制邀请链接' }
  finally { busy.value = false }
}
function dialogKeydown(event) {
  if (event.key === 'Escape') { event.stopPropagation(); emit('close'); return }
  if (event.key !== 'Tab') return
  const controls = [...dialog.value.querySelectorAll('button:not(:disabled),input')]
  const first = controls[0], last = controls.at(-1)
  if (event.shiftKey && document.activeElement === first) { event.preventDefault(); last?.focus() }
  else if (!event.shiftKey && document.activeElement === last) { event.preventDefault(); first?.focus() }
}
onMounted(() => { previousFocus = document.activeElement; dialog.value?.querySelector('button')?.focus({ preventScroll: true }) })
onBeforeUnmount(() => { if (previousFocus?.isConnected) previousFocus.focus({ preventScroll: true }) })
</script>

<style scoped>
.naval-share-mask{position:fixed;inset:0;z-index:570;display:grid;grid-template-columns:minmax(0,1fr);place-items:center;padding:20px;background:#17313e66;backdrop-filter:blur(3px)}
.naval-share-dialog{width:420px;max-width:100%;max-height:90dvh;overflow:auto;background:#fff;border:1px solid #d4e0e5;border-radius:7px;padding:19px;box-shadow:0 18px 60px #15334029;letter-spacing:0}
.naval-share-dialog>header{display:flex;align-items:center;justify-content:space-between;gap:14px;margin-bottom:15px}
.naval-share-dialog h2{font-size:17px;color:#2d4d59}
.share-icon{display:grid;place-items:center;width:32px;height:32px;background:transparent;border-radius:4px;color:#81959e}
.share-icon:hover{background:#eff4f5}
.room-share-preview{overflow:hidden;border:1px solid #dfe8eb;border-radius:5px;background:#fafcfc}
.room-share-preview>img{display:block;width:100%;height:108px;object-fit:cover;image-rendering:pixelated}
.room-share-preview>div{padding:13px 13px 10px}
.room-share-preview h3{font-size:14px;line-height:1.5;color:#355864;overflow-wrap:anywhere}
.room-share-preview p{font-size:11px;line-height:1.7;color:#7e969f;margin-top:5px;overflow-wrap:anywhere}
.room-share-preview footer{display:flex;align-items:center;justify-content:space-between;gap:12px;border-top:1px solid #e4ecee;padding:9px 13px;font-size:11px;color:#91a3ab}
.room-share-preview strong{font-family:inherit;font-size:15px;color:#b97c3b;font-weight:700;letter-spacing:0}
.room-share-actions{display:grid;grid-template-columns:repeat(4,minmax(0,1fr));gap:7px;margin:16px 0}
.room-share-actions>button{min-height:62px;display:flex;align-items:center;justify-content:center;flex-direction:column;gap:7px;border:1px solid #dce6e9;border-radius:5px;background:#fff;color:#6f939e;font-size:10px}
.room-share-actions>button:first-child{color:#5b9673}
.room-share-actions>button:nth-child(2){color:#6895ae}
.room-share-actions>button:hover{border-color:#dda36a;background:#fff8ef;color:#c28747}
.room-share-actions>button:disabled{opacity:.45}
.room-share-link{display:block;font-size:10px;color:#8a9fa7}
.room-share-link input{display:block;width:100%;min-width:0;margin-top:6px;padding:8px;border:1px solid #dce6e9;border-radius:4px;background:#f7fafb;color:#69848e;font-family:inherit;font-size:11px;white-space:nowrap}
.share-feedback{font-size:11px;line-height:1.8;color:#648876;margin:12px 0 0;overflow-wrap:anywhere}
.share-feedback.failure{color:#b47a5e}
@media(max-height:500px) and (orientation:landscape){
  .naval-share-dialog{width:560px;padding:14px}
  .naval-share-dialog>header{margin-bottom:9px}
  .room-share-preview{display:grid;grid-template-columns:150px minmax(0,1fr)}
  .room-share-preview>img{grid-row:1/3;height:100%;min-height:100px}
  .room-share-preview>div{padding:9px 12px 6px}
  .room-share-preview footer{padding:7px 12px}
  .room-share-actions{margin:10px 0}
  .room-share-actions>button{min-height:43px;flex-direction:row;gap:6px}
  .room-share-link input{padding:6px}
  .share-feedback{margin-top:8px}
}
@media(max-width:480px) and (orientation:portrait){
  .naval-share-mask{padding:14px}
  .naval-share-dialog{padding:16px}
  .room-share-preview>img{height:100px}
}
</style>

<style scoped>
@media(max-height:500px) and (orientation:landscape){
  .naval-share-mask{padding:8px max(12px,env(safe-area-inset-right)) 8px max(12px,env(safe-area-inset-left));box-sizing:border-box;overflow:hidden}
  .naval-share-dialog{display:grid;grid-template-columns:minmax(0,1fr);grid-template-rows:32px minmax(80px,1fr) 44px 32px auto;gap:6px;width:700px;max-width:100%;height:min(304px,calc(100dvh - 16px));max-height:calc(100dvh - 16px);min-height:0;overflow:hidden;padding:10px;box-sizing:border-box}
  .naval-share-dialog>header{height:32px;margin:0;min-width:0}.naval-share-dialog h2{font-size:15px}.share-icon{width:32px;height:32px}
  .room-share-preview{grid-template-columns:144px minmax(0,1fr);grid-template-rows:minmax(0,1fr) 28px;min-width:0;min-height:0}.room-share-preview>img{height:100%;min-height:0;grid-row:1/3}.room-share-preview>div{min-height:0;padding:7px 10px 4px}.room-share-preview h3{font-size:12px;line-height:1.4;display:-webkit-box;-webkit-line-clamp:1;-webkit-box-orient:vertical;overflow:hidden}.room-share-preview p{font-size:10px;line-height:1.4;margin-top:4px;display:-webkit-box;-webkit-line-clamp:2;-webkit-box-orient:vertical;overflow:hidden}.room-share-preview footer{font-size:10px;padding:4px 10px}.room-share-preview strong{font-size:14px}
  .room-share-actions{height:44px;margin:0;gap:6px}.room-share-actions>button{min-height:44px;height:44px;gap:6px;font-size:10px}.room-share-actions>button>svg{width:18px;height:18px}
  .room-share-link{display:flex;align-items:center;gap:8px;min-width:0}.room-share-link>span{flex:0 0 auto;font-size:10px}.room-share-link input{min-height:32px;height:32px;margin:0;padding:6px 8px;font-size:10px}
  .share-feedback{min-height:0;max-height:28px;margin:0;font-size:10px;line-height:1.4;display:-webkit-box;-webkit-line-clamp:2;-webkit-box-orient:vertical;overflow:hidden}
}
@media(max-width:620px) and (max-height:500px) and (orientation:landscape){.room-share-preview{grid-template-columns:120px minmax(0,1fr)}}
</style>
