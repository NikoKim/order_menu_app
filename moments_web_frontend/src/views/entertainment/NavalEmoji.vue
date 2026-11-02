<template><div class="naval-emoji" aria-label="常用表情"><button v-for="emoji in emojis" :key="emoji" type="button" :aria-label="`添加表情 ${emoji}`" :title="emoji" @click="$emit('select', emoji)">{{ emoji }}</button></div></template>
<script setup>
const emojis = ['😀', '😎', '👍', '👏', '🤝', '🎯', '🔥', '🚢']
defineEmits(['select'])
</script>
<style scoped>.naval-emoji{display:flex;gap:3px;flex-wrap:wrap;padding:6px 0}.naval-emoji button{display:grid;place-items:center;width:30px;height:30px;padding:0;background:transparent;font-size:19px;border-radius:4px}.naval-emoji button:hover{background:#e7f1ed}</style>
