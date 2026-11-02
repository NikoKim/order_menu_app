import {ref} from 'vue'
import {getNavalMedals,equipNavalMedal} from '../api/naval'
export {NAVAL_MEDALS} from './navalMedalCatalog'
import {NAVAL_MEDALS} from './navalMedalCatalog'
export const ownNavalMedals=ref({unlocked:[],equipped:''})
export async function refreshNavalMedals(){try{ownNavalMedals.value=await getNavalMedals()}catch{}return ownNavalMedals.value}
export async function wearNavalMedal(id){ownNavalMedals.value=await equipNavalMedal(id);return ownNavalMedals.value}
export function medalAsset(id){return NAVAL_MEDALS.find(m=>m.id===id)?.src}
export function openNavalMedalWall(person){window.dispatchEvent(new CustomEvent('naval-medal-wall',{detail:person}))}
