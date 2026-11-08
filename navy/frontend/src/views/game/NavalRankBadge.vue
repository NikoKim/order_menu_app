<template>
  <span class="naval-rank-badge" :style="{width:`${size}px`,height:`${size}px`}" :title="rank">
    <img :src="`/naval/ranks/lv${safeLevel}.png`" :alt="`${rank}军衔`" />
  </span>
</template>
<script setup>
import {computed} from 'vue'
const props=defineProps({level:{type:Number,default:1},size:{type:Number,default:48}})
const ranks=['下士','中士','上士','少尉','中尉','上尉','少校','中校','上校','少将','中将','上将']
const safeLevel=computed(()=>Math.max(1,Math.min(12,Math.trunc(props.level)||1)))
const rank=computed(()=>ranks[safeLevel.value-1])
</script>
<style scoped>
.naval-rank-badge{display:inline-flex;align-items:center;justify-content:center;flex-shrink:0}.naval-rank-badge img{width:100%;height:100%;object-fit:contain;image-rendering:pixelated}
</style>
