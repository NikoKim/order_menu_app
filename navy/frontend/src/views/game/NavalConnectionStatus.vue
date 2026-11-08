<template>
 <section class="network-details" :data-network-details="own?'self':'opponent'">
  <div class="network-detail-heading"><Wifi :size="19" /><h3>{{ message }}</h3></div>
  <dl class="network-detail-latency"><dt>延迟</dt><dd><output v-if="hasLatency">{{ Math.ceil(status.latencyMs) }}<small>ms</small></output><span v-else>检测中</span></dd></dl>
 </section>
</template>
<script setup>
import {computed} from 'vue'
import {Wifi} from 'lucide-vue-next'
const props=defineProps({status:Object,own:Boolean})
const hasLatency=computed(()=>!props.status?.unavailable && Number.isFinite(props.status?.latencyMs) && props.status.latencyMs>=0)
const message=computed(()=>props.status?.unavailable ? `${props.own?'':'对方'}连接中断，正在重连` : `${props.own?'当前您的':'对方'}连接较差`)
</script>
<style scoped>
.network-details{padding:10px 0 4px;color:#587985}
.network-detail-heading{display:flex;align-items:center;gap:8px;min-width:0;color:#b98340}
.network-detail-heading>svg{flex-shrink:0}
.network-detail-heading h3{margin:0;font-size:13px;line-height:1.6;font-weight:600;color:#456d79;overflow-wrap:anywhere}
.network-detail-latency{display:flex;align-items:baseline;justify-content:space-between;gap:16px;margin:16px 0 0}
.network-detail-latency dt{font-size:11px;color:#8aa0a8}
.network-detail-latency dd{margin:0;font-size:13px;font-variant-numeric:tabular-nums}
.network-detail-latency output{font-size:23px;line-height:1.2;font-weight:600;color:#af7d3d}
.network-detail-latency output small{font-size:11px;font-weight:400;margin-left:5px;color:#8b9fa6}
</style>
