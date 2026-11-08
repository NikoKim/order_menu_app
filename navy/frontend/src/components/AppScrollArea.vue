<template><component :is="tag" ref="element" class="app-scroll-area"><slot /></component></template>
<script setup>
import { ref } from 'vue'
defineProps({tag:{type:String,default:'div'}})
const element=ref(null)
defineExpose({get scrollTop(){return element.value?.scrollTop||0},set scrollTop(value){if(element.value)element.value.scrollTop=value},get scrollHeight(){return element.value?.scrollHeight||0},get clientHeight(){return element.value?.clientHeight||0},scrollTo:(...args)=>element.value?.scrollTo(...args),contains:node=>element.value?.contains(node)})
</script>
<style scoped>.app-scroll-area{min-width:0;min-height:0;overflow:auto;overscroll-behavior:contain;scrollbar-width:thin;scrollbar-color:#8aa6b34d transparent}.app-scroll-area::-webkit-scrollbar{width:3px;height:3px}.app-scroll-area::-webkit-scrollbar-track{background:transparent}.app-scroll-area::-webkit-scrollbar-thumb{background:#8aa6b34d;border-radius:3px}.app-scroll-area::-webkit-scrollbar-thumb:hover{background:#8aa6b37a}</style>
