<template>
  <span class="naval-turn-clock" :class="{ 'is-visible':visible, compact, reverse }" :aria-hidden="visible?undefined:true">
    <span class="turn-clock-content"><slot /><strong :class="compact?'rail-clock':'board-clock'">{{ display }}</strong></span>
  </span>
</template>
<script setup>
import { ref, watch } from 'vue'
const props=defineProps({visible:Boolean,clock:String,compact:Boolean,reverse:Boolean})
const display=ref('0:00')
watch([()=>props.visible,()=>props.clock],([visible,clock])=>{if(visible&&clock)display.value=clock},{immediate:true})
</script>
<style scoped>
.naval-turn-clock{--clock-width:54px;--clock-height:24px;--clock-gap:9px;display:inline-flex;flex:0 0 auto;width:0;height:var(--clock-height);min-width:0;margin-inline-end:calc(-1 * var(--clock-gap));overflow:hidden;opacity:0;pointer-events:none;transition:width .26s cubic-bezier(.2,.8,.3,1),opacity .2s ease}
.naval-turn-clock.is-visible{width:calc(var(--clock-width) + var(--clock-gap));opacity:1}
.turn-clock-content{display:flex;flex:0 0 var(--clock-width);align-items:center;justify-content:center;gap:5px;width:var(--clock-width);height:var(--clock-height);white-space:nowrap;transform:translateX(-5px);transition:transform .26s cubic-bezier(.2,.8,.3,1)}
.reverse .turn-clock-content{transform:translateX(5px)}.is-visible .turn-clock-content{transform:translateX(0)}
.turn-clock-content :deep(svg){flex-shrink:0}.board-clock{font-size:14px;line-height:20px;font-weight:600;font-variant-numeric:tabular-nums}.compact{--clock-width:44px;--clock-gap:4px}.compact .turn-clock-content{gap:2px}.rail-clock{display:block;width:26px;flex:0 0 26px;font-size:11px;line-height:14px;font-weight:650;font-variant-numeric:tabular-nums;text-align:center;white-space:nowrap}
@media(max-height:600px) and (orientation:landscape){.naval-turn-clock:not(.compact){--clock-width:46px;--clock-gap:6px}.board-clock{font-size:11px}}
@media(prefers-reduced-motion:reduce){.naval-turn-clock,.turn-clock-content{transition:none}}
</style>
