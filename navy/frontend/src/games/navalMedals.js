import {ref} from 'vue'
import {getNavalMedals,equipNavalMedal} from '../api/naval'
import {ensureGuest,guestToken} from '../api/guest'
export {NAVAL_MEDALS} from './navalMedalCatalog'
import {NAVAL_MEDALS} from './navalMedalCatalog'
const emptyWall=()=>({unlocked:[],equipped:''})
export const ownNavalMedals=ref(emptyWall())
export const navalMedalIdentity=()=>{const token=localStorage.getItem('navy_token');return token?'account:'+token:guestToken()?'guest:'+guestToken():''}
let readGeneration=0,wearGeneration=0,wearIntent=0,activeWear=null,knownOwner=null
export function syncNavalMedalIdentity(){
 const owner=navalMedalIdentity()
 if(owner!==knownOwner){knownOwner=owner;readGeneration++;wearGeneration++;activeWear=null;ownNavalMedals.value=emptyWall()}
 return owner
}
const intent=()=>({account:localStorage.getItem('navy_token')||'',guest:guestToken()})
const intended=value=>(localStorage.getItem('navy_token')||'')===value.account && (Boolean(value.account)||!value.guest||guestToken()===value.guest)
export async function refreshNavalMedals(retryGuest=true,throwError=false){
 const target=intent()
 syncNavalMedalIdentity()
 try{
  await ensureGuest()
  if(!intended(target)){syncNavalMedalIdentity();return ownNavalMedals.value}
  const owner=syncNavalMedalIdentity(),request=++readGeneration,write=wearGeneration,value=await getNavalMedals()
  if(owner!==syncNavalMedalIdentity()){
   if(retryGuest&&!target.account&&!localStorage.getItem('navy_token'))return refreshNavalMedals(false,throwError)
   return ownNavalMedals.value
  }
  if(request===readGeneration&&write===wearGeneration&&activeWear?.owner!==owner)ownNavalMedals.value=value
 }catch(error){if(throwError)throw error}
 return ownNavalMedals.value
}
export const loadOwnNavalMedals=()=>refreshNavalMedals(true,true)
export async function wearNavalMedal(id){
 const target=intent(),ticket=++wearIntent
 syncNavalMedalIdentity();readGeneration++
 await ensureGuest()
 if(ticket!==wearIntent||!intended(target)){syncNavalMedalIdentity();return null}
 const owner=syncNavalMedalIdentity(),request=++wearGeneration
 activeWear={request,owner}
 try{
  const value=await equipNavalMedal(id)
  if(owner!==syncNavalMedalIdentity()){
   if(!target.account&&!localStorage.getItem('navy_token'))await refreshNavalMedals()
   return null
  }
  if(request!==wearGeneration)return null
  readGeneration++;ownNavalMedals.value=value;return value
 }finally{if(activeWear?.request===request)activeWear=null}
}
if(typeof window!=='undefined')window.addEventListener('storage',event=>{if(['navy_token','navy_guest_token'].includes(event.key))syncNavalMedalIdentity()})
export function medalAsset(id){return NAVAL_MEDALS.find(m=>m.id===id)?.src}
export function openNavalMedalWall(person){window.dispatchEvent(new CustomEvent('naval-medal-wall',{detail:person}))}
