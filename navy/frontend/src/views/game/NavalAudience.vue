<template>
  <section class="naval-audience" aria-label="观众席">
    <header><span><Eye :size="15" />观众席 <strong>{{ room.spectators?.length || 0 }}</strong></span><span v-if="spectating" class="viewer-tag">{{ modeLabel }}</span></header>
    <div v-if="room.spectators?.length" class="audience-list"><div v-for="person in room.spectators" :key="person.id" class="audience-person" :class="{ current: person.id === room.ownSpectatorId }" :title="person.name"><NavalAvatar :person="person" /><span>{{ person.name }}</span></div></div>
    <p v-else class="audience-empty">{{ room.allowSpectators === false ? '已关闭观战' : '暂无观众' }}</p>
    <label v-if="!spectating" class="audience-setting"><input type="checkbox" role="switch" :checked="room.allowSpectators !== false" :disabled="busy || cooldown > 0" @change="changeSpectators" />允许观战<small v-if="cooldown > 0" :title="`${cooldown}秒后可切换`">{{ cooldown }}s</small></label>
    <div v-if="spectating && room.mode === 'MULTIPLAYER' && ['WAITING', 'FINISHED'].includes(room.phase)" class="seat-choices"><button v-for="(player, index) in room.players" :key="index" class="seat-choice" :disabled="busy || player.occupied && !player.left" @click="$emit('claim', index)"><Armchair :size="16" /><span>{{ index === 0 ? '一号席位' : '二号席位' }}</span><strong>{{ player.occupied && !player.left ? player.name : '加入此席位' }}</strong></button></div>
  </section>
</template>
<script setup>
import { computed } from 'vue'
import { Armchair, Eye } from 'lucide-vue-next'
import NavalAvatar from './NavalAvatar.vue'
const props = defineProps({ room: { type: Object, required: true }, busy: Boolean, cooldown:{type:Number,default:0} })
const emit = defineEmits(['claim', 'spectators'])
function changeSpectators(event) { const value = event.target.checked; event.target.checked = props.room.allowSpectators !== false; if (!props.busy && !props.cooldown) emit('spectators', value) }
const spectating = computed(() => props.room.role === 'SPECTATOR' || props.room.you === -1)
const modeLabel = computed(() => props.room.viewMode === 'PLAYER_0' ? '一号玩家视角' : props.room.viewMode === 'PLAYER_1' ? '二号玩家视角' : '全局观战')
</script>
<style scoped>
.naval-audience{border-top:1px solid #dce6ea;padding:13px 0;margin-top:12px}.naval-audience header{display:flex;justify-content:space-between;gap:12px;align-items:center;font-size:11px;color:#7d959e}.naval-audience header>span:first-child{display:flex;align-items:center;gap:7px}.naval-audience header strong{font:11px inherit;color:#526f7c}.audience-list{display:flex;flex-wrap:wrap;gap:12px 20px;padding-top:12px;max-height:125px;overflow:auto}.audience-person{display:flex;align-items:center;gap:7px;font-size:11px;color:#698490;max-width:170px}.audience-person>span{overflow:hidden;text-overflow:ellipsis;white-space:nowrap}.audience-person.current{color:#be803d}.audience-person.current :deep(.naval-avatar){border-color:#dfac74}.audience-empty{font-size:11px;color:#9aaeb6;margin:10px 0 0}.viewer-tag{font-size:10px}.seat-choices{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:10px;margin-top:14px}.seat-choice{display:flex;align-items:center;gap:8px;min-height:42px;padding:8px 12px;border:1px solid #d8e4e9;border-radius:5px;color:#577984;background:#fff;font-size:11px;min-width:0}.seat-choice strong{margin-left:auto;font-size:11px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}.seat-choice:disabled{opacity:.45;cursor:default}@media(max-height:600px){.naval-audience{padding:9px 0;margin-top:8px}.audience-list{padding-top:8px;gap:8px 12px;max-height:80px}.audience-person :deep(.naval-avatar){width:25px;height:25px}.seat-choice{padding:6px 8px;min-height:34px;gap:5px;font-size:9px}.seat-choice strong{font-size:9px}}
</style>
<style scoped>.audience-setting{display:flex;align-items:center;gap:8px;font-size:12px;margin-top:12px;color:var(--color-text-secondary)}.audience-setting input{width:16px;height:16px;accent-color:var(--color-primary)}.audience-setting small{font-size:10px;white-space:nowrap;color:#9aa5a7}</style>
