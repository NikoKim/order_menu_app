<template>
 <section class="naval-medal-wall community-body" aria-label="勋章墙">
  <p v-if="error" role="alert">{{ error }}<button @click="load">重试</button></p><p v-else-if="loading" role="status">正在读取勋章...</p>
  <template v-else><p class="medal-progress">经典连胜 {{ wall.classicCurrent || 0 }} · 军事连胜 {{ wall.militaryCurrent || 0 }}</p>
   <section v-for="mode in ['classic','military']" :key="mode" class="medal-group"><h3>{{ mode==='classic'?'经典模式':'军事模式' }} · 人人对战</h3>
    <div v-for="m in NAVAL_MEDALS.filter(m=>m.mode===mode)" :key="m.id" class="medal-row" :class="{locked:!wall.unlocked?.includes(m.id)}">
     <img :src="m.src" :alt="m.name" /><div><strong>{{ m.name }}</strong><small>{{ wall.unlocked?.includes(m.id)?'已解锁':`需连续获胜 ${m.wins} 局` }}</small></div>
     <button v-if="wall.mine" :disabled="busy||!wall.unlocked?.includes(m.id)" :aria-pressed="wall.equipped===m.id" @click="wear(wall.equipped===m.id?'':m.id)"><Check v-if="wall.equipped===m.id" :size="15" />{{ wall.equipped===m.id?'卸下':'佩戴' }}</button>
    </div>
   </section><small>两种模式独立累计，人机与训练局不计入。解锁后永久保留。</small></template>
 </section>
</template>
<script setup>
import {ref,watch} from 'vue'
import {Check} from 'lucide-vue-next'
import {getNavalMedals} from '../../api/naval'
import {NAVAL_MEDALS,ownNavalMedals,refreshNavalMedals,wearNavalMedal} from '../../games/navalMedals'
const props=defineProps({person:Object})
const wall=ref({unlocked:[]}),loading=ref(false),busy=ref(false),error=ref('');let request=0
async function load(){const seq=++request;loading.value=true;error.value='';try{const value=props.person?.self?await refreshNavalMedals():props.person?.medals?.medalId?await getNavalMedals(props.person.medals.medalId):{...(props.person?.medals||{}),unlocked:props.person?.medals?.unlocked||[],mine:false};if(seq===request)wall.value=value}catch(e){if(seq===request)error.value=e.message}finally{if(seq===request)loading.value=false}}
async function wear(id){busy.value=true;try{wall.value={...await wearNavalMedal(id),mine:true};window.dispatchEvent(new CustomEvent('naval-medal-equipped'))}catch(e){error.value=e.message}finally{busy.value=false}}
watch(()=>props.person,load,{immediate:true})
watch(ownNavalMedals,value=>{if(wall.value.mine)wall.value={...value,mine:true}})
</script>
<style scoped>
.naval-medal-wall{flex:1;min-height:0;overflow:auto;color:inherit}.medal-progress{font-size:12px;color:#698b83;margin:14px 0}.medal-group{padding:8px 0;border-top:1px solid #dce7e5}.medal-group h3{font-size:13px;margin:5px 0}.medal-row{display:flex;align-items:center;gap:12px;min-height:88px;padding:8px 0;border-bottom:1px solid #e6eeeb}.medal-row>img{width:58px;height:68px;object-fit:contain;image-rendering:pixelated}.medal-row>div{flex:1;min-width:0}.medal-row strong{font-size:13px}.medal-row small{display:block;font-size:11px;color:#78908b;margin:4px 0}.medal-row.locked>img{filter:grayscale(1);opacity:.45}.medal-row>button{display:flex;align-items:center;gap:3px;background:#e4f0e9;color:#337c64;padding:7px 9px;border:1px solid #cbdfd1;border-radius:4px;font-size:11px;flex-shrink:0}.medal-row>button:disabled{opacity:.4}.medal-reward{display:flex;align-items:center;gap:6px;font-size:11px;color:#768c88}.medal-reward>img{width:42px;height:42px;object-fit:contain;image-rendering:pixelated}.naval-medal-wall>small{font-size:11px;line-height:1.8;color:#879a94}@media(max-height:450px){.medal-row{min-height:76px}.medal-row>img{height:54px;width:48px}.medal-reward>img{height:30px;width:30px}}
</style>
