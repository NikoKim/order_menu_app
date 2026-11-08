<template>
 <span class="network-indicator-slot" :class="{'is-weak':weak,'is-compact':compact}" :data-network-subject="own?'self':subject||'opponent'" :aria-hidden="weak?undefined:true">
  <button class="icon-control network-indicator" :class="compact?'mobile-rail-button':undefined" type="button" :tabindex="weak?0:-1" :title="title" :aria-label="title" :aria-expanded="expanded" :disabled="!weak" @click="$emit('toggle',$event)"><Wifi :size="18" /></button>
 </span>
</template>
<script setup>
import {computed} from 'vue'
import {Wifi} from 'lucide-vue-next'
const props=defineProps({status:Object,own:Boolean,subject:String,compact:Boolean,expanded:Boolean})
defineEmits(['toggle'])
const weak=computed(()=>props.status?.weak===true)
const title=computed(()=>props.own?'查看我的网络状态':`查看${props.subject||'对方'}网络状态`)
</script>
<style scoped>
.network-indicator-slot{--network-icon-width:var(--navy-toolbar-size,34px);--network-icon-height:var(--navy-toolbar-size,34px);--network-gap:6px;display:inline-block;flex:0 0 auto;width:0;height:var(--network-icon-height);margin-inline-end:calc(-1 * var(--network-gap));opacity:0;overflow:hidden;pointer-events:none;vertical-align:middle;transition:width .24s cubic-bezier(.2,.8,.3,1),opacity .18s ease}
.network-indicator-slot.is-compact{--network-icon-width:26px;--network-icon-height:24px;--network-gap:4px}.network-indicator-slot.is-weak{width:calc(var(--network-icon-width) + var(--network-gap));opacity:1;pointer-events:auto}
.network-indicator{display:grid;place-items:center;box-sizing:border-box;width:var(--network-icon-width);height:var(--network-icon-height);min-height:var(--network-icon-height);padding:0;border:1px solid #cfdee4;border-radius:4px;background:#fff;color:#70919d;cursor:pointer}.network-indicator:hover,.network-indicator[aria-expanded=true]{background:#fff1df;border-color:#e6b079;color:#bd7c3e}.network-indicator:focus-visible{outline-offset:-2px}
.network-indicator>svg{width:18px;height:18px;animation:network-breathe 1.6s ease-in-out infinite}
.network-indicator-slot:not(.is-weak) .network-indicator>svg{animation:none}
@keyframes network-breathe{0%,100%{opacity:.95}50%{opacity:.3}}
@media(prefers-reduced-motion:reduce){.network-indicator-slot{transition:none}.network-indicator>svg{animation:none}}
</style>
