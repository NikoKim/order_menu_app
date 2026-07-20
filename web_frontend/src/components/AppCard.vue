<template>
  <div class="card" :style="{ padding: padding }">
    <slot />
  </div>
</template>

<script setup>
defineProps({ padding: { type: String, default: '16px' } })
</script>
