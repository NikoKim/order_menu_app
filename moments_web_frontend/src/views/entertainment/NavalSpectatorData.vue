<template>
  <div class="spectator-data" aria-label="舰队战备数据"><span class="spectator-coins" title="剩余金币"><Coins :size="14" />{{ player?.coins ?? 0 }}</span><span v-for="(item, key) in ITEMS" :key="key" :title="item.name"><img :src="`/entertainment/naval/v2/${key}.png`" alt="" />{{ player?.inventory?.[key] || 0 }}</span></div>
</template>
<script setup>
import { Coins } from 'lucide-vue-next'
import { ITEMS } from '../../games/navalEngine'
defineProps({ player: Object })
</script>
<style scoped>
.spectator-data{display:flex;flex-wrap:wrap;align-items:center;justify-content:space-between;gap:6px;padding:9px 2px;font-size:11px;color:#7a949e;border-bottom:1px solid #e0e9ed}.spectator-data>span{display:flex;align-items:center;gap:4px}.spectator-data img{width:19px;height:19px;object-fit:contain;image-rendering:pixelated}.spectator-data .spectator-coins{color:#bd9257}@media(max-height:600px){.spectator-data{gap:4px;padding:5px 2px;font-size:9px}.spectator-data img{width:14px;height:14px}}
</style>
