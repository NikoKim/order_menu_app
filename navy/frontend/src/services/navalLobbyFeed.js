export function createLobbyFeed() {
  let rows=[],cursor='0',cards='',owner=null,inFlight=null,generation=0,loaded=false
  function reset(identity){if(owner!==identity){owner=identity;rows=[];cursor='0';cards='';inFlight=null;generation++;loaded=false}}
  function merge(incoming,updates=[]){
    const messages=new Map(rows.map(row=>[String(row.id),row]))
    for(const row of incoming){messages.set(String(row.id),row);if(BigInt(row.id)>BigInt(cursor))cursor=String(row.id)}
    for(const update of updates){const row=messages.get(String(update.id));if(row)messages.set(String(update.id),{...row,room:update.room})}
    rows=[...messages.values()].sort((a,b)=>BigInt(a.id)<BigInt(b.id)?-1:BigInt(a.id)>BigInt(b.id)?1:0).slice(-100)
    return rows
  }
  return {
    snapshot(identity){reset(identity);return {rows,afterId:cursor,cardVersion:cards,loaded}},
    ingest(identity,result){reset(identity);merge(result.records,result.cards);cursor=String(result.afterId);cards=result.cardVersion;loaded=true;return rows},
    async read(identity,fetch){
      reset(identity);if(inFlight)return inFlight
      const epoch=generation
      const pending=(async()=>{let result;for(let page=0;page<3;page++){result=await fetch({afterId:cursor,cardVersion:cards});if(epoch!==generation)return [];merge(result.records,result.cards);cards=result.cardVersion;if(!result.hasMore)break}return rows})()
      inFlight=pending
      try{const value=await pending;loaded=true;return value}finally{if(inFlight===pending)inFlight=null}
    },
    async post(identity,send){reset(identity);const epoch=generation;const result=await send(cursor);return epoch===generation?merge(result):[]}
  }
}
export const lobbyFeed=createLobbyFeed()
