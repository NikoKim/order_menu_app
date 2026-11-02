<template><img v-if="sticker" class="naval-message-sticker" :src="sticker.src" :alt="sticker.name" /><span v-else>{{ text }}</span></template>
<script setup>
import { computed } from 'vue'
import { messageSticker } from '../../games/navalStickers'
const props = defineProps({ text: String })
const sticker = computed(() => messageSticker(props.text))
</script>
<style scoped>.naval-message-sticker{display:block;width:112px;height:112px;max-width:100%;object-fit:contain;image-rendering:pixelated}</style>
