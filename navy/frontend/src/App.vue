<template><router-view /><AppNotice /></template>
<script setup>
import { onMounted, onUnmounted } from 'vue'
import AppNotice from './components/AppNotice.vue'
import { installNavyVisualViewport } from './games/navalViewport'
let stopViewport
onMounted(() => { stopViewport = installNavyVisualViewport() })
onUnmounted(() => stopViewport?.())
</script>
