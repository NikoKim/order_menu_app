<template>
  <header class="ent-navigation">
    <nav class="back-links" aria-label="返回主站"><router-link to="/customer/chat" title="返回聊天"><ArrowLeft :size="17" /><MessageCircle :size="17" /><span>聊天</span></router-link><router-link to="/customer/moments" title="返回动态"><ArrowLeft :size="17" /><Aperture :size="17" /><span>动态</span></router-link></nav>
    <nav class="game-tabs" aria-label="娱乐类别"><router-link to="/customer/entertainment" :class="{ active: !naval }"><TrendingUp :size="18" />股票</router-link><router-link @pointerenter="prefetchNavalPage" @pointerdown="prefetchNavalPage" @focus="prefetchNavalPage" to="/customer/entertainment/naval" :class="{ active: naval }"><Anchor :size="18" />海战棋</router-link></nav>
  </header>
</template>
<script setup>
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { prefetchNavalPage } from '../games/navalPageLoader'
import { Anchor, Aperture, ArrowLeft, MessageCircle, TrendingUp } from 'lucide-vue-next'
const route = useRoute()
const naval = computed(() => route.path.includes('/naval'))
</script>
<style scoped>
.ent-navigation{display:flex;align-items:center;gap:24px;min-height:52px;border-bottom:1px solid #dce4e7;background:#fff;padding:0 24px}.back-links,.game-tabs{display:flex;align-items:center;gap:18px}.back-links a,.game-tabs a{display:flex;align-items:center;gap:6px;font-size:13px;color:#73868d;white-space:nowrap}.back-links a:hover{color:#c37632}.game-tabs{align-self:stretch;gap:24px}.game-tabs a{border-bottom:2px solid transparent;padding:12px 3px}.game-tabs a.active{color:#cf782d;border-color:#e9954b;font-weight:700}@media(max-width:600px){.ent-navigation{padding:0 12px;gap:20px;min-height:46px}.back-links,.game-tabs{gap:12px}.back-links a{gap:3px}.back-links a>svg:nth-child(2){display:none}.game-tabs a{font-size:13px}}
</style>
