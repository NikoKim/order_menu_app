import { API_BASE_URL } from '../config/endpoints'
import { ensureEntertainmentGuest, entertainmentGuestToken } from '../api/entertainment'
import { lobbyFeed } from './navalLobbyFeed'

// Subscriptions share one page connection, including while no game is active.
const liveRooms=new Map(), listeners={lobby:new Set(),directory:new Set(),presence:new Set(),mirror:new Set()}
let socket,retry,heartbeat,opening=false,delay=1000,scope=0,subscriptionQueued=false,roomEntry=null,mirrorId='',directoryRows=[],directoryRevision=-1
const identity=()=>localStorage.getItem('token')||entertainmentGuestToken()
let owner=''
const wanted=()=>roomEntry||Object.values(listeners).some(set=>set.size)
export const hasLiveRoom=id=>Boolean(liveRooms.get(id)?.ready&&liveRooms.get(id).identity===identity())
function ready(entry,value){
  if(!entry)return
  entry.ready=value;entry.status(value)
  if(!value)for(const waiter of entry.waiters){clearTimeout(waiter.timer);waiter.reject(new Error('Room stream disconnected'));entry.waiters.delete(waiter)}
}
export function waitForRoomVersion(id,version){
  const entry=liveRooms.get(id)
  if(!hasLiveRoom(id))return Promise.reject(new Error('Room stream unavailable'))
  if(entry.state?.version>=version)return Promise.resolve(structuredClone(entry.state))
  if(entry.waiters.size>=32)return Promise.reject(new Error('Too many pending actions'))
  return new Promise((resolve,reject)=>{const waiter={version,resolve,reject};waiter.timer=setTimeout(()=>{entry.waiters.delete(waiter);reject(new Error('Room stream timeout'))},5000);entry.waiters.add(waiter)})
}
function emit(topic,value){for(const listener of listeners[topic])listener(structuredClone(value))}
function subscriptions(options={}){
  if(socket?.readyState!==1)return
  const cursor=lobbyFeed.snapshot(identity())
  socket.send(JSON.stringify({multiplex:true,token:localStorage.getItem('token')||'',guest:entertainmentGuestToken(),
    subscriptions:{lobby:Boolean(listeners.lobby.size),directory:Boolean(listeners.directory.size),presence:Boolean(listeners.presence.size)},
    afterId:cursor.afterId,cardVersion:cursor.cardVersion,roomId:roomEntry?.id||'',scope:roomEntry?.scope||0,version:roomEntry?.state?.version??-1,mirrorId,...options}))
}
function update(){
  if(owner&&owner!==identity()){clearInterval(heartbeat);const previous=socket;socket=null;previous?.close();ready(roomEntry,false)}
  if(!wanted()){clearTimeout(retry);clearInterval(heartbeat);const previous=socket;socket=null;previous?.close();return}
  if(socket?.readyState===1){if(!subscriptionQueued){subscriptionQueued=true;queueMicrotask(()=>{subscriptionQueued=false;subscriptions()})}}else open()
}
async function open(){
  if(opening||socket||!wanted())return
  opening=true;clearTimeout(retry)
  try{
    await ensureEntertainmentGuest();if(!wanted())return
    const currentOwner=identity()
    if(owner!==currentOwner){owner=currentOwner;directoryRevision=-1;directoryRows=[];if(roomEntry){roomEntry.state=null;roomEntry.identity=owner}}
    const url=new URL(`${API_BASE_URL}/entertainment/naval/live`,location.origin);url.protocol=url.protocol==='https:'?'wss:':'ws:'
    const current=new WebSocket(url);socket=current
    current.onopen=()=>{if(socket!==current)return current.close();delay=1000;directoryRevision=-1;subscriptions();heartbeat=setInterval(()=>{if(current.readyState===1)current.send('{}')},10000)}
    current.onmessage=event=>{
      if(socket!==current)return
      try{
        const message=JSON.parse(event.data)
        if(message.type==='lobby'){emit('lobby',lobbyFeed.ingest(identity(),message.data));return}
        if(message.type==='presence'){emit('presence',message.onlineCount);return}
        if(message.type==='mirror'){if(message.state.id===mirrorId)emit('mirror',message.state);return}
        if(message.type==='mirrorError'){if(message.id===mirrorId)emit('mirror',{id:mirrorId,missing:true});return}
        if(message.type==='directory'){
          if(message.snapshot)directoryRows=message.rooms
          else{
            if(directoryRevision!==message.baseRevision){subscriptions({forceDirectory:true});return}
            const rows=new Map(directoryRows.map(row=>[row.id,row]));for(const id of message.removed)rows.delete(id);for(const row of message.upsert)rows.set(row.id,row);directoryRows=[...rows.values()]
          }
          directoryRevision=message.revision;emit('directory',directoryRows);return
        }
        const entry=roomEntry
        if(!entry||message.scope!==entry.scope||message.roomId!==entry.id)return
        if(message.type==='roomError'){ready(entry,false);if(message.code===503)current.close();else entry.status(false,new Error('房间订阅已关闭'));return}
        if(message.type==='snapshot')entry.state=message.state
        else if(message.type!=='unchanged'){
          if(!entry.state||entry.state.version!==message.baseVersion){entry.state=null;ready(entry,false);entry.scope=++scope;subscriptions({forceSnapshot:true});return}
          for(const change of message.changes){let node=entry.state;for(const key of change.path.slice(0,-1))node=node[key];if(change.remove)delete node[change.path.at(-1)];else node[change.path.at(-1)]=change.value}
        }
        if(!entry.state){entry.scope=++scope;subscriptions({forceSnapshot:true});return}
        if(Number.isFinite(message.serverTime))entry.state.serverTime=message.serverTime
        entry.state.version=message.version;ready(entry,true)
        for(const waiter of entry.waiters)if(entry.state.version>=waiter.version){clearTimeout(waiter.timer);waiter.resolve(structuredClone(entry.state));entry.waiters.delete(waiter)}
        entry.receive(structuredClone(entry.state))
      }catch{if(roomEntry)roomEntry.state=null;current.close()}
    }
    current.onclose=()=>{if(socket!==current)return;socket=null;clearInterval(heartbeat);ready(roomEntry,false);if(wanted()){retry=setTimeout(open,delay+Math.random()*250);delay=Math.min(15000,delay*2)}}
    current.onerror=()=>current.close()
  }catch{if(wanted())retry=setTimeout(open,5000)}finally{opening=false}
}
function subscribe(topic,receive){
  listeners[topic].add(receive)
  if(topic==='lobby'){const cache=lobbyFeed.snapshot(identity());if(cache.loaded)receive(cache.rows)}
  if(topic==='directory'&&directoryRevision>=0)receive(structuredClone(directoryRows))
  update()
  return ()=>{listeners[topic].delete(receive);update()}
}
export const subscribeLobby=receive=>subscribe('lobby',receive)
export const subscribeDirectory=receive=>subscribe('directory',receive)
export const subscribePresence=receive=>subscribe('presence',receive)
export function subscribeMirror(id,receive){mirrorId=id;const stop=subscribe('mirror',receive);return ()=>{stop();if(mirrorId===id)mirrorId='';update()}}
export function refreshStreams(){if(socket?.readyState===1){socket.send('{}');subscriptions({forceDirectory:Boolean(listeners.directory.size)})}else update()}
export function connectRoom(id,receive,status=()=>{}){
  if(roomEntry)ready(roomEntry,false)
  const entry={id,identity:identity(),scope:++scope,ready:false,state:null,receive,status,waiters:new Set()}
  roomEntry=entry;liveRooms.set(id,entry);update()
  return ()=>{ready(entry,false);if(liveRooms.get(id)===entry)liveRooms.delete(id);if(roomEntry===entry)roomEntry=null;update()}
}
