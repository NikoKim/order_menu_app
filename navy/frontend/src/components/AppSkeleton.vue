<template>
  <div class="app-skeleton" :class="variant" :data-variant="variant" role="status" :aria-label="label" aria-busy="true">
    <span class="sr-only">{{ label }}</span>
    <div v-if="variant === 'profile'" class="skeleton-profile" aria-hidden="true"><i class="skeleton-avatar" /><div class="skeleton-copy"><i class="skeleton-line title" /><i class="skeleton-line short" /></div></div>
    <div v-if="variant === 'detail'" class="skeleton-detail" aria-hidden="true"><div v-for="side in 2" :key="side"><i class="skeleton-line title" /><i class="skeleton-block" /><i class="skeleton-line" /><i class="skeleton-line short" /></div></div>
    <div v-else class="skeleton-rows" aria-hidden="true">
      <div v-for="index in count" :key="index" class="skeleton-row">
        <i v-if="variant !== 'profile'" :class="variant === 'products' ? 'skeleton-art' : 'skeleton-avatar'" />
        <div class="skeleton-copy"><i class="skeleton-line" :style="{width: widths[index % widths.length]}" /><i class="skeleton-line short" /></div>
        <i v-if="variant === 'ranking' || variant === 'products'" class="skeleton-value" />
      </div>
    </div>
  </div>
</template>
<script setup>
import { computed } from 'vue'
const props = defineProps({variant:{type:String,default:'list'}, rows:{type:Number,default:5}, label:{type:String,default:'正在加载'}})
const count = computed(() => Math.max(1, Math.min(12, Math.trunc(props.rows) || 5)))
const widths = ['69%', '83%', '58%', '76%']
</script>
<style scoped>
.app-skeleton{min-width:0;width:100%;padding:5px 0;color:#dae5e9;overflow:hidden}
.skeleton-row{display:flex;align-items:center;gap:10px;min-height:52px;padding:10px 1px;border-bottom:1px solid #e5edef}
.skeleton-avatar,.skeleton-art,.skeleton-value,.skeleton-line,.skeleton-block{display:block;flex-shrink:0;border-radius:3px;background:linear-gradient(90deg,#e3ecef 20%,#f2f6f7 45%,#e3ecef 70%);background-size:240% 100%;animation:skeleton-sweep 1.35s linear infinite}
.skeleton-avatar{width:29px;height:29px;border-radius:50%}.skeleton-copy{flex:1;min-width:0;display:flex;flex-direction:column;gap:8px}.skeleton-line{height:9px;width:78%;max-width:280px}.skeleton-line.short{height:7px;width:42%;opacity:.68}.skeleton-line.title{height:14px;width:56%}.skeleton-value{width:38px;height:11px;margin-left:auto}
.ranking .skeleton-row{min-height:42px;padding:6px 1px}.ranking .skeleton-avatar{width:24px;height:24px}.ranking .skeleton-line.short{height:5px}.ranking .skeleton-copy{gap:5px}
.skeleton-profile{display:flex;align-items:center;gap:12px;min-height:84px;padding:10px 0 18px}.skeleton-profile>.skeleton-avatar{width:52px;height:52px}.profile .skeleton-row{min-height:58px}.skeleton-detail{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:20px;padding-top:14px}.skeleton-detail>div{display:flex;flex-direction:column;gap:14px}.skeleton-block{width:100%;height:94px;opacity:.65}
.products .skeleton-rows{display:grid;grid-template-columns:minmax(0,1fr)}.products .skeleton-row{position:relative;gap:12px;min-height:133px;align-items:flex-start;padding:17px 0}.skeleton-art{width:58px;height:65px;border-radius:4px}.products .skeleton-copy{padding-top:7px;gap:14px}.products .skeleton-value{position:absolute;right:0;bottom:18px;width:60px;height:27px}.products .skeleton-line.short{width:65%}
@media(orientation:landscape) and (max-height:550px){.products .skeleton-rows{grid-template-columns:repeat(2,minmax(0,1fr));column-gap:22px}.products .skeleton-row{min-height:120px;padding-top:12px}.skeleton-art{width:45px;height:54px}.ranking .skeleton-row{min-height:32px;padding:4px 0}.ranking .skeleton-avatar{width:21px;height:21px}.skeleton-row{min-height:45px;padding:7px 0}.skeleton-detail{gap:12px}.skeleton-block{height:72px}}
@media(prefers-reduced-motion:reduce){.app-skeleton i{animation:none;background:#e6eef1}}
@keyframes skeleton-sweep{from{background-position:100% 0}to{background-position:-100% 0}}
</style>
