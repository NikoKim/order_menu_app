<template><span class="naval-avatar" :class="{ interactive: canOpen }" :role="canOpen ? 'button' : undefined" :tabindex="canOpen ? 0 : undefined" :aria-label="canOpen ? `查看${person?.name || '用户'}的玩家主页` : person?.name" @click.stop="openWall" @keydown.enter.stop="openWall" @keydown.space.prevent.stop="openWall"><img v-if="person?.registered && person?.avatarUrl && !failed" :src="imageUrl(person.avatarUrl)" :alt="person.name" @error="failed = true" /><UserRound v-else :size="size" /><img v-if="medalAsset(wall?.equipped)" class="worn-medal" :src="medalAsset(wall.equipped)" :alt="'佩戴勋章：'+wall.equipped" /></span></template>
<script setup>
import { computed, ref, watch } from 'vue'
import { UserRound } from 'lucide-vue-next'
import { imageUrl } from '../../composables/useImage'
const props = defineProps({ person: Object, size: { type: Number, default: 17 } })
import {medalAsset,openNavalMedalWall,ownNavalMedals} from '../../games/navalMedals'
import {canOpenNavalPlayer,navalPublicPlayerId} from '../../games/navalPublicPlayers'
const canOpen=computed(()=>canOpenNavalPlayer(props.person))
function openWall(){if(canOpen.value)openNavalMedalWall(props.person)}
const wall=computed(()=>props.person?.self||navalPublicPlayerId(props.person)&&navalPublicPlayerId(props.person)===ownNavalMedals.value.medalId?ownNavalMedals.value:props.person?.medals)
const failed = ref(false)
watch(() => props.person?.avatarUrl, () => { failed.value = false })
</script>
<style scoped>.naval-avatar{width:32px;height:32px;display:inline-flex;align-items:center;justify-content:center;flex:0 0 auto;border:1px solid #d7e3e7;border-radius:50%;position:relative;overflow:visible;background:#e7eef1;color:#7c98a3}.naval-avatar>img:not(.worn-medal){border-radius:50%;width:100%;height:100%;object-fit:cover}.naval-avatar>.worn-medal{position:absolute;left:50%;bottom:max(-3px,-10%);transform:translateX(-50%);width:50%;height:55%;max-width:16px;max-height:17px;object-fit:contain;image-rendering:pixelated;z-index:2}.naval-avatar.interactive{cursor:pointer}.naval-avatar:focus-visible{outline:2px solid #d38b3e;outline-offset:3px}</style>
